package com.veruproject.vmusix.ui

import androidx.lifecycle.ViewModel
import com.veruproject.vmusix.data.download.DownloadManager
import com.veruproject.vmusix.data.local.prefs.SettingsDataStore
import com.veruproject.vmusix.data.playback.PlayerRepository
import com.veruproject.vmusix.data.remote.lastfm.LastFmApi
import com.veruproject.vmusix.data.repo.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel tunggal: menyatukan dependensi layer data untuk semua layar.
 * State per layar (loading/hasil) disimpan lokal di composable masing-masing.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    val music: MusicRepository,
    val player: PlayerRepository,
    val downloads: DownloadManager,
    val settings: SettingsDataStore,
    val lastFm: LastFmApi,
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** id lagu yang disukai (untuk ikon hati di daftar lagu). */
    val likedIds: StateFlow<Set<String>> = music.observeLiked()
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Token Last.fm: mulai alur otorisasi (buka browser → setujui → simpan sesi). */
    suspend fun lastFmConnect(onToken: (String) -> Unit): Boolean {
        val token = lastFm.getToken() ?: return false
        onToken(token)
        return true
    }

    suspend fun lastFmApprove(token: String): Boolean {
        val session = lastFm.getSession(token) ?: return false
        settings.setLastFmSession(session.first, session.second)
        return true
    }

    suspend fun lastFmDisconnect() = settings.clearLastFm()

    fun toggleLike(song: com.veruproject.vmusix.domain.model.Song) {
        scope.launch { music.toggleLike(song) }
    }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit) {
        scope.launch { onCreated(music.createPlaylist(name)) }
    }

    fun addToPlaylist(playlistId: Long, song: com.veruproject.vmusix.domain.model.Song) {
        scope.launch { music.addToPlaylist(playlistId, song) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        scope.launch { music.removeFromPlaylist(playlistId, songId) }
    }

    fun deletePlaylist(id: Long) {
        scope.launch { music.deletePlaylist(id) }
    }
}
