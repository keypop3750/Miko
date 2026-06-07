package yokai.presentation.globalsearch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import yokai.presentation.globalsearch.components.SourceResultCard

/**
 * Main screen for global search across all enabled sources.
 * 
 * Displays a search bar at the top and a scrollable list of source results below.
 * Each source shows its search results in a horizontal row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    viewModel: GlobalSearchViewModel,
    initialQuery: String,
    onBackClick: () -> Unit,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    onSourceClick: (CatalogueSource, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Track which sources have had their animation played - persists across recompositions
    // Using rememberSaveable with a Set stored as List (Saveable requires primitives)
    val animatedSourceKeys = remember { mutableSetOf<String>() }
    
    // Handle back press
    BackHandler { onBackClick() }
    
    // Initial search when screen opens
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            viewModel.search(initialQuery)
        }
    }
    
    // Get status bar padding
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Search bar with status bar padding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarPadding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.surface)
        ) {
            GlobalSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { 
                    if (searchQuery.isNotBlank()) {
                        // Clear animation tracking and scroll to top for new search
                        animatedSourceKeys.clear()
                        coroutineScope.launch {
                            listState.scrollToItem(0)
                        }
                        viewModel.search(searchQuery)
                        focusManager.clearFocus()
                    }
                },
                onClear = { searchQuery = "" },
                onBackClick = onBackClick,
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                // Initial state - no search performed yet
                uiState.searchQuery.isEmpty() && uiState.sourceResults.isEmpty() -> {
                    EmptySearchState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                // Loading state - search in progress with no results yet
                uiState.isLoading && uiState.sourceResults.all { it.isLoading } -> {
                    LoadingState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                // Results state
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.sourceResults,
                            key = { it.source.id }
                        ) { sourceResult ->
                            SourceResultCard(
                                sourceResult = sourceResult,
                                animatedSourceKeys = animatedSourceKeys,
                                onMangaClick = onMangaClick,
                                onMangaLongClick = onMangaLongClick,
                                onSourceClick = { 
                                    onSourceClick(sourceResult.source, uiState.searchQuery) 
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search bar for global search with back icon inside.
 * Matches YokaiSearchBar styling exactly for visual consistency.
 */
@Composable
private fun GlobalSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBackClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val cardBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = MaterialTheme.colorScheme.onSurface
    val contentColorAlpha = contentColor.copy(alpha = 0.78f)
    
    // Request focus after the composable is laid out
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
            // Focus request may fail if not attached yet, ignore
        }
    }
    
    // Match YokaiSearchBar: horizontal padding 10dp, vertical 4dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(cardBackgroundColor)
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back icon inside the search bar
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = contentColorAlpha,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = contentColor,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search all sources...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColorAlpha.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Normal,
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = contentColorAlpha,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Enter a search query",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = "Search across all enabled sources",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = "Searching...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
