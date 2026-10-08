package com.veruproject.vmusix.brand

import androidx.compose.ui.graphics.Color

/**
 * Satu tempat untuk seluruh identitas aplikasi.
 * Ubah nilai di sini untuk mengganti nama, warna, dan credit
 * tanpa perlu menyentuh kode UI lain.
 *
 * Catatan: label nama di icon launcher dibaca dari
 * res/values/strings.xml (app_name) — ubah juga di sana.
 */
object BrandConfig {
    const val APP_NAME = "Vmusix"
    const val APP_ICON = "assets/app_icon.png"
    const val DEVELOPER_NAME = "VeruProject"
    const val TAGLINE = "Listen Without Limits"
    const val VERSION_NAME = "1.0.0"

    // Warna resmi Vmusix
    val PRIMARY_COLOR = Color(0xFF6D28D9)   // ungu
    val SECONDARY_COLOR = Color(0xFF2563EB) // biru
    val BACKGROUND_COLOR = Color(0xFF09090B) // hitam AMOLED
    val CARD_COLOR = Color(0xFF18181B)
    val TEXT_COLOR = Color(0xFFFFFFFF)
    val ACCENT_COLOR = Color(0xFFA855F7)

    // Pilihan warna tema yang ditampilkan di Pengaturan
    val THEME_COLORS = listOf(
        Color(0xFFA855F7), // ungu (default)
        Color(0xFF2563EB), // biru
        Color(0xFFEC4899), // pink
        Color(0xFF10B981), // hijau
        Color(0xFFF59E0B), // oranye
    )
}
