package eu.kanade.tachiyomi.data.metadata.mangadex

import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy
import yokai.domain.metadata.*
import kotlin.math.max

/**
 * MangaDex metadata client for comprehensive manhwa, webtoon, manhua, and manga metadata
 * Uses MangaDex API v5 - public endpoints, no authentication required
 * 
 * API Documentation: https://api.mangadex.org/docs/
 */
class MangaDexMetadataClient(
    private val client: OkHttpClient
) {
    private val json: Json by injectLazy()
    
    companion object {
        private const val BASE_URL = "https://api.mangadex.org"
        private const val SEARCH_LIMIT = 10
    }
    
    /**
     * Search MangaDex for manga/manhwa/webtoon by title
     */
    suspend fun search(
        title: String,
        alternativeTitles: List<String> = emptyList(),
        includeAdult: Boolean = false
    ): List<MetadataSearchResult> {
        return withIOContext {
            try {
                val url = "$BASE_URL/manga".toUri().buildUpon()
                    .appendQueryParameter("title", title)
                    .appendQueryParameter("limit", SEARCH_LIMIT.toString())
                    .appendQueryParameter("includes[]", "cover_art")
                    .appendQueryParameter("includes[]", "author")
                    .appendQueryParameter("includes[]", "artist")
                    .appendQueryParameter("order[relevance]", "desc")
                    .apply {
                        // Content ratings
                        appendQueryParameter("contentRating[]", "safe")
                        appendQueryParameter("contentRating[]", "suggestive")
                        if (includeAdult) {
                            appendQueryParameter("contentRating[]", "erotica")
                            appendQueryParameter("contentRating[]", "pornographic")
                        }
                    }
                    .build()
                
                val response = client.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MangaDexSearchResponse>()
                
                response.data.map { manga ->
                    val coverArt = manga.relationships
                        ?.firstOrNull { it.type == "cover_art" }
                        ?.attributes?.fileName
                    
                    val coverUrl = if (coverArt != null) {
                        "https://uploads.mangadex.org/covers/${manga.id}/$coverArt.256.jpg"
                    } else null
                    
                    // Extract all titles for better matching
                    val allTitles = buildList {
                        manga.attributes.title.values.forEach { add(it) }
                        manga.attributes.altTitles?.forEach { titleMap ->
                            titleMap.values.forEach { add(it) }
                        }
                    }
                    
                    MetadataSearchResult(
                        provider = MetadataProvider.MANGADEX,
                        providerId = manga.id.hashCode().toLong(),
                        title = manga.attributes.title.values.firstOrNull() ?: title,
                        alternativeTitles = allTitles,
                        coverUrl = coverUrl,
                        confidence = calculateConfidence(title, alternativeTitles, manga),
                        format = manga.attributes.publicationDemographic
                    )
                }.sortedByDescending { it.confidence }
                
            } catch (e: Exception) {
                Logger.e(e) { "Failed to search MangaDex for '$title'" }
                emptyList()
            }
        }
    }
    
    /**
     * Get detailed metadata for a specific MangaDex manga
     */
    suspend fun getDetails(mangaDexId: String): MetadataDetails? {
        return withIOContext {
            try {
                val url = "$BASE_URL/manga/$mangaDexId".toUri().buildUpon()
                    .appendQueryParameter("includes[]", "cover_art")
                    .appendQueryParameter("includes[]", "author")
                    .appendQueryParameter("includes[]", "artist")
                    .build()
                
                val response = client.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MangaDexMangaResponse>()
                
                response.data.toMetadataDetails()
                
            } catch (e: Exception) {
                Logger.e(e) { "Failed to get MangaDex details for ID: $mangaDexId" }
                null
            }
        }
    }
    
    /**
     * Calculate confidence score for MangaDex search result
     */
    private fun calculateConfidence(
        sourceTitle: String,
        alternativeTitles: List<String>,
        manga: MangaDexManga
    ): Float {
        val allSourceTitles = listOf(sourceTitle) + alternativeTitles
        
        // Extract all MangaDex titles
        val allMangaDexTitles = buildList {
            manga.attributes.title.values.forEach { add(it) }
            manga.attributes.altTitles?.forEach { titleMap ->
                titleMap.values.forEach { add(it) }
            }
        }
        
        // Check for exact matches
        for (srcTitle in allSourceTitles) {
            for (mdTitle in allMangaDexTitles) {
                if (srcTitle.equals(mdTitle, ignoreCase = true)) {
                    return 1.0f
                }
            }
        }
        
        // Check normalized matches
        for (srcTitle in allSourceTitles) {
            for (mdTitle in allMangaDexTitles) {
                val normalizedSrc = srcTitle.normalize()
                val normalizedMd = mdTitle.normalize()
                
                if (normalizedSrc.equals(normalizedMd, ignoreCase = true)) {
                    return 0.95f
                }
                
                // Fuzzy matching
                val similarity = calculateSimilarity(normalizedSrc, normalizedMd)
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
            .replace(Regex("""\(.*?\)"""), "")
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""[^\p{L}\p{N}\s]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()
    }
    
    /**
     * Calculate string similarity using Levenshtein distance
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        val longerLength = longer.length
        if (longerLength == 0) return 1.0f
        
        return ((longerLength - editDistance(longer, shorter)) / longerLength.toFloat())
    }
    
    /**
     * Calculate Levenshtein edit distance
     */
    private fun editDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else if (j > 0) {
                    var newValue = costs[j - 1]
                    if (s1[i - 1] != s2[j - 1]) {
                        newValue = minOf(newValue, lastValue, costs[j]) + 1
                    }
                    costs[j - 1] = lastValue
                    lastValue = newValue
                }
            }
            if (i > 0) {
                costs[s2.length] = lastValue
            }
        }
        
        return costs[s2.length]
    }
}

/**
 * Extension to convert MangaDex manga to MetadataDetails
 */
private fun MangaDexManga.toMetadataDetails(): MetadataDetails {
    val coverArt = relationships
        ?.firstOrNull { it.type == "cover_art" }
        ?.attributes?.fileName
    
    val coverUrl = if (coverArt != null) {
        "https://uploads.mangadex.org/covers/$id/$coverArt.512.jpg"
    } else null
    
    val authors = relationships
        ?.filter { it.type == "author" }
        ?.mapNotNull { it.attributes?.name }
        ?: emptyList()
    
    val artists = relationships
        ?.filter { it.type == "artist" }
        ?.mapNotNull { it.attributes?.name }
        ?: emptyList()
    
    // Extract description (prefer English)
    val description = attributes.description?.get("en") 
        ?: attributes.description?.values?.firstOrNull()
    
    // Extract tags
    val tags = attributes.tags?.mapNotNull { tag ->
        tag.attributes.name.get("en") ?: tag.attributes.name.values.firstOrNull()
    } ?: emptyList()
    
    return MetadataDetails(
        provider = MetadataProvider.MANGADEX,
        providerId = id.hashCode().toLong(),
        title = attributes.title.values.firstOrNull() ?: "",
        alternativeTitles = buildList {
            attributes.altTitles?.forEach { titleMap ->
                titleMap.values.forEach { add(it) }
            }
        },
        totalChapters = attributes.lastChapter?.toIntOrNull(),
        genres = attributes.tags?.mapNotNull { tag ->
            tag.attributes.name.get("en") ?: tag.attributes.name.values.firstOrNull()
        } ?: emptyList(),
        tags = tags,
        authors = authors,
        artists = artists,
        startDate = attributes.year?.toString(),
        endDate = null,
        coverUrl = coverUrl,
        averageScore = null, // MangaDex doesn't provide ratings in API
        description = description,
        status = when (attributes.status) {
            "ongoing" -> PublishingStatus.RELEASING
            "completed" -> PublishingStatus.FINISHED
            "hiatus" -> PublishingStatus.HIATUS
            "cancelled" -> PublishingStatus.CANCELLED
            else -> PublishingStatus.UNKNOWN
        },
        format = attributes.publicationDemographic,
        isAdult = attributes.contentRating in listOf("erotica", "pornographic")
    )
}

// ========== MangaDex API Models ==========

@Serializable
data class MangaDexSearchResponse(
    val result: String,
    val response: String,
    val data: List<MangaDexManga>
)

@Serializable
data class MangaDexMangaResponse(
    val result: String,
    val response: String,
    val data: MangaDexManga
)

@Serializable
data class MangaDexManga(
    val id: String,
    val type: String,
    val attributes: MangaDexMangaAttributes,
    val relationships: List<MangaDexRelationship>? = null
)

@Serializable
data class MangaDexMangaAttributes(
    val title: Map<String, String>,
    val altTitles: List<Map<String, String>>? = null,
    val description: Map<String, String>? = null,
    val status: String? = null,
    val year: Int? = null,
    val contentRating: String? = null,
    val tags: List<MangaDexTag>? = null,
    val publicationDemographic: String? = null,
    val originalLanguage: String? = null,
    val lastVolume: String? = null,
    val lastChapter: String? = null
)

@Serializable
data class MangaDexTag(
    val id: String,
    val type: String,
    val attributes: MangaDexTagAttributes
)

@Serializable
data class MangaDexTagAttributes(
    val name: Map<String, String>
)

@Serializable
data class MangaDexRelationship(
    val id: String,
    val type: String,
    val attributes: MangaDexRelationshipAttributes? = null
)

@Serializable
data class MangaDexRelationshipAttributes(
    val name: String? = null,
    val fileName: String? = null
)
