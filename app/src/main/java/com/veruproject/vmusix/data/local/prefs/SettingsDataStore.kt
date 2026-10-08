package com.veruproject.vmusix.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val darkMode: Boolean = true,
    val themeColor: String = "#A855F7",
    val downloadQuality: String = "high",
    val audioQuality: String = "auto",
    val lyricsSource: String = "auto",
    val autoCleanCache: Boolean = false,
    val scrobbleEnabled: Boolean = false,
    val lastFmSessionKey: String = "",
    val lastFmUser: String = "",
)

private val Context.dataStore by preferencesDataStore(name = "vmusix_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val LYRICS_SOURCE = stringPreferencesKey("lyrics_source")
        val AUTO_CLEAN = booleanPreferencesKey("auto_clean_cache")
        val SCROBBLE = booleanPreferencesKey("scrobble_enabled")
        val LASTFM_SK = stringPreferencesKey("lastfm_session_key")
        val LASTFM_USER = stringPreferencesKey("lastfm_user")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            darkMode = p[Keys.DARK_MODE] ?: true,
            themeColor = p[Keys.THEME_COLOR] ?: "#A855F7",
            downloadQuality = p[Keys.DOWNLOAD_QUALITY] ?: "high",
            audioQuality = p[Keys.AUDIO_QUALITY] ?: "auto",
            lyricsSource = p[Keys.LYRICS_SOURCE] ?: "auto",
            autoCleanCache = p[Keys.AUTO_CLEAN] ?: false,
            scrobbleEnabled = p[Keys.SCROBBLE] ?: false,
            lastFmSessionKey = p[Keys.LASTFM_SK] ?: "",
            lastFmUser = p[Keys.LASTFM_USER] ?: "",
        )
    }

    suspend fun setDarkMode(value: Boolean) = context.dataStore.edit { it[Keys.DARK_MODE] = value }
    suspend fun setThemeColor(value: String) = context.dataStore.edit { it[Keys.THEME_COLOR] = value }
    suspend fun setDownloadQuality(value: String) = context.dataStore.edit { it[Keys.DOWNLOAD_QUALITY] = value }
    suspend fun setAudioQuality(value: String) = context.dataStore.edit { it[Keys.AUDIO_QUALITY] = value }
    suspend fun setLyricsSource(value: String) = context.dataStore.edit { it[Keys.LYRICS_SOURCE] = value }
    suspend fun setAutoClean(value: Boolean) = context.dataStore.edit { it[Keys.AUTO_CLEAN] = value }
    suspend fun setScrobbleEnabled(value: Boolean) = context.dataStore.edit { it[Keys.SCROBBLE] = value }
    suspend fun setLastFmSession(key: String, user: String) = context.dataStore.edit {
        it[Keys.LASTFM_SK] = key
        it[Keys.LASTFM_USER] = user
    }
    suspend fun clearLastFm() = setLastFmSession("", "")
}
