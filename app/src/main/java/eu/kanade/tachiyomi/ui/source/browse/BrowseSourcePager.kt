package eu.kanade.tachiyomi.ui.source.browse

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.cache.MangaEntityCache
import eu.kanade.tachiyomi.data.cache.SourcePageCache
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.manga.interactor.GetManga

open class BrowseSourcePager(
    val source: CatalogueSource,
    val query: String,
    val filters: FilterList,
    private val onLoadingStateChange: (Boolean) -> Unit,  // NEW: Callback for loading state
    private val sourcePageCache: SourcePageCache = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val mangaEntityCache: MangaEntityCache = Injekt.get(),
) : Pager() {

    override suspend fun requestNextPage() {
        val page = currentPage
        
        // Check cache first (only for non-search, first page browsing)
        // Search always fetches fresh data to show latest results
        if (query.isBlank() && filters.isEmpty() && page == 1) {
            val cached = sourcePageCache.getCached(source.id, page, query, filters)
            if (cached != null) {
                val (mangas, hasNextPage) = cached
                if (mangas.isNotEmpty()) {
                    Logger.d { "🗄️ [CACHE] Disk cache HIT for source ${source.id} page $page" }
                    onLoadingStateChange(false)  // Cache hit = instant, stop loading
                    Logger.d { "⏳ [STATE] Loading stopped (cache hit)" }
                    
                    // ⚡ PERFORMANCE OPTIMIZATION: Preload ALL manga entities for this source
                    // This eliminates 40+ individual DB queries (15ms each = 600ms total)
                    // by replacing them with ONE batch query (20ms total)
                    try {
                        val allSourceManga = getManga.awaitBySource(source.id)
                        Logger.d { "🗄️ [CACHE] Preloaded ${allSourceManga.size} manga entities from DB for source ${source.id}" }
                        
                        // Populate entity cache with all manga from this source
                        allSourceManga.forEach { manga ->
                            mangaEntityCache.putCached(manga, source.id)
                        }
                        Logger.d { "🗄️ [CACHE] Populated entity cache with ${allSourceManga.size} manga" }
                    } catch (e: Exception) {
                        Logger.e(e) { "🗄️ [CACHE] Failed to preload manga entities, will query individually" }
                    }
                    
                    Logger.d { "🗄️ [CACHE] Serving ${mangas.size} cached mangas from source ${source.id}" }
                    onPageReceived(eu.kanade.tachiyomi.source.model.MangasPage(mangas, hasNextPage))
                    return
                }
            }
        }
        
        // Cache miss or search query - fetch from network
        Logger.d { "🌐 [CACHE] Fetching from network for source ${source.id} page $page" }
        
        val mangasPage = if (query.isBlank() && filters.isEmpty()) {
            source.getPopularManga(page)
        } else {
            source.getSearchManga(page, query, filters)
        }
        
        onLoadingStateChange(false)  // Network fetch complete, stop loading
        Logger.d { "⏳ [STATE] Loading stopped (network fetch complete)" }

        if (mangasPage.mangas.isNotEmpty()) {
            // Cache the result (only for non-search browsing)
            if (query.isBlank() && filters.isEmpty()) {
                sourcePageCache.putCache(
                    source.id,
                    page,
                    query,
                    filters,
                    mangasPage.mangas,
                    mangasPage.hasNextPage
                )
                Logger.d { "🗄️ [CACHE] Cached ${mangasPage.mangas.size} mangas for source ${source.id} page $page" }
            }
            
            onPageReceived(mangasPage)
        } else {
            throw NoResultsException()
        }
    }
}
