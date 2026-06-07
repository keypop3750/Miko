package eu.kanade.tachiyomi.ui.novel.details

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.NovelChaptersItemBinding
import yokai.domain.novelchapter.models.NovelChapter

/**
 * Simple RecyclerView adapter for novel chapters.
 * No FlexibleAdapter complexity - just a straightforward list with optional header.
 * Supports swipe gestures for quick actions (mark read/unread, bookmark).
 */
class NovelChapterAdapter(
    private val onChapterClick: (NovelChapter) -> Unit,
    private val onChapterLongClick: (NovelChapter) -> Unit,
    private val onDownloadClick: ((Int) -> Unit)? = null,  // Download button click callback
    private val onSwipeLeft: ((Int) -> Unit)? = null,  // Swipe left callback (mark read/unread)
    private val onSwipeRight: ((Int) -> Unit)? = null  // Swipe right callback (bookmark)
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var chapters = emptyList<NovelChapter>()
    private var headerView: View? = null
    
    // Cached colors for performance optimization
    private var cachedBookmarkedColor: Int? = null
    private var cachedUnreadColor: Int? = null
    
    var accentColor: Int? = null
        set(value) {
            field = value
            // Pre-calculate themed colors once when accent color changes
            value?.let { color ->
                val r = android.graphics.Color.red(color)
                val g = android.graphics.Color.green(color)
                val b = android.graphics.Color.blue(color)
                cachedBookmarkedColor = android.graphics.Color.argb(20, r, g, b)
                cachedUnreadColor = android.graphics.Color.argb(10, r, g, b)
            } ?: run {
                cachedBookmarkedColor = null
                cachedUnreadColor = null
            }
            // Update all visible chapter items when accent color changes
            val start = if (headerView != null) 1 else 0
            notifyItemRangeChanged(start, chapters.size)
        }
    
    fun setHeaderView(view: View) {
        val hadHeader = headerView != null
        headerView = view
        if (hadHeader) {
            notifyItemChanged(0)
        } else {
            notifyItemInserted(0)
        }
    }

    fun updateChapters(newChapters: List<NovelChapter>) {
        chapters = newChapters
        val startPosition = if (headerView != null) 1 else 0
        notifyItemRangeChanged(startPosition, chapters.size)
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (position == 0 && headerView != null) {
            VIEW_TYPE_HEADER
        } else {
            VIEW_TYPE_CHAPTER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(headerView!!)
        } else {
            val binding = NovelChaptersItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            ChapterViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ChapterViewHolder) {
            val chapterPosition = if (headerView != null) position - 1 else position
            holder.bind(chapters[chapterPosition])
        }
    }

    override fun getItemCount(): Int {
        val headerCount = if (headerView != null) 1 else 0
        return chapters.size + headerCount
    }
    
    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CHAPTER = 1

        /**
         * Clean chapter titles by removing HTML syntax artifacts that may leak from
         * extension parsers (e.g. </option>, <select>, &nbsp;).
         * Preserves legitimate chapter prefixes like C.1, E1, S1, Chapter, etc.
         */
        fun cleanChapterTitle(title: String): String {
            if (title.isBlank()) return title

            // Remove HTML tags including variants with backslash escapes like <\/option>
            val noTags = title.replace(Regex("""<[^>]+>"""), "")

            // Remove common HTML entities
            val noEntities = noTags.replace(Regex("""&[a-zA-Z#][a-zA-Z0-9]*;"""), "")

            // Collapse multiple whitespace into single space and trim
            return noEntities.replace(Regex("""\s+"""), " ").trim()
        }
    }

    inner class ChapterViewHolder(
        val binding: NovelChaptersItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val chapterPosition = if (headerView != null) position - 1 else position
                    if (chapterPosition >= 0 && chapterPosition < chapters.size) {
                        onChapterClick(chapters[chapterPosition])
                    }
                }
            }
            
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val chapterPosition = if (headerView != null) position - 1 else position
                    if (chapterPosition >= 0 && chapterPosition < chapters.size) {
                        onChapterLongClick(chapters[chapterPosition])
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            
            // Download button click handler
            binding.downloadButton.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val chapterPosition = if (headerView != null) position - 1 else position
                    if (chapterPosition >= 0 && chapterPosition < chapters.size) {
                        onDownloadClick?.invoke(chapterPosition)
                    }
                }
            }
        }

        fun bind(chapter: NovelChapter) {
            binding.chapterTitle.text = cleanChapterTitle(chapter.title)
            
            // Use scanlator field for chapter info
            val info = buildString {
                if (chapter.chapterNumber >= 0) {
                    append("Ch. ${chapter.chapterNumber}")
                }
                if (chapter.wordCount > 0) {
                    if (isNotEmpty()) append(" • ")
                    append("${chapter.wordCount} words")
                }
                if (chapter.read) {
                    if (isNotEmpty()) append(" • ")
                    append("Read")
                } else if (chapter.lastReadPosition > 0 && chapter.wordCount > 0) {
                    // Show percentage progress for partially-read chapters.
                    // lastReadPosition is in characters; approximate total chars from wordCount.
                    if (isNotEmpty()) append(" • ")
                    val estimatedTotalChars = chapter.wordCount * 6 // ~5 chars + 1 space per word
                    val pct = ((chapter.lastReadPosition.toDouble() / estimatedTotalChars) * 100)
                        .toInt()
                        .coerceIn(1, 99)
                    append("${pct}%")
                }
            }
            
            if (info.isNotEmpty()) {
                binding.chapterScanlator.text = info
                binding.chapterScanlator.isVisible = true
            } else {
                binding.chapterScanlator.isVisible = false
            }
            
            // Visual indicators for read status
            binding.chapterTitle.alpha = if (chapter.read) 0.5f else 1.0f
            
            // Update swipe icons based on chapter status (like manga)
            binding.read.setImageResource(
                if (chapter.read) R.drawable.ic_eye_off_24dp else R.drawable.ic_eye_24dp
            )
            binding.bookmark.setImageResource(
                if (chapter.bookmark) R.drawable.ic_bookmark_off_24dp else R.drawable.ic_bookmark_24dp
            )
            
            // Apply subtle background tinting based on chapter status for better visual feedback
            applyChapterStatusTheming(chapter)
            
            // Apply accent color to download indicator if available
            accentColor?.let { color ->
                binding.downloadButton.downloadIcon.imageTintList = ColorStateList.valueOf(color)
                binding.downloadButton.downloadBorder.imageTintList = ColorStateList.valueOf(color)
            }
            
            // Show download button (TODO: implement actual download functionality)
            binding.downloadButton.root.isVisible = true
            // Set to default download state for now
            binding.downloadButton.downloadBorder.isVisible = true
            binding.downloadButton.downloadIcon.isVisible = true
            binding.downloadButton.downloadProgress.isVisible = false
            binding.downloadButton.downloadProgressIndeterminate.isVisible = false
        }
        
        /**
         * Applies subtle background tinting to chapter items based on their status.
         * Provides visual feedback for bookmarked and unread chapters.
         * Optimized: Uses pre-calculated cached colors to avoid expensive Color.argb() calls.
         */
        private fun applyChapterStatusTheming(chapter: NovelChapter) {
            val backgroundColor = when {
                chapter.bookmark -> cachedBookmarkedColor
                !chapter.read -> cachedUnreadColor
                else -> null
            }
            
            backgroundColor?.let {
                binding.root.setBackgroundColor(it)
            } ?: run {
                // Reset to default selectable background by resolving the theme attribute
                val typedValue = android.util.TypedValue()
                if (binding.root.context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground,
                    typedValue,
                    true
                )) {
                    // Use the resolved drawable directly, not the resource ID
                    binding.root.background = binding.root.context.getDrawable(typedValue.resourceId)
                } else {
                    // Fallback: clear background if attribute not resolved
                    binding.root.background = null
                }
            }
        }
    }
    
    /**
     * Creates an ItemTouchHelper for swipe gestures on chapter items.
     * Swipe right: Bookmark/unbookmark chapter
     * Swipe left: Toggle read/unread status
     */
    fun createSwipeHelper(): ItemTouchHelper {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0, // No drag support
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // Swipe left and right
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                
                // Adjust position if header exists
                val adjustedPosition = if (headerView != null) position - 1 else position
                if (adjustedPosition < 0 || adjustedPosition >= chapters.size) return
                
                when (direction) {
                    ItemTouchHelper.RIGHT -> onSwipeRight?.invoke(adjustedPosition)
                    ItemTouchHelper.LEFT -> onSwipeLeft?.invoke(adjustedPosition)
                }
                
                // Notify adapter to restore the item view after swipe
                notifyItemChanged(position)
            }
            
            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (viewHolder is ChapterViewHolder) {
                    val binding = viewHolder.binding
                    
                    // Show appropriate background based on swipe direction
                    if (dX > 0) {
                        // Swiping right - show bookmark background
                        binding.startView.isVisible = true
                        binding.endView.isVisible = false
                    } else if (dX < 0) {
                        // Swiping left - show read/unread background
                        binding.startView.isVisible = false
                        binding.endView.isVisible = true
                    } else {
                        // No swipe - hide both
                        binding.startView.isVisible = false
                        binding.endView.isVisible = false
                    }
                    
                    // Move the front view with the swipe
                    binding.frontView.translationX = dX
                } else {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                }
            }
            
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (viewHolder is ChapterViewHolder) {
                    val binding = viewHolder.binding
                    // Animate the front view back to position
                    binding.frontView.animate()
                        .translationX(0f)
                        .setDuration(150)
                        .withEndAction {
                            // Hide background views after animation completes
                            binding.startView.isVisible = false
                            binding.endView.isVisible = false
                        }
                        .start()
                }
            }
            
            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                // Disable swipe on header
                if (viewHolder is HeaderViewHolder) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
            }
        }
        
        return ItemTouchHelper(callback)
    }
}
