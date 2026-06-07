package yokai.data.swipes

import yokai.data.DatabaseHandler
import yokai.domain.swipes.SwipesBlacklistRepository

class SwipesBlacklistRepositoryImpl(private val handler: DatabaseHandler) : SwipesBlacklistRepository {

    override suspend fun getAllBlacklistedUrls(): List<Pair<String, Long>> = handler.awaitList {
        swipes_blacklistQueries.getAllBlacklistedUrls { manga_url, source_id ->
            manga_url to source_id
        }
    }

    override suspend fun isBlacklisted(mangaUrl: String, sourceId: Long): Boolean = handler.awaitOne {
        swipes_blacklistQueries.isBlacklisted(mangaUrl, sourceId)
    }

    override suspend fun insert(
        mangaUrl: String,
        sourceId: Long,
        mangaTitle: String?,
        coverUrl: String?,
        blacklistedAt: Long
    ) {
        handler.await {
            swipes_blacklistQueries.insert(
                mangaUrl = mangaUrl,
                sourceId = sourceId,
                mangaTitle = mangaTitle,
                coverUrl = coverUrl,
                blacklistedAt = blacklistedAt
            )
        }
    }

    override suspend fun removeFromBlacklist(mangaUrl: String, sourceId: Long) {
        handler.await {
            swipes_blacklistQueries.removeFromBlacklist(mangaUrl, sourceId)
        }
    }

    override suspend fun getBlacklistCount(): Long = handler.awaitOne {
        swipes_blacklistQueries.getBlacklistCount()
    }

    override suspend fun clearAllBlacklist() {
        handler.await {
            swipes_blacklistQueries.clearAllBlacklist()
        }
    }
}
