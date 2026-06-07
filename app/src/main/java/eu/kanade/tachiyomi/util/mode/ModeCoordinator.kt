package eu.kanade.tachiyomi.util.mode

import android.content.Context
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.cache.BrowseExtensionCache
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.library.LibraryPresenter
import eu.kanade.tachiyomi.util.system.getPrefTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager
import yokai.domain.manga.interactor.GetLibraryManga
import yokai.domain.novel.NovelRepository

/**
 * Centralized coordinator for mode switching operations.
 * 
 * This class consolidates all mode-change handling into a single location,
 * eliminating redundant observers and race conditions. It provides:
 * 
 * 1. **Debounced mode changes** - Prevents rapid toggle spam
 * 2. **Theme comparison** - Skips recreate when themes match
 * 3. **Pre-caching** - Loads alternate mode data in background
 * 4. **State preservation** - Saves/restores controller state across recreate
 * 5. **Event coordination** - Single source of truth for mode change events
 */
@OptIn(FlowPreview::class)
class ModeCoordinator private constructor(
    private val context: Context,
    private val preferences: PreferencesHelper = Injekt.get(),
    private val browseCache: BrowseExtensionCache = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var prefetchJob: Job? = null
    
    // Debounced mode change events (300ms debounce to prevent spam)
    private val _modeChangeEvents = MutableSharedFlow<ModeChangeEvent>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val modeChangeEvents: SharedFlow<ModeChangeEvent> = _modeChangeEvents.asSharedFlow()
    
    // Last known mode for change detection
    private var lastKnownMode: ContentType? = null
    private var lastKnownTheme: yokai.core.content.ContentType? = null
    
    // Track the last event that was handled to prevent replay-triggered recreates
    @Volatile
    private var lastHandledEventTimestamp: Long = 0L
    
    // Flag to indicate we're in the middle of a recreate
    @Volatile
    private var isRecreating: Boolean = false
    
    // Preserved state across recreate
    private val preservedState = ModeTransitionState()
    
    init {
        setupModeObserver()
        prefetchAlternateModeData()
    }
    
    /**
     * Setup the main mode observer with debouncing.
     * This is the ONLY observer that should react to ModeManager.currentMode.
     */
    private fun setupModeObserver() {
        ModeManager.currentMode
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { newMode ->
                handleModeChange(newMode)
            }
            .launchIn(scope)
    }
    
    /**
     * Handle a mode change after debouncing.
     */
    private suspend fun handleModeChange(newMode: ContentType) {
        val previousMode = lastKnownMode
        lastKnownMode = newMode
        
        if (previousMode == null) {
            // Initial load, no event needed
            Logger.d { "🔄 [MODE_COORDINATOR] Initial mode set: $newMode" }
            return
        }
        
        if (previousMode == newMode) {
            // No actual change (debounce filtered duplicate)
            return
        }
        
        Logger.d { "🔄 [MODE_COORDINATOR] Mode changed: $previousMode -> $newMode" }
        
        // Get themes for comparison (for logging purposes)
        val previousTheme = getThemeForMode(previousMode)
        val newTheme = getThemeForMode(newMode)
        val themesAreDifferent = previousTheme != newTheme
        
        Logger.d { "🔄 [MODE_COORDINATOR] Theme comparison: $previousTheme -> $newTheme, different=$themesAreDifferent" }
        
        // Emit the mode change event
        // When themes are different, require activity recreate for proper View color refresh
        _modeChangeEvents.emit(
            ModeChangeEvent(
                previousMode = previousMode,
                newMode = newMode,
                requiresThemeAnimation = themesAreDifferent,
                requiresRecreate = themesAreDifferent, // Need recreate for View-based UIs
                timestamp = System.currentTimeMillis()
            )
        )
        
        // Trigger background prefetch for the alternate mode
        prefetchAlternateModeData()
    }
    
    /**
     * Get the resolved theme for a given mode.
     */
    private fun getThemeForMode(mode: ContentType): ResolvedTheme {
        val isDark = context.applicationContext.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES ||
            preferences.nightMode().get() == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        
        val theme = if (mode == ContentType.NOVEL) {
            if (isDark) preferences.novelDarkTheme().get() else preferences.novelLightTheme().get()
        } else {
            if (isDark) preferences.darkTheme().get() else preferences.lightTheme().get()
        }
        
        val amoled = if (mode == ContentType.NOVEL) {
            preferences.novelThemeDarkAmoled().get()
        } else {
            preferences.themeDarkAmoled().get()
        }
        
        return ResolvedTheme(theme, isDark, amoled && isDark)
    }
    
    /**
     * Pre-cache data for the alternate mode in the background.
     * This ensures instant switching when the user toggles modes.
     */
    private fun prefetchAlternateModeData() {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            // Wait a bit to not interfere with current mode loading
            delay(500)
            
            val currentMode = ModeManager.currentMode.value
            val alternateMode = when (currentMode) {
                ContentType.MANGA -> ContentType.NOVEL
                ContentType.NOVEL -> ContentType.MANGA
            }
            
            Logger.d { "🔄 [MODE_COORDINATOR] Pre-fetching data for alternate mode: $alternateMode" }
            
            // Pre-cache browse sources for alternate mode
            try {
                prefetchBrowseSources(alternateMode)
            } catch (e: Exception) {
                Logger.e(e) { "🔄 [MODE_COORDINATOR] Failed to prefetch browse sources" }
            }
            
            // Pre-cache library data for alternate mode
            try {
                prefetchLibraryData(alternateMode)
            } catch (e: Exception) {
                Logger.e(e) { "🔄 [MODE_COORDINATOR] Failed to prefetch library data" }
            }
        }
    }
    
    /**
     * Pre-cache library data for a specific mode.
     * This loads the library into memory so mode switches are instant.
     */
    private suspend fun prefetchLibraryData(mode: ContentType) {
        when (mode) {
            ContentType.MANGA -> {
                // Prefetch manga library
                try {
                    val getLibraryManga: GetLibraryManga = Injekt.get()
                    val mangaList = getLibraryManga.await()
                    Logger.d { "🔄 [MODE_COORDINATOR] Pre-cached ${mangaList.size} manga for library" }
                } catch (e: Exception) {
                    Logger.e(e) { "🔄 [MODE_COORDINATOR] Failed to prefetch manga library" }
                }
            }
            ContentType.NOVEL -> {
                // Prefetch novel library
                try {
                    val novelRepository: NovelRepository = Injekt.get()
                    // Collect first emission from the Flow to cache the data
                    val novels = novelRepository.getFavoriteNovels().first()
                    Logger.d { "🔄 [MODE_COORDINATOR] Pre-cached ${novels.size} novels for library" }
                } catch (e: Exception) {
                    Logger.e(e) { "🔄 [MODE_COORDINATOR] Failed to prefetch novel library" }
                }
            }
        }
    }
    
    /**
     * Pre-cache browse sources for a specific mode.
     */
    private suspend fun prefetchBrowseSources(mode: ContentType) {
        val enabledLanguages = preferences.enabledLanguages().get()
        val hiddenSources = preferences.hiddenSources().get()
        val pinnedSources = preferences.pinnedCatalogues().get()
        
        // Check if already cached
        val cached = browseCache.getCachedSources(
            enabledLanguages,
            hiddenSources,
            pinnedSources,
            mode
        )
        
        if (cached != null) {
            Logger.d { "🔄 [MODE_COORDINATOR] Browse sources for $mode already cached" }
            return
        }
        
        // Would need to load sources here, but that requires the full extension loading
        // The BrowseExtensionCache will handle this naturally when the user switches
        Logger.d { "🔄 [MODE_COORDINATOR] Browse sources for $mode will be cached on first access" }
    }
    
    /**
     * Mark that we're about to recreate, so we don't process the replayed event.
     */
    fun markRecreateStarted() {
        isRecreating = true
        Logger.d { "🔄 [MODE_COORDINATOR] Marked recreate started" }
    }
    
    /**
     * Mark that recreate is complete and we can process new events.
     */
    fun markRecreateComplete() {
        isRecreating = false
        Logger.d { "🔄 [MODE_COORDINATOR] Marked recreate complete" }
    }
    
    /**
     * Check if an event should be handled (not a stale replay).
     * Returns true if the event is new and should be processed.
     */
    fun shouldHandleEvent(event: ModeChangeEvent): Boolean {
        // If we're in the middle of a recreate, don't handle any events
        if (isRecreating) {
            Logger.d { "🔄 [MODE_COORDINATOR] Ignoring event during recreate: ${event.timestamp}" }
            return false
        }
        
        // If this event was already handled, skip it (replay scenario)
        if (event.timestamp <= lastHandledEventTimestamp) {
            Logger.d { "🔄 [MODE_COORDINATOR] Ignoring stale event: ${event.timestamp} <= $lastHandledEventTimestamp" }
            return false
        }
        
        return true
    }
    
    /**
     * Mark an event as handled to prevent re-processing on replay.
     */
    fun markEventHandled(event: ModeChangeEvent) {
        lastHandledEventTimestamp = event.timestamp
        Logger.d { "🔄 [MODE_COORDINATOR] Marked event handled: ${event.timestamp}" }
    }
    
    /**
     * Save controller state before recreate.
     * Call this from MainActivity before calling recreate().
     */
    fun saveStateBeforeRecreate(
        scrollPositions: Map<String, Int> = emptyMap(),
        selectedTabs: Map<String, Int> = emptyMap(),
        filterStates: Map<String, Any?> = emptyMap()
    ) {
        preservedState.scrollPositions.clear()
        preservedState.scrollPositions.putAll(scrollPositions)
        preservedState.selectedTabs.clear()
        preservedState.selectedTabs.putAll(selectedTabs)
        preservedState.filterStates.clear()
        preservedState.filterStates.putAll(filterStates)
        preservedState.timestamp = System.currentTimeMillis()
        
        Logger.d { "🔄 [MODE_COORDINATOR] Saved state: scrollPositions=${scrollPositions.size}, tabs=${selectedTabs.size}" }
    }
    
    /**
     * Restore controller state after recreate.
     * Call this from MainActivity after recreate completes.
     */
    fun restoreStateAfterRecreate(): ModeTransitionState? {
        // State is only valid for 5 seconds after save
        if (System.currentTimeMillis() - preservedState.timestamp > 5000) {
            Logger.d { "🔄 [MODE_COORDINATOR] State expired, not restoring" }
            return null
        }
        
        Logger.d { "🔄 [MODE_COORDINATOR] Restoring state" }
        return preservedState.copy()
    }
    
    /**
     * Apply theme overlay dynamically without recreating activity.
     * This is used when themes are similar enough to avoid recreate.
     * 
     * The overlay applies mode-specific styling adjustments while keeping
     * the base theme intact, providing a seamless visual transition.
     */
    fun applyThemeOverlay(context: Context, newMode: ContentType) {
        val activity = context as? androidx.appcompat.app.AppCompatActivity ?: return
        
        // Get the appropriate overlay for the mode
        val overlayRes = when (newMode) {
            ContentType.MANGA -> eu.kanade.tachiyomi.R.style.ThemeOverlay_Tachiyomi_MangaMode
            ContentType.NOVEL -> eu.kanade.tachiyomi.R.style.ThemeOverlay_Tachiyomi_NovelMode
        }
        
        // Apply the overlay to the activity's theme
        activity.theme.applyStyle(overlayRes, true)
        
        // Update the status bar and navigation bar appearance
        val isDark = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        val wic = androidx.core.view.WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView
        )
        wic.isAppearanceLightStatusBars = !isDark
        wic.isAppearanceLightNavigationBars = !isDark
        
        Logger.d { "🔄 [MODE_COORDINATOR] Applied theme overlay for mode: $newMode (overlay=$overlayRes)" }
    }
    
    companion object {
        private const val DEBOUNCE_MS = 300L
        
        @Volatile
        private var instance: ModeCoordinator? = null
        
        fun getInstance(context: Context): ModeCoordinator {
            return instance ?: synchronized(this) {
                instance ?: ModeCoordinator(context.applicationContext).also { instance = it }
            }
        }
        
        /**
         * Clear the instance (for testing or cleanup).
         */
        fun clearInstance() {
            instance = null
        }
    }
}

/**
 * Event emitted when mode changes.
 * NOTE: requiresThemeAnimation indicates if themes differ and colors should animate.
 * requiresRecreate indicates if the activity should be recreated to properly refresh View colors.
 */
data class ModeChangeEvent(
    val previousMode: ContentType,
    val newMode: ContentType,
    val requiresThemeAnimation: Boolean,
    val requiresRecreate: Boolean = false,
    val timestamp: Long
)

/**
 * Resolved theme information for comparison.
 */
private data class ResolvedTheme(
    val theme: eu.kanade.tachiyomi.util.system.Themes,
    val isDark: Boolean,
    val isAmoled: Boolean
)

/**
 * State preserved across activity recreate.
 */
data class ModeTransitionState(
    val scrollPositions: MutableMap<String, Int> = mutableMapOf(),
    val selectedTabs: MutableMap<String, Int> = mutableMapOf(),
    val filterStates: MutableMap<String, Any?> = mutableMapOf(),
    var timestamp: Long = 0L
) {
    fun copy(): ModeTransitionState = ModeTransitionState(
        scrollPositions = scrollPositions.toMutableMap(),
        selectedTabs = selectedTabs.toMutableMap(),
        filterStates = filterStates.toMutableMap(),
        timestamp = timestamp
    )
}
