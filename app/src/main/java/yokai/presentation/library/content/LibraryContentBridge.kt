package yokai.presentation.library.content

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.library.LibraryItem
import eu.kanade.tachiyomi.ui.library.LibraryMangaItem
import eu.kanade.tachiyomi.ui.library.LibraryNovelItem
import eu.kanade.tachiyomi.ui.library.LibraryPlaceholderItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.ui.UiPreferences

/**
 * Bridge class that connects the existing LibraryPresenter data flow
 * to the new Compose UI components. This allows gradual migration
 * without disrupting the existing adapter-based implementation.
 */
class LibraryContentBridge(
    private val preferences: PreferencesHelper = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
) {
    // Coroutine scope for preference flows
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    private val _uiState = MutableStateFlow<LibraryContentUiState>(LibraryContentUiState.Loading)
    val uiState: StateFlow<LibraryContentUiState> = _uiState.asStateFlow()
    
    private val _selectedItems = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItems: StateFlow<Set<Long>> = _selectedItems.asStateFlow()
    
    private val _expandedCategories = MutableStateFlow<Set<Int>>(emptySet())
    val expandedCategories: StateFlow<Set<Int>> = _expandedCategories.asStateFlow()
    
    // Display settings as reactive StateFlows
    val layoutMode: StateFlow<LibraryLayoutMode> = preferences.libraryLayout().changes()
        .map { value ->
            when (value) {
                0 -> LibraryLayoutMode.LIST
                1 -> LibraryLayoutMode.COMPACT_GRID
                2 -> LibraryLayoutMode.COMFORTABLE_GRID
                3 -> LibraryLayoutMode.COVER_ONLY_GRID
                else -> LibraryLayoutMode.COMFORTABLE_GRID
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), LibraryLayoutMode.COMFORTABLE_GRID)
    
    /**
     * Grid columns as reactive StateFlow.
     * The preference is a Float from -0.5 to 3.0, which maps to display values 0-7.
     * Display value determines the number of columns per row.
     */
    val gridColumns: StateFlow<Int> = preferences.gridSize().changes()
        .map { internalValue ->
            val displayValue = ((internalValue + 0.5f) * 2f).toInt()
            when (displayValue.coerceIn(0, 7)) {
                0 -> 1
                1 -> 2
                2 -> 2
                3 -> 3
                4 -> 4
                5 -> 5
                6 -> 6
                7 -> 7
                else -> 3
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 3)
    
    val showUnreadBadge: StateFlow<Boolean> = preferences.unreadBadgeType().changes()
        .map { it != 0 }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), true)
    
    // Expose the actual badge type for proper dot vs count display
    val unreadBadgeType: StateFlow<Int> = preferences.unreadBadgeType().changes()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 2)
    
    val showDownloadBadge: StateFlow<Boolean> = preferences.downloadBadge().changes()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)
    
    val showLanguageBadge: StateFlow<Boolean> = preferences.languageBadge().changes()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)
    
    // Always hide the continue/play button as per user preference - this removes it entirely
    val showContinueButton: StateFlow<Boolean> = preferences.hideStartReadingButton().changes()
        .map { hideButton -> !hideButton }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)
    
    val showCategoryHeaders: StateFlow<Boolean> = preferences.showAllCategories().changes()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)
    
    // Outline on covers setting
    val showOutline: StateFlow<Boolean> = uiPreferences.outlineOnCovers().changes()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)
    
    // Current category index for single category mode
    private val _currentCategoryIndex = MutableStateFlow(0)
    val currentCategoryIndex: StateFlow<Int> = _currentCategoryIndex.asStateFlow()
    
    // Current category ID for single category mode (more reliable than index)
    private val _currentCategoryId = MutableStateFlow<Int?>(null)
    val currentCategoryId: StateFlow<Int?> = _currentCategoryId.asStateFlow()
    
    // Whether to show item counts in headers
    private val _showNumberOfItems = MutableStateFlow(true)
    val showNumberOfItems: StateFlow<Boolean> = _showNumberOfItems.asStateFlow()
    
    /**
     * Update the current category index for single category mode.
     */
    fun setCurrentCategoryIndex(index: Int) {
        _currentCategoryIndex.value = index
    }
    
    /**
     * Update the current category ID for single category mode.
     */
    fun setCurrentCategoryId(id: Int?) {
        _currentCategoryId.value = id
    }
    
    /**
     * Update whether to show item counts.
     */
    fun setShowNumberOfItems(show: Boolean) {
        _showNumberOfItems.value = show
    }
    
    /**
     * Update the library content from the presenter.
     * Called from LibraryController when presenter emits new data.
     */
    fun updateContent(
        libraryItems: List<LibraryItem>,
        categories: List<Category>,
    ) {
        val categoryContentMap = mutableMapOf<Int, MutableList<LibraryContentItem>>()
        val categoryMap = categories.associateBy { it.id ?: 0 }
        
        // Initialize all categories with empty lists
        categories.forEach { category ->
            categoryContentMap[category.id ?: 0] = mutableListOf()
        }
        
        // Group items by category
        libraryItems.forEach { item ->
            val categoryId = when (item) {
                is LibraryMangaItem -> item.header.category.id ?: 0
                is LibraryNovelItem -> item.header.category.id ?: 0
                is LibraryPlaceholderItem -> item.header.category.id ?: 0
                else -> 0
            }
            
            // Skip placeholder items - they're just for empty states
            if (item is LibraryPlaceholderItem) return@forEach
            
            val contentItem = item.toContentItem()
            if (contentItem != null) {
                categoryContentMap.getOrPut(categoryId) { mutableListOf() }.add(contentItem)
            }
        }
        
        // Build category content list
        val categoryContents = categories.mapNotNull { category ->
            val items = categoryContentMap[category.id] ?: return@mapNotNull null
            LibraryCategoryContent(
                category = category,
                items = items,
            )
        }.filter { it.items.isNotEmpty() || categories.size > 1 }

        // Check if there are any real (non-placeholder) items at all
        val hasRealItems = libraryItems.any { it !is LibraryPlaceholderItem }
        val isEffectivelyEmpty = categoryContents.isEmpty() && !hasRealItems
        android.util.Log.d("LibraryContentBridge", "updateContent: categoryContents=${categoryContents.size}, hasRealItems=$hasRealItems, isEffectivelyEmpty=$isEffectivelyEmpty")

        _uiState.value = if (isEffectivelyEmpty) {
            LibraryContentUiState.Success(emptyList())
        } else {
            LibraryContentUiState.Success(categoryContents)
        }
        
        // Auto-expand all categories initially
        if (_expandedCategories.value.isEmpty() && categories.isNotEmpty()) {
            _expandedCategories.value = categories.mapNotNull { it.id }.toSet()
        }
    }
    
    /**
     * Set loading state
     */
    fun setLoading() {
        _uiState.value = LibraryContentUiState.Loading
    }
    
    /**
     * Set error state
     */
    fun setError(message: String) {
        _uiState.value = LibraryContentUiState.Error(message)
    }
    
    /**
     * Toggle category expansion
     */
    fun toggleCategoryExpansion(categoryId: Int) {
        _expandedCategories.update { current ->
            if (current.contains(categoryId)) {
                current - categoryId
            } else {
                current + categoryId
            }
        }
    }
    
    /**
     * Toggle item selection
     */
    fun toggleSelection(itemId: Long) {
        _selectedItems.update { current ->
            if (current.contains(itemId)) {
                current - itemId
            } else {
                current + itemId
            }
        }
    }
    
    /**
     * Clear all selections
     */
    fun clearSelection() {
        _selectedItems.value = emptySet()
    }
    
    /**
     * Select all items in the current view
     */
    fun selectAll(items: List<LibraryContentItem>) {
        _selectedItems.value = items.map { it.id }.toSet()
    }
    
    /**
     * Check if in selection mode
     */
    val isInSelectionMode: Boolean
        get() = _selectedItems.value.isNotEmpty()
    
    /**
     * Get selected items count
     */
    val selectedCount: Int
        get() = _selectedItems.value.size
}

/**
 * Extension to convert LibraryItem to LibraryContentItem
 */
private fun LibraryItem.toContentItem(): LibraryContentItem? {
    return when (this) {
        is LibraryMangaItem -> LibraryContentItem.MangaItem(
            id = manga.manga.id ?: return null,
            title = manga.manga.title,
            thumbnailUrl = manga.manga.thumbnail_url,
            author = manga.manga.author,
            artist = manga.manga.artist,
            isLocal = manga.manga.source == LocalSource.ID,
            downloadCount = downloadCount,
            unreadCount = manga.unread,
            language = sourceLanguage,
            sourceId = manga.manga.source,
            status = manga.manga.status,
            isFavorite = manga.manga.favorite,
            totalChapters = manga.totalChapters,
            readChapters = manga.read,
        )
        is LibraryNovelItem -> LibraryContentItem.NovelItem(
            id = novel.id,
            title = novel.title,
            thumbnailUrl = novel.posterUrl,
            author = novel.author,
            isLocal = false, // Novels aren't local sources
            downloadCount = downloadCount,
            unreadCount = unreadCount,
            language = language,
            sourceId = novel.source,
            status = novel.status,
            totalChapters = totalChapters.toInt(),
            readChapters = 0, // Will be computed from chapter tracking
            lastReadChapter = lastReadChapter,
        )
        else -> null
    }
}
