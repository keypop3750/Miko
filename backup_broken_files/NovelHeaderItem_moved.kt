package eu.kanade.tachiyomi.ui.novel.details

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.novel.NovelHeaderHolder

class NovelHeaderItem(val novelId: Long, private var startExpanded: Boolean) :
    AbstractFlexibleItem<NovelHeaderHolder>() {

    var isChapterHeader = false
    var isLocked = false
    var isTablet = false

    override fun getLayoutRes(): Int {
        return if (isChapterHeader) R.layout.novel_chapter_header_item else R.layout.novel_header_item
    }

    override fun isSelectable(): Boolean {
        return false
    }

    override fun isSwipeable(): Boolean {
        return false
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): NovelHeaderHolder {
        return NovelHeaderHolder(view, adapter as NovelDetailsAdapter, startExpanded, isTablet)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: NovelHeaderHolder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        if (isChapterHeader) {
            holder.bindChapters()
        } else {
            holder.bind(this)
        }
    }

    override fun equals(other: Any?): Boolean {
        return (this === other)
    }

    override fun hashCode(): Int {
        return -(novelId).hashCode()
    }
}
