package yokai.domain.swipes

interface SwipesHistoryRepository {
    /**
     * Insert a new swipe entry (left or right)
     */
    suspend fun insert(
        mangaUrl: String,
        sourceId: Long,
        mangaTitle: String,
        mangaAuthor: String?,
        coverUrl: String?,
        swipeDirection: String, // "left" or "right"
        swipedAt: Long
    )

    /**
     * Get last 50 swipes (for history UI)
     */
    suspend fun getLast50Swipes(): List<SwipeHistoryEntry>

    /**
     * Get total swipe count (for analytics)
     */
    suspend fun getTotalSwipeCount(): Long

    /**
     * Delete old swipes (keep only last 50)
     * Called after each insert to maintain limit
     */
    suspend fun deleteOldSwipes()

    /**
     * Clear all history
     */
    suspend fun clearAll()
}

/**
 * Data class representing a swipe history entry
 */
data class SwipeHistoryEntry(
    val historyId: Long,
    val mangaUrl: String,
    val sourceId: Long,
    val mangaTitle: String,
    val mangaAuthor: String?,
    val coverUrl: String?,
    val swipeDirection: String,
    val swipedAt: Long
)
