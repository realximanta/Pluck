package xyz.ximanta.pluck.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object FileSaver {

    /**
     * Saves a file from the app's cache to the public directories (Movies/Pluck, Music/Pluck, etc.)
     * using MediaStore on API 29+ and direct file writing on lower APIs.
     */
    fun saveToPublic(context: Context, tempFile: File, mediaType: String, displayName: String): String {
        val type = mediaType.lowercase()
        val mimeType = getMimeType(type)
        val folder = getFolderName(type)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Pluck")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = when (type) {
                "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(tempFile).use { input -> input.copyTo(out) }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    return "$folder/Pluck/$displayName"
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val publicDir = Environment.getExternalStoragePublicDirectory(folder)
            val appDir = File(publicDir, "Pluck")
            if (!appDir.exists()) appDir.mkdirs()
            val destFile = File(appDir, displayName)
            try {
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return destFile.absolutePath
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        // Fallback if everything fails
        return tempFile.absolutePath
    }

    fun deleteQuietly(file: File?) {
        if (file == null) return
        runCatching { if (file.exists()) file.delete() }
    }

    private fun getFolderName(type: String): String {
        return when (type) {
            "video" -> Environment.DIRECTORY_MOVIES
            "audio" -> Environment.DIRECTORY_MUSIC
            "image" -> Environment.DIRECTORY_PICTURES
            else -> Environment.DIRECTORY_DOWNLOADS
        }
    }

    private fun getMimeType(type: String): String {
        return when (type) {
            "video" -> "video/mp4"
            "audio" -> "audio/mp4"
            "image" -> "image/jpeg"
            "archive" -> "application/zip"
            "document" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
