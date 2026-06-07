package yokai.core.content

enum class ContentType(val value: Int) {
    MANGA(0),    // Existing manga content
    NOVEL(1)     // New novel content
}

// Helper functions for content type checking
fun ContentType.isManga(): Boolean = this == ContentType.MANGA
fun ContentType.isNovel(): Boolean = this == ContentType.NOVEL

// Conversion utilities
fun Int.toContentType(): ContentType = when (this) {
    1 -> ContentType.NOVEL
    else -> ContentType.MANGA
}

fun String.toContentType(): ContentType = when (this.lowercase()) {
    "novel" -> ContentType.NOVEL
    "manga" -> ContentType.MANGA
    else -> ContentType.MANGA // Default to manga
}

// Display utilities
fun ContentType.displayName(): String = when (this) {
    ContentType.MANGA -> "Manga"
    ContentType.NOVEL -> "Novel"
}

fun ContentType.fileExtension(): String = when (this) {
    ContentType.MANGA -> "cbz"
    ContentType.NOVEL -> "epub"
}

// Type-safe content identification
inline fun <reified T> ContentType.isCompatibleWith(): Boolean = when (this) {
    ContentType.MANGA -> T::class.simpleName?.contains("Manga") == true
    ContentType.NOVEL -> T::class.simpleName?.contains("Novel") == true
}

// Content type collections
object ContentTypes {
    val ALL = listOf(ContentType.MANGA, ContentType.NOVEL)
    val DEFAULT = ContentType.MANGA
    
    fun fromValue(value: Int): ContentType = value.toContentType()
    fun fromString(value: String): ContentType = value.toContentType()
}