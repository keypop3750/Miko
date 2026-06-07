package eu.kanade.tachiyomi.ui.novel.reader

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import yokai.source.novel.model.NovelComment

/**
 * RecyclerView adapter for displaying novel chapter comments.
 */
class NovelCommentsAdapter : ListAdapter<NovelComment, NovelCommentsAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_novel_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val userView: TextView = view.findViewById(R.id.comment_user)
        private val likesView: TextView = view.findViewById(R.id.comment_likes)
        private val contentView: TextView = view.findViewById(R.id.comment_content)

        fun bind(comment: NovelComment) {
            userView.text = comment.userName
            likesView.text = "${comment.likes} likes"

            // Replace image/gif placeholders with *gif*/*image* as requested
            val cleanedContent = comment.content
                .replace(Regex("<img[^>]*>", RegexOption.IGNORE_CASE), " *image* ")
                .replace(Regex("<gif[^>]*>", RegexOption.IGNORE_CASE), " *gif* ")
                .replace(Regex("<video[^>]*>.*?</video>", RegexOption.IGNORE_CASE), " *video* ")

            // Parse simple HTML
            val spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(cleanedContent, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(cleanedContent)
            }
            contentView.text = spanned
        }
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<NovelComment>() {
        override fun areItemsTheSame(oldItem: NovelComment, newItem: NovelComment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NovelComment, newItem: NovelComment): Boolean {
            return oldItem == newItem
        }
    }
}
