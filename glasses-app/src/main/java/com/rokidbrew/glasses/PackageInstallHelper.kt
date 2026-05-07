package com.rokidbrew.glasses

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

object PackageInstallHelper {
    const val ACTION_INSTALL_STATUS = "com.rokidbrew.glasses.INSTALL_STATUS"
    const val EXTRA_MESSAGE = "message"

    fun requestInstall(activity: Activity, apkFile: File, onStatus: (String) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            PendingInstallStore.save(activity, apkFile.absolutePath)
            onStatus("Allow unknown apps for RokidBrew, then return.")
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            return false
        }
        return startPackageInstaller(activity, apkFile, onStatus)
    }

    fun resumePending(activity: Activity, onStatus: (String) -> Unit): Boolean {
        val path = PendingInstallStore.get(activity) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) return false

        val file = File(path)
        if (!file.exists()) {
            PendingInstallStore.clear(activity)
            onStatus("Pending APK disappeared. Download it again.")
            return false
        }
        return startPackageInstaller(activity, file, onStatus)
    }

    private fun startPackageInstaller(context: Context, apkFile: File, onStatus: (String) -> Unit): Boolean {
        return runCatching {
            PendingInstallStore.save(context, apkFile.absolutePath)
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
                    Intent(context, InstallResultReceiver::class.java),
                    flags,
                )
                session.commit(intent.intentSender)
            }
            onStatus("Installer opened. Confirm on glasses.")
        }.isSuccess
    }
}
