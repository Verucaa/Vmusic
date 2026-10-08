package com.veruproject.vmusix.core.util

import java.io.File

/** Ukuran total sebuah folder (rekursif) dalam byte. */
fun dirSize(file: File): Long {
    if (file.isFile) return file.length()
    var total = 0L
    file.listFiles()?.forEach { total += dirSize(it) }
    return total
}

/** Jumlah berkas dalam folder (rekursif). */
fun dirFileCount(file: File): Int {
    if (file.isFile) return 1
    var count = 0
    file.listFiles()?.forEach { count += dirFileCount(it) }
    return count
}

/** Format byte → KB/MB/GB untuk ditampilkan ke pengguna. */
fun humanSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> "%.2f GB".format(bytes / gb)
        bytes >= mb -> "%.1f MB".format(bytes / mb)
        bytes >= kb -> "%.0f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}
