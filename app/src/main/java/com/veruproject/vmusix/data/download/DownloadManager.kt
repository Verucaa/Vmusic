package com.veruproject.vmusix.data.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.veruproject.vmusix.data.local.db.DownloadDao
import com.veruproject.vmusix.data.local.db.DownloadEntity
import com.veruproject.vmusix.data.local.prefs.SettingsDataStore
import com.veruproject.vmusix.data.remote.youtube.InnerTube
import com.veruproject.vmusix.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pengunduh lagu dengan jeda/lanjut/batal/coba-lagi.
 * Target: koleksi publik Music/Vmusix (MediaStore untuk API 29+, file langsung untuk 26–28).
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val tube: InnerTube,
    private val dao: DownloadDao,
    private val settings: SettingsDataStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = ConcurrentHashMap<String, Job>()

    fun observe(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun start(song: Song) = launchFor(song.id) {
        val existing = dao.get(song.id)
        if (existing != null && existing.state == DownloadEntity.STATE_DONE) return@launchFor
        // Baris harus ada sebelum run(); unduhan yang dijeda dilanjutkan dari byte terakhir.
        val row = existing ?: DownloadEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            duration = song.duration,
            thumbnail = song.thumbnail,
        )
        dao.upsert(row)
        run(song.id, if (row.state == DownloadEntity.STATE_PAUSED) row.downloadedBytes else 0)
    }

    fun resume(id: String) = launchFor(id) {
        val row = dao.get(id) ?: return@launchFor
        run(id, row.downloadedBytes)
    }

    fun retry(id: String) = launchFor(id) {
        dao.get(id)?.let { deleteTarget(it) }
        dao.get(id)?.let { dao.upsert(it.copy(downloadedBytes = 0, totalBytes = 0, progress = 0, path = "")) }
        run(id, 0)
    }

    fun pause(id: String) {
        active.remove(id)?.cancel()
        scope.launch {
            dao.get(id)?.let {
                if (it.state == DownloadEntity.STATE_DOWNLOADING) {
                    dao.upsert(it.copy(state = DownloadEntity.STATE_PAUSED))
                }
            }
        }
    }

    fun cancel(id: String) {
        active.remove(id)?.cancel()
        scope.launch {
            deleteTarget(dao.get(id))
            dao.delete(id)
        }
    }

    suspend fun clearCompleted() {
        active.keys.toList().forEach { pause(it) }
        dao.getCompleted().forEach { deleteTarget(it) }
        dao.clear()
    }

    private fun launchFor(id: String, block: suspend () -> Unit) {
        if (active.containsKey(id)) return
        val job = scope.launch { block() }
        active[id] = job
        job.invokeOnCompletion { active.remove(id) }
    }

    // ------------------------------------------------------------------ core

    private suspend fun run(id: String, offsetIn: Long) = withContext(Dispatchers.IO) {
        try {
            var row = dao.get(id) ?: return@withContext
            dao.upsert(row.copy(state = DownloadEntity.STATE_DOWNLOADING))
            val quality = settings.settings.first().downloadQuality
            var offset = offsetIn
            var url = tube.audioUrl(id, quality)
            var target = openTarget(url, row, offset)
            // Simpan path/uri segera: bila gagal di tengah, lanjut bisa memakai target yang sama.
            if (row.path != target.path) {
                row = row.copy(path = target.path)
                dao.upsert(row)
            }

            var response = execute(url, offset)
            // 416/200 tak terduga saat lanjut → file parsial tidak valid, mulai ulang sekali.
            if (offset > 0 && (response.code == 416 || response.code == 200)) {
                response.close()
                target.close()
                deleteTarget(row)
                row = row.copy(downloadedBytes = 0, totalBytes = 0, progress = 0, path = "")
                offset = 0
                target = openTarget(url, row, 0)
                row = row.copy(path = target.path)
                dao.upsert(row)
                response = execute(url, 0)
            }
            if (!response.isSuccessful && response.code != 206) throw IOException("HTTP ${response.code}")

            response.use { resp ->
                val body = resp.body ?: throw IOException("body kosong")
                val declared = body.contentLength()
                val total = if (declared >= 0) offset + declared else row.totalBytes
                var written = offset
                var lastUpdate = 0L
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        target.stream.write(buffer, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) {
                            lastUpdate = now
                            val progress = if (total > 0) ((written * 100) / total).toInt().coerceIn(0, 100) else 0
                            dao.upsert(
                                row.copy(
                                    downloadedBytes = written,
                                    totalBytes = total.coerceAtLeast(0),
                                    progress = progress,
                                    state = DownloadEntity.STATE_DOWNLOADING,
                                ),
                            )
                        }
                    }
                }
                target.stream.flush()
                target.close()
                finalizeTarget(target, row)
                dao.upsert(
                    row.copy(
                        state = DownloadEntity.STATE_DONE,
                        downloadedBytes = written,
                        totalBytes = total.coerceAtLeast(written),
                        progress = 100,
                        path = target.path,
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            dao.get(id)?.let { dao.upsert(it.copy(state = DownloadEntity.STATE_FAILED)) }
        }
    }

    private fun execute(url: String, offset: Long): Response {
        val request = Request.Builder().url(url)
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()
        return client.newCall(request).execute()
    }

    // ----------------------------------------------------------- target tulis

    private class Target(val stream: OutputStream, val uri: Uri?, val path: String) {
        fun close() = runCatching { stream.close() }
    }

    private fun openTarget(url: String, row: DownloadEntity, offset: Long): Target {
        val name = sanitizeFileName(row.title) + "_" + row.id.take(11) + extensionFor(url)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = row.path.takeIf { it.startsWith("content://") }?.let(Uri::parse)
                ?: insertMediaStore(name)
                ?: throw IOException("MediaStore gagal")
            // "wa" = append: posisi = akhir file parsial = offset (bila file tidak diubah).
            val stream = context.contentResolver.openOutputStream(uri, "wa")
                ?: throw IOException("stream MediaStore gagal")
            return Target(stream, uri, uri.toString())
        }
        val file = File(publicDir(), name)
        if (offset > 0 && !file.exists()) throw IOException("file parsial hilang")
        file.parentFile?.mkdirs()
        val stream = FileOutputStream(file, offset > 0)
        return Target(stream, null, file.absolutePath)
    }

    private suspend fun finalizeTarget(target: Target, row: DownloadEntity) {
        if (target.uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            runCatching { context.contentResolver.update(target.uri, values, null, null) }
        } else {
            MediaScannerConnection.scanFile(
                context, arrayOf(target.path), arrayOf("audio/mp4"), null,
            )
        }
    }

    private suspend fun deleteTarget(row: DownloadEntity?) {
        if (row == null) return
        if (row.path.startsWith("content://")) {
            runCatching { context.contentResolver.delete(Uri.parse(row.path), null, null) }
            return
        }
        val file = row.path.takeIf { it.isNotEmpty() }?.let(::File) ?: findFile(row)
        file?.delete()
    }

    private fun findFile(row: DownloadEntity): File? =
        publicDir()?.listFiles()?.firstOrNull { it.name.contains(row.id.take(11)) }

    // ------------------------------------------------------------- utilitas

    private fun insertMediaStore(name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (name.endsWith(".webm") || name.endsWith(".opus")) "audio/webm" else "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Vmusix")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun publicDir(): File? =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            ?.let { File(it, "Vmusix") }

    private fun extensionFor(url: String): String {
        val path = url.substringBefore("?").substringAfterLast("/")
        return when {
            path.endsWith(".webm") -> ".webm"
            path.endsWith(".opus") -> ".opus"
            else -> ".m4a"
        }
    }

    private fun sanitizeFileName(title: String): String =
        title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(60).ifEmpty { "lagu" }
}
