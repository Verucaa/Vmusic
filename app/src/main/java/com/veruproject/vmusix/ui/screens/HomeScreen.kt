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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.veruproject.vmusix.R
import com.veruproject.vmusix.domain.model.Shelf
import com.veruproject.vmusix.ui.AppViewModel
import com.veruproject.vmusix.ui.components.AlbumCard
import com.veruproject.vmusix.ui.components.ArtistCard
import com.veruproject.vmusix.ui.components.EmptyView
import com.veruproject.vmusix.ui.components.ErrorView
import com.veruproject.vmusix.ui.components.MediaCard
import com.veruproject.vmusix.ui.components.PlaylistCard
import com.veruproject.vmusix.ui.components.SectionHeader
import com.veruproject.vmusix.ui.components.VLoader
import com.veruproject.vmusix.ui.nav.Routes

/** Beranda: riwayat "Baru Diputar" + shelf editorial YouTube Music. */
@Composable
fun HomeScreen(vm: AppViewModel, navController: NavHostController) {
    var shelves by remember { mutableStateOf<List<Shelf>?>(null) }
    var error by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val recent by vm.music.observeRecent().collectAsState(initial = emptyList())

    LaunchedEffect(reload) {
        error = false
        shelves = null
        runCatching { vm.music.home() }
            .onSuccess { shelves = it }
            .onFailure { error = true }
    }

    when {
        error && shelves == null -> ErrorView(
            message = stringResource(R.string.error_generic),
            onRetry = { reload++ },
            modifier = Modifier.fillMaxSize(),
        )
        shelves == null -> VLoader()
        else -> {
            val data = shelves ?: emptyList()
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(R.string.tagline),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { reload++ }) {
                            Icon(painterResource(R.drawable.ic_history), contentDescription = stringResource(R.string.refresh))
                        }
                    }
                }

                if (recent.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.home_recently_played)) }
                    item {
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(recent, key = { _, s -> "r" + s.id }) { index, song ->
                                MediaCard(
                                    title = song.title,
                                    subtitle = song.artist,
                                    thumbnail = song.thumbnail,
                                    onClick = { vm.player.playQueue(recent, index) },
                                )
                            }
                        }
                    }
                }

                if (data.isEmpty()) {
                    item { EmptyView(stringResource(R.string.home_empty)) }
                }

                itemsIndexed(data, key = { i, s -> s.title + i }) { _, shelf ->
                    if (!shelf.isEmpty) {
                        SectionHeader(shelf.title)
                        ShelfRow(shelf, vm, navController)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfRow(shelf: Shelf, vm: AppViewModel, navController: NavHostController) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (shelf.songs.isNotEmpty()) {
            itemsIndexed(shelf.songs, key = { _, s -> s.id }) { _, song ->
                MediaCard(
                    title = song.title,
                    subtitle = song.artist,
                    thumbnail = song.thumbnail,
                    onClick = { vm.player.playQueue(shelf.songs, shelf.songs.indexOf(song)) },
                )
            }
        }
        items(shelf.albums, key = { it.id }) { album ->
            AlbumCard(album, onClick = { navController.navigate(Routes.detail("album", album.id)) })
        }
        items(shelf.artists, key = { it.id }) { artist ->
            ArtistCard(artist, onClick = { navController.navigate(Routes.detail("artist", artist.id)) })
        }
        items(shelf.playlists, key = { it.id }) { playlist ->
            PlaylistCard(playlist, onClick = { navController.navigate(Routes.detail("online_playlist", playlist.id)) })
        }
    }
}
