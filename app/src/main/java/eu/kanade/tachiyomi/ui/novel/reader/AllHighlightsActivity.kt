package eu.kanade.tachiyomi.ui.novel.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
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
import kotlinx.coroutines.flow.first
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

    private suspend fun findNovelForHighlights(data: NovelHighlightManager.NovelHighlightsData): Novel? {
        // 1. If JSON has a novelId, look up directly by ID (most reliable)
        data.novelId?.let { id ->
            novelRepository.getNovelById(id)?.let { return it }
        }

        val trimmed = data.novelTitle.trim()

        // 2. Exact case-insensitive title match
        novelRepository.getNovelByTitle(trimmed)?.let { return it }

        // 3. If title match fails, try matching by posterUrl (exact, unique per novel)
        if (!data.posterUrl.isNullOrBlank()) {
            try {
                val allNovels = novelRepository.getAllNovels().first()
                allNovels.find { !it.posterUrl.isNullOrBlank() && it.posterUrl == data.posterUrl }?.let { return it }
            } catch (_: Exception) { }
        }

        // 4. If posterUrl fails, try matching by vibrantCoverColor (exact)
        if (data.vibrantCoverColor != null) {
            try {
                val allNovels = novelRepository.getAllNovels().first()
                allNovels.find { it.vibrantCoverColor == data.vibrantCoverColor }?.let { return it }
            } catch (_: Exception) { }
        }

        return null
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
        adapter = NovelHighlightsAdapter(
            onClick = { data, novel ->
                startActivity(
                    NovelHighlightsActivity.newIntent(
                        this,
                        novelTitle = data.novelTitle,
                        novelAuthor = data.author ?: novel?.author,
                        posterUrl = data.posterUrl ?: novel?.posterUrl,
                        vibrantColor = data.vibrantCoverColor ?: novel?.vibrantCoverColor,
                        readerBackgroundColor = ThemeUtil.readerBackgroundColor(
                            preferences.readerTheme().get(),
                            getResourceColor(R.attr.background)
                        ),
                    )
                )
            },
            onMoreClick = { item, position ->
                showOptionsDialog(item, position)
            },
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadNovels() {
        lifecycleScope.launch {
            val manager = NovelHighlightManager(this@AllHighlightsActivity)
            val highlightNovels = manager.getAllNovelsWithHighlights()

            // Look up each novel in the DB for accurate metadata (author, status, cover)
            val enrichedList = withContext(Dispatchers.IO) {
                highlightNovels.map { data ->
                    val dbNovel = findNovelForHighlights(data)
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
        private val onMoreClick: (HighlightItem, Int) -> Unit,
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
            return CardViewHolder(view, onClick, onMoreClick)
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            holder.bind(items[position])
        }

        inner class CardViewHolder(
            view: View,
            private val onClick: (NovelHighlightManager.NovelHighlightsData, Novel?) -> Unit,
            private val onMoreClick: (HighlightItem, Int) -> Unit,
        ) : RecyclerView.ViewHolder(view) {
            private val coverImage: ImageView = view.findViewById(R.id.cover_image)
            private val titleView: TextView = view.findViewById(R.id.novel_title)
            private val authorView: TextView = view.findViewById(R.id.novel_author)
            private val countView: TextView = view.findViewById(R.id.highlight_count)
            private val statusView: TextView = view.findViewById(R.id.novel_status)
            private val moreButton: ImageButton = view.findViewById(R.id.more_button)

            fun bind(item: HighlightItem) {
                val data = item.highlightData
                val novel = item.dbNovel

                // Title from JSON (user-editable)
                titleView.text = data.novelTitle

                // Author from JSON (user-editable), fallback to DB, then Unknown
                authorView.text = data.author ?: novel?.author ?: "Unknown"
                authorView.isVisible = true

                // Status from JSON (user-editable), fallback to DB, then Unknown
                val statusText = data.status?.let { resolveStatusText(it) }
                    ?: novel?.let { resolveStatusText(it.status) }
                    ?: "Unknown"
                statusView.text = statusText
                statusView.isVisible = true

                // Highlight count badge (bottom-right)
                val totalHighlights = data.chapters.sumOf { it.highlights.size }
                countView.text = totalHighlights.toString()

                // Cover art: prioritize JSON posterUrl (user-editable), fallback to DB
                val effectivePosterUrl = data.posterUrl ?: novel?.posterUrl
                val effectiveVibrantColor = data.vibrantCoverColor ?: novel?.vibrantCoverColor

                if (effectiveVibrantColor != null) {
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(effectiveVibrantColor, hsl)
                    hsl[2] = (hsl[2] * 0.5f).coerceIn(0.1f, 0.5f)
                    coverImage.setBackgroundColor(ColorUtils.HSLToColor(hsl))
                } else {
                    coverImage.setBackgroundColor(Color.parseColor("#FF2D2D2D"))
                }

                coverImage.setImageDrawable(null)
                if (!effectivePosterUrl.isNullOrBlank()) {
                    val request = ImageRequest.Builder(itemView.context)
                        .data(effectivePosterUrl)
                        .target(coverImage)
                        .build()
                    itemView.context.imageLoader.enqueue(request)
                }

                itemView.setOnClickListener { onClick(data, novel) }
                moreButton.setOnClickListener { onMoreClick(item, bindingAdapterPosition) }
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

    // ------------------------------------------------------------------
    // Options dialog (Edit / Delete)
    // ------------------------------------------------------------------
    private fun showOptionsDialog(item: HighlightItem, position: Int) {
        val options = arrayOf("Edit", "Delete")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(item.highlightData.novelTitle)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(item, position)
                    1 -> showDeleteConfirmationDialog(item, position)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Edit dialog
    // ------------------------------------------------------------------
    private fun showEditDialog(item: HighlightItem, position: Int) {
        val data = item.highlightData
        val novel = item.dbNovel

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_highlight_novel, null)
        val coverImage = dialogView.findViewById<ImageView>(R.id.novel_cover)
        val titleInput = dialogView.findViewById<EditText>(R.id.title)
        val authorInput = dialogView.findViewById<EditText>(R.id.novel_author)
        val statusSpinner = dialogView.findViewById<eu.kanade.tachiyomi.widget.MaterialSpinnerView>(R.id.novel_status)
        val posterUrlInput = dialogView.findViewById<EditText>(R.id.poster_url)
        val resetCoverBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.reset_cover)

        // Load current cover
        val currentPosterUrl = data.posterUrl ?: novel?.posterUrl
        coverImage.setImageDrawable(null)
        if (!currentPosterUrl.isNullOrBlank()) {
            val request = ImageRequest.Builder(this)
                .data(currentPosterUrl)
                .target(coverImage)
                .build()
            imageLoader.enqueue(request)
        } else {
            coverImage.setImageResource(R.mipmap.ic_launcher)
        }

        // Pre-fill fields
        titleInput.setText(data.novelTitle)
        authorInput.setText(data.author ?: novel?.author ?: "")
        posterUrlInput.setText(data.posterUrl ?: novel?.posterUrl ?: "")

        // Status: prefer JSON, fallback to DB, default to Unknown (0)
        val currentStatus = data.status ?: novel?.status ?: 0
        statusSpinner.setSelection(currentStatus.coerceIn(0, 5))

        // Reset cover clears the poster URL input
        resetCoverBtn.setOnClickListener {
            posterUrlInput.setText("")
            coverImage.setImageResource(R.mipmap.ic_launcher)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edit Novel Info")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val manager = NovelHighlightManager(this)
                val novelKey = NovelHighlightManager.NovelKey(
                    title = data.novelTitle,
                    author = data.author,
                    novelId = data.novelId,
                )
                val newTitle = titleInput.text.toString().trim()
                val newAuthor = authorInput.text.toString().trim().takeIf { it.isNotBlank() }
                val newPosterUrl = posterUrlInput.text.toString().trim().takeIf { it.isNotBlank() }
                val newStatus = statusSpinner.selectedPosition.coerceIn(0, 5)

                manager.updateNovelMetadata(
                    novelKey = novelKey,
                    title = newTitle.takeIf { it.isNotBlank() },
                    author = newAuthor,
                    status = newStatus,
                    posterUrl = newPosterUrl,
                    onComplete = { loadNovels() }
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Delete confirmation
    // ------------------------------------------------------------------
    private fun showDeleteConfirmationDialog(item: HighlightItem, position: Int) {
        val data = item.highlightData
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete all highlights?")
            .setMessage("This will permanently remove all highlights for \"${data.novelTitle}\".")
            .setPositiveButton("Delete") { _, _ ->
                val manager = NovelHighlightManager(this)
                val novelKey = NovelHighlightManager.NovelKey(
                    title = data.novelTitle,
                    author = data.author,
                    novelId = data.novelId,
                )
                manager.deleteAllHighlights(novelKey) {
                    loadNovels()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
