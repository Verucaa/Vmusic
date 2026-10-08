package com.veruproject.vmusix.data.remote.youtube

import com.veruproject.vmusix.domain.model.BrowseDetail
import com.veruproject.vmusix.domain.model.Lyrics
import com.veruproject.vmusix.domain.model.SearchResults
import com.veruproject.vmusix.domain.model.Shelf
import com.veruproject.vmusix.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Klien InnerTube (API resmi YouTube Music).
 * Request memakai konteks WEB_REMIX untuk konten dan ANDROID_VR untuk stream audio
 * (karena konteks itu mengembalikan URL audio langsung tanpa sandi tanda tangan).
 */
@Singleton
class InnerTube @Inject constructor(private val http: OkHttpClient) {

    suspend fun search(query: String): SearchResults = io {
        YoutubeParser.parseSearch(
            post(
                "search",
                JSONObject()
                    .put("context", webContext())
                    .put("query", query),
            ),
        )
    }

    /** Shelf editorial di beranda YouTube Music (playlist, album, artis). */
    suspend fun home(): List<Shelf> = io {
        YoutubeParser.parseShelves(post("browse", browseBody("FEmusic_home")))
    }

    /** Halaman explore: Trending, Rilis Baru, Moods & Genres, Video Terbaru. */
    suspend fun explore(): List<Shelf> = io {
        YoutubeParser.parseShelves(post("browse", browseBody("FEmusic_explore")))
    }

    /** 50 teratas. Fallback ke shelf Trending bila charts tidak menyediakan lagu. */
    suspend fun charts(): List<Song> = io {
        val fromCharts = YoutubeParser.parseSongs(post("browse", browseBody("FEmusic_charts")))
        val songs = fromCharts.ifEmpty {
            YoutubeParser.parseSongs(post("browse", browseBody("FEmusic_explore")))
        }
        songs.distinctBy { it.id }.take(50)
    }

    suspend fun album(id: String): BrowseDetail = io {
        YoutubeParser.parseDetail(post("browse", browseBody(id)), YoutubeParser.Kind.ALBUM, id)
    }

    suspend fun artist(id: String): BrowseDetail = io {
        YoutubeParser.parseDetail(post("browse", browseBody(id)), YoutubeParser.Kind.ARTIST, id)
    }

    suspend fun playlist(id: String): BrowseDetail = io {
        YoutubeParser.parseDetail(post("browse", browseBody(id)), YoutubeParser.Kind.PLAYLIST, id)
    }

    /**
     * Ambil URL audio untuk satu lagu.
     * @param quality auto|high|medium|low — membatasi bitrate yang dipilih.
     */
    suspend fun audioUrl(videoId: String, quality: String = "auto"): String = io {
        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "ANDROID_VR")
                        .put("clientVersion", "1.60.19")
                        .put("androidSdkVersion", 34)
                        .put("hl", "en")
                        .put("gl", "US"),
                ),
            )
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val json = post("player", body, androidPlayerHeaders)
        YoutubeParser.parseAudioUrl(json, quality)
            ?: throw IOException("URL audio tidak tersedia untuk lagu ini")
    }

    /** Lirik dari YouTube Music (opsional: biasanya lirik biasa, bukan timed). */
    suspend fun timedLyrics(videoId: String): Lyrics? = io {
        val next = post("next", JSONObject().put("context", webContext()).put("videoId", videoId))
        val browseId = YoutubeParser.findLyricsBrowseId(next) ?: return@io null
        YoutubeParser.parseLyrics(post("browse", browseBody(browseId)))
    }

    // ------------------------------------------------------------------

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun post(endpoint: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject {
        val builder = Request.Builder()
            .url("$BASE/$endpoint")
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_USER_AGENT)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.post(body.toString().toRequestBody(JSON_TYPE)).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string()
            if (!response.isSuccessful || text.isNullOrEmpty()) {
                throw IOException("HTTP ${response.code} dari $endpoint")
            }
            return JSONObject(text)
        }
    }

    private fun webContext(): JSONObject = JSONObject().put(
        "client",
        JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20241001.01.00")
            .put("hl", "en")
            .put("gl", "US"),
    )

    private fun browseBody(browseId: String): JSONObject =
        JSONObject().put("context", webContext()).put("browseId", browseId)

    companion object {
        const val BASE = "https://www.youtube.com/youtubei/v1"
        private val JSON_TYPE = "application/json".toMediaType()
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        private val androidPlayerHeaders = mapOf(
            "User-Agent" to "com.google.android.apps.youtube.vr/1.60.19 (Linux; U; Android 14) gzip",
            "X-Youtube-Client-Name" to "28",
            "X-Youtube-Client-Version" to "1.60.19",
        )

        /** Client OkHttp khusus untuk InnerTube (dipakai juga oleh DI module). */
        fun newClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
