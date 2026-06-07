# Library Content Area - Compose UI Migration Plan

## Document Purpose
This document provides a comprehensive architectural analysis of the library content view system in the Miko project, mapping all components that need to change for migrating the main library content area to Compose UI with novel mode support.

**Scope**: Main content area (manga/novel grid/list display). Filter sheet and display options are already migrated to Compose.

---

## 1. Current Architecture Overview

### 1.1 High-Level Component Hierarchy

```
LibraryController (Conductor Controller)
├── LibraryControllerBinding (ViewBinding)
│   ├── SwipeRefreshLayout
│   │   └── FrameLayout (recycler_layout)
│   │       ├── CategoryRecyclerView (horizontal category tabs)
│   │       ├── AutofitRecyclerView (main content grid/list)
│   │       └── RecyclerCover (overlay for category popup)
│   ├── ComposeView (compose_view - unused for content)
│   ├── ComposeFilterSheet (Compose filter - already migrated)
│   └── CategoryHopperFrame (FAB-style category hopper)
│
└── LibraryPresenter (MVP Presenter)
    ├── ModeManager.currentMode (ContentType.MANGA | ContentType.NOVEL)
    ├── LibraryMap (categories -> List<LibraryItem>)
    └── Data Sources
        ├── GetLibraryManga (manga repository)
        └── NovelRepository (novel repository)
```

### 1.2 Content Flow Summary

```
User Action / Mode Change
        ↓
LibraryPresenter (subscribeMangaLibrary / subscribeNovelLibrary)
        ↓
Flow<List<LibraryItem>> (LibraryMangaItem | LibraryNovelItem)
        ↓
applyFilters() → applySort() → sectionLibrary()
        ↓
LibraryController.onNextLibraryUpdate(items)
        ↓
LibraryCategoryAdapter.setItems(items)
        ↓
FlexibleAdapter → RecyclerView → ViewHolders
        ↓
LibraryGridHolder / LibraryListHolder (renders content)
```

---

## 2. Component Breakdown

### 2.1 LibraryController.kt

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryController.kt`

**Size**: ~2,837 lines (massive controller)

**Key Responsibilities**:
- Main UI controller extending `BaseCoroutineController<LibraryControllerBinding, LibraryPresenter>`
- Manages RecyclerView setup and scroll behavior
- Handles action mode for multi-selection
- Manages category hopper (floating category navigator)
- Controls filter sheet visibility and state
- Handles navigation to manga/novel details
- Search functionality
- Pull-to-refresh library updates
- Drag-and-drop reordering

**Dependencies**:
- `LibraryCategoryAdapter` - FlexibleAdapter for RecyclerView
- `LibraryPresenter` - data and business logic
- `AutofitRecyclerView` - custom RecyclerView with auto-fit grid
- `CategoryRecyclerView` - horizontal category tabs
- `LibraryFilterState` (Compose) - filter state management
- `DisplayOptionsState` (Compose) - display options state
- `ModeManager` - content type switching

**Key Methods for Migration**:
| Method | Description | Migration Impact |
|--------|-------------|------------------|
| `onViewCreated()` | Sets up RecyclerView, adapters, scroll listeners | Replace with Compose setup |
| `setRecyclerLayout()` | Configures grid/list layout | Replace with Compose grid |
| `onNextLibraryUpdate()` | Updates adapter with new items | Replace with state flow |
| `scrollToHeader()` | Scrolls to category header | Compose LazyGrid scroll |
| `showCategories()` | Shows/hides category popup | Compose animation |
| `openManga()` / `openNovel()` | Navigation to details | Keep navigation logic |

**What Needs to Change**:
1. Replace `AutofitRecyclerView` + `LibraryCategoryAdapter` with Compose `LazyVerticalGrid`
2. Move scroll state management to Compose `LazyListState`
3. Replace ViewBinding-based UI with Compose UI
4. Keep presenter communication pattern (or migrate to ViewModel)
5. Replace action mode with Compose selection state

**Novel Mode Considerations**:
- Already handles `ContentType.NOVEL` via `ModeManager`
- Routes to `NovelReaderActivity` for novel items
- Uses same visual layout (grid/list) for novels

**Estimated Complexity**: 🔴 **HIGH** - Large controller, many responsibilities

---

### 2.2 LibraryPresenter.kt

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryPresenter.kt`

**Size**: ~1,841 lines

**Key Responsibilities**:
- Manages library data state (`LibraryMap`)
- Subscribes to manga/novel library flows
- Applies filters and sorting
- Handles category operations
- Download/unread badge calculations
- Grouping logic (by tag, source, status, etc.)

**Dependencies**:
- `GetLibraryManga` - manga repository
- `NovelRepository` - novel repository
- `GetCategories` - category repository
- `DownloadManager` / `DownloadCache`
- `TrackManager` - tracking services
- `ModeManager` - current content type

**Key Data Flows**:
```kotlin
// Manga flow
getMangaLibraryFlow() → combine(downloadCache.changes) → collectLatest { data ->
    categories = data.categories
    currentLibrary = data.items
    sectionLibrary(mangaMap)
}

// Novel flow (simpler)
novelRepository.getFavoriteNovels() → collectLatest { novels ->
    val novelMap = mapOf(defaultCategory to novelLibraryItems)
    sectionLibrary(novelMap)
}
```

**What Needs to Change**:
1. Expose library state as `StateFlow` instead of `LibraryMap` mutable property
2. Move filter/sort logic to composable-friendly functions
3. Consider migrating to `ViewModel` pattern for better Compose integration
4. Keep existing repository integration

**Novel Mode Considerations**:
- `subscribeNovelLibrary()` already handles novel content
- Novel items use simpler sorting (alphabetical)
- Novel metadata: `downloadCount`, `unreadCount`, `totalChapters`, `lastReadChapter`

**Estimated Complexity**: 🟡 **MEDIUM** - Data layer, mostly needs state exposure changes

---

### 2.3 LibraryItem.kt (Abstract Base)

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryItem.kt`

**Size**: ~60 lines

**Key Responsibilities**:
- Abstract base class for all library items (FlexibleAdapter pattern)
- Extends `AbstractSectionableItem<LibraryHolder, LibraryHeaderItem>`
- Implements `IFilterable<String>` for search filtering
- Provides layout constants: `LAYOUT_LIST`, `LAYOUT_COMPACT_GRID`, `LAYOUT_COMFORTABLE_GRID`, `LAYOUT_COVER_ONLY_GRID`

**Dependencies**:
- `FlexibleAdapter` framework
- `LibraryHolder` - ViewHolder base
- `LibraryHeaderItem` - section header

**What Needs to Change**:
1. **Remove FlexibleAdapter dependency** - Compose doesn't need this
2. Convert to simple data class hierarchy or sealed interface
3. Keep filter logic as extension functions

**Novel Mode Considerations**:
- Extended by both `LibraryMangaItem` and `LibraryNovelItem`
- Polymorphic binding in `bindViewHolder()`

**Estimated Complexity**: 🟢 **LOW** - Simple base class

---

### 2.4 LibraryMangaItem.kt

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryMangaItem.kt`

**Size**: ~170 lines

**Key Responsibilities**:
- Represents a manga item in the library grid
- Contains `LibraryManga` (database model with manga + tracking info)
- Provides view holder creation (`LibraryGridHolder` / `LibraryListHolder`)
- Search filtering by title, author, artist, source, genre
- Download count, unread count, source language tracking

**Key Properties**:
```kotlin
val manga: LibraryManga       // Core manga data
var downloadCount: Int = -1   // -1 = not loaded, 0+ = count
var unreadType: Int = 2       // Badge display type
var sourceLanguage: String?   // For language badges
```

**What Needs to Change**:
1. Remove `createViewHolder()` - Compose doesn't use ViewHolders
2. Remove `getLayoutRes()` - No XML layouts in Compose
3. Convert to pure data class
4. Keep filter logic as separate function

**Target Structure**:
```kotlin
data class LibraryMangaItem(
    val manga: LibraryManga,
    val header: LibraryHeaderItem,
    val downloadCount: Int = -1,
    val unreadType: Int = 2,
    val sourceLanguage: String? = null,
) {
    fun matchesFilter(query: String): Boolean { ... }
}
```

**Estimated Complexity**: 🟢 **LOW** - Simple data conversion

---

### 2.5 LibraryNovelItem.kt

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryNovelItem.kt`

**Size**: ~100 lines

**Key Responsibilities**:
- Represents a novel item in the library grid
- Contains `Novel` domain model
- Tracks novel-specific metadata: `totalChapters`, `lastReadChapter`
- Reuses manga grid/list layouts

**Key Properties**:
```kotlin
val novel: Novel
var downloadCount: Int = 0
var unreadCount: Int = 0
var language: String = ""
var lastReadChapter: String? = null
var totalChapters: Long = 0
```

**What Needs to Change**:
1. Same simplification as `LibraryMangaItem`
2. Remove ViewHolder creation logic
3. Pure data class

**Estimated Complexity**: 🟢 **LOW** - Simple data conversion

---

### 2.6 LibraryPlaceholderItem.kt

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryPlaceholderItem.kt`

**Size**: ~55 lines

**Key Responsibilities**:
- Placeholder for hidden or empty categories
- Two types: `Type.Hidden` (collapsed category) and `Type.Blank` (empty category)
- Used when filters result in no items

**What Needs to Change**:
1. Convert to sealed class/interface
2. Remove FlexibleAdapter inheritance

**Target Structure**:
```kotlin
sealed interface LibraryPlaceholder {
    data class Hidden(val title: String, val hiddenItems: List<LibraryItem>) : LibraryPlaceholder
    data class Blank(val categoryId: Int, val mangaCount: Int) : LibraryPlaceholder
}
```

**Estimated Complexity**: 🟢 **LOW**

---

### 2.7 LibraryHeaderItem.kt

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryHeaderItem.kt`

**Size**: ~60 lines

**Key Responsibilities**:
- Category section header in the grid
- Contains category reference via lambda (lazy loading)
- Creates `LibraryHeaderHolder` for header UI

**What Needs to Change**:
1. Convert to data class
2. Remove ViewHolder creation
3. Keep category reference pattern

**Target Structure**:
```kotlin
data class LibraryHeader(
    val categoryId: Int,
    val categoryProvider: (Int) -> Category,
) {
    val category: Category get() = categoryProvider(categoryId)
}
```

**Estimated Complexity**: 🟢 **LOW**

---

### 2.8 LibraryCategoryAdapter.kt

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryCategoryAdapter.kt`

**Size**: ~316 lines

**Key Responsibilities**:
- FlexibleAdapter implementation for library RecyclerView
- Manages items per category mapping
- Provides fast scroll bubble text
- Handles filtering by search query
- Sort mode bubble text generation

**Dependencies**:
- `FlexibleAdapter` framework
- `LibraryController` - listener callbacks
- Multiple repository interactors

**What Needs to Change**:
1. **REMOVE ENTIRELY** - Compose uses declarative lists
2. Fast scroll behavior → Compose `LazyListState` + custom fast scroller
3. Bubble text → Compose popup or tooltip
4. Filter logic → Move to ViewModel/Presenter

**Estimated Complexity**: 🔴 **HIGH** - Complex adapter logic needs complete replacement

---

### 2.9 LibraryHolder.kt (Base ViewHolder)

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryHolder.kt`

**Size**: ~85 lines

**Key Responsibilities**:
- Base ViewHolder for library items
- Handles badge updates, reading button visibility
- Manages drag state for reordering

**What Needs to Change**:
1. **REMOVE** - No ViewHolders in Compose
2. Badge logic → Compose component
3. Drag handling → Compose drag modifier

**Estimated Complexity**: 🟢 **LOW** - Delete and recreate as Compose component

---

### 2.10 LibraryGridHolder.kt

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryGridHolder.kt`

**Size**: ~315 lines

**Key Responsibilities**:
- Grid item ViewHolder (compact, comfortable, cover-only layouts)
- Binds manga/novel data to grid item views
- Handles cover image loading with Coil
- Touch coordinate tracking for shared element transitions
- Freeform cover ratio support (non-uniform grid)

**Dependencies**:
- `MangaGridItemBinding` (ViewBinding)
- Coil image loading (`loadManga`, `loadNovel`)
- `MangaCoverMetadata` / `NovelCoverMetadata`

**What Needs to Change**:
1. **REPLACE** with Compose `LibraryGridItem` composable
2. Image loading → Coil Compose integration
3. Cover ratio → Compose `aspectRatio` modifier
4. Touch tracking → Compose gesture detection

**Target Composable**:
```kotlin
@Composable
fun LibraryGridItem(
    item: LibraryItem,
    layout: LibraryLayout,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Estimated Complexity**: 🟡 **MEDIUM** - Complex UI with multiple layout modes

---

### 2.11 LibraryListHolder.kt

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryListHolder.kt`

**Size**: ~156 lines

**Key Responsibilities**:
- List item ViewHolder
- Simpler layout than grid
- Handles both manga and novel items
- Badge display (smaller than grid)

**What Needs to Change**:
1. **REPLACE** with Compose `LibraryListItem` composable
2. Similar structure to grid but horizontal layout

**Target Composable**:
```kotlin
@Composable
fun LibraryListItem(
    item: LibraryItem,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

**Estimated Complexity**: 🟢 **LOW** - Simpler than grid

---

### 2.12 AutofitRecyclerView.kt

**Location**: `app/src/main/java/eu/kanade/tachiyomi/widget/AutofitRecyclerView.kt`

**Size**: ~187 lines

**Key Responsibilities**:
- Custom RecyclerView with auto-fit grid columns
- Supports both `GridLayoutManager` and `StaggeredGridLayoutManager`
- Calculates optimal span count based on column width
- Provides scroll position utilities

**What Needs to Change**:
1. **REPLACE** with Compose `LazyVerticalGrid`
2. Auto-fit logic → `GridCells.Adaptive(minSize)`
3. Staggered support → Compose `LazyVerticalStaggeredGrid`

**Target Composable**:
```kotlin
@Composable
fun LibraryGrid(
    items: List<LibraryItem>,
    columns: GridCells,
    layout: LibraryLayout,
    isStaggered: Boolean,
    ...
)
```

**Estimated Complexity**: 🟡 **MEDIUM** - Layout calculation logic

---

### 2.13 CategoryRecyclerView.kt

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/category/CategoryRecyclerView.kt`

**Size**: ~80 lines

**Key Responsibilities**:
- Horizontal scrolling category tabs
- Uses FastAdapter for categories
- Highlights selected category
- Click handling for category navigation

**What Needs to Change**:
1. **REPLACE** with Compose `LazyRow` or `ScrollableTabRow`
2. Selection state → Compose state
3. Click handling → Compose callbacks

**Target Composable**:
```kotlin
@Composable
fun CategoryTabs(
    categories: List<Category>,
    selectedCategory: Int,
    itemCounts: Map<Int, Int>,
    onCategoryClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

**Estimated Complexity**: 🟢 **LOW**

---

### 2.14 XML Layouts

**Locations**:
- `res/layout/library_controller.xml` - Main controller layout
- `res/layout/library_grid_recycler.xml` - RecyclerView container
- `res/layout/manga_grid_item.xml` - Grid item layout (shared with manga details)
- `res/layout/manga_list_item.xml` - List item layout
- `res/layout/library_category_header_item.xml` - Category header
- `res/layout/library_badges_layout.xml` - Badge container

**What Needs to Change**:
1. Most layouts can be **REMOVED** after Compose migration
2. Keep `library_controller.xml` shell with `ComposeView`
3. Badge layouts → Compose badge components

**Estimated Complexity**: 🟢 **LOW** - Delete and replace

---

### 2.15 models/LibraryItem.kt (Sealed Interface - Already Exists)

**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/models/LibraryItem.kt`

**Size**: ~109 lines

**Current Structure** (already Compose-friendly):
```kotlin
sealed interface LibraryItem {
    val contentType: ContentType
    
    data class Blank(val mangaCount: Int = 0) : LibraryItem
    data class Hidden(val title: String, val hiddenItems: List<LibraryItem>) : LibraryItem
    data class Manga(...) : LibraryItem
    data class Novel(...) : LibraryItem
}
```

**Status**: ✅ **ALREADY EXISTS** - Can be used as the Compose data model

**What Needs to Change**:
1. Bridge between `LibraryMangaItem` (FlexibleAdapter) and `LibraryItem.Manga` (sealed)
2. Eventually replace FlexibleAdapter items with sealed interface

**Estimated Complexity**: 🟢 **LOW** - Already implemented

---

## 3. Data Flow Analysis

### 3.1 Current Data Flow (View-based)

```
┌─────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                               │
├─────────────────────────────────────────────────────────────────┤
│  GetLibraryManga  ←→  Room Database  ←→  NovelRepository        │
│         ↓                                      ↓                 │
│    Flow<LibraryMangaData>              Flow<List<Novel>>        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      PRESENTER LAYER                             │
├─────────────────────────────────────────────────────────────────┤
│                      LibraryPresenter                            │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ ModeManager.currentMode.collectLatest { mode ->          │    │
│  │   when (mode) {                                          │    │
│  │     MANGA -> subscribeMangaLibrary()                     │    │
│  │     NOVEL -> subscribeNovelLibrary()                     │    │
│  │   }                                                      │    │
│  │ }                                                        │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↓                                                        │
│  applyFilters() → applySort() → sectionLibrary()                │
│         ↓                                                        │
│  currentLibrary: LibraryMap (mutable property)                  │
│  libraryToDisplay: LibraryMutableMap                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    CONTROLLER LAYER                              │
├─────────────────────────────────────────────────────────────────┤
│                    LibraryController                             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ onNextLibraryUpdate(items: List<LibraryItem>)           │    │
│  │   → adapter.setItems(items)                             │    │
│  │   → update UI state (empty view, progress, etc.)        │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                     ADAPTER LAYER                                │
├─────────────────────────────────────────────────────────────────┤
│                  LibraryCategoryAdapter                          │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ setItems(list) → mangas = list.toList()                 │    │
│  │ performFilter() → updateDataSet(filtered)               │    │
│  │ notifyDataSetChanged()                                  │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    VIEW LAYER                                    │
├─────────────────────────────────────────────────────────────────┤
│  AutofitRecyclerView                                            │
│    ├── LibraryGridHolder (manga_grid_item.xml)                  │
│    ├── LibraryListHolder (manga_list_item.xml)                  │
│    └── LibraryHeaderHolder (library_category_header_item.xml)   │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Target Data Flow (Compose-based)

```
┌─────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                               │
│                        (unchanged)                               │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                  VIEWMODEL LAYER (new/adapted)                   │
├─────────────────────────────────────────────────────────────────┤
│              LibraryViewModel : ViewModel()                      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ // Exposed state flows                                   │    │
│  │ val libraryState: StateFlow<LibraryUiState>             │    │
│  │ val selectedItems: StateFlow<Set<Long>>                 │    │
│  │ val searchQuery: StateFlow<String>                      │    │
│  │                                                         │    │
│  │ // Computed flows                                        │    │
│  │ val displayItems = combine(                             │    │
│  │   libraryItems, filters, sortMode, searchQuery          │    │
│  │ ) { items, filters, sort, query ->                      │    │
│  │   items.filter(filters).sort(sort).search(query)        │    │
│  │ }.stateIn(viewModelScope)                               │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   COMPOSE UI LAYER                               │
├─────────────────────────────────────────────────────────────────┤
│  LibraryScreen(viewModel: LibraryViewModel)                      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ val state by viewModel.libraryState.collectAsState()    │    │
│  │                                                         │    │
│  │ LibraryContent(                                         │    │
│  │   items = state.displayItems,                           │    │
│  │   layout = state.layout,                                │    │
│  │   selectedItems = state.selectedItems,                  │    │
│  │   onItemClick = viewModel::onItemClick,                 │    │
│  │   onItemLongClick = viewModel::onItemLongClick,         │    │
│  │ )                                                       │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   COMPOSABLE COMPONENTS                          │
├─────────────────────────────────────────────────────────────────┤
│  LibraryContent                                                  │
│  ├── when (layout) {                                            │
│  │     LIST -> LazyColumn { LibraryListItem(...) }              │
│  │     GRID -> LazyVerticalGrid { LibraryGridItem(...) }        │
│  │     STAGGERED -> LazyVerticalStaggeredGrid { ... }           │
│  │   }                                                          │
│  ├── LibraryHeader (sticky category headers)                    │
│  └── LibraryItemBadge (unread/download counts)                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Migration Strategy (Phased Approach)

### Phase 1: Data Layer Preparation (Low Risk)
**Duration**: 1-2 days
**Risk**: 🟢 Low

#### Tasks:
1. ✅ `LibraryItem` sealed interface already exists in `models/`
2. Create mapping functions: `LibraryMangaItem` → `LibraryItem.Manga`
3. Add `StateFlow` exposure in `LibraryPresenter`
4. Create `LibraryUiState` data class

```kotlin
data class LibraryUiState(
    val items: List<LibraryItem>,
    val categories: List<Category>,
    val selectedCategory: Int,
    val layout: LibraryLayout,
    val isLoading: Boolean,
    val error: String?,
    val hasActiveFilters: Boolean,
)

enum class LibraryLayout {
    LIST, COMPACT_GRID, COMFORTABLE_GRID, COVER_ONLY_GRID
}
```

### Phase 2: Create Compose Components (Medium Risk)
**Duration**: 3-5 days
**Risk**: 🟡 Medium

#### Tasks:
1. Create `LibraryGridItem.kt` composable
2. Create `LibraryListItem.kt` composable
3. Create `LibraryHeader.kt` composable (category header)
4. Create `LibraryBadge.kt` composable (unread/download badges)
5. Create `LibraryContent.kt` main composable
6. Create `FastScrollGrid.kt` with fast scroll support
7. Test components in isolation

#### File Structure:
```
yokai/presentation/library/
├── LibraryScreen.kt           # Main screen composable
├── LibraryContent.kt          # Grid/List container
├── components/
│   ├── LibraryGridItem.kt     # Grid item (all 4 modes)
│   ├── LibraryListItem.kt     # List item
│   ├── LibraryHeader.kt       # Category header
│   ├── LibraryBadge.kt        # Unread/download badge
│   ├── LibraryCoverImage.kt   # Cover with loading
│   └── FastScrollGrid.kt      # Grid with fast scroller
├── state/
│   ├── LibraryUiState.kt      # UI state model
│   └── LibrarySelectionState.kt # Multi-select state
```

### Phase 3: Hybrid Integration (Medium-High Risk)
**Duration**: 2-3 days
**Risk**: 🟠 Medium-High

#### Tasks:
1. Add `ComposeView` to `library_controller.xml` (already exists)
2. Wire Compose content to `LibraryController`
3. Implement state bridge between Presenter and Compose
4. Keep ViewBinding shell for navigation, hopper, filter sheet
5. Test side-by-side with feature flag

```kotlin
// In LibraryController.onViewCreated()
binding.composeView.apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        YokaiTheme {
            val state by presenter.libraryUiState.collectAsState()
            LibraryContent(
                state = state,
                onItemClick = ::openContent,
                onItemLongClick = ::toggleSelection,
            )
        }
    }
}
```

### Phase 4: Feature Parity & Polish (Medium Risk)
**Duration**: 3-5 days
**Risk**: 🟡 Medium

#### Tasks:
1. Implement drag-and-drop reordering
2. Implement multi-selection with action mode
3. Add shared element transitions
4. Implement fast scroll with bubble text
5. Add pull-to-refresh
6. Test all layout modes (list, compact, comfortable, cover-only)
7. Test staggered grid mode
8. Test novel mode thoroughly

### Phase 5: Cleanup (Low Risk)
**Duration**: 1-2 days
**Risk**: 🟢 Low

#### Tasks:
1. Remove `LibraryCategoryAdapter`
2. Remove `LibraryGridHolder`, `LibraryListHolder`
3. Remove unused XML layouts
4. Remove FlexibleAdapter dependencies from library items
5. Update navigation to pass Compose-compatible data

---

## 5. Novel Mode Integration Points

### 5.1 Content Type Detection
```kotlin
// ModeManager provides current mode
ModeManager.currentMode: StateFlow<ContentType>

// Items carry their content type
sealed interface LibraryItem {
    val contentType: ContentType
}
```

### 5.2 Novel-Specific Rendering
| Aspect | Manga | Novel |
|--------|-------|-------|
| Cover aspect ratio | 2:3 | 2:3 (same) |
| Badge: Download | Green count | Same |
| Badge: Unread | Blue count | Blue count |
| Badge: Language | Source lang | "Novel" |
| Subtitle | Author/Artist | Author only |
| Click action | MangaDetailsActivity | NovelDetailsController |
| Long-press | Multi-select | Multi-select |

### 5.3 Navigation Routing
```kotlin
// In LibraryController (keep existing pattern)
fun openContent(item: LibraryItem) {
    when (item) {
        is LibraryItem.Manga -> openManga(item.libraryManga.manga)
        is LibraryItem.Novel -> openNovel(item.novel)
    }
}

private fun openNovel(novel: Novel) {
    router.pushController(
        NovelDetailsController(novel.id).withFadeTransaction()
    )
}
```

### 5.4 Novel Badge Composable
```kotlin
@Composable
fun LibraryBadge(
    unreadCount: Int,
    downloadCount: Int,
    language: String?,
    isNovel: Boolean,  // Affects badge styling
    isGridMode: Boolean,
    modifier: Modifier = Modifier,
) {
    // Novel uses same badge layout but may hide download count
}
```

---

## 6. Complexity Summary

| Component | Complexity | Effort (days) | Risk |
|-----------|------------|---------------|------|
| LibraryController.kt | 🔴 High | 3-4 | High |
| LibraryPresenter.kt | 🟡 Medium | 1-2 | Medium |
| LibraryItem.kt | 🟢 Low | 0.5 | Low |
| LibraryMangaItem.kt | 🟢 Low | 0.5 | Low |
| LibraryNovelItem.kt | 🟢 Low | 0.5 | Low |
| LibraryPlaceholderItem.kt | 🟢 Low | 0.25 | Low |
| LibraryHeaderItem.kt | 🟢 Low | 0.25 | Low |
| LibraryCategoryAdapter.kt | 🔴 High | 2-3 | High |
| LibraryHolder.kt | 🟢 Low | 0.25 | Low |
| LibraryGridHolder.kt | 🟡 Medium | 1-2 | Medium |
| LibraryListHolder.kt | 🟢 Low | 0.5 | Low |
| AutofitRecyclerView.kt | 🟡 Medium | 1 | Medium |
| CategoryRecyclerView.kt | 🟢 Low | 0.5 | Low |
| XML Layouts | 🟢 Low | 0.5 | Low |
| **New Compose Components** | 🟡 Medium | 3-4 | Medium |
| **Integration & Testing** | 🟠 Medium-High | 3-4 | High |

**Total Estimated Effort**: 15-20 developer days

---

## 7. Key Risks & Mitigations

### Risk 1: Performance Regression
**Issue**: Compose LazyGrid may be slower than optimized RecyclerView
**Mitigation**: 
- Use `key` parameter for stable item identity
- Implement proper `remember` for expensive calculations
- Profile with large libraries (1000+ items)

### Risk 2: Feature Parity Gaps
**Issue**: Missing features during migration
**Mitigation**:
- Create comprehensive feature checklist
- Test each layout mode thoroughly
- Keep fallback to old implementation via feature flag

### Risk 3: Novel Mode Regression
**Issue**: Novel-specific features broken
**Mitigation**:
- Test novel mode separately
- Ensure `ContentType` properly propagates
- Verify navigation to NovelReaderActivity

### Risk 4: Drag-and-Drop Complexity
**Issue**: Compose drag-and-drop is less mature
**Mitigation**:
- Use `Modifier.dragAndDropSource` / `dragAndDropTarget`
- Consider third-party library if needed
- May need to keep reorder feature in separate phase

---

## 8. Success Criteria

1. ✅ All 4 layout modes work (list, compact, comfortable, cover-only)
2. ✅ Novel items display correctly in all modes
3. ✅ Multi-selection works with action mode
4. ✅ Fast scroll with bubble text functional
5. ✅ Category headers sticky-scroll correctly
6. ✅ Search filtering instant and responsive
7. ✅ Pull-to-refresh triggers library update
8. ✅ Navigation to manga/novel details works
9. ✅ Performance comparable to RecyclerView
10. ✅ Staggered grid mode optional support

---

## Appendix A: File Locations Quick Reference

```
app/src/main/java/eu/kanade/tachiyomi/ui/library/
├── LibraryController.kt          # Main controller (2837 lines)
├── LibraryPresenter.kt           # Presenter (1841 lines)
├── LibraryItem.kt                # Base item class
├── LibraryMangaItem.kt           # Manga item
├── LibraryNovelItem.kt           # Novel item
├── LibraryPlaceholderItem.kt     # Placeholder item
├── LibraryHeaderItem.kt          # Header item
├── LibraryCategoryAdapter.kt     # FlexibleAdapter
├── LibraryHolder.kt              # Base ViewHolder
├── LibraryGridHolder.kt          # Grid ViewHolder
├── LibraryListHolder.kt          # List ViewHolder
├── LibrarySort.kt                # Sort options enum
├── LibraryGroup.kt               # Group by options
├── category/
│   ├── CategoryItem.kt
│   └── CategoryRecyclerView.kt
├── filter/
│   └── FilterBottomSheet.kt      # Old View filter (deprecated)
└── models/
    └── LibraryItem.kt            # Sealed interface (Compose-ready)

app/src/main/java/yokai/presentation/library/
├── filter/                       # ✅ Already migrated to Compose
│   ├── LibraryFilterSheet.kt
│   ├── LibraryFilterState.kt
│   └── FilterDialog.kt
└── displayoptions/               # ✅ Already migrated to Compose
    ├── DisplayOptionsSheet.kt
    ├── DisplayOptionsState.kt
    └── DisplayTab.kt

app/src/main/res/layout/
├── library_controller.xml
├── library_grid_recycler.xml
├── library_category_header_item.xml
├── manga_grid_item.xml           # Shared with other screens
└── manga_list_item.xml           # Shared with other screens
```

---

*Document generated: January 30, 2026*
*Based on Miko codebase analysis for Compose migration planning*
