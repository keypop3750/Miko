package eu.kanade.tachiyomi.ui.novel.reader.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import yokai.core.novel.reader.NovelReaderPreferences
import yokai.core.novel.reader.TextAlignment
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Optimized text view for novel reading with character-level position tracking,
 * smooth scrolling, and advanced typography controls.
 */
class NovelTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Text content and layout
    private var textContent: String = ""
    private var staticLayout: StaticLayout? = null
    private val textPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 48f // Default 16sp * density
    }

    // Position tracking
    private val _characterPosition = MutableStateFlow(0)
    val characterPosition: StateFlow<Int> = _characterPosition

    private val _scrollProgress = MutableStateFlow(0f)
    val scrollProgress: StateFlow<Float> = _scrollProgress

    // Reading preferences
    private var preferences = NovelReaderPreferences()
    
    // Scroll state
    private var scrollY = 0f
    private var maxScrollY = 0f
    
    // Touch handling
    private var lastTouchY = 0f
    private var isScrolling = false
    
    // Character position indicators
    private val characterRects = mutableListOf<Rect>()
    private var highlightedCharacterRange: IntRange? = null
    
    // Callbacks
    var onCharacterPositionChanged: ((Int) -> Unit)? = null
    var onScrollChanged: ((Float) -> Unit)? = null
    var onTextTapped: (() -> Unit)? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = createStaticLayout(width)
        
        setMeasuredDimension(width, height)
        
        // Calculate max scroll
        maxScrollY = max(0f, height - measuredHeight.toFloat())
    }

    private fun createStaticLayout(width: Int): Int {
        if (textContent.isEmpty() || width <= 0) {
            staticLayout = null
            return 0
        }

        val availableWidth = width - paddingLeft - paddingRight
        if (availableWidth <= 0) return 0

        // Apply text preferences
        textPaint.apply {
            textSize = preferences.fontSize * context.resources.displayMetrics.scaledDensity
            color = preferences.textColor
            // Apply font family if needed
        }

        // Create layout with proper alignment
        val alignment = when (preferences.textAlignment) {
            TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            TextAlignment.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL // Android doesn't have native justify
        }

        staticLayout = StaticLayout.Builder.obtain(
            textContent, 0, textContent.length, textPaint, availableWidth
        ).apply {
            setAlignment(alignment)
            setLineSpacing(0f, preferences.lineHeight)
            setIncludePad(true)
        }.build()

        // Calculate character positions for tracking
        calculateCharacterPositions()

        return staticLayout?.height?.plus(paddingTop + paddingBottom) ?: 0
    }

    private fun calculateCharacterPositions() {
        characterRects.clear()
        val layout = staticLayout ?: return

        for (i in textContent.indices) {
            val line = layout.getLineForOffset(i)
            val lineTop = layout.getLineTop(line)
            val lineBottom = layout.getLineBottom(line)
            val lineLeft = layout.getLineLeft(line)
            val characterLeft = layout.getPrimaryHorizontal(i)
            val characterRight = if (i < textContent.length - 1) {
                layout.getPrimaryHorizontal(i + 1)
            } else {
                characterLeft + textPaint.measureText(textContent.substring(i, i + 1))
            }

            characterRects.add(
                Rect(
                    (lineLeft + characterLeft).toInt(),
                    lineTop,
                    (lineLeft + characterRight).toInt(),
                    lineBottom
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Apply background color
        canvas.drawColor(preferences.backgroundColor)

        val layout = staticLayout ?: return
        
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop - scrollY)
        
        // Draw highlighted character range if set
        highlightedCharacterRange?.let { range ->
            drawCharacterHighlight(canvas, range)
        }
        
        // Draw the text
        layout.draw(canvas)
        
        canvas.restore()
        
        // Draw reading progress indicator if enabled
        if (preferences.showReadingProgress) {
            drawProgressIndicator(canvas)
        }
    }

    private fun drawCharacterHighlight(canvas: Canvas, range: IntRange) {
        val highlightPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.novel_text_highlight)
            alpha = 100
        }

        for (i in range) {
            if (i < characterRects.size) {
                val rect = characterRects[i]
                canvas.drawRect(rect, highlightPaint)
            }
        }
    }

    private fun drawProgressIndicator(canvas: Canvas) {
        val progress = _scrollProgress.value
        val indicatorWidth = 4f * context.resources.displayMetrics.density
        val indicatorHeight = height * 0.3f
        val indicatorTop = (height - indicatorHeight) / 2
        val indicatorX = width - indicatorWidth - 8f * context.resources.displayMetrics.density

        // Background
        val backgroundPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.novel_progress_background)
            alpha = 100
        }
        canvas.drawRect(
            indicatorX, indicatorTop,
            indicatorX + indicatorWidth, indicatorTop + indicatorHeight,
            backgroundPaint
        )

        // Progress
        val progressPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.novel_progress_foreground)
        }
        val progressHeight = indicatorHeight * progress
        canvas.drawRect(
            indicatorX, indicatorTop + indicatorHeight - progressHeight,
            indicatorX + indicatorWidth, indicatorTop + indicatorHeight,
            progressPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isScrolling = false
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val deltaY = lastTouchY - event.y
                if (abs(deltaY) > 10) { // Touch slop
                    isScrolling = true
                    scrollBy(deltaY)
                    lastTouchY = event.y
                }
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                if (!isScrolling) {
                    // Single tap - show/hide controls
                    onTextTapped?.invoke()
                } else {
                    // End scrolling - update character position
                    updateCharacterPositionFromScroll()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scrollBy(deltaY: Float) {
        scrollY = (scrollY + deltaY).coerceIn(0f, maxScrollY)
        
        // Update scroll progress
        val progress = if (maxScrollY > 0) scrollY / maxScrollY else 0f
        _scrollProgress.value = progress
        
        onScrollChanged?.invoke(progress)
        invalidate()
    }

    private fun updateCharacterPositionFromScroll() {
        val layout = staticLayout ?: return
        
        // Find the first visible line
        val visibleLineTop = scrollY.toInt()
        val firstVisibleLine = layout.getLineForVertical(visibleLineTop)
        
        // Get the character position at the start of the first visible line
        val characterPos = layout.getLineStart(firstVisibleLine)
        
        _characterPosition.value = characterPos.coerceIn(0, textContent.length)
        onCharacterPositionChanged?.invoke(characterPos)
    }

    /**
     * Set the text content to display
     */
    fun setTextContent(text: String) {
        textContent = text
        requestLayout()
        invalidate()
    }

    /**
     * Apply reader preferences
     */
    fun applyPreferences(newPreferences: NovelReaderPreferences) {
        preferences = newPreferences
        requestLayout()
        invalidate()
    }

    /**
     * Scroll to a specific character position
     */
    fun scrollToCharacterPosition(position: Int) {
        val layout = staticLayout ?: return
        if (position >= textContent.length) return

        val line = layout.getLineForOffset(position)
        val lineTop = layout.getLineTop(line)
        
        scrollY = lineTop.toFloat().coerceIn(0f, maxScrollY)
        
        val progress = if (maxScrollY > 0) scrollY / maxScrollY else 0f
        _scrollProgress.value = progress
        _characterPosition.value = position
        
        invalidate()
    }

    /**
     * Scroll to a progress percentage (0.0 to 1.0)
     */
    fun scrollToProgress(progress: Float) {
        val targetScrollY = (maxScrollY * progress.coerceIn(0f, 1f))
        scrollY = targetScrollY
        
        _scrollProgress.value = progress
        updateCharacterPositionFromScroll()
        
        invalidate()
    }

    /**
     * Highlight a range of characters
     */
    fun highlightCharacterRange(start: Int, end: Int) {
        highlightedCharacterRange = start..end
        invalidate()
    }

    /**
     * Clear character highlighting
     */
    fun clearHighlight() {
        highlightedCharacterRange = null
        invalidate()
    }

    /**
     * Get the current visible character range
     */
    fun getVisibleCharacterRange(): IntRange {
        val layout = staticLayout ?: return 0..0
        
        val topY = scrollY.toInt()
        val bottomY = (scrollY + height).toInt()
        
        val firstLine = layout.getLineForVertical(topY)
        val lastLine = layout.getLineForVertical(bottomY)
        
        val startChar = layout.getLineStart(firstLine)
        val endChar = layout.getLineEnd(lastLine.coerceAtMost(layout.lineCount - 1))
        
        return startChar..endChar.coerceAtMost(textContent.length)
    }

    /**
     * Get reading statistics for the current view
     */
    fun getReadingStats(): NovelReadingStats {
        val visibleRange = getVisibleCharacterRange()
        val visibleText = if (visibleRange.first < textContent.length) {
            textContent.substring(visibleRange.first, visibleRange.last.coerceAtMost(textContent.length))
        } else ""
        
        val wordCount = visibleText.split("\\s+".toRegex()).size
        val characterCount = visibleText.length
        val lineCount = staticLayout?.lineCount ?: 0
        
        return NovelReadingStats(
            totalCharacters = textContent.length,
            visibleCharacters = characterCount,
            totalWords = textContent.split("\\s+".toRegex()).size,
            visibleWords = wordCount,
            totalLines = lineCount,
            currentPosition = _characterPosition.value,
            scrollProgress = _scrollProgress.value
        )
    }
}

/**
 * Reading statistics data class
 */
data class NovelReadingStats(
    val totalCharacters: Int,
    val visibleCharacters: Int,
    val totalWords: Int,
    val visibleWords: Int,
    val totalLines: Int,
    val currentPosition: Int,
    val scrollProgress: Float
)