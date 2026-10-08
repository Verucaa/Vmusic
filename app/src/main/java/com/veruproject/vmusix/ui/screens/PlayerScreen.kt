package com.veruproject.vmusix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.veruproject.vmusix.R
import com.veruproject.vmusix.domain.model.Lyrics
import com.veruproject.vmusix.domain.model.Song
import com.veruproject.vmusix.ui.AppViewModel
import com.veruproject.vmusix.ui.components.Artwork
import com.veruproject.vmusix.ui.components.ScreenHeader
import com.veruproject.vmusix.core.util.formatPosition

/**
 * Pemutar layar penuh: background blur, kontrol, lirik sinkron, dan antrean.
 */
@Composable
fun PlayerScreen(vm: AppViewModel, navController: NavHostController) {
    val song by vm.player.currentSong.collectAsState()
    val isPlaying by vm.player.isPlaying.collectAsState()
    val position by vm.player.positionMs.collectAsState()
    val duration by vm.player.durationMs.collectAsState()
    val shuffle by vm.player.shuffle.collectAsState()
    val repeatMode by vm.player.repeatMode.collectAsState()
    val queue by vm.player.queue.collectAsState()
    val queueIndex by vm.player.queueIndex.collectAsState()
    val likedIds by vm.likedIds.collectAsState()

    var tab by remember { mutableStateOf(0) } // 0 = kontrol, 1 = lirik, 2 = antrean
    val current = song

    Box(Modifier.fillMaxSize()) {
        // Background blur dari artwork lagu.
        AsyncImage(
            model = current?.thumbnail ?: "",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(48.dp).background(MaterialTheme.colorScheme.background),
        )
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)))

        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = when (tab) {
                    1 -> stringResource(R.string.lyrics)
                    2 -> stringResource(R.string.queue)
                    else -> stringResource(R.string.app_name)
                },
                onBack = { navController.popBackStack() },
            )

            if (current == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.player_no_song),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                return@Column
            }

            when (tab) {
                1 -> LyricsView(vm, current, position, Modifier.weight(1f).fillMaxWidth())
                2 -> QueueView(queue, queueIndex, vm, Modifier.weight(1f).fillMaxWidth())
                else -> ControlsView(
                    song = current,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    shuffle = shuffle,
                    repeatMode = repeatMode,
                    isLiked = likedIds.contains(current.id),
                    vm = vm,
                    modifier = Modifier.weight(1f),
                )
            }

            // Tab bawah: kontrol / lirik / antrean.
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                PlayerTab(0, tab, R.drawable.ic_play, stringResource(R.string.play)) { tab = 0 }
                PlayerTab(1, tab, R.drawable.ic_lyrics_panel, stringResource(R.string.lyrics)) { tab = 1 }
                PlayerTab(2, tab, R.drawable.ic_queue, stringResource(R.string.queue)) { tab = 2 }
            }
        }
    }
}

@Composable
private fun PlayerTab(index: Int, selected: Int, icon: Int, label: String, onClick: () -> Unit) {
    val color = if (selected == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ------------------------------------------------------------- tab kontrol

@Composable
private fun ControlsView(
    song: Song,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    shuffle: Boolean,
    repeatMode: Int,
    isLiked: Boolean,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    var scrub by remember(song.id) { mutableFloatStateOf(-1f) }
    val shown = if (scrub >= 0) (scrub * duration).toLong() else position

    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Artwork(song.thumbnail, size = 280.dp, rounded = 20.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            song.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        Slider(
            value = if (duration > 0) shown.toFloat() / duration else 0f,
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                if (duration > 0 && scrub >= 0) vm.player.seekTo((scrub * duration).toLong())
                scrub = -1f
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPosition(shown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatPosition(duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlIcon(
                if (shuffle) R.drawable.ic_shuffle else R.drawable.ic_shuffle,
                stringResource(R.string.shuffle),
                active = shuffle,
            ) { vm.player.toggleShuffle() }
            ControlIcon(R.drawable.ic_previous, stringResource(R.string.previous)) { vm.player.previous() }
            // Tombol putar/jeda utama.
            IconButton(
                onClick = { vm.player.togglePlayPause() },
                modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp),
                )
            }
            ControlIcon(R.drawable.ic_next, stringResource(R.string.next)) { vm.player.next() }
            ControlIcon(
                if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                    R.drawable.ic_repeat_one
                } else {
                    R.drawable.ic_repeat
                },
                if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                    stringResource(R.string.repeat_one)
                } else {
                    stringResource(R.string.repeat)
                },
                active = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
            ) { vm.player.cycleRepeat() }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ControlIcon(
                if (isLiked) R.drawable.ic_like else R.drawable.ic_like_border,
                stringResource(if (isLiked) R.string.unlike else R.string.like),
                active = isLiked,
            ) { vm.toggleLike(song) }
            ControlIcon(R.drawable.ic_download, stringResource(R.string.download)) {
                vm.downloads.start(song)
            }
        }
    }
}

@Composable
private fun ControlIcon(icon: Int, contentDescription: String, active: Boolean = false, onClick: () -> Unit) {
    Icon(
        painterResource(icon),
        contentDescription = contentDescription,
        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClick).padding(4.dp),
    )
}

// ----------------------------------------------------------------- lirik

@Composable
private fun LyricsView(vm: AppViewModel, song: Song, position: Long, modifier: Modifier = Modifier) {
    var lyrics by remember(song.id) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(song.id) { mutableStateOf(true) }

    LaunchedEffect(song.id) {
        loading = true
        lyrics = null
        runCatching { vm.music.lyrics(song) }
            .onSuccess { lyrics = it }
            .onFailure { lyrics = null }
        loading = false
    }

    val data = lyrics
    when {
        loading -> Box(modifier, contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        data == null -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.lyrics_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> {
            val activeIndex = if (data.synced) {
                data.lines.indexOfLast { (it.timeMs ?: 0) <= position }
            } else -1
            LazyColumn(modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                if (!data.synced) {
                    item {
                        Text(
                            stringResource(R.string.lyrics_unsynced),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                itemsIndexed(data.lines) { index, line ->
                    val active = index == activeIndex
                    Text(
                        line.text.ifEmpty { "♪" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            active -> MaterialTheme.colorScheme.primary
                            data.synced && activeIndex >= 0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable {
                                line.timeMs?.let { vm.player.seekTo(it) }
                            },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------- antrean

@Composable
private fun QueueView(queue: List<Song>, currentIndex: Int, vm: AppViewModel, modifier: Modifier = Modifier) {
    if (queue.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.queue_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier) {
        itemsIndexed(queue, key = { _, s -> s.id }) { index, song ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.player.playQueue(queue, index) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(song.thumbnail, size = 40.dp, rounded = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
