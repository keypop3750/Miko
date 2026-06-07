package eu.kanade.tachiyomi.ui.novel.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import eu.kanade.tachiyomi.R
import yokai.domain.novel.Novel

// Temporary binding class until view binding generates the proper one
class NovelLibraryItemBinding(val view: View) : ViewBinding {
    override fun getRoot(): View = view
    val novelTitle: TextView = view.findViewById(R.id.novel_title)
    val novelAuthor: TextView = view.findViewById(R.id.novel_author)
    val novelInfo: TextView = view.findViewById(R.id.novel_info)
    val novelProgress: ProgressBar = view.findViewById(R.id.novel_progress)
    val novelProgressText: TextView = view.findViewById(R.id.novel_progress_text)
    val unreadIndicator: View = view.findViewById(R.id.unread_indicator)
    val favoriteIndicator: ImageView = view.findViewById(R.id.favorite_indicator)
    val lastUpdate: TextView = view.findViewById(R.id.last_update)
    val novelCover: ImageView = view.findViewById(R.id.novel_cover)
}

/**
 * Novel-specific library adapter completely separate from manga library adapter
 * Prevents type confusion and provides novel-optimized display
 */
class NovelLibraryAdapter(
    private val onItemClick: (Novel) -> Unit,
    private val onItemLongClick: (Novel) -> Boolean = { false }
) : ListAdapter<Novel, NovelLibraryAdapter.NovelViewHolder>(NovelDiffCallback()) {
    
    fun updateNovels(novels: List<Novel>) {
        submitList(novels)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NovelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.novel_library_item, parent, false)
        val binding = NovelLibraryItemBinding(view)
        return NovelViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: NovelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class NovelViewHolder(
        private val binding: NovelLibraryItemBinding
    ) : RecyclerView.ViewHolder(binding.getRoot()) {
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClick(getItem(position))
                } else {
                    false
                }
            }
        }
        
        fun bind(novel: Novel) {
            binding.apply {
                // Novel title
                novelTitle.text = novel.title
                
                // Author
                novelAuthor.text = novel.author ?: "Unknown Author"
                
                // Chapter count and status
                val chapterText = "${novel.chapterCount ?: 0} chapters"
                val statusText = getStatusText(novel.status)
                novelInfo.text = "$chapterText • $statusText"
                
                // Reading progress
                val progress = novel.getProgress()
                if (progress > 0f) {
                    novelProgress.progress = (progress * 100).toInt()
                    novelProgress.isVisible = true
                    novelProgressText.text = "${(progress * 100).toInt()}%"
                    novelProgressText.isVisible = true
                } else {
                    novelProgress.isVisible = false
                    novelProgressText.isVisible = false
                }
                
                // Unread indicator
                val hasUnread = !novel.isCompleted()
                unreadIndicator.isVisible = hasUnread
                
                // Favorite indicator
                favoriteIndicator.isVisible = novel.isFavorite
                
                // Cover image
                loadCoverImage(novel.posterUrl)
                
                // Last update info
                if (novel.lastUpdate > 0) {
                    val daysSinceUpdate = (System.currentTimeMillis() - novel.lastUpdate) / (1000 * 60 * 60 * 24)
                    lastUpdate.text = when {
                        daysSinceUpdate < 1 -> "Updated today"
                        daysSinceUpdate < 7 -> "${daysSinceUpdate}d ago"
                        daysSinceUpdate < 30 -> "${daysSinceUpdate / 7}w ago"
                        else -> "${daysSinceUpdate / 30}mo ago"
                    }
                    lastUpdate.isVisible = true
                } else {
                    lastUpdate.isVisible = false
                }
            }
        }
        
        private fun loadCoverImage(coverUrl: String?) {
            // TODO: Use Coil to load cover image
            // This will be implemented when we integrate with image loading
            binding.novelCover.setImageResource(R.drawable.default_novel_cover)
        }
        
        private fun getStatusText(status: Int): String {
            return when (status) {
                0 -> "Unknown"
                1 -> "Ongoing"
                2 -> "Completed"
                3 -> "Hiatus"
                4 -> "Cancelled"
                else -> "Unknown"
            }
        }
    }
    
    private class NovelDiffCallback : DiffUtil.ItemCallback<Novel>() {
        override fun areItemsTheSame(oldItem: Novel, newItem: Novel): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Novel, newItem: Novel): Boolean {
            return oldItem == newItem
        }
    }
}