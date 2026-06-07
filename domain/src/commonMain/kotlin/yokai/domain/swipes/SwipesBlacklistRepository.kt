package yokai.domain.swipes

interface SwipesBlacklistRepository {
    /**
     * Get all blacklisted manga URLs (for filtering recommendations)
     * Returns list of (manga_url, source_id) pairs
     */
    suspend fun getAllBlacklistedUrls(): List<Pair<String, Long>>

    /**
     * Check if a specific manga is blacklisted
     */
    suspend fun isBlacklisted(mangaUrl: String, sourceId: Long): Boolean

    /**
     * Add manga to blacklist (called on swipe-left)
     */
    suspend fun insert(
        mangaUrl: String,
        sourceId: Long,
        mangaTitle: String?,
        coverUrl: String?,
        blacklistedAt: Long
    )

    /**
     * Remove manga from blacklist (un-blacklist)
     */
    suspend fun removeFromBlacklist(mangaUrl: String, sourceId: Long)

    /**
     * Get blacklist count (for UI/analytics)
     */
    suspend fun getBlacklistCount(): Long

    /**
     * Clear all blacklisted manga
     */
    suspend fun clearAllBlacklist()
}
