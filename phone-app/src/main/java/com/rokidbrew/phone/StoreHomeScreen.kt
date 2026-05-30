package com.rokidbrew.phone

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

private const val COLLAPSED_APP_COUNT = 5

@Composable
internal fun BrewPhoneApp(
    apps: List<BrewApp>,
    busy: Boolean,
    refreshing: Boolean,
    selectedHostApp: RokidHostApp,
    hostAppInstalled: Boolean,
    cxrConnection: CxrConnectionState,
    statusLines: List<String>,
    statusExpanded: Boolean,
    downloadProgress: Map<String, Int>,
    phoneInstallStates: Map<String, MainActivity.InstallState>,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    onToggleStatus: () -> Unit,
    onRefresh: () -> Unit,
    onHostAppSelected: (RokidHostApp) -> Unit,
    onAuthorize: () -> Unit,
    onInstall: (BrewApp, String) -> Unit,
    onCheckGlassesInstall: (BrewApp) -> Unit,
    onUninstall: (BrewApp, String) -> Unit,
    selfUpdateState: BrewSelfUpdateState,
    onSelfUpdate: () -> Unit,
) {
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var appListExpanded by rememberSaveable { mutableStateOf(false) }
    var showingFeaturedList by rememberSaveable { mutableStateOf(false) }
    var updateSheetVisible by rememberSaveable { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<BrewApp?>(null) }
    val listState = rememberLazyListState()

    BackHandler(enabled = updateSheetVisible || selectedApp != null || showingFeaturedList) {
        if (updateSheetVisible) {
            updateSheetVisible = false
        } else if (selectedApp != null) {
            selectedApp = null
        } else {
            showingFeaturedList = false
        }
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
        apps.curatedHeroApps().ifEmpty { apps.take(6) }
    }

    LaunchedEffect(showingFeaturedList) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(selectedApp?.id) {
        selectedApp?.takeIf { it.hasTarget("glasses") }?.let(onCheckGlassesInstall)
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 132.dp),
        ) {
            if (showingFeaturedList) {
                item(key = "featured-page-header") {
                    FeaturedListTopBar(
                        count = featuredApps.size,
                        onBack = { showingFeaturedList = false },
                    )
                }
                appListItems(
                    apps = featuredApps,
                    expanded = true,
                    showToggle = false,
                    iconLoader = iconLoader,
                    mediaLoader = mediaLoader,
                    busy = busy,
                    progress = downloadProgress,
                    phoneInstallStates = phoneInstallStates,
                    glassesInstallStates = glassesInstallStates,
                    onExpandedChange = {},
                    onOpen = { selectedApp = it },
                    onInstall = onInstall,
                    topPadding = 14,
                )
            } else {
                item(key = "header") {
                    Header(
                        refreshing = refreshing,
                        searchActive = searchVisible || query.isNotBlank(),
                        onSearchToggle = {
                            val nextVisible = !searchVisible
                            searchVisible = nextVisible
                            if (!nextVisible) {
                                query = ""
                                appListExpanded = false
                            }
                        },
                        updateAvailable = selfUpdateState.available,
                        onUpdateOpen = { updateSheetVisible = true },
                        onRefresh = onRefresh,
                        onReset = {
                            categoryFilter = null
                            query = ""
                            searchVisible = false
                            appListExpanded = false
                        },
                    )
                }
                item(key = "search") {
                    AnimatedVisibility(
                        visible = searchVisible || query.isNotBlank(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        SearchBar(
                            query = query,
                            onQueryChange = {
                                query = it
                                categoryFilter = null
                                appListExpanded = false
                            },
                        )
                    }
                }
                item(key = "connection") {
                    ConnectionPanel(
                        selectedHostApp = selectedHostApp,
                        hostAppInstalled = hostAppInstalled,
                        cxrConnection = cxrConnection,
                        busy = busy,
                        onHostAppSelected = onHostAppSelected,
                        onAuthorize = onAuthorize,
                    )
                }
                if (featuredApps.isNotEmpty()) {
                    item(key = "featured") {
                        FeaturedShelf(
                            apps = featuredApps.take(8),
                            iconLoader = iconLoader,
                            mediaLoader = mediaLoader,
                            onOpen = { selectedApp = it },
                            onViewAll = {
                                showingFeaturedList = true
                                categoryFilter = null
                                query = ""
                                searchVisible = false
                                appListExpanded = false
                            },
                        )
                    }
                }
                item(key = "categories") {
                    CategoryStrip(
                        categories = categories,
                        selected = categoryFilter,
                        onSelect = {
                            categoryFilter = it
                            appListExpanded = false
                        },
                    )
                }
                appListItems(
                    apps = visibleApps,
                    expanded = appListExpanded,
                    showToggle = true,
                    iconLoader = iconLoader,
                    mediaLoader = mediaLoader,
                    busy = busy,
                    progress = downloadProgress,
                    phoneInstallStates = phoneInstallStates,
                    glassesInstallStates = glassesInstallStates,
                    onExpandedChange = { appListExpanded = it },
                    onOpen = { selectedApp = it },
                    onInstall = onInstall,
                    topPadding = 14,
                )
            }
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
                phoneInstallStates = phoneInstallStates,
                glassesInstallStates = glassesInstallStates,
                statusLines = statusLines,
                statusExpanded = statusExpanded,
                onToggleStatus = onToggleStatus,
                onDismiss = { selectedApp = null },
                onInstall = onInstall,
                onUninstall = onUninstall,
            )
        }

        AnimatedVisibility(
            visible = updateSheetVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            UpdateSheet(
                state = selfUpdateState,
                statusLines = statusLines,
                statusExpanded = statusExpanded,
                onToggleStatus = onToggleStatus,
                onDismiss = { updateSheetVisible = false },
                onUpdate = onSelfUpdate,
            )
        }

    }
}

@Composable
internal fun FeaturedListTopBar(count: Int, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                null,
                tint = BrewTextBright,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(3.dp),
            )
            Spacer(Modifier.width(14.dp))
            RokidBrewLogo(Modifier.size(34.dp))
            Spacer(Modifier.width(9.dp))
            BrandTitle(fontSize = 21)
            Spacer(Modifier.weight(1f))
            Text(
                "$count apps",
                color = BrewMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Text(
            "Featured",
            color = BrewTextBright,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp),
        )
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
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            items = apps,
            key = { _, app -> "featured-card:${app.id}" },
        ) { _, app ->
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
private fun LazyListScope.appListItems(
    apps: List<BrewApp>,
    expanded: Boolean,
    showToggle: Boolean,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    busy: Boolean,
    progress: Map<String, Int>,
    phoneInstallStates: Map<String, MainActivity.InstallState>,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    onExpandedChange: (Boolean) -> Unit,
    onOpen: (BrewApp) -> Unit,
    onInstall: (BrewApp, String) -> Unit,
    topPadding: Int,
) {
    if (apps.isEmpty()) {
        item(key = "app-list-empty") {
            EmptyState(modifier = Modifier.padding(top = topPadding.dp))
        }
        return
    }
    val canExpand = showToggle && apps.size > COLLAPSED_APP_COUNT
    val visibleApps = if (canExpand && !expanded) apps.take(COLLAPSED_APP_COUNT) else apps
    itemsIndexed(
        items = visibleApps,
        key = { _, app -> "app-row:${app.id}" },
    ) { index, app ->
        AppListRowShell(
            index = index,
            itemCount = visibleApps.size,
            hasFooter = canExpand,
            topPadding = topPadding,
        ) {
                StoreAppRow(
                    app = app,
                    iconLoader = iconLoader,
                    mediaLoader = mediaLoader,
                    busy = busy,
                    progress = appProgress(app, progress),
                    phoneInstallStates = phoneInstallStates,
                    glassesInstallStates = glassesInstallStates,
                    onOpen = { onOpen(app) },
                    onInstall = onInstall,
                )
        }
    }
    if (canExpand) {
        item(key = "app-list-toggle") {
            AppListToggleFooter(
                expanded = expanded,
                onClick = { onExpandedChange(!expanded) },
            )
        }
    }
}

@Composable
private fun AppListRowShell(
    index: Int,
    itemCount: Int,
    hasFooter: Boolean,
    topPadding: Int,
    content: @Composable () -> Unit,
) {
    val shape = appListRowShape(index, itemCount, hasFooter)
    val roundedShape = index == 0 || (index == itemCount - 1 && !hasFooter)
    val surfaceModifier = if (roundedShape) {
        Modifier
            .clip(shape)
            .background(BrewPanel.copy(alpha = 0.80f))
            .border(1.dp, BrewBorderHi.copy(alpha = 0.38f), shape)
    } else {
        Modifier.background(BrewPanel.copy(alpha = 0.80f))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (index == 0) topPadding.dp else 0.dp)
            .then(surfaceModifier)
            .padding(horizontal = 10.dp),
    ) {
        content()
        if (index != itemCount - 1 || hasFooter) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BrewBorderHi.copy(alpha = 0.22f)),
            )
        }
    }
}

@Composable
private fun AppListToggleFooter(expanded: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(BrewPanel.copy(alpha = 0.80f))
            .border(1.dp, BrewBorderHi.copy(alpha = 0.38f), shape)
            .clickable(onClick = onClick),
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

private fun appListRowShape(index: Int, itemCount: Int, hasFooter: Boolean): RoundedCornerShape {
    val top = if (index == 0) 14.dp else 0.dp
    val bottom = if (index == itemCount - 1 && !hasFooter) 14.dp else 0.dp
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomEnd = bottom,
        bottomStart = bottom,
    )
}

@Composable
internal fun StoreAppRow(
    app: BrewApp,
    iconLoader: IconLoader,
    mediaLoader: MediaLoader,
    busy: Boolean,
    progress: Int?,
    phoneInstallStates: Map<String, MainActivity.InstallState>,
    glassesInstallStates: Map<String, MainActivity.InstallState>,
    onOpen: () -> Unit,
    onInstall: (BrewApp, String) -> Unit,
) {
    val target = primaryInstallTarget(app)
    val phoneInstallState = phoneInstallStateFor(app, phoneInstallStates)
    val glassesInstallState = rememberGlassesInstallState(app, glassesInstallStates)
    val targetState = when (target) {
        "phone" -> phoneInstallState
        "glasses" -> glassesInstallState
        else -> MainActivity.InstallState.UNKNOWN
    }
    val artifact = target?.let(app::artifactFor)
    val actionLabel = when (targetState) {
        MainActivity.InstallState.INSTALLED -> "Installed"
        MainActivity.InstallState.INSTALLED_UNKNOWN_VERSION -> "Latest"
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
