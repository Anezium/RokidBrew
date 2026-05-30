package com.rokidbrew.phone

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

private const val MEDIA_MAX_DIMENSION_PX = 1080

class MediaLoader(private val context: Context) {
    private val cache = ConcurrentHashMap<String, Drawable>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun load(assetName: String?, url: String? = null): Drawable? {
        return loadLocal(assetName) ?: loadRemote(url)
    }

    private fun loadLocal(assetName: String?): Drawable? {
        if (assetName.isNullOrBlank()) return null
        val key = "asset:$assetName"
        cache[key]?.let { return it }
        if (missing.contains(key)) return null
        return runCatching {
            context.assets.open("media/$assetName").use { input ->
                decodeSampledBitmap(input.readBytes(), MEDIA_MAX_DIMENSION_PX)?.let { bitmap ->
                    BitmapDrawable(context.resources, bitmap)
                }
            }
        }.getOrNull().also { drawable ->
            if (drawable == null) missing.add(key) else cache[key] = drawable
        }
    }

    private fun loadRemote(url: String?): Drawable? {
        if (url.isNullOrBlank()) return null
        val key = "remote:$url"
        cache[key]?.let { return it }
        if (missing.contains(key)) return null
        return runCatching {
            val file = cachedImageFile(url)
            if (!file.exists()) download(url, file)
            decodeSampledBitmap(file, MEDIA_MAX_DIMENSION_PX)?.let { bitmap ->
                BitmapDrawable(context.resources, bitmap)
            }
        }.getOrNull().also { drawable ->
            if (drawable == null) missing.add(key) else cache[key] = drawable
        }
    }

    fun hero(appId: String): Drawable? {
        val fileName = when (appId) {
            "rokid-connect-hud" -> "rokid-connect-hud.jpg"
            "dew-browser" -> "dew-browser.jpg"
            "ek-reader" -> "ek-reader.jpg"
            "ek-trans" -> "ek-trans.jpg"
            else -> null
        } ?: return null

        return load(fileName)
    }

    private fun cachedImageFile(url: String): File {
        val dir = File(context.cacheDir, "registry-media").apply { mkdirs() }
        return File(dir, url.sha256())
    }

    private fun download(url: String, output: File) {
        val temp = File(output.parentFile, "${output.name}.tmp")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 12000
        }
        connection.inputStream.use { input ->
            temp.outputStream().use { outputStream -> input.copyTo(outputStream) }
        }
        temp.renameTo(output)
        connection.disconnect()
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
