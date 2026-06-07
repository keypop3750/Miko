package eu.kanade.tachiyomi.source.novel

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import yokai.source.novel.NovelMainAPI
import yokai.source.novel.model.NovelSearchResult
import yokai.source.novel.model.NovelDetails
import yokai.source.novel.model.NovelChapter

/**
 * Wrapper class that adapts NovelMainAPI to CatalogueSource interface
 * This allows novel providers to appear in the source browser alongside manga sources
 */
class NovelSourceWrapper(
    val novelProvider: NovelMainAPI
) : CatalogueSource {
    
    override val id: Long = novelProvider.id
    override val name: String = novelProvider.name
    override val lang: String = novelProvider.lang
    
    // Mark as supportsLatest only if provider actually implements it
    override val supportsLatest: Boolean = novelProvider.capabilities.hasLatestUpdates
    
    override suspend fun getPopularManga(page: Int): MangasPage {
        val novels = novelProvider.getPopularNovels(page)
        return MangasPage(novels.map { it.toSManga() }, novels.isNotEmpty())
    }
    
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (!supportsLatest) {
            return MangasPage(emptyList(), false)
        }
        val novels = novelProvider.getLatestUpdates(page)
        return MangasPage(novels.map { it.toSManga() }, novels.isNotEmpty())
    }
    
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        // Convert FilterList to Map<String, String> for novel provider
        val filterMap = convertFiltersToMap(filters)
        
        android.util.Log.d("NovelSourceWrapper", "getSearchManga: query='$query', page=$page, filterMap=$filterMap")
        
        val novels = if (query.isNotBlank()) {
            // Text search
            android.util.Log.d("NovelSourceWrapper", "Using searchNovels with query='$query'")
            novelProvider.searchNovels(query, page)
        } else if (filterMap.isNotEmpty()) {
            // Browse with filters
            android.util.Log.d("NovelSourceWrapper", "Using getBrowseNovels with filterMap=$filterMap")
            novelProvider.getBrowseNovels(page, filterMap)
        } else {
            // Default popular browsing
            android.util.Log.d("NovelSourceWrapper", "Using getPopularNovels (default)")
            novelProvider.getPopularNovels(page)
        }
        
        android.util.Log.d("NovelSourceWrapper", "Got ${novels.size} novels from provider")
        return MangasPage(novels.map { it.toSManga() }, novels.isNotEmpty())
    }
    
    /**
     * Convert FilterList to a Map<String, String> for novel providers.
     * This allows novel sources to receive filter state as URL parameters.
     */
    private fun convertFiltersToMap(filters: FilterList): Map<String, String> {
        val map = mutableMapOf<String, String>()
        
        for (filter in filters) {
            when (filter) {
                is Filter.Text -> {
                    if (filter.state.isNotBlank()) {
                        map[filter.name.lowercase().replace(" ", "_")] = filter.state
                    }
                }
                is Filter.Select<*> -> {
                    if (filter.state > 0) { // 0 is usually "Any"
                        val values = filter.values
                        if (filter.state < values.size) {
                            // Use lowercase value for consistency with source expectations
                            val value = values[filter.state].toString().lowercase().replace(" ", "_")
                            map[filter.name.lowercase().replace(" ", "_")] = value
                        }
                    }
                }
                is Filter.Sort -> {
                    filter.state?.let { selection ->
                        val values = filter.values
                        if (selection.index < values.size) {
                            map["sort"] = values[selection.index].lowercase().replace(" ", "_")
                            map["order"] = if (selection.ascending) "asc" else "desc"
                        }
                    }
                }
                is Filter.Group<*> -> {
                    // Handle different group types based on their name
                    val filterName = filter.name.lowercase().replace(" ", "_")
                    val included = mutableListOf<String>()
                    val excluded = mutableListOf<String>()
                    
                    for (subFilter in filter.state) {
                        when (subFilter) {
                            is Filter.TriState -> {
                                when (subFilter.state) {
                                    Filter.TriState.STATE_INCLUDE -> included.add(subFilter.name.lowercase().replace(" ", "_"))
                                    Filter.TriState.STATE_EXCLUDE -> excluded.add(subFilter.name.lowercase().replace(" ", "_"))
                                }
                            }
                            is Filter.CheckBox -> {
                                if (subFilter.state) {
                                    included.add(subFilter.name.lowercase().replace(" ", "_"))
                                }
                            }
                        }
                    }
                    
                    // Determine the map keys based on filter group name
                    when {
                        filterName.contains("content") || filterName.contains("warning") -> {
                            // Content warnings filter group
                            if (included.isNotEmpty()) {
                                map["include_content_warnings"] = included.joinToString(",")
                            }
                            if (excluded.isNotEmpty()) {
                                map["exclude_content_warnings"] = excluded.joinToString(",")
                            }
                        }
                        filterName.contains("genre") || filterName.contains("tag") -> {
                            // Genres filter group
                            if (included.isNotEmpty()) {
                                map["genres"] = included.joinToString(",")
                            }
                            if (excluded.isNotEmpty()) {
                                map["exclude_genres"] = excluded.joinToString(",")
                            }
                        }
                        filterName.contains("status") -> {
                            // Status filter group (for multi-select status)
                            if (included.isNotEmpty()) {
                                map["status"] = included.joinToString(",")
                            }
                        }
                        else -> {
                            // Default genre-style handling for unnamed groups
                            if (included.isNotEmpty()) {
                                map["genres"] = included.joinToString(",")
                            }
                            if (excluded.isNotEmpty()) {
                                map["exclude_genres"] = excluded.joinToString(",")
                            }
                        }
                    }
                }
                else -> {} // Ignore other filter types
            }
        }
        
        return map
    }
    
    override suspend fun getMangaDetails(manga: SManga): SManga {
        val details = novelProvider.getNovelDetails(manga.url)
        return details.toSManga().apply {
            // Preserve the original URL
            url = manga.url
        }
    }
    
    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val chapters = novelProvider.getChapterList(manga.url)
        return chapters.mapIndexed { index, novelChapter ->
            novelChapter.toSChapter(index)
        }
    }
    
    override fun getFilterList(): FilterList {
        // TODO: Implement filter conversion from NovelFilter to Filter
        return FilterList()
    }
    
    override suspend fun getPageList(chapter: SChapter): List<eu.kanade.tachiyomi.source.model.Page> {
        // Novels don't have pages - return empty list
        // The MangaDetailsController detects NovelSourceWrapper and routes to NovelReaderActivity
        return emptyList()
    }
    
    override fun toString(): String {
        // Return just the name for display in "Last Used" section
        // This matches how manga sources display their names
        return name
    }
}

/**
 * Extension functions to convert between Novel and Manga models
 */
private fun NovelSearchResult.toSManga(): SManga = SManga.create().apply {
    url = this@toSManga.url
    title = this@toSManga.title
    thumbnail_url = this@toSManga.thumbnailUrl
    author = this@toSManga.author
    description = this@toSManga.description
    status = SManga.UNKNOWN // TODO: Map novel status to manga status
}

private fun NovelDetails.toSManga(): SManga = SManga.create().apply {
    url = this@toSManga.url
    title = this@toSManga.title
    thumbnail_url = this@toSManga.thumbnailUrl
    author = this@toSManga.author
    description = this@toSManga.description
    genre = this@toSManga.genres.joinToString(", ")
    status = when (this@toSManga.status) {
        0 -> SManga.COMPLETED
        1 -> SManga.ONGOING
        2 -> SManga.ON_HIATUS
        3 -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

private fun NovelChapter.toSChapter(index: Int): SChapter = SChapter.create().apply {
    url = this@toSChapter.url
    name = this@toSChapter.title
    date_upload = this@toSChapter.dateUpload
    chapter_number = this@toSChapter.chapterNumber?.toFloat() ?: -1f
    scanlator = "" // Novels typically don't have scanlators
}
