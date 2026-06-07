# Novel Features Implementation Plan
**Date Created**: October 22, 2025  
**Branch**: `novel-details-rebuild-backup`  
**Status**: Ready for Implementation

---

## Executive Summary

This plan addresses all TODO items in the novel system by adapting existing, battle-tested manga implementations. The novel infrastructure (domain models, repository, database) is complete with no compilation errors. The remaining work focuses on presenter business logic and UI features.

**Key Principle**: Don't reinvent the wheel - adapt proven manga patterns to novel architecture.

---

## Phase 1: Chapter Management Core (HIGH PRIORITY)
**Goal**: Implement chapter filtering, sorting, and basic operations  
**Existing References**: `ChapterFilter.kt`, `ChapterSort.kt`, `MangaDetailsPresenter.kt` (lines 594-723)  
**Estimated Effort**: 2-3 days

### Tasks

#### 1.1 Create Novel Chapter Filter System
**Files to Create**:
- `app/src/main/java/eu/kanade/tachiyomi/util/novel/NovelChapterFilter.kt`

**Implementation Strategy**:
- Copy `ChapterFilter.kt` structure
- Adapt to use `NovelChapter` instead of `Chapter`
- Support filter types from `Novel.kt` companion object:
  - `CHAPTER_SHOW_READ` (1)
  - `CHAPTER_SHOW_UNREAD` (2)
  - `CHAPTER_SHOW_BOOKMARKED` (8)
  - `CHAPTER_SHOW_NOT_BOOKMARKED` (16)
- Remove download filtering (novels don't support chapter downloads)
- Method signatures:
  ```kotlin
  fun filterChapters(chapters: List<NovelChapter>, novel: Novel): List<NovelChapter>
  fun filterChaptersForReader(chapters: List<NovelChapter>, novel: Novel, selectedChapter: NovelChapter?): List<NovelChapter>
  ```

**Manga Reference**: `ChapterFilter.kt` lines 13-41 (read/bookmark filtering logic)

---

#### 1.2 Create Novel Chapter Sort System
**Files to Create**:
- `app/src/main/java/eu/kanade/tachiyomi/util/novel/NovelChapterSort.kt`

**Implementation Strategy**:
- Copy `ChapterSort.kt` structure
- Adapt to use `NovelChapter` and `Novel`
- Support sorting types from `Novel.kt` companion object:
  - `CHAPTER_SORTING_SOURCE` (0) - by `sourceOrder`
  - `CHAPTER_SORTING_NUMBER` (1) - by `chapterNumber`
  - `CHAPTER_SORTING_UPLOAD_DATE` (3) - by `dateUpload`
- Use `Novel.CHAPTER_SORT_DIR_ASC`/`DESC` for direction
- Method signatures:
  ```kotlin
  fun getChaptersSorted(rawChapters: List<NovelChapter>, andFiltered: Boolean = true): List<NovelChapter>
  fun getNextUnreadChapter(rawChapters: List<NovelChapter>, andFiltered: Boolean = true): NovelChapter?
  fun sortComparator(ignoreAsc: Boolean = false): Comparator<NovelChapter>
  ```

**Manga Reference**: `ChapterSort.kt` lines 13-65 (sorting comparator logic)

---

#### 1.3 Implement Presenter Filter/Sort Methods
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt`

**Methods to Implement** (replace TODOs):

```kotlin
// Line 75: currentFilters()
fun currentFilters(): String {
    val novel = _novel.value ?: return ""
    val filters = mutableListOf<String>()
    
    when (novel.readFilter) {
        Novel.CHAPTER_SHOW_READ -> filters.add("Read")
        Novel.CHAPTER_SHOW_UNREAD -> filters.add("Unread")
    }
    when (novel.bookmarkedFilter) {
        Novel.CHAPTER_SHOW_BOOKMARKED -> filters.add("Bookmarked")
        Novel.CHAPTER_SHOW_NOT_BOOKMARKED -> filters.add("Not Bookmarked")
    }
    
    return filters.joinToString(", ")
}

// Line 115-116: sortingOrder() and sortDescending()
fun sortingOrder(): Int = _novel.value?.sorting ?: Novel.CHAPTER_SORTING_SOURCE
fun sortDescending(): Boolean = _novel.value?.sortDescending() ?: false

// Line 117-120: setGlobalChapterSort, setSortOrder, resetSortingToDefault
fun setGlobalChapterSort(sortingMode: Int, sortDescending: Boolean) {
    preferences.sortChapterOrder().set(sortingMode)
    preferences.sortChapterDirection().set(sortDescending)
    val novel = _novel.value ?: return
    val update = NovelUpdate(
        id = novel.id,
        chapterFlags = novel.setChapterOrder(sortingMode).setChapterSortDescending(sortDescending).chapterFlags
    )
    presenterScope.launchIO { updateNovel.await(update) }
}

fun setSortOrder(order: Int, descending: Boolean) {
    val novel = _novel.value ?: return
    val update = NovelUpdate(
        id = novel.id,
        chapterFlags = novel.setChapterOrder(order).setChapterSortDescending(descending).chapterFlags
    )
    presenterScope.launchIO { updateNovel.await(update) }
}

fun resetSortingToDefault() {
    val novel = _novel.value ?: return
    val globalSort = preferences.sortChapterOrder().get()
    val globalDesc = preferences.sortChapterDirection().get()
    setSortOrder(globalSort, globalDesc)
}

// Line 121-123: setGlobalChapterFilters, setFilters, resetFilterToDefault
fun setGlobalChapterFilters(readFilter: Int, bookmarkFilter: Int) {
    preferences.filterChapterByRead().set(readFilter)
    preferences.filterChapterByBookmarked().set(bookmarkFilter)
    val novel = _novel.value ?: return
    val update = NovelUpdate(
        id = novel.id,
        chapterFlags = novel.setReadFilter(readFilter).setBookmarkFilter(bookmarkFilter).chapterFlags
    )
    presenterScope.launchIO { updateNovel.await(update) }
}

fun setFilters(read: Int, bookmarked: Int) {
    val novel = _novel.value ?: return
    val update = NovelUpdate(
        id = novel.id,
        chapterFlags = novel.setReadFilter(read).setBookmarkFilter(bookmarked).chapterFlags
    )
    presenterScope.launchIO { updateNovel.await(update) }
}

fun resetFilterToDefault() {
    val novel = _novel.value ?: return
    val globalRead = preferences.filterChapterByRead().get()
    val globalBookmark = preferences.filterChapterByBookmarked().get()
    setFilters(globalRead, globalBookmark)
}

// Line 124-125: mangaFilterMatchesDefault, novelSortMatchesDefault
fun mangaFilterMatchesDefault(): Boolean {
    val novel = _novel.value ?: return true
    return novel.readFilter == preferences.filterChapterByRead().get() &&
           novel.bookmarkedFilter == preferences.filterChapterByBookmarked().get()
}

fun novelSortMatchesDefault(): Boolean {
    val novel = _novel.value ?: return true
    return novel.sorting == preferences.sortChapterOrder().get() &&
           novel.sortDescending() == preferences.sortChapterDirection().get()
}
```

**Manga Reference**: `MangaDetailsPresenter.kt` lines 594-723

---

#### 1.4 Apply Filtering/Sorting to Chapter List
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt`

**Implementation Strategy**:
- Inject `NovelChapterFilter` and create `NovelChapterSort` in presenter
- Modify `onCreate()` to apply filters/sort to `_chapters` flow:
  ```kotlin
  private val novelChapterFilter: NovelChapterFilter by injectLazy()
  private lateinit var novelChapterSort: NovelChapterSort
  
  override fun onCreate() {
      super.onCreate()
      
      val novel = _novel.value ?: return
      novelChapterSort = NovelChapterSort(novel, novelChapterFilter, preferences)
      
      // Existing code...
      
      presenterScope.launchIO {
          getNovelChapter.subscribeByNovelId(novelId).collectLatest { rawChapters ->
              val novel = _novel.value ?: return@collectLatest
              val sorted = novelChapterSort.getChaptersSorted(rawChapters)
              _chapters.value = sorted
          }
      }
  }
  ```

**Manga Reference**: `MangaDetailsPresenter.kt` lines 280-348 (getChapters and applyChapterFilters)

---

#### 1.5 Implement Scroll Type Detection
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt` line 52

**Implementation Strategy**:
- Copy logic from `MangaDetailsPresenter.getScrollType()`
- Detect chapter grouping patterns:
  - `TENS_OF_CHAPTERS` (0): Default, many sequential chapters
  - `MULTIPLE_VOLUMES` (1): Chapter names contain "Volume" or "Vol"
  - `MULTIPLE_SEASONS` (2): Chapter names contain "Season" or "S1"

```kotlin
val scrollType: Int get() = getScrollType(_chapters.value)

private fun getScrollType(chapters: List<NovelChapter>): Int {
    val volumeCount = chapters.count { it.name.contains("volume", ignoreCase = true) || it.name.contains("vol", ignoreCase = true) }
    val seasonCount = chapters.count { it.name.contains("season", ignoreCase = true) || Regex("S\\d+").containsMatchIn(it.name) }
    
    return when {
        volumeCount > chapters.size / 4 -> MULTIPLE_VOLUMES
        seasonCount > chapters.size / 4 -> MULTIPLE_SEASONS
        else -> TENS_OF_CHAPTERS
    }
}
```

**Manga Reference**: `MangaDetailsPresenter.kt` getScrollType() method

---

## Phase 2: Chapter Operations (HIGH PRIORITY)
**Goal**: Implement chapter deletion and bulk operations  
**Existing References**: `MangaDetailsPresenter.kt` lines 409-587  
**Estimated Effort**: 1-2 days

### Tasks

#### 2.1 Implement Chapter Deletion
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt` line 106
- `domain/src/commonMain/kotlin/yokai/domain/novelchapter/interactor/DeleteNovelChapter.kt` (create new)

**Implementation Strategy**:
- Create domain interactor `DeleteNovelChapter`:
  ```kotlin
  class DeleteNovelChapter(private val novelRepository: NovelRepository) {
      suspend fun await(chapterId: Long) {
          novelRepository.deleteChapter(chapterId)
      }
      
      suspend fun awaitAll(chapterIds: List<Long>) {
          chapterIds.forEach { novelRepository.deleteChapter(it) }
      }
  }
  ```

- Implement presenter method:
  ```kotlin
  fun deleteChapters(chapters: List<NovelChapter>, update: Boolean = true, isEverything: Boolean = false) {
      presenterScope.launchIO {
          try {
              chapters.forEach { chapter ->
                  deleteNovelChapter.await(chapter.id)
              }
              
              if (update) {
                  withUIContext {
                      view?.updateChapters()
                  }
              }
              
              logger.i { "Successfully deleted ${chapters.size} chapters" }
          } catch (e: Exception) {
              logger.e(e) { "Failed to delete chapters" }
              handleError(e, "Delete chapters")
          }
      }
  }
  ```

**Manga Reference**: `MangaDetailsPresenter.kt` lines 409-502 (deleteChapters implementation)

**Note**: Novels don't have downloaded files like manga, so deletion only affects database records.

---

#### 2.2 Implement Scanlator Filter (Novel Translator/Uploader Filter)
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt` lines 127-129

**Implementation Strategy**:
- Novels use "translator" or "uploader" field (stored in `scanlator` column for compatibility)
- Implement filtering similar to manga scanlator filter:

```kotlin
val allChapterScanlators: Set<String> get() {
    return _chapters.value.mapNotNull { it.scanlator }.toSet()
}

fun setScanlatorFilter(scanlators: Set<String>) {
    val novel = _novel.value ?: return
    presenterScope.launchIO {
        try {
            // Use NovelUpdate when filteredScanlators field is added to Novel domain model
            // For now, store in preferences per-novel
            val key = "novel_${novel.id}_filtered_scanlators"
            preferences.novelFilteredScanlators(novel.id).set(scanlators)
            
            // Refresh chapter list to apply filter
            fetchChapters(andTracking = false)
            
            logger.i { "Updated scanlator filter: ${scanlators.joinToString()}" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to set scanlator filter" }
        }
    }
}
```

**Manga Reference**: `MangaDetailsPresenter.kt` lines 723-735

**Future Enhancement**: Add `filteredScanlators` column to `novel` table (see TODO in ChaptersSortBottomSheet.kt line 170)

---

## ~~Phase 3: Tracking System - Goodreads Integration~~ (DEFERRED - NO VIABLE API)
**Status**: ⏸️ **DEFERRED UNTIL VIABLE TRACKING PLATFORM EXISTS**

**Reason for Deferral**: 
- Goodreads public API was **retired in December 2020** and is no longer accessible
- AniList has very limited novel coverage (primarily anime/manga focused)
- Novel Updates does not have an official API (would require fragile web scraping)
- No viable alternative tracking services with official APIs currently exist

**Work Completed Before Deferral**:
- ✅ Phase 3.1: Novel Tracking Domain Models (NovelTrack + 3 interactors)
- ✅ Phase 3.2: Novel Tracking Database Table (novel_track.sq with full schema)
- ✅ Phase 3.3: Tracking Repository Implementation (DI + Presenter integration)
- ✅ Build Verification: All local tracking infrastructure compiles successfully

**Local Tracking Status**: All database infrastructure is complete and functional for future integration when a viable API becomes available.

**Future Considerations**:
- Monitor for new novel tracking platforms with official APIs
- Consider AniList if they expand novel coverage significantly
- Evaluate Novel Updates if they release an official API
- Local-only tracking remains functional via database

**NOTE**: Phase 3.1-3.3 code remains in codebase and is production-ready. Only remote sync functionality (Phase 3.4-3.6) is blocked.

---

## Phase 3: Backup & Restore System for Novels (HIGH PRIORITY)
**Goal**: Integrate novels into the existing backup/restore system so user progress is preserved  
**Existing References**: `BackupCreator.kt`, `BackupRestorer.kt`, `BackupManga.kt`, `MangaBackupCreator.kt`, `MangaBackupRestorer.kt`  
**Estimated Effort**: 3-4 days

**CRITICAL**: Novels must be backed up separately from manga with proper content-type awareness during restore to prevent cross-contamination. The backup system must preserve:
- Novel metadata (title, author, description, genre, status, cover URL, etc.)
- Novel chapters with read status, bookmarks, and last read positions
- Novel categories and favorites
- Novel-specific preferences (filtering, sorting, translator filters)
- Novel reading history

### Architecture Analysis

#### Current Backup System Structure
The Miko backup system uses **Protocol Buffers (protobuf)** serialization:

1. **Backup File Format**: `.tachibk` file containing gzipped protobuf binary
2. **Main Model**: `Backup` data class with serializable fields:
   - `backupManga: List<BackupManga>` - All manga with chapters, tracking, categories
   - `backupCategories: List<BackupCategory>` - Shared categories
   - `backupSources: List<BackupSource>` - Source metadata
   - `backupPreferences: List<BackupPreference>` - App preferences
   - `backupSourcePreferences: List<BackupSourcePreferences>` - Source settings

3. **Backup Creation Flow**:
   ```
   BackupCreatorJob → BackupCreator → Individual Creators → Protobuf Serialization → Gzipped File
   ```

4. **Backup Restore Flow**:
   ```
   Gzipped File → Protobuf Deserialization → BackupRestorer → Individual Restorers → Database
   ```

5. **Key Components**:
   - **BackupManga**: Contains manga metadata + chapters + tracking + history
   - **BackupChapter**: Chapter metadata (read status, bookmark, last page)
   - **BackupTracking**: External tracker sync data (AniList, MAL)
   - **BackupHistory**: Reading history entries

#### Novel Backup Requirements

Novels need equivalent backup models:
- **BackupNovel**: Novel metadata + chapters + categories
- **BackupNovelChapter**: Chapter with character position tracking (not page numbers)
- **BackupNovelHistory**: Novel reading history
- **BackupNovelTrack**: Novel tracking (future - currently unused due to deferred Phase 3)

**CRITICAL DIFFERENCE**: Novels use `ContentType.NOVEL` and must restore to novel-specific database tables, NOT manga tables.

### Tasks

#### 3.1 Create Novel Backup Models
**Files to Create**:
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupNovel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupNovelChapter.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/BackupNovelHistory.kt`

**Implementation Strategy**:

**BackupNovel.kt** (adapt from `BackupManga.kt`):
```kotlin
@Serializable
data class BackupNovel(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var author: String? = null,
    @ProtoNumber(5) var description: String? = null,
    @ProtoNumber(6) var genre: List<String> = emptyList(),
    @ProtoNumber(7) var status: Int = 0,
    @ProtoNumber(8) var posterUrl: String? = null,
    @ProtoNumber(9) var dateAdded: Long = 0,
    @ProtoNumber(10) var chapters: List<BackupNovelChapter> = emptyList(),
    @ProtoNumber(11) var categories: List<Int> = emptyList(),
    @ProtoNumber(12) var history: List<BackupNovelHistory> = emptyList(),
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    @ProtoNumber(102) var wordCount: Int? = null,
    @ProtoNumber(103) var chapterCount: Int? = null,
    @ProtoNumber(104) var filteredTranslators: List<String> = emptyList(),
    @ProtoNumber(105) var vibrantCoverColor: Int? = null,
) {
    fun getNovelImpl(): Novel {
        return Novel(
            source = this.source,
            url = this.url,
            title = this.title,
            author = this.author,
            description = this.description,
            genre = this.genre.joinToString(),
            status = this.status,
            posterUrl = this.posterUrl,
            isFavorite = this.favorite,
            dateAdded = this.dateAdded,
            chapterFlags = this.chapterFlags,
            wordCount = this.wordCount,
            chapterCount = this.chapterCount,
            filteredTranslators = this.filteredTranslators?.joinToString(","),
            vibrantCoverColor = this.vibrantCoverColor,
        )
    }
    
    companion object {
        fun copyFrom(novel: Novel): BackupNovel {
            return BackupNovel(
                url = novel.url,
                title = novel.title,
                author = novel.author,
                description = novel.description,
                genre = novel.genre?.split(",")?.map { it.trim() } ?: emptyList(),
                status = novel.status,
                posterUrl = novel.posterUrl,
                favorite = novel.isFavorite,
                source = novel.source,
                dateAdded = novel.dateAdded,
                chapterFlags = novel.chapterFlags,
                wordCount = novel.wordCount,
                chapterCount = novel.chapterCount,
                filteredTranslators = novel.filteredTranslators?.split(",")?.map { it.trim() } ?: emptyList(),
                vibrantCoverColor = novel.vibrantCoverColor,
            )
        }
    }
}
```

**BackupNovelChapter.kt** (adapt from `BackupChapter.kt`):
```kotlin
@Serializable
data class BackupNovelChapter(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var title: String,
    @ProtoNumber(3) var chapterNumber: Float = -1f,
    @ProtoNumber(4) var volumeNumber: Float? = null,
    @ProtoNumber(5) var read: Boolean = false,
    @ProtoNumber(6) var bookmark: Boolean = false,
    @ProtoNumber(7) var lastReadPosition: Int = 0, // Character position, not page
    @ProtoNumber(8) var dateUpload: Long = 0L,
    @ProtoNumber(9) var dateFetch: Long = 0L,
    @ProtoNumber(10) var sourceOrder: Int = 0,
    @ProtoNumber(11) var wordCount: Int? = null,
    @ProtoNumber(12) var scanlator: String? = null, // Actually translator/uploader for novels
) {
    fun toNovelChapterImpl(): NovelChapter {
        return NovelChapter(
            url = this.url,
            title = this.title,
            chapterNumber = this.chapterNumber.toDouble(),
            volumeNumber = this.volumeNumber?.toDouble(),
            read = this.read,
            bookmark = this.bookmark,
            lastReadPosition = this.lastReadPosition,
            dateUpload = this.dateUpload,
            dateFetch = this.dateFetch,
            sourceOrder = this.sourceOrder,
            wordCount = this.wordCount,
            scanlator = this.scanlator,
        )
    }
    
    companion object {
        fun copyFrom(chapter: NovelChapter): BackupNovelChapter {
            return BackupNovelChapter(
                url = chapter.url,
                title = chapter.title,
                chapterNumber = chapter.chapterNumber.toFloat(),
                volumeNumber = chapter.volumeNumber?.toFloat(),
                read = chapter.read,
                bookmark = chapter.bookmark,
                lastReadPosition = chapter.lastReadPosition,
                dateUpload = chapter.dateUpload,
                dateFetch = chapter.dateFetch,
                sourceOrder = chapter.sourceOrder,
                wordCount = chapter.wordCount,
                scanlator = chapter.scanlator,
            )
        }
    }
}
```

**BackupNovelHistory.kt**:
```kotlin
@Serializable
data class BackupNovelHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastRead: Long,
    @ProtoNumber(3) var readDuration: Long = 0,
)
```

**Manga Reference**: `BackupManga.kt`, `BackupChapter.kt`, `BackupHistory.kt`

---

#### 3.2 Update Backup Model to Include Novels
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt`

**Implementation Strategy**:
Add novel list to the main `Backup` data class:

```kotlin
@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga>,
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    
    // NEW: Novel backup support
    @ProtoNumber(200) var backupNovels: List<BackupNovel> = emptyList(),
) {
    // ... existing companion object
}
```

**CRITICAL**: Use ProtoNumber 200+ for novel fields to avoid conflicts with existing manga fields.

---

#### 3.3 Create Novel Backup Creator
**Files to Create**:
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/creators/NovelBackupCreator.kt`

**Implementation Strategy**:
Mirror `MangaBackupCreator` structure for novels:

```kotlin
class NovelBackupCreator(
    private val getNovel: GetNovel = Injekt.get(),
    private val getNovelChapter: GetNovelChapter = Injekt.get(),
    private val getNovelHistory: GetNovelHistory = Injekt.get(),
    private val getCategoriesForNovel: GetCategoriesForNovel = Injekt.get(),
) {
    suspend operator fun invoke(novels: List<Novel>, options: BackupOptions): List<BackupNovel> {
        return novels.map { novel ->
            val backupNovel = BackupNovel.copyFrom(novel)
            
            // Backup chapters if enabled
            if (options.chapters) {
                val chapters = getNovelChapter.awaitAllByNovelId(novel.id)
                backupNovel.chapters = chapters.map { BackupNovelChapter.copyFrom(it) }
            }
            
            // Backup categories
            val categories = getCategoriesForNovel.await(novel.id)
            backupNovel.categories = categories.map { it.order }
            
            // Backup history if enabled
            if (options.history) {
                val history = getNovelHistory.awaitByNovelId(novel.id)
                backupNovel.history = history.map { 
                    BackupNovelHistory(
                        url = it.chapterUrl,
                        lastRead = it.readAt?.toEpochMilli() ?: 0L,
                        readDuration = it.readDuration ?: 0L
                    )
                }
            }
            
            backupNovel
        }
    }
}
```

**Manga Reference**: `MangaBackupCreator.kt`

---

#### 3.4 Integrate Novel Backup into BackupCreator
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt`

**Implementation Strategy**:
Add novel backup logic to main creator:

```kotlin
class BackupCreator(
    val context: Context,
    private val categoriesBackupCreator: CategoriesBackupCreator = CategoriesBackupCreator(),
    private val mangaBackupCreator: MangaBackupCreator = MangaBackupCreator(),
    private val novelBackupCreator: NovelBackupCreator = NovelBackupCreator(), // NEW
    private val preferenceBackupCreator: PreferenceBackupCreator = PreferenceBackupCreator(),
    private val sourcesBackupCreator: SourcesBackupCreator = SourcesBackupCreator(),
    private val getManga: GetManga = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(), // NEW
) {
    // ... existing code
    
    suspend fun createBackup(uri: Uri, options: BackupOptions, isAutoBackup: Boolean): String {
        // ... existing file setup code
        
        val readNotFavorites = if (options.readManga) getManga.awaitReadNotFavorites() else emptyList()
        val backupManga = backupMangas(getManga.awaitFavorites() + readNotFavorites, options)
        
        // NEW: Backup novels
        val readNotFavoriteNovels = if (options.readManga) getNovel.awaitReadNotFavorites() else emptyList()
        val backupNovels = backupNovels(getNovel.awaitFavorites() + readNotFavoriteNovels, options)
        
        val backup = Backup(
            backupManga = backupManga,
            backupCategories = backupCategories(options),
            backupSources = backupSources(backupManga),
            backupPreferences = backupAppPreferences(options),
            backupSourcePreferences = backupSourcePreferences(options),
            backupNovels = backupNovels, // NEW
        )
        
        // ... rest of existing code
    }
    
    // NEW
    private suspend fun backupNovels(novels: List<Novel>, options: BackupOptions): List<BackupNovel> {
        if (!options.libraryEntries) return emptyList()
        return novelBackupCreator(novels, options)
    }
}
```

---

#### 3.5 Create Novel Backup Restorer
**Files to Create**:
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/restorers/NovelBackupRestorer.kt`

**Implementation Strategy**:
Mirror `MangaBackupRestorer` with novel-specific logic:

```kotlin
class NovelBackupRestorer(
    private val getNovel: GetNovel = Injekt.get(),
    private val insertNovel: InsertNovel = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val getNovelChapter: GetNovelChapter = Injekt.get(),
    private val insertNovelChapter: InsertNovelChapter = Injekt.get(),
    private val getNovelHistory: GetNovelHistory = Injekt.get(),
    private val upsertNovelHistory: UpsertNovelHistory = Injekt.get(),
    private val getCategoriesForNovel: GetCategoriesForNovel = Injekt.get(),
    private val setNovelCategories: SetNovelCategories = Injekt.get(),
) {
    suspend fun restoreNovel(
        backupNovel: BackupNovel,
        backupCategories: List<BackupCategory>,
        onComplete: (Novel) -> Unit,
        onError: (BackupNovel, Throwable) -> Unit,
    ) {
        try {
            // Find or create novel in database
            val existingNovel = getNovel.awaitByUrlAndSourceId(backupNovel.url, backupNovel.source)
            val novel = if (existingNovel != null) {
                // Update existing novel
                val update = NovelUpdate(
                    id = existingNovel.id,
                    title = backupNovel.title,
                    author = backupNovel.author,
                    description = backupNovel.description,
                    genre = backupNovel.genre.joinToString(","),
                    status = backupNovel.status,
                    posterUrl = backupNovel.posterUrl,
                    isFavorite = backupNovel.favorite,
                    chapterFlags = backupNovel.chapterFlags,
                    wordCount = backupNovel.wordCount,
                    chapterCount = backupNovel.chapterCount,
                    filteredTranslators = backupNovel.filteredTranslators.joinToString(","),
                    vibrantCoverColor = backupNovel.vibrantCoverColor,
                )
                updateNovel.await(update)
                getNovel.awaitById(existingNovel.id)!!
            } else {
                // Insert new novel
                val novelToInsert = backupNovel.getNovelImpl()
                val novelId = insertNovel.await(novelToInsert)
                getNovel.awaitById(novelId)!!
            }
            
            // Restore chapters
            restoreNovelChapters(novel, backupNovel.chapters)
            
            // Restore categories
            restoreNovelCategories(novel, backupNovel.categories, backupCategories)
            
            // Restore history
            restoreNovelHistory(novel, backupNovel.history)
            
            onComplete(novel)
        } catch (e: Exception) {
            onError(backupNovel, e)
        }
    }
    
    private suspend fun restoreNovelChapters(novel: Novel, backupChapters: List<BackupNovelChapter>) {
        val existingChapters = getNovelChapter.awaitAllByNovelId(novel.id)
        
        backupChapters.forEach { backupChapter ->
            val existingChapter = existingChapters.find { it.url == backupChapter.url }
            
            if (existingChapter != null) {
                // Update read status, bookmark, position
                val update = NovelChapterUpdate(
                    id = existingChapter.id,
                    read = backupChapter.read,
                    bookmark = backupChapter.bookmark,
                    lastReadPosition = backupChapter.lastReadPosition,
                )
                updateNovelChapter.await(update)
            } else {
                // Insert new chapter
                val chapterToInsert = backupChapter.toNovelChapterImpl().copy(novelId = novel.id)
                insertNovelChapter.await(chapterToInsert)
            }
        }
    }
    
    private suspend fun restoreNovelCategories(
        novel: Novel,
        categoryIds: List<Int>,
        backupCategories: List<BackupCategory>
    ) {
        val categories = categoryIds.mapNotNull { id ->
            backupCategories.find { it.order == id }?.name
        }
        setNovelCategories.await(novel.id, categories)
    }
    
    private suspend fun restoreNovelHistory(novel: Novel, backupHistory: List<BackupNovelHistory>) {
        backupHistory.forEach { historyEntry ->
            val chapter = getNovelChapter.awaitByUrlAndNovelId(historyEntry.url, novel.id)
            if (chapter != null) {
                upsertNovelHistory.await(
                    NovelHistory(
                        novelId = novel.id,
                        chapterId = chapter.id,
                        chapterUrl = chapter.url,
                        readAt = Instant.ofEpochMilli(historyEntry.lastRead),
                        readDuration = historyEntry.readDuration
                    )
                )
            }
        }
    }
}
```

**CRITICAL**: Novel restore MUST use novel-specific database tables and interactors. DO NOT mix with manga restore logic.

**Manga Reference**: `MangaBackupRestorer.kt`

---

#### 3.6 Integrate Novel Restore into BackupRestorer
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt`

**Implementation Strategy**:
Add novel restoration to main restorer:

```kotlin
class BackupRestorer(
    val context: Context,
    val notifier: BackupNotifier,
    private val categoriesBackupRestorer: CategoriesBackupRestorer = CategoriesBackupRestorer(),
    private val mangaBackupRestorer: MangaBackupRestorer = MangaBackupRestorer(),
    private val novelBackupRestorer: NovelBackupRestorer = NovelBackupRestorer(), // NEW
    private val preferenceBackupRestorer: PreferenceBackupRestorer = PreferenceBackupRestorer(context),
) {
    private suspend fun performRestore(uri: Uri) {
        val backup = BackupUtil.decodeBackup(context, uri)

        restoreAmount = backup.backupManga.size + backup.backupNovels.size + 3 // +3 for categories, app prefs, source prefs

        // ... existing category/preference restore code
        
        // Restore manga
        backup.backupManga.forEach {
            ensureActive()
            mangaBackupRestorer.restoreManga(
                it,
                backup.backupCategories,
                onComplete = { manga ->
                    restoreProgress += 1
                    showRestoreProgress(restoreProgress, restoreAmount, manga.title)
                },
                onError = { manga, e ->
                    val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
                    errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
                },
            )
        }
        
        // NEW: Restore novels
        backup.backupNovels.forEach {
            ensureActive()
            novelBackupRestorer.restoreNovel(
                it,
                backup.backupCategories,
                onComplete = { novel ->
                    restoreProgress += 1
                    showRestoreProgress(restoreProgress, restoreAmount, novel.title)
                },
                onError = { novel, e ->
                    val sourceName = sourceMapping[novel.source] ?: novel.source.toString()
                    errors.add(Date() to "${novel.title} [$sourceName]: ${e.message}")
                },
            )
        }
    }
}
```

---

#### 3.7 Update Backup Options UI
**Files to Modify**:
- UI files for backup options (if novels need separate toggle)
- `BackupOptions.kt` (if novels need separate flag)

**Implementation Strategy**:
Novels should use the same "Library entries" option as manga since they're both content types. No UI changes needed unless you want separate novel/manga toggles.

---

#### 3.8 Testing & Validation
**Testing Checklist**:
- [ ] Create backup with favorite novels → backup file created
- [ ] Backup includes novel chapters with correct read status
- [ ] Backup includes novel bookmarks and last read positions
- [ ] Backup includes novel categories
- [ ] Backup includes novel reading history
- [ ] Restore backup to fresh install → novels appear in library
- [ ] Restored novels have correct metadata (title, author, cover)
- [ ] Restored chapters have correct read status and bookmarks
- [ ] Restored novels appear in NOVEL mode, not manga mode
- [ ] Backup/restore doesn't affect existing manga data
- [ ] Large backups (100+ novels) complete without errors

**Edge Cases to Test**:
- Novel and manga with same title (different content types)
- Novel from source that no longer exists
- Corrupted backup file handling
- Backup file from older app version
- Novels in categories that don't exist in restore destination

---

### Migration Strategy

**Backward Compatibility**:
- Old backup files (without `backupNovels` field) will still restore manga correctly
- New backup files can be opened in old app versions (novels will be ignored)
- ProtoNumber 200+ ensures no conflicts with existing manga fields

**Database Migrations**:
No database migrations needed - novel tables already exist from previous phases.

**Testing Approach**:
1. Create backup on current version
2. Verify backup file contains novel data (check with protobuf parser)
3. Restore on fresh install → validate all novels present
4. Restore on existing install → validate no duplicates or data loss

---

## Phase 4: UI Enhancement (MEDIUM PRIORITY) [RENUMBERED]
**Goal**: Complete UI interactions and dialogs  
**Existing References**: Various controller and dialog files  
**Estimated Effort**: 2-3 days

### Tasks

#### 4.1 Implement Edit Novel Dialog
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsControllerNew.kt` line 611
- `backup_broken_files/EditNovelDialog.kt` (restore and fix)

**Implementation Strategy**:
- Copy `EditMangaDialog.kt` structure
- Adapt for novel fields (remove artist, series type)
- Connect to `NovelDetailsPresenter.updateNovel()`
- Handle cover image selection and upload

**Manga Reference**: `EditMangaDialog.kt` implementation

---

#### 4.2 Implement Range Marking (Read/Unread)
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsControllerNew.kt` lines 455, 459

**Implementation Strategy**:
- Copy range marking dialog from `MangaDetailsController`
- Allow marking chapter ranges as read/unread/bookmarked
- Use `NovelDetailsPresenter.markChaptersRead()`

**Manga Reference**: `MangaDetailsController.kt` chapter range marking implementation

---

#### 4.3 Implement Hide Title Feature
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt` line 131

**Implementation Strategy**:
```kotlin
fun hideTitle(hide: Boolean) {
    val novel = _novel.value ?: return
    presenterScope.launchIO {
        val update = NovelUpdate(
            id = novel.id,
            hideTitle = hide
        )
        updateNovel.await(update)
        logger.i { "Updated hideTitle to $hide" }
    }
}
```

**Manga Reference**: `MangaDetailsPresenter.kt` hideTitle implementation

---

## ~~Phase 5: Reader Integration~~ (EXCLUDED)
**Status**: ⚠️ **READER FUNCTIONALITY IS COMPLETE AND WORKING PERFECTLY - DO NOT TOUCH**

The novel reader system (clicking chapters, reading interface, text settings, navigation, preferences) is fully functional and should not be modified. All reader-related TODOs are intentionally left as they are working correctly.

**Excluded Tasks**:
- ~~Reader preferences application~~
- ~~Navigation to novel details from reader~~
- ~~Reader settings dialog~~
- ~~Text highlighting~~
- ~~Text settings application~~

---

## Phase 5: Advanced Features (LOW PRIORITY) [RENUMBERED FROM PHASE 6]
**Goal**: Implement nice-to-have features that enhance UX  
**Estimated Effort**: 3-4 days

### Tasks

#### 5.1 Implement Filter/Sort Bottom Sheet Functionality
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/chapter/NovelChaptersSortBottomSheet.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt`

**Current Status**: UI exists but filters don't apply to chapter list

**Implementation Strategy**:
- Connect filter checkboxes to `presenter.setFilters()`
- Trigger chapter list refresh when filters change
- Update chapter adapter with filtered/sorted results
- Add logging to track filter application
- Example:
  ```kotlin
  private fun setFilters(filterLayout: NovelChapterFilterLayout) {
      presenter.setFilters(
          unread = filterLayout.unread.state,
          downloaded = TriStateCheckBox.State.IGNORE, // Novels don't support downloads
          bookmarked = filterLayout.bookmarked.state
      )
      // Trigger refresh
      presenter.fetchChapters(andTracking = false)
  }
  ```

**Manga Reference**: `ChaptersSortBottomSheet.kt` setFilters() implementation and chapter refresh logic

---

#### 5.2 Implement Bottom Sheet Theming
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/chapter/NovelChaptersSortBottomSheet.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsControllerNew.kt`

**Current Status**: Filter bottom sheet doesn't inherit accent color from novel cover

**Implementation Strategy**:
- Pass accent color from controller to bottom sheet constructor
- Apply accent color to checkboxes, buttons, and text highlights
- Example:
  ```kotlin
  class NovelChaptersSortBottomSheet(
      controller: NovelDetailsControllerNew,
      private val accentColor: Int?
  ) : E2EBottomSheetDialog<NovelChapterSortBottomSheetBinding>(controller.activity!!) {
      
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          initGeneralPreferences()
          
          // Apply theming
          accentColor?.let { color ->
              binding.chapterFilterLayout.unread.buttonTintList = ColorStateList.valueOf(color)
              binding.chapterFilterLayout.bookmarked.buttonTintList = ColorStateList.valueOf(color)
              binding.sortGroup.setTextColor(ColorStateList.valueOf(color))
              // Apply to all interactive elements
          }
      }
  }
  ```

**Manga Reference**: Material3 theming patterns in manga details

---

#### 5.3 Implement Chapter Swipe Actions
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsControllerNew.kt`
- Create: `app/src/main/java/eu/kanade/tachiyomi/ui/novel/chapter/NovelChapterSwipeCallback.kt`

**Current Status**: Missing feature - swipe gestures for chapter operations

**Implementation Strategy**:
- Create ItemTouchHelper.Callback for RecyclerView swipe gestures
- Swipe left → Toggle read/unread status
- Swipe right → Toggle bookmark status
- Visual feedback during swipe (icons, colors)
- Example:
  ```kotlin
  class NovelChapterSwipeCallback(
      private val adapter: NovelChaptersAdapter,
      private val presenter: NovelDetailsPresenter
  ) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
      
      override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
          val position = viewHolder.bindingAdapterPosition
          val chapter = adapter.getItem(position)
          
          when (direction) {
              ItemTouchHelper.LEFT -> presenter.toggleChapterRead(chapter)
              ItemTouchHelper.RIGHT -> presenter.toggleChapterBookmark(chapter)
          }
      }
      
      override fun onChildDraw(/* canvas, viewHolder, dX, dY, actionState, isCurrentlyActive */) {
          // Draw swipe indicators (read icon, bookmark icon)
      }
  }
  ```

**Manga Reference**: `MangaDetailsController.kt` chapter swipe implementation

---

#### 5.4 Implement Text Highlighting (Reader)
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/reader/NovelReaderActivity.kt` line 371

**Implementation Strategy**:
- Add text selection listeners to WebView/TextView
- Create highlight database table (chapter_id, start_pos, end_pos, color, note)
- Display highlights with background color
- Add highlight management UI

**Reference**: Kindle/Google Play Books highlighting systems

**Note**: This is a reader feature but is LOW priority and optional.

---

#### 5.5 Implement Bookmark System
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/reader/tracking/CharacterPositionTracker.kt` lines 120, 136

**Implementation Strategy**:
- Create bookmark database table (novel_id, chapter_id, character_position, note, created_at)
- Implement bookmark creation from reader
- Display bookmark indicators in chapter list
- Jump to bookmarks from details screen

**Note**: This is a reader feature but is LOW priority and optional.

---

#### 5.6 Implement Cover Image Loading (Library)
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/library/NovelLibraryAdapter.kt` line 128

**Implementation Strategy**:
```kotlin
Coil3.imageLoader(context).enqueue(
    ImageRequest.Builder(context)
        .data(novel.posterUrl)
        .target(coverImageView)
        .placeholder(R.drawable.cover_placeholder)
        .error(R.drawable.cover_error)
        .crossfade(true)
        .build()
)
```

**Manga Reference**: Manga library adapter cover loading

---

## Phase 6: Polish & Optimization (LOW PRIORITY) [RENUMBERED FROM PHASE 7]
**Goal**: Code cleanup, performance improvements, edge case handling  
**Estimated Effort**: 2 days

### Tasks

#### 6.1 Implement Novel Cover Cache
**Files to Modify**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt` line 152

**Implementation Strategy**:
- Create `NovelCoverCache` class similar to `CoverCache`
- Cache novel cover images locally
- Implement cover update/refresh logic

---

#### 6.2 Code Cleanup & Documentation
**Files to Review**: All modified files

**Tasks**:
- Remove all `// TODO` comments
- Add KDoc documentation to public methods
- Remove commented-out code blocks
- Optimize imports
- Run `./gradlew formatKotlin`
- Run `./gradlew lintKotlin`

---

## Migration Strategy from Manga Code

For each feature implementation, follow this pattern:

1. **Locate Manga Implementation**
   - Find the corresponding manga feature in codebase
   - Read and understand the implementation
   - Identify dependencies and utilities used

2. **Identify Differences**
   - Novel-specific: No downloads, no pages, character-based tracking
   - Manga-specific: Image handling, download system, page-based navigation
   - Shared: Tracking, filtering, sorting, favorites, categories

3. **Adapt, Don't Copy**
   - Replace `Manga` → `Novel`
   - Replace `Chapter` → `NovelChapter`
   - Replace `MangaRepository` → `NovelRepository`
   - Remove download-related logic
   - Keep business logic patterns identical

4. **Use Modern Architecture**
   - Novel uses modern `yokai.domain.*` namespace
   - Use StateFlow instead of LiveData where applicable
   - Use Kotlin Coroutines consistently
   - Follow repository pattern strictly

5. **Test Thoroughly**
   - Build project after each phase
   - Manual testing of each feature
   - Compare behavior with manga equivalent
   - Verify database operations

---

## Dependencies & Setup

### Required Gradle Dependencies
All dependencies already present in project. No new dependencies needed.

### Database Migrations
- Phase 3 requires database migration for `novel_track` table
- Create migration file: `data/src/commonMain/sqldelight/tachiyomi/migrations/XX.sqm`
- Test migration on debug builds before release

### Preference Keys
Add to `PreferencesHelper`:
```kotlin
// Novel-specific preferences
fun novelReaderFontSize() = rxPrefs.getInteger("novel_reader_font_size", 16)
fun novelReaderFontFamily() = rxPrefs.getString("novel_reader_font_family", "sans-serif")
fun novelReaderLineSpacing() = rxPrefs.getFloat("novel_reader_line_spacing", 1.5f)
fun novelReaderBackgroundColor() = rxPrefs.getInteger("novel_reader_bg_color", Color.WHITE)
fun novelReaderTextColor() = rxPrefs.getInteger("novel_reader_text_color", Color.BLACK)
fun novelFilteredScanlators(novelId: Long) = rxPrefs.getStringSet("novel_${novelId}_filtered_scanlators", emptySet())
```

---

## Testing Checklist

### Phase 1 Testing
- [ ] Chapters filter by read status
- [ ] Chapters filter by bookmark status
- [ ] Chapters sort by source order
- [ ] Chapters sort by chapter number
- [ ] Chapters sort by upload date
- [ ] Sort direction (ascending/descending)
- [ ] Filter reset to defaults
- [ ] Sort reset to defaults
- [ ] Scroll type detection (tens/volumes/seasons)

### Phase 2 Testing
- [ ] Delete single chapter
- [ ] Delete multiple chapters
- [ ] Delete all chapters
- [ ] Scanlator filter application
- [ ] Scanlator filter persistence

### Phase 3 Testing
- [ ] Goodreads OAuth login
- [ ] Search for novel on Goodreads
- [ ] Add Goodreads tracking
- [ ] Refresh Goodreads tracking status
- [ ] Update reading progress on Goodreads
- [ ] Update rating on Goodreads
- [ ] Update reading status (Want to Read, Currently Reading, Read)
- [ ] Remove Goodreads tracking
- [ ] Two-way sync: Goodreads → App chapter progress
- [ ] Two-way sync: App chapter progress → Goodreads

### Phase 4 Testing
- [ ] Edit novel dialog opens
- [ ] Edit novel metadata saves
- [ ] Custom cover upload
- [ ] Range mark as read
- [ ] Range mark as unread
- [ ] Hide/show title

### Phase 5 Testing
- [ ] Reader preferences load
- [ ] Reader preferences apply
- [ ] Navigate to novel details from reader
- [ ] Reader settings dialog
- [ ] Text appearance changes

---

## Success Metrics

- **Code Quality**: Zero compilation errors, zero lint warnings
- **Feature Parity**: Novel system has 90%+ feature parity with manga system (excluding download system)
- **Performance**: Novel details screen loads < 500ms
- **User Experience**: Smooth transitions, no UI jank, intuitive interactions
- **Maintainability**: All TODOs resolved, code documented, architecture consistent

---

## Notes

- **QuickNovel Project**: This plan is for Miko (Yōkai) project. QuickNovel has different architecture.
- **Branch Strategy**: Implement on `novel-details-rebuild-backup`, merge to `master` when complete.
- **Breaking Changes**: Phase 3 (tracking) requires database migration - coordinate with release schedule.
- **Future Work**: Novel download system (if needed), advanced text rendering, TTS improvements.

---

## References

### Key Manga Files for Reference
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsPresenter.kt` - Main presenter logic
- `app/src/main/java/eu/kanade/tachiyomi/util/chapter/ChapterFilter.kt` - Filtering logic
- `app/src/main/java/eu/kanade/tachiyomi/util/chapter/ChapterSort.kt` - Sorting logic
- `domain/src/commonMain/kotlin/yokai/domain/manga/` - Manga domain layer
- `data/src/commonMain/kotlin/yokai/data/repository/MangaRepositoryImpl.kt` - Repository pattern

### Novel Architecture Files
- `domain/src/commonMain/kotlin/yokai/domain/novel/Novel.kt` - Novel domain model
- `data/src/commonMain/kotlin/yokai/data/repository/NovelRepositoryImpl.kt` - Novel repository
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt` - Novel presenter

---

**END OF PLAN**
