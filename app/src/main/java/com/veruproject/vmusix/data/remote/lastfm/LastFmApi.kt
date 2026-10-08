package com.veruproject.vmusix.data.remote.lastfm

import com.veruproject.vmusix.BuildConfig
import com.veruproject.vmusix.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Klien Last.fm (scrobbling opsional).
 * API key/secret diisi lewat local.properties: LASTFM_API_KEY / LASTFM_API_SECRET.
 * Tanpa kunci, semua fungsi no-op.
 */
@Singleton
class LastFmApi @Inject constructor(private val http: OkHttpClient) {

    private val apiKey: String = BuildConfig.LASTFM_API_KEY
    // ponytail: secret disimpan sebagai konstanta di build; aman karena hanya untuk API publik Last.fm.
    private val apiSecret: String = BuildConfig.LASTFM_API_SECRET

    val isConfigured: Boolean get() = apiKey.isNotEmpty() && apiSecret.isNotEmpty()

    suspend fun getToken(): String? = call(
        mapOf("method" to "auth.getToken"),
    )?.optString("token")?.takeIf { it.isNotEmpty() }

    suspend fun getSession(token: String): Pair<String, String>? {
        val json = call(mapOf("method" to "auth.getSession", "token" to token)) ?: return null
        val session = json.optJSONObject("session") ?: return null
        val key = session.optString("key")
        val user = session.optString("name")
        return if (key.isNotEmpty()) key to user else null
    }

    /** Kirim now-playing (dipanggil saat lagu mulai). */
    suspend fun updateNowPlaying(song: Song, sessionKey: String): Boolean {
        if (!isConfigured || song.title.isEmpty()) return false
        val ok = post(
            baseParams(
                "track.updateNowPlaying",
                sessionKey,
                "artist" to song.artist,
                "track" to song.title,
                "album" to (song.album.takeIf { it.isNotEmpty() } ?: ""),
                "duration" to song.duration.toString(),
            ),
        )
        return ok
    }

    /** Scrobble satu lagu (timestamp = waktu mulai diputar). */
    suspend fun scrobble(song: Song, sessionKey: String, timestampSec: Long): Boolean {
        if (!isConfigured || song.title.isEmpty() || song.artist.isEmpty()) return false
        return post(
            baseParams(
                "track.scrobble",
                sessionKey,
                "artist" to song.artist,
                "track" to song.title,
                "album" to song.album.takeIf { it.isNotEmpty() } ?: "",
                "duration" to song.duration.toString(),
                "timestamp" to timestampSec.toString(),
                "chosen" to "0",
            ),
        )
    }

    // ------------------------------------------------------------------ core

    private fun baseParams(method: String, sessionKey: String, vararg extra: Pair<String, String>): Map<String, String> =
        buildMap {
            put("method", method)
            put("api_key", apiKey)
            put("sk", sessionKey)
            extra.forEach { (k, v) -> if (v.isNotEmpty()) put(k, v) }
        }

    /** GET + api_sig. true bila sukses (status "ok"). */
    private suspend fun call(params: Map<String, String>): JSONObject? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val all = params + ("format" to "json")
        val url = BASE + "?" + all.entries.joinToString("&") { (k, v) -> "${k.urlEncode()}=${v.urlEncode()}" } +
            "&api_sig=${apiSig(all)}"
        val request = Request.Builder().url(url).build()
        runCatching {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@runCatching null
                if (!response.isSuccessful) return@runCatching null
                val json = JSONObject(body)
                if (json.optString("error").isNotEmpty()) null else json
            }
        }.getOrNull()
    }

    private suspend fun post(params: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext false
        val all = params + ("format" to "json")
        val body = FormBody.Builder().apply {
            all.forEach { (k, v) -> add(k, v) }
            add("api_sig", apiSig(all))
        }.build()
        val request = Request.Builder().url(BASE).post(body).build()
        runCatching {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: return@runCatching false
                response.isSuccessful && !JSONObject(text).has("error")
            }
        }.getOrDefault(false)
    }

    private fun apiSig(params: Map<String, String>): String {
        val sorted = params.entries.sortedBy { it.key }.joinToString("") { (k, v) -> k + v }
        val digest = MessageDigest.getInstance("MD5").digest((sorted + apiSecret).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

    companion object {
        private const val BASE = "https://ws.audioscrobbler.com/2.0/"
        val AUTH_URL: String = "https://www.last.fm/api/auth"
    }
}
