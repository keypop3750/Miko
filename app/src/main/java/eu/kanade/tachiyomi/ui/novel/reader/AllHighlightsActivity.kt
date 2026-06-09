package eu.kanade.tachiyomi.ui.novel.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.placeholder
import coil3.request.target
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ActivityAllHighlightsBinding
import eu.kanade.tachiyomi.util.system.ThemeUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import uy.kohesive.injekt.injectLazy

/**
 * Unified highlights page showing all novels that have saved highlights.
 * Each novel is displayed as a horizontal card with its cover art as background.
 *
 * Uses the novel reader theme so the background matches the user's novel reading preferences.
 */
class AllHighlightsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllHighlightsBinding
    private lateinit var adapter: NovelHighlightsAdapter
    private val preferences: PreferencesHelper by injectLazy()

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, AllHighlightsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply novel reader theme background before view inflation
        val readerBg = ThemeUtil.readerBackgroundColor(
            preferences.readerTheme().get(),
            getResourceColor(R.attr.background)
        )
        setTheme(android.R.style.Theme_Material_NoActionBar)

        binding = ActivityAllHighlightsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply novel reader background
        binding.root.setBackgroundColor(readerBg)
        binding.recyclerView.setBackgroundColor(readerBg)

        setupToolbar()
        setupRecyclerView()
        loadNovels()
    }

    private fun getResourceColor(attr: Int): Int {
        val ta = theme.obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, Color.WHITE)
        ta.recycle()
        return color
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Tint toolbar to match novel reader theme
        val readerBg = ThemeUtil.readerBackgroundColor(
            preferences.readerTheme().get(),
            getResourceColor(R.attr.background)
        )
        val isLightBg = readerBg == Color.WHITE || ColorUtils.calculateLuminance(readerBg) > 0.5
        val textColor = if (isLightBg) Color.BLACK else Color.WHITE
        binding.toolbar.setTitleTextColor(textColor)
        binding.toolbar.navigationIcon?.setTint(textColor)
        binding.toolbar.setBackgroundColor(ColorUtils.setAlphaComponent(readerBg, 230))
    }

    private fun setupRecyclerView() {
        adapter = NovelHighlightsAdapter { novelData ->
            // Open the per-novel highlights activity, passing saved color from JSON
            startActivity(
                NovelHighlightsActivity.newIntent(
                    this,
                    novelTitle = novelData.novelTitle,
                    novelAuthor = novelData.author,
                    posterUrl = novelData.posterUrl,
                    vibrantColor = novelData.vibrantCoverColor,
                    readerBackgroundColor = ThemeUtil.readerBackgroundColor(
                        preferences.readerTheme().get(),
                        getResourceColor(R.attr.background)
                    ),
                )
            )
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadNovels() {
        val manager = NovelHighlightManager(this)
        val novels = manager.getAllNovelsWithHighlights()
        adapter.submitList(novels)

        binding.emptyView.isVisible = novels.isEmpty()
        binding.recyclerView.isVisible = novels.isNotEmpty()
    }

    class NovelHighlightsAdapter(
        private val onClick: (NovelHighlightManager.NovelHighlightsData) -> Unit,
    ) : RecyclerView.Adapter<NovelHighlightsAdapter.CardViewHolder>() {

        private var items: List<NovelHighlightManager.NovelHighlightsData> = emptyList()
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun submitList(newItems: List<NovelHighlightManager.NovelHighlightsData>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_highlight_novel_card, parent, false)
            return CardViewHolder(view, onClick)
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            holder.bind(items[position])
        }

        inner class CardViewHolder(
            view: View,
            private val onClick: (NovelHighlightManager.NovelHighlightsData) -> Unit,
        ) : RecyclerView.ViewHolder(view) {
            private val coverImage: ImageView = view.findViewById(R.id.cover_image)
            private val titleView: TextView = view.findViewById(R.id.novel_title)
            private val authorView: TextView = view.findViewById(R.id.novel_author)
            private val countView: TextView = view.findViewById(R.id.highlight_count)
            private val statusView: TextView = view.findViewById(R.id.novel_status)

            fun bind(data: NovelHighlightManager.NovelHighlightsData) {
                titleView.text = data.novelTitle
                authorView.text = data.author ?: "Unknown Author"
                authorView.isVisible = true

                val totalHighlights = data.chapters.sumOf { it.highlights.size }
                val chapterCount = data.chapters.size
                countView.text = totalHighlights.toString()

                // Status line: "X highlights across Y chapters | Last: date"
                val lastHighlightDate = data.chapters
                    .flatMap { it.highlights }
                    .maxOfOrNull { it.timestamp }
                    ?.let { dateFormat.format(Date(it)) }
                statusView.text = buildString {
                    append("$totalHighlights highlight${if (totalHighlights != 1) "s" else ""}")
                    append(" across $chapterCount chapter${if (chapterCount != 1) "s" else ""}")
                    if (lastHighlightDate != null) append(" | Last: $lastHighlightDate")
                }

                // Card background: use vibrantCoverColor as fallback when no poster
                val vibrantColor = data.vibrantCoverColor
                if (vibrantColor != null) {
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(vibrantColor, hsl)
                    hsl[2] = (hsl[2] * 0.5f).coerceIn(0.1f, 0.5f) // darken for readability
                    val cardBg = ColorUtils.HSLToColor(hsl)
                    coverImage.setBackgroundColor(cardBg)
                } else {
                    coverImage.setBackgroundColor(Color.parseColor("#FF2D2D2D")) // default dark gray
                }

                // Load cover with Coil (use vibrant color as placeholder)
                coverImage.setImageDrawable(null)
                if (data.posterUrl != null) {
                    val request = ImageRequest.Builder(itemView.context)
                        .data(data.posterUrl)
                        .target(coverImage)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .build()
                    itemView.context.imageLoader.enqueue(request)
                }

                itemView.setOnClickListener { onClick(data) }
            }
        }
    }
}
