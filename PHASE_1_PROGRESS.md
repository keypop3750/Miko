# Phase 1 Implementation Progress
**Date**: October 22, 2025  
**Status**: IN PROGRESS

---

## Completed Tasks

### ✅ 1.1 Novel Chapter Filter System - COMPLETE
**Created**: `app/src/main/java/eu/kanade/tachiyomi/util/novel/NovelChapterFilter.kt` (87 lines)

- Filters chapters by read status (Read/Unread)
- Filters chapters by bookmark status (Bookmarked/Not Bookmarked)
- Removed download filtering (novels don't have downloads)
- Reader filtering with skip read/filtered preferences support
- Selected chapter always included in reader filtering

**Key Methods**:
- `filterChapters(List<NovelChapter>, Novel): List<NovelChapter>`
- `filterChaptersForReader(List<NovelChapter>, Novel, NovelChapter?): List<NovelChapter>`

---

### ✅ 1.2 Novel Chapter Sort System - COMPLETE
**Created**: `app/src/main/java/eu/kanade/tachiyomi/util/novel/NovelChapterSort.kt` (113 lines)

- Sort by source order (default)
- Sort by chapter number (natural order)
- Sort by upload date
- Ascending/descending support
- Next chapter and next unread chapter queries

**Key Methods**:
- `getChaptersSorted(rawChapters, andFiltered, filterForReader, currentChapter): List<NovelChapter>`
- `getNextChapter(rawChapters, andFiltered): NovelChapter?`
- `getNextUnreadChapter(rawChapters, andFiltered): NovelChapter?`
- `sortComparator(ignoreAsc): Comparator<NovelChapter>`

---

### ✅ Novel Extension Functions - COMPLETE
**Created**: `app/src/main/java/eu/kanade/tachiyomi/util/novel/NovelExtensions.kt` (121 lines)

**Properties Added to Novel**:
- `usesLocalSort: Boolean` - Check if novel uses per-novel sort settings
- `usesLocalFilter: Boolean` - Check if novel uses per-novel filter settings
- `sorting: Int` - Get chapter sorting order
- `readFilter: Int` - Get read filter
- `bookmarkedFilter: Int` - Get bookmark filter
- `chapterDisplayMode: Int` - Get chapter display mode

**Extension Functions**:
- `sortDescending(PreferencesHelper): Boolean` - Get sort direction
- `chapterOrder(PreferencesHelper): Int` - Get sort order (local or global)
- `readFilter(PreferencesHelper): Int` - Get read filter (local or global)
- `bookmarkedFilter(PreferencesHelper): Int` - Get bookmark filter (local or global)
- `downloadedFilter(PreferencesHelper): Int` - Always returns 0 (novels don't download)
- `hideChapterTitle(PreferencesHelper): Boolean` - Get hide title setting

**Mutation Functions**:
- `setChapterOrder(Int): Novel` - Set sort order and enable local sort
- `setChapterSortDescending(Boolean): Novel` - Set sort direction
- `setReadFilter(Int): Novel` - Set read filter and enable local filter
- `setBookmarkFilter(Int): Novel` - Set bookmark filter
- `setChapterDisplayMode(Int): Novel` - Set display mode

---

### ✅ Database Schema Updates - COMPLETE

**Created**: `data/src/commonMain/sqldelight/tachiyomi/migrations/30.sqm`
- Added `chapter_flags INTEGER NOT NULL DEFAULT 0` column to `novels` table

**Modified**: `data/src/commonMain/sqldelight/tachiyomi/data/novels.sq`
- Added `chapter_flags` to table schema
- Added `chapter_flags` to `insertNovel` query
- Added `chapter_flags` to `updateNovel` query

---

### ✅ Domain Model Updates - COMPLETE

**Modified**: `domain/src/commonMain/kotlin/yokai/domain/novel/Novel.kt`
- Added `chapterFlags: Int = 0` property to Novel data class
- Added `CHAPTER_SORT_DIR_ASC = 0x00000000` constant
- Added `CHAPTER_SORT_DIR_DESC = 0x00000400` constant
- Added `CHAPTER_SORT_DIR_MASK = 0x00000400` constant

---

### ✅ Repository Updates - COMPLETE

**Modified**: `data/src/commonMain/kotlin/yokai/data/repository/NovelRepositoryImpl.kt`
- Updated `mapNovel()` to include chapter_flags parameter
- Updated `insertNovel()` to save chapter_flags
- Updated `updateNovel()` to update chapter_flags
- Updated `update()` to handle NovelUpdate.chapterFlags
- Updated `updateAll()` to handle chapterFlags in batch updates

---

## In Progress Tasks

### 🔄 1.3 Implement Presenter Filter/Sort Methods
**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt`

**TODO**: Implement the following methods:
- Line 75: `currentFilters()` - Return filter description string
- Line 115-116: `sortingOrder()`, `sortDescending()` - Return sort settings
- Lines 117-125: Sort and filter methods (setGlobalChapterSort, setSortOrder, etc.)

### ⏳ 1.4 Apply Filtering/Sorting to Chapter List
**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt`

**TODO**: Inject NovelChapterFilter, create NovelChapterSort, apply to chapters flow

### ⏳ 1.5 Implement Scroll Type Detection
**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt` line 52

**TODO**: Detect TENS_OF_CHAPTERS/MULTIPLE_VOLUMES/MULTIPLE_SEASONS from chapter names

---

## Build Status

### Current Errors (as of last build):
1. ❌ `ChapterFilterLayout.kt` - Missing `downloadedFilter` references
2. ❌ `ChaptersSortBottomSheet.kt` - Missing `hideChapterTitle` references
3. ❌ `NovelDetailsController.kt` - Type mismatches with ChapterItem

**Status**: Extension functions created to resolve items 1-2. Controller issues need investigation.

---

## Next Steps

1. Build project to verify extension function fixes
2. Investigate controller type mismatches (ChapterItem vs NovelChapter)
3. Complete presenter method implementations (1.3)
4. Apply filters/sorting to chapter flow (1.4)
5. Implement scroll type detection (1.5)
6. Full integration test

---

## Files Created
- `/app/src/main/java/eu/kanade/tachiyomi/util/novel/NovelChapterFilter.kt`
- `/app/src/main/java/eu/kanade/tachiyomi/util/novel/NovelChapterSort.kt`
- `/app/src/main/java/eu/kanade/tachiyomi/util/novel/NovelExtensions.kt`
- `/data/src/commonMain/sqldelight/tachiyomi/migrations/30.sqm`

## Files Modified
- `/domain/src/commonMain/kotlin/yokai/domain/novel/Novel.kt`
- `/data/src/commonMain/sqldelight/tachiyomi/data/novels.sq`
- `/data/src/commonMain/kotlin/yokai/data/repository/NovelRepositoryImpl.kt`

## Files Deleted
- `/app/src/main/java/eu/kanade/tachiyomi/util/novel/NovelUtil.kt` (conflicting definitions)

---

**Last Updated**: October 22, 2025, 18:45
