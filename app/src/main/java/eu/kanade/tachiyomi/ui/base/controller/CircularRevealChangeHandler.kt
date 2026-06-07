package eu.kanade.tachiyomi.ui.base.controller

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.changehandler.AnimatorChangeHandler

/**
 * Custom Conductor change handler for circular reveal shared element transition.
 * Animates from library item click position with expanding circular reveal.
 * 
 * **Phase 3**: Basic circular reveal implementation (no thumbnail morph)
 * 
 * Architecture:
 * - Extends AnimatorChangeHandler for Conductor integration
 * - Uses ViewAnimationUtils.createCircularReveal() for circular mask
 * - Keeps library view visible during transition (removesFromViewOnPush = false)
 * - Reveals details view from click coordinates outward
 * 
 * @param clickX X coordinate of the library item click (in details view coordinates)
 * @param clickY Y coordinate of the library item click (in details view coordinates)
 * @param duration Duration of reveal animation in milliseconds
 */
class CircularRevealChangeHandler : AnimatorChangeHandler {
    
    private var clickX: Float = 0f
    private var clickY: Float = 0f
    
    /**
     * Default constructor required by Conductor for state restoration.
     */
    constructor() : super()
    
    /**
     * Primary constructor with click coordinates.
     */
    constructor(clickX: Float, clickY: Float, duration: Long = 300L) : super(duration, false) {
        this.clickX = clickX
        this.clickY = clickY
    }
    
    /**
     * Create and return the circular reveal animator.
     * Called by Conductor framework during controller transition.
     * 
     * @param container Parent ViewGroup containing both controllers
     * @param from Library controller view (nullable, may be null on first navigation)
     * @param to Details controller view (being revealed)
     * @param isPush True if pushing to details, false if popping back
     * @param toAddedToContainer True if 'to' view needs to be added to container
     * @return Animator for the circular reveal
     */
    override fun getAnimator(
        container: ViewGroup,
        from: View?,
        to: View?,
        isPush: Boolean,
        toAddedToContainer: Boolean
    ): Animator {
        // Only animate on push (library → details), not pop (back navigation)
        if (!isPush || to == null) {
            return AnimatorSet()  // Empty animator for pop
        }
        
        // CRITICAL FIX: Force the 'to' view to be visible and measure/layout BEFORE animation
        // This ensures content is drawn and ready when the circular reveal starts
        to.visibility = View.VISIBLE
        
        // If view hasn't been measured yet, force measure/layout pass
        if (to.width == 0 || to.height == 0) {
            to.measure(
                View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(container.height, View.MeasureSpec.EXACTLY)
            )
            to.layout(0, 0, to.measuredWidth, to.measuredHeight)
        }
        
        // Calculate ending radius to cover entire details view
        val endRadius = calculateEndRadius(to, clickX.toInt(), clickY.toInt())
        
        // Create circular reveal animator
        return ViewAnimationUtils.createCircularReveal(
            to,
            clickX.toInt(),   // Reveal center X
            clickY.toInt(),   // Reveal center Y
            0f,               // Starting radius (point)
            endRadius         // Ending radius (covers view)
        ).apply {
            duration = animationDuration
        }
    }
    
    /**
     * Calculates the ending radius for circular reveal to cover entire view.
     * Uses diagonal distance from reveal center to farthest corner.
     * 
     * @param view View to be revealed
     * @param centerX Reveal center X coordinate
     * @param centerY Reveal center Y coordinate
     * @return Radius that will cover entire view
     */
    private fun calculateEndRadius(view: View, centerX: Int, centerY: Int): Float {
        val maxX = kotlin.math.max(centerX, view.width - centerX)
        val maxY = kotlin.math.max(centerY, view.height - centerY)
        return kotlin.math.hypot(maxX.toDouble(), maxY.toDouble()).toFloat()
    }
    
    /**
     * Reset from view to initial state (no-op for this handler).
     * Required by AnimatorChangeHandler base class.
     */
    override fun resetFromView(from: View) {
        // No special reset needed - from view stays visible
    }
    
    /**
     * Creates a copy of this handler with same parameters.
     * Required by Conductor for state restoration.
     */
    override fun copy(): ControllerChangeHandler {
        return CircularRevealChangeHandler(clickX, clickY, animationDuration)
    }
}
