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
import android.view.View
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
    enum class InstallState { UNKNOWN, NOT_INSTALLED, INSTALLED, INSTALLED_UNKNOWN_VERSION, UPDATE_AVAILABLE }

    private lateinit var cxrL: CxrLHiRokidSession
    private lateinit var downloader: ApkDownloader
    private lateinit var iconLoader: IconLoader
    private lateinit var mediaLoader: MediaLoader
    private lateinit var installCache: UserInstallCache

    private var apps by mutableStateOf(emptyList<BrewApp>())
    private var busy by mutableStateOf(false)
    private var refreshing by mutableStateOf(false)
    private var installCheckTick by mutableStateOf(0)
    private var statusExpanded by mutableStateOf(false)
    private var statusLines by mutableStateOf(listOf("Ready."))
    private val downloadProgress = mutableStateMapOf<String, Int>()
    private val phoneInstallStates = mutableStateMapOf<String, InstallState>()
    private val glassesInstallStates = mutableStateMapOf<String, InstallState>()
    private var pendingAction: (() -> Unit)? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var selfUpdateState by mutableStateOf(
        BrewSelfUpdateState(
            currentVersion = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
        ),
    )
    private var showUpdatePrompt by mutableStateOf(false)
    private var selectedHostApp by mutableStateOf(RokidHostApp.DEFAULT)
    private var cxrConnection by mutableStateOf(CxrConnectionState())
    private var phoneInstallRefreshGeneration = 0

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
            refreshPhoneInstallStates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferHighRefreshRate()

        downloader = ApkDownloader(this)
        iconLoader = IconLoader(this)
        mediaLoader = MediaLoader(this)
        installCache = UserInstallCache(this)
        selectedHostApp = loadSelectedHostApp()
        cxrL = CxrLHiRokidSession(
            activity = this,
            onStatus = ::log,
            onBusyChanged = ::updateBusy,
            onConnectionChanged = { cxrConnection = it },
            initialHostApp = selectedHostApp,
        )
        apps = BrewIndex.loadInitial(this)
        refreshCachedGlassesInstallStates(apps)
        refreshPhoneInstallStates(apps)

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
                    statusLines = statusLines,
                    statusExpanded = statusExpanded,
                    downloadProgress = downloadProgress,
                    phoneInstallStates = phoneInstallStates,
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
                    onCheckGlassesInstall = ::checkGlassesInstallStateIfNeeded,
                    onUninstall = { app, target ->
                        if (target == "glasses") {
                            runWithPrerequisites { uninstallArtifact(app, target) }
                        } else {
                            uninstallArtifact(app, target)
                        }
                    },
                    selfUpdateState = selfUpdateState,
                    onSelfUpdate = { performSelfUpdate() },
                )
                if (showUpdatePrompt && selfUpdateState.available) {
                    UpdateDialog(
                        version = selfUpdateState.latestVersion.ifBlank { "latest" },
                        downloading = selfUpdateState.downloading,
                        downloadPercent = selfUpdateState.downloadPercent,
                        onUpdate = { performSelfUpdate() },
                        onDismiss = { showUpdatePrompt = false },
                    )
                }
            }
        }
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
        preferHighRefreshRate()
        installCheckTick += 1
        refreshPhoneInstallStates()
    }

    private fun preferHighRefreshRate() {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return
        val currentMode = display.mode
        val matchingModes = display.supportedModes
            .filter { mode ->
                mode.physicalWidth == currentMode.physicalWidth &&
                    mode.physicalHeight == currentMode.physicalHeight
            }
        val fastestMode = matchingModes.maxByOrNull { it.refreshRate } ?: return
        val preferredMode = if (fastestMode.refreshRate > currentMode.refreshRate) fastestMode else currentMode

        val attributes = window.attributes
        if (
            attributes.preferredDisplayModeId != preferredMode.modeId ||
            attributes.preferredRefreshRate != fastestMode.refreshRate
        ) {
            attributes.preferredDisplayModeId = preferredMode.modeId
            attributes.preferredRefreshRate = fastestMode.refreshRate
            attributes.setFrameRatePowerSavingsBalanced(false)
            window.attributes = attributes
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.decorView.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)
        }
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
                refreshCachedGlassesInstallStates(refresh.apps)
                refreshPhoneInstallStates(refresh.apps)
                installCheckTick += 1
                val remoteCode = refresh.brewVersionCode ?: 0L
                val updateAvailable = remoteCode > BuildConfig.VERSION_CODE && !refresh.brewApkUrl.isNullOrBlank()
                val wasUpdateAvailable = selfUpdateState.available
                selfUpdateState = selfUpdateState.copy(
                    currentVersion = BuildConfig.VERSION_NAME,
                    currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    latestVersion = refresh.brewVersion.orEmpty(),
                    latestVersionCode = refresh.brewVersionCode,
                    apkUrl = refresh.brewApkUrl.orEmpty(),
                    releaseUrl = refresh.brewReleaseUrl.orEmpty(),
                    notes = refresh.brewNotes.orEmpty(),
                    changes = refresh.brewChanges,
                    available = updateAvailable,
                )
                if (updateAvailable) {
                    if (!wasUpdateAvailable) showUpdatePrompt = true
                    log("Update available: RokidBrew ${updateVersionLabel(refresh.brewVersion)}.")
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
        if (selfUpdateState.downloading) return
        val url = selfUpdateState.apkUrl
        if (url.isBlank()) {
            log("Update failed: missing RokidBrew APK URL.")
            return
        }
        selfUpdateState = selfUpdateState.copy(downloading = true, downloadPercent = 0)
        downloadProgress["brew-self-update"] = 0
        val version = selfUpdateState.latestVersion.ifBlank { "latest" }
        lifecycleScope.launch {
            runCatching {
                log("Downloading RokidBrew $version...")
                val file = downloader.download(url, "RokidBrew-update.apk") { percent ->
                    runOnUiThread {
                        downloadProgress["brew-self-update"] = percent
                        selfUpdateState = selfUpdateState.copy(downloadPercent = percent)
                    }
                }
                downloadProgress["brew-self-update"] = 100
                selfUpdateState = selfUpdateState.copy(downloading = false, downloadPercent = 100)
                log("Downloaded ${file.length()} bytes.")
                val ok = PhonePackageInstallHelper.requestInstall(this@MainActivity, file, ::log)
                if (!ok) {
                    selfUpdateState = selfUpdateState.copy(downloading = false)
                }
                downloadProgress.remove("brew-self-update")
            }.onFailure { error ->
                log("Update failed: ${error.message ?: error.javaClass.simpleName}")
                downloadProgress.remove("brew-self-update")
                selfUpdateState = selfUpdateState.copy(downloading = false)
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

    private fun checkGlassesInstallStateIfNeeded(app: BrewApp) {
        val artifact = app.artifactFor("glasses") ?: return
        val packageName = artifact.packageName?.takeIf { it.isNotBlank() } ?: return
        if (glassesInstallStates.containsKey(packageName)) return

        cachedGlassesInstallState(app, artifact)?.let { cachedState ->
            glassesInstallStates[packageName] = cachedState
            return
        }

        if (busy || !cxrL.hasAuthorization()) return
        refreshGlassesInstallStates(listOf(app))
    }

    private fun refreshCachedGlassesInstallStates(targetApps: List<BrewApp> = apps) {
        val knownPackages = targetApps
            .mapNotNull { it.artifactFor("glasses")?.packageName?.takeIf(String::isNotBlank) }
            .toSet()
        glassesInstallStates.keys
            .filterNot(knownPackages::contains)
            .forEach(glassesInstallStates::remove)

        targetApps.forEach { app ->
            val artifact = app.artifactFor("glasses") ?: return@forEach
            val packageName = artifact.packageName?.takeIf { it.isNotBlank() } ?: return@forEach
            cachedGlassesInstallState(app, artifact)?.let { state ->
                glassesInstallStates[packageName] = state
            }
        }
    }

    private fun cachedGlassesInstallState(app: BrewApp, artifact: BrewArtifact): InstallState? {
        val packageName = artifact.packageName?.takeIf { it.isNotBlank() } ?: return null
        val record = installCache.getGlasses(packageName) ?: return null
        if (!record.versionKnown) return InstallState.INSTALLED_UNKNOWN_VERSION
        val registryVersionCode = artifact.versionCode
        if (registryVersionCode != null && record.versionCode != null) {
            return if (record.versionCode < registryVersionCode) InstallState.UPDATE_AVAILABLE else InstallState.INSTALLED
        }
        val cachedVersionName = record.versionName?.takeIf { it.isNotBlank() }
        return if (cachedVersionName != null && cachedVersionName != app.version) {
            InstallState.UPDATE_AVAILABLE
        } else {
            InstallState.INSTALLED
        }
    }

    private fun refreshGlassesInstallStates(targetApps: List<BrewApp> = apps) {
        val appsByPackage = targetApps
            .mapNotNull { app ->
                val artifact = app.artifactFor("glasses") ?: return@mapNotNull null
                val packageName = artifact.packageName?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                packageName to (app to artifact)
            }
            .toMap()
        val packageNames = appsByPackage.keys.toList()
        if (packageNames.isEmpty() || !cxrL.hasAuthorization()) return

        cxrL.queryInstalledApps(
            packageNames = packageNames,
            onResult = { packageName, installed ->
                val appAndArtifact = appsByPackage[packageName]
                if (installed && appAndArtifact != null) {
                    val (app, artifact) = appAndArtifact
                    if (installCache.getGlasses(packageName) == null) {
                        installCache.recordGlassesDiscovered(app, artifact)
                    }
                    glassesInstallStates[packageName] =
                        cachedGlassesInstallState(app, artifact) ?: InstallState.INSTALLED_UNKNOWN_VERSION
                } else {
                    installCache.removeGlasses(packageName)
                    glassesInstallStates[packageName] = InstallState.NOT_INSTALLED
                }
                installCheckTick += 1
            },
            onComplete = {
                log("Glasses install states refreshed.")
            },
        )
    }

    private fun refreshPhoneInstallStates(targetApps: List<BrewApp> = apps) {
        val artifacts = targetApps
            .mapNotNull { it.artifactFor("phone") }
            .filter { !it.packageName.isNullOrBlank() }
            .distinctBy { it.packageName }
        val generation = ++phoneInstallRefreshGeneration
        if (artifacts.isEmpty()) {
            phoneInstallStates.clear()
            return
        }

        lifecycleScope.launch {
            val states = withContext(Dispatchers.IO) {
                artifacts.associate { artifact ->
                    artifact.packageName.orEmpty() to installStateFor(artifact)
                }
            }
            if (generation != phoneInstallRefreshGeneration) return@launch
            val stalePackages = phoneInstallStates.keys.filterNot(states::containsKey)
            stalePackages.forEach(phoneInstallStates::remove)
            states.forEach { (packageName, state) ->
                if (phoneInstallStates[packageName] != state) {
                    phoneInstallStates[packageName] = state
                }
            }
        }
    }

    private fun installArtifact(app: BrewApp, target: String) {
        if (busy) return
        val artifact = app.artifactFor(target)
        if (artifact == null) {
            Toast.makeText(this, "No $target artifact for ${app.name}", Toast.LENGTH_SHORT).show()
            return
        }
        if (target == "glasses" && !cxrL.ensureGlassesOperationReady()) return

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
                        artifact.packageName?.takeIf { it.isNotBlank() }?.let { packageName ->
                            if (installed) {
                                installCache.recordGlassesInstall(app, artifact)
                                glassesInstallStates[packageName] =
                                    cachedGlassesInstallState(app, artifact) ?: InstallState.INSTALLED
                            } else {
                                installCache.removeGlasses(packageName)
                                glassesInstallStates[packageName] = InstallState.NOT_INSTALLED
                            }
                            installCheckTick += 1
                        }
                        downloadProgress.remove(progressKey)
                        updateBusy(false)
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

    private fun uninstallArtifact(app: BrewApp, target: String) {
        if (busy) return
        val artifact = app.artifactFor(target)
        val packageName = artifact?.packageName?.takeIf { it.isNotBlank() }
        if (artifact == null || packageName == null) {
            Toast.makeText(this, "No $target package for ${app.name}", Toast.LENGTH_SHORT).show()
            return
        }

        if (target == "glasses") {
            cxrL.uninstallApp(packageName) { uninstalled ->
                if (uninstalled) {
                    installCache.removeGlasses(packageName)
                    glassesInstallStates[packageName] = InstallState.NOT_INSTALLED
                    installCheckTick += 1
                }
            }
        } else {
            PhonePackageInstallHelper.requestUninstall(this, packageName, app.name, ::log)
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

    private fun updateVersionLabel(version: String?): String {
        val clean = version?.trim().orEmpty()
        if (clean.isBlank()) return "latest"
        return if (clean.startsWith("v", ignoreCase = true)) clean else "v$clean"
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
