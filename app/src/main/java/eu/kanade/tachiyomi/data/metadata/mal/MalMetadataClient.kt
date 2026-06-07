package eu.kanade.tachiyomi.data.metadata.mal

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import yokai.domain.metadata.*

/**
 * MyAnimeList metadata client for enhanced manga information
 * Uses MAL's public API for search and details retrieval
 * 
 * Rate limits: ~3 requests per second (conservative)
 * No authentication required for basic manga data
 */
class MalMetadataClient(
    private val client: OkHttpClient
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    companion object {
        private const val BASE_URL = "https://api.myanimelist.net/v2"
        private const val MAX_SEARCH_RESULTS = 20
    }
    
    /**
     * Search for manga by title and alternative titles
     * Returns up to MAX_SEARCH_RESULTS results for matching
     */
    suspend fun search(
        title: String,
        alternativeTitles: List<String> = emptyList(),
        includeAdult: Boolean = false
    ): List<MetadataSearchResult> = withIOContext {
        Logger.d { "📊 [MAL] Searching for '$title' (${alternativeTitles.size} alt titles)" }
        
        val allTitles = listOf(title) + alternativeTitles
        val results = mutableListOf<MetadataSearchResult>()
        
        // Search with each title variation
        for (searchTitle in allTitles.take(3)) { // Limit to avoid spam
            try {
                val searchResults = searchByTitle(searchTitle, includeAdult)
                results.addAll(searchResults)
                
                if (results.size >= MAX_SEARCH_RESULTS) break
            } catch (e: Exception) {
                Logger.w(e) { "📊 [MAL] Search failed for '$searchTitle'" }
            }
        }
        
        Logger.d { "📊 [MAL] Found ${results.size} search results for '$title'" }
        results.distinctBy { it.providerId }.take(MAX_SEARCH_RESULTS)
    }
    
    /**
     * Get detailed information for a specific manga by MAL ID
     */
    suspend fun getDetails(malId: Long): MetadataDetails? = withIOContext {
        Logger.d { "📊 [MAL] Fetching details for ID: $malId" }
        
        try {
            val url = "$BASE_URL/manga/$malId?fields=id,title,main_picture,alternative_titles," +
                    "start_date,end_date,synopsis,mean,rank,popularity,num_list_users," +
                    "num_scoring_users,nsfw,created_at,updated_at,media_type,status," +
                    "genres,my_list_status,num_volumes,num_chapters,authors{first_name,last_name}," +
                    "pictures,background,related_anime,related_manga,recommendations," +
                    "studios,statistics"
            
            val request = GET(url).newBuilder().apply {
                malHeaders().forEach { (key, value) ->
                    addHeader(key, value)
                }
            }.build()
            
            val response = client.newCall(request).awaitSuccess()
            val malManga = response.parseAs<MalMangaDetails>()
            
            val details = malManga.toMetadataDetails()
            Logger.d { "📊 [MAL] ✓ Retrieved details for '${details.title}'" }
            
            details
        } catch (e: Exception) {
            Logger.e(e) { "📊 [MAL] Failed to fetch details for ID: $malId" }
            null
        }
    }
    
    /**
     * Search MAL by a single title
     */
    private suspend fun searchByTitle(
        title: String,
        includeAdult: Boolean
    ): List<MetadataSearchResult> {
        val url = "$BASE_URL/manga?q=${java.net.URLEncoder.encode(title, "UTF-8")}" +
                "&limit=$MAX_SEARCH_RESULTS&fields=id,title,main_picture,start_date,status,media_type,nsfw"
        
        val request = GET(url).newBuilder().apply {
            malHeaders().forEach { (key, value) ->
                addHeader(key, value)
            }
        }.build()
        
        val response = client.newCall(request).awaitSuccess()
        val searchResponse = response.parseAs<MalSearchResponse>()
        
        return searchResponse.data.mapNotNull { result ->
            val manga = result.node
            
            // Filter NSFW content if not wanted
            if (manga.nsfw == "white" || (manga.nsfw != "white" && includeAdult)) {
                MetadataSearchResult(
                    provider = MetadataProvider.MYANIMELIST,
                    providerId = manga.id.toLong(),
                    title = manga.title,
                    alternativeTitles = emptyList(), // Will be filled in details
                    coverUrl = manga.main_picture?.large ?: manga.main_picture?.medium,
                    confidence = 0.0f, // Will be calculated by matcher
                    format = manga.media_type
                )
            } else null
        }
    }
    
    /**
     * Headers for MAL API requests
     * Note: MAL requires client ID for API access, but we'll use a public one for now
     */
    private fun malHeaders() = mapOf(
        "X-MAL-CLIENT-ID" to "6114d00ca681b7701d1e15fe11a4987e", // Public client ID
        "User-Agent" to "Tachiyomi"
    )
}

// MAL API Response Models
@Serializable
data class MalSearchResponse(
    val data: List<MalSearchResult>
)

@Serializable
data class MalSearchResult(
    val node: MalMangaBasic
)

@Serializable
data class MalMangaBasic(
    val id: Int,
    val title: String,
    val main_picture: MalPicture? = null,
    val start_date: String? = null,
    val status: String? = null,
    val media_type: String? = null,
    val nsfw: String? = null
)

@Serializable
data class MalMangaDetails(
    val id: Int,
    val title: String,
    val main_picture: MalPicture? = null,
    val alternative_titles: MalAlternativeTitles? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val synopsis: String? = null,
    val mean: Float? = null,
    val rank: Int? = null,
    val popularity: Int? = null,
    val num_list_users: Int? = null,
    val num_scoring_users: Int? = null,
    val nsfw: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val media_type: String? = null,
    val status: String? = null,
    val genres: List<MalGenre>? = null,
    val num_volumes: Int? = null,
    val num_chapters: Int? = null,
    val authors: List<MalAuthor>? = null,
    val pictures: List<MalPicture>? = null,
    val background: String? = null
) {
    fun toMetadataDetails(): MetadataDetails {
        return MetadataDetails(
            provider = MetadataProvider.MYANIMELIST,
            providerId = id.toLong(),
            title = title,
            alternativeTitles = alternative_titles?.let { alt ->
                listOfNotNull(
                    alt.en,
                    alt.ja
                ).plus(alt.synonyms ?: emptyList())
            } ?: emptyList(),
            description = synopsis?.stripHtml(),
            coverUrl = main_picture?.large ?: main_picture?.medium,
            
            // Genres and tags
            genres = genres?.map { it.name } ?: emptyList(),
            tags = emptyList(), // MAL doesn't have separate tags
            
            // Authors and artists
            authors = authors?.map { "${it.node.first_name} ${it.node.last_name}".trim() } ?: emptyList(),
            artists = emptyList(), // MAL doesn't distinguish artists from authors
            
            // Dates
            startDate = start_date,
            endDate = end_date,
            
            // Stats
            averageScore = mean?.times(10), // Convert 0-10 to 0-100 scale
            totalChapters = num_chapters,
            
            // Status
            status = when (status) {
                "finished" -> PublishingStatus.FINISHED
                "currently_publishing" -> PublishingStatus.RELEASING
                "not_yet_published" -> PublishingStatus.NOT_YET_RELEASED
                "on_hiatus" -> PublishingStatus.HIATUS
                "discontinued" -> PublishingStatus.CANCELLED
                else -> PublishingStatus.UNKNOWN
            },
            
            // Content info
            isAdult = nsfw != "white",
            format = media_type
        )
    }
}

@Serializable
data class MalPicture(
    val medium: String? = null,
    val large: String? = null
)

@Serializable
data class MalAlternativeTitles(
    val synonyms: List<String>? = null,
    val en: String? = null,
    val ja: String? = null
)

@Serializable
data class MalGenre(
    val id: Int,
    val name: String
)

@Serializable
data class MalAuthor(
    val node: MalAuthorNode
)

@Serializable
data class MalAuthorNode(
    val id: Int,
    val first_name: String,
    val last_name: String
)

/**
 * Strip HTML tags from text
 */
private fun String.stripHtml(): String {
    return this.replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .trim()
}