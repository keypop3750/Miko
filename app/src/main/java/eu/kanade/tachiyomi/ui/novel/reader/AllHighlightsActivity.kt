package eu.kanade.tachiyomi.ui.novel.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.target
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ActivityAllHighlightsBinding

/**
 * Unified highlights page showing all novels that have saved highlights.
 * Each novel is displayed as a horizontal card with its cover art as background.
 */
class AllHighlightsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllHighlightsBinding
    private lateinit var adapter: NovelHighlightsAdapter

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, AllHighlightsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllHighlightsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadNovels()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = NovelHighlightsAdapter { novelData ->
            // Open the per-novel highlights activity
            startActivity(
                NovelHighlightsActivity.newIntent(
                    this,
                    novelTitle = novelData.novelTitle,
                    novelAuthor = novelData.author,
                    posterUrl = novelData.posterUrl,
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

        class CardViewHolder(
            view: View,
            private val onClick: (NovelHighlightManager.NovelHighlightsData) -> Unit,
        ) : RecyclerView.ViewHolder(view) {
            private val coverImage: ImageView = view.findViewById(R.id.cover_image)
            private val titleView: TextView = view.findViewById(R.id.novel_title)
            private val authorView: TextView = view.findViewById(R.id.novel_author)
            private val countView: TextView = view.findViewById(R.id.highlight_count)

            fun bind(data: NovelHighlightManager.NovelHighlightsData) {
                titleView.text = data.novelTitle
                authorView.text = data.author ?: ""
                authorView.isVisible = !data.author.isNullOrBlank()

                val totalHighlights = data.chapters.sumOf { it.highlights.size }
                countView.text = totalHighlights.toString()

                // Load cover with Coil
                coverImage.setImageDrawable(null)
                data.posterUrl?.let { url ->
                    val request = ImageRequest.Builder(itemView.context)
                        .data(url)
                        .target(coverImage)
                        .build()
                    itemView.context.imageLoader.enqueue(request)
                }

                itemView.setOnClickListener { onClick(data) }
            }
        }
    }
}
