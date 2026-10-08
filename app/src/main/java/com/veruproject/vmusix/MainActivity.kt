package com.veruproject.vmusix

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.veruproject.vmusix.data.local.prefs.AppSettings
import com.veruproject.vmusix.data.local.prefs.SettingsDataStore
import com.veruproject.vmusix.data.playback.PlayerRepository
import com.veruproject.vmusix.ui.nav.AppNav
import com.veruproject.vmusix.ui.theme.VmusixTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerRepository: PlayerRepository
    @Inject lateinit var settings: SettingsDataStore

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playerRepository.connect()
        requestNeededPermissions()

        setContent {
            val s by settings.settings.collectAsState(initial = AppSettings())
            val accent = Color(runCatching { s.themeColor.toColorInt() }.getOrDefault(0xFFA855F7.toInt()))
            VmusixTheme(accent = accent, darkMode = s.darkMode) {
                AppNav()
            }
        }
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Unduhan di API 26–28 menulis ke Music/ publik langsung.
        if (Build.VERSION.SDK_INT <= 28 && !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            requestPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
