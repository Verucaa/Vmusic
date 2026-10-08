package com.veruproject.vmusix.data.repo

import com.veruproject.vmusix.data.local.db.HistoryDao
import com.veruproject.vmusix.data.local.db.LikedDao
import com.veruproject.vmusix.data.local.db.LikedSongEntity
import com.veruproject.vmusix.data.local.db.PlayHistoryEntity
import com.veruproject.vmusix.data.local.db.PlaylistDao
import com.veruproject.vmusix.data.local.db.PlaylistEntity
import com.veruproject.vmusix.data.local.db.PlaylistSongEntity
import com.veruproject.vmusix.data.local.prefs.SettingsDataStore
import com.veruproject.vmusix.data.remote.lyrics.LyricsRepository
import com.veruproject.vmusix.data.remote.youtube.InnerTube
import com.veruproject.vmusix.domain.model.BrowseDetail
import com.veruproject.vmusix.domain.model.DetailKind
import com.veruproject.vmusix.domain.model.Lyrics
import com.veruproject.vmusix.domain.model.SearchResults
import com.veruproject.vmusix.domain.model.Shelf
import com.veruproject.vmusix.domain.model.Song
import com.veruproject.vmusix.domain.model.UserPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Satu pintu data: YouTube (InnerTube), lirik, dan penyimpanan lokal (Room).
 */
@Singleton
class MusicRepository @Inject constructor(
    private val tube: InnerTube,
    private val lyricsRepo: LyricsRepository,
    private val likedDao: LikedDao,
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao,
    private val settings: SettingsDataStore,
) {
    // URL audio di-cache per video (2 menit) supaya lagu berikutnya tidak menunggu request ulang.
    private val audioCache = LinkedHashMap<String, Pair<String, Long>>()
    private val audioLock = Semaphore(3) // ponytail: batasi request player paralel

    // ------------------------------------------------------------- remote

    suspend fun search(query: String): SearchResults = tube.search(query)
    suspend fun home(): List<Shelf> = tube.home()
    suspend fun explore(): List<Shelf> = tube.explore()
    suspend fun charts(): List<Song> = tube.charts()

    suspend fun detail(kind: DetailKind, id: String): BrowseDetail = when (kind) {
        DetailKind.ALBUM -> tube.album(id)
        DetailKind.ARTIST -> tube.artist(id)
        DetailKind.ONLINE_PLAYLIST -> tube.playlist(id)
        else -> BrowseDetail(title = id, subtitle = "", thumbnail = "")
    }

    /** URL audio dengan cache singkat + batas request paralel. */
    suspend fun audioUrl(videoId: String): String {
        val quality = settings.settings.first().audioQuality
        synchronized(audioCache) {
            audioCache[videoId]?.let { (url, expires) ->
                if (expires > System.currentTimeMillis()) return url
            }
        }
        val url = audioLock.withPermit { tube.audioUrl(videoId, quality) }
        synchronized(audioCache) {
            audioCache[videoId] = url to (System.currentTimeMillis() + 2 * 60_000)
            if (audioCache.size > 60) audioCache.remove(audioCache.keys.first())
        }
        return url
    }

    suspend fun lyrics(song: Song): Lyrics? {
        val preferred = settings.settings.first().lyricsSource
        return lyricsRepo.lyrics(song, preferred)
    }

    // -------------------------------------------------------------- liked

    fun observeLiked(): Flow<List<Song>> = likedDao.observeAll().map { list -> list.map { it.toSong() } }

    fun observeIsLiked(id: String): Flow<Boolean> = likedDao.observeIsLiked(id)

    suspend fun toggleLike(song: Song) {
        if (likedDao.observeIsLiked(song.id).first()) likedDao.delete(song.id)
        else likedDao.insert(song.toLiked())
    }

    // ------------------------------------------------------------ history

    fun observeRecent(limit: Int = 30): Flow<List<Song>> =
        historyDao.observeRecent(limit).map { list -> list.map { it.toSong() } }

    suspend fun recordPlay(song: Song) = historyDao.insert(song.toHistory())

    // ----------------------------------------------------------- playlists

    fun observePlaylists(): Flow<List<UserPlaylist>> = playlistDao.observePlaylistsWithMeta().map { rows ->
        rows.map { UserPlaylist(it.id, it.name, it.cnt, it.thumb) }
    }

    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>> =
        playlistDao.observeSongs(playlistId).map { list -> list.map { it.toSong() } }

    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name))

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deleteSongs(playlistId)
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addToPlaylist(playlistId: Long, song: Song) {
        if (playlistDao.containsSong(playlistId, song.id)) return
        playlistDao.insertSong(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id,
                title = song.title,
                artist = song.artist,
                artistId = song.artistId,
                album = song.album,
                albumId = song.albumId,
                duration = song.duration,
                thumbnail = song.thumbnail,
            ),
        )
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) =
        playlistDao.deleteSongRow(playlistId, songId)

    suspend fun getPlaylistSongs(playlistId: Long): List<Song> =
        playlistDao.getSongs(playlistId).map { it.toSong() }

    // --------------------------------------------------------- konversi

    private fun LikedSongEntity.toSong() = Song(id, title, artist, artistId, album, albumId, duration, thumbnail)
    private fun PlayHistoryEntity.toSong() = Song(id, title, artist, artistId, album, albumId, duration, thumbnail)
    private fun PlaylistSongEntity.toSong() = Song(songId, title, artist, artistId, album, albumId, duration, thumbnail)

    private fun Song.toLiked() = LikedSongEntity(id, title, artist, artistId, album, albumId, duration, thumbnail)
    private fun Song.toHistory() = PlayHistoryEntity(id, title, artist, artistId, album, albumId, duration, thumbnail)
}
