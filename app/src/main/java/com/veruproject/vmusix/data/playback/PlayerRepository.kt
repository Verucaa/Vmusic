package com.veruproject.vmusix.data.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.veruproject.vmusix.data.local.db.DownloadDao
import com.veruproject.vmusix.data.local.db.DownloadEntity
import com.veruproject.vmusix.data.local.prefs.SettingsDataStore
import com.veruproject.vmusix.data.remote.lastfm.LastFmApi
import com.veruproject.vmusix.data.repo.MusicRepository
import com.veruproject.vmusix.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jembatan UI ↔ PlaybackService (MediaController).
 * Menyediakan state pemutar + antrean, mencatat riwayat, dan scrobble Last.fm.
 */
@Singleton
class PlayerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val music: MusicRepository,
    private val downloads: DownloadDao,
    private val lastFm: LastFmApi,
    private val settings: SettingsDataStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    /** id video → Song lengkap (dipakai untuk like/riwayat/lirik dari antrean yang sedang berjalan). */
    private val songLookup = LinkedHashMap<String, Song>()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex

    // --- status scrobble lagu berjalan ---
    private var trackSong: Song? = null
    private var trackPlayedMs = 0L
    private var playStartWall = 0L
    private var scrobbleStartSec = 0L

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(playerListener)
            _connected.value = true
            syncState()
            startPositionPolling()
        }, ContextCompat.getMainExecutor(context))
    }

    // ------------------------------------------------------------- antrean

    /** Putar daftar lagu dari awal / index tertentu. URL diambil saat dibutuhkan. */
    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        songs.forEach { songLookup[it.id] = it }
        scope.launch {
            val items = songs.map { buildItem(it) }
            controller?.let { c ->
                c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0)
                c.prepare()
                c.play()
            }
            _queue.value = songs
            _queueIndex.value = startIndex.coerceIn(0, songs.lastIndex)
        }
    }

    fun playSong(song: Song) = playQueue(listOf(song), 0)

    /** Tambahkan ke antrean setelah lagu sekarang. */
    fun addToQueue(song: Song) {
        songLookup[song.id] = song
        scope.launch {
            val c = controller ?: return@launch
            val index = c.currentMediaItemIndex + 1
            c.addMediaItem(index, buildItem(song))
            _queue.value = queue.value.toMutableList().apply { add(index, song) }
        }
    }

    // ------------------------------------------------------------ kendali

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem() ?: Unit
    fun previous() = controller?.seekToPreviousMediaItem() ?: Unit
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs) ?: Unit

    fun toggleShuffle() {
        controller?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
            _shuffle.value = it.shuffleModeEnabled
        }
    }

    fun cycleRepeat() {
        controller?.repeatMode = when (controller?.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = controller?.repeatMode ?: Player.REPEAT_MODE_OFF
    }

    // ------------------------------------------------------------- internal

    /** Bangun MediaItem: pakai file unduhan bila tersedia, selain itu stream. */
    private suspend fun buildItem(song: Song): MediaItem {
        val local = downloads.get(song.id)
        val uri: String = if (local != null && local.state == DownloadEntity.STATE_DONE && local.path.isNotEmpty()) {
            local.path
        } else {
            runCatching { music.audioUrl(song.id) }.getOrElse { "" }
                .ifEmpty { "https://www.youtube.com/watch?v=${song.id}" }
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(Uri.parse(song.thumbnail))
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                finishTrack(player)
                startTrack(player)
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) {
                    playStartWall = System.currentTimeMillis()
                } else {
                    accumulate()
                }
            }
        }
    }

    private fun syncState() {
        val c = controller ?: return
        _isPlaying.value = c.isPlaying
        _durationMs.value = c.duration.takeIf { it > 0 } ?: 0L
        _repeatMode.value = c.repeatMode
        _shuffle.value = c.shuffleModeEnabled
        _queueIndex.value = c.currentMediaItemIndex.coerceAtLeast(0)
        val id = c.currentMediaItem?.mediaId
        _currentSong.value = id?.let { songLookup[it] } ?: _currentSong.value?.takeIf { it.id == id }
        _queue.value = if (c.mediaItemCount > 0) {
            (0 until c.mediaItemCount).mapNotNull { i ->
                val item = c.getMediaItemAt(i)
                songLookup[item.mediaId] ?: songFromMetadata(item)
            }
        } else {
            _queue.value
        }
    }

    private fun songFromMetadata(item: MediaItem): Song = Song(
        id = item.mediaId,
        title = item.mediaMetadata.title?.toString() ?: "",
        artist = item.mediaMetadata.artist?.toString() ?: "",
        thumbnail = item.mediaMetadata.artworkUri?.toString() ?: "",
    )

    private fun startTrack(player: Player) {
        val song = player.currentMediaItem?.mediaId?.let { songLookup[it] } ?: return
        trackSong = song
        trackPlayedMs = 0L
        playStartWall = if (player.isPlaying) System.currentTimeMillis() else 0L
        scrobbleStartSec = System.currentTimeMillis() / 1000
        scope.launch {
            music.recordPlay(song)
            val s = settings.settings.first()
            if (s.scrobbleEnabled && s.lastFmSessionKey.isNotEmpty() && lastFm.isConfigured) {
                lastFm.updateNowPlaying(song, s.lastFmSessionKey)
            }
        }
    }

    private fun finishTrack(player: Player) {
        val song = trackSong ?: return
        val played = accumulatedMs(player.isPlaying)
        // ponytail: scrobble setelah 30 detik (aturan umum Last.fm); tanpa itu dianggap skip.
        if (played >= 30_000) {
            scope.launch {
                val s = settings.settings.first()
                if (s.scrobbleEnabled && s.lastFmSessionKey.isNotEmpty() && lastFm.isConfigured) {
                    lastFm.scrobble(song, s.lastFmSessionKey, scrobbleStartSec)
                }
            }
        }
        trackSong = null
        trackPlayedMs = 0
        playStartWall = 0
    }

    private fun accumulate() {
        if (playStartWall > 0) {
            trackPlayedMs += System.currentTimeMillis() - playStartWall
            playStartWall = 0
        }
    }

    private fun accumulatedMs(isPlaying: Boolean): Long {
        var total = trackPlayedMs
        if (isPlaying && playStartWall > 0) total += System.currentTimeMillis() - playStartWall
        return total
    }

    private var positionJob: Job? = null

    private fun startPositionPolling() {
        if (positionJob != null) return
        positionJob = scope.launch {
            while (true) {
                val c = controller
                if (c != null) {
                    _positionMs.value = c.currentPosition.coerceAtLeast(0)
                    val d = c.duration
                    if (d > 0) _durationMs.value = d
                    if (_isPlaying.value != c.isPlaying) _isPlaying.value = c.isPlaying
                }
                delay(500)
            }
        }
    }
}
