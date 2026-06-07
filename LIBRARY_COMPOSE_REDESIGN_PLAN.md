# Library Compose UI Redesign Plan

## Overview
Complete redesign of the Compose Library to match the original Yokai/Miko-master app's design and functionality. This plan is based on thorough analysis of the original implementation including `LibraryController.kt` (2201 lines), `FilterBottomSheet.kt` (661 lines), and related display/filter components.

## Current State vs Target State

### Screenshots Reference:
- **Current**: Images 1-3 (basic implementation)
- **Target**: Images 4-11 (original Yokai app)

### Original Implementation Reference:
```
Miko-master/app/src/main/java/eu/kanade/tachiyomi/ui/library/
├── LibraryController.kt        # Main controller (2201 lines)
├── LibraryPresenter.kt         # Data/business logic
├── LibraryGroup.kt             # Group by constants (8 types)
├── LibrarySort.kt              # Sort enum with icons (9 types)
├── LibraryHeaderItem.kt        # Category headers
├── LibraryMangaItem.kt         # Individual manga items
├── LibraryCategoryAdapter.kt   # RecyclerView adapter
├── display/
│   ├── TabbedLibraryDisplaySheet.kt
│   ├── LibraryDisplayView.kt
│   ├── LibraryBadgesView.kt
│   └── LibraryCategoryView.kt
└── filter/
    ├── FilterBottomSheet.kt    # Complex filter system (661 lines)
    ├── ExpandedFilterSheet.kt  # Full-screen filter mode
    └── FilterTagGroup.kt       # Individual filter chips
```

---

## Phase 1: Top App Bar Redesign

### 1.1 Search Bar (Priority: HIGH)
**Current**: Static "Library" title with search icon
**Target**: Expandable search bar with placeholder suggestions

```
┌─────────────────────────────────────────────────────┐
│ 🔍 Search "Action, Seinen"        [📚]  ≡    ⋮    │
└─────────────────────────────────────────────────────┘
```

**Changes needed:**
- [ ] Replace TopAppBar with custom SearchTopAppBar
- [ ] Add search suggestion from preferences (`librarySearchSuggestion`)
- [ ] Long-press search field to insert suggestion
- [ ] Expandable/collapsible search field
- [ ] **Show All Categories Toggle** [📚] - appears during search when not in "show all" mode
- [ ] Filter icon (≡) triggers filter bottom sheet
- [ ] Overflow menu (⋮) with actions

**Search Behavior (from original):**
- When search starts and not showing all categories → auto-show categories overlay
- `forceShowAllCategories` toggle during search
- Search hint: "Search your library" or suggestion in quotes
- `SearchGlobalItem` added to adapter during search

### 1.2 Overflow Menu Items
- [ ] Turn on/off Incognito mode
- [ ] Navigate to Settings
- [ ] Navigate to Stats
- [ ] Show About dialog (with version)
- [ ] Navigate to Help

---

## Phase 2: Category Header (LibraryHeaderItem)

### 2.1 Category Row
**Current**: Missing entirely
**Target**: Shows category name, count, current sort, and actions

```
┌─────────────────────────────────────────────────────┐
│ [▼] Default (33)                  Date fetched ↓ ⋮ │
└─────────────────────────────────────────────────────┘
```

**Components:**
- [ ] Collapse/Expand arrow (▼/▶) for category visibility
- [ ] Category name
- [ ] Item count (optional via `categoryNumberOfItems` preference)
- [ ] Current sort option (clickable to open sort sheet)
- [ ] Sort direction indicator (↑/↓)
- [ ] Overflow menu (⋮): Select all, Update category, Manage category

**Header Actions (from LibraryCategoryAdapter.LibraryListener):**
- `toggleCategoryVisibility(position)` - collapse/expand
- `updateCategory(position)` - trigger library update for category
- `manageCategory(position)` - open category management dialog
- `sortCategory(catId, sortBy)` - change category sort
- `selectAll(position)` - select all in category
- `allSelected(position)` - check if all selected

### 2.2 Category Visibility System
- [ ] Categories can be collapsed/expanded individually
- [ ] "Expand/Collapse All" button in filter sheet
- [ ] Remember collapsed state per category
- [ ] Hidden categories show minimal height with just name

### 2.3 Header Gestures (LibraryHeaderGestureDetector)
- [ ] Swipe left/right on header to trigger category update
- [ ] Visual feedback with progress drawables during swipe
- [ ] Haptic feedback on threshold crossed

### 2.4 Dynamic Category Features
- [ ] Source icon display for source-grouped categories
- [ ] Language flag icon display for language-grouped categories
- [ ] "Long press category" tutorial tooltip on first collapse

---

## Phase 3: Unified Bottom Sheet with Tabs (TabbedLibraryDisplaySheet)

### 3.1 Bottom Sheet Structure
**Current**: Separate bottom sheets for Filter, Display, Sort
**Target**: Single tabbed bottom sheet (matches original `TabbedLibraryDisplaySheet`)

```
┌─────────────────────────────────────────────────────┐
│  Display  │  Badges  │  Categories  │          ⚙️  │
├─────────────────────────────────────────────────────┤
│                                                     │
│  [Content based on selected tab]                    │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 3.2 Display Tab (LibraryDisplayView)
**From original `library_display_layout.xml`:**

- [ ] **Layout Radio Group** (`displayGroup`):
  - ○ List
  - ○ Compact Grid  
  - ● Comfortable Grid
  - ○ Cover-only grid

- [ ] **Uniform Grid Checkbox** (`uniformGrid`):
  - Binds to `uiPreferences.uniformGrid()`
  - When enabled, disables staggered grid option

- [ ] **Staggered Grid Checkbox** (`staggeredGrid`):
  - Text includes "(BETA)" tag via `addBetaTag()`
  - Disabled when uniform grid is checked
  - Binds to `preferences.useStaggeredGrid()`

- [ ] **Outline on Covers Checkbox** (`outlineOnCovers`):
  - Binds to `uiPreferences.outlineOnCovers()`

- [ ] **Grid Size Slider** (`gridSeekbar`):
  - Range: 1-5 (internally 0.5-2.5 via formula)
  - Label formatter shows: "Portrait: X • Landscape: Y"
  - Real-time preview of rows per orientation
  - Reset button to default (value 3)

- [ ] **Grid Size Text** (`gridSizeText`):
  - Shows "Grid size" with subtitle "X per row"

### 3.3 Badges Tab (LibraryBadgesView)
**From original `library_badges_layout.xml`:**

- [ ] **Unread Badge Radio Group** (`unreadBadgeGroup`):
  - ○ Hide unread badges
  - ○ Show unread badges (dot only)
  - ● Show unread count (number)
  - Binds to `preferences.unreadBadgeType()`
  - On change: `presenter.requestUnreadBadgesUpdate()`

- [ ] **Hide Start Reading Button** (`hideReading`):
  - Checkbox binding to `preferences.hideStartReadingButton()`

- [ ] **Download Badge** (`downloadBadge`):
  - Checkbox binding to `preferences.downloadBadge()`
  - On change: `presenter.requestDownloadBadgesUpdate()`

- [ ] **Language Badge** (`languageBadge`):
  - Checkbox binding to `preferences.languageBadge()`
  - On change: `presenter.requestLanguageBadgesUpdate()`

- [ ] **Show Number of Items** (`showNumberOfItems`):
  - Checkbox binding to `preferences.categoryNumberOfItems()`
  - Shows count in category headers

### 3.4 Categories Tab (LibraryCategoryView)
**From original `library_category_layout.xml`:**

- [ ] **Show All Categories** (`showAll`):
  - Checkbox binding to `preferences.showAllCategories()`
  - On change: `presenter.updateLibrary()`
  - Enables/disables "Show category in title" option

- [ ] **Show Category in Title** (`categoryShow`):
  - Checkbox binding to `preferences.showCategoryInTitle()`
  - Only enabled when "Show all categories" is checked
  - On change: `controller.showMiniBar()`

- [ ] **Move Dynamic to Bottom** (`dynamicToBottom`):
  - Text: "Move collapsed dynamic categories to bottom"
  - Subtitle: "When grouping by sources, tags"
  - Binding to `preferences.collapsedDynamicAtBottom()`
  - On change: `presenter.updateLibrary()`

- [ ] **Show Empty Categories While Filtering** (`showEmptyCatsFiltering`):
  - Checkbox binding to `preferences.showEmptyCategoriesWhileFiltering()`
  - On change: `presenter.requestFilterUpdate()`

- [ ] **Hide Hopper Spinner** (`hideHopperSpinner`):
  - Dropdown with 3 options:
    - 0: Always show
    - 1: Hides when scrolling
    - 2: Always hide
  - Updates both `preferences.hideHopper()` and `preferences.autohideHopper()`
  - On change: `controller.hideHopper()`, `controller.resetHopperY()`

- [ ] **Hopper Long Press Action** (`hopperLongPress`):
  - Dropdown binding to `preferences.hopperLongPressAction()`
  - Options (values 0-5):
    - 0: Search
    - 1: Toggle all category visibility
    - 2: Show display options
    - 3: Show group options
    - 4: Open random manga (in category)
    - 5: Open random manga (global)

- [ ] **Add/Edit Categories Button** (`addCategoriesButton`):
  - Only visible in LibraryController (not settings)
  - Navigates to `CategoryController`

### 3.5 Settings Gear Icon
- [ ] Add gear icon in tab bar that navigates to `SettingsLibraryController`
- [ ] Tooltip: "More library settings"

---

## Phase 4: Category Hopper (Floating Control)

### 4.1 Hopper UI Structure
**Current**: Missing
**Target**: Complex floating pill with navigation and configurable actions

```
                    ┌───────────────────┐
                    │  ◀   [📁]   ▶    │
                    └───────────────────┘
```

**From original `rounded_category_hopper.xml` and LibraryController:**

**Components:**
- [ ] **Up/Left Arrow** (`upCategory`):
  - Navigate to previous category
  - Alpha 0.25 when at top, 1.0 otherwise
  - Long-press: Scroll to top of list
  - Icon changes based on mode:
    - All categories: `ic_expand_less_24dp` (▲)
    - Single category: `ic_arrow_start_24dp` (◀)

- [ ] **Category Button** (`categoryButton`):
  - Shows current group icon (from `LibraryGroup.groupTypeDrawableRes`)
  - Click: Opens category jump menu (MaterialMenuSheet)
  - Long-press: Configurable action (0-5)

- [ ] **Down/Right Arrow** (`downCategory`):
  - Navigate to next category
  - Alpha 0.25 when at bottom, 1.0 otherwise
  - Long-press: Scroll to bottom of list
  - Icon changes based on mode:
    - All categories: `ic_expand_more_24dp` (▼)
    - Single category: `ic_arrow_end_24dp` (▶)

### 4.2 Hopper Behavior
**From LibraryController.kt lines 740-900:**

- [ ] **Gravity/Position** (`hopperGravity`):
  - 0: Left aligned
  - 1: Center aligned
  - 2: Right aligned
  - First-time users see random position + slide tutorial

- [ ] **Auto-hide on Scroll** (`autohideHopper`):
  - Animates offset when scrolling
  - `hopperOffset` tracks current hide amount
  - `maxHopperOffset` = 55dp + bottom insets
  - Snaps to hidden/shown based on scroll position

- [ ] **Slide Tutorial Animation**:
  - Shows on first use (`shownHopperSwipeTutorial`)
  - Animates left-right to show draggability
  - 5-part animation sequence

- [ ] **Category Text Popup** (`jumperCategoryText`):
  - Shows category name when scrolling between categories
  - Fades out after 2 seconds
  - Position anchored to hopper

- [ ] **Y Position Updates**:
  - Stays above filter bottom sheet
  - Stays above bottom nav when visible
  - Handles IME (keyboard) visibility

### 4.3 Category Jump Menu
**When center button clicked:**
```
┌─────────────────────────────────────┐
│      Jump to category               │
├─────────────────────────────────────┤
│  Default (33)                    ✓  │
│  Reading (12)                       │
│  Completed (45)                     │
│  Plan to Read (8)                   │
└─────────────────────────────────────┘
```

- [ ] Lists all categories with optional counts
- [ ] Highlights current category
- [ ] On selection: `scrollToHeader(categoryOrder)`

---

## Phase 5: Filter Bottom Sheet (FilterBottomSheet - COMPLEX)

### 5.1 Filter Sheet Structure
**This is MORE complex than initially planned. From FilterBottomSheet.kt (661 lines):**

```
┌─────────────────────────────────────────────────────┐
│ ─────────────────── (drag handle) ─────────────────│
├─────────────────────────────────────────────────────┤
│ [⊞] │ Read progress │ Unread │ Downloaded │ ...   │ (scrollable)
├─────────────────────────────────────────────────────┤
│  [↕] Expand/Collapse All    [📁] Group by    [⚙]  │
└─────────────────────────────────────────────────────┘
```

### 5.2 Filter Tag Groups (8 Types)
**Each filter has 3 states: IGNORE (0), INCLUDE (1), EXCLUDE (2)**

1. **Read Progress** (`unreadProgress`):
   - "Not started" / "In progress"
   - Mutually exclusive with Unread filter

2. **Unread** (`unread`):
   - "Has unread" / "Read all"
   - Mutually exclusive with Read Progress filter

3. **Downloaded** (`downloaded`):
   - "Downloaded" / "Not downloaded"
   - Binding: `preferences.filterDownloaded()`

4. **Completed** (`completed`):
   - "Completed" / "Ongoing"
   - Binding: `preferences.filterCompleted()`

5. **Bookmarked** (`bookmarked`):
   - "Bookmarked" / "Not bookmarked"
   - Binding: `preferences.filterBookmarked()`

6. **Tracked** (`tracked`):
   - "Tracked" / "Not tracked"
   - Only shown if user has logged tracking services
   - When activated, shows additional tracker-specific filters
   - Binding: `preferences.filterTracked()`

7. **Content Type** (`contentType`):
   - "Manga" / "Novels" (when novel support enabled)
   - Binding: `preferences.filterContentType()`

8. **Series Type** (`mangaType`):
   - "Manga" / "Manhua" / "Manhwa" / "Comic"
   - Only shown if library has manhwa content
   - Binding: `preferences.filterMangaType()`

### 5.3 Filter Behavior
- [ ] **Filter Order**: Customizable via `preferences.filterOrder()`
- [ ] **Clear Button**: Appears when any filter is active
- [ ] **Active Filters First**: Activated filters move to front of scroll
- [ ] **Tracker Sub-filters**: When "Tracked" activated, shows service-specific filters

### 5.4 Bottom Action Row
- [ ] **Expand/Collapse All** (`expandCategories`):
  - Icon button to toggle all category visibility
  - Calls `presenter.toggleAllCategoryVisibility()`

- [ ] **Group By** (`groupBy`):
  - Icon shows current group type
  - Calls `showGroupOptions()` to open group sheet

- [ ] **View Options** (`viewOptions`):
  - Opens `TabbedLibraryDisplaySheet`

### 5.5 Expanded Filter Sheet
- [ ] **Full-screen filter mode** (`ExpandedFilterSheet`):
  - Opened via filter button [⊞]
  - More detailed filter controls
  - Additional sorting within filters

### 5.6 Sheet Behavior
- [ ] **Peek Height**: 60dp + bottom insets
- [ ] **Auto-hide on scroll**: Hides after 1000px scroll
- [ ] **States**: Hidden → Collapsed → Expanded
- [ ] **Tip Tooltip**: Shows "Tap library to show filters" on first use

---

## Phase 6: Group By Sheet

### 6.1 Group Options (8 Types from LibraryGroup.kt)
**Add new bottom sheet:**

```
┌─────────────────────────────────────────────────────┐
│           Group library by...                       │
├─────────────────────────────────────────────────────┤
│  📁 Categories                                   ✓  │
│  🏷️ Tag                                            │
│  🔌 Sources                                         │
│  ⏱️ Status                                          │
│  👤 Author                                          │
│  🔄 Tracking status        (only if logged in)     │
│  🌐 Language                                        │
│  📦 Ungrouped              (only if >1 category)   │
└─────────────────────────────────────────────────────┘
```

**Constants from LibraryGroup.kt:**
```kotlin
BY_DEFAULT = 0      // Categories (or Ungrouped if no categories)
BY_TAG = 1          // Group by manga tags
BY_SOURCE = 2       // Group by source
BY_STATUS = 3       // Group by manga status
BY_TRACK_STATUS = 4 // Group by tracking status
UNGROUPED = 5       // No grouping
BY_AUTHOR = 6       // Group by author
BY_LANGUAGE = 7     // Group by source language
```

**Icons from `groupTypeDrawableRes()`:**
- Categories: `ic_label_outline_24dp`
- Tag: `ic_style_24dp`
- Sources: `ic_browse_24dp`
- Status: `ic_progress_clock_24dp`
- Author: `ic_author_24dp`
- Tracking: `ic_sync_24dp`
- Language: `ic_translate_24dp`
- Ungrouped: `ic_ungroup_24dp`

### 6.2 Group Selection Behavior
- [ ] Only show "Tracking status" if `presenter.isLoggedIntoTracking`
- [ ] Only show "Ungrouped" if `presenter.isCategoryMoreThanOne()`
- [ ] On selection: `presenter.groupType = item; presenter.updateLibrary()`
- [ ] Persist to `preferences.groupLibraryBy()`

---

## Phase 7: Sort Sheet Redesign

### 7.1 Sort Options (9 Types from LibrarySort.kt)
**Current**: Card-based buttons
**Target**: List with icons and direction indicator

```
┌─────────────────────────────────────────────────────┐
│                    Sort by                          │
├─────────────────────────────────────────────────────┤
│  🔤 Title                                           │
│  🕐 Last read                                       │
│  📥 Latest chapter                                  │
│  👁️ Unread                                          │
│  🔢 Total chapters                                  │
│  ❤️ Date added                                      │
│  📋 Date fetched                                 ↓  │
│  ↕️ Drag & Drop                                     │
│  🔀 Random                                          │
└─────────────────────────────────────────────────────┘
```

**Sort enum from LibrarySort.kt:**
```kotlin
Title(0, MR.strings.title, R.drawable.ic_sort_by_alpha_24dp)
LastRead(1, MR.strings.last_read, R.drawable.ic_recent_read_outline_24dp)
LatestChapter(2, MR.strings.latest_chapter, R.drawable.ic_new_releases_24dp)
Unread(3, MR.strings.unread, R.drawable.ic_eye_24dp)
TotalChapters(4, MR.strings.total_chapters, R.drawable.ic_sort_by_numeric_24dp)
DateAdded(5, MR.strings.date_added, R.drawable.ic_heart_outline_24dp)
DateFetched(6, MR.strings.date_fetched, R.drawable.ic_calendar_text_outline_24dp)
DragAndDrop(7, MR.strings.drag_and_drop, R.drawable.ic_swap_vert_24dp)
Random(8, MR.strings.random, R.drawable.ic_shuffle_24dp)
```

### 7.2 Sort Behavior
- [ ] **Inverted sort default**: LastRead, DateAdded, LatestChapter, DateFetched
- [ ] **Non-directional sorts**: DragAndDrop, Random (no ascending/descending)
- [ ] **Per-category sorting**: Categories can have different sort orders
- [ ] **Category value encoding**: Uses char codes for persistence

---

## Phase 8: Action Mode (Multi-Selection)

### 8.1 Selection UI
**When items are selected:**
- [ ] Action mode toolbar appears
- [ ] Title shows: "X selected"
- [ ] Selection highlights on items
- [ ] Long-press + drag for range selection

### 8.2 Action Mode Menu (R.menu.library_selection)
```
┌─────────────────────────────────────────────────────┐
│ 3 selected                    📁 🔗 🗑️ 📥 ✓ ✗ 🔄  │
└─────────────────────────────────────────────────────┘
```

- [ ] **Move to Category** (`action_move_to_category`):
  - Only visible if `presenter.isCategoryMoreThanOne()`
  - Opens category selection sheet

- [ ] **Share** (`action_share`):
  - Only visible if any manga is not local
  - Shares manga URLs

- [ ] **Delete** (`action_delete`):
  - Multi-choice dialog:
    - ☑ Remove downloads
    - ☑ Remove from library
  - "Remove downloads" always checked

- [ ] **Download Unread** (`action_download_unread`):
  - Downloads all unread chapters for selected manga

- [ ] **Mark as Read** (`action_mark_as_read`):
  - Confirmation dialog
  - Undo snackbar support

- [ ] **Mark as Unread** (`action_mark_as_unread`):
  - Confirmation dialog
  - Undo snackbar support

- [ ] **Migrate** (`action_migrate`):
  - Only visible if any manga is not local
  - Opens PreMigrationController

---

## Phase 9: Library Update Integration

### 9.1 Swipe Refresh
- [ ] Pull-to-refresh triggers library update
- [ ] Different behavior based on mode:
  - All categories: Full update
  - Single category: Category-specific update
  - Grouped (non-default): Category-specific via index

### 9.2 Update Status
- [ ] Show update progress in category headers
- [ ] "Already in queue" detection
- [ ] Cancel button in snackbar
- [ ] Queue status via `LibraryUpdateJob.categoryInQueue()`

### 9.3 Update Snackbars
- [ ] "Updating library" with cancel action
- [ ] "Adding category to queue"
- [ ] "Already in queue"

---

## Phase 10: Advanced Features

### 10.1 Fast Scroll (LibraryFastScroll)
- [ ] Bubble text shows current position info
- [ ] Two modes: item-level scroll vs category-level scroll
- [ ] Category-level: touching far from handle jumps between categories
- [ ] Haptic feedback when crossing category boundaries
- [ ] Bubble position adjusts based on scroll mode

### 10.2 Search Global Item
- [ ] When searching, show "Search globally for: [query]" button
- [ ] Clicking navigates to GlobalSearchController
- [ ] Spans full width in grid layout

### 10.3 Placeholder Items (LibraryPlaceholderItem)
- [ ] Two types: Hidden (collapsed category) and Blank (empty/filtered)
- [ ] Hidden type stores collapsed items for quick expand
- [ ] Blank type shows category is empty or filtered out
- [ ] Supports filtering within hidden items

### 10.4 FilteredLibraryController
- [ ] Specialized controller for stats/filtered views
- [ ] Custom filters: status, sources, languages, tags, tracking score, start year, length
- [ ] No swipe refresh
- [ ] No filter bottom sheet
- [ ] Custom title support

### 10.5 Category Overlay (CategoryRecyclerView)
- [ ] Horizontal list of categories shown when searching
- [ ] Click to jump to category
- [ ] Selected category highlighted
- [ ] Auto-scrolls to center selected category
- [ ] Triggered by clicking header or starting search

### 10.6 Library Badges (LibraryBadge)
- [ ] Compound badge view with multiple indicators
- [ ] Unread count/dot with configurable background color
- [ ] Download count with "Local" text for local manga
- [ ] Language flag icon
- [ ] Angled dividers between badge sections
- [ ] Total chapters mode (different color scheme)

### 10.7 Drag and Drop Reordering
- [ ] Long-press to start drag
- [ ] Visual feedback during drag
- [ ] Can drag between categories (moves manga to new category)
- [ ] "Already in category" prevention
- [ ] Undo snackbar after move
- [ ] Disabled when filters active or in multi-select mode

---

## Phase 11: State Management Updates

### 10.1 LibraryViewModel Updates
- [ ] Add `groupBy: StateFlow<LibraryGroupBy>`
- [ ] Add `badgeSettings: StateFlow<BadgeSettings>`
- [ ] Add `categoryDisplaySettings: StateFlow<CategoryDisplaySettings>`
- [ ] Add `hopperSettings: StateFlow<HopperSettings>`
- [ ] Add `filterSettings: StateFlow<FilterSettings>`
- [ ] Add `activeCategory: StateFlow<Int>`
- [ ] Add `selectedItems: StateFlow<Set<Long>>`
- [ ] Add `isInActionMode: StateFlow<Boolean>`
- [ ] Add `searchQuery: StateFlow<String>`
- [ ] Add `forceShowAllCategories: StateFlow<Boolean>`

### 10.2 Category Navigation Methods
- [ ] `jumpToNextCategory(next: Boolean): Boolean`
- [ ] `scrollToCategory(category: Category?)`
- [ ] `scrollToHeader(position: Int)`
- [ ] `saveActiveCategory(category: Category)`

### 10.3 Filter Methods
- [ ] `requestFilterUpdate()`
- [ ] `clearFilters()`
- [ ] `setFilter(filterType, state)`

### 10.4 Selection Methods
- [ ] `toggleSelection(mangaId: Long)`
- [ ] `selectAll(categoryId: Int)`
- [ ] `clearSelection()`
- [ ] `isSelected(mangaId: Long): Boolean`

---

## Phase 11: New Data Classes

### 11.1 BadgeSettings ✅ (Already created)
```kotlin
data class BadgeSettings(
    val unreadBadgeMode: UnreadBadgeMode,
    val hideStartReadingButton: Boolean,
    val showLanguageBadges: Boolean,
    val showDownloadBadges: Boolean,
    val showItemCount: Boolean,
)

enum class UnreadBadgeMode { HIDE, SHOW_DOT, SHOW_COUNT }
```

### 11.2 CategoryDisplaySettings ✅ (Already created)
```kotlin
data class CategoryDisplaySettings(
    val showAllCategories: Boolean,
    val showCategoryInTitle: Boolean,
    val moveDynamicToBottom: Boolean,
    val showEmptyWhileFiltering: Boolean,
)
```

### 11.3 HopperSettings
```kotlin
data class HopperSettings(
    val hideMode: HopperHideMode,
    val longPressAction: HopperLongPressAction,
    val gravity: HopperGravity,
)

enum class HopperHideMode { ALWAYS_SHOW, HIDE_ON_SCROLL, ALWAYS_HIDE }
enum class HopperLongPressAction { SEARCH, TOGGLE_CATEGORIES, DISPLAY_OPTIONS, GROUP_OPTIONS, RANDOM_CATEGORY, RANDOM_GLOBAL }
enum class HopperGravity { LEFT, CENTER, RIGHT }
```

### 11.4 FilterSettings
```kotlin
data class FilterSettings(
    val downloaded: FilterState,
    val unread: FilterState,
    val unreadProgress: FilterState,
    val completed: FilterState,
    val bookmarked: FilterState,
    val tracked: FilterState,
    val contentType: FilterState,
    val mangaType: Int, // 0=none, TYPE_MANGA, TYPE_MANHUA, TYPE_MANHWA, TYPE_COMIC
    val trackerFilter: String,
)

enum class FilterState { IGNORE, INCLUDE, EXCLUDE }
```

### 11.5 LibraryGroupBy ✅ (Already created)
```kotlin
enum class LibraryGroupBy {
    DEFAULT,      // Categories
    TAG,
    SOURCE,
    STATUS,
    TRACK_STATUS,
    UNGROUPED,
    AUTHOR,
    LANGUAGE,
}
```

---

## Implementation Status & Order

### ✅ Completed
1. ✅ `LibraryTabbedBottomSheet.kt` - Basic structure created
2. ✅ `GroupByBottomSheet.kt` - Basic structure created
3. ✅ `LibraryUiState.kt` - Added BadgeSettings, CategoryDisplaySettings, enums
4. ✅ `LibraryViewModel.kt` - Added new state flows and update methods
5. ✅ `LibrarySortBottomSheet.kt` - Redesigned with icons
6. ✅ `LibraryComposeController.kt` - Fixed inheritance (extends BaseComposeController)

### 🔄 In Progress
7. 🔄 Phase 3 - Wire tabbed bottom sheet into LibraryScreen
8. 🔄 Phase 1 - TopAppBar with search suggestions

### ⏳ Pending
9. ⏳ Phase 2 - Category headers with actions
10. ⏳ Phase 4 - Category hopper (complex)
11. ⏳ Phase 5 - Filter bottom sheet (very complex)
12. ⏳ Phase 8 - Action mode (multi-selection)
13. ⏳ Phase 9 - Library update integration

---

## File Changes Required

### New Files to Create:
1. ✅ `LibraryTabbedBottomSheet.kt` - Unified tabbed sheet
2. ✅ `GroupByBottomSheet.kt` - Group by options
3. ⏳ `LibraryTopAppBar.kt` - Custom search app bar
4. ⏳ `CategoryHeader.kt` - Category header with actions
5. ⏳ `CategoryHopper.kt` - Floating hopper control
6. ⏳ `FilterBottomSheet.kt` - Complex filter sheet (Compose version)
7. ⏳ `LibraryActionMode.kt` - Selection action bar
8. ⏳ `HopperSettings.kt` - Hopper configuration data class
9. ⏳ `FilterSettings.kt` - Filter state data class

### Files to Modify:
1. ⏳ `LibraryScreen.kt` - Integrate all new components
2. ✅ `LibraryViewModel.kt` - Add state management
3. ✅ `LibraryUiState.kt` - Add state fields
4. ⏳ `LibraryComposeController.kt` - Add action mode, search handling

### Files to Delete/Deprecate:
1. ⏳ Separate filter/display sheets (merge into unified components)

---

## Estimated Effort (Updated)

| Phase | Complexity | Time Estimate | Status |
|-------|------------|---------------|--------|
| Phase 1 - TopAppBar | Medium | 2-3 hours | ⏳ |
| Phase 2 - Category Headers | Medium | 2-3 hours | ⏳ |
| Phase 3 - Tabbed Sheet | High | 4-5 hours | 🔄 70% |
| Phase 4 - Category Hopper | High | 4-5 hours | ⏳ |
| Phase 5 - Filter Sheet | Very High | 5-6 hours | ⏳ |
| Phase 6 - Group By | Low | 1 hour | ✅ Done |
| Phase 7 - Sort Sheet | Medium | 1-2 hours | ✅ Done |
| Phase 8 - Action Mode | High | 3-4 hours | ⏳ |
| Phase 9 - Update Integration | Medium | 2 hours | ⏳ |
| Phase 10-11 - State Management | Medium | 2 hours | 🔄 60% |
| **Total** | | **27-33 hours** | |

---

## Success Criteria

### Core Functionality
- [ ] Search bar matches original (expandable, suggestions, all-categories toggle)
- [ ] Category headers show name, count, sort with all actions
- [ ] Single tabbed bottom sheet with Display/Badges/Categories tabs
- [ ] Category hopper works with all features (gravity, auto-hide, long-press)
- [ ] Filter system with all 8 filter types + expanded mode
- [ ] Group by functionality with all 8 options
- [ ] Sort sheet with all 9 options + direction indicators
- [ ] Action mode with all selection operations
- [ ] Library update integration with progress tracking

### Preferences Persistence
- [ ] All display preferences save correctly
- [ ] All badge preferences save correctly
- [ ] All category preferences save correctly
- [ ] All hopper preferences save correctly
- [ ] All filter preferences save correctly
- [ ] Sort order per category saves correctly

### Navigation
- [ ] Overflow menu navigates to Settings/Stats/About/Help
- [ ] Settings gear navigates to SettingsLibraryController
- [ ] Add/Edit categories navigates to CategoryController
- [ ] Category jump menu works correctly

### Visual Parity
- [ ] Matches original Yokai screenshots (Images 4-11)
- [ ] Correct icons for all options
- [ ] Proper animations (hopper slide, filter transitions)
- [ ] Correct alpha states (hopper arrows at bounds)
