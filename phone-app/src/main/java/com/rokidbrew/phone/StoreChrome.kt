package com.rokidbrew.phone

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ElectricScooter
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun Header(
    refreshing: Boolean,
    searchActive: Boolean,
    updateAvailable: Boolean,
    onSearchToggle: () -> Unit,
    onUpdateOpen: () -> Unit,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onReset)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RokidBrewLogo(Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            BrandTitle(fontSize = 24)
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSearchToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Search,
                null,
                tint = if (searchActive) BrewGreen else BrewTextBright,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onUpdateOpen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.SystemUpdateAlt,
                null,
                tint = if (updateAvailable) BrewAmber else BrewTextBright,
                modifier = Modifier.size(24.dp),
            )
            if (updateAvailable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 5.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(BrewRed)
                        .border(1.dp, BrewAmber, RoundedCornerShape(5.dp)),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
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
                modifier = Modifier.size(25.dp).graphicsLayer { rotationZ = rotation },
            )
        }
    }
}
@Composable
internal fun RokidBrewLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.mipmap.ic_launcher),
        contentDescription = "RokidBrew",
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(9.dp)),
    )
}
@Composable
internal fun BrandTitle(fontSize: Int, modifier: Modifier = Modifier) {
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
internal fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
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
internal fun CategoryChip(
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
internal fun CategoryIcon(label: String, color: Color, size: androidx.compose.ui.unit.Dp = 15.dp) {
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
internal fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
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
internal fun StoreActionButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    destructive: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = if (destructive) BrewRed else BrewGreen
    val border = accent
    val background = if (primary) accent else Color.Transparent
    val contentColor = if (primary) BrewBg else accent
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
internal fun EmptyState(modifier: Modifier = Modifier) {
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
