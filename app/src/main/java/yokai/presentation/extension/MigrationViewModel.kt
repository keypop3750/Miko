package yokai.presentation.extension

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.iconUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetManga

/**
 * ViewModel for the Compose-based Migration screen.
 * 
 * Handles source selection and manga list display for migration.
 * Exposes state as StateFlow for reactive Compose observation.
 */
class MigrationViewModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
) : ViewModel() {
    
    private val getManga: GetManga by injectLazy()
    
    private val _state = MutableStateFlow(MigrationUiState())
    val state: StateFlow<MigrationUiState> = _state.asStateFlow()
    
    // Events for one-shot actions
    private val _events = MutableSharedFlow<MigrationEvent>()
    val events = _events.asSharedFlow()
    
    // Cache of sources and manga
    private var sourceItems = emptyList<MigrationSourceUiItem>()
    private var mangaBySource = mutableMapOf<Long, List<MigrationMangaUiItem>>()
    
    init {
        Logger.d { "[MIG_VM] Initializing MigrationViewModel" }
        refresh()
    }
    
    fun onAction(action: MigrationAction) {
        when (action) {
            is MigrationAction.SelectSource -> selectSource(action.sourceId)
            is MigrationAction.DeselectSource -> deselectSource()
            is MigrationAction.MigrateAll -> migrateAllFromSource(action.sourceId)
            is MigrationAction.MigrateManga -> migrateManga(action.mangaId)
            is MigrationAction.SetSortOrder -> setSortOrder(action.order)
        }
    }
    
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            
            val favorites = getManga.awaitFavorites()
            val sources = findSourcesWithManga(favorites)
            sourceItems = sources
            
            // Pre-build manga lists for all sources
            mangaBySource.clear()
            sources.forEach { sourceItem ->
                val sourceManga = favorites
                    .filter { it.source == sourceItem.sourceId }
                    .map { it.toMigrationUiItem(sourceItem.name) }
                mangaBySource[sourceItem.sourceId] = sourceManga
            }
            
            _state.update {
                it.copy(
                    isLoading = false,
                    sources = sources,
                    currentView = MigrationViewType.SourceList,
                )
            }
        }
    }
    
    private fun findSourcesWithManga(library: List<Manga>): List<MigrationSourceUiItem> {
        val sortOrder = PreferenceValues.MigrationSourceOrder.fromPreference(preferences)
        val extensions = extensionManager.installedExtensionsFlow.value
        val obsoleteSources = extensions
            .filter { it.isObsolete }
            .flatMap { it.sources }
            .map { it.id }
        
        val sourceGroups = library
            .groupBy { it.source }
            .filter { it.key != LocalSource.ID }
        
        return sourceGroups
            .mapNotNull { (sourceId, manga) ->
                val source = sourceManager.getOrStub(sourceId)
                MigrationSourceUiItem(
                    sourceId = sourceId,
                    name = source.name,
                    lang = source.lang,
                    mangaCount = manga.size,
                    isUninstalled = source is SourceManager.StubSource,
                    isObsolete = sourceId in obsoleteSources,
                    iconUrl = source.iconUrl(),
                )
            }
            .sortedWith(
                compareBy(
                    {
                        when (sortOrder) {
                            PreferenceValues.MigrationSourceOrder.Alphabetically -> it.name
                            PreferenceValues.MigrationSourceOrder.MostEntries -> (Long.MAX_VALUE - it.mangaCount).toString()
                            PreferenceValues.MigrationSourceOrder.Obsolete -> (!it.isUninstalled && !it.isObsolete).toString()
                        }
                    },
                    { it.name }
                )
            )
    }
    
    private fun selectSource(sourceId: Long) {
        val source = sourceItems.find { it.sourceId == sourceId } ?: return
        val manga = mangaBySource[sourceId] ?: emptyList()
        
        _state.update {
            it.copy(
                currentView = MigrationViewType.MangaList,
                selectedSourceName = source.name,
                selectedSourceManga = manga,
            )
        }
    }
    
    private fun deselectSource() {
        _state.update {
            it.copy(
                currentView = MigrationViewType.SourceList,
                selectedSourceName = null,
                selectedSourceManga = emptyList(),
            )
        }
    }
    
    private fun migrateAllFromSource(sourceId: Long) {
        viewModelScope.launch {
            val manga = mangaBySource[sourceId] ?: return@launch
            val source = sourceItems.find { it.sourceId == sourceId } ?: return@launch
            _events.emit(MigrationEvent.StartMigration(sourceId, source.name, manga.map { it.mangaId }))
        }
    }
    
    private fun migrateManga(mangaId: Long) {
        viewModelScope.launch {
            _events.emit(MigrationEvent.MigrateSingle(mangaId))
        }
    }
    
    private fun setSortOrder(order: MigrationSortOrder) {
        val prefOrder = when (order) {
            MigrationSortOrder.Alphabetically -> PreferenceValues.MigrationSourceOrder.Alphabetically
            MigrationSortOrder.MostEntries -> PreferenceValues.MigrationSourceOrder.MostEntries
            MigrationSortOrder.Obsolete -> PreferenceValues.MigrationSourceOrder.Obsolete
        }
        preferences.migrationSourceOrder().set(prefOrder.value)
        
        _state.update { it.copy(sortOrder = order) }
        
        // Re-sort sources
        viewModelScope.launch(Dispatchers.Default) {
            val sorted = sourceItems.sortedWith(
                compareBy(
                    {
                        when (order) {
                            MigrationSortOrder.Alphabetically -> it.name
                            MigrationSortOrder.MostEntries -> (Long.MAX_VALUE - it.mangaCount).toString()
                            MigrationSortOrder.Obsolete -> (!it.isUninstalled && !it.isObsolete).toString()
                        }
                    },
                    { it.name }
                )
            )
            sourceItems = sorted
            
            _state.update { it.copy(sources = sorted) }
        }
    }
    
    /**
     * Handle back press - return true if consumed.
     */
    fun handleBack(): Boolean {
        return if (_state.value.currentView == MigrationViewType.MangaList) {
            deselectSource()
            true
        } else {
            false
        }
    }
}

/**
 * One-shot events from ViewModel to UI.
 */
sealed interface MigrationEvent {
    data class StartMigration(val sourceId: Long, val sourceName: String, val mangaIds: List<Long>) : MigrationEvent
    data class MigrateSingle(val mangaId: Long) : MigrationEvent
    data class ShowError(val message: String) : MigrationEvent
}
