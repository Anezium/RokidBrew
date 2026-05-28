package com.rokidbrew.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun DetailScreenshotStrip(
    app: BrewApp,
    mediaLoader: MediaLoader,
    onScreenshotClick: (Int) -> Unit,
) {
    if (app.screenshotCount == 0) return
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            repeat(app.screenshotCount) { index ->
                val screenshotRef = app.screenshotAt(index)
                val screenshot = rememberScreenshotPainter(screenshotRef.assetName, screenshotRef.url, mediaLoader)
                Box(
                    modifier = Modifier
                        .width(92.dp)
                        .height(154.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(BrewPanelAlt)
                        .border(1.dp, BrewBorderHi.copy(alpha = 0.6f), RoundedCornerShape(9.dp))
                        .clickable { onScreenshotClick(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (screenshot != null) {
                        Image(
                            painter = screenshot,
                            contentDescription = "${app.name} screenshot ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("Loading", color = BrewMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScreenshotViewerDialog(
    app: BrewApp,
    initialIndex: Int,
    mediaLoader: MediaLoader,
    onDismiss: () -> Unit,
) {
    val startPage = initialIndex.coerceIn(0, (app.screenshotCount - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { app.screenshotCount })
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f)),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val screenshotRef = app.screenshotAt(page)
                val painter = rememberScreenshotPainter(screenshotRef.assetName, screenshotRef.url, mediaLoader)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (painter != null) {
                        Image(
                            painter = painter,
                            contentDescription = "${app.name} screenshot ${page + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("Loading preview", color = BrewTextBright, fontSize = 14.sp)
                    }
                }
            }
            Text(
                "${pagerState.currentPage + 1}/${app.screenshotCount}",
                color = BrewTextBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 14.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(BrewBg.copy(alpha = 0.76f))
                    .border(1.dp, BrewBorderHi.copy(alpha = 0.58f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
            Icon(
                Icons.Outlined.ArrowBack,
                null,
                tint = BrewTextBright,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 9.dp, start = 10.dp)
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrewBg.copy(alpha = 0.76f))
                    .clickable(onClick = onDismiss)
                    .padding(7.dp),
            )
        }
    }
}
