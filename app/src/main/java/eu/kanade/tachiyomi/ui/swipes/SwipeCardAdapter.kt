package eu.kanade.tachiyomi.ui.swipes

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import co.touchlab.kermit.Logger
import coil3.dispose
import coil3.load
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SwipeCardItemBinding
import kotlinx.serialization.Serializable
import yokai.domain.manga.models.MangaCover
import yokai.util.coil.loadManga

/**
 * RecyclerView adapter for swipe cards in CardStackView
 * 
 * Phase 2: Basic adapter with placeholder data
 * Phase 3: Real manga data from sources with prefetch strategy
 * 
 * Uses AsyncListDiffer for efficient updates - only rebinds changed cards
 * instead of all visible cards, preventing image load cancellations
 */
class SwipeCardAdapter : RecyclerView.Adapter<SwipeCardAdapter.SwipeCardViewHolder>() {

    private val diffCallback = object : DiffUtil.ItemCallback<SwipeCardItem>() {
        override fun areItemsTheSame(oldItem: SwipeCardItem, newItem: SwipeCardItem): Boolean {
            // Items are the same if they have the same ID and URL
            return oldItem.id == newItem.id && oldItem.url == newItem.url
        }

        override fun areContentsTheSame(oldItem: SwipeCardItem, newItem: SwipeCardItem): Boolean {
            // Contents are the same if all data matches (data class equality)
            return oldItem == newItem
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwipeCardViewHolder {
        val binding = SwipeCardItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SwipeCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SwipeCardViewHolder, position: Int) {
        val item = differ.currentList[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = differ.currentList.size

    /**
     * Update the card list with new items
     * Uses AsyncListDiffer to calculate diff and only rebind changed items
     */
    fun setItems(newItems: List<SwipeCardItem>) {
        differ.submitList(newItems)
    }

    /**
     * Add more items to the end of the list (for dynamic queue loading)
     */
    fun addItems(newItems: List<SwipeCardItem>) {
        val currentList = differ.currentList.toMutableList()
        currentList.addAll(newItems)
        differ.submitList(currentList)
    }

    /**
     * Remove item at specific position (after swipe)
     */
    fun removeItemAt(position: Int) {
        val currentList = differ.currentList.toMutableList()
        if (position >= 0 && position < currentList.size) {
            currentList.removeAt(position)
            differ.submitList(currentList)
        }
    }

    /**
     * Get item at specific position
     */
    fun getItemAt(position: Int): SwipeCardItem? {
        return if (position >= 0 && position < differ.currentList.size) differ.currentList[position] else null
    }

    /**
     * ViewHolder for swipe cards with gradient fade-in effect
     */
    class SwipeCardViewHolder(
        private val binding: SwipeCardItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // Track last scroll position to determine scroll direction
        private var lastScrollY = 0

        fun bind(item: SwipeCardItem) {
            binding.apply {
                // CRITICAL: Reset scroll position for new card (prevent inheritance)
                scrollView.scrollTo(0, 0)
                
                // Reset gradient opacity to initial state (darker, more visible)
                gradientOverlay.alpha = 0.75f
                
                // Reset dark overlay
                darkOverlay.alpha = 0.0f
                
                // Reset image blur (if Android 12+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    mangaCover.setRenderEffect(null)
                }
                
                // Set manga information - Use DISPLAY properties for metadata-enhanced data
                mangaTitle.text = item.title
                mangaAuthor.text = buildString {
                    // Use enhanced author if available, fallback to source
                    val displayAuthor = item.displayAuthor
                    val hasValidAuthor = displayAuthor.isNotEmpty() && 
                                        displayAuthor.isNotBlank() && 
                                        displayAuthor != "_"
                    
                    if (hasValidAuthor) {
                        append(displayAuthor)
                    } else {
                        append("Unknown")
                    }
                    
                    // Use enhanced chapter count if available
                    val displayChapters = item.displayChapterCount
                    if (displayChapters > 0) {
                        append(" • ")
                        append("$displayChapters chapters")
                    }
                    
                    // Use enhanced status if available
                    val displayStatus = item.displayStatus
                    if (displayStatus.isNotEmpty()) {
                        append(" • ")
                        append(displayStatus)
                    }
                }
                mangaDescription.text = item.displayDescription.ifEmpty { "No description available" }
                mangaTags.text = buildString {
                    // Add score first if available from metadata enhancement
                    item.displayScore?.let { score ->
                        append("%.1f/10".format(score / 10f)) // Convert from 0-100 to 0-10 with /10 format
                        
                        // Add separator if genres exist
                        val genres = item.displayGenres
                        if (genres.isNotEmpty()) {
                            append(" • ")
                            append(genres)
                        }
                    } ?: run {
                        // No score available, just show genres
                        val genres = item.displayGenres
                        if (genres.isNotEmpty()) {
                            append(genres)
                        } else {
                            append("No tags")
                        }
                    }
                }
                
                // METADATA DEBUG BADGE (Phase 1 & 2 - Testing Only)
                if (item.metadataEnhanced && !item.metadataProvider.isNullOrEmpty()) {
                    metadataBadge.visibility = View.VISIBLE
                    metadataBadge.text = when (item.metadataProvider) {
                        "anilist" -> "AniList"
                        "mangadex" -> "MangaDex"
                        "myanimelist" -> "MAL"
                        "kitsu" -> "Kitsu"
                        else -> item.metadataProvider?.uppercase()
                    }
                    
                    // Color code by provider (blue=AniList, green=MangaDex, red=MAL, orange=Kitsu)
                    val badgeColor = when (item.metadataProvider) {
                        "anilist" -> android.graphics.Color.parseColor("#CC1976D2") // Blue
                        "mangadex" -> android.graphics.Color.parseColor("#CC43A047") // Green (Material Green 600)
                        "myanimelist" -> android.graphics.Color.parseColor("#CCD32F2F") // Red (MAL colors)
                        "kitsu" -> android.graphics.Color.parseColor("#CCFF6F00") // Orange
                        else -> android.graphics.Color.parseColor("#CC424242") // Gray fallback
                    }
                    metadataBadge.background.setTint(badgeColor)
                } else {
                    metadataBadge.visibility = View.GONE
                }

                // Load manga cover using loadManga() for better caching and lifecycle management
                // NOTE: For swipe cards, we use mangaId=0 since they're not in library yet
                // We disable MEMORY caching to allow fresh loads after library addition
                // (Disk cache uses different keys for library vs non-library via MangaCoverKeyer)
                // ENHANCEMENT: Use displayCoverUrl which prefers enhanced high-quality covers
                val coverUrl = item.displayCoverUrl
                if (coverUrl.isNotEmpty()) {
                    mangaCover.visibility = View.VISIBLE
                    
                    // Dispose previous load to prevent memory leaks
                    mangaCover.dispose()
                    
                    // Create MangaCover for stable cache key (url + sourceId combination)
                    // Use mangaId=0 for swipe previews (not in library yet)
                    val coverData = MangaCover(
                        mangaId = 0L,  // Swipe cards are not in library, use 0
                        sourceId = item.sourceId,
                        url = coverUrl, // Use enhanced cover URL
                        lastModified = 0,
                        inLibrary = false
                    )
                    
                    // Use loadManga() with disabled memory cache for swipe cards
                    // This allows proper refresh when manga is added to library
                    binding.mangaCover.loadManga(coverData) {
                        memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
                        Logger.d { "📸 [SWIPES] Loading cover for '${item.title}' (no memory cache)" }
                    }
                } else {
                    // No cover URL, show placeholder
                    mangaCover.visibility = View.VISIBLE
                    mangaCover.dispose()
                    mangaCover.setImageResource(R.drawable.appwidget_cover_error)
                }

                // Hide overlays initially (will be shown during swipe)
                leftOverlay.visibility = View.GONE
                rightOverlay.visibility = View.GONE
                
                // Setup scroll effects (gradient expansion, blur, darkening)
                setupScrollEffects()
                
                // Show/hide scroll hint based on description length
                updateScrollHint(item)
            }
        }
        
        /**
         * Setup scroll effects:
         * - Gradient expands from bottom to halfway up
         * - Image gets blurred (Android 12+)
         * - Image gets darker at same rate as blur for better readability
         * 
         * FIXED: Only increases effects when scrolling DOWN, never decreases
         */
        private fun setupScrollEffects() {
            // Reset lastScrollY when setting up (for new card binding)
            lastScrollY = 0
            
            binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val maxScroll = 400f
                val progress = (scrollY.toFloat() / maxScroll).coerceIn(0f, 1f)
                
                // FIXED: Always apply effects based on scroll position, regardless of direction
                // This makes the effects scroll-dependent in both directions
                
                // 1. Gradient expands: starts at 0.6 opacity, goes to 0.95
                val gradientAlpha = 0.6f + (0.35f * progress)
                binding.gradientOverlay.alpha = gradientAlpha
                
                // 2. Dark overlay fades in: 0% to 70% darkness
                binding.darkOverlay.alpha = 0.7f * progress
                
                // 3. Apply blur effect (Android 12+ only)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blurRadius = 20f * progress
                    if (blurRadius > 0f) {
                        val blurEffect = RenderEffect.createBlurEffect(
                            blurRadius, 
                            blurRadius, 
                            Shader.TileMode.CLAMP
                        )
                        binding.mangaCover.setRenderEffect(blurEffect)
                    } else {
                        binding.mangaCover.setRenderEffect(null)
                    }
                }
                
                // Hide scroll hint after scrolling 50px
                if (scrollY > 50) {
                    binding.scrollHint.visibility = View.GONE
                }
                
                // Update lastScrollY for next scroll event
                lastScrollY = scrollY
            }
        }
        
        /**
         * Show scroll hint if description is long enough to scroll
         */
        private fun updateScrollHint(item: SwipeCardItem) {
            binding.scrollHint.post {
                val scrollView = binding.scrollView
                val contentHeight = binding.contentInfo.height
                val scrollViewHeight = scrollView.height
                
                // Show hint if content is scrollable
                val isScrollable = contentHeight > scrollViewHeight && item.description.length > 200
                binding.scrollHint.visibility = if (isScrollable) View.VISIBLE else View.GONE
            }
        }
    }
}

/**
 * Data class representing a swipe card item
 * 
 * Phase 2: Simple data structure with placeholder support
 * Phase 3: Populated from actual manga sources via fromSManga()
 */
@Serializable
data class SwipeCardItem(
    val id: Long = 0,
    val title: String,
    val author: String = "",
    val status: String = "",
    val description: String = "",
    val tags: String = "",
    val coverUrl: String = "",
    val sourceId: Long = 0,
    val url: String = "",
    val chapterCount: Int = 0,
    val detailsLoaded: Boolean = false, // Track if full details have been fetched
    
    // Metadata Enhancement Fields (Phase 1)
    val metadataEnhanced: Boolean = false,
    val metadataProvider: String? = null, // "anilist", "myanimelist", "kitsu"
    val enhancedChapterCount: Int? = null, // Priority 1: Accurate chapter count
    val enhancedGenres: List<String> = emptyList(), // Priority 2
    val enhancedTags: List<String> = emptyList(), // Priority 2
    val enhancedAuthors: List<String> = emptyList(), // Priority 3
    val enhancedArtists: List<String> = emptyList(), // Priority 3
    val enhancedStartDate: String? = null, // Priority 3
    val enhancedEndDate: String? = null, // Priority 3
    val enhancedCoverUrl: String? = null, // Priority 4: Higher quality cover
    val enhancedScore: Float? = null, // Priority 4: Community rating
    val enhancedDescription: String? = null, // Priority 4: Cleaner description
    val enhancedStatus: String? = null, // Priority 5: Publishing status
    val isNsfw: Boolean = false // NSFW flag for filtering
) {
    
    /**
     * Get display-ready chapter count (enhanced or fallback to source)
     */
    val displayChapterCount: Int
        get() = enhancedChapterCount ?: chapterCount
    
    /**
     * Get display-ready genres (enhanced or fallback to source tags)
     */
    val displayGenres: String
        get() = if (enhancedGenres.isNotEmpty()) {
            enhancedGenres.joinToString(", ")
        } else {
            tags
        }
    
    /**
     * Get display-ready authors (enhanced or fallback to source)
     */
    val displayAuthor: String
        get() = if (enhancedAuthors.isNotEmpty()) {
            enhancedAuthors.joinToString(", ")
        } else {
            author
        }
    
    /**
     * Get display-ready cover URL (enhanced or fallback to source)
     */
    val displayCoverUrl: String
        get() = enhancedCoverUrl ?: coverUrl
    
    /**
     * Get display-ready score (returns null if not available)
     */
    val displayScore: Float?
        get() = enhancedScore
    
    /**
     * Get display-ready description (prioritize source, use enhanced as fallback)
     * Source descriptions are often more detailed and comprehensive
     */
    val displayDescription: String
        get() = description.ifEmpty { enhancedDescription ?: "" }
    
    /**
     * Get display-ready status (enhanced or fallback to source)
     */
    val displayStatus: String
        get() = enhancedStatus ?: status
    companion object {
        /**
         * Create SwipeCardItem from SManga and source information
         * Phase 3: Convert source manga to swipe card format
         */
        fun fromSManga(manga: eu.kanade.tachiyomi.source.model.SManga, sourceId: Long, statusText: String): SwipeCardItem {
            return SwipeCardItem(
                id = 0, // Will be set by database if needed
                title = manga.title,
                author = manga.author.orEmpty(),
                status = statusText,
                description = manga.description.orEmpty(),
                tags = manga.getGenres()?.joinToString(", ") ?: "",
                coverUrl = manga.thumbnail_url.orEmpty(),
                sourceId = sourceId,
                url = manga.url
            )
        }
        
        /**
         * Generate placeholder data for testing Phase 2 implementation
         */
        fun generatePlaceholderData(count: Int = 20): List<SwipeCardItem> {
            return (1..count).map { index ->
                SwipeCardItem(
                    id = index.toLong(),
                    title = "Sample Manga Title #$index",
                    author = "Sample Author $index",
                    status = if (index % 3 == 0) "Completed" else "Ongoing",
                    description = "This is a placeholder description for manga #$index. " +
                            "In Phase 3, this will be replaced with real manga data from " +
                            "configured sources. The description can be quite long and will " +
                            "scroll within the card if needed.",
                    tags = listOf("Action", "Adventure", "Fantasy", "Shounen")
                        .shuffled()
                        .take((2..4).random())
                        .joinToString(", ")
                )
            }
        }
    }
}
