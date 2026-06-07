package yokai.presentation.animation.strategies

import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import yokai.presentation.animation.core.AnimationConfig
import yokai.presentation.animation.core.AnimationStrategy

/**
 * Domino-style grid animation: items animate left-to-right, top-to-bottom in waves.
 * 
 * This strategy creates a cascading "domino effect" where:
 * - Items in the same row animate with slight delays (left to right)
 * - Each new row starts slightly after the previous row begins
 * - Creates a diagonal wave pattern across the grid
 * - Items slide down from above with a bounce-back effect
 * 
 * Timing formula: delay = (column × 50ms) + (row × 80ms)
 * - Column 0, Row 0: 0ms
 * - Column 1, Row 0: 50ms
 * - Column 2, Row 0: 100ms
 * - Column 0, Row 1: 80ms
 * - Column 1, Row 1: 130ms (50 + 80)
 * 
 * @param config Animation configuration (defaults to DOMINO_GRID preset)
 */
class DominoGridStrategy(
    private val config: AnimationConfig = AnimationConfig.DOMINO_GRID
) : AnimationStrategy {
    
    // Custom bounce interpolator for slide-down effect
    private val bounceInterpolator = OvershootInterpolator(1.2f)
    
    /**
     * Calculate delay based on grid position (row and column).
     * Creates diagonal wave pattern by combining column and row delays.
     */
    override fun calculateDelay(position: Int, layoutManager: RecyclerView.LayoutManager): Long {
        // Detect grid span count from layout manager
        val spanCount = when (layoutManager) {
            is GridLayoutManager -> layoutManager.spanCount
            else -> 1 // Fallback to 1 if not a grid (shouldn't happen with this strategy)
        }
        
        val column = position % spanCount
        val row = position / spanCount
        
        // Diagonal wave: column delay + row delay (weighted more for row)
        val delay = (column * config.delayIncrement) + (row * config.delayIncrement * 1.6).toLong()
        
        return delay
    }
    
    /**
     * Set initial animation state: invisible, offset from above (negative Y).
     * No scaling - items maintain full size throughout animation.
     */
    override fun prepareForAnimation(itemView: View) {
        if (config.fadeEnabled) {
            itemView.alpha = 0f
        }
        if (config.slideEnabled) {
            // Slide from above (negative Y means upward offset)
            itemView.translationY = -config.slideDistance * 3
        }
        // No scaling - items stay at 1.0
        itemView.scaleX = 1f
        itemView.scaleY = 1f
    }
    
    /**
     * Animate to final state: visible, no offset, with bounce-back effect.
     * Uses hardware layer for smooth GPU-accelerated animation.
     */
    override fun animateView(itemView: View, delay: Long, onAnimationEnd: () -> Unit) {
        itemView.animate()
            .apply {
                if (config.fadeEnabled) alpha(1f)
                if (config.slideEnabled) translationY(0f)
            }
            .setDuration(config.duration)
            .setStartDelay(delay)
            .setInterpolator(bounceInterpolator) // Bounce effect for slide
            .withLayer() // Force hardware layer for smooth animation
            .withEndAction {
                // Ensure final state is clean (remove any floating-point errors)
                resetView(itemView)
                onAnimationEnd()
            }
            .start()
    }
    
    /**
     * Reset view to neutral state (fully visible, no transformations).
     */
    override fun resetView(itemView: View) {
        itemView.alpha = 1f
        itemView.translationY = 0f
        itemView.scaleX = 1f
        itemView.scaleY = 1f
    }
    
    override fun getStrategyName(): String = "DominoGrid"
}
