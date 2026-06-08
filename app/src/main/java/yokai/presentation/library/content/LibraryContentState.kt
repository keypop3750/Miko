package yokai.presentation.library.content

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import yokai.core.content.ContentType
import yokai.domain.novel.Novel

/**
 * Represents a library item that can be displayed in the Compose library content.
 * This is a unified model for both manga and novels in the grid/list views.
 */
sealed interface LibraryContentItem {
    val id: Long
    val title: String
    val thumbnailUrl: String?
    val contentType: ContentType
    val isLocal: Boolean
    val downloadCount: Int
    val unreadCount: Int
    val language: String?
    
    /** Unique key for Compose lazy list/grid item identification */
    val uniqueKey: String
        get() = "${contentType.name}_$id"
    
    data class MangaItem(
        override val id: Long,
        override val title: String,
        override val thumbnailUrl: String?,
        val author: String?,
        val artist: String?,
        override val isLocal: Boolean = false,
        override val downloadCount: Int = -1,
        override val unreadCount: Int = -1,
        override val language: String? = null,
        val sourceId: Long,
        val status: Int,
        val dominantCoverColor: Int? = null,
        val textColor: Int? = null,
        val isFavorite: Boolean = true,
        val totalChapters: Int = 0,
        val readChapters: Int = 0,
    ) : LibraryContentItem {
        override val contentType: ContentType = ContentType.MANGA
    }
    
    data class NovelItem(
        override val id: Long,
        override val title: String,
        override val thumbnailUrl: String?,
        val author: String?,
        override val isLocal: Boolean = false,
        override val downloadCount: Int = -1,
        override val unreadCount: Int = -1,
        override val language: String? = null,
        val sourceId: Long,
        val status: Int,
        val totalChapters: Int = 0,
        val readChapters: Int = 0,
        val lastReadChapter: String? = null,
    ) : LibraryContentItem {
        override val contentType: ContentType = ContentType.NOVEL
    }
    
    /**
     * Placeholder item shown when category is empty or collapsed
     */
    data class Placeholder(
        val categoryId: Int,
        val itemCount: Int = 0,
    ) : LibraryContentItem {
        override val id: Long = -categoryId.toLong()
        override val title: String = ""
        override val thumbnailUrl: String? = null
        override val contentType: ContentType = ContentType.MANGA
        override val isLocal: Boolean = false
        override val downloadCount: Int = 0
        override val unreadCount: Int = 0
        override val language: String? = null
    }
}

/**
 * Represents a category with its items for display
 */
data class LibraryCategoryContent(
    val category: Category,
    val items: List<LibraryContentItem>,
    val isExpanded: Boolean = true,
    val mangaCount: Int = 0,
)

/**
 * UI state for the library content view
 */
sealed interface LibraryContentUiState {
    data object Loading : LibraryContentUiState
    
    data class Success(
        val categories: List<LibraryCategoryContent>,
    ) : LibraryContentUiState {
        val allItems: List<LibraryContentItem>
            get() = categories.flatMap { it.items }
        
        val isEmpty: Boolean
            get() = categories.isEmpty() || allItems.filterNot { it is LibraryContentItem.Placeholder }.isEmpty()
    }
    
    data class Error(val message: String) : LibraryContentUiState
}

/**
 * Actions that can be performed on the library content
 */
sealed interface LibraryContentAction {
    data class ItemClick(val item: LibraryContentItem) : LibraryContentAction
    data class ItemLongClick(val item: LibraryContentItem) : LibraryContentAction
    data class CategoryClick(val category: Category) : LibraryContentAction
    data class CategoryExpand(val category: Category, val expanded: Boolean) : LibraryContentAction
    data class SelectItem(val item: LibraryContentItem, val selected: Boolean) : LibraryContentAction
    data object ClearSelection : LibraryContentAction
    data object SelectAll : LibraryContentAction
    data class Search(val query: String) : LibraryContentAction
    data object Refresh : LibraryContentAction
}

/**
 * Layout modes matching LibraryItem constants
 */
object LibraryLayout {
    const val LIST = 0
    const val COMPACT_GRID = 1
    const val COMFORTABLE_GRID = 2
    const val COVER_ONLY_GRID = 3
}

/**
 * State holder for library content Compose UI
 */
class LibraryContentStateHolder {
    private val _uiState = MutableStateFlow<LibraryContentUiState>(LibraryContentUiState.Loading)
    val uiState: StateFlow<LibraryContentUiState> = _uiState.asStateFlow()
    
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()
    
    fun updateState(state: LibraryContentUiState) {
        _uiState.value = state
    }
    
    fun setCategories(categories: List<LibraryCategoryContent>) {
        _uiState.value = LibraryContentUiState.Success(categories = categories)
    }
    
    fun toggleSelection(itemId: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (itemId in current) {
            current - itemId
        } else {
            current + itemId
        }
    }
    
    fun setSelected(itemId: Long, selected: Boolean) {
        val current = _selectedIds.value
        _selectedIds.value = if (selected) {
            current + itemId
        } else {
            current - itemId
        }
    }
    
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }
    
    fun selectAll(items: List<LibraryContentItem>) {
        _selectedIds.value = items.map { it.id }.toSet()
    }
    
    fun setLoading() {
        _uiState.value = LibraryContentUiState.Loading
    }
    
    fun setError(message: String) {
        _uiState.value = LibraryContentUiState.Error(message)
    }
}

/**
 * Extension functions for converting existing models to Compose-ready models
 */
fun LibraryManga.toContentItem(
    downloadCount: Int = -1,
    language: String? = null,
    isLocal: Boolean = false,
): LibraryContentItem.MangaItem {
    return LibraryContentItem.MangaItem(
        id = manga.id ?: 0L,
        title = manga.title,
        thumbnailUrl = manga.thumbnail_url,
        author = manga.author,
        artist = manga.artist,
        isLocal = isLocal,
        downloadCount = downloadCount,
        unreadCount = unread,
        language = language,
        sourceId = manga.source,
        status = manga.status,
        isFavorite = manga.favorite,
        totalChapters = totalChapters,
        readChapters = read,
    )
}

fun Novel.toContentItem(
    downloadCount: Int = -1,
    unreadCount: Int = -1,
    language: String? = null,
    isLocal: Boolean = false,
    lastReadChapter: String? = null,
    readChapters: Int = 0,
    totalChaptersCount: Int = 0,
): LibraryContentItem.NovelItem {
    return LibraryContentItem.NovelItem(
        id = id,
        title = title,
        thumbnailUrl = posterUrl,
        author = author,
        isLocal = isLocal,
        downloadCount = downloadCount,
        unreadCount = unreadCount,
        language = language,
        sourceId = source,
        status = status,
        totalChapters = totalChaptersCount,
        readChapters = readChapters,
        lastReadChapter = lastReadChapter,
    )
}
