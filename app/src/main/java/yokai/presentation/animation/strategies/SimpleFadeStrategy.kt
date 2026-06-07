package yokai.presentation.animation.strategies

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import co.touchlab.kermit.Logger
import yokai.presentation.animation.core.AnimationConfig
import yokai.presentation.animation.core.AnimationStrategy

/**
 * Simple fade animation: items fade in without slide or scale transformations.
 * 
 * This strategy provides minimal animation for:
 * - Better performance on low-end devices
 * - Less distracting animation for content-heavy screens
 * - Faster perceived loading (shorter duration)
 * 
 * Only animates alpha (opacity) property.
 * 
 * Timing formula: delay = position × 30ms (faster than other strategies)
 * - Position 0: 0ms
 * - Position 1: 30ms
 * - Position 2: 60ms
 * 
 * @param config Animation configuration (defaults to SIMPLE_FADE preset)
 */
class SimpleFadeStrategy(
    private val config: AnimationConfig = AnimationConfig.SIMPLE_FADE
) : AnimationStrategy {
    
    private val logger = Logger.withTag("SimpleFadeStrategy")
    
    /**
     * Calculate delay based on position with faster timing.
     */
    override fun calculateDelay(position: Int, layoutManager: RecyclerView.LayoutManager): Long {
        // Faster timing for simple fade (30ms per item)
        val delay = position * 30L
        logger.d { "📐 Position $position -> Delay: ${delay}ms" }
        return delay
    }
    
    /**
     * Set initial state: invisible only (no slide or scale).
     */
    override fun prepareForAnimation(itemView: View) {
        itemView.alpha = 0f
        logger.d { "👻 Prepared view: alpha=${itemView.alpha}" }
    }
    
    /**
     * Animate to visible state.
     * Shorter duration than other strategies for snappier feel.
     */
    override fun animateView(itemView: View, delay: Long, onAnimationEnd: () -> Unit) {
        itemView.animate()
            .alpha(1f)
            .setDuration(config.duration) // 250ms by default (vs 350ms for others)
            .setStartDelay(delay)
            .setInterpolator(config.interpolator)
            .withEndAction {
                resetView(itemView)
                onAnimationEnd()
            }
            .start()
        
        logger.d { "🎬 Started fade animation with delay ${delay}ms, duration ${config.duration}ms" }
    }
    
    /**
     * Reset view to fully visible state.
     */
    override fun resetView(itemView: View) {
        itemView.alpha = 1f
    }
    
    override fun getStrategyName(): String = "SimpleFade"
}
