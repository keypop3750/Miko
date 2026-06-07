package eu.kanade.tachiyomi.ui.novel.browse.filter

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.NovelFilterBottomSheetBinding
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.source.filter.GroupItem
import eu.kanade.tachiyomi.ui.source.filter.SelectItem
import eu.kanade.tachiyomi.ui.source.filter.SortGroup
import eu.kanade.tachiyomi.ui.source.filter.SortItem
import eu.kanade.tachiyomi.ui.source.filter.TextItem
import eu.kanade.tachiyomi.ui.source.filter.TriStateSectionItem
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.widget.E2EBottomSheetDialog
import yokai.presentation.component.recyclerview.VertPaddingDecoration
import yokai.source.novel.model.NovelProviderCapabilities

/**
 * Universal filter bottom sheet for novel sources.
 * Uses tabs to organize filter options: Sort, Genres, More.
 * Dynamically shows only filters that the source supports via capabilities.
 */
class NovelFilterBottomSheet(
    activity: Activity,
    private val onFilterApplied: (NovelFilterState) -> Unit,
    private val onResetClicked: () -> Unit,
    private var currentState: NovelFilterState = NovelFilterState(),
    private var initialTab: Int = 0,
    private val sourceCapabilities: NovelProviderCapabilities = NovelProviderCapabilities(),
) : E2EBottomSheetDialog<NovelFilterBottomSheetBinding>(activity) {

    private val adapter: FlexibleAdapter<IFlexible<*>> = FlexibleAdapter<IFlexible<*>>(null)
        .setDisplayHeadersAtStartUp(true)
    
    override var recyclerView: RecyclerView? = binding.filterRecycler
    
    override fun createBinding(inflater: LayoutInflater) = NovelFilterBottomSheetBinding.inflate(inflater)

    // Filter items for each tab
    private var sortFilters: FilterList = createSortFilters()
    private var genreFilters: FilterList = createGenreFilters()
    private var moreFilters: FilterList = createMoreFilters()
    
    private var currentTab = initialTab
    
    /** Get the current tab position for state persistence */
    fun getCurrentTab(): Int = currentTab

    init {
        sheetBehavior.peekHeight = 450.dpToPx
        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        
        setupTabs()
        setupRecyclerView()
        setupButtons()
        
        // Restore to initial tab if not 0
        if (initialTab > 0 && initialTab <= 2) {
            binding.filterTabs.selectTab(binding.filterTabs.getTabAt(initialTab))
        } else {
            showTabContent(0)
        }
    }

    private fun setupTabs() {
        // Add tabs
        binding.filterTabs.addTab(binding.filterTabs.newTab().setText("Sort"))
        binding.filterTabs.addTab(binding.filterTabs.newTab().setText("Genres"))
        binding.filterTabs.addTab(binding.filterTabs.newTab().setText("More"))
        
        binding.filterTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.position?.let { position ->
                    currentTab = position
                    showTabContent(position)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.addItemDecoration(VertPaddingDecoration(8.dpToPx))
        recyclerView?.adapter = adapter
        recyclerView?.setHasFixedSize(false)
    }

    private fun setupButtons() {
        binding.resetBtn.setOnClickListener {
            currentState = NovelFilterState()
            sortFilters = createSortFilters()
            genreFilters = createGenreFilters()
            moreFilters = createMoreFilters()
            showTabContent(currentTab)
            onResetClicked()
        }

        binding.applyBtn.setOnClickListener {
            // Collect current filter state from all filters
            currentState = collectFilterState()
            onFilterApplied(currentState)
            dismiss()
        }
    }

    private fun showTabContent(tabIndex: Int, restoreScroll: Boolean = false) {
        val filters = when (tabIndex) {
            0 -> sortFilters
            1 -> genreFilters
            2 -> moreFilters
            else -> FilterList()
        }
        
        // Expand Sort and Genres groups by default
        val shouldExpand = tabIndex == 0 || tabIndex == 1
        adapter.updateDataSet(filtersToItems(filters, expandGroups = shouldExpand))
        
        if (!restoreScroll) {
            recyclerView?.scrollToPosition(0)
        }
    }

    private fun filtersToItems(filters: FilterList, expandGroups: Boolean = false): List<IFlexible<*>> {
        return filters.mapNotNull { filter ->
            when (filter) {
                is Filter.Sort -> {
                    val group = SortGroup(filter)
                    val subItems = filter.values.mapIndexed { index, name ->
                        SortItem(name, group)
                    }
                    group.subItems = subItems
                    // Expand by default if requested
                    if (expandGroups) {
                        group.isExpanded = true
                    }
                    group
                }
                is Filter.Group<*> -> {
                    val group = GroupItem(filter)
                    val subItems = filter.state.mapNotNull { subFilter ->
                        when (subFilter) {
                            is Filter.TriState -> TriStateSectionItem(subFilter).apply { header = group }
                            else -> null
                        }
                    }
                    group.subItems = subItems
                    // Expand by default if requested
                    if (expandGroups) {
                        group.isExpanded = true
                    }
                    group
                }
                is Filter.Select<*> -> SelectItem(filter)
                is Filter.Text -> TextItem(filter)
                else -> null
            }
        }
    }

    private fun collectFilterState(): NovelFilterState {
        // Collect sort options - map back to SortOption based on available sorts
        val supportedSorts = sourceCapabilities.supportedSorts
        val availableSorts = if (supportedSorts.isNotEmpty()) {
            SortOption.entries.filter { supportedSorts.contains(it.queryValue) }
        } else {
            SortOption.entries.toList()
        }
        
        var sortBy = SortOption.POPULAR
        var sortOrder = SortOrder.DESCENDING
        
        for (filter in sortFilters) {
            when (filter) {
                is Filter.Sort -> {
                    if (filter.state != null) {
                        val index = filter.state!!.index
                        val ascending = filter.state!!.ascending
                        // Map back to actual SortOption using available sorts list
                        sortBy = availableSorts.getOrElse(index) { SortOption.POPULAR }
                        sortOrder = if (ascending) SortOrder.ASCENDING else SortOrder.DESCENDING
                    }
                }
                else -> {} // Ignore other filter types
            }
        }
        
        // Collect genre filters
        val includedGenres = mutableSetOf<Genre>()
        val excludedGenres = mutableSetOf<Genre>()
        
        // Collect content warning filters
        val includedContentWarnings = mutableSetOf<ContentWarning>()
        val excludedContentWarnings = mutableSetOf<ContentWarning>()
        
        for (filter in genreFilters) {
            if (filter is Filter.Group<*>) {
                val groupName = filter.name.lowercase()
                
                for (subFilter in filter.state) {
                    if (subFilter is Filter.TriState) {
                        if (groupName.contains("content") || groupName.contains("warning")) {
                            // Content warning filter
                            val warning = ContentWarning.entries.find { it.displayName == subFilter.name }
                            if (warning != null) {
                                when (subFilter.state) {
                                    Filter.TriState.STATE_INCLUDE -> includedContentWarnings.add(warning)
                                    Filter.TriState.STATE_EXCLUDE -> excludedContentWarnings.add(warning)
                                }
                            }
                        } else {
                            // Genre filter
                            val genre = Genre.entries.find { it.displayName == subFilter.name }
                            if (genre != null) {
                                when (subFilter.state) {
                                    Filter.TriState.STATE_INCLUDE -> includedGenres.add(genre)
                                    Filter.TriState.STATE_EXCLUDE -> excludedGenres.add(genre)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Collect more filters (status, content rating, violence, language, chapters, rating)
        var status = NovelStatus.ANY
        var contentRating = ContentRating.ANY
        var violenceLevel = ViolenceLevel.ANY
        var languageLevel = LanguageLevel.ANY
        var minChapters: Int? = null
        var maxChapters: Int? = null
        var minRating: Float? = null
        
        // Collect text filters from sort tab (min/max chapters, min rating)
        for (filter in sortFilters) {
            when (filter) {
                is Filter.Text -> {
                    when (filter.name) {
                        "Min Chapters" -> minChapters = filter.state.toIntOrNull()
                        "Max Chapters" -> maxChapters = filter.state.toIntOrNull()
                        "Min Rating (0-5)" -> minRating = filter.state.toFloatOrNull()?.coerceIn(0f, 5f)
                    }
                }
                else -> {} // Sort filter already handled above
            }
        }
        
        for (filter in moreFilters) {
            when (filter) {
                is Filter.Select<*> -> {
                    when (filter.name) {
                        "Status" -> status = NovelStatus.entries.getOrElse(filter.state) { NovelStatus.ANY }
                        "Content Rating" -> contentRating = ContentRating.entries.getOrElse(filter.state) { ContentRating.ANY }
                        "Violence Level" -> violenceLevel = ViolenceLevel.entries.getOrElse(filter.state) { ViolenceLevel.ANY }
                        "Profanity Level" -> languageLevel = LanguageLevel.entries.getOrElse(filter.state) { LanguageLevel.ANY }
                    }
                }
                else -> {} // Ignore other filter types
            }
        }
        
        return NovelFilterState(
            sortBy = sortBy,
            sortOrder = sortOrder,
            status = status,
            genres = includedGenres,
            excludedGenres = excludedGenres,
            includedContentWarnings = includedContentWarnings,
            excludedContentWarnings = excludedContentWarnings,
            contentRating = contentRating,
            violenceLevel = violenceLevel,
            languageLevel = languageLevel,
            minChapters = minChapters,
            maxChapters = maxChapters,
            minRating = minRating,
        )
    }

    // Create filter lists for each tab
    private fun createSortFilters(): FilterList {
        // Only show sort options that the source supports
        val supportedSorts = sourceCapabilities.supportedSorts
        val availableSorts = if (supportedSorts.isNotEmpty()) {
            SortOption.entries.filter { sort ->
                supportedSorts.contains(sort.queryValue)
            }
        } else {
            // If no capabilities specified, show all sorts as fallback
            SortOption.entries.toList()
        }
        
        val sortOptions = availableSorts.map { it.displayName }.toTypedArray()
        val currentIndex = availableSorts.indexOf(currentState.sortBy).coerceAtLeast(0)
        val ascending = currentState.sortOrder == SortOrder.ASCENDING
        
        val filters = mutableListOf<Filter<*>>()
        
        // Always show sort (even with limited options)
        if (sortOptions.isNotEmpty()) {
            filters.add(SortFilter("Sort By", sortOptions, Filter.Sort.Selection(currentIndex, ascending)))
        }
        
        // Only show chapter count filter if source supports it
        if (sourceCapabilities.supportsChapterCountFilter) {
            filters.add(MinChaptersFilter("Min Chapters", currentState.minChapters?.toString() ?: ""))
            filters.add(MaxChaptersFilter("Max Chapters", currentState.maxChapters?.toString() ?: ""))
        }
        
        // Only show rating filter if source supports it
        if (sourceCapabilities.supportsRatingFilter) {
            filters.add(MinRatingFilter("Min Rating (0-5)", currentState.minRating?.toString() ?: ""))
        }
        
        return FilterList(filters)
    }

    private fun createGenreFilters(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        
        // Get supported genres from capabilities
        val supportedGenreStrings = sourceCapabilities.supportedGenres
        
        // Filter genres based on what the source supports
        val availableGenres = if (supportedGenreStrings.isNotEmpty()) {
            Genre.allGenres.filter { genre ->
                supportedGenreStrings.contains(genre.queryValue)
            }
        } else {
            // If no capabilities specified, show all genres as fallback
            Genre.allGenres
        }
        
        if (availableGenres.isNotEmpty()) {
            val genreFilters = availableGenres.map { genre ->
                GenreFilter(
                    genre.displayName, 
                    currentState.genres.contains(genre), 
                    currentState.excludedGenres.contains(genre),
                    // Disable exclusion if source doesn't support it
                    supportsExclusion = sourceCapabilities.supportsGenreExclusion
                )
            }
            filters.add(GenreGroupFilter("Genres", genreFilters))
        }
        
        // Add content warnings group if source supports them
        val supportedWarnings = sourceCapabilities.supportedContentWarnings
        if (supportedWarnings.isNotEmpty()) {
            val availableWarnings = ContentWarning.entries.filter { warning ->
                supportedWarnings.contains(warning.queryValue)
            }
            
            if (availableWarnings.isNotEmpty()) {
                val warningFilters = availableWarnings.map { warning ->
                    ContentWarningFilter(
                        warning.displayName, 
                        currentState.includedContentWarnings.contains(warning), 
                        currentState.excludedContentWarnings.contains(warning),
                        supportsExclusion = sourceCapabilities.supportsContentWarningExclusion
                    )
                }
                filters.add(ContentWarningGroupFilter("Content Warnings", warningFilters))
            }
        }
        
        return FilterList(filters)
    }

    private fun createMoreFilters(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        
        // Status filter - only show if source supports it
        val supportedStatuses = sourceCapabilities.supportedStatuses
        if (supportedStatuses.isNotEmpty()) {
            val availableStatuses = NovelStatus.entries.filter { status ->
                status == NovelStatus.ANY || supportedStatuses.contains(status.queryValue)
            }
            val statusOptions = availableStatuses.map { it.displayName }.toTypedArray()
            val currentStatusIndex = availableStatuses.indexOf(currentState.status).coerceAtLeast(0)
            filters.add(StatusFilter("Status", statusOptions, currentStatusIndex))
        }
        
        // Content Rating filter - show if source has rating support in capabilities
        // For now, we'll show it if there are no specific capabilities (fallback behavior)
        val showContentRating = sourceCapabilities.supportedGenres.isEmpty() || 
            sourceCapabilities.supportedSorts.isEmpty()
        
        if (showContentRating) {
            val contentRatingOptions = ContentRating.entries.map { it.displayName }.toTypedArray()
            val currentRatingIndex = ContentRating.entries.indexOf(currentState.contentRating)
            filters.add(ContentRatingFilter("Content Rating", contentRatingOptions, currentRatingIndex))
            
            val violenceOptions = ViolenceLevel.entries.map { it.displayName }.toTypedArray()
            val currentViolenceIndex = ViolenceLevel.entries.indexOf(currentState.violenceLevel)
            filters.add(ViolenceLevelFilter("Violence Level", violenceOptions, currentViolenceIndex))
            
            val languageOptions = LanguageLevel.entries.map { it.displayName }.toTypedArray()
            val currentLanguageIndex = LanguageLevel.entries.indexOf(currentState.languageLevel)
            filters.add(LanguageLevelFilter("Profanity Level", languageOptions, currentLanguageIndex))
        }
        
        return FilterList(filters)
    }

    // Custom filter classes
    class SortFilter(name: String, values: Array<String>, state: Selection? = null) :
        Filter.Sort(name, values, state ?: Selection(0, false))

    class GenreFilter(
        name: String, 
        include: Boolean = false, 
        exclude: Boolean = false,
        private val supportsExclusion: Boolean = true
    ) : Filter.TriState(name, when {
            // If exclusion not supported, only allow include or ignore
            exclude && supportsExclusion -> STATE_EXCLUDE
            include -> STATE_INCLUDE
            else -> STATE_IGNORE
        })
    
    class ContentWarningFilter(
        name: String, 
        include: Boolean = false, 
        exclude: Boolean = false,
        private val supportsExclusion: Boolean = true
    ) : Filter.TriState(name, when {
            exclude && supportsExclusion -> STATE_EXCLUDE
            include -> STATE_INCLUDE
            else -> STATE_IGNORE
        })

    class GenreGroupFilter(name: String, genres: List<GenreFilter>) :
        Filter.Group<GenreFilter>(name, genres)
    
    class ContentWarningGroupFilter(name: String, warnings: List<ContentWarningFilter>) :
        Filter.Group<ContentWarningFilter>(name, warnings)

    class StatusFilter(name: String, values: Array<String>, state: Int = 0) :
        Filter.Select<String>(name, values, state)

    class ContentRatingFilter(name: String, values: Array<String>, state: Int = 0) :
        Filter.Select<String>(name, values, state)

    class ViolenceLevelFilter(name: String, values: Array<String>, state: Int = 0) :
        Filter.Select<String>(name, values, state)

    class LanguageLevelFilter(name: String, values: Array<String>, state: Int = 0) :
        Filter.Select<String>(name, values, state)

    class MinChaptersFilter(name: String, state: String = "") :
        Filter.Text(name, state)

    class MaxChaptersFilter(name: String, state: String = "") :
        Filter.Text(name, state)

    class MinRatingFilter(name: String, state: String = "") :
        Filter.Text(name, state)

    companion object {
        const val TAG = "NovelFilterBottomSheet"
    }
}
