package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.databinding.MangaGridItemBinding
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import yokai.domain.novel.Novel

class LibraryNovelItem(
    val novel: Novel,
    header: LibraryHeaderItem,
    context: Context?,
) : LibraryItem(header, context) {

    var downloadCount = 0
    var unreadCount = 0
    var language: String = ""
    var lastReadChapter: String? = null
    var totalChapters: Long = 0

    override fun getLayoutRes(): Int {
        // Reuse manga layouts for novels (same visual structure)
        return if (libraryLayout == LAYOUT_LIST) {
            R.layout.manga_list_item
        } else {
            R.layout.manga_grid_item
        }
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): LibraryHolder {
        // Return list holder for list layout
        val listHolder by lazy { LibraryListHolder(view, adapter as LibraryCategoryAdapter) }
        val parent = adapter.recyclerView
        if (parent !is AutofitRecyclerView) return listHolder

        val libraryLayout = libraryLayout
        val isFixedSize = uniformSize

        if (libraryLayout == LAYOUT_LIST) { return listHolder }

        // Set up grid view with proper aspect ratio (matching manga behavior)
        view.apply {
            val binding = MangaGridItemBinding.bind(this)
            
            // Set 2:3 aspect ratio for novel covers (matching manga)
            if (isFixedSize) {
                binding.constraintLayout.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                binding.coverThumbnail.maxHeight = Int.MAX_VALUE
                binding.coverThumbnail.minimumHeight = 0
                binding.constraintLayout.minHeight = 0
                binding.coverThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                binding.coverThumbnail.adjustViewBounds = false
                binding.coverThumbnail.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                    this.dimensionRatio = "2:3"
                }
            }
        }
        
        // Grid holder now supports both manga and novels
        return LibraryGridHolder(
            view,
            adapter as LibraryCategoryAdapter,
            compact = libraryLayout == LAYOUT_COMPACT_GRID,
            fixedSize = isFixedSize
        )
    }

    override fun filter(constraint: String): Boolean {
        filter = constraint
        if (constraint.isEmpty()) return false
        return novel.title.contains(constraint, ignoreCase = true) ||
            novel.author?.contains(constraint, ignoreCase = true) == true
    }

    /**
     * Returns true if this item is draggable.
     * Only allow dragging when the category is in DragAndDrop sort mode.
     */
    override fun isDraggable(): Boolean {
        return header.category.isDragAndDrop
    }

    override fun isEnabled(): Boolean {
        return true
    }

    override fun isSelectable(): Boolean {
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LibraryNovelItem) return false
        return novel.id == other.novel.id
    }

    override fun hashCode(): Int {
        return novel.id.toInt()
    }
}
