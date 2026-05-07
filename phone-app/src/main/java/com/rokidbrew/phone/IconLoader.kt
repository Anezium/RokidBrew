package com.rokidbrew.phone

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class IconLoader(private val context: Context) {
    private val cache = ConcurrentHashMap<String, Drawable>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun load(appId: String, iconUrl: String? = null): Drawable? {
        return loadLocal(appId) ?: loadRemote(iconUrl)
    }

    private fun loadLocal(appId: String): Drawable? {
        val key = "asset:$appId"
        cache[key]?.let { return it }
        if (missing.contains(key)) return null
        return runCatching {
            context.assets.open("icons/$appId.png").use { input ->
                BitmapDrawable(context.resources, BitmapFactory.decodeStream(input))
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
            BitmapDrawable(context.resources, BitmapFactory.decodeFile(file.absolutePath))
        }.getOrNull().also { drawable ->
            if (drawable == null) missing.add(key) else cache[key] = drawable
        }
    }

    private fun cachedImageFile(url: String): File {
        val dir = File(context.cacheDir, "registry-icons").apply { mkdirs() }
        return File(dir, url.sha256())
    }

    private fun download(url: String, output: File) {
        val temp = File(output.parentFile, "${output.name}.tmp")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 10000
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
