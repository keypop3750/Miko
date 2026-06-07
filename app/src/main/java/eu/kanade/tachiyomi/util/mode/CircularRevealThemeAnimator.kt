package eu.kanade.tachiyomi.util.mode

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.applyCanvas
import co.touchlab.kermit.Logger
import kotlin.math.hypot

/**
 * Handles smooth Telegram-style circular reveal theme transitions.
 * 
 * The approach:
 * 1. Capture a screenshot of the current screen (old theme)
 * 2. Add it as an overlay on top of the window
 * 3. Apply the new theme (Compose UI will update reactively underneath)
 * 4. Animate the overlay with a circular reveal to reveal the new theme
 * 5. Remove the overlay when animation completes
 * 
 * This works because Compose UI updates reactively when theme colors change,
 * so the "new" UI is instantly underneath the overlay.
 */
class CircularRevealThemeAnimator {
    
    companion object {
        private const val ANIMATION_DURATION_MS = 400L
        private const val TAG = "CIRCULAR_REVEAL"
        
        /**
         * Get the current instance (singleton-like access for convenience).
         */
        @Volatile
        private var instance: CircularRevealThemeAnimator? = null
        
        fun getInstance(): CircularRevealThemeAnimator {
            return instance ?: synchronized(this) {
                instance ?: CircularRevealThemeAnimator().also { instance = it }
            }
        }
    }
    
    /**
     * Data class to hold the origin point for the circular reveal.
     * Typically the center of the mode toggle button.
     */
    data class RevealOrigin(
        val x: Float,
        val y: Float
    )
    
    private var isAnimating = false
    
    /**
     * Execute a theme change with circular reveal animation.
     * 
     * @param activity The activity whose theme is changing
     * @param revealOrigin The point from which the circular reveal should originate
     * @param applyTheme Lambda that applies the new theme (called after screenshot capture)
     * @param onComplete Called when animation is complete
     */
    fun animateThemeChange(
        activity: AppCompatActivity,
        revealOrigin: RevealOrigin,
        applyTheme: () -> Unit,
        onComplete: () -> Unit = {}
    ) {
        if (isAnimating) {
            Logger.d { "🔄 [$TAG] Animation already in progress, skipping" }
            applyTheme()
            onComplete()
            return
        }
        
        isAnimating = true
        Logger.d { "🔄 [$TAG] Starting circular reveal animation from (${revealOrigin.x}, ${revealOrigin.y})" }
        
        val decorView = activity.window.decorView as ViewGroup
        
        // Step 1: Capture screenshot of current state
        captureScreenshot(activity) { bitmap ->
            if (bitmap == null) {
                Logger.w { "🔄 [$TAG] Failed to capture screenshot, applying theme without animation" }
                applyTheme()
                isAnimating = false
                onComplete()
                return@captureScreenshot
            }
            
            // Step 2: Create overlay ImageView with the screenshot
            val overlayView = createOverlayView(activity, bitmap)
            decorView.addView(overlayView)
            
            // Step 3: Apply the new theme (Compose will update reactively underneath)
            applyTheme()
            
            // Step 4: Animate the overlay with circular reveal (inverted - revealing what's underneath)
            // Post to ensure the new theme has been applied
            Handler(Looper.getMainLooper()).postDelayed({
                performCircularReveal(overlayView, revealOrigin, bitmap) {
                    // Step 5: Remove overlay when done
                    decorView.removeView(overlayView)
                    bitmap.recycle()
                    isAnimating = false
                    Logger.d { "🔄 [$TAG] Circular reveal animation complete" }
                    onComplete()
                }
            }, 16) // Wait one frame for theme to apply
        }
    }
    
    /**
     * Capture a screenshot of the current activity.
     */
    private fun captureScreenshot(activity: AppCompatActivity, callback: (Bitmap?) -> Unit) {
        val window = activity.window
        val decorView = window.decorView
        
        val width = decorView.width
        val height = decorView.height
        
        if (width <= 0 || height <= 0) {
            callback(null)
            return
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Use PixelCopy for API 26+ (more accurate)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                
                PixelCopy.request(
                    window,
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            callback(bitmap)
                        } else {
                            Logger.w { "🔄 [$TAG] PixelCopy failed with result: $copyResult, falling back to drawing cache" }
                            bitmap.recycle()
                            callback(captureScreenshotFallback(decorView))
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } else {
                // Fallback for older APIs
                callback(captureScreenshotFallback(decorView))
            }
        } catch (e: Exception) {
            Logger.e(e) { "🔄 [$TAG] Error capturing screenshot" }
            callback(null)
        }
    }
    
    /**
     * Fallback screenshot method using the drawing cache.
     */
    private fun captureScreenshotFallback(view: View): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            bitmap.applyCanvas {
                view.draw(this)
            }
            bitmap
        } catch (e: Exception) {
            Logger.e(e) { "🔄 [$TAG] Fallback screenshot capture failed" }
            null
        }
    }
    
    /**
     * Create an ImageView overlay containing the screenshot.
     */
    private fun createOverlayView(activity: AppCompatActivity, bitmap: Bitmap): ImageView {
        return ImageView(activity).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Ensure it's on top
            elevation = 100f
        }
    }
    
    /**
     * Perform the circular reveal animation on the overlay.
     * We animate the clip from full to nothing (revealing what's underneath).
     */
    private fun performCircularReveal(
        overlayView: ImageView,
        origin: RevealOrigin,
        bitmap: Bitmap,
        onComplete: () -> Unit
    ) {
        // Calculate the final radius (from origin to the farthest corner)
        val maxRadius = calculateMaxRadius(overlayView, origin)
        
        try {
            // Create circular reveal that shrinks FROM full size TO nothing
            // This reveals the new theme underneath
            val animator = ViewAnimationUtils.createCircularReveal(
                overlayView,
                origin.x.toInt(),
                origin.y.toInt(),
                maxRadius,  // Start fully visible
                0f          // End fully hidden (revealed)
            )
            
            animator.duration = ANIMATION_DURATION_MS
            
            // Use a smooth interpolator
            animator.interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onComplete()
                }
                
                override fun onAnimationCancel(animation: Animator) {
                    onComplete()
                }
            })
            
            animator.start()
        } catch (e: Exception) {
            Logger.e(e) { "🔄 [$TAG] Error performing circular reveal, falling back to fade" }
            // Fallback to simple fade
            overlayView.animate()
                .alpha(0f)
                .setDuration(ANIMATION_DURATION_MS)
                .withEndAction(onComplete)
                .start()
        }
    }
    
    /**
     * Calculate the maximum radius needed to cover the entire view from the origin point.
     */
    private fun calculateMaxRadius(view: View, origin: RevealOrigin): Float {
        val width = view.width.toFloat()
        val height = view.height.toFloat()
        
        // Calculate distance to each corner
        val topLeft = hypot(origin.x, origin.y)
        val topRight = hypot(width - origin.x, origin.y)
        val bottomLeft = hypot(origin.x, height - origin.y)
        val bottomRight = hypot(width - origin.x, height - origin.y)
        
        // Return the maximum distance (to ensure full coverage)
        return maxOf(topLeft, topRight, bottomLeft, bottomRight)
    }
    
    /**
     * Get the center point of a view for use as reveal origin.
     */
    fun getViewCenter(view: View): RevealOrigin {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return RevealOrigin(
            x = location[0] + view.width / 2f,
            y = location[1] + view.height / 2f
        )
    }
    
    /**
     * Get the center of the screen as a fallback reveal origin.
     */
    fun getScreenCenter(activity: AppCompatActivity): RevealOrigin {
        val decorView = activity.window.decorView
        return RevealOrigin(
            x = decorView.width / 2f,
            y = decorView.height / 2f
        )
    }
}
