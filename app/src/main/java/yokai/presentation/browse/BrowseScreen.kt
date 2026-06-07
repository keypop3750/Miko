package yokai.presentation.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import yokai.core.content.ContentType
import yokai.presentation.browse.components.LanguageHeader
import yokai.presentation.browse.components.SourceListItem
import yokai.presentation.component.SearchBarContext
import yokai.presentation.component.UnifiedSearchBar
import yokai.presentation.component.browseSearchBarTitle

/**
 * Compose-based Browse screen that displays sources grouped by language.
 * 
 * Uses MaterialTheme.colorScheme which reactively updates when ModeManager.currentMode
 * changes, enabling smooth theme transitions without view recreation.
 * 
 * Includes the UnifiedSearchBar which is shared with the Library screen,
 * eliminating the need for rebuilding when navigating between screens.
 * 
 * NOTE: All colors are applied directly without animation to ensure
 * simultaneous theme switching across all elements.
 */
@Composable
fun BrowseScreen(
    state: BrowseScreenState,
    searchQuery: String,
    currentMode: ContentType,
    isIncognito: Boolean,
    sheetProgress: Float = 0f,  // Extension sheet expansion progress (0 = collapsed, 1 = expanded)
    onSourceClick: (BrowseSourceItem) -> Unit,
    onSourceLongClick: (BrowseSourceItem) -> Unit,
    onPinClick: (BrowseSourceItem) -> Unit,
    onLatestClick: (BrowseSourceItem) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onGlobalSearch: (String) -> Unit,
    onFilterClick: () -> Unit,
    onModeToggle: () -> Unit,
    onMenuClick: () -> Unit,
    statusBarPadding: Int = 0,
    bottomPadding: Int = 0,
    modifier: Modifier = Modifier,
) {
    // Use direct colors - no animation for instant theme switching
    val backgroundColor = MaterialTheme.colorScheme.background
    
    // Calculate search bar alpha - start fading when toolbar appears (around 70% progress)
    // Fade from 70% to 100% expansion: at 70% alpha=1.0, at 100% alpha=0.0
    val searchBarAlpha = if (sheetProgress < 0.7f) {
        1f  // Fully visible until 70%
    } else {
        ((1f - sheetProgress) / 0.3f).coerceIn(0f, 1f)  // Fade from 70-100%
    }
    
    // Local search state
    var isSearchActive by remember { mutableStateOf(false) }
    var localSearchQuery by remember { mutableStateOf("") }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Status bar padding
        if (statusBarPadding > 0) {
            Box(modifier = Modifier.padding(top = statusBarPadding.dp))
        } else {
            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
        }
        
        // UnifiedSearchBar - fades out as extension sheet expands
        Box(modifier = Modifier.alpha(searchBarAlpha)) {
            UnifiedSearchBar(
                title = browseSearchBarTitle(),
                subtitle = null,
                isSearchActive = isSearchActive,
                searchQuery = localSearchQuery,
                onSearchQueryChange = { query ->
                    localSearchQuery = query
                    // Don't filter extensions while searching - user will press Enter to trigger global search
                },
                onSearchClick = { isSearchActive = true },
                onSearchClose = {
                    isSearchActive = false
                    localSearchQuery = ""
                    onSearchQueryChange("")  // Clear filter when closing search
                },
                onSearchSubmit = { query ->
                    if (query.isNotBlank()) {
                        onGlobalSearch(query)  // Trigger global search when Enter/Search pressed
                    }
                },
                context = SearchBarContext.BROWSE,
                currentMode = currentMode,
                isIncognito = isIncognito,
                isRoot = true,
                onNavigationClick = { /* Root screen - no navigation action */ },
                onFilterClick = onFilterClick,
                onModeToggle = onModeToggle,
                onMenuClick = onMenuClick,
            )
        }
        
        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                state.isLoading -> {
                    LoadingIndicator()
                }
                state.sources.isEmpty() && searchQuery.isNotEmpty() -> {
                    NoSearchResultsMessage(query = searchQuery)
                }
                state.sources.isEmpty() -> {
                    EmptySourcesMessage()
                }
                else -> {
                    SourceList(
                        sources = state.sources,
                        lastUsedSource = state.lastUsedSource,
                        onSourceClick = onSourceClick,
                        onSourceLongClick = onSourceLongClick,
                        onPinClick = onPinClick,
                        onLatestClick = onLatestClick,
                        contentPadding = PaddingValues(bottom = bottomPadding.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoSearchResultsMessage(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No results for \"$query\"",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Try a different search term",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun EmptySourcesMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No sources available",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Install extensions to add sources",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SourceList(
    sources: List<BrowseListItem>,
    lastUsedSource: BrowseSourceItem?,
    onSourceClick: (BrowseSourceItem) -> Unit,
    onSourceLongClick: (BrowseSourceItem) -> Unit,
    onPinClick: (BrowseSourceItem) -> Unit,
    onLatestClick: (BrowseSourceItem) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize()
    ) {
        // Last used source header and item
        if (lastUsedSource != null) {
            item(key = "last_used_header") {
                LanguageHeader(
                    title = "Last used",
                    isSpecialSection = true
                )
            }
            item(key = "last_used_${lastUsedSource.sourceId}") {
                SourceListItem(
                    source = lastUsedSource,
                    onClick = { onSourceClick(lastUsedSource) },
                    onLongClick = { onSourceLongClick(lastUsedSource) },
                    onPinClick = { onPinClick(lastUsedSource) },
                    onLatestClick = { onLatestClick(lastUsedSource) },
                )
            }
        }
        
        // Main source list with headers
        items(
            items = sources,
            key = { item ->
                when (item) {
                    is BrowseListItem.Header -> "header_${item.language}"
                    is BrowseListItem.Source -> "source_${item.source.sourceId}_${item.source.isPinned}"
                }
            }
        ) { item ->
            when (item) {
                is BrowseListItem.Header -> {
                    LanguageHeader(
                        title = item.displayName,
                        isSpecialSection = item.isSpecial
                    )
                }
                is BrowseListItem.Source -> {
                    SourceListItem(
                        source = item.source,
                        onClick = { onSourceClick(item.source) },
                        onLongClick = { onSourceLongClick(item.source) },
                        onPinClick = { onPinClick(item.source) },
                        onLatestClick = { onLatestClick(item.source) },
                    )
                }
            }
        }
    }
}

/**
 * State for the Browse screen.
 */
data class BrowseScreenState(
    val isLoading: Boolean = true,
    val sources: List<BrowseListItem> = emptyList(),
    val lastUsedSource: BrowseSourceItem? = null,
)

/**
 * Sealed class representing items in the browse list.
 */
sealed class BrowseListItem {
    data class Header(
        val language: String,
        val displayName: String,
        val isSpecial: Boolean = false
    ) : BrowseListItem()
    
    data class Source(val source: BrowseSourceItem) : BrowseListItem()
}

/**
 * Data class representing a source item in the browse list.
 */
data class BrowseSourceItem(
    val sourceId: Long,
    val name: String,
    val language: String,
    val supportsLatest: Boolean,
    val isPinned: Boolean,
    val iconUrl: String? = null,
    val isNovelSource: Boolean = false,
)
