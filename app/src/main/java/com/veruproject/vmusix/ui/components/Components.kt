package com.veruproject.vmusix.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veruproject.vmusix.R
import com.veruproject.vmusix.domain.model.Album
import com.veruproject.vmusix.domain.model.Artist
import com.veruproject.vmusix.domain.model.OnlinePlaylist
import com.veruproject.vmusix.domain.model.Song
import com.veruproject.vmusix.domain.model.UserPlaylist
import com.veruproject.vmusix.core.util.formatDuration

/** Loader melingkar tanpa teks (identitas Vmusix). */
@Composable
fun VLoader(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(42.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
    }
}

/** Judul bagian + aksen teks opsional di kanan. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Baris lagu: thumbnail, judul, artis, durasi, menu. */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onMore: (@Composable () -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.thumbnail, size = 48.dp, rounded = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        } else if (song.duration > 0) {
            Text(
                formatDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onMore != null) onMore()
    }
}

/** Gambar karya seni (album/playlist/artis) dengan placeholder minimal. */
@Composable
fun Artwork(url: String, size: androidx.compose.ui.unit.Dp, rounded: androidx.compose.ui.unit.Dp) {
    if (url.isEmpty()) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(rounded))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(rounded)),
            contentScale = ContentScale.Crop,
        )
    }
}

/** Kartu grid (album / playlist / artis). */
@Composable
fun MediaCard(title: String, subtitle: String, thumbnail: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(140.dp).clickable(onClick = onClick).padding(4.dp),
    ) {
        Artwork(thumbnail, size = 132.dp, rounded = 12.dp)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) =
    MediaCard(album.title, album.artist, album.thumbnail, onClick, modifier)

@Composable
fun ArtistCard(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(120.dp).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = artist.thumbnail,
            contentDescription = null,
            modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(6.dp))
        Text(artist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PlaylistCard(playlist: OnlinePlaylist, onClick: () -> Unit, modifier: Modifier = Modifier) =
    MediaCard(playlist.title, playlist.author, playlist.thumbnail, onClick, modifier)

/** Kartu playlist lokal (dari Room). */
@Composable
fun UserPlaylistCard(playlist: UserPlaylist, onClick: () -> Unit, modifier: Modifier = Modifier) =
    MediaCard(
        playlist.name,
        stringResource(R.string.songs_count, playlist.songCount),
        playlist.thumbnail,
        onClick,
        modifier,
    )

// ------------------------------------------------------------ state kosong

@Composable
fun EmptyView(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

// -------------------------------------------------------------- menu lagu

/** Menu tindakan lagu (dropdown) — dipanggil dari baris lagu. */
@Composable
fun SongMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onAddQueue: () -> Unit,
    onAddPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onGoAlbum: (() -> Unit)? = null,
    onGoArtist: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_play_now)) }, onClick = { onPlayNow(); onDismiss() })
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_queue)) }, onClick = { onAddQueue(); onDismiss() })
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_playlist)) }, onClick = { onAddPlaylist(); onDismiss() })
        DropdownMenuItem(text = { Text(stringResource(R.string.menu_download)) }, onClick = { onDownload(); onDismiss() })
        if (onGoAlbum != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_go_album)) }, onClick = { onGoAlbum(); onDismiss() })
        }
        if (onGoArtist != null) {
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_go_artist)) }, onClick = { onGoArtist(); onDismiss() })
        }
    }
}

/** Dialog pilih / buat playlist. */
@Composable
fun AddToPlaylistDialog(
    playlists: List<UserPlaylist>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_playlist)) },
        text = {
            Column {
                playlists.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(p.id) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_playlist_add), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (name.isNotBlank()) { onCreate(name.trim()); onDismiss() }
                    }) { Text(stringResource(R.string.create)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Mini player di atas bottom navigation. */
@Composable
fun MiniPlayer(
    title: String,
    artist: String,
    artwork: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(artwork, size = 40.dp, rounded = 8.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                modifier = Modifier.size(30.dp).clickable(onClick = onToggle).padding(4.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_next),
                contentDescription = stringResource(R.string.next),
                modifier = Modifier.size(30.dp).clickable(onClick = onNext).padding(4.dp),
            )
        }
    }
}

/** Baris pengaturan (label + nilai + aksi). */
@Composable
fun SettingRow(title: String, value: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onClick != null) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Header layar stack: tombol kembali + judul. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
     Row(
         modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
         verticalAlignment = Alignment.CenterVertically,
     ) {
         IconButton(onClick = onBack) {
             Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
         }
         Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
     }
 }
