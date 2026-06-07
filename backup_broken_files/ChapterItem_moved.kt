package eu.kanade.tachiyomi.ui.novel.chapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import yokai.domain.novel.Novel
import eu.kanade.tachiyomi.ui.novel.details.NovelDetailsAdapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.ui.UiPreferences

class ChapterItem(chapter: Chapter, val novel: Novel) :
    BaseChapterItem<ChapterHolder, AbstractHeaderItem<FlexibleViewHolder>>(chapter) {

    var isLocked = false

    override fun getLayoutRes(): Int {
        return R.layout.novel_chapters_item
    }

    override fun isSelectable(): Boolean {
        return true
    }

    override fun isSwipeable(): Boolean {
        return !isLocked && Injekt.get<UiPreferences>().enableChapterSwipeAction().get()
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): ChapterHolder {
        return ChapterHolder(view, adapter as NovelDetailsAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: ChapterHolder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        holder.bind(this, novel)
    }

    override fun unbindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>?,
        holder: ChapterHolder?,
        position: Int,
    ) {
        super.unbindViewHolder(adapter, holder, position)
        (adapter as NovelDetailsAdapter).controller.dismissPopup(position)
    }
}
