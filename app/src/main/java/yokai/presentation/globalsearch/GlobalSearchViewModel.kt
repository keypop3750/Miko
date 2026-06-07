package yokai.presentation.globalsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.create
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.core.mode.ModeManager
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.InsertManga
import yokai.domain.manga.interactor.UpdateManga
import java.util.Locale

/**
 * ViewModel for the Compose-based Global Search screen.
 * 
 * Handles parallel searching across multiple sources with:
 * - Semaphore-based concurrency (max 5 parallel searches)
 * - Real-time result updates as each source completes
 * - Mode-aware filtering (manga vs novel)
 * - Result sorting by pinned status, load time, and name
 */
class GlobalSearchViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
) : ViewModel() {
    
    private val getManga: GetManga by injectLazy()
    private val insertManga: InsertManga by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    
    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()
    
    // Semaphore to limit concurrent source searches
    private val semaphore = Semaphore(5)
    
    // Track active search jobs for cancellation
    private val searchJobs = mutableMapOf<Long, Job>()
    
    // Track load times for sorting
    private val loadTimes = mutableMapOf<Long, Long>()
    
    /**
     * Initiates a search across all enabled sources.
     * Cancels any existing searches and starts fresh.
     */
    fun search(query: String, extensionFilter: String? = null) {
        if (query.isBlank()) return
        
        Logger.d { "[GLOBAL_SEARCH] Starting search for: $query" }
        
        // Cancel existing searches
        searchJobs.values.forEach { it.cancel() }
        searchJobs.clear()
        loadTimes.clear()
        
        // Update state with new query
        _uiState.update { 
            it.copy(
                searchQuery = query, 
                isLoading = true,
                extensionFilter = extensionFilter,
                currentMode = ModeManager.currentMode.value
            ) 
        }
        
        // Get sources to search
        val sources = getEnabledSources(extensionFilter)
        val pinnedIds = preferences.pinnedCatalogues().get()
        
        Logger.d { "[GLOBAL_SEARCH] Searching ${sources.size} sources" }
        
        // Initialize results with loading state
        val initialResults = sources.map { source ->
            SourceSearchResult(
                source = source,
                isLoading = true,
                isPinned = source.id.toString() in pinnedIds
            )
        }
        _uiState.update { it.copy(sourceResults = initialResults) }
        
        // Launch parallel searches with semaphore on IO dispatcher
        sources.forEach { source ->
            val job = viewModelScope.launch(Dispatchers.IO) {
                semaphore.withPermit {
                    searchSource(source, query)
                }
            }
            searchJobs[source.id] = job
        }
    }
    
    /**
     * Search a single source and update results.
     * 
     * Performance note: We do NOT call getMangaDetails here - the SManga from
     * getSearchManga already contains thumbnail_url which is copied via copyFrom.
     * Lazy detail fetching happens only when needed (e.g., for null thumbnails).
     */
    private suspend fun searchSource(source: CatalogueSource, query: String) {
        try {
            Logger.d { "[GLOBAL_SEARCH] Searching source: ${source.name}" }
            
            // Perform search
            val mangasPage = try {
                source.getSearchManga(1, query, source.getFilterList())
            } catch (e: Exception) {
                Logger.e(e) { "[GLOBAL_SEARCH] Error searching ${source.name}" }
                MangasPage(emptyList(), false)
            }
            
            // Convert to local manga (limit to 10)
            // Note: SManga from search already has thumbnail_url, copyFrom transfers it
            val mangas = mangasPage.mangas
                .take(10)
                .mapNotNull { networkToLocalManga(it, source.id) }
            
            // Record load time
            val loadTime = System.currentTimeMillis()
            loadTimes[source.id] = loadTime
            
            // Update results for this source
            updateSourceResult(source.id, mangas, loadTime)
            
            Logger.d { "[GLOBAL_SEARCH] Found ${mangas.size} results from ${source.name}" }
            
        } catch (e: Exception) {
            Logger.e(e) { "[GLOBAL_SEARCH] Error in searchSource for ${source.name}" }
            updateSourceResult(source.id, emptyList(), null)
        }
    }
    
    /**
     * Update results for a specific source and re-sort.
     */
    private fun updateSourceResult(sourceId: Long, mangas: List<Manga>, loadTime: Long?) {
        _uiState.update { state ->
            val updatedResults = state.sourceResults.map { result ->
                if (result.source.id == sourceId) {
                    result.copy(
                        isLoading = false,
                        results = mangas.map { MangaSearchItem(it) },
                        loadTime = loadTime
                    )
                } else {
                    result
                }
            }.sortedWith(
                compareBy(
                    // Sources with results first
                    { it.results.isNullOrEmpty() },
                    // Pinned sources first
                    { !it.isPinned },
                    // Earlier load times first
                    { it.loadTime ?: Long.MAX_VALUE },
                    // Alphabetical by name
                    { "${it.source.name.lowercase(Locale.getDefault())} (${it.source.lang})" }
                )
            )
            
            val stillSearching = updatedResults.any { it.isLoading }
            
            state.copy(
                sourceResults = updatedResults,
                isLoading = stillSearching
            )
        }
    }
    
    /**
     * Re-search with current query after mode change.
     */
    fun researchWithNewMode() {
        val currentQuery = _uiState.value.searchQuery
        val extensionFilter = _uiState.value.extensionFilter
        if (currentQuery.isNotBlank()) {
            search(currentQuery, extensionFilter)
        }
    }
    
    /**
     * Update a manga's favorite status in the results.
     */
    fun updateMangaFavoriteStatus(mangaId: Long, isFavorite: Boolean) {
        _uiState.update { state ->
            val updatedResults = state.sourceResults.map { sourceResult ->
                val updatedMangaItems = sourceResult.results?.map { item ->
                    if (item.manga.id == mangaId) {
                        item.copy(manga = item.manga.apply { favorite = isFavorite })
                    } else {
                        item
                    }
                }
                sourceResult.copy(results = updatedMangaItems)
            }
            state.copy(sourceResults = updatedResults)
        }
    }
    
    /**
     * Get enabled sources filtered by mode and user preferences.
     */
    private fun getEnabledSources(extensionFilter: String?): List<CatalogueSource> {
        val languages = preferences.enabledLanguages().get()
        val hiddenCatalogues = preferences.hiddenSources().get()
        val pinnedCatalogues = preferences.pinnedCatalogues().get()
        val isNovelMode = ModeManager.isNovelMode()
        
        // If extension filter is set, only search that extension
        if (!extensionFilter.isNullOrEmpty()) {
            val filterSources = extensionManager.installedExtensionsFlow.value
                .filter { it.pkgName == extensionFilter }
                .flatMap { it.sources }
                .filter { it.lang in languages }
                .filterIsInstance<CatalogueSource>()
            
            if (filterSources.isNotEmpty()) {
                return filterSources
            }
        }
        
        // Get all enabled sources filtered by mode
        val sources = sourceManager.getCatalogueSources()
            .filter { it.lang in languages }
            .filterNot { it.id.toString() in hiddenCatalogues }
            .filter { source -> source.isNovelSource() == isNovelMode }
            .sortedBy { "(${it.lang}) ${it.name}" }
        
        // Optionally filter to only pinned sources
        return if (preferences.onlySearchPinned().get()) {
            sources.filter { it.id.toString() in pinnedCatalogues }
        } else {
            sources.sortedBy { it.id.toString() !in pinnedCatalogues }
        }
    }
    
    /**
     * Convert network manga to local database manga.
     */
    private suspend fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga? {
        var localManga = getManga.awaitByUrlAndSource(sManga.url, sourceId)
        if (localManga == null) {
            val newManga = try {
                Manga.create(sManga.url, sManga.title, sourceId)
            } catch (_: UninitializedPropertyAccessException) {
                return null
            }
            newManga.copyFrom(sManga)
            newManga.id = insertManga.await(newManga)
            localManga = newManga
        } else if (!localManga.favorite) {
            // Update title for non-favorite manga
            localManga.title = try {
                sManga.title
            } catch (_: UninitializedPropertyAccessException) {
                return localManga
            }
        }
        return localManga
    }
    
    override fun onCleared() {
        super.onCleared()
        searchJobs.values.forEach { it.cancel() }
        searchJobs.clear()
    }
}
