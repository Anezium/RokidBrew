package com.rokidbrew.phone

import android.content.Context
import org.json.JSONObject

private const val USER_INSTALL_CACHE_PREFS = "rokidbrew_user_install_cache"
private const val TARGET_GLASSES = "glasses"

internal data class UserInstallRecord(
    val target: String,
    val packageName: String,
    val appId: String?,
    val versionCode: Long?,
    val versionName: String?,
    val versionKnown: Boolean,
    val source: String,
    val installedAt: Long,
    val lastVerifiedAt: Long,
)

internal class UserInstallCache(context: Context) {
    private val prefs = context.getSharedPreferences(USER_INSTALL_CACHE_PREFS, Context.MODE_PRIVATE)

    fun getGlasses(packageName: String): UserInstallRecord? = get(TARGET_GLASSES, packageName)

    fun recordGlassesInstall(app: BrewApp, artifact: BrewArtifact, source: String = "rokidbrew_install") {
        val packageName = artifact.packageName?.takeIf { it.isNotBlank() } ?: return
        record(
            target = TARGET_GLASSES,
            packageName = packageName,
            appId = app.id,
            versionCode = artifact.versionCode,
            versionName = app.version.takeIf { it.isNotBlank() },
            versionKnown = true,
            source = source,
        )
    }

    fun recordGlassesDiscovered(app: BrewApp, artifact: BrewArtifact) {
        val packageName = artifact.packageName?.takeIf { it.isNotBlank() } ?: return
        record(
            target = TARGET_GLASSES,
            packageName = packageName,
            appId = app.id,
            versionCode = null,
            versionName = null,
            versionKnown = false,
            source = "cxrl_check",
        )
    }

    fun removeGlasses(packageName: String) {
        remove(TARGET_GLASSES, packageName)
    }

    private fun get(target: String, packageName: String): UserInstallRecord? {
        val raw = prefs.getString(key(target, packageName), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            UserInstallRecord(
                target = json.optString("target", target),
                packageName = json.optString("packageName", packageName),
                appId = json.optString("appId").takeIf { it.isNotBlank() },
                versionCode = json.optionalLong("versionCode"),
                versionName = json.optString("versionName").takeIf { it.isNotBlank() },
                versionKnown = json.optBoolean("versionKnown", false),
                source = json.optString("source", "unknown"),
                installedAt = json.optLong("installedAt", 0L),
                lastVerifiedAt = json.optLong("lastVerifiedAt", 0L),
            )
        }.getOrNull()
    }

    private fun record(
        target: String,
        packageName: String,
        appId: String?,
        versionCode: Long?,
        versionName: String?,
        versionKnown: Boolean,
        source: String,
    ) {
        val now = System.currentTimeMillis()
        val existing = get(target, packageName)
        val json = JSONObject()
            .put("target", target)
            .put("packageName", packageName)
            .put("versionKnown", versionKnown)
            .put("source", source)
            .put("installedAt", existing?.installedAt?.takeIf { it > 0L } ?: now)
            .put("lastVerifiedAt", now)
        appId?.let { json.put("appId", it) }
        versionCode?.let { json.put("versionCode", it) }
        versionName?.let { json.put("versionName", it) }
        prefs.edit().putString(key(target, packageName), json.toString()).apply()
    }

    private fun remove(target: String, packageName: String) {
        prefs.edit().remove(key(target, packageName)).apply()
    }

    private fun key(target: String, packageName: String): String = "$target::$packageName"
}

private fun JSONObject.optionalLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name).takeIf { it > 0L } else null
