package com.focus.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.min

/** Imported images live in app-private storage, so they still work after the picker grant expires. */
object WallpaperStorage {
    private const val MAX_WIDTH = 1440
    private const val MAX_HEIGHT = 3200

    fun file(context: Context, id: String): File = File(File(context.filesDir, "wallpapers"), "$id.webp")

    suspend fun import(context: Context, uri: Uri): ImportedWallpaper = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not an image" }

        var sample = 1
        while (bounds.outWidth / sample > MAX_WIDTH || bounds.outHeight / sample > MAX_HEIGHT) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Could not read the selected image")

        val rotation = try {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) { 0f }
        val oriented = if (rotation == 0f) decoded else Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true,
        ).also { decoded.recycle() }
        val scale = min(1f, min(MAX_WIDTH.toFloat() / oriented.width, MAX_HEIGHT.toFloat() / oriented.height))
        val bitmap = if (scale == 1f) oriented else Bitmap.createScaledBitmap(
            oriented, (oriented.width * scale).toInt().coerceAtLeast(1), (oriented.height * scale).toInt().coerceAtLeast(1), true,
        ).also { oriented.recycle() }

        val id = UUID.randomUUID().toString()
        val output = file(context, id)
        output.parentFile?.mkdirs()
        try {
            output.outputStream().use { stream ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                check(bitmap.compress(format, 94, stream)) { "Could not save the wallpaper" }
            }
            ImportedWallpaper(id, displayName(context, uri), darkAtTop(bitmap))
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        val raw = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null }
        return raw?.substringBeforeLast('.')?.trim()?.take(48)?.ifBlank { null } ?: "Imported wallpaper"
    }

    private fun darkAtTop(bitmap: Bitmap): Boolean {
        var luminance = 0.0
        var count = 0
        for (y in 0 until 8) for (x in 0 until 20) {
            val pixel = bitmap.getPixel((x * bitmap.width / 20).coerceAtMost(bitmap.width - 1),
                (y * bitmap.height / 80).coerceAtMost(bitmap.height - 1))
            luminance += (0.2126 * android.graphics.Color.red(pixel) +
                0.7152 * android.graphics.Color.green(pixel) + 0.0722 * android.graphics.Color.blue(pixel)) / 255.0
            count++
        }
        return luminance / count < 0.5
    }
}
