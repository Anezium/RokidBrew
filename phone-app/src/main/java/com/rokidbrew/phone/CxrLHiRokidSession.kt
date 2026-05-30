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
    private val onConnectionChanged: (CxrConnectionState) -> Unit,
    initialHostApp: RokidHostApp = RokidHostApp.DEFAULT,
) {
    companion object {
        const val AUTH_REQUEST_CODE = 4027

        private const val AUTH_ACTIVITY_CLASS = "com.rokid.sprite.aiapp.externalapp.auth.AuthorizationActivity"
        private const val AUTH_ACTION = "com.rokid.sprite.aiapp.externalapp.AUTHORIZATION"
        private const val MEDIA_SERVICE_ACTION = "com.rokid.sprite.aiapp.externalapp.MEDIA_STREAM_SERVICE"
        private const val AUTH_TOKEN_EXTRA = "auth_token"
        private const val AUTH_PACKAGE_EXTRA = "auth_package"
    }

    private var hostApp: RokidHostApp = initialHostApp
    private var token: String? = null
    private var cxrLink: CXRLink? = null
    private var pendingOperation: CxrAppOperation? = null
    private var queryQueue: ArrayDeque<String> = ArrayDeque()
    private var onQueryResult: ((String, Boolean) -> Unit)? = null
    private var onQueryComplete: (() -> Unit)? = null
    private var cxrlConnected = false
    private var glassBtConnected = false
    private var operationStarted = false
    private var timeoutJob: Job? = null

    fun hasAuthorization(): Boolean = !token.isNullOrBlank()

    fun ensureGlassesOperationReady(): Boolean {
        return hasGlassesOperationPrerequisites(hostApp, requestAuthorizationIfMissing = true)
    }

    fun selectHostApp(nextHostApp: RokidHostApp) {
        if (hostApp == nextHostApp) return
        cleanup()
        token = null
        hostApp = nextHostApp
        notifyConnectionChanged()
    }

    fun isHostAppInstalled(targetHostApp: RokidHostApp = hostApp): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.packageManager.getPackageInfo(targetHostApp.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                activity.packageManager.getPackageInfo(targetHostApp.packageName, 0)
            }
        }.isSuccess
    }

    fun requestAuthorization() {
        val targetHostApp = hostApp
        if (!isHostAppInstalled(targetHostApp)) {
            onStatus("Install ${targetHostApp.displayName} first.")
            return
        }

        runCatching {
            val intent = Intent().setComponent(ComponentName(targetHostApp.packageName, AUTH_ACTIVITY_CLASS))
            activity.startActivityForResult(intent, AUTH_REQUEST_CODE)
        }.recoverCatching {
            val fallback = Intent(AUTH_ACTION).setPackage(targetHostApp.packageName)
            activity.startActivityForResult(fallback, AUTH_REQUEST_CODE)
        }.onSuccess {
            onStatus("Authorization opened in ${targetHostApp.displayName}.")
        }.onFailure { error ->
            onStatus("Failed to open ${targetHostApp.displayName} authorization: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun handleAuthorizationResult(resultCode: Int, data: Intent?) {
        when (val result = AuthorizationHelper.INSTANCE.parseAuthorizationResult(resultCode, data)) {
            is AuthResult.AuthSuccess -> {
                token = result.token
                onStatus("${hostApp.displayName} authorization token received.")
                notifyConnectionChanged()
            }

            is AuthResult.AuthCancel -> {
                token = null
                onStatus("${hostApp.displayName} authorization cancelled.")
                notifyConnectionChanged()
            }

            is AuthResult.AuthFail -> {
                token = null
                onStatus("${hostApp.displayName} authorization failed.")
                notifyConnectionChanged()
            }
        }
    }

    fun installApk(apkFile: File, onInstallResult: ((Boolean) -> Unit)? = null) {
        val targetHostApp = hostApp
        if (!hasGlassesOperationPrerequisites(targetHostApp, requestAuthorizationIfMissing = true)) {
            onInstallResult?.invoke(false)
            return
        }
        val authToken = token.orEmpty()

        onBusyChanged(true)
        runCatching {
            val packageName = readPackageName(apkFile)
            onStatus("Detected package: $packageName")
            connectAndUpload(authToken, targetHostApp, packageName, apkFile, onInstallResult)
        }.onFailure { error ->
            onStatus("CXR-L failed: ${error.message ?: error.javaClass.simpleName}")
            onBusyChanged(false)
            onInstallResult?.invoke(false)
        }
    }

    fun uninstallApp(packageName: String, onUninstallResult: ((Boolean) -> Unit)? = null) {
        val targetHostApp = hostApp
        if (!hasGlassesOperationPrerequisites(targetHostApp, requestAuthorizationIfMissing = true)) {
            onUninstallResult?.invoke(false)
            return
        }
        val authToken = token.orEmpty()

        onBusyChanged(true)
        connectAndUninstall(authToken, targetHostApp, packageName, onUninstallResult)
    }

    fun queryInstalledApps(
        packageNames: List<String>,
        onResult: (String, Boolean) -> Unit,
        onComplete: () -> Unit,
    ) {
        val targetHostApp = hostApp
        if (packageNames.isEmpty()) {
            onComplete()
            return
        }
        if (!hasGlassesOperationPrerequisites(targetHostApp, requestAuthorizationIfMissing = true)) {
            onComplete()
            return
        }
        val authToken = token.orEmpty()

        cleanup()
        queryQueue = ArrayDeque(packageNames.distinct())
        onQueryResult = onResult
        onQueryComplete = onComplete
        onBusyChanged(true)
        queryNext(authToken, targetHostApp)
    }

    fun cleanup() {
        timeoutJob?.cancel()
        timeoutJob = null
        runCatching { cxrLink?.disconnect() }
        cxrLink = null
        pendingOperation = null
        cxrlConnected = false
        glassBtConnected = false
        operationStarted = false
        notifyConnectionChanged()
    }

    private fun connectAndUpload(
        authToken: String,
        targetHostApp: RokidHostApp,
        packageName: String,
        apkFile: File,
        onInstallResult: ((Boolean) -> Unit)?,
    ) {
        connectAndRunCustomAppOperation(
            authToken = authToken,
            targetHostApp = targetHostApp,
            operation = CxrAppOperation(
                packageName = packageName,
                timeoutMillis = 90_000,
                timeoutMessage = "Timed out waiting for ${targetHostApp.displayName} install result.",
                bindMessage = "Binding to ${targetHostApp.displayName} service...",
                configureFailureMessage = "Failed to configure CXR-L CUSTOMAPP session.",
                bindFailureMessage = "${targetHostApp.displayName} service bind failed. Open it, then retry.",
                showConnectionStatus = true,
                onReady = { link ->
                    onStatus("CXR-L ready. Uploading and installing on glasses...")
                    link.appUploadAndInstall(apkFile.absolutePath, glassAppCallback(
                        onInstall = { success ->
                            completeActiveOperation()
                            onStatus(if (success) "Glasses install succeeded." else "Glasses install failed.")
                            onBusyChanged(false)
                            onInstallResult?.invoke(success)
                        },
                    ))
                },
                onFailure = {
                    cleanup()
                    onBusyChanged(false)
                    onInstallResult?.invoke(false)
                },
            ),
        )
    }

    private fun queryNext(authToken: String, targetHostApp: RokidHostApp) {
        val packageName = queryQueue.pollFirst()
        if (packageName == null) {
            finishQueries()
            return
        }
        connectAndQuery(authToken, targetHostApp, packageName)
    }

    private fun connectAndQuery(authToken: String, targetHostApp: RokidHostApp, packageName: String) {
        connectAndRunCustomAppOperation(
            authToken = authToken,
            targetHostApp = targetHostApp,
            operation = CxrAppOperation(
                packageName = packageName,
                timeoutMillis = 30_000,
                timeoutMessage = "Timed out querying $packageName.",
                configureFailureMessage = "Failed to configure query for $packageName.",
                bindFailureMessage = "${targetHostApp.displayName} service bind failed. Open it, then retry.",
                onReady = { link ->
                    link.appIsInstalled(glassAppCallback(
                        onQuery = { installed ->
                            completeActiveOperation()
                            onQueryResult?.invoke(packageName, installed)
                            queryNext(authToken, targetHostApp)
                        },
                    ))
                },
                onFailure = {
                    onQueryResult?.invoke(packageName, false)
                    queryNext(authToken, targetHostApp)
                },
                onBindFailure = {
                    finishQueries()
                },
            ),
        )
    }

    private fun connectAndUninstall(
        authToken: String,
        targetHostApp: RokidHostApp,
        packageName: String,
        onUninstallResult: ((Boolean) -> Unit)?,
    ) {
        connectAndRunCustomAppOperation(
            authToken = authToken,
            targetHostApp = targetHostApp,
            operation = CxrAppOperation(
                packageName = packageName,
                timeoutMillis = 60_000,
                timeoutMessage = "Timed out uninstalling $packageName from glasses.",
                bindMessage = "Binding to ${targetHostApp.displayName} service...",
                configureFailureMessage = "Failed to configure uninstall for $packageName.",
                bindFailureMessage = "${targetHostApp.displayName} service bind failed. Open it, then retry.",
                showConnectionStatus = true,
                onReady = { link ->
                    onStatus("CXR-L ready. Uninstalling $packageName from glasses...")
                    link.appUninstall(glassAppCallback(
                        onUninstall = { success ->
                            completeActiveOperation()
                            onStatus(if (success) "Glasses uninstall succeeded." else "Glasses uninstall failed.")
                            onBusyChanged(false)
                            onUninstallResult?.invoke(success)
                        },
                    ))
                },
                onFailure = {
                    cleanup()
                    onBusyChanged(false)
                    onUninstallResult?.invoke(false)
                },
            ),
        )
    }

    private fun connectAndRunCustomAppOperation(
        authToken: String,
        targetHostApp: RokidHostApp,
        operation: CxrAppOperation,
    ) {
        cleanup()
        val link = CXRLink(activity.applicationContext).also { newLink ->
            newLink.setCXRLinkCbk(object : ICXRLinkCbk {
                override fun onCXRLConnected(connected: Boolean) {
                    activity.runOnUiThread {
                        cxrlConnected = connected
                        if (operation.showConnectionStatus) onStatus("CXR-L service connected: $connected")
                        notifyConnectionChanged()
                        maybeRunPendingOperation()
                    }
                }

                override fun onGlassBtConnected(connected: Boolean) {
                    activity.runOnUiThread {
                        glassBtConnected = connected
                        if (operation.showConnectionStatus) onStatus("Glasses Bluetooth connected: $connected")
                        notifyConnectionChanged()
                        maybeRunPendingOperation()
                    }
                }

                override fun onGlassAiAssistStart() = Unit
                override fun onGlassAiAssistStop() = Unit
            })
            cxrLink = newLink
            newLink
        }

        pendingOperation = operation
        cxrlConnected = false
        glassBtConnected = false
        operationStarted = false
        timeoutJob = activity.lifecycleScope.launch {
            delay(operation.timeoutMillis)
            if (pendingOperation === operation) {
                pendingOperation = null
                operationStarted = false
                onStatus(operation.timeoutMessage)
                operation.onFailure()
            }
        }

        val configured = link.configCXRSession(
            CxrDefs.CXRSession(CxrDefs.CXRSessionType.CUSTOMAPP, operation.packageName),
        )
        if (!configured) {
            pendingOperation = null
            operationStarted = false
            onStatus(operation.configureFailureMessage)
            operation.onFailure()
            return
        }

        operation.bindMessage?.let(onStatus)
        if (!bindRokidHostService(link, targetHostApp, authToken)) {
            pendingOperation = null
            operationStarted = false
            onStatus(operation.bindFailureMessage)
            operation.onBindFailure()
        }
    }

    private fun maybeRunPendingOperation() {
        val operation = pendingOperation ?: return
        if (operationStarted || !cxrlConnected || !glassBtConnected) return
        val link = cxrLink ?: return
        operationStarted = true
        operation.onReady(link)
    }

    private fun completeActiveOperation() {
        timeoutJob?.cancel()
        timeoutJob = null
        pendingOperation = null
        operationStarted = false
    }

    private fun glassAppCallback(
        onInstall: (Boolean) -> Unit = {},
        onUninstall: (Boolean) -> Unit = {},
        onQuery: (Boolean) -> Unit = {},
    ): IGlassAppCbk = object : IGlassAppCbk {
        override fun onInstallAppResult(success: Boolean) {
            activity.runOnUiThread { onInstall(success) }
        }

        override fun onUnInstallAppResult(success: Boolean) {
            activity.runOnUiThread { onUninstall(success) }
        }

        override fun onOpenAppResult(success: Boolean) = Unit
        override fun onStopAppResult(success: Boolean) = Unit
        override fun onGlassAppResume(resumed: Boolean) = Unit

        override fun onQueryAppResult(installed: Boolean) {
            activity.runOnUiThread { onQuery(installed) }
        }
    }

    private data class CxrAppOperation(
        val packageName: String,
        val timeoutMillis: Long,
        val timeoutMessage: String,
        val configureFailureMessage: String,
        val bindFailureMessage: String,
        val bindMessage: String? = null,
        val showConnectionStatus: Boolean = false,
        val onReady: (CXRLink) -> Unit,
        val onFailure: () -> Unit,
        val onBindFailure: () -> Unit = onFailure,
    )

    private fun finishQueries() {
        val complete = onQueryComplete
        cleanup()
        queryQueue.clear()
        onQueryResult = null
        onQueryComplete = null
        onBusyChanged(false)
        complete?.invoke()
    }

    private fun bindRokidHostService(link: CXRLink, targetHostApp: RokidHostApp, authToken: String): Boolean {
        return runCatching {
            val intent = Intent(MEDIA_SERVICE_ACTION)
                .setPackage(targetHostApp.packageName)
                .putExtra(AUTH_TOKEN_EXTRA, authToken)
                .putExtra(AUTH_PACKAGE_EXTRA, activity.packageName)
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

    private fun isWifiEnabled(): Boolean {
        val wifiManager = activity.applicationContext.getSystemService(WifiManager::class.java)
        return wifiManager?.isWifiEnabled == true
    }

    private fun hasGlassesOperationPrerequisites(
        targetHostApp: RokidHostApp,
        requestAuthorizationIfMissing: Boolean,
    ): Boolean {
        if (!isHostAppInstalled(targetHostApp)) {
            onStatus("Install ${targetHostApp.displayName} on this phone first.")
            return false
        }
        if (!isWifiEnabled()) {
            onStatus("Turn on phone Wi-Fi first. ${targetHostApp.displayName} needs it for the glasses hotspot.")
            return false
        }
        if (token.isNullOrBlank()) {
            onStatus("Press Authorize ${targetHostApp.shortLabel} first.")
            if (requestAuthorizationIfMissing) requestAuthorization()
            return false
        }
        return true
    }

    private fun readPackageName(apkFile: File): String {
        @Suppress("DEPRECATION")
        val info = activity.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)
        return info?.packageName?.takeIf { it.isNotBlank() } ?: error("Cannot read APK package name")
    }

    private fun notifyConnectionChanged() {
        onConnectionChanged(
            CxrConnectionState(
                authorized = hasAuthorization(),
                cxrlConnected = cxrlConnected,
                glassBtConnected = glassBtConnected,
            ),
        )
    }
}

data class CxrConnectionState(
    val authorized: Boolean = false,
    val cxrlConnected: Boolean = false,
    val glassBtConnected: Boolean = false,
) {
    val connected: Boolean
        get() = cxrlConnected && glassBtConnected

    val connecting: Boolean
        get() = authorized && (cxrlConnected || glassBtConnected) && !connected
}
