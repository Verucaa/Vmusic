package com.veruproject.vmusix.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.veruproject.vmusix.R
import com.veruproject.vmusix.domain.model.SearchResults
import com.veruproject.vmusix.ui.AppViewModel
import com.veruproject.vmusix.ui.components.AlbumCard
import com.veruproject.vmusix.ui.components.ArtistCard
import com.veruproject.vmusix.ui.components.EmptyView
import com.veruproject.vmusix.ui.components.ErrorView
import com.veruproject.vmusix.ui.components.PlaylistCard
import com.veruproject.vmusix.ui.components.SongList
import com.veruproject.vmusix.ui.components.VLoader
import com.veruproject.vmusix.ui.nav.Routes
import kotlinx.coroutines.delay

/** Layar Cari: input + tab hasil (Lagu/Album/Artis/Playlist). */
@Composable
fun SearchScreen(vm: AppViewModel, navController: NavHostController) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var retryTick by remember { mutableIntStateOf(0) }

    // Debounce: cari 400ms setelah pengguna berhenti mengetik.
    LaunchedEffect(query, retryTick) {
        if (query.isBlank()) {
            results = null
            loading = false
            error = false
            return@LaunchedEffect
        }
        loading = true
        error = false
        delay(400)
        val q = query.trim()
        if (q.isEmpty()) {
            loading = false
            return@LaunchedEffect
        }
        runCatching { vm.music.search(q) }
            .onSuccess { results = it; loading = false }
            .onFailure { error = true; loading = false }
    }

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(painterResource(R.drawable.ic_clear), contentDescription = null)
                    }
                }
            } else null,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val r = results
        when {
            query.isBlank() -> EmptyView(stringResource(R.string.search_initial), Modifier.fillMaxSize())
            error -> ErrorView(
                stringResource(R.string.search_error),
                onRetry = { retryTick++ },
                modifier = Modifier.fillMaxSize(),
            )
            loading && r == null -> VLoader()
            r == null -> VLoader()
            else -> {
                val tabs = listOf(
                    stringResource(R.string.search_tab_songs) to r.songs.size,
                    stringResource(R.string.search_tab_albums) to r.albums.size,
                    stringResource(R.string.search_tab_artists) to r.artists.size,
                    stringResource(R.string.search_tab_playlists) to r.playlists.size,
                )
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    tabs.forEachIndexed { i, (label, count) ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = { Text("$label ($count)") },
                        )
                    }
                }
                when (tab) {
                    0 -> if (r.songs.isEmpty()) EmptyView(stringResource(R.string.search_empty), Modifier.fillMaxSize())
                    else SongList(r.songs, vm, navController, Modifier.fillMaxSize())
                    1 -> if (r.albums.isEmpty()) EmptyView(stringResource(R.string.search_empty), Modifier.fillMaxSize())
                    else LazyVerticalGrid(
                        GridCells.Fixed(2),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(r.albums, key = { it.id }) { album ->
                            AlbumCard(album) { navController.navigate(Routes.detail("album", album.id)) }
                        }
                    }
                    2 -> if (r.artists.isEmpty()) EmptyView(stringResource(R.string.search_empty), Modifier.fillMaxSize())
                    else LazyVerticalGrid(
                        GridCells.Fixed(3),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(r.artists, key = { it.id }) { artist ->
                            ArtistCard(artist) { navController.navigate(Routes.detail("artist", artist.id)) }
                        }
                    }
                    else -> if (r.playlists.isEmpty()) EmptyView(stringResource(R.string.search_empty), Modifier.fillMaxSize())
                    else LazyVerticalGrid(
                        GridCells.Fixed(2),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(r.playlists, key = { it.id }) { playlist ->
                            PlaylistCard(playlist) { navController.navigate(Routes.detail("online_playlist", playlist.id)) }
                        }
                    }
                }
            }
        }
    }
}
