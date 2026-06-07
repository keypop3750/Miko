package eu.kanade.tachiyomi.ui.source.browse

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A general pager for source requests (latest updates, popular, search)
 */
abstract class Pager(var currentPage: Int = 1) {

    var hasNextPage = true
        private set

    // CRITICAL: replay=1 prevents data loss when cache returns results before flow collection starts.
    // On 3rd+ source open, memory cache serves data so fast that emit() can happen before
    // collectLatest() is ready, causing infinite loading. Replay ensures the emission is stored
    // and delivered to late collectors.
    protected val results = MutableSharedFlow<Pair<Int, List<SManga>>>(replay = 1)

    fun asFlow(): SharedFlow<Pair<Int, List<SManga>>> {
        return results.asSharedFlow()
    }

    abstract suspend fun requestNextPage()

    suspend fun onPageReceived(mangasPage: MangasPage) {
        val page = currentPage
        currentPage++
        hasNextPage = mangasPage.hasNextPage && mangasPage.mangas.isNotEmpty()
        results.emit(Pair(page, mangasPage.mangas))
    }
}
