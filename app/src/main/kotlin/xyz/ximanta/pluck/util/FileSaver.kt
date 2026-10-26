package xyz.ximanta.pluck.util

import android.content.Context
import android.os.Environment
import java.io.File

object FileSaver {

    private fun baseDir(context: Context, mediaType: String): File {
        val dirName = when (mediaType.lowercase()) {
            "video" -> Environment.DIRECTORY_MOVIES
            "audio" -> Environment.DIRECTORY_MUSIC
            "image" -> Environment.DIRECTORY_PICTURES
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        val root = context.getExternalFilesDir(dirName)
            ?: File(context.filesDir, dirName)
        if (!root.exists()) root.mkdirs()
        return root
    }

    fun destinationFor(context: Context, mediaType: String, desiredName: String): File {
        val dir = baseDir(context, mediaType)
        val safe = Formatters.safeFilename(desiredName, fallback = defaultName(mediaType))
        val dot = safe.lastIndexOf('.')
        val hasExt = dot in 1 until safe.length - 1
        val stem = if (hasExt) safe.substring(0, dot) else safe
        val ext = if (hasExt) safe.substring(dot) else ""

        var candidate = File(dir, safe)
        if (!candidate.exists()) return candidate

        var i = 1
        while (i <= 999) {
            candidate = File(dir, "$stem ($i)$ext")
            if (!candidate.exists()) return candidate
            i++
        }
        return File(dir, "$stem-${System.currentTimeMillis()}$ext")
    }

    fun deleteQuietly(file: File?) {
        if (file == null) return
        runCatching { if (file.exists()) file.delete() }
    }

    fun displayPath(context: Context, mediaType: String): String {
        return baseDir(context, mediaType).absolutePath
    }

    private fun defaultName(mediaType: String): String = when (mediaType.lowercase()) {
        "video" -> "video.mp4"
        "audio" -> "audio.m4a"
        "image" -> "image.jpg"
        "archive" -> "archive.zip"
        "document" -> "document.pdf"
        "code" -> "code.txt"
        "text" -> "text.txt"
        else -> "download.bin"
    }
}
