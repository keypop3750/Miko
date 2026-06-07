# Global Search Compose Migration Plan

## Overview
Migrate the View-based `GlobalSearchController` to a modern Compose-based implementation (`GlobalSearchScreen`) matching the architecture patterns used in `BrowseScreen`.

**Current Status**: ✅ COMPLETED - Compose implementation deployed
**Target**: Compose UI with ViewModel architecture
**Complexity**: High - involves parallel search across multiple sources, dynamic results, favorites management

---

## Implementation Status

### ✅ Completed Components
1. **GlobalSearchUiState.kt** - Data classes for UI state
2. **GlobalSearchViewModel.kt** - ViewModel with parallel search logic
3. **GlobalSearchScreen.kt** - Main Compose screen
4. **SourceResultCard.kt** - Source section with horizontal manga row
5. **MangaResultItem.kt** - Individual manga card with cover loading
6. **ComposeGlobalSearchController.kt** - Conductor controller hosting Compose

### ✅ Integration Complete
- BrowseController now uses `ComposeGlobalSearchController` for global search
- Search bar submit triggers global search navigation
- Mode-aware filtering active
- Favorites add/remove working

---

## Architecture Analysis

### Current View-Based Implementation

#### Components (To Be Migrated)
1. **GlobalSearchController** - Main controller handling UI
2. **GlobalSearchPresenter** - Business logic and source searching
3. **GlobalSearchAdapter** - RecyclerView adapter for source groups
4. **GlobalSearchCardAdapter** - Nested RecyclerView for manga cards within each source
5. **GlobalSearchItem** - FlexibleAdapter item for source sections
6. **GlobalSearchMangaItem** - Individual manga item wrapper
7. **GlobalSearchHolder** - ViewHolder for source sections

#### Key Features
1. **Multi-Source Search**
   - Searches across all enabled sources simultaneously
   - Semaphore-based concurrency (max 5 parallel searches)
   - Real-time result updates as each source completes

2. **Result Display**
   - Vertical list of sources
   - Horizontal scrolling manga cards within each source
   - Shows up to 10 results per source
   - Loading indicators per source
   - "No results" state per source

3. **Result Ordering**
   - Pinned sources appear first
   - Sources with results bubble to top
   - Alphabetical sorting by source name
   - Load time tracking for sorting

4. **Mode Awareness**
   - Filters sources by content mode (manga vs novel)
   - `researchWithNewMode()` for mode toggle during search
   - Mode indicator in toolbar

5. **Search Features**
   - Query submission from search bar
   - Extension URL detection (deep linking)
   - Source filtering by extension package

6. **Manga Interactions**
   - Click → Open MangaDetailsController
   - Long-press → Add/remove favorites with migration
   - Tap source title → Open BrowseSourceController with query
   - Cover image lazy loading with fallback

7. **State Persistence**
   - Bundle-based ViewHolder state saving
   - Query preservation across config changes
   - Last viewed position tracking

---

## Target Compose Architecture

### File Structure
```
yokai/presentation/globalsearch/
├── GlobalSearchScreen.kt          # Main Compose UI
├── GlobalSearchViewModel.kt       # State management
├── GlobalSearchUiState.kt         # UI state data classes
├── components/
│   ├── SourceResultCard.kt        # Source section with manga row
│   ├── MangaResultItem.kt         # Individual manga card
│   └── EmptyResultsPlaceholder.kt # No results state
```

---

## Data Layer Design

### GlobalSearchUiState.kt
```kotlin
data class GlobalSearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val searchResults: List<SourceSearchResult> = emptyList(),
    val currentMode: ContentType = ContentType.MANGA,
    val extensionFilter: String? = null
)

data class SourceSearchResult(
    val source: CatalogueSource,
    val isLoading: Boolean = true,
    val results: List<MangaSearchItem>? = null, // null = loading, empty = no results
    val isPinned: Boolean = false,
    val loadTime: Long? = null,
    val isHighlighted: Boolean = false // For migration mode
)

data class MangaSearchItem(
    val manga: Manga,
    val coverUrl: String?,
    val isInitialized: Boolean = false
)
```

### GlobalSearchViewModel.kt
```kotlin
class GlobalSearchViewModel(
    private val sourceManager: SourceManager,
    private val preferences: PreferencesHelper,
    private val getManga: GetManga,
    private val insertManga: InsertManga,
    private val updateManga: UpdateManga,
    private val coverCache: CoverCache
) : ViewModel() {
    
    private val _state = MutableStateFlow(GlobalSearchUiState())
    val state: StateFlow<GlobalSearchUiState> = _state.asStateFlow()
    
    private val semaphore = Semaphore(5) // Max 5 concurrent searches
    private val searchJobs = mutableMapOf<Long, Job>()
    
    fun search(query: String, extensionFilter: String? = null) {
        // Cancel existing searches
        searchJobs.values.forEach { it.cancel() }
        searchJobs.clear()
        
        // Update query
        _state.update { it.copy(query = query, isLoading = true) }
        
        // Get sources to search
        val sources = getEnabledSources(extensionFilter)
        val pinnedIds = preferences.pinnedCatalogues().get()
        
        // Initialize results with loading state
        val initialResults = sources.map { source ->
            SourceSearchResult(
                source = source,
                isLoading = true,
                isPinned = source.id.toString() in pinnedIds
            )
        }
        _state.update { it.copy(searchResults = initialResults) }
        
        // Launch parallel searches
        sources.forEach { source ->
            val job = viewModelScope.launch {
                semaphore.withPermit {
                    searchSource(source, query)
                }
            }
            searchJobs[source.id] = job
        }
    }
    
    private suspend fun searchSource(source: CatalogueSource, query: String) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Search source
            val results = source.getSearchManga(1, query, source.getFilterList())
                .mangas
                .take(10)
                .mapNotNull { networkToLocalManga(it, source.id) }
            
            // Fetch cover images for uninitialized manga
            results.filter { !it.initialized }
                .forEach { manga ->
                    try {
                        val details = source.getMangaDetails(manga.copy())
                        manga.copyFrom(details)
                        manga.initialized = true
                        updateManga.await(manga.toMangaUpdate())
                    } catch (e: Exception) {
                        // Keep manga with default cover
                    }
                }
            
            // Update results
            val loadTime = System.currentTimeMillis()
            updateSourceResult(source.id, results, loadTime)
            
        } catch (e: Exception) {
            // Mark as failed with empty results
            updateSourceResult(source.id, emptyList(), null)
        }
    }
    
    private fun updateSourceResult(
        sourceId: Long,
        mangas: List<Manga>,
        loadTime: Long?
    ) {
        _state.update { state ->
            val updatedResults = state.searchResults.map { result ->
                if (result.source.id == sourceId) {
                    result.copy(
                        isLoading = false,
                        results = mangas.map { MangaSearchItem(it, it.thumbnail_url, it.initialized) },
                        loadTime = loadTime
                    )
                } else {
                    result
                }
            }.sortedWith(
                compareBy(
                    { it.results.isNullOrEmpty() }, // Results first
                    { !it.isPinned }, // Pinned first
                    { it.loadTime ?: Long.MAX_VALUE }, // Faster loads first
                    { "${it.source.name} (${it.source.lang})" } // Alphabetical
                )
            )
            
            state.copy(
                searchResults = updatedResults,
                isLoading = updatedResults.any { it.isLoading }
            )
        }
    }
    
    fun toggleFavorite(manga: Manga) {
        viewModelScope.launch {
            manga.addOrRemoveToFavorites(...)
            // Update manga item in results
            updateMangaInResults(manga)
        }
    }
    
    fun researchWithNewMode() {
        val currentQuery = _state.value.query
        _state.update { it.copy(currentMode = ModeManager.currentMode.value) }
        search(currentQuery)
    }
    
    private fun getEnabledSources(filter: String?): List<CatalogueSource> {
        // Same logic as GlobalSearchPresenter.getEnabledSources()
        // + filter by extension if provided
        // + filter by current mode
    }
    
    private suspend fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga? {
        // Same logic as presenter
    }
}
```

---

## UI Component Design

### GlobalSearchScreen.kt
```kotlin
@Composable
fun GlobalSearchScreen(
    initialQuery: String? = null,
    extensionFilter: String? = null,
    onMangaClick: (Manga) -> Unit,
    onSourceClick: (CatalogueSource, String) -> Unit, // Open source with query
    onNavigateBack: () -> Unit,
    viewModel: GlobalSearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val currentMode by ModeManager.currentMode.collectAsState()
    
    LaunchedEffect(initialQuery) {
        initialQuery?.let { viewModel.search(it, extensionFilter) }
    }
    
    LaunchedEffect(currentMode) {
        if (state.query.isNotEmpty()) {
            viewModel.researchWithNewMode()
        }
    }
    
    YokaiTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search bar (same as BrowseScreen)
            YokaiSearchBar(
                title = state.query.ifEmpty { "Global Search" },
                isSearchActive = true,
                searchQuery = state.query,
                onSearchQueryChange = { viewModel.search(it, extensionFilter) },
                onSearchSubmit = { viewModel.search(it, extensionFilter) },
                onNavigationClick = onNavigateBack,
                menuItems = listOf(
                    SearchBarMenuItem(
                        icon = Icons.Default.Book,
                        contentDescription = "Toggle mode",
                        onClick = { ModeManager.toggleMode() }
                    )
                )
            )
            
            // Results list
            when {
                state.isLoading && state.searchResults.isEmpty() -> {
                    LoadingIndicator()
                }
                state.searchResults.isEmpty() -> {
                    EmptyState(message = "No sources available")
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = state.searchResults,
                            key = { it.source.id }
                        ) { sourceResult ->
                            SourceResultCard(
                                sourceResult = sourceResult,
                                onMangaClick = onMangaClick,
                                onMangaLongClick = { viewModel.toggleFavorite(it) },
                                onSourceClick = { onSourceClick(sourceResult.source, state.query) }
                            )
                        }
                    }
                }
            }
        }
    }
}
```

### components/SourceResultCard.kt
```kotlin
@Composable
fun SourceResultCard(
    sourceResult: SourceSearchResult,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    onSourceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Source header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = sourceResult.results?.isNotEmpty() == true) { 
                    onSourceClick() 
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Source name with highlight indicator
                val prefix = if (sourceResult.isHighlighted) "▶ " else ""
                Text(
                    text = "$prefix${sourceResult.source.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Language
                if (sourceResult.source.lang.isNotEmpty()) {
                    Text(
                        text = LocaleHelper.getLocalizedDisplayName(sourceResult.source.lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Arrow icon if results exist
            if (sourceResult.results?.isNotEmpty() == true) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View all",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        
        // Results or loading/empty state
        when {
            sourceResult.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            sourceResult.results.isNullOrEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            else -> {
                // Horizontal manga row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = sourceResult.results,
                        key = { it.manga.id }
                    ) { mangaItem ->
                        MangaResultItem(
                            manga = mangaItem.manga,
                            coverUrl = mangaItem.coverUrl,
                            onClick = { onMangaClick(mangaItem.manga) },
                            onLongClick = { onMangaLongClick(mangaItem.manga) }
                        )
                    }
                }
            }
        }
    }
}
```

### components/MangaResultItem.kt
```kotlin
@Composable
fun MangaResultItem(
    manga: Manga,
    coverUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(100.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Cover image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = manga.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.cover_error)
            )
            
            // Favorite badge
            if (manga.favorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = Color.Red,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }
        
        // Title
        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
```

---

## Migration Steps

### Phase 1: Foundation (Week 1)
- [ ] Create `GlobalSearchUiState.kt` with all data classes
- [ ] Create `GlobalSearchViewModel.kt` skeleton
- [ ] Set up dependency injection for ViewModel
- [ ] Create empty `GlobalSearchScreen.kt` Composable

### Phase 2: Core Search Logic (Week 2)
- [ ] Port `getEnabledSources()` logic to ViewModel
- [ ] Implement `search()` function with semaphore concurrency
- [ ] Implement `searchSource()` for individual source queries
- [ ] Add result sorting and state updates
- [ ] Test multi-source search with logging

### Phase 3: UI Components (Week 3)
- [ ] Build `MangaResultItem.kt` with cover loading
- [ ] Build `SourceResultCard.kt` with lazy row
- [ ] Build `EmptyResultsPlaceholder.kt`
- [ ] Integrate components into `GlobalSearchScreen.kt`
- [ ] Add loading states and animations

### Phase 4: Interactions (Week 4)
- [ ] Implement manga click → open details
- [ ] Implement source title click → open catalog with query
- [ ] Implement long-press favorites toggle
- [ ] Add cover image initialization logic
- [ ] Add URL intent detection for deep linking

### Phase 5: Mode Support (Week 5)
- [ ] Add mode filtering to source selection
- [ ] Implement `researchWithNewMode()` on mode toggle
- [ ] Add mode toggle button to search bar
- [ ] Test manga ↔ novel mode switching

### Phase 6: Polish & Migration (Week 6)
- [ ] Add state persistence for config changes
- [ ] Add extension filter support
- [ ] Performance optimization (LazyColumn keys, recomposition)
- [ ] Accessibility improvements
- [ ] Update `BrowseController.performGlobalSearch()` to use new screen
- [ ] Add feature flag for gradual rollout
- [ ] A/B testing with old controller

### Phase 7: Cleanup (Week 7)
- [ ] Remove old GlobalSearchController after validation
- [ ] Remove GlobalSearchPresenter
- [ ] Remove GlobalSearchAdapter/Holder/Item classes
- [ ] Remove XML layouts
- [ ] Update documentation

---

## Testing Strategy

### Unit Tests
- `GlobalSearchViewModel` state updates
- Source filtering by mode
- Result sorting logic
- Concurrency/semaphore behavior

### Integration Tests
- Multi-source search scenarios
- Mode switching during search
- Favorites toggle with migration
- Error handling (network failures)

### UI Tests
- Search flow end-to-end
- Result card rendering
- Mode toggle interaction
- Deep link handling

---

## Performance Considerations

1. **Lazy Loading**
   - Use `LazyColumn`/`LazyRow` for efficient rendering
   - Key items properly to avoid recomposition

2. **Image Loading**
   - Use Coil's caching
   - Show placeholders during load
   - Handle missing covers gracefully

3. **Search Concurrency**
   - Maintain semaphore (max 5 sources)
   - Cancel jobs on new search
   - Show incremental results

4. **State Management**
   - Minimize recompositions with `remember` and `derivedStateOf`
   - Use stable keys for `items()`
   - Optimize sort comparisons

---

## Migration Risk Mitigation

1. **Feature Flag**
   - Add `preferences.useComposeGlobalSearch()` setting
   - Keep old controller until new one is validated
   - Allow rollback if issues found

2. **Gradual Rollout**
   - Beta users first
   - Monitor crash reports and feedback
   - Full rollout after 1-2 releases

3. **Fallback Mechanism**
   - Keep old code paths until Compose version stable
   - Easy toggle in code for quick rollback

---

## Success Criteria

- [ ] All features from View-based version working
- [ ] Search performance equal or better
- [ ] Smooth mode switching
- [ ] Proper theming with MaterialTheme
- [ ] No regressions in favorite management
- [ ] Passes all integration tests
- [ ] Positive user feedback in beta

---

## Dependencies

### New Dependencies (if needed)
```gradle
implementation("io.coil-kt:coil-compose:2.5.0") // Already included
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
```

### Existing Dependencies
- Conductor (until full migration to Compose Navigation)
- Injekt for DI
- Coroutines for async search
- Existing domain/data layer

---

## Open Questions

1. **Navigation**: Continue using Conductor or migrate to Compose Navigation?
   - **Decision**: Keep Conductor initially, migrate later
   
2. **State Persistence**: Use SavedStateHandle or custom solution?
   - **Decision**: Use SavedStateHandle in ViewModel

3. **Deep Linking**: How to handle extension intents in Compose?
   - **Decision**: Keep intent handling in controller/router level

4. **Migration Mode**: Special highlighting for migration searches?
   - **Decision**: Pass `isHighlighted` flag to SourceSearchResult

---

## Timeline

**Total Estimated Time**: 7 weeks (1 engineer)
**Critical Path**: Phase 2 (Search Logic) → Phase 3 (UI) → Phase 6 (Migration)
**Parallel Work Possible**: UI components (Phase 3) can overlap with search logic testing

---

## Notes

- Maintain feature parity with View-based implementation
- Follow BrowseScreen patterns for consistency
- Use MaterialTheme colors for automatic theme switching
- Keep presenter logic reusable for potential future needs
- Document migration decisions for future reference
