package yokai.data.metadata

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import yokai.data.DatabaseHandler
import yokai.domain.metadata.*

class MetadataRepositoryImpl(
    private val databaseHandler: DatabaseHandler,
    private val json: Json
) : MetadataRepository {

    override suspend fun getCachedMetadata(
        mangaUrl: String,
        sourceId: Long
    ): CachedMetadata? = withContext(Dispatchers.IO) {
        databaseHandler.awaitOneOrNull {
            manga_metadata_cacheQueries.getCached(mangaUrl, sourceId) { manga_url, source_id, provider, provider_id, matched_title, confidence_score, total_chapters, genres, tags, authors, artists, start_date, end_date, cover_url_hq, average_score, description, publishing_status, is_ongoing, cached_at, last_refreshed ->
                CachedMetadata(
                    mangaUrl = manga_url,
                    sourceId = source_id,
                    provider = MetadataProvider.valueOf(provider.uppercase()),
                    providerId = provider_id,
                    matchedTitle = matched_title,
                    confidence = confidence_score.toFloat(),
                    metadata = MetadataDetails(
                        provider = MetadataProvider.valueOf(provider.uppercase()),
                        providerId = provider_id,
                        title = matched_title,
                        alternativeTitles = emptyList(), // Not stored in cache
                        totalChapters = total_chapters?.toInt(),
                        genres = genres?.let { json.decodeFromString<List<String>>(it) } ?: emptyList(),
                        tags = tags?.let { json.decodeFromString<List<String>>(it) } ?: emptyList(),
                        authors = authors?.let { json.decodeFromString<List<String>>(it) } ?: emptyList(),
                        artists = artists?.let { json.decodeFromString<List<String>>(it) } ?: emptyList(),
                        startDate = start_date,
                        endDate = end_date,
                        coverUrl = cover_url_hq,
                        averageScore = average_score?.toFloat(),
                        description = description,
                        status = PublishingStatus.fromString(publishing_status),
                        format = null, // Not stored in cache
                        isAdult = false // Default to safe content
                    ),
                    cachedAt = cached_at,
                    lastRefreshed = last_refreshed,
                    isOngoing = is_ongoing
                )
            }
        }
    }

    override suspend fun cacheMetadata(
        mangaUrl: String,
        sourceId: Long,
        metadata: MetadataDetails,
        matchedTitle: String,
        confidence: Float
    ): Unit = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val isOngoing = metadata.status != PublishingStatus.FINISHED

        databaseHandler.await(true) {
            manga_metadata_cacheQueries.insert(
                mangaUrl = mangaUrl,
                sourceId = sourceId,
                provider = metadata.provider.name.lowercase(),
                providerId = metadata.providerId.toLong(),
                matchedTitle = metadata.title,
                confidenceScore = confidence.toDouble(),
                totalChapters = metadata.totalChapters?.toLong(),
                genres = if (metadata.genres.isNotEmpty()) json.encodeToString(metadata.genres) else null,
                tags = if (metadata.tags.isNotEmpty()) json.encodeToString(metadata.tags) else null,
                authors = if (metadata.authors.isNotEmpty()) json.encodeToString(metadata.authors) else null,
                artists = if (metadata.artists.isNotEmpty()) json.encodeToString(metadata.artists) else null,
                startDate = metadata.startDate,
                endDate = metadata.endDate,
                coverUrlHq = metadata.coverUrl,
                averageScore = metadata.averageScore?.toDouble(),
                description = metadata.description,
                publishingStatus = metadata.status.name,
                isOngoing = isOngoing,
                cachedAt = currentTime,
                lastRefreshed = currentTime
            )
        }
    }

    override suspend fun shouldRefreshMetadata(
        mangaUrl: String,
        sourceId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val cached = getCachedMetadata(mangaUrl, sourceId) ?: return@withContext true

        // Never refresh completed manga (permanent cache)
        if (!cached.isOngoing) {
            return@withContext false
        }

        // Refresh ongoing manga if >7 days old
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        return@withContext cached.lastRefreshed < sevenDaysAgo
    }

    override suspend fun clearCache(): Unit = withContext(Dispatchers.IO) {
        databaseHandler.await(true) {
            manga_metadata_cacheQueries.clearAll()
        }
    }

    override suspend fun searchMetadata(
        title: String,
        alternativeTitles: List<String>,
        contentType: ContentType
    ): List<MetadataSearchResult> {
        // This is handled by individual clients (AnilistMetadataClient, etc.)
        // Not implemented at repository level
        return emptyList()
    }

    override suspend fun getMetadataDetails(
        provider: MetadataProvider,
        providerId: Long
    ): MetadataDetails? {
        // This is handled by individual clients (AnilistMetadataClient, etc.)
        // Not implemented at repository level
        return null
    }
}