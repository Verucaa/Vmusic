package com.veruproject.vmusix.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.veruproject.vmusix.R
import com.veruproject.vmusix.domain.model.Song
import com.veruproject.vmusix.ui.AppViewModel

/**
 * Daftar lagu standar: tap = putar berurutan dari posisi itu, menu = tindakan lengkap.
 * Dipakai oleh layar detail, cari, liked, charts, playlist.
 */
@Composable
fun SongList(
    songs: List<Song>,
    vm: AppViewModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    emptyMessage: String? = null,
    header: (@Composable () -> Unit)? = null,
    onRemove: ((Song) -> Unit)? = null,
) {
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var pickingFor by remember { mutableStateOf<Song?>(null) }
    val playlists by vm.music.observePlaylists().collectAsState(initial = emptyList())
    val currentSong by vm.player.currentSong.collectAsState()

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        if (header != null) item { header() }
        if (songs.isEmpty() && emptyMessage != null) {
            item { EmptyView(emptyMessage) }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            SongRow(
                song = song,
                onClick = { vm.player.playQueue(songs, index) },
                isPlaying = currentSong?.id == song.id,
                trailing = if (onRemove != null) {
                    {
                        IconButton(onClick = { onRemove(song) }) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else null,
                onMore = {
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menuSong = song }) {
                            Icon(
                                painterResource(R.drawable.ic_more),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (menuSong?.id == song.id) {
                            SongMenu(
                                expanded = true,
                                onDismiss = { menuSong = null },
                                onPlayNow = { vm.player.playQueue(songs, index) },
                                onAddQueue = { vm.player.addToQueue(song) },
                                onAddPlaylist = { pickingFor = song },
                                onDownload = { vm.downloads.start(song) },
                                onGoAlbum = song.albumId.takeIf { it.startsWith("MPRE") }?.let { id ->
                                    { navController.navigate("detail/album/$id") }
                                },
                                onGoArtist = song.artistId.takeIf { it.startsWith("UC") }?.let { id ->
                                    { navController.navigate("detail/artist/$id") }
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    pickingFor?.let { song ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { pickingFor = null },
            onPick = { playlistId ->
                vm.addToPlaylist(playlistId, song)
                pickingFor = null
            },
            onCreate = { name ->
                vm.createPlaylist(name) { id -> vm.addToPlaylist(id, song) }
            },
        )
    }
}
