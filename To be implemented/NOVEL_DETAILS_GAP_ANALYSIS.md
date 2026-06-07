# Novel Details Implementation - Comprehensive Gap Analysis

**Generated:** 2024-01-XX  
**Current State:** Visual layout complete with rich header, but zero functionality  
**Goal:** Achieve 100% functional parity with MangaDetailsController

---

## 🔍 EXECUTIVE SUMMARY

### What Works ✅
- Novel details screen displays without crashing
- Rich visual layout (428-line `novel_header_item.xml`) matches manga aesthetics
- View binding correctly references all UI elements (ImageView fix applied)
- Mode Inheritance System routes novels correctly
- Browse → Details navigation functional

### What's Missing ❌
1. **No chapter display system** - RecyclerView empty, no adapter/holder/items
2. **All buttons non-functional** - Placeholders with no real handlers
3. **No presenter functionality** - Stub only, missing all business logic
4. **FABs still present** - Should be removed per user requirement
5. **Backdrop doesn't extend to status bar** - Cuts off at top
6. **Title visible in app bar** - Should be hidden like manga
7. **No backend integration** - Database, downloads, tracking, history all missing

---

## 📋 FILES TO DUPLICATE FROM MANGA → NOVEL

### 1. CHAPTER DISPLAY INFRASTRUCTURE

#### **NovelChapterItem.kt** (Duplicate from `ChapterItem.kt`)
**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/manga/chapter/ChapterItem.kt`  
**Purpose:** FlexibleAdapter item representing a single novel chapter in the list  
**Key Elements:**
```kotlin
class ChapterItem(val chapter: Chapter, val manga: Manga) : 
    AbstractSectionableItem<ChapterHolder, ChapterHeaderItem>() {
    
    var status: Download.State = Download.State.NOT_DOWNLOADED
    val chapter_number get() = chapter.chapter_number
    val id get() = chapter.id
    val isDownloaded get() = status == Download.State.DOWNLOADED
    val isLocked: Boolean // Based on manga.hideChapterTitles
    
    override fun getLayoutRes(): Int = R.layout.chapters_item
    override fun isSelectable(): Boolean = true
    override fun isSwipeable(): Boolean = !isLocked && preferences.enableChapterSwipeAction()
    override fun createViewHolder() // Returns ChapterHolder
    override fun bindViewHolder() // Calls holder.bind()
}
```
**Novel Adaptation:**
- Replace `Chapter` with `NovelChapter` model
- Replace `Manga` with `Novel` model
- Keep download status logic (novels should support chapter downloads)
- Adapt `isLocked` for novel-specific privacy
- Use novel-specific layout (`novel_chapter_item.xml`)

---

#### **NovelChapterHolder.kt** (Duplicate from `ChapterHolder.kt`)
**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/manga/chapter/ChapterHolder.kt`  
**Purpose:** ViewHolder displaying chapter UI and handling interactions  
**Key Elements:**
```kotlin
class ChapterHolder(view: View, private val adapter: MangaDetailsAdapter) : 
    BaseChapterHolder(view, adapter) {
    
    private val binding = ChaptersItemBinding.bind(view)
    
    fun bind(item: ChapterItem, manga: Manga) {
        binding.chapterTitle.text = chapter.preferredChapterName()
        binding.downloadButton.downloadButton.isVisible = !manga.isLocal()
        
        // Chapter status indicators
        val chapterColor = ChapterUtil.chapterColor(context, chapter)
        val readColor = if (chapter.read) readTextColor else unreadTextColor
        
        // Download status
        notifyStatus(item.status, item.progress)
        
        // Read/bookmark indicators
        binding.bookmarkButton.isVisible = chapter.bookmark
        
        // Click listeners
        itemView.setOnClickListener { adapter.delegate.onItemClick(flexibleAdapterPosition) }
        itemView.setOnLongClickListener { 
            adapter.delegate.onItemLongClick(flexibleAdapterPosition)
        }
        binding.downloadButton.setOnLongClickListener {
            adapter.delegate.startDownloadRange(flexibleAdapterPosition)
        }
    }
    
    fun notifyStatus(status: Download.State, progress: Int) // Update download UI
}
```
**Novel Adaptation:**
- Use `NovelChapter` and `Novel` models
- Replace `ChaptersItemBinding` with `NovelChapterItemBinding`
- Adapt download button visibility (novels may not support images)
- Keep read/bookmark indicators (novels need progress tracking)
- Route clicks to `NovelDetailsAdapter` delegate
- Consider progress bars for text download status

---

#### **NovelDetailsAdapter.kt** (Duplicate from `MangaDetailsAdapter.kt`)
**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsAdapter.kt`  
**Purpose:** FlexibleAdapter managing chapter list with filtering/sorting  
**Key Elements:**
```kotlin
class MangaDetailsAdapter(val controller: MangaDetailsController) :
    BaseChapterAdapter<IFlexible<*>>(controller) {
    
    val delegate: MangaDetailsInterface = controller
    val presenter = controller.presenter
    
    var items: List<ChapterItem> = emptyList()
    
    fun setChapters(items: List<ChapterItem>?) {
        this.items = items ?: emptyList()
        performFilter()
    }
    
    fun performFilter() {
        val s = getFilter(String::class.java)
        if (s.isNullOrBlank()) {
            updateDataSet(items)
        } else {
            updateDataSet(items.filter { it.name.contains(s, true) })
        }
    }
    
    override fun onItemSwiped(position: Int, direction: Int) {
        when (direction) {
            ItemTouchHelper.RIGHT -> controller.bookmarkChapter(position)
            ItemTouchHelper.LEFT -> controller.toggleReadChapter(position)
        }
    }
    
    override fun onCreateBubbleText(position: Int): String {
        // Fast scroller bubble text (chapter numbers/volumes)
    }
}

interface MangaDetailsInterface : MangaHeaderInterface, DownloadInterface {
    fun updateHeader()
    fun updateChapters()
}
```
**Novel Adaptation:**
- Replace `MangaDetailsController` with `NovelDetailsController`
- Replace `ChapterItem` with `NovelChapterItem`
- Keep swipe actions for bookmark/read (novels need same UX)
- Adapt bubble text for novel chapter numbering
- Create `NovelDetailsInterface` with novel-specific methods
- Keep filter/search functionality

---

#### **BaseChapterAdapter.kt** (May need novel variant)
**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/manga/chapter/BaseChapterAdapter.kt`  
**Purpose:** Base FlexibleAdapter with download interface  
**Status:** Potentially reusable as-is, but verify download integration works for novels

---

### 2. LAYOUT FILES

#### **novel_chapter_item.xml** (Duplicate from `chapters_item.xml`)
**Source:** `app/src/main/res/layout/chapters_item.xml`  
**Purpose:** Individual chapter row layout  
**Key Elements:**
- Chapter title TextView
- Read/unread indicator
- Download button with progress
- Bookmark icon
- Background color for selection state
- Scanlator info (adapt for novel translators?)

**Novel Adaptation:**
- Replace "Chapter X" with novel-appropriate format
- Consider adding "Progress: 45%" for partially read chapters
- Adapt download icon (novels are text, not images)
- Keep bookmark, read indicators
- May need "word count" or "reading time" labels

---

#### **chapter_header_item.xml** (May need novel variant)
**Source:** `app/src/main/res/layout/chapter_header_item.xml`  
**Purpose:** Header for chapter list section (tablet mode)  
**Status:** Check if needed for novel tablet layout

---

### 3. PRESENTER BUSINESS LOGIC

#### **NovelDetailsPresenter.kt** - MASSIVE EXPANSION NEEDED
**Current State:** 209-line stub with basic structure  
**Target State:** ~1146 lines matching `MangaDetailsPresenter.kt` functionality

**Missing Core Methods (from MangaDetailsPresenter):**

##### A. Initialization & Lifecycle
```kotlin
fun onCreate() // LibraryUpdateJob.updateFlow listener, fetch manga/chapters
fun onCreateLate() // UI-ready initialization
fun onDestroy() // Cleanup listeners
fun syncData() // Sync chapter sort, header state
```

##### B. Chapter Management
```kotlin
suspend fun getChapters(queue: List<Download>) // Load chapters with download status
fun getChaptersNow(): List<ChapterItem> // Blocking chapter fetch
fun fetchChapters(andTracking: Boolean = true) // Refresh from DB
suspend fun fetchChaptersFromSource(manualFetch: Boolean) // Network fetch
fun refreshAll() // Fetch manga + chapters from source
fun getNextUnreadChapter(): ChapterItem? // For "Start Reading" button
```

##### C. Chapter Actions
```kotlin
fun markChaptersRead(chapters: List<ChapterItem>, read: Boolean)
fun bookmarkChapters(chapters: List<ChapterItem>, bookmarked: Boolean)
fun downloadChapters(chapters: List<ChapterItem>)
fun deleteChapters(chapters: List<ChapterItem>, update: Boolean, isEverything: Boolean)
```

##### D. Filtering & Sorting
```kotlin
fun currentFilters(): String // Display active filters (read/unread/downloaded/bookmarked)
fun setReadFilter(state: TriStateCheckBox.State)
fun setDownloadedFilter(state: TriStateCheckBox.State)
fun setBookmarkedFilter(state: TriStateCheckBox.State)
fun setScanlatorFilter(filteredScanlators: Set<String>) // Adapt for translators
fun setSortOrder(sort: Int, descending: Boolean)
```

##### E. Library & Categories
```kotlin
fun getCategories(): List<Category>
fun moveMangaToCategory(categories: List<Category>)
fun confirmDeletion() // Remove from library, delete downloads
```

##### F. Tracking Integration
```kotlin
fun fetchTracks() // Load tracking from services (MAL, AniList, etc.)
fun refreshTracking(showOfflineSnack: Boolean)
fun isTracked(): Boolean
fun hasTrackers(): Boolean
fun registerTracking(item: TrackItem, service: TrackService)
fun setTrackerScore(item: TrackItem, score: Int)
fun setTrackerStatus(item: TrackItem, index: Int)
fun setTrackerChapters(item: TrackItem, chapters: Int)
fun setTrackerStartDate(item: TrackItem, date: Long)
fun setTrackerFinishDate(item: TrackItem, date: Long)
suspend fun getSuggestedDate(readingDate: ReadingDate): Long?
```

##### G. History & Progress
```kotlin
private suspend fun getHistory() // Load reading history
```

##### H. Manga/Cover Management
```kotlin
suspend fun refreshMangaFromDb(): Manga
private suspend fun fetchMangaFromSource() // Network fetch manga details
fun updateManga(title, author, artist, uri, description, tags, status)
fun shareManga() // Share cover image
private fun saveCover(directory: UniFile): UniFile // Save cover to file
```

##### I. Download Queue Integration
```kotlin
override fun onStatusChange(download: Download) // DownloadQueue.Listener
override fun onProgressChange(download: Download)
override fun onPageProgressChange(download: Download)
```

##### J. Custom Manga Info
```kotlin
fun getChapterUrl(chapter: Chapter): String?
suspend fun customMangaFromShareIntent(uri: Uri, manga: Manga): Manga?
```

**Novel Adaptations Needed:**
- Replace all `Manga` with `Novel`
- Replace all `Chapter` with `NovelChapter`
- Replace `source.getChapterList()` with novel provider APIs
- Adapt download logic for text content (not images)
- Keep tracking integration (novels should track on same services)
- Adapt history to track character position, not page numbers
- Replace cover sharing with novel info sharing

---

### 4. CONTROLLER FUNCTIONALITY

#### **NovelDetailsController.kt** - MASSIVE EXPANSION NEEDED
**Current State:** 299 lines with placeholder button handlers  
**Target State:** ~2013 lines matching `MangaDetailsController.kt`

**Missing Core Methods (from MangaDetailsController):**

##### A. View Setup
```kotlin
fun setRecycler(view: View) // Configure RecyclerView with adapter
fun setTabletMode(view: View) // Two-pane layout for tablets
fun setPaletteColor() // Dynamic theming from cover
fun setStatusBarAndToolbar() // Status bar color management
```

##### B. Header Management
```kotlin
fun updateHeader() // Refresh header views with novel data
fun addMangaHeader() // Add/update header in adapter
fun getHeader(): MangaHeaderHolder? // Get header holder for updates
fun colorToolbar(isColor: Boolean) // Dynamic toolbar theming
fun updateToolbarTitleAlpha(@FloatRange alpha: Float)
```

##### C. Chapter Management
```kotlin
fun updateChapters() // Refresh chapter list from presenter
fun refreshAdapter() // Notify adapter of changes
```

##### D. FAB Management
```kotlin
override fun configureFab(fab: ExtendedFloatingActionButton) // FAB visibility/text
override fun cleanupFab(fab: ExtendedFloatingActionButton)
```

##### E. Tracking
```kotlin
fun showTrackingSheet() // Open tracking bottom sheet
fun refreshTracking(trackings: List<TrackItem>)
fun onTrackSearchResults(results: List<TrackSearch>)
fun refreshTracker()
fun trackRefreshDone()
fun trackRefreshError(error: Exception)
fun trackSearchError(error: Exception)
```

##### F. Chapter Actions (Interface Implementation)
```kotlin
fun onItemClick(view: View?, position: Int): Boolean
fun onItemLongClick(position: Int)
fun bookmarkChapter(position: Int)
fun toggleReadChapter(position: Int)
fun downloadChapter(position: Int)
fun startDownloadNow(position: Int)
fun startDownloadRange(position: Int)
```

##### G. Manga Actions
```kotlin
fun favoriteManga(longPress: Boolean)
fun showCategoriesSheet()
fun toggleMangaFavorite()
fun makeFavPopup(popupView: View, categories: List<Category>): PopupMenu?
fun setFavButtonPopup(popupView: View)
```

##### H. Sharing & Web
```kotlin
fun prepareToShareManga()
fun shareManga(cover: File)
fun openInWebView()
fun openChapterInWebView(item: ChapterItem)
```

##### I. Filtering & Sorting
```kotlin
fun showChapterFilter() // Open filter bottom sheet
```

##### J. Reader Integration
```kotlin
fun readNextChapter(readingButton: View) // Open reader with next unread
private fun openChapter(chapter: Chapter, sharedElement: View?)
```

##### K. Deletion & Mass Operations
```kotlin
private fun massDeleteChapters(choice: Int)
fun showDeleteChapters()
```

##### L. Action Mode (Multi-Select)
```kotlin
private fun createActionModeIfNeeded()
private fun destroyActionModeIfNeeded()
override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean
override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean
override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean
override fun onDestroyActionMode(mode: ActionMode?)
```

##### M. Search Integration
```kotlin
fun localSearch(text: String, isTag: Boolean) // Search novels by tag
```

##### N. Cover Zoom
```kotlin
fun zoomImageFromThumb(thumbView: View) // Full-screen cover view
private fun showFullCoverDialog()
```

##### O. Floating Action Mode
```kotlin
fun showFloatingActionMode(view: TextView, content: String?, isTag: Boolean)
fun customActionMode(view: TextView): ActionMode.Callback
fun copyContentToClipboard(content: String, label: StringResource, useToast: Boolean)
```

##### P. Smart Search (Migration)
```kotlin
private fun performGlobalSearch(title: String) // Migrate to another source
```

##### Q. Snackbar Management
```kotlin
private fun showAddedSnack() // Show "Added to library" snackbar
```

**Novel Adaptations Needed:**
- Replace manga-specific terminology in UI strings
- Adapt reader opening to `NovelReaderActivity`
- Keep download/tracking/library logic (novels use same infrastructure)
- Adapt chapter selection for text-based content
- Keep action mode for bulk operations

---

### 5. INTERFACE FILES

#### **NovelHeaderInterface** (Duplicate pattern from `MangaHeaderInterface`)
**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsAdapter.kt` (lines 133-155)  
**Purpose:** Interface defining header-controller interactions  
**Required Methods:**
```kotlin
interface NovelHeaderInterface {
    fun coverColor(): Int?
    fun accentColor(): Int?
    fun novelPresenter(): NovelDetailsPresenter // Changed from mangaPresenter()
    fun prepareToShareNovel() // Changed from prepareToShareManga()
    fun openInWebView()
    fun startDownloadRange(position: Int)
    fun readNextChapter(readingButton: View)
    fun topCoverHeight(): Int
    fun showFloatingActionMode(view: TextView, content: String?, isTag: Boolean)
    fun showChapterFilter()
    fun favoriteNovel(longPress: Boolean) // Changed from favoriteManga()
    fun copyContentToClipboard(content: String, label: StringResource, useToast: Boolean)
    fun copyContentToClipboard(content: String, label: Int, useToast: Boolean)
    fun customActionMode(view: TextView): ActionMode.Callback
    fun copyContentToClipboard(content: String, label: String?, useToast: Boolean)
    fun zoomImageFromThumb(thumbView: View)
    fun showTrackingSheet()
    fun updateScroll()
    fun setFavButtonPopup(popupView: View)
}
```

#### **NovelDetailsInterface** (Extension of NovelHeaderInterface)
```kotlin
interface NovelDetailsInterface : NovelHeaderInterface, DownloadInterface {
    fun updateHeader()
    fun updateChapters()
}
```

---

### 6. ITEM FILES

#### **NovelHeaderItem.kt** (Duplicate from `MangaHeaderItem.kt`)
**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaHeaderItem.kt`  
**Purpose:** FlexibleAdapter item for novel header  
**Key Elements:**
```kotlin
class MangaHeaderItem(val mangaId: Long, private var startExpanded: Boolean) :
    AbstractFlexibleItem<MangaHeaderHolder>() {
    
    var isChapterHeader = false // For tablet mode
    var isLocked = false // Privacy lock state
    var isTablet = false
    
    override fun getLayoutRes(): Int {
        return if (isChapterHeader) {
            R.layout.chapter_header_item
        } else {
            R.layout.manga_header_item
        }
    }
    
    override fun isSelectable(): Boolean = false
    override fun isSwipeable(): Boolean = false
    
    override fun createViewHolder() // Returns MangaHeaderHolder
    override fun bindViewHolder() // Calls holder.bind() or holder.bindChapters()
}
```
**Novel Adaptation:**
- Replace `mangaId: Long` with `novelId: Long`
- Replace `MangaHeaderHolder` with `NovelHeaderHolder`
- Change layout res to `R.layout.novel_header_item`
- Keep `isChapterHeader` for tablet mode
- Keep `isLocked` for privacy

---

#### **NovelHeaderHolder.kt** (Duplicate from `MangaHeaderHolder.kt`)
**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaHeaderHolder.kt` (727 lines!)  
**Purpose:** Massive ViewHolder managing entire novel header UI  
**Key Elements:**
```kotlin
class MangaHeaderHolder(
    view: View,
    private val adapter: MangaDetailsAdapter,
    startExpanded: Boolean,
    private val isTablet: Boolean = false
) : BaseFlexibleViewHolder(view, adapter) {
    
    val binding: MangaHeaderItemBinding? // Full header
    private val chapterBinding: ChapterHeaderItemBinding? // Tablet chapter header
    
    // State
    private var showReadingButton = true
    private var showMoreButton = true
    var hadSelection = false
    private var canCollapse = true
    
    init {
        // Setup all button click listeners
        binding.startReadingButton.setOnClickListener { adapter.delegate.readNextChapter(it) }
        binding.moreButton.setOnClickListener { expandDesc(true) }
        binding.lessButton.setOnClickListener { collapseDesc(true) }
        binding.webviewButton.setOnClickListener { adapter.delegate.openInWebView() }
        binding.shareButton.setOnClickListener { adapter.delegate.prepareToShareManga() }
        binding.favoriteButton.setOnClickListener { adapter.delegate.favoriteManga(false) }
        binding.favoriteButton.setOnLongClickListener { adapter.delegate.favoriteManga(true); true }
        binding.title.setOnClickListener { /* Floating action mode */ }
        binding.title.setOnLongClickListener { /* Copy to clipboard */ }
        binding.mangaAuthor.setOnClickListener { /* Floating action mode */ }
        binding.mangaAuthor.setOnLongClickListener { /* Copy to clipboard */ }
        binding.mangaCover.setOnClickListener { adapter.delegate.zoomImageFromThumb(coverCard) }
        binding.trackButton.setOnClickListener { adapter.delegate.showTrackingSheet() }
    }
    
    fun bind(item: MangaHeaderItem) {
        val presenter = adapter.delegate.mangaPresenter()
        val manga = presenter.manga
        
        // Populate all fields
        binding.title.text = manga.title
        setGenreTags(binding, manga)
        binding.mangaAuthor.text = manga.author
        setDescription()
        
        // Configure buttons
        binding.favoriteButton.icon = if (manga.favorite) heartIcon else heartOutlineIcon
        binding.favoriteButton.text = if (manga.favorite) "In Library" else "Add to Library"
        binding.trackButton.isVisible = presenter.hasTrackers()
        binding.trackButton.icon = if (presenter.isTracked()) checkIcon else syncIcon
        binding.startReadingButton.isVisible = presenter.chapters.isNotEmpty()
        binding.startReadingButton.isEnabled = presenter.getNextUnreadChapter() != null
        
        // Load cover
        binding.mangaCover.loadManga(manga)
        binding.backdrop.loadManga(manga)
        
        // Set colors
        binding.trueBackdrop.setBackgroundColor(adapter.delegate.coverColor())
    }
    
    fun bindChapters() {
        // Update chapter count and filter text
        val count = adapter.delegate.mangaPresenter().chapters.size
        binding.chaptersTitle.text = "$count Chapters"
        binding.filtersText.text = presenter.currentFilters()
    }
    
    private fun expandDesc(animated: Boolean = false) // Expand description
    private fun collapseDesc(animated: Boolean = false) // Collapse description
    private fun setDescription() // Populate description text
    private fun setGenreTags(binding: MangaHeaderItemBinding, manga: Manga) // Add genre chips
}
```
**Novel Adaptation:**
- Replace all `MangaDetailsAdapter` with `NovelDetailsAdapter`
- Replace all `Manga` references with `Novel`
- Replace `MangaHeaderItemBinding` with `NovelHeaderItemBinding`
- Change button handler calls to novel-specific methods
- Adapt cover loading for novel covers (may be different aspect ratio)
- Keep all expansion/collapse/genre logic
- Adapt "Start Reading" button text to "Start Reading" or "Continue Reading"
- Keep tracking integration
- Update strings to novel-appropriate text

---

### 7. UTILITIES & HELPERS

#### **ChapterUtil** Methods
**Source:** `app/src/main/java/eu/kanade/tachiyomi/util/chapter/ChapterUtil.kt`  
**Novel Equivalent:** `NovelChapterUtil` (may need adaptation)  
**Key Methods:**
- `preferredChapterName()` - Format chapter display name
- `chapterColor()` - Read/unread color coding
- `relativeDate()` - "3 days ago" formatting
- `getGroupNumber()` - Volume/season grouping

**Novel Adaptations:**
- Adapt chapter naming for novel conventions (Volume X, Chapter Y)
- Keep color coding for read/unread
- Keep relative dates
- Adapt grouping for novel structure

---

### 8. BOTTOM SHEETS

#### **ChaptersSortBottomSheet** → **NovelChaptersSortBottomSheet**
**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/manga/chapter/ChaptersSortBottomSheet.kt`  
**Purpose:** Filter/sort dialog for chapters  
**Key Features:**
- Read/Unread filter
- Downloaded/Not Downloaded filter
- Bookmarked/Not Bookmarked filter
- Scanlator filter (adapt to translator filter for novels)
- Sort by source/chapter number
- Ascending/descending toggle

**Novel Adaptation:**
- Keep all filter types
- Replace "Scanlator" with "Translator" or "Source"
- Keep sort options
- Update strings to novel-appropriate text

---

#### **TrackingBottomSheet** (Reusable?)
**Source:** `app/src/main/java/eu/kanade/tachiyomi/ui/manga/track/TrackingBottomSheet.kt`  
**Status:** May be reusable as-is if tracking services support novels  
**Check:** Does MAL/AniList/etc. track novels or just manga/anime?

---

### 9. MISSING UI FIXES (User-Identified Gaps)

#### **A. Remove Bottom FABs**
**Files to Modify:**
- `app/src/main/res/layout/novel_details_controller_rich.xml`

**Changes:**
```xml
<!-- DELETE these FABs -->
<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    android:id="@+id/fab_read_next" ... />

<com.google.android.material.floatingactionbutton.FloatingActionButton
    android:id="@+id/fab_favorite" ... />
```

**Reason:** User specified these should be removed - functionality should be in header buttons instead

---

#### **B. Extend Backdrop to Top of Screen**
**Files to Modify:**
- `app/src/main/res/layout/novel_header_item.xml`
- `NovelHeaderHolder.kt`

**Current Issue:** Backdrop cuts off at status bar  
**Solution:** 
```kotlin
// In NovelHeaderHolder or NovelDetailsController
binding.backdrop.updateLayoutParams<ConstraintLayout.LayoutParams> {
    // Extend to include status bar height
    topMargin = -(view.rootWindowInsetsCompat?.getInsets(systemBars())?.top ?: 0)
}
```

**Reference:** See how `MangaHeaderHolder.kt` handles `topView` height calculation with `topCoverHeight()`

---

#### **C. Hide Title from App Bar**
**Files to Modify:**
- `NovelDetailsController.kt`

**Current Issue:** Title visible in toolbar  
**Solution:**
```kotlin
override fun getTitle(): String? {
    // Return null to hide title when scrolled to top
    // Return novel title when scrolled down (like manga)
    return if (binding.recyclerView.canScrollVertically(-1)) {
        presenter.novel?.title
    } else {
        null
    }
}

// Also need scroll listener to update title visibility
private fun setupScrollListener() {
    binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            updateToolbarTitleAlpha(if (recyclerView.canScrollVertically(-1)) 1f else 0f)
        }
    })
}
```

**Reference:** `MangaDetailsController.kt` lines 700-750 for scroll handling

---

#### **D. Wire Up Action Buttons**

**Files to Modify:**
- `NovelDetailsController.kt`
- `NovelHeaderHolder.kt`
- `NovelDetailsPresenter.kt`

**Buttons to Wire:**

1. **Add to Library / In Library Button**
   - Click: Toggle favorite (add/remove from library)
   - Long click: Show category selection
   - Must update icon/text based on state
   - Reference: `MangaDetailsController.favoriteManga()` (line 1588)

2. **Tracking Button**
   - Click: Open tracking bottom sheet
   - Show sync icon when not tracked, check icon when tracked
   - Reference: `MangaDetailsController.showTrackingSheet()` (line 1727)

3. **More Button (Expand Description)**
   - Click: Expand description with animation
   - Reference: `MangaHeaderHolder.expandDesc()` (line 194)

4. **Start Reading Button**
   - Click: Open next unread chapter in reader
   - Disable if no unread chapters
   - Update text to "Continue" or "Start" based on progress
   - Reference: `MangaDetailsController.readNextChapter()` (line 1468)

5. **Filter Button**
   - Click: Open filter/sort bottom sheet
   - Reference: `MangaDetailsController.showChapterFilter()` (line 1584)

6. **Web Button**
   - Click: Open novel source in WebView
   - Reference: `MangaDetailsController.openInWebView()` (line 1270)

7. **Share Button**
   - Click: Share novel info/cover
   - Reference: `MangaDetailsController.prepareToShareManga()` (line 1217)

---

#### **E. Populate Genre Chips**
**Files to Modify:**
- `NovelHeaderHolder.kt`

**Current Issue:** ChipGroup empty  
**Solution:**
```kotlin
private fun setGenreTags(binding: NovelHeaderItemBinding, novel: Novel) {
    binding.novelGenresTags.removeAllViews()
    novel.genre?.split(",")?.map { it.trim() }?.forEach { tag ->
        val chip = Chip(binding.root.context).apply {
            text = tag
            setOnClickListener { 
                adapter.delegate.localSearch(tag, isTag = true)
            }
        }
        binding.novelGenresTags.addView(chip)
    }
}
```

**Reference:** `MangaHeaderHolder.kt` (search for `setGenreTags`)

---

## 📊 ESTIMATED FILE COUNT & EFFORT

### New Files to Create: **~15-20 files**
1. NovelChapterItem.kt
2. NovelChapterHolder.kt
3. NovelDetailsAdapter.kt
4. NovelHeaderItem.kt
5. NovelHeaderHolder.kt (727 lines!)
6. NovelChapterUtil.kt
7. NovelChaptersSortBottomSheet.kt
8. novel_chapter_item.xml
9. NovelDetailsInterface (in adapter file)
10. NovelHeaderInterface (in adapter file)

### Files to Heavily Modify: **~3 files**
1. NovelDetailsController.kt (~300 → ~2000 lines)
2. NovelDetailsPresenter.kt (~200 → ~1100 lines)
3. novel_header_item.xml (minor tweaks for backdrop extension)

### Files to Slightly Modify: **~2 files**
1. novel_details_controller_rich.xml (remove FABs)
2. NovelDetailsControllerBinding.kt (remove FAB references)

---

## 🎯 IMPLEMENTATION PRIORITY

### Phase 1: Core Chapter Display (HIGH PRIORITY)
**Goal:** Show chapters in RecyclerView  
**Files:**
1. NovelChapterItem.kt
2. NovelChapterHolder.kt
3. NovelDetailsAdapter.kt
4. novel_chapter_item.xml
5. Update NovelDetailsController to create adapter
6. Update NovelDetailsPresenter.loadChapters() to populate adapter

**Deliverable:** Chapter list visible with titles, read/unread indicators

---

### Phase 2: Basic Interactions (MEDIUM PRIORITY)
**Goal:** Chapters clickable, navigate to reader  
**Files:**
1. Wire NovelChapterHolder click listeners
2. Implement NovelDetailsInterface
3. Add NovelDetailsController.onItemClick()
4. Add NovelDetailsController.onItemLongClick()

**Deliverable:** Can click chapters to open reader

---

### Phase 3: Header Buttons (MEDIUM PRIORITY)
**Goal:** All header buttons functional  
**Files:**
1. NovelHeaderHolder.kt (wire button listeners)
2. NovelHeaderInterface
3. NovelDetailsController button handler methods
4. NovelDetailsPresenter action methods

**Deliverable:** Can favorite, track, share, filter, read next

---

### Phase 4: Filtering & Sorting (MEDIUM PRIORITY)
**Goal:** Filter/sort chapters like manga  
**Files:**
1. NovelChaptersSortBottomSheet.kt
2. NovelDetailsAdapter filtering logic
3. NovelDetailsPresenter filter state management

**Deliverable:** Can filter read/unread/downloaded/bookmarked chapters

---

### Phase 5: Download Integration (LOW PRIORITY)
**Goal:** Download novel chapters for offline reading  
**Files:**
1. Update NovelChapterHolder download button
2. Connect to DownloadManager
3. NovelDetailsPresenter download methods

**Deliverable:** Can download/delete novel chapters

---

### Phase 6: Tracking Integration (LOW PRIORITY)
**Goal:** Track novel progress on MAL/AniList/etc.  
**Files:**
1. TrackingBottomSheet integration (may work as-is)
2. NovelDetailsPresenter tracking methods
3. Test with tracking services

**Deliverable:** Can track novel reading progress

---

### Phase 7: Polish & UI Fixes (LOW PRIORITY)
**Goal:** Fix visual gaps identified by user  
**Files:**
1. Add search, download, three-dot icons and their functionalities
2. Extend backdrop to status bar
3. Hide title in app bar when at top
4. Populate genre chips

**Deliverable:** 100% visual parity with manga details

---

## 🔗 KEY DEPENDENCIES

### External Classes to Understand:
1. **FlexibleAdapter** - Third-party RecyclerView adapter framework
2. **BaseChapterAdapter** - Base adapter with download interface
3. **DownloadManager** - Handles chapter downloads
4. **TrackManager** - Integrates with tracking services
5. **ChapterFilter** - Filtering logic for chapters
6. **ChapterSort** - Sorting logic for chapters
7. **BaseCoroutinePresenter** - Presenter base class with coroutine support
8. **BaseLegacyController** - Controller base class

---

## 🚨 CRITICAL ARCHITECTURAL NOTES

### 1. Mode Inheritance Compliance
**ALL** novel code must route through `ContentRouter` and respect `ModeManager.currentMode`.  
**NO** runtime `if (isNovel)` checks allowed - use polymorphism instead.

### 2. Database Separation
Novels use completely separate tables from manga:
- `novel` table (NOT `manga`)
- `novel_chapter` table (NOT `chapter`)
- NO shared columns to prevent data corruption

### 3. Reader Routing
Novels MUST route to `NovelReaderActivity`, NOT `ReaderActivity`.  
Page-based navigation doesn't apply to text content.

### 4. Progress Tracking
Novels track **character position** in text, NOT page numbers.  
Adapt all progress logic accordingly.

### 5. Download Strategy
Novels download **text content** (likely compressed), NOT image URLs.  
Adapt download manager integration.

---

## 📝 TESTING CHECKLIST

After implementation, verify:

### Display
- [ ] Novel details screen loads without crash
- [ ] Cover image displays correctly
- [ ] Backdrop extends to top of screen (past status bar)
- [ ] Title hidden when scrolled to top, visible when scrolled down
- [ ] Genre chips populated and clickable
- [ ] Description expandable/collapsible with animation
- [ ] Author/status/source info visible
- [ ] No FABs present at bottom

### Chapters
- [ ] Chapter list displays in RecyclerView
- [ ] Chapter titles formatted correctly
- [ ] Read/unread indicators work
- [ ] Bookmark indicators work
- [ ] Download indicators work (if implemented)
- [ ] Click chapter opens reader
- [ ] Long click opens context menu
- [ ] Swipe left/right for read/bookmark (if enabled)

### Buttons
- [ ] Add to Library button toggles favorite
- [ ] Add to Library long press opens category selection
- [ ] Tracking button opens tracking sheet
- [ ] More button expands description
- [ ] Start Reading opens next unread chapter
- [ ] Start Reading disabled when all read
- [ ] Filter button opens filter sheet
- [ ] Web button opens source in WebView
- [ ] Share button shares novel info

### Filtering
- [ ] Can filter by read/unread
- [ ] Can filter by downloaded/not downloaded
- [ ] Can filter by bookmarked/not bookmarked
- [ ] Can filter by translator/source
- [ ] Can sort by source order/chapter number
- [ ] Can toggle ascending/descending
- [ ] Filter text displays active filters
- [ ] Filters persist across app restarts

### Downloads
- [ ] Can download novel chapters
- [ ] Download progress shows
- [ ] Can delete downloads
- [ ] Downloaded indicator updates
- [ ] Offline reading works

### Tracking
- [ ] Tracking sheet opens
- [ ] Can search for novel on services
- [ ] Can bind to tracking service
- [ ] Can update chapter progress
- [ ] Can update status (reading/completed/etc.)
- [ ] Can update score/rating
- [ ] Can set start/finish dates

### Integration
- [ ] Novel appears in library after favoriting
- [ ] History updates after reading
- [ ] Notifications work for new chapters
- [ ] Search works from novel details
- [ ] Migration works (if applicable)
- [ ] Backup includes novel data

---

## 🎓 LEARNING RESOURCES

### Reference Files (Manga Implementation):
1. `MangaDetailsController.kt` - Main controller pattern
2. `MangaDetailsPresenter.kt` - Business logic pattern
3. `MangaDetailsAdapter.kt` - Adapter + interface pattern
4. `MangaHeaderHolder.kt` - Complex ViewHolder pattern
5. `ChapterItem.kt` - FlexibleAdapter item pattern
6. `ChapterHolder.kt` - Simple ViewHolder pattern

### Key Patterns to Follow:
1. **FlexibleAdapter Usage** - How to extend AbstractFlexibleItem
2. **Presenter-View Separation** - How to call withUIContext()
3. **Download Integration** - How to use DownloadQueue.Listener
4. **Tracking Integration** - How to use TrackManager
5. **Coroutine Usage** - presenterScope.launch() patterns

---

## 🏁 COMPLETION CRITERIA

Novel Details is considered **COMPLETE** when:

1. ✅ All buttons functional (favorite, track, share, web, filter, read, expand)
2. ✅ Chapters display in RecyclerView with correct formatting
3. ✅ Clicking chapter opens NovelReaderActivity
4. ✅ Can filter/sort chapters like manga
5. ✅ Can download novel chapters (if implemented)
6. ✅ Can track novel progress (if implemented)
7. ✅ Visual parity with manga details (no FABs, backdrop extends, title hides)
8. ✅ Genre chips populated and functional
9. ✅ Description expansion animated
10. ✅ All user-identified gaps fixed

---

## 📌 FINAL NOTES

This document represents a **COMPLETE INVENTORY** of missing functionality between the current stub implementation and full manga parity. Implementation should follow the phased approach, testing after each phase to ensure stability.

**Estimated Total Effort:** 2000-3000 lines of new code + 1500 lines of modified code  
**Complexity:** High (requires deep understanding of FlexibleAdapter, coroutines, and Miko architecture)

The largest files to duplicate are:
1. **MangaHeaderHolder.kt** (727 lines) → NovelHeaderHolder.kt
2. **MangaDetailsController.kt** (2013 lines) → Enhance NovelDetailsController.kt
3. **MangaDetailsPresenter.kt** (1146 lines) → Enhance NovelDetailsPresenter.kt

All other files are smaller (<200 lines each) but critical for complete functionality.
