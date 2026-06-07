package eu.kanade.tachiyomi.ui.swipes

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.databinding.SwipesFilterSheetBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.widget.E2EBottomSheetDialog

/**
 * Bottom sheet dialog for configuring Swipes filters
 * 
 * Allows users to:
 * - Toggle sources on/off
 * - Toggle NSFW content inclusion
 */
class SwipesFilterSheet(
    private val activity: Activity,
    private val availableSources: List<CatalogueSource>,
    private val currentFilters: SwipesFilters,
    private val onFiltersApplied: (SwipesFilters) -> Unit
) : E2EBottomSheetDialog<SwipesFilterSheetBinding>(activity),
    FlexibleAdapter.OnItemClickListener {

    private var adapter: FlexibleAdapter<SourceFilterItem>? = null
    
    init {
        setupUI()
        loadCurrentFilters()
    }

    override fun createBinding(inflater: LayoutInflater) = SwipesFilterSheetBinding.inflate(inflater)

    private fun setupUI() {
        val sourceItems = availableSources.map { source ->
            val isEnabled = currentFilters.isSourceEnabled(source.id)
            SourceFilterItem(source, sourceEnabled = isEnabled)
        }
        
        adapter = FlexibleAdapter(sourceItems, this, true)
        
        binding.sourcesRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SwipesFilterSheet.adapter
        }
        
        // NSFW toggle
        binding.nsfwSwitch.setOnCheckedChangeListener { _, _ ->
            // Update will happen when Apply is clicked
        }
        
        // Apply button
        binding.applyButton.setOnClickListener {
            applyFilters()
            dismiss()
        }
        
        // Reset button
        binding.resetButton.setOnClickListener {
            resetFilters()
        }
        
        // Close button
        binding.closeButton.setOnClickListener {
            dismiss()
        }
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        adapter?.getItem(position)?.let { item ->
            item.sourceEnabled = !item.sourceEnabled
        }
        adapter?.notifyItemChanged(position)
        updateSourceCount()
        return false
    }

    private fun loadCurrentFilters() {
        // Load NSFW setting
        binding.nsfwSwitch.isChecked = !currentFilters.excludeNsfw
        
        // Update source count
        updateSourceCount()
    }

    private fun applyFilters() {
        val enabledSources = adapter?.currentItems
            ?.filter { it.sourceEnabled }
            ?.map { it.source.id } ?: emptyList()
        
        val enabledSourceIds = if (enabledSources.isEmpty()) {
            // No sources enabled = all sources enabled
            emptySet()
        } else if (enabledSources.size == availableSources.size) {
            // All sources enabled = all sources enabled (default state)
            emptySet()
        } else {
            enabledSources.toSet()
        }
        
        val newFilters = SwipesFilters(
            enabledSourceIds = enabledSourceIds,
            excludeNsfw = !binding.nsfwSwitch.isChecked,
            includedGenres = emptySet(),
            excludedGenres = emptySet()
        )
        
        onFiltersApplied(newFilters)
    }

    private fun resetFilters() {
        // Enable all sources
        adapter?.currentItems?.forEach { it.sourceEnabled = true }
        adapter?.notifyDataSetChanged()
        
        // Reset NSFW to default (exclude)
        binding.nsfwSwitch.isChecked = false
        
        updateSourceCount()
    }

    private fun updateSourceCount() {
        val enabledCount = adapter?.currentItems?.count { it.sourceEnabled } ?: 0
        val totalCount = availableSources.size
        
        binding.sourceCountText.text = when {
            enabledCount == 0 -> "No sources selected"
            enabledCount == totalCount -> "All sources ($totalCount)"
            else -> "$enabledCount of $totalCount sources"
        }
    }
}
