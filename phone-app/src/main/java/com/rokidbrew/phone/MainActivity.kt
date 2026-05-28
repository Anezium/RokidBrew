package com.rokidbrew.phone

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ElectricScooter
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NEW_CATEGORY = "New"
private const val PREFS_NAME = "rokidbrew_preferences"
private const val PREF_ROKID_HOST_APP = "rokid_host_app"

class MainActivity : AppCompatActivity() {
    private enum class Section { PHONE, GLASSES }
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

@Composable
private fun BrewPhoneApp(
    apps: List<BrewApp>,
    busy: Boolean,
    refreshing: Boolean,
    selectedHostApp: RokidHostApp,
    hostAppInstalled: Boolean,
    cxrConnection: CxrConnectionState,
    installCheckTick: Int,
    statusLines: List<String>,
    statusExpanded: Boolean,
    downloadProgress: Map<String, Int>,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    onToggleStatus: () -> Unit,
    onRefresh: () -> Unit,
    onHostAppSelected: (RokidHostApp) -> Unit,
    onAuthorize: () -> Unit,
    onInstall: (BrewApp, String) -> Unit,
) {
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<BrewApp?>(null) }
    val scrollState = rememberScrollState()

    BackHandler(enabled = selectedApp != null) {
        selectedApp = null
    }

    val visibleApps = remember(apps, categoryFilter, query) {
        apps.filter { app ->
            val categoryOk = categoryFilter == null ||
                (categoryFilter == NEW_CATEGORY && app.isNew) ||
                app.category.equals(categoryFilter, ignoreCase = true)
            val searchOk = query.isBlank() ||
                app.name.contains(query, ignoreCase = true) ||
                app.author.contains(query, ignoreCase = true) ||
                app.category.contains(query, ignoreCase = true) ||
                app.summary.contains(query, ignoreCase = true) ||
                app.description.contains(query, ignoreCase = true)
            categoryOk && searchOk
        }
    }
    val categories = remember(apps) {
        buildList {
            if (apps.any { it.isNew }) add(NEW_CATEGORY)
            addAll(apps.map { it.category }.distinct().sorted())
        }
    }
    val featuredApps = remember(apps) {
        apps.curatedHeroApps().ifEmpty { apps.take(6) }.take(8)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrewBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(BrewPanelHi.copy(alpha = 0.38f), Color.Transparent),
                    center = Offset(145f, 115f),
                    radius = 440f,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 132.dp),
        ) {
            Header(
                refreshing = refreshing,
                onRefresh = onRefresh,
                onReset = {
                    categoryFilter = null
                    query = ""
                },
            )
            SearchBar(
                query = query,
                onQueryChange = {
                    query = it
                    categoryFilter = null
                },
            )
            ConnectionPanel(
                selectedHostApp = selectedHostApp,
                hostAppInstalled = hostAppInstalled,
                cxrConnection = cxrConnection,
                busy = busy,
                onHostAppSelected = onHostAppSelected,
                onAuthorize = onAuthorize,
            )
            if (featuredApps.isNotEmpty()) {
                FeaturedShelf(
                    apps = featuredApps,
                    iconLoader = iconLoader,
                    mediaLoader = mediaLoader,
                    onOpen = { selectedApp = it },
                    onViewAll = {
                        categoryFilter = null
                        query = ""
                    },
                )
            }
            CategoryStrip(
                categories = categories,
                selected = categoryFilter,
                onSelect = { categoryFilter = it },
            )
            AppShelf(
                apps = visibleApps,
                iconLoader = iconLoader,
                mediaLoader = mediaLoader,
                busy = busy,
                progress = downloadProgress,
                installCheckTick = installCheckTick,
                glassesInstallStates = glassesInstallStates,
                onOpen = { selectedApp = it },
                onInstall = onInstall,
            )
        }

        StatusDock(
            statusLines = statusLines,
            expanded = statusExpanded,
            busy = busy,
            onToggle = onToggleStatus,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = selectedApp != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            DetailSheet(
                app = selectedApp,
                busy = busy,
                progress = downloadProgress,
                iconLoader = iconLoader,
                mediaLoader = mediaLoader,
                installCheckTick = installCheckTick,
                glassesInstallStates = glassesInstallStates,
                statusLines = statusLines,
                statusExpanded = statusExpanded,
                onToggleStatus = onToggleStatus,
                onDismiss = { selectedApp = null },
                onInstall = onInstall,
            )
        }
    }
}

@Composable
private fun Header(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onReset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RokidBrewLogo(Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        BrandTitle(fontSize = 24)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !refreshing, onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            val rotation by animateFloatAsState(
                targetValue = if (refreshing) 360f else 0f,
                animationSpec = if (refreshing) infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ) else tween(300),
                label = "refresh-spin",
            )
            Icon(
                Icons.Outlined.Refresh,
                null,
                tint = if (refreshing) BrewGreen else BrewTextBright,
                modifier = Modifier.size(27.dp).graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

@Composable
private fun RokidBrewLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.075f
        val bag = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.38f)
            quadraticTo(size.width * 0.2f, size.height * 0.28f, size.width * 0.3f, size.height * 0.28f)
            lineTo(size.width * 0.7f, size.height * 0.28f)
            quadraticTo(size.width * 0.8f, size.height * 0.28f, size.width * 0.8f, size.height * 0.38f)
            lineTo(size.width * 0.8f, size.height * 0.8f)
            quadraticTo(size.width * 0.8f, size.height * 0.88f, size.width * 0.72f, size.height * 0.88f)
            lineTo(size.width * 0.28f, size.height * 0.88f)
            quadraticTo(size.width * 0.2f, size.height * 0.88f, size.width * 0.2f, size.height * 0.8f)
            close()
        }
        drawPath(
            path = bag,
            color = BrewGreen,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawArc(
            color = BrewGreen,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.33f, size.height * 0.04f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.34f, size.height * 0.34f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val prompt = Path().apply {
            moveTo(size.width * 0.34f, size.height * 0.53f)
            lineTo(size.width * 0.43f, size.height * 0.61f)
            lineTo(size.width * 0.34f, size.height * 0.69f)
        }
        drawPath(prompt, BrewGreen, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(
            BrewGreen,
            start = Offset(size.width * 0.52f, size.height * 0.7f),
            end = Offset(size.width * 0.64f, size.height * 0.7f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun BrandTitle(fontSize: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Rokid",
            color = BrewTextBright,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            "Brew",
            color = BrewGreen,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConnectionPanel(
    selectedHostApp: RokidHostApp,
    hostAppInstalled: Boolean,
    cxrConnection: CxrConnectionState,
    busy: Boolean,
    onHostAppSelected: (RokidHostApp) -> Unit,
    onAuthorize: () -> Unit,
) {
    val hostVersion = rememberHostAppVersion(selectedHostApp)
    val connectionStatus = connectionStatus(hostAppInstalled, cxrConnection, busy)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = BrewPanel.copy(alpha = 0.78f)),
        border = BorderStroke(1.dp, BrewBorderHi.copy(alpha = 0.46f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Visibility, null, tint = BrewGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Glasses connection", color = BrewTextBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(connectionStatus.color),
                )
                Spacer(Modifier.width(6.dp))
                Text("CXR-L Link", color = BrewMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(" / ", color = BrewMuted, fontSize = 11.sp)
                Text(
                    connectionStatus.label,
                    color = connectionStatus.color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            HostAppPicker(
                selectedHostApp = selectedHostApp,
                enabled = !busy,
                onSelect = onHostAppSelected,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HostAppBadge(selectedHostApp, Modifier.size(45.dp))
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text(
                        selectedHostApp.label,
                        color = BrewTextBright,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        selectedHostApp.packageName,
                        color = BrewMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            null,
                            tint = if (hostAppInstalled) BrewGreen else BrewAmber,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (hostAppInstalled) "Installed" else "Not installed",
                            color = if (hostAppInstalled) BrewGreen else BrewAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        hostVersion?.let {
                            Text("  $it", color = BrewMuted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                StoreActionButton(
                    label = "Authorize",
                    primary = false,
                    enabled = !busy,
                    icon = { Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(16.dp)) },
                    onClick = onAuthorize,
                    modifier = Modifier
                        .width(122.dp)
                        .height(36.dp),
                )
            }
        }
    }
}

private data class ConnectionStatus(val label: String, val color: Color)

private fun connectionStatus(
    hostAppInstalled: Boolean,
    connection: CxrConnectionState,
    busy: Boolean,
): ConnectionStatus = when {
    !hostAppInstalled -> ConnectionStatus("Missing", BrewAmber)
    connection.connected -> ConnectionStatus("Connected", BrewCyan)
    busy && connection.authorized -> ConnectionStatus("Connecting", BrewAmber)
    connection.connecting -> ConnectionStatus("Connecting", BrewAmber)
    connection.authorized -> ConnectionStatus("Authorized", BrewGreen)
    else -> ConnectionStatus("Needs auth", BrewMuted)
}

@Composable
private fun rememberHostAppVersion(hostApp: RokidHostApp): String? {
    val context = LocalContext.current
    val version by produceState<String?>(initialValue = null, hostApp) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(hostApp.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(hostApp.packageName, 0)
                }
                info.versionName?.let { "v$it" }
            }.getOrNull()
        }
    }
    return version
}

@Composable
private fun rememberHostAppIcon(hostApp: RokidHostApp): Drawable? {
    val context = LocalContext.current
    val icon by produceState<Drawable?>(initialValue = null, hostApp) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(hostApp.packageName) }.getOrNull()
        }
    }
    return icon
}

@Composable
private fun HostAppBadge(hostApp: RokidHostApp, modifier: Modifier = Modifier) {
    val painter = rememberDrawablePainter(rememberHostAppIcon(hostApp))
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(BrewBg)
            .border(1.dp, BrewBorderHi.copy(alpha = 0.72f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = "${hostApp.label} icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
            )
        } else {
            HostAppFallbackMark(hostApp, color = BrewMuted)
        }
    }
}

@Composable
private fun HostAppFallbackMark(hostApp: RokidHostApp, modifier: Modifier = Modifier, color: Color = BrewMuted) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            if (hostApp == RokidHostApp.GLOBAL) "Hi" else "CN",
            color = color,
            fontSize = if (hostApp == RokidHostApp.GLOBAL) 18.sp else 13.sp,
            lineHeight = if (hostApp == RokidHostApp.GLOBAL) 18.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

@Composable
private fun HostAppPicker(
    selectedHostApp: RokidHostApp,
    enabled: Boolean,
    onSelect: (RokidHostApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(43.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        RokidHostApp.values().forEach { hostApp ->
            HostAppSegment(
                hostApp = hostApp,
                selected = hostApp == selectedHostApp,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(hostApp) },
            )
        }
    }
}

@Composable
private fun HostAppSegment(
    hostApp: RokidHostApp,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val hostIcon = rememberDrawablePainter(rememberHostAppIcon(hostApp))
    val color = when {
        selected -> BrewTextBright
        enabled -> BrewText
        else -> BrewDim
    }
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) BrewGreen.copy(alpha = 0.10f) else BrewPanelAlt.copy(alpha = 0.72f))
            .border(1.dp, if (selected) BrewGreen else BrewBorderHi.copy(alpha = 0.40f), RoundedCornerShape(11.dp))
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (hostIcon != null) {
            Image(
                painter = hostIcon,
                contentDescription = "${hostApp.label} icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp)),
            )
        } else {
            HostAppFallbackMark(hostApp, Modifier.size(20.dp), if (selected) BrewGreen else color)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            hostApp.label,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BrewPanel.copy(alpha = 0.88f))
            .border(1.dp, BrewBorderHi.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = BrewMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = BrewTextBright, fontSize = 15.sp),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isBlank()) {
                    Text(
                        "Search",
                        color = BrewMuted.copy(alpha = 0.75f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            },
        )
        if (query.isNotBlank()) {
            Text(
                text = "x",
                color = BrewMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onQueryChange("") }
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun SectionTabs(active: SectionUi, onChange: (SectionUi) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BrewPanel.copy(alpha = 0.64f))
            .border(1.dp, BrewBorder, RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionTab("Phone + Glasses", active == SectionUi.PHONE, Modifier.weight(1f), leading = "phone") { onChange(SectionUi.PHONE) }
        SectionTab("Glasses Only", active == SectionUi.GLASSES, Modifier.weight(1f), leading = "glasses") { onChange(SectionUi.GLASSES) }
    }
}

@Composable
private fun SectionTab(label: String, selected: Boolean, modifier: Modifier, leading: String, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) BrewGreen.copy(alpha = 0.12f) else Color.Transparent)
            .border(if (selected) 1.dp else 0.dp, if (selected) BrewGreenDim else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading == "phone") Icon(Icons.Outlined.PhoneAndroid, null, tint = if (selected) BrewGreen else BrewText, modifier = Modifier.size(18.dp))
        if (leading == "glasses") Icon(Icons.Outlined.Visibility, null, tint = if (selected) BrewGreen else BrewText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (selected) BrewGreen else BrewTextBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TargetFilterStrip(active: TargetFilter, onChange: (TargetFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TargetFilter.values().forEach { filter ->
            TargetFilterChip(
                filter = filter,
                selected = active == filter,
                modifier = Modifier.weight(filter.weight),
            ) {
                onChange(filter)
            }
        }
    }
}

@Composable
private fun TargetFilterChip(filter: TargetFilter, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) BrewGreen.copy(alpha = 0.13f) else BrewPanel.copy(alpha = 0.76f))
            .border(1.dp, if (selected) BrewGreenDim else BrewBorder, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when (filter) {
            TargetFilter.ALL -> Icon(Icons.Outlined.Apps, null, tint = if (selected) BrewGreen else BrewText, modifier = Modifier.size(15.dp))
            TargetFilter.COMBO -> Icon(Icons.Outlined.PhoneAndroid, null, tint = if (selected) BrewGreen else BrewText, modifier = Modifier.size(15.dp))
            TargetFilter.GLASSES -> Icon(Icons.Outlined.Visibility, null, tint = if (selected) BrewGreen else BrewText, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            filter.label,
            color = if (selected) BrewTextBright else BrewText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HeroCarousel(
    apps: List<BrewApp>,
    mediaLoader: MediaLoader,
    iconLoader: IconLoader,
    onClick: (BrewApp) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { apps.size })
    LaunchedEffect(apps.map { it.id }) {
        pagerState.scrollToPage(0)
    }
    Column(Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(204.dp),
        ) { page ->
            val app = apps[page]
            HeroCard(
                app = app,
                mediaLoader = mediaLoader,
                iconLoader = iconLoader,
                onClick = { onClick(app) },
            )
        }
        if (apps.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(apps.size) { index ->
                    Box(
                        modifier = Modifier
                            .width(if (index == pagerState.currentPage) 18.dp else 7.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (index == pagerState.currentPage) BrewGreen else BrewBorderHi),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(app: BrewApp, mediaLoader: MediaLoader, iconLoader: IconLoader, onClick: () -> Unit) {
    val painter = rememberAppPainter(app = app, iconLoader = iconLoader, mediaLoader = mediaLoader, preferScreenshot = false)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(184.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrewPanel.copy(alpha = 0.66f)),
        border = BorderStroke(1.dp, BrewBorder),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(BrewPanelHi.copy(alpha = 0.55f), BrewBg.copy(alpha = 0.95f)),
                        ),
                    ),
            )
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Star, null, tint = BrewGreen, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("FEATURED", color = BrewGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (app.isNew) {
                    Spacer(Modifier.width(8.dp))
                    Badge("NEW", color = BrewAmber)
                }
            }
            Text(
                text = "v${app.version}",
                color = BrewMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 12.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(BrewBg.copy(alpha = 0.46f))
                    .border(1.dp, BrewBorder, RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, top = 56.dp, end = 24.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrewBg)
                            .border(1.dp, BrewBorderHi, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (painter != null) Image(painter, app.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else FallbackVisual(app.name, Modifier.fillMaxSize())
                    }
                    BrewButton(
                        label = "Install",
                        primary = true,
                        icon = { Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp)) },
                        onClick = onClick,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(136.dp)
                            .height(34.dp),
                    )
                }
                Column(Modifier.padding(start = 24.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.name,
                            color = BrewTextBright,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        app.summary,
                        color = BrewText.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    AppTargetTags(app, Modifier.align(Alignment.End).padding(bottom = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryStrip(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    if (categories.isEmpty()) return
    val labels = remember(categories) { listOf("All") + prioritizedCategories(categories) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { label ->
            val isSelected = if (label == "All") selected == null else selected == label
            CategoryChip(label, isSelected) {
                if (label == "All") onSelect(null) else onSelect(label)
            }
        }
    }
}

private fun prioritizedCategories(categories: List<String>): List<String> {
    val priority = listOf(NEW_CATEGORY, "AI", "Navigation", "Media", "Games", "Utilities", "Productivity", "Browser", "Mobility")
    return (priority.filter { wanted -> categories.any { it.equals(wanted, ignoreCase = true) } }
        .map { wanted -> categories.first { it.equals(wanted, ignoreCase = true) } } +
        categories.filterNot { category -> priority.any { it.equals(category, ignoreCase = true) } })
        .distinct()
}

private fun List<BrewApp>.curatedHeroApps(): List<BrewApp> {
    val newApps = filter { it.isNew }
        .sortedWith(compareByDescending<BrewApp> { it.publishedAt.orEmpty() }.thenBy { it.name.lowercase(Locale.US) })
    val featuredApps = filter { !it.isNew && it.isFeatured() }
        .sortedWith(compareBy<BrewApp> { it.featuredRank ?: Int.MAX_VALUE }.thenBy { it.name.lowercase(Locale.US) })
    return (newApps + featuredApps).distinctBy { it.id }
}

private fun categoryRowWeight(label: String): Float = when (label.lowercase()) {
    "navigation", "accessibility", "productivity" -> 1.65f
    "utilities", "mobility", "browser" -> 1.35f
    "games", "media", "more" -> 1.08f
    "all", "ai", "new" -> 0.92f
    else -> 1.18f
}

@Composable
private fun UpdateDialog(version: String, downloading: Boolean, downloadPercent: Int, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BrewPanelAlt),
            border = BorderStroke(1.dp, BrewBorderHi),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    if (downloading) "Downloading..." else "Update available",
                    style = MaterialTheme.typography.titleLarge,
                    color = BrewGreen,
                    fontFamily = BrewFont,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (downloading) "RokidBrew $version ($downloadPercent%)" else "RokidBrew $version is ready to install.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrewText,
                )
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { downloadPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = BrewGreen,
                        trackColor = BrewPanel,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !downloading) {
                        Text("Later", color = BrewDim)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onUpdate,
                        enabled = !downloading,
                        colors = ButtonDefaults.buttonColors(containerColor = BrewGreen),
                    ) {
                        Text("Update", color = BrewBg)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySheet(
    categories: List<String>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BrewPanelAlt),
                border = BorderStroke(1.dp, BrewBorderHi),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BrewDim),
                    )
                    Text(
                        "All categories",
                        color = BrewTextBright,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    val labels = remember(categories) { listOf("All") + categories }
                    labels.chunked(2).forEachIndexed { index, rowCategories ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (index == 0) 14.dp else 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (rowCategories.size == 1) {
                                val label = rowCategories.first()
                                SheetCategoryChip(
                                    label = label,
                                    selected = if (label == "All") selected == null else selected == label,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (label == "All") onSelect(null) else onSelect(label)
                                }
                            } else {
                                rowCategories.forEach { label ->
                                    SheetCategoryChip(
                                        label = label,
                                        selected = if (label == "All") selected == null else selected == label,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        if (label == "All") onSelect(null) else onSelect(label)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetCategoryChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    CategoryChip(label = label, selected = selected, modifier = modifier, onClick = onClick)
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    fun fixedSp(value: Float) = (value / fontScale.coerceAtLeast(1f)).sp
    Box(
        modifier = modifier
            .height(34.dp)
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) BrewGreen else BrewPanelAlt.copy(alpha = 0.86f))
            .border(1.dp, if (selected) BrewGreen else BrewBorderHi.copy(alpha = 0.44f), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                label,
                color = if (selected) BrewBg else BrewTextBright,
                fontSize = fixedSp(13f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryIcon(label: String, color: Color, size: androidx.compose.ui.unit.Dp = 15.dp) {
    val icon = when (label.lowercase()) {
        "more" -> Icons.Outlined.Apps
        "all" -> Icons.Outlined.Apps
        "new" -> Icons.Outlined.Star
        "ai" -> Icons.Outlined.Psychology
        "accessibility" -> Icons.Outlined.AccessibilityNew
        "browser" -> Icons.Outlined.Public
        "media" -> Icons.Outlined.PlayCircle
        "mobility" -> Icons.Outlined.ElectricScooter
        "navigation" -> Icons.Outlined.Navigation
        else -> Icons.Outlined.Apps
    }
    Icon(icon, null, tint = color, modifier = Modifier.size(size))
}

@Composable
private fun FeaturedShelf(
    apps: List<BrewApp>,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    onOpen: (BrewApp) -> Unit,
    onViewAll: () -> Unit,
) {
    SectionHeader(
        title = "Featured",
        action = "View all",
        onAction = onViewAll,
        modifier = Modifier.padding(top = 22.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        apps.forEach { app ->
            FeaturedAppCard(
                app = app,
                iconLoader = iconLoader,
                mediaLoader = mediaLoader,
                onClick = { onOpen(app) },
            )
        }
    }
}

@Composable
private fun FeaturedAppCard(
    app: BrewApp,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    onClick: () -> Unit,
) {
    val painter = rememberAppPainter(app = app, iconLoader = iconLoader, mediaLoader = mediaLoader, preferScreenshot = false)
    Column(
        modifier = Modifier
            .width(102.dp)
            .height(122.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BrewPanel.copy(alpha = 0.82f))
            .border(1.dp, BrewBorderHi.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .background(BrewBg)
                .border(1.dp, BrewBorderHi.copy(alpha = 0.72f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (painter != null) {
                Image(painter, app.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                FallbackVisual(app.name, Modifier.fillMaxSize())
            }
        }
        Text(
            app.name,
            color = BrewTextBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            app.category,
            color = BrewMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun AppShelf(
    apps: List<BrewApp>,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    busy: Boolean,
    progress: Map<String, Int>,
    installCheckTick: Int,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    onOpen: (BrewApp) -> Unit,
    onInstall: (BrewApp, String) -> Unit,
) {
    if (apps.isEmpty()) {
        EmptyState(modifier = Modifier.padding(top = 20.dp))
        return
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visibleApps = if (expanded) apps else apps.take(5)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BrewPanel.copy(alpha = 0.80f)),
        border = BorderStroke(1.dp, BrewBorderHi.copy(alpha = 0.38f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            visibleApps.forEachIndexed { index, app ->
                StoreAppRow(
                    app = app,
                    iconLoader = iconLoader,
                    mediaLoader = mediaLoader,
                    busy = busy,
                    progress = appProgress(app, progress),
                    installCheckTick = installCheckTick,
                    glassesInstallStates = glassesInstallStates,
                    onOpen = { onOpen(app) },
                    onInstall = onInstall,
                )
                if (index != visibleApps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BrewBorderHi.copy(alpha = 0.22f)),
                    )
                }
            }
            if (apps.size > 5) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (expanded) "Show less" else "Show more",
                        color = BrewGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        null,
                        tint = BrewGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreAppRow(
    app: BrewApp,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    busy: Boolean,
    progress: Int?,
    installCheckTick: Int,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    onOpen: () -> Unit,
    onInstall: (BrewApp, String) -> Unit,
) {
    val target = primaryInstallTarget(app)
    val phoneInstallState = if (app.hasTarget("phone")) rememberInstallState(app, "phone", installCheckTick) else MainActivity.InstallState.UNKNOWN
    val glassesInstallState = rememberGlassesInstallState(app, glassesInstallStates)
    val targetState = when (target) {
        "phone" -> phoneInstallState
        "glasses" -> glassesInstallState
        else -> MainActivity.InstallState.UNKNOWN
    }
    val artifact = target?.let(app::artifactFor)
    val actionLabel = when (targetState) {
        MainActivity.InstallState.INSTALLED -> "Installed"
        MainActivity.InstallState.UPDATE_AVAILABLE -> "Update"
        else -> "Install"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .clickable(onClick = onOpen)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, iconLoader, mediaLoader, Modifier.size(44.dp))
        Column(
            modifier = Modifier
                .padding(start = 11.dp)
                .weight(1f),
        ) {
            Text(
                app.name,
                color = BrewTextBright,
                fontSize = 13.2f.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.summary,
                color = BrewMuted,
                fontSize = 10.5f.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            AppTargetTags(app, modifier = Modifier.padding(top = 4.dp))
        }
        Column(
            modifier = Modifier.width(58.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                "v${app.version}",
                color = BrewText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatBytes(artifact?.sizeBytes),
                color = BrewMuted,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        if (progress != null) {
            CompactProgressLine(progress, Modifier.width(88.dp))
        } else if (targetState == MainActivity.InstallState.INSTALLED) {
            Row(
                modifier = Modifier.width(88.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.CheckCircle, null, tint = BrewGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Installed", color = BrewGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            StoreActionButton(
                label = actionLabel,
                primary = false,
                enabled = !busy && target != null,
                icon = {
                    Icon(
                        if (targetState == MainActivity.InstallState.UPDATE_AVAILABLE) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.Download,
                        null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = { target?.let { onInstall(app, it) } },
                modifier = Modifier
                    .width(88.dp)
                    .height(34.dp),
            )
        }
    }
}

@Composable
private fun StoreActionButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val border = if (primary) BrewGreen else BrewGreen
    val background = if (primary) BrewGreen else Color.Transparent
    val contentColor = if (primary) BrewBg else BrewGreen
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) background else BrewPanelHi.copy(alpha = 0.45f))
            .border(1.dp, if (enabled) border else BrewBorderHi, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) contentColor else BrewMuted) {
            if (icon != null) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(17.dp)) {
                    icon()
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                color = if (enabled) contentColor else BrewMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppCard(
    app: BrewApp,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    busy: Boolean,
    progress: Int?,
    installCheckTick: Int,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAppPainter(app = app, iconLoader = iconLoader, mediaLoader = mediaLoader, preferScreenshot = false)
    val alpha by animateFloatAsState(if (busy && progress == null) 0.52f else 1f, label = "cardAlpha")
    val phoneInstallState = if (app.hasTarget("phone")) rememberInstallState(app, "phone", installCheckTick) else MainActivity.InstallState.UNKNOWN
    val glassesInstallState = rememberGlassesInstallState(app, glassesInstallStates)
    val displayInstallState = if (glassesInstallState != MainActivity.InstallState.UNKNOWN) glassesInstallState else phoneInstallState
    val fontScale = LocalDensity.current.fontScale
    fun fixedSp(value: Float) = (value / fontScale.coerceAtLeast(1f)).sp
    Card(
        modifier = modifier
            .height(126.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BrewPanel.copy(alpha = 0.82f)),
        border = BorderStroke(1.dp, BrewBorder),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app, iconLoader, mediaLoader, Modifier.size(64.dp))
                Column(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .weight(1f),
                ) {
                    Text(
                        app.name,
                        color = BrewTextBright.copy(alpha = alpha),
                        fontSize = fixedSp(16f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "v${app.version}",
                        color = BrewMuted.copy(alpha = alpha),
                        fontSize = fixedSp(11f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        app.summary,
                        color = BrewText.copy(alpha = 0.78f),
                        fontSize = fixedSp(12f),
                        lineHeight = fixedSp(16f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    AppTargetTags(app, modifier = Modifier.padding(top = 8.dp))
                }
                Icon(Icons.Outlined.KeyboardArrowRight, null, tint = BrewMuted, modifier = Modifier.size(22.dp))
            }
            if (progress != null) {
                CompactProgressLine(progress, Modifier.align(Alignment.BottomEnd).width(112.dp))
            }
            InstallStateBadge(
                state = displayInstallState,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun AppTargetTags(app: BrewApp, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (app.hasTarget("phone")) MiniTargetTag("PHONE", minWidth = 48.dp)
        if (app.hasTarget("glasses")) MiniTargetTag("GLASSES", minWidth = 78.dp)
    }
}

@Composable
private fun MiniTargetTag(label: String, minWidth: androidx.compose.ui.unit.Dp, color: Color = BrewGreen) {
    val fontScale = LocalDensity.current.fontScale
    fun fixedSp(value: Float) = (value / fontScale.coerceAtLeast(1f)).sp
    Box(
        modifier = Modifier
            .widthIn(min = minWidth)
            .height(18.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = color,
            fontSize = fixedSp(7.2f),
            lineHeight = fixedSp(7.2f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

@Composable
private fun DetailSheet(
    app: BrewApp?,
    busy: Boolean,
    progress: Map<String, Int>,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    installCheckTick: Int,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    statusLines: List<String>,
    statusExpanded: Boolean,
    onToggleStatus: () -> Unit,
    onDismiss: () -> Unit,
    onInstall: (BrewApp, String) -> Unit,
) {
    if (app == null) return
    var expandedScreenshotIndex by remember(app.id) { mutableStateOf<Int?>(null) }
    val phoneInstallState = if (app.hasTarget("phone")) rememberInstallState(app, "phone", installCheckTick) else MainActivity.InstallState.UNKNOWN
    val glassesInstallState = rememberGlassesInstallState(app, glassesInstallStates)
    val detailScrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrewBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(BrewPanelHi.copy(alpha = 0.36f), Color.Transparent),
                    center = Offset(120f, 80f),
                    radius = 430f,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(detailScrollState)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 132.dp),
        ) {
            DetailTopBar(onDismiss = onDismiss)
            DetailHeroHeader(app = app, iconLoader = iconLoader, mediaLoader = mediaLoader)
            DetailScreenshotStrip(
                app = app,
                mediaLoader = mediaLoader,
                onScreenshotClick = { expandedScreenshotIndex = it },
            )
            val phoneProgress = progress["${app.id}:phone"]
            val glassesProgress = progress["${app.id}:glasses"]
            if (phoneProgress != null || glassesProgress != null) {
                ProgressLine(phoneProgress ?: glassesProgress ?: 0, Modifier.padding(top = 14.dp))
            }
            DetailInstallActions(
                app = app,
                busy = busy,
                phoneInstallState = phoneInstallState,
                glassesInstallState = glassesInstallState,
                onInstall = onInstall,
                modifier = Modifier.padding(top = 18.dp),
            )
            DetailInfoPanel(
                app = app,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        StatusDock(
            statusLines = statusLines,
            expanded = statusExpanded,
            busy = busy,
            onToggle = onToggleStatus,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        expandedScreenshotIndex?.let { index ->
            ScreenshotViewerDialog(
                app = app,
                initialIndex = index,
                mediaLoader = mediaLoader,
                onDismiss = { expandedScreenshotIndex = null },
            )
        }
    }
}

@Composable
private fun DetailTopBar(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ArrowBack,
            null,
            tint = BrewTextBright,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onDismiss)
                .padding(3.dp),
        )
        Spacer(Modifier.width(16.dp))
        RokidBrewLogo(Modifier.size(34.dp))
        Spacer(Modifier.width(9.dp))
        BrandTitle(fontSize = 21)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DetailHeroHeader(app: BrewApp, iconLoader: IconLoader, mediaLoader: MediaLoader) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, iconLoader, mediaLoader, Modifier.size(88.dp))
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(
                app.name,
                color = BrewTextBright,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 29.sp,
            )
            Text(
                app.category,
                color = BrewGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoPill("v${app.version}")
            }
            Text(
                app.author,
                color = BrewMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 9.dp),
            )
            TargetTags(app, Modifier.padding(top = 9.dp))
        }
    }
}

@Composable
private fun InfoPill(label: String, color: Color = BrewTextBright, leading: String? = null) {
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(BrewPanel.copy(alpha = 0.78f))
            .border(1.dp, BrewBorderHi.copy(alpha = 0.62f), RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            Text(leading, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = BrewFont)
    }
}

@Composable
private fun DetailInstallActions(
    app: BrewApp,
    busy: Boolean,
    phoneInstallState: MainActivity.InstallState,
    glassesInstallState: MainActivity.InstallState,
    onInstall: (BrewApp, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (app.hasTarget("phone")) {
            StoreActionButton(
                label = installButtonLabel("phone", phoneInstallState),
                primary = true,
                enabled = !busy && phoneInstallState != MainActivity.InstallState.INSTALLED,
                icon = { Icon(Icons.Outlined.Download, null, modifier = Modifier.size(20.dp)) },
                onClick = { onInstall(app, "phone") },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            )
        }
        if (app.hasTarget("glasses")) {
            StoreActionButton(
                label = installButtonLabel("glasses", glassesInstallState),
                primary = false,
                enabled = !busy && glassesInstallState != MainActivity.InstallState.INSTALLED,
                icon = { Icon(Icons.Outlined.Visibility, null, modifier = Modifier.size(20.dp)) },
                onClick = { onInstall(app, "glasses") },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            )
        }
    }
}

@Composable
private fun DetailInfoPanel(app: BrewApp, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = BrewPanel.copy(alpha = 0.76f)),
        border = BorderStroke(1.dp, BrewBorderHi.copy(alpha = 0.46f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DetailSectionTitle("About")
                Spacer(Modifier.weight(1f))
            }
            AboutBody(app.aboutText(), Modifier.padding(top = 11.dp))
            SourceLine(app, Modifier.padding(top = 18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(1.dp)
                    .background(BrewBorderHi.copy(alpha = 0.32f)),
            )
            WhatsNewSection(app, Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun DetailSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        color = BrewTextBright,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
private fun AboutBody(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { readableDescriptionBlocks(text) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            if (block.startsWith("- ") || block.startsWith("* ")) {
                DetailBulletLine(block.drop(2), Modifier.padding(top = if (index == 0) 0.dp else 7.dp))
            } else {
                Text(
                    block,
                    color = BrewText,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp),
                )
            }
        }
    }
}

private fun readableDescriptionBlocks(text: String): List<String> {
    val cleaned = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .map { it.trim() }
        .joinToString("\n")
        .trim()
    if (cleaned.isBlank()) return emptyList()

    val naturalBlocks = cleaned
        .split(Regex("\\n\\s*\\n"))
        .map { it.lines().filter(String::isNotBlank).joinToString(" ").trim() }
        .filter(String::isNotBlank)
    if (naturalBlocks.size > 1) return naturalBlocks

    val bulletLines = cleaned.lines().filter { it.startsWith("- ") || it.startsWith("* ") }
    if (bulletLines.isNotEmpty()) return cleaned.lines().filter(String::isNotBlank)

    val sentences = cleaned
        .replace('\n', ' ')
        .split(Regex("(?<=[.!?])\\s+"))
        .map(String::trim)
        .filter(String::isNotBlank)
    if (sentences.size < 3) return listOf(cleaned.replace('\n', ' '))

    return sentences.chunked(2).map { it.joinToString(" ") }
}

@Composable
private fun DetailBulletLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BrewGreen),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = BrewText,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WhatsNewSection(app: BrewApp, modifier: Modifier = Modifier) {
    val release = app.releases.firstOrNull()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DetailSectionTitle("What's New")
            Spacer(Modifier.weight(1f))
            if (release?.sourceReleaseUrl != null) {
                Text("View full changelog", color = BrewGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (release == null) {
            Text(
                "Release notes will appear here when the registry imports GitHub Releases.",
                color = BrewMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            return
        }
        val title = buildList {
            release.version?.let { add("v$it") }
            release.date?.take(10)?.let { add(it) }
        }.joinToString(" / ").ifBlank { "Latest release" }
        Text(
            title,
            color = BrewGreen,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        release.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                notes,
                color = BrewText,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        release.changes.take(4).forEach { change ->
            DetailBulletLine(change, Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun SystemLogPanel(
    statusLines: List<String>,
    expanded: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logScrollState = rememberScrollState()
    val latestLine = statusLines.lastOrNull() ?: "Ready."
    LaunchedEffect(expanded, statusLines.size) {
        if (expanded) logScrollState.animateScrollTo(logScrollState.maxValue)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BrewPanel.copy(alpha = 0.94f))
            .border(1.dp, BrewBorderHi.copy(alpha = 0.52f), RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
            .animateContentSize(),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .width(36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BrewMuted.copy(alpha = 0.50f))
                .align(Alignment.CenterHorizontally),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 7.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TerminalBadge(size = 30.dp, active = false)
            Spacer(Modifier.width(12.dp))
            Text("System Log", color = BrewTextBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = BrewFont)
            Spacer(Modifier.weight(1f))
            LogStatusPill(busy)
            Spacer(Modifier.width(10.dp))
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                null,
                tint = BrewTextBright,
                modifier = Modifier.size(21.dp),
            )
        }
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp, max = 172.dp)
                    .padding(start = 18.dp, end = 18.dp, bottom = 13.dp)
                    .verticalScroll(logScrollState),
            ) {
                Text(
                    statusLines.joinToString("\n"),
                    color = BrewMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = BrewFont,
                )
            }
        } else {
            Text(
                latestLine,
                color = BrewMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 32.dp, end = 18.dp, bottom = 10.dp),
                fontFamily = BrewFont,
            )
        }
    }
}

@Composable
private fun SourceLine(app: BrewApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BrewPanelAlt.copy(alpha = 0.88f))
            .border(1.dp, BrewBorderHi.copy(alpha = 0.52f), RoundedCornerShape(12.dp))
            .clickable(enabled = app.sourceUrl != null) {
                app.sourceUrl?.let { sourceUrl ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl)))
                    }
                }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(BrewTextBright),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_github_mark),
                contentDescription = "GitHub",
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("GitHub", color = BrewTextBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(
            app.author,
            color = BrewMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.34f),
        )
        app.sourceUrl?.let { sourceUrl ->
            Spacer(Modifier.width(8.dp))
            Text(
                sourceUrl.removePrefix("https://"),
                color = BrewGreen,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.66f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.KeyboardArrowRight, null, tint = BrewMuted, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun DetailScreenshotStrip(
    app: BrewApp,
    mediaLoader: MediaLoader,
    onScreenshotClick: (Int) -> Unit,
) {
    if (app.screenshotCount == 0) return
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            repeat(app.screenshotCount) { index ->
                val screenshotRef = app.screenshotAt(index)
                val screenshot = rememberScreenshotPainter(screenshotRef.assetName, screenshotRef.url, mediaLoader)
                Box(
                    modifier = Modifier
                        .width(92.dp)
                        .height(154.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(BrewPanelAlt)
                        .border(1.dp, BrewBorderHi.copy(alpha = 0.6f), RoundedCornerShape(9.dp))
                        .clickable { onScreenshotClick(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (screenshot != null) {
                        Image(
                            painter = screenshot,
                            contentDescription = "${app.name} screenshot ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("Loading", color = BrewMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScreenshotViewerDialog(
    app: BrewApp,
    initialIndex: Int,
    mediaLoader: MediaLoader,
    onDismiss: () -> Unit,
) {
    val startPage = initialIndex.coerceIn(0, (app.screenshotCount - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { app.screenshotCount })
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f)),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val screenshotRef = app.screenshotAt(page)
                val painter = rememberScreenshotPainter(screenshotRef.assetName, screenshotRef.url, mediaLoader)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (painter != null) {
                        Image(
                            painter = painter,
                            contentDescription = "${app.name} screenshot ${page + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("Loading preview", color = BrewTextBright, fontSize = 14.sp)
                    }
                }
            }
            Text(
                "${pagerState.currentPage + 1}/${app.screenshotCount}",
                color = BrewTextBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 14.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(BrewBg.copy(alpha = 0.76f))
                    .border(1.dp, BrewBorderHi.copy(alpha = 0.58f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
            Icon(
                Icons.Outlined.ArrowBack,
                null,
                tint = BrewTextBright,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 9.dp, start = 10.dp)
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrewBg.copy(alpha = 0.76f))
                    .clickable(onClick = onDismiss)
                    .padding(7.dp),
            )
        }
    }
}

@Composable
private fun TerminalBadge(size: androidx.compose.ui.unit.Dp, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(BrewBg)
            .border(1.dp, if (active) BrewGreenDim else BrewBorderHi, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(">_", color = if (active) BrewGreen else BrewTextBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = BrewFont)
    }
}

@Composable
private fun LogStatusPill(busy: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (busy) BrewAmber.copy(alpha = 0.14f) else BrewGreen.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            if (busy) "BUSY" else "READY",
            color = if (busy) BrewAmber else BrewGreen,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusDock(
    statusLines: List<String>,
    expanded: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SystemLogPanel(
        statusLines = statusLines,
        expanded = expanded,
        busy = busy,
        onToggle = onToggle,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = BrewTextBright, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() },
            ) {
                Text(action, color = BrewGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Outlined.KeyboardArrowRight, null, tint = BrewGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TargetTags(app: BrewApp, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(top = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (app.isNew) TargetTag("NEW", color = BrewAmber)
        if (app.hasTarget("phone")) TargetTag("PHONE", "phone")
        if (app.hasTarget("glasses")) TargetTag("GLASSES", "glasses")
        if (app.phoneRequired && !app.hasTarget("phone")) TargetTag("PHONE REQ")
    }
}

@Composable
private fun TargetTag(label: String, icon: String? = null, color: Color = BrewGreen) {
    val tagWidth = when (label) {
        "NEW" -> 68.dp
        "PHONE" -> 78.dp
        "GLASSES" -> 90.dp
        "PHONE REQ" -> 102.dp
        else -> 82.dp
    }
    Row(
        modifier = Modifier
            .widthIn(min = tagWidth)
            .height(27.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon == "phone") Icon(Icons.Outlined.PhoneAndroid, null, tint = color, modifier = Modifier.size(11.dp))
        if (icon == "glasses") Icon(Icons.Outlined.Visibility, null, tint = color, modifier = Modifier.size(12.dp))
        if (icon != null) Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = color,
            fontSize = 8.4f.sp,
            lineHeight = 8.4f.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

@Composable
private fun Badge(label: String, modifier: Modifier = Modifier, color: Color = BrewGreen) {
    Text(
        label,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun InstallStateBadge(state: MainActivity.InstallState, modifier: Modifier = Modifier) {
    when (state) {
        MainActivity.InstallState.INSTALLED -> Badge("INSTALLED", modifier)
        MainActivity.InstallState.UPDATE_AVAILABLE -> Badge("UPDATE", modifier)
        else -> Unit
    }
}

@Composable
private fun rememberGlassesInstallState(
    app: BrewApp,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
): MainActivity.InstallState {
    val packageName = app.artifactFor("glasses")?.packageName?.takeIf { it.isNotBlank() }
    return packageName?.let { glassesInstallStates[it] } ?: MainActivity.InstallState.UNKNOWN
}

@Composable
private fun rememberInstallState(app: BrewApp, target: String, installCheckTick: Int): MainActivity.InstallState {
    val context = LocalContext.current
    val artifact = app.artifactFor(target)
    val state by produceState(
        initialValue = MainActivity.InstallState.UNKNOWN,
        artifact?.packageName,
        artifact?.versionCode,
        installCheckTick,
    ) {
        value = withContext(Dispatchers.IO) { context.installStateFor(artifact) }
    }
    return state
}

private fun installButtonLabel(target: String, state: MainActivity.InstallState): String {
    return when (state) {
        MainActivity.InstallState.INSTALLED -> "Installed"
        MainActivity.InstallState.UPDATE_AVAILABLE -> "Update $target"
        else -> "Install $target"
    }
}

@Composable
private fun BrewButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) BrewGreen else BrewPanel.copy(alpha = 0.66f),
            contentColor = if (primary) BrewBg else BrewTextBright,
            disabledContainerColor = BrewPanel,
            disabledContentColor = BrewDim,
        ),
        border = BorderStroke(1.dp, if (primary) BrewGreen else BrewBorder),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(9.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun AppIcon(app: BrewApp, iconLoader: IconLoader, mediaLoader: MediaLoader, modifier: Modifier = Modifier) {
    val painter = rememberAppPainter(app = app, iconLoader = iconLoader, mediaLoader = mediaLoader, preferScreenshot = false)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BrewPanelHi)
            .border(1.dp, BrewBorderHi, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(painter, app.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            FallbackVisual(app.name, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BrewPanel)
            .border(1.dp, BrewBorder, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("No apps found", color = BrewMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressLine(progress: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Downloading", color = BrewGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("$progress%", color = BrewGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BrewBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((progress.coerceIn(0, 100) / 100f).coerceAtLeast(0.02f))
                    .height(5.dp)
                    .background(BrewGreen),
            )
        }
    }
}

@Composable
private fun CompactProgressLine(progress: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BrewBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((progress.coerceIn(0, 100) / 100f).coerceAtLeast(0.02f))
                    .height(5.dp)
                    .background(BrewGreen),
            )
        }
        Text(
            "$progress%",
            color = BrewGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun FallbackVisual(label: String, modifier: Modifier = Modifier) {
    val initials = when {
        label.contains("M365", ignoreCase = true) -> "M3"
        label.contains("Rokid", ignoreCase = true) -> "RO"
        label.contains("GMaps", ignoreCase = true) -> "MAP"
        else -> label.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").take(2).ifBlank { label.take(2).uppercase() }
    }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(BrewPanelHi, BrewBg),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = BrewGreen,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = Shadow(BrewGreen, blurRadius = 12f)),
        )
    }
}

@Composable
private fun rememberAppPainter(
    app: BrewApp,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    preferScreenshot: Boolean,
): Painter? {
    val screenshotKey = "${app.screenshotAssets.joinToString("|")}::${app.screenshotUrls.joinToString("|")}"
    val drawable by produceState<Drawable?>(initialValue = null, app.id, screenshotKey, preferScreenshot) {
        value = withContext(Dispatchers.IO) {
            if (preferScreenshot) {
                mediaLoader.load(app.screenshotAsset, app.screenshotUrl) ?: iconLoader.load(app.id, app.iconUrl)
            } else {
                iconLoader.load(app.id, app.iconUrl)
            }
        }
    }
    return rememberDrawablePainter(drawable)
}

@Composable
private fun rememberScreenshotPainter(assetName: String?, url: String?, mediaLoader: MediaLoader): Painter? {
    val drawable by produceState<Drawable?>(initialValue = null, assetName, url) {
        value = withContext(Dispatchers.IO) { mediaLoader.load(assetName, url) }
    }
    return rememberDrawablePainter(drawable)
}

@Composable
private fun rememberDrawablePainter(drawable: Drawable?): Painter? {
    return remember(drawable) {
        drawable?.toBitmap()?.asImageBitmap()?.let(::BitmapPainter)
    }
}

private fun appProgress(app: BrewApp, progress: Map<String, Int>): Int? =
    progress["${app.id}:phone"] ?: progress["${app.id}:glasses"]

private fun primaryInstallTarget(app: BrewApp): String? = when {
    app.hasTarget("phone") -> "phone"
    app.hasTarget("glasses") -> "glasses"
    else -> null
}

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}

private fun Context.installStateFor(artifact: BrewArtifact?): MainActivity.InstallState {
    val packageName = artifact?.packageName?.takeIf { it.isNotBlank() } ?: return MainActivity.InstallState.UNKNOWN
    val info = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }.getOrNull() ?: return MainActivity.InstallState.NOT_INSTALLED
    val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    val registryVersion = artifact.versionCode ?: return MainActivity.InstallState.INSTALLED
    return if (installedVersion < registryVersion) {
        MainActivity.InstallState.UPDATE_AVAILABLE
    } else {
        MainActivity.InstallState.INSTALLED
    }
}

private enum class TargetFilter(val label: String, val shelfTitle: String, val weight: Float) {
    ALL("All", "All apps", 0.72f),
    COMBO("Phone + Glasses", "Phone + Glasses", 1.42f),
    GLASSES("Glasses only", "Glasses only", 1.12f);

    fun matches(app: BrewApp): Boolean = when (this) {
        ALL -> true
        COMBO -> app.isPhoneSection()
        GLASSES -> !app.isPhoneSection() && app.hasTarget("glasses")
    }
}

private enum class SectionUi { PHONE, GLASSES }

@Composable
private fun RokidBrewTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BrewBg,
            surface = BrewPanel,
            primary = BrewGreen,
            onPrimary = BrewBg,
            onSurface = BrewText,
        ),
        content = {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = density.density,
                    fontScale = density.fontScale.coerceAtMost(1.0f),
                ),
            ) {
                Surface(color = BrewBg, content = content)
            }
        },
    )
}

private val BrewFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val BrewBg = Color(0xFF020404)
private val BrewPanel = Color(0xFF0B0F0F)
private val BrewPanelAlt = Color(0xFF111616)
private val BrewPanelHi = Color(0xFF1A2020)
private val BrewTextBright = Color(0xFFF1F6F4)
private val BrewText = Color(0xFFCDD6D2)
private val BrewMuted = Color(0xFF98A19D)
private val BrewDim = Color(0xFF5D6A65)
private val BrewGreen = Color(0xFF8CFF2F)
private val BrewGreenDim = Color(0xFF4BAA28)
private val BrewCyan = Color(0xFF56C8F2)
private val BrewBorder = Color(0xFF1E2524)
private val BrewBorderHi = Color(0xFF303938)
private val BrewAmber = Color(0xFFFFB347)
