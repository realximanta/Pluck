package xyz.ximanta.pluck.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {

    fun humanBytes(bytes: Long): String {
        if (bytes <= 0L) return "—"
        val kb = 1024.0
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= kb && index < units.lastIndex) {
            value /= kb
            index++
        }
        return if (index == 0) "${bytes} B"
        else String.format(Locale.US, "%.1f %s", value, units[index])
    }

    fun formatDuration(totalSeconds: Int): String {
        if (totalSeconds <= 0) return ""
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun relativeTime(epochSeconds: Long): String {
        if (epochSeconds <= 0L) return ""
        val now = System.currentTimeMillis() / 1000L
        val diff = now - epochSeconds
        return when {
            diff < 60L -> "just now"
            diff < 3600L -> "${diff / 60L}m ago"
            diff < 86400L -> "${diff / 3600L}h ago"
            diff < 604800L -> "${diff / 86400L}d ago"
            else -> {
                val fmt = SimpleDateFormat("d MMM yyyy", Locale.US)
                fmt.format(Date(epochSeconds * 1000L))
            }
        }
    }

    fun expiresLabel(epochSeconds: Long): String {
        if (epochSeconds <= 0L) return "Never"
        val now = System.currentTimeMillis() / 1000L
        if (epochSeconds <= now) return "Expired"
        val days = TimeUnit.SECONDS.toDays(epochSeconds - now)
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.US)
        val dateStr = fmt.format(Date(epochSeconds * 1000L))
        return if (days >= 1L) "$dateStr · ${days}d left" else dateStr
    }

    fun progressPercent(read: Long, total: Long?): Int? {
        if (total == null || total <= 0L) return null
        return ((read.coerceAtMost(total) * 100L) / total).toInt()
    }

    fun safeFilename(raw: String, fallback: String = "download"): String {
        var name = raw.trim()
            .replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim('.', ' ')
        if (name.isBlank()) name = fallback
        if (name.length > 200) {
            val dot = name.lastIndexOf('.')
            name = if (dot in 1 until name.length - 1) {
                val ext = name.substring(dot)
                name.substring(0, 200 - ext.length) + ext
            } else name.substring(0, 200)
        }
        return name
    }

    fun extensionOf(filename: String): String {
        val dot = filename.lastIndexOf('.')
        return if (dot in 1 until filename.length - 1)
            filename.substring(dot + 1).lowercase(Locale.US)
        else ""
    }
}
