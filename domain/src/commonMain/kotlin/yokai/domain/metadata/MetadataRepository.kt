package yokai.domain.metadata

/**
 * Repository for managing manga metadata from external tracking APIs
 */
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
     * Returns true if:
     * - No cache exists
     * - Manga is ongoing and cache is >7 days old
     */
    suspend fun shouldRefreshMetadata(
        mangaUrl: String,
        sourceId: Long
    ): Boolean
    
    /**
     * Clear all cached metadata (for testing/debugging)
     */
    suspend fun clearCache()
}

/**
 * Metadata provider services
 */
enum class MetadataProvider {
    ANILIST,
    MYANIMELIST,
    KITSU,
    MANGADEX
}

/**
 * Content type classification
 */
enum class ContentType {
    MANGA,
    MANHWA,
    MANHUA,
    WEBTOON,
    LIGHT_NOVEL,
    ONE_SHOT,
    DOUJINSHI
}

/**
 * Search result from a metadata provider
 */
data class MetadataSearchResult(
    val provider: MetadataProvider,
    val providerId: Long,
    val title: String,
    val alternativeTitles: List<String>,
    val coverUrl: String?,
    val confidence: Float, // 0.0 - 1.0
    val format: String? // "MANGA", "MANHWA", "ONE_SHOT", etc.
)

/**
 * Detailed metadata from a provider
 */
data class MetadataDetails(
    val provider: MetadataProvider,
    val providerId: Long,
    val title: String,
    val alternativeTitles: List<String>,
    
    // Priority 1: Chapter count (most critical)
    val totalChapters: Int?,
    
    // Priority 2: Genres and tags
    val genres: List<String>,
    val tags: List<String>,
    
    // Priority 3: Authors, artists, dates
    val authors: List<String>,
    val artists: List<String>,
    val startDate: String?, // ISO format
    val endDate: String?, // ISO format or null
    
    // Priority 4: Quality improvements
    val coverUrl: String?,
    val averageScore: Float?, // 0-100
    val description: String?,
    
    // Priority 5: Status
    val status: PublishingStatus,
    
    // Additional metadata
    val format: String?,
    val isAdult: Boolean = false
)

/**
 * Publishing status of manga/manhwa/webtoon
 */
enum class PublishingStatus {
    RELEASING,
    FINISHED,
    CANCELLED,
    HIATUS,
    NOT_YET_RELEASED,
    UNKNOWN;
    
    companion object {
        fun fromString(status: String?): PublishingStatus {
            return when (status?.uppercase()) {
                "RELEASING", "ONGOING", "PUBLISHING" -> RELEASING
                "FINISHED", "COMPLETED" -> FINISHED
                "CANCELLED" -> CANCELLED
                "HIATUS", "ON_HIATUS" -> HIATUS
                "NOT_YET_RELEASED" -> NOT_YET_RELEASED
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Cached metadata entry
 */
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
