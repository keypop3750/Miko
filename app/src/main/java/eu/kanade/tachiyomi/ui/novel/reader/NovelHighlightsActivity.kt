package eu.kanade.tachiyomi.ui.novel.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.NovelHighlightsActivityBinding
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.target
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.ThemeUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.injectLazy
import yokai.domain.novel.NovelRepository

/**
 * Full-page activity for viewing all highlights of a novel.
 * Displays highlights grouped by chapter with professional formatting:
 * - Novel title as toolbar title
 * - Chapter title as sub-heading
 * - Highlight text with colored left bar
 * - Date/time as sub-text
 */
class NovelHighlightsActivity : AppCompatActivity() {

    private lateinit var binding: NovelHighlightsActivityBinding
    private lateinit var highlightManager: NovelHighlightManager
    private lateinit var adapter: HighlightsAdapter

    private var novelTitle: String = ""
    private var novelAuthor: String? = null
    private var posterUrl: String? = null
    private var vibrantColor: Int? = null

    private val preferences: PreferencesHelper by injectLazy()
    private val novelRepository: NovelRepository by injectLazy()

    companion object {
        private const val EXTRA_NOVEL_TITLE = "novel_title"
        private const val EXTRA_NOVEL_AUTHOR = "novel_author"
        private const val EXTRA_NOVEL_POSTER = "novel_poster"
        private const val EXTRA_NOVEL_COLOR = "novel_color"
        private const val EXTRA_READER_BG = "reader_bg_color"

        fun newIntent(context: Context, novelTitle: String, novelAuthor: String?, posterUrl: String? = null, vibrantColor: Int? = null, readerBackgroundColor: Int? = null): Intent {
            return Intent(context, NovelHighlightsActivity::class.java).apply {
                putExtra(EXTRA_NOVEL_TITLE, novelTitle)
                putExtra(EXTRA_NOVEL_AUTHOR, novelAuthor)
                putExtra(EXTRA_NOVEL_POSTER, posterUrl)
                vibrantColor?.let { putExtra(EXTRA_NOVEL_COLOR, it) }
                readerBackgroundColor?.let { putExtra(EXTRA_READER_BG, it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ALWAYS apply the novel reader theme background (not just when passed via intent)
        val readerBg = ThemeUtil.readerBackgroundColor(
            preferences.readerTheme().get(),
            getResourceColor(R.attr.background)
        )

        binding = NovelHighlightsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply novel reader background to the coordinator
        binding.coordinator.setBackgroundColor(readerBg)

        novelTitle = intent.getStringExtra(EXTRA_NOVEL_TITLE) ?: ""
        novelAuthor = intent.getStringExtra(EXTRA_NOVEL_AUTHOR)
        posterUrl = intent.getStringExtra(EXTRA_NOVEL_POSTER)
        vibrantColor = if (intent.hasExtra(EXTRA_NOVEL_COLOR)) intent.getIntExtra(EXTRA_NOVEL_COLOR, 0) else null

        highlightManager = NovelHighlightManager(this)

        // Look up novel from DB for accurate posterUrl and vibrantCoverColor
        val dbNovel = runCatching {
            kotlinx.coroutines.runBlocking {
                novelRepository.getNovelByTitle(novelTitle)
            }
        }.getOrNull()

        // Fallback to DB values if intent didn't provide them
        if (posterUrl == null) posterUrl = dbNovel?.posterUrl
        if (vibrantColor == null) vibrantColor = dbNovel?.vibrantCoverColor

        // Also try loading from saved highlights JSON as last resort
        if (vibrantColor == null || posterUrl == null) {
            val savedData = highlightManager.getAllHighlights(
                NovelHighlightManager.NovelKey(title = novelTitle, author = novelAuthor)
            )
            if (vibrantColor == null) vibrantColor = savedData.vibrantCoverColor
            if (posterUrl == null) posterUrl = savedData.posterUrl
        }

        setupToolbar()
        setupBackdrop()
        setupRecyclerView()
        loadHighlights()
    }

    private fun getResourceColor(attr: Int): Int {
        val ta = theme.obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, Color.WHITE)
        ta.recycle()
        return color
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = binding.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = novelTitle
        toolbar.setNavigationOnClickListener { finish() }

        // Keep toolbar transparent over the blurred backdrop
        toolbar.setBackgroundColor(Color.TRANSPARENT)

        // Tint toolbar text and nav icon to match novel reader theme
        val readerBg = ThemeUtil.readerBackgroundColor(
            preferences.readerTheme().get(),
            getResourceColor(R.attr.background)
        )
        val isLightBg = readerBg == Color.WHITE || ColorUtils.calculateLuminance(readerBg) > 0.5
        val textColor = if (isLightBg) Color.BLACK else Color.WHITE
        toolbar.setTitleTextColor(textColor)
        toolbar.navigationIcon?.setTint(textColor)

        // Use translucent dark status bar instead of solid vibrant color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window?.statusBarColor = Color.parseColor("#66000000")
        }

        // Apply reader background to RecyclerView so the whole page is themed
        binding.recyclerView.setBackgroundColor(readerBg)
        binding.emptyView.setTextColor(textColor)
    }

    private fun setupBackdrop() {
        val backdrop: ImageView = binding.backdrop
        val backdropGradient: View = binding.backdropGradient
        val trueBackdrop: View = binding.trueBackdrop

        // Detect light background for contrast adjustments
        val readerBg = ThemeUtil.readerBackgroundColor(
            preferences.readerTheme().get(),
            getResourceColor(R.attr.background)
        )
        val isLightBg = readerBg == Color.WHITE || ColorUtils.calculateLuminance(readerBg) > 0.7

        posterUrl?.let { url ->
            backdrop.isVisible = true
            backdropGradient.isVisible = true
            trueBackdrop.isVisible = true
            // Subtle image alpha: even on light backgrounds keep it restrained
            // so the overall background never gets too bright
            backdrop.alpha = if (isLightBg) 0.20f else 0.08f
            val request = ImageRequest.Builder(this)
                .data(url)
                .target(backdrop)
                .build()
            imageLoader.enqueue(request)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                backdrop.post {
                    backdrop.setRenderEffect(
                        RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.MIRROR)
                    )
                }
            }
        } ?: run {
            backdrop.isVisible = false
            backdropGradient.isVisible = false
            trueBackdrop.isVisible = false
        }

        // Apply vibrant cover color to backdrop, but darken it so bright covers
        // (e.g. neon green) don't overwhelm the screen
        vibrantColor?.let { color ->
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color, hsl)
            hsl[2] = (hsl[2] * 0.6f).coerceIn(0f, 0.6f) // darken 40%, cap at 60% lightness
            val darkenedColor = ColorUtils.HSLToColor(hsl)
            trueBackdrop.setBackgroundColor(darkenedColor)
            trueBackdrop.isVisible = true
        }

        // Build a very gradual full-screen gradient so the background "seeps"
        // softly into the blurred image with no hard line anywhere.
        val bgColor = readerBg
            ?: (binding.coordinator.background as? android.graphics.drawable.ColorDrawable)?.color
            ?: Color.WHITE
        // Use bgColor@0-alpha instead of Color.TRANSPARENT so the RGB channel
        // stays consistent throughout the gradient. Color.TRANSPARENT is black@0
        // which causes dark muddy interpolation when alpha is very low.
        val transparentBg = ColorUtils.setAlphaComponent(bgColor, 0)
        backdropGradient.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                transparentBg,      // 0.00 – image fully visible, same hue as bg
                transparentBg,      // 0.11
                transparentBg,      // 0.22
                ColorUtils.setAlphaComponent(bgColor, 25),   // 0.33 – faint bg begins
                ColorUtils.setAlphaComponent(bgColor, 60),   // 0.44
                ColorUtils.setAlphaComponent(bgColor, 110),  // 0.55 – mid transition
                ColorUtils.setAlphaComponent(bgColor, 160),  // 0.66
                ColorUtils.setAlphaComponent(bgColor, 210),  // 0.77 – nearly opaque
                bgColor,                // 0.88 – fully opaque
                bgColor,                // 0.99 – solid for rest of screen
            )
        )
    }

    private fun setupRecyclerView() {
        val accentColor = vibrantColor ?: ContextCompat.getColor(this, R.color.colorAccent)
        adapter = HighlightsAdapter(
            accentColor = accentColor,
            onAction = { action, entry, chapterNumber ->
                when (action) {
                    is HighlightAction.Copy -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Highlight", entry.text))
                    }
                    is HighlightAction.Share -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "\"${entry.text}\"\n— From: $novelTitle")
                        }
                        startActivity(Intent.createChooser(intent, "Share Highlight"))
                    }
                    is HighlightAction.Delete -> {
                        highlightManager.deleteHighlight(
                            NovelHighlightManager.NovelKey(novelTitle, novelAuthor),
                            chapterNumber,
                            entry.text,
                            entry.timestamp
                        )
                        loadHighlights()
                    }
                    else -> { /* EditNote handled inline in adapter */ }
                }
            },
            onNoteChanged = { entry, chapterNumber, note ->
                highlightManager.updateHighlightNote(
                    NovelHighlightManager.NovelKey(novelTitle, novelAuthor),
                    chapterNumber,
                    entry.text,
                    entry.timestamp,
                    note
                )
                // Post to next frame to avoid "Cannot call this method while RecyclerView is computing layout"
                binding.recyclerView.post { loadHighlights() }
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadHighlights() {
        val key = NovelHighlightManager.NovelKey(title = novelTitle, author = novelAuthor)
        val data = highlightManager.getAllHighlights(key)
        val items = buildHighlightItems(data)
        adapter.submitList(items)

        binding.emptyView.isVisible = items.isEmpty()
        binding.recyclerView.isVisible = items.isNotEmpty()
    }

    private fun buildHighlightItems(data: NovelHighlightManager.NovelHighlightsData): List<HighlightListItem> {
        val items = mutableListOf<HighlightListItem>()
        val sortedChapters = data.chapters.filter { it.highlights.isNotEmpty() }.sortedBy { it.chapterNumber }
        for (ch in sortedChapters) {
            items.add(HighlightListItem.ChapterHeader(ch.chapterTitle.ifBlank { "Chapter ${ch.chapterNumber}" }))
            for (hl in ch.highlights.sortedBy { it.timestamp }) {
                items.add(HighlightListItem.Highlight(hl, ch.chapterNumber))
            }
        }
        return items
    }

    // --- Adapter ---

    sealed class HighlightListItem {
        data class ChapterHeader(val title: String) : HighlightListItem()
        data class Highlight(val entry: NovelHighlightManager.HighlightEntry, val chapterNumber: Double) : HighlightListItem()
    }

    sealed class HighlightAction {
        object Copy : HighlightAction()
        object Share : HighlightAction()
        object Delete : HighlightAction()
        object EditNote : HighlightAction()
    }

    class HighlightsAdapter(
        private val accentColor: Int,
        private val onAction: (HighlightAction, NovelHighlightManager.HighlightEntry, Double) -> Unit,
        private val onNoteChanged: (NovelHighlightManager.HighlightEntry, Double, String?) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<HighlightListItem> = emptyList()
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        companion object {
            const val TYPE_HEADER = 0
            const val TYPE_HIGHLIGHT = 1
        }

        fun submitList(newItems: List<HighlightListItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is HighlightListItem.ChapterHeader -> TYPE_HEADER
            is HighlightListItem.Highlight -> TYPE_HIGHLIGHT
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_HEADER -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_highlight_chapter_header, parent, false)
                    HeaderViewHolder(view, accentColor)
                }
                else -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_highlight_card, parent, false)
                    HighlightViewHolder(view, accentColor, onAction, onNoteChanged, dateFormat)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is HighlightListItem.ChapterHeader -> (holder as HeaderViewHolder).bind(item.title)
                is HighlightListItem.Highlight -> (holder as HighlightViewHolder).bind(item.entry, item.chapterNumber)
            }
        }

        class HeaderViewHolder(view: View, private val accentColor: Int) : RecyclerView.ViewHolder(view) {
            private val titleView: TextView = view.findViewById(R.id.chapter_header_title)
            private val accentBar: View? = view.findViewById(R.id.chapter_header_accent_bar)
            fun bind(title: String) {
                titleView.text = title
                accentBar?.setBackgroundColor(accentColor)
            }
        }

        class HighlightViewHolder(
            view: View,
            private val accentColor: Int,
            private val onAction: (HighlightAction, NovelHighlightManager.HighlightEntry, Double) -> Unit,
            private val onNoteChanged: (NovelHighlightManager.HighlightEntry, Double, String?) -> Unit,
            private val dateFormat: SimpleDateFormat
        ) : RecyclerView.ViewHolder(view) {
            private val colorBar: View = view.findViewById(R.id.color_bar)
            private val textView: TextView = view.findViewById(R.id.highlight_text)
            private val dateView: TextView = view.findViewById(R.id.highlight_date)
            private val noteView: TextView = view.findViewById(R.id.highlight_note)
            private val addNoteHint: TextView = view.findViewById(R.id.add_note_hint)
            private val noteInput: android.widget.EditText = view.findViewById(R.id.note_input)

            fun bind(entry: NovelHighlightManager.HighlightEntry, chapterNumber: Double) {
                textView.text = "\"${entry.text}\""
                dateView.text = dateFormat.format(Date(entry.timestamp))

                // Color bar
                val color = try {
                    android.graphics.Color.parseColor(entry.color ?: NovelHighlightManager.COLOR_YELLOW)
                } catch (e: Exception) {
                    accentColor
                }
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(color)
                    cornerRadius = 8f
                }
                colorBar.background = drawable

                // Note display
                if (!entry.note.isNullOrBlank()) {
                    noteView.text = entry.note
                    noteView.isVisible = true
                    addNoteHint.isVisible = false
                } else {
                    noteView.isVisible = false
                    addNoteHint.isVisible = true
                }
                noteInput.isVisible = false

                // Style hint and underline in accent color (vibrant cover color)
                addNoteHint.setTextColor(accentColor)
                noteInput.backgroundTintList = android.content.res.ColorStateList.valueOf(accentColor)

                // Tap to edit note inline
                addNoteHint.setOnClickListener {
                    addNoteHint.isVisible = false
                    noteInput.isVisible = true
                    noteInput.setText(entry.note ?: "")
                    noteInput.requestFocus()
                    val imm = itemView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(noteInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }

                noteInput.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                        saveNote(entry, chapterNumber)
                        true
                    } else false
                }

                noteInput.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        saveNote(entry, chapterNumber)
                    }
                }

                // Long-press or tap on note to edit
                noteView.setOnClickListener {
                    noteView.isVisible = false
                    noteInput.isVisible = true
                    noteInput.setText(entry.note ?: "")
                    noteInput.requestFocus()
                    val imm = itemView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(noteInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }

                // Tap for actions (copy, share, delete) on the card body
                itemView.setOnClickListener {
                    showActions(entry, chapterNumber)
                }
            }

            private fun saveNote(entry: NovelHighlightManager.HighlightEntry, chapterNumber: Double) {
                val text = noteInput.text.toString().trim()
                val note = text.takeIf { it.isNotBlank() }
                val imm = itemView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(noteInput.windowToken, 0)
                onNoteChanged(entry, chapterNumber, note)
            }

            private fun showActions(entry: NovelHighlightManager.HighlightEntry, chapterNumber: Double) {
                val options = arrayOf("Copy text", "Share", "Delete")
                androidx.appcompat.app.AlertDialog.Builder(itemView.context)
                    .setTitle("Highlight")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> onAction(HighlightAction.Copy, entry, chapterNumber)
                            1 -> onAction(HighlightAction.Share, entry, chapterNumber)
                            2 -> onAction(HighlightAction.Delete, entry, chapterNumber)
                        }
                    }
                    .show()
            }
        }
    }
}
