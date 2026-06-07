package yokai.presentation.globalsearch

import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import yokai.core.content.ContentType

/**
 * UI state for the Global Search screen.
 * Represents all data needed to render the search results.
 */
data class GlobalSearchUiState(
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val sourceResults: List<SourceSearchResult> = emptyList(),
    val currentMode: ContentType = ContentType.MANGA,
    val extensionFilter: String? = null,
)

/**
 * Represents search results from a single source.
 * 
 * @param source The catalogue source
 * @param isLoading True while searching this source
 * @param results null = still loading, empty list = no results, list = results found
 * @param isPinned Whether this source is pinned by user
 * @param loadTime When results were loaded (for sorting)
 * @param isHighlighted For migration mode - highlights the source being migrated from
 */
data class SourceSearchResult(
    val source: CatalogueSource,
    val isLoading: Boolean = true,
    val results: List<MangaSearchItem>? = null,
    val isPinned: Boolean = false,
    val loadTime: Long? = null,
    val isHighlighted: Boolean = false,
)

/**
 * Individual manga item in search results.
 * 
 * @param manga The manga data
 * @param coverUrl Cover image URL (may be null if not initialized)
 * @param isInitialized Whether manga details have been fetched
 */
data class MangaSearchItem(
    val manga: Manga,
    val coverUrl: String? = manga.thumbnail_url,
    val isInitialized: Boolean = manga.initialized,
)
