package com.veruproject.vmusix.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.veruproject.vmusix.R
import com.veruproject.vmusix.core.util.dirFileCount
import com.veruproject.vmusix.core.util.dirSize
import com.veruproject.vmusix.core.util.humanSize
import com.veruproject.vmusix.ui.AppViewModel
import com.veruproject.vmusix.ui.components.ScreenHeader
import com.veruproject.vmusix.ui.components.SettingRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Layar Cache: ukuran cache gambar & data, tombol bersihkan. */
@Composable
fun CacheScreen(vm: AppViewModel, navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }

    val cache = context.cacheDir
    val codeCache = context.codeCacheDir
    // Hitung ulang setiap kali tick berubah (setelah pembersihan).
    val cacheSize = remember(tick, cleared) { dirSize(cache) }
    val codeSize = remember(tick, cleared) { dirSize(codeCache) }
    val fileCount = remember(tick, cleared) { dirFileCount(cache) + dirFileCount(codeCache) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.cache_title)) { navController.popBackStack() }

        SettingRow(stringResource(R.string.cache_total), humanSize(cacheSize + codeSize))
        SettingRow(stringResource(R.string.cache_images), humanSize(cacheSize))
        SettingRow(stringResource(R.string.cache_data), humanSize(codeSize))
        SettingRow(stringResource(R.string.cache_stats_count, fileCount))

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { confirm = true },
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = cacheSize + codeSize > 0,
        ) {
            Text(stringResource(R.string.cache_clear_all))
        }
        if (cleared) {
            Text(
                stringResource(R.string.cache_cleared),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.cache_clear_all)) },
            text = { Text(humanSize(cacheSize + codeSize)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // ponytail: hanya bersihkan cache aplikasi; unduhan & data Room tidak disentuh.
                            cache.listFiles()?.forEach { it.deleteRecursively() }
                            codeCache.listFiles()?.forEach { it.deleteRecursively() }
                        }
                        tick++
                        cleared = true
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
