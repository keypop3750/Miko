package eu.kanade.tachiyomi.ui.swipes.history

/**
 * Data class representing a swipe history item
 * Phase 4.2: History Feature - Simple view-only history
 */
data class SwipesHistoryItem(
    val url: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val swipedRight: Boolean, // true = swiped right (added to library), false = swiped left (rejected)
    val timestamp: Long
)
