package com.rokidbrew.phone

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class BrewArtifact(
    val target: String,
    val url: String,
    val sha256: String?,
    val sizeBytes: Long?,
    val packageName: String?,
    val versionCode: Long?,
)

data class BrewScreenshot(
    val assetName: String?,
    val url: String?,
)

data class BrewApp(
    val id: String,
    val name: String,
    val category: String,
    val type: String,
    val version: String,
    val summary: String,
    val description: String,
    val iconAsset: String?,
    val iconUrl: String?,
    val screenshotAssets: List<String>,
    val screenshotUrls: List<String>,
    val phoneRequired: Boolean,
    val artifacts: List<BrewArtifact>,
) {
    val screenshotAsset: String?
        get() = screenshotAssets.firstOrNull()
    val screenshotUrl: String?
        get() = screenshotUrls.firstOrNull()
    val screenshotCount: Int
        get() = maxOf(screenshotAssets.size, screenshotUrls.size)

    fun artifactFor(target: String): BrewArtifact? = artifacts.firstOrNull { it.target == target }
    fun hasTarget(target: String): Boolean = artifactFor(target) != null
    fun isPhoneSection(): Boolean = type == "combo" || type == "phone" || hasTarget("phone") || phoneRequired
    fun screenshotAt(index: Int): BrewScreenshot = BrewScreenshot(
        assetName = screenshotAssets.getOrNull(index),
        url = screenshotUrls.getOrNull(index),
    )
}

data class BrewIndexRefresh(
    val apps: List<BrewApp>,
    val sourceUrl: String,
)

object BrewIndex {
    private const val CACHE_FILE = "apps.v1.json"
    private val remoteUrls = listOf(
        "https://raw.githubusercontent.com/Anezium/RokidBrew-Registry/main/dist/apps.v1.json",
        "https://anezium.github.io/RokidBrew-Registry/apps.v1.json",
    )

    fun loadInitial(context: Context): List<BrewApp> {
        return loadCached(context).ifEmpty { loadBundled(context) }
    }

    fun loadBundled(context: Context): List<BrewApp> {
        val raw = context.assets.open("apps.json").bufferedReader().use { it.readText() }
        return parse(raw)
    }

    fun loadCached(context: Context): List<BrewApp> {
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.exists()) return emptyList()
        return runCatching { parse(file.readText()) }.getOrDefault(emptyList())
    }

    suspend fun refresh(context: Context): BrewIndexRefresh = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (url in remoteUrls) {
            runCatching {
                val raw = fetch(url)
                val apps = parse(raw)
                require(apps.isNotEmpty()) { "Remote registry is empty" }
                File(context.filesDir, CACHE_FILE).writeText(raw)
                return@withContext BrewIndexRefresh(apps = apps, sourceUrl = url)
            }.onFailure { error ->
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("No registry endpoint available")
    }

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 9000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }.also {
            connection.disconnect()
        }
    }

    private fun parse(raw: String): List<BrewApp> {
        val root = JSONObject(raw)
        val apps = root.getJSONArray("apps")
        return buildList {
            for (i in 0 until apps.length()) {
                val app = apps.getJSONObject(i)
                val artifactsJson = app.getJSONArray("artifacts")
                val artifacts = buildList {
                    for (j in 0 until artifactsJson.length()) {
                        val artifact = artifactsJson.getJSONObject(j)
                        add(
                            BrewArtifact(
                                target = artifact.getString("target"),
                                url = artifact.getString("url"),
                                sha256 = artifact.optString("sha256").takeIf { it.isNotBlank() },
                                sizeBytes = artifact.optLong("sizeBytes").takeIf { it > 0L },
                                packageName = artifact.optString("packageName").takeIf { it.isNotBlank() },
                                versionCode = artifact.optLong("versionCode").takeIf { it > 0L },
                            ),
                        )
                    }
                }
                add(
                    BrewApp(
                        id = app.getString("id"),
                        name = app.getString("name"),
                        category = app.getString("category"),
                        type = app.getString("type"),
                        version = app.getString("version"),
                        summary = app.getString("summary"),
                        description = app.optString("description", app.getString("summary")),
                        iconAsset = app.optString("iconAsset").takeIf { it.isNotBlank() },
                        iconUrl = app.optString("iconUrl").takeIf { it.isNotBlank() },
                        screenshotAssets = app.screenshotAssets(),
                        screenshotUrls = app.screenshotUrls(),
                        phoneRequired = app.optBoolean("phoneRequired", false),
                        artifacts = artifacts,
                    ),
                )
            }
        }
    }

    private fun JSONObject.screenshotAssets(): List<String> {
        val assets = optJSONArray("screenshotAssets")
        if (assets != null) {
            return buildList {
                for (i in 0 until assets.length()) {
                    assets.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        return optString("screenshotAsset").takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
    }

    private fun JSONObject.screenshotUrls(): List<String> {
        val urls = optJSONArray("screenshotUrls") ?: return emptyList()
        return buildList {
            for (i in 0 until urls.length()) {
                urls.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
