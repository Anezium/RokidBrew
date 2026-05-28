package com.rokidbrew.phone

import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "rokidbrew_preferences"
private const val PREF_ROKID_HOST_APP = "rokid_host_app"

class MainActivity : AppCompatActivity() {
    enum class InstallState { UNKNOWN, NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE }

    private lateinit var cxrL: CxrLHiRokidSession
    private lateinit var downloader: ApkDownloader
    private lateinit var iconLoader: IconLoader
    private lateinit var mediaLoader: MediaLoader

    private var apps by mutableStateOf(emptyList<BrewApp>())
    private var busy by mutableStateOf(false)
    private var refreshing by mutableStateOf(false)
    private var installCheckTick by mutableStateOf(0)
    private var statusExpanded by mutableStateOf(false)
    private var statusLines by mutableStateOf(listOf("Ready."))
    private val downloadProgress = mutableStateMapOf<String, Int>()
    private val glassesInstallStates = mutableStateMapOf<String, InstallState>()
    private var pendingAction: (() -> Unit)? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var updateAvailable by mutableStateOf(false)
    private var updateVersion by mutableStateOf("")
    private var updateApkUrl by mutableStateOf("")
    private var updateDownloading by mutableStateOf(false)
    private var selectedHostApp by mutableStateOf(RokidHostApp.DEFAULT)
    private var cxrConnection by mutableStateOf(CxrConnectionState())

    private val permissions: Array<String>
        get() = buildList {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (permissions.all(::hasPermission)) consumePendingAction() else log("Bluetooth permission denied.")
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isBluetoothEnabled()) consumePendingAction() else log("Bluetooth is still disabled.")
        }

    private val phoneInstallStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra(PhoneInstallResultReceiver.EXTRA_MESSAGE)?.let(::log)
            installCheckTick += 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        downloader = ApkDownloader(this)
        iconLoader = IconLoader(this)
        mediaLoader = MediaLoader(this)
        selectedHostApp = loadSelectedHostApp()
        cxrL = CxrLHiRokidSession(
            activity = this,
            onStatus = ::log,
            onBusyChanged = ::updateBusy,
            onConnectionChanged = { cxrConnection = it },
            initialHostApp = selectedHostApp,
        )
        apps = BrewIndex.loadInitial(this)

        setContent {
            RokidBrewTheme {
                val hostAppInstalled by produceState(false, selectedHostApp, installCheckTick) {
                    value = withContext(Dispatchers.IO) { cxrL.isHostAppInstalled(selectedHostApp) }
                }
                BrewPhoneApp(
                    apps = apps,
                    busy = busy,
                    refreshing = refreshing,
                    selectedHostApp = selectedHostApp,
                    hostAppInstalled = hostAppInstalled,
                    cxrConnection = cxrConnection,
                    installCheckTick = installCheckTick,
                    statusLines = statusLines,
                    statusExpanded = statusExpanded,
                    downloadProgress = downloadProgress,
                    glassesInstallStates = glassesInstallStates,
                    iconLoader = iconLoader,
                    mediaLoader = mediaLoader,
                    onToggleStatus = { statusExpanded = !statusExpanded },
                    onRefresh = { refreshStoreIndex(manual = true) },
                    onHostAppSelected = ::selectRokidHostApp,
                    onAuthorize = { runWithPrerequisites { cxrL.requestAuthorization() } },
                    onInstall = { app, target ->
                        if (target == "glasses") {
                            runWithPrerequisites { installArtifact(app, target) }
                        } else {
                            installArtifact(app, target)
                        }
                    },
                )
                if (updateAvailable) {
                    UpdateDialog(
                        version = updateVersion,
                        downloading = updateDownloading,
                        downloadPercent = downloadProgress["brew-self-update"] ?: 0,
                        onUpdate = { performSelfUpdate() },
                        onDismiss = { updateAvailable = false },
                    )
                }
            }
        }
        warmAssets(apps)
        refreshStoreIndex(manual = false)
        log("Ready. Authorize ${selectedHostApp.displayName} before installing glasses APKs.")
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(PhoneInstallResultReceiver.ACTION_PHONE_INSTALL_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(phoneInstallStatusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(phoneInstallStatusReceiver, filter)
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(phoneInstallStatusReceiver) }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        installCheckTick += 1
    }

    private fun refreshStoreIndex(manual: Boolean) {
        if (refreshing) return
        lifecycleScope.launch {
            refreshing = true
            val started = System.currentTimeMillis()
            if (manual) log("Refreshing store registry...")
            runCatching {
                BrewIndex.refresh(this@MainActivity)
            }.onSuccess { refresh ->
                apps = refresh.apps
                warmAssets(refresh.apps)
                installCheckTick += 1
                val remoteCode = refresh.brewVersionCode ?: 0L
                if (remoteCode > BuildConfig.VERSION_CODE && !refresh.brewApkUrl.isNullOrBlank()) {
                    updateAvailable = true
                    updateVersion = refresh.brewVersion.orEmpty()
                    updateApkUrl = refresh.brewApkUrl
                }
                log("Store registry updated (${refresh.apps.size} apps).")
            }.onFailure { error ->
                log("Remote registry unavailable: ${error.message ?: error.javaClass.simpleName}")
            }
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < 800) delay(800 - elapsed)
            refreshing = false
        }
    }

    private fun performSelfUpdate() {
        if (updateDownloading) return
        updateDownloading = true
        val url = updateApkUrl
        val version = updateVersion.ifBlank { "latest" }
        lifecycleScope.launch {
            runCatching {
                log("Downloading RokidBrew $version...")
                val file = downloader.download(url, "RokidBrew-update.apk") { percent ->
                    downloadProgress["brew-self-update"] = percent
                }
                log("Downloaded ${file.length()} bytes.")
                val ok = PhonePackageInstallHelper.requestInstall(this@MainActivity, file, ::log)
                if (!ok) {
                    updateDownloading = false
                }
            }.onFailure { error ->
                log("Update failed: ${error.message ?: error.javaClass.simpleName}")
                updateDownloading = false
            }
        }
    }

    private fun warmAssets(targetApps: List<BrewApp>) {
        lifecycleScope.launch(Dispatchers.IO) {
            targetApps.forEach { app ->
                iconLoader.load(app.id, app.iconUrl)
                for (index in 0 until app.screenshotCount) {
                    val screenshot = app.screenshotAt(index)
                    mediaLoader.load(screenshot.assetName, screenshot.url)
                }
            }
        }
    }

    override fun onDestroy() {
        cxrL.cleanup()
        super.onDestroy()
    }

    @Deprecated("CXR-L SDK still uses startActivityForResult for authorization.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CxrLHiRokidSession.AUTH_REQUEST_CODE) {
            cxrL.handleAuthorizationResult(resultCode, data)
        }
    }

    private fun refreshGlassesInstallStates(targetApps: List<BrewApp> = apps) {
        val packageNames = targetApps
            .mapNotNull { it.artifactFor("glasses")?.packageName?.takeIf(String::isNotBlank) }
            .distinct()
        if (packageNames.isEmpty() || !cxrL.hasAuthorization()) return

        cxrL.queryInstalledApps(
            packageNames = packageNames,
            onResult = { packageName, installed ->
                glassesInstallStates[packageName] = if (installed) InstallState.INSTALLED else InstallState.NOT_INSTALLED
                installCheckTick += 1
            },
            onComplete = {
                log("Glasses install states refreshed.")
            },
        )
    }

    private fun installArtifact(app: BrewApp, target: String) {
        if (busy) return
        val artifact = app.artifactFor(target)
        if (artifact == null) {
            Toast.makeText(this, "No $target artifact for ${app.name}", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val progressKey = "${app.id}:$target"
            updateBusy(true)
            downloadProgress[progressKey] = 0
            runCatching {
                val fileName = "${app.id}-${target}-${app.version}.apk"
                log("Downloading $target APK for ${app.name}...")
                val file = downloader.download(artifact.url, fileName, artifact.sha256) { progress ->
                    downloadProgress[progressKey] = progress
                    if (progress % 25 == 0) log("Download $target: $progress%")
                }
                downloadProgress[progressKey] = 100
                log("Downloaded ${file.name} (${file.length() / 1024} KB).")
                if (target == "glasses") {
                    cxrL.installApk(file) { installed ->
                        artifact.packageName?.takeIf { it.isNotBlank() }?.let {
                            glassesInstallStates[it] = if (installed) InstallState.INSTALLED else InstallState.NOT_INSTALLED
                            installCheckTick += 1
                        }
                    }
                } else {
                    updateBusy(false)
                    downloadProgress.remove(progressKey)
                    PhonePackageInstallHelper.requestInstall(this@MainActivity, file, ::log)
                }
            }.onFailure { error ->
                log("Install failed: ${error.message ?: error.javaClass.simpleName}")
                downloadProgress.remove(progressKey)
                updateBusy(false)
            }
        }
    }

    private fun updateBusy(value: Boolean) {
        runOnUiThread {
            busy = value
            if (!value) downloadProgress.clear()
        }
    }

    private fun loadSelectedHostApp(): RokidHostApp {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return RokidHostApp.fromId(prefs.getString(PREF_ROKID_HOST_APP, null))
    }

    private fun selectRokidHostApp(hostApp: RokidHostApp) {
        if (selectedHostApp == hostApp) return
        selectedHostApp = hostApp
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(PREF_ROKID_HOST_APP, hostApp.id)
            .apply()
        cxrL.selectHostApp(hostApp)
        glassesInstallStates.clear()
        installCheckTick += 1
        log("CXR-L host set to ${hostApp.displayName}. Authorize again before glasses installs.")
    }

    private fun log(message: String) {
        runOnUiThread {
            val line = "[${timeFormat.format(Date())}] $message"
            statusLines = if (statusLines.singleOrNull() == "Ready.") listOf(line) else (statusLines + line).takeLast(60)
        }
    }

    private fun runWithPrerequisites(action: () -> Unit) {
        pendingAction = action
        when {
            !permissions.all(::hasPermission) -> permissionLauncher.launch(permissions)
            !isBluetoothEnabled() -> enableBluetoothLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> consumePendingAction()
        }
    }

    private fun consumePendingAction() {
        val action = pendingAction ?: return
        pendingAction = null
        action()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun isBluetoothEnabled(): Boolean {
        val manager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        return manager.adapter?.isEnabled == true
    }
}
