package yokai.presentation.component

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import yokai.core.content.ContentType

/**
 * State holder for the unified search bar used across Library and Browse screens.
 * 
 * This state is hosted at the activity level, allowing both screens to share
 * the same search bar without rebuilding it when navigating.
 */
class UnifiedSearchBarState {
    
    // Current context (which screen is using the search bar)
    private val _context = MutableStateFlow(SearchBarContext.LIBRARY)
    val context: StateFlow<SearchBarContext> = _context.asStateFlow()
    
    // Title and subtitle
    private val _title = MutableStateFlow("Library")
    val title: StateFlow<String> = _title.asStateFlow()
    
    private val _subtitle = MutableStateFlow<String?>(null)
    val subtitle: StateFlow<String?> = _subtitle.asStateFlow()
    
    // Search state
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Filter toggle state (for Library)
    private val _filterToggleState = MutableStateFlow(FilterToggleState.CLOSED)
    val filterToggleState: StateFlow<FilterToggleState> = _filterToggleState.asStateFlow()
    
    // Visibility
    private val _isVisible = MutableStateFlow(true)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()
    
    // Alpha for fade effects (e.g., when extension sheet expands)
    private val _alpha = MutableStateFlow(1f)
    val alpha: StateFlow<Float> = _alpha.asStateFlow()
    
    // Callbacks - these will be set by the current controller
    var onSearchClick: () -> Unit = {}
    var onSearchClose: () -> Unit = {}
    var onSearchQueryChange: (String) -> Unit = {}
    var onSearchSubmit: (String) -> Unit = {}
    var onFilterClick: () -> Unit = {}
    var onModeToggle: () -> Unit = {}
    var onMenuClick: () -> Unit = {}
    var onNavigationClick: () -> Unit = {}
    
    /**
     * Updates the search bar for a new context (e.g., when switching screens).
     */
    fun setContext(
        context: SearchBarContext,
        title: String,
        subtitle: String? = null,
    ) {
        _context.value = context
        _title.value = title
        _subtitle.value = subtitle
        // Reset search state when context changes
        _isSearchActive.value = false
        _searchQuery.value = ""
        _filterToggleState.value = FilterToggleState.CLOSED
    }
    
    /**
     * Updates just the title (e.g., when library count changes).
     */
    fun updateTitle(title: String, subtitle: String? = null) {
        _title.value = title
        _subtitle.value = subtitle
    }
    
    /**
     * Sets search mode active/inactive.
     */
    fun setSearchActive(active: Boolean) {
        _isSearchActive.value = active
        if (!active) {
            _searchQuery.value = ""
        }
    }
    
    /**
     * Updates the search query.
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * Updates the filter toggle state.
     */
    fun setFilterToggleState(state: FilterToggleState) {
        _filterToggleState.value = state
    }
    
    /**
     * Sets visibility of the search bar.
     */
    fun setVisible(visible: Boolean) {
        _isVisible.value = visible
    }
    
    /**
     * Sets alpha for fade effects.
     */
    fun setAlpha(alpha: Float) {
        _alpha.value = alpha.coerceIn(0f, 1f)
    }
    
    /**
     * Clears all callbacks (call when detaching from a controller).
     */
    fun clearCallbacks() {
        onSearchClick = {}
        onSearchClose = {}
        onSearchQueryChange = {}
        onSearchSubmit = {}
        onFilterClick = {}
        onModeToggle = {}
        onMenuClick = {}
        onNavigationClick = {}
    }
}
