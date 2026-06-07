package yokai.presentation.library.displayoptions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.ui.UiPreferences

/**
 * State holder for Display Options sheet.
 * Manages all preferences for Display, Badges, and Categories tabs.
 */
class DisplayOptionsState(
    private val preferences: PreferencesHelper = Injekt.get(),
    private val uiPreferences: UiPreferences = Injekt.get(),
) {
    // ============ Display Tab ============
    
    // Library layout (0=List, 1=CompactGrid, 2=ComfortableGrid, 3=CoverOnlyGrid)
    private val _libraryLayout = MutableStateFlow(preferences.libraryLayout().get())
    val libraryLayout: StateFlow<Int> = _libraryLayout.asStateFlow()
    
    fun setLibraryLayout(value: Int) {
        preferences.libraryLayout().set(value)
        _libraryLayout.value = value
        onLibraryLayoutChanged?.invoke()
    }
    
    // Grid size (slider value)
    private val _gridSize = MutableStateFlow(preferences.gridSize().get())
    val gridSize: StateFlow<Float> = _gridSize.asStateFlow()
    
    fun setGridSize(value: Float) {
        preferences.gridSize().set(value)
        _gridSize.value = value
        onLibraryLayoutChanged?.invoke()
    }
    
    fun resetGridSize() {
        setGridSize(1f) // Default value
    }
    
    // Uniform grid covers
    private val _uniformGrid = MutableStateFlow(uiPreferences.uniformGrid().get())
    val uniformGrid: StateFlow<Boolean> = _uniformGrid.asStateFlow()
    
    fun setUniformGrid(value: Boolean) {
        uiPreferences.uniformGrid().set(value)
        _uniformGrid.value = value
        onLibraryLayoutChanged?.invoke()
    }
    
    // Use staggered grid
    private val _useStaggeredGrid = MutableStateFlow(preferences.useStaggeredGrid().get())
    val useStaggeredGrid: StateFlow<Boolean> = _useStaggeredGrid.asStateFlow()
    
    fun setUseStaggeredGrid(value: Boolean) {
        preferences.useStaggeredGrid().set(value)
        _useStaggeredGrid.value = value
        onLibraryLayoutChanged?.invoke()
    }
    
    // Staggered grid is only enabled when uniform grid is disabled
    val staggeredGridEnabled: Boolean
        get() = !_uniformGrid.value
    
    // Show outline around covers
    private val _outlineOnCovers = MutableStateFlow(uiPreferences.outlineOnCovers().get())
    val outlineOnCovers: StateFlow<Boolean> = _outlineOnCovers.asStateFlow()
    
    fun setOutlineOnCovers(value: Boolean) {
        uiPreferences.outlineOnCovers().set(value)
        _outlineOnCovers.value = value
        onLibraryLayoutChanged?.invoke()
    }
    
    // ============ Badges Tab ============
    
    // Unread badge type (0=Hide, 1=Show, 2=ShowCount)
    private val _unreadBadgeType = MutableStateFlow(preferences.unreadBadgeType().get())
    val unreadBadgeType: StateFlow<Int> = _unreadBadgeType.asStateFlow()
    
    fun setUnreadBadgeType(value: Int) {
        preferences.unreadBadgeType().set(value)
        _unreadBadgeType.value = value
        onUnreadBadgesChanged?.invoke()
    }
    
    // Hide start reading button
    private val _hideStartReadingButton = MutableStateFlow(preferences.hideStartReadingButton().get())
    val hideStartReadingButton: StateFlow<Boolean> = _hideStartReadingButton.asStateFlow()
    
    fun setHideStartReadingButton(value: Boolean) {
        preferences.hideStartReadingButton().set(value)
        _hideStartReadingButton.value = value
    }
    
    // Language badges
    private val _languageBadge = MutableStateFlow(preferences.languageBadge().get())
    val languageBadge: StateFlow<Boolean> = _languageBadge.asStateFlow()
    
    fun setLanguageBadge(value: Boolean) {
        preferences.languageBadge().set(value)
        _languageBadge.value = value
        onLanguageBadgesChanged?.invoke()
    }
    
    // Download badges
    private val _downloadBadge = MutableStateFlow(preferences.downloadBadge().get())
    val downloadBadge: StateFlow<Boolean> = _downloadBadge.asStateFlow()
    
    fun setDownloadBadge(value: Boolean) {
        preferences.downloadBadge().set(value)
        _downloadBadge.value = value
        onDownloadBadgesChanged?.invoke()
    }
    
    // Show number of items (category item count)
    private val _categoryNumberOfItems = MutableStateFlow(preferences.categoryNumberOfItems().get())
    val categoryNumberOfItems: StateFlow<Boolean> = _categoryNumberOfItems.asStateFlow()
    
    fun setCategoryNumberOfItems(value: Boolean) {
        preferences.categoryNumberOfItems().set(value)
        _categoryNumberOfItems.value = value
    }
    
    // ============ Categories Tab ============
    
    // Always show current category
    private val _showCategoryInTitle = MutableStateFlow(preferences.showCategoryInTitle().get())
    val showCategoryInTitle: StateFlow<Boolean> = _showCategoryInTitle.asStateFlow()
    
    fun setShowCategoryInTitle(value: Boolean) {
        preferences.showCategoryInTitle().set(value)
        _showCategoryInTitle.value = value
        onShowMiniBar?.invoke()
    }
    
    // Show all categories
    private val _showAllCategories = MutableStateFlow(preferences.showAllCategories().get())
    val showAllCategories: StateFlow<Boolean> = _showAllCategories.asStateFlow()
    
    fun setShowAllCategories(value: Boolean) {
        preferences.showAllCategories().set(value)
        _showAllCategories.value = value
        onLibraryUpdateRequired?.invoke()
    }
    
    // Move collapsed dynamic categories to bottom
    private val _collapsedDynamicAtBottom = MutableStateFlow(preferences.collapsedDynamicAtBottom().get())
    val collapsedDynamicAtBottom: StateFlow<Boolean> = _collapsedDynamicAtBottom.asStateFlow()
    
    fun setCollapsedDynamicAtBottom(value: Boolean) {
        preferences.collapsedDynamicAtBottom().set(value)
        _collapsedDynamicAtBottom.value = value
        onLibraryUpdateRequired?.invoke()
    }
    
    // Show empty categories while filtering
    private val _showEmptyCategoriesWhileFiltering = MutableStateFlow(preferences.showEmptyCategoriesWhileFiltering().get())
    val showEmptyCategoriesWhileFiltering: StateFlow<Boolean> = _showEmptyCategoriesWhileFiltering.asStateFlow()
    
    fun setShowEmptyCategoriesWhileFiltering(value: Boolean) {
        preferences.showEmptyCategoriesWhileFiltering().set(value)
        _showEmptyCategoriesWhileFiltering.value = value
        onFilterUpdateRequired?.invoke()
    }
    
    // Hide hopper (0=Never, 1=Auto-hide, 2=Always)
    private val _hideHopperMode = MutableStateFlow(calculateHopperMode())
    val hideHopperMode: StateFlow<Int> = _hideHopperMode.asStateFlow()
    
    private fun calculateHopperMode(): Int {
        val hideHopper = preferences.hideHopper().get()
        val autohideHopper = preferences.autohideHopper().get()
        return when {
            hideHopper -> 2
            autohideHopper -> 1
            else -> 0
        }
    }
    
    fun setHideHopperMode(mode: Int) {
        preferences.hideHopper().set(mode == 2)
        preferences.autohideHopper().set(mode == 1)
        _hideHopperMode.value = mode
        onHopperVisibilityChanged?.invoke(mode == 2)
        onResetHopperY?.invoke()
    }
    
    // Hopper long press action
    private val _hopperLongPressAction = MutableStateFlow(preferences.hopperLongPressAction().get())
    val hopperLongPressAction: StateFlow<Int> = _hopperLongPressAction.asStateFlow()
    
    fun setHopperLongPressAction(value: Int) {
        preferences.hopperLongPressAction().set(value)
        _hopperLongPressAction.value = value
    }
    
    // Callbacks for external updates (e.g., refreshing library)
    var onLibraryLayoutChanged: (() -> Unit)? = null
    var onUnreadBadgesChanged: (() -> Unit)? = null
    var onDownloadBadgesChanged: (() -> Unit)? = null
    var onLanguageBadgesChanged: (() -> Unit)? = null
    var onLibraryUpdateRequired: (() -> Unit)? = null
    var onFilterUpdateRequired: (() -> Unit)? = null
    var onHopperVisibilityChanged: ((Boolean) -> Unit)? = null
    var onShowMiniBar: (() -> Unit)? = null
    var onResetHopperY: (() -> Unit)? = null
    
    companion object {
        // Layout constants from LibraryItem
        const val LAYOUT_LIST = LibraryItem.LAYOUT_LIST
        const val LAYOUT_COMPACT_GRID = LibraryItem.LAYOUT_COMPACT_GRID
        const val LAYOUT_COMFORTABLE_GRID = LibraryItem.LAYOUT_COMFORTABLE_GRID
        const val LAYOUT_COVER_ONLY_GRID = LibraryItem.LAYOUT_COVER_ONLY_GRID
        
        // Unread badge type constants
        const val UNREAD_BADGE_HIDE = 0
        const val UNREAD_BADGE_SHOW = 1
        const val UNREAD_BADGE_SHOW_COUNT = 2
        
        // Hopper mode constants
        const val HOPPER_ALWAYS_SHOW = 0
        const val HOPPER_AUTO_HIDE = 1
        const val HOPPER_ALWAYS_HIDE = 2
    }
}

@Composable
fun rememberDisplayOptionsState(): DisplayOptionsState {
    return remember { DisplayOptionsState() }
}
