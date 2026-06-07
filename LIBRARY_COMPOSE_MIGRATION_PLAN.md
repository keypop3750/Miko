# Library → Compose UI Migration Plan

## Executive Summary
Comprehensive plan to migrate the Library from View-based (Conductor) to Jetpack Compose. The Library is the app's central feature (~2,364 lines in Controller + ~1,841 lines in Presenter) with complex interactions including drag-and-drop, multi-select, categories, filtering, and content-type switching.

---

## Current Architecture Analysis

### Files to Migrate (View-Based)

#### Core Files
| File | Lines | Purpose | Complexity |
|------|-------|---------|------------|
| `LibraryController.kt` | 2,364 | Main library UI controller | 🔴 High |
| `LibraryPresenter.kt` | 1,841 | Business logic, data loading | 🔴 High |
| `LibraryCategoryAdapter.kt` | ~500 | RecyclerView adapter with FlexibleAdapter | 🟡 Medium |
| `LibraryHeaderItem.kt` | ~200 | Category header items | 🟢 Low |
| `LibraryMangaItem.kt` | ~150 | Manga grid/list items | 🟢 Low |
| `LibraryNovelItem.kt` | ~100 | Novel grid/list items | 🟢 Low |
| `LibraryItem.kt` | ~50 | Base item interface | 🟢 Low |
| `LibraryPlaceholderItem.kt` | ~30 | Loading placeholders | 🟢 Low |
| `SearchGlobalItem.kt` | ~50 | Global search suggestion item | 🟢 Low |

#### Display/Layout Files
| File | Lines | Purpose | Complexity |
|------|-------|---------|------------|
| `LibraryGridHolder.kt` | ~200 | Grid item ViewHolder | 🟢 Low |
| `LibraryListHolder.kt` | ~150 | List item ViewHolder | 🟢 Low |
| `LibraryHolder.kt` | ~100 | Base ViewHolder | 🟢 Low |
| `LibraryHeaderHolder.kt` | ~250 | Category header ViewHolder | 🟡 Medium |

#### Filter System
| File | Lines | Purpose | Complexity |
|------|-------|---------|------------|
| `filter/FilterBottomSheet.kt` | ~800 | Filter bottom sheet | 🔴 High |
| `filter/FilterTagGroup.kt` | ~200 | Filter tag groups | 🟡 Medium |
| `filter/ExpandedFilterSheet.kt` | ~300 | Expanded filter view | 🟡 Medium |
| `filter/ExpandedFilterItem.kt` | ~100 | Filter item | 🟢 Low |
| `filter/ManageFilterItem.kt` | ~80 | Manage filters | 🟢 Low |
| `filter/LibraryFilter.kt` | ~50 | Filter definitions | 🟢 Low |

#### Display Settings
| File | Lines | Purpose | Complexity |
|------|-------|---------|------------|
| `display/TabbedLibraryDisplaySheet.kt` | ~400 | Display options sheet | 🟡 Medium |
| `display/LibraryDisplayView.kt` | ~200 | Display view settings | 🟢 Low |
| `display/LibraryCategoryView.kt` | ~150 | Category display settings | 🟢 Low |
| `display/LibraryBadgesView.kt` | ~100 | Badge settings | 🟢 Low |

#### Category System
| File | Lines | Purpose | Complexity |
|------|-------|---------|------------|
| `category/CategoryRecyclerView.kt` | ~250 | Category tabs/chips | 🟡 Medium |
| `category/CategoryItem.kt` | ~80 | Category item | 🟢 Low |

#### Gesture Detection
| File | Lines | Purpose | Complexity |
|------|-------|---------|------------|
| `LibraryGestureDetector.kt` | ~100 | Hopper swipe gestures | 🟢 Low |
| `LibraryCategoryGestureDetector.kt` | ~100 | Category swipe gestures | 🟢 Low |
| `LibraryHeaderGestureDetector.kt` | ~80 | Header tap gestures | 🟢 Low |

#### Other
| File | Lines | Purpose | Complexity |
|------|-------|---------|------------|
| `LibrarySort.kt` | ~150 | Sort options | 🟢 Low |
| `LibraryGroup.kt` | ~100 | Grouping options | 🟢 Low |
| `LibraryBadge.kt` | ~80 | Badge rendering | 🟢 Low |
| `LibraryFastScroll.kt` | ~200 | Fast scroll | 🟡 Medium |
| `FilteredLibraryController.kt` | ~150 | Filtered library variant | 🟡 Medium |

---

## Feature Inventory

### 1. Display Modes
- **Grid View** - Configurable columns (2-6)
- **List View** - Single column with details
- **Compact Grid** - Cover-only display
- **Comfortable Grid** - Cover with title below
- **Staggered Grid** - Pinterest-style layout

### 2. Category System
- **Category Headers** - Expandable/collapsible sections
- **Category Tabs** - Horizontal scrolling tabs
- **Single Category Mode** - View one category at a time
- **All Categories Mode** - View all categories with headers
- **Category Visibility Toggle** - Show/hide categories
- **Dynamic Categories** - BY_SOURCE, BY_TAG, BY_AUTHOR, etc.

### 3. Filtering
- **Download Status** - Downloaded, not downloaded
- **Read Status** - Unread, started, completed
- **Tracking Status** - Tracked, not tracked, specific trackers
- **Content Type** - Manga, manhwa, manhua, comic, novel
- **Completion Status** - Ongoing, completed, licensed, etc.
- **Filter Persistence** - Filters saved across sessions

### 4. Sorting
- **Sort Options** - Alphabetical, last read, last updated, unread count, total chapters, date added, drag and drop
- **Sort Direction** - Ascending/descending
- **Category-specific Sorting** - Different sort per category

### 5. Selection & Actions
- **Multi-select Mode** - Long press to enter, tap to toggle
- **Selection Actions** - Delete, download, migrate, mark read/unread, move to category
- **Action Mode Toolbar** - Context actions for selection
- **Range Selection** - Select range by clicking first and last

### 6. Category Hopper
- **Navigation Arrows** - Up/down category navigation
- **Category Menu** - Jump to specific category
- **Auto-hide** - Hides on scroll down
- **Swipe Tutorial** - First-time user guidance
- **Long-press Actions** - Configurable actions (random manga, group, display, etc.)
- **Position** - Left/Center/Right configurable

### 7. Search
- **Library Search** - Filter by title, author
- **Search Suggestions** - Based on recent reads
- **Global Search Redirect** - Link to search all sources
- **Category Toggle in Search** - Show all categories during search

### 8. Drag and Drop
- **Reorder Items** - Drag to reorder within category
- **Move to Category** - Drag to different category header
- **Item Touch Helper** - Swipe and drag gestures

### 9. Pull to Refresh
- **Library Update** - Swipe to trigger update
- **Category Update** - Update specific category

### 10. Bottom Sheet Filters
- **Peek State** - Collapsed showing filter chips
- **Expanded State** - Full filter options
- **Group By Options** - BY_DEFAULT, BY_SOURCE, BY_STATUS, BY_AUTHOR, BY_TAG, BY_TRACK_STATUS, BY_LANGUAGE, UNGROUPED

### 11. Display Settings Sheet
- **Grid Size** - Column count slider
- **Layout Options** - Grid/list/comfortable/cover-only
- **Badge Options** - Unread, download, language, source
- **Uniform Grid** - Equal item sizes
- **Hide Titles** - Cover-only mode

### 12. Content Type Support
- **Manga Display** - LibraryMangaItem
- **Novel Display** - LibraryNovelItem
- **Mode Toggle** - Switch between manga/novel
- **Mode-specific Content** - Only show current mode's items

### 13. Visual Features
- **Cover Images** - With lazy loading
- **Badges** - Unread count, download status, language
- **Outline on Covers** - Optional border
- **Incognito Indicator** - Visual indicator in search bar
- **Empty State** - Guidance for new users
- **Loading Progress** - Spinner during data load

### 14. Category Management
- **Edit Category** - Rename, change order
- **Collapse/Expand All** - Toggle all category visibility
- **Category Specific Actions** - Update, manage

### 15. Integration Points
- **Library Update Job** - Background updates
- **Download Manager** - Download status integration
- **Track Manager** - Tracking service integration
- **Source Manager** - Source-based grouping

---

## Migration Strategy

### Phase 1: Foundation (Week 1-2)
**Goal:** Create base Compose infrastructure without replacing existing UI

#### 1.1 Create Compose ViewModel
```kotlin
// yokai/presentation/library/LibraryViewModel.kt
class LibraryViewModel : ViewModel() {
    // State
    val libraryState: StateFlow<LibraryUiState>
    val filterState: StateFlow<FilterState>
    val displayState: StateFlow<DisplayState>
    val selectionState: StateFlow<SelectionState>
    
    // Actions
    fun onMangaClick(manga: Manga)
    fun onMangaLongClick(manga: Manga)
    fun toggleSelection(manga: Manga)
    fun updateFilter(filter: FilterUpdate)
    fun updateSort(sort: SortUpdate)
    fun search(query: String)
    fun toggleMode()
}
```

#### 1.2 Create State Models
```kotlin
// yokai/presentation/library/LibraryUiState.kt
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(
        val categories: List<CategoryWithItems>,
        val searchQuery: String,
        val isEmpty: Boolean,
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

data class CategoryWithItems(
    val category: Category,
    val items: List<LibraryContentItem>,
    val isExpanded: Boolean,
)

sealed interface LibraryContentItem {
    data class MangaItem(val manga: LibraryManga, val badges: BadgeState) : LibraryContentItem
    data class NovelItem(val novel: Novel, val badges: BadgeState) : LibraryContentItem
}
```

### Phase 2: Core Components (Week 3-4)
**Goal:** Build reusable Compose components

#### 2.1 Item Components
```
yokai/presentation/library/components/
├── LibraryMangaCard.kt       - Grid manga item
├── LibraryNovelCard.kt       - Grid novel item
├── LibraryListItem.kt        - List mode item
├── LibraryCoverOnlyItem.kt   - Cover-only grid item
├── BadgeRow.kt               - Unread/download badges
├── CategoryHeader.kt         - Expandable category header
└── LibraryEmptyState.kt      - Empty library view
```

#### 2.2 Layout Components
```
yokai/presentation/library/components/
├── LibraryGrid.kt            - LazyVerticalGrid with categories
├── LibraryList.kt            - LazyColumn list view
├── LibraryStaggeredGrid.kt   - StaggeredLazyVerticalGrid
└── CategoryRow.kt            - Horizontal category tabs
```

### Phase 3: Filter & Display Sheets (Week 5-6)
**Goal:** Compose bottom sheets for filtering and display

#### 3.1 Filter Sheet
```
yokai/presentation/library/filter/
├── FilterBottomSheet.kt      - Main filter sheet
├── FilterSection.kt          - Filter category section
├── FilterChip.kt             - Individual filter chip
├── FilterState.kt            - Filter state model
└── SortSection.kt            - Sort options
```

#### 3.2 Display Settings Sheet
```
yokai/presentation/library/display/
├── DisplayBottomSheet.kt     - Display options sheet
├── GridSizeSlider.kt         - Column count slider
├── LayoutOptionRow.kt        - Layout type selection
├── BadgeOptionsSection.kt    - Badge visibility toggles
└── DisplayState.kt           - Display state model
```

### Phase 4: Category Hopper (Week 7)
**Goal:** Floating hopper component in Compose

```kotlin
// yokai/presentation/library/components/CategoryHopper.kt
@Composable
fun CategoryHopper(
    categories: List<Category>,
    currentCategory: Category,
    hopperGravity: HopperGravity,
    isVisible: Boolean,
    onUpClick: () -> Unit,
    onDownClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onLongPress: () -> Unit,
)
```

### Phase 5: Selection & Actions (Week 8)
**Goal:** Multi-select with action mode

```kotlin
// yokai/presentation/library/selection/
├── SelectionState.kt         - Selection tracking
├── SelectionTopBar.kt        - Action mode bar
├── SelectionActions.kt       - Bulk action handlers
└── SelectionOverlay.kt       - Selection checkmarks
```

### Phase 6: Drag and Drop (Week 9)
**Goal:** Reordering and category movement

```kotlin
// Using Compose LazyListItemInfo and detectDragGestures
// yokai/presentation/library/drag/
├── DragDropState.kt          - Drag state tracking
├── DraggableItem.kt          - Draggable item wrapper
└── DropTarget.kt             - Category drop targets
```

### Phase 7: Integration (Week 10-11)
**Goal:** Wire everything together

#### 7.1 Main Screen
```kotlin
// yokai/presentation/library/LibraryScreen.kt
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onMangaClick: (Manga) -> Unit,
    onNovelClick: (Novel) -> Unit,
    onSearchClick: () -> Unit,
)
```

#### 7.2 Controller Wrapper
```kotlin
// Initial wrapper that hosts Compose in existing Controller
class LibraryComposeController : BaseComposeController<LibraryBinding>() {
    @Composable
    override fun ComposeContent() {
        val viewModel = viewModel<LibraryViewModel>()
        YokaiTheme {
            LibraryScreen(viewModel = viewModel, ...)
        }
    }
}
```

### Phase 8: Testing & Polish (Week 12)
**Goal:** Performance optimization and bug fixes

- Profile with Android Profiler
- Optimize recomposition with `key()`, `remember`, `derivedStateOf`
- Add shimmer loading states
- Ensure smooth 60fps scrolling
- Test on low-end devices

---

## Component Mapping

| View Component | Compose Component |
|----------------|-------------------|
| RecyclerView + FlexibleAdapter | LazyVerticalGrid / LazyColumn |
| GridLayoutManager | LazyVerticalGrid |
| StaggeredGridLayoutManager | LazyVerticalStaggeredGrid |
| LibraryGridHolder | LibraryMangaCard composable |
| LibraryListHolder | LibraryListItem composable |
| LibraryHeaderHolder | CategoryHeader composable |
| FilterBottomSheet | ModalBottomSheet |
| TabbedLibraryDisplaySheet | ModalBottomSheet with tabs |
| CategoryRecyclerView | LazyRow with CategoryChip |
| FastScroller | Custom Compose fast scroller |
| ActionMode | TopAppBar with selection actions |
| ItemTouchHelper | Modifier.detectDragGestures |

---

## State Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     LibraryViewModel                        │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │  LibraryState   │  │   FilterState   │  │DisplayState │ │
│  │  - categories   │  │  - downloaded   │  │ - layout    │ │
│  │  - items        │  │  - unread       │  │ - gridSize  │ │
│  │  - loading      │  │  - completed    │  │ - badges    │ │
│  │  - searchQuery  │  │  - tracked      │  │             │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
│                                                             │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │ SelectionState  │  │  HopperState    │                  │
│  │ - selectedIds   │  │ - visible       │                  │
│  │ - isActive      │  │ - gravity       │                  │
│  │ - count         │  │ - category      │                  │
│  └─────────────────┘  └─────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

---

## File Structure After Migration

```
yokai/presentation/library/
├── LibraryScreen.kt              # Main screen
├── LibraryViewModel.kt           # ViewModel
├── LibraryUiState.kt             # State models
├── components/
│   ├── LibraryGrid.kt            # Grid layout
│   ├── LibraryList.kt            # List layout  
│   ├── LibraryStaggeredGrid.kt   # Staggered layout
│   ├── LibraryMangaCard.kt       # Manga item
│   ├── LibraryNovelCard.kt       # Novel item
│   ├── LibraryListItem.kt        # List item
│   ├── LibraryCoverOnlyItem.kt   # Cover-only item
│   ├── CategoryHeader.kt         # Category header
│   ├── CategoryHopper.kt         # Floating hopper
│   ├── CategoryRow.kt            # Category tabs
│   ├── BadgeRow.kt               # Badges
│   ├── LibraryEmptyState.kt      # Empty view
│   ├── LibraryFastScroller.kt    # Fast scroll
│   └── SearchSuggestionItem.kt   # Global search link
├── filter/
│   ├── FilterBottomSheet.kt      # Filter sheet
│   ├── FilterSection.kt          # Filter section
│   ├── FilterChip.kt             # Filter chip
│   ├── SortSection.kt            # Sort options
│   └── FilterState.kt            # Filter state
├── display/
│   ├── DisplayBottomSheet.kt     # Display sheet
│   ├── GridSizeSlider.kt         # Grid size
│   ├── LayoutOptionRow.kt        # Layout options
│   ├── BadgeOptionsSection.kt    # Badge options
│   └── DisplayState.kt           # Display state
├── selection/
│   ├── SelectionTopBar.kt        # Selection bar
│   ├── SelectionActions.kt       # Bulk actions
│   └── SelectionState.kt         # Selection state
└── drag/
    ├── DragDropState.kt          # Drag state
    └── DraggableItem.kt          # Drag wrapper
```

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Performance regression | Medium | High | Profile early, optimize LazyList keys |
| FlexibleAdapter features lost | Medium | Medium | Implement custom solution for drag-drop |
| State management complexity | High | Medium | Use clean ViewModel architecture |
| Category header expand/collapse | Low | Medium | Use AnimatedContent |
| Fast scroller feel different | Medium | Low | Custom implementation matching current |
| Filter persistence | Low | Low | Reuse existing preference system |

---

## Dependencies Required

```kotlin
// build.gradle.kts additions
implementation("androidx.compose.foundation:foundation-layout")  // Already have
implementation("androidx.compose.material3:material3")           // Already have
implementation("sh.calvin.reorderable:reorderable:x.x.x")        // For drag-drop
```

---

## Migration Checklist

- [ ] Phase 1: Foundation
  - [ ] Create LibraryViewModel
  - [ ] Create state models
  - [ ] Set up Compose hosting

- [ ] Phase 2: Core Components
  - [ ] LibraryMangaCard
  - [ ] LibraryNovelCard  
  - [ ] LibraryListItem
  - [ ] CategoryHeader
  - [ ] LibraryEmptyState

- [ ] Phase 3: Filter & Display
  - [ ] FilterBottomSheet
  - [ ] DisplayBottomSheet
  - [ ] Filter state management

- [ ] Phase 4: Category Hopper
  - [ ] Hopper component
  - [ ] Animation
  - [ ] Long-press actions

- [ ] Phase 5: Selection
  - [ ] Multi-select mode
  - [ ] Selection actions
  - [ ] Action bar

- [ ] Phase 6: Drag and Drop
  - [ ] Reorder within category
  - [ ] Move to category

- [ ] Phase 7: Integration
  - [ ] LibraryScreen
  - [ ] Controller wrapper
  - [ ] Navigation

- [ ] Phase 8: Testing
  - [ ] Performance profiling
  - [ ] Low-end device testing
  - [ ] Feature parity verification

---

## Estimated Timeline

| Phase | Duration | Status |
|-------|----------|--------|
| Phase 1: Foundation | 2 weeks | ⏳ Not Started |
| Phase 2: Core Components | 2 weeks | ⏳ Not Started |
| Phase 3: Filter & Display | 2 weeks | ⏳ Not Started |
| Phase 4: Category Hopper | 1 week | ⏳ Not Started |
| Phase 5: Selection | 1 week | ⏳ Not Started |
| Phase 6: Drag and Drop | 1 week | ⏳ Not Started |
| Phase 7: Integration | 2 weeks | ⏳ Not Started |
| Phase 8: Testing | 1 week | ⏳ Not Started |
| **Total** | **12 weeks** | |

---

## Notes

1. **Existing Compose Files**: `library/compose/LibraryComposeController.kt` and `LibraryComposePresenter.kt` exist but appear to be early attempts - evaluate for reuse
2. **Priority Order**: Grid display → Filtering → Selection → Drag-drop → Hopper
3. **Feature Flag**: Implement behind feature flag for gradual rollout
4. **Backwards Compatibility**: Keep View-based controller available during transition
