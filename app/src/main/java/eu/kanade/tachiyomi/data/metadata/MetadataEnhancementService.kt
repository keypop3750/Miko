package eu.kanade.tachiyomi.data.metadata

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.metadata.anilist.AnilistMetadataClient
import eu.kanade.tachiyomi.data.metadata.mal.MalMetadataClient
import eu.kanade.tachiyomi.data.metadata.kitsu.KitsuMetadataClient
import eu.kanade.tachiyomi.data.metadata.mangadex.MangaDexMetadataClient
import eu.kanade.tachiyomi.ui.swipes.SwipeCardItem
import eu.kanade.tachiyomi.util.system.e
import eu.kanade.tachiyomi.util.system.w
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import yokai.domain.metadata.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * Service that coordinates metadata enhancement from multiple providers
 * Handles provider fallback, rate limiting, retries, and caching
 * 
 * Provider priority: AniList → MangaDex → MAL → Kitsu → Source fallback
 */
class MetadataEnhancementService(
    private val anilistClient: AnilistMetadataClient,
    private val mangadexClient: MangaDexMetadataClient,
    private val malClient: MalMetadataClient,
    private val kitsuClient: KitsuMetadataClient,
    private val metadataRepository: MetadataRepository,
    private val matcher: MetadataMatcher
) {
    
    private val activeRequests = AtomicInteger(0)
    private val maxConcurrentRequests = 8
    private var rateLimitExceeded = false
    
    // Track provider health to skip consistently failing providers
    private var malConsecutiveFailures = 0
    private val maxConsecutiveFailures = 3
    
    companion object {
        private const val RETRY_DELAY_MS = 2000L
        private const val MAX_RETRIES = 2
        private const val REQUEST_TIMEOUT_SECONDS = 8L // Reduced to 8s for faster fallback
        private const val MAL_TIMEOUT_SECONDS = 5L // MAL gets shorter timeout due to frequent failures
    }
    
    /**
     * Enhance a single card with metadata
     * Tries providers in order: AniList → MAL → Kitsu → Source fallback
     */
    suspend fun enhanceCard(card: SwipeCardItem): SwipeCardItem {
        // Check cache first
        val cached = metadataRepository.getCachedMetadata(card.url, card.sourceId)
        
        if (cached != null && !metadataRepository.shouldRefreshMetadata(card.url, card.sourceId)) {
            Logger.d { "📊 [METADATA] ✓ Using cached metadata for '${card.title}' from ${cached.provider}" }
            return card.applyMetadata(cached.metadata, cached.provider)
        }
        
        // Try fetching fresh metadata
        val metadata = fetchMetadataWithFallback(card)
        
        return if (metadata != null) {
            card.applyMetadata(metadata.first, metadata.second)
        } else {
            Logger.w { "📊 [METADATA] ✗ No metadata found for '${card.title}'" }
            card // Return original card unchanged
        }
    }
    
    /**
     * Enhance multiple cards in parallel
     * Automatically falls back to batched processing if rate limited
     */
    suspend fun enhanceCards(cards: List<SwipeCardItem>): List<SwipeCardItem> = coroutineScope {
        Logger.d { "� [METADATA_SERVICE] enhanceCards() START - ${cards.size} cards to enhance" }
        Logger.d { "�📊 [METADATA] Enhancing ${cards.size} cards..." }
        
        val startTime = System.currentTimeMillis()
        
        val enhanced = if (rateLimitExceeded) {
            Logger.w { "📊 [METADATA] Rate limit active, using batched processing" }
            enhanceCardsBatched(cards)
        } else {
            try {
                // Attempt parallel processing (8 concurrent)
                cards.chunked(maxConcurrentRequests).flatMap { chunk ->
                    chunk.map { card ->
                        async {
                            activeRequests.incrementAndGet()
                            try {
                                enhanceCard(card)
                            } finally {
                                activeRequests.decrementAndGet()
                            }
                        }
                    }.awaitAll()
                }
            } catch (e: Exception) {
                if (e.message?.contains("rate", ignoreCase = true) == true ||
                    e.message?.contains("429", ignoreCase = true) == true) {
                    Logger.w { "📊 [METADATA] Rate limit exceeded, falling back to batched mode" }
                    rateLimitExceeded = true
                    enhanceCardsBatched(cards)
                } else {
                    Logger.e(e) { "📊 [METADATA] Error during parallel enhancement" }
                    cards // Return original cards on error
                }
            }
        }
        
        val duration = System.currentTimeMillis() - startTime
        val enhancedCount = enhanced.count { it.metadataEnhanced }
        
        Logger.d { 
            "📊 [METADATA] Enhanced $enhancedCount/${cards.size} cards in ${duration}ms " +
            "(avg ${if (cards.isNotEmpty()) duration / cards.size else 0}ms per card)"
        }
        
        enhanced
    }
    
    /**
     * Enhance cards in smaller batches with delays (rate limit safe)
     */
    private suspend fun enhanceCardsBatched(cards: List<SwipeCardItem>): List<SwipeCardItem> = coroutineScope {
        Logger.d { "📊 [METADATA] Batched enhancement: ${cards.size} cards (3 per batch)" }
        
        cards.chunked(3).flatMap { batch ->
            val enhanced = batch.map { card ->
                async { enhanceCard(card) }
            }.awaitAll()
            
            // Small delay between batches to respect rate limits
            delay(1000)
            enhanced
        }
    }
    
    /**
     * Fetch metadata with provider fallback and retry logic
     * Phase 1: AniList only
     * Phase 2: MAL fallback
     * Phase 3: Kitsu fallback
     * Phase 4: MangaDex fallback (excellent for manhwa/webtoons)
     */
    private suspend fun fetchMetadataWithFallback(
        card: SwipeCardItem
    ): Pair<MetadataDetails, MetadataProvider>? {
        
        // Extract alternative titles from description
        val altTitles = extractAlternativeTitles(card.description)
        
        Logger.d { "📊 [METADATA] Searching for '${card.title}' (${altTitles.size} alt titles)" }
        
        // Respect Swipes NSFW filter setting
        val includeAdult = card.isNsfw
        
        // Try AniList first (Priority 1)
        tryProvider(MetadataProvider.ANILIST, card.title, altTitles, includeAdult)?.let { metadata ->
            Logger.d { "📊 [METADATA] ✓ Enhanced '${card.title}' via AniList" }
            
            // Cache the result
            metadataRepository.cacheMetadata(
                card.url,
                card.sourceId,
                metadata,
                card.title,
                0.95f // High confidence from successful strict match
            )
            
            return metadata to MetadataProvider.ANILIST
        }
        
        // Try MangaDex if AniList failed (Priority 2) - excellent for manhwa/webtoons
        tryProvider(MetadataProvider.MANGADEX, card.title, altTitles, includeAdult)?.let { metadata ->
            Logger.d { "📊 [METADATA] ✓ Enhanced '${card.title}' via MangaDex" }
            
            // Cache the result
            metadataRepository.cacheMetadata(
                card.url,
                card.sourceId,
                metadata,
                card.title,
                0.93f // High confidence - MangaDex has excellent data quality
            )
            
            return metadata to MetadataProvider.MANGADEX
        }
        
        // Try MAL (Priority 3) - skip if consistently failing
        if (malConsecutiveFailures < maxConsecutiveFailures) {
            tryProvider(MetadataProvider.MYANIMELIST, card.title, altTitles, includeAdult)?.let { metadata ->
                Logger.d { "📊 [METADATA] ✓ Enhanced '${card.title}' via MAL" }
                malConsecutiveFailures = 0 // Reset on success
                
                // Cache the result
                metadataRepository.cacheMetadata(
                    card.url,
                    card.sourceId,
                    metadata,
                    card.title,
                    0.90f // Slightly lower confidence for fallback
                )
                
                return metadata to MetadataProvider.MYANIMELIST
            }
            // Track failure
            malConsecutiveFailures++
            if (malConsecutiveFailures >= maxConsecutiveFailures) {
                Logger.w { "📊 [METADATA] ⚠️ MAL disabled after $maxConsecutiveFailures consecutive failures" }
            }
        }
        
        // Try Kitsu if all others failed (Priority 4)
        tryProvider(MetadataProvider.KITSU, card.title, altTitles, includeAdult)?.let { metadata ->
            Logger.d { "📊 [METADATA] ✓ Enhanced '${card.title}' via Kitsu" }
            
            // Cache the result
            metadataRepository.cacheMetadata(
                card.url,
                card.sourceId,
                metadata,
                card.title,
                0.85f // Lowest confidence for final fallback
            )
            
            return metadata to MetadataProvider.KITSU
        }
        
        Logger.w { "📊 [METADATA] ✗ No providers matched '${card.title}'" }
        return null
    }
    
    /**
     * Try fetching from a specific provider with retry logic
     * Implements exponential backoff for retries
     */
    private suspend fun tryProvider(
        provider: MetadataProvider,
        title: String,
        altTitles: List<String>,
        includeAdult: Boolean
    ): MetadataDetails? {
        
        var attempts = 0
        
        while (attempts <= MAX_RETRIES) {
            try {
                // Use shorter timeout for MAL due to frequent failures
                val timeoutSeconds = if (provider == MetadataProvider.MYANIMELIST) {
                    MAL_TIMEOUT_SECONDS
                } else {
                    REQUEST_TIMEOUT_SECONDS
                }
                
                return withTimeoutOrNull(timeoutSeconds.seconds) {
                    when (provider) {
                        MetadataProvider.ANILIST -> tryAniList(title, altTitles, includeAdult)
                        MetadataProvider.MANGADEX -> tryMangaDex(title, altTitles, includeAdult)
                        MetadataProvider.MYANIMELIST -> tryMAL(title, altTitles, includeAdult)
                        MetadataProvider.KITSU -> tryKitsu(title, altTitles, includeAdult)
                    }
                }
            } catch (e: Exception) {
                attempts++
                
                if (attempts > MAX_RETRIES) {
                    Logger.e(e) { "📊 [METADATA] Failed after $MAX_RETRIES retries: ${provider.name}" }
                    return null
                }
                
                val backoffDelay = RETRY_DELAY_MS * attempts
                Logger.w { "📊 [METADATA] Retry $attempts/$MAX_RETRIES for ${provider.name} (delay ${backoffDelay}ms)" }
                delay(backoffDelay) // Exponential backoff
            }
        }
        
        return null
    }
    
    /**
     * Clean and normalize title for better matching
     * Removes common suffixes and patterns that prevent matching
     */
    private fun cleanTitleForSearch(title: String): List<String> {
        val cleanedTitles = mutableListOf<String>()
        
        // Add original title
        cleanedTitles.add(title.trim())
        
        // Remove common suffixes that prevent matching
        val suffixPatterns = listOf(
            Regex("""\s*\((?:Colored?|Full Colored?|Webtoon|Manhwa|Manhua)\)""", RegexOption.IGNORE_CASE),
            Regex("""\s*\[(?:Colored?|Full Colored?|Webtoon|Manhwa|Manhua)\]""", RegexOption.IGNORE_CASE),
            Regex("""\s*-\s*(?:Colored?|Full Colored?|Webtoon|Manhwa|Manhua)""", RegexOption.IGNORE_CASE),
            Regex("""\s*\(Official\)""", RegexOption.IGNORE_CASE),
            Regex("""\s*\[Official\]""", RegexOption.IGNORE_CASE)
        )
        
        var cleanedTitle = title.trim()
        suffixPatterns.forEach { pattern ->
            cleanedTitle = cleanedTitle.replace(pattern, "").trim()
        }
        
        if (cleanedTitle != title.trim() && cleanedTitle.isNotBlank()) {
            cleanedTitles.add(cleanedTitle)
        }
        
        // Try romanized version for Chinese/Japanese titles
        if (title.any { it.code > 127 }) {
            // For titles with non-ASCII characters, try with just ASCII parts
            val asciiOnly = title.filter { it.code <= 127 }.trim()
            if (asciiOnly.isNotBlank() && asciiOnly.length > 3) {
                cleanedTitles.add(asciiOnly)
            }
        }
        
        return cleanedTitles.distinct().take(3) // Limit variations
    }
    
    /**
     * Search AniList and return best match if confidence > 90%
     */
    private suspend fun tryAniList(
        title: String,
        altTitles: List<String>,
        includeAdult: Boolean
    ): MetadataDetails? {
        
        // Try multiple cleaned versions of the title
        val searchTitles = cleanTitleForSearch(title)
        val allTitles = (searchTitles + altTitles).distinct()
        
        Logger.d { "📊 [METADATA] Searching AniList with ${allTitles.size} title variations: ${allTitles.joinToString(" | ")}" }
        
        val searchResults = anilistClient.search(searchTitles.first(), allTitles.drop(1), includeAdult)
        
        if (searchResults.isEmpty()) {
            Logger.d { "📊 [METADATA] AniList search returned 0 results for '$title'" }
            return null
        }
        
        // Find best match using strict confidence threshold (>90%)
        // Use all cleaned title variations for better matching
        val bestMatch = matcher.selectBestMatch(title, allTitles, searchResults)
        
        if (bestMatch == null) {
            val topResult = searchResults.firstOrNull()
            Logger.d { 
                "📊 [METADATA] No confident match for '$title' " +
                "(best: '${topResult?.title}' @ ${topResult?.confidence?.let { "%.2f".format(it * 100) }}%)"
            }
            return null
        }
        
        Logger.d { 
            "📊 [METADATA] Matched '$title' → '${bestMatch.title}' " +
            "(confidence: ${"%.1f".format(bestMatch.confidence * 100)}%)"
        }
        
        // Fetch full details
        val details = anilistClient.getDetails(bestMatch.providerId)
        
        // Additional NSFW filter check
        if (details != null && details.isAdult && !includeAdult) {
            Logger.w { "📊 [METADATA] Rejected adult content: '${details.title}'" }
            return null
        }
        
        return details
    }
    
    /**
     * Search MAL and return best match if confidence > 90%
     */
    private suspend fun tryMAL(
        title: String,
        altTitles: List<String>,
        includeAdult: Boolean
    ): MetadataDetails? {
        
        val searchResults = malClient.search(title, altTitles, includeAdult)
        
        if (searchResults.isEmpty()) {
            Logger.d { "📊 [METADATA] MAL search returned 0 results for '$title'" }
            return null
        }
        
        // Find best match using strict confidence threshold (>90%)
        val bestMatch = matcher.selectBestMatch(title, altTitles, searchResults)
        
        if (bestMatch == null) {
            val topResult = searchResults.firstOrNull()
            Logger.d { 
                "📊 [METADATA] No confident MAL match for '$title' " +
                "(best: '${topResult?.title}' @ ${topResult?.confidence?.let { "%.2f".format(it * 100) }}%)"
            }
            return null
        }
        
        Logger.d { 
            "📊 [METADATA] MAL matched '$title' → '${bestMatch.title}' " +
            "(confidence: ${"%.1f".format(bestMatch.confidence * 100)}%)"
        }
        
        // Fetch full details
        val details = malClient.getDetails(bestMatch.providerId)
        
        // Additional NSFW filter check
        if (details != null && details.isAdult && !includeAdult) {
            Logger.w { "📊 [METADATA] Rejected adult content: '${details.title}'" }
            return null
        }
        
        return details
    }
    
    /**
     * Search MangaDex and return best match if confidence > 90%
     * MangaDex has excellent coverage for manhwa, webtoons, and manhua
     */
    private suspend fun tryMangaDex(
        title: String,
        altTitles: List<String>,
        includeAdult: Boolean
    ): MetadataDetails? {
        
        val searchResults = mangadexClient.search(title, altTitles, includeAdult)
        
        if (searchResults.isEmpty()) {
            Logger.d { "📊 [METADATA] MangaDex search returned 0 results for '$title'" }
            return null
        }
        
        // Find best match using strict confidence threshold (>90%)
        val bestMatch = matcher.selectBestMatch(title, altTitles, searchResults)
        
        if (bestMatch == null) {
            val topResult = searchResults.firstOrNull()
            Logger.d { 
                "📊 [METADATA] No confident MangaDex match for '$title' " +
                "(best: '${topResult?.title}' @ ${topResult?.confidence?.let { "%.2f".format(it * 100) }}%)"
            }
            return null
        }
        
        Logger.d { 
            "📊 [METADATA] MangaDex matched '$title' → '${bestMatch.title}' " +
            "(confidence: ${"%.1f".format(bestMatch.confidence * 100)}%)"
        }
        
        // Note: MangaDex search already returns the ID as a hash, but we need the original UUID
        // For now, we'll skip detailed fetch and use search results
        // TODO: Store and use actual MangaDex UUID for detail fetching
        
        // Return metadata from search result (MangaDex search is already comprehensive)
        return MetadataDetails(
            provider = bestMatch.provider,
            providerId = bestMatch.providerId,
            title = bestMatch.title,
            alternativeTitles = bestMatch.alternativeTitles,
            totalChapters = null,
            genres = emptyList(),
            tags = emptyList(),
            authors = emptyList(),
            artists = emptyList(),
            startDate = null,
            endDate = null,
            coverUrl = bestMatch.coverUrl,
            averageScore = null,
            description = null,
            status = PublishingStatus.UNKNOWN,
            format = bestMatch.format,
            isAdult = false
        )
    }
    
    /**
     * Search Kitsu and return best match if confidence > 90%
     */
    private suspend fun tryKitsu(
        title: String,
        altTitles: List<String>,
        includeAdult: Boolean
    ): MetadataDetails? {
        
        val searchResults = kitsuClient.search(title, altTitles, includeAdult)
        
        if (searchResults.isEmpty()) {
            Logger.d { "📊 [METADATA] Kitsu search returned 0 results for '$title'" }
            return null
        }
        
        // Find best match using strict confidence threshold (>90%)
        val bestMatch = matcher.selectBestMatch(title, altTitles, searchResults)
        
        if (bestMatch == null) {
            val topResult = searchResults.firstOrNull()
            Logger.d { 
                "📊 [METADATA] No confident Kitsu match for '$title' " +
                "(best: '${topResult?.title}' @ ${topResult?.confidence?.let { "%.2f".format(it * 100) }}%)"
            }
            return null
        }
        
        Logger.d { 
            "📊 [METADATA] Kitsu matched '$title' → '${bestMatch.title}' " +
            "(confidence: ${"%.1f".format(bestMatch.confidence * 100)}%)"
        }
        
        // Fetch full details
        val details = kitsuClient.getDetails(bestMatch.providerId)
        
        // Additional NSFW filter check
        if (details != null && details.isAdult && !includeAdult) {
            Logger.w { "📊 [METADATA] Rejected adult content: '${details.title}'" }
            return null
        }
        
        return details
    }
    
    /**
     * Extract alternative titles from manga description
     * Common patterns:
     * - "Alternative Names: Title1, Title2"
     * - "(Alt: Title)"
     * - "Also Known As: Title"
     */
    private fun extractAlternativeTitles(description: String?): List<String> {
        if (description.isNullOrBlank()) return emptyList()
        
        val patterns = listOf(
            Regex("""(?:Alternative Names?|Alt\.?|Also Known As):\s*([^\n]+)""", RegexOption.IGNORE_CASE),
            Regex("""\((?:Alt|Alternative):\s*([^)]+)\)""", RegexOption.IGNORE_CASE),
            Regex("""【([^】]+)】""") // Japanese brackets often contain alt titles
        )
        
        return patterns.flatMap { pattern ->
            pattern.findAll(description).flatMap { match ->
                match.groupValues[1]
                    .split(Regex("""[,;|/]"""))
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.length > 2 }
            }
        }
            .distinct()
            .take(5) // Limit to 5 alternative titles to avoid noise
    }
    
    /**
     * Apply metadata to a SwipeCardItem
     * Creates new card with enhanced fields populated
     */
    private fun SwipeCardItem.applyMetadata(
        metadata: MetadataDetails,
        provider: MetadataProvider
    ): SwipeCardItem {
        return copy(
            metadataEnhanced = true,
            metadataProvider = provider.name.lowercase(),
            
            // Priority 1: Chapter count (most critical)
            enhancedChapterCount = metadata.totalChapters,
            
            // Priority 2: Genres/Tags
            enhancedGenres = metadata.genres,
            enhancedTags = metadata.tags.take(10), // Limit to top 10 tags
            
            // Priority 3: Authors/Dates
            enhancedAuthors = metadata.authors,
            enhancedArtists = metadata.artists,
            enhancedStartDate = metadata.startDate,
            enhancedEndDate = metadata.endDate,
            
            // Priority 4: Quality improvements
            enhancedCoverUrl = metadata.coverUrl,
            enhancedScore = metadata.averageScore,
            enhancedDescription = metadata.description,
            
            // Priority 5: Status (formatted for display)
            enhancedStatus = when (metadata.status) {
                PublishingStatus.RELEASING -> "On-Going"
                PublishingStatus.FINISHED -> "Finished"
                PublishingStatus.CANCELLED -> "Cancelled"
                PublishingStatus.HIATUS -> "Hiatus"
                PublishingStatus.NOT_YET_RELEASED -> "Not Yet Released"
                PublishingStatus.UNKNOWN -> "Unknown"
            }
        )
    }
}
