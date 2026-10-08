package com.veruproject.vmusix.data.remote.youtube

import com.veruproject.vmusix.core.util.parseLrc
import com.veruproject.vmusix.core.util.plainToLyrics
import com.veruproject.vmusix.core.util.textIsSyncedLrc
import com.veruproject.vmusix.domain.model.Album
import com.veruproject.vmusix.domain.model.Artist
import com.veruproject.vmusix.domain.model.BrowseDetail
import com.veruproject.vmusix.domain.model.Lyrics
import com.veruproject.vmusix.domain.model.OnlinePlaylist
import com.veruproject.vmusix.domain.model.SearchResults
import com.veruproject.vmusix.domain.model.Shelf
import com.veruproject.vmusix.domain.model.Song
import org.json.JSONArray
import org.json.JSONObject

/** Parser JSON InnerTube → model domain (aman terhadap field yang hilang). */
object YoutubeParser {

    enum class Kind { ALBUM, ARTIST, PLAYLIST }

    private enum class RowKind { SONG, ALBUM, ARTIST, PLAYLIST, UNKNOWN }

    private data class Row(
        val kind: RowKind,
        val id: String,
        val title: String,
        val artist: String = "",
        val artistId: String = "",
        val album: String = "",
        val albumId: String = "",
        val duration: Long = 0L,
        val thumb: String = "",
    )

    private val timeRegex = Regex("""(?<!\d)(\d{1,3}):(\d{2})(?::(\d{2}))?(?!\d)""")

    // ---------------------------------------------------------------- search

    fun parseSearch(json: JSONObject): SearchResults {
        val sections = json.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")?.sectionAt(0)
            ?.optJSONObject("tabRenderer")?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents") ?: return SearchResults()

        val songs = LinkedHashMap<String, Song>()
        val albums = LinkedHashMap<String, Album>()
        val artists = LinkedHashMap<String, Artist>()
        val playlists = LinkedHashMap<String, OnlinePlaylist>()

        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: continue
            when {
                section.has("musicCardShelfRenderer") -> {
                    val card = section.optJSONObject("musicCardShelfRenderer") ?: continue
                    // Hasil utama: judul dari title, jenis konten dari subtitle ("Artist • 55.6M…").
                    val title = runsText(card.optJSONObject("title"))
                    val endpoint = endpointOfRuns(card.optJSONObject("title"))
                    val watch = endpoint?.optJSONObject("watchEndpoint")
                    val browse = endpoint?.optJSONObject("browseEndpoint")
                    val id = watch?.optString("videoId")?.ifEmpty { null }
                        ?: browse?.optString("browseId") ?: ""
                    val thumb = bestThumb(card)
                    when (typeFromRuns(card.optJSONObject("subtitle"))) {
                        RowKind.ARTIST -> if (id.startsWith("UC")) artists[id] = Artist(id, title, thumb)
                        RowKind.ALBUM -> if (id.startsWith("MPRE")) albums[id] = Album(id, title, "", "", thumb)
                        RowKind.PLAYLIST -> if (id.isNotEmpty()) playlists[id] = OnlinePlaylist(id, title, "", thumb)
                        else -> if (id.isNotEmpty()) songs[id] = Song(id, title, thumbnail = thumb)
                    }
                    // Konten kartu = daftar lagu terkait.
                    val related = card.optJSONArray("contents") ?: JSONArray()
                    for (j in 0 until related.length()) {
                        val item = related.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val r = rowFromResponsive(item, RowKind.SONG)
                        if (r.kind == RowKind.SONG && r.id.isNotEmpty()) songs[r.id] = r.toSong()
                    }
                }
                section.has("musicShelfRenderer") -> {
                    val items = section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents") ?: JSONArray()
                    collectResponsiveItems(items, songs, albums, artists, playlists)
                }
                section.has("itemSectionRenderer") -> {
                    val items = section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: JSONArray()
                    collectResponsiveItems(items, songs, albums, artists, playlists)
                }
            }
        }
        return SearchResults(
            songs = songs.values.toList(),
            albums = albums.values.toList(),
            artists = artists.values.toList(),
            playlists = playlists.values.toList(),
        )
    }

    private fun collectResponsiveItems(
        items: JSONArray,
        songs: MutableMap<String, Song>,
        albums: MutableMap<String, Album>,
        artists: MutableMap<String, Artist>,
        playlists: MutableMap<String, OnlinePlaylist>,
    ) {
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val renderer = item.optJSONObject("musicResponsiveListItemRenderer")
                ?: item.optJSONObject("musicTwoRowItemRenderer")
                ?: continue
            val row = if (item.has("musicTwoRowItemRenderer")) rowFromTwoRow(renderer) else rowFromResponsive(renderer, RowKind.UNKNOWN)
            when (row.kind) {
                RowKind.SONG -> if (row.id.isNotEmpty()) songs[row.id] = row.toSong()
                RowKind.ALBUM -> if (row.id.startsWith("MPRE")) albums[row.id] = Album(row.id, row.title, row.artist, row.artistId, row.thumb)
                RowKind.ARTIST -> if (row.id.startsWith("UC")) artists[row.id] = Artist(row.id, row.title, row.thumb)
                RowKind.PLAYLIST -> if (row.id.isNotEmpty()) playlists[row.id] = OnlinePlaylist(row.id, row.title, row.artist, row.thumb)
                RowKind.UNKNOWN -> Unit
            }
        }
    }

    // ------------------------------------------------------------ beranda/dll

    /** Shelf carousel (beranda, explore, charts, header artis). */
    fun parseShelves(json: JSONObject): List<Shelf> {
        val sections = findSectionList(json) ?: return emptyList()
        val shelves = mutableListOf<Shelf>()
        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: continue
            val carousel = section.optJSONObject("musicCarouselShelfRenderer")
            val plain = section.optJSONObject("musicShelfRenderer")
            val renderer = carousel ?: plain ?: continue
            val title = when {
                carousel != null -> runsText(
                    carousel.optJSONObject("header")
                        ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.optJSONObject("title"),
                )
                plain != null -> runsText(plain.optJSONObject("title"))
                else -> ""
            }
            val items = renderer.optJSONArray("contents") ?: JSONArray()
            val songs = LinkedHashMap<String, Song>()
            val albums = LinkedHashMap<String, Album>()
            val artists = LinkedHashMap<String, Artist>()
            val playlists = LinkedHashMap<String, OnlinePlaylist>()
            for (j in 0 until items.length()) {
                val entry = items.optJSONObject(j) ?: continue
                val item = entry.optJSONObject("musicTwoRowItemRenderer")
                    ?: entry.optJSONObject("musicResponsiveListItemRenderer")
                    ?: continue
                val row = if (entry.has("musicTwoRowItemRenderer")) rowFromTwoRow(item) else rowFromResponsive(item, RowKind.UNKNOWN)
                when (row.kind) {
                    RowKind.SONG -> if (row.id.isNotEmpty()) songs[row.id] = row.toSong()
                    RowKind.ALBUM -> if (row.id.startsWith("MPRE")) albums[row.id] = Album(row.id, row.title, row.artist, row.artistId, row.thumb)
                    RowKind.ARTIST -> if (row.id.startsWith("UC")) artists[row.id] = Artist(row.id, row.title, row.thumb)
                    RowKind.PLAYLIST -> if (row.id.isNotEmpty()) playlists[row.id] = OnlinePlaylist(row.id, row.title, row.artist, row.thumb)
                    RowKind.UNKNOWN -> Unit
                }
            }
            val shelf = Shelf(title, songs.values.toList(), albums.values.toList(), artists.values.toList(), playlists.values.toList())
            if (!shelf.isEmpty) shelves += shelf
        }
        return shelves
    }

    /** Kumpulkan semua lagu yang bisa diputar dari respons browse mana pun. */
    fun parseSongs(json: JSONObject): List<Song> {
        val songs = LinkedHashMap<String, Song>()
        walk(json) { obj ->
            val responsive = obj.optJSONObject("musicResponsiveListItemRenderer")
            if (responsive != null) {
                val row = rowFromResponsive(responsive, RowKind.SONG)
                if (row.kind == RowKind.SONG && row.id.isNotEmpty()) songs[row.id] = row.toSong()
            }
            val twoRow = obj.optJSONObject("musicTwoRowItemRenderer")
            if (twoRow != null) {
                val row = rowFromTwoRow(twoRow)
                if (row.kind == RowKind.SONG && row.id.isNotEmpty()) songs[row.id] = row.toSong()
            }
        }
        return songs.values.toList()
    }

    // ----------------------------------------------------------- detail browse

    fun parseDetail(json: JSONObject, kind: Kind, id: String): BrowseDetail {
        val twoCol = json.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")
        if (twoCol != null && kind != Kind.ARTIST) {
            val sectionList = twoCol.optJSONArray("tabs")?.sectionAt(0)
                ?.optJSONObject("tabRenderer")?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
            val sections = sectionList?.optJSONArray("contents") ?: JSONArray()
            val header = sections.optJSONObject(0)?.optJSONObject("musicResponsiveHeaderRenderer")
            val title = runsText(header?.optJSONObject("title"))
            val subtitle = runsText(header?.optJSONObject("subtitle"))
            val thumb = bestThumb(header)
            // Deskripsi mikroformat: "Album • Tulus" / "Playlist • nama pembuat".
            val description = json.optJSONObject("microformat")
                ?.optJSONObject("microformatDataRenderer")?.optString("description") ?: ""
            val author = description.substringAfter("•", "").trim()
            val shelf = twoCol.optJSONObject("secondaryContents")?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")?.sectionAt(0)
                ?.let { it.optJSONObject("musicShelfRenderer") ?: it.optJSONObject("musicPlaylistShelfRenderer") }
                ?.optJSONArray("contents")
                ?: JSONArray()
            val songs = ArrayList<Song>()
            for (i in 0 until shelf.length()) {
                val item = shelf.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val row = rowFromResponsive(item, RowKind.SONG)
                if (row.id.isNotEmpty()) songs += row.copy(artist = row.artist.ifEmpty { author }).toSong()
            }
            val artistName = author.ifEmpty { subtitle.substringAfterLast("•").trim() }
            return BrowseDetail(
                title = title.ifEmpty { subtitle },
                subtitle = if (kind == Kind.PLAYLIST && author.isNotEmpty()) author else subtitle,
                thumbnail = thumb,
                artistName = artistName,
                artistId = songs.firstOrNull()?.artistId ?: "",
                songs = songs,
            )
        }

        // Halaman artis memakai header tunggal di root respons.
        val immersive = json.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
        val title = runsText(immersive?.optJSONObject("title"))
        val thumb = bestThumb(immersive)
        val topSongs = json.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")?.sectionAt(0)
            ?.optJSONObject("tabRenderer")?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            ?.let { sections ->
                for (i in 0 until sections.length()) {
                    val shelf = sections.optJSONObject(i)?.optJSONObject("musicShelfRenderer") ?: continue
                    val items = shelf.optJSONArray("contents") ?: JSONArray()
                    val songs = ArrayList<Song>()
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val row = rowFromResponsive(item, RowKind.SONG)
                        if (row.id.isNotEmpty()) {
                            songs += row.copy(artist = row.artist.ifEmpty { title }, artistId = row.artistId.ifEmpty { id }).toSong()
                        }
                    }
                    if (songs.isNotEmpty()) return@let songs
                }
                emptyList<Song>()
            } ?: emptyList()
        return BrowseDetail(
            title = title,
            subtitle = "Artis",
            thumbnail = thumb,
            artistName = title,
            artistId = id,
            songs = topSongs,
        )
    }

    // ---------------------------------------------------------------- player

    fun parseAudioUrl(json: JSONObject, quality: String): String? {
        val streaming = json.optJSONObject("streamingData") ?: return null
        val candidates = ArrayList<Triple<String, Int, String>>() // url, bitrate, mime
        for (key in arrayOf("adaptiveFormats", "formats")) {
            val arr = streaming.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val url = f.optString("url")
                val mime = f.optString("mimeType")
                if (url.isEmpty() || !mime.startsWith("audio")) continue
                candidates += Triple(url, f.optInt("bitrate"), mime)
            }
        }
        if (candidates.isEmpty()) return null
        val max = candidates.maxOf { it.second }
        val min = candidates.minOf { it.second }
        // ponytail: filter bitrate kasar per profil; presisi bitrate bukan masalah streaming musik.
        val limit = when (quality) {
            "low" -> max / 3
            "medium" -> max / 2
            else -> max
        }
        val pool = candidates.filter { it.second <= limit }.ifEmpty { candidates }
        val best = pool.maxByOrNull { it.second } ?: return null
        // Utamakan opus bila kualitas sama (rasio kualitas/ukuran lebih baik).
        val opus = pool.filter { it.second == best.second && it.third.contains("opus") }
        return (opus.firstOrNull() ?: best).first
    }

    fun findLyricsBrowseId(next: JSONObject): String? {
        var found: String? = null
        walk(next) { obj ->
            if (found != null) return@walk
            val endpoint = obj.optJSONObject("browseEndpoint") ?: return@walk
            val pageType = endpoint.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")
            if (pageType == "MUSIC_PAGE_TYPE_TRACK_LYRICS") found = endpoint.optString("browseId").ifEmpty { null }
        }
        if (found == null) {
            // Fallback: tab berjudul "Lyrics".
            val tabs = next.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")?.optJSONArray("tabs") ?: return null
            for (i in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                if (tab.optString("title").equals("Lyrics", ignoreCase = true)) {
                    found = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")?.ifEmpty { null }
                }
            }
        }
        return found
    }

    fun parseLyrics(browse: JSONObject): Lyrics? {
        val desc = browse.optJSONObject("contents")
            ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            ?.sectionAt(0)?.optJSONObject("musicDescriptionShelfRenderer")
            ?.optJSONObject("description") ?: return null
        val text = runsText(desc)
        if (text.isBlank()) return null
        return if (textIsSyncedLrc(text)) {
            val lines = parseLrc(text)
            if (lines.isEmpty()) null else Lyrics(lines, synced = true)
        } else {
            val lines = plainToLyrics(text)
            if (lines.isEmpty()) null else Lyrics(lines, synced = false)
        }
    }

    // ------------------------------------------------------------- primitives

    private fun rowFromResponsive(item: JSONObject, fallback: RowKind): Row {
        val columns = item.optJSONArray("flexColumns") ?: JSONArray()
        fun colText(index: Int): List<Pair<String, JSONObject?>> {
            val col = columns.optJSONObject(index) ?: return emptyList()
            val runs = col.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs") ?: return emptyList()
            return (0 until runs.length()).mapNotNull { i ->
                val r = runs.optJSONObject(i) ?: return@mapNotNull null
                r.optString("text") to r.optJSONObject("navigationEndpoint")
            }
        }
        val titleRuns = colText(0)
        val title = titleRuns.joinToString("") { it.first }.trim()
        var id = ""
        var kind = fallback
        for ((_, endpoint) in titleRuns) {
            val watch = endpoint?.optJSONObject("watchEndpoint")
            if (watch != null) {
                id = watch.optString("videoId")
                if (id.isNotEmpty()) kind = RowKind.SONG
                break
            }
            val browse = endpoint?.optJSONObject("browseEndpoint")
            if (browse != null) {
                val browseId = browse.optString("browseId")
                if (browseId.isNotEmpty()) {
                    id = browseId
                    kind = kindFromPageType(browse.pageType()) ?: when {
                        browseId.startsWith("MPRE") -> RowKind.ALBUM
                        browseId.startsWith("UC") -> RowKind.ARTIST
                        browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RD") -> RowKind.PLAYLIST
                        else -> kind
                    }
                }
            }
        }
        // Kolom berikutnya: jenis konten + artis/album.
        var artist = ""
        var artistId = ""
        var album = ""
        var albumId = ""
        var duration = 0L
        for (ci in 1 until columns.length()) {
            val runs = colText(ci)
            for ((text, endpoint) in runs) {
                val trimmed = text.trim()
                if (trimmed.isEmpty() || trimmed == "•") continue
                val browseId = endpoint?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""
                val pageType = endpoint?.optJSONObject("browseEndpoint")?.pageType() ?: ""
                val t = trimmed.lowercase()
                when {
                    t in setOf("song", "video", "single", "album", "artist", "playlist", "episode", "podcast", "profile", "film") -> {
                        kind = when (t) {
                            "song", "video", "single", "film" -> RowKind.SONG
                            "album" -> RowKind.ALBUM
                            "artist" -> RowKind.ARTIST
                            "playlist" -> RowKind.PLAYLIST
                            else -> kind
                        }
                        if (t in setOf("single", "film", "video")) kind = RowKind.SONG
                    }
                    browseId.startsWith("UC") && artistId.isEmpty() -> {
                        artist = trimmed; artistId = browseId
                    }
                    pageType == "MUSIC_PAGE_TYPE_ALBUM" && albumId.isEmpty() -> {
                        album = trimmed; albumId = browseId
                    }
                    browseId.startsWith("MPRE") && albumId.isEmpty() -> {
                        album = trimmed; albumId = browseId
                    }
                    timeRegex.containsMatchIn(trimmed) && duration == 0L -> {
                        duration = parseDuration(trimmed)
                    }
                    else -> if (artist.isEmpty() && artistId.isEmpty()) artist = trimmed
                }
            }
        }
        // Kolom fixed (durasi) pada daftar album/playlist.
        if (duration == 0L) {
            val fixed = item.optJSONArray("fixedColumns")
            if (fixed != null) {
                for (i in 0 until fixed.length()) {
                    val text = runsText(
                        fixed.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                            ?.optJSONObject("text"),
                    )
                    if (timeRegex.containsMatchIn(text)) {
                        duration = parseDuration(text)
                        break
                    }
                }
            }
        }
        if (kind == RowKind.UNKNOWN && id.startsWith("MPRE")) kind = RowKind.ALBUM
        if (kind == RowKind.UNKNOWN && id.startsWith("UC")) kind = RowKind.ARTIST
        return Row(kind, id, title, artist, artistId, album, albumId, duration, bestThumb(item))
    }

    private fun rowFromTwoRow(item: JSONObject): Row {
        val title = runsText(item.optJSONObject("title"))
        val endpoint = item.optJSONObject("navigationEndpoint")
            ?: endpointOfRuns(item.optJSONObject("title"))
        val watch = endpoint?.optJSONObject("watchEndpoint")
        val browse = endpoint?.optJSONObject("browseEndpoint")
        val id = when {
            watch != null -> watch.optString("videoId")
            browse != null -> browse.optString("browseId")
            else -> ""
        }
        val kind = when {
            watch != null -> RowKind.SONG
            browse != null -> when (browse.pageType()) {
                "MUSIC_PAGE_TYPE_ALBUM" -> RowKind.ALBUM
                "MUSIC_PAGE_TYPE_ARTIST" -> RowKind.ARTIST
                "MUSIC_PAGE_TYPE_PLAYLIST" -> RowKind.PLAYLIST
                else -> when {
                    id.startsWith("MPRE") -> RowKind.ALBUM
                    id.startsWith("UC") -> RowKind.ARTIST
                    else -> RowKind.PLAYLIST
                }
            }
            else -> RowKind.UNKNOWN
        }
        val subtitleRuns = runsParts(item.optJSONObject("subtitle"))
        val artist = subtitleRuns.firstOrNull { it != "•" && it.length > 1 } ?: ""
        return Row(kind, id, title, artist = artist, thumb = bestThumb(item))
    }

    private fun Row.toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        duration = duration,
        thumbnail = thumb,
    )

    private fun typeFromRuns(subtitle: JSONObject?): RowKind {
        for (part in runsParts(subtitle)) {
            when (part.trim().lowercase()) {
                "song", "video", "single", "film" -> return RowKind.SONG
                "album" -> return RowKind.ALBUM
                "artist" -> return RowKind.ARTIST
                "playlist" -> return RowKind.PLAYLIST
            }
        }
        return RowKind.UNKNOWN
    }

    private fun kindFromPageType(pageType: String): RowKind? = when (pageType) {
        "MUSIC_PAGE_TYPE_ALBUM" -> RowKind.ALBUM
        "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_CHANNEL" -> RowKind.ARTIST
        "MUSIC_PAGE_TYPE_PLAYLIST" -> RowKind.PLAYLIST
        "MUSIC_PAGE_TYPE_AUDIO_OR_VIDEO" -> RowKind.SONG
        else -> null
    }

    private fun JSONObject.pageType(): String =
        optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType") ?: ""

    private fun parseDuration(text: String): Long {
        val m = timeRegex.find(text) ?: return 0L
        val parts = m.groupValues
        return if (parts[3].isNotEmpty()) {
            (parts[1].toLongOrNull() ?: 0L) * 3600 + (parts[2].toLongOrNull() ?: 0L) * 60 + (parts[3].toLongOrNull() ?: 0L)
        } else {
            (parts[1].toLongOrNull() ?: 0L) * 60 + (parts[2].toLongOrNull() ?: 0L)
        }
    }

    private fun runsParts(obj: JSONObject?): List<String> {
        val runs = obj?.optJSONArray("runs") ?: return emptyList()
        return (0 until runs.length()).mapNotNull { runs.optJSONObject(it)?.optString("text") }
    }

    private fun runsText(obj: JSONObject?): String = runsParts(obj).joinToString("") { it }.trim()

    private fun endpointOfRuns(obj: JSONObject?): JSONObject? {
        val runs = obj?.optJSONArray("runs") ?: return null
        for (i in 0 until runs.length()) {
            val ep = runs.optJSONObject(i)?.optJSONObject("navigationEndpoint")
            if (ep != null) return ep
        }
        return null
    }

    private fun bestThumb(obj: JSONObject?): String {
        if (obj == null) return ""
        val container = obj.optJSONObject("musicThumbnailRenderer")
            ?: obj.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")
            ?: obj.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
            ?: return ""
        val thumbs = container.optJSONObject("thumbnail")?.optJSONArray("thumbnails") ?: return ""
        // Ukuran terakhir = kualitas tertinggi yang tersedia.
        return thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
    }

    private fun findSectionList(json: JSONObject): JSONArray? {
        var result: JSONArray? = null
        walk(json) { obj ->
            if (result == null && obj.has("sectionListRenderer")) {
                result = obj.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            }
        }
        return result
    }

    /** DFS sederhana: panggil block pada setiap objek JSON yang punya kunci renderer umum. */
    private fun walk(node: Any?, block: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                block(node)
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), block)
            }
            is JSONArray -> {
                for (i in 0 until node.length()) walk(node.opt(i), block)
            }
        }
    }

    private fun JSONArray.sectionAt(index: Int): JSONObject? =
        if (index in 0 until length()) optJSONObject(index) else null
}
