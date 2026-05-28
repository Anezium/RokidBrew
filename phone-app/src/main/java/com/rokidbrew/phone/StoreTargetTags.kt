package com.rokidbrew.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AppTargetTags(app: BrewApp, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (app.hasTarget("phone")) MiniTargetTag("PHONE", minWidth = 48.dp)
        if (app.hasTarget("glasses")) MiniTargetTag("GLASSES", minWidth = 78.dp)
    }
}

@Composable
internal fun MiniTargetTag(label: String, minWidth: androidx.compose.ui.unit.Dp, color: Color = BrewGreen) {
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
