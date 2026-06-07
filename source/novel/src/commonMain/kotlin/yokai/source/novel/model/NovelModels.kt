package yokai.source.novel.model

import yokai.core.content.ContentItem
import yokai.core.content.ContentType

/**
 * Search result for novels from providers
 */
data class NovelSearchResult(
    override val title: String,
    override val url: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    override val description: String? = null,
    override val status: Int = NovelStatus.UNKNOWN.value,
    override val sourceId: Long,
    val sourceName: String,
    override val lastUpdate: Long = 0L,
    override val dateAdded: Long = 0L
) : ContentItem {
    override val id: Long get() = url.hashCode().toLong()
    override val posterUrl: String? get() = thumbnailUrl
    override val isFavorite: Boolean = false
    override val contentType: ContentType = ContentType.NOVEL
    
    override fun isCompleted(): Boolean = false // Search results don't have completion status
    override fun getProgress(): Float = 0f // Search results don't have progress
}

/**
 * Detailed novel information with chapters
 */
data class NovelDetails(
    override val title: String,
    override val url: String,
    val author: String? = null,
    override val description: String? = null,
    val thumbnailUrl: String? = null,
    override val status: Int = NovelStatus.UNKNOWN.value,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    override val sourceId: Long,
    val sourceName: String,
    val chapters: List<NovelChapter>,
    override val lastUpdate: Long = 0L,
    override val dateAdded: Long = 0L
) : ContentItem {
    override val id: Long get() = url.hashCode().toLong()
    override val posterUrl: String? get() = thumbnailUrl
    override val isFavorite: Boolean = false
    override val contentType: ContentType = ContentType.NOVEL
    
    override fun isCompleted(): Boolean = status == NovelStatus.COMPLETED.value
    override fun getProgress(): Float = 0f // Source models don't track reading progress
}

/**
 * Novel chapter information
 */
data class NovelChapter(
    val title: String,
    val url: String,
    val dateUpload: Long = 0L,
    val chapterNumber: Float = 0f,
    val scanlator: String? = null,
    val sourceOrder: Int = 0
) {
    val id: Long get() = url.hashCode().toLong()
}

/**
 * Novel publication status
 */
enum class NovelStatus(val value: Int) { 
    ONGOING(0), 
    COMPLETED(1), 
    LICENSED(2), 
    HIATUS(3),
    CANCELLED(4),
    UNKNOWN(5);
    
    companion object {
        fun fromValue(value: Int): NovelStatus = 
            entries.find { it.value == value } ?: UNKNOWN
            
        fun fromString(status: String): NovelStatus = when (status.lowercase().trim()) {
            "ongoing", "publishing", "active" -> ONGOING
            "completed", "finished", "complete" -> COMPLETED
            "licensed" -> LICENSED
            "hiatus", "on hiatus", "break" -> HIATUS
            "cancelled", "dropped", "axed" -> CANCELLED
            else -> UNKNOWN
        }
    }
}

/**
 * Novel content with reading metadata
 */
data class NovelContent(
    val chapterUrl: String,
    val content: String,
    val title: String? = null,
    val nextChapterUrl: String? = null,
    val previousChapterUrl: String? = null,
    val wordCount: Int = 0,
    val readingTimeMinutes: Int = 0
) {
    companion object {
        fun calculateReadingTime(wordCount: Int, wordsPerMinute: Int = 200): Int {
            return if (wordCount > 0) (wordCount / wordsPerMinute).coerceAtLeast(1) else 0
        }
    }
}

/**
 * Novel genre/tag information
 */
data class NovelGenre(
    val name: String,
    val description: String? = null
)

/**
 * A comment on a novel chapter.
 */
data class NovelComment(
    val id: String,
    val userName: String,
    val avatarUrl: String? = null,
    val content: String,
    val likes: Int = 0,
    val replyCount: Int = 0,
    val date: Long = 0L,
    val replies: List<NovelComment> = emptyList()
)

/**
 * Novel provider capabilities.
 * Declares what filtering/sorting capabilities a source supports.
 * The app uses this to dynamically show only supported options in the filter UI.
 */
data class NovelProviderCapabilities(
    // Basic capabilities
    val hasSearch: Boolean = true,
    val hasLatestUpdates: Boolean = false,
    val hasPopular: Boolean = false,
    val supportsChapterList: Boolean = true,
    val supportsContentDownload: Boolean = true,
    
    // Filter capabilities - used to dynamically show/hide filter options
    /**
     * List of supported sort options. Use the standard values:
     * "popular", "last_updated", "newest", "rating", "views", "trending",
     * "most_chapters", "alphabetical"
     */
    val supportedSorts: List<String> = listOf("popular"),
    
    /**
     * Whether ascending/descending sort direction is supported.
     */
    val supportsSortDirection: Boolean = true,
    
    /**
     * List of supported genre/tag query values.
     * Use lowercase_underscore format: "action", "fantasy", "litrpg", etc.
     */
    val supportedGenres: List<String> = emptyList(),
    
    /**
     * Whether genre exclusion is supported (exclude tags from results).
     */
    val supportsGenreExclusion: Boolean = false,
    
    /**
     * List of supported status values: "ongoing", "completed", "hiatus", "dropped"
     */
    val supportedStatuses: List<String> = emptyList(),
    
    /**
     * List of supported content warnings that can be filtered.
     * Examples: "ai_assisted", "ai_generated", "graphic_violence", 
     * "profanity", "sensitive_content", "sexual_content"
     */
    val supportedContentWarnings: List<String> = emptyList(),
    
    /**
     * Whether content warning filters can include (show only) or exclude (hide).
     * If true, content warnings are tri-state (ignore/include/exclude).
     */
    val supportsContentWarningExclusion: Boolean = false,
    
    /**
     * Whether min/max chapter count filtering is supported.
     */
    val supportsChapterCountFilter: Boolean = false,
    
    /**
     * Whether minimum rating filter is supported.
     */
    val supportsRatingFilter: Boolean = false,
    
    /**
     * Whether author search/filter is supported.
     */
    val supportsAuthorFilter: Boolean = false,
    
    /**
     * Whether this source supports reading chapter comments.
     */
    val supportsComments: Boolean = false,
    
    // Legacy compatibility flags
    val hasGenreFilter: Boolean = supportedGenres.isNotEmpty(),
    val hasStatusFilter: Boolean = supportedStatuses.isNotEmpty(),
    val hasAdvancedSearch: Boolean = false
)