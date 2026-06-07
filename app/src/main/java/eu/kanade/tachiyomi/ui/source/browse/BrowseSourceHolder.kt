package eu.kanade.tachiyomi.ui.source.browse

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder

/**
 * Generic class used to hold the displayed data of a manga in the catalogue.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 */
abstract class BrowseSourceHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>) :
    BaseFlexibleViewHolder(view, adapter) {

    /**
     * Method called from [CatalogueAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga to bind.
     */
    abstract fun onSetValues(manga: Manga)

    /**
     * Updates the image for this holder. Useful to update the image when the manga is initialized
     * and the url is now known.
     *
     * @param manga the manga to bind.
     */
    abstract fun setImage(manga: Manga)
    
    /**
     * Override to ensure long-click is properly handled for browse catalog items.
     * FlexibleAdapter's default behavior may not always trigger the listener.
     */
    override fun onLongClick(view: View?): Boolean {
        android.util.Log.d("BrowseSourceHolder", "onLongClick: position=$flexibleAdapterPosition")
        val listener = mAdapter.mItemLongClickListener
        if (listener != null) {
            android.util.Log.d("BrowseSourceHolder", "onLongClick: calling listener")
            listener.onItemLongClick(flexibleAdapterPosition)
            return true
        }
        android.util.Log.d("BrowseSourceHolder", "onLongClick: no listener, calling super")
        return super.onLongClick(view)
    }
}
