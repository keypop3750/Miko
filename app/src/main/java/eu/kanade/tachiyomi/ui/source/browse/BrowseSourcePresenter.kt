package eu.kanade.tachiyomi.ui.source.browse

import co.touchlab.kermit.Logger
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cache.MangaEntityCache
import eu.kanade.tachiyomi.data.database.models.create
import eu.kanade.tachiyomi.data.database.models.removeCover
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.source.filter.CheckboxItem
import eu.kanade.tachiyomi.ui.source.filter.CheckboxSectionItem
import eu.kanade.tachiyomi.ui.source.filter.GroupItem
import eu.kanade.tachiyomi.ui.source.filter.HeaderItem
import eu.kanade.tachiyomi.ui.source.filter.SelectItem
import eu.kanade.tachiyomi.ui.source.filter.SelectSectionItem
import eu.kanade.tachiyomi.ui.source.filter.SeparatorItem
import eu.kanade.tachiyomi.ui.source.filter.SortGroup
import eu.kanade.tachiyomi.ui.source.filter.SortItem
import eu.kanade.tachiyomi.ui.source.filter.TextItem
import eu.kanade.tachiyomi.ui.source.filter.TextSectionItem
import eu.kanade.tachiyomi.ui.source.filter.TriStateItem
import eu.kanade.tachiyomi.ui.source.filter.TriStateSectionItem
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchNonCancellableIO
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.InsertManga
import yokai.domain.manga.interactor.UpdateManga
import yokai.domain.manga.models.MangaUpdate
import yokai.domain.source.browse.filter.FilterSerializer
import yokai.domain.source.browse.filter.interactor.DeleteSavedSearch
import yokai.domain.source.browse.filter.interactor.GetSavedSearch
import yokai.domain.source.browse.filter.interactor.InsertSavedSearch
import yokai.domain.source.browse.filter.models.SavedSearch
import yokai.domain.ui.UiPreferences

// FIXME: Migrate to Compose
/**
 * Presenter of [BrowseSourceController].
 */
open class BrowseSourcePresenter(
    private val sourceId: Long,
    searchQuery: String? = null,
    var useLatest: Boolean = false,
    val sourceManager: SourceManager = Injekt.get(),
    val uiPreferences: UiPreferences = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val mangaEntityCache: MangaEntityCache = Injekt.get(),
) : BaseCoroutinePresenter<BrowseSourceController>() {
    
    private val logger = Logger.withTag("BrowseSourcePresenter")
    
    private val getManga: GetManga by injectLazy()
    private val insertManga: InsertManga by injectLazy()
    private val updateManga: UpdateManga by injectLazy()

    private val deleteSavedSearch: DeleteSavedSearch by injectLazy()
    private val getSavedSearch: GetSavedSearch by injectLazy()
    private val insertSavedSearch: InsertSavedSearch by injectLazy()
    private val filterSerializer: FilterSerializer by injectLazy()

    /**
     * Selected source.
     */
    lateinit var source: CatalogueSource

    val sourceIsInitialized
        get() = this::source.isInitialized

    var filtersChanged = false

    val page: Int
        get() = pager.currentPage

    /**
     * Modifiable list of filters.
     */
    var sourceFilters = FilterList()
        set(value) {
            field = value
            filtersChanged = true
            filterItems = value.toItems()
        }

    var filterItems: List<IFlexible<*>> = emptyList()

    /**
     * List of filters used by the [Pager]. If empty alongside [query], the popular query is used.
     */
    var appliedFilters = FilterList()

    /**
     * Pager containing a list of manga results.
     */
    private lateinit var pager: Pager
    private var pagerJob: Job? = null

    /**
     * Subscription for one request from the pager.
     */
    private var nextPageJob: Job? = null

    /**
     * Buffer for page data that arrives before the view is ready.
     * Critical for pre-fetch optimization where data arrives before view creation.
     */
    private val pendingPageData = mutableListOf<Pair<Int, List<BrowseSourceItem>>>()

    /**
     * Loading state for reactive UI updates.
     * 
     * **Architecture: State-Driven Loading**
     * This StateFlow replaces the old timer-based spinner scheduling approach which caused
     * race conditions between data arrival and spinner visibility checks.
     * 
     * **Flow:**
     * 1. `restartPager()` sets `_isLoading.value = true`
     * 2. `BrowseSourcePager` reports loading completion via `onLoadingStateChange` callback
     * 3. Controller observes this StateFlow in `setupStateObservers()` and reacts immediately
     * 
     * **Benefits:**
     * - No spinner flash on cache hits (pager reports instant completion)
     * - No race conditions (state updates are synchronous)
     * - Reactive UI (follows existing ModeManager.currentMode pattern)
     * 
     * Pattern: Same as NovelDetailsPresenter.kt (novel reader architecture)
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Indicates if current data is from cache (instant load, no spinner).
     * Used for logging and debugging - not directly shown to users.
     * 
     * Future: Could be used for cache staleness indicators or offline mode UI.
     */
    private val _isFromCache = MutableStateFlow(false)
    val isFromCache: StateFlow<Boolean> = _isFromCache.asStateFlow()

    var query = searchQuery ?: ""

    private val oldFilters = mutableListOf<Any?>()

    override fun onCreate() {
        super.onCreate()
        if (!::pager.isInitialized) {
            source = sourceManager.get(sourceId) as? CatalogueSource ?: return

            sourceFilters = source.getFilterList()

            if (oldFilters.isEmpty()) {
                for (i in sourceFilters) {
                    if (i is Filter.Group<*>) {
                        val subFilters = mutableListOf<Any?>()
                        for (j in i.state) {
                            subFilters.add((j as Filter<*>).state)
                        }
                        oldFilters.add(subFilters)
                    } else {
                        oldFilters.add(i.state)
                    }
                }
            }
            filtersChanged = false

            runBlocking { view?.savedSearches = loadSearches() }

            getSavedSearch.subscribeAllBySourceId(sourceId)
                .map { it.applyAllSave(source.getFilterList()) }
                .onEach {
                    withUIContext { view?.savedSearches = it }
                }
                .launchIn(presenterScope)
        }
    }

    fun filtersMatchDefault(): Boolean {
        for (i in sourceFilters.indices) {
            val filter = oldFilters.getOrNull(i)
            if (filter is List<*>) {
                for (j in filter.indices) {
                    val state = ((sourceFilters[i] as Filter.Group<*>).state[j] as Filter<*>).state
                    if (filter[j] != state) {
                        return false
                    }
                }
            } else if (filter != sourceFilters[i].state) {
                return false
            }
        }
        return true
    }

    /**
     * Restarts the pager for the active source with the provided query and filters.
     *
     * @param query the query.
     * @param filters the current state of the filters (for search mode).
     */
    fun restartPager(query: String = this.query, filters: FilterList = this.appliedFilters) {
        Logger.d { "🔄 [PRESENTER] restartPager called - Query: '$query', Filters: ${filters.size}" }
        this.query = query
        this.appliedFilters = filters
        
        // Set loading state at start of pager restart
        _isLoading.value = true
        _isFromCache.value = false
        Logger.d { "⏳ [STATE] Loading state set to true (pager restarting)" }

        // Create a new pager.
        Logger.d { "🔄 [PRESENTER] Creating new pager..." }
        pager = createPager(
            query,
            filters.takeIf { it.isNotEmpty() || query.isBlank() } ?: source.getFilterList(),
        )

        val sourceId = source.id

        val browseAsList = preferences.browseAsList()
        val sourceListType = preferences.libraryLayout()
        val outlineCovers = uiPreferences.outlineOnCovers()

        view?.unsubscribe()

        // Prepare the pager.
        Logger.d { "🔄 [PRESENTER] Cancelling old pager job and starting new one..." }
        pagerJob?.cancel()
        pagerJob = presenterScope.launchIO {
            Logger.d { "🔄 [PRESENTER] Pager coroutine started, collecting flow..." }
            pager.asFlow()
                .map { (first, second) ->
                    Logger.d { "🔄 [PRESENTER] Received page ${first} with ${second.size} items from network" }
                    first to second
                        .map { networkToLocalManga(it, sourceId) }
                        .filter { !preferences.hideInLibraryItems().get() || !it.favorite }
                }
                .onEach { 
                    Logger.d { "🔄 [PRESENTER] Initializing ${it.second.size} mangas..." }
                    initializeMangas(it.second) 
                }
                .map { (first, second) ->
                    Logger.d { "🔄 [PRESENTER] Converting ${second.size} mangas to BrowseSourceItems..." }
                    val isNovel = source is eu.kanade.tachiyomi.source.novel.NovelSourceWrapper
                    first to second.map {
                        BrowseSourceItem(
                            it,
                            browseAsList,
                            sourceListType,
                            outlineCovers,
                            isNovel,
                        )
                    }
                }
                .catch { error ->
                    Logger.e(error) { "Unable to prepare a page" }
                }
                .collectLatest { (page, mangas) ->
                    if (mangas.isEmpty() && page == 1) {
                        Logger.w { "🔄 [PRESENTER] First page is empty, calling onAddPageError" }
                        withUIContext { view?.onAddPageError(NoResultsException()) }
                        return@collectLatest
                    }
                    Logger.d { "🔄 [PRESENTER] Calling view.onAddPage with page $page and ${mangas.size} items (view=${if (view != null) "EXISTS" else "NULL"})" }
                    withUIContext { 
                        val currentView = view
                        if (currentView != null) {
                            currentView.onAddPage(page, mangas)
                        } else {
                            Logger.w { "🔄 [PRESENTER] ⚠️ View is NULL! Buffering page $page with ${mangas.size} items" }
                            pendingPageData.add(page to mangas)
                        }
                    }
                }
        }

        // Request first page.
        requestNext()
    }

    /**
     * Requests the next page for the active pager.
     */
    fun requestNext() {
        if (!hasNextPage()) return

        nextPageJob?.cancel()
        nextPageJob = presenterScope.launchIO {
            try {
                pager.requestNextPage()
            } catch (e: Throwable) {
                withUIContext { view?.onAddPageError(e) }
            }
        }
    }

    /**
     * Returns true if the last fetched page has a next page.
     */
    fun hasNextPage(): Boolean {
        return pager.hasNextPage
    }

    /**
     * Returns a manga from the database for the given manga from network. It creates a new entry
     * if the manga is not yet in the database.
     *
     * @param sManga the manga from the source.
     * @return a manga from the database.
     */
    private suspend fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga {
        // PERFORMANCE OPTIMIZATION: Check entity cache first to avoid DB query
        // This eliminates 30-50 DB queries per browse page after first load
        var localManga = mangaEntityCache.getCached(sManga.url, sourceId)
        
        if (localManga == null) {
            // Cache miss - query database
            logger.d { "📦 [ENTITY_CACHE] MISS for ${sManga.url} source $sourceId - querying DB" }
            localManga = getManga.awaitByUrlAndSource(sManga.url, sourceId)
            
            if (localManga == null) {
                // Not in DB - create new manga
                val newManga = Manga.create(sManga.url, sManga.title, sourceId)
                newManga.copyFrom(sManga)
                newManga.id = insertManga.await(newManga)
                localManga = newManga
                logger.d { "📦 [ENTITY_CACHE] Created new manga: ${sManga.title}" }
            }
            
            // Cache the result for future lookups
            mangaEntityCache.putCached(localManga, sourceId)
            logger.d { "📦 [ENTITY_CACHE] Cached manga: ${localManga.title}" }
        } else {
            logger.d { "📦 [ENTITY_CACHE] HIT for ${sManga.url} source $sourceId" }
        }
        
        // Update title if needed
        if (localManga.title.isBlank()) {
            localManga.title = sManga.title
            updateManga.await(
                MangaUpdate(
                    id = localManga.id!!,
                    title = sManga.title,
                )
            )
            // Invalidate cache since we updated the manga
            mangaEntityCache.invalidate(sManga.url, sourceId)
            logger.d { "📦 [ENTITY_CACHE] Invalidated cache after title update" }
        } else if (!localManga.favorite) {
            // if the manga isn't a favorite, set its display title from source
            // if it later becomes a favorite, updated title will go to db
            localManga.title = sManga.title
        }
        
        return localManga
    }

    /**
     * Initialize a list of manga.
     *
     * @param mangas the list of manga to initialize.
     */
    fun initializeMangas(mangas: List<Manga>) {
        presenterScope.launchIO {
            mangas.asFlow()
                .filter { it.thumbnail_url == null && !it.initialized }
                .map { getMangaDetails(it) }
                .onEach {
                    withUIContext { view?.onMangaInitialized(it) }
                }
                .catch { e -> Logger.e(e) { "Unable to initialize manga" } }
                .collect()
        }
    }

    /**
     * Returns the initialized manga.
     *
     * @param manga the manga to initialize.
     * @return the initialized manga
     */
    private suspend fun getMangaDetails(manga: Manga): Manga {
        try {
            val networkManga = source.getMangaDetails(manga.copy())
            manga.copyFrom(networkManga)
            manga.initialized = true
            updateManga.await(manga.toMangaUpdate())
        } catch (e: Exception) {
            Logger.e(e) { "Something went wrong while trying to initialize manga" }
        }
        return manga
    }

    fun confirmDeletion(manga: Manga) {
        launchIO {
            manga.removeCover(coverCache)
            val downloadManager: DownloadManager = Injekt.get()
            downloadManager.deleteManga(manga, source)
        }
    }

    /**
     * Set the filter states for the current source.
     *
     * @param filters a list of active filters.
     */
    fun setSourceFilter(filters: FilterList) {
        filtersChanged = true
        restartPager(filters = filters)
    }

    open fun createPager(query: String, filters: FilterList): Pager {
        return if (useLatest && query.isBlank() && !filtersChanged) {
            LatestUpdatesPager(source)
        } else {
            useLatest = false
            BrowseSourcePager(
                source, 
                query, 
                filters,
                onLoadingStateChange = { isLoading -> 
                    _isLoading.value = isLoading 
                    Logger.d { "⏳ [STATE] Presenter received loading state: $isLoading" }
                }
            )
        }
    }

    private fun FilterList.toItems(): List<IFlexible<*>> {
        return mapNotNull { filter ->
            when (filter) {
                is Filter.Header -> HeaderItem(filter)
                is Filter.Separator -> SeparatorItem(filter)
                is Filter.CheckBox -> CheckboxItem(filter)
                is Filter.TriState -> TriStateItem(filter)
                is Filter.Text -> TextItem(filter)
                is Filter.Select<*> -> SelectItem(filter)
                is Filter.Group<*> -> {
                    val group = GroupItem(filter)
                    val subItems = filter.state.mapNotNull { type ->
                        when (type) {
                            is Filter.CheckBox -> CheckboxSectionItem(type)
                            is Filter.TriState -> TriStateSectionItem(type)
                            is Filter.Text -> TextSectionItem(type)
                            is Filter.Select<*> -> SelectSectionItem(type)
                            else -> null
                        }
                    }
                    subItems.forEach { it.header = group }
                    group.subItems = subItems
                    group
                }
                is Filter.Sort -> {
                    val group = SortGroup(filter)
                    val subItems = filter.values.map {
                        SortItem(it, group)
                    }
                    group.subItems = subItems
                    group
                }
            }
        }
    }

    fun saveSearch(name: String, query: String, filters: FilterList) {
        presenterScope.launchNonCancellableIO {
            insertSavedSearch.await(
                sourceId,
                name,
                query,
                try {
                    Json.encodeToString(filterSerializer.serialize(filters))
                } catch (e: Exception) {
                    "[]"
                },
            )
        }
    }

    fun deleteSearch(searchId: Long) {
        presenterScope.launchNonCancellableIO {
            deleteSavedSearch.await(searchId)
        }
    }

    suspend fun loadSearch(id: Long): SavedSearch? {
        return getSavedSearch.awaitById(id)?.applySave(source.getFilterList())
    }

    suspend fun loadSearches(): List<SavedSearch> {
       return getSavedSearch.awaitAllBySourceId(sourceId).applyAllSave(source.getFilterList())
    }

    /**
     * Replays buffered data that arrived before view was ready.
     * Called when view is attached/created to deliver pre-fetched content.
     */
    fun flushPendingData() {
        if (pendingPageData.isEmpty()) {
            Logger.d { "📦 [BUFFER] No pending data to replay" }
            return
        }
        Logger.d { "📦 [BUFFER] Replaying ${pendingPageData.size} buffered pages" }
        presenterScope.launchIO {
            pendingPageData.sortedBy { it.first }.forEach { (page, items) ->
                Logger.d { "📦 [BUFFER] Delivering buffered page $page with ${items.size} items" }
                withUIContext { view?.onAddPage(page, items) }
            }
            pendingPageData.clear()
            Logger.d { "📦 [BUFFER] Buffer cleared, all data delivered" }
        }
    }
    
    /**
     * Get manga from database by URL and source.
     * Used by BrowseSourceController to refresh favorite states.
     */
    suspend fun getMangaFromDb(url: String, sourceId: Long): Manga? {
        return getManga.awaitByUrlAndSource(url, sourceId)
    }
}