package com.rokidbrew.glasses

import android.content.Context
import org.json.JSONObject

data class BrewArtifact(
    val target: String,
    val url: String,
    val sha256: String?,
    val sizeBytes: Long?,
    val packageName: String?,
    val versionCode: Long?,
)

data class BrewApp(
    val id: String,
    val name: String,
    val category: String,
    val type: String,
    val version: String,
    val summary: String,
    val phoneRequired: Boolean,
    val artifacts: List<BrewArtifact>,
) {
    fun artifactFor(target: String): BrewArtifact? = artifacts.firstOrNull { it.target == target }
}

object BrewIndex {
    fun load(context: Context): List<BrewApp> {
        val raw = context.assets.open("apps.json").bufferedReader().use { it.readText() }
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
                        phoneRequired = app.optBoolean("phoneRequired", false),
                        artifacts = artifacts,
                    ),
                )
            }
        }
    }
}
