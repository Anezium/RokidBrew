package com.rokidbrew.glasses

import android.content.Context

object PendingInstallStore {
    private const val PREFS = "rokidbrew_pending_install"
    private const val KEY_PATH = "path"

    fun save(context: Context, path: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PATH, path)
            .apply()
    }

    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PATH, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
