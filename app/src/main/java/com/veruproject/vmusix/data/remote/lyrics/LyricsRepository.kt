package com.veruproject.vmusix.data.remote.lyrics

import com.veruproject.vmusix.domain.model.LyricLine
import com.veruproject.vmusix.domain.model.Lyrics
import com.veruproject.vmusix.domain.model.Song
import com.veruproject.vmusix.core.util.parseLrc
import com.veruproject.vmusix.core.util.plainToLyrics
import com.veruproject.vmusix.core.util.textIsSyncedLrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Satu sumber lirik: mengembalikan null bila tidak menemukan / gagal (fallback ke berikutnya). */
fun interface LyricsSource {
    suspend fun fetch(song: Song): Lyrics?
}

/**
 * Rantai sumber lirik: YouTube → LRCLIB → Better Lyrics → KuGou → Paxsenix.
 * Setiap sumber dibungkus try-catch + timeout; kegagalan satu sumber langsung lanjut.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val youtube: com.veruproject.vmusix.data.remote.youtube.InnerTube,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val chain: List<Pair<String, LyricsSource>> = listOf(
        "youtube" to LyricsSource { s -> runCatching { youtube.timedLyrics(s.id) }.getOrNull() },
        "lrclib" to LyricsSource { s -> runCatching { lrclib(s) }.getOrNull() },
        "betterlyrics" to LyricsSource { s -> runCatching { betterLyrics(s) }.getOrNull() },
        "kugou" to LyricsSource { s -> runCatching { kugou(s) }.getOrNull() },
        "paxsenix" to LyricsSource { s -> runCatching { paxsenix(s) }.getOrNull() },
    )

    /** Cari lirik dengan rantai fallback penuh. @param preferred "auto" = semua sumber. */
    suspend fun lyrics(song: Song, preferred: String = "auto"): Lyrics? {
        val sources = if (preferred == "auto") chain else chain.filter { it.first == preferred } + chain
        for ((_, source) in sources) {
            val result = runCatching { source.fetch(song) }.getOrNull()
            if (result != null && result.lines.isNotEmpty()) return result
        }
        return null
    }

    // ------------------------------------------------------------------ LRCLIB

    private suspend fun lrclib(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val base = "https://lrclib.net/api"
        val params = buildList {
            if (song.title.isNotEmpty()) add("track_name=${song.title.urlEncode()}")
            if (song.artist.isNotEmpty()) add("artist_name=${song.artist.urlEncode()}")
            if (song.album.isNotEmpty()) add("album_name=${song.album.urlEncode()}")
            if (song.duration > 0) add("duration=${song.duration}")
        }.joinToString("&")
        val json = get("$base/get?$params")?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (json != null && json.optInt("id") != 0) {
            parseLyricFields(json.optString("syncedLyrics"), json.optString("plainLyrics"))
                ?.let { return@withContext it }
        }
        // Pencarian (bila exact gagal / tidak ada durasi).
        val search = get(
            "$base/search?track_name=${song.title.urlEncode()}&artist_name=${song.artist.urlEncode()}&limit=5",
        ) ?: return@withContext null
        val arr = runCatching { JSONArray(search) }.getOrNull() ?: return@withContext null
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val lyrics = parseLyricFields(item.optString("syncedLyrics"), item.optString("plainLyrics"))
            if (lyrics != null) return@withContext lyrics
        }
        null
    }

    // ----------------------------------------------------------- Better Lyrics

    /** Better Lyrics (unison): lirik sinkron berbasis videoId. */
    private suspend fun betterLyrics(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val url = "https://unison.betterlyrics.org/lyrics?" + buildList {
            add("v=${song.id.urlEncode()}")
            if (song.title.isNotEmpty()) add("song=${song.title.urlEncode()}")
            if (song.artist.isNotEmpty()) add("artist=${song.artist.urlEncode()}")
        }.joinToString("&")
        val body = get(url) ?: return@withContext null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
        if (json.optBoolean("success") == false) return@withContext null
        val lyricsJson = json.optJSONObject("lyrics") ?: return@withContext null
        val synced = lyricsJson.optJSONArray("syncedLyrics")
        if (synced != null && synced.length() > 0) {
            val lines = ArrayList<LyricLine>(synced.length())
            for (i in 0 until synced.length()) {
                val line = synced.optJSONObject(i) ?: continue
                lines += LyricLine(line.optLong("startTimeMs").takeIf { it > 0 }, line.optString("words").trim())
            }
            if (lines.any { it.timeMs != null }) return@withContext Lyrics(lines, synced = true)
        }
        val plain = lyricsJson.optString("plainLyrics")
        if (plain.isNotEmpty()) return@withContext Lyrics(plainToLyrics(plain), synced = false)
        // Bentuk lain: {"lyrics": "teks..."}.
        val text = lyricsJson.optString("lyrics")
        if (text.isNotEmpty()) return@withContext fromText(text)
        null
    }

    // ------------------------------------------------------------------- KuGou

    private suspend fun kugou(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val q = listOfNotNull(song.title, song.artist).joinToString(" ").ifEmpty { song.title }
        val search = get("https://lyrics.paxsenix.org/kugou/search?q=${q.urlEncode()}") ?: return@withContext null
        val json = runCatching { JSONObject(search) }.getOrNull() ?: return@withContext null
        val candidates = json.optJSONArray("candidates") ?: return@withContext null
        for (i in 0 until candidates.length()) {
            val c = candidates.optJSONObject(i) ?: continue
            val id = c.optString("id")
            if (id.isEmpty()) continue
            val acc = c.optString("accesskey")
            val body = get("https://lyrics.paxsenix.org/kugou/lyrics?id=${id.urlEncode()}&word=${acc.urlEncode()}")
                ?: continue
            val detail = runCatching { JSONObject(body) }.getOrNull() ?: continue
            val lyrics = detail.optString("lyrics").ifEmpty { detail.optString("content") }
            if (lyrics.isNotEmpty()) return@withContext fromText(lyrics)
        }
        null
    }

    // ---------------------------------------------------------------- Paxsenix

    private suspend fun paxsenix(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val q = listOfNotNull(song.title, song.artist).joinToString(" ").ifEmpty { song.title }
        val body = get("https://lyrics.paxsenix.org/musixmatch/lyrics?type=search&q=${q.urlEncode()}")
            ?: return@withContext null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
        val lyrics = json.optString("lyrics").ifEmpty { json.optString("plainLyrics") }
        if (lyrics.isNotEmpty()) return@withContext fromText(lyrics)
        null
    }

    // -------------------------------------------------------------- util

    private fun fromText(text: String): Lyrics? = if (textIsSyncedLrc(text)) {
        parseLrc(text).takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = true) }
    } else {
        plainToLyrics(text).takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = false) }
    }

    private fun parseLyricFields(synced: String, plain: String): Lyrics? = when {
        synced.isNotEmpty() -> fromText(synced)
        plain.isNotEmpty() -> Lyrics(plainToLyrics(plain), synced = false)
        else -> null
    }

    private suspend fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()?.takeIf { it.isNotBlank() }
        }
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

    companion object {
        private const val UA = "Vmusix/1.0 (https://github.com/VeruProject/Vmusix)"
    }
}
