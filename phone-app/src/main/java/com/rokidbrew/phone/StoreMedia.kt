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
import androidx.compose.ui.graphics.ImageBitmap
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
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val PAINTER_ICON_MAX_DIMENSION_PX = 256
private const val PAINTER_MEDIA_MAX_DIMENSION_PX = 1080
private const val PAINTER_BITMAP_CACHE_ENTRIES = 96

private object PainterBitmapCache {
    private val lock = Any()
    private val entries = object : LinkedHashMap<String, ImageBitmap>(PAINTER_BITMAP_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > PAINTER_BITMAP_CACHE_ENTRIES
        }
    }

    fun get(key: String): ImageBitmap? = synchronized(lock) { entries[key] }

    fun put(key: String, bitmap: ImageBitmap): ImageBitmap = synchronized(lock) {
        entries[key] = bitmap
        bitmap
    }
}

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
    val sourceKey = if (preferScreenshot) {
        "${app.screenshotAsset.orEmpty()}::${app.screenshotUrl.orEmpty()}"
    } else {
        app.iconUrl.orEmpty()
    }
    val cacheKey = remember(app.id, sourceKey, preferScreenshot) {
        "${if (preferScreenshot) "media" else "icon"}:${app.id}:$sourceKey"
    }
    val initialBitmap = remember(cacheKey) { PainterBitmapCache.get(cacheKey) }
    val imageBitmap by produceState<ImageBitmap?>(initialValue = initialBitmap, cacheKey) {
        if (initialBitmap != null) return@produceState
        value = withContext(Dispatchers.IO) {
            PainterBitmapCache.get(cacheKey)?.let { return@withContext it }
            val drawable = if (preferScreenshot) {
                mediaLoader.load(app.screenshotAsset, app.screenshotUrl) ?: iconLoader.load(app.id, app.iconUrl)
            } else {
                iconLoader.load(app.id, app.iconUrl)
            }
            drawable
                ?.toImageBitmap(maxDimensionPx = if (preferScreenshot) PAINTER_MEDIA_MAX_DIMENSION_PX else PAINTER_ICON_MAX_DIMENSION_PX)
                ?.let { PainterBitmapCache.put(cacheKey, it) }
        }
    }
    return remember(imageBitmap) { imageBitmap?.let(::BitmapPainter) }
}

@Composable
internal fun rememberScreenshotPainter(assetName: String?, url: String?, mediaLoader: MediaLoader): Painter? {
    val cacheKey = remember(assetName, url) { "screenshot:${assetName.orEmpty()}:${url.orEmpty()}" }
    val initialBitmap = remember(cacheKey) { PainterBitmapCache.get(cacheKey) }
    val imageBitmap by produceState<ImageBitmap?>(initialValue = initialBitmap, cacheKey) {
        if (initialBitmap != null) return@produceState
        value = withContext(Dispatchers.IO) {
            PainterBitmapCache.get(cacheKey)?.let { return@withContext it }
            mediaLoader.load(assetName, url)
                ?.toImageBitmap(PAINTER_MEDIA_MAX_DIMENSION_PX)
                ?.let { PainterBitmapCache.put(cacheKey, it) }
        }
    }
    return remember(imageBitmap) { imageBitmap?.let(::BitmapPainter) }
}

@Composable
internal fun rememberDrawablePainter(drawable: Drawable?): Painter? {
    val cacheKey = remember(drawable) { drawable?.let { "drawable:${System.identityHashCode(it)}" } }
    val initialBitmap = remember(cacheKey) { cacheKey?.let(PainterBitmapCache::get) }
    val imageBitmap by produceState<ImageBitmap?>(initialValue = initialBitmap, cacheKey) {
        if (cacheKey == null || initialBitmap != null) return@produceState
        value = withContext(Dispatchers.IO) {
            PainterBitmapCache.get(cacheKey)?.let { return@withContext it }
            drawable?.toImageBitmap(PAINTER_ICON_MAX_DIMENSION_PX)?.let { PainterBitmapCache.put(cacheKey, it) }
        }
    }
    return remember(imageBitmap) { imageBitmap?.let(::BitmapPainter) }
}

private fun Drawable.toImageBitmap(maxDimensionPx: Int): ImageBitmap {
    val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: maxDimensionPx
    val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: maxDimensionPx
    val scale = minOf(1f, maxDimensionPx.toFloat() / maxOf(sourceWidth, sourceHeight).toFloat())
    val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
    return toBitmap(width = width, height = height).asImageBitmap()
}
