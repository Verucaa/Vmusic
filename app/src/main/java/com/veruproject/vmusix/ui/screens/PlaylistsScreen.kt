package com.veruproject.vmusix.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.veruproject.vmusix.R
import com.veruproject.vmusix.ui.AppViewModel
import com.veruproject.vmusix.ui.components.EmptyView
import com.veruproject.vmusix.ui.components.ScreenHeader
import com.veruproject.vmusix.ui.components.UserPlaylistCard
import com.veruproject.vmusix.ui.nav.Routes

/** Daftar playlist lokal: tap = buka, tekan lama = hapus. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(vm: AppViewModel, navController: NavHostController) {
    val playlists by vm.music.observePlaylists().collectAsState(initial = emptyList())
    var createOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<com.veruproject.vmusix.domain.model.UserPlaylist?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { createOpen = true }) {
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.playlist_create_title),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(stringResource(R.string.library_playlists)) { navController.popBackStack() }
            if (playlists.isEmpty()) {
                EmptyView(stringResource(R.string.playlist_empty), Modifier.fillMaxSize())
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        UserPlaylistCard(
                            playlist = playlist,
                            onClick = { },
                            modifier = Modifier.combinedClickable(
                                onClick = { navController.navigate(Routes.detail("playlist", playlist.id.toString())) },
                                onLongClick = { deleting = playlist },
                            ),
                        )
                    }
                }
            }
        }
    }

    if (createOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createOpen = false },
            title = { Text(stringResource(R.string.playlist_create_title)) },
            text = {
                androidx.compose.material3.TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        vm.createPlaylist(name.trim()) { }
                        createOpen = false
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { createOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deleting?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.playlist_delete_confirm)) },
            text = { Text(playlist.name) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlaylist(playlist.id)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
