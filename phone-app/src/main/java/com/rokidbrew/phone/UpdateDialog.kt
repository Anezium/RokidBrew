package com.rokidbrew.phone

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun UpdateDialog(
    version: String,
    downloading: Boolean,
    downloadPercent: Int,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
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
internal fun UpdateSheet(
    state: BrewSelfUpdateState,
    statusLines: List<String>,
    statusExpanded: Boolean,
    onToggleStatus: () -> Unit,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrewBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(BrewAmber.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(360f, 70f),
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
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 132.dp),
        ) {
            DetailTopBar(onDismiss = onDismiss)
            UpdateHero(state, Modifier.padding(top = 18.dp))
            if (state.downloading) {
                ProgressLine(state.downloadPercent, Modifier.padding(top = 16.dp))
            }
            UpdateActions(
                state = state,
                onUpdate = onUpdate,
                modifier = Modifier.padding(top = 18.dp),
            )
            UpdateInfoPanel(
                state = state,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        StatusDock(
            statusLines = statusLines,
            expanded = statusExpanded,
            busy = state.downloading,
            onToggle = onToggleStatus,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun UpdateHero(state: BrewSelfUpdateState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (state.available) BrewAmber.copy(alpha = 0.18f) else BrewPanelAlt)
                .border(
                    1.dp,
                    if (state.available) BrewAmber.copy(alpha = 0.72f) else BrewBorderHi.copy(alpha = 0.60f),
                    RoundedCornerShape(18.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.available) Icons.Outlined.SystemUpdateAlt else Icons.Outlined.CheckCircle,
                null,
                tint = if (state.available) BrewAmber else BrewGreen,
                modifier = Modifier.size(34.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(
                "RokidBrew Update",
                color = BrewTextBright,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 29.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                updateStatusText(state),
                color = if (state.available) BrewAmber else BrewMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 7.dp),
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoPill("Current ${versionBadge(state.currentVersion)}")
                if (state.latestVersion.isNotBlank()) {
                    InfoPill("Latest ${versionBadge(state.latestVersion)}")
                }
            }
        }
    }
}

@Composable
private fun UpdateActions(
    state: BrewSelfUpdateState,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StoreActionButton(
            label = when {
                state.downloading -> "Downloading"
                state.available -> "Download / Install"
                else -> "Up to date"
            },
            primary = true,
            enabled = state.available && !state.downloading && state.apkUrl.isNotBlank(),
            icon = { Icon(Icons.Outlined.Download, null, modifier = Modifier.size(20.dp)) },
            onClick = onUpdate,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
        )
        StoreActionButton(
            label = "Release",
            primary = false,
            enabled = state.releaseUrl.isNotBlank(),
            icon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(19.dp)) },
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.releaseUrl)))
                }
            },
            modifier = Modifier
                .weight(0.72f)
                .height(44.dp),
        )
    }
}

@Composable
private fun UpdateInfoPanel(state: BrewSelfUpdateState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = BrewPanel.copy(alpha = 0.76f)),
        border = BorderStroke(1.dp, BrewBorderHi.copy(alpha = 0.46f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            DetailSectionTitle("Version")
            UpdateVersionRow(
                label = "Installed",
                value = "${versionBadge(state.currentVersion)} (${state.currentVersionCode})",
                modifier = Modifier.padding(top = 11.dp),
            )
            UpdateVersionRow(
                label = "Latest",
                value = latestVersionText(state),
                modifier = Modifier.padding(top = 8.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(1.dp)
                    .background(BrewBorderHi.copy(alpha = 0.32f)),
            )
            DetailSectionTitle("Changelog", Modifier.padding(top = 16.dp))
            UpdateChangelog(state, Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun UpdateVersionRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = BrewMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = BrewTextBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UpdateChangelog(state: BrewSelfUpdateState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (state.changes.isEmpty() && state.notes.isBlank()) {
            Text(
                "Release notes will appear here after the registry imports the RokidBrew GitHub release.",
                color = BrewMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            return
        }
        state.changes.take(8).forEachIndexed { index, change ->
            DetailBulletLine(change, Modifier.padding(top = if (index == 0) 0.dp else 6.dp))
        }
        if (state.notes.isNotBlank()) {
            DetailMarkdownBody(
                state.notes,
                Modifier.padding(top = if (state.changes.isEmpty()) 0.dp else 14.dp),
                maxBlocks = 5,
            )
        }
    }
}

private fun updateStatusText(state: BrewSelfUpdateState): String = when {
    state.downloading -> "Downloading ${versionBadge(state.latestVersion)} (${state.downloadPercent}%)"
    state.available -> "Update available"
    state.latestVersion.isNotBlank() -> "You're up to date"
    else -> "Refresh the registry to check the latest build"
}

private fun latestVersionText(state: BrewSelfUpdateState): String {
    if (state.latestVersion.isBlank()) return "Not checked"
    return buildString {
        append(versionBadge(state.latestVersion))
        state.latestVersionCode?.let { append(" ($it)") }
    }
}

private fun versionBadge(version: String): String {
    val clean = version.trim()
    if (clean.isBlank()) return "unknown"
    return if (clean.startsWith("v", ignoreCase = true)) clean else "v$clean"
}
