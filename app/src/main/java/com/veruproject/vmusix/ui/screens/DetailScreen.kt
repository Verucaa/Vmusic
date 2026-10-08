package com.veruproject.vmusix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.veruproject.vmusix.R
import com.veruproject.vmusix.domain.model.BrowseDetail
import com.veruproject.vmusix.domain.model.DetailKind
import com.veruproject.vmusix.domain.model.Song
import com.veruproject.vmusix.ui.AppViewModel
import com.veruproject.vmusix.ui.components.Artwork
import com.veruproject.vmusix.ui.components.ErrorView
import com.veruproject.vmusix.ui.components.ScreenHeader
import com.veruproject.vmusix.ui.components.SongList
import com.veruproject.vmusix.ui.components.VLoader

/**
 * Layar detail universal: album / artis / playlist online / playlist lokal /
 * lagu disukai / 50 teratas. Route: detail/{kind}/{id}
 */
@Composable
fun DetailScreen(vm: AppViewModel, navController: NavHostController, kind: String, id: String) {
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    var header by remember { mutableStateOf<BrowseDetail?>(null) }
    var customTitle by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var removeFromPlaylist by remember { mutableStateOf<((Song) -> Unit)?>(null) }

    val localPlaylistId = if (kind == "playlist") id.toLongOrNull() else null
    val isFlow = kind == "liked" || localPlaylistId != null

    // Muat data statis (album/artis/playlist online/charts).
    LaunchedEffect(kind, id, reload) {
        if (isFlow) return@LaunchedEffect
        error = false
        songs = null
        header = null
        runCatching {
            when (kind) {
                "charts" -> vm.music.charts()
                "album" -> vm.music.detail(DetailKind.ALBUM, id).also { header = it }.songs
                "artist" -> vm.music.detail(DetailKind.ARTIST, id).also { header = it }.songs
                "online_playlist" -> vm.music.detail(DetailKind.ONLINE_PLAYLIST, id).also { header = it }.songs
                else -> emptyList()
            }
        }.onSuccess { songs = it }
            .onFailure { error = true }
    }

    // Playlist lokal & lagu disukai bersifat reaktif (Flow).
    LaunchedEffect(kind, id, reload) {
        if (!isFlow) return@LaunchedEffect
        songs = null
        if (localPlaylistId != null) {
            removeFromPlaylist = { song -> vm.removeSongFromPlaylist(localPlaylistId, song.id) }
            vm.music.observePlaylists().collect { list ->
                customTitle = list.find { it.id == localPlaylistId }?.name
            }
            // (observePlaylists tidak pernah selesai; judul ikut berubah otomatis)
        }
    }
    LaunchedEffect(kind, id) {
        if (localPlaylistId != null) {
            vm.music.observePlaylistSongs(localPlaylistId).collect { songs = it }
        } else if (kind == "liked") {
            vm.music.observeLiked().collect { songs = it }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = header?.title ?: customTitle ?: titleForKind(kind),
            onBack = { navController.popBackStack() },
        )
        when {
            error -> ErrorView(stringResource(R.string.error_generic), onRetry = { reload++ }, Modifier.fillMaxSize())
            songs == null -> VLoader()
            else -> {
                val list = songs ?: emptyList()
                val h = header
                SongList(
                    songs = list,
                    vm = vm,
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                    emptyMessage = stringResource(R.string.empty_list),
                    onRemove = removeFromPlaylist,
                    header = {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Artwork(h?.thumbnail ?: "", size = 120.dp, rounded = 12.dp)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        h?.title ?: customTitle ?: titleForKind(kind),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!h?.artistName.isNullOrBlank()) {
                                        Text(
                                            h?.artistName ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.songs_count, list.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { vm.player.playQueue(list, 0) }, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.play_all))
                                }
                                OutlinedButton(
                                    onClick = { vm.player.playQueue(list.shuffled(), 0) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.shuffle_all))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    },
                )
            }
        }
    }
}

private fun titleForKind(kind: String): String = when (kind) {
    "liked" -> "Disukai"
    "charts" -> "50 Teratas"
    "playlist" -> "Playlist"
    "album" -> "Album"
    "artist" -> "Artis"
    else -> "Playlist"
}
