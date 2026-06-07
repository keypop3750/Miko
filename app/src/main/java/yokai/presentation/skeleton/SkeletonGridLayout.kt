package yokai.presentation.skeleton

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.touchlab.kermit.Logger

private val logger = Logger.withTag("SkeletonGridLayout")

/**
 * Container for skeleton placeholders that mimics a RecyclerView grid.
 * Shows exactly 12 skeleton items (3×4 grid) with slow shimmer animation.
 */
class SkeletonGridLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val recyclerView: RecyclerView
    private var skeletonAdapter: SkeletonAdapter? = null
    private var isGridMode: Boolean = true
    
    companion object {
        private const val TAG = "SkeletonGridLayout"
    }
    
    init {
        recyclerView = RecyclerView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            overScrollMode = View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = false
            clipToPadding = false // Allow skeleton items to draw under app bar like catalogue
        }
        addView(recyclerView)
        Log.d(TAG, "💀 [INIT] SkeletonGridLayout created, internal RecyclerView added")
    }
    
    /**
     * Show skeleton grid with specified number of columns.
     * 
     * @param columns Number of columns for grid layout
     * @param isGrid True for grid layout, false for list layout
     * @param topInset Top padding in pixels to offset skeleton below app bar (optional)
     */
    fun show(columns: Int = 3, isGrid: Boolean = true, topInset: Int = 0) {
        Log.d(TAG, "💀 [SHOW] CALLED - columns: $columns, isGrid: $isGrid, topInset: $topInset")
        Log.d(TAG, "💀 [SHOW] BEFORE - visibility: $visibility, alpha: $alpha, childCount: $childCount")
        
        isGridMode = isGrid
        
        val layoutManager = if (isGrid) {
            GridLayoutManager(context, columns)
        } else {
            LinearLayoutManager(context)
        }
        
        recyclerView.layoutManager = layoutManager
        
        // 🔍 CAUSE #3: Track adapter creation/recreation
        val hadAdapter = skeletonAdapter != null
        logger.w { "⚠️ [VIEW-RECYCLING] show() called - existing adapter: $hadAdapter, visibility: $visibility" }
        
        skeletonAdapter = SkeletonAdapter(isGrid).also {
            recyclerView.adapter = it
            Log.d(TAG, "💀 [SHOW] Adapter set with ${it.itemCount} items")
        }
        
        // Apply top inset to position skeleton below app bar (matches content RecyclerView behavior)
        if (topInset > 0) {
            recyclerView.setPadding(0, topInset, 0, 0)
            Log.d(TAG, "💀 [SHOW] Applied top padding: $topInset")
        }
        
        visibility = View.VISIBLE
        alpha = 1f
        
        Log.d(TAG, "💀 [SHOW] AFTER - visibility: $visibility, alpha: $alpha")
        Log.d(TAG, "💀 [SHOW] RecyclerView state - visibility: ${recyclerView.visibility}, alpha: ${recyclerView.alpha}, paddingTop: ${recyclerView.paddingTop}")
        
        // Force immediate layout so ViewHolders are available for fade animation
        post {
            recyclerView.requestLayout()
            Log.d(TAG, "💀 [SHOW] requestLayout() posted")
        }
    }
    
    /**
     * Hide skeleton grid immediately.
     */
    fun hide() {
        Log.d(TAG, "💀 [HIDE] CALLED - current visibility: $visibility")
        
        // 🔍 CAUSE #3: Track cleanup
        val itemCount = skeletonAdapter?.itemCount ?: 0
        logger.w { "⚠️ [VIEW-RECYCLING] hide() called - adapter items: $itemCount, visibility: $visibility" }
        
        visibility = View.GONE
        // No shimmer to stop - skeleton is just empty container
        skeletonAdapter = null
        Log.d(TAG, "💀 [HIDE] COMPLETE - visibility now: $visibility, adapter cleared")
        
        logger.w { "⚠️ [VIEW-RECYCLING] Adapter destroyed, visibility now: $visibility" }
    }
    
    /**
     * Fade out skeleton grid with animation.
     */
    fun fadeOut(duration: Long = 150, onComplete: (() -> Unit)? = null) {
        Log.d(TAG, "💀 [FADE] Starting fadeOut animation - duration: $duration, current alpha: $alpha")
        animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                Log.d(TAG, "💀 [FADE] Animation complete, calling hide()")
                hide()
                onComplete?.invoke()
            }
            .start()
    }
    
    /**
     * Get view holder at position for synchronized fade animation.
     */
    fun getViewHolderAt(position: Int): SkeletonViewHolder? {
        return recyclerView.findViewHolderForAdapterPosition(position) as? SkeletonViewHolder
    }
    
    /**
     * Get total number of skeleton items shown.
     */
    fun getItemCount(): Int = skeletonAdapter?.itemCount ?: 0
    
    private class SkeletonAdapter(private val isGrid: Boolean) : RecyclerView.Adapter<SkeletonViewHolder>() {
        
        private val viewHolders = mutableListOf<SkeletonViewHolder>()
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkeletonViewHolder {
            return if (isGrid) {
                // Create SkeletonMangaCardView directly (it handles its own layout inflation)
                val skeletonView = SkeletonMangaCardView(parent.context)
                skeletonView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                SkeletonViewHolder.GridViewHolder(skeletonView)
            } else {
                // Create SkeletonMangaListView directly (it handles its own layout inflation)
                val skeletonView = SkeletonMangaListView(parent.context)
                skeletonView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                SkeletonViewHolder.ListViewHolder(skeletonView)
            }
        }
        
        override fun onBindViewHolder(holder: SkeletonViewHolder, position: Int) {
            // 🔍 CAUSE #3: Track ViewHolder reuse
            val wasInList = viewHolders.contains(holder)
            if (!viewHolders.contains(holder)) {
                viewHolders.add(holder)
            }
            logger.v { "♻️ [VIEW-RECYCLING] onBindViewHolder pos=$position, ViewHolder reused: $wasInList, total holders: ${viewHolders.size}" }
            // No shimmer to start - skeleton is just empty container
        }
        
        override fun getItemCount(): Int = 12 // Fixed 12 items (3×4 grid)
        
        override fun onViewRecycled(holder: SkeletonViewHolder) {
            super.onViewRecycled(holder)
            logger.v { "♻️ [VIEW-RECYCLING] onViewRecycled - removing holder, total before: ${viewHolders.size}" }
            viewHolders.remove(holder)
            // No shimmer to stop - skeleton is just empty container
        }
        
        fun stopShimmers() {
            logger.d { "🛑 [VIEW-RECYCLING] stopShimmers() called - clearing ${viewHolders.size} holders (no shimmers)" }
            viewHolders.clear()
        }
    }
    
    sealed class SkeletonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        abstract fun fadeOut(delay: Long, duration: Long)
        
        class GridViewHolder(private val skeletonCardView: SkeletonMangaCardView) : 
            SkeletonViewHolder(skeletonCardView) {
            
            override fun fadeOut(delay: Long, duration: Long) {
                logger.d { "🌊 [FADE] GridViewHolder fadeOut starting - delay: ${delay}ms, duration: ${duration}ms, current alpha: ${skeletonCardView.alpha}" }
                skeletonCardView.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .setStartDelay(delay)
                    .withStartAction {
                        logger.d { "🌊 [FADE] Animation STARTED - alpha: ${skeletonCardView.alpha}" }
                    }
                    .withEndAction {
                        logger.d { "🌊 [FADE] Animation COMPLETED - alpha: ${skeletonCardView.alpha}" }
                    }
                    .start()
            }
        }
        
        class ListViewHolder(private val skeletonListView: SkeletonMangaListView) : 
            SkeletonViewHolder(skeletonListView) {
            
            override fun fadeOut(delay: Long, duration: Long) {
                logger.d { "🌊 [FADE] ListViewHolder fadeOut starting - delay: ${delay}ms, duration: ${duration}ms, current alpha: ${skeletonListView.alpha}" }
                skeletonListView.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .setStartDelay(delay)
                    .withStartAction {
                        logger.d { "🌊 [FADE] List animation STARTED - alpha: ${skeletonListView.alpha}" }
                    }
                    .withEndAction {
                        logger.d { "🌊 [FADE] List animation COMPLETED - alpha: ${skeletonListView.alpha}" }
                    }
                    .start()
            }
        }
    }
}
