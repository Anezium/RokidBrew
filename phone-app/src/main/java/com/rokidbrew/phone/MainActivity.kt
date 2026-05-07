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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ElectricScooter
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.fontResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        cxrL = CxrLHiRokidSession(
            activity = this,
            onStatus = ::log,
            onBusyChanged = ::updateBusy,
        )
        apps = BrewIndex.loadInitial(this)

        setContent {
            RokidBrewTheme {
                BrewPhoneApp(
                    apps = apps,
                    busy = busy,
                    refreshing = refreshing,
                    installCheckTick = installCheckTick,
                    statusLines = statusLines,
                    statusExpanded = statusExpanded,
                    downloadProgress = downloadProgress,
                    glassesInstallStates = glassesInstallStates,
                    iconLoader = iconLoader,
                    mediaLoader = mediaLoader,
                    onToggleStatus = { statusExpanded = !statusExpanded },
                    onRefresh = { refreshStoreIndex(manual = true) },
                    onAuthorize = { runWithPrerequisites { cxrL.requestAuthorization() } },
                    onInstall = { app, target ->
                        if (target == "glasses") {
                            runWithPrerequisites { installArtifact(app, target) }
                        } else {
                            installArtifact(app, target)
                        }
                    },
                )
            }
        }
        warmAssets(apps)
        refreshStoreIndex(manual = false)
        log("Ready. Authorize Hi Rokid before installing glasses APKs.")
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
            if (manual) log("Refreshing store registry...")
            runCatching {
                BrewIndex.refresh(this@MainActivity)
            }.onSuccess { refresh ->
                apps = refresh.apps
                warmAssets(refresh.apps)
                installCheckTick += 1
                if (cxrL.hasAuthorization()) refreshGlassesInstallStates(refresh.apps)
                log("Store registry updated (${refresh.apps.size} apps).")
            }.onFailure { error ->
                log("Remote registry unavailable: ${error.message ?: error.javaClass.simpleName}")
            }.also {
                refreshing = false
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
            refreshGlassesInstallStates(apps)
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
    installCheckTick: Int,
    statusLines: List<String>,
    statusExpanded: Boolean,
    downloadProgress: Map<String, Int>,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    onToggleStatus: () -> Unit,
    onRefresh: () -> Unit,
    onAuthorize: () -> Unit,
    onInstall: (BrewApp, String) -> Unit,
) {
    var activeSection by rememberSaveable { mutableStateOf(SectionUi.PHONE) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<BrewApp?>(null) }
    val scrollState = rememberScrollState()
    val uiScope = rememberCoroutineScope()

    BackHandler(enabled = selectedApp != null) {
        selectedApp = null
    }

    val sectionApps = remember(apps, activeSection) {
        apps.filter { app ->
            when (activeSection) {
                SectionUi.PHONE -> app.isPhoneSection()
                SectionUi.GLASSES -> !app.isPhoneSection() && app.hasTarget("glasses")
            }
        }
    }
    val visibleApps = remember(sectionApps, categoryFilter, query) {
        sectionApps.filter { app ->
            val categoryOk = categoryFilter == null || app.category.equals(categoryFilter, ignoreCase = true)
            val searchOk = query.isBlank() ||
                app.name.contains(query, ignoreCase = true) ||
                app.author.contains(query, ignoreCase = true) ||
                app.category.contains(query, ignoreCase = true) ||
                app.summary.contains(query, ignoreCase = true) ||
                app.description.contains(query, ignoreCase = true)
            categoryOk && searchOk
        }
    }
    val categories = remember(sectionApps) { sectionApps.map { it.category }.distinct().sorted() }
    val hero = visibleApps.firstOrNull() ?: sectionApps.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrewBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(BrewGreen.copy(alpha = 0.10f), Color.Transparent),
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
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 190.dp),
        ) {
            Header(
                busy = busy,
                refreshing = refreshing,
                onAuthorize = onAuthorize,
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
            SectionTabs(
                active = activeSection,
                onChange = {
                    activeSection = it
                    categoryFilter = null
                    uiScope.launch { scrollState.scrollTo(0) }
                },
            )
            hero?.let {
                HeroCard(
                    app = it,
                    mediaLoader = mediaLoader,
                    iconLoader = iconLoader,
                    onClick = { selectedApp = it },
                )
            }
            CategoryStrip(
                categories = categories,
                selected = categoryFilter,
                onSelect = { categoryFilter = it },
            )
            AppShelf(
                title = if (activeSection == SectionUi.PHONE) "Phone + Glasses" else "Glasses Only",
                apps = visibleApps,
                iconLoader = iconLoader,
                mediaLoader = mediaLoader,
                busy = busy,
                progress = downloadProgress,
                installCheckTick = installCheckTick,
                glassesInstallStates = glassesInstallStates,
                onOpen = { selectedApp = it },
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
                onDismiss = { selectedApp = null },
                onInstall = { app, target ->
                    selectedApp = null
                    onInstall(app, target)
                },
            )
        }
    }
}

@Composable
private fun Header(
    busy: Boolean,
    refreshing: Boolean,
    onAuthorize: () -> Unit,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onReset),
        ) {
            Text(
                text = "RokidBrew",
                color = BrewTextBright,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), offset = Offset(0f, 4f), blurRadius = 8f)),
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, BrewGreenDim, RoundedCornerShape(24.dp))
                    .background(BrewPanel.copy(alpha = 0.60f))
                    .clickable(enabled = !refreshing, onClick = onRefresh)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Refresh, null, tint = BrewTextBright, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text(if (refreshing) "Sync" else "Refresh", color = BrewGreen, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        Text(
            text = "Homebrew APKs from community projects. Check sources and install at your own risk.",
            color = BrewMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) {
            BrewButton(
                label = "Authorize",
                primary = false,
                enabled = !busy,
                icon = { Icon(Icons.Outlined.Security, null, modifier = Modifier.size(22.dp)) },
                onClick = onAuthorize,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BrewPanel.copy(alpha = 0.62f))
            .border(1.dp, BrewBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = BrewMuted, modifier = Modifier.size(25.dp))
        Spacer(Modifier.width(14.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = BrewTextBright, fontSize = 14.sp, fontFamily = BrewFont),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isBlank()) {
                    Text(
                        "Search AR apps and experiences",
                        color = BrewMuted.copy(alpha = 0.62f),
                        fontSize = 14.sp,
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
        if (leading == "glasses") Text("∞", color = if (selected) BrewGreen else BrewText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    var showMore by remember { mutableStateOf(false) }
    val visibleCategories = remember(categories) { prioritizedCategories(categories).take(4) }
    val hasHiddenCategories = categories.size > visibleCategories.size
    val chipLabels = remember(visibleCategories, hasHiddenCategories) {
        buildList {
            add("All")
            addAll(visibleCategories)
            if (hasHiddenCategories) add("More")
        }
    }
    val firstRowCount = when {
        chipLabels.size <= 3 -> chipLabels.size
        chipLabels.size == 4 -> 2
        else -> 3
    }
    val rows = listOf(chipLabels.take(firstRowCount), chipLabels.drop(firstRowCount)).filter { it.isNotEmpty() }
    SectionHeader(
        title = "Categories",
        action = if (selected == null) null else "Clear",
        onAction = { onSelect(null) },
        modifier = Modifier.padding(top = 22.dp),
    )
    rows.forEachIndexed { index, row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (index == 0) 14.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { label ->
                val isSelected = when (label) {
                    "All" -> selected == null
                    "More" -> showMore || (selected != null && selected !in visibleCategories)
                    else -> selected == label
                }
                CategoryChip(label, isSelected, Modifier.weight(categoryRowWeight(label))) {
                    when (label) {
                        "All" -> onSelect(null)
                        "More" -> showMore = true
                        else -> onSelect(label)
                    }
                }
            }
        }
    }
    if (showMore) {
        CategorySheet(
            categories = categories,
            selected = selected,
            onDismiss = { showMore = false },
            onSelect = {
                onSelect(it)
                showMore = false
            },
        )
    }
}

private fun prioritizedCategories(categories: List<String>): List<String> {
    val priority = listOf("AI", "Navigation", "Media", "Games", "Utilities", "Productivity", "Browser", "Mobility")
    return (priority.filter { wanted -> categories.any { it.equals(wanted, ignoreCase = true) } }
        .map { wanted -> categories.first { it.equals(wanted, ignoreCase = true) } } +
        categories.filterNot { category -> priority.any { it.equals(category, ignoreCase = true) } })
        .distinct()
}

private fun categoryRowWeight(label: String): Float = when (label.lowercase()) {
    "navigation", "accessibility", "productivity" -> 1.65f
    "utilities", "mobility", "browser" -> 1.35f
    "games", "media", "more" -> 1.08f
    "all", "ai" -> 0.92f
    else -> 1.18f
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
private fun CategoryChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    fun fixedSp(value: Float) = (value / fontScale.coerceAtLeast(1f)).sp
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrewGreen else BrewPanel)
            .border(1.dp, if (selected) BrewGreenDim else BrewBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            CategoryIcon(label, if (selected) BrewTextBright else BrewText, size = 13.dp)
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                color = if (selected) BrewTextBright else BrewText,
                fontSize = fixedSp(10.2f),
                fontWeight = FontWeight.Medium,
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
private fun AppShelf(
    title: String,
    apps: List<BrewApp>,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    busy: Boolean,
    progress: Map<String, Int>,
    installCheckTick: Int,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    onOpen: (BrewApp) -> Unit,
) {
    SectionHeader(title = title, action = "${apps.size} apps", modifier = Modifier.padding(top = 24.dp))
    if (apps.isEmpty()) {
        EmptyState(modifier = Modifier.padding(top = 12.dp))
        return
    }
    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        apps.forEach { app ->
            AppCard(
                app = app,
                iconLoader = iconLoader,
                mediaLoader = mediaLoader,
                busy = busy,
                progress = appProgress(app, progress),
                installCheckTick = installCheckTick,
                glassesInstallStates = glassesInstallStates,
                onOpen = { onOpen(app) },
                modifier = Modifier.fillMaxWidth(),
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
            .height(104.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrewPanel.copy(alpha = 0.78f)),
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
                AppIcon(app, iconLoader, mediaLoader, Modifier.size(68.dp))
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (app.hasTarget("phone")) MiniTargetTag("PHONE", "phone", width = 74.dp)
        if (app.hasTarget("glasses")) MiniTargetTag("GLASSES", "glasses", width = 90.dp)
    }
}

@Composable
private fun MiniTargetTag(label: String, icon: String, width: androidx.compose.ui.unit.Dp) {
    val fontScale = LocalDensity.current.fontScale
    fun fixedSp(value: Float) = (value / fontScale.coerceAtLeast(1f)).sp
    Row(
        modifier = Modifier
            .width(width)
            .height(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(BrewGreen.copy(alpha = 0.08f))
            .border(1.dp, BrewGreenDim, RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon == "phone") Icon(Icons.Outlined.PhoneAndroid, null, tint = BrewGreen, modifier = Modifier.size(11.dp))
        if (icon == "glasses") Text("∞", color = BrewGreen, fontSize = fixedSp(14f), fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(3.dp))
        Text(
            label,
            color = BrewGreen,
            fontSize = fixedSp(8f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
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
    onDismiss: () -> Unit,
    onInstall: (BrewApp, String) -> Unit,
) {
    if (app == null) return
    var expandedScreenshot by remember(app.id) { mutableStateOf<BrewScreenshot?>(null) }
    val phoneInstallState = if (app.hasTarget("phone")) rememberInstallState(app, "phone", installCheckTick) else MainActivity.InstallState.UNKNOWN
    val glassesInstallState = rememberGlassesInstallState(app, glassesInstallStates)
    val configuration = LocalConfiguration.current
    val sheetMaxHeight = (configuration.screenHeightDp.dp - 28.dp).coerceAtMost(720.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(onClick = onDismiss),
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxHeight)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
                        .padding(14.dp)
                        .animateContentSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BrewDim),
                    )
                    ScreenshotPager(
                        app = app,
                        mediaLoader = mediaLoader,
                        onScreenshotClick = { expandedScreenshot = it },
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app, iconLoader, mediaLoader, Modifier.size(50.dp))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                app.name,
                                color = BrewTextBright,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${app.category} / ${app.author} / v${app.version}",
                                color = BrewGreen,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TargetTags(app, Modifier.padding(top = 8.dp))
                    SourceLine(app, Modifier.padding(top = 8.dp))
                    Text(
                        app.description,
                        color = BrewText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    val phoneProgress = progress["${app.id}:phone"]
                    val glassesProgress = progress["${app.id}:glasses"]
                    if (phoneProgress != null || glassesProgress != null) {
                        ProgressLine(phoneProgress ?: glassesProgress ?: 0, Modifier.padding(top = 10.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (app.hasTarget("phone")) {
                            BrewButton(
                                label = installButtonLabel("phone", phoneInstallState),
                                primary = false,
                                enabled = !busy && phoneInstallState != MainActivity.InstallState.INSTALLED,
                                onClick = { onInstall(app, "phone") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (app.hasTarget("glasses")) {
                            BrewButton(
                                label = installButtonLabel("glasses", glassesInstallState),
                                primary = true,
                                enabled = !busy && glassesInstallState != MainActivity.InstallState.INSTALLED,
                                onClick = { onInstall(app, "glasses") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        expandedScreenshot?.let { screenshot ->
            ScreenshotViewerDialog(
                app = app,
                screenshot = screenshot,
                mediaLoader = mediaLoader,
                onDismiss = { expandedScreenshot = null },
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
            .clip(RoundedCornerShape(10.dp))
            .background(BrewBg.copy(alpha = 0.46f))
            .border(1.dp, BrewBorder, RoundedCornerShape(10.dp))
            .clickable(enabled = app.sourceUrl != null) {
                app.sourceUrl?.let { sourceUrl ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl)))
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Author", color = BrewMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(
            app.author,
            color = BrewTextBright,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.42f),
        )
        app.sourceUrl?.let { sourceUrl ->
            Spacer(Modifier.width(8.dp))
            Text(
                sourceUrl.removePrefix("https://"),
                color = BrewGreen,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.58f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScreenshotPager(
    app: BrewApp,
    mediaLoader: MediaLoader,
    onScreenshotClick: (BrewScreenshot) -> Unit,
) {
    if (app.screenshotCount == 0) return
    val pagerState = rememberPagerState(pageCount = { app.screenshotCount })
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.42f))
                .border(1.dp, BrewBorderHi, RoundedCornerShape(14.dp)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val screenshotRef = app.screenshotAt(page)
                val screenshot = rememberScreenshotPainter(screenshotRef.assetName, screenshotRef.url, mediaLoader)
                if (screenshot != null) {
                    Image(
                        painter = screenshot,
                        contentDescription = "${app.name} screenshot ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onScreenshotClick(screenshotRef) }
                            .padding(8.dp),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading preview", color = BrewMuted, fontSize = 13.sp)
                    }
                }
            }
            if (app.screenshotCount > 1) {
                Text(
                    "${pagerState.currentPage + 1}/${app.screenshotCount}",
                    color = BrewTextBright,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(BrewBg.copy(alpha = 0.78f))
                        .border(1.dp, BrewBorder, RoundedCornerShape(9.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (app.screenshotCount > 1) {
            Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(app.screenshotCount) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (index == pagerState.currentPage) BrewGreen else BrewBorderHi),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenshotViewerDialog(
    app: BrewApp,
    screenshot: BrewScreenshot,
    mediaLoader: MediaLoader,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        var scale by remember(screenshot.assetName, screenshot.url) { mutableStateOf(1f) }
        var offset by remember(screenshot.assetName, screenshot.url) { mutableStateOf(Offset.Zero) }
        val painter = rememberScreenshotPainter(screenshot.assetName, screenshot.url, mediaLoader)

        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = "${app.name} screenshot fullscreen",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                        .pointerInput(screenshot.assetName, screenshot.url) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = nextScale
                                offset = if (nextScale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            } else {
                Text("Loading preview", color = BrewTextBright, fontSize = 14.sp)
            }
            Text(
                "Back to close",
                color = BrewMuted,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
            )
        }
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
    val logScrollState = rememberScrollState()
    LaunchedEffect(expanded, statusLines.size) {
        if (expanded) logScrollState.animateScrollTo(logScrollState.maxValue)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrewPanel.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, BrewBorderHi),
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 26.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(">_", color = BrewText, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("  System Log", color = BrewText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(BrewGreen),
                )
                Spacer(Modifier.width(16.dp))
                Text(if (expanded) "v" else "^", color = BrewMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (busy) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = BrewAmber,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("BUSY", color = BrewAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 84.dp, max = 188.dp)
                        .padding(start = 26.dp, end = 18.dp, bottom = 10.dp)
                        .verticalScroll(logScrollState),
                ) {
                    Text(
                        text = statusLines.joinToString("\n"),
                        color = BrewMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            } else {
                Text(
                    text = statusLines.takeLast(2).joinToString("\n"),
                    color = BrewMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 26.dp, end = 18.dp, bottom = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = BrewTextBright, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() },
            ) {
                Text(action, color = BrewGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Outlined.KeyboardArrowRight, null, tint = BrewGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun TargetTags(app: BrewApp, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (app.hasTarget("phone")) TargetTag("PHONE", "phone")
        if (app.hasTarget("glasses")) TargetTag("GLASSES", "glasses")
        if (app.phoneRequired && !app.hasTarget("phone")) TargetTag("PHONE REQ")
    }
}

@Composable
private fun TargetTag(label: String, icon: String? = null) {
    val tagWidth = when (label) {
        "PHONE" -> 92.dp
        "GLASSES" -> 104.dp
        "PHONE REQ" -> 112.dp
        else -> 92.dp
    }
    Row(
        modifier = Modifier
            .width(tagWidth)
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BrewGreen.copy(alpha = 0.10f))
            .border(1.dp, BrewGreenDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon == "phone") Icon(Icons.Outlined.PhoneAndroid, null, tint = BrewGreen, modifier = Modifier.size(12.dp))
        if (icon == "glasses") Text("∞", color = BrewGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (icon != null) Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = BrewGreen,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Badge(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        color = BrewGreen,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Transparent)
            .border(1.dp, BrewGreenDim, RoundedCornerShape(6.dp))
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
            contentColor = if (primary) Color.Black else BrewTextBright,
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

private enum class SectionUi { PHONE, GLASSES }

@Composable
private fun RokidBrewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BrewBg,
            surface = BrewPanel,
            primary = BrewGreen,
            onPrimary = BrewBg,
            onSurface = BrewText,
        ),
        typography = MaterialTheme.typography.copy(
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = BrewFont),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = BrewFont),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = BrewFont, fontWeight = FontWeight.Bold),
        ),
        content = { Surface(color = BrewBg, content = content) },
    )
}

private val BrewFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val BrewBg = Color(0xFF061012)
private val BrewPanel = Color(0xFF162121)
private val BrewPanelAlt = Color(0xFF182625)
private val BrewPanelHi = Color(0xFF233433)
private val BrewTextBright = Color(0xFFE9EDEE)
private val BrewText = Color(0xFFC3CAC8)
private val BrewMuted = Color(0xFF858E8B)
private val BrewDim = Color(0xFF4E5C59)
private val BrewGreen = Color(0xFF79D96C)
private val BrewGreenDim = Color(0xFF4A9C44)
private val BrewBorder = Color(0xFF344240)
private val BrewBorderHi = Color(0xFF51615E)
private val BrewAmber = Color(0xFFFFB347)
