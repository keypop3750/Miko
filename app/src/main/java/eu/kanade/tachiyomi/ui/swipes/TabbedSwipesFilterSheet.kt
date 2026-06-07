package eu.kanade.tachiyomi.ui.swipes

import android.app.Activity
import android.view.View
import android.view.View.inflate
import androidx.annotation.IntRange
import androidx.core.view.isVisible
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.widget.TabbedBottomSheetDialog

class TabbedSwipesFilterSheet(
    private val activity: Activity,
    private val currentFilters: SwipesFilters,
    private val onFiltersApplied: (SwipesFilters) -> Unit,
    @IntRange(from = 0, to = 1) startingTab: Int = 0
) : TabbedBottomSheetDialog(activity) {

    private val filterView = inflate(activity, R.layout.swipes_filter_view, null) as SwipesFilterView
    private val sourcesView = inflate(activity, R.layout.swipes_sources_view, null) as SwipesSourcesView
    private val repository = SwipesRepository()

    init {
        binding.menu.isVisible = false
        
        // Initialize filter view
        filterView.initializeFilters(currentFilters, onFiltersApplied) { dismiss() }
        
        // Initialize sources view with source groups
        val availableSourceGroups = repository.getAvailableSourceGroups()
        sourcesView.initializeSourceGroups(
            sourceGroups = availableSourceGroups,
            filters = currentFilters,
            onApply = { newFilters ->
                // Apply filters directly (individual source IDs already collected)
                onFiltersApplied(newFilters)
            },
            dismiss = { dismiss() }
        )

        binding.tabs.getTabAt(startingTab)?.select()
    }

    override fun getTabViews(): List<View> = listOf(
        filterView,
        sourcesView,
    )

    override fun getTabTitles(): List<StringResource> = listOf(
        MR.strings.filter,
        MR.strings.sources,
    )
}