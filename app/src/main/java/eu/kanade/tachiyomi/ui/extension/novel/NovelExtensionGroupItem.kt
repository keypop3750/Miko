package eu.kanade.tachiyomi.ui.extension.novel

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R

/**
 * Item that contains the novel extension group header.
 *
 * @param name The header name.
 * @param size The number of items in the group.
 */
data class NovelExtensionGroupItem(
    val name: String, 
    val size: Int, 
    var canUpdate: Boolean? = null, 
    var installedSorting: Int? = null
) : AbstractHeaderItem<NovelExtensionGroupHolder>() {

    /**
     * Returns the layout resource of this item.
     */
    override fun getLayoutRes(): Int {
        return R.layout.extension_card_header
    }

    /**
     * Creates a new view holder for this item.
     */
    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): NovelExtensionGroupHolder {
        return NovelExtensionGroupHolder(view, adapter)
    }

    /**
     * Binds this item to the given view holder.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: NovelExtensionGroupHolder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        holder.bind(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is NovelExtensionGroupItem) {
            return name == other.name
        }
        return false
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
