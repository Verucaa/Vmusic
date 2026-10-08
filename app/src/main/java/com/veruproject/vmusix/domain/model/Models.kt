package com.veruproject.vmusix.domain.model

/** Model utama yang dipakai di seluruh layer UI. */

data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val artistId: String = "",
    val album: String = "",
    val albumId: String = "",
    val duration: Long = 0L, // detik
    val thumbnail: String = "",
)

data class Artist(
    val id: String,
    val name: String,
    val thumbnail: String = "",
)

data class Album(
    val id: String,
    val title: String,
    val artist: String = "",
    val artistId: String = "",
    val thumbnail: String = "",
)

data class OnlinePlaylist(
    val id: String,
    val title: String,
    val author: String = "",
    val thumbnail: String = "",
)

/** Satu baris konten di Home (shelf dari YouTube Music). */
data class Shelf(
    val title: String,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<OnlinePlaylist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<OnlinePlaylist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

/** Hasil browse: album / artis / playlist dari YouTube Music. */
data class BrowseDetail(
    val title: String,
    val subtitle: String,
    val thumbnail: String,
    val artistName: String = "",
    val artistId: String = "",
    val songs: List<Song> = emptyList(),
)

/** Jenis layar daftar lagu di rute detail/{kind}/{id}. */
enum class DetailKind { LIKED, CHARTS, PLAYLIST, ALBUM, ARTIST, ONLINE_PLAYLIST }

data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

/** Playlist lokal milik pengguna (Room). */
data class UserPlaylist(val id: Long, val name: String, val songCount: Int, val thumbnail: String)
