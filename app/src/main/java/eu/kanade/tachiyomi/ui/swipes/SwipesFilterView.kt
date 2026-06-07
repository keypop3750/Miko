package eu.kanade.tachiyomi.ui.swipes

import android.content.Context
import android.util.AttributeSet
import eu.kanade.tachiyomi.databinding.SwipesFilterViewBinding

class SwipesFilterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseSwipesDisplayView<SwipesFilterViewBinding>(context, attrs) {

    override fun inflateBinding() = SwipesFilterViewBinding.bind(this)
    
    override fun initGeneralPreferences() {
        // Will be called after binding is initialized
    }
    
    fun initializeFilters(
        currentFilters: SwipesFilters,
        onFiltersApplied: (SwipesFilters) -> Unit,
        onDismiss: () -> Unit
    ) {
        // Load NSFW setting
        binding.nsfwSwitch.isChecked = !currentFilters.excludeNsfw
        
        // Apply button
        binding.applyButton.setOnClickListener {
            val newFilters = SwipesFilters(
                enabledSourceIds = currentFilters.enabledSourceIds, // Keep source selections from Sources tab
                excludeNsfw = !binding.nsfwSwitch.isChecked,
                includedGenres = emptySet(),
                excludedGenres = emptySet()
            )
            
            onFiltersApplied(newFilters)
            onDismiss()
        }
        
        // Reset button
        binding.resetButton.setOnClickListener {
            binding.nsfwSwitch.isChecked = false // Default: exclude NSFW
        }
        
        // Close button
        binding.closeButton.setOnClickListener {
            onDismiss()
        }
    }
}