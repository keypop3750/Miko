package yokai.presentation.animation.core

import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator

/**
 * Configuration for animation timing and visual properties.
 * Provides presets for common animation styles and allows customization.
 */
data class AnimationConfig(
    /** Duration of animation in milliseconds */
    val duration: Long = 350L,
    
    /** Base delay before first animation starts */
    val baseDelay: Long = 0L,
    
    /** Delay increment between consecutive items */
    val delayIncrement: Long = 50L,
    
    /** Interpolator for animation curve (easing function) */
    val interpolator: Interpolator = OvershootInterpolator(0.8f),
    
    /** Enable debug logging for this animation */
    val enableLogging: Boolean = false,
    
    // Visual properties - enable/disable specific transformation types
    
    /** Enable fade in/out animation (alpha property) */
    val fadeEnabled: Boolean = true,
    
    /** Enable slide animation (translationY property) */
    val slideEnabled: Boolean = true,
    
    /** Enable scale animation (scaleX/scaleY properties) */
    val scaleEnabled: Boolean = true,
    
    /** Distance to slide in density-independent pixels */
    val slideDistance: Float = 30f,
    
    /** Starting scale factor (1.0 = full size) */
    val scaleFrom: Float = 0.95f
) {
    companion object {
        /**
         * Default configuration - balanced animation for general use
         */
        val DEFAULT = AnimationConfig()
        
        /**
         * Domino grid configuration - optimized for grid layouts
         * Creates left-to-right, top-to-bottom wave effect
         */
        val DOMINO_GRID = AnimationConfig(
            delayIncrement = 35L,
            slideDistance = 20f,
            duration = 250L
        )
        
        /**
         * Sequential list configuration - optimized for linear lists
         * Creates smooth top-to-bottom reveal
         */
        val SEQUENTIAL_LIST = AnimationConfig(
            delayIncrement = 60L,
            slideDistance = 40f,
            duration = 350L
        )
        
        /**
         * Simple fade configuration - minimal animation
         * Only fades in without slide or scale transformations
         */
        val SIMPLE_FADE = AnimationConfig(
            slideEnabled = false,
            scaleEnabled = false,
            duration = 250L,
            delayIncrement = 30L
        )
        
        /**
         * Fast animation - quick reveal for better perceived performance
         */
        val FAST = AnimationConfig(
            duration = 200L,
            delayIncrement = 30L,
            slideDistance = 20f
        )
        
        /**
         * Slow animation - more dramatic reveal for special cases
         */
        val SLOW = AnimationConfig(
            duration = 500L,
            delayIncrement = 80L,
            slideDistance = 40f
        )
    }
}
