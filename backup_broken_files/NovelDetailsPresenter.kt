package eu.kanade.tachiyomi.ui.novel.details

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toFile
import co.touchlab.kermit.Logger
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.hippo.unifile.UniFile
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.bookmarkedFilter
import eu.kanade.tachiyomi.data.database.models.chapterOrder
import eu.kanade.tachiyomi.data.database.models.downloadedFilter
import eu.kanade.tachiyomi.data.database.models.prepareCoverUpdate
import eu.kanade.tachiyomi.data.database.models.readFilter
import eu.kanade.tachiyomi.data.database.models.removeCover
import eu.kanade.tachiyomi.data.database.models.sortDescending
import eu.kanade.tachiyomi.data.database.models.updateCoverLastModified
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.data.library.customNovelManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import yokai.domain.novel.Novel
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.SourceNotFoundException
import eu.kanade.tachiyomi.source.getExtension
import eu.kanade.tachiyomi.source.model.SManga
import yokai.source.novel.NovelMainAPI
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.novel.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.novel.details.track.TrackingBottomSheet
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.chapter.ChapterSort
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithTrackServiceTwoWay
import eu.kanade.tachiyomi.util.chapter.updateTrackChapterMarkedAsRead
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.trimOrNull
import eu.kanade.tachiyomi.util.novel.NovelShortcutManager
import eu.kanade.tachiyomi.util.novel.NovelUtil
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.e
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchNonCancellableIO
import eu.kanade.tachiyomi.util.system.launchNow
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.category.interactor.GetCategories
import yokai.domain.chapter.novel.interactor.GetAvailableScanlators
import yokai.domain.chapter.novel.interactor.GetNovelChapter
import yokai.domain.chapter.novel.interactor.UpdateChapter
import yokai.domain.history.interactor.GetHistory
import yokai.domain.library.custom.model.CustomNovelInfo
import yokai.domain.novel.interactor.GetNovel
import yokai.domain.novel.interactor.UpdateNovel
import yokai.domain.novel.models.NovelUpdate
import yokai.domain.novel.models.cover
import yokai.domain.novelchapter.models.NovelChapter
import yokai.domain.storage.StorageManager
import yokai.domain.track.interactor.DeleteTrack
import yokai.domain.track.interactor.GetTrack
import yokai.domain.track.interactor.InsertTrack
import yokai.i18n.MR
import yokai.util.lang.getString

class NovelDetailsPresenter(
    val novelId: Long,
    val sourceManager: SourceManager = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
    val coverCache: CoverCache = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val chapterFilter: ChapterFilter = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
) : BaseCoroutinePresenter<NovelDetailsController>(),
    DownloadQueue.Listener {
    private val getAvailableScanlators: GetAvailableScanlators by injectLazy()
    private val getCategories: GetCategories by injectLazy()
    private val GetNovelChapter: GetNovelChapter by injectLazy()
    private val getNovel: GetNovel by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val UpdateNovel: UpdateNovel by injectLazy()
    private val deleteTrack: DeleteTrack by injectLazy()
    private val getTrack: GetTrack by injectLazy()
    private val insertTrack: InsertTrack by injectLazy()
    private val getHistory: GetHistory by injectLazy()

    private val networkPreferences: NetworkPreferences by injectLazy()

//    private val currentMangaInternal: MutableStateFlow<Novel?> = MutableStateFlow(null)
//    val currentManga get() = currentMangaInternal.asStateFlow()

    lateinit var novel: Novel
    fun isNovelLateInitInitialized() = ::novel.isInitialized

    private val customNovelManager: customNovelManager by injectLazy()
    private val novelShortcutManager: NovelShortcutManager by injectLazy()

    val source: Source by lazy { sourceManager.getOrStub(novel.source) }

    private lateinit var chapterSort: ChapterSort
    val extension by lazy { (source as? NovelMainAPI)?.getExtension() }

    var isLockedFromSearch = false
    var hasRequested = false
    var isLoading = false
    var scrollType = 0

    private val loggedServices by lazy { Injekt.get<TrackManager>().services.filter { it.isLogged } }
    private var tracks = emptyList<Track>()

    var trackList: List<TrackItem> = emptyList()

    var chapters: List<ChapterItem> = emptyList()
        private set

    var allChapters: List<ChapterItem> = emptyList()
        private set

    var allHistory: List<History> = emptyList()
        private set

    val headerItem: MangaHeaderItem by lazy { MangaHeaderItem(novelId, view?.fromCatalogue == true)}
    var tabletChapterHeaderItem: MangaHeaderItem? = null
        get() {
            when (view?.isTablet) {
                true -> if (field == null) {
                    field = MangaHeaderItem(novelId, false).apply {
                        isChapterHeader = true
                    }
                }
                else -> if (field != null) {
                    field = null
                }
            }
            return field
        }
        private set

    var allChapterScanlators: Set<String> = emptySet()

    override val progressJobs: MutableMap<Download, Job> = mutableMapOf()
    override val queueListenerScope get() = presenterScope

    override fun onCreate() {
        val controller = view ?: return

        isLockedFromSearch = controller.shouldLockIfNeeded && SecureActivityDelegate.shouldBeLocked()
        if (!::Novel.isInitialized) runBlocking { refreshMangaFromDb() }
        syncData()

        presenterScope.launchUI {
            downloadManager.statusFlow()
                .filter { it.Novel.id == novelId }
                .catch { error -> Logger.e(error) }
                .collect(::onStatusChange)
        }
        presenterScope.launchUI {
            downloadManager.progressFlow()
                .filter { it.Novel.id == novelId }
                .catch { error -> Logger.e(error) }
                .collect(::onQueueUpdate)
        }
        presenterScope.launchIO {
            downloadManager.queueState.collectLatest(::onQueueUpdate)
        }

        runBlocking {
            tracks = getTrack.awaitAllByMangaId(novelId)
        }
    }

    /**
     * onCreate but executed after UI layout is ready otherwise it'd only show blank screen
     */
    fun onCreateLate() {
        val controller = view ?: return

        LibraryUpdateJob.updateFlow
            .filter { it == novelId }
            .onEach { onUpdateNovel() }
            .launchIn(presenterScope)

        val fetchMangaNeeded = !Novel.initialized || Novel.isLocal()
        val fetchChaptersNeeded = runBlocking { GetNovelChaptersNow() }.isEmpty() || Novel.isLocal()

        presenterScope.launch {
            isLoading = true
            withUIContext {
                controller.updateHeader()
            }
            val tasks = listOf(
                async { if (fetchMangaNeeded) fetchMangaFromSource() },
                async { if (fetchChaptersNeeded) fetchChaptersFromSource(false) },
            )
            tasks.awaitAll()
            isLoading = false
            withUIContext {
                controller.updateChapters()
            }

            setTrackItems()
        }

        refreshTracking(false)
    }

    fun fetchChapters(andTracking: Boolean = true) {
        presenterScope.launch {
            GetNovelChapters()
            if (andTracking) fetchTracks()
            withUIContext { view?.updateChapters() }
            getHistory()
        }
    }

    fun setCurrentManga(novel: Novel?) {
//        currentMangaInternal.update { Novel }
        this.Novel = Novel!!
    }

    // TODO: Use flow to "sync" data instead
    fun syncData() {
        chapterSort = ChapterSort(Novel, chapterFilter, preferences)
        headerItem.apply {
            isTablet = view?.isTablet == true
            isLocked = isLockedFromSearch
        }
    }

    suspend fun GetNovelChaptersNow(): List<ChapterItem> {
        GetNovelChapters()
        return chapters
    }

    private suspend fun GetNovelChapters(queue: List<Download> = downloadManager.queueState.value) {
        val chapters = GetNovelChapter.awaitAll(novelId, isScanlatorFiltered()).map { it.toModel() }
        allChapters = if (!isScanlatorFiltered()) chapters else GetNovelChapter.awaitAll(novelId, false).map { it.toModel() }

        // Find downloaded chapters
        setDownloadedChapters(chapters, queue)
        allChapterScanlators = allChapters.mapNotNull { it.chapter.scanlator }.toSet()

        this.chapters = applyChapterFilters(chapters)
    }

    private suspend fun getHistory() {
        allHistory = getHistory.awaitAllByMangaId(novelId)
    }

    /**
     * Finds and assigns the list of downloaded chapters.
     *
     * @param chapters the list of chapter from the database.
     */
    private fun setDownloadedChapters(chapters: List<ChapterItem>, queue: List<Download>) {
        for (chapter in chapters) {
            if (downloadManager.isChapterDownloaded(chapter, novel)) {
                chapter.status = Download.State.DOWNLOADED
            } else if (queue.isNotEmpty()) {
                chapter.status = queue.find { it.chapter.id == chapter.id }
                    ?.status ?: Download.State.default
            }
        }
    }

    /**
     * Converts a chapter from the database to an extended model, allowing to store new fields.
     */
    private fun Chapter.toModel(): ChapterItem {
        // Create the model object.
        val model = ChapterItem(this, novel)
        model.isLocked = isLockedFromSearch

        // Find an active download for this chapter.
        val download = downloadManager.queueState.value.find { it.chapter.id == id }

        if (download != null) {
            // If there's an active download, assign it.
            model.download = download
        }
        return model
    }

    /**
     * Whether the sorting method is descending or ascending.
     */
    fun sortDescending() = novel.sortDescending(preferences)

    fun sortingOrder() = novel.chapterOrder(preferences)

    /**
     * Applies the view filters to the list of chapters obtained from the database.
     * @param chapterList the list of chapters from the database
     * @return an observable of the list of chapters filtered and sorted.
     */
    private fun applyChapterFilters(chapterList: List<ChapterItem>): List<ChapterItem> {
        if (isLockedFromSearch) {
            return chapterList
        }
        getScrollType(chapterList)
        return chapterSort.GetNovelChaptersSorted(chapterList)
    }

    fun GetNovelChapterUrl(chapter: Chapter): String? {
        val source = source as? NovelMainAPI ?: return null
        val chapterUrl = try { source.GetNovelChapterUrl(chapter) } catch (_: Exception) { null }
        return chapterUrl.takeIf { !it.isNullOrBlank() }
            ?: try { source.GetNovelChapterUrl(Novel, chapter) } catch (_: Exception) { null }
    }

    private fun getScrollType(chapters: List<ChapterItem>) {
        scrollType = when {
            ChapterUtil.hasMultipleVolumes(chapters) -> MULTIPLE_VOLUMES
            ChapterUtil.hasMultipleSeasons(chapters) -> MULTIPLE_SEASONS
            ChapterUtil.hasTensOfChapters(chapters) -> TENS_OF_CHAPTERS
            else -> 0
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): ChapterItem? {
        return chapterSort.getNextUnreadChapter(chapters)
    }

    fun anyRead(): Boolean = allChapters.any { it.read }
    fun hasBookmark(): Boolean = allChapters.any { it.bookmark }
    fun hasDownloads(): Boolean = allChapters.any { it.isDownloaded }

    fun getUnreadChaptersSorted() =
        chapters.filter { !it.read && it.status == Download.State.NOT_DOWNLOADED }.distinctBy { it.name }
            .sortedWith(chapterSort.sortComparator(true))

    fun startDownloadingNow(chapter: Chapter) {
        downloadManager.startDownloadNow(chapter)
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    fun downloadChapters(chapters: List<ChapterItem>) {
        downloadManager.downloadChapters(Novel, chapters.filter { !it.isDownloaded })
    }

    /**
     * Deletes the given list of chapter.
     * @param chapter the chapter to delete.
     */
    fun deleteChapter(chapter: ChapterItem) {
        this.chapters.find { it.id == chapter.id }?.apply {
            if (chapter.chapter.bookmark && !preferences.removeBookmarkedChapters().get()) return@apply
            status = Download.State.NOT_DOWNLOADED
            download = null
        }

        view?.updateChapters()

        downloadManager.deleteChapters(listOf(chapter), Novel, source, true)
    }

    /**
     * Deletes the given list of chapter.
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<ChapterItem>, update: Boolean = true, isEverything: Boolean = false) {
        chapters.forEach { chapter ->
            this.chapters.find { it.id == chapter.id }?.apply {
                if (chapter.chapter.bookmark && !preferences.removeBookmarkedChapters().get() && !isEverything) return@apply
                status = Download.State.NOT_DOWNLOADED
                download = null
            }
        }

        if (update) view?.updateChapters()

        if (isEverything) {
            downloadManager.deleteNovel(Novel, source)
        } else {
            downloadManager.deleteChapters(chapters, Novel, source)
        }
    }

    suspend fun refreshMangaFromDb(): Novel {
        val dbManga = GetNovel.awaitById(novelId)!!
        setCurrentManga(dbManga)
        return dbManga
    }

    private suspend fun fetchMangaFromSource() {
        try {
            withIOContext {
                val networkManga = source.GetNovelDetails(novel.copy())
                novel.prepareCoverUpdate(coverCache, networkManga, false)
                novel.copyFrom(networkManga)
                novel.initialized = true

                UpdateNovel.await(novel.toNovelUpdate())

                presenterScope.launchNonCancellableIO {
                    val request =
                        ImageRequest.Builder(preferences.context).data(novel.cover())
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.WRITE_ONLY)
                            .build()

                    if (preferences.context.imageLoader.execute(request) is SuccessResult) {
                        withUIContext {
                            view?.setPaletteColor()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is HttpException && e.code == 103) return
            withUIContext {
                view?.showError(trimException(e))
            }
        }
    }

    private suspend fun fetchChaptersFromSource(manualFetch: Boolean = true) {
        try {
            withIOContext {
                val chapters = source.GetNovelChapterList(novel.copy())
                val (added, removed) = withIOContext { syncChaptersWithSource(chapters, Novel, source) }
                if (added.isNotEmpty()) {
                    if (novel.shouldDownloadNewChapters(preferences) && manualFetch) {
                        downloadChapters(
                            added.sortedBy { it.chapter_number }
                                .map { it.toModel() },
                        )
                    }
                    view?.view?.context?.let { mangaShortcutManager.updateShortcuts(it) }
                }
                if (removed.isNotEmpty()) {
                    val removedChaptersId = removed.map { it.id }
                    val removedChapters = this@MangaDetailsPresenter.chapters.filter {
                        it.id in removedChaptersId && it.isDownloaded
                    }
                    if (removedChapters.isNotEmpty()) {
                        withUIContext {
                            view?.showChaptersRemovedPopup(removedChapters)
                        }
                    }
                }
                GetNovelChapters()
                getHistory()
            }
        } catch (e: Exception) {
            withUIContext {
                view?.showError(trimException(e))
            }
        }
    }

    /** Refresh Novel Info and Chapter List (not tracking) */
    fun refreshAll() {
        val isLocal by lazy { novel.isLocal() }
        if (view?.isNotOnline(!isLocal) == true && !isLocal) return
        presenterScope.launch {
            isLoading = true
            val tasks = listOf(
                async { fetchMangaFromSource() },
                async { fetchChaptersFromSource() },
            )
            tasks.awaitAll()
            isLoading = false
            withUIContext {
                view?.updateChapters()
            }
        }
    }

    private fun trimException(e: java.lang.Exception): String {
        return (
            if (e !is SourceNotFoundException &&
                e.message?.contains(": ") == true
            ) {
                e.message?.split(": ")?.drop(1)
                    ?.joinToString(": ")
            } else {
                e.message
            }
            ) ?: view?.view?.context?.getString(MR.strings.unknown_error) ?: ""
    }

    /**
     * Bookmarks the given list of chapters.
     * @param selectedChapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(selectedChapters: List<ChapterItem>, bookmarked: Boolean) {
        presenterScope.launchNonCancellableIO {
            val updates = selectedChapters.map {
                it.bookmark = bookmarked
                it.toProgressUpdate()
            }
            updateChapter.awaitAll(updates)
            GetNovelChapters()
            withUIContext { view?.updateChapters() }
        }
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param selectedChapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(
        selectedChapters: List<ChapterItem>,
        read: Boolean,
        deleteNow: Boolean = true,
        lastRead: Int? = null,
        pagesLeft: Int? = null,
    ) {
        presenterScope.launchNonCancellableIO {
            val updates = selectedChapters.map {
                it.read = read
                if (!read) {
                    it.last_page_read = lastRead ?: 0
                    it.pages_left = pagesLeft ?: 0
                }
                it.toProgressUpdate()
            }
            updateChapter.awaitAll(updates)
            if (read && deleteNow && preferences.removeAfterMarkedAsRead().get()) {
                deleteChapters(selectedChapters, false)
            }
            GetNovelChapters()
            withUIContext { view?.updateChapters() }
            if (read && deleteNow) {
                val latestReadChapter = selectedChapters.maxByOrNull { it.chapter_number.toInt() }?.chapter
                updateTrackChapterMarkedAsRead(preferences, latestReadChapter, novel.id) {
                    fetchTracks()
                }
            }
        }
    }

    /**
     * Sets the sorting order and requests an UI update.
     */
    fun setSortOrder(sort: Int, descend: Boolean) {
        novel.setChapterOrder(sort, if (descend) Novel.CHAPTER_SORT_DESC else Novel.CHAPTER_SORT_ASC)
        if (novelSortMatchesDefault()) {
            novel.setSortToGlobal()
        }
        presenterScope.launchNonCancellableIO { asyncUpdateNovelAndChapters() }
    }

    fun setGlobalChapterSort(sort: Int, descend: Boolean) {
        preferences.sortChapterOrder().set(sort)
        preferences.chaptersDescAsDefault().set(descend)
        novel.setSortToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateNovelAndChapters() }
    }

    fun novelSortMatchesDefault(): Boolean {
        return (
            novel.sortDescending == preferences.chaptersDescAsDefault().get() &&
                novel.sorting == preferences.sortChapterOrder().get()
            ) || !novel.usesLocalSort
    }

    fun mangaFilterMatchesDefault(): Boolean {
        return (
            novel.readFilter == preferences.filterChapterByRead().get() &&
                novel.downloadedFilter == preferences.filterChapterByDownloaded().get() &&
                novel.bookmarkedFilter == preferences.filterChapterByBookmarked().get() &&
                novel.hideChapterTitles == preferences.hideChapterTitlesByDefault().get()
            ) || !Novel.usesLocalFilter
    }

    fun resetSortingToDefault() {
        novel.setSortToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateNovelAndChapters() }
    }

    /**
     * Removes all filters and requests an UI update.
     */
    fun setFilters(
        unread: TriStateCheckBox.State,
        downloaded: TriStateCheckBox.State,
        bookmarked: TriStateCheckBox.State,
    ) {
        novel.readFilter = when (unread) {
            TriStateCheckBox.State.CHECKED -> Novel.CHAPTER_SHOW_UNREAD
            TriStateCheckBox.State.IGNORE -> Novel.CHAPTER_SHOW_READ
            else -> Novel.SHOW_ALL
        }
        novel.downloadedFilter = when (downloaded) {
            TriStateCheckBox.State.CHECKED -> Novel.CHAPTER_SHOW_DOWNLOADED
            TriStateCheckBox.State.IGNORE -> Novel.CHAPTER_SHOW_NOT_DOWNLOADED
            else -> Novel.SHOW_ALL
        }
        novel.bookmarkedFilter = when (bookmarked) {
            TriStateCheckBox.State.CHECKED -> Novel.CHAPTER_SHOW_BOOKMARKED
            TriStateCheckBox.State.IGNORE -> Novel.CHAPTER_SHOW_NOT_BOOKMARKED
            else -> Novel.SHOW_ALL
        }
        novel.setFilterToLocal()
        if (mangaFilterMatchesDefault()) {
            novel.setFilterToGlobal()
        }
        presenterScope.launchNonCancellableIO { asyncUpdateNovelAndChapters() }
    }

    /**
     * Sets the active display mode.
     * @param hide set title to hidden
     */
    fun hideTitle(hide: Boolean) {
        novel.displayMode = if (hide) Novel.CHAPTER_DISPLAY_NUMBER else Novel.CHAPTER_DISPLAY_NAME
        novel.setFilterToLocal()
        presenterScope.launchNonCancellableIO { UpdateNovel.await(NovelUpdate(novel.id!!, chapterFlags = novel.chapter_flags)) }
        if (mangaFilterMatchesDefault()) {
            novel.setFilterToGlobal()
        }
        view?.refreshAdapter()
    }

    fun resetFilterToDefault() {
        novel.setFilterToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateNovelAndChapters() }
    }

    fun setGlobalChapterFilters(
        unread: TriStateCheckBox.State,
        downloaded: TriStateCheckBox.State,
        bookmarked: TriStateCheckBox.State,
    ) {
        preferences.filterChapterByRead().set(
            when (unread) {
                TriStateCheckBox.State.CHECKED -> Novel.CHAPTER_SHOW_UNREAD
                TriStateCheckBox.State.IGNORE -> Novel.CHAPTER_SHOW_READ
                else -> Novel.SHOW_ALL
            },
        )
        preferences.filterChapterByDownloaded().set(
            when (downloaded) {
                TriStateCheckBox.State.CHECKED -> Novel.CHAPTER_SHOW_DOWNLOADED
                TriStateCheckBox.State.IGNORE -> Novel.CHAPTER_SHOW_NOT_DOWNLOADED
                else -> Novel.SHOW_ALL
            },
        )
        preferences.filterChapterByBookmarked().set(
            when (bookmarked) {
                TriStateCheckBox.State.CHECKED -> Novel.CHAPTER_SHOW_BOOKMARKED
                TriStateCheckBox.State.IGNORE -> Novel.CHAPTER_SHOW_NOT_BOOKMARKED
                else -> Novel.SHOW_ALL
            },
        )
        preferences.hideChapterTitlesByDefault().set(novel.hideChapterTitles)
        novel.setFilterToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateNovelAndChapters() }
    }

    private suspend fun asyncUpdateNovelAndChapters(justChapters: Boolean = false) {
        if (!justChapters) UpdateNovel.await(NovelUpdate(novel.id!!, chapterFlags = novel.chapter_flags))
        GetNovelChapters()
        withUIContext { view?.updateChapters() }
    }

    private fun isScanlatorFiltered() = novel.filtered_scanlators?.isNotEmpty() == true

    fun currentFilters(): String {
        val filtersId = mutableListOf<StringResource?>()
        filtersId.add(if (novel.readFilter(preferences) == novel.CHAPTER_SHOW_READ) MR.strings.read else null)
        filtersId.add(if (novel.readFilter(preferences) == novel.CHAPTER_SHOW_UNREAD) MR.strings.unread else null)
        filtersId.add(if (novel.downloadedFilter(preferences) == novel.CHAPTER_SHOW_DOWNLOADED) MR.strings.downloaded else null)
        filtersId.add(if (novel.downloadedFilter(preferences) == novel.CHAPTER_SHOW_NOT_DOWNLOADED) MR.strings.not_downloaded else null)
        filtersId.add(if (novel.bookmarkedFilter(preferences) == novel.CHAPTER_SHOW_BOOKMARKED) MR.strings.bookmarked else null)
        filtersId.add(if (novel.bookmarkedFilter(preferences) == novel.CHAPTER_SHOW_NOT_BOOKMARKED) MR.strings.not_bookmarked else null)
        filtersId.add(if (isScanlatorFiltered()) MR.strings.scanlators else null)
        return filtersId.filterNotNull()
            .joinToString(", ") { view?.view?.context?.getString(it) ?: "" }
    }

    fun setScanlatorFilter(filteredScanlators: Set<String>) {
        presenterScope.launchNonCancellableIO {
            val novel = Novel
            MangaUtil.setScanlatorFilter(
                UpdateNovel,
                Novel,
                if (filteredScanlators.size == allChapterScanlators.size) emptySet() else filteredScanlators
            )
            asyncUpdateNovelAndChapters(true)
        }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    fun getCategories(): List<Category> {
        return runBlocking { getCategories.await() }
    }

    fun confirmDeletion() {
        presenterScope.launchNonCancellableIO {
            novel.removeCover(coverCache)
            customNovelManager.saveMangaInfo(CustomNovelInfo(
                novelId = novel.id!!,
                title = null,
                author = null,
                artist = null,
                description = null,
                genre = null,
                status = null,
            ))
            downloadManager.deleteNovel(Novel, source)
            asyncUpdateNovelAndChapters(true)
        }
    }

    private fun onUpdateNovel() = fetchChapters()

    fun shareNovel() {
        val context = Injekt.get<Application>()

        val destDir = UniFile.fromFile(context.cacheDir)!!.createDirectory("shared_image")!!

        presenterScope.launchIO {
            try {
                val uri = saveCover(destDir)
                withUIContext {
                    view?.shareNovel(uri.uri.toFile())
                }
            } catch (_: java.lang.Exception) {
            }
        }
    }

    private fun saveImage(cover: Bitmap, directory: File, novel: Novel): File? {
        directory.mkdirs()

        // Build destination file.
        val filename = DiskUtil.buildValidFilename("${Novel.title} - Cover.jpg")

        val destFile = File(directory, filename)
        val stream: OutputStream = FileOutputStream(destFile)
        cover.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        stream.flush()
        stream.close()
        return destFile
    }

    fun updateNovel(
        title: String?,
        author: String?,
        artist: String?,
        uri: Uri?,
        description: String?,
        tags: Array<String>?,
        status: Int?,
        seriesType: Int?,
        lang: String?,
        resetCover: Boolean = false,
    ) {
        if (novel.isLocal()) {
            novel.title = if (title.isNullOrBlank()) novel.url else title.trim()
            novel.author = author?.trimOrNull()
            novel.artist = artist?.trimOrNull()
            novel.description = description?.trimOrNull()
            val tagsString = tags?.joinToString(", ") { tag ->
                tag.replaceFirstChar {
                    it.uppercase(Locale.getDefault())
                }
            }
            novel.genre = if (tags.isNullOrEmpty()) null else tagsString?.trim()
            if (seriesType != null) {
                novel.genre = setSeriesType(seriesType, novel.genre).joinToString(", ") {
                    it.replaceFirstChar { genre ->
                        genre.titlecase(Locale.getDefault())
                    }
                }
                novel.viewer_flags = -1
                presenterScope.launchIO { updateNovel.await(NovelUpdate(novel.id!!, viewerFlags = novel.viewer_flags)) }
            }
            novel.status = status ?: SManga.UNKNOWN
            LocalSource(downloadManager.context).updateNovelInfo(novel, lang)
            presenterScope.launchIO {
                updateNovel.await(
                    NovelUpdate(
                        novel.id!!,
                        title = novel.ogTitle,
                        author = novel.originalAuthor,
                        artist = novel.originalArtist,
                        description = novel.originalDescription,
                        genres = novel.originalGenre?.split(", ").orEmpty(),
                        status = novel.ogStatus,
                    )
                )
            }
        } else {
            var genre = if (!tags.isNullOrEmpty() && tags.joinToString(", ") != novel.originalGenre) {
                tags.map { tag -> tag.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
                    .toTypedArray()
            } else {
                null
            }
            if (seriesType != null) {
                genre = setSeriesType(seriesType, genre?.joinToString())
                novel.viewer_flags = -1
                presenterScope.launchIO { updateNovel.await(NovelUpdate(novel.id!!, viewerFlags = novel.viewer_flags)) }
            }
            val customNovel = CustomNovelInfo(
                novelId = novel.id!!,
                title?.trimOrNull(),
                author?.trimOrNull(),
                artist?.trimOrNull(),
                description?.trimOrNull(),
                genre?.joinToString(),
                if (status != this.novel.ogStatus) status else null,
            )
            launchNow {
                customNovelManager.saveNovelInfo(customNovel)
            }
        }
        if (uri != null) {
            editCoverWithStream(uri)
        } else if (resetCover) {
            coverCache.deleteCustomCover(novel)
            presenterScope.launchIO { novel.updateCoverLastModified() }
            view?.setPaletteColor()
        }
        view?.updateHeader()
    }

    private fun setSeriesType(seriesType: Int, genres: String? = null): Array<String> {
        val tags = (genres ?: novel.genre)?.split(",")?.map { it.trim() }?.toMutableList() ?: mutableListOf()
        tags.removeAll { novel.isSeriesTag(it) }
        when (seriesType) {
            Novel.TYPE_MANGA -> tags.add("Novel")
            Novel.TYPE_MANHUA -> tags.add("Manhua")
            Novel.TYPE_MANHWA -> tags.add("Manhwa")
            Novel.TYPE_COMIC -> tags.add("Comic")
            Novel.TYPE_WEBTOON -> tags.add("Webtoon")
        }
        return tags.toTypedArray()
    }

    fun editCoverWithStream(uri: Uri): Boolean {
        val inputStream =
            downloadManager.context.contentResolver.openInputStream(uri) ?: return false
        if (novel.isLocal()) {
            LocalSource.updateCover(novel, inputStream)
            presenterScope.launchNonCancellableIO { novel.updateCoverLastModified() }
            view?.setPaletteColor()
            return true
        }

        if (novel.favorite) {
            coverCache.setCustomCoverToCache(novel, inputStream)
            presenterScope.launchNonCancellableIO { novel.updateCoverLastModified() }
            view?.setPaletteColor()
            return true
        }
        return false
    }

    fun shareCover(): Uri? {
        return try {
            val destDir = UniFile.fromFile(coverCache.context.cacheDir)!!.createDirectory("shared_image")!!
            val file = saveCover(destDir)
            file.uri
        } catch (e: Exception) {
            null
        }
    }

    fun saveCover(): Boolean {
        return try {
            val directory = if (preferences.folderPerNovel().get()) {
                storageManager.getCoversDirectory()!!.createDirectory(DiskUtil.buildValidFilename(novel.title))!!
            } else {
                storageManager.getCoversDirectory()!!
            }
            val file = saveCover(directory)
            DiskUtil.scanMedia(preferences.context, file)
            true
        } catch (e: Exception) {
            if (networkPreferences.verboseLogging().get()) Logger.e(e) { "Unable to save cover" }
            false
        }
    }

    private fun saveCover(directory: UniFile): UniFile {
        val cover = coverCache.getCustomCoverFile(Novel).takeIf { it.exists() } ?: coverCache.getCoverFile(novel.thumbnail_url, !Novel.favorite)
        val type = cover?.let { ImageUtil.findImageType(it.inputStream()) }
            ?: throw Exception("Not an image")

        // Build destination file.
        val filename = DiskUtil.buildValidFilename("${Novel.title}.${type.extension}")

        val destFile = directory.createFile(filename)!!
        cover.inputStream().use { input ->
            destFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    fun isTracked(): Boolean =
        loggedServices.any { service -> tracks.any { it.sync_id == service.id } }

    fun hasTrackers(): Boolean = loggedServices.isNotEmpty()

    // Tracking
    private fun setTrackItems() {
        trackList = loggedServices.filter { service ->
            if (service !is EnhancedTrackService) return@filter true
            service.accept(source)
        }.map { service ->
            TrackItem(tracks.find { it.sync_id == service.id }, service)
        }
    }

    suspend fun fetchTracks() {
        tracks = withContext(Dispatchers.IO) { getTrack.awaitAllByMangaId(novel.id!!) }
        setTrackItems()
        withContext(Dispatchers.Main) { view?.refreshTracking(trackList) }
    }

    fun refreshTracking(showOfflineSnack: Boolean = false, trackIndex: Int? = null) {
        if (view?.isNotOnline(showOfflineSnack) == false) {
            presenterScope.launch {
                val asyncList = (trackIndex?.let { listOf(trackList[it]) } ?: trackList.filter { it.track != null })
                    .map { item ->
                        async(Dispatchers.IO) {
                            val trackItem = try {
                                item.service.refresh(item.track!!)
                            } catch (e: Exception) {
                                trackError(e)
                                null
                            }
                            if (trackItem != null) {
                                insertTrack.await(trackItem)
                                syncChaptersWithTrackServiceTwoWay(chapters, trackItem, item.service)
                                trackItem
                            } else {
                                item.track
                            }
                        }
                    }
                asyncList.awaitAll()
                fetchTracks()
            }
        }
    }

    fun trackSearch(query: String, service: TrackService) {
        if (view?.isNotOnline() == false) {
            presenterScope.launch(Dispatchers.IO) {
                val results = try {
                    service.search(query)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { view?.trackSearchError(e) }
                    return@launch
                }
                withContext(Dispatchers.Main) { view?.onTrackSearchResults(results) }
            }
        }
    }

    fun registerTracking(item: Track?, service: TrackService) {
        if (item != null) {
            item.manga_id = novel.id!!

            presenterScope.launch {
                val binding = try {
                    service.bind(item)
                } catch (e: Exception) {
                    trackError(e)
                    null
                }
                withContext(Dispatchers.IO) {
                    if (binding != null) {
                        insertTrack.await(binding)
                    }

                    syncChaptersWithTrackServiceTwoWay(chapters, item, service)
                }
                fetchTracks()
            }
        }
    }

    fun removeTracker(trackItem: TrackItem, removeFromService: Boolean) {
        presenterScope.launch {
            withContext(Dispatchers.IO) {
                deleteTrack.awaitForNovel(novel.id!!, trackItem.service.id)
                if (removeFromService && trackItem.service.canRemoveFromService()) {
                    trackItem.service.removeFromService(trackItem.track!!)
                }
            }
            fetchTracks()
        }
    }

    private fun updateRemote(track: Track, service: TrackService) {
        presenterScope.launch {
            val binding = try {
                service.update(track)
            } catch (e: Exception) {
                trackError(e)
                null
            }
            if (binding != null) {
                withContext(Dispatchers.IO) { insertTrack.await(binding) }
                fetchTracks()
            } else {
                trackRefreshDone()
            }
        }
    }

    private fun trackRefreshDone() {
        presenterScope.launch(Dispatchers.Main) { view?.trackRefreshDone() }
    }

    private fun trackError(error: Exception) {
        presenterScope.launch(Dispatchers.Main) { view?.trackRefreshError(error) }
    }

    fun setStatus(item: TrackItem, index: Int) {
        val track = item.track!!
        track.status = item.service.getStatusList()[index]
        if (item.service.isCompletedStatus(index) && track.total_chapters > 0L) {
            track.last_chapter_read = track.total_chapters.toFloat()
        }
        updateRemote(track, item.service)
    }

    fun setScore(item: TrackItem, index: Int) {
        val track = item.track!!
        track.score = item.service.indexToScore(index)
        updateRemote(track, item.service)
    }

    fun setLastChapterRead(item: TrackItem, chapterNumber: Int) {
        val track = item.track!!
        track.last_chapter_read = chapterNumber.toFloat()
        updateRemote(track, item.service)
    }

    fun setTrackerStartDate(item: TrackItem, date: Long) {
        val track = item.track!!
        track.started_reading_date = date
        updateRemote(track, item.service)
    }

    fun setTrackerFinishDate(item: TrackItem, date: Long) {
        val track = item.track!!
        track.finished_reading_date = date
        updateRemote(track, item.service)
    }

    suspend fun getSuggestedDate(readingDate: TrackingBottomSheet.ReadingDate): Long? {
        val chapters = getHistory.awaitAllByMangaId(novel.id ?: 0L)
        val date = when (readingDate) {
            TrackingBottomSheet.ReadingDate.Start -> chapters.minOfOrNull { it.last_read }
            TrackingBottomSheet.ReadingDate.Finish -> chapters.maxOfOrNull { it.last_read }
        } ?: return null
        return if (date <= 0L) null else date
    }

    override fun onStatusChange(download: Download) {
        super.onStatusChange(download)
        chapters.find { it.id == download.chapter.id }?.status = download.status
        onPageProgressUpdate(download)
    }

    private suspend fun onQueueUpdate(queue: List<Download>) = withIOContext {
        GetNovelChapters(queue)
        withUIContext {
            view?.updateChapters()
        }
    }

    override fun onQueueUpdate(download: Download) {
        // already handled by onStatusChange
    }

    override fun onProgressUpdate(download: Download) {
        // already handled by onStatusChange
    }

    override fun onPageProgressUpdate(download: Download) {
        chapters.find { it.id == download.chapter.id }?.download = download
        view?.updateChapterDownload(download)
    }

    // Convert NovelChapter (domain model) to Chapter (database model) for adapter compatibility
    private fun yokai.domain.novelchapter.models.NovelChapter.toChapter(): Chapter {
        return Chapter.mapper(
            id = this.id,
            mangaId = this.novelId, // Map novelId to mangaId
            url = this.url,
            name = this.title,
            scanlator = null, // Novels don't have scanlators
            read = this.read,
            bookmark = this.bookmark,
            lastPageRead = this.lastReadPosition, // Map character position to page
            pagesLeft = 0, // Not applicable for novels
            chapterNumber = this.chapterNumber,
            sourceOrder = this.sourceOrder,
            dateFetch = this.dateFetch,
            dateUpload = this.dateUpload
        )
    }

    // Convert NovelChapter to ChapterItem
    private fun yokai.domain.novelchapter.models.NovelChapter.toModel(): ChapterItem {
        val chapterModel = this.toChapter()
        val model = ChapterItem(chapterModel, novel)
        model.isLocked = isLockedFromSearch
        return model
    }

    companion object {
        const val MULTIPLE_VOLUMES = 1
        const val TENS_OF_CHAPTERS = 2
        const val MULTIPLE_SEASONS = 3
    }
}
