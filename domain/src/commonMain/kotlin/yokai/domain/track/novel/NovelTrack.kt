package yokai.domain.track.novel

/**
 * Domain model for novel tracking.
 * Represents a connection between a novel and a tracking service (e.g., Goodreads).
 */
data class NovelTrack(
    val id: Long = 0,
    val novelId: Long,
    val syncId: Int, // Tracking service ID (e.g., Goodreads = 1)
    val remoteId: Long, // ID on the tracking service
    val libraryId: Long? = null, // Library/shelf ID on tracking service
    val title: String,
    val lastChapterRead: Float = 0f,
    val totalChapters: Int = 0,
    val status: Int = 0, // Reading status (want to read, reading, read, etc.)
    val score: Float = 0f, // Rating (e.g., 1-5 stars)
    val remoteUrl: String,
    val startDate: Long = 0,
    val finishDate: Long = 0,
) {
    companion object {
        // Tracking statuses
        const val UNREAD = 1
        const val READING = 2
        const val COMPLETED = 3
        const val ON_HOLD = 4
        const val DROPPED = 5
        const val PLAN_TO_READ = 6
        
        // Score values (for 5-star rating systems like Goodreads)
        const val SCORE_UNRATED = 0f
        const val SCORE_1_STAR = 1f
        const val SCORE_2_STAR = 2f
        const val SCORE_3_STAR = 3f
        const val SCORE_4_STAR = 4f
        const val SCORE_5_STAR = 5f
    }
    
    /**
     * Creates a copy of this track with updated reading progress.
     */
    fun copyWithProgress(
        lastChapterRead: Float,
        totalChapters: Int = this.totalChapters
    ): NovelTrack {
        return copy(
            lastChapterRead = lastChapterRead,
            totalChapters = totalChapters
        )
    }
    
    /**
     * Creates a copy of this track with updated status.
     */
    fun copyWithStatus(status: Int): NovelTrack {
        return copy(status = status)
    }
    
    /**
     * Creates a copy of this track with updated score.
     */
    fun copyWithScore(score: Float): NovelTrack {
        return copy(score = score)
    }
    
    /**
     * Checks if this track represents a completed read.
     */
    fun isCompleted(): Boolean {
        return status == COMPLETED
    }
    
    /**
     * Checks if this track is currently being read.
     */
    fun isReading(): Boolean {
        return status == READING
    }
    
    /**
     * Gets progress percentage (0-100).
     */
    fun getProgressPercentage(): Int {
        if (totalChapters <= 0) return 0
        return ((lastChapterRead / totalChapters) * 100).toInt().coerceIn(0, 100)
    }
}
