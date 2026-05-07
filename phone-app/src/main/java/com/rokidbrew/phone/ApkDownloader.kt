package com.rokidbrew.phone

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ApkDownloader(private val context: Context) {
    suspend fun download(url: String, label: String, expectedSha256: String? = null, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val safeName = label.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(context.cacheDir, safeName)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 120_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "RokidBrew/0.1")
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val total = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0L) onProgress(((copied * 100L) / total).toInt())
                    }
                }
            }
            expectedSha256?.takeIf { it.isNotBlank() }?.let { expected ->
                val actual = target.sha256()
                require(actual.equals(expected, ignoreCase = true)) {
                    "Checksum mismatch for $safeName"
                }
            }
            target
        }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
