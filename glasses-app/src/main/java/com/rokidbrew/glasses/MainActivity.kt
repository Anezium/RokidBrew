package com.rokidbrew.glasses

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private enum class InstallState { UNKNOWN, NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE }

    private lateinit var apps: List<BrewApp>
    private lateinit var appNameText: TextView
    private lateinit var metaText: TextView
    private lateinit var summaryText: TextView
    private lateinit var actionText: TextView
    private lateinit var statusText: TextView
    private lateinit var gridPreview: LinearLayout
    private lateinit var downloader: ApkDownloader
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val columns = 3
    private var selectedIndex = 0
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        hideSystemUi()

        appNameText = findViewById(R.id.appNameText)
        metaText = findViewById(R.id.metaText)
        summaryText = findViewById(R.id.summaryText)
        actionText = findViewById(R.id.actionText)
        statusText = findViewById(R.id.statusText)
        gridPreview = findViewById(R.id.gridPreview)
        downloader = ApkDownloader(this)
        apps = BrewIndex.load(this).filter { it.artifactFor("glasses") != null }

        renderSelected()
        setStatus("Ready. Select installs the glasses APK directly.")
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        PackageInstallHelper.resumePending(this, ::setStatus)
        renderSelected()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveHorizontal(-1)
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveHorizontal(1)
                true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                moveHorizontal(-columns)
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveHorizontal(columns)
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                installSelected()
                true
            }

            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP) {
            installSelected()
            return true
        }
        return true
    }

    private fun moveHorizontal(delta: Int) {
        if (busy || apps.isEmpty()) return
        selectedIndex = (selectedIndex + delta).floorMod(apps.size)
        renderSelected()
    }

    private fun renderSelected() {
        if (apps.isEmpty()) {
            appNameText.text = "No apps"
            metaText.text = ""
            summaryText.text = "The embedded RokidBrew index is empty."
            actionText.text = ""
            return
        }

        val app = apps[selectedIndex]
        val installState = installState(app)
        appNameText.text = app.name
        metaText.text = "${selectedIndex + 1}/${apps.size}  ${app.type.uppercase()}  ${app.category}  v${app.version}"
        summaryText.text = app.summary
        actionText.text = when (installState) {
            InstallState.INSTALLED -> "Installed"
            InstallState.UPDATE_AVAILABLE -> "Update available"
            else -> if (app.phoneRequired) {
                "Install glasses APK  /  Phone app required"
            } else {
                "Install on glasses"
            }
        }
        renderGridPreview()
    }

    private fun renderGridPreview() {
        gridPreview.removeAllViews()
        if (apps.isEmpty()) return

        val startRow = (selectedIndex / columns).coerceAtLeast(0)
        val rowsToShow = 2
        for (rowOffset in 0 until rowsToShow) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                )
            }
            for (col in 0 until columns) {
                val index = (startRow + rowOffset) * columns + col
                val cell = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        setMargins(3, 3, 3, 3)
                    }
                    setPadding(8, 5, 8, 5)
                    textSize = 12f
                    maxLines = 2
                    text = if (index < apps.size) apps[index].name else ""
                    setTextColor(
                        when {
                            index == selectedIndex -> Color.BLACK
                            index < apps.size -> getColor(R.color.ar_text)
                            else -> getColor(R.color.ar_black)
                        },
                    )
                    setBackgroundColor(
                        when {
                            index == selectedIndex -> getColor(R.color.ar_green)
                            index < apps.size -> Color.rgb(9, 20, 15)
                            else -> Color.TRANSPARENT
                        },
                    )
                }
                row.addView(cell)
            }
            gridPreview.addView(row)
        }
    }

    private fun installSelected() {
        if (busy || apps.isEmpty()) return
        val app = apps[selectedIndex]
        val artifact = app.artifactFor("glasses") ?: return
        when (installState(app)) {
            InstallState.INSTALLED -> {
                setStatus("${app.name} is already installed.")
                return
            }
            else -> Unit
        }
        busy = true
        renderSelected()
        setStatus("Downloading ${app.name}...")

        scope.launch {
            runCatching {
                val fileName = "${app.id}-glasses-${app.version}.apk"
                val file = downloader.download(artifact.url, fileName, artifact.sha256) { progress ->
                    setStatus("Downloading ${app.name}: $progress%")
                }
                setStatus("Downloaded ${file.length() / 1024} KB. Opening installer...")
                PackageInstallHelper.requestInstall(this@MainActivity, file, ::setStatus)
            }.onFailure { error ->
                setStatus("Failed: ${error.message ?: error.javaClass.simpleName}")
            }
            busy = false
            renderSelected()
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread { statusText.text = message }
    }

    private fun hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    private fun installState(app: BrewApp): InstallState {
        val artifact = app.artifactFor("glasses") ?: return InstallState.UNKNOWN
        val packageName = artifact.packageName?.takeIf { it.isNotBlank() } ?: return InstallState.UNKNOWN
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        }.getOrNull() ?: return InstallState.NOT_INSTALLED
        val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        val registryVersion = artifact.versionCode ?: return InstallState.INSTALLED
        return if (installedVersion < registryVersion) InstallState.UPDATE_AVAILABLE else InstallState.INSTALLED
    }

    private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod
}
