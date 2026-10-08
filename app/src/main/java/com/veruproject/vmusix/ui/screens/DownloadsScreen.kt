package com.veruproject.vmusix.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.veruproject.vmusix.R
import com.veruproject.vmusix.data.local.db.DownloadEntity
import com.veruproject.vmusix.ui.AppViewModel
import com.veruproject.vmusix.ui.components.Artwork
import com.veruproject.vmusix.ui.components.EmptyView
import com.veruproject.vmusix.ui.components.ScreenHeader
import com.veruproject.vmusix.core.util.formatDuration

/** Layar unduhan: progres, jeda/lanjut/batal/coba-lagi. */
@Composable
fun DownloadsScreen(vm: AppViewModel, navController: NavHostController) {
    val downloads by vm.downloads.observe().collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.library_downloads)) { navController.popBackStack() }
        Text(
            stringResource(R.string.download_location),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (downloads.isEmpty()) {
            EmptyView(stringResource(R.string.downloads_empty), Modifier.fillMaxSize())
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(downloads, key = { it.id }) { item ->
                    DownloadRow(item, vm)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadEntity, vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.thumbnail, size = 48.dp, rounded = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                item.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            when (item.state) {
                DownloadEntity.STATE_DOWNLOADING -> LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                DownloadEntity.STATE_PAUSED, DownloadEntity.STATE_FAILED -> LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Unit
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stateLabel(item),
                style = MaterialTheme.typography.bodySmall,
                color = when (item.state) {
                    DownloadEntity.STATE_FAILED -> MaterialTheme.colorScheme.error
                    DownloadEntity.STATE_DONE -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        when (item.state) {
            DownloadEntity.STATE_DOWNLOADING -> ActionButton(R.drawable.ic_pause, R.string.download_pause) {
                vm.downloads.pause(item.id)
            }
            DownloadEntity.STATE_PAUSED -> ActionButton(R.drawable.ic_play, R.string.download_resume) {
                vm.downloads.resume(item.id)
            }
            DownloadEntity.STATE_FAILED -> ActionButton(R.drawable.ic_warning, R.string.download_retry) {
                vm.downloads.retry(item.id)
            }
            else -> Unit
        }
        ActionButton(R.drawable.ic_close, R.string.download_cancel) {
            vm.downloads.cancel(item.id)
        }
    }
}

@Composable
private fun stateLabel(item: DownloadEntity): String = when (item.state) {
    DownloadEntity.STATE_DOWNLOADING -> "${stringResource(R.string.download_state_downloading)} · ${item.progress}%"
    DownloadEntity.STATE_PAUSED -> "${stringResource(R.string.download_state_paused)} · ${item.progress}%"
    DownloadEntity.STATE_FAILED -> stringResource(R.string.download_state_failed)
    else -> stringResource(R.string.download_state_done)
}

@Composable
private fun ActionButton(icon: Int, description: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = stringResource(description), tint = MaterialTheme.colorScheme.primary)
    }
}
