package eu.kanade.tachiyomi.ui.swipes

import android.app.Activity
import android.content.Context
import android.widget.Toast
import co.touchlab.kermit.Logger
import coil3.imageLoader
import eu.kanade.tachiyomi.data.database.models.create
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.moveCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.data.DatabaseHandler
import yokai.data.swipes.SwipesBlacklistRepositoryImpl
import yokai.data.swipes.SwipesHistoryRepositoryImpl
import yokai.domain.manga.MangaRepository
import yokai.domain.manga.interactor.InsertManga
import yokai.domain.swipes.SwipesBlacklistRepository
import yokai.domain.swipes.SwipesHistoryRepository
import kotlin.time.Duration.Companion.seconds

/**
 * Presenter for SwipesActivity
 * Manages recommendation queue, data fetching, and state management
 * 
 * Phase 3: Basic implementation with source integration
 * Phase 3.2: Database integration for blacklist and history
 * Phase 3.3: Library exclusion to prevent recommending owned manga
 * Phase 4+: Advanced filtering, preferences, and queue optimization
 */
class SwipesPresenter(
    private val sourceManager: SourceManager = Injekt.get(),
    databaseHandler: DatabaseHandler = Injekt.get(),
    private val context: Context
) {
    // Metadata Enhancement Service (Phase 1) - Injected lazily
    private val metadataEnhancementService: eu.kanade.tachiyomi.data.metadata.MetadataEnhancementService by lazy { 
        Injekt.get() 
    }
    private val repository: SwipesRepository = SwipesRepository(sourceManager)
    private val insertManga: InsertManga = Injekt.get()
    private val mangaRepository: MangaRepository = Injekt.get()
    
    // Database repositories for persistent blacklist and history
    private val blacklistRepository: SwipesBlacklistRepository = SwipesBlacklistRepositoryImpl(databaseHandler)
    private val historyRepository: SwipesHistoryRepository = SwipesHistoryRepositoryImpl(databaseHandler)
    
    // SharedPreferences for queue caching
    private val prefs by lazy {
        context.getSharedPreferences("swipes_queue_cache", Context.MODE_PRIVATE)
    }
    
    // JSON serializer with lenient parsing for safety
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val presenterScope = CoroutineScope(Job() + Dispatchers.Main)

    // UI State
    private val _uiState = MutableStateFlow<SwipesUiState>(SwipesUiState.Loading)
    val uiState: StateFlow<SwipesUiState> = _uiState.asStateFlow()

    // Current queue of cards
    private val _cards = MutableStateFlow<List<SwipeCardItem>>(emptyList())
    val cards: StateFlow<List<SwipeCardItem>> = _cards.asStateFlow()

    // Queue management
    private val currentMangaUrls = mutableSetOf<String>()
    private val blacklistedUrls = mutableSetOf<String>() // Loaded from database on onCreate
    private val libraryUrls = mutableSetOf<String>() // Loaded from library on onCreate
    
    // Details cache to avoid re-fetching
    private val detailsCache = mutableMapOf<String, SwipeCardItem>()
    
    // Prefetch management
    private val prefetchLookahead = 5 // Number of cards ahead to prefetch details
    private val prefetchingPositions = mutableSetOf<Int>() // Track ongoing prefetch operations

    // Filters (Phase 4)
    private var currentFilters = SwipesFilters.DEFAULT
    
    // Search (Phase 4.2)
    private val _searchQuery = MutableStateFlow<String>("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private var isSearchActive = false
    
    // Settings (TODO Phase 4.3: Load from preferences)
    private var preloadCount = 15
    private var refreshThreshold = 5
    
    // Pending swipe state for restoration if user navigates away
    private var pendingSwipe: SwipeCardItem? = null
    private val _isProcessingSwipe = MutableStateFlow(false)
    val isProcessingSwipe: StateFlow<Boolean> = _isProcessingSwipe.asStateFlow()

    /**
     * Initialize presenter and load initial recommendations
     * Phase 3.2: Load blacklist from database and library URLs
     * Phase 3.3: Library exclusion to prevent recommending owned manga
     * Phase 3.4: Queue caching for session persistence
     */
    fun onCreate() {
        Logger.d { "📱 [SWIPES] Presenter onCreate()" }
        
        presenterScope.launch {
            try {
                // Load blacklist from database
                val blacklistedPairs = blacklistRepository.getAllBlacklistedUrls()
                blacklistedUrls.clear()
                blacklistedUrls.addAll(blacklistedPairs.map { "${it.first}|${it.second}" })
                Logger.d { "📱 [SWIPES] Loaded ${blacklistedUrls.size} blacklisted manga from database" }
                
                // Load library manga URLs to prevent recommending owned manga
                val libraryManga = mangaRepository.getFavorites()
                libraryUrls.clear()
                libraryUrls.addAll(libraryManga.map { it.url })
                Logger.d { "📱 [SWIPES] Loaded ${libraryUrls.size} library manga for exclusion" }
                
                // Try to restore cached queue
                val cachedQueue = restoreQueueFromCache()
                if (cachedQueue.isNotEmpty()) {
                    Logger.d { "📱 [SWIPES] Restored ${cachedQueue.size} cards from cache - showing immediately" }
                    currentMangaUrls.clear()
                    currentMangaUrls.addAll(cachedQueue.map { it.url })

                    // Show cached cards IMMEDIATELY without blocking on metadata enhancement
                    _cards.value = cachedQueue
                    _uiState.value = SwipesUiState.Success(cachedQueue.size)

                    // Prefetch details for restored cards (starting from position 0)
                    prefetchUpcomingCards(0)
                } else {
                    Logger.d { "📱 [SWIPES] No cache found, loading fresh recommendations" }
                    // Load initial recommendations
                    loadInitialRecommendations()
                }
                
            } catch (e: Exception) {
                Logger.e(e) { "📱 [SWIPES] Failed to load blacklist/library from database" }
                // Still try to load recommendations
                loadInitialRecommendations()
            }
        }
    }

    /**
     * Load initial batch of recommendations
     * Phase 4.1: Now uses filters for source selection and NSFW filtering
     */
    fun loadInitialRecommendations() {
        Logger.d { "📱 [SWIPES] Loading initial recommendations (count: $preloadCount)" }
        _uiState.value = SwipesUiState.Loading

        presenterScope.launch {
            try {
                // Get enabled sources from filters (null = all sources)
                val enabledSourceIds = if (currentFilters.enabledSourceIds.isEmpty()) {
                    null // All sources
                } else {
                    currentFilters.enabledSourceIds
                }

                val recommendations = repository.fetchRecommendations(
                    count = preloadCount,
                    excludeNsfw = currentFilters.excludeNsfw,
                    enabledSourceIds = enabledSourceIds,
                    blacklistedMangaUrls = blacklistedUrls,
                    libraryMangaUrls = libraryUrls
                )

                if (recommendations.isEmpty()) {
                    Logger.w { "📱 [SWIPES] No recommendations found - showing empty state" }
                    _uiState.value = SwipesUiState.NoSources
                    _cards.value = emptyList()
                } else {
                    Logger.d { "📱 [SWIPES] Loaded ${recommendations.size} recommendations from sources" }

                    // Convert MangaWithSource to SwipeCardItem (basic info only)
                    val cardItems = recommendations.map { mangaWithSource ->
                        val statusText = repository.getStatusText(mangaWithSource.manga.status)

                        SwipeCardItem.fromSManga(
                            manga = mangaWithSource.manga,
                            sourceId = mangaWithSource.sourceId,
                            statusText = statusText
                        ).also {
                            currentMangaUrls.add(it.url)
                        }
                    }

                    // SHOW CARDS IMMEDIATELY with basic info - don't wait for details
                    _cards.value = cardItems
                    _uiState.value = SwipesUiState.Success(cardItems.size)
                    Logger.d { "📱 [SWIPES] UI visible with ${cardItems.size} cards (details loading in background)" }

                    // Prefetch details in background WITHOUT blocking UI
                    prefetchDetailsForInitialCards(cardItems)
                }
            } catch (e: Exception) {
                Logger.e(e) { "📱 [SWIPES] Error loading recommendations: ${e.message}" }
                _uiState.value = SwipesUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Load more recommendations when running low on cards
     * Called when cards remaining <= refreshThreshold
     */
    fun loadMoreRecommendations() {
        val currentCount = _cards.value.size
        if (currentCount > refreshThreshold) {
            Logger.d { "📱 [SWIPES] Not loading more - still have $currentCount cards (threshold: $refreshThreshold)" }
            return
        }

        Logger.d { "📱 [SWIPES] Loading more recommendations (current: $currentCount, adding: $refreshThreshold)" }

        presenterScope.launch {
            try {
                val moreRecommendations = repository.fetchMoreRecommendations(
                    count = refreshThreshold,
                    currentMangaUrls = currentMangaUrls,
                    excludeNsfw = currentFilters.excludeNsfw,
                    enabledSourceIds = null,
                    blacklistedMangaUrls = blacklistedUrls,
                    libraryMangaUrls = libraryUrls
                )

                if (moreRecommendations.isNotEmpty()) {
                    Logger.d { "📱 [SWIPES] Loaded ${moreRecommendations.size} more raw recommendations" }
                    
                    val newCards = moreRecommendations.map { mangaWithSource ->
                        val statusText = repository.getStatusText(mangaWithSource.manga.status)
                        
                        SwipeCardItem.fromSManga(
                            manga = mangaWithSource.manga,
                            sourceId = mangaWithSource.sourceId,
                            statusText = statusText
                        ).also {
                            currentMangaUrls.add(it.url)
                        }
                    }
                    
                    // CRITICAL FIX: Use PARALLEL fetching like prefetchDetailsForInitialCards
                    // Sequential mapNotNull was causing HTTP 429 rate limit errors
                    Logger.d { "📱 [SWIPES] Prefetching details for ${newCards.size} new cards (PARALLEL)..." }
                    
                    // Use semaphore to limit concurrent requests (same as initial load)
                    val maxConcurrent = 8
                    val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrent)
                    
                    val detailedCards = withContext(Dispatchers.IO) {
                        newCards.map { card ->
                            async {
                                semaphore.acquire()
                                try {
                                    withTimeoutOrNull(8.seconds) {
                                        try {
                                            fetchDetailsSync(card)
                                        } catch (e: Exception) {
                                            Logger.w(e) { "📱 [SWIPES] Failed to load details for ${card.title}" }
                                            null
                                        }
                                    } ?: run {
                                        Logger.w { "📱 [SWIPES] Timeout loading details for ${card.title}" }
                                        null
                                    }
                                } finally {
                                    semaphore.release()
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }
                    
                    Logger.d { "📱 [SWIPES] Successfully loaded ${detailedCards.size}/${newCards.size} cards with details" }
                    
                    // POST-FETCH NSFW RE-FILTERING: Now that we have full details, re-check NSFW status
                    val afterNsfwFilter = if (currentFilters.excludeNsfw) {
                        val beforeCount = detailedCards.size
                        val filtered = detailedCards.filterNot { detailedCard ->
                            // Reconstruct SManga for NSFW check with full details
                            val manga = eu.kanade.tachiyomi.source.model.SManga.create().apply {
                                url = detailedCard.url
                                title = detailedCard.title
                                author = detailedCard.author
                                description = detailedCard.description
                                genre = detailedCard.tags
                                thumbnail_url = detailedCard.coverUrl
                            }
                            
                            repository.isNsfwPublic(manga).also { isNsfw ->
                                if (isNsfw) {
                                    Logger.d { "📱 [SWIPES] Post-fetch NSFW filter removing: ${detailedCard.title}" }
                                }
                            }
                        }
                        Logger.d { "📱 [SWIPES] Post-fetch NSFW filter: ${filtered.size}/${beforeCount} cards retained" }
                        filtered
                    } else {
                        detailedCards
                    }
                    
                    // METADATA ENHANCEMENT (Phase 1): Background enhancement for new cards
                    Logger.d { "📊 [METADATA] Background enhancing ${afterNsfwFilter.size} new cards..." }
                    Logger.d { "📊 [METADATA] MetadataEnhancementService instance: $metadataEnhancementService" }
                    val enhancedNewCards = try {
                        Logger.d { "📊 [METADATA] Calling enhanceCards() for background cards..." }
                        val result = metadataEnhancementService.enhanceCards(afterNsfwFilter)
                        Logger.d { "📊 [METADATA] Background enhancement completed, ${result.count { it.metadataEnhanced }} cards enhanced" }
                        result
                    } catch (e: Exception) {
                        Logger.e(e) { "📊 [METADATA] Background enhancement failed with exception: ${e.message}" }
                        afterNsfwFilter // Fallback to unenhanced
                    }
                    
                    // Add enhanced cards to end of current queue
                    _cards.value = _cards.value + enhancedNewCards
                    
                    // Save updated queue to cache
                    saveQueueToCache()
                    
                    val enhancedCount = enhancedNewCards.count { it.metadataEnhanced }
                    Logger.d { 
                        "📱 [SWIPES] Queue now has ${_cards.value.size} cards " +
                        "(${enhancedCount}/${enhancedNewCards.size} new cards metadata-enhanced, saved to cache)" 
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "📱 [SWIPES] Error loading more recommendations: ${e.message}" }
                // Don't update error state for background loading failures
            }
        }
    }

    /**
     * Handle card swiped left (skip/blacklist)
     * 
     * @param card The swiped card
     */
    fun onCardSwipedLeft(card: SwipeCardItem) {
        Logger.d { "📱 [SWIPES] Card swiped LEFT: ${card.title}" }
        
        // Add to blacklist database (permanent)
        presenterScope.launch {
            try {
                val urlKey = "${card.url}|${card.sourceId}"
                blacklistedUrls.add(urlKey)
                
                blacklistRepository.insert(
                    mangaUrl = card.url,
                    sourceId = card.sourceId,
                    mangaTitle = card.title,
                    coverUrl = card.coverUrl,
                    blacklistedAt = System.currentTimeMillis()
                )
                Logger.d { "📱 [SWIPES] Added to blacklist database: ${card.title}" }
                
                // Track in history (swipe-left)
                historyRepository.insert(
                    mangaUrl = card.url,
                    sourceId = card.sourceId,
                    mangaTitle = card.title,
                    mangaAuthor = card.author,
                    coverUrl = card.coverUrl,
                    swipeDirection = "left",
                    swipedAt = System.currentTimeMillis()
                )
                Logger.d { "📱 [SWIPES] Added to history database: swipe-left for ${card.title}" }
                
            } catch (e: Exception) {
                Logger.e(e) { "📱 [SWIPES] Failed to save swipe-left to database" }
            }
        }
        
        // Remove from current URLs
        currentMangaUrls.remove(card.url)
        
        // Check if we need to load more
        val remaining = _cards.value.size
        if (remaining <= refreshThreshold) {
            Logger.d { "📱 [SWIPES] Low on cards ($remaining <= $refreshThreshold), loading more" }
            loadMoreRecommendations()
        }
    }

    /**
     * Handle card swiped right (add to library)
     * 
     * @param activity Activity context for showing category selection dialog
     * @param card The swiped card
     */
    fun onCardSwipedRight(activity: Activity, card: SwipeCardItem) {
        Logger.d { "📱 [SWIPES] Card swiped RIGHT: ${card.title}" }
        
        // Mark as processing and save pending state
        _isProcessingSwipe.value = true
        pendingSwipe = card
        
        // Track in history (swipe-right)
        presenterScope.launch {
            try {
                historyRepository.insert(
                    mangaUrl = card.url,
                    sourceId = card.sourceId,
                    mangaTitle = card.title,
                    mangaAuthor = card.author,
                    coverUrl = card.coverUrl,
                    swipeDirection = "right",
                    swipedAt = System.currentTimeMillis()
                )
                Logger.d { "📱 [SWIPES] Added to history database: swipe-right for ${card.title}" }
            } catch (e: Exception) {
                Logger.e(e) { "📱 [SWIPES] Failed to save swipe-right to database" }
            }
        }
        
        // Add to library with category selection
        addMangaToLibrary(activity, card)
        
        // Add to library URLs to prevent future recommendations
        libraryUrls.add(card.url)
        currentMangaUrls.remove(card.url)
        
        // Check if we need to load more
        val remaining = _cards.value.size
        if (remaining <= refreshThreshold) {
            Logger.d { "📱 [SWIPES] Low on cards ($remaining <= $refreshThreshold), loading more" }
            loadMoreRecommendations()
        }
    }

    /**
     * Remove card from queue (after swipe animation completes)
     */
    fun removeCard(position: Int) {
        if (position >= 0 && position < _cards.value.size) {
            val updatedCards = _cards.value.toMutableList()
            updatedCards.removeAt(position)
            _cards.value = updatedCards
            
            Logger.d { "📱 [SWIPES] Removed card at position $position, ${updatedCards.size} cards remaining" }
            
            // Update empty state if needed
            if (updatedCards.isEmpty()) {
                Logger.d { "📱 [SWIPES] No more cards - showing empty state" }
                _uiState.value = SwipesUiState.NoMoreCards
            }
        }
    }

    /**
     * Prefetch details using BATCH LOADING pattern (like MangaDetailsPresenter)
     * 
     * OPTIMIZATION: Load ALL cards in background, emit ONCE when complete
     * Pattern from MangaDetailsPresenter.kt:229-240 (tasks.awaitAll() approach)
     * 
     * Performance Optimizations:
     * - Limited concurrency (8 max parallel) prevents cache contention
     * - All async tasks launched, then awaitAll() blocks until complete
     * - SINGLE UI emission eliminates incremental loading flashes
     * - Smart filtering removes NSFW/failed cards before UI update
     * 
     * User Experience:
     * - Loading spinner shown during entire fetch
     * - NO cards appear until ALL are ready with thumbnails
     * - Single smooth appearance of complete card stack
     * - No jarring "cards popping in" effect
     */
    private fun prefetchDetailsForInitialCards(cards: List<SwipeCardItem>) {
        presenterScope.launch {
            Logger.d { "📱 [SWIPES] 🚀 BACKGROUND LOADING: Fetching details for ${cards.size} cards" }

            // Semaphore to limit concurrency (prevents rate limiting)
            val maxConcurrent = 4
            val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrent)

            // Launch all fetches concurrently but don't block UI
            cards.map { card ->
                async(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        val detailedCard = withTimeoutOrNull(6.seconds) {
                            fetchDetailsSync(card)
                        }

                        if (detailedCard == null) return@async null

                        // Only keep cards that have at least some useful info
                        val hasValidMetadata = detailedCard.description.isNotBlank() ||
                                               detailedCard.tags.isNotBlank() ||
                                               detailedCard.author.isNotBlank() ||
                                               detailedCard.chapterCount > 0

                        if (!hasValidMetadata) return@async null

                        detailsCache[card.url] = detailedCard
                        detailedCard
                    } catch (e: Exception) {
                        null
                    } finally {
                        semaphore.release()
                    }
                }
            }.forEach { deferred ->
                // Process each card as it completes - update UI incrementally
                val detailedCard = deferred.await()
                if (detailedCard != null) {
                    val currentCards = _cards.value.toMutableList()
                    val index = currentCards.indexOfFirst { it.url == detailedCard.url }
                    if (index >= 0) {
                        currentCards[index] = detailedCard
                        _cards.value = currentCards
                    }
                }
            }

            Logger.d { "📱 [SWIPES] Background detail loading complete" }
        }
    }
    
    /**
     * Prefetch details for upcoming cards (5 positions ahead)
     * Called when user swipes to maintain lookahead buffer
     */
    fun prefetchUpcomingCards(currentPosition: Int) {
        presenterScope.launch {
            val cards = _cards.value
            val startPos = currentPosition + 1
            val endPos = (startPos + prefetchLookahead).coerceAtMost(cards.size)
            
            for (position in startPos until endPos) {
                // Skip if already prefetching or already has details
                if (position in prefetchingPositions || cards[position].detailsLoaded) {
                    continue
                }
                
                prefetchingPositions.add(position)
                val card = cards[position]
                
                // Check cache first
                if (detailsCache.containsKey(card.url)) {
                    updateCardAtPosition(position, detailsCache[card.url]!!)
                    prefetchingPositions.remove(position)
                    continue
                }
                
                try {
                    Logger.d { "📱 [SWIPES] Prefetching card at position $position: '${card.title}'" }
                    val detailedCard = fetchDetailsSync(card)
                    
                    // Filter out manga with 0 chapters (no content available)
                    if (detailedCard.chapterCount == 0) {
                        Logger.w { "📱 [SWIPES] Skipping '${detailedCard.title}' - no chapters available (chapterCount=0)" }
                        // Remove this card from the list
                        val updatedCards = _cards.value.toMutableList()
                        updatedCards.removeAt(position)
                        _cards.value = updatedCards
                        prefetchingPositions.remove(position)
                        continue
                    }
                    
                    detailsCache[card.url] = detailedCard
                    updateCardAtPosition(position, detailedCard)
                    Logger.d { "📱 [SWIPES] Prefetch complete for position $position" }
                } catch (e: Exception) {
                    Logger.w(e) { "📱 [SWIPES] Prefetch failed for position $position: ${e.message}" }
                    // Mark as loaded to avoid retry spam
                    val failedCard = card.copy(detailsLoaded = true)
                    updateCardAtPosition(position, failedCard)
                } finally {
                    prefetchingPositions.remove(position)
                }
            }
        }
    }
    
    /**
     * Clean description by removing "Alternative Name:" section and other metadata
     * These sections are added by some sources but clutter the card description
     */
    private fun cleanDescription(description: String?): String? {
        if (description.isNullOrBlank()) return description
        
        // Remove "Alternative Name:" section (everything from "Alternative Name:" to the next line break)
        // Pattern matches common formats:
        // - "Alternative Name: ..."
        // - "Alt Name: ..."
        // - Multi-line alternative name blocks
        var cleaned = description
        
        // Remove entire "Alternative Name:" sections (handles multi-language lists)
        cleaned = cleaned.replace(Regex("Alternative Name:.*?(?=\\n\\n|$)", RegexOption.DOT_MATCHES_ALL), "")
        
        // Trim excessive whitespace and normalize line breaks
        cleaned = cleaned.replace(Regex("\\n{3,}"), "\n\n") // Max 2 consecutive line breaks
        cleaned = cleaned.trim()
        
        return cleaned.takeIf { it.isNotBlank() }
    }

    /**
     * Synchronously fetch manga details (for use in prefetch operations)
     * Throws exception if fetch fails - caller should handle and filter out
     */
    private suspend fun fetchDetailsSync(card: SwipeCardItem): SwipeCardItem {
        // Create temporary SManga from card data
        val manga = eu.kanade.tachiyomi.source.model.SManga.create().apply {
            url = card.url
            title = card.title
            author = card.author
            description = card.description
            thumbnail_url = card.coverUrl
            status = when (card.status) {
                "Ongoing" -> eu.kanade.tachiyomi.source.model.SManga.ONGOING
                "Completed" -> eu.kanade.tachiyomi.source.model.SManga.COMPLETED
                "Licensed" -> eu.kanade.tachiyomi.source.model.SManga.LICENSED
                else -> eu.kanade.tachiyomi.source.model.SManga.UNKNOWN
            }
        }
        
        // Fetch full details (throws exception on failure - let it propagate)
        val detailedManga = repository.fetchMangaDetails(manga, card.sourceId)
        
        // Fetch chapter count for display in card
        // NOTE: Let exceptions propagate - if we can't fetch chapters, this manga has no content
        // and will be filtered out by the 0-chapter check in prefetchDetailsAhead()
        val chapterCount = repository.fetchChapterCount(card.url, card.sourceId)
        Logger.d { "📚 [SWIPES] Fetched chapter count for '${card.title}': $chapterCount chapters" }
        
        // Create updated card with details (use original values as fallbacks for uninitialized fields)
        // NOTE: Some sources don't initialize all fields in getMangaDetails() response
        // Use safe access to avoid UninitializedPropertyAccessException
        return SwipeCardItem(
            id = card.id,
            title = try {
                detailedManga.title.takeIf { it.isNotBlank() } ?: card.title
            } catch (e: UninitializedPropertyAccessException) {
                card.title // Use original if detailedManga.title not initialized
            },
            author = detailedManga.author.takeIf { it?.isNotBlank() == true } ?: card.author,
            status = repository.getStatusText(detailedManga.status),
            description = (cleanDescription(detailedManga.description) ?: cleanDescription(card.description)) ?: card.description,
            tags = detailedManga.getGenres()?.joinToString(", ").takeIf { it?.isNotBlank() == true } ?: card.tags,
            coverUrl = detailedManga.thumbnail_url.takeIf { it?.isNotBlank() == true } ?: card.coverUrl,
            sourceId = card.sourceId,
            url = card.url,
            chapterCount = chapterCount,
            detailsLoaded = true
        )
    }
    
    /**
     * Update card at specific position (thread-safe)
     */
    private fun updateCardAtPosition(position: Int, updatedCard: SwipeCardItem) {
        val updatedCards = _cards.value.toMutableList()
        if (position >= 0 && position < updatedCards.size) {
            updatedCards[position] = updatedCard
            _cards.value = updatedCards
        }
    }

    /**
     * Add manga to library from swipe card
     * Shows category selection dialog and handles library addition
     * 
     * Uses the same pattern as BrowseSourcePresenter:
     * 1. Check if manga exists in database
     * 2. If not, insert it
     * 3. Fetch the database entity (required for moveCategories)
     * 4. Call moveCategories which handles category selection and favorites
     * 
     * @param activity Activity context for showing dialogs
     * @param card The swipe card containing manga information
     */
    fun addMangaToLibrary(activity: Activity, card: SwipeCardItem) {
        presenterScope.launch {
            try {
                Logger.d { "📚 [LIBRARY] Adding manga to library: ${card.title}" }
                
                val getManga: yokai.domain.manga.interactor.GetManga = Injekt.get()
                
                // Check if manga already exists in database (returns old Manga interface type)
                var localManga: eu.kanade.tachiyomi.domain.manga.models.Manga? = 
                    getManga.awaitByUrlAndSource(card.url, card.sourceId) as? eu.kanade.tachiyomi.domain.manga.models.Manga
                
                if (localManga == null) {
                    Logger.d { "📚 [LIBRARY] Manga not in DB, creating new entry" }
                    
                    // Fetch detailed manga information
                    val detailedCard = try {
                        Logger.d { "📚 [LIBRARY] Fetching detailed manga information..." }
                        fetchDetailsSync(card)
                    } catch (e: Exception) {
                        Logger.w(e) { "📚 [LIBRARY] Failed to fetch details, using basic card data" }
                        card // Fallback to original card if fetch fails
                    }
                    
                    // Create SManga from detailed card data
                    val sManga = repository.createSMangaFromCard(detailedCard)
                    
                    // Create Manga object for database (old interface type)
                    val newManga = eu.kanade.tachiyomi.domain.manga.models.Manga.create(detailedCard.url, detailedCard.title, detailedCard.sourceId)
                    newManga.copyFrom(sManga)
                    
                    // Insert manga into database
                    val mangaId = insertManga.await(newManga)
                    newManga.id = mangaId
                    Logger.d { "📚 [LIBRARY] Manga inserted with ID: $mangaId (with detailed info)" }
                    
                    localManga = newManga
                } else if (localManga.favorite) {
                    // Already in library
                    Logger.w { "📚 [LIBRARY] Manga already in library: ${card.title}" }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            activity,
                            "This item is already in your library",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    libraryUrls.add(card.url)
                    return@launch
                } else {
                    // Manga exists but not in library - fetch detailed information before adding
                    Logger.d { "📚 [LIBRARY] Manga in DB but not favorited, fetching details: ${card.title}" }
                    
                    val detailedCard = try {
                        Logger.d { "📚 [LIBRARY] Fetching detailed manga information..." }
                        fetchDetailsSync(card)
                    } catch (e: Exception) {
                        Logger.w(e) { "📚 [LIBRARY] Failed to fetch details, using basic card data" }
                        card // Fallback to original card if fetch fails
                    }
                    
                    // Update existing manga with detailed information
                    val sManga = repository.createSMangaFromCard(detailedCard)
                    localManga.copyFrom(sManga)
                    
                    // Update manga in database
                    val updateManga: yokai.domain.manga.interactor.UpdateManga = Injekt.get()
                    updateManga.await(
                        yokai.domain.manga.models.MangaUpdate(
                            id = localManga.id!!,
                            title = detailedCard.title,
                            author = detailedCard.author.takeIf { it.isNotBlank() },
                            description = detailedCard.description.takeIf { it.isNotBlank() },  
                            genres = detailedCard.tags.takeIf { it.isNotBlank() }?.split(", "),
                            thumbnailUrl = detailedCard.coverUrl.takeIf { it.isNotBlank() },
                            status = when (detailedCard.status.lowercase()) {
                                "ongoing" -> eu.kanade.tachiyomi.source.model.SManga.ONGOING
                                "completed" -> eu.kanade.tachiyomi.source.model.SManga.COMPLETED
                                "licensed" -> eu.kanade.tachiyomi.source.model.SManga.LICENSED
                                else -> eu.kanade.tachiyomi.source.model.SManga.UNKNOWN
                            }
                        )
                    )
                    Logger.d { "📚 [LIBRARY] Updated existing manga with detailed info: ${card.title}" }
                }
                
                Logger.d { "📚 [LIBRARY] Showing category dialog for: ${card.title}" }
                Logger.d { "📚 [LIBRARY] Before moveCategories - favorite=${localManga.favorite}, id=${localManga.id}" }
                
                // Show category selection dialog using existing extension function
                // The addingToLibrary parameter triggers the dialog to mark manga as favorite
                // moveCategories is a suspend function that waits for dialog completion
                localManga.moveCategories(activity, addingToLibrary = true) {
                    Logger.d { "📚 [LIBRARY] 🎉 CALLBACK FIRED! Manga: ${card.title}, favorite=${localManga.favorite}" }
                    
                    // Force cover cache refresh - delete both memory and disk cache
                    // This ensures the cover is re-fetched with proper mangaId and inLibrary=true metadata
                    presenterScope.launch {
                        try {
                            val coverCache: eu.kanade.tachiyomi.data.cache.CoverCache = Injekt.get()
                            coverCache.deleteFromCache(localManga, false)
                            Logger.d { "📚 [LIBRARY] Cleared cover cache for reload with mangaId=${localManga.id}" }
                            
                            // Also clear Coil's disk cache for this cover URL
                            // This is critical because swipe cards load with mangaId=0, inLibrary=false
                            // which creates a different cache key than library manga
                            activity.imageLoader.diskCache?.remove(card.coverUrl)
                            Logger.d { "📚 [LIBRARY] Cleared Coil disk cache for URL: ${card.coverUrl}" }
                        } catch (e: Exception) {
                            Logger.w(e) { "📚 [LIBRARY] Failed to clear cover cache: ${e.message}" }
                        }
                    }
                    
                    // Add to library URLs to prevent future recommendations
                    libraryUrls.add(card.url)
                    
                    // Fetch chapters in background for complete library entry
                    presenterScope.launch {
                        try {
                            Logger.d { "📚 [LIBRARY] Fetching chapters for: ${card.title}" }
                            
                            // Check if chapters already exist
                            val getChapter: yokai.domain.chapter.interactor.GetChapter = Injekt.get()
                            val existingChapters = getChapter.awaitAll(localManga, false)
                            
                            if (existingChapters.isEmpty()) {
                                Logger.d { "📚 [LIBRARY] No existing chapters, fetching from source..." }
                                val sChapters = repository.fetchChapterList(localManga.url, card.sourceId)
                                if (sChapters.isNotEmpty()) {
                                    // Convert SChapter to Chapter objects
                                    val chapters = sChapters.mapIndexed { i, sChapter ->
                                        Chapter.create().apply {
                                            copyFrom(sChapter)
                                            manga_id = localManga.id
                                            source_order = i
                                        }
                                    }
                                    
                                    // Insert chapters into database
                                    val insertChapter: yokai.domain.chapter.interactor.InsertChapter = Injekt.get()
                                    insertChapter.awaitBulk(chapters)
                                    Logger.d { "📚 [LIBRARY] Added ${chapters.size} chapters for: ${card.title}" }
                                } else {
                                    Logger.w { "📚 [LIBRARY] No chapters found from source for: ${card.title}" }
                                }
                            } else {
                                Logger.d { "📚 [LIBRARY] Chapters already exist (${existingChapters.size}), skipping fetch for: ${card.title}" }
                            }
                        } catch (e: Exception) {
                            Logger.w(e) { "📚 [LIBRARY] Failed to fetch chapters for: ${card.title}" }
                        }
                    }
                    
                    // Show success feedback (callback runs on main thread already)
                    Toast.makeText(
                        activity,
                        "Added '${card.title}' to library",
                        Toast.LENGTH_LONG
                    ).show()
                }
                
                Logger.d { "📚 [LIBRARY] After moveCategories call (function returned immediately)" }
                
            } catch (e: Exception) {
                Logger.e(e) { "📚 [LIBRARY] Error adding manga to library: ${card.title}" }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        activity,
                        "Failed to add to library: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                // Clear pending swipe state - processing complete
                _isProcessingSwipe.value = false
                pendingSwipe = null
                Logger.d { "📚 [LIBRARY] Processing complete, navigation unlocked" }
            }
        }
    }

    // getAvailableSources() removed - source grouping handled in filter dialog directly

    /**
     * Get current active filters
     * Phase 4.1: Filter state management
     */
    fun getCurrentFilters() = currentFilters

    /**
     * Apply new filters and rebuild queue
     * Phase 4.1: Filter application with queue rebuild
     */
    fun applyFilters(newFilters: SwipesFilters) {
        Logger.d { "🔧 [FILTERS] Applying new filters: $newFilters" }
        
        if (newFilters == currentFilters) {
            Logger.d { "🔧 [FILTERS] No changes detected, skipping rebuild" }
            return
        }
        
        currentFilters = newFilters
        
        // Clear current queue and caches
        _cards.value = emptyList()
        currentMangaUrls.clear()
        detailsCache.clear()
        prefetchingPositions.clear()
        
        Logger.d { "🔧 [FILTERS] Queue cleared, reloading with new filters" }
        
        // Reload recommendations with new filters
        loadInitialRecommendations()
    }

    /**
     * Cleanup when presenter is destroyed
     */
    /**
     * Save pending swipe when Activity is stopped (user navigates away)
     * This preserves the swipe state if they return before the operation completes
     */
    fun savePendingSwipe() {
        if (_isProcessingSwipe.value) {
            // Currently processing a swipe - save the pending item
            pendingSwipe = _cards.value.firstOrNull()
            Logger.d { "💾 [SWIPES] Saved pending swipe: ${pendingSwipe?.title}" }
        } else {
            pendingSwipe = null
        }
        
        // Save queue to cache
        saveQueueToCache()
    }
    
    /**
     * Restore pending swipe when Activity is started (user returns)
     * Re-queues the card if it was still being processed when they left
     */
    fun restorePendingSwipe() {
        pendingSwipe?.let { card ->
            if (!_isProcessingSwipe.value) {
                // Processing completed while away - clear pending
                Logger.d { "✅ [SWIPES] Pending swipe completed: ${card.title}" }
                pendingSwipe = null
            } else {
                // Still processing - keep as pending
                Logger.d { "⏳ [SWIPES] Pending swipe still processing: ${card.title}" }
            }
        }
    }
    
    /**
     * Save current queue to SharedPreferences cache
     * CRITICAL: Uses try-catch with fallback to prevent corruption
     */
    private fun saveQueueToCache() {
        try {
            val currentCards = _cards.value
            if (currentCards.isEmpty()) {
                Logger.d { "💾 [CACHE] Queue empty, clearing cache" }
                prefs.edit().remove("queue_json").remove("queue_timestamp").apply()
                return
            }
            
            // Serialize queue to JSON
            val jsonString = json.encodeToString(currentCards)
            val timestamp = System.currentTimeMillis()
            
            // Save with transaction safety
            prefs.edit()
                .putString("queue_json", jsonString)
                .putLong("queue_timestamp", timestamp)
                .apply()
            
            Logger.d { "💾 [CACHE] Saved ${currentCards.size} cards to cache (${jsonString.length} bytes)" }
        } catch (e: Exception) {
            Logger.e(e) { "💾 [CACHE] Failed to save queue: ${e.message}" }
            // Clear potentially corrupted cache
            try {
                prefs.edit().clear().apply()
            } catch (clearError: Exception) {
                Logger.e(clearError) { "💾 [CACHE] Failed to clear corrupted cache" }
            }
        }
    }
    
    /**
     * Restore queue from SharedPreferences cache
     * Returns restored cards or empty list if cache is invalid/expired
     */
    private fun restoreQueueFromCache(): List<SwipeCardItem> {
        try {
            val jsonString = prefs.getString("queue_json", null)
            val timestamp = prefs.getLong("queue_timestamp", 0L)
            
            if (jsonString == null || timestamp == 0L) {
                Logger.d { "💾 [CACHE] No cached queue found" }
                return emptyList()
            }
            
            // Check if cache is too old (7 days)
            val cacheAge = System.currentTimeMillis() - timestamp
            val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 days in milliseconds
            
            if (cacheAge > maxAge) {
                Logger.d { "💾 [CACHE] Cache expired (${cacheAge / (24 * 60 * 60 * 1000)} days old), clearing" }
                prefs.edit().clear().apply()
                return emptyList()
            }
            
            // Deserialize JSON to cards
            val cards = json.decodeFromString<List<SwipeCardItem>>(jsonString)
            Logger.d { "💾 [CACHE] Restored ${cards.size} cards from cache (${cacheAge / 1000}s old)" }
            
            return cards
        } catch (e: Exception) {
            Logger.e(e) { "💾 [CACHE] Failed to restore queue: ${e.message}" }
            // Clear corrupted cache
            try {
                prefs.edit().clear().apply()
                Logger.d { "💾 [CACHE] Cleared corrupted cache" }
            } catch (clearError: Exception) {
                Logger.e(clearError) { "💾 [CACHE] Failed to clear corrupted cache" }
            }
            return emptyList()
        }
    }
    
    fun onDestroy() {
        Logger.d { "📱 [SWIPES] Presenter onDestroy()" }
        // Save queue before destroying
        saveQueueToCache()
        
        // Clear caches
        detailsCache.clear()
        prefetchingPositions.clear()
        pendingSwipe = null
        // Coroutines will be cancelled when presenterScope is cancelled
    }
    
    /**
     * Phase 4.2: History Feature - Get swipe history for UI (view-only)
     */
    suspend fun getSwipeHistory(): List<eu.kanade.tachiyomi.ui.swipes.history.SwipesHistoryItem> {
        return try {
            Logger.d { "📜 [HISTORY] Fetching swipe history from repository..." }
            val historyEntries = historyRepository.getLast50Swipes()
            Logger.d { "📜 [HISTORY] Got ${historyEntries.size} entries from repository" }
            
            historyEntries.map { entry ->
                eu.kanade.tachiyomi.ui.swipes.history.SwipesHistoryItem(
                    url = entry.mangaUrl,
                    title = entry.mangaTitle,
                    author = entry.mangaAuthor ?: "",
                    coverUrl = entry.coverUrl ?: "",
                    swipedRight = entry.swipeDirection == "right",
                    timestamp = entry.swipedAt
                )
            }.also {
                Logger.d { "📜 [HISTORY] Mapped to ${it.size} UI items" }
            }
        } catch (e: Exception) {
            Logger.e(e) { "📜 [HISTORY] Failed to load swipe history" }
            emptyList()
        }
    }
    
    /**
     * Phase 4.2: Clear all swipe history
     */
    suspend fun clearSwipeHistory() {
        try {
            historyRepository.clearAll()
            Logger.d { "📜 [HISTORY] Cleared all swipe history" }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to clear swipe history" }
        }
    }
    
    /**
     * Phase 4.2: Add manga to permanent blacklist
     */
    suspend fun addToBlacklist(mangaUrl: String) {
        try {
            // Find source ID from URL (check current queue or history)
            val sourceId = _cards.value.find { it.url == mangaUrl }?.sourceId ?: 0L
            
            blacklistRepository.insert(
                mangaUrl = mangaUrl,
                sourceId = sourceId,
                mangaTitle = null,
                coverUrl = null,
                blacklistedAt = System.currentTimeMillis()
            )
            
            blacklistedUrls.add(mangaUrl)
            Logger.d { "🚫 [BLACKLIST] Added to blacklist: $mangaUrl" }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to add to blacklist: $mangaUrl" }
        }
    }
    
    /**
     * Phase 4.2: Remove manga from permanent blacklist
     */
    suspend fun removeFromBlacklist(mangaUrl: String) {
        try {
            val sourceId = _cards.value.find { it.url == mangaUrl }?.sourceId ?: 0L
            
            blacklistRepository.removeFromBlacklist(
                mangaUrl = mangaUrl,
                sourceId = sourceId
            )
            
            blacklistedUrls.remove(mangaUrl)
            Logger.d { "✅ [BLACKLIST] Removed from blacklist: $mangaUrl" }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to remove from blacklist: $mangaUrl" }
        }
    }
    
    /**
     * Phase 4.2: Search manga with query
     * Replaces current queue with search results
     */
    fun searchManga(query: String) {
        Logger.d { "🔍 [SEARCH] Search initiated: '$query'" }
        
        // Update search state
        _searchQuery.value = query
        isSearchActive = query.isNotBlank()
        
        if (query.isBlank()) {
            // Clear search - reload normal recommendations
            Logger.d { "🔍 [SEARCH] Empty query, loading recommendations" }
            loadInitialRecommendations()
            return
        }
        
        presenterScope.launch {
            try {
                _uiState.value = SwipesUiState.Loading
                Logger.d { "🔍 [SEARCH] Loading search results for '$query'..." }
                
                // Clear current queue
                _cards.value = emptyList()
                currentMangaUrls.clear()
                detailsCache.clear()
                
                // Fetch search results from repository
                val searchResults = withContext(Dispatchers.IO) {
                    repository.searchManga(
                        query = query,
                        count = preloadCount,
                        excludeNsfw = currentFilters.excludeNsfw,
                        enabledSourceIds = currentFilters.enabledSourceIds,
                        blacklistedMangaUrls = blacklistedUrls,
                        libraryMangaUrls = libraryUrls
                    )
                }
                
                if (searchResults.isEmpty()) {
                    Logger.d { "🔍 [SEARCH] No results found for '$query'" }
                    _uiState.value = SwipesUiState.NoResults(query)
                    return@launch
                }
                
                Logger.d { "🔍 [SEARCH] Found ${searchResults.size} results for '$query'" }
                
                // Convert to SwipeCardItems
                val cards = searchResults.map { mangaWithSource ->
                    SwipeCardItem(
                        url = mangaWithSource.manga.url,
                        title = mangaWithSource.manga.title,
                        author = mangaWithSource.manga.author ?: "",
                        coverUrl = mangaWithSource.manga.thumbnail_url ?: "",
                        description = mangaWithSource.manga.description ?: "",
                        tags = mangaWithSource.manga.genre ?: "",
                        status = repository.getStatusText(mangaWithSource.manga.status),
                        sourceId = mangaWithSource.sourceId,
                        detailsLoaded = false
                    )
                }
                
                _cards.value = cards
                currentMangaUrls.clear()
                currentMangaUrls.addAll(cards.map { it.url })
                _uiState.value = SwipesUiState.Success(cards.size)
                
                Logger.d { "🔍 [SEARCH] Search queue loaded: ${cards.size} cards" }
                
                // Prefetch details for search results
                prefetchUpcomingCards(0)
                
            } catch (e: Exception) {
                Logger.e(e) { "🔍 [SEARCH] Error searching manga" }
                _uiState.value = SwipesUiState.Error(e.message ?: "Search failed")
            }
        }
    }
    
    /**
     * Phase 4.2: Clear search and return to recommendations
     */
    fun clearSearch() {
        Logger.d { "🔍 [SEARCH] Clearing search, returning to recommendations" }
        _searchQuery.value = ""
        isSearchActive = false
        loadInitialRecommendations()
    }
    
    /**
     * Phase 4.2: Get current search query
     */
    fun getCurrentSearchQuery(): String = _searchQuery.value
    
    /**
     * Phase 4.2: Check if search is active
     */
    fun isSearching(): Boolean = isSearchActive
}

/**
 * UI State for SwipesActivity
 */
sealed class SwipesUiState {
    object Loading : SwipesUiState()
    data class Success(val cardCount: Int) : SwipesUiState()
    data class Error(val message: String) : SwipesUiState()
    object NoSources : SwipesUiState()
    object NoMoreCards : SwipesUiState()
    data class NoResults(val query: String) : SwipesUiState() // Phase 4.2: Search no results
}
