package yokai.presentation.skeleton

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.databinding.SkeletonMangaListBinding

private val logger = Logger.withTag("SkeletonMangaListView")

/**
 * Skeleton placeholder for manga list items with high-performance shimmer animation.
 * Uses hardware-accelerated matrix transformation for 60fps smooth shimmer.
 * Shows only cover image placeholder, no text.
 */
class SkeletonMangaListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    private val binding: SkeletonMangaListBinding
    private var shimmerAnimator: ValueAnimator? = null
    
    // Shimmer effect using Paint with shader (hardware accelerated)
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shimmerMatrix = Matrix()
    private var shimmerTranslateX = 0f
    
    // Colors for shimmer gradient (subtle effect)
    private val baseColor = 0x1A808080 // 10% grey
    private val highlightColor = 0x30FFFFFF // 19% white highlight
    
    init {
        binding = SkeletonMangaListBinding.inflate(LayoutInflater.from(context), this, true)
        setWillNotDraw(false) // Enable onDraw for shimmer
        
        // Enable drawing on top of children (foreground layer)
        setWillNotDraw(false)
        
        logger.d { "✨ [SKELETON] SkeletonMangaListView created" }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            setupShimmer(w)
        }
    }
    
    private fun setupShimmer(width: Int) {
        // Create gradient shader (wider than view for smooth animation)
        val shimmerWidth = width * 2f
        val shader = LinearGradient(
            0f, 0f, shimmerWidth, 0f,
            intArrayOf(baseColor, highlightColor, baseColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        
        shimmerPaint.shader = shader
        
        // Start shimmer animation
        shimmerAnimator?.cancel()
        shimmerAnimator = ValueAnimator.ofFloat(-shimmerWidth, width.toFloat()).apply {
            duration = 1500L // 1.5 second cycle
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                shimmerTranslateX = animation.animatedValue as Float
                invalidate() // Request redraw (hardware accelerated)
            }
            start()
        }
        
        logger.d { "✨ [SKELETON] List shimmer started (width: $width)" }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // This draws AFTER children, so shimmer appears on top
    }
    
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        
        // Draw shimmer AFTER children are drawn (foreground layer)
        if (shimmerPaint.shader != null && binding.skeletonCover.width > 0) {
            // Get cover bounds relative to this parent FrameLayout
            val coverLeft = binding.skeletonCard.left.toFloat()
            val coverTop = binding.skeletonCard.top.toFloat()
            val coverRight = binding.skeletonCard.right.toFloat()
            val coverBottom = binding.skeletonCard.bottom.toFloat()
            
            // Apply translation matrix to shader (hardware accelerated)
            shimmerMatrix.setTranslate(shimmerTranslateX, 0f)
            shimmerPaint.shader.setLocalMatrix(shimmerMatrix)
            
            // Draw shimmer with rounded corners matching the card (12dp)
            val cornerRadius = 12f * resources.displayMetrics.density
            canvas.drawRoundRect(
                coverLeft, coverTop, coverRight, coverBottom,
                cornerRadius, cornerRadius,
                shimmerPaint
            )
        }
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        shimmerAnimator?.resume()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shimmerAnimator?.pause()
        logger.d { "✨ [SKELETON] SkeletonMangaListView detached, shimmer paused" }
    }
}
