# Swipes Feature - Phase 0 Research Findings
**Comprehensive Internal Codebase Analysis**

## Executive Summary
This document contains detailed research findings from the Miko codebase to support the implementation of the Swipes feature. Research covers navigation systems, UI patterns, data management, and integration points.

---

## 1. Navigation System Analysis ✅

### Current Implementation
**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`

#### Bottom Navigation Architecture
- **View Type**: `NavigationBarView` (supports both `BottomNavigationView` and `NavigationRailView`)
- **Current Tabs**: 3 tabs (Library, Recents, Browse)
- **Menu Resource**: `app/src/main/res/menu/bottom_navigation.xml`

#### Current Tab IDs
```kotlin
R.id.nav_library  // Library tab (default selected)
R.id.nav_recents  // Recents tab (downloads queue, history, updates)
R.id.nav_browse   // Browse tab (sources, extensions, migration)
```

#### Navigation Selection Logic (MainActivity.kt lines ~580-610)
```kotlin
nav.setOnItemSelectedListener { item ->
    val id = item.itemId
    val currentRoot = router.backstack.firstOrNull()
    if (currentRoot?.tag()?.toIntOrNull() != id) {
        setRoot(
            when (id) {
                R.id.nav_library -> LibraryComposeController() or LibraryController()
                R.id.nav_recents -> RecentsController()
                else -> BrowseController()  // nav_browse
            },
            id,
        )
    }
    true
}
```

#### Long-Press Actions
All tabs support long-press gestures for quick actions:
- **Library**: Trigger library update
- **Recents**: Show recents sheet or jump to last read
- **Browse**: Show browse sheet or open global search

### Adding 4th Tab - Implementation Strategy

#### Step 1: Update Bottom Navigation Menu
**File**: `app/src/main/res/menu/bottom_navigation.xml`

```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <group android:id="@+id/group_feature">
        <item android:id="@+id/nav_library" ... />
        <item android:id="@+id/nav_recents" ... />
        <item android:id="@+id/nav_browse" ... />
        <!-- NEW: Add 4th tab -->
        <item
            android:id="@+id/nav_swipes"
            android:icon="@drawable/ic_swipes_selector_24dp"
            android:title="@string/swipes" />
    </group>
</menu>
```

#### Step 2: Add Icon Resources
**Location**: `app/src/main/res/drawable/`

Need to create:
- `ic_swipes_selector_24dp.xml` - Selector drawable (normal/selected states)
- `ic_swipes_24dp.xml` - Normal state icon (card stack visual)
- `ic_swipes_filled_24dp.xml` - Selected state icon

**Existing icon pattern** (from `ic_library_selector_24dp.xml` example):
```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/ic_library_filled_24dp" android:state_checked="true" />
    <item android:drawable="@drawable/ic_library_24dp" />
</selector>
```

#### Step 3: Update MainActivity Navigation Logic
```kotlin
// Add to nav.setOnItemSelectedListener
when (id) {
    R.id.nav_library -> ...
    R.id.nav_recents -> ...
    R.id.nav_browse -> ...
    R.id.nav_swipes -> SwipesActivity()  // NEW: Launch SwipesActivity
}
```

#### Step 4: Add String Resources
**File**: `i18n/src/commonMain/moko-resources/base/strings.xml`

```xml
<string name="swipes">Swipes</string>
```

### Navigation Integration Points
1. **MainActivity.onCreate()** - Tab setup and listeners
2. **MainActivity.setStartingTab()** - Persist last selected tab
3. **MainActivity.startingTab()** - Determine starting tab on app launch
4. **MainActivity.goToTab()** - Programmatic tab selection
5. **Intent handling** - Shortcut support (like `SHORTCUT_LIBRARY`)

### Key Architectural Decisions
- **Activity vs Controller**: Swipes will use Activity (not Conductor Controller)
- **Independent routing**: Swipes manages its own navigation stack
- **Tab persistence**: Save/restore swipes tab selection in preferences
- **Badge support**: `nav.getOrCreateBadge(R.id.nav_swipes)` for notifications

---

## 2. Search Bar Integration Research 🔍

### Current Search Implementations

#### RecentsController Search
**Pattern**: Expandable search in app bar with filter integration

**Key Components**:
- `SearchToolbar` with `MenuItem.OnActionExpandListener`
- `SearchView` integration with query text listeners
- Filter icon alongside search icon
- Novel toggle implementation (can be reused for Swipes)

#### BrowseController Search
**Pattern**: Global search with source filtering

**Key Components**:
- `GlobalSearchController` for cross-source search
- `SearchView` with source-specific queries
- Filter bottom sheet integration

#### LibraryController Search
**Pattern**: In-place filtering with chip-based categories

**Key Components**:
- Real-time filtering as user types
- Category filtering integration
- Sort options alongside search

### Search Bar Architecture for Swipes

#### Recommended Pattern
Use **RecentsController pattern** as base:
- Expandable search action view
- Filter button alongside search
- Novel toggle (marked as TODO)
- Three-dots menu for history access

#### Implementation Components
```kotlin
// SearchToolbar setup (MainActivity.kt lines ~640-680)
binding.searchToolbar.searchItem?.setOnActionExpandListener(
    object : MenuItem.OnActionExpandListener {
        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
            // Handle search expansion
            binding.searchToolbar.menu.forEach { it.isVisible = false }
            controller?.onActionViewExpand(item)
            return true
        }
        
        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
            // Handle search collapse
            setupSearchTBMenu(binding.toolbar.menu, true)
            controller?.onActionViewCollapse(item)
            return true
        }
    },
)
```

#### Search Integration Points for Swipes
1. **SwipesActivity toolbar** - Setup search action view
2. **Query handling** - Filter recommendations by title/description
3. **Filter integration** - Combine search with genre/tag filters
4. **History integration** - Three-dots menu shows search + swipe history

### Novel Toggle Implementation
**Current Implementation** (Recents/Library):
```kotlin
// Toggle button in toolbar menu
// Switches between manga and novel content types
// Uses ModeManager.currentMode for state management
```

**Swipes Adaptation**:
- Add novel toggle to search toolbar
- Mark as `TODO` for Phase 8 (Novel Integration)
- Use same `ModeManager` pattern when ready

---

## 3. Filter System Analysis 🔧

### Existing Filter Implementations

#### RecentsController Filter Dialog
**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/recents/RecentsController.kt`

**Dialog Type**: Material bottom sheet with toggleable options

**Filter Categories**:
- View type (Updates/History/All)
- Download status filters
- Read status filters
- Bookmark filters

**UI Pattern**: Uses `MaterialMenuSheet` for simple option selection

```kotlin
// Example from RecentsController
val items = listOf(
    MaterialMenuSheet.MenuSheetItem(0, textRes = MR.strings.updates),
    MaterialMenuSheet.MenuSheetItem(1, textRes = MR.strings.history),
    // ... more options
)
val sheet = MaterialMenuSheet(activity, items, title, selectedId) { sheet, item ->
    // Handle selection
    true
}
sheet.show()
```

#### BrowseController Filter System
**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/source/browse/BrowseSourceController.kt`

**Filter Type**: Source-specific advanced filtering

**Filter Categories**:
- **Genre**: Multi-select checkboxes
- **Tags**: Searchable tag selection
- **Demographic**: Single-select (Shounen, Seinen, Josei, etc.)
- **Type**: Content type (Manga, Manhwa, Manhua, One-shot)
- **Content Rating**: Safe/Suggestive/Erotica
- **Status**: Ongoing/Completed/Hiatus/Cancelled

**UI Pattern**: Custom filter bottom sheet with multiple sections

**Data Structure**:
```kotlin
// From source-api module
sealed class Filter<T> {
    class CheckBox(name: String, val state: Boolean = false)
    class TriState(name: String, val state: Int = STATE_IGNORE)
    class Select<V>(name: String, val values: Array<V>, val state: Int = 0)
    class Group<V>(name: String, val filters: List<Filter<V>>)
    // ... more filter types
}
```

### Recommended Filter Architecture for Swipes

#### Two-Tab Filter Dialog
**Tab 1: Source Selection**
- Toggle switches for each configured source
- "Select All" / "Deselect All" buttons
- NSFW content toggle override
- Source count indicator

**Tab 2: Content Filtering**
- Reuse BrowseController filter components
- Genre multi-select (with search)
- Tags multi-select (with search)
- Demographic single-select
- Type filters (Manga/Manhwa/Manhua/Novel)
- Content rating (with NSFW override)
- Status filters (Ongoing/Completed)

#### Filter Dialog Implementation Pattern
```kotlin
class SwipesFilterDialog(
    activity: Activity,
    private val currentFilters: SwipesFilters,
    private val onFilterApplied: (SwipesFilters) -> Unit
) : BottomSheetDialog(activity) {
    
    private var selectedTab = 0  // 0 = Sources, 1 = Content
    
    fun show() {
        // Create TabLayout with 2 tabs
        // Tab 1: Source selection UI
        // Tab 2: Content filtering UI (reuse Browse filters)
        super.show()
    }
}

data class SwipesFilters(
    val enabledSources: Set<Long>,
    val includeNSFW: Boolean,
    val genres: Set<String>,
    val tags: Set<String>,
    val demographic: String?,
    val contentTypes: Set<String>,
    val status: Set<String>
)
```

#### Filter Persistence
**Pattern**: Use shared preferences (like Browse/Recents)
```kotlin
// SwipesPreferences.kt
class SwipesPreferences {
    fun savedFilters(): Preference<SwipesFilters>
    fun lastSourceSelection(): Preference<Set<Long>>
    fun includeNSFW(): Preference<Boolean>  // Default: false
}
```

#### Queue Rebuild on Filter Change
**Critical**: When filters change, wipe and rebuild recommendation queue
```kotlin
fun onFiltersApplied(newFilters: SwipesFilters) {
    if (newFilters != currentFilters) {
        // Clear existing queue
        queueManager.clearQueue()
        // Rebuild with new filters
        queueManager.rebuildQueue(newFilters)
        // Reset UI to first card
        cardStackView.scrollToPosition(0)
    }
}
```

---

## 4. Settings Integration Research ⚙️

### Settings Architecture Overview
**Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/setting/`

### Current Settings Structure
```
SettingsMainController.kt           - Root settings screen
├── SettingsGeneralController.kt    - General app settings
├── SettingsAppearanceController.kt - Theme/UI settings
├── SettingsLibraryController.kt    - Library behavior settings
├── SettingsReaderController.kt     - Reader settings
├── SettingsDownloadController.kt   - Download settings
├── SettingsTrackingController.kt   - Tracking service settings
├── SettingsBrowseController.kt     - Browse/source settings
├── SettingsBackupController.kt     - Backup/restore settings
├── SettingsSecurityController.kt   - Security/privacy settings
└── SettingsAdvancedController.kt   - Advanced settings
```

### Preference Management Pattern
**Uses**: AndroidX Preference library with custom extensions

**Base Class**: `SettingsController` extends `PreferenceController`

**Example Structure** (from SettingsLibraryController.kt):
```kotlin
class SettingsLibraryController : SettingsController() {
    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.library
        
        // Preference groups
        preferenceCategory {
            titleRes = MR.strings.categories
            // Individual preferences...
        }
        
        // Switch preferences
        switchPreference {
            key = Keys.autoUpdateMangaRestrictions
            titleRes = MR.strings.update_only_non_completed
            summaryRes = MR.strings.auto_update_restrictions_summary
            defaultValue = false
        }
        
        // List preferences
        listPreference(activity) {
            key = Keys.libraryUpdateInterval
            titleRes = MR.strings.update_library_frequency
            entries = updateIntervals
            entryValues = updateIntervalValues
            defaultValue = "0"
        }
    }
}
```

### Swipes Settings Implementation

#### Create SettingsSwipesController
**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/setting/SettingsSwipesController.kt`

```kotlin
class SettingsSwipesController : SettingsController() {
    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.swipes
        
        // === Swipe Actions ===
        preferenceCategory {
            titleRes = MR.strings.swipe_actions
            
            listPreference(activity) {
                key = "swipe_left_action"
                titleRes = MR.strings.swipe_left_action
                entriesRes = arrayOf(
                    MR.strings.skip,
                    MR.strings.blacklist
                )
                entryValues = arrayOf("skip", "blacklist")
                defaultValue = "skip"
                summary = "%s"
            }
            
            listPreference(activity) {
                key = "swipe_right_action"
                titleRes = MR.strings.swipe_right_action
                entriesRes = arrayOf(
                    MR.strings.add_to_default_category,
                    MR.strings.ask_every_time,
                    MR.strings.batch_selection
                )
                entryValues = arrayOf("default", "ask", "batch")
                defaultValue = "default"
                summary = "%s"
            }
            
            // Category selection (only visible if right action = default)
            multiSelectListPreference(activity) {
                key = "swipe_default_category"
                titleRes = MR.strings.default_category
                isVisible = preferences.swipeRightAction().get() == "default"
                // Dynamic category list from database
            }
        }
        
        // === Content Preferences ===
        preferenceCategory {
            titleRes = MR.strings.content
            
            switchPreference {
                key = "swipe_include_nsfw"
                titleRes = MR.strings.include_nsfw_content
                summaryRes = MR.strings.include_nsfw_summary
                defaultValue = false
            }
            
            multiSelectListPreference(activity) {
                key = "swipe_enabled_sources"
                titleRes = MR.strings.enabled_sources
                summaryRes = MR.strings.enabled_sources_summary
                defaultValue = emptySet()
                // Dynamic source list
            }
        }
        
        // === Queue Behavior ===
        preferenceCategory {
            titleRes = MR.strings.queue_behavior
            
            intListPreference(activity) {
                key = "swipe_preload_size"
                titleRes = MR.strings.preload_size
                entries = arrayOf("20", "30", "40", "50")
                entryValues = arrayOf("20", "30", "40", "50")
                defaultValue = "30"
                summary = "%s items"
            }
            
            intListPreference(activity) {
                key = "swipe_refresh_threshold"
                titleRes = MR.strings.refresh_threshold
                entries = arrayOf("3", "5", "10")
                entryValues = arrayOf("3", "5", "10")
                defaultValue = "5"
                summary = "Add more after %s swipes"
            }
        }
        
        // === Blacklist Management ===
        preferenceCategory {
            titleRes = MR.strings.blacklist
            
            preference {
                key = "view_blacklist"
                titleRes = MR.strings.view_blacklist
                summaryRes = MR.strings.manage_blacklisted_items
                
                onClick {
                    router.pushController(
                        SwipesBlacklistController().withFadeTransaction()
                    )
                }
            }
            
            preference {
                key = "clear_blacklist"
                titleRes = MR.strings.clear_blacklist
                summaryRes = MR.strings.clear_blacklist_warning
                
                onClick {
                    MaterialAlertDialogBuilder(activity!!)
                        .setTitle(MR.strings.clear_blacklist)
                        .setMessage(MR.strings.clear_blacklist_confirmation)
                        .setPositiveButton(AR.string.ok) { _, _ ->
                            // Clear blacklist
                        }
                        .setNegativeButton(AR.string.cancel, null)
                        .show()
                }
            }
        }
        
        // === Feedback Settings ===
        preferenceCategory {
            titleRes = MR.strings.feedback
            
            switchPreference {
                key = "swipe_haptic_feedback"
                titleRes = MR.strings.haptic_feedback
                summaryRes = MR.strings.vibrate_on_swipe
                defaultValue = true
            }
            
            listPreference(activity) {
                key = "swipe_gesture_sensitivity"
                titleRes = MR.strings.gesture_sensitivity
                entriesRes = arrayOf(
                    MR.strings.low,
                    MR.strings.medium,
                    MR.strings.high
                )
                entryValues = arrayOf("low", "medium", "high")
                defaultValue = "medium"
                summary = "%s"
            }
        }
    }
}
```

#### Register in SettingsMainController
```kotlin
// Add to SettingsMainController preference list
preference {
    iconRes = R.drawable.ic_swipes_24dp
    titleRes = MR.strings.swipes
    onClick {
        router.pushController(SettingsSwipesController().withFadeTransaction())
    }
}
```

### Preference Storage Pattern
**File**: `app/src/main/java/eu/kanade/tachiyomi/data/preference/PreferencesHelper.kt`

```kotlin
class PreferencesHelper(val context: Context) {
    // ... existing preferences
    
    // Swipes preferences
    fun swipeLeftAction() = prefs.getString("swipe_left_action", "skip")
    fun swipeRightAction() = prefs.getString("swipe_right_action", "default")
    fun swipeDefaultCategory() = prefs.getStringSet("swipe_default_category", emptySet())
    fun swipeIncludeNSFW() = prefs.getBoolean("swipe_include_nsfw", false)
    fun swipeEnabledSources() = prefs.getStringSet("swipe_enabled_sources", emptySet())
    fun swipePreloadSize() = prefs.getInt("swipe_preload_size", 30)
    fun swipeRefreshThreshold() = prefs.getInt("swipe_refresh_threshold", 5)
    fun swipeHapticFeedback() = prefs.getBoolean("swipe_haptic_feedback", true)
    fun swipeGestureSensitivity() = prefs.getString("swipe_gesture_sensitivity", "medium")
}
```

---

## 5. Library Integration Research 📚

### Library Management Architecture

#### Core Components
**Files**:
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryController.kt`
- `domain/src/commonMain/kotlin/yokai/domain/library/`

#### Category Management
**Database Schema** (from SQLDelight):
```sql
CREATE TABLE categories (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    order INTEGER NOT NULL,
    flags INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE mangas_categories (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    manga_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    FOREIGN KEY(manga_id) REFERENCES mangas(_id) ON DELETE CASCADE,
    FOREIGN KEY(category_id) REFERENCES categories(_id) ON DELETE CASCADE
);
```

#### Add to Library Workflow

**Pattern 1: Direct Addition** (existing in app):
```kotlin
// From MangaDetailsController
private fun onFavoriteClick() {
    val manga = presenter.manga
    manga.favorite = !manga.favorite
    
    if (manga.favorite) {
        // Add to default category or show category selection
        val categories = presenter.getCategories()
        if (categories.isEmpty() || preferences.defaultCategory() > -1) {
            // Add to default
            presenter.moveMangaToCategory(manga, defaultCategoryId)
        } else {
            // Show category selection dialog
            showCategoryDialog(manga, categories)
        }
    } else {
        // Remove from library
        presenter.moveMangaToCategory(manga, null)
    }
}
```

**Pattern 2: Category Selection Dialog**:
```kotlin
fun showCategoryDialog(manga: Manga, categories: List<Category>) {
    val items = categories.map { it.name }
    val selected = presenter.getMangaCategories(manga).map { it.id }
    
    MaterialAlertDialogBuilder(activity!!)
        .setTitle(MR.strings.categories)
        .setMultiChoiceItems(items.toTypedArray(), selected) { _, which, isChecked ->
            // Track selections
        }
        .setPositiveButton(AR.string.ok) { _, _ ->
            // Apply category changes
            presenter.moveMangaToCategories(manga, selectedCategories)
        }
        .show()
}
```

### Swipes Library Integration Strategy

#### Integration Point 1: Right Swipe Action
```kotlin
// SwipesActivity - Right swipe handler
fun onSwipeRight(mangaItem: SwipeMangaItem) {
    val action = preferences.swipeRightAction().get()
    
    when (action) {
        "default" -> {
            // Add to default category immediately
            val defaultCategoryId = preferences.swipeDefaultCategory().get()
            addToLibrary(mangaItem.manga, defaultCategoryId)
            showToast(getString(MR.strings.added_to_library))
        }
        
        "ask" -> {
            // Show category selection dialog
            showCategorySelectionDialog(mangaItem.manga)
        }
        
        "batch" -> {
            // Queue for batch processing
            batchQueue.add(mangaItem.manga)
            showSnackbar(getString(MR.strings.queued_for_batch_selection))
        }
    }
}
```

#### Integration Point 2: Batch Category Selection
```kotlin
// SwipesBatchProcessor.kt
class SwipesBatchProcessor(
    private val activity: SwipesActivity,
    private val presenter: SwipesPresenter
) {
    private val queuedManga = mutableListOf<Manga>()
    
    fun addToQueue(manga: Manga) {
        queuedManga.add(manga)
        updateBadge(queuedManga.size)
    }
    
    fun showBatchDialog() {
        if (queuedManga.isEmpty()) {
            activity.showToast(MR.strings.no_items_in_queue)
            return
        }
        
        val categories = presenter.getCategories()
        MaterialAlertDialogBuilder(activity)
            .setTitle(getString(MR.strings.add_n_items_to_categories, queuedManga.size))
            .setMultiChoiceItems(categories.map { it.name }.toTypedArray()) { _, which, isChecked ->
                // Track selections
            }
            .setPositiveButton(AR.string.ok) { _, _ ->
                // Apply to all queued manga
                queuedManga.forEach { manga ->
                    presenter.moveMangaToCategories(manga, selectedCategories)
                }
                queuedManga.clear()
                updateBadge(0)
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }
}
```

#### Integration Point 3: Exclusion Database
```kotlin
// SwipesExclusionDatabase.kt
class SwipesExclusionDatabase(
    private val db: Database
) {
    // Scan library on first setup
    suspend fun initializeFromLibrary() {
        val libraryManga = db.mangaQueries.getFavorites().executeAsList()
        libraryManga.forEach { manga ->
            addToExclusionList(manga.id)
        }
    }
    
    // Periodic sync with library
    suspend fun syncWithLibrary() {
        val currentExclusions = getExclusionList()
        val libraryManga = db.mangaQueries.getFavorites().executeAsList()
        val libraryIds = libraryManga.map { it.id }.toSet()
        
        // Add new library items to exclusion
        libraryIds.forEach { id ->
            if (!currentExclusions.contains(id)) {
                addToExclusionList(id)
            }
        }
        
        // Remove items no longer in library (optional)
        currentExclusions.forEach { id ->
            if (!libraryIds.contains(id)) {
                removeFromExclusionList(id)
            }
        }
    }
    
    // Check if manga should be excluded from swipes
    fun isExcluded(mangaId: Long): Boolean {
        return db.swipesQueries.isExcluded(mangaId).executeAsOne()
    }
}
```

#### Integration Point 4: Duplicate Detection
```kotlin
// When swiping right on already-owned manga
fun onSwipeRight(mangaItem: SwipeMangaItem) {
    // Check if already in library
    if (exclusionDatabase.isExcluded(mangaItem.manga.id)) {
        // Show toast
        activity.showToast(getString(MR.strings.already_in_library))
        // Add to exclusion database (redundant check)
        exclusionDatabase.addToExclusionList(mangaItem.manga.id)
        // Continue to next card
        return
    }
    
    // Proceed with normal right swipe logic
    // ...
}
```

---

## 6. Activity Architecture Research 🏗️

### Existing Activity Implementations

#### MangaDetailsActivity Pattern
**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsActivity.kt`

**Key Characteristics**:
- Extends `AppCompatActivity`
- Uses `ViewBinding` for layouts
- Integrates with existing `MangaDetailsPresenter`
- Supports shared element transitions
- Proper lifecycle management (onCreate/onResume/onPause/onDestroy)
- State persistence with `onSaveInstanceState`
- Theme integration with existing app styles

**Architecture Pattern**:
```kotlin
class MangaDetailsActivity : AppCompatActivity(),
    MangaDetailsAdapter.MangaDetailsInterface {
    
    private lateinit var binding: MangaDetailsActivityBinding
    private lateinit var presenter: MangaDetailsPresenter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Setup window transitions
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        
        // 2. Allow content behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        super.onCreate(savedInstanceState)
        
        // 3. Extract intent extras
        val mangaId = intent.getLongExtra(EXTRA_MANGA_ID, -1L)
        
        // 4. Initialize presenter
        presenter = MangaDetailsPresenter(mangaId)
        
        // 5. Setup view binding
        binding = MangaDetailsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 6. Apply window insets
        binding.root.doOnApplyWindowInsetsCompat { ... }
        
        // 7. Setup toolbar
        setSupportActionBar(binding.toolbar)
        
        // 8. Load data
        lifecycleScope.launch {
            presenter.refreshMangaFromDb()
            updateUI()
        }
    }
    
    companion object {
        fun newIntent(context: Context, mangaId: Long): Intent {
            return Intent(context, MangaDetailsActivity::class.java).apply {
                putExtra(EXTRA_MANGA_ID, mangaId)
            }
        }
    }
}
```

#### MainActivity Navigation to Activity
**Pattern**: Launch Activity from navigation tab

```kotlin
// MainActivity.kt - Navigation listener
nav.setOnItemSelectedListener { item ->
    when (item.itemId) {
        R.id.nav_library -> setRoot(LibraryController(), ...)
        R.id.nav_recents -> setRoot(RecentsController(), ...)
        R.id.nav_browse -> setRoot(BrowseController(), ...)
        R.id.nav_swipes -> {
            // Launch SwipesActivity instead of Controller
            val intent = SwipesActivity.newIntent(this)
            startActivity(intent)
            // Don't change the selected tab visually - return false
            return@setOnItemSelectedListener false
        }
    }
    true
}
```

### Swipes Activity Architecture

#### SwipesActivity.kt Structure
```kotlin
class SwipesActivity : AppCompatActivity(),
    SwipeCardAdapter.SwipeCardInterface {
    
    private lateinit var binding: SwipesActivityBinding
    private lateinit var presenter: SwipesPresenter
    private lateinit var cardStackView: CardStackView
    private lateinit var queueManager: SwipeQueueManager
    
    // Queue position persistence
    private var currentPosition = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Setup window features
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        
        // 2. Initialize components
        presenter = SwipesPresenter()
        queueManager = SwipeQueueManager(presenter)
        
        // 3. Setup view binding
        binding = SwipesActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 4. Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 5. Setup card stack
        setupCardStack()
        
        // 6. Restore queue position
        currentPosition = savedInstanceState?.getInt(KEY_POSITION) ?: 0
        
        // 7. Load recommendations
        lifecycleScope.launch {
            if (presenter.hasConfiguredSources()) {
                queueManager.loadInitialQueue()
                cardStackView.scrollToPosition(currentPosition)
            } else {
                showNoSourcesState()
            }
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save queue position
        outState.putInt(KEY_POSITION, cardStackView.topPosition)
    }
    
    override fun onPause() {
        super.onPause()
        // Cancel any in-progress swipe animation
        cardStackView.cancelPendingSwipe()
        // Save position to preferences
        preferences.swipeQueuePosition().set(cardStackView.topPosition)
    }
    
    override fun onResume() {
        super.onResume()
        // Sync exclusion database
        lifecycleScope.launch {
            exclusionDatabase.syncWithLibrary()
        }
    }
    
    companion object {
        private const val KEY_POSITION = "queue_position"
        
        fun newIntent(context: Context): Intent {
            return Intent(context, SwipesActivity::class.java)
        }
    }
}
```

#### Theming Consistency
**Pattern**: Use existing app themes and color attributes

```xml
<!-- swipes_activity.xml -->
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface">
    
    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/app_bar"
        style="@style/Widget.Tachiyomi.AppBarLayout"
        android:background="?attr/colorSurface">
        
        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            style="@style/Widget.Tachiyomi.Toolbar"
            app:navigationIconTint="?attr/actionBarTintColor"
            app:titleTextColor="?attr/actionBarTintColor" />
    </com.google.android.material.appbar.AppBarLayout>
    
    <!-- Card stack container -->
    <!-- ... -->
</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## 7. Icon System Research 🎨

### Current Icon Structure

#### Icon Locations
- `app/src/main/res/drawable/` - Vector drawable icons
- `app/src/main/res/drawable-*dpi/` - Raster icons (deprecated)

#### Icon Naming Convention
**Pattern**: `ic_<name>_<size>dp.xml` or `ic_<name>_selector_<size>dp.xml`

**Examples**:
- `ic_library_24dp.xml` - Outline version
- `ic_library_filled_24dp.xml` - Filled version
- `ic_library_selector_24dp.xml` - Selector (switches between outline/filled)

#### Selector Pattern
```xml
<!-- ic_library_selector_24dp.xml -->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/ic_library_filled_24dp" android:state_checked="true" />
    <item android:drawable="@drawable/ic_library_24dp" />
</selector>
```

### Icons Needed for Swipes Feature

#### 1. Navigation Tab Icon
**Files to create**:
- `ic_swipes_24dp.xml` - Outline/normal state
- `ic_swipes_filled_24dp.xml` - Filled/selected state
- `ic_swipes_selector_24dp.xml` - Selector drawable

**Visual Design**: Card stack (2-3 cards layered)
- Front card visible
- 1-2 cards peeking behind
- Simple, recognizable silhouette

**Example Vector Path** (conceptual):
```xml
<!-- ic_swipes_24dp.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    
    <!-- Back card -->
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="?attr/colorOnSurface"
        android:strokeWidth="2"
        android:pathData="M4,8 L16,8 A2,2 0,0,1 18,10 L18,18 A2,2 0,0,1 16,20 L4,20 A2,2 0,0,1 2,18 L2,10 A2,2 0,0,1 4,8 Z" />
    
    <!-- Middle card (offset) -->
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="?attr/colorOnSurface"
        android:strokeWidth="2"
        android:pathData="M6,6 L18,6 A2,2 0,0,1 20,8 L20,16 A2,2 0,0,1 18,18 L6,18 A2,2 0,0,1 4,16 L4,8 A2,2 0,0,1 6,6 Z" />
    
    <!-- Front card -->
    <path
        android:fillColor="?attr/colorOnSurface"
        android:fillAlpha="0.1"
        android:strokeColor="?attr/colorOnSurface"
        android:strokeWidth="2"
        android:pathData="M8,4 L20,4 A2,2 0,0,1 22,6 L22,14 A2,2 0,0,1 20,16 L8,16 A2,2 0,0,1 6,14 L6,6 A2,2 0,0,1 8,4 Z" />
</vector>
```

#### 2. Menu Icons
**Files to create**:
- `ic_filter_24dp.xml` (may already exist - check first)
- `ic_history_24dp.xml` (may already exist - check first)
- `ic_blacklist_24dp.xml` - New icon for blacklist management

#### 3. Swipe Direction Icons (for history display)
**Files to create**:
- `ic_swipe_left_24dp.xml` - Red X icon for skipped/blacklisted
- `ic_swipe_right_24dp.xml` - Green checkmark for added to library

#### Icon Research Checklist
- [x] Understand naming conventions
- [x] Identify selector pattern
- [ ] Check for existing card/stack icons in drawable folder
- [ ] Research Material Design icon guidelines for navigation icons
- [ ] Determine if custom icon design needed or can adapt existing

---

## 8. Data Flow & Source Integration Research 📊

### Source System Architecture

#### Source Discovery & Management
**Files**:
- `source/api/src/commonMain/kotlin/yokai/source/Source.kt`
- `app/src/main/java/eu/kanade/tachiyomi/source/SourceManager.kt`

#### Source Types
```kotlin
interface Source {
    val id: Long
    val name: String
}

interface CatalogueSource : Source {
    fun fetchPopularManga(page: Int): Observable<MangasPage>
    fun fetchLatestUpdates(page: Int): Observable<MangasPage>
    fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage>
}

interface HttpSource : CatalogueSource {
    val baseUrl: String
    fun fetchMangaDetails(manga: SManga): Observable<SManga>
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>>
}
```

#### Source Integration for Swipes

**Pattern 1: Get All Configured Sources**
```kotlin
// SwipesRepository.kt
class SwipesRepository(
    private val sourceManager: SourceManager,
    private val preferences: SwipesPreferences
) {
    fun getEnabledSources(): List<CatalogueSource> {
        val allSources = sourceManager.getCatalogueSources()
        val enabledSourceIds = preferences.swipeEnabledSources().get()
        
        return if (enabledSourceIds.isEmpty()) {
            // Default: ALL sources
            allSources
        } else {
            allSources.filter { it.id in enabledSourceIds }
        }
    }
}
```

**Pattern 2: Fetch Recommendations from Sources**
```kotlin
suspend fun fetchRecommendations(
    sources: List<CatalogueSource>,
    count: Int = 30
): List<SwipeMangaItem> {
    val recommendations = mutableListOf<SwipeMangaItem>()
    val filters = preferences.savedFilters().get()
    
    // Fetch from each source (with NSFW filtering)
    sources.forEach { source ->
        try {
            val page = source.fetchPopularManga(1).awaitSingle()
            val filteredManga = page.mangas
                .filter { manga ->
                    // Apply NSFW filter
                    if (!filters.includeNSFW) {
                        !manga.isNSFW()
                    } else true
                }
                .filter { manga ->
                    // Apply exclusion database check
                    !exclusionDatabase.isExcluded(manga.id)
                }
                .filter { manga ->
                    // Apply blacklist check
                    !blacklistManager.isBlacklisted(manga.id)
                }
            
            recommendations.addAll(filteredManga.map { SwipeMangaItem(it, source) })
        } catch (e: Exception) {
            // Log error but continue with other sources
            Logger.e(e) { "Failed to fetch from source: ${source.name}" }
        }
    }
    
    // Randomize and limit
    return recommendations.shuffled().take(count)
}
```

### Database Schema Research

#### Existing Schema (SQLDelight)
**Location**: `data/src/commonMain/sqldelight/tachiyomi/`

**Relevant Tables**:
```sql
-- manga table
CREATE TABLE mangas(
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    source INTEGER NOT NULL,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    thumbnail_url TEXT,
    favorite INTEGER NOT NULL,
    -- ... more columns
);

-- Manga categories (library)
CREATE TABLE mangas_categories(
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    manga_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL
);
```

#### New Tables for Swipes Feature

**Swipe History Table**:
```sql
CREATE TABLE swipe_history(
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    manga_id INTEGER NOT NULL,
    swipe_direction TEXT NOT NULL, -- 'left' or 'right'
    action TEXT NOT NULL, -- 'skip', 'blacklist', 'add_to_library'
    timestamp INTEGER NOT NULL,
    manga_title TEXT NOT NULL,
    manga_thumbnail_url TEXT,
    FOREIGN KEY(manga_id) REFERENCES mangas(_id) ON DELETE CASCADE
);

CREATE INDEX swipe_history_timestamp_idx ON swipe_history(timestamp DESC);
CREATE INDEX swipe_history_manga_idx ON swipe_history(manga_id);
```

**Blacklist Table**:
```sql
CREATE TABLE swipe_blacklist(
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    manga_id INTEGER NOT NULL UNIQUE,
    manga_title TEXT NOT NULL,
    manga_thumbnail_url TEXT,
    blacklisted_at INTEGER NOT NULL,
    FOREIGN KEY(manga_id) REFERENCES mangas(_id) ON DELETE CASCADE
);

CREATE INDEX swipe_blacklist_manga_idx ON swipe_blacklist(manga_id);
```

**Exclusion Database Table** (hidden from user):
```sql
CREATE TABLE swipe_exclusions(
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    manga_id INTEGER NOT NULL UNIQUE,
    added_at INTEGER NOT NULL,
    FOREIGN KEY(manga_id) REFERENCES mangas(_id) ON DELETE CASCADE
);

CREATE INDEX swipe_exclusions_manga_idx ON swipe_exclusions(manga_id);
```

**Queue Cache Table** (optional optimization):
```sql
CREATE TABLE swipe_queue_cache(
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    manga_id INTEGER NOT NULL,
    source_id INTEGER NOT NULL,
    position INTEGER NOT NULL, -- Position in queue
    cached_at INTEGER NOT NULL,
    FOREIGN KEY(manga_id) REFERENCES mangas(_id) ON DELETE CASCADE
);

CREATE INDEX swipe_queue_position_idx ON swipe_queue_cache(position ASC);
```

### Preference Storage Pattern

**File**: `app/src/main/java/eu/kanade/tachiyomi/data/preference/PreferencesHelper.kt`

**Pattern**: Use AndroidX Preference with SharedPreferences backend

```kotlin
class PreferencesHelper(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    
    // Swipes preferences
    fun swipeQueuePosition() = rxPrefs.getInteger("swipe_queue_position", 0)
    fun swipeIncludeNSFW() = rxPrefs.getBoolean("swipe_include_nsfw", false)
    fun swipeEnabledSources() = rxPrefs.getStringSet("swipe_enabled_sources", emptySet())
    fun swipePreloadSize() = rxPrefs.getInteger("swipe_preload_size", 30)
    fun swipeRefreshThreshold() = rxPrefs.getInteger("swipe_refresh_threshold", 5)
    
    // Complex object storage (JSON serialization)
    fun swipeSavedFilters() = object : Preference<SwipesFilters> {
        override fun get(): SwipesFilters {
            val json = prefs.getString("swipe_saved_filters", null)
            return json?.let { Json.decodeFromString(it) } ?: SwipesFilters()
        }
        
        override fun set(value: SwipesFilters) {
            val json = Json.encodeToString(value)
            prefs.edit().putString("swipe_saved_filters", json).apply()
        }
    }
}
```

---

## 9. Network & Caching Patterns 🌐

### Network Request Pattern (from Source implementations)

**Example: MangaDex source**
```kotlin
class MangaDexSource : HttpSource() {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga?limit=20&offset=${(page - 1) * 20}&order[followedCount]=desc")
    }
    
    override fun popularMangaParse(response: Response): MangasPage {
        val json = response.body.string()
        val results = json.parseAs<MangaDexResponse>()
        return MangasPage(results.data.map { it.toSManga() }, results.hasNextPage)
    }
}
```

### Caching Strategy for Swipes

**Pattern**: Cache manga metadata + thumbnails for offline swiping

```kotlin
class SwipesCacheManager(
    private val db: Database,
    private val imageLoader: CoilImageLoader
) {
    suspend fun cacheQueue(items: List<SwipeMangaItem>) {
        // 1. Cache manga metadata
        items.forEach { item ->
            db.swipeQueueCacheQueries.insert(
                manga_id = item.manga.id,
                source_id = item.source.id,
                position = items.indexOf(item),
                cached_at = System.currentTimeMillis()
            )
        }
        
        // 2. Preload thumbnails
        items.forEach { item ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(item.manga.thumbnail_url)
                    .build()
            )
        }
    }
    
    suspend fun getCachedQueue(): List<SwipeMangaItem> {
        val cached = db.swipeQueueCacheQueries
            .getAll()
            .executeAsList()
        
        return cached.map { cache ->
            val manga = db.mangaQueries.getMangaById(cache.manga_id).executeAsOne()
            val source = sourceManager.getOrStub(cache.source_id)
            SwipeMangaItem(manga, source)
        }
    }
}
```

---

## 10. External Library Research - CardStackView ✅

### Library Overview
**Repository**: [yuyakaido/CardStackView](https://github.com/yuyakaido/CardStackView)
**License**: Apache 2.0
**Stars**: 2.4k
**Status**: Mature (last updated 2022, stable)
**Installation**: `implementation "com.yuyakaido.android:card-stack-view:2.3.4"`

### Architecture Pattern
**Type**: Custom RecyclerView LayoutManager
**Base**: RecyclerView.LayoutManager (NOT a custom View)
**Advantage**: Reuses RecyclerView adapter pattern - familiar to Miko developers

### Core Components

#### 1. CardStackView (extends RecyclerView)
```kotlin
val cardStackView = findViewById<CardStackView>(R.id.card_stack_view)
cardStackView.layoutManager = CardStackLayoutManager(context, listener)
cardStackView.adapter = CardStackAdapter()
```

#### 2. CardStackLayoutManager
**Purpose**: Controls card positioning, animation, and swipe behavior

**Key Configuration Methods**:
```kotlin
val layoutManager = CardStackLayoutManager(context, object : CardStackListener {
    override fun onCardDragging(direction: Direction, ratio: Float) {
        // Real-time drag feedback (0.0 to 1.0)
    }
    
    override fun onCardSwiped(direction: Direction) {
        // Handle swipe completion (LEFT, RIGHT, TOP, BOTTOM)
    }
    
    override fun onCardRewound() {
        // Handle rewind action
    }
    
    override fun onCardCanceled() {
        // Handle canceled swipe (dragged less than threshold)
    }
    
    override fun onCardAppeared(view: View, position: Int) {
        // Card became visible (for preloading)
    }
    
    override fun onCardDisappeared(view: View, position: Int) {
        // Card removed from view (for cleanup)
    }
})

// Visual settings
layoutManager.setStackFrom(StackFrom.None)  // Card stack direction
layoutManager.setVisibleCount(3)  // How many cards visible behind top card
layoutManager.setTranslationInterval(8.0f)  // Spacing between cards (dp)
layoutManager.setScaleInterval(0.95f)  // Scale factor for cards behind (95%)
layoutManager.setMaxDegree(20.0f)  // Max rotation angle during swipe

// Swipe behavior
layoutManager.setSwipeThreshold(0.3f)  // 30% drag = committed swipe
layoutManager.setDirections(Direction.HORIZONTAL)  // Swipe directions allowed
layoutManager.setSwipeableMethod(SwipeableMethod.AutomaticAndManual)

// Gesture control
layoutManager.setCanScrollHorizontal(true)
layoutManager.setCanScrollVertical(false)  // Disable vertical swipes
```

### Key Features for Swipes Implementation

#### Feature 1: Manual Swipe with Cancellation
**Built-in**: Drag-to-swipe with threshold-based cancellation
- User drags card < 30% → **Cancel** (card returns to center)
- User drags card ≥ 30% → **Commit swipe** (card flies off screen)
- **Callback**: `onCardCanceled()` fires when swipe canceled

**Perfect for**: User exploration without commitment

#### Feature 2: Programmatic Swipe
```kotlin
// Automatic swipe with custom animation
val setting = SwipeAnimationSetting.Builder()
    .setDirection(Direction.Right)
    .setDuration(Duration.Normal.duration)  // 300ms
    .setInterpolator(AccelerateInterpolator())
    .build()

layoutManager.setSwipeAnimationSetting(setting)
cardStackView.swipe()  // Trigger programmatic swipe
```

**Use case**: Swipe buttons (left/right action buttons)

#### Feature 3: Rewind
```kotlin
val rewindSetting = RewindAnimationSetting.Builder()
    .setDirection(Direction.Bottom)  // Card comes from bottom
    .setDuration(Duration.Normal.duration)
    .setInterpolator(DecelerateInterpolator())
    .build()

layoutManager.setRewindAnimationSetting(rewindSetting)
cardStackView.rewind()  // Undo last swipe
```

**Critical**: Enables undo functionality for accidental swipes

#### Feature 4: Overlay Views
**Pattern**: Define overlay views in item layout XML

```xml
<!-- card_item.xml -->
<FrameLayout android:layout_width="match_parent" android:layout_height="match_parent">
    <!-- Main card content -->
    <ImageView android:id="@+id/manga_cover" ... />
    <TextView android:id="@+id/manga_title" ... />
    
    <!-- Left swipe overlay (Skip/Blacklist) -->
    <FrameLayout
        android:id="@+id/left_overlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone">
        
        <ImageView
            android:src="@drawable/ic_swipe_left_indicator"
            android:tint="@color/swipe_left_color" />
    </FrameLayout>
    
    <!-- Right swipe overlay (Add to Library) -->
    <FrameLayout
        android:id="@+id/right_overlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone">
        
        <ImageView
            android:src="@drawable/ic_swipe_right_indicator"
            android:tint="@color/swipe_right_color" />
    </FrameLayout>
</FrameLayout>
```

**Overlay IDs** (CardStackView recognizes automatically):
- `left_overlay` - Shown when swiping left
- `right_overlay` - Shown when swiping right
- `top_overlay` - Shown when swiping up
- `bottom_overlay` - Shown when swiping down

**Interpolator**: Custom alpha fade
```kotlin
layoutManager.setOverlayInterpolator(LinearInterpolator())
// Default: overlay alpha increases linearly with swipe distance
```

#### Feature 5: Paging Support
**Pattern**: Add more items to adapter without resetting position

```kotlin
// CORRECT: Use DiffUtil or notifyItemRangeInserted
val oldSize = adapter.itemCount
val newItems = fetchMoreRecommendations()
adapter.addItems(newItems)
adapter.notifyItemRangeInserted(oldSize, newItems.size)

// WRONG: Don't use notifyDataSetChanged - resets to position 0
adapter.notifyDataSetChanged()  // ❌ Avoid this
```

**Integration point**: Queue manager background refresh

#### Feature 6: Position Management
```kotlin
val topPosition = layoutManager.getTopPosition()  // Current card index
cardStackView.scrollToPosition(position)  // Jump to position (no animation)
cardStackView.smoothScrollToPosition(position)  // Animated scroll
```

**Use case**: Restore queue position after app backgrounding

#### Feature 7: Speed Limiting (Built-in)
**Automatic**: CardStackView prevents gesture conflicts
- Only processes one swipe at a time
- Ignores new touch events during animation
- Cancels pending swipe with `cardStackView.cancelPendingSwipe()`

**No custom speed limiting needed** - library handles this internally

### Recommended Configuration for Swipes

```kotlin
class SwipesActivity : AppCompatActivity(), CardStackListener {
    
    private lateinit var cardStackView: CardStackView
    private lateinit var layoutManager: CardStackLayoutManager
    private lateinit var adapter: SwipeCardAdapter
    
    private fun setupCardStack() {
        layoutManager = CardStackLayoutManager(this, this).apply {
            // Visual appearance
            setStackFrom(StackFrom.None)  // Don't offset stack
            setVisibleCount(3)  // Show 3 cards behind top card
            setTranslationInterval(8.0f)  // 8dp spacing
            setScaleInterval(0.95f)  // Scale to 95% per card
            setMaxDegree(20.0f)  // 20° max rotation
            
            // Swipe behavior
            setSwipeThreshold(0.3f)  // 30% threshold
            setDirections(Direction.HORIZONTAL)  // Left/right only
            setSwipeableMethod(SwipeableMethod.AutomaticAndManual)
            setCanScrollHorizontal(true)
            setCanScrollVertical(false)  // Disable vertical scrolling
            
            // Overlay interpolator
            setOverlayInterpolator(AccelerateDecelerateInterpolator())
        }
        
        adapter = SwipeCardAdapter()
        
        cardStackView.layoutManager = layoutManager
        cardStackView.adapter = adapter
    }
    
    // CardStackListener implementation
    override fun onCardDragging(direction: Direction, ratio: Float) {
        // Update overlay opacity based on ratio (0.0 to 1.0)
        // Provide haptic feedback at milestones (0.3, 0.5, 0.7)
        if (ratio >= 0.3f && !threshold30Reached) {
            performHapticFeedback()
            threshold30Reached = true
        }
    }
    
    override fun onCardSwiped(direction: Direction) {
        when (direction) {
            Direction.Left -> handleLeftSwipe()  // Skip or blacklist
            Direction.Right -> handleRightSwipe()  // Add to library
            else -> {}  // Shouldn't happen (vertical disabled)
        }
        
        // Check if need to load more items
        val remainingItems = adapter.itemCount - layoutManager.getTopPosition()
        if (remainingItems <= REFRESH_THRESHOLD) {
            loadMoreRecommendations()
        }
    }
    
    override fun onCardRewound() {
        // Handle undo action
        undoManager.removeLastAction()
    }
    
    override fun onCardCanceled() {
        // Swipe canceled - no action needed
    }
    
    override fun onCardAppeared(view: View, position: Int) {
        // Preload manga details for upcoming cards
        preloadManager.preloadMangaDetails(position)
    }
    
    override fun onCardDisappeared(view: View, position: Int) {
        // Cleanup resources for cards that left view
        preloadManager.cleanup(position)
    }
}
```

### Implementation Advantages

✅ **Mature & Stable**: 2.4k stars, Apache 2.0 license, 5+ years of production use
✅ **RecyclerView Pattern**: Familiar adapter pattern, ViewHolder support
✅ **Built-in Animations**: Swipe, rewind, cancel all handled
✅ **Overlay Support**: Native overlay system with interpolators
✅ **Paging Support**: Add items without position reset
✅ **Speed Limiting**: Automatic gesture conflict prevention
✅ **Lightweight**: No dependencies beyond RecyclerView
✅ **Customizable**: Extensive configuration options
✅ **Active Callbacks**: Real-time drag feedback, swipe detection

### Potential Issues & Solutions

❌ **Last Update 2022**: No active development
✅ **Solution**: Library is complete and stable, no breaking Android changes

❌ **No Kotlin Coroutines**: Uses Java patterns
✅ **Solution**: Easy to wrap callbacks with coroutine channels

❌ **No Compose Support**: View-based only
✅ **Solution**: Miko uses mixed View/Compose architecture, SwipesActivity can be View-based

### Alternative: Custom Implementation Analysis

**If building from scratch**, would need:
1. Custom RecyclerView LayoutManager (complex)
2. Touch event handling (GestureDetector, MotionEvent)
3. Animation framework (ObjectAnimator, ValueAnimator)
4. Swipe threshold logic
5. Overlay management system
6. Rewind/undo stack
7. Speed limiting mechanisms

**Estimated effort**: 3-4 weeks vs 1-2 days with CardStackView

**Recommendation**: ✅ Use CardStackView - proven, stable, perfect fit for requirements

---

## Summary & Key Takeaways

### Critical Integration Points Identified

1. **Navigation**: Add 4th tab to `bottom_navigation.xml` and handle in `MainActivity`
2. **Search**: Reuse `RecentsController` search pattern with filter integration
3. **Filters**: Combine `RecentsController` dialog + `BrowseController` advanced filters
4. **Settings**: Create `SettingsSwipesController` following existing pattern
5. **Library**: Integrate with existing category management and "add to library" workflows
6. **Activity**: Follow `MangaDetailsActivity` architecture pattern
7. **Icons**: Create card stack icon following existing selector pattern
8. **Database**: Add 4 new tables (history, blacklist, exclusions, queue cache)
9. **Sources**: Leverage existing `SourceManager` and `CatalogueSource` interfaces
10. **Caching**: Implement queue caching with Coil image preloading

### Reusable Components Identified

- ✅ `MaterialMenuSheet` for simple dialogs
- ✅ `MaterialAlertDialogBuilder` for confirmations
- ✅ Filter UI components from `BrowseController`
- ✅ Category selection from `LibraryController`
- ✅ Preference screens from `SettingsController`
- ✅ Image loading with Coil
- ✅ Database access with SQLDelight
- ✅ Preference management with `PreferencesHelper`
- ✅ Source fetching with `SourceManager`

### Architecture Decisions Made

1. **Activity-based**: Use Activity (not Controller) for better lifecycle control
2. **Queue Management**: Seamless background updates without UI refresh
3. **Database-First**: Persist everything (history, blacklist, exclusions, queue)
4. **Filter Integration**: Two-tab dialog (sources + content filters)
5. **Library Sync**: Periodic exclusion database updates
6. **NSFW Default**: Exclude by default, allow opt-in via settings
7. **Batch Processing**: Support queued category selection
8. **Speed Limiting**: Prevent rapid gesture conflicts

---

## Next Steps: External Research Phase

With internal codebase research complete, proceed to:

1. **External swipe library research** - CardStackView, custom implementations
2. **Card flip animation research** - Best practices and implementation approaches
3. **Speed limiting mechanisms** - Gesture control patterns
4. **Seamless queue management** - Background updates without visual disruption

---

*Document created: Phase 0.2 Research (Internal Codebase Analysis)*
*Next phase: Phase 0.1 External Research (Swipe Libraries & Best Practices)*
