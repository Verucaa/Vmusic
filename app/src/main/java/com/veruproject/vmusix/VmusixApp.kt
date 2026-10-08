package com.veruproject.vmusix

import android.app.Application
import com.veruproject.vmusix.data.local.prefs.SettingsDataStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VmusixApp : Application() {

    @Inject lateinit var settings: SettingsDataStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // ponytail: bersihkan cache gambar/data saat dibuka bila opsi diaktifkan.
        appScope.launch {
            if (settings.settings.first().autoCleanCache) {
                cacheDir.deleteRecursively()
                codeCacheDir.deleteRecursively()
            }
        }
    }
}
