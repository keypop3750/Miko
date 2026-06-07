package eu.kanade.tachiyomi.ui.library.models

import eu.kanade.tachiyomi.data.database.models.LibraryManga
import yokai.core.content.ContentType
import yokai.domain.novel.Novel

sealed interface LibraryItem {
    val contentType: ContentType
    
    data class Blank(val mangaCount: Int = 0) : LibraryItem {
        override val contentType: ContentType = ContentType.MANGA
    }
    
    data class Hidden(val title: String, val hiddenItems: List<LibraryItem>) : LibraryItem {
        override val contentType: ContentType = hiddenItems.firstOrNull()?.contentType ?: ContentType.MANGA
    }
    
    data class Manga(
        val libraryManga: LibraryManga,
        val isLocal: Boolean = false,
        val downloadCount: Long = -1,
        val unreadCount: Long = -1,
        val language: String = "",
    ) : LibraryItem {
        override val contentType: ContentType = ContentType.MANGA
    }
    
    data class Novel(
        val novel: yokai.domain.novel.Novel,
        val isLocal: Boolean = false,
        val downloadCount: Long = -1,
        val unreadCount: Long = -1,
        val language: String = "",
        val lastReadChapter: String? = null,
        val totalChapters: Long = 0,
    ) : LibraryItem {
        override val contentType: ContentType = ContentType.NOVEL
    }
}

/**
 * Extension functions for type-safe content filtering
 */
fun List<LibraryItem>.filterByContentType(contentType: ContentType): List<LibraryItem> {
    return this.filter { item ->
        when (contentType) {
            ContentType.MANGA -> item is LibraryItem.Manga || 
                                (item is LibraryItem.Hidden && item.hiddenItems.any { it is LibraryItem.Manga }) ||
                                item is LibraryItem.Blank
            ContentType.NOVEL -> item is LibraryItem.Novel || 
                                (item is LibraryItem.Hidden && item.hiddenItems.any { it is LibraryItem.Novel })
        }
    }
}

/**
 * Get manga items only
 */
fun List<LibraryItem>.getMangaItems(): List<LibraryItem.Manga> {
    return this.filterIsInstance<LibraryItem.Manga>()
}

/**
 * Get novel items only
 */
fun List<LibraryItem>.getNovelItems(): List<LibraryItem.Novel> {
    return this.filterIsInstance<LibraryItem.Novel>()
}

/**
 * Convert manga to library item
 */
fun LibraryManga.toLibraryItem(
    isLocal: Boolean = false,
    downloadCount: Long = -1,
    unreadCount: Long = -1,
    language: String = ""
): LibraryItem.Manga {
    return LibraryItem.Manga(
        libraryManga = this,
        isLocal = isLocal,
        downloadCount = downloadCount,
        unreadCount = unreadCount,
        language = language
    )
}

/**
 * Convert novel to library item
 */
fun yokai.domain.novel.Novel.toLibraryItem(
    isLocal: Boolean = false,
    downloadCount: Long = -1,
    unreadCount: Long = -1,
    language: String = "",
    lastReadChapter: String? = null,
    totalChapters: Long = 0
): LibraryItem.Novel {
    return LibraryItem.Novel(
        novel = this,
        isLocal = isLocal,
        downloadCount = downloadCount,
        unreadCount = unreadCount,
        language = language,
        lastReadChapter = lastReadChapter,
        totalChapters = totalChapters
    )
}
