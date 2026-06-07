package yokai.presentation.library.filter

import androidx.compose.runtime.Stable
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.library.LibraryGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import yokai.i18n.MR

/**
 * Filter option representing a single selectable filter within a filter group.
 */
@Stable
data class FilterOption(
    val label: String,
    val isSelected: Boolean = false,
)

/**
 * Filter group containing multiple filter options.
 */
@Stable
data class FilterGroup(
    val id: FilterType,
    val name: String,
    val options: List<FilterOption>,
    val selectedIndex: Int = 0,
)

/**
 * Types of filters available in the library.
 */
enum class FilterType(val char: Char, val stringRes: StringResource) {
    ReadProgress('u', MR.strings.read_progress),
    Unread('r', MR.strings.unread),
    Downloaded('d', MR.strings.downloaded),
    Status('c', MR.strings.status),
    SeriesType('m', MR.strings.series_type),
    Bookmarked('b', MR.strings.bookmarked),
    Tracking('t', MR.strings.tracking),
    ContentType('s', MR.strings.content_type),
    ;

    companion object {
        val DEFAULT_ORDER = entries.map { it.char }.joinToString("")
        fun fromChar(char: Char) = entries.find { it.char == char }
    }
}

/**
 * Different views/screens within the filter sheet.
 */
enum class FilterSheetScreen {
    MAIN,       // Filter chips + menu buttons
    GROUP_BY,   // Group by selection
}

/**
 * Expansion state for the filter sheet.
 */
enum class FilterSheetExpansion {
    COLLAPSED,  // Only filter row visible (pills + filter button)
    EXPANDED,   // Full sheet with menu buttons
}

/**
 * State holder for library filter UI.
 */
class LibraryFilterState {
    private val _filterGroups = MutableStateFlow<List<FilterGroup>>(emptyList())
    val filterGroups: StateFlow<List<FilterGroup>> = _filterGroups.asStateFlow()

    private val _filterOrder = MutableStateFlow(FilterType.DEFAULT_ORDER)
    val filterOrder: StateFlow<String> = _filterOrder.asStateFlow()

    private val _activeFilters = MutableStateFlow<Map<FilterType, Int>>(emptyMap())
    val activeFilters: StateFlow<Map<FilterType, Int>> = _activeFilters.asStateFlow()

    private val _groupByType = MutableStateFlow(LibraryGroup.BY_DEFAULT)
    val groupByType: StateFlow<Int> = _groupByType.asStateFlow()

    // Current screen within the sheet
    private val _currentScreen = MutableStateFlow(FilterSheetScreen.MAIN)
    val currentScreen: StateFlow<FilterSheetScreen> = _currentScreen.asStateFlow()

    // Expansion state for the sheet (collapsed vs expanded)
    private val _expansionState = MutableStateFlow(FilterSheetExpansion.COLLAPSED)
    val expansionState: StateFlow<FilterSheetExpansion> = _expansionState.asStateFlow()

    private val _showFilterDialog = MutableStateFlow(false)
    val showFilterDialog: StateFlow<Boolean> = _showFilterDialog.asStateFlow()

    private val _showReorderDialog = MutableStateFlow(false)
    val showReorderDialog: StateFlow<Boolean> = _showReorderDialog.asStateFlow()

    // Callbacks
    var onFilterChanged: ((FilterType, Int) -> Unit)? = null
    var onFilterOrderChanged: ((String) -> Unit)? = null
    var onGroupByChanged: ((Int) -> Unit)? = null
    var onClearFilters: (() -> Unit)? = null

    fun setFilterGroups(groups: List<FilterGroup>) {
        _filterGroups.value = groups
    }

    fun setFilterOrder(order: String) {
        _filterOrder.value = order
    }

    fun setActiveFilter(type: FilterType, value: Int) {
        val current = _activeFilters.value.toMutableMap()
        if (value == 0) {
            current.remove(type)
        } else {
            current[type] = value
        }
        _activeFilters.value = current
        onFilterChanged?.invoke(type, value)
    }

    fun setGroupBy(type: Int) {
        _groupByType.value = type
        onGroupByChanged?.invoke(type)
    }

    fun setCurrentScreen(screen: FilterSheetScreen) {
        _currentScreen.value = screen
    }

    fun setExpansionState(state: FilterSheetExpansion) {
        _expansionState.value = state
    }

    fun expand() {
        _expansionState.value = FilterSheetExpansion.EXPANDED
    }

    fun collapse() {
        _expansionState.value = FilterSheetExpansion.COLLAPSED
    }

    fun isExpanded(): Boolean = _expansionState.value == FilterSheetExpansion.EXPANDED

    fun showFilterDialog(show: Boolean) {
        _showFilterDialog.value = show
    }

    fun showReorderDialog(show: Boolean) {
        _showReorderDialog.value = show
    }

    fun clearFilters() {
        _activeFilters.value = emptyMap()
        onClearFilters?.invoke()
    }

    fun hasActiveFilters(): Boolean = _activeFilters.value.isNotEmpty()

    fun updateFilterOrder(newOrder: String) {
        _filterOrder.value = newOrder
        onFilterOrderChanged?.invoke(newOrder)
    }
    
    fun goBack(): Boolean {
        return if (_currentScreen.value != FilterSheetScreen.MAIN) {
            _currentScreen.value = FilterSheetScreen.MAIN
            true
        } else {
            false
        }
    }
}
