package eu.kanade.tachiyomi.ui.novel.reader

import android.content.Context
import android.text.Spanned
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jsoup.Jsoup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import yokai.core.novel.reader.NovelReaderPreferences
import yokai.domain.novel.Novel
import yokai.domain.novel.NovelChapter
import yokai.domain.novel.NovelRepository
import yokai.domain.novel.NovelReadingPosition
import yokai.source.novel.NovelMainAPI
import eu.kanade.tachiyomi.source.SourceManager

/**
 * ViewModel for novel reading with content streaming, position tracking, and chapter navigation.
 * Handles direct content flow from providers to reader, bypassing manga's page-based structure.
 *
 * Uses Koin for constructor injection (NovelRepository) and Injekt for SourceManager.
 */
class NovelReaderViewModel(
    private val novelRepository: NovelRepository,
    private val sourceManager: SourceManager,
) : ViewModel(), KoinComponent {

    // Novel download manager for offline reading
    private val novelDownloadManager: NovelDownloadManager by inject()

    // State flows for UI
    private val _novel = MutableStateFlow<Novel?>(null)
    val novel: StateFlow<Novel?> = _novel.asStateFlow()

    private val _currentChapter = MutableStateFlow<NovelChapter?>(null)
    val currentChapter: StateFlow<NovelChapter?> = _currentChapter.asStateFlow()

    // FIX: Start with loading message instead of empty string to prevent white flash
    // Activity observer will show loading indicator for "Loading..." state
    private val _chapterContent = MutableStateFlow("Loading chapter content...")
    val chapterContent: StateFlow<String> = _chapterContent.asStateFlow()

    private val _chapters = MutableStateFlow<List<NovelChapter>>(emptyList())
    val chapters: StateFlow<List<NovelChapter>> = _chapters.asStateFlow()

    private val _readerPreferences = MutableStateFlow(NovelReaderPreferences())
    val readerPreferences: StateFlow<NovelReaderPreferences> = _readerPreferences.asStateFlow()

    private val _readingProgress = MutableStateFlow(NovelReadingProgress())
    val readingProgress: StateFlow<NovelReadingProgress> = _readingProgress.asStateFlow()

    private val _uiState = MutableStateFlow(NovelReaderUiState())
    val uiState: StateFlow<NovelReaderUiState> = _uiState.asStateFlow()

    // Navigation loading states for manga-style overlay
    private val _isLoadingPrevious = MutableStateFlow(false)
    val isLoadingPrevious: StateFlow<Boolean> = _isLoadingPrevious.asStateFlow()

    private val _isLoadingNext = MutableStateFlow(false)
    val isLoadingNext: StateFlow<Boolean> = _isLoadingNext.asStateFlow()

    // Events for one-time UI updates
    private val _events = MutableSharedFlow<NovelReaderEvent>()
    val events: SharedFlow<NovelReaderEvent> = _events.asSharedFlow()

    // Phase 3: Content items for RecyclerView display (paragraph-level rendering)
    // MIGRATION: Changed from NovelContentItem to TextItem (Phase 3 - Option A)
    private val _contentItems = MutableStateFlow<List<TextItem>>(emptyList())
    val contentItems: StateFlow<List<TextItem>> = _contentItems.asStateFlow()

    // Phase 4.1: Reading mode state management for multi-chapter features
    private val _readingMode = MutableStateFlow(ReadingMode.DEFAULT)
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()
    
    // Phase 4.1: Chapter loading states for infinite scroll
    var isLoadingNextChapter = false
        private set
    var isLoadingPreviousChapter = false
        private set
    
    // BUG FIX: Track which chapters have been loaded into content items to prevent duplicates
    // This is more reliable than currentChapterIndex which changes during scroll
    private val loadedChapterIds = mutableSetOf<Long>()
    
    // BUG FIX: Flag to track if next content update should trigger scroll restoration
    // Only true when loading a new chapter manually (not during infinite scroll append)
    private val _shouldScrollOnNextUpdate = MutableStateFlow(false)
    val shouldScrollOnNextUpdate: StateFlow<Boolean> = _shouldScrollOnNextUpdate.asStateFlow()

    // Markwon instance for HTML → Spanned conversion with paragraph-level parsing
    // Lazy initialization requires context from Activity (initContext() must be called first)
    private var context: Context? = null
    private val markwon: Markwon by lazy {
        val ctx = context ?: throw IllegalStateException("Context not initialized. Call initContext() first.")
        Markwon.builder(ctx)
            .usePlugin(HtmlPlugin.create { plugin ->
                plugin.excludeDefaults(false) // Keep default HTML tag handlers (p, br, b, i, etc.)
            })
            .build()
    }

    /**
     * Initialize context for Markwon HTML parsing.
     * Must be called from Activity before any chapter loading occurs.
     * 
     * @param ctx Application or Activity context
     */
    fun initContext(ctx: Context) {
        this.context = ctx.applicationContext
        android.util.Log.d("NovelReaderViewModel", "Context initialized for Markwon HTML parsing")
    }

    // Internal state
    private var currentProvider: NovelMainAPI? = null
    val supportsComments: Boolean
        get() = currentProvider?.capabilities?.supportsComments == true
    private var currentChapterIndex = 0
    private var characterPosition = 0
    private var readingStartTime = 0L

    // Phase 4.2: Chapter content cache for pre-loading optimization
    private val chapterCache = ChapterContentCache(maxCachedChapters = 3)
    
    // Preferences for loading saved settings
    private val preferences: eu.kanade.tachiyomi.data.preference.PreferencesHelper by uy.kohesive.injekt.injectLazy()

    fun initialize(novelId: Long, chapterId: Long?) {
        viewModelScope.launch {
            try {
                android.util.Log.d("NovelReaderViewModel", "=== initialize START ===")
                android.util.Log.d("NovelReaderViewModel", "Novel ID: $novelId, Chapter ID: $chapterId")
                
                // BUG FIX: Initialize reading mode from saved preferences instead of hardcoded DEFAULT
                val savedModeValue = preferences.novelReadingMode().get()
                val savedMode = ReadingMode.fromPrefValue(savedModeValue)
                _readingMode.value = savedMode
                android.util.Log.d("NovelReaderViewModel", "Loaded reading mode from preferences: $savedMode (value=$savedModeValue)")
                
                // Load novel
                val novel = novelRepository.getNovelById(novelId)
                if (novel == null) {
                    android.util.Log.e("NovelReaderViewModel", "Novel not found!")
                    _events.emit(NovelReaderEvent.ShowError("Novel not found"))
                    return@launch
                }
                _novel.value = novel
                android.util.Log.d("NovelReaderViewModel", "Novel loaded: ${novel.title}, Source: ${novel.source}")

                // Get provider for content loading via SourceManager (more reliable than NovelProviderRegistry)
                // MUST be set BEFORE loading chapters so loadChapter() can use it!
                android.util.Log.d("NovelReaderViewModel", "Getting provider for source ${novel.source}...")
                val sourceInstance = sourceManager.getOrStub(novel.source)
                currentProvider = when (sourceInstance) {
                    is eu.kanade.tachiyomi.source.novel.NovelSourceWrapper -> sourceInstance.novelProvider
                    is NovelMainAPI -> sourceInstance
                    else -> null
                }
                if (currentProvider == null) {
                    android.util.Log.e("NovelReaderViewModel", "Provider not found for source ${novel.source}")
                    _events.emit(NovelReaderEvent.ShowError("Source not installed for this novel"))
                    return@launch
                }
                android.util.Log.d("NovelReaderViewModel", "Provider found: ${currentProvider?.name}")

                // Load chapters (use direct query for one-time loading, matching NovelDetailsPresenter pattern)
                android.util.Log.d("NovelReaderViewModel", "Loading chapters...")
                val chapterList = novelRepository.getChaptersByNovelIdOnce(novelId)
                _chapters.value = chapterList.sortedBy { it.sourceOrder }
                android.util.Log.d("NovelReaderViewModel", "Loaded ${chapterList.size} chapters")
                android.util.Log.d("NovelReaderViewModel", "Looking for chapter with ID: $chapterId")
                
                // Set current chapter
                val targetChapter = if (chapterId != null) {
                    val found = chapterList.find { it.id == chapterId }
                    android.util.Log.d("NovelReaderViewModel", "Searching by chapter ID: found=${found != null}")
                    found
                } else {
                    // Find first unread chapter or first chapter
                    val unread = chapterList.find { !it.read }
                    val first = chapterList.firstOrNull()
                    android.util.Log.d("NovelReaderViewModel", "No chapter ID specified, using first unread or first: unread=${unread != null}, first=${first != null}")
                    unread ?: first
                }
                
                if (targetChapter != null) {
                    currentChapterIndex = _chapters.value.indexOf(targetChapter)
                    
                    // CRITICAL: Load saved position BEFORE loading content
                    // This ensures shouldScrollTo flag is set before content observer runs
                    loadSavedPosition(novelId, targetChapter.id)
                    
                    loadChapter(targetChapter)
                } else {
                    android.util.Log.e("NovelReaderViewModel", "No target chapter found - chapter list empty")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderViewModel", "Failed to initialize reader", e)
                _events.emit(NovelReaderEvent.ShowError("Failed to initialize: ${e.message}"))
            }
        }
    }

    private suspend fun loadChapter(chapter: NovelChapter) {
        try {
            _currentChapter.value = chapter
            val novel = _novel.value
            
            // Load content from provider
            val provider = currentProvider
            if (provider == null) {
                android.util.Log.e("NovelReaderViewModel", "No provider available")
                _events.emit(NovelReaderEvent.ShowError("No provider available"))
                return
            }

            // Show loading state
            _chapterContent.value = "Loading chapter content..."
            
            // Phase 5: Check downloaded content first (offline reading support)
            val downloadedContent = if (novel != null) {
                novelDownloadManager.getDownloadedContent(novel, chapter)
            } else null
            
            val htmlContent: String
            val items: List<TextItem>
            
            if (downloadedContent != null) {
                android.util.Log.d("NovelReaderViewModel", "Using downloaded content for chapter ${chapter.id}")
                htmlContent = downloadedContent
                items = wrapWithNavigation(parseHtmlToParagraphs(htmlContent, chapter.id), chapter.id)
            } else {
                // Phase 4.2: Check in-memory cache before fetching from provider
                val cachedContent = chapterCache.get(chapter.id)
                
                if (cachedContent != null) {
                    android.util.Log.d("NovelReaderViewModel", "Using cached content for chapter ${chapter.id}")
                    htmlContent = cachedContent.rawHtml  // BUG FIX: Use raw HTML to preserve <p> tags
                    // Parse HTML and wrap with navigation
                    items = wrapWithNavigation(parseHtmlToParagraphs(htmlContent, chapter.id), chapter.id)
                } else {
                    android.util.Log.d("NovelReaderViewModel", "Fetching chapter ${chapter.id} from provider")
                    // Load chapter content from novel provider
                    val novelContent = provider.getNovelChapterContent(chapter.url)
                    htmlContent = novelContent.content
                    
                    // Phase 3: Parse HTML into paragraph items, then wrap with navigation
                    items = wrapWithNavigation(parseHtmlToParagraphs(htmlContent, chapter.id), chapter.id)
                    
                    // Phase 4.2: Cache the RAW HTML (not parsed text) to preserve structure
                    chapterCache.put(
                        chapter.id,
                        ChapterContentCache.ChapterContent(
                            chapter = chapter,
                            rawHtml = htmlContent,  // BUG FIX: Store raw HTML instead of parsed paragraphs
                            totalCharacters = htmlContent.length
                        )
                    )
                }
            }
            
            // BUG FIX: Track this chapter as loaded for infinite scroll duplicate prevention
            loadedChapterIds.clear()  // Clear on new chapter load (not infinite scroll append)
            loadedChapterIds.add(chapter.id)
            android.util.Log.d("NovelReaderViewModel", "Tracking loaded chapter: ${chapter.id} (cleared previous)")
            
            // BUG FIX: Mark that next content update should trigger scroll restoration
            _shouldScrollOnNextUpdate.value = true
            _contentItems.value = items
            
            // Keep for backward compatibility during migration (Phase 3 will eventually remove)
            _chapterContent.value = htmlContent
            
            // Update UI state
            updateUiState()
            
            // Update reading session
            readingStartTime = System.currentTimeMillis()
            
            // Emit chapter changed event
            _events.emit(NovelReaderEvent.ChapterChanged(chapter.title))
            
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderViewModel", "=== loadChapter FAILED ===", e)
            android.util.Log.e("NovelReaderViewModel", "Error message: ${e.message}")
            android.util.Log.e("NovelReaderViewModel", "Error stack trace:", e)
            _chapterContent.value = "Error: ${e.message}"
            _events.emit(NovelReaderEvent.ShowError("Failed to load chapter: ${e.message}"))
        }
    }

    private suspend fun loadSavedPosition(novelId: Long, chapterId: Long?) {
        if (chapterId == null) {
            android.util.Log.w("NovelReaderViewModel", "Cannot load saved position - chapterId is null")
            return
        }
        
        try {
            val savedPosition = novelRepository.getReadingPosition(novelId, chapterId)
            android.util.Log.d("NovelReaderViewModel", "loadSavedPosition: novelId=$novelId, chapterId=$chapterId")
            
            if (savedPosition != null) {
                android.util.Log.d("NovelReaderViewModel", "Loaded saved position: charPos=${savedPosition.characterPosition}, scrollPos=${savedPosition.scrollPosition}")
                characterPosition = savedPosition.characterPosition
                _readingProgress.value = _readingProgress.value.copy(
                    shouldScrollTo = true,
                    scrollPosition = savedPosition.scrollPosition
                )
            } else {
                android.util.Log.d("NovelReaderViewModel", "No saved position found - will start at beginning")
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderViewModel", "Failed to load saved position", e)
        }
    }

    /**
     * Load a specific chapter by ID (from chapter list selection)
     */
    fun loadChapterById(chapterId: Long) {
        val chapter = _chapters.value.find { it.id == chapterId } ?: return
        val newIndex = _chapters.value.indexOf(chapter)
        if (newIndex != -1) {
            currentChapterIndex = newIndex
            viewModelScope.launch {
                try {
                    loadChapter(chapter)
                } catch (e: Exception) {
                    android.util.Log.e("NovelReaderViewModel", "Failed to load chapter", e)
                    _events.emit(NovelReaderEvent.ShowError("Failed to load chapter: ${e.message}"))
                }
            }
        }
    }

    /**
     * Update the current chapter by ID without loading new content.
     * Used during infinite scroll to update the toolbar title when scrolling between chapters.
     * This doesn't trigger content loading - it just updates the UI state.
     * 
     * @param chapterId The ID of the chapter that is now visible
     */
    fun updateCurrentChapterById(chapterId: Long) {
        val chapter = _chapters.value.find { it.id == chapterId }
        if (chapter != null && _currentChapter.value?.id != chapterId) {
            android.util.Log.d("NovelReaderViewModel", "Updating current chapter to: ${chapter.title} (ID: $chapterId)")
            _currentChapter.value = chapter
            currentChapterIndex = _chapters.value.indexOf(chapter)
            
            // Chapter read status is only updated via saveCurrentPosition when
            // meaningful progress is made (not just from scrolling into view).
        }
    }

    fun navigateToPreviousChapter() {
        if (currentChapterIndex > 0 && !_isLoadingPrevious.value) {
            _isLoadingPrevious.value = true
            currentChapterIndex--
            val chapter = _chapters.value[currentChapterIndex]
            viewModelScope.launch {
                try {
                    // BUG FIX: Clear content list when manually navigating (not infinite scroll)
                    // This ensures scroll bar reflects current chapter only
                    if (_readingMode.value != ReadingMode.INFINITE_SCROLL) {
                        _contentItems.value = emptyList()
                    }
                    
                    // BUG FIX: Disable auto-scroll restoration for manual navigation
                    // ScrollToPosition event will handle the scroll instead
                    _shouldScrollOnNextUpdate.value = false
                    
                    loadChapter(chapter)
                    
                    // BUG FIX: In DEFAULT/OVERSCROLL modes, scroll to bottom after loading previous chapter
                    // This provides natural navigation experience (back = end of previous chapter)
                    if (_readingMode.value != ReadingMode.INFINITE_SCROLL) {
                        _events.emit(NovelReaderEvent.ScrollToPosition(-1)) // -1 = scroll to bottom
                    }
                } finally {
                    _isLoadingPrevious.value = false
                }
            }
        }
    }

    fun navigateToNextChapter() {
        if (currentChapterIndex < _chapters.value.size - 1 && !_isLoadingNext.value) {
            _isLoadingNext.value = true
            currentChapterIndex++
            val chapter = _chapters.value[currentChapterIndex]
            viewModelScope.launch {
                try {
                    // BUG FIX: Clear content list when manually navigating (not infinite scroll)
                    // This ensures scroll bar reflects current chapter only
                    if (_readingMode.value != ReadingMode.INFINITE_SCROLL) {
                        _contentItems.value = emptyList()
                    }
                    
                    // BUG FIX: Disable auto-scroll restoration for manual navigation
                    // ScrollToPosition event will handle the scroll instead
                    _shouldScrollOnNextUpdate.value = false
                    
                    loadChapter(chapter)
                    
                    // BUG FIX: In DEFAULT/OVERSCROLL modes, scroll to top after loading next chapter
                    // This provides natural navigation experience (forward = start of next chapter)
                    if (_readingMode.value != ReadingMode.INFINITE_SCROLL) {
                        _events.emit(NovelReaderEvent.ScrollToPosition(0)) // 0 = scroll to top
                    }
                } finally {
                    _isLoadingNext.value = false
                }
            }
        }
    }

    /**
     * Phase 4.1: Set reading mode and adjust content loading strategy.
     * 
     * @param mode Reading mode to enable (DEFAULT, INFINITE_SCROLL, OVERSCROLL)
     */
    fun setReadingMode(mode: ReadingMode) {
        android.util.Log.d("NovelReaderViewModel", "Setting reading mode: $mode")
        _readingMode.value = mode
        
        // BUG FIX: Don't re-wrap content when switching modes in multi-chapter infinite scroll
        // Re-wrapping would lose all non-current chapters that were loaded
        // Instead, we should only re-wrap when in single-chapter view (DEFAULT/OVERSCROLL)
        
        // Only re-wrap if we have exactly one chapter's worth of content
        // (Infinite scroll may have multiple chapters loaded)
        val currentItems = _contentItems.value
        val chapterIds = currentItems.mapNotNull { item ->
            when (item) {
                is TextItem.Paragraph -> item.chapterId
                is TextItem.ChapterHeader -> item.chapterId
                else -> null
            }
        }.distinct()
        
        // If only one chapter loaded, safe to re-wrap for button visibility
        if (chapterIds.size == 1) {
            val currentChapter = _currentChapter.value
            if (currentChapter != null) {
                val currentParagraphs = currentItems.filterIsInstance<TextItem.Paragraph>()
                if (currentParagraphs.isNotEmpty()) {
                    val reWrappedItems = wrapWithNavigation(currentParagraphs, currentChapter.id)
                    _contentItems.value = reWrappedItems
                    android.util.Log.d("NovelReaderViewModel", "Re-wrapped single chapter for mode $mode (${reWrappedItems.size} items)")
                }
            }
        } else {
            // Multiple chapters loaded - can't safely re-wrap without complex multi-chapter logic
            // User will see old button visibility until they manually navigate
            android.util.Log.d("NovelReaderViewModel", "Multiple chapters loaded (${chapterIds.size} chapters) - skipping re-wrap to preserve content")
        }
        
        // Adjust loading strategy based on mode
        when (mode) {
            ReadingMode.DEFAULT -> {
                // Single chapter mode - current behavior unchanged
                android.util.Log.d("NovelReaderViewModel", "DEFAULT mode: Single chapter display")
            }
            ReadingMode.INFINITE_SCROLL -> {
                // Enable multi-chapter mode - will be handled by scroll listener in Activity
                android.util.Log.d("NovelReaderViewModel", "INFINITE_SCROLL mode: Multi-chapter enabled")
                // Pre-load next chapter if available
                viewModelScope.launch {
                    preloadNextChapterIfNeeded()
                }
            }
            ReadingMode.OVERSCROLL -> {
                // Gesture-based navigation - current single-chapter behavior
                android.util.Log.d("NovelReaderViewModel", "OVERSCROLL mode: Gesture navigation enabled")
            }
        }
    }

    /**
     * Phase 4.2: Load next chapter in background for infinite scroll.
     * Appends content to existing TextItem list without clearing current chapter.
     */
    suspend fun loadNextChapterInBackground() {
        if (isLoadingNextChapter) {
            android.util.Log.d("NovelReaderViewModel", "Already loading next chapter, skipping")
            return
        }
        
        if (currentChapterIndex >= _chapters.value.size - 1) {
            android.util.Log.d("NovelReaderViewModel", "Already at last chapter")
            return
        }
        
        // BUG FIX: Find the highest loaded chapter index to get the TRUE next chapter
        // This prevents duplication when currentChapterIndex gets updated by scroll detection
        val highestLoadedIndex = _chapters.value.indexOfLast { loadedChapterIds.contains(it.id) }
        val actualNextIndex = if (highestLoadedIndex >= 0) highestLoadedIndex + 1 else currentChapterIndex + 1
        
        if (actualNextIndex >= _chapters.value.size) {
            android.util.Log.d("NovelReaderViewModel", "No more chapters to load after highest loaded")
            return
        }
        
        val nextChapter = _chapters.value[actualNextIndex]
        
        // BUG FIX: Check if this chapter is already loaded
        if (loadedChapterIds.contains(nextChapter.id)) {
            android.util.Log.d("NovelReaderViewModel", "Chapter ${nextChapter.id} already loaded, skipping")
            return
        }
        
        isLoadingNextChapter = true
        
        // ENHANCED LOGGING: Track which chapters are being loaded
        val nextIndex = actualNextIndex
        android.util.Log.d("NovelReaderViewModel", "=== LOADING NEXT CHAPTER IN BACKGROUND ===")
        android.util.Log.d("NovelReaderViewModel", "Current chapter index: $currentChapterIndex")
        android.util.Log.d("NovelReaderViewModel", "Loading chapter index: $nextIndex")
        android.util.Log.d("NovelReaderViewModel", "Chapter title: ${nextChapter.title}")
        android.util.Log.d("NovelReaderViewModel", "Current items count: ${_contentItems.value.size}")
        
        try {
            // Add loading indicator at end
            val currentItems = _contentItems.value.toMutableList()
            val nextChapterId = _chapters.value.getOrNull(currentChapterIndex + 1)?.id ?: -1L
            currentItems.add(TextItem.Loading(System.currentTimeMillis(), nextChapterId, "Loading next chapter..."))
            _contentItems.value = currentItems
            
            val provider = currentProvider
            if (provider == null) {
                android.util.Log.e("NovelReaderViewModel", "No provider available for background load")
                return
            }
            
            // Phase 4.2: Check cache first
            val cachedContent = chapterCache.get(nextChapter.id)
            val nextItems: List<TextItem>
            
            if (cachedContent != null) {
                android.util.Log.d("NovelReaderViewModel", "Using cached next chapter ${nextChapter.id}")
                val htmlContent = cachedContent.rawHtml  // BUG FIX: Use raw HTML to preserve <p> tags
                nextItems = parseHtmlToParagraphs(htmlContent, nextChapter.id)
            } else {
                android.util.Log.d("NovelReaderViewModel", "Fetching next chapter ${nextChapter.id} from provider")
                // Fetch chapter content
                val novelContent = provider.getNovelChapterContent(nextChapter.url)
                nextItems = parseHtmlToParagraphs(novelContent.content, nextChapter.id)
                
                // Cache for future use (store raw HTML, not parsed text)
                chapterCache.put(
                    nextChapter.id,
                    ChapterContentCache.ChapterContent(
                        chapter = nextChapter,
                        rawHtml = novelContent.content,  // BUG FIX: Store raw HTML instead of parsed paragraphs
                        totalCharacters = novelContent.content.length
                    )
                )
            }
            
            // Wrap next chapter items with navigation/headers (headers will show, buttons will be hidden in infinite scroll)
            val wrappedNextItems = wrapWithNavigation(nextItems, nextChapter.id)
            
            // Append to current list (remove loading indicator first)
            val updatedItems = currentItems.dropLast(1) + wrappedNextItems
            // BUG FIX: Don't trigger scroll restoration for infinite scroll append
            _shouldScrollOnNextUpdate.value = false
            _contentItems.value = updatedItems
            
            // Update current chapter index
            currentChapterIndex = nextIndex
            _currentChapter.value = nextChapter
            
            // BUG FIX: Track this chapter as loaded to prevent duplicates
            loadedChapterIds.add(nextChapter.id)
            android.util.Log.d("NovelReaderViewModel", "Added to loaded chapters: ${nextChapter.id}, total loaded: ${loadedChapterIds.size}")
            
            android.util.Log.d("NovelReaderViewModel", "Next chapter loaded: ${nextChapter.title} (${nextItems.size} items)")
            android.util.Log.d("NovelReaderViewModel", "Updated current chapter index to: $currentChapterIndex")
            android.util.Log.d("NovelReaderViewModel", "Total items after append: ${updatedItems.size}")
            android.util.Log.d("NovelReaderViewModel", "=== NEXT CHAPTER LOAD COMPLETE ===")
            
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderViewModel", "Failed to load next chapter in background", e)
            // Remove loading indicator on error
            val currentItems = _contentItems.value.toMutableList()
            _contentItems.value = currentItems.dropLast(1)
        } finally {
            isLoadingNextChapter = false
        }
    }

    /**
     * Phase 4.2: Load previous chapter in background for infinite scroll.
     * Prepends content to existing TextItem list.
     */
    suspend fun loadPreviousChapterInBackground() {
        if (isLoadingPreviousChapter) {
            android.util.Log.d("NovelReaderViewModel", "Already loading previous chapter, skipping")
            return
        }
        
        if (currentChapterIndex <= 0) {
            android.util.Log.d("NovelReaderViewModel", "Already at first chapter")
            return
        }
        
        // BUG FIX: Find the lowest loaded chapter index to get the TRUE previous chapter
        // This prevents duplication when currentChapterIndex gets updated by scroll detection
        val lowestLoadedIndex = _chapters.value.indexOfFirst { loadedChapterIds.contains(it.id) }
        val actualPrevIndex = if (lowestLoadedIndex > 0) lowestLoadedIndex - 1 else currentChapterIndex - 1
        
        if (actualPrevIndex < 0) {
            android.util.Log.d("NovelReaderViewModel", "No more chapters to load before lowest loaded")
            return
        }
        
        val prevChapter = _chapters.value[actualPrevIndex]
        
        // BUG FIX: Check if this chapter is already loaded
        if (loadedChapterIds.contains(prevChapter.id)) {
            android.util.Log.d("NovelReaderViewModel", "Chapter ${prevChapter.id} already loaded, skipping")
            return
        }
        
        isLoadingPreviousChapter = true
        
        // ENHANCED LOGGING: Track which chapters are being loaded
        val prevIndex = actualPrevIndex
        android.util.Log.d("NovelReaderViewModel", "=== LOADING PREVIOUS CHAPTER IN BACKGROUND ===")
        android.util.Log.d("NovelReaderViewModel", "Current chapter index: $currentChapterIndex")
        android.util.Log.d("NovelReaderViewModel", "Loading chapter index: $prevIndex")
        android.util.Log.d("NovelReaderViewModel", "Chapter title: ${prevChapter.title}")
        android.util.Log.d("NovelReaderViewModel", "Current items count: ${_contentItems.value.size}")
        
        try {
            // Add loading indicator at beginning
            val currentItems = _contentItems.value.toMutableList()
            val prevChapterId = _chapters.value.getOrNull(currentChapterIndex - 1)?.id ?: -1L
            currentItems.add(0, TextItem.Loading(System.currentTimeMillis(), prevChapterId, "Loading previous chapter..."))
            _contentItems.value = currentItems
            
            val provider = currentProvider
            if (provider == null) {
                android.util.Log.e("NovelReaderViewModel", "No provider available for background load")
                return
            }
            
            // Phase 4.2: Check cache first
            val cachedContent = chapterCache.get(prevChapter.id)
            val prevItems: List<TextItem>
            
            if (cachedContent != null) {
                android.util.Log.d("NovelReaderViewModel", "Using cached previous chapter ${prevChapter.id}")
                val htmlContent = cachedContent.rawHtml  // BUG FIX: Use raw HTML to preserve <p> tags
                prevItems = parseHtmlToParagraphs(htmlContent, prevChapter.id)
            } else {
                android.util.Log.d("NovelReaderViewModel", "Fetching previous chapter ${prevChapter.id} from provider")
                // Fetch chapter content
                val novelContent = provider.getNovelChapterContent(prevChapter.url)
                prevItems = parseHtmlToParagraphs(novelContent.content, prevChapter.id)
                
                // Cache for future use (store raw HTML, not parsed text)
                chapterCache.put(
                    prevChapter.id,
                    ChapterContentCache.ChapterContent(
                        chapter = prevChapter,
                        rawHtml = novelContent.content,  // BUG FIX: Store raw HTML instead of parsed paragraphs
                        totalCharacters = novelContent.content.length
                    )
                )
            }
            
            // Wrap previous chapter items with navigation/headers
            val wrappedPrevItems = wrapWithNavigation(prevItems, prevChapter.id)
            
            // Prepend to current list (remove loading indicator first)
            val updatedItems = wrappedPrevItems + currentItems.drop(1)
            // BUG FIX: Don't trigger scroll restoration for infinite scroll prepend
            _shouldScrollOnNextUpdate.value = false
            _contentItems.value = updatedItems
            
            // Update current chapter index
            currentChapterIndex = prevIndex
            _currentChapter.value = prevChapter
            
            // BUG FIX: Track this chapter as loaded to prevent duplicates
            loadedChapterIds.add(prevChapter.id)
            android.util.Log.d("NovelReaderViewModel", "Added to loaded chapters: ${prevChapter.id}, total loaded: ${loadedChapterIds.size}")
            
            android.util.Log.d("NovelReaderViewModel", "Previous chapter loaded: ${prevChapter.title} (${prevItems.size} items)")
            android.util.Log.d("NovelReaderViewModel", "Updated current chapter index to: $currentChapterIndex")
            android.util.Log.d("NovelReaderViewModel", "Total items after prepend: ${updatedItems.size}")
            android.util.Log.d("NovelReaderViewModel", "=== PREVIOUS CHAPTER LOAD COMPLETE ===")
            
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderViewModel", "Failed to load previous chapter in background", e)
            // Remove loading indicator on error
            val currentItems = _contentItems.value.toMutableList()
            _contentItems.value = currentItems.drop(1)
        } finally {
            isLoadingPreviousChapter = false
        }
    }

    /**
     * Phase 4.2: Pre-load next chapter if close to end (cache optimization).
     * Silently fetches next chapter content and stores in cache.
     */
    private suspend fun preloadNextChapterIfNeeded() {
        if (_readingMode.value != ReadingMode.INFINITE_SCROLL) return
        if (currentChapterIndex >= _chapters.value.size - 1) return
        if (isLoadingNextChapter) return
        
        val nextChapter = _chapters.value.getOrNull(currentChapterIndex + 1) ?: return
        
        // Check if already cached
        val cached = chapterCache.get(nextChapter.id)
        if (cached != null) {
            android.util.Log.d("NovelReaderViewModel", "Next chapter ${nextChapter.id} already cached")
            return
        }
        
        android.util.Log.d("NovelReaderViewModel", "Pre-loading next chapter ${nextChapter.id} into cache...")
        
        try {
            val provider = currentProvider ?: return
            
            // Fetch silently in background
            val novelContent = provider.getNovelChapterContent(nextChapter.url)
            val items = parseHtmlToParagraphs(novelContent.content, nextChapter.id)
            
            // Store in cache (raw HTML for future re-parsing)
            chapterCache.put(
                nextChapter.id,
                ChapterContentCache.ChapterContent(
                    chapter = nextChapter,
                    rawHtml = novelContent.content,  // BUG FIX: Store raw HTML, not parsed text
                    totalCharacters = novelContent.content.length
                )
            )
            
            android.util.Log.d("NovelReaderViewModel", "Successfully pre-loaded chapter ${nextChapter.id}")
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderViewModel", "Failed to pre-load next chapter", e)
            // Silent failure - not critical for user experience
        }
    }
    
    /**
     * Phase 4.2: Get cache statistics for monitoring.
     */
    suspend fun getCacheStats(): ChapterContentCache.CacheStats {
        return chapterCache.getStats()
    }
    
    /**
     * Phase 4.2: Clear chapter cache (useful for debugging/testing).
     */
    suspend fun clearCache() {
        chapterCache.clear()
        android.util.Log.d("NovelReaderViewModel", "Chapter cache cleared")
    }
    
    // ==================== Download Functions ====================
    
    /**
     * Download the current chapter for offline reading
     */
    fun downloadCurrentChapter() {
        val novel = _novel.value ?: return
        val chapter = _currentChapter.value ?: return
        
        viewModelScope.launch {
            try {
                val success = novelDownloadManager.downloadChapter(novel, chapter)
                if (success) {
                    _events.emit(NovelReaderEvent.ShowMessage("Chapter downloaded"))
                } else {
                    _events.emit(NovelReaderEvent.ShowError("Failed to download chapter"))
                }
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderViewModel", "Download failed", e)
                _events.emit(NovelReaderEvent.ShowError("Download failed: ${e.message}"))
            }
        }
    }
    
    /**
     * Download multiple chapters for offline reading
     */
    fun downloadChapters(chapterIds: List<Long>) {
        val novel = _novel.value ?: return
        val chaptersToDownload = _chapters.value.filter { it.id in chapterIds }
        
        if (chaptersToDownload.isEmpty()) return
        
        viewModelScope.launch {
            novelDownloadManager.queueDomainChapters(novel, chaptersToDownload)
            _events.emit(NovelReaderEvent.ShowMessage("Queued ${chaptersToDownload.size} chapters for download"))
        }
    }
    
    /**
     * Check if current chapter is downloaded
     */
    fun isCurrentChapterDownloaded(): Boolean {
        val novel = _novel.value ?: return false
        val chapter = _currentChapter.value ?: return false
        return novelDownloadManager.isChapterDownloaded(novel, chapter)
    }
    
    /**
     * Delete downloaded chapter
     */
    fun deleteDownloadedChapter() {
        val novel = _novel.value ?: return
        val chapter = _currentChapter.value ?: return
        
        viewModelScope.launch {
            novelDownloadManager.deleteChapter(novel, chapter)
            _events.emit(NovelReaderEvent.ShowMessage("Download deleted"))
        }
    }

    fun toggleBookmark() {
        val chapter = _currentChapter.value ?: return
        viewModelScope.launch {
            try {
                val updatedChapter = chapter.copy(bookmark = !chapter.bookmark)
                novelRepository.updateChapter(updatedChapter)
                _currentChapter.value = updatedChapter
                
                updateUiState()
                _events.emit(NovelReaderEvent.BookmarkToggled(updatedChapter.bookmark))
            } catch (e: Exception) {
                _events.emit(NovelReaderEvent.ShowError("Failed to toggle bookmark"))
            }
        }
    }

    fun updateScrollPosition(scrollY: Int, contentHeight: Int) {
        // Calculate reading progress based on scroll position
        val progress = if (contentHeight > 0) {
            (scrollY.toFloat() / contentHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
        } else 0f
        
        // Estimate character position based on scroll
        val content = _chapterContent.value
        characterPosition = (content.length * progress).toInt().coerceIn(0, content.length)
        
        // Update reading progress
        _readingProgress.value = _readingProgress.value.copy(
            progress = progress,
            scrollPosition = scrollY,
            shouldScrollTo = false
        )
        
        updateUiState()
    }

    fun seekToPosition(progressPercent: Float) {
        val contentItems = _contentItems.value
        val paragraphs = contentItems.filterIsInstance<TextItem.Paragraph>()
        
        if (paragraphs.isEmpty()) {
            android.util.Log.w("NovelReaderViewModel", "seekToPosition called with no paragraphs")
            return
        }
        
        // SIMPLE FIX: 100% always means absolute end of last paragraph
        if (progressPercent >= 1.0f) {
            val lastParagraph = paragraphs.last()
            characterPosition = lastParagraph.endCharIndex
            android.util.Log.d("NovelReaderViewModel", "Slider seek: 100% → absolute end (char $characterPosition)")
            
            _readingProgress.value = _readingProgress.value.copy(
                progress = 1.0f,
                scrollPosition = Int.MAX_VALUE, // Force scroll to bottom
                shouldScrollTo = true
            )
            updateUiState()
            return
        }
        
        // For 0-99%: Simple linear mapping by total character count
        val totalChars = paragraphs.last().endCharIndex
        characterPosition = (totalChars * progressPercent).toInt().coerceIn(0, totalChars)
        
        android.util.Log.d("NovelReaderViewModel", "Slider seek: ${(progressPercent * 100).toInt()}% → char $characterPosition / $totalChars")
        
        _readingProgress.value = _readingProgress.value.copy(
            progress = progressPercent,
            scrollPosition = (progressPercent * 1000).toInt(),
            shouldScrollTo = true
        )
        
        updateUiState()
    }

    /**
     * Phase 4: Update character position from RecyclerView scroll state.
     * Called by Activity when user scrolls through content.
     */
    fun updateCharacterPosition(newPosition: Int) {
        characterPosition = newPosition
    }

    /**
     * Phase 4: Get current character position for bookmark restoration.
     */
    fun getCurrentCharacterPosition(): Int = characterPosition

    /**
     * Reset the scroll restoration flag after scroll has been applied.
     * Called by Activity after successfully restoring scroll position.
     */
    fun clearScrollRestorationFlag() {
        _shouldScrollOnNextUpdate.value = false
        _readingProgress.value = _readingProgress.value.copy(shouldScrollTo = false)
    }

    /**
     * Phase 4.5: Convert character position to chunk-based position.
     * Maps global character position to specific (chapterId, chunkIndex, characterOffset).
     * 
     * @param characterPos Global character position within chapter
     * @param chapterId Chapter ID to find position within
     * @return Triple of (chapterId, chunkIndex, characterOffset) or null if position invalid
     */
    fun migrateReadingPosition(characterPos: Int, chapterId: Long): Triple<Long, Int, Int>? {
        val items = _contentItems.value
        
        // Find the paragraph (chunk) containing the character position
        val paragraph = items.filterIsInstance<TextItem.Paragraph>()
            .find { it.chapterId == chapterId && characterPos in it.startCharIndex..it.endCharIndex }
            ?: return null
        
        // Calculate offset within the paragraph/chunk
        val characterOffset = characterPos - paragraph.startCharIndex
        
        return Triple(chapterId, paragraph.paragraphIndex, characterOffset)
    }

    /**
     * Phase 4.5: Convert chunk-based position back to character position.
     * Maps (chapterId, chunkIndex, characterOffset) to global character position.
     * 
     * @param chapterId Chapter ID
     * @param chunkIndex Paragraph index within chapter
     * @param characterOffset Character offset within the paragraph
     * @return Character position within chapter or null if chunk not found
     */
    fun restoreCharacterPosition(chapterId: Long, chunkIndex: Int, characterOffset: Int): Int? {
        val items = _contentItems.value
        
        // Find the paragraph at the specified chunk index
        val paragraph = items.filterIsInstance<TextItem.Paragraph>()
            .find { it.chapterId == chapterId && it.paragraphIndex == chunkIndex }
            ?: return null
        
        // Calculate absolute character position
        val absolutePosition = paragraph.startCharIndex + characterOffset
        
        // Validate position is within paragraph bounds
        return if (absolutePosition in paragraph.startCharIndex..paragraph.endCharIndex) {
            absolutePosition
        } else {
            // If offset exceeds paragraph length, return end of paragraph
            paragraph.endCharIndex
        }
    }

    fun saveCurrentPosition() {
        val novel = _novel.value ?: return
        val chapter = _currentChapter.value ?: return

        android.util.Log.d("NovelReaderViewModel", "saveCurrentPosition: chapterId=${chapter.id}, charPos=$characterPosition")

        viewModelScope.launch {
            try {
                // Save character position and scroll position
                novelRepository.updateReadingProgress(chapter.id, characterPosition)
                android.util.Log.d("NovelReaderViewModel", "Saved reading position: chapterId=${chapter.id}, charPos=$characterPosition")

                // Calculate total characters from parsed text (NOT raw HTML length)
                val totalChars = _contentItems.value.filterIsInstance<TextItem.Paragraph>().lastOrNull()?.endCharIndex
                    ?: chapterCache.get(chapter.id)?.totalCharacters
                    ?: 0

                // Update reading time and persist total character count as wordCount
                // so the chapter list can show accurate reading percentage
                val readingTime = System.currentTimeMillis() - readingStartTime
                val updatedChapter = chapter.copy(
                    lastReadPosition = characterPosition,
                    readingTimeMs = chapter.readingTimeMs + readingTime,
                    wordCount = if (totalChars > 0) totalChars else chapter.wordCount
                )
                novelRepository.updateChapter(updatedChapter)

                // Only mark as read if user has scrolled past 95% of the chapter
                if (totalChars > 0 && characterPosition >= totalChars * 0.95) {
                    novelRepository.markChapterRead(chapter.id, true)
                    android.util.Log.d("NovelReaderViewModel", "Auto-marked chapter ${chapter.id} as read (progress >= 95%)")
                }

            } catch (e: Exception) {
                // Ignore save errors
                android.util.Log.e("NovelReaderViewModel", "Failed to save position", e)
            }
        }
    }

    fun updateReadingSession() {
        readingStartTime = System.currentTimeMillis()
    }

    /**
     * Parses HTML content into paragraph-level TextItem list.
     * Splits content by <p> tags and converts each to a Paragraph item using Markwon.
     * 
     * This enables RecyclerView-based rendering where each paragraph is a separate item,
     * creating natural spacing through RecyclerView item margins (8dp top/bottom padding).
     * 
     * MIGRATION NOTE: Changed from NovelContentItem to TextItem (Phase 3 - Option A)
     * 
     * @param html Raw HTML content from provider (may contain <p>, <br>, <b>, <i>, etc.)
     * @param chapterId Chapter identifier for the content
     * @return List of TextItem (primarily Paragraph items)
     */
    private fun parseHtmlToParagraphs(
        html: String,
        chapterId: Long
    ): List<TextItem> {
        val items = mutableListOf<TextItem>()
        
        try {
            android.util.Log.d("NovelReaderViewModel", "Parsing HTML to paragraphs...")
            android.util.Log.d("NovelReaderViewModel", "HTML length: ${html.length} chars")
            
            // Clean HTML with Jsoup
            val document = Jsoup.parse(html)
            document.select("style, script, noscript").remove() // Remove non-content elements
            
            // Extract paragraphs (select <p> tags)
            val paragraphs = document.select("p")
            android.util.Log.d("NovelReaderViewModel", "Found ${paragraphs.size} <p> tags")
            
            var charIndex = 0
            
            paragraphs.forEachIndexed { index, element ->
                val paragraphHtml = element.html()
                
                // Skip empty paragraphs
                if (paragraphHtml.isBlank()) {
                    android.util.Log.d("NovelReaderViewModel", "Skipping empty paragraph at index $index")
                    return@forEachIndexed
                }
                
                // Convert to Spanned using Markwon (preserves formatting: bold, italic, links, etc.)
                val spanned: Spanned = markwon.toMarkdown(paragraphHtml)
                
                val startChar = charIndex
                // FIX: endChar is INCLUSIVE (last character belongs to this paragraph)
                // Example: text.length=5 means chars 0,1,2,3,4 (endChar=4, not 5)
                val endChar = charIndex + spanned.length - 1
                
                // MIGRATION: Changed from NovelContentItem.Paragraph to TextItem.Paragraph
                items.add(
                    TextItem.Paragraph(
                        id = chapterId * 100000L + index,  // Unique ID for DiffUtil
                        text = spanned,
                        startCharIndex = startChar,
                        endCharIndex = endChar,
                        paragraphIndex = index,
                        chapterId = chapterId
                    )
                )
                
                // Next paragraph starts right after this one
                charIndex = endChar + 1
                
                if (index < 3) { // Log first 3 paragraphs for debugging
                    android.util.Log.d("NovelReaderViewModel", "Paragraph $index: ${spanned.toString().take(50)}...")
                }
            }
            
            android.util.Log.d("NovelReaderViewModel", "Parsed ${items.size} paragraphs successfully")
            
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderViewModel", "Error parsing HTML to paragraphs", e)
            // Return error item if parsing fails
            items.clear()
            items.add(
                TextItem.Error(
                    id = chapterId * 100000L - 3,  // Error ID convention
                    chapterId = chapterId,
                    errorMessage = "Failed to parse chapter content: ${e.message}",
                    canRetry = false
                )
            )
        }
        
        return items
    }

    /**
     * Wraps content items with navigation buttons and chapter header.
     * Adds Previous Chapter button at top, Next Chapter button at bottom.
     * 
     * @param contentItems Base content items (paragraphs)
     * @param chapterId Current chapter ID
     * @return Items with navigation injected
     */
    private fun wrapWithNavigation(contentItems: List<TextItem>, chapterId: Long): List<TextItem> {
        val result = mutableListOf<TextItem>()
        
        // BUG FIX: Look up chapter by chapterId parameter, NOT _currentChapter.value
        // During infinite scroll background loading, _currentChapter is not yet updated
        val chapter = _chapters.value.find { it.id == chapterId }
        
        // Get chapter index using the correct chapter object
        val chapterIndex = if (chapter != null) _chapters.value.indexOf(chapter) else -1
        val hasPrevious = chapterIndex > 0
        val hasNext = chapterIndex >= 0 && chapterIndex < _chapters.value.size - 1
        
        // HIDE navigation buttons in infinite scroll mode (user scrolls seamlessly across chapters)
        val showNavigationButtons = _readingMode.value != ReadingMode.INFINITE_SCROLL
        android.util.Log.d("NovelReaderViewModel", "wrapWithNavigation: chapterId=$chapterId, chapterTitle=${chapter?.title}, readingMode=${_readingMode.value}, showButtons=$showNavigationButtons")
        
        // Add Previous Chapter button at top ONLY if not first chapter AND not infinite scroll
        if (hasPrevious && showNavigationButtons) {
            result.add(
                TextItem.ChapterNavigation(
                    id = chapterId * 100000L - 10,  // Unique ID
                    direction = TextItem.LoadDirection.PREVIOUS,
                    chapterTitle = _chapters.value[chapterIndex - 1].title,
                    isEnabled = true
                )
            )
        }
        
        // Add chapter header with title and number
        if (chapter != null) {
            result.add(
                TextItem.ChapterHeader(
                    id = chapterId * 100000L - 1,  // Header ID convention
                    chapterId = chapterId,
                    chapterTitle = chapter.title,
                    chapterNumber = "Chapter ${chapterIndex + 1}"
                )
            )
        }
        
        // Add all content items (paragraphs)
        result.addAll(contentItems)
        
        // Add Comments button if source supports comments
        val provider = currentProvider
        if (provider?.capabilities?.supportsComments == true) {
            result.add(
                TextItem.CommentsButton(
                    id = chapterId * 100000L - 20,
                    chapterId = chapterId,
                    commentCount = 0
                )
            )
        }
        
        // Add Next Chapter button at bottom ONLY if not infinite scroll
        if (showNavigationButtons) {
            result.add(
                TextItem.ChapterNavigation(
                    id = chapterId * 100000L - 11,  // Unique ID
                    direction = TextItem.LoadDirection.NEXT,
                    chapterTitle = if (hasNext) _chapters.value[chapterIndex + 1].title else "",
                    isEnabled = hasNext
                )
            )
        }
        
        return result
    }

    /**
     * Fetch comments for a chapter.
     * @param chapterId The chapter ID to fetch comments for
     * @return List of comments, or empty list if not supported / error
     */
    suspend fun fetchComments(chapterId: Long): List<yokai.source.novel.model.NovelComment> {
        val chapter = _chapters.value.find { it.id == chapterId } ?: return emptyList()
        val provider = currentProvider ?: return emptyList()
        return try {
            provider.getChapterComments(chapter.url)
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderViewModel", "Error fetching comments: ${e.message}")
            emptyList()
        }
    }

    /**
     * Phase 4.5: Validate position tracking accuracy.
     * Tests round-trip conversion: charPos → (chunk, offset) → charPos
     * Target accuracy: ±10 characters as specified in Phase 4.5 requirements.
     * 
     * @param chapterId Chapter to validate
     * @return Pair of (testsPassed, maxError) or null if no items to test
     */
    fun validatePositionTracking(chapterId: Long): Pair<Int, Int>? {
        val paragraphs = _contentItems.value.filterIsInstance<TextItem.Paragraph>()
            .filter { it.chapterId == chapterId }
        
        if (paragraphs.isEmpty()) {
            android.util.Log.w("NovelReaderViewModel", "No paragraphs to validate for chapter $chapterId")
            return null
        }
        
        var testsPassed = 0
        var maxError = 0
        val testPoints = mutableListOf<Int>()
        
        // Test at paragraph boundaries and midpoints
        for (paragraph in paragraphs) {
            testPoints.add(paragraph.startCharIndex)
            testPoints.add((paragraph.startCharIndex + paragraph.endCharIndex) / 2)
            testPoints.add(paragraph.endCharIndex)
        }
        
        for (originalPos in testPoints) {
            // Convert to chunk-based
            val chunkData = migrateReadingPosition(originalPos, chapterId)
            if (chunkData == null) {
                android.util.Log.w("NovelReaderViewModel", "Failed to migrate position $originalPos")
                continue
            }
            
            val (_, chunkIndex, characterOffset) = chunkData
            
            // Convert back to character position
            val restoredPos = restoreCharacterPosition(chapterId, chunkIndex, characterOffset)
            if (restoredPos == null) {
                android.util.Log.w("NovelReaderViewModel", "Failed to restore chunk $chunkIndex offset $characterOffset")
                continue
            }
            
            // Calculate error
            val error = kotlin.math.abs(restoredPos - originalPos)
            maxError = maxOf(maxError, error)
            
            if (error <= 10) {
                testsPassed++
            } else {
                android.util.Log.w("NovelReaderViewModel", 
                    "Position error ${error}chars: $originalPos → ($chunkIndex, $characterOffset) → $restoredPos")
            }
        }
        
        val totalTests = testPoints.size
        android.util.Log.d("NovelReaderViewModel", 
            "Position tracking validation: $testsPassed/$totalTests passed, max error: ${maxError}chars")
        
        return Pair(testsPassed, maxError)
    }

    private fun updateUiState() {
        val chapters = _chapters.value
        val currentChapter = _currentChapter.value
        val progress = _readingProgress.value.progress
        
        val chapterIndex = currentChapter?.let { chapters.indexOf(it) } ?: 0
        val totalChapters = chapters.size
        
        // Calculate estimated reading time
        val content = _chapterContent.value
        val wordsPerMinute = 200 // Average reading speed
        val wordCount = content.split("\\s+".toRegex()).size
        val estimatedMinutes = ((wordCount * (1 - progress)) / wordsPerMinute).toInt()
        
        _uiState.value = NovelReaderUiState(
            hasPreviousChapter = chapterIndex > 0,
            hasNextChapter = chapterIndex < totalChapters - 1,
            isBookmarked = currentChapter?.bookmark ?: false,
            readingProgress = progress,
            showReadingProgress = true,
            progressText = "Chapter ${chapterIndex + 1} of $totalChapters • ${(progress * 100).toInt()}% complete",
            estimatedTimeText = if (estimatedMinutes > 0) "${estimatedMinutes}m left" else "Done"
        )
    }
}

/**
 * UI state for the novel reader
 */
data class NovelReaderUiState(
    val hasPreviousChapter: Boolean = false,
    val hasNextChapter: Boolean = false,
    val isBookmarked: Boolean = false,
    val readingProgress: Float = 0f,
    val showReadingProgress: Boolean = false,
    val progressText: String = "",
    val estimatedTimeText: String = ""
)

/**
 * Reading progress tracking with character-level positions
 */
data class NovelReadingProgress(
    val progress: Float = 0f,
    val scrollPosition: Int = 0,
    val shouldScrollTo: Boolean = false
)

/**
 * Events for one-time UI updates
 */
sealed class NovelReaderEvent {
    data class ShowError(val message: String) : NovelReaderEvent()
    data class ShowMessage(val message: String) : NovelReaderEvent()
    data class ChapterChanged(val chapterTitle: String) : NovelReaderEvent()
    data class BookmarkToggled(val isBookmarked: Boolean) : NovelReaderEvent()
    /**
     * Signal to scroll RecyclerView to specific position after chapter navigation.
     * @param position 0 = scroll to top (next chapter), -1 = scroll to bottom (previous chapter)
     */
    data class ScrollToPosition(val position: Int) : NovelReaderEvent()
}