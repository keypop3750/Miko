package yokai.presentation.animation.core

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import co.touchlab.kermit.Logger

/**
 * Custom RecyclerView.ItemAnimator using strategy pattern for flexible animations.
 * Respects user preferences and only animates items on initial appearance.
 * 
 * Key features:
 * - Strategy pattern allows different animation behaviors per RecyclerView
 * - Tracks animated positions to prevent re-animation of recycled views
 * - Respects user preferences via lambda parameter
 * - Extends DefaultItemAnimator for standard remove/change animations
 * - Optional callback for app bar updates during animations
 * 
 * @param strategy The animation strategy to use (domino, sequential, fade, etc.)
 * @param isAnimationEnabled Lambda that checks if animations are currently enabled
 * @param onAnimationUpdate Optional callback invoked when animations occur (for app bar sync)
 */
class YokaiItemAnimator(
    private val strategy: AnimationStrategy,
    private val isAnimationEnabled: () -> Boolean,
    private val onAnimationUpdate: (() -> Unit)? = null
) : DefaultItemAnimator() {
    
    private val logger = Logger.withTag("YokaiItemAnimator")
    
    /**
     * Set of adapter positions that have already been animated.
     * Used to prevent re-animating recycled views when scrolling.
     */
    private val animatedPositions = mutableSetOf<Int>()
    
    /**
     * Animate the addition of a new item.
     * Called by RecyclerView when new items appear (initial load or scroll).
     * 
     * @param holder The ViewHolder for the item being added
     * @return true if animation was started, false if skipped
     */
    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        val position = holder.bindingAdapterPosition
        
        // Check if position is valid
        if (position == RecyclerView.NO_POSITION) {
            logger.w { "⚠️ Invalid position (NO_POSITION), skipping animation" }
            dispatchAddFinished(holder)
            return false
        }
        
        // Check if animations are enabled via preference
        if (!isAnimationEnabled()) {
            logger.d { "⏭️ Animations disabled, skipping position $position" }
            dispatchAddFinished(holder)
            return false
        }
        
        // Check if this position was already animated
        if (position in animatedPositions) {
            logger.d { "♻️ Position $position already animated (recycled view)" }
            dispatchAddFinished(holder)
            return false
        }
        
        logger.d { "🎬 [${strategy.getStrategyName()}] Animating new item at position $position" }
        
        // Mark position as animated
        animatedPositions.add(position)
        
        // Get layout manager from RecyclerView
        val layoutManager = (holder.itemView.parent as? RecyclerView)?.layoutManager
        
        if (layoutManager == null) {
            logger.w { "⚠️ No LayoutManager found, skipping animation for position $position" }
            dispatchAddFinished(holder)
            return false
        }
        
        // Calculate delay using strategy
        val delay = strategy.calculateDelay(position, layoutManager)
        
        // Prepare view for animation (set initial state)
        strategy.prepareForAnimation(holder.itemView)
        
        // Animate view to final state
        strategy.animateView(holder.itemView, delay) {
            // Animation completed - notify RecyclerView
            dispatchAddFinished(holder)
            logger.d { "✅ [${strategy.getStrategyName()}] Animation completed for position $position" }
        }
        
        return true
    }
    
    /**
     * Animate the removal of an item.
     * Uses default implementation for consistent behavior.
     */
    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        return super.animateRemove(holder)
    }
    
    /**
     * Animate changes to an item.
     * Uses default implementation for consistent behavior.
     * Triggers app bar update callback if set.
     */
    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder,
        fromX: Int, 
        fromY: Int, 
        toX: Int, 
        toY: Int
    ): Boolean {
        onAnimationUpdate?.invoke()
        return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
    }
    
    /**
     * Animate item movement (position changes).
     * Uses default implementation for consistent behavior.
     * Triggers app bar update callback if set.
     */
    override fun animateMove(
        holder: RecyclerView.ViewHolder,
        fromX: Int, 
        fromY: Int, 
        toX: Int, 
        toY: Int
    ): Boolean {
        onAnimationUpdate?.invoke()
        return super.animateMove(holder, fromX, fromY, toX, toY)
    }
    
    /**
     * Called when all animations are finished.
     * Triggers final app bar update if callback is set.
     */
    override fun onAnimationFinished(viewHolder: RecyclerView.ViewHolder) {
        onAnimationUpdate?.invoke()
        super.onAnimationFinished(viewHolder)
    }
    
    /**
     * Clear animated positions when RecyclerView is scrolled out or refreshed.
     * Call this when data is completely refreshed to allow items to animate again.
     */
    fun resetAnimationState() {
        animatedPositions.clear()
        logger.d { "🔄 [${strategy.getStrategyName()}] Animation state reset" }
    }
    
    /**
     * Reset specific view to neutral state (for view recycling).
     * Ensures views don't retain transformation values when recycled.
     * 
     * @param itemView The view to reset
     */
    fun resetViewState(itemView: android.view.View) {
        strategy.resetView(itemView)
    }
    
    /**
     * Get the name of the current animation strategy.
     * Useful for debugging and logging.
     * 
     * @return The strategy name
     */
    fun getStrategyName(): String {
        return strategy.getStrategyName()
    }
}
