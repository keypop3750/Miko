package eu.kanade.tachiyomi.ui.novel.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.target
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ActivityAllHighlightsBinding
import eu.kanade.tachiyomi.ui.base.activity.BaseThemedActivity
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.isDarkMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import yokai.domain.novel.Novel
import yokai.domain.novel.NovelRepository
import yokai.domain.novel.NovelStatus

/**
 * Unified highlights page showing all novels that have saved highlights.
 * Each novel is displayed as a horizontal card with its cover art as background.
 *
 * Uses the novel reader theme so the background matches the user's novel reading preferences.
 * Card metadata (author, status, cover) is fetched from the novel database for accuracy.
 */
class AllHighlightsActivity : BaseThemedActivity() {

    private lateinit var binding: ActivityAllHighlightsBinding
    private lateinit var adapter: NovelHighlightsAdapter
    private val novelRepository: NovelRepository by injectLazy()

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, AllHighlightsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force novel theme since this is a novel-specific activity
        val isDark = isDarkMode(preferences) && preferences.nightMode().get() != AppCompatDelegate.MODE_NIGHT_NO
        val novelTheme = if (isDark) preferences.novelDarkTheme().get() else preferences.novelLightTheme().get()
        setTheme(novelTheme.styleRes)

        // Use the novel reader theme background
        val readerBg = ThemeUtil.readerBackgroundColor(
            preferences.readerTheme().get(),
            getResourceColor(R.attr.background)
        )

        binding = ActivityAllHighlightsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply reader background
        binding.root.setBackgroundColor(readerBg)
        // RecyclerView stays transparent so the theme background shows through gaps

        setupToolbar()
        setupRecyclerView()
        loadNovels()
    }

    override fun onResume() {
        super.onResume()
        // Refresh so edited author/status from detail screen is reflected
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

        // Tint toolbar to match reader theme
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
        adapter = NovelHighlightsAdapter { data, novel ->
            // Open the per-novel highlights activity
            startActivity(
                NovelHighlightsActivity.newIntent(
                    this,
                    novelTitle = data.novelTitle,
                    novelAuthor = novel?.author ?: data.author,
                    posterUrl = novel?.posterUrl ?: data.posterUrl,
                    vibrantColor = novel?.vibrantCoverColor ?: data.vibrantCoverColor,
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
        lifecycleScope.launch {
            val manager = NovelHighlightManager(this@AllHighlightsActivity)
            val highlightNovels = manager.getAllNovelsWithHighlights()

            // Look up each novel in the DB by title for accurate metadata
            val enrichedList = withContext(Dispatchers.IO) {
                highlightNovels.map { data ->
                    val dbNovel = novelRepository.getNovelByTitle(data.novelTitle)
                    HighlightItem(data, dbNovel)
                }
            }

            adapter.submitList(enrichedList)

            binding.emptyView.isVisible = enrichedList.isEmpty()
            binding.recyclerView.isVisible = enrichedList.isNotEmpty()
        }
    }

    /**
     * Combines highlight JSON data with the actual novel DB entry for accurate metadata.
     */
    data class HighlightItem(
        val highlightData: NovelHighlightManager.NovelHighlightsData,
        val dbNovel: Novel?,
    )

    class NovelHighlightsAdapter(
        private val onClick: (NovelHighlightManager.NovelHighlightsData, Novel?) -> Unit,
    ) : RecyclerView.Adapter<NovelHighlightsAdapter.CardViewHolder>() {

        private var items: List<HighlightItem> = emptyList()

        fun submitList(newItems: List<HighlightItem>) {
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
            private val onClick: (NovelHighlightManager.NovelHighlightsData, Novel?) -> Unit,
        ) : RecyclerView.ViewHolder(view) {
            private val coverImage: ImageView = view.findViewById(R.id.cover_image)
            private val titleView: TextView = view.findViewById(R.id.novel_title)
            private val authorView: TextView = view.findViewById(R.id.novel_author)
            private val countView: TextView = view.findViewById(R.id.highlight_count)
            private val statusView: TextView = view.findViewById(R.id.novel_status)

            fun bind(item: HighlightItem) {
                val data = item.highlightData
                val novel = item.dbNovel

                titleView.text = data.novelTitle

                // Author from DB (most accurate), fallback to JSON
                authorView.text = novel?.author ?: data.author ?: "Unknown Author"
                authorView.isVisible = true

                // Status from DB (Ongoing/Completed/etc.), fallback to Unknown
                val statusText = novel?.let { resolveStatusText(it.status) } ?: "Unknown"
                statusView.text = statusText
                statusView.isVisible = true

                // Highlight count badge (top-right)
                val totalHighlights = data.chapters.sumOf { it.highlights.size }
                countView.text = totalHighlights.toString()

                // Cover art: prioritize DB posterUrl, fallback to saved JSON posterUrl
                val effectivePosterUrl = novel?.posterUrl ?: data.posterUrl
                val effectiveVibrantColor = novel?.vibrantCoverColor ?: data.vibrantCoverColor

                // Set a fallback background color on the ImageView so it's not pure black
                if (effectiveVibrantColor != null) {
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(effectiveVibrantColor, hsl)
                    hsl[2] = (hsl[2] * 0.5f).coerceIn(0.1f, 0.5f)
                    coverImage.setBackgroundColor(ColorUtils.HSLToColor(hsl))
                } else {
                    coverImage.setBackgroundColor(Color.parseColor("#FF2D2D2D"))
                }

                // Load cover with Coil
                coverImage.setImageDrawable(null)
                if (!effectivePosterUrl.isNullOrBlank()) {
                    val request = ImageRequest.Builder(itemView.context)
                        .data(effectivePosterUrl)
                        .target(coverImage)
                        .build()
                    itemView.context.imageLoader.enqueue(request)
                }

                itemView.setOnClickListener { onClick(data, novel) }
            }

            private fun resolveStatusText(status: Int): String {
                return when (status) {
                    NovelStatus.ONGOING -> "Ongoing"
                    NovelStatus.COMPLETED -> "Completed"
                    NovelStatus.PAUSED -> "On Hiatus"
                    NovelStatus.DROPPED -> "Cancelled"
                    NovelStatus.STUBBED -> "Stubbed"
                    else -> "Unknown"
                }
            }
        }
    }
}
