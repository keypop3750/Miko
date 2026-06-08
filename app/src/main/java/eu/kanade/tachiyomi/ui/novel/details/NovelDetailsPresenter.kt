package eu.kanade.tachiyomi.ui.novel.details

import android.net.Uri
import co.touchlab.kermit.Logger
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.util.novel.NovelChapterFilter
import eu.kanade.tachiyomi.util.novel.NovelChapterSort
import eu.kanade.tachiyomi.util.novel.bookmarkedFilter
import eu.kanade.tachiyomi.util.novel.chapterOrder
import eu.kanade.tachiyomi.util.novel.readFilter
import eu.kanade.tachiyomi.util.novel.setBookmarkFilter
import eu.kanade.tachiyomi.util.novel.setChapterOrder
import eu.kanade.tachiyomi.util.novel.setChapterSortDescending
import eu.kanade.tachiyomi.util.novel.setFilterToGlobal
import eu.kanade.tachiyomi.util.novel.setFilterToLocal
import eu.kanade.tachiyomi.util.novel.setReadFilter
import eu.kanade.tachiyomi.util.novel.setSortToGlobal
import eu.kanade.tachiyomi.util.novel.sortDescending
import eu.kanade.tachiyomi.util.novel.usesLocalFilter
import eu.kanade.tachiyomi.util.novel.usesLocalSort
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.novel.Novel
import yokai.domain.novel.interactor.GetNovel
import yokai.domain.novel.interactor.InsertNovel
import yokai.domain.novel.interactor.UpdateNovel
import yokai.domain.novel.models.NovelUpdate
import yokai.domain.novelchapter.interactor.DeleteNovelChapter
import yokai.domain.novelchapter.interactor.GetNovelChapter
import yokai.domain.novelchapter.interactor.InsertNovelChapter
import yokai.domain.novelchapter.interactor.UpdateNovelChapter
import yokai.domain.novelchapter.models.NovelChapter
import yokai.domain.novelchapter.models.NovelChapterUpdate
import yokai.domain.track.novel.NovelTrack
import yokai.domain.track.novel.interactor.GetNovelTrack
import yokai.domain.track.novel.interactor.InsertNovelTrack
import yokai.domain.track.novel.interactor.DeleteNovelTrack
import yokai.i18n.MR
import yokai.source.novel.NovelMainAPI
import yokai.util.lang.getString

class NovelDetailsPresenter(
    val novelId: Long,
    val sourceManager: SourceManager = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
) : BaseCoroutinePresenter<NovelDetailsControllerNew>() {
    
    private val logger = Logger.withTag("NovelDetailsPresenter")
    
    private val getNovel: GetNovel by injectLazy()
    private val insertNovel: InsertNovel by injectLazy()
    private val updateNovel: UpdateNovel by injectLazy()
    private val getNovelChapter: GetNovelChapter by injectLazy()
    private val insertNovelChapter: InsertNovelChapter by injectLazy()
    private val updateNovelChapter: UpdateNovelChapter by injectLazy()
    private val deleteNovelChapter: DeleteNovelChapter by injectLazy()
    
    // Tracking interactors
    private val getNovelTrack: GetNovelTrack by injectLazy()
    private val insertNovelTrack: InsertNovelTrack by injectLazy()
    private val deleteNovelTrack: DeleteNovelTrack by injectLazy()
    
    // Download manager for offline reading
    private val downloadManager: NovelDownloadManager by injectLazy()
    
    private val novelChapterFilter: NovelChapterFilter = NovelChapterFilter()
    private lateinit var novelChapterSort: NovelChapterSort
    
    private val _novel = MutableStateFlow<Novel?>(null)
    val novel: StateFlow<Novel?> = _novel.asStateFlow()
    
    private val _chapters = MutableStateFlow<List<NovelChapter>>(emptyList())
    val chapters: StateFlow<List<NovelChapter>> = _chapters.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Event to notify library when favorite status changes
    private val _novelFavoriteChangedEvent = kotlinx.coroutines.flow.MutableSharedFlow<Long>()
    val novelFavoriteChangedEvent: kotlinx.coroutines.flow.SharedFlow<Long> = _novelFavoriteChangedEvent.asSharedFlow()
    
    // Scroll type for adapter (determines chapter grouping UI)
    var scrollType: Int = TENS_OF_CHAPTERS
        private set
    
    val source: NovelMainAPI?
        get() = _novel.value?.let { novelData ->
            val sourceInstance = sourceManager.getOrStub(novelData.source)
            logger.d { "Getting source for novel ${novelData.id}, source ID ${novelData.source}: ${sourceInstance?.javaClass?.simpleName}" }
            
            // Unwrap NovelSourceWrapper to get the underlying NovelMainAPI
            when (sourceInstance) {
                is eu.kanade.tachiyomi.source.novel.NovelSourceWrapper -> {
                    val unwrapped = sourceInstance.novelProvider
                    logger.d { "Unwrapped NovelSourceWrapper to: ${unwrapped.javaClass.simpleName}" }
                    unwrapped
                }
                is NovelMainAPI -> sourceInstance
                else -> null
            }
        }
    
    // Compatibility layer for old controller/adapter code (DISABLED - old controller removed)
    // Direct access properties
    val novelValue: Novel? get() = _novel.value
    val novel_chapters: List<NovelChapter> get() = _chapters.value
    // val novel_chapter_items: List<eu.kanade.tachiyomi.ui.novel.chapter.ChapterItem> get() = getChapterItemsNow()
    val isNovelLateInitInitialized: Boolean get() = _novel.value != null
    var isLockedFromSearch: Boolean = false
    
    // Header item placeholder - will be set by controller
    var headerItem: HeaderItemPlaceholder? = null
    class HeaderItemPlaceholder {
        var isLocked: Boolean = false
    }
    
    // Chapter methods
    fun currentFilters(): String {
        val currentNovel = novel.value ?: return ""
        val filtersId = mutableListOf<StringResource?>()
        filtersId.add(if (currentNovel.readFilter(preferences) == Novel.CHAPTER_SHOW_READ) MR.strings.read else null)
        filtersId.add(if (currentNovel.readFilter(preferences) == Novel.CHAPTER_SHOW_UNREAD) MR.strings.unread else null)
        filtersId.add(if (currentNovel.bookmarkedFilter(preferences) == Novel.CHAPTER_SHOW_BOOKMARKED) MR.strings.bookmarked else null)
        filtersId.add(if (currentNovel.bookmarkedFilter(preferences) == Novel.CHAPTER_SHOW_NOT_BOOKMARKED) MR.strings.not_bookmarked else null)
        filtersId.add(if (isTranslatorFiltered()) MR.strings.scanlators else null)
        val context = view?.view?.context ?: return ""
        return filtersId.filterNotNull()
            .joinToString(", ") { context.getString(it) }
    }
    
    private fun isTranslatorFiltered(): Boolean {
        return _novel.value?.filteredTranslators?.isNotEmpty() == true
    }
    
    fun getNextUnreadChapter(): NovelChapter? {
        return if (::novelChapterSort.isInitialized) {
            novelChapterSort.getNextUnreadChapter(_chapters.value, andFiltered = false)
        } else {
            _chapters.value.firstOrNull { !it.read }
        }
    }
    fun getChaptersNow(): List<NovelChapter> = _chapters.value
    
    // DISABLED: Convert NovelChapter to ChapterItem for adapter compatibility (old controller removed)
    // fun getChapterItemsNow(): List<eu.kanade.tachiyomi.ui.novel.chapter.ChapterItem> {
    //     val novel = _novel.value ?: return emptyList()
    //     return _chapters.value.map { novelChapter ->
    //         // Create a temporary Chapter object from NovelChapter
    //         val tempChapter = eu.kanade.tachiyomi.data.database.models.ChapterImpl().apply {
    //             id = novelChapter.id
    //             manga_id = novel.id
    //             url = novelChapter.url
    //             name = novelChapter.name
    //             date_fetch = novelChapter.dateFetch
    //             date_upload = novelChapter.dateUpload
    //             chapter_number = novelChapter.chapterNumber
    //             read = novelChapter.read
    //             bookmark = novelChapter.bookmark
    //             last_page_read = novelChapter.lastReadPosition.toInt()
    //         }
    //         eu.kanade.tachiyomi.ui.novel.chapter.ChapterItem(tempChapter, novel)
    //     }
    // }
    
    fun fetchChapters(andTracking: Boolean = true) {
        presenterScope.launchIO {
            fetchChaptersFromSource()
        }
    }
    
    fun deleteChapters(chapters: List<NovelChapter>, update: Boolean = true, isEverything: Boolean = false) {
        presenterScope.launchIO {
            try {
                // Filter out bookmarked chapters unless isEverything is true
                val chaptersToDelete = if (isEverything) {
                    chapters
                } else {
                    chapters.filter { 
                        !it.bookmark || preferences.removeBookmarkedChapters().get() 
                    }
                }
                
                if (chaptersToDelete.isEmpty()) {
                    logger.i { "No chapters to delete (all bookmarked and removeBookmarkedChapters is false)" }
                    return@launchIO
                }
                
                // Delete chapters from database
                val chapterIds = chaptersToDelete.map { it.id }
                val success = deleteNovelChapter.awaitAll(chapterIds)
                
                if (success) {
                    logger.i { "Successfully deleted ${chaptersToDelete.size} chapters" }
                    
                    // Refresh chapter list if requested
                    if (update) {
                        fetchChapters(andTracking = false)
                    }
                } else {
                    logger.e { "Failed to delete some chapters" }
                }
            } catch (e: Exception) {
                logger.e(e) { "Error deleting chapters" }
            }
        }
    }
    
    // Tracking state and methods
    private val _tracks = MutableStateFlow<List<NovelTrack>>(emptyList())
    val tracks: StateFlow<List<NovelTrack>> = _tracks.asStateFlow()
    
    var isTracked: Boolean = false
        get() = _tracks.value.isNotEmpty()
        private set
    
    var hasTrackers: Boolean = false
        get() = _tracks.value.isNotEmpty() // TODO: Check if tracking services are available
        private set
    
    /**
     * Fetches all tracks for this novel from the database.
     */
    suspend fun fetchTracks() {
        try {
            val novelTracks = getNovelTrack.awaitAllByNovelId(novelId)
            _tracks.value = novelTracks
            logger.d { "Fetched ${novelTracks.size} tracks for novel $novelId" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to fetch tracks for novel $novelId" }
        }
    }
    
    /**
     * Refreshes tracking information from remote tracking services.
     * @param showOfflineTrackers Whether to show offline tracker warning
     * @param trackIndex Optional specific track index to refresh
     */
    fun refreshTracking(showOfflineTrackers: Boolean = false, trackIndex: Int? = null) {
        // TODO: Implement Goodreads API integration
        // For now, just fetch tracks from database
        presenterScope.launchIO {
            fetchTracks()
        }
    }
    
    /**
     * Inserts or updates a track for this novel.
     */
    suspend fun insertTrack(track: NovelTrack) {
        try {
            insertNovelTrack.awaitUpsert(track)
            fetchTracks()
            logger.i { "Inserted/updated track for novel $novelId, service ${track.syncId}" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to insert track" }
        }
    }
    
    /**
     * Deletes a track for this novel.
     */
    suspend fun deleteTrack(syncId: Int) {
        try {
            deleteNovelTrack.awaitByNovelIdAndSyncId(novelId, syncId)
            fetchTracks()
            logger.i { "Deleted track for novel $novelId, service $syncId" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to delete track" }
        }
    }
    
    // Sort and filter methods
    fun sortingOrder(): Int {
        val novel = _novel.value ?: return Novel.CHAPTER_SORTING_SOURCE
        return novel.chapterOrder(preferences)
    }
    
    fun sortDescending(): Boolean {
        val novel = _novel.value ?: return false
        return novel.sortDescending(preferences)
    }
    
    fun setGlobalChapterSort(sortingMode: Int, sortDescending: Boolean) {
        preferences.sortChapterOrder().set(sortingMode)
        preferences.chaptersDescAsDefault().set(sortDescending)
        val novel = _novel.value ?: return
        presenterScope.launchIO {
            val updated = novel.setSortToGlobal()
            val update = NovelUpdate(id = updated.id, chapterFlags = updated.chapterFlags)
            updateNovel.await(update)
            refreshChaptersWithSort()
        }
    }
    
    fun setSortOrder(order: Int, descending: Boolean) {
        val novel = _novel.value ?: return
        presenterScope.launchIO {
            val updated = novel.setChapterOrder(order).setChapterSortDescending(descending)
            val update = NovelUpdate(id = updated.id, chapterFlags = updated.chapterFlags)
            updateNovel.await(update)
            refreshChaptersWithSort()
        }
    }
    
    fun resetSortingToDefault() {
        val novel = _novel.value ?: return
        presenterScope.launchIO {
            val updated = novel.setSortToGlobal()
            val update = NovelUpdate(id = updated.id, chapterFlags = updated.chapterFlags)
            updateNovel.await(update)
            refreshChaptersWithSort()
        }
    }
    
    fun novelSortMatchesDefault(): Boolean {
        val novel = _novel.value ?: return true
        return (
            novel.sortDescending(preferences) == preferences.chaptersDescAsDefault().get() &&
                novel.chapterOrder(preferences) == preferences.sortChapterOrder().get()
            ) || !novel.usesLocalSort
    }
    
    fun setGlobalChapterFilters(
        readFilter: TriStateCheckBox.State,
        downloadFilter: TriStateCheckBox.State,
        bookmarkFilter: TriStateCheckBox.State
    ) {
        preferences.filterChapterByRead().set(
            when (readFilter) {
                TriStateCheckBox.State.CHECKED -> Novel.CHAPTER_SHOW_UNREAD
                TriStateCheckBox.State.IGNORE -> Novel.CHAPTER_SHOW_READ
                else -> Novel.SHOW_ALL
            }
        )
        // Novels don't support downloads, so downloadFilter is ignored
        preferences.filterChapterByBookmarked().set(
            when (bookmarkFilter) {
                TriStateCheckBox.State.CHECKED -> Novel.CHAPTER_SHOW_BOOKMARKED
                TriStateCheckBox.State.IGNORE -> Novel.CHAPTER_SHOW_NOT_BOOKMARKED
                else -> Novel.SHOW_ALL
            }
        )
        val novel = _novel.value ?: return
        presenterScope.launchIO {
            val updated = novel.setFilterToGlobal()
            val update = NovelUpdate(id = updated.id, chapterFlags = updated.chapterFlags)
            updateNovel.await(update)
            refreshChaptersWithSort()
        }
    }
    
    fun setFilters(
        unread: eu.kanade.tachiyomi.widget.TriStateCheckBox.State,
        downloaded: eu.kanade.tachiyomi.widget.TriStateCheckBox.State,
        bookmarked: eu.kanade.tachiyomi.widget.TriStateCheckBox.State,
    ) {
        val read = when (unread) {
            eu.kanade.tachiyomi.widget.TriStateCheckBox.State.CHECKED -> Novel.CHAPTER_SHOW_UNREAD
            eu.kanade.tachiyomi.widget.TriStateCheckBox.State.IGNORE -> Novel.CHAPTER_SHOW_READ
            else -> Novel.SHOW_ALL
        }
        val bookmark = when (bookmarked) {
            eu.kanade.tachiyomi.widget.TriStateCheckBox.State.CHECKED -> Novel.CHAPTER_SHOW_BOOKMARKED
            eu.kanade.tachiyomi.widget.TriStateCheckBox.State.IGNORE -> Novel.CHAPTER_SHOW_NOT_BOOKMARKED
            else -> Novel.SHOW_ALL
        }
        // Downloaded filter doesn't apply to novels, ignore it
        setFilters(read, Novel.SHOW_ALL, bookmark)
    }
    
    private fun setFilters(read: Int, downloaded: Int, bookmarked: Int) {
        val novel = _novel.value ?: return
        presenterScope.launchIO {
            var updated = novel.setReadFilter(read).setBookmarkFilter(bookmarked)
            // Auto-switch to global if matches defaults
            if (mangaFilterMatchesDefault()) {
                updated = updated.setFilterToGlobal()
            } else {
                updated = updated.setFilterToLocal()
            }
            val update = NovelUpdate(id = updated.id, chapterFlags = updated.chapterFlags)
            updateNovel.await(update)
            refreshChaptersWithSort()
        }
    }
    
    fun resetFilterToDefault() {
        val novel = _novel.value ?: return
        presenterScope.launchIO {
            val updated = novel.setFilterToGlobal()
            val update = NovelUpdate(id = updated.id, chapterFlags = updated.chapterFlags)
            updateNovel.await(update)
            refreshChaptersWithSort()
        }
    }
    
    fun mangaFilterMatchesDefault(): Boolean {
        val novel = _novel.value ?: return true
        return (
            novel.readFilter(preferences) == preferences.filterChapterByRead().get() &&
                novel.bookmarkedFilter(preferences) == preferences.filterChapterByBookmarked().get()
            ) || !novel.usesLocalFilter
    }
    
    // translator methods (novels use translator/uploader field)
    val allChapterTranslators: Set<String> get() {
        return _chapters.value.mapNotNull { it.translator }.toSet()
    }
    
    fun setTranslatorFilter(translators: Set<String>) {
        val novel = _novel.value ?: return
        presenterScope.launchIO {
            val filteredTranslators = if (translators.size == allChapterTranslators.size) {
                null  // All selected = none filtered
            } else {
                translators.joinToString(",")
            }
            val update = NovelUpdate(
                id = novel.id,
                filteredTranslators = filteredTranslators
            )
            updateNovel.await(update)
            refreshChaptersWithSort()
        }
    }
    
    // Hide title method
    fun hideTitle(hide: Boolean) {
        val novel = _novel.value ?: return
        presenterScope.launchIO {
            val displayMode = if (hide) Novel.CHAPTER_DISPLAY_NUMBER else Novel.CHAPTER_DISPLAY_NAME
            val updated = novel.setFilterToLocal()
            // Apply display mode change through chapterFlags
            val chapterFlags = (updated.chapterFlags and Novel.CHAPTER_DISPLAY_MASK.inv()) or (displayMode and Novel.CHAPTER_DISPLAY_MASK)
            val update = NovelUpdate(id = updated.id, chapterFlags = chapterFlags)
            updateNovel.await(update)
            
            // If matches default, switch back to global
            if (mangaFilterMatchesDefault()) {
                val updatedGlobal = novel.setFilterToGlobal()
                val updateGlobal = NovelUpdate(id = updatedGlobal.id, chapterFlags = updatedGlobal.chapterFlags)
                updateNovel.await(updateGlobal)
            }
        }
    }
    
    /**
     * Refresh chapter list with current sorting/filtering applied
     */
    private suspend fun refreshChaptersWithSort() {
        val rawChapters = getNovelChapter.awaitAll(novelId)
        val novel = _novel.value ?: return
        
        // Reinitialize sort with latest novel
        novelChapterSort = NovelChapterSort(novel, novelChapterFilter, preferences)
        
        val sorted = novelChapterSort.getChaptersSorted(rawChapters)
        _chapters.value = sorted
        
        // Update scroll type
        getScrollType(sorted)
    }
    
    // State management methods
    fun setCurrentNovel(novel: Novel?) {
        android.util.Log.d("NovelDetailsPresenter", "=== setCurrentNovel called ===")
        android.util.Log.d("NovelDetailsPresenter", "novel?.id: ${novel?.id}")
        android.util.Log.d("NovelDetailsPresenter", "novel?.title: '${novel?.title}'")
        android.util.Log.d("NovelDetailsPresenter", "novel?.url: '${novel?.url}'")
        _novel.value = novel
    }
    fun onCreateLate() {} // TODO: Implement late initialization
    fun refreshAll() {
        presenterScope.launchIO {
            refreshNovelFromSource()
            fetchChaptersFromSource()
        }
    }
    fun syncData() {
        presenterScope.launchIO {
            // TODO: Implement data sync
        }
    }
    fun refreshNovelFromDb() {
        presenterScope.launchIO {
            loadNovelFromDatabase()
        }
    }
    
    // Cover cache methods
    fun getCoverFile(): java.io.File? = null // TODO: Implement if needed
    
    // Status and progress (expose internal state)
    val status: MutableStateFlow<Int> = MutableStateFlow(0)
    val progress: MutableStateFlow<Int> = MutableStateFlow(0)
    
    // Novel locking (novels don't use this)
    fun isLocked(): Boolean = false
    
    override fun onCreate() {
        super.onCreate()
        logger.d { "onCreate: Loading novel $novelId" }
        
        presenterScope.launchIO {
            try {
                // STEP 1: Load novel from database (this sets _novel.value with correct ID)
                loadNovelFromDatabase()
                
                // STEP 2: Get the actual novel with correct database ID
                val actualNovel = _novel.value
                if (actualNovel == null) {
                    logger.e { "Failed to load novel from database" }
                    return@launchIO
                }
                
                val actualNovelId = actualNovel.id
                logger.d { "Using actual database novel ID: $actualNovelId (constructor had: $novelId)" }
                
                // STEP 3: Initialize NovelChapterSort with the correct novel
                novelChapterSort = NovelChapterSort(actualNovel, novelChapterFilter, preferences)
                
                // STEP 4: Auto-fetch from source if not initialized
                if (!actualNovel.initialized) {
                    logger.i { "Novel not initialized, launching background fetch for novel ID ${actualNovel.id}, source ID ${actualNovel.source}" }
                    presenterScope.launchIO {
                        logger.i { "Background fetch coroutine started" }
                        try {
                            refreshNovelFromSource()
                            // After refresh, the novel will be inserted with a new ID
                            // The subscriptions below will automatically pick it up via _novel flow
                            logger.i { "Background fetch coroutine completed successfully" }
                        } catch (e: Exception) {
                            logger.e(e) { "Background fetch coroutine failed" }
                        }
                    }
                } else if (_chapters.value.isEmpty()) {
                    logger.i { "Novel has no chapters, launching background refresh from source" }
                    presenterScope.launchIO {
                        logger.i { "Background refresh coroutine started" }
                        try {
                            // Always refresh from source first to ensure novel is in DB
                            // before fetching chapters (avoids FOREIGN KEY constraint errors)
                            refreshNovelFromSource()
                            logger.i { "Background refresh coroutine completed successfully" }
                        } catch (e: Exception) {
                            logger.e(e) { "Background refresh coroutine failed" }
                        }
                    }
                }
                
                // STEP 5 & 6: Subscribe to novel and chapter updates
                // Use distinctUntilChangedBy to only restart subscriptions when ID changes
                logger.d { "Starting reactive subscriptions based on _novel StateFlow" }
                
                presenterScope.launchIO {
                    _novel
                        .filterNotNull()
                        .filter { it.id > 0 }
                        .distinctUntilChangedBy { it.id }
                        .collectLatest { novel ->
                            logger.d { "Novel ID changed to ${novel.id}, starting/restarting subscriptions" }
                            
                            // Both subscriptions run in parallel for this novel ID
                            coroutineScope {
                                // Subscribe to novel updates from database
                                launch {
                                    getNovel.subscribeById(novel.id).collectLatest { updatedNovel ->
                                        if (updatedNovel != null) {
                                            _novel.value = updatedNovel
                                            novelChapterSort = NovelChapterSort(updatedNovel, novelChapterFilter, preferences)
                                            logger.d { "Novel updated from database: ${updatedNovel.title}" }
                                        }
                                    }
                                }
                                
                                // Subscribe to chapter updates from database
                                launch {
                                    getNovelChapter.subscribeByNovelId(novel.id).collectLatest { rawChapters ->
                                        logger.d { "Chapter Flow emitted for novel ${novel.id}: ${rawChapters.size} chapters" }
                                        if (rawChapters.isNotEmpty()) {
                                            logger.d { "First 3 chapters: ${rawChapters.take(3).map { "${it.id}:${it.title}" }}" }
                                        }
                                        
                                        if (::novelChapterSort.isInitialized) {
                                            // Apply sorting and filtering
                                            val sorted = novelChapterSort.getChaptersSorted(rawChapters)
                                            _chapters.value = sorted
                                            
                                            // Update scroll type detection
                                            getScrollType(sorted)
                                            
                                            logger.d { "Chapters updated and sorted: ${sorted.size} chapters" }
                                        } else {
                                            // Fallback: just sort by chapter number if sort not initialized yet
                                            _chapters.value = rawChapters.sortedBy { it.chapterNumber }
                                            logger.d { "Chapters updated (no sort): ${rawChapters.size} chapters" }
                                        }
                                    }
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to initialize novel details" }
            }
        }
    }
    
    /**
     * Detect chapter grouping type for UI presentation
     */
    private fun getScrollType(chapters: List<NovelChapter>) {
        scrollType = when {
            hasMultipleVolumes(chapters) -> MULTIPLE_VOLUMES
            hasMultipleSeasons(chapters) -> MULTIPLE_SEASONS
            hasTensOfChapters(chapters) -> TENS_OF_CHAPTERS
            else -> TENS_OF_CHAPTERS
        }
    }
    
    private val volumeRegex = Regex("""(vol|volume)\.? *([0-9]+)?""", RegexOption.IGNORE_CASE)
    private val seasonRegex = Regex("""(Season |S)([0-9]+)?""")
    
    private fun hasMultipleVolumes(chapters: List<NovelChapter>): Boolean {
        val volumeSet = mutableSetOf<Int>()
        chapters.forEach {
            val volNum = getVolumeNumber(it)
            if (volNum != null) {
                volumeSet.add(volNum)
                if (volumeSet.size >= 2) return true
            }
        }
        return false
    }
    
    private fun hasMultipleSeasons(chapters: List<NovelChapter>): Boolean {
        val seasonSet = mutableSetOf<Int>()
        chapters.forEach {
            val seasonNum = getSeasonNumber(it)
            if (seasonNum != null) {
                seasonSet.add(seasonNum)
                if (seasonSet.size >= 2) return true
            }
        }
        return false
    }
    
    private fun hasTensOfChapters(chapters: List<NovelChapter>): Boolean {
        return chapters.size > 20
    }
    
    private fun getVolumeNumber(chapter: NovelChapter): Int? {
        val groups = volumeRegex.find(chapter.title)?.groups
        if (groups != null) return groups[2]?.value?.toIntOrNull()
        return null
    }
    
    private fun getSeasonNumber(chapter: NovelChapter): Int? {
        val groups = seasonRegex.find(chapter.title)?.groups
        if (groups != null) return groups[2]?.value?.toIntOrNull()
        return null
    }
    
    private suspend fun loadNovelFromDatabase() {
        android.util.Log.d("NovelDetailsPresenter", "=== loadNovelFromDatabase START ===")
        logger.d { "Loading novel from database" }
        try {
            _isLoading.value = true
            _error.value = null
            
            // If setCurrentNovel() was called (from browse), use URL-based lookup
            // This handles the case where novelId is a temporary browse ID
            val currentNovel = _novel.value
            val dbNovel = if (currentNovel != null) {
                android.util.Log.d("NovelDetailsPresenter", "Querying by URL: ${currentNovel.url}, source: ${currentNovel.source}")
                getNovel.awaitByUrlAndSource(currentNovel.url, currentNovel.source)
            } else {
                android.util.Log.d("NovelDetailsPresenter", "Querying by novelId: $novelId")
                getNovel.awaitById(novelId)
            }
            
            if (dbNovel != null) {
                android.util.Log.d("NovelDetailsPresenter", "Database returned novel:")
                android.util.Log.d("NovelDetailsPresenter", "  - dbNovel.id: ${dbNovel.id}")
                android.util.Log.d("NovelDetailsPresenter", "  - dbNovel.title: '${dbNovel.title}'")
                android.util.Log.d("NovelDetailsPresenter", "  - dbNovel.url: '${dbNovel.url}'")
                _novel.value = dbNovel
                logger.i { "Successfully loaded novel: ${dbNovel.title}" }
            } else {
                android.util.Log.w("NovelDetailsPresenter", "Database returned NULL - novel not yet in database")
                // Novel not in database yet - this is OK for browse mode, will be inserted later
                if (currentNovel == null) {
                    _error.value = "Novel not found in database"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelDetailsPresenter", "Exception in loadNovelFromDatabase", e)
            logger.e(e) { "Failed to load novel" }
            handleError(e, "Load from database")
        } finally {
            _isLoading.value = false
            android.util.Log.d("NovelDetailsPresenter", "=== loadNovelFromDatabase END ===")
        }
    }
    
    suspend fun refreshNovelFromSource() {
        logger.d { "refreshNovelFromSource: Entry point" }
        val currentNovel = _novel.value
        if (currentNovel == null) {
            logger.e { "refreshNovelFromSource: currentNovel is null, returning" }
            return
        }
        logger.d { "refreshNovelFromSource: currentNovel found: ${currentNovel.title}" }
        
        val novelSource = source
        if (novelSource == null) {
            logger.e { "refreshNovelFromSource: source is null, returning" }
            return
        }
        logger.d { "refreshNovelFromSource: source found: ${novelSource.javaClass.simpleName}" }
        
        logger.d { "Refreshing novel from source" }
        try {
            _isLoading.value = true
            _error.value = null
            
            val networkNovel = novelSource.getNovelDetails(currentNovel.url)
            logger.d { "Fetched novel details: ${networkNovel.title}" }
            
            // Check if novel exists in database by URL (not by ID, since browse IDs are temporary)
            val existingNovel = getNovel.awaitByUrlAndSource(currentNovel.url, currentNovel.source)
            if (existingNovel == null) {
                logger.i { "Novel not in database, inserting first before update" }
                // Novel doesn't exist - insert it before updating
                // Use id = 0 to let database auto-generate the ID (don't use browse screen's temporary ID!)
                val novelToInsert = Novel(
                    id = 0,  // Let database auto-generate ID
                    source = currentNovel.source,
                    url = currentNovel.url,
                    title = networkNovel.title,
                    author = networkNovel.author,
                    description = networkNovel.description,
                    genre = networkNovel.genres.joinToString(", "),
                    status = networkNovel.status.toInt(),
                    posterUrl = networkNovel.posterUrl,
                    isFavorite = false,  // Browsed novels aren't in library yet
                    lastUpdate = 0,
                    initialized = true,
                    dateAdded = System.currentTimeMillis(),
                    vibrantCoverColor = currentNovel.vibrantCoverColor,
                    chapterFlags = currentNovel.chapterFlags,
                    filteredTranslators = currentNovel.filteredTranslators
                )
                val insertedId = insertNovel.await(novelToInsert)
                logger.i { "Successfully inserted novel into database with ID: $insertedId" }
                
                // Update our local state with the correctly-IDed novel
                _novel.value = novelToInsert.copy(id = insertedId)
            } else {
                logger.d { "Novel already exists in database with ID ${existingNovel.id}" }
                // Use the existing novel from database
                _novel.value = existingNovel
            }
            
            // Now update using the correct novel ID from _novel.value
            val novelToUpdate = _novel.value!!
            val update = NovelUpdate(
                id = novelToUpdate.id,  // Use the correct database ID
                title = networkNovel.title,
                author = networkNovel.author,
                description = networkNovel.description,
                posterUrl = networkNovel.posterUrl,
                status = networkNovel.status.toInt(),
                genres = networkNovel.genres,
                initialized = true  // Mark as initialized after fetching from source
            )
            
            val updateSuccess = updateNovel.await(update)
            logger.i { "Update result: $updateSuccess" }
            
            // Insert chapters (FK constraint will now be satisfied)
            fetchChaptersFromSource()
            
        } catch (e: Exception) {
            logger.e(e) { "Failed to refresh from source" }
            handleError(e, "Refresh from source")
        } finally {
            _isLoading.value = false
        }
    }
    
    suspend fun fetchChaptersFromSource() {
        logger.d { "fetchChaptersFromSource: Entry point" }
        val currentNovel = _novel.value
        if (currentNovel == null) {
            logger.e { "fetchChaptersFromSource: currentNovel is null, returning" }
            return
        }
        logger.d { "fetchChaptersFromSource: currentNovel found: ${currentNovel.title}" }

        // Guard against inserting chapters for a novel not yet in the database.
        // This happens when a novel is opened from browse/search before being persisted.
        val dbNovel = getNovel.awaitByUrlAndSource(currentNovel.url, currentNovel.source)
        if (dbNovel == null) {
            logger.w { "Novel not found in database, refreshing from source first" }
            refreshNovelFromSource()
            return
        }
        // Ensure we use the correct DB ID going forward
        if (_novel.value?.id != dbNovel.id) {
            _novel.value = dbNovel
        }

        val novelSource = source
        if (novelSource == null) {
            logger.e { "fetchChaptersFromSource: source is null, returning" }
            return
        }
        logger.d { "fetchChaptersFromSource: source found: ${novelSource.javaClass.simpleName}" }

        logger.d { "Fetching chapters from source" }
        try {
            _isLoading.value = true
            _error.value = null

            val networkChapters = novelSource.getChapterList(currentNovel.url)
            logger.d { "Fetched ${networkChapters.size} chapters from source" }
            
            if (networkChapters.isEmpty()) {
                logger.w { "No chapters returned from source" }
                return
            }
            
            // Convert network chapters to domain chapters
            val chaptersToInsert = networkChapters.mapIndexed { index, networkChapter ->
                yokai.domain.novel.NovelChapter(
                    novelId = currentNovel.id,
                    url = networkChapter.url,
                    title = networkChapter.title,
                    chapterNumber = networkChapter.chapterNumber.toDouble(),
                    volumeNumber = null,
                    sourceOrder = networkChapter.sourceOrder,
                    dateFetch = System.currentTimeMillis(),
                    dateUpload = networkChapter.dateUpload,
                    translator = networkChapter.scanlator
                )
            }
            
            logger.i { "Inserting ${chaptersToInsert.size} chapters into database" }
            val insertedChapters = insertNovelChapter.awaitBulk(chaptersToInsert)
            logger.i { "Successfully inserted ${insertedChapters.size} chapters" }
            
            // CRITICAL DEBUG: Verify chapters are actually in the database
            val verifyCount = getNovelChapter.awaitAll(currentNovel.id)
            logger.i { "VERIFICATION: Database query returns ${verifyCount.size} chapters for novelId=${currentNovel.id}" }
            if (verifyCount.isEmpty() && insertedChapters.isNotEmpty()) {
                logger.e { "CRITICAL BUG: Inserted ${insertedChapters.size} chapters but database query returns 0!" }
                logger.e { "NovelId: ${currentNovel.id}, First inserted chapter ID: ${insertedChapters.firstOrNull()?.id}" }
            }
            
        } catch (e: Exception) {
            logger.e(e) { "Failed to fetch chapters" }
            handleError(e, "Fetch chapters")
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Updates the novel's vibrant cover color in the database for caching.
     * This prevents re-extraction of the color on every screen open.
     */
    fun updateNovelCoverColor(color: Int) {
        presenterScope.launchIO {
            try {
                val currentNovel = _novel.value ?: return@launchIO
                val update = NovelUpdate(
                    id = currentNovel.id,
                    vibrantCoverColor = color
                )
                updateNovel.await(update)
                logger.d { "Saved vibrantCoverColor to DB: ${color.toString(16)}" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to save vibrantCoverColor" }
            }
        }
    }
    
    fun markChapterRead(chapter: NovelChapter, read: Boolean) {
        logger.d { "Marking chapter as read=$read" }
        presenterScope.launchIO {
            try {
                val update = NovelChapterUpdate(
                    id = chapter.id,
                    read = read
                )
                updateNovelChapter.await(update)
                logger.i { "Successfully marked chapter as read=$read" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to update chapter" }
                handleError(e, "Mark chapter read")
            }
        }
    }
    
    fun toggleBookmark(chapter: NovelChapter) {
        logger.d { "Toggling bookmark" }
        presenterScope.launchIO {
            try {
                val update = NovelChapterUpdate(
                    id = chapter.id,
                    bookmark = !chapter.bookmark
                )
                updateNovelChapter.await(update)
                logger.i { "Successfully toggled bookmark" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to toggle bookmark" }
                handleError(e, "Toggle bookmark")
            }
        }
    }
    
    fun markMultipleRead(chapters: List<NovelChapter>, read: Boolean) {
        logger.d { "Marking ${chapters.size} chapters as read=$read" }
        presenterScope.launchIO {
            try {
                chapters.forEach { chapter ->
                    val update = NovelChapterUpdate(
                        id = chapter.id,
                        read = read
                    )
                    updateNovelChapter.await(update)
                }
                logger.i { "Successfully marked ${chapters.size} chapters" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to mark multiple chapters" }
                handleError(e, "Mark multiple read")
            }
        }
    }
    
    fun updateNovelFavoriteStatus(favorite: Boolean) {
        android.util.Log.d("NovelDetailsPresenter", "=== NOVEL DETAILS ADD/REMOVE START ===")
        val currentNovel = _novel.value ?: run {
            logger.e { "Cannot update favorite status: novel not loaded" }
            android.util.Log.e("NovelDetailsPresenter", "Cannot update favorite status: novel not loaded")
            return
        }

        presenterScope.launchIO {
            try {
                var actualNovelId = currentNovel.id

                // If novel doesn't have a valid database ID (e.g. browsed but not yet inserted),
                // insert it first before we can update its favorite status
                if (actualNovelId == null || actualNovelId <= 0) {
                    logger.i { "Novel not in database, inserting before updating favorite status" }
                    android.util.Log.d("NovelDetailsPresenter", "Inserting novel into database: ${currentNovel.title}")
                    val novelToInsert = currentNovel.copy(
                        id = 0,
                        isFavorite = false,
                        dateAdded = System.currentTimeMillis()
                    )
                    actualNovelId = insertNovel.await(novelToInsert)
                    _novel.value = currentNovel.copy(id = actualNovelId)
                    android.util.Log.d("NovelDetailsPresenter", "Inserted novel with ID: $actualNovelId")
                }

                logger.d { "Updating novel favorite status to $favorite for novel ID $actualNovelId" }
                val update = NovelUpdate(
                    id = actualNovelId,
                    inLibrary = favorite
                )
                updateNovel.await(update)
                android.util.Log.d("NovelDetailsPresenter", "updateNovel.await completed successfully")
                logger.i { "Successfully updated favorite status for novel $actualNovelId" }

                // FIX: Emit event to trigger library refresh
                _novelFavoriteChangedEvent.emit(actualNovelId)
                android.util.Log.d("NovelDetailsPresenter", "=== NOVEL DETAILS ADD/REMOVE END ===")
            } catch (e: Exception) {
                android.util.Log.e("NovelDetailsPresenter", "updateNovelFavoriteStatus: Error", e)
                logger.e(e) { "Failed to update favorite status" }
                handleError(e, "Update favorite")
            }
        }
    }
    
    fun markPreviousAsRead(chapter: NovelChapter) {
        presenterScope.launchIO {
            val allChapters = _chapters.value
            val targetIndex = allChapters.indexOf(chapter)
            
            if (targetIndex > 0) {
                val previousChapters = allChapters.subList(0, targetIndex)
                logger.d { "Marking ${previousChapters.size} previous chapters as read" }
                markMultipleRead(previousChapters, read = true)
            }
        }
    }
    
    fun markChaptersRead(chapters: List<NovelChapter>, read: Boolean) {
        markMultipleRead(chapters, read)
    }
    
    // ==================== Download Functions ====================
    
    /**
     * Download selected chapters for offline reading
     */
    fun downloadChapters(chapters: List<NovelChapter>) {
        val currentNovel = _novel.value ?: return
        presenterScope.launchIO {
            try {
                downloadManager.queueChapters(currentNovel, chapters)
                logger.d { "Queued ${chapters.size} chapters for download" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to queue chapters for download" }
            }
        }
    }
    
    /**
     * Delete downloaded chapters
     */
    fun deleteDownloadedChapters(chapters: List<NovelChapter>) {
        val currentNovel = _novel.value ?: return
        presenterScope.launchIO {
            chapters.forEach { chapter ->
                try {
                    downloadManager.deleteChapter(currentNovel, chapter)
                } catch (e: Exception) {
                    logger.e(e) { "Failed to delete chapter download: ${chapter.title}" }
                }
            }
            logger.d { "Deleted ${chapters.size} downloaded chapters" }
        }
    }
    
    /**
     * Check if a chapter is downloaded
     */
    fun isChapterDownloaded(chapter: NovelChapter): Boolean {
        val currentNovel = _novel.value ?: return false
        return downloadManager.isChapterDownloaded(currentNovel, chapter)
    }
    
    /**
     * Get download count for current novel
     */
    fun getDownloadCount(): Int {
        val currentNovel = _novel.value ?: return 0
        return downloadManager.getDownloadCount(currentNovel)
    }
    
    private fun handleError(e: Exception, context: String) {
        val message = when (e) {
            is java.net.UnknownHostException -> "No internet connection"
            is java.net.SocketTimeoutException -> "Connection timeout"
            is IllegalStateException -> e.message ?: "Invalid state"
            else -> "$context failed: ${e.message}"
        }
        _error.value = message
        logger.e(e) { "Error in $context: $message" }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun updateNovel(
        title: String?,
        author: String?,
        artist: String?, // Unused for novels
        uri: Uri?,
        description: String?,
        tags: List<String>?,
        status: Int?,
        seriesType: Int?, // Unused for novels
        lang: String?,
        resetCover: Boolean = false,
    ) {
        presenterScope.launchIO {
            val currentNovel = _novel.value ?: return@launchIO
            
            val update = NovelUpdate(
                id = currentNovel.id,
                title = title?.takeIf { it.isNotBlank() },
                author = author?.takeIf { it.isNotBlank() },
                artist = artist?.takeIf { it.isNotBlank() },
                description = description?.takeIf { it.isNotBlank() },
                genres = tags?.takeIf { it.isNotEmpty() },
                status = status?.toInt(),
            )
            
            try {
                updateNovel.await(update)
                logger.i { "Successfully updated novel metadata" }
                
                // TODO: Handle custom cover URI if needed
                if (uri != null) {
                    logger.d { "Custom cover URI provided but not yet implemented" }
                }
                
            } catch (e: Exception) {
                logger.e(e) { "Failed to update novel metadata" }
                handleError(e, "Update novel")
            }
        }
    }
    
    companion object {
        // Chapter scroll/grouping type constants
        const val TENS_OF_CHAPTERS = 0
        const val MULTIPLE_VOLUMES = 1
        const val MULTIPLE_SEASONS = 2
    }
}