package com.rokidbrew.phone

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rokid.cxr.link.CXRLink
import com.rokid.cxr.link.callbacks.ICXRLinkCbk
import com.rokid.cxr.link.callbacks.IGlassAppCbk
import com.rokid.cxr.link.utils.CxrDefs
import com.rokid.sprite.aiapp.externalapp.auth.AuthResult
import com.rokid.sprite.aiapp.externalapp.auth.AuthorizationHelper
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class CxrLHiRokidSession(
    private val activity: AppCompatActivity,
    private val onStatus: (String) -> Unit,
    private val onBusyChanged: (Boolean) -> Unit,
) {
    companion object {
        const val AUTH_REQUEST_CODE = 4027

        private const val GLOBAL_AI_APP_PACKAGE = "com.rokid.sprite.global.aiapp"
        private const val AUTH_ACTIVITY_CLASS = "com.rokid.sprite.aiapp.externalapp.auth.AuthorizationActivity"
        private const val AUTH_ACTION = "com.rokid.sprite.aiapp.externalapp.AUTHORIZATION"
        private const val MEDIA_SERVICE_ACTION = "com.rokid.sprite.aiapp.externalapp.MEDIA_STREAM_SERVICE"
        private const val AUTH_TOKEN_EXTRA = "auth_token"
    }

    private var token: String? = null
    private var cxrLink: CXRLink? = null
    private var pendingUpload: File? = null
    private var pendingInstallResult: ((Boolean) -> Unit)? = null
    private var pendingQueryPackage: String? = null
    private var queryQueue: ArrayDeque<String> = ArrayDeque()
    private var onQueryResult: ((String, Boolean) -> Unit)? = null
    private var onQueryComplete: (() -> Unit)? = null
    private var cxrlConnected = false
    private var glassBtConnected = false
    private var uploadStarted = false
    private var queryStarted = false
    private var timeoutJob: Job? = null

    fun hasAuthorization(): Boolean = !token.isNullOrBlank()

    fun requestAuthorization() {
        if (!isGlobalHiRokidInstalled()) {
            onStatus("Install global Hi Rokid first.")
            return
        }

        runCatching {
            val intent = Intent().setComponent(ComponentName(GLOBAL_AI_APP_PACKAGE, AUTH_ACTIVITY_CLASS))
            activity.startActivityForResult(intent, AUTH_REQUEST_CODE)
        }.recoverCatching {
            val fallback = Intent(AUTH_ACTION).setPackage(GLOBAL_AI_APP_PACKAGE)
            activity.startActivityForResult(fallback, AUTH_REQUEST_CODE)
        }.onSuccess {
            onStatus("Authorization opened in Hi Rokid.")
        }.onFailure { error ->
            onStatus("Failed to open Hi Rokid authorization: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun handleAuthorizationResult(resultCode: Int, data: Intent?) {
        when (val result = AuthorizationHelper.INSTANCE.parseAuthorizationResult(resultCode, data)) {
            is AuthResult.AuthSuccess -> {
                token = result.token
                onStatus("Hi Rokid authorization token received.")
            }

            is AuthResult.AuthCancel -> onStatus("Hi Rokid authorization cancelled.")
            is AuthResult.AuthFail -> onStatus("Hi Rokid authorization failed.")
        }
    }

    fun installApk(apkFile: File, onInstallResult: ((Boolean) -> Unit)? = null) {
        if (!isGlobalHiRokidInstalled()) {
            onStatus("Install global Hi Rokid on this phone first.")
            onInstallResult?.invoke(false)
            return
        }
        if (!isWifiEnabled()) {
            onStatus("Turn on phone Wi-Fi first. Hi Rokid needs it for the glasses hotspot.")
            onInstallResult?.invoke(false)
            return
        }
        val authToken = token
        if (authToken.isNullOrBlank()) {
            onStatus("Press Authorize Hi Rokid first.")
            requestAuthorization()
            onInstallResult?.invoke(false)
            return
        }

        onBusyChanged(true)
        runCatching {
            val packageName = readPackageName(apkFile)
            onStatus("Detected package: $packageName")
            connectAndUpload(authToken, packageName, apkFile, onInstallResult)
        }.onFailure { error ->
            onStatus("CXR-L failed: ${error.message ?: error.javaClass.simpleName}")
            onBusyChanged(false)
            onInstallResult?.invoke(false)
        }
    }

    fun queryInstalledApps(
        packageNames: List<String>,
        onResult: (String, Boolean) -> Unit,
        onComplete: () -> Unit,
    ) {
        if (packageNames.isEmpty()) {
            onComplete()
            return
        }
        if (!isGlobalHiRokidInstalled()) {
            onStatus("Install global Hi Rokid on this phone first.")
            onComplete()
            return
        }
        if (!isWifiEnabled()) {
            onStatus("Turn on phone Wi-Fi first. Hi Rokid needs it for the glasses hotspot.")
            onComplete()
            return
        }
        val authToken = token
        if (authToken.isNullOrBlank()) {
            onStatus("Press Authorize Hi Rokid first.")
            requestAuthorization()
            onComplete()
            return
        }

        cleanup()
        queryQueue = ArrayDeque(packageNames.distinct())
        onQueryResult = onResult
        onQueryComplete = onComplete
        onBusyChanged(true)
        queryNext(authToken)
    }

    fun cleanup() {
        timeoutJob?.cancel()
        timeoutJob = null
        runCatching { cxrLink?.disconnect() }
        cxrLink = null
        pendingUpload = null
        pendingInstallResult = null
        pendingQueryPackage = null
        cxrlConnected = false
        glassBtConnected = false
        uploadStarted = false
        queryStarted = false
    }

    private fun connectAndUpload(
        authToken: String,
        packageName: String,
        apkFile: File,
        onInstallResult: ((Boolean) -> Unit)?,
    ) {
        cleanup()
        val link = CXRLink(activity.applicationContext).also { newLink ->
            newLink.setCXRLinkCbk(object : ICXRLinkCbk {
                override fun onCXRLConnected(connected: Boolean) {
                    activity.runOnUiThread {
                        cxrlConnected = connected
                        onStatus("CXR-L service connected: $connected")
                        maybeUploadPending()
                    }
                }

                override fun onGlassBtConnected(connected: Boolean) {
                    activity.runOnUiThread {
                        glassBtConnected = connected
                        onStatus("Glasses Bluetooth connected: $connected")
                        maybeUploadPending()
                    }
                }

                override fun onGlassAiAssistStart() = Unit
                override fun onGlassAiAssistStop() = Unit
            })
            cxrLink = newLink
            newLink
        }

        pendingUpload = apkFile
        pendingInstallResult = onInstallResult
        pendingQueryPackage = null
        cxrlConnected = false
        glassBtConnected = false
        uploadStarted = false
        queryStarted = false
        timeoutJob = activity.lifecycleScope.launch {
            delay(90_000)
            if (pendingUpload != null) {
                onStatus("Timed out waiting for Hi Rokid install result.")
                cleanup()
                onBusyChanged(false)
                onInstallResult?.invoke(false)
            }
        }

        val configured = link.configCXRSession(
            CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMAPP, packageName),
        )
        if (!configured) {
            pendingUpload = null
            onBusyChanged(false)
            onStatus("Failed to configure CXR-L CUSTOMAPP session.")
            onInstallResult?.invoke(false)
            return
        }

        onStatus("Binding to global Hi Rokid service...")
        if (!bindGlobalHiRokidService(link, authToken)) {
            pendingUpload = null
            onBusyChanged(false)
            onStatus("Hi Rokid service bind failed. Open Hi Rokid, then retry.")
            onInstallResult?.invoke(false)
        }
    }

    private fun queryNext(authToken: String) {
        val packageName = queryQueue.pollFirst()
        if (packageName == null) {
            finishQueries()
            return
        }
        connectAndQuery(authToken, packageName)
    }

    private fun connectAndQuery(authToken: String, packageName: String) {
        cleanup()
        val link = CXRLink(activity.applicationContext).also { newLink ->
            newLink.setCXRLinkCbk(object : ICXRLinkCbk {
                override fun onCXRLConnected(connected: Boolean) {
                    activity.runOnUiThread {
                        cxrlConnected = connected
                        maybeQueryPending()
                    }
                }

                override fun onGlassBtConnected(connected: Boolean) {
                    activity.runOnUiThread {
                        glassBtConnected = connected
                        maybeQueryPending()
                    }
                }

                override fun onGlassAiAssistStart() = Unit
                override fun onGlassAiAssistStop() = Unit
            })
            cxrLink = newLink
            newLink
        }

        pendingQueryPackage = packageName
        pendingUpload = null
        cxrlConnected = false
        glassBtConnected = false
        uploadStarted = false
        queryStarted = false
        timeoutJob = activity.lifecycleScope.launch {
            delay(30_000)
            if (pendingQueryPackage == packageName) {
                onStatus("Timed out querying $packageName.")
                onQueryResult?.invoke(packageName, false)
                queryNext(authToken)
            }
        }

        val configured = link.configCXRSession(
            CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMAPP, packageName),
        )
        if (!configured) {
            onStatus("Failed to configure query for $packageName.")
            onQueryResult?.invoke(packageName, false)
            queryNext(authToken)
            return
        }

        if (!bindGlobalHiRokidService(link, authToken)) {
            onStatus("Hi Rokid service bind failed. Open Hi Rokid, then retry.")
            finishQueries()
        }
    }

    private fun maybeUploadPending() {
        val apkFile = pendingUpload ?: return
        if (uploadStarted || !cxrlConnected || !glassBtConnected) return

        uploadStarted = true
        onStatus("CXR-L ready. Uploading and installing on glasses...")
        cxrLink?.appUploadAndInstall(apkFile.absolutePath, object : IGlassAppCbk {
            override fun onInstallAppResult(success: Boolean) {
                activity.runOnUiThread {
                    timeoutJob?.cancel()
                    timeoutJob = null
                    pendingUpload = null
                    uploadStarted = false
                    onStatus(if (success) "Glasses install succeeded." else "Glasses install failed.")
                    onBusyChanged(false)
                    pendingInstallResult?.invoke(success)
                    pendingInstallResult = null
                }
            }

            override fun onUnInstallAppResult(success: Boolean) = Unit
            override fun onOpenAppResult(success: Boolean) = Unit
            override fun onStopAppResult(success: Boolean) = Unit
            override fun onGlassAppResume(resumed: Boolean) = Unit
            override fun onQueryAppResult(installed: Boolean) = Unit
        })
    }

    private fun maybeQueryPending() {
        val packageName = pendingQueryPackage ?: return
        if (queryStarted || !cxrlConnected || !glassBtConnected) return

        queryStarted = true
        cxrLink?.appIsInstalled(object : IGlassAppCbk {
            override fun onInstallAppResult(success: Boolean) = Unit
            override fun onUnInstallAppResult(success: Boolean) = Unit
            override fun onOpenAppResult(success: Boolean) = Unit
            override fun onStopAppResult(success: Boolean) = Unit
            override fun onGlassAppResume(resumed: Boolean) = Unit

            override fun onQueryAppResult(installed: Boolean) {
                activity.runOnUiThread {
                    timeoutJob?.cancel()
                    timeoutJob = null
                    onQueryResult?.invoke(packageName, installed)
                    queryNext(token.orEmpty())
                }
            }
        })
    }

    private fun finishQueries() {
        val complete = onQueryComplete
        cleanup()
        queryQueue.clear()
        onQueryResult = null
        onQueryComplete = null
        onBusyChanged(false)
        complete?.invoke()
    }

    private fun bindGlobalHiRokidService(link: CXRLink, authToken: String): Boolean {
        return runCatching {
            val intent = Intent(MEDIA_SERVICE_ACTION)
                .setPackage(GLOBAL_AI_APP_PACKAGE)
                .putExtra(AUTH_TOKEN_EXTRA, authToken)
            activity.applicationContext.bindService(intent, findServiceConnection(link), Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
    }

    private fun findServiceConnection(link: CXRLink): ServiceConnection {
        var type: Class<*>? = link.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { ServiceConnection::class.java.isAssignableFrom(it.type) }
            if (field != null) {
                field.isAccessible = true
                return field.get(link) as ServiceConnection
            }
            type = type.superclass
        }
        error("CXR-L ServiceConnection field not found")
    }

    private fun isGlobalHiRokidInstalled(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.packageManager.getPackageInfo(GLOBAL_AI_APP_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                activity.packageManager.getPackageInfo(GLOBAL_AI_APP_PACKAGE, 0)
            }
        }.isSuccess
    }

    private fun isWifiEnabled(): Boolean {
        val wifiManager = activity.applicationContext.getSystemService(WifiManager::class.java)
        return wifiManager?.isWifiEnabled == true
    }

    private fun readPackageName(apkFile: File): String {
        @Suppress("DEPRECATION")
        val info = activity.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)
        return info?.packageName?.takeIf { it.isNotBlank() } ?: error("Cannot read APK package name")
    }
}
