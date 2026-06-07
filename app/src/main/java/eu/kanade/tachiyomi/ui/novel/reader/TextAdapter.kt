package eu.kanade.tachiyomi.ui.novel.reader

import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R

/**
 * RecyclerView adapter for novel text content (QuickNovel pattern + Miko extensions).
 * Renders text in paragraph chunks for efficient scrolling.
 * Uses Spanned text from Markwon HTML parsing for rich text support.
 */
class TextAdapter(
    textConfig: TextConfig,
    private val onNavigationClick: ((TextItem.LoadDirection) -> Unit)? = null,
    private val onCommentsClick: ((Long) -> Unit)? = null
) : ListAdapter<TextItem, TextAdapter.TextViewHolder>(TextItemDiffCallback()) {

    // Make textConfig public for theme updates (getter exposed)
    var textConfig: TextConfig = textConfig
        private set

    // Highlight and selection support
    private var highlightManager: NovelHighlightManager? = null
    private var novelTitle: String = ""
    private var novelAuthor: String? = null
    private var chapterTitle: String = ""
    private var chapterNumber: Double = 0.0

    fun setNovelInfo(title: String, author: String?) {
        novelTitle = title
        novelAuthor = author
    }

    fun setChapterInfo(title: String, number: Double) {
        chapterTitle = title
        chapterNumber = number
    }

    fun setHighlightManager(manager: NovelHighlightManager) {
        highlightManager = manager
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder {
        return when (viewType) {
            VIEW_TYPE_PARAGRAPH -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_novel_paragraph, parent, false)
                ParagraphViewHolder(
                    view = view,
                    getConfig = { textConfig },
                    getNovelInfo = { Triple(novelTitle, novelAuthor, "") },
                    getChapterInfo = { chapterTitle to chapterNumber },
                    getHighlightManager = { highlightManager },
                )
            }
            VIEW_TYPE_CHAPTER_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_novel_chapter_header, parent, false)
                ChapterHeaderViewHolder(view) { textConfig }
            }
            VIEW_TYPE_LOADING -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_novel_loading, parent, false)
                LoadingViewHolder(view)
            }
            VIEW_TYPE_ERROR -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_novel_paragraph, parent, false)  // Reuse paragraph layout for error
                ErrorViewHolder(view)
            }
            VIEW_TYPE_CHAPTER_NAVIGATION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_novel_chapter_navigation, parent, false)
                ChapterNavigationViewHolder(view, onNavigationClick)
            }
            VIEW_TYPE_COMMENTS_BUTTON -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_novel_comments_button, parent, false)
                CommentsButtonViewHolder(view, onCommentsClick)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TextItem.Paragraph -> (holder as ParagraphViewHolder).bind(item)
            is TextItem.ChapterHeader -> (holder as ChapterHeaderViewHolder).bind(item)
            is TextItem.Loading -> (holder as LoadingViewHolder).bind(item)
            is TextItem.Error -> (holder as ErrorViewHolder).bind(item)
            is TextItem.ChapterNavigation -> (holder as ChapterNavigationViewHolder).bind(item)
            is TextItem.CommentsButton -> (holder as CommentsButtonViewHolder).bind(item)
        }
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TextItem.Paragraph -> VIEW_TYPE_PARAGRAPH
            is TextItem.ChapterHeader -> VIEW_TYPE_CHAPTER_HEADER
            is TextItem.Loading -> VIEW_TYPE_LOADING
            is TextItem.Error -> VIEW_TYPE_ERROR
            is TextItem.ChapterNavigation -> VIEW_TYPE_CHAPTER_NAVIGATION
            is TextItem.CommentsButton -> VIEW_TYPE_COMMENTS_BUTTON
        }
    }
    
    /**
     * Update text configuration and refresh all visible items.
     * Forces rebind of all items to apply new colors, fonts, alignment, etc.
     */
    fun updateTextConfig(newConfig: TextConfig) {
        textConfig = newConfig
        // Force rebind of all items (more reliable than notifyDataSetChanged for config changes)
        notifyItemRangeChanged(0, itemCount)
    }
    
    /**
     * Find adapter position for a specific chapter and paragraph.
     * Used for restoring reading position.
     */
    fun findItemPosition(chapterId: Long, paragraphIndex: Int): Int {
        return currentList.indexOfFirst { item ->
            item is TextItem.Paragraph && 
            item.chapterId == chapterId && 
            item.paragraphIndex == paragraphIndex
        }
    }
    
    sealed class TextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(item: TextItem)
    }
    
    class ParagraphViewHolder(
        view: View,
        private val getConfig: () -> TextConfig,
        private val getNovelInfo: () -> Triple<String, String?, String>,
        private val getChapterInfo: () -> Pair<String, Double>,
        private val getHighlightManager: () -> NovelHighlightManager?,
    ) : TextViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.paragraph_text)

        override fun bind(item: TextItem) {
            if (item is TextItem.Paragraph) {
                val textConfig = getConfig()
                val rawText = item.text

                // Apply saved highlight spans
                val displayText = applyHighlightSpans(rawText)
                textView.text = displayText

                textView.textSize = textConfig.textSize
                textView.setTextColor(textConfig.textColor)
                textConfig.textFont?.let { textView.typeface = it }
                textView.setLineSpacing(0f, textConfig.lineSpacing)

                when (textConfig.textAlignment) {
                    yokai.core.novel.reader.TextAlignment.LEFT -> {
                        textView.gravity = android.view.Gravity.START
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE
                        }
                    }
                    yokai.core.novel.reader.TextAlignment.CENTER -> {
                        textView.gravity = android.view.Gravity.CENTER_HORIZONTAL
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE
                        }
                    }
                    yokai.core.novel.reader.TextAlignment.RIGHT -> {
                        textView.gravity = android.view.Gravity.END
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE
                        }
                    }
                    yokai.core.novel.reader.TextAlignment.JUSTIFY -> {
                        textView.gravity = android.view.Gravity.START
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
                        }
                    }
                }

                val density = textView.context.resources.displayMetrics.density
                val hPadding = (textConfig.horizontalPadding * density).toInt()
                textView.setPadding(hPadding, 0, hPadding, 0)

                val layoutParams = textView.layoutParams as? ViewGroup.MarginLayoutParams
                if (layoutParams != null) {
                    val spacing = (textConfig.paragraphSpacing * density).toInt()
                    layoutParams.topMargin = spacing / 2
                    layoutParams.bottomMargin = spacing / 2
                    textView.layoutParams = layoutParams
                }

                textView.setTextIsSelectable(textConfig.isTextSelectable)

                // Set custom selection action mode callback for Define + Highlight
                if (textConfig.isTextSelectable) {
                    val (novelTitle, novelAuthor, _) = getNovelInfo()
                    val (chapterTitle, chapterNumber) = getChapterInfo()
                    val activity = textView.context as? android.app.Activity
                    if (activity != null) {
                        val accentColor = androidx.core.content.ContextCompat.getColor(activity, R.color.colorAccent)
                        val highlightColors = HighlightColorUtils.computeColors(accentColor, textConfig.backgroundColor)
                        textView.customSelectionActionModeCallback = NovelSelectionActionModeCallback(
                            activity = activity,
                            textView = textView,
                            novelTitle = novelTitle,
                            chapterTitle = chapterTitle,
                            chapterNumber = chapterNumber,
                            highlightColors = highlightColors,
                            onHighlight = { selectedText, start, end, colorHex ->
                                val manager = getHighlightManager()
                                if (manager != null && novelTitle.isNotBlank()) {
                                    manager.saveHighlight(
                                        novelKey = NovelHighlightManager.NovelKey(
                                            title = novelTitle,
                                            author = novelAuthor,
                                        ),
                                        chapterNumber = chapterNumber,
                                        chapterTitle = chapterTitle,
                                        selectedText = selectedText,
                                        paragraphIndex = item.paragraphIndex,
                                        color = colorHex,
                                    )
                                    // Apply visual span immediately (BackgroundColorSpan works across lines)
                                    if (displayText is android.text.Spannable) {
                                        val color = HighlightColorUtils.fromHex(colorHex)
                                        displayText.setSpan(
                                            NovelHighlightSpan(color),
                                            start,
                                            end,
                                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                // Single-tap detection on existing highlights
                setupHighlightTapDetection(textView, item)
            }
        }

        /**
         * Apply saved highlight spans from the manager onto the paragraph text.
         */
        private fun applyHighlightSpans(text: android.text.Spanned): android.text.Spannable {
            val spannable = android.text.SpannableString(text)
            val manager = getHighlightManager() ?: return spannable
            val (novelTitle, _, _) = getNovelInfo()
            val (_, chapterNumber) = getChapterInfo()
            if (novelTitle.isBlank()) return spannable

            val highlights = manager.getChapterHighlights(
                NovelHighlightManager.NovelKey(title = novelTitle),
                chapterNumber
            )
            if (highlights.isEmpty()) return spannable

            val plainText = text.toString()

            for (hl in highlights) {
                val color = try {
                    HighlightColorUtils.fromHex(hl.color ?: NovelHighlightManager.COLOR_YELLOW)
                } catch (e: Exception) {
                    HighlightColorUtils.fromHex(NovelHighlightManager.COLOR_YELLOW)
                }
                var start = plainText.indexOf(hl.text)
                while (start >= 0) {
                    val end = start + hl.text.length
                    spannable.setSpan(
                        NovelHighlightSpan(color),
                        start,
                        end,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    start = plainText.indexOf(hl.text, end)
                }
            }
            return spannable
        }

        /**
         * Detect single taps on existing highlights and show an action popup.
         */
        private fun setupHighlightTapDetection(textView: TextView, item: TextItem.Paragraph) {
            var downX = 0f
            var downY = 0f
            var downTime = 0L

            textView.setOnTouchListener { _, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        downTime = System.currentTimeMillis()
                        false
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - downTime
                        val distance = kotlin.math.hypot(event.x - downX, event.y - downY)
                        // Only handle as a tap if short duration, small movement, and no active text selection
                        if (duration < 200 && distance < 20 && !textView.hasSelection()) {
                            val offset = getOffsetForPosition(textView, event.x, event.y)
                            if (offset >= 0) {
                                val spannable = textView.text as? android.text.Spannable
                                val spans = spannable?.getSpans(offset, offset, NovelHighlightSpan::class.java)
                                if (!spans.isNullOrEmpty()) {
                                    val span = spans.first()
                                    val spanStart = spannable.getSpanStart(span)
                                    val spanEnd = spannable.getSpanEnd(span)
                                    val highlightText = spannable.substring(spanStart, spanEnd)
                                    showHighlightActions(textView, highlightText, spanStart, spanEnd, item, event.x, event.y)
                                    return@setOnTouchListener true
                                }
                            }
                        }
                        false
                    }
                    else -> false
                }
            }
        }

        private fun getOffsetForPosition(textView: TextView, x: Float, y: Float): Int {
            val layout = textView.layout ?: return -1
            val line = layout.getLineForVertical((y - textView.totalPaddingTop).toInt())
            if (line < 0 || line >= layout.lineCount) return -1
            return layout.getOffsetForHorizontal(line, x - textView.totalPaddingLeft)
        }

        private fun showHighlightActions(
            textView: TextView,
            highlightText: String,
            spanStart: Int,
            spanEnd: Int,
            item: TextItem.Paragraph,
            touchX: Float,
            touchY: Float
        ) {
            val activity = textView.context as? android.app.Activity ?: return
            val (novelTitle, novelAuthor, _) = getNovelInfo()
            val (chapterTitle, chapterNumber) = getChapterInfo()
            val manager = getHighlightManager() ?: return

            // Find the stored highlight entry
            val highlights = manager.getChapterHighlights(
                NovelHighlightManager.NovelKey(title = novelTitle, author = novelAuthor),
                chapterNumber
            )
            val match = highlights.find { highlightText.contains(it.text) || it.text.contains(highlightText) }

            showFloatingActionBar(textView, touchX, touchY, listOf(
                "Copy" to {
                    val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Highlight", highlightText))
                },
                "Share" to {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "\"$highlightText\"\n— From: $novelTitle")
                    }
                    activity.startActivity(android.content.Intent.createChooser(intent, "Share Highlight"))
                },
                "Delete" to {
                    if (match != null) {
                        manager.deleteHighlight(
                            NovelHighlightManager.NovelKey(title = novelTitle, author = novelAuthor),
                            chapterNumber,
                            match.text,
                            match.timestamp
                        )
                        val spannable = textView.text as? android.text.Spannable
                        spannable?.getSpans(spanStart, spanEnd, NovelHighlightSpan::class.java)?.forEach {
                            spannable.removeSpan(it)
                        }
                    }
                }
            ))
        }

        private fun showFloatingActionBar(
            anchorView: TextView,
            touchX: Float,
            touchY: Float,
            actions: List<Pair<String, () -> Unit>>
        ) {
            val context = anchorView.context
            val density = context.resources.displayMetrics.density

            val container = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                elevation = 8 * density
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 24 * density
                    setColor(android.graphics.Color.parseColor("#E6222222"))
                }
            }

            val popup = android.widget.PopupWindow(
                container,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }

            for ((label, action) in actions) {
                val btn = android.widget.TextView(context).apply {
                    text = label
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 14f
                    setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
                    setOnClickListener {
                        popup.dismiss()
                        action()
                    }
                }
                container.addView(btn)
            }

            // Measure and position above the tap point
            container.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val x = location[0] + touchX.toInt() - container.measuredWidth / 2
            val y = location[1] + touchY.toInt() - container.measuredHeight - (16 * density).toInt()
            popup.showAtLocation(anchorView, android.view.Gravity.NO_GRAVITY, x.coerceAtLeast(0), y.coerceAtLeast(0))
        }

        private fun showInlineNoteDialog(
            activity: android.app.Activity,
            textView: TextView,
            highlightText: String,
            spanStart: Int,
            spanEnd: Int,
            manager: NovelHighlightManager,
            novelTitle: String,
            novelAuthor: String?,
            chapterNumber: Double,
            chapterTitle: String,
            paragraphIndex: Int
        ) {
            val highlights = manager.getChapterHighlights(
                NovelHighlightManager.NovelKey(title = novelTitle, author = novelAuthor),
                chapterNumber
            )
            val match = highlights.find { highlightText.contains(it.text) || it.text.contains(highlightText) }
            val existingNote = match?.note

            val editText = android.widget.EditText(activity).apply {
                setText(existingNote ?: "")
                hint = "Add a note..."
                setPadding(32, 16, 32, 16)
            }
            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Note")
                .setView(editText)
                .setPositiveButton("Save") { _, _ ->
                    val note = editText.text.toString().takeIf { it.isNotBlank() }
                    if (match != null) {
                        manager.updateHighlightNote(
                            NovelHighlightManager.NovelKey(title = novelTitle, author = novelAuthor),
                            chapterNumber,
                            match.text,
                            match.timestamp,
                            note
                        )
                    } else {
                        manager.saveHighlight(
                            NovelHighlightManager.NovelKey(title = novelTitle, author = novelAuthor),
                            chapterNumber,
                            chapterTitle,
                            highlightText,
                            paragraphIndex = paragraphIndex,
                            note = note
                        )
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    class ChapterHeaderViewHolder(
        view: View,
        private val getConfig: () -> TextConfig
    ) : TextViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.chapter_title)
        
        override fun bind(item: TextItem) {
            if (item is TextItem.ChapterHeader) {
                val textConfig = getConfig()
                titleView.text = item.chapterTitle
                titleView.setTextColor(textConfig.textColor)
                // DO NOT set background - RecyclerView handles background
            }
        }
    }
    
    class LoadingViewHolder(view: View) : TextViewHolder(view) {
        override fun bind(item: TextItem) {
            // Loading indicator is static - no binding needed
        }
    }
    
    class ErrorViewHolder(view: View) : TextViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.paragraph_text)
        
        override fun bind(item: TextItem) {
            if (item is TextItem.Error) {
                textView.text = "Error: ${item.errorMessage}\n\n${if (item.canRetry) "Tap to retry" else ""}"
                textView.textSize = 16f
                textView.setTextColor(android.graphics.Color.RED)
            }
        }
    }
    
    class ChapterNavigationViewHolder(
        view: View,
        private val onNavigationClick: ((TextItem.LoadDirection) -> Unit)?
    ) : TextViewHolder(view) {
        private val button: com.google.android.material.button.MaterialButton = view.findViewById(R.id.navigation_button)
        
        override fun bind(item: TextItem) {
            if (item is TextItem.ChapterNavigation) {
                // Set button text based on direction and enabled state
                val buttonText = when {
                    !item.isEnabled && item.direction == TextItem.LoadDirection.NEXT -> "No more chapters"
                    item.direction == TextItem.LoadDirection.PREVIOUS -> "Previous Chapter"
                    item.direction == TextItem.LoadDirection.NEXT -> "Next Chapter"
                    else -> ""
                }
                button.text = buttonText
                button.isEnabled = item.isEnabled
                button.alpha = if (item.isEnabled) 1.0f else 0.5f
                
                // Set click listener
                if (item.isEnabled) {
                    button.setOnClickListener {
                        onNavigationClick?.invoke(item.direction)
                    }
                } else {
                    button.setOnClickListener(null)
                }
            }
        }
    }
    
    class CommentsButtonViewHolder(
        view: View,
        private val onCommentsClick: ((Long) -> Unit)?
    ) : TextViewHolder(view) {
        private val button: com.google.android.material.button.MaterialButton = view.findViewById(R.id.comments_button)
        
        override fun bind(item: TextItem) {
            if (item is TextItem.CommentsButton) {
                val countText = if (item.commentCount > 0) "${item.commentCount} Comments" else "Comments"
                button.text = countText
                button.setOnClickListener {
                    onCommentsClick?.invoke(item.chapterId)
                }
            }
        }
    }
    
    companion object {
        const val VIEW_TYPE_PARAGRAPH = 0
        const val VIEW_TYPE_CHAPTER_HEADER = 1
        const val VIEW_TYPE_LOADING = 2
        const val VIEW_TYPE_ERROR = 3
        const val VIEW_TYPE_CHAPTER_NAVIGATION = 4
        const val VIEW_TYPE_COMMENTS_BUTTON = 5
    }
}

/**
 * DiffUtil callback for efficient list updates.
 */
class TextItemDiffCallback : DiffUtil.ItemCallback<TextItem>() {
    override fun areItemsTheSame(oldItem: TextItem, newItem: TextItem): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: TextItem, newItem: TextItem): Boolean {
        return oldItem == newItem
    }
}

/**
 * Text items for RecyclerView (sealed class for type safety).
 * Compatible with Miko's existing Markwon-based HTML rendering.
 */
sealed class TextItem {
    abstract val id: Long
    
    /**
     * Paragraph item - Contains pre-rendered Spanned text with position tracking.
     * Uses Spanned (from Markwon) instead of String for rich HTML formatting.
     */
    data class Paragraph(
        override val id: Long,
        val chapterId: Long,
        val paragraphIndex: Int,
        val text: Spanned,  // Changed from String to Spanned for Markwon compatibility
        val startCharIndex: Int,
        val endCharIndex: Int
    ) : TextItem()
    
    /**
     * Chapter header item - Visual separator between chapters.
     */
    data class ChapterHeader(
        override val id: Long,
        val chapterId: Long,
        val chapterTitle: String,
        val chapterNumber: String = ""  // Added for compatibility with NovelContentItem
    ) : TextItem()
    
    /**
     * Loading indicator item - Shows chapter loading progress.
     */
    data class Loading(
        override val id: Long,
        val chapterId: Long,
        val loadingMessage: String = "Loading chapter..."
    ) : TextItem()
    
    /**
     * Error item - Shows chapter loading failures with retry option.
     */
    data class Error(
        override val id: Long,
        val chapterId: Long,
        val errorMessage: String,
        val canRetry: Boolean = true
    ) : TextItem()
    
    /**
     * Chapter navigation item - Clickable button for Previous/Next chapter navigation.
     * Displayed at top (PREVIOUS) and bottom (NEXT) of chapter content.
     */
    data class ChapterNavigation(
        override val id: Long,
        val direction: LoadDirection,
        val chapterTitle: String,
        val isEnabled: Boolean
    ) : TextItem()
    
    /**
     * Comments button item - Shows a button to view chapter comments.
     * Only displayed if the source supports comments.
     */
    data class CommentsButton(
        override val id: Long,
        val chapterId: Long,
        val commentCount: Int = 0
    ) : TextItem()
    
    enum class LoadDirection { PREVIOUS, NEXT }
}
