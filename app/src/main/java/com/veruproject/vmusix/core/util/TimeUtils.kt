package com.veruproject.vmusix.core.util

import com.veruproject.vmusix.domain.model.LyricLine

/** Format milidetik → mm:ss / h:mm:ss untuk posisi pemutar. */
fun formatPosition(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Format detik → mm:ss untuk baris daftar lagu. */
fun formatDuration(seconds: Long): String = formatPosition(seconds * 1000)

/** Parse teks LRC menjadi baris lirik ber-timestamp. */
fun parseLrc(text: String): List<LyricLine> {
    val regex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    val lines = mutableListOf<LyricLine>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val matches = regex.findAll(line).toList()
        if (matches.isEmpty()) continue
        val content = line.substring(matches.last().range.last + 1).trim()
        if (content.isEmpty()) continue
        for (m in matches) {
            val min = m.groupValues[1].toLongOrNull() ?: continue
            val sec = m.groupValues[2].toLongOrNull() ?: continue
            val frac = m.groupValues[3]
            val millis = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> (frac.take(3).toLongOrNull() ?: 0)
            }
            lines += LyricLine(min * 60_000 + sec * 1000 + millis, content)
        }
    }
    return lines.sortedBy { it.timeMs ?: 0L }
}

/** Ubah teks biasa (pisah baris) menjadi lirik tanpa sinkronisasi. */
fun plainToLyrics(text: String): List<LyricLine> =
    text.lineSequence().map { LyricLine(null, it.trim()) }.filter { it.text.isNotEmpty() }.toList()

/** Hanya lirik yang mengandung timestamp dianggap LRC valid. */
fun textIsSyncedLrc(text: String): Boolean = text.contains(Regex("""\[\d{1,3}:\d{2}"""))
