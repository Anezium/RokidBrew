package com.rokidbrew.phone

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ProgressLine(progress: Int, modifier: Modifier = Modifier) {
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
internal fun CompactProgressLine(progress: Int, modifier: Modifier = Modifier) {
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

internal fun appProgress(app: BrewApp, progress: Map<String, Int>): Int? =
    progress["${app.id}:phone"] ?: progress["${app.id}:glasses"]

internal fun primaryInstallTarget(app: BrewApp): String? = when {
    app.hasTarget("phone") -> "phone"
    app.hasTarget("glasses") -> "glasses"
    else -> null
}

internal fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}

internal fun Context.installStateFor(artifact: BrewArtifact?): MainActivity.InstallState {
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

@Composable
internal fun rememberGlassesInstallState(
    app: BrewApp,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
): MainActivity.InstallState {
    val packageName = app.artifactFor("glasses")?.packageName?.takeIf { it.isNotBlank() }
    return packageName?.let { glassesInstallStates[it] } ?: MainActivity.InstallState.UNKNOWN
}

internal fun phoneInstallStateFor(
    app: BrewApp,
    phoneInstallStates: Map<String, MainActivity.InstallState>,
): MainActivity.InstallState {
    val packageName = app.artifactFor("phone")?.packageName?.takeIf { it.isNotBlank() }
    return packageName?.let { phoneInstallStates[it] } ?: MainActivity.InstallState.UNKNOWN
}

@Composable
internal fun rememberInstallState(app: BrewApp, target: String, installCheckTick: Int): MainActivity.InstallState {
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

internal fun installButtonLabel(target: String, state: MainActivity.InstallState): String {
    return when (state) {
        MainActivity.InstallState.INSTALLED -> "Installed"
        MainActivity.InstallState.UPDATE_AVAILABLE -> "Update $target"
        else -> "Install $target"
    }
}
