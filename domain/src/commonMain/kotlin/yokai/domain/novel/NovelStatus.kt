package yokai.domain.novel

/**
 * Novel publication status constants.
 * Based on QuickNovel's ReleaseStatus pattern and Miko's SManga constants.
 */
object NovelStatus {
    const val UNKNOWN = 0
    const val ONGOING = 1
    const val COMPLETED = 2
    const val PAUSED = 3    // On hiatus
    const val DROPPED = 4   // Cancelled
    const val STUBBED = 5   // Incomplete/removed
    
    /**
     * Parse status string from novel sources to status constant.
     * Based on QuickNovel's LoadResponse.setStatus() pattern.
     */
    fun parseStatus(status: String?): Int {
        if (status == null) return UNKNOWN
        
        return when (status.lowercase().trim()) {
            "ongoing", "on-going", "on_going", "publishing" -> ONGOING
            "completed", "complete", "done", "finished" -> COMPLETED
            "hiatus", "paused", "pause", "on_hiatus", "on hiatus" -> PAUSED
            "dropped", "drop", "cancelled", "canceled" -> DROPPED
            "stub", "stubbed", "incomplete" -> STUBBED
            else -> UNKNOWN
        }
    }
    
    /**
     * Get localized status string resource key.
     */
    fun getStatusStringRes(status: Int): String {
        return when (status) {
            ONGOING -> "ongoing"
            COMPLETED -> "completed"
            PAUSED -> "on_hiatus"
            DROPPED -> "cancelled"
            STUBBED -> "unknown_status"
            else -> "unknown_status"
        }
    }
}
