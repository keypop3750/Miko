package yokai.domain.novel

import yokai.core.content.ContentItem
import yokai.core.content.ContentType

data class Novel(
    override val id: Long = 0,
    val source: Long,
    override val url: String,
    override val title: String,
    val author: String? = null,
    override val description: String? = null,
    val genre: String? = null,
    override val status: Int = 0, // NovelStatus enum value
    override val posterUrl: String? = null,
    override val isFavorite: Boolean = false,
    override val lastUpdate: Long = 0L,
    val initialized: Boolean = false,
    override val dateAdded: Long = 0L,
    val wordCount: Int? = null,
    val chapterCount: Int? = null,
    val coverLastModified: Long = 0,
    var vibrantCoverColor: Int? = null, // Extracted vibrant color from cover for theming
    val chapterFlags: Int = 0, // Stores chapter sorting/filtering preferences
    val filteredTranslators: String? = null, // Comma-separated list of translators/uploaders to filter
) : ContentItem {
    override val sourceId: Long = source
    override val contentType: ContentType = ContentType.NOVEL
    
    override fun isCompleted(): Boolean = chapterCount?.let { total ->
        total > 0 && getReadChapters() >= total
    } ?: false
    
    override fun getProgress(): Float = chapterCount?.let { total ->
        if (total > 0) getReadChapters().toFloat() / total.toFloat()
        else 0f
    } ?: 0f
    
    private fun getReadChapters(): Int {
        // This will be implemented when we add chapter reading state tracking
        // For now, return 0 - this is a placeholder that will be enhanced
        return 0
    }
    
    companion object {
        // Chapter filter and sort constants (mirroring Manga constants)
        const val SHOW_ALL = 0x00000000
        
        const val CHAPTER_SORT_FILTER_GLOBAL = 0x00000000
        const val CHAPTER_SORT_LOCAL = 0x00001000
        const val CHAPTER_SORT_LOCAL_MASK = 0x00001000
        const val CHAPTER_FILTER_LOCAL = 0x00002000
        const val CHAPTER_FILTER_LOCAL_MASK = 0x00002000

        const val CHAPTER_SHOW_UNREAD = 0x00000002
        const val CHAPTER_SHOW_READ = 0x00000004
        const val CHAPTER_READ_MASK = 0x00000006

        const val CHAPTER_SHOW_DOWNLOADED = 0x00000008
        const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010
        const val CHAPTER_DOWNLOADED_MASK = 0x00000018

        const val CHAPTER_SHOW_BOOKMARKED = 0x00000020
        const val CHAPTER_SHOW_NOT_BOOKMARKED = 0x00000040
        const val CHAPTER_BOOKMARKED_MASK = 0x00000060

        const val CHAPTER_SORTING_SOURCE = 0x00000000
        const val CHAPTER_SORTING_NUMBER = 0x00000100
        const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200
        const val CHAPTER_SORTING_MASK = 0x00000300

        const val CHAPTER_SORT_DIR_ASC = 0x00000000
        const val CHAPTER_SORT_DIR_DESC = 0x00000400
        const val CHAPTER_SORT_DIR_MASK = 0x00000400

        const val CHAPTER_DISPLAY_NAME = 0x00000000
        const val CHAPTER_DISPLAY_NUMBER = 0x00100000
        const val CHAPTER_DISPLAY_MASK = 0x00100000

        const val TYPE_MANGA = 1
        const val TYPE_MANHWA = 2
        const val TYPE_MANHUA = 3
        const val TYPE_COMIC = 4
        const val TYPE_WEBTOON = 5
    }
}

data class NovelChapter(
    val id: Long = 0,
    val novelId: Long,
    val url: String,
    val title: String,
    val chapterNumber: Double,
    val volumeNumber: Double? = null,
    val wordCount: Int? = null,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val sourceOrder: Int,
    val dateFetch: Long,
    val dateUpload: Long,
    val lastReadPosition: Int = 0,
    val readingTimeMs: Long = 0,
    val translator: String? = null
) {
    // Mutable property for tracking download state (not persisted to database)
    var downloadState: Int = 0
}

data class NovelCategory(
    val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val flags: Int = 0
)

data class NovelReadingPosition(
    val chapterId: Long,
    val characterPosition: Int = 0,
    val scrollPosition: Int = 0,
    val readingTimeMs: Long = 0,
    val lastReadAt: Long = System.currentTimeMillis()
)