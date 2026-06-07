package eu.kanade.tachiyomi.ui.swipes.history

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SwipesHistoryItemBinding

/**
 * RecyclerView adapter for displaying swipe history items
 * Phase 4.2: History Feature - Simple view-only history
 */
class SwipesHistoryAdapter(
    private val onItemClick: (SwipesHistoryItem) -> Unit
) : ListAdapter<SwipesHistoryItem, SwipesHistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = SwipesHistoryItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(
        private val binding: SwipesHistoryItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SwipesHistoryItem) {
            // Load manga cover
            binding.mangaCover.load(item.coverUrl)

            // Set swipe direction icon
            if (item.swipedRight) {
                binding.swipeDirectionIcon.setImageResource(R.drawable.ic_arrow_forward_24dp)
                binding.swipeDirectionIcon.contentDescription = "Added to library"
            } else {
                binding.swipeDirectionIcon.setImageResource(R.drawable.ic_arrow_back_24dp)
                binding.swipeDirectionIcon.contentDescription = "Rejected"
            }

            // Set manga info
            binding.mangaTitle.text = item.title
            
            // Format author same as card view
            val hasValidAuthor = item.author.isNotEmpty() && 
                                item.author.isNotBlank() && 
                                item.author != "_"
            binding.mangaAuthor.text = if (hasValidAuthor) item.author else "Unknown"

            // Format timestamp as relative time
            binding.timestamp.text = DateUtils.getRelativeTimeSpanString(
                item.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )

            // Click listener for whole item
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    private class HistoryDiffCallback : DiffUtil.ItemCallback<SwipesHistoryItem>() {
        override fun areItemsTheSame(
            oldItem: SwipesHistoryItem,
            newItem: SwipesHistoryItem
        ): Boolean = oldItem.url == newItem.url

        override fun areContentsTheSame(
            oldItem: SwipesHistoryItem,
            newItem: SwipesHistoryItem
        ): Boolean = oldItem == newItem
    }
}
