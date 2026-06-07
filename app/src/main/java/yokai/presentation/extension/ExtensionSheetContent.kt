package yokai.presentation.extension

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import yokai.i18n.MR
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Progress-aware Compose bottom sheet content for Extensions and Migration tabs.
 * 
 * Layout structure (top to bottom):
 * - Pill/Handle at very top (fades as sheet expands)
 * - Toolbar below handle (grows in height as sheet expands)
 * - Tabs at bottom of header (always visible)
 * - Content below tabs (fades in)
 */
@Composable
fun ExtensionSheetContent(
    progressFlow: StateFlow<Float>,
    extensionViewModel: ExtensionViewModel,
    migrationViewModel: MigrationViewModel,
    onExpandSheet: () -> Unit,
    onCollapseSheet: () -> Unit,
    onNavigateToRepos: () -> Unit,
    onNavigateToLanguages: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by progressFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val density = LocalDensity.current
    
    // Get status bar height for proper top padding when expanded
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val toolbarHeight = 56.dp
    
    // Direct values (no extra animation on top of progress)
    val pillAlpha = (1f - progress) * 0.25f
    val toolbarAlpha = progress
    val contentAlpha = (progress * 10f).coerceAtMost(1f)
    
    // Colors for tabs - lerp based on progress
    val actionBarTintColor = MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    // Selected tab color: actionBarTintColor when collapsed, accent when expanded
    val selectedTabColor by animateColorAsState(
        targetValue = lerp(actionBarTintColor, accentColor, progress),
        animationSpec = tween(durationMillis = 100),
        label = "selectedTabColor"
    )
    
    // Unselected tab color: actionBarTintColor when collapsed, 60% alpha when expanded
    val unselectedTabColor by animateColorAsState(
        targetValue = lerp(actionBarTintColor, actionBarTintColor.copy(alpha = 0.6f), progress),
        animationSpec = tween(durationMillis = 100),
        label = "unselectedTabColor"
    )
    
    // Tab indicator color: transparent when collapsed, accent when expanded
    val tabIndicatorColor by animateColorAsState(
        targetValue = accentColor.copy(alpha = progress),
        animationSpec = tween(durationMillis = 100),
        label = "tabIndicatorColor"
    )
    
    val migrationState by migrationViewModel.state.collectAsState()
    val isShowingMangaList = migrationState.currentView == MigrationViewType.MangaList
    
    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Full toolbar height including status bar
    val fullToolbarHeight = statusBarHeight + toolbarHeight
    
    // Toolbar only starts appearing after 70% expansion
    // Map progress 0.7-1.0 to 0.0-1.0 for toolbar
    val toolbarProgress = ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f)
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        // Header area: Handle → Toolbar → Tabs
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor)
        ) {
            // 1. Drag handle pill at very top (draggable to expand/collapse)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp) // Compact touch target
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            // Negative drag = upward = expand, Positive = downward = collapse
                            if (dragAmount < -5) {
                                onExpandSheet()
                            } else if (dragAmount > 5) {
                                onCollapseSheet()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(4.dp)
                        .alpha(pillAlpha)
                        .clip(RoundedCornerShape(20.dp))
                        .background(actionBarTintColor)
                )
            }
            
            // 2. Toolbar (grows from 0 to full height, starts at 70% expansion)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fullToolbarHeight * toolbarProgress)
                    .alpha(toolbarProgress)
            ) {
                if (toolbarProgress > 0.01f) {
                    SheetToolbar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .height(toolbarHeight),
                        currentTab = pagerState.currentPage,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        sourceName = migrationState.selectedSourceName,
                        isShowingMangaList = isShowingMangaList,
                        onCloseClick = onCollapseSheet,
                        onSearchClick = { isSearchActive = true },
                        onSearchClose = {
                            isSearchActive = false
                            searchQuery = ""
                            extensionViewModel.updateSearchQuery("")
                        },
                        onSearchQueryChange = { query ->
                            searchQuery = query
                            extensionViewModel.updateSearchQuery(query)
                        },
                        onBackClick = { migrationViewModel.handleBack() },
                        onReposClick = onNavigateToRepos,
                        onLanguagesClick = onNavigateToLanguages,
                        onSortClick = { /* Sort handled by dropdown */ },
                        onSettingsClick = onNavigateToSettings,
                        onHelpClick = onNavigateToHelp,
                        sortOrder = migrationState.sortOrder,
                        onSortOrderChange = { order ->
                            migrationViewModel.onAction(MigrationAction.SetSortOrder(order))
                        },
                    )
                }
            }
            
            // 3. Tab row (always visible, at bottom of header)
            ElasticTabRow(
                selectedTabIndex = pagerState.currentPage,
                indicatorColor = tabIndicatorColor,
                onTabClick = { index ->
                    onExpandSheet()
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                tabs = listOf(
                    stringResource(MR.strings.extensions),
                    stringResource(MR.strings.migration),
                ),
                selectedColor = selectedTabColor,
                unselectedColor = unselectedTabColor,
                modifier = Modifier.background(surfaceColor),
            )
        }
        
        // Tab content pager with fade-in animation
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .alpha(contentAlpha),
        ) { page ->
            when (page) {
                0 -> ExtensionTabScreen(viewModel = extensionViewModel)
                1 -> MigrationTabScreen(viewModel = migrationViewModel)
            }
        }
    }
}

/**
 * Custom TabRow with elastic indicator animation.
 * Animation: Leading edge moves first, trailing edge follows with ease-out.
 * No overshoot - clean finish.
 */
@Composable
private fun ElasticTabRow(
    selectedTabIndex: Int,
    indicatorColor: Color,
    onTabClick: (Int) -> Unit,
    tabs: List<String>,
    selectedColor: Color,
    unselectedColor: Color,
    modifier: Modifier = Modifier,
) {
    // Track tab row width and previous index for direction detection
    var tabRowWidth by remember { mutableFloatStateOf(0f) }
    var previousTabIndex by remember { mutableIntStateOf(selectedTabIndex) }
    val tabWidth = if (tabs.isNotEmpty() && tabRowWidth > 0) tabRowWidth / tabs.size else 0f
    
    // Target positions for the indicator edges
    val targetLeft = selectedTabIndex * tabWidth
    val targetRight = (selectedTabIndex + 1) * tabWidth
    
    // The indicator is smaller than tab width - about 35% centered
    val indicatorWidthRatio = 0.35f
    val indicatorPadding = tabWidth * (1f - indicatorWidthRatio) / 2
    
    // Detect direction of movement
    val isMovingRight = selectedTabIndex > previousTabIndex
    
    // Update previous index after calculating direction
    LaunchedEffect(selectedTabIndex) {
        previousTabIndex = selectedTabIndex
    }
    
    // Elastic animation like Material TabLayout:
    // - Leading edge (direction of travel) moves FAST
    // - Trailing edge lags behind, then catches up with ease-out
    // When moving RIGHT: right edge leads (fast), left edge trails (slow)
    // When moving LEFT: left edge leads (fast), right edge trails (slow)
    
    val animatedRight by animateFloatAsState(
        targetValue = targetRight - indicatorPadding,
        animationSpec = tween(
            durationMillis = if (isMovingRight) 200 else 350,  // Fast when leading (moving right), slow when trailing
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "indicatorRight"
    )
    
    val animatedLeft by animateFloatAsState(
        targetValue = targetLeft + indicatorPadding,
        animationSpec = tween(
            durationMillis = if (isMovingRight) 350 else 200,  // Slow when trailing (moving right), fast when leading
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "indicatorLeft"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { tabRowWidth = it.width.toFloat() }
            .drawBehind {
                val indicatorHeight = 3.dp.toPx()
                val cornerRadius = 4.dp.toPx()
                
                if (tabRowWidth > 0) {
                    drawRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(animatedLeft, size.height - indicatorHeight),
                        size = Size((animatedRight - animatedLeft).coerceAtLeast(1f), indicatorHeight),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                    )
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEachIndexed { index, text ->
                // Simple clickable text, no nested Tab/Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onTabClick(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        color = if (index == selectedTabIndex) selectedColor else unselectedColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        fontWeight = if (index == selectedTabIndex) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetToolbar(
    currentTab: Int,
    isSearchActive: Boolean,
    searchQuery: String,
    sourceName: String?,
    isShowingMangaList: Boolean,
    onCloseClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onReposClick: () -> Unit,
    onLanguagesClick: () -> Unit,
    onSortClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit,
    sortOrder: MigrationSortOrder,
    onSortOrderChange: (MigrationSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when {
        isShowingMangaList && sourceName != null -> sourceName
        currentTab == 0 -> stringResource(MR.strings.extensions)
        else -> stringResource(MR.strings.migration)
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Navigation icon - X to close or back arrow for manga list
        if (isShowingMangaList) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        
        if (isSearchActive && currentTab == 0) {
            // Search field
            SearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClose = onSearchClose,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Title - centered style like original CenteredToolbar
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                )
            }
            
            // Action icons based on current tab
            if (currentTab == 0) {
                // Extensions tab actions
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onReposClick) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Extension repos",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onLanguagesClick) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Filter languages",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                // Migration tab actions
                MigrationSortDropdown(
                    currentOrder = sortOrder,
                    onOrderChange = onSortOrderChange,
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Help",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { focusManager.clearFocus() }
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(MR.strings.search_extensions),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                        )
                    }
                    innerTextField()
                }
            }
        )
        
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MigrationSortDropdown(
    currentOrder: MigrationSortOrder,
    onOrderChange: (MigrationSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MigrationSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(
                                selected = order == currentOrder,
                                onClick = null,
                            )
                            Text(
                                text = when (order) {
                                    MigrationSortOrder.Alphabetically -> stringResource(MR.strings.alphabetically)
                                    MigrationSortOrder.MostEntries -> stringResource(MR.strings.most_entries)
                                    MigrationSortOrder.Obsolete -> stringResource(MR.strings.obsolete)
                                },
                            )
                        }
                    },
                    onClick = {
                        onOrderChange(order)
                        expanded = false
                    },
                )
            }
        }
    }
}
