package eu.kanade.tachiyomi.ui.swipes

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SourceGroupFilterItemBinding
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder

/**
 * Item representing a source group (e.g., "Comick" with multiple language variants) in the filter dialog
 * Provides toggle functionality for enabling/disabling source groups
 */
class SourceGroupFilterItem(
    val sourceGroup: SwipesRepository.SourceGroup,
    var sourceEnabled: Boolean = true
) : AbstractFlexibleItem<SourceGroupFilterItem.ViewHolder>() {

    interface OnToggleListener {
        fun onToggleChanged(item: SourceGroupFilterItem, enabled: Boolean)
    }
    
    var toggleListener: OnToggleListener? = null

    override fun getLayoutRes() = R.layout.source_group_filter_item

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
    ): ViewHolder {
        return ViewHolder(view, adapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: ViewHolder,
        position: Int,
        payloads: List<Any?>?
    ) {
        holder.bind(sourceGroup, sourceEnabled)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is SourceGroupFilterItem) {
            return sourceGroup.id == other.sourceGroup.id
        }
        return false
    }

    override fun hashCode(): Int {
        return sourceGroup.id.hashCode()
    }

    class ViewHolder(view: View, adapter: FlexibleAdapter<*>) : 
        BaseFlexibleViewHolder(view, adapter) {
        
        private val binding = SourceGroupFilterItemBinding.bind(view)

        fun bind(sourceGroup: SwipesRepository.SourceGroup, sourceEnabled: Boolean) {
            // Use primary source for display
            binding.sourceIcon.load(sourceGroup.primarySource.icon())
            binding.sourceName.text = sourceGroup.name
            
            // Clear listener to avoid triggering during bind
            binding.sourceToggle.setOnCheckedChangeListener(null)
            binding.sourceToggle.isChecked = sourceEnabled
            
            // Handle toggle clicks separately from row clicks
            binding.sourceToggle.setOnCheckedChangeListener { _, isChecked ->
                // Update the item state and notify listener
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = mAdapter?.getItem(position) as? SourceGroupFilterItem
                    if (item != null) {
                        item.sourceEnabled = isChecked
                        item.toggleListener?.onToggleChanged(item, isChecked)
                    }
                }
            }
        }
    }
}