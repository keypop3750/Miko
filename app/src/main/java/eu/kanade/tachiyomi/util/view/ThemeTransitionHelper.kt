package eu.kanade.tachiyomi.util.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.view.PixelCopy
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.hypot

/**
 * Helper class for creating Telegram-style theme transition animations.
 * 
 * Optimized for speed with:
 * - Pre-capture on touch down (before user releases)
 * - Immediate visual feedback via button scale animation
 * - Hardware-accelerated circular reveal
 * - Minimal blocking operations
 */
object ThemeTransitionHelper {
    
    private var overlayView: ImageView? = null
    private var isAnimating = false
    
    // Pre-captured bitmap for instant transition
    private var preCapturedBitmap: Bitmap? = null
    private var preCaptureActivity: Activity? = null
    
    /**
     * Call this on ACTION_DOWN to pre-capture the screen before user releases.
     * This eliminates capture delay when the actual click happens.
     */
    fun prepareTransition(activity: Activity) {
        if (isAnimating) return
        
        // Recycle old pre-capture if different activity
        if (preCaptureActivity != activity) {
            preCapturedBitmap?.recycle()
            preCapturedBitmap = null
        }
        preCaptureActivity = activity
        
        // Pre-capture in background - use synchronous canvas capture for speed
        val decorView = activity.window.decorView
        if (decorView.width > 0 && decorView.height > 0) {
            try {
                preCapturedBitmap = Bitmap.createBitmap(
                    decorView.width,
                    decorView.height,
                    Bitmap.Config.ARGB_8888
                ).also { bitmap ->
                    Canvas(bitmap).also { decorView.draw(it) }
                }
            } catch (e: Exception) {
                preCapturedBitmap = null
            }
        }
    }
    
    /**
     * Provide immediate visual feedback when button is pressed.
     * Call this to animate the anchor view (e.g., scale down then up).
     */
    fun animateButtonPress(anchorView: View?, onComplete: () -> Unit) {
        if (anchorView == null) {
            onComplete()
            return
        }
        
        // Quick scale-down animation for tactile feedback
        anchorView.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(50)
            .setInterpolator(FastOutLinearInInterpolator())
            .withEndAction {
                // Scale back up
                anchorView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(50)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .withEndAction(onComplete)
                    .start()
            }
            .start()
    }
    
    /**
     * Perform a theme transition with circular reveal animation.
     * Uses pre-captured bitmap if available for instant response.
     */
    fun animateThemeChange(
        activity: Activity,
        anchorView: View?,
        rootView: ViewGroup,
        onThemeChange: () -> Unit,
        duration: Long = 250L  // Fast animation
    ) {
        if (isAnimating) {
            onThemeChange()
            return
        }
        
        isAnimating = true
        
        val decorView = activity.window.decorView as? ViewGroup
        if (decorView == null) {
            onThemeChange()
            isAnimating = false
            return
        }
        
        // Calculate anchor position for circular reveal
        val anchorPosition = if (anchorView != null && anchorView.isVisible && anchorView.width > 0) {
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            Pair(location[0] + anchorView.width / 2, location[1] + anchorView.height / 2)
        } else {
            Pair(decorView.width - 100, 100)
        }
        
        // Get the current background color for fallback
        val backgroundColor = getWindowBackgroundColor(activity)
        
        // Use pre-captured bitmap if available and matches this activity
        val capturedBitmap = if (preCaptureActivity == activity && preCapturedBitmap != null) {
            preCapturedBitmap.also {
                preCapturedBitmap = null
                preCaptureActivity = null
            }
        } else {
            // Fallback: fast synchronous capture
            captureScreenFast(activity)
        }
        
        // Add overlay to CURRENT activity IMMEDIATELY
        val overlay = ImageView(activity).apply {
            if (capturedBitmap != null) {
                setImageBitmap(capturedBitmap)
                scaleType = ImageView.ScaleType.FIT_XY
            } else {
                setBackgroundColor(backgroundColor)
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            elevation = Float.MAX_VALUE
        }
        decorView.addView(overlay)
        
        // Store the transition info
        pendingTransition = PendingTransition(
            bitmap = capturedBitmap,
            backgroundColor = backgroundColor,
            anchorPosition = anchorPosition,
            duration = duration
        )
        
        // Trigger the theme change
        onThemeChange()
        
        // Timeout safety (reduced from 1500ms)
        decorView.postDelayed({
            if (isAnimating) {
                clearPendingTransition()
            }
        }, 800)
    }
    
    /**
     * Get the window background color from the current theme
     */
    private fun getWindowBackgroundColor(activity: Activity): Int {
        val typedValue = android.util.TypedValue()
        activity.theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)
        return if (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
            typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            typedValue.data
        } else {
            // Default to a dark color
            0xFF1F2025.toInt()
        }
    }
    
    /**
     * Data class for pending transition state
     */
    private data class PendingTransition(
        val bitmap: Bitmap?,
        val backgroundColor: Int,
        val anchorPosition: Pair<Int, Int>,
        val duration: Long
    )
    
    private var pendingTransition: PendingTransition? = null
    
    /**
     * Called by the new activity after recreation to continue the animation.
     * Should be called in onCreate after setContentView.
     */
    fun continuePendingTransition(activity: Activity) {
        val transition = pendingTransition ?: return
        
        val decorView = activity.window.decorView as? ViewGroup
        if (decorView == null) {
            clearPendingTransition()
            return
        }
        
        val (bitmap, backgroundColor, anchorPosition, duration) = transition
        
        // Create overlay
        val overlay = ImageView(activity).apply {
            if (bitmap != null) {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_XY
            } else {
                setBackgroundColor(backgroundColor)
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            elevation = Float.MAX_VALUE
        }
        overlayView = overlay
        decorView.addView(overlay)
        
        // Start animation immediately on next frame (no delay)
        decorView.post {
            animateOverlayOut(decorView, overlay, anchorPosition, bitmap, duration)
            pendingTransition = null
        }
    }
    
    /**
     * Clear pending transition state
     */
    fun clearPendingTransition() {
        pendingTransition?.bitmap?.recycle()
        pendingTransition = null
        isAnimating = false
        overlayView = null
    }
    
    /**
     * Animate overlay out using a pre-calculated anchor position
     */
    private fun animateOverlayOut(
        rootView: ViewGroup,
        overlay: ImageView,
        anchorPosition: Pair<Int, Int>,
        bitmap: Bitmap?,
        duration: Long
    ) {
        val (centerX, centerY) = anchorPosition
        val rootLocation = IntArray(2)
        rootView.getLocationOnScreen(rootLocation)
        
        val adjustedX = centerX - rootLocation[0]
        val adjustedY = centerY - rootLocation[1]
        
        val finalRadius = hypot(
            maxOf(adjustedX, rootView.width - adjustedX).toDouble(),
            maxOf(adjustedY, rootView.height - adjustedY).toDouble()
        ).toFloat()

        try {
            val animator = ViewAnimationUtils.createCircularReveal(
                overlay,
                adjustedX,
                adjustedY,
                finalRadius,
                0f
            )
            animator.duration = duration
            animator.interpolator = FastOutSlowInInterpolator()
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cleanupOverlay(rootView, overlay, bitmap)
                }
                override fun onAnimationCancel(animation: Animator) {
                    cleanupOverlay(rootView, overlay, bitmap)
                }
            })
            animator.start()
        } catch (e: Exception) {
            // Fallback to simple fade
            fadeOutOverlay(rootView, overlay, bitmap, duration)
        }
    }
    
    /**
     * Fast screen capture - only tries PixelCopy with short timeout
     */
    private fun captureScreenFast(activity: Activity): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        
        val decorView = activity.window.decorView
        val bitmap = try {
            Bitmap.createBitmap(
                decorView.width.coerceAtLeast(1),
                decorView.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
        } catch (e: Exception) {
            return null
        }
        
        val latch = CountDownLatch(1)
        var success = false
        
        val handlerThread = android.os.HandlerThread("PixelCopy")
        handlerThread.start()
        val handler = Handler(handlerThread.looper)
        
        try {
            PixelCopy.request(
                activity.window,
                bitmap,
                { result ->
                    success = (result == PixelCopy.SUCCESS)
                    latch.countDown()
                },
                handler
            )
            
            // Short timeout - 150ms max
            if (!latch.await(150, TimeUnit.MILLISECONDS) || !success) {
                bitmap.recycle()
                return null
            }
            return bitmap
        } catch (e: Exception) {
            bitmap.recycle()
            return null
        } finally {
            handlerThread.quitSafely()
        }
    }
    
    /**
     * Fallback fade-out animation.
     */
    private fun fadeOutOverlay(
        rootView: ViewGroup,
        overlay: ImageView,
        bitmap: Bitmap?,
        duration: Long
    ) {
        val animator = ValueAnimator.ofFloat(1f, 0f)
        animator.duration = duration
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { animation ->
            overlay.alpha = animation.animatedValue as Float
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                cleanupOverlay(rootView, overlay, bitmap)
            }
        })
        animator.start()
    }
    
    /**
     * Clean up the overlay and bitmap after animation.
     */
    private fun cleanupOverlay(rootView: ViewGroup, overlay: ImageView, bitmap: Bitmap?) {
        try {
            rootView.removeView(overlay)
            overlay.setImageBitmap(null)
            bitmap?.recycle()
        } catch (e: Exception) {
            // Ignore cleanup errors
        } finally {
            overlayView = null
            isAnimating = false
        }
    }
    
    /**
     * Cancel any ongoing animation and clean up.
     */
    fun cancelAnimation(rootView: ViewGroup?) {
        if (rootView != null && overlayView != null) {
            try {
                rootView.removeView(overlayView)
                overlayView?.setImageBitmap(null)
            } catch (e: Exception) {
                // Ignore
            }
        }
        overlayView = null
        isAnimating = false
    }
}
