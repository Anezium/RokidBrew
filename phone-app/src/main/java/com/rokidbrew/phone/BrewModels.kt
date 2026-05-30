package com.rokidbrew.phone

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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

data class BrewListing(
    val about: String?,
    val descriptionMarkdown: String?,
)

data class BrewRelease(
    val version: String?,
    val date: String?,
    val sourceReleaseUrl: String?,
    val notes: String?,
    val changes: List<String>,
)

data class BrewApp(
    val id: String,
    val name: String,
    val category: String,
    val type: String,
    val version: String,
    val summary: String,
    val description: String,
    val author: String,
    val sourceUrl: String?,
    val iconAsset: String?,
    val iconUrl: String?,
    val screenshotAssets: List<String>,
    val screenshotUrls: List<String>,
    val featured: Boolean,
    val featuredRank: Int?,
    val publishedAt: String?,
    val newUntil: String?,
    val isNew: Boolean,
    val phoneRequired: Boolean,
    val artifacts: List<BrewArtifact>,
    val listing: BrewListing?,
    val releases: List<BrewRelease>,
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
    fun isFeatured(): Boolean = featured || featuredRank != null
    fun aboutText(): String = listing?.about?.takeIf { it.isNotBlank() }
        ?: listing?.descriptionMarkdown?.takeIf { it.isNotBlank() }
        ?: description
    fun screenshotAt(index: Int): BrewScreenshot = BrewScreenshot(
        assetName = screenshotAssets.getOrNull(index),
        url = screenshotUrls.getOrNull(index),
    )
}

data class BrewIndexRefresh(
    val apps: List<BrewApp>,
    val sourceUrl: String,
    val brewVersion: String?,
    val brewVersionCode: Long?,
    val brewApkUrl: String?,
    val brewReleaseUrl: String?,
    val brewNotes: String?,
    val brewChanges: List<String>,
)

private data class BrewIndexRaw(
    val json: JSONObject,
    val apps: List<BrewApp>,
    val brewVersion: String?,
    val brewVersionCode: Long?,
    val brewApkUrl: String?,
    val brewReleaseUrl: String?,
    val brewNotes: String?,
    val brewChanges: List<String>,
)

object BrewIndex {
    private val remoteUrls = listOf(
        BuildConfig.ROKIDBREW_REGISTRY_URL,
    )

    fun loadInitial(context: Context): List<BrewApp> {
        val bundled = loadBundled(context)
        val cached = loadCached(context)
        return mergeBundledMedia(cached, bundled).ifEmpty { bundled }
    }

    fun loadBundled(context: Context): List<BrewApp> {
        val raw = context.assets.open("apps.json").bufferedReader().use { it.readText() }
        return parse(raw).apps
    }

    fun loadCached(context: Context): List<BrewApp> {
        val file = cacheFile(context)
        if (!file.exists()) return emptyList()
        return runCatching { parse(file.readText()).apps }.getOrDefault(emptyList())
    }

    suspend fun refresh(context: Context): BrewIndexRefresh = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (url in remoteUrls) {
            runCatching {
                val raw = fetch(url)
                val parsed = parse(raw)
                val apps = mergeBundledMedia(parsed.apps, loadBundled(context))
                require(apps.isNotEmpty()) { "Remote registry is empty" }
                cacheFile(context).writeText(raw)
                return@withContext BrewIndexRefresh(
                    apps = apps,
                    sourceUrl = url,
                    brewVersion = parsed.brewVersion,
                    brewVersionCode = parsed.brewVersionCode,
                    brewApkUrl = parsed.brewApkUrl,
                    brewReleaseUrl = parsed.brewReleaseUrl,
                    brewNotes = parsed.brewNotes,
                    brewChanges = parsed.brewChanges,
                )
            }.onFailure { error ->
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("No registry endpoint available")
    }

    private fun cacheFile(context: Context): File {
        val suffix = Integer.toHexString(BuildConfig.ROKIDBREW_REGISTRY_URL.hashCode())
        return File(context.filesDir, "apps.$suffix.json")
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

    private fun mergeBundledMedia(apps: List<BrewApp>, bundled: List<BrewApp>): List<BrewApp> {
        if (apps.isEmpty()) return emptyList()
        val bundledById = bundled.associateBy { it.id }
        return apps.map { app ->
            val bundledApp = bundledById[app.id] ?: return@map app
            app.copy(
                author = app.author.takeUnless { it == "Unknown" } ?: bundledApp.author,
                sourceUrl = app.sourceUrl ?: bundledApp.sourceUrl,
                iconAsset = app.iconAsset ?: bundledApp.iconAsset,
                iconUrl = app.iconUrl ?: bundledApp.iconUrl,
                screenshotAssets = app.screenshotAssets.ifEmpty { bundledApp.screenshotAssets },
                screenshotUrls = app.screenshotUrls.ifEmpty { bundledApp.screenshotUrls },
                listing = app.listing ?: bundledApp.listing,
                releases = app.releases.ifEmpty { bundledApp.releases },
            )
        }
    }

    private fun parse(raw: String): BrewIndexRaw {
        val root = JSONObject(raw)
        val appsArray = root.getJSONArray("apps")
        val apps = buildList {
            for (i in 0 until appsArray.length()) {
                val app = appsArray.getJSONObject(i)
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
                val sourceUrl = app.optString("sourceUrl").takeIf { it.isNotBlank() } ?: artifacts.inferredSourceUrl()
                val publishedAt = app.optString("publishedAt").takeIf { it.isNotBlank() }
                val newUntil = app.optString("newUntil").takeIf { it.isNotBlank() }
                val listing = app.listing()
                add(
                    BrewApp(
                        id = app.getString("id"),
                        name = app.getString("name"),
                        category = app.getString("category"),
                        type = app.getString("type"),
                        version = app.getString("version"),
                        summary = app.getString("summary"),
                        description = app.optString("description", app.getString("summary")),
                        author = app.optString("author").takeIf { it.isNotBlank() } ?: sourceUrl.inferredAuthor(),
                        sourceUrl = sourceUrl,
                        iconAsset = app.optString("iconAsset").takeIf { it.isNotBlank() },
                        iconUrl = app.optString("iconUrl").takeIf { it.isNotBlank() },
                        screenshotAssets = app.screenshotAssets(),
                        screenshotUrls = app.screenshotUrls(),
                        featured = app.optBoolean("featured", false),
                        featuredRank = app.optionalInt("featuredRank"),
                        publishedAt = publishedAt,
                        newUntil = newUntil,
                        isNew = isNewApp(publishedAt, newUntil),
                        phoneRequired = app.optBoolean("phoneRequired", false),
                        artifacts = artifacts,
                        listing = listing,
                        releases = app.releases(),
                    ),
                )
            }
        }
        return BrewIndexRaw(
            json = root,
            apps = apps,
            brewVersion = root.optString("brewVersion").takeIf { it.isNotBlank() },
            brewVersionCode = root.optLong("brewVersionCode").takeIf { it > 0L },
            brewApkUrl = root.optString("brewApkUrl").takeIf { it.isNotBlank() },
            brewReleaseUrl = root.optString("brewReleaseUrl").takeIf { it.isNotBlank() },
            brewNotes = root.optString("brewNotes").takeIf { it.isNotBlank() },
            brewChanges = root.stringList("brewChanges"),
        )
    }

    private fun List<BrewArtifact>.inferredSourceUrl(): String? {
        val url = firstOrNull()?.url ?: return null
        val rawGithub = Regex("""https://raw\.githubusercontent\.com/([^/]+)/([^/]+)/([^/]+)/(.+)/[^/]+""").find(url)
        if (rawGithub != null) {
            val branch = rawGithub.groupValues[3].takeUnless { it.equals("HEAD", ignoreCase = true) } ?: "main"
            return "https://github.com/${rawGithub.groupValues[1]}/${rawGithub.groupValues[2]}/tree/$branch/${rawGithub.groupValues[4]}"
        }

        val github = Regex("""https://github\.com/([^/]+)/([^/]+)""").find(url) ?: return url
        return "https://github.com/${github.groupValues[1]}/${github.groupValues[2]}"
    }

    private fun String?.inferredAuthor(): String {
        if (this.isNullOrBlank()) return "Unknown"
        val owner = Regex("""https://github\.com/([^/]+)""").find(this)?.groupValues?.getOrNull(1)
        return owner ?: URL(this).host.removePrefix("www.")
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

    private fun JSONObject.listing(): BrewListing? {
        val listing = optJSONObject("listing") ?: return null
        val about = listing.optString("about").takeIf { it.isNotBlank() }
        val descriptionMarkdown = listing.optString("descriptionMarkdown").takeIf { it.isNotBlank() }
        if (about == null && descriptionMarkdown == null) return null
        return BrewListing(
            about = about,
            descriptionMarkdown = descriptionMarkdown,
        )
    }

    private fun JSONObject.releases(): List<BrewRelease> {
        val releases = optJSONArray("releases") ?: return emptyList()
        return buildList {
            for (i in 0 until releases.length()) {
                val release = releases.optJSONObject(i) ?: continue
                add(
                    BrewRelease(
                        version = release.optString("version").takeIf { it.isNotBlank() },
                        date = release.optString("date").takeIf { it.isNotBlank() },
                        sourceReleaseUrl = release.optString("sourceReleaseUrl").takeIf { it.isNotBlank() },
                        notes = release.optString("notes").takeIf { it.isNotBlank() }
                            ?: release.optString("notesMarkdown").takeIf { it.isNotBlank() },
                        changes = release.stringList("changes"),
                    ),
                )
            }
        }
    }

    private fun JSONObject.stringList(name: String): List<String> {
        val values = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (i in 0 until values.length()) {
                values.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONObject.optionalInt(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return optInt(name).takeIf { it >= 0 }
    }

    private fun isNewApp(publishedAt: String?, newUntil: String?): Boolean {
        val now = System.currentTimeMillis()
        parseRegistryTime(newUntil)?.let { return now <= it }
        val published = parseRegistryTime(publishedAt) ?: return false
        val newWindowMillis = 2L * 24L * 60L * 60L * 1000L
        return now >= published && now - published <= newWindowMillis
    }

    private fun parseRegistryTime(value: String?): Long? {
        val raw = value?.takeIf { it.isNotBlank() } ?: return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd",
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(raw)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}
