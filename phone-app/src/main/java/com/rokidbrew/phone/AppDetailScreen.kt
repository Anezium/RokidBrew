package com.rokidbrew.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DetailSheet(
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
internal fun DetailTopBar(onDismiss: () -> Unit) {
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
internal fun DetailHeroHeader(app: BrewApp, iconLoader: IconLoader, mediaLoader: MediaLoader) {
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
internal fun DetailInstallActions(
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
