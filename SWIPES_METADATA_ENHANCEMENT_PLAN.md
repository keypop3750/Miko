# Swipes Metadata Enhancement Implementation Plan

## 📋 Project Overview

**Goal**: Enhance Swipes cards with high-quality metadata from AniList, MyAnimeList, and Kitsu tracking APIs to provide accurate chapter counts, genres/tags, author information, and improved cover images.

**Scope**: Manga, Manhwa, Manhua, and Webtoon content types

**Timeline**: 2-3 weeks (Incremental rollout)

---

## 🎯 Requirements Summary

### Priority Rankings (1 = Critical → 5 = Nice to have)
1. **Total chapters** (accurate count) - **Rank 1** ⭐⭐⭐⭐⭐
2. **Genres/Tags** (for future filtering) - **Rank 2** ⭐⭐⭐⭐
3. **Authors/Artists** (complete and accurate) - **Rank 3** ⭐⭐⭐
4. **Publication dates** (start/end) - **Rank 3** ⭐⭐⭐
5. **Cover images** (higher quality) - **Rank 4** ⭐⭐
6. **Average rating/score** - **Rank 4** ⭐⭐
7. **Descriptions/Synopsis** - **Rank 4** ⭐⭐
8. **Publishing status** - **Rank 5** ⭐

### Key Decisions
- ✅ **Enhancement Timing**: Eager (during initial load)
- ✅ **Matching Strategy**: Strict (>90% confidence) with alternative titles
- ✅ **Caching**: Smart refresh (permanent for completed, refresh for ongoing)
- ✅ **Visibility**: Invisible to users + debug badge for testing
- ✅ **Provider Priority**: AniList → MAL → Kitsu → Source fallback
- ✅ **Error Handling**: Graceful degradation + retry logic (no spam)
- ✅ **Rollout**: Incremental phases
- ✅ **Performance**: Parallel (8 concurrent) with rate-limit fallback to batching
- ✅ **NSFW Filtering**: Use tracking API filters + existing Swipes logic
- ✅ **Multi-part Content**: Match separately

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      SwipesPresenter                             │
│  - Orchestrates card loading and enhancement                    │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                     SwipesRepository                             │
│  - Fetches basic cards from manga sources (EXISTING)            │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│              MetadataEnhancementService (NEW)                    │
│  - Coordinates metadata fetching from tracking APIs             │
│  - Handles provider fallback logic                              │
│  - Manages rate limiting and retries                            │
└──────┬──────────────┬──────────────┬──────────────┬─────────────┘
       │              │              │              │
       ↓              ↓              ↓              ↓
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────────┐
│ AniList  │   │   MAL    │   │  Kitsu   │   │   Metadata   │
│ Metadata │   │ Metadata │   │ Metadata │   │    Matcher   │
│  Client  │   │  Client  │   │  Client  │   │    (NEW)     │
│  (NEW)   │   │  (NEW)   │   │  (NEW)   │   └──────────────┘
└──────────┘   └──────────┘   └──────────┘
     │              │              │
     ↓              ↓              ↓
┌────────────────────────────────────────────────────────────────┐
│           MetadataCache (Database - NEW)                       │
│  - Stores enhanced metadata with expiry logic                 │
└────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Enhanced SwipeCard                            │
│  - Original source data + enhanced metadata overlay             │
│  - Debug badge showing enhancement source (testing only)        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📦 Phase 1: Foundation & AniList Integration (Week 1)

### 1.1 Database Schema Changes

**New Table: `manga_metadata_cache`**
```sql
-- Migration 34.sqm
CREATE TABLE manga_metadata_cache (
    manga_url TEXT NOT NULL,
    source_id INTEGER NOT NULL,
    
    -- Metadata provider info
    provider TEXT NOT NULL, -- 'anilist', 'mal', 'kitsu'
    provider_id INTEGER NOT NULL, -- ID in the external service
    matched_title TEXT NOT NULL, -- Title that was matched
    confidence_score REAL NOT NULL, -- 0.0-1.0 matching confidence
    
    -- Priority 1: Chapter count
    total_chapters INTEGER,
    
    -- Priority 2: Genres/Tags
    genres TEXT, -- JSON array: ["Action", "Fantasy", ...]
    tags TEXT, -- JSON array: ["Isekai", "Magic", ...]
    
    -- Priority 3: Authors/Dates
    authors TEXT, -- JSON array: ["Author Name", ...]
    artists TEXT, -- JSON array: ["Artist Name", ...]
    start_date TEXT, -- ISO format: "2020-01-15"
    end_date TEXT, -- ISO format or NULL
    
    -- Priority 4: Quality enhancements
    cover_url_hq TEXT, -- High quality cover URL
    average_score REAL, -- 0-100 rating
    description TEXT,
    
    -- Priority 5: Status
    publishing_status TEXT, -- 'RELEASING', 'FINISHED', 'CANCELLED', 'HIATUS'
    
    -- Cache management
    is_ongoing INTEGER NOT NULL DEFAULT 1, -- 1 = ongoing, 0 = completed
    cached_at INTEGER NOT NULL, -- Unix timestamp
    last_refreshed INTEGER NOT NULL, -- Unix timestamp
    
    PRIMARY KEY(manga_url, source_id)
);

CREATE INDEX idx_metadata_cache_provider ON manga_metadata_cache(provider, provider_id);
CREATE INDEX idx_metadata_cache_refresh ON manga_metadata_cache(is_ongoing, last_refreshed);
```

**Update: `swipe_card` model (data class)**
```kotlin
// Add to SwipeCard.kt
data class SwipeCard(
    // ... existing fields ...
    
    // NEW: Metadata enhancement fields
    val metadataEnhanced: Boolean = false,
    val metadataProvider: String? = null, // "anilist", "mal", "kitsu"
    val enhancedChapterCount: Int? = null,
    val enhancedGenres: List<String>? = null,
    val enhancedTags: List<String>? = null,
    val enhancedAuthors: List<String>? = null,
    val enhancedArtists: List<String>? = null,
    val enhancedCoverUrl: String? = null,
    val enhancedScore: Float? = null,
    val enhancedDescription: String? = null,
    val enhancedStatus: String? = null,
    val enhancedStartDate: String? = null,
    val enhancedEndDate: String? = null
) {
    // Convenience properties that prefer enhanced data
    val displayChapterCount: Int? get() = enhancedChapterCount ?: totalChapters
    val displayGenres: List<String> get() = enhancedGenres ?: emptyList()
    val displayAuthors: List<String> get() = enhancedAuthors ?: listOfNotNull(author)
    val displayCoverUrl: String get() = enhancedCoverUrl ?: coverUrl
    val displayScore: Float? get() = enhancedScore
}
```

### 1.2 Repository Layer

**File: `domain/src/commonMain/kotlin/yokai/domain/metadata/MetadataRepository.kt`**
```kotlin
package yokai.domain.metadata

interface MetadataRepository {
    
    /**
     * Search for manga metadata by title
     * @param title Primary title to search
     * @param alternativeTitles Alternative titles to improve matching
     * @param contentType Content type filter (manga, manhwa, manhua, webtoon)
     * @return List of potential matches with confidence scores
     */
    suspend fun searchMetadata(
        title: String,
        alternativeTitles: List<String> = emptyList(),
        contentType: ContentType = ContentType.MANGA
    ): List<MetadataSearchResult>
    
    /**
     * Get detailed metadata for a specific provider entry
     */
    suspend fun getMetadataDetails(
        provider: MetadataProvider,
        providerId: Long
    ): MetadataDetails?
    
    /**
     * Get cached metadata for a manga
     */
    suspend fun getCachedMetadata(
        mangaUrl: String,
        sourceId: Long
    ): CachedMetadata?
    
    /**
     * Save metadata to cache
     */
    suspend fun cacheMetadata(
        mangaUrl: String,
        sourceId: Long,
        metadata: MetadataDetails,
        matchedTitle: String,
        confidence: Float
    )
    
    /**
     * Check if cached metadata should be refreshed
     */
    suspend fun shouldRefreshMetadata(
        mangaUrl: String,
        sourceId: Long
    ): Boolean
}

enum class MetadataProvider {
    ANILIST,
    MYANIMELIST,
    KITSU
}

enum class ContentType {
    MANGA,
    MANHWA,
    MANHUA,
    WEBTOON,
    LIGHT_NOVEL,
    ONE_SHOT,
    DOUJINSHI
}

data class MetadataSearchResult(
    val provider: MetadataProvider,
    val providerId: Long,
    val title: String,
    val alternativeTitles: List<String>,
    val coverUrl: String?,
    val confidence: Float, // 0.0 - 1.0
    val format: String? // "MANGA", "MANHWA", "ONE_SHOT", etc.
)

data class MetadataDetails(
    val provider: MetadataProvider,
    val providerId: Long,
    val title: String,
    val alternativeTitles: List<String>,
    
    // Priority 1
    val totalChapters: Int?,
    
    // Priority 2
    val genres: List<String>,
    val tags: List<String>,
    
    // Priority 3
    val authors: List<String>,
    val artists: List<String>,
    val startDate: String?,
    val endDate: String?,
    
    // Priority 4
    val coverUrl: String?,
    val averageScore: Float?,
    val description: String?,
    
    // Priority 5
    val status: PublishingStatus,
    
    // Additional
    val format: String?,
    val isAdult: Boolean = false
)

enum class PublishingStatus {
    RELEASING,
    FINISHED,
    CANCELLED,
    HIATUS,
    NOT_YET_RELEASED,
    UNKNOWN
}

data class CachedMetadata(
    val mangaUrl: String,
    val sourceId: Long,
    val provider: MetadataProvider,
    val providerId: Long,
    val matchedTitle: String,
    val confidence: Float,
    val metadata: MetadataDetails,
    val cachedAt: Long,
    val lastRefreshed: Long,
    val isOngoing: Boolean
)
```

### 1.3 AniList Metadata Client

**File: `app/src/main/java/eu/kanade/tachiyomi/data/metadata/anilist/AnilistMetadataClient.kt`**
```kotlin
package eu.kanade.tachiyomi.data.metadata.anilist

import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.anilist.AnilistInterceptor
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy
import yokai.domain.metadata.*

class AnilistMetadataClient(
    private val client: OkHttpClient,
    private val interceptor: AnilistInterceptor
) {
    
    private val json: Json by injectLazy()
    
    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        .build()
    
    /**
     * Search AniList for manga by title with enhanced metadata
     */
    suspend fun search(
        title: String,
        alternativeTitles: List<String> = emptyList(),
        includeAdult: Boolean = false
    ): List<MetadataSearchResult> {
        return withIOContext {
            val payload = buildJsonObject {
                put("query", searchQuery())
                putJsonObject("variables") {
                    put("query", title)
                    put("isAdult", includeAdult)
                }
            }
            
            client.newCall(
                POST(
                    AnilistApi.API_URL,
                    body = payload.toString().toRequestBody(jsonMime)
                )
            )
                .awaitSuccess()
                .parseAs<AniListSearchResponse>()
                .data.page.media
                .map { it.toMetadataSearchResult(title, alternativeTitles) }
                .sortedByDescending { it.confidence }
        }
    }
    
    /**
     * Get detailed metadata for a specific AniList manga ID
     */
    suspend fun getDetails(anilistId: Long): MetadataDetails? {
        return withIOContext {
            val payload = buildJsonObject {
                put("query", detailsQuery())
                putJsonObject("variables") {
                    put("id", anilistId)
                }
            }
            
            client.newCall(
                POST(
                    AnilistApi.API_URL,
                    body = payload.toString().toRequestBody(jsonMime)
                )
            )
                .awaitSuccess()
                .parseAs<AniListDetailsResponse>()
                .data.media
                .toMetadataDetails()
        }
    }
    
    companion object {
        /**
         * Enhanced search query with all metadata fields
         */
        fun searchQuery() = """
            |query Search(${'$'}query: String, ${'$'}isAdult: Boolean) {
                |Page (perPage: 20) {
                    |media(search: ${'$'}query, type: MANGA, isAdult: ${'$'}isAdult) {
                        |id
                        |title {
                            |romaji
                            |english
                            |native
                        |}
                        |synonyms
                        |format
                        |status
                        |chapters
                        |genres
                        |tags {
                            |name
                            |rank
                        |}
                        |coverImage {
                            |extraLarge
                            |large
                        |}
                        |averageScore
                        |isAdult
                    |}
                |}
            |}
            |
        """.trimMargin()
        
        /**
         * Detailed metadata query with authors/artists
         */
        fun detailsQuery() = """
            |query Details(${'$'}id: Int!) {
                |Media(id: ${'$'}id, type: MANGA) {
                    |id
                    |title {
                        |romaji
                        |english
                        |native
                    |}
                    |synonyms
                    |format
                    |status
                    |chapters
                    |volumes
                    |genres
                    |tags {
                        |name
                        |rank
                    |}
                    |staff {
                        |edges {
                            |node {
                                |name {
                                    |full
                                |}
                            |}
                            |role
                        |}
                    |}
                    |coverImage {
                        |extraLarge
                        |large
                    |}
                    |averageScore
                    |description
                    |startDate {
                        |year
                        |month
                        |day
                    |}
                    |endDate {
                        |year
                        |month
                        |day
                    |}
                    |isAdult
                |}
            |}
            |
        """.trimMargin()
    }
}
```

### 1.4 Metadata Matching Algorithm

**File: `app/src/main/java/eu/kanade/tachiyomi/data/metadata/MetadataMatcher.kt`**
```kotlin
package eu.kanade.tachiyomi.data.metadata

import yokai.domain.metadata.MetadataSearchResult
import kotlin.math.min

/**
 * Intelligent matching algorithm for manga titles
 * Handles alternative titles, romanization differences, and fuzzy matching
 */
class MetadataMatcher {
    
    companion object {
        private const val EXACT_MATCH_THRESHOLD = 1.0f
        private const val NORMALIZED_MATCH_THRESHOLD = 0.95f
        private const val ALT_TITLE_MATCH_THRESHOLD = 0.90f
        private const val FUZZY_MATCH_THRESHOLD = 0.85f
        private const val MINIMUM_CONFIDENCE = 0.70f
    }
    
    /**
     * Calculate confidence score for a metadata match
     * 
     * @param sourceTitle Original title from manga source
     * @param alternativeTitles Alternative titles from source (often in description)
     * @param candidateTitle Main title from metadata provider
     * @param candidateAltTitles Alternative titles from metadata provider
     * @return Confidence score 0.0-1.0, or null if below threshold
     */
    fun calculateConfidence(
        sourceTitle: String,
        alternativeTitles: List<String>,
        candidateTitle: String,
        candidateAltTitles: List<String>
    ): Float? {
        
        // 1. Exact match (case-insensitive)
        if (sourceTitle.equals(candidateTitle, ignoreCase = true)) {
            return EXACT_MATCH_THRESHOLD
        }
        
        // 2. Check alternative titles for exact match
        val allSourceTitles = listOf(sourceTitle) + alternativeTitles
        val allCandidateTitles = listOf(candidateTitle) + candidateAltTitles
        
        for (srcTitle in allSourceTitles) {
            for (candTitle in allCandidateTitles) {
                if (srcTitle.equals(candTitle, ignoreCase = true)) {
                    return ALT_TITLE_MATCH_THRESHOLD
                }
            }
        }
        
        // 3. Normalized match (remove special chars, extra spaces)
        val normalizedSource = sourceTitle.normalize()
        val normalizedCandidate = candidateTitle.normalize()
        
        if (normalizedSource.equals(normalizedCandidate, ignoreCase = true)) {
            return NORMALIZED_MATCH_THRESHOLD
        }
        
        // 4. Check normalized alternative titles
        val normalizedSourceTitles = allSourceTitles.map { it.normalize() }
        val normalizedCandidateTitles = allCandidateTitles.map { it.normalize() }
        
        for (srcTitle in normalizedSourceTitles) {
            for (candTitle in normalizedCandidateTitles) {
                if (srcTitle.equals(candTitle, ignoreCase = true)) {
                    return FUZZY_MATCH_THRESHOLD
                }
            }
        }
        
        // 5. Levenshtein distance for fuzzy matching
        val similarity = calculateSimilarity(normalizedSource, normalizedCandidate)
        
        return if (similarity >= MINIMUM_CONFIDENCE) similarity else null
    }
    
    /**
     * Select best match from multiple candidates
     * Returns null if no match exceeds 90% confidence (strict matching)
     */
    fun selectBestMatch(
        sourceTitle: String,
        alternativeTitles: List<String>,
        candidates: List<MetadataSearchResult>
    ): MetadataSearchResult? {
        
        val scoredCandidates = candidates.mapNotNull { candidate ->
            val confidence = calculateConfidence(
                sourceTitle,
                alternativeTitles,
                candidate.title,
                candidate.alternativeTitles
            )
            
            if (confidence != null) {
                candidate.copy(confidence = confidence) to confidence
            } else {
                null
            }
        }
        
        // Strict threshold: only use if confidence > 90%
        return scoredCandidates
            .filter { it.second >= 0.90f }
            .maxByOrNull { it.second }
            ?.first
    }
    
    /**
     * Normalize title for comparison
     * - Remove special characters
     * - Collapse whitespace
     * - Lowercase
     * - Remove common suffixes like "(Webtoon)", "[Official]", etc.
     */
    private fun String.normalize(): String {
        return this
            .replace(Regex("""\(.*?\)"""), "") // Remove parentheses content
            .replace(Regex("""\[.*?\]"""), "") // Remove brackets content
            .replace(Regex("""[^\p{L}\p{N}\s]"""), "") // Keep only letters, numbers, spaces
            .replace(Regex("""\s+"""), " ") // Collapse whitespace
            .trim()
            .lowercase()
    }
    
    /**
     * Calculate similarity using Levenshtein distance
     * Returns value 0.0-1.0 where 1.0 is identical
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        val distance = levenshteinDistance(s1, s2)
        val maxLength = maxOf(s1.length, s2.length)
        return if (maxLength == 0) 1.0f else 1.0f - (distance.toFloat() / maxLength)
    }
    
    /**
     * Levenshtein distance implementation
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1].equals(s2[j - 1], ignoreCase = true)) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[len1][len2]
    }
}
```

### 1.5 Metadata Enhancement Service

**File: `app/src/main/java/eu/kanade/tachiyomi/data/metadata/MetadataEnhancementService.kt`**
```kotlin
package eu.kanade.tachiyomi.data.metadata

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.metadata.anilist.AnilistMetadataClient
import eu.kanade.tachiyomi.ui.swipes.SwipeCard
import eu.kanade.tachiyomi.util.system.e
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
 */
class MetadataEnhancementService(
    private val anilistClient: AnilistMetadataClient,
    private val metadataRepository: MetadataRepository,
    private val matcher: MetadataMatcher
) {
    
    private val activeRequests = AtomicInteger(0)
    private val maxConcurrentRequests = 8
    private var rateLimitExceeded = false
    
    companion object {
        private const val RETRY_DELAY_MS = 2000L
        private const val MAX_RETRIES = 2
        private const val REQUEST_TIMEOUT_SECONDS = 10L
    }
    
    /**
     * Enhance a single card with metadata
     * Tries providers in order: AniList → MAL → Kitsu → Source fallback
     */
    suspend fun enhanceCard(card: SwipeCard): SwipeCard {
        // Check cache first
        val cached = metadataRepository.getCachedMetadata(card.url, card.sourceId)
        
        if (cached != null && !metadataRepository.shouldRefreshMetadata(card.url, card.sourceId)) {
            return card.applyMetadata(cached.metadata, cached.provider)
        }
        
        // Try fetching fresh metadata
        val metadata = fetchMetadataWithFallback(card)
        
        return if (metadata != null) {
            card.applyMetadata(metadata.first, metadata.second)
        } else {
            Logger.w { "📊 [METADATA] No metadata found for '${card.title}'" }
            card // Return original card unchanged
        }
    }
    
    /**
     * Enhance multiple cards in parallel
     * Automatically falls back to batched processing if rate limited
     */
    suspend fun enhanceCards(cards: List<SwipeCard>): List<SwipeCard> = coroutineScope {
        Logger.d { "📊 [METADATA] Enhancing ${cards.size} cards..." }
        
        if (rateLimitExceeded) {
            Logger.w { "📊 [METADATA] Rate limit detected, using batched processing" }
            return@coroutineScope enhanceCardsBatched(cards)
        }
        
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
            if (e.message?.contains("rate", ignoreCase = true) == true) {
                Logger.w { "📊 [METADATA] Rate limit exceeded, falling back to batched mode" }
                rateLimitExceeded = true
                enhanceCardsBatched(cards)
            } else {
                throw e
            }
        }
    }
    
    /**
     * Enhance cards in smaller batches with delays (rate limit safe)
     */
    private suspend fun enhanceCardsBatched(cards: List<SwipeCard>): List<SwipeCard> = coroutineScope {
        Logger.d { "📊 [METADATA] Batched enhancement: ${cards.size} cards" }
        
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
     */
    private suspend fun fetchMetadataWithFallback(
        card: SwipeCard
    ): Pair<MetadataDetails, MetadataProvider>? {
        
        // Extract alternative titles from description
        val altTitles = extractAlternativeTitles(card.description)
        
        // Try AniList first (Priority 1)
        tryProvider(MetadataProvider.ANILIST, card.title, altTitles, card.isNsfw)?.let { metadata ->
            Logger.d { "📊 [METADATA] ✓ Enhanced '${card.title}' via AniList" }
            
            // Cache the result
            metadataRepository.cacheMetadata(
                card.url,
                card.sourceId,
                metadata,
                card.title,
                0.95f // High confidence from successful match
            )
            
            return metadata to MetadataProvider.ANILIST
        }
        
        // TODO Phase 2: Try MAL
        // TODO Phase 3: Try Kitsu
        
        Logger.w { "📊 [METADATA] ✗ No metadata providers matched '${card.title}'" }
        return null
    }
    
    /**
     * Try fetching from a specific provider with retry logic
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
                return withTimeoutOrNull(REQUEST_TIMEOUT_SECONDS.seconds) {
                    when (provider) {
                        MetadataProvider.ANILIST -> tryAniList(title, altTitles, includeAdult)
                        MetadataProvider.MYANIMELIST -> null // TODO Phase 2
                        MetadataProvider.KITSU -> null // TODO Phase 3
                    }
                }
            } catch (e: Exception) {
                attempts++
                
                if (attempts > MAX_RETRIES) {
                    Logger.e(e) { "📊 [METADATA] Failed after $MAX_RETRIES retries: ${provider.name}" }
                    return null
                }
                
                Logger.w { "📊 [METADATA] Retry $attempts/$MAX_RETRIES for ${provider.name}" }
                delay(RETRY_DELAY_MS * attempts) // Exponential backoff
            }
        }
        
        return null
    }
    
    /**
     * Search AniList and return best match if confidence > 90%
     */
    private suspend fun tryAniList(
        title: String,
        altTitles: List<String>,
        includeAdult: Boolean
    ): MetadataDetails? {
        
        val searchResults = anilistClient.search(title, altTitles, includeAdult)
        
        if (searchResults.isEmpty()) {
            return null
        }
        
        // Find best match using strict confidence threshold
        val bestMatch = matcher.selectBestMatch(title, altTitles, searchResults)
        
        if (bestMatch == null || bestMatch.confidence < 0.90f) {
            Logger.d { "📊 [METADATA] No confident match for '$title' (best: ${searchResults.firstOrNull()?.confidence ?: 0f})" }
            return null
        }
        
        Logger.d { "📊 [METADATA] Matched '$title' → '${bestMatch.title}' (confidence: ${bestMatch.confidence})" }
        
        // Fetch full details
        return anilistClient.getDetails(bestMatch.providerId)
    }
    
    /**
     * Extract alternative titles from manga description
     * Common patterns: "Alternative Names: Title1, Title2" or "(Alt: Title)"
     */
    private fun extractAlternativeTitles(description: String?): List<String> {
        if (description.isNullOrBlank()) return emptyList()
        
        val patterns = listOf(
            Regex("""(?:Alternative Names?|Alt\.?|Also Known As):\s*([^\n]+)""", RegexOption.IGNORE_CASE),
            Regex("""\((?:Alt|Alternative):\s*([^)]+)\)""", RegexOption.IGNORE_CASE)
        )
        
        return patterns.flatMap { pattern ->
            pattern.findAll(description).flatMap { match ->
                match.groupValues[1]
                    .split(Regex("""[,;|]"""))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
        }.distinct()
    }
    
    /**
     * Apply metadata to a SwipeCard
     */
    private fun SwipeCard.applyMetadata(
        metadata: MetadataDetails,
        provider: MetadataProvider
    ): SwipeCard {
        return copy(
            metadataEnhanced = true,
            metadataProvider = provider.name.lowercase(),
            
            // Priority 1: Chapter count
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
            
            // Priority 5: Status
            enhancedStatus = metadata.status.name
        )
    }
}
```

### 1.6 Integration with SwipesPresenter

**File: `app/src/main/java/eu/kanade/tachiyomi/ui/swipes/SwipesPresenter.kt`**

Update the presenter to use metadata enhancement:

```kotlin
class SwipesPresenter(
    // ... existing dependencies ...
    private val metadataEnhancementService: MetadataEnhancementService = Injekt.get()
) : BasePresenter<SwipesController>() {
    
    // ... existing code ...
    
    /**
     * Load initial recommendations with metadata enhancement
     */
    fun loadInitialRecommendations() {
        presenterScope.launchIO {
            _loadingState.value = LoadingState.LOADING
            
            try {
                Logger.d { "🎴 [SWIPES] Loading initial 30 recommendations..." }
                
                // 1. Fetch basic cards from sources (existing logic)
                val basicCards = fetchCardsFromSources(count = 30)
                
                Logger.d { "📊 [METADATA] Enhancing ${basicCards.size} cards..." }
                
                // 2. Enhance cards with metadata (NEW)
                val enhancedCards = metadataEnhancementService.enhanceCards(basicCards)
                
                // 3. Update state
                withUIContext {
                    _cardQueue.value = enhancedCards.toMutableList()
                    _loadingState.value = LoadingState.SUCCESS
                }
                
                // 4. Save to cache
                saveQueueToCache()
                
                Logger.d { "✅ [SWIPES] Loaded ${enhancedCards.size} enhanced cards" }
                
                // 5. Start background prefetching
                loadMoreRecommendations()
                
            } catch (e: Exception) {
                Logger.e(e) { "❌ [SWIPES] Failed to load recommendations" }
                withUIContext {
                    _loadingState.value = LoadingState.ERROR
                }
            }
        }
    }
    
    /**
     * Load more cards in background with metadata enhancement
     */
    private fun loadMoreRecommendations() {
        presenterScope.launchIO {
            // ... existing semaphore logic ...
            
            try {
                val basicCards = fetchCardsFromSources(count = 20)
                val enhancedCards = metadataEnhancementService.enhanceCards(basicCards)
                
                withUIContext {
                    val updatedQueue = _cardQueue.value.toMutableList()
                    updatedQueue.addAll(enhancedCards)
                    _cardQueue.value = updatedQueue
                }
                
                saveQueueToCache()
                
            } finally {
                loadingSemaphore.release()
            }
        }
    }
}
```

### 1.7 UI Updates (Testing Badge)

**File: `app/src/main/res/layout/swipes_card_item.xml`**

Add debug badge to card layout:

```xml
<!-- Add this TextView in the top-right corner of the card -->
<TextView
    android:id="@+id/metadata_badge"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="8dp"
    android:layout_marginEnd="8dp"
    android:paddingStart="8dp"
    android:paddingEnd="8dp"
    android:paddingTop="4dp"
    android:paddingBottom="4dp"
    android:background="@drawable/metadata_badge_background"
    android:textSize="10sp"
    android:textColor="@android:color/white"
    android:textStyle="bold"
    android:visibility="gone"
    android:elevation="4dp"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintTop_toTopOf="parent"
    tools:text="AniList"
    tools:visibility="visible" />
```

**File: `app/src/main/res/drawable/metadata_badge_background.xml`**
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#4CAF50" />
    <corners android:radius="12dp" />
</shape>
```

**File: `app/src/main/java/eu/kanade/tachiyomi/ui/swipes/SwipesAdapter.kt`**

Update adapter to show badge:

```kotlin
class SwipesAdapter : RecyclerView.Adapter<SwipesAdapter.CardViewHolder>() {
    
    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        
        // ... existing binding logic ...
        
        // Show metadata badge (testing only)
        if (card.metadataEnhanced && card.metadataProvider != null) {
            holder.binding.metadataBadge.visibility = View.VISIBLE
            holder.binding.metadataBadge.text = card.metadataProvider.uppercase()
            
            // Different colors for different providers
            val badgeColor = when (card.metadataProvider) {
                "anilist" -> Color.parseColor("#3DB4F2") // AniList blue
                "mal" -> Color.parseColor("#2E51A2") // MAL blue
                "kitsu" -> Color.parseColor("#FD755C") // Kitsu orange
                else -> Color.parseColor("#4CAF50")
            }
            holder.binding.metadataBadge.background.setTint(badgeColor)
        } else {
            holder.binding.metadataBadge.visibility = View.GONE
        }
    }
}
```

---

## 📦 Phase 2: MyAnimeList Integration (Week 2, Days 1-4)

### 2.1 MAL Metadata Client

**File: `app/src/main/java/eu/kanade/tachiyomi/data/metadata/mal/MalMetadataClient.kt`**

```kotlin
package eu.kanade.tachiyomi.data.metadata.mal

import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy
import yokai.domain.metadata.*

class MalMetadataClient(
    private val client: OkHttpClient,
    private val interceptor: MyAnimeListInterceptor
) {
    
    private val json: Json by injectLazy()
    
    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        .build()
    
    suspend fun search(
        title: String,
        alternativeTitles: List<String> = emptyList(),
        includeAdult: Boolean = false
    ): List<MetadataSearchResult> {
        return withIOContext {
            val url = "${MyAnimeListApi.BASE_API_URL}/manga".toUri().buildUpon()
                .appendQueryParameter("q", title)
                .appendQueryParameter("limit", "20")
                .appendQueryParameter("nsfw", includeAdult.toString())
                .appendQueryParameter("fields", "alternative_titles,media_type,status,num_chapters,genres,main_picture,mean")
                .build()
            
            client.newCall(GET(url.toString()))
                .awaitSuccess()
                .parseAs<MALSearchResponse>()
                .data
                .map { it.toMetadataSearchResult(title, alternativeTitles) }
                .sortedByDescending { it.confidence }
        }
    }
    
    suspend fun getDetails(malId: Long): MetadataDetails? {
        return withIOContext {
            val url = "${MyAnimeListApi.BASE_API_URL}/manga/$malId".toUri().buildUpon()
                .appendQueryParameter("fields", "alternative_titles,media_type,status,num_chapters,num_volumes,genres,authors{first_name,last_name},main_picture,mean,synopsis,start_date,end_date,nsfw")
                .build()
            
            client.newCall(GET(url.toString()))
                .awaitSuccess()
                .parseAs<MALDetailsResponse>()
                .toMetadataDetails()
        }
    }
}
```

### 2.2 Update MetadataEnhancementService

Add MAL fallback logic to `fetchMetadataWithFallback`:

```kotlin
// Try AniList first
tryProvider(MetadataProvider.ANILIST, card.title, altTitles, card.isNsfw)?.let { ... }

// Try MAL if AniList failed (NEW)
tryProvider(MetadataProvider.MYANIMELIST, card.title, altTitles, card.isNsfw)?.let { metadata ->
    Logger.d { "📊 [METADATA] ✓ Enhanced '${card.title}' via MAL" }
    metadataRepository.cacheMetadata(...)
    return metadata to MetadataProvider.MYANIMELIST
}

// Try Kitsu (Phase 3)
// ...
```

Update `tryProvider` to handle MAL:

```kotlin
private suspend fun tryProvider(...): MetadataDetails? {
    // ... existing code ...
    
    when (provider) {
        MetadataProvider.ANILIST -> tryAniList(title, altTitles, includeAdult)
        MetadataProvider.MYANIMELIST -> tryMAL(title, altTitles, includeAdult) // NEW
        MetadataProvider.KITSU -> null // TODO Phase 3
    }
}

private suspend fun tryMAL(
    title: String,
    altTitles: List<String>,
    includeAdult: Boolean
): MetadataDetails? {
    val searchResults = malClient.search(title, altTitles, includeAdult)
    
    if (searchResults.isEmpty()) return null
    
    val bestMatch = matcher.selectBestMatch(title, altTitles, searchResults)
    
    if (bestMatch == null || bestMatch.confidence < 0.90f) {
        return null
    }
    
    Logger.d { "📊 [METADATA] Matched '$title' → '${bestMatch.title}' via MAL (confidence: ${bestMatch.confidence})" }
    
    return malClient.getDetails(bestMatch.providerId)
}
```

---

## 📦 Phase 3: Kitsu Integration (Week 2, Days 5-7)

### 3.1 Kitsu Metadata Client

Similar structure to AniList and MAL clients, leveraging existing `KitsuApi.kt`.

### 3.2 Complete Provider Fallback Chain

All three providers active with full fallback chain:
1. AniList → 2. MAL → 3. Kitsu → 4. Source metadata

---

## 📦 Phase 4: Polish & Optimization (Week 3)

### 4.1 Cache Refresh Logic

**File: `data/src/commonMain/kotlin/yokai/data/metadata/MetadataRepositoryImpl.kt`**

```kotlin
class MetadataRepositoryImpl(private val handler: DatabaseHandler) : MetadataRepository {
    
    override suspend fun shouldRefreshMetadata(
        mangaUrl: String,
        sourceId: Long
    ): Boolean {
        return handler.awaitOneOrNull {
            metadataCacheQueries.getCached(mangaUrl, sourceId)
        }?.let { cached ->
            val now = System.currentTimeMillis()
            val ageInDays = (now - cached.last_refreshed) / (1000 * 60 * 60 * 24)
            
            if (cached.is_ongoing == 1) {
                // Refresh ongoing manga every 7 days
                ageInDays > 7
            } else {
                // Never refresh completed manga
                false
            }
        } ?: true // No cache = needs refresh
    }
}
```

### 4.2 Remove Debug Badge

Before production release, remove or hide the metadata badge:

```kotlin
// In SwipesAdapter.kt
holder.binding.metadataBadge.visibility = View.GONE // Always hide in production
```

Or make it a debug-only feature:

```kotlin
if (BuildConfig.DEBUG && card.metadataEnhanced) {
    holder.binding.metadataBadge.visibility = View.VISIBLE
    // ...
}
```

### 4.3 Performance Monitoring

Add logging to track enhancement performance:

```kotlin
suspend fun enhanceCards(cards: List<SwipeCard>): List<SwipeCard> = coroutineScope {
    val startTime = System.currentTimeMillis()
    
    val enhanced = // ... enhancement logic ...
    
    val duration = System.currentTimeMillis() - startTime
    val enhancedCount = enhanced.count { it.metadataEnhanced }
    
    Logger.d { 
        "📊 [METADATA] Enhanced $enhancedCount/${cards.size} cards in ${duration}ms " +
        "(avg ${duration / cards.size}ms per card)"
    }
    
    enhanced
}
```

### 4.4 NSFW Filtering Integration

Update enhancement to respect NSFW settings:

```kotlin
private suspend fun fetchMetadataWithFallback(card: SwipeCard): Pair<MetadataDetails, MetadataProvider>? {
    
    val altTitles = extractAlternativeTitles(card.description)
    
    // Respect Swipes NSFW filter setting
    val includeAdult = card.isNsfw // Use existing Swipes filter logic
    
    tryProvider(MetadataProvider.ANILIST, card.title, altTitles, includeAdult)?.let { metadata ->
        // Additional filter: reject if metadata provider marks as adult but Swipes doesn't allow
        if (metadata.isAdult && !includeAdult) {
            Logger.w { "📊 [METADATA] Rejected adult content: '${card.title}'" }
            return null
        }
        
        // ... rest of logic
    }
}
```

---

## 🧪 Testing Strategy

### Phase 1 Testing (AniList Only)
1. **Unit Tests**:
   - `MetadataMatcherTest` - Test confidence scoring
   - `AnilistMetadataClientTest` - Mock API responses
   
2. **Integration Tests**:
   - Load 30 cards and verify enhancement
   - Test cache hit/miss scenarios
   - Verify alternative title matching
   
3. **Manual Testing**:
   - Test with popular manga (should match easily)
   - Test with obscure titles (should gracefully fail)
   - Test with webtoons/manhwa (different naming)
   - Verify debug badge appears correctly

### Phase 2 Testing (MAL Fallback)
1. Test cards that fail AniList matching
2. Verify MAL provides fallback metadata
3. Test provider priority (AniList before MAL)

### Phase 3 Testing (Complete Chain)
1. End-to-end testing of all providers
2. Rate limit handling verification
3. Performance benchmarks (30 cards in <10 seconds)

### Phase 4 Testing (Production Readiness)
1. Cache refresh logic for ongoing manga
2. NSFW filter integration
3. Memory leak testing
4. Remove debug badges

---

## 📊 Success Metrics

### Performance Targets
- **Enhancement Speed**: <10 seconds for 30 cards (parallel mode)
- **Match Rate**: >70% of cards successfully enhanced
- **Confidence**: >90% confidence for all matched cards
- **Cache Hit Rate**: >80% for previously seen manga

### Quality Metrics
- **Chapter Count Accuracy**: Validate against known manga
- **Genre Coverage**: >5 genres per enhanced card
- **Author Completeness**: >90% of enhanced cards have author

---

## 🚀 Rollout Plan

### Week 1: Phase 1
- Days 1-2: Database schema, repository interfaces
- Days 3-4: AniList client, metadata matcher
- Days 5-6: Enhancement service, presenter integration
- Day 7: Testing and bug fixes

### Week 2: Phases 2-3
- Days 1-4: MAL integration and testing
- Days 5-7: Kitsu integration and testing

### Week 3: Phase 4
- Days 1-3: Polish, optimization, performance tuning
- Days 4-5: Remove debug features, final testing
- Days 6-7: Documentation and deployment

---

## 📝 Implementation Checklist

### Phase 1: AniList Foundation
- [ ] Create database migration 34.sqm
- [ ] Create MetadataRepository interface
- [ ] Implement MetadataRepositoryImpl with SQLDelight
- [ ] Create AnilistMetadataClient
- [ ] Implement MetadataMatcher algorithm
- [ ] Create MetadataEnhancementService
- [ ] Update SwipeCard data class
- [ ] Integrate with SwipesPresenter
- [ ] Add debug badge to card layout
- [ ] Update SwipesAdapter for badge display
- [ ] Write unit tests
- [ ] Manual testing

### Phase 2: MAL Integration
- [ ] Create MalMetadataClient
- [ ] Update MetadataEnhancementService with MAL fallback
- [ ] Add MAL-specific DTO mappings
- [ ] Test MAL search and details
- [ ] Verify fallback chain works
- [ ] Performance testing

### Phase 3: Kitsu Integration
- [ ] Create KitsuMetadataClient
- [ ] Complete provider fallback chain
- [ ] Test all three providers
- [ ] Verify graceful degradation

### Phase 4: Production Polish
- [ ] Implement cache refresh logic
- [ ] Add performance monitoring
- [ ] Integrate NSFW filtering
- [ ] Remove/hide debug badges
- [ ] Final QA testing
- [ ] Documentation
- [ ] Deployment

---

## 🔧 Configuration & Settings

### Future Enhancement: User Settings

For future phases, consider adding user-facing settings:

```kotlin
// In PreferencesHelper.kt
fun metadataEnhancementEnabled() = 
    flowPrefs.getBoolean("metadata_enhancement_enabled", true)

fun preferredMetadataProvider() = 
    flowPrefs.getString("preferred_metadata_provider", "anilist")
```

But for initial rollout, keep it invisible to users.

---

## 📖 API Documentation References

- **AniList GraphQL**: https://anilist.gitbook.io/anilist-apiv2-docs
- **MyAnimeList API v2**: https://myanimelist.net/apiconfig/references/api/v2
- **Kitsu API**: https://kitsu.docs.apiary.io/

---

## 🎯 Next Steps

After approval of this plan:

1. **Immediate**: Start Phase 1 database schema and repository setup
2. **Day 2**: Begin AniList client implementation
3. **Day 3**: Metadata matching algorithm
4. **Day 4**: Enhancement service and presenter integration
5. **Day 5-7**: Testing and iteration

**Estimated total development time**: 2-3 weeks for complete implementation.

Would you like me to begin implementation starting with Phase 1?
