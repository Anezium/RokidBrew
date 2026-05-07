package com.rokidbrew.phone

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

object PhonePackageInstallHelper {
    fun requestInstall(activity: Activity, apkFile: File, onStatus: (String) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            onStatus("Allow installs from RokidBrew Phone, then tap install again.")
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            return false
        }
        return startInstaller(activity, apkFile, onStatus)
    }

    private fun startInstaller(context: Context, apkFile: File, onStatus: (String) -> Unit): Boolean {
        return runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setSize(apkFile.length()) }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite("package.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val intent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, PhoneInstallResultReceiver::class.java),
                    flags,
                )
                session.commit(intent.intentSender)
            }
            onStatus("Phone install session committed. Waiting for confirmation...")
        }.onFailure { error ->
            onStatus("Phone PackageInstaller failed: ${error.message ?: error.javaClass.simpleName}")
        }.isSuccess
    }
}
