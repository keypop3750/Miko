package eu.kanade.tachiyomi.ui.swipes

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SourceFilterItemBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder

/**
 * Item representing a source in the filter dialog
 */
class SourceFilterItem(
    val source: CatalogueSource,
    var sourceEnabled: Boolean = true
) : AbstractFlexibleItem<SourceFilterItem.ViewHolder>() {

    override fun getLayoutRes() = R.layout.source_filter_item

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
        holder.bind(source, sourceEnabled)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is SourceFilterItem) {
            return source.id == other.source.id
        }
        return false
    }

    override fun hashCode(): Int {
        return source.id.hashCode()
    }

    class ViewHolder(view: View, adapter: FlexibleAdapter<*>) : 
        BaseFlexibleViewHolder(view, adapter) {
        
        private val binding = SourceFilterItemBinding.bind(view)

        fun bind(source: CatalogueSource, sourceEnabled: Boolean) {
            binding.sourceIcon.load(source.icon())
            binding.sourceName.text = source.name
            binding.sourceCheckbox.isChecked = sourceEnabled
        }
    }
}
