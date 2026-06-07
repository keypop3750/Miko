package eu.kanade.tachiyomi.ui.source

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.cache.BrowseExtensionCache
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.novel.NovelSourceWrapper
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager
import java.util.TreeMap

/**
 * Presenter of [BrowseController]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param sourceManager manages the different sources.
 * @param preferences application preferences.
 */
class SourcePresenter(
    val controller: BrowseController,
    val sourceManager: SourceManager = Injekt.get(),
    val extensionManager: ExtensionManager = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val browseCache: BrowseExtensionCache = Injekt.get(),
) {

    private var scope = CoroutineScope(Job() + Dispatchers.Default)
    var sources = getEnabledSources()

    var sourceItems = emptyList<SourceItem>()
    var lastUsedItem: SourceItem? = null

    var lastUsedJob: Job? = null

    fun onCreate() {
        if (lastSources != null) {
            if (sourceItems.isEmpty()) {
                sourceItems = lastSources ?: emptyList()
            }
            lastUsedItem = lastUsedItemRem
            lastSources = null
            lastUsedItemRem = null
        }

        // Observe extension changes and invalidate cache when extensions are modified
        scope.launch {
            extensionManager.installedExtensionsFlow
                .drop(1) // Skip initial emission
                .collect {
                    Logger.d { "📦 [BROWSE_CACHE] Extension list changed, invalidating cache" }
                    browseCache.invalidate()
                    // Don't auto-reload here - let user refresh manually or on next open
                }
        }

        // Observe mode changes and reload sources accordingly
        scope.launch {
            ModeManager.currentMode.collect { mode ->
                updateSources() // Need to recalculate sources, not just reload
            }
        }

        // Load enabled and last used sources
        loadSources()
    }

    /**
     * Load sources with instant cache display + background refresh strategy.
     * 
     * **Flow:**
     * 1. Check cache - if available, show INSTANTLY (< 16ms)
     * 2. ALWAYS refresh in background (even if cache was shown)
     * 3. Update UI if background refresh finds changes
     * 
     * **Result:**
     * - First load: Normal speed (300-500ms)
     * - Subsequent loads: Instant cached display + invisible background update
     */
    private fun loadSources() {
        scope.launch {
            val enabledLanguages = preferences.enabledLanguages().get()
            val hiddenSources = preferences.hiddenSources().get()
            val pinnedCatalogues = preferences.pinnedCatalogues().get()
            
            // Try cache first - eliminates repeated extension scanning
            val cachedSources = browseCache.getCachedSources(
                enabledLanguages,
                hiddenSources,
                pinnedCatalogues,
                ModeManager.currentMode.value
            )
            
            if (cachedSources != null) {
                // Cache HIT - show instantly, then refresh in background
                Logger.d { "📦 [BROWSE] Cache HIT - showing ${cachedSources.size} cached sources instantly" }
                sourceItems = cachedSources
                lastUsedItem = getLastUsedSource(preferences.lastUsedCatalogueSource().get())
                withUIContext {
                    controller.setSources(sourceItems, lastUsedItem)
                    loadLastUsedSource()
                }
                
                // Continue to background refresh (don't return early)
                Logger.d { "📦 [BROWSE] Starting background refresh..." }
            } else {
                Logger.d { "📦 [BROWSE] Cache MISS - loading from extensions" }
            }
            
            // Load from extensions (either cache miss OR background refresh)
            val pinnedSources = mutableListOf<SourceItem>()

            val map = TreeMap<String, MutableList<CatalogueSource>> { d1, d2 ->
                // Catalogues without a lang defined will be placed at the end
                when {
                    d1 == "" && d2 != "" -> 1
                    d2 == "" && d1 != "" -> -1
                    else -> d1.compareTo(d2)
                }
            }
            val byLang = sources.groupByTo(map) { it.lang }
            val freshSourceItems = byLang.flatMap {
                val langItem = LangItem(it.key)
                it.value.map { source ->
                    val isPinned = source.id.toString() in pinnedCatalogues
                    if (source.id.toString() in pinnedCatalogues) {
                        pinnedSources.add(SourceItem(source, LangItem(PINNED_KEY)))
                    }

                    SourceItem(source, langItem, isPinned)
                }
            }

            val finalSourceItems = if (pinnedSources.isNotEmpty()) {
                pinnedSources + freshSourceItems
            } else {
                freshSourceItems
            }
            
            // Cache the loaded sources for next time
            browseCache.cacheSources(
                finalSourceItems,
                enabledLanguages,
                hiddenSources,
                pinnedCatalogues,
                ModeManager.currentMode.value
            )

            // Only update UI if data actually changed (background refresh found updates)
            if (cachedSources != null) {
                // This was a background refresh - only update if different
                // OPTIMIZED: Fast-path size check first (avoids iteration if sizes differ)
                val changed = finalSourceItems.size != cachedSources.size || run {
                    // Use indexed loop instead of zip() to avoid Pair allocations
                    var hasDifference = false
                    for (i in finalSourceItems.indices) {
                        if (finalSourceItems[i].source.id != cachedSources[i].source.id) {
                            hasDifference = true
                            break // Early exit on first change
                        }
                    }
                    hasDifference
                }
                
                if (changed) {
                    Logger.d { "📦 [BROWSE] Background refresh found changes - updating UI" }
                    sourceItems = finalSourceItems
                    lastUsedItem = getLastUsedSource(preferences.lastUsedCatalogueSource().get())
                    withUIContext {
                        controller.setSources(sourceItems, lastUsedItem)
                        loadLastUsedSource()
                    }
                } else {
                    Logger.d { "📦 [BROWSE] Background refresh - no changes detected" }
                }
            } else {
                // Cache miss - always update UI
                sourceItems = finalSourceItems
                Logger.d { "[SOURCE_PRESENTER] Updating UI with ${sourceItems.size} source items" }
                lastUsedItem = getLastUsedSource(preferences.lastUsedCatalogueSource().get())
                withUIContext {
                    controller.setSources(sourceItems, lastUsedItem)
                    loadLastUsedSource()
                }
            }
        }
    }

    private fun loadLastUsedSource() {
        lastUsedJob?.cancel()
        lastUsedJob = preferences.lastUsedCatalogueSource().changes()
            .drop(1)
            .onEach {
                lastUsedItem = getLastUsedSource(it)
                withUIContext {
                    controller.setLastUsedSource(lastUsedItem)
                }
            }.launchIn(scope)
    }
    private fun getLastUsedSource(value: Long): SourceItem? {
        return (sourceManager.get(value) as? CatalogueSource)?.let { source ->
            val pinnedCatalogues = preferences.pinnedCatalogues().get()
            val isPinned = source.id.toString() in pinnedCatalogues
            
            // Filter by current mode - don't show manga sources in novel mode and vice versa
            // Use proper source type checking instead of ID threshold
            val currentMode = ModeManager.currentMode.value
            val sourceIsNovel = source is NovelSourceWrapper
            val isCorrectMode = when (currentMode) {
                ContentType.MANGA -> !sourceIsNovel
                ContentType.NOVEL -> sourceIsNovel
            }
            
            // Don't show last-used source if it's pinned (already in pinned section)
            // or if it doesn't match current mode
            if (isPinned || !isCorrectMode) {
                null
            } else {
                SourceItem(source, LangItem(LAST_USED_KEY), isPinned)
            }
        }
    }

    fun updateSources() {
        Logger.d { "[SOURCE_PRESENTER] updateSources() called for mode: ${ModeManager.currentMode.value}" }
        sources = getEnabledSources()
        Logger.d { "[SOURCE_PRESENTER] After getEnabledSources(): ${sources.size} sources" }
        loadSources()
    }

    fun onDestroy() {
        lastSources = sourceItems
        lastUsedItemRem = lastUsedItem
    }

    /**
     * Returns a list of enabled sources ordered by language and name.
     *
     * @return list containing enabled sources.
     */
    private fun getEnabledSources(): List<CatalogueSource> {
        val languages = preferences.enabledLanguages().get()
        val hiddenCatalogues = preferences.hiddenSources().get()
        val currentMode = ModeManager.currentMode.value

        return when (currentMode) {
            ContentType.MANGA -> getMangaSources(languages, hiddenCatalogues)
            ContentType.NOVEL -> getNovelSources(languages, hiddenCatalogues)
        }
    }

    private fun getMangaSources(languages: Set<String>, hiddenCatalogues: Set<String>): List<CatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter { it.lang in languages || it.id == LocalSource.ID }
            .filterNot { it.id.toString() in hiddenCatalogues }
            .filterNot { source -> source is NovelSourceWrapper } // Exclude novel sources
            .sortedBy { "(${it.lang}) ${it.name}" }
    }

    private fun getNovelSources(languages: Set<String>, hiddenCatalogues: Set<String>): List<CatalogueSource> {
        val allSources = sourceManager.getCatalogueSources()
        Logger.d { "[SOURCE_PRESENTER] Getting novel sources. Total catalogue sources: ${allSources.size}" }
        Logger.d { "[SOURCE_PRESENTER] Languages enabled: $languages" }
        
        val novelSources = allSources.filter { source -> source is NovelSourceWrapper }
        Logger.d { "[SOURCE_PRESENTER] Found ${novelSources.size} NovelSourceWrapper sources" }
        novelSources.forEach { Logger.d { "[SOURCE_PRESENTER] Novel source: ${it.name} (lang=${it.lang})" } }
        
        val filtered = novelSources
            .filter { it.lang in languages || it.id == LocalSource.ID }
            .filterNot { it.id.toString() in hiddenCatalogues }
            .sortedBy { "(${it.lang}) ${it.name}" }
        
        Logger.d { "[SOURCE_PRESENTER] After filtering: ${filtered.size} sources" }
        return filtered
    }

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"

        private var lastSources: List<SourceItem>? = null
        private var lastUsedItemRem: SourceItem? = null

        fun onLowMemory() {
            lastSources = null
            lastUsedItemRem = null
        }
    }
}
