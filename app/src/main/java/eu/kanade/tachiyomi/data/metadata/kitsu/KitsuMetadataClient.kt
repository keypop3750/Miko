package eu.kanade.tachiyomi.data.metadata.kitsu

import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.track.kitsu.KitsuApi
import eu.kanade.tachiyomi.data.track.kitsu.KitsuInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.network.decodeFromJsonResponse
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy
import yokai.domain.metadata.*
import kotlin.math.max

/**
 * Kitsu metadata client for swipes metadata enhancement
 * Uses Kitsu's Algolia search API for comprehensive manga metadata
 * Note: Uses public APIs that don't require authentication
 */
class KitsuMetadataClient(
    private val client: OkHttpClient
) {
    private val json: Json by injectLazy()
    
    // Custom JSON configuration for Kitsu API that handles null values
    private val kitsuJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true // This will handle null values in non-nullable fields
        encodeDefaults = false
    }
    
    // Custom parsing extension that uses our null-safe JSON configuration
    private inline fun <reified T> Response.parseAsKitsu(): T {
        return kitsuJson.decodeFromJsonResponse(kotlinx.serialization.serializer(), this)
    }
    
    /**
     * Search Kitsu for manga by title with enhanced metadata
     */
    suspend fun search(
        title: String,
        alternativeTitles: List<String> = emptyList(),
        includeAdult: Boolean = false
    ): List<MetadataSearchResult> {
        return withIOContext {
            try {
                // First get the Algolia search key
                val searchKey = getAlgoliaKey()
                
                // Perform Algolia search with enhanced fields
                val searchResults = performAlgoliaSearch(searchKey, title, includeAdult)
                
                // Convert to MetadataSearchResult with confidence scoring
                searchResults.map { item ->
                    MetadataSearchResult(
                        provider = MetadataProvider.KITSU,
                        providerId = item.id,
                        title = item.canonicalTitle,
                        alternativeTitles = item.titles?.values?.filterNotNull() ?: emptyList(),
                        coverUrl = item.posterImage?.original,
                        confidence = calculateConfidence(title, alternativeTitles, item),
                        format = item.subtype
                    )
                }.sortedByDescending { it.confidence }
                
            } catch (e: Exception) {
                Logger.e(e) { "Failed to search Kitsu for '$title'" }
                emptyList()
            }
        }
    }
    
    /**
     * Get detailed metadata for a specific Kitsu manga ID
     */
    suspend fun getDetails(kitsuId: Long): MetadataDetails? {
        return withIOContext {
            try {
                // Use the public Kitsu API for detailed manga information
                val url = "${BASE_URL}manga/$kitsuId".toUri().buildUpon()
                    .appendQueryParameter("include", "categories,staff")
                    .build()
                
                val response = client.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAsKitsu<KitsuDetailsResponse>()
                
                response.toMetadataDetails()
                
            } catch (e: Exception) {
                Logger.e(e) { "Failed to get Kitsu details for ID: $kitsuId" }
                null
            }
        }
    }
    
    /**
     * Get Algolia search key from Kitsu API
     */
    private suspend fun getAlgoliaKey(): String {
        return client.newCall(GET(ALGOLIA_KEY_URL))
            .awaitSuccess()
            .parseAsKitsu<KitsuAlgoliaKeyResponse>()
            .media.key
    }
    
    /**
     * Perform Algolia search with enhanced metadata fields
     */
    private suspend fun performAlgoliaSearch(
        key: String,
        query: String,
        includeAdult: Boolean
    ): List<KitsuAlgoliaSearchItem> {
        val algoliaFilter = if (includeAdult) {
            "&facetFilters=[[\"kind:manga\",\"kind:manhua\",\"kind:manhwa\"]]"
        } else {
            "&facetFilters=[[\"kind:manga\",\"kind:manhua\",\"kind:manhwa\"],[\"ageRating:G\",\"ageRating:PG\",\"ageRating:PG13\"]]"
        }
        
        val jsonObject = buildJsonObject {
            put("params", "query=$query$algoliaFilter")
        }
        
        return client.newCall(
            POST(
                ALGOLIA_URL,
                headers = headersOf(
                    "X-Algolia-Application-Id", ALGOLIA_APP_ID,
                    "X-Algolia-API-Key", key
                ),
                body = jsonObject.toString().toRequestBody(jsonMime)
            )
        )
            .awaitSuccess()
            .parseAsKitsu<KitsuAlgoliaSearchResult>()
            .hits
            .filter { it.subtype != "novel" } // Filter out novels
    }
    
    /**
     * Calculate confidence score for Kitsu search result
     */
    private fun calculateConfidence(
        sourceTitle: String,
        alternativeTitles: List<String>,
        item: KitsuAlgoliaSearchItem
    ): Float {
        val allSourceTitles = listOf(sourceTitle) + alternativeTitles
        val allKitsuTitles = listOf(item.canonicalTitle) + (item.titles?.values?.filterNotNull() ?: emptyList())
        
        // Check for exact matches
        for (srcTitle in allSourceTitles) {
            for (kitsuTitle in allKitsuTitles) {
                if (srcTitle.equals(kitsuTitle, ignoreCase = true)) {
                    return 1.0f
                }
            }
        }
        
        // Check normalized matches
        for (srcTitle in allSourceTitles) {
            for (kitsuTitle in allKitsuTitles) {
                val normalizedSrc = srcTitle.normalize()
                val normalizedKitsu = kitsuTitle.normalize()
                
                if (normalizedSrc.equals(normalizedKitsu, ignoreCase = true)) {
                    return 0.95f
                }
                
                // Fuzzy matching using simple similarity
                val similarity = calculateSimilarity(normalizedSrc, normalizedKitsu)
                if (similarity >= 0.85f) {
                    return similarity
                }
            }
        }
        
        return 0.0f
    }
    
    /**
     * Normalize title for comparison
     */
    private fun String.normalize(): String {
        return this
            .replace(Regex("""\(.*?\)"""), "") // Remove parentheses
            .replace(Regex("""\[.*?\]"""), "") // Remove brackets
            .replace(Regex("""[^\p{L}\p{N}\s]"""), "") // Keep letters, numbers, spaces
            .replace(Regex("""\s+"""), " ") // Collapse whitespace
            .trim()
            .lowercase()
    }
    
    /**
     * Calculate similarity using simple string distance
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        val distance = levenshteinDistance(s1, s2)
        val maxLength = max(s1.length, s2.length)
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
    
    companion object {
        private const val BASE_URL = "https://kitsu.app/api/edge/"
        private const val ALGOLIA_KEY_URL = "https://kitsu.app/api/edge/algolia-keys/media/"
        private const val ALGOLIA_URL = "https://AWQO5J657S-dsn.algolia.net/1/indexes/production_media/query/"
        private const val ALGOLIA_APP_ID = "AWQO5J657S"
    }
}

// =====================================================
// SERIALIZATION MODELS FOR KITSU API
// =====================================================

/**
 * Response model for Algolia search key
 */
@kotlinx.serialization.Serializable
data class KitsuAlgoliaKeyResponse(
    val media: KitsuAlgoliaKeyData
)

@kotlinx.serialization.Serializable
data class KitsuAlgoliaKeyData(
    val key: String
)

/**
 * Enhanced Algolia search result with additional metadata fields
 */
@kotlinx.serialization.Serializable
data class KitsuAlgoliaSearchResult(
    val hits: List<KitsuAlgoliaSearchItem>
)

@kotlinx.serialization.Serializable
data class KitsuAlgoliaSearchItem(
    val id: Long,
    val canonicalTitle: String,
    val titles: Map<String, String?>? = null, // Alternative titles with nullable values for null entries
    val chapterCount: Long? = null,
    val subtype: String? = null, // "manga", "manhwa", "manhua", etc.
    val posterImage: KitsuPosterImage? = null,
    val synopsis: String? = null,
    val averageRating: Double? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val status: String? = null, // "current", "finished", etc.
    val ageRating: String? = null, // "G", "PG", "PG13", "R", etc.
    val categories: List<String>? = null, // Genre categories
    val serialization: String? = null // Publication info
)

@kotlinx.serialization.Serializable
data class KitsuPosterImage(
    val original: String? = null,
    val large: String? = null,
    val medium: String? = null
)

/**
 * Detailed manga response from Kitsu API
 */
@kotlinx.serialization.Serializable
data class KitsuDetailsResponse(
    val data: KitsuMangaData,
    val included: List<KitsuIncludedData> = emptyList()
)

@kotlinx.serialization.Serializable
data class KitsuMangaData(
    val id: String,
    val type: String,
    val attributes: KitsuMangaAttributes
)

@kotlinx.serialization.Serializable
data class KitsuMangaAttributes(
    val canonicalTitle: String,
    val titles: Map<String, String>? = null,
    val chapterCount: Long? = null,
    val volumeCount: Long? = null,
    val subtype: String? = null,
    val synopsis: String? = null,
    val averageRating: Double? = null,
    val status: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val ageRating: String? = null,
    val posterImage: Map<String, String>? = null // URLs for different sizes
)

@kotlinx.serialization.Serializable
data class KitsuIncludedData(
    val id: String,
    val type: String,
    val attributes: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
)

/**
 * Extension function to convert KitsuDetailsResponse to MetadataDetails
 */
fun KitsuDetailsResponse.toMetadataDetails(): MetadataDetails {
    val manga = data.attributes
    
    // Extract categories/genres from included data
    val categories = included
        .filter { it.type == "categories" }
        .mapNotNull { 
            it.attributes["title"]?.let { title ->
                if (title is kotlinx.serialization.json.JsonPrimitive && title.isString) {
                    title.content
                } else null
            }
        }
    
    // Map Kitsu status to our PublishingStatus enum
    val publishingStatus = when (manga.status?.lowercase()) {
        "current" -> PublishingStatus.RELEASING
        "finished" -> PublishingStatus.FINISHED
        "tba" -> PublishingStatus.NOT_YET_RELEASED
        "unreleased" -> PublishingStatus.NOT_YET_RELEASED
        "upcoming" -> PublishingStatus.NOT_YET_RELEASED
        else -> PublishingStatus.UNKNOWN
    }
    
    return MetadataDetails(
        provider = MetadataProvider.KITSU,
        providerId = data.id.toLongOrNull() ?: 0L,
        title = manga.canonicalTitle,
        alternativeTitles = manga.titles?.values?.toList() ?: emptyList(),
        
        // Priority 1: Chapter count
        totalChapters = manga.chapterCount?.toInt(),
        
        // Priority 2: Genres/Tags (Kitsu uses categories)
        genres = categories,
        tags = emptyList(), // Kitsu doesn't have separate tags
        
        // Priority 3: Authors/Artists (TODO: extract from staff relationships if needed)
        authors = emptyList(), // Would need additional API call
        artists = emptyList(), // Would need additional API call
        startDate = manga.startDate,
        endDate = manga.endDate,
        
        // Priority 4: Quality enhancements
        coverUrl = manga.posterImage?.get("original") 
            ?: manga.posterImage?.get("large")
            ?: manga.posterImage?.get("medium"),
        averageScore = manga.averageRating?.toFloat(),
        description = manga.synopsis,
        
        // Priority 5: Status
        status = publishingStatus,
        
        // Additional
        format = manga.subtype,
        isAdult = when (manga.ageRating?.uppercase()) {
            "R", "R18", "RX" -> true
            else -> false
        }
    )
}