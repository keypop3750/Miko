package eu.kanade.tachiyomi.ui.swipes

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.SwipesSourcesViewBinding

class SwipesSourcesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseSwipesDisplayView<SwipesSourcesViewBinding>(context, attrs),
    FlexibleAdapter.OnItemClickListener,
    SourceGroupFilterItem.OnToggleListener {

    override fun inflateBinding() = SwipesSourcesViewBinding.bind(this)
    
    override fun initGeneralPreferences() {
        // Will be called after binding is initialized
    }
    private var adapter: FlexibleAdapter<SourceGroupFilterItem>? = null
    private var availableSourceGroups: List<SwipesRepository.SourceGroup> = emptyList()
    private var onFiltersApplied: ((SwipesFilters) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null
    private var currentFilters: SwipesFilters = SwipesFilters()
    
    fun initializeSourceGroups(
        sourceGroups: List<SwipesRepository.SourceGroup>,
        filters: SwipesFilters,
        onApply: (SwipesFilters) -> Unit,
        dismiss: () -> Unit
    ) {
        availableSourceGroups = sourceGroups
        currentFilters = filters
        onFiltersApplied = onApply
        onDismiss = dismiss
        
        val sourceItems = sourceGroups.map { sourceGroup ->
            // Check if this source group is enabled
            // A group is enabled if any of its sources are in the enabled set
            val isEnabled = if (filters.enabledSourceIds.isEmpty()) {
                // Empty set means all sources enabled
                true
            } else {
                // Group is enabled if any of its sources are enabled
                sourceGroup.sources.any { source -> source.id in filters.enabledSourceIds }
            }
            SourceGroupFilterItem(sourceGroup, sourceEnabled = isEnabled).apply {
                toggleListener = this@SwipesSourcesView
            }
        }
        
        adapter = FlexibleAdapter(sourceItems.toMutableList(), this, true)
        
        binding.sourcesRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SwipesSourcesView.adapter
        }
        
        // Apply button
        binding.applyButton.setOnClickListener {
            applySourceFilters()
        }
        
        // Reset button
        binding.resetButton.setOnClickListener {
            resetSources()
        }
        
        // Close button
        binding.closeButton.setOnClickListener {
            onDismiss?.invoke()
        }
        
    }
    
    @Deprecated("Use initializeSourceGroups instead")
    fun initializeSources(
        sources: List<eu.kanade.tachiyomi.source.CatalogueSource>,
        filters: SwipesFilters,
        onApply: (SwipesFilters) -> Unit,
        dismiss: () -> Unit
    ) {
        // Convert individual sources to mock source groups for backward compatibility
        val mockSourceGroups = sources.map { source ->
            SwipesRepository.SourceGroup(
                name = source.name,
                sources = listOf(source),
                primarySource = source
            )
        }
        initializeSourceGroups(mockSourceGroups, filters, onApply, dismiss)
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        // No row click functionality needed - sources only have toggle behavior
        return false // Don't handle click, let other handlers take care of it
    }

    override fun onToggleChanged(item: SourceGroupFilterItem, enabled: Boolean) {
        // Toggle state has been updated, no additional action needed
        // The filter application logic will read the current state from items
    }

    private fun applySourceFilters() {
        // Collect enabled source IDs from enabled groups
        val enabledSourceIds = mutableSetOf<Long>()
        
        adapter?.currentItems?.forEach { item ->
            if (item.sourceEnabled) {
                // Enable all sources in the group
                item.sourceGroup.sources.forEach { source ->
                    enabledSourceIds.add(source.id)
                }
            }
        }
        
        val finalEnabledSourceIds = if (enabledSourceIds.isEmpty()) {
            // No sources enabled = all sources enabled
            emptySet()
        } else if (enabledSourceIds.size == availableSourceGroups.sumOf { it.sources.size }) {
            // All sources enabled = all sources enabled (default state)
            emptySet()
        } else {
            enabledSourceIds
        }
        
        val newFilters = SwipesFilters(
            enabledSourceIds = finalEnabledSourceIds,
            excludeNsfw = currentFilters.excludeNsfw, // Keep NSFW setting from Filter tab
            includedGenres = emptySet(),
            excludedGenres = emptySet()
        )
        
        onFiltersApplied?.invoke(newFilters)
        onDismiss?.invoke()
    }

    private fun resetSources() {
        // Enable all source groups and update toggles
        adapter?.currentItems?.forEach { item ->
            item.sourceEnabled = true
        }
        adapter?.notifyDataSetChanged()
    }
}