package com.rokidbrew.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun InfoPill(label: String, color: Color = BrewTextBright, leading: String? = null) {
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(BrewPanel.copy(alpha = 0.78f))
            .border(1.dp, BrewBorderHi.copy(alpha = 0.62f), RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            Text(leading, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = BrewFont)
    }
}

@Composable
internal fun TargetTags(app: BrewApp, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(top = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (app.isNew) TargetTag("NEW", color = BrewAmber)
        if (app.hasTarget("phone")) TargetTag("PHONE", "phone")
        if (app.hasTarget("glasses")) TargetTag("GLASSES", "glasses")
        if (app.phoneRequired && !app.hasTarget("phone")) TargetTag("PHONE REQ")
    }
}

@Composable
internal fun TargetTag(label: String, icon: String? = null, color: Color = BrewGreen) {
    val tagWidth = when (label) {
        "NEW" -> 68.dp
        "PHONE" -> 78.dp
        "GLASSES" -> 90.dp
        "PHONE REQ" -> 102.dp
        else -> 82.dp
    }
    Row(
        modifier = Modifier
            .widthIn(min = tagWidth)
            .height(27.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon == "phone") Icon(Icons.Outlined.PhoneAndroid, null, tint = color, modifier = Modifier.size(11.dp))
        if (icon == "glasses") Icon(Icons.Outlined.Visibility, null, tint = color, modifier = Modifier.size(12.dp))
        if (icon != null) Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = color,
            fontSize = 8.4f.sp,
            lineHeight = 8.4f.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}
