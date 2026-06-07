package yokai.data.swipes

import yokai.data.DatabaseHandler
import yokai.domain.swipes.SwipesHistoryRepository
import yokai.domain.swipes.SwipeHistoryEntry

class SwipesHistoryRepositoryImpl(private val handler: DatabaseHandler) : SwipesHistoryRepository {

    override suspend fun insert(
        mangaUrl: String,
        sourceId: Long,
        mangaTitle: String,
        mangaAuthor: String?,
        coverUrl: String?,
        swipeDirection: String,
        swipedAt: Long
    ) {
        handler.await(inTransaction = true) {
            // Insert the new swipe
            swipes_historyQueries.insert(
                mangaUrl = mangaUrl,
                sourceId = sourceId,
                mangaTitle = mangaTitle,
                mangaAuthor = mangaAuthor,
                coverUrl = coverUrl,
                swipeDirection = swipeDirection,
                swipedAt = swipedAt
            )
            // Auto-maintain 50-item limit
            swipes_historyQueries.deleteOldSwipes()
        }
    }

    override suspend fun getLast50Swipes(): List<SwipeHistoryEntry> = handler.awaitList {
        swipes_historyQueries.getLast50Swipes { history_id, manga_url, source_id, manga_title, manga_author, cover_url, swipe_direction, swiped_at ->
            SwipeHistoryEntry(
                historyId = history_id,
                mangaUrl = manga_url,
                sourceId = source_id,
                mangaTitle = manga_title,
                mangaAuthor = manga_author,
                coverUrl = cover_url,
                swipeDirection = swipe_direction,
                swipedAt = swiped_at
            )
        }
    }

    override suspend fun getTotalSwipeCount(): Long = handler.awaitOne {
        swipes_historyQueries.getTotalSwipeCount()
    }

    override suspend fun deleteOldSwipes() {
        handler.await {
            swipes_historyQueries.deleteOldSwipes()
        }
    }

    override suspend fun clearAll() {
        handler.await {
            swipes_historyQueries.clearAll()
        }
    }
}
