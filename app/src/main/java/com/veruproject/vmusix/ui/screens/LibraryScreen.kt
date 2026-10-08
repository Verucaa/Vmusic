package com.veruproject.vmusix.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.veruproject.vmusix.R
import com.veruproject.vmusix.brand.BrandConfig
import com.veruproject.vmusix.ui.AppViewModel
import com.veruproject.vmusix.ui.nav.Routes

/** Tab "Lainnya": akses ke bagian perpustakaan dan pengaturan. */
@Composable
fun LibraryScreen(vm: AppViewModel, navController: NavHostController) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.nav_library),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )

        LibraryEntry(R.drawable.ic_like, stringResource(R.string.library_liked), stringResource(R.string.library_liked_desc)) {
            navController.navigate(Routes.detail("liked", "all"))
        }
        LibraryEntry(R.drawable.ic_download, stringResource(R.string.library_downloads), stringResource(R.string.library_downloads_desc)) {
            navController.navigate(Routes.DOWNLOADS)
        }
        LibraryEntry(R.drawable.ic_playlist_add, stringResource(R.string.library_playlists), stringResource(R.string.library_playlists_desc)) {
            navController.navigate(Routes.PLAYLISTS)
        }
        LibraryEntry(R.drawable.ic_charts, stringResource(R.string.library_charts), stringResource(R.string.library_charts_desc)) {
            navController.navigate(Routes.detail("charts", "all"))
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LibraryEntry(R.drawable.ic_clear, stringResource(R.string.library_cache), stringResource(R.string.library_cache_desc)) {
            navController.navigate(Routes.CACHE)
        }
        LibraryEntry(R.drawable.ic_settings, stringResource(R.string.library_settings), stringResource(R.string.library_settings_desc)) {
            navController.navigate(Routes.SETTINGS)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.about_version, BrandConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun LibraryEntry(icon: Int, title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
