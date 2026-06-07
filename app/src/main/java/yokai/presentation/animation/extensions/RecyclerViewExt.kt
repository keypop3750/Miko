package yokai.presentation.animation.extensions

import androidx.recyclerview.widget.RecyclerView
import yokai.presentation.animation.core.YokaiItemAnimator
import yokai.presentation.animation.strategies.DominoGridStrategy
import yokai.presentation.animation.strategies.NoAnimationStrategy
import yokai.presentation.animation.strategies.SequentialListStrategy
import yokai.presentation.animation.strategies.SimpleFadeStrategy

/**
 * Extension functions for easily applying animation strategies to RecyclerViews.
 * 
 * These provide a convenient API for setting up ItemAnimator with various
 * animation strategies throughout the app.
 */

/**
 * Apply the domino grid animation strategy to this RecyclerView.
 * Best suited for grid layouts with diagonal wave effect.
 * 
 * @param isEnabled Lambda that returns whether animations should be shown
 * @param onAnimationUpdate Optional callback for animation events (e.g., app bar updates)
 */
fun RecyclerView.applyDominoAnimation(
    isEnabled: () -> Boolean,
    onAnimationUpdate: (() -> Unit)? = null
) {
    this.itemAnimator = YokaiItemAnimator(
        strategy = DominoGridStrategy(),
        isAnimationEnabled = isEnabled,
        onAnimationUpdate = onAnimationUpdate
    )
}

/**
 * Apply the sequential list animation strategy to this RecyclerView.
 * Best suited for linear lists with waterfall effect.
 * 
 * @param isEnabled Lambda that returns whether animations should be shown
 * @param onAnimationUpdate Optional callback for animation events (e.g., app bar updates)
 */
fun RecyclerView.applySequentialAnimation(
    isEnabled: () -> Boolean,
    onAnimationUpdate: (() -> Unit)? = null
) {
    this.itemAnimator = YokaiItemAnimator(
        strategy = SequentialListStrategy(),
        isAnimationEnabled = isEnabled,
        onAnimationUpdate = onAnimationUpdate
    )
}

/**
 * Apply the simple fade animation strategy to this RecyclerView.
 * Minimal animation with just alpha transition, good for performance.
 * 
 * @param isEnabled Lambda that returns whether animations should be shown
 * @param onAnimationUpdate Optional callback for animation events (e.g., app bar updates)
 */
fun RecyclerView.applyFadeAnimation(
    isEnabled: () -> Boolean,
    onAnimationUpdate: (() -> Unit)? = null
) {
    this.itemAnimator = YokaiItemAnimator(
        strategy = SimpleFadeStrategy(),
        isAnimationEnabled = isEnabled,
        onAnimationUpdate = onAnimationUpdate
    )
}

/**
 * Apply a custom animation strategy to this RecyclerView.
 * Use this when you want to use a custom strategy implementation.
 * 
 * @param strategy The animation strategy to use
 * @param isEnabled Lambda that returns whether animations should be shown
 * @param onAnimationUpdate Optional callback for animation events (e.g., app bar updates)
 */
fun RecyclerView.applyCustomAnimation(
    strategy: yokai.presentation.animation.core.AnimationStrategy,
    isEnabled: () -> Boolean,
    onAnimationUpdate: (() -> Unit)? = null
) {
    this.itemAnimator = YokaiItemAnimator(
        strategy = strategy,
        isAnimationEnabled = isEnabled,
        onAnimationUpdate = onAnimationUpdate
    )
}

/**
 * Disable animations for this RecyclerView.
 * Uses NoAnimationStrategy for zero overhead.
 */
fun RecyclerView.disableAnimations() {
    this.itemAnimator = YokaiItemAnimator(
        strategy = NoAnimationStrategy(),
        isAnimationEnabled = { false }
    )
}

/**
 * Reset the animation state for this RecyclerView.
 * Call this when data is refreshed to allow items to animate again.
 * 
 * This clears the tracking of which positions have been animated,
 * so items will animate on the next bind.
 */
fun RecyclerView.resetAnimationState() {
    val animator = this.itemAnimator
    if (animator is YokaiItemAnimator) {
        animator.resetAnimationState()
    }
}

/**
 * Reset the view state for a specific item view.
 * Useful when recycling views to ensure clean state.
 * 
 * @param itemView The view to reset
 */
fun RecyclerView.resetViewState(itemView: android.view.View) {
    val animator = this.itemAnimator
    if (animator is YokaiItemAnimator) {
        animator.resetViewState(itemView)
    }
}

/**
 * Check if animations are currently enabled for this RecyclerView.
 * 
 * @return true if the ItemAnimator is a YokaiItemAnimator, false otherwise
 */
fun RecyclerView.hasYokaiAnimations(): Boolean {
    return this.itemAnimator is YokaiItemAnimator
}

/**
 * Get the current animation strategy name being used.
 * 
 * @return The strategy name, or null if not using YokaiItemAnimator
 */
fun RecyclerView.getAnimationStrategyName(): String? {
    val animator = this.itemAnimator
    return if (animator is YokaiItemAnimator) {
        animator.getStrategyName()
    } else {
        null
    }
}
