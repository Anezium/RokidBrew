package com.rokidbrew.phone

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AppIcon(app: BrewApp, iconLoader: IconLoader, mediaLoader: MediaLoader, modifier: Modifier = Modifier) {
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
internal fun FallbackVisual(label: String, modifier: Modifier = Modifier) {
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
internal fun rememberAppPainter(
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
internal fun rememberScreenshotPainter(assetName: String?, url: String?, mediaLoader: MediaLoader): Painter? {
    val drawable by produceState<Drawable?>(initialValue = null, assetName, url) {
        value = withContext(Dispatchers.IO) { mediaLoader.load(assetName, url) }
    }
    return rememberDrawablePainter(drawable)
}

@Composable
internal fun rememberDrawablePainter(drawable: Drawable?): Painter? {
    return remember(drawable) {
        drawable?.toBitmap()?.asImageBitmap()?.let(::BitmapPainter)
    }
}
