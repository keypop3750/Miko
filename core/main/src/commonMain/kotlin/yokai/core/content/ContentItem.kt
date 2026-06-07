package yokai.core.content

/**
 * Unified interface for all content items (manga and novels)
 * Provides common properties and behaviors for content polymorphism
 */
interface ContentItem {
    val id: Long
    val title: String
    val url: String
    val sourceId: Long
    val contentType: ContentType
    val description: String?
    val posterUrl: String?
    val status: Int
    val isFavorite: Boolean
    val lastUpdate: Long
    val dateAdded: Long
    
    /**
     * Gets the display title for this content item
     */
    fun getDisplayTitle(): String = title
    
    /**
     * Gets a short description for this content item
     */
    fun getShortDescription(): String = description?.take(100) ?: ""
    
    /**
     * Checks if this content has been read/completed
     */
    fun isCompleted(): Boolean
    
    /**
     * Gets the reading progress (0.0 to 1.0)
     */
    fun getProgress(): Float
    
    /**
     * Gets the content type specific identifier
     */
    fun getContentIdentifier(): String = "${contentType}_${id}"
}

/**
 * Extension functions for ContentItem collections
 */
fun List<ContentItem>.filterByType(type: ContentType): List<ContentItem> {
    return this.filter { it.contentType == type }
}

fun List<ContentItem>.getMangaItems(): List<ContentItem> {
    return this.filterByType(ContentType.MANGA)
}

fun List<ContentItem>.getNovelItems(): List<ContentItem> {
    return this.filterByType(ContentType.NOVEL)
}