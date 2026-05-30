package com.rokidbrew.phone

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ConnectionPanel(
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
internal data class ConnectionStatus(val label: String, val color: Color)
internal fun connectionStatus(
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
internal fun rememberHostAppVersion(hostApp: RokidHostApp): String? {
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
internal fun rememberHostAppIcon(hostApp: RokidHostApp): Drawable? {
    val context = LocalContext.current
    val icon by produceState<Drawable?>(initialValue = null, hostApp) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(hostApp.packageName) }.getOrNull()
        }
    }
    return icon
}
@Composable
internal fun HostAppBadge(hostApp: RokidHostApp, modifier: Modifier = Modifier) {
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
internal fun HostAppFallbackMark(hostApp: RokidHostApp, modifier: Modifier = Modifier, color: Color = BrewMuted) {
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
internal fun HostAppPicker(
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
internal fun HostAppSegment(
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
