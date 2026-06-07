package yokai.presentation.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.cache.BrowseExtensionCache
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.iconUrl
import eu.kanade.tachiyomi.source.includeLangInName
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.novel.NovelSourceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager
import java.util.TreeMap

/**
 * ViewModel for the Compose-based Browse screen.
 * 
 * Exposes source data as StateFlow for reactive Compose observation.
 * Automatically updates when mode changes, enabling smooth theme transitions.
 */
class BrowseViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val novelExtensionManager: NovelExtensionManager = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val browseCache: BrowseExtensionCache = Injekt.get(),
) : ViewModel() {
    
    private val _state = MutableStateFlow(BrowseScreenState())
    val state: StateFlow<BrowseScreenState> = _state.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Sheet progress for fading the search bar when extension sheet expands
    private val _sheetProgress = MutableStateFlow(0f)
    val sheetProgress: StateFlow<Float> = _sheetProgress.asStateFlow()
    
    // Visibility counter - increments when controller becomes visible to trigger recomposition
    // This ensures YokaiTheme's SideEffect runs and updates status bar color
    private val _visibilityCounter = MutableStateFlow(0)
    val visibilityCounter: StateFlow<Int> = _visibilityCounter.asStateFlow()
    
    /** Notify that the controller has become visible */
    fun onControllerVisible() {
        _visibilityCounter.value++
        Logger.d { "[BROWSE_VM] Controller became visible, counter=${_visibilityCounter.value}" }
    }
    
    /** Update sheet expansion progress (0 = collapsed, 1 = expanded) */
    fun updateSheetProgress(progress: Float) {
        _sheetProgress.value = progress
    }
    
    private var lastUsedJob: Job? = null
    
    // Cache of all sources for filtering
    private var allSources: List<BrowseListItem> = emptyList()
    
    init {
        // Load initial sources
        loadSources()
        
        // Observe mode changes
        viewModelScope.launch {
            ModeManager.currentMode.collect { mode ->
                Logger.d { "[BROWSE_VM] Mode changed to $mode, reloading sources" }
                loadSources()
            }
        }
        
        // Observe catalogue sources from SourceManager
        // This ensures we only reload AFTER SourceManager has rebuilt its map
        // (fixes race condition where we'd reload before new extensions were registered)
        sourceManager.catalogueSources
            .drop(1)
            .onEach { sources ->
                Logger.d { "[BROWSE_VM] Catalogue sources changed (${sources.size} sources), invalidating cache and reloading" }
                browseCache.invalidate()
                loadSources()
            }
            .launchIn(viewModelScope)
        
        // Observe last used source changes
        observeLastUsedSource()
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterSources(query)
    }
    
    private fun filterSources(query: String) {
        val filtered = filterSourcesList(allSources, query)
        _state.update { it.copy(sources = filtered) }
    }
    
    private fun loadSources() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            
            val enabledLanguages = preferences.enabledLanguages().get()
            val hiddenSources = preferences.hiddenSources().get()
            val pinnedCatalogues = preferences.pinnedCatalogues().get()
            val currentMode = ModeManager.currentMode.value
            
            val sources = getEnabledSources(enabledLanguages, hiddenSources, currentMode)
            val browseItems = buildBrowseList(sources, pinnedCatalogues, enabledLanguages)
            val lastUsedSource = getLastUsedSource()
            
            // Cache all sources for filtering
            allSources = browseItems
            
            // Apply current filter if any
            val currentQuery = _searchQuery.value
            val displayItems = if (currentQuery.isBlank()) {
                browseItems
            } else {
                filterSourcesList(browseItems, currentQuery)
            }
            
            _state.update {
                it.copy(
                    isLoading = false,
                    sources = displayItems,
                    lastUsedSource = lastUsedSource
                )
            }
        }
    }
    
    private fun filterSourcesList(items: List<BrowseListItem>, query: String): List<BrowseListItem> {
        if (query.isBlank()) return items
        
        val filtered = mutableListOf<BrowseListItem>()
        var currentHeader: BrowseListItem.Header? = null
        val sourcesForHeader = mutableListOf<BrowseListItem.Source>()
        
        items.forEach { item ->
            when (item) {
                is BrowseListItem.Header -> {
                    // Add previous header if it has matching sources
                    if (currentHeader != null && sourcesForHeader.isNotEmpty()) {
                        filtered.add(currentHeader!!)
                        filtered.addAll(sourcesForHeader)
                    }
                    currentHeader = item
                    sourcesForHeader.clear()
                }
                is BrowseListItem.Source -> {
                    if (item.source.name.contains(query, ignoreCase = true)) {
                        sourcesForHeader.add(item)
                    }
                }
            }
        }
        
        // Add last header if it has matching sources
        if (currentHeader != null && sourcesForHeader.isNotEmpty()) {
            filtered.add(currentHeader!!)
            filtered.addAll(sourcesForHeader)
        }
        
        return filtered
    }
    
    private fun buildBrowseList(
        sources: List<CatalogueSource>,
        pinnedCatalogues: Set<String>,
        enabledLanguages: Set<String>
    ): List<BrowseListItem> {
        val items = mutableListOf<BrowseListItem>()
        val pinnedSources = mutableListOf<BrowseSourceItem>()
        
        // Group sources by language
        val byLang = TreeMap<String, MutableList<CatalogueSource>> { d1, d2 ->
            when {
                d1 == "" && d2 != "" -> 1
                d2 == "" && d1 != "" -> -1
                else -> d1.compareTo(d2)
            }
        }
        sources.groupByTo(byLang) { it.lang }
        
        // Collect pinned sources first
        sources.forEach { source ->
            if (source.id.toString() in pinnedCatalogues) {
                pinnedSources.add(source.toBrowseSourceItem(isPinned = true, enabledLanguages))
            }
        }
        
        // Add pinned section
        if (pinnedSources.isNotEmpty()) {
            items.add(BrowseListItem.Header(
                language = PINNED_KEY,
                displayName = "Pinned",
                isSpecial = true
            ))
            pinnedSources.forEach { items.add(BrowseListItem.Source(it)) }
        }
        
        // Add language sections
        byLang.forEach { (lang, langSources) ->
            val displayName = getLanguageDisplayName(lang)
            items.add(BrowseListItem.Header(
                language = lang,
                displayName = displayName,
                isSpecial = false
            ))
            langSources.forEach { source ->
                val isPinned = source.id.toString() in pinnedCatalogues
                items.add(BrowseListItem.Source(source.toBrowseSourceItem(isPinned, enabledLanguages)))
            }
        }
        
        return items
    }
    
    private fun CatalogueSource.toBrowseSourceItem(
        isPinned: Boolean,
        enabledLanguages: Set<String>
    ): BrowseSourceItem {
        val showLanguage = includeLangInName(enabledLanguages, extensionManager)
        val displayName = if (showLanguage) toString() else name
        
        return BrowseSourceItem(
            sourceId = id,
            name = displayName,
            language = lang,
            supportsLatest = supportsLatest,
            isPinned = isPinned,
            iconUrl = iconUrl(),
            isNovelSource = isNovelSource()
        )
    }
    
    private fun getLanguageDisplayName(code: String): String {
        return when (code) {
            "" -> "Other"
            "all" -> "Multi-language"
            else -> {
                val locale = java.util.Locale(code)
                locale.displayLanguage.replaceFirstChar { it.uppercase() }
            }
        }
    }
    
    private fun getEnabledSources(
        languages: Set<String>,
        hiddenCatalogues: Set<String>,
        currentMode: ContentType
    ): List<CatalogueSource> {
        return when (currentMode) {
            ContentType.MANGA -> getMangaSources(languages, hiddenCatalogues)
            ContentType.NOVEL -> getNovelSources(languages, hiddenCatalogues)
        }
    }
    
    private fun getMangaSources(
        languages: Set<String>,
        hiddenCatalogues: Set<String>
    ): List<CatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { it.lang in languages || it.id == LocalSource.ID }
            .filterNot { it.id.toString() in hiddenCatalogues }
            .filterNot { source -> source is NovelSourceWrapper }
            .sortedBy { "(${it.lang}) ${it.name}" }
    }
    
    private fun getNovelSources(
        languages: Set<String>,
        hiddenCatalogues: Set<String>
    ): List<CatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { source -> source is NovelSourceWrapper }
            .filter { it.lang in languages || it.id == LocalSource.ID }
            .filterNot { it.id.toString() in hiddenCatalogues }
            .sortedBy { "(${it.lang}) ${it.name}" }
    }
    
    private fun observeLastUsedSource() {
        lastUsedJob?.cancel()
        lastUsedJob = preferences.lastUsedCatalogueSource().changes()
            .drop(1)
            .onEach {
                val lastUsed = getLastUsedSource()
                _state.update { state -> state.copy(lastUsedSource = lastUsed) }
            }
            .launchIn(viewModelScope)
    }
    
    private fun getLastUsedSource(): BrowseSourceItem? {
        val sourceId = preferences.lastUsedCatalogueSource().get()
        val source = sourceManager.get(sourceId) as? CatalogueSource ?: return null
        
        val pinnedCatalogues = preferences.pinnedCatalogues().get()
        val isPinned = source.id.toString() in pinnedCatalogues
        
        // Don't show if pinned (already in pinned section)
        if (isPinned) return null
        
        // Check mode compatibility
        val currentMode = ModeManager.currentMode.value
        val isCorrectMode = when (currentMode) {
            ContentType.MANGA -> source !is NovelSourceWrapper
            ContentType.NOVEL -> source is NovelSourceWrapper
        }
        if (!isCorrectMode) return null
        
        val enabledLanguages = preferences.enabledLanguages().get()
        return source.toBrowseSourceItem(isPinned = false, enabledLanguages)
    }
    
    fun pinSource(sourceId: Long) {
        val current = preferences.pinnedCatalogues().get()
        val sourceIdStr = sourceId.toString()
        
        if (sourceIdStr in current) {
            preferences.pinnedCatalogues().set(current - sourceIdStr)
        } else {
            preferences.pinnedCatalogues().set(current + sourceIdStr)
        }
        
        loadSources()
    }
    
    fun hideSource(sourceId: Long) {
        val current = preferences.hiddenSources().get()
        preferences.hiddenSources().set(current + sourceId.toString())
        loadSources()
    }
    
    fun unhideSource(sourceId: Long) {
        val current = preferences.hiddenSources().get()
        preferences.hiddenSources().set(current - sourceId.toString())
        loadSources()
    }
    
    fun getSource(sourceId: Long): CatalogueSource? {
        return sourceManager.get(sourceId) as? CatalogueSource
    }
    
    fun refreshSources() {
        browseCache.invalidate()
        loadSources()
    }
    
    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"
    }
}
