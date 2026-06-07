package yokai.presentation.extension

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yokai.i18n.MR

/**
 * Compose-based bottom sheet content for Extensions and Migration tabs.
 * 
 * Replaces the View-based ExtensionBottomSheet with modern Compose UI.
 * Features:
 * - TabRow with Extensions and Migration tabs
 * - HorizontalPager for swipe navigation
 * - Integrated search toolbar
 * - Menu actions for each tab
 */
@Composable
fun ExtensionBottomSheetContent(
    extensionViewModel: ExtensionViewModel = viewModel(),
    migrationViewModel: MigrationViewModel = viewModel(),
    onNavigateToExtensionDetails: (String) -> Unit = {},
    onNavigateToMigration: (Long, String, List<Long>) -> Unit = { _, _, _ -> },
    onNavigateToMangaMigration: (Long) -> Unit = {},
    onNavigateToExtensionRepos: () -> Unit = {},
    onNavigateToLanguages: () -> Unit = {},
    bottomNavPadding: Int = 0,  // Padding to account for bottom navigation
    modifier: Modifier = Modifier,
) {
    val extensionState by extensionViewModel.state.collectAsState()
    val migrationState by migrationViewModel.state.collectAsState()
    
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    
    // Handle extension events
    LaunchedEffect(Unit) {
        extensionViewModel.events.collect { event ->
            when (event) {
                is ExtensionEvent.OpenDetails -> onNavigateToExtensionDetails(event.pkgName)
                is ExtensionEvent.ShowError -> { /* Show snackbar */ }
                is ExtensionEvent.StartBatchInstall -> { /* Start installer job */ }
            }
        }
    }
    
    // Handle migration events
    LaunchedEffect(Unit) {
        migrationViewModel.events.collect { event ->
            when (event) {
                is MigrationEvent.StartMigration -> onNavigateToMigration(event.sourceId, event.sourceName, event.mangaIds)
                is MigrationEvent.MigrateSingle -> onNavigateToMangaMigration(event.mangaId)
                is MigrationEvent.ShowError -> { /* Show snackbar */ }
            }
        }
    }
    
    // Handle back press in migration manga list
    BackHandler(enabled = migrationState.currentView == MigrationViewType.MangaList) {
        migrationViewModel.handleBack()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Drag handle pill - compact height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
        
        // Toolbar
        ExtensionBottomSheetToolbar(
            currentTab = pagerState.currentPage,
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            migrationSourceName = migrationState.selectedSourceName,
            onSearchClick = { isSearchActive = true },
            onSearchClose = {
                isSearchActive = false
                searchQuery = ""
                extensionViewModel.updateSearchQuery("")
            },
            onSearchQueryChange = { query ->
                searchQuery = query
                if (pagerState.currentPage == 0) {
                    extensionViewModel.updateSearchQuery(query)
                }
            },
            onBackClick = {
                if (migrationState.currentView == MigrationViewType.MangaList) {
                    migrationViewModel.handleBack()
                }
            },
            showBackButton = migrationState.currentView == MigrationViewType.MangaList,
            onMenuClick = { showMenu = true },
        )
        
        // Menu dropdown
        Box {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                if (pagerState.currentPage == 0) {
                    // Extension menu
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.source_repos)) },
                        onClick = {
                            showMenu = false
                            onNavigateToExtensionRepos()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.filter_languages)) },
                        onClick = {
                            showMenu = false
                            onNavigateToLanguages()
                        },
                    )
                } else {
                    // Migration menu
                    MigrationSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = when (order) {
                                        MigrationSortOrder.Alphabetically -> stringResource(MR.strings.alphabetically)
                                        MigrationSortOrder.MostEntries -> stringResource(MR.strings.most_entries)
                                        MigrationSortOrder.Obsolete -> stringResource(MR.strings.obsolete)
                                    },
                                    fontWeight = if (order == migrationState.sortOrder) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                showMenu = false
                                migrationViewModel.onAction(MigrationAction.SetSortOrder(order))
                            },
                        )
                    }
                }
            }
        }
        
        // Tab row
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text(stringResource(MR.strings.extensions)) },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text(stringResource(MR.strings.migration)) },
            )
        }
        
        // Tab content pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> ExtensionTabScreen(viewModel = extensionViewModel)
                1 -> MigrationTabScreen(viewModel = migrationViewModel)
            }
        }
    }
}

@Composable
private fun ExtensionBottomSheetToolbar(
    currentTab: Int,
    isSearchActive: Boolean,
    searchQuery: String,
    migrationSourceName: String?,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    showBackButton: Boolean,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when {
        currentTab == 1 && migrationSourceName != null -> migrationSourceName
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
        // Back button (for migration manga list)
        if (showBackButton) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        
        if (isSearchActive && currentTab == 0) {
            // Search field
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(MR.strings.search_extensions)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                leadingIcon = {
                    IconButton(onClick = onSearchClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close search",
                        )
                    }
                },
            )
        } else {
            // Title
            if (!showBackButton) {
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            
            // Search button (only for extensions tab)
            if (currentTab == 0) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            
            // Menu button
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
