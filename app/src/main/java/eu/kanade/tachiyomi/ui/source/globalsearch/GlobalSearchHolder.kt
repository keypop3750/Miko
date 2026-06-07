package eu.kanade.tachiyomi.ui.source.globalsearch

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SourceGlobalSearchControllerCardBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.migration.SearchController
import eu.kanade.tachiyomi.util.system.LocaleHelper

/**
 * Holder that binds the [GlobalSearchItem] containing catalogue cards.
 *
 * Enhanced with:
 * - Loading indicator on right side (replaces arrow) during search
 * - Skeleton placeholders shown by default
 * - Fade transition when results arrive
 *
 * @param view view of [GlobalSearchItem]
 * @param adapter instance of [GlobalSearchAdapter]
 */
class GlobalSearchHolder(view: View, val adapter: GlobalSearchAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    /**
     * Adapter containing manga from search results.
     */
    private val mangaAdapter = GlobalSearchCardAdapter(adapter.controller)

    private var lastBoundResults: List<GlobalSearchMangaItem>? = null

    private val binding = SourceGlobalSearchControllerCardBinding.bind(view)
    
    // Flag to track if skeletons are currently shown
    private var skeletonsPopulated = false

    init {
        // Set layout horizontal.
        binding.recycler.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(view.context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        binding.recycler.adapter = mangaAdapter

        val canShowMore = adapter.controller !is SearchController && adapter.controller.extensionFilter == null
        if (canShowMore) {
            binding.titleWrapper.setOnClickListener {
                adapter.titleClickListener.onTitleClick(bindingAdapterPosition)
            }
        }
    }
    
    /**
     * Populate skeleton placeholder views into the skeleton row.
     */
    private fun populateSkeletons() {
        if (skeletonsPopulated) return
        skeletonsPopulated = true
        
        val skeletonRow = binding.skeletonRow
        skeletonRow.removeAllViews()
        
        val inflater = LayoutInflater.from(itemView.context)
        
        // Add 6 skeleton items to match typical result count
        for (i in 0 until 6) {
            val skeleton = inflater.inflate(R.layout.source_global_search_skeleton_item, skeletonRow, false)
            skeletonRow.addView(skeleton)
        }
    }
    
    /**
     * Show skeleton placeholders.
     */
    private fun showSkeletons() {
        populateSkeletons()
        binding.skeletonContainer.isVisible = true
        binding.skeletonContainer.alpha = 1f
        binding.recycler.isVisible = false
    }
    
    /**
     * Hide skeletons and fade in actual content.
     */
    private fun hideSkeletonsAndShowContent() {
        if (!binding.skeletonContainer.isVisible && binding.recycler.isVisible) return
        
        // Cross-fade: fade out skeletons, fade in recycler
        binding.skeletonContainer.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.skeletonContainer.isVisible = false
            }
            .start()
        
        binding.recycler.alpha = 0f
        binding.recycler.isVisible = true
        binding.recycler.animate()
            .alpha(1f)
            .setDuration(250)
            .start()
    }

    /**
     * Show the loading of source search result.
     * Uses loading indicator on right side (replaces arrow icon).
     * Shows skeleton placeholders while loading.
     *
     * @param item item of card.
     */
    fun bind(item: GlobalSearchItem) {
        val source = item.source
        val results = item.results

        val titlePrefix = if (item.highlighted) "▶" else ""
        val langSuffix = if (source.lang.isNotEmpty()) " (${source.lang})" else ""

        // Set Title with country code if available.
        binding.title.text = titlePrefix + source.name + langSuffix
        binding.subtitle.isVisible = source !is LocalSource
        binding.subtitle.text = LocaleHelper.getLocalizedDisplayName(source.lang)
        
        val canShowMore = adapter.controller !is SearchController && adapter.controller.extensionFilter == null

        when {
            results == null -> {
                // Loading state - show loading indicator (replaces arrow), show skeletons
                binding.inlineProgress.isVisible = true
                binding.titleMoreIcon.isVisible = false
                binding.inlineNoResults.isVisible = false
                binding.sourceCard.isVisible = true
                // Hide legacy indicators
                binding.progress.isVisible = false
                binding.noResults.isVisible = false
                
                // Show skeleton placeholders
                showSkeletons()
            }
            results.isEmpty() -> {
                // No results - show inline "No results" text, hide card area
                binding.inlineProgress.isVisible = false
                binding.titleMoreIcon.isVisible = false // No arrow when no results
                binding.inlineNoResults.isVisible = true
                binding.sourceCard.isVisible = false
                // Hide legacy indicators
                binding.progress.isVisible = false
                binding.noResults.isVisible = false
            }
            else -> {
                // Results available - show arrow immediately, hide loading
                binding.inlineProgress.isVisible = false
                binding.titleMoreIcon.isVisible = canShowMore
                binding.inlineNoResults.isVisible = false
                binding.sourceCard.isVisible = true
                // Hide legacy indicators
                binding.progress.isVisible = false
                binding.noResults.isVisible = false
                
                // Hide skeletons and show content
                hideSkeletonsAndShowContent()
            }
        }
        if (results !== lastBoundResults) {
            mangaAdapter.updateDataSet(results)
            lastBoundResults = results
        }
    }

    fun updateManga(position: Int) = mangaAdapter.notifyItemChanged(position)

    @SuppressLint("NotifyDataSetChanged")
    fun updateAll() = mangaAdapter.notifyDataSetChanged()

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the initialized manga.
     */
    fun setImage(manga: Manga) {
        getHolder(manga)?.setImage(manga)
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param manga the manga to find.
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(manga: Manga): GlobalSearchMangaHolder? {
        mangaAdapter.allBoundViewHolders.forEach { holder ->
            val item = mangaAdapter.getItem(holder.flexibleAdapterPosition)
            if (item != null && item.manga.id!! == manga.id!!) {
                return holder as GlobalSearchMangaHolder
            }
        }

        return null
    }
}
