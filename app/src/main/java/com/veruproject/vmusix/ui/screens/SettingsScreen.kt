package com.veruproject.vmusix.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.veruproject.vmusix.BuildConfig
import com.veruproject.vmusix.R
import com.veruproject.vmusix.brand.BrandConfig
import com.veruproject.vmusix.ui.AppViewModel
import com.veruproject.vmusix.ui.components.ScreenHeader
import com.veruproject.vmusix.ui.components.SettingRow
import com.veruproject.vmusix.ui.nav.Routes
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.toArgb

/** Layar Pengaturan: tampilan, pemutaran, penyimpanan, integrasi Last.fm, tentang. */
@Composable
fun SettingsScreen(vm: AppViewModel, navController: NavHostController) {
    val settings by vm.settings.settings.collectAsState(initial = com.veruproject.vmusix.data.local.prefs.AppSettings())
    val s = settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickingQuality by remember { mutableStateOf<String?>(null) } // "audio" | "download" | "lyrics"
    var pendingToken by remember { mutableStateOf<String?>(null) }
    var lastFmError by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(stringResource(R.string.settings_title)) { navController.popBackStack() }

        SectionLabel(stringResource(R.string.settings_appearance))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_dark_mode), Modifier.weight(1f))
            Switch(checked = s.darkMode, onCheckedChange = { scope.launch { vm.settings.setDarkMode(it) } })
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_theme_color), Modifier.weight(1f))
            BrandConfig.THEME_COLORS.forEach { color ->
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(color)
                        .clickable { scope.launch { vm.settings.setThemeColor("#%06X".format(0xFFFFFF and color.toArgb())) } },
                )
            }
        }

        SectionLabel(stringResource(R.string.settings_playback))
        SettingRow(
            stringResource(R.string.settings_audio_quality),
            qualityLabel(s.audioQuality),
        ) { pickingQuality = "audio" }
        SettingRow(
            stringResource(R.string.settings_download_quality),
            qualityLabel(s.downloadQuality),
        ) { pickingQuality = "download" }
        SettingRow(
            stringResource(R.string.settings_lyrics_source),
            lyricsLabel(s.lyricsSource),
        ) { pickingQuality = "lyrics" }

        SectionLabel(stringResource(R.string.settings_storage))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.cache_auto_clean), Modifier.weight(1f))
            Switch(checked = s.autoCleanCache, onCheckedChange = { scope.launch { vm.settings.setAutoClean(it) } })
        }
        SettingRow(stringResource(R.string.settings_clear_cache)) { navController.navigate(Routes.CACHE) }

        SectionLabel(stringResource(R.string.settings_integration))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_lastfm_scrobble), Modifier.weight(1f))
            Switch(checked = s.scrobbleEnabled, onCheckedChange = { scope.launch { vm.settings.setScrobbleEnabled(it) } })
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (s.lastFmSessionKey.isNotEmpty()) {
                        stringResource(R.string.lastfm_connected, s.lastFmUser)
                    } else {
                        stringResource(R.string.lastfm_status_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (s.lastFmSessionKey.isNotEmpty()) {
                TextButton(onClick = { scope.launch { vm.lastFmDisconnect() } }) {
                    Text(stringResource(R.string.lastfm_disconnect))
                }
            } else {
                TextButton(onClick = {
                    scope.launch {
                        vm.lastFmConnect { token ->
                            pendingToken = token
                            val url = "https://www.last.fm/api/auth?api_key=${BuildConfig.LASTFM_API_KEY}&token=$token"
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    }
                }) { Text(stringResource(R.string.lastfm_connect)) }
            }
        }

        SectionLabel(stringResource(R.string.settings_about))
        Text(
            stringResource(R.string.about_text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Text(
            stringResource(R.string.about_version, BrandConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            "${BrandConfig.DEVELOPER_NAME} · ${BrandConfig.TAGLINE}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(Modifier.size(24.dp))
    }

    // Dialog pilihan opsi (kualitas audio/unduh, sumber lirik).
    pickingQuality?.let { which ->
        val options: List<Pair<String, String>> = when (which) {
            "audio" -> listOf(
                "auto" to stringResource(R.string.quality_auto),
                "high" to stringResource(R.string.quality_high),
                "medium" to stringResource(R.string.quality_medium),
                "low" to stringResource(R.string.quality_low),
            )
            "download" -> listOf(
                "high" to stringResource(R.string.quality_high),
                "medium" to stringResource(R.string.quality_medium),
                "low" to stringResource(R.string.quality_low),
            )
            else -> listOf(
                "auto" to stringResource(R.string.lyrics_source_auto),
                "youtube" to stringResource(R.string.lyrics_source_youtube),
                "lrclib" to stringResource(R.string.lyrics_source_lrclib),
                "betterlyrics" to stringResource(R.string.lyrics_source_better),
                "kugou" to stringResource(R.string.lyrics_source_kugou),
                "paxsenix" to stringResource(R.string.lyrics_source_paxsenix),
            )
        }
        AlertDialog(
            onDismissRequest = { pickingQuality = null },
            title = { Text(titleForPicker(which)) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    when (which) {
                                        "audio" -> vm.settings.setAudioQuality(value)
                                        "download" -> vm.settings.setDownloadQuality(value)
                                        else -> vm.settings.setLyricsSource(value)
                                    }
                                }
                                pickingQuality = null
                            }.padding(vertical = 10.dp),
                        ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingQuality = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Konfirmasi persetujuan Last.fm.
    pendingToken?.let { token ->
        AlertDialog(
            onDismissRequest = { pendingToken = null },
            title = { Text(stringResource(R.string.lastfm_connect)) },
            text = { Text(stringResource(R.string.lastfm_opened)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = vm.lastFmApprove(token)
                        lastFmError = !ok
                        if (ok) pendingToken = null
                    }
                }) { Text(stringResource(R.string.lastfm_approve)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingToken = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (lastFmError) {
        AlertDialog(
            onDismissRequest = { lastFmError = false },
            title = { Text(stringResource(R.string.error_generic)) },
            confirmButton = {
                TextButton(onClick = { lastFmError = false }) { Text(stringResource(R.string.retry)) }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 4.dp),
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun qualityLabel(value: String): String = when (value) {
    "high" -> stringResource(R.string.quality_high)
    "medium" -> stringResource(R.string.quality_medium)
    "low" -> stringResource(R.string.quality_low)
    else -> stringResource(R.string.quality_auto)
}

@Composable
private fun lyricsLabel(value: String): String = when (value) {
    "youtube" -> stringResource(R.string.lyrics_source_youtube)
    "lrclib" -> stringResource(R.string.lyrics_source_lrclib)
    "betterlyrics" -> stringResource(R.string.lyrics_source_better)
    "kugou" -> stringResource(R.string.lyrics_source_kugou)
    "paxsenix" -> stringResource(R.string.lyrics_source_paxsenix)
    else -> stringResource(R.string.lyrics_source_auto)
}

@Composable
private fun titleForPicker(which: String): String = when (which) {
    "audio" -> stringResource(R.string.settings_audio_quality)
    "download" -> stringResource(R.string.settings_download_quality)
    else -> stringResource(R.string.settings_lyrics_source)
}
