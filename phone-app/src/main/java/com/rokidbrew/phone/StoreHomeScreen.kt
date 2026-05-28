package com.rokidbrew.phone

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun BrewPhoneApp(
    apps: List<BrewApp>,
    busy: Boolean,
    refreshing: Boolean,
    selectedHostApp: RokidHostApp,
    hostAppInstalled: Boolean,
    cxrConnection: CxrConnectionState,
    installCheckTick: Int,
    statusLines: List<String>,
    statusExpanded: Boolean,
    downloadProgress: Map<String, Int>,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    onToggleStatus: () -> Unit,
    onRefresh: () -> Unit,
    onHostAppSelected: (RokidHostApp) -> Unit,
    onAuthorize: () -> Unit,
    onInstall: (BrewApp, String) -> Unit,
) {
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<BrewApp?>(null) }
    val scrollState = rememberScrollState()

    BackHandler(enabled = selectedApp != null) {
        selectedApp = null
    }

    val visibleApps = remember(apps, categoryFilter, query) {
        apps.filter { app ->
            val categoryOk = categoryFilter == null ||
                (categoryFilter == NEW_CATEGORY && app.isNew) ||
                app.category.equals(categoryFilter, ignoreCase = true)
            val searchOk = query.isBlank() ||
                app.name.contains(query, ignoreCase = true) ||
                app.author.contains(query, ignoreCase = true) ||
                app.category.contains(query, ignoreCase = true) ||
                app.summary.contains(query, ignoreCase = true) ||
                app.description.contains(query, ignoreCase = true)
            categoryOk && searchOk
        }
    }
    val categories = remember(apps) {
        buildList {
            if (apps.any { it.isNew }) add(NEW_CATEGORY)
            addAll(apps.map { it.category }.distinct().sorted())
        }
    }
    val featuredApps = remember(apps) {
        apps.curatedHeroApps().ifEmpty { apps.take(6) }.take(8)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrewBg)
            .background(
                Brush.radialGradient(
                    colors = listOf(BrewPanelHi.copy(alpha = 0.38f), Color.Transparent),
                    center = Offset(145f, 115f),
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
                .padding(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 132.dp),
        ) {
            Header(
                refreshing = refreshing,
                onRefresh = onRefresh,
                onReset = {
                    categoryFilter = null
                    query = ""
                },
            )
            SearchBar(
                query = query,
                onQueryChange = {
                    query = it
                    categoryFilter = null
                },
            )
            ConnectionPanel(
                selectedHostApp = selectedHostApp,
                hostAppInstalled = hostAppInstalled,
                cxrConnection = cxrConnection,
                busy = busy,
                onHostAppSelected = onHostAppSelected,
                onAuthorize = onAuthorize,
            )
            if (featuredApps.isNotEmpty()) {
                FeaturedShelf(
                    apps = featuredApps,
                    iconLoader = iconLoader,
                    mediaLoader = mediaLoader,
                    onOpen = { selectedApp = it },
                    onViewAll = {
                        categoryFilter = null
                        query = ""
                    },
                )
            }
            CategoryStrip(
                categories = categories,
                selected = categoryFilter,
                onSelect = { categoryFilter = it },
            )
            AppShelf(
                apps = visibleApps,
                iconLoader = iconLoader,
                mediaLoader = mediaLoader,
                busy = busy,
                progress = downloadProgress,
                installCheckTick = installCheckTick,
                glassesInstallStates = glassesInstallStates,
                onOpen = { selectedApp = it },
                onInstall = onInstall,
            )
        }

        StatusDock(
            statusLines = statusLines,
            expanded = statusExpanded,
            busy = busy,
            onToggle = onToggleStatus,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = selectedApp != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            DetailSheet(
                app = selectedApp,
                busy = busy,
                progress = downloadProgress,
                iconLoader = iconLoader,
                mediaLoader = mediaLoader,
                installCheckTick = installCheckTick,
                glassesInstallStates = glassesInstallStates,
                statusLines = statusLines,
                statusExpanded = statusExpanded,
                onToggleStatus = onToggleStatus,
                onDismiss = { selectedApp = null },
                onInstall = onInstall,
            )
        }
    }
}
@Composable
internal fun CategoryStrip(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    if (categories.isEmpty()) return
    val labels = remember(categories) { listOf("All") + prioritizedCategories(categories) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { label ->
            val isSelected = if (label == "All") selected == null else selected == label
            CategoryChip(label, isSelected) {
                if (label == "All") onSelect(null) else onSelect(label)
            }
        }
    }
}
internal fun prioritizedCategories(categories: List<String>): List<String> {
    val priority = listOf(NEW_CATEGORY, "AI", "Navigation", "Media", "Games", "Utilities", "Productivity", "Browser", "Mobility")
    return (priority.filter { wanted -> categories.any { it.equals(wanted, ignoreCase = true) } }
        .map { wanted -> categories.first { it.equals(wanted, ignoreCase = true) } } +
        categories.filterNot { category -> priority.any { it.equals(category, ignoreCase = true) } })
        .distinct()
}
internal fun List<BrewApp>.curatedHeroApps(): List<BrewApp> {
    val newApps = filter { it.isNew }
        .sortedWith(compareByDescending<BrewApp> { it.publishedAt.orEmpty() }.thenBy { it.name.lowercase(Locale.US) })
    val featuredApps = filter { !it.isNew && it.isFeatured() }
        .sortedWith(compareBy<BrewApp> { it.featuredRank ?: Int.MAX_VALUE }.thenBy { it.name.lowercase(Locale.US) })
    return (newApps + featuredApps).distinctBy { it.id }
}
@Composable
internal fun FeaturedShelf(
    apps: List<BrewApp>,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    onOpen: (BrewApp) -> Unit,
    onViewAll: () -> Unit,
) {
    SectionHeader(
        title = "Featured",
        action = "View all",
        onAction = onViewAll,
        modifier = Modifier.padding(top = 22.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        apps.forEach { app ->
            FeaturedAppCard(
                app = app,
                iconLoader = iconLoader,
                mediaLoader = mediaLoader,
                onClick = { onOpen(app) },
            )
        }
    }
}
@Composable
internal fun FeaturedAppCard(
    app: BrewApp,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    onClick: () -> Unit,
) {
    val painter = rememberAppPainter(app = app, iconLoader = iconLoader, mediaLoader = mediaLoader, preferScreenshot = false)
    Column(
        modifier = Modifier
            .width(102.dp)
            .height(122.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BrewPanel.copy(alpha = 0.82f))
            .border(1.dp, BrewBorderHi.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .background(BrewBg)
                .border(1.dp, BrewBorderHi.copy(alpha = 0.72f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (painter != null) {
                Image(painter, app.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                FallbackVisual(app.name, Modifier.fillMaxSize())
            }
        }
        Text(
            app.name,
            color = BrewTextBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            app.category,
            color = BrewMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
@Composable
internal fun AppShelf(
    apps: List<BrewApp>,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    busy: Boolean,
    progress: Map<String, Int>,
    installCheckTick: Int,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    onOpen: (BrewApp) -> Unit,
    onInstall: (BrewApp, String) -> Unit,
) {
    if (apps.isEmpty()) {
        EmptyState(modifier = Modifier.padding(top = 20.dp))
        return
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visibleApps = if (expanded) apps else apps.take(5)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BrewPanel.copy(alpha = 0.80f)),
        border = BorderStroke(1.dp, BrewBorderHi.copy(alpha = 0.38f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            visibleApps.forEachIndexed { index, app ->
                StoreAppRow(
                    app = app,
                    iconLoader = iconLoader,
                    mediaLoader = mediaLoader,
                    busy = busy,
                    progress = appProgress(app, progress),
                    installCheckTick = installCheckTick,
                    glassesInstallStates = glassesInstallStates,
                    onOpen = { onOpen(app) },
                    onInstall = onInstall,
                )
                if (index != visibleApps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BrewBorderHi.copy(alpha = 0.22f)),
                    )
                }
            }
            if (apps.size > 5) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (expanded) "Show less" else "Show more",
                        color = BrewGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        null,
                        tint = BrewGreen,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
@Composable
internal fun StoreAppRow(
    app: BrewApp,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    busy: Boolean,
    progress: Int?,
    installCheckTick: Int,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    onOpen: () -> Unit,
    onInstall: (BrewApp, String) -> Unit,
) {
    val target = primaryInstallTarget(app)
    val phoneInstallState = if (app.hasTarget("phone")) rememberInstallState(app, "phone", installCheckTick) else MainActivity.InstallState.UNKNOWN
    val glassesInstallState = rememberGlassesInstallState(app, glassesInstallStates)
    val targetState = when (target) {
        "phone" -> phoneInstallState
        "glasses" -> glassesInstallState
        else -> MainActivity.InstallState.UNKNOWN
    }
    val artifact = target?.let(app::artifactFor)
    val actionLabel = when (targetState) {
        MainActivity.InstallState.INSTALLED -> "Installed"
        MainActivity.InstallState.UPDATE_AVAILABLE -> "Update"
        else -> "Install"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .clickable(onClick = onOpen)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, iconLoader, mediaLoader, Modifier.size(44.dp))
        Column(
            modifier = Modifier
                .padding(start = 11.dp)
                .weight(1f),
        ) {
            Text(
                app.name,
                color = BrewTextBright,
                fontSize = 13.2f.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.summary,
                color = BrewMuted,
                fontSize = 10.5f.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            AppTargetTags(app, modifier = Modifier.padding(top = 4.dp))
        }
        Column(
            modifier = Modifier.width(58.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                "v${app.version}",
                color = BrewText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatBytes(artifact?.sizeBytes),
                color = BrewMuted,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        if (progress != null) {
            CompactProgressLine(progress, Modifier.width(88.dp))
        } else if (targetState == MainActivity.InstallState.INSTALLED) {
            Row(
                modifier = Modifier.width(88.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.CheckCircle, null, tint = BrewGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Installed", color = BrewGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            StoreActionButton(
                label = actionLabel,
                primary = false,
                enabled = !busy && target != null,
                icon = {
                    Icon(
                        if (targetState == MainActivity.InstallState.UPDATE_AVAILABLE) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.Download,
                        null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = { target?.let { onInstall(app, it) } },
                modifier = Modifier
                    .width(88.dp)
                    .height(34.dp),
            )
        }
    }
}
