package yokai.presentation.animation.strategies

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import co.touchlab.kermit.Logger
import yokai.presentation.animation.core.AnimationConfig
import yokai.presentation.animation.core.AnimationStrategy

/**
 * Sequential list animation: items animate top-to-bottom in linear order.
 * 
 * This strategy creates a smooth cascading effect where:
 * - Each item animates slightly after the previous one
 * - Creates a flowing waterfall-like reveal
 * - Perfect for linear lists (LinearLayoutManager)
 * 
 * Timing formula: delay = position × delayIncrement
 * - Position 0: 0ms
 * - Position 1: 60ms
 * - Position 2: 120ms
 * - Position 3: 180ms
 * 
 * @param config Animation configuration (defaults to SEQUENTIAL_LIST preset)
 */
class SequentialListStrategy(
    private val config: AnimationConfig = AnimationConfig.SEQUENTIAL_LIST
) : AnimationStrategy {
    
    private val logger = Logger.withTag("SequentialListStrategy")
    
    /**
     * Calculate delay based on linear position.
     * Each item gets progressively more delay.
     */
    override fun calculateDelay(position: Int, layoutManager: RecyclerView.LayoutManager): Long {
        val delay = position * config.delayIncrement
        logger.d { "📐 Position $position -> Delay: ${delay}ms" }
        return delay
    }
    
    /**
     * Set initial animation state: invisible, offset down, slightly scaled down.
     */
    override fun prepareForAnimation(itemView: View) {
        if (config.fadeEnabled) {
            itemView.alpha = 0f
        }
        if (config.slideEnabled) {
            itemView.translationY = config.slideDistance
        }
        if (config.scaleEnabled) {
            itemView.scaleX = config.scaleFrom
            itemView.scaleY = config.scaleFrom
        }
        
        logger.d { "👻 Prepared view: alpha=${itemView.alpha}, translationY=${itemView.translationY}, scale=${itemView.scaleX}" }
    }
    
    /**
     * Animate to final state: visible, no offset, full scale.
     * Uses hardware layer for smooth GPU-accelerated animation.
     */
    override fun animateView(itemView: View, delay: Long, onAnimationEnd: () -> Unit) {
        itemView.animate()
            .apply {
                if (config.fadeEnabled) alpha(1f)
                if (config.slideEnabled) translationY(0f)
                if (config.scaleEnabled) {
                    scaleX(1f)
                    scaleY(1f)
                }
            }
            .setDuration(config.duration)
            .setStartDelay(delay)
            .setInterpolator(config.interpolator)
            .withLayer() // Force hardware layer for smooth animation
            .withEndAction {
                // Ensure final state is clean
                resetView(itemView)
                onAnimationEnd()
            }
            .start()
        
        logger.d { "🎬 Started animation with delay ${delay}ms, duration ${config.duration}ms" }
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
    
    override fun getStrategyName(): String = "SequentialList"
}
