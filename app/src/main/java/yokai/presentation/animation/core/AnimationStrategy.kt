package yokai.presentation.animation.core

import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Strategy interface for defining RecyclerView item animations.
 * Implementations provide specific animation behaviors (domino, fade, slide, etc.)
 * 
 * This interface follows the Strategy Pattern to allow pluggable animation behaviors
 * that can be easily swapped or combined without modifying existing code.
 */
interface AnimationStrategy {
    
    /**
     * Calculate delay for item at specific position.
     * 
     * @param position Item position in adapter
     * @param layoutManager RecyclerView's layout manager (for grid span detection)
     * @return Delay in milliseconds before animation starts
     */
    fun calculateDelay(position: Int, layoutManager: RecyclerView.LayoutManager): Long
    
    /**
     * Apply initial pre-animation state to view.
     * Sets view properties to their starting values (e.g., alpha=0, translationY=30).
     * 
     * @param itemView The view to prepare for animation
     */
    fun prepareForAnimation(itemView: View)
    
    /**
     * Animate view to final state.
     * Transitions view from prepared state to final visible state with smooth animation.
     * 
     * @param itemView The view to animate
     * @param delay Calculated delay from calculateDelay()
     * @param onAnimationEnd Callback when animation completes (must be called)
     */
    fun animateView(
        itemView: View, 
        delay: Long, 
        onAnimationEnd: () -> Unit
    )
    
    /**
     * Reset view to neutral state (for recycled views).
     * Ensures recycled views don't retain transformation values.
     * 
     * @param itemView The view to reset
     */
    fun resetView(itemView: View)
    
    /**
     * Get human-readable name for logging/debugging.
     * Used in log messages to identify which strategy is executing.
     * 
     * @return Strategy name (e.g., "DominoGrid", "SequentialList")
     */
    fun getStrategyName(): String
}
