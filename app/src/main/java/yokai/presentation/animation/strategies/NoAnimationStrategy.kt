package yokai.presentation.animation.strategies

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import yokai.presentation.animation.core.AnimationStrategy

/**
 * No-operation animation strategy (Null Object Pattern).
 * 
 * Used when animations are disabled via user preferences.
 * Immediately displays items in their final state without any animation.
 * 
 * Benefits:
 * - Zero overhead when animations are disabled
 * - Clean null object pattern (no null checks needed)
 * - Consistent interface with other strategies
 * 
 * This strategy:
 * - Returns 0ms delay for all items
 * - Skips all transformation setup
 * - Immediately calls onAnimationEnd callback
 * - Ensures views are in clean state
 */
class NoAnimationStrategy : AnimationStrategy {
    
    /**
     * No delay needed - items appear immediately.
     */
    override fun calculateDelay(position: Int, layoutManager: RecyclerView.LayoutManager): Long {
        return 0L
    }
    
    /**
     * No preparation needed - view stays in default state.
     */
    override fun prepareForAnimation(itemView: View) {
        // Intentionally empty - no transformations needed
    }
    
    /**
     * No animation - just ensure clean state and immediately call completion.
     */
    override fun animateView(itemView: View, delay: Long, onAnimationEnd: () -> Unit) {
        // Reset to clean state (in case view was recycled from animated state)
        resetView(itemView)
        
        // Immediately notify completion
        onAnimationEnd()
    }
    
    /**
     * Ensure view is in neutral state (fully visible, no transformations).
     */
    override fun resetView(itemView: View) {
        itemView.alpha = 1f
        itemView.translationY = 0f
        itemView.scaleX = 1f
        itemView.scaleY = 1f
    }
    
    override fun getStrategyName(): String = "NoAnimation"
}
