package eu.kanade.tachiyomi.data.metadata.anilist

import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy
import yokai.domain.metadata.*

/**
 * AniList metadata client for enhanced Swipes card data
 * Leverages existing AniList API infrastructure but fetches richer metadata
 */
class AnilistMetadataClient(
    private val client: OkHttpClient
) {
    
    private val json: Json by injectLazy()
    
    /**
     * Search AniList for manga by title with enhanced metadata
     * No authentication needed for search queries
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
                    "https://graphql.anilist.co",
                    body = payload.toString().toRequestBody(jsonMime)
                )
            )
                .awaitSuccess()
                .parseAs<AniListSearchResponse>()
                .data.Page.media
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
                    "https://graphql.anilist.co",
                    body = payload.toString().toRequestBody(jsonMime)
                )
            )
                .awaitSuccess()
                .parseAs<AniListDetailsResponse>()
                .data.Media
                .toMetadataDetails()
        }
    }
    
    companion object {
        /**
         * Enhanced search query with all metadata fields
         * Optimized for Swipes cards
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
                            |isMediaSpoiler
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
         * Detailed metadata query with authors/artists and complete information
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
                        |isMediaSpoiler
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

// ========== DTOs ==========

@Serializable
data class AniListSearchResponse(
    val data: AniListSearchData
)

@Serializable
data class AniListSearchData(
    val Page: AniListPage
)

@Serializable
data class AniListPage(
    val media: List<AniListMedia>
)

@Serializable
data class AniListMedia(
    val id: Long,
    val title: AniListTitle,
    val synonyms: List<String> = emptyList(),
    val format: String? = null,
    val status: String? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val genres: List<String> = emptyList(),
    val tags: List<AniListTag> = emptyList(),
    val coverImage: AniListCoverImage? = null,
    val averageScore: Int? = null,
    val isAdult: Boolean = false
)

@Serializable
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null
) {
    fun preferredTitle(): String {
        return english ?: romaji ?: native ?: "Unknown"
    }
    
    fun allTitles(): List<String> {
        return listOfNotNull(romaji, english, native).distinct()
    }
}

@Serializable
data class AniListTag(
    val name: String,
    val rank: Int = 0,
    val isMediaSpoiler: Boolean = false
)

@Serializable
data class AniListCoverImage(
    val extraLarge: String? = null,
    val large: String? = null
) {
    fun bestQuality(): String? = extraLarge ?: large
}

@Serializable
data class AniListDetailsResponse(
    val data: AniListDetailsData
)

@Serializable
data class AniListDetailsData(
    val Media: AniListMediaDetails
)

@Serializable
data class AniListMediaDetails(
    val id: Long,
    val title: AniListTitle,
    val synonyms: List<String> = emptyList(),
    val format: String? = null,
    val status: String? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val genres: List<String> = emptyList(),
    val tags: List<AniListTag> = emptyList(),
    val staff: AniListStaff? = null,
    val coverImage: AniListCoverImage? = null,
    val averageScore: Int? = null,
    val description: String? = null,
    val startDate: AniListDate? = null,
    val endDate: AniListDate? = null,
    val isAdult: Boolean = false
)

@Serializable
data class AniListStaff(
    val edges: List<AniListStaffEdge> = emptyList()
)

@Serializable
data class AniListStaffEdge(
    val node: AniListStaffNode,
    val role: String? = null
)

@Serializable
data class AniListStaffNode(
    val name: AniListStaffName
)

@Serializable
data class AniListStaffName(
    val full: String
)

@Serializable
data class AniListDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
) {
    fun toIsoString(): String? {
        if (year == null) return null
        val m = month?.toString()?.padStart(2, '0') ?: "01"
        val d = day?.toString()?.padStart(2, '0') ?: "01"
        return "$year-$m-$d"
    }
}

// ========== Extension Functions ==========

/**
 * Convert AniList search result to metadata search result
 * Includes basic confidence calculation
 */
fun AniListMedia.toMetadataSearchResult(
    searchTitle: String,
    alternativeTitles: List<String>
): MetadataSearchResult {
    // Basic confidence: exact match = 1.0, contains = 0.8, else 0.5
    val allTitles = title.allTitles() + synonyms
    val confidence = when {
        allTitles.any { it.equals(searchTitle, ignoreCase = true) } -> 1.0f
        allTitles.any { it.contains(searchTitle, ignoreCase = true) } -> 0.8f
        else -> 0.5f
    }
    
    return MetadataSearchResult(
        provider = MetadataProvider.ANILIST,
        providerId = id,
        title = title.preferredTitle(),
        alternativeTitles = synonyms,
        coverUrl = coverImage?.bestQuality(),
        confidence = confidence,
        format = format
    )
}

/**
 * Convert AniList detailed response to metadata details
 */
fun AniListMediaDetails.toMetadataDetails(): MetadataDetails {
    // Extract authors (Original Creator) and artists from staff
    val authors = staff?.edges
        ?.filter { it.role?.contains("Story", ignoreCase = true) == true || 
                   it.role?.contains("Original Creator", ignoreCase = true) == true }
        ?.map { it.node.name.full }
        ?.distinct() ?: emptyList()
    
    val artists = staff?.edges
        ?.filter { it.role?.contains("Art", ignoreCase = true) == true }
        ?.map { it.node.name.full }
        ?.distinct() ?: emptyList()
    
    // Top tags (non-spoiler, ranked by importance)
    val topTags = tags
        .filter { !it.isMediaSpoiler }
        .sortedByDescending { it.rank }
        .take(15)
        .map { it.name }
    
    return MetadataDetails(
        provider = MetadataProvider.ANILIST,
        providerId = id,
        title = title.preferredTitle(),
        alternativeTitles = synonyms,
        totalChapters = chapters,
        genres = genres,
        tags = topTags,
        authors = authors,
        artists = artists,
        startDate = startDate?.toIsoString(),
        endDate = endDate?.toIsoString(),
        coverUrl = coverImage?.bestQuality(),
        averageScore = averageScore?.toFloat(),
        description = description?.stripHtml(),
        status = PublishingStatus.fromString(status),
        format = format,
        isAdult = isAdult
    )
}

/**
 * Strip HTML tags from AniList descriptions
 */
private fun String.stripHtml(): String {
    return this
        .replace(Regex("<br>"), "\n")
        .replace(Regex("<[^>]*>"), "")
        .trim()
}
