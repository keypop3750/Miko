package yokai.domain.novelchapter.models

data class NovelChapter(
    val id: Long,
    val novelId: Long,
    val url: String,
    val title: String,
    val chapterNumber: Double,
    val volumeNumber: Double,  // SQLDelight generates as non-nullable, 0.0 for NULL
    val wordCount: Long,
    val read: Boolean,
    val bookmark: Boolean,
    val sourceOrder: Long,
    val dateFetch: Long,
    val dateUpload: Long,
    val lastReadPosition: Long,
    val readingTimeMs: Long,
    val translator: String?,
) {
    // Mutable property for tracking download state (not persisted to database)
    var downloadState: Int = 0
}

data class NovelChapterUpdate(
    val id: Long,
    val title: String? = null,
    val chapterNumber: Double? = null,
    val volumeNumber: Double? = null,
    val wordCount: Long? = null,
    val read: Boolean? = null,
    val bookmark: Boolean? = null,
    val sourceOrder: Long? = null,
    val dateFetch: Long? = null,
    val dateUpload: Long? = null,
    val lastReadPosition: Long? = null,
    val readingTimeMs: Long? = null,
    val translator: String? = null,
)
