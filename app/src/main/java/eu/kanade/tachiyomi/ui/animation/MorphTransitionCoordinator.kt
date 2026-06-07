package eu.kanade.tachiyomi.ui.animation

import android.graphics.drawable.Drawable
import android.view.View
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.injectLazy

/**
 * Coordinates morph transition animations based on user preference.
 * 
 * **Purpose**: Centralizes all morph transition logic to ensure clean toggle behavior.
 * When `preferences.sourceOpeningAnimation()` is disabled, this coordinator ensures
 * NO morph transition code executes, making the feature completely non-destructive.
 * 
 * **Architecture Pattern**: Feature Flag Coordinator
 * - Single source of truth for morph animation state
 * - All morph-related code checks `isEnabled()` before executing
 * - When disabled, returns safe defaults and no-ops
 * 
 * **Usage Example**:
 * ```kotlin
 * // In BrowseSourceController.onViewCreated()
 * val savedBackground = MorphTransitionCoordinator.applyConditionalTransparency(view)
 * 
 * if (MorphTransitionCoordinator.isEnabled(view.context)) {
 *     // Morph animation code
 *     view.animate().alpha(1f).withEndAction {
 *         MorphTransitionCoordinator.restoreBackground(view, savedBackground)
 *     }
 * } else {
 *     // Standard navigation (existing behavior)
 *     view.alpha = 1f
 * }
 * ```
 * 
 * @see eu.kanade.tachiyomi.data.preference.PreferencesHelper.sourceOpeningAnimation
 */
object MorphTransitionCoordinator {
    
    private val preferences: PreferencesHelper by injectLazy()
    
    /**
     * Check if morph transition animations are enabled.
     * 
     * **Decision Logic**:
     * - Returns `true` only if user has enabled source opening animations
     * - Ignores system animation scale settings (as per requirements)
     * - Safe to call from any context
     * 
     * @return `true` if morph animations should run, `false` otherwise
     */
    fun isEnabled(): Boolean {
        return preferences.sourceOpeningAnimation().get()
    }
    
    /**
     * Apply transparent background if morph animations are enabled.
     * 
     * **Behavior**:
     * - **When enabled**: Removes view's background and returns the original for restoration
     * - **When disabled**: Does nothing, returns `null` (background stays opaque)
     * 
     * **Thread Safety**: Must be called on UI thread (view manipulation)
     * 
     * @param view The view whose background should be made transparent
     * @return Original background drawable if transparency applied, `null` otherwise
     */
    fun applyConditionalTransparency(view: View): Drawable? {
        return if (isEnabled()) {
            // Save original background for later restoration
            val original = view.background
            // Make transparent for morph transition
            view.background = null
            original
        } else {
            // Animations disabled: keep existing background (no transparency)
            null
        }
    }
    
    /**
     * Restore background after animation completes or immediately if disabled.
     * 
     * **Behavior**:
     * - **When enabled + immediate=false**: Schedules background restoration after MORPH_DURATION
     * - **When enabled + immediate=true**: Restores background immediately
     * - **When disabled**: Restores background immediately (no delay needed)
     * 
     * **Memory Safety**: Cancels any pending restoration callbacks to prevent leaks
     * 
     * @param view The view whose background should be restored
     * @param original The original background drawable (from applyConditionalTransparency)
     * @param immediate If true, restore immediately; if false, delay until animation completes
     */
    fun restoreBackground(view: View, original: Drawable?, immediate: Boolean = false) {
        // Safety check: nothing to restore
        if (original == null) return
        
        if (immediate || !isEnabled()) {
            // Immediate restoration (animations disabled or explicitly requested)
            view.background = original
        } else {
            // Delayed restoration after morph animation completes
            scheduleBackgroundRestoration(view, original, MORPH_DURATION)
        }
    }
    
    /**
     * Calculate when to start navigation for optimal overlap.
     * 
     * **Timing Strategy**:
     * - Navigation starts at 50% of source exit animation
     * - This creates visual overlap: sources still animating OUT while content animates IN
     * - Ensures both views are semi-transparent simultaneously (the "morph" effect)
     * 
     * **Example**:
     * - 10 sources → ~700ms total exit → navigation at 350ms
     * - User sees sources fading down AND manga grid fading up at same time
     * 
     * @param sourceCount Number of source items currently visible
     * @return Delay in milliseconds before starting navigation
     */
    fun calculateNavigationDelay(sourceCount: Int): Long {
        val totalDuration = calculateTotalExitDuration(sourceCount)
        return (totalDuration * NAVIGATION_DELAY_PERCENT).toLong()
    }
    
    /**
     * Calculate total duration of source exit animation.
     * 
     * **Animation Math**:
     * - Base duration: SOURCE_EXIT_DURATION (300ms)
     * - Each item adds: SOURCE_EXIT_DELAY_INCREMENT (40ms) of stagger
     * - Example: 10 items = 300ms + (10 * 40ms) = 700ms total
     * 
     * @param sourceCount Number of source items to animate
     * @return Total animation duration in milliseconds
     */
    fun calculateTotalExitDuration(sourceCount: Int): Long {
        return SOURCE_EXIT_DURATION + (sourceCount * SOURCE_EXIT_DELAY_INCREMENT)
    }
    
    /**
     * Schedule background restoration with memory leak prevention.
     * 
     * **Implementation Note**: Stores callback in companion object to allow cancellation.
     * This prevents memory leaks if view is destroyed before callback fires.
     * 
     * @param view Target view
     * @param background Background to restore
     * @param delay Delay before restoration
     */
    private fun scheduleBackgroundRestoration(view: View, background: Drawable, delay: Long) {
        // Cancel any pending restoration for this view
        backgroundRestorationCallbacks[view]?.let { 
            view.removeCallbacks(it)
        }
        
        // Create new restoration callback
        val callback = Runnable {
            view.background = background
            // Clean up callback reference
            backgroundRestorationCallbacks.remove(view)
        }
        
        // Store callback for potential cancellation
        backgroundRestorationCallbacks[view] = callback
        
        // Schedule restoration
        view.postDelayed(callback, delay)
    }
    
    /**
     * Cancel any pending background restoration for a view.
     * Call this in onDestroyView() to prevent memory leaks.
     * 
     * @param view View whose restoration should be cancelled
     */
    fun cancelBackgroundRestoration(view: View) {
        backgroundRestorationCallbacks[view]?.let { callback ->
            view.removeCallbacks(callback)
            backgroundRestorationCallbacks.remove(view)
        }
    }
    
    // ═══════════════════════════════════════════════════════════
    // Timing Constants
    // ═══════════════════════════════════════════════════════════
    
    /**
     * Duration of the morph transition fade-in animation.
     * Matches BrowseSourceController entry animation duration.
     */
    const val MORPH_DURATION = 400L
    
    /**
     * Base duration for each source item's exit animation.
     * Each item fades and slides down over this duration.
     */
    const val SOURCE_EXIT_DURATION = 300L
    
    /**
     * Stagger delay between consecutive source items.
     * Creates the "domino" effect as items exit sequentially.
     */
    const val SOURCE_EXIT_DELAY_INCREMENT = 40L
    
    /**
     * Percentage of source exit animation before navigation starts.
     * 0.5 = 50% = optimal overlap (sources half-faded when content appears)
     */
    const val NAVIGATION_DELAY_PERCENT = 0.5f
    
    /**
     * Guaranteed minimum overlap duration where both views are visible.
     * This is the "magic moment" of the morph transition.
     */
    const val OVERLAP_DURATION = 200L
    
    // ═══════════════════════════════════════════════════════════
    // Private State
    // ═══════════════════════════════════════════════════════════
    
    /**
     * Tracks pending background restoration callbacks.
     * Map: View → Restoration Runnable
     * Used for memory leak prevention and cancellation.
     */
    private val backgroundRestorationCallbacks = mutableMapOf<View, Runnable>()
}
