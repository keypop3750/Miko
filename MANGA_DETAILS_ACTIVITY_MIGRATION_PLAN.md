# Manga Details Activity Migration Plan
**Goal**: Migrate from Conductor Controller to Activity to enable true shared element transitions

**Scope**: Convert `MangaDetailsController` (2017 lines) to `MangaDetailsActivity`

**Estimated Effort**: 3-5 days of focused work + 2-3 days testing

**Risk Level**: 🔴 **HIGH** - Major architectural change affecting core navigation

---

## Table of Contents
1. [Migration Overview](#migration-overview)
2. [Rollback Strategy (No Git)](#rollback-strategy-no-git)
3. [Phase 1: Preparation & Backup](#phase-1-preparation--backup)
4. [Phase 2: Create Activity Skeleton](#phase-2-create-activity-skeleton)
5. [Phase 3: Migrate Core Functionality](#phase-3-migrate-core-functionality)
6. [Phase 4: Implement Shared Element Transitions](#phase-4-implement-shared-element-transitions)
7. [Phase 5: Dual Navigation System](#phase-5-dual-navigation-system)
8. [Phase 6: Testing & Validation](#phase-6-testing--validation)
9. [Phase 7: Switch Default & Cleanup](#phase-7-switch-default--cleanup)
10. [Rollback Procedures](#rollback-procedures)
11. [Risk Mitigation](#risk-mitigation)

---

## Migration Overview

### What We're Changing

**Before (Current)**:
```
LibraryController (Conductor)
    ↓ router.pushController()
MangaDetailsController (Conductor)
    - 2017 lines of code
    - Extends BaseCoroutineController
    - View lifecycle managed by Conductor
    - No true shared element support
```

**After (Target)**:
```
LibraryController (Conductor)
    ↓ startActivity() + ActivityOptions
MangaDetailsActivity (Activity)
    - Extends AppCompatActivity
    - Standard Android Activity lifecycle
    - Full shared element transition support
    - Window transition animations
```

### Why This Is Complex

**Challenges**:
1. **Size**: 2017 lines of interconnected code
2. **Dependencies**: Presenter, ViewBinding, Adapter, Chapter management
3. **State Management**: Parcelable state, savedInstanceState handling
4. **Navigation**: Deep linking, shortcuts, back stack behavior changes
5. **Lifecycle**: onAttach/onDetach → onCreate/onDestroy/onResume
6. **Testing**: Comprehensive validation needed across all features

---

## Rollback Strategy (No Git)

### File Naming Convention

We'll use a parallel file system with clear naming:

```
app/src/main/java/eu/kanade/tachiyomi/ui/manga/
├── MangaDetailsController.kt              ← ORIGINAL (will rename to _LEGACY)
├── MangaDetailsController_LEGACY.kt       ← BACKUP (excluded from build)
├── MangaDetailsActivity.kt                ← NEW IMPLEMENTATION
├── MangaDetailsActivityViewModel.kt       ← NEW (if needed)
└── chapter/
    ├── ChapterHolder.kt                   ← SHARED (may need updates)
    └── ChapterItem.kt                     ← SHARED
```

### Rollback Mechanism

**Feature Flag Pattern**:
```kotlin
// PreferencesHelper.kt
fun useMangaDetailsActivity() = preferenceStore.getBoolean(
    "use_manga_details_activity", 
    false  // Default: false (use Controller)
)
```

**Navigation Router**:
```kotlin
// LibraryController.kt
private fun openManga(manga: Manga, sourceView: View? = null) {
    if (preferences.useMangaDetailsActivity().get()) {
        // NEW: Launch Activity
        startMangaDetailsActivity(manga, sourceView)
    } else {
        // LEGACY: Push Controller
        router.pushController(MangaDetailsController_LEGACY.newInstance(manga))
    }
}
```

### Rollback Steps

**If Activity Implementation Fails**:
1. Set feature flag to `false` in Settings
2. App immediately uses legacy Controller
3. No data loss, no crashes
4. Can delete Activity files later

**If Need to Abandon Migration**:
1. Delete `MangaDetailsActivity.kt`
2. Delete `MangaDetailsActivityViewModel.kt` (if created)
3. Rename `MangaDetailsController_LEGACY.kt` → `MangaDetailsController.kt`
4. Remove feature flag preference
5. Remove dual navigation code
6. App returns to original state

---

## Phase 1: Preparation & Backup

### Step 1.1: Create Backup Structure

**Action**: Rename existing controller to legacy version

```bash
# PowerShell commands
cd "c:\Users\karol\OneDrive\Documents\GitHub\Miko\app\src\main\java\eu\kanade\tachiyomi\ui\manga"

# Create backup
Copy-Item "MangaDetailsController.kt" -Destination "MangaDetailsController_LEGACY.kt"
```

**Then manually edit `MangaDetailsController_LEGACY.kt`**:
```kotlin
// Change class name
class MangaDetailsController_LEGACY :  // Was: MangaDetailsController
    BaseCoroutineController<MangaDetailsControllerBinding, MangaDetailsPresenter>,
    // ... rest stays same
    
companion object {
    fun newInstance(mangaId: Long): MangaDetailsController_LEGACY {  // Return type changed
        // ...
    }
}
```

**Mark as excluded** (add to bottom of file):
```kotlin
/**
 * LEGACY BACKUP - DO NOT DELETE
 * 
 * This is the original Conductor-based implementation.
 * Kept for rollback purposes if Activity migration fails.
 * 
 * To rollback:
 * 1. Delete MangaDetailsActivity.kt
 * 2. Rename this file back to MangaDetailsController.kt
 * 3. Change class name back to MangaDetailsController
 * 4. Remove feature flag code
 */
```

**File Location**: `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsController_LEGACY.kt`

**Verification**:
- ✅ File copied and renamed
- ✅ Class name changed to `MangaDetailsController_LEGACY`
- ✅ Companion object returns correct type
- ✅ File builds successfully
- ✅ Backup comment added

---

### Step 1.2: Add Feature Flag Preference

**File**: `app/src/main/java/eu/kanade/tachiyomi/data/preference/PreferencesHelper.kt`

**Location**: Around line 175 (near other UI preferences)

```kotlin
// Add after enableSharedElementTransitions()
fun useMangaDetailsActivity() = preferenceStore.getBoolean(
    "use_manga_details_activity", 
    false  // Start with Controller (safe default)
)
```

**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/setting/controllers/SettingsAppearanceController.kt`

**Location**: Around line 155 (in Animation section)

```kotlin
switchPreference {
    key = "use_manga_details_activity"
    titleRes = MR.strings.use_manga_details_activity
    summaryRes = MR.strings.use_manga_details_activity_summary
    defaultValue = false
}
```

**File**: `i18n/src/commonMain/moko-resources/base/strings.xml`

**Location**: After shared element strings (line ~1257)

```xml
<!-- Manga Details Activity Migration -->
<string name="use_manga_details_activity">Use new manga details screen (experimental)</string>
<string name="use_manga_details_activity_summary">Activity-based implementation with true shared element transitions. Disable if experiencing issues.</string>
```

**Verification**:
- ✅ Preference helper method added
- ✅ Settings toggle added
- ✅ i18n strings added
- ✅ Default is `false` (uses legacy Controller)
- ✅ Builds successfully

---

### Step 1.3: Document Current Integration Points

**Create reference document**: `MANGA_DETAILS_INTEGRATION_POINTS.md`

```markdown
# MangaDetailsController Integration Points

## Navigation Sources (Who launches MangaDetailsController?)

1. **LibraryController** (line ~1603)
   - `openManga(manga, sourceView, clickX, clickY)`
   - Main entry point from library grid
   
2. **RecentsController** (line ~792)
   - Opens from recent manga list
   
3. **BrowseSourceController**
   - Opens from source browse results
   
4. **GlobalSearchController**
   - Opens from global search results
   
5. **Deep Links**
   - Shortcuts, notifications, external links
   
6. **MangaDetailsController itself**
   - Recommendations, "similar manga" navigation

## Data Passed to Controller

- `mangaId: Long` (required)
- Optional: chapter ID for auto-open
- Optional: fromSource flag

## Expected Return Behavior

- Back button: Return to previous controller
- State restoration after process death
- No explicit result passing (unlike Activity)

## External Dependencies

- MangaDetailsPresenter
- ChaptersAdapter (FlexibleAdapter)
- TrackingBottomSheet
- Category dialogs
- Download manager
- Reader launch
```

**Verification**:
- ✅ All navigation entry points documented
- ✅ Parameters cataloged
- ✅ Return behavior noted
- ✅ Dependencies listed

---

## Phase 2: Create Activity Skeleton

### Step 2.1: Create AndroidManifest.xml Entry

**File**: `app/src/main/AndroidManifest.xml`

**Location**: After `<activity android:name=".ui.reader.ReaderActivity">`

```xml
<!-- Manga Details Activity (Activity-based implementation) -->
<activity
    android:name=".ui.manga.MangaDetailsActivity"
    android:theme="@style/Theme.Tachiyomi"
    android:configChanges="orientation|screenSize|screenLayout"
    android:windowSoftInputMode="adjustResize"
    android:exported="false">
    
    <!-- Enable shared element transitions -->
    <meta-data
        android:name="android.app.ACTIVITY_TRANSITIONS"
        android:value="true" />
</activity>
```

**Verification**:
- ✅ Activity declared in manifest
- ✅ Theme set correctly
- ✅ Config changes handled
- ✅ Transitions enabled via meta-data

---

### Step 2.2: Create Activity Skeleton File

**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsActivity.kt`

**Initial Implementation** (~150 lines):

```kotlin
package eu.kanade.tachiyomi.ui.manga

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import eu.kanade.tachiyomi.databinding.MangaDetailsControllerBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.getParcelableExtraCompat

/**
 * Activity-based implementation of Manga Details screen.
 * 
 * Replaces MangaDetailsController to enable true shared element transitions.
 * 
 * **Migration Status**: Phase 2 - Skeleton
 * 
 * Architecture:
 * - Extends AppCompatActivity (standard Android Activity)
 * - Uses ViewBinding (same layout as Controller)
 * - Integrates with MangaDetailsPresenter (shared with Controller)
 * - Supports Activity shared element transitions
 * 
 * @see MangaDetailsController_LEGACY for original Controller implementation
 */
class MangaDetailsActivity : AppCompatActivity() {
    
    private lateinit var binding: MangaDetailsControllerBinding
    private lateinit var presenter: MangaDetailsPresenter
    
    private var mangaId: Long = -1L
    private var fromSource: Boolean = false
    
    companion object {
        private const val EXTRA_MANGA_ID = "manga_id"
        private const val EXTRA_FROM_SOURCE = "from_source"
        private const val TRANSITION_NAME = "manga_cover_transition"
        
        /**
         * Create intent for launching MangaDetailsActivity.
         * 
         * @param context Calling context
         * @param mangaId ID of manga to display
         * @param fromSource Whether navigated from source browse
         * @return Intent for starting activity
         */
        fun newIntent(
            context: Context,
            mangaId: Long,
            fromSource: Boolean = false
        ): Intent {
            return Intent(context, MangaDetailsActivity::class.java).apply {
                putExtra(EXTRA_MANGA_ID, mangaId)
                putExtra(EXTRA_FROM_SOURCE, fromSource)
            }
        }
        
        /**
         * Create intent with shared element transition options.
         * 
         * Enables Material Design shared element transitions from source view
         * (library cover, search result) to details screen cover.
         * 
         * @param context Calling context
         * @param mangaId ID of manga to display
         * @param sharedElement Source view to transition from (cover image)
         * @param fromSource Whether navigated from source browse
         * @return Pair of Intent and ActivityOptions bundle
         */
        fun newIntentWithTransitionOptions(
            context: Context,
            mangaId: Long,
            sharedElement: View,
            fromSource: Boolean = false
        ): Pair<Intent, Bundle?> {
            val intent = newIntent(context, mangaId, fromSource)
            intent.putExtra(TRANSITION_NAME, sharedElement.transitionName)
            
            val activityOptions = ActivityOptions.makeSceneTransitionAnimation(
                context as android.app.Activity,
                sharedElement,
                sharedElement.transitionName
            )
            
            return intent to activityOptions.toBundle()
        }
    }
    
    /**
     * Called when activity is created.
     * Sets up window transitions, binding, and presenter.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable window transitions if shared element present
        if (intent.extras?.getString(TRANSITION_NAME) != null) {
            window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        }
        
        super.onCreate(savedInstanceState)
        
        // Extract intent extras
        mangaId = intent.getLongExtra(EXTRA_MANGA_ID, -1L)
        fromSource = intent.getBooleanExtra(EXTRA_FROM_SOURCE, false)
        
        if (mangaId == -1L) {
            // Invalid manga ID, finish activity
            finish()
            return
        }
        
        // Setup view binding (reuse Controller layout)
        binding = MangaDetailsControllerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup shared element transition name
        intent.extras?.getString(TRANSITION_NAME)?.let { transitionName ->
            ViewCompat.setTransitionName(binding.mangaCover, transitionName)
        }
        
        // TODO: Initialize presenter
        // TODO: Setup toolbar
        // TODO: Setup RecyclerView
        // TODO: Setup FAB
        
        supportPostponeEnterTransition()  // Wait for content to load
    }
    
    override fun onDestroy() {
        // TODO: Cleanup presenter
        super.onDestroy()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // TODO: Save state
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // TODO: Restore state
    }
}
```

**Verification**:
- ✅ File created
- ✅ Builds successfully (even with TODOs)
- ✅ Companion object has newIntent methods
- ✅ Shared element transition support added
- ✅ Uses same ViewBinding as Controller
- ✅ Transition postponement for content loading

---

### Step 2.3: Test Basic Activity Launch

**Add temporary navigation code to LibraryController**:

**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryController.kt`

**Location**: Around line 1610 (in `openManga` method)

```kotlin
private fun openManga(
    manga: Manga,
    sourceView: View? = null,
    clickX: Float = sourceView?.width?.div(2f) ?: 0f,
    clickY: Float = sourceView?.height?.div(2f) ?: 0f
) {
    // TEMPORARY: Test Activity launch
    if (preferences.useMangaDetailsActivity().get()) {
        // NEW PATH: Launch Activity
        val intent = MangaDetailsActivity.newIntent(
            activity!!,
            manga.id,
            fromSource = false
        )
        startActivity(intent)
        return
    }
    
    // EXISTING: Controller navigation
    // ... rest of existing code
}
```

**Testing Steps**:
1. Build and install APK
2. Go to Settings → Appearance → Animation
3. Enable "Use new manga details screen"
4. Go to Library
5. Tap any manga
6. **Expected**: Empty MangaDetailsActivity launches
7. **Expected**: Back button returns to library
8. Disable setting
9. Tap manga again
10. **Expected**: Original MangaDetailsController_LEGACY opens

**Verification**:
- ✅ Activity launches when feature flag enabled
- ✅ Controller launches when feature flag disabled
- ✅ Back button works
- ✅ No crashes
- ✅ Screen is blank (expected at this stage)

---

## Phase 3: Migrate Core Functionality

### Step 3.1: Integrate MangaDetailsPresenter

**Current Issue**: Presenter expects Conductor Controller lifecycle

**Solution**: Create Activity-compatible presenter wrapper

**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaDetailsActivityPresenter.kt`

```kotlin
package eu.kanade.tachiyomi.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.domain.manga.models.Manga
import kotlinx.coroutines.launch

/**
 * ViewModel-based presenter for MangaDetailsActivity.
 * 
 * Wraps MangaDetailsPresenter logic in Activity-compatible lifecycle.
 * 
 * Architecture:
 * - Extends ViewModel (survives configuration changes)
 * - Delegates to MangaDetailsPresenter for business logic
 * - Exposes StateFlow/LiveData for UI observation
 */
class MangaDetailsActivityPresenter(
    private val mangaId: Long
) : ViewModel() {
    
    // TODO: Initialize presenter
    // TODO: Expose manga state
    // TODO: Expose chapters state
    // TODO: Handle presenter lifecycle
    
    init {
        viewModelScope.launch {
            // TODO: Load manga data
        }
    }
    
    override fun onCleared() {
        // TODO: Cleanup presenter
        super.onCleared()
    }
}
```

**Alternative**: Reuse existing MangaDetailsPresenter directly

**Decision Point**: Choose based on complexity of presenter integration

**Estimated Time**: 4-6 hours

---

### Step 3.2: Port View Setup Code

**Source**: `MangaDetailsController_LEGACY.kt` lines ~200-500

**Target**: `MangaDetailsActivity.kt`

**Key Sections to Port**:

1. **Toolbar Setup**
   ```kotlin
   // From onViewCreated()
   private fun setupToolbar() {
       setSupportActionBar(binding.toolbar)
       supportActionBar?.setDisplayHomeAsUpEnabled(true)
       binding.toolbar.setNavigationOnClickListener {
           onBackPressed()
       }
   }
   ```

2. **RecyclerView Setup**
   ```kotlin
   private fun setupRecyclerView() {
       adapter = FlexibleAdapter(null, this)
       binding.recycler.adapter = adapter
       binding.recycler.layoutManager = LinearLayoutManager(this)
       // ... etc
   }
   ```

3. **FAB Setup**
   ```kotlin
   private fun setupFab() {
       binding.fab.setOnClickListener {
           // Open first unread chapter
       }
   }
   ```

**Estimated Time**: 3-4 hours

---

### Step 3.3: Port Data Loading Logic

**Source**: `MangaDetailsPresenter.kt`

**Key Methods to Integrate**:
- `refreshAll()` - Load manga, chapters, tracking
- `fetchChaptersFromSource()` - Refresh chapter list
- `toggleFavorite()` - Add/remove from library
- `downloadChapters()` - Queue chapter downloads

**Estimated Time**: 4-6 hours

---

### Step 3.4: Port Menu/Options Handling

**Source**: `MangaDetailsController_LEGACY.kt` lines ~800-1200

**Key Methods**:
- `onCreateOptionsMenu()`
- `onOptionsItemSelected()`
- `onChapterClick()`
- `onChapterLongClick()`

**Estimated Time**: 2-3 hours

---

## Phase 4: Implement Shared Element Transitions

### Step 4.1: Configure Window Transitions

**File**: `MangaDetailsActivity.kt`

**Add to onCreate() before setContentView()**:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // Setup window transitions
    if (intent.extras?.getString(TRANSITION_NAME) != null) {
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        
        // Configure transition durations
        window.enterTransition = android.transition.Fade().apply {
            duration = 300
        }
        window.exitTransition = android.transition.Fade().apply {
            duration = 300
        }
        
        // Shared element transition
        window.sharedElementEnterTransition = android.transition.ChangeBounds().apply {
            duration = 350
        }
        window.sharedElementExitTransition = android.transition.ChangeBounds().apply {
            duration = 350
        }
    }
    
    super.onCreate(savedInstanceState)
    // ...
}
```

**Estimated Time**: 1 hour

---

### Step 4.2: Update LibraryController Navigation

**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryController.kt`

**Replace temporary test code**:

```kotlin
private fun openManga(
    manga: Manga,
    sourceView: View? = null,
    clickX: Float = sourceView?.width?.div(2f) ?: 0f,
    clickY: Float = sourceView?.height?.div(2f) ?: 0f
) {
    if (preferences.useMangaDetailsActivity().get()) {
        // NEW: Activity with shared element
        if (sourceView != null) {
            // Set transition name on source cover
            val coverView = sourceView.findViewById<ImageView>(R.id.cover_thumbnail)
            coverView?.let {
                ViewCompat.setTransitionName(it, "manga_cover_${manga.id}")
                
                val (intent, bundle) = MangaDetailsActivity.newIntentWithTransitionOptions(
                    activity!!,
                    manga.id,
                    it,
                    fromSource = false
                )
                startActivity(intent, bundle)
                return
            }
        }
        
        // Fallback without transition
        startActivity(MangaDetailsActivity.newIntent(activity!!, manga.id))
        return
    }
    
    // LEGACY: Controller path (existing code)
    // ... keep existing circular reveal code
}
```

**Estimated Time**: 2 hours

---

### Step 4.3: Test Shared Element Transitions

**Testing Checklist**:
- [ ] Enable Activity feature flag
- [ ] Tap library manga
- [ ] **Expected**: Cover morphs smoothly from library to details
- [ ] **Expected**: Other content fades in
- [ ] Press back
- [ ] **Expected**: Reverse transition works
- [ ] Tap from different positions
- [ ] **Expected**: Transition always smooth
- [ ] Disable flag
- [ ] **Expected**: Circular reveal still works

**Estimated Time**: 2-3 hours testing + fixes

---

## Phase 5: Dual Navigation System

### Step 5.1: Update All Navigation Entry Points

**Files to Update**:

1. **RecentsController.kt** (line ~792)
   ```kotlin
   private fun openManga(manga: Manga, view: View?) {
       if (preferences.useMangaDetailsActivity().get()) {
           // Activity path
       } else {
           // Controller path (existing)
       }
   }
   ```

2. **BrowseSourceController.kt**
   ```kotlin
   private fun onMangaClick(manga: Manga, view: View?) {
       if (preferences.useMangaDetailsActivity().get()) {
           // Activity path
       } else {
           // Controller path (existing)
       }
   }
   ```

3. **GlobalSearchController.kt**
   ```kotlin
   fun onMangaClick(manga: Manga) {
       if (preferences.useMangaDetailsActivity().get()) {
           // Activity path (no shared element from search)
       } else {
           // Controller path (existing)
       }
   }
   ```

**Estimated Time**: 3-4 hours

---

### Step 5.2: Handle Deep Links

**Update MainActivity** to route deep links to Activity:

**File**: `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`

```kotlin
private fun handleMangaDeepLink(mangaId: Long) {
    if (preferences.useMangaDetailsActivity().get()) {
        startActivity(MangaDetailsActivity.newIntent(this, mangaId))
    } else {
        router.pushController(MangaDetailsController_LEGACY.newInstance(mangaId))
    }
}
```

**Estimated Time**: 2 hours

---

## Phase 6: Testing & Validation

### Step 6.1: Feature Parity Checklist

**Core Features** (must all work in Activity):
- [ ] Display manga information (title, author, status, description)
- [ ] Load cover image from network
- [ ] Display chapter list
- [ ] Sort chapters (ascending/descending, by source/number)
- [ ] Filter chapters (read/unread, downloaded/not downloaded, bookmarked)
- [ ] Download chapters
- [ ] Mark chapters as read/unread
- [ ] Delete chapter downloads
- [ ] Open reader from chapter tap
- [ ] Add/remove manga from library
- [ ] Edit categories
- [ ] Track manga (MAL, AniList, etc.)
- [ ] Share manga
- [ ] Open in WebView
- [ ] Similar manga recommendations
- [ ] Migrate manga to different source
- [ ] Long press chapter menu
- [ ] Multi-select chapters
- [ ] Batch download
- [ ] Batch mark as read
- [ ] Search within chapters

**UI Features**:
- [ ] Toolbar appears/hides on scroll
- [ ] FAB appears/hides based on unread chapters
- [ ] Palette colors from cover
- [ ] Proper insets handling
- [ ] Landscape orientation
- [ ] Tablet/foldable support
- [ ] Dark theme support

**Lifecycle Features**:
- [ ] State survives rotation
- [ ] State survives process death
- [ ] Back button returns to previous screen
- [ ] Deep link opens correct manga
- [ ] Notification tap opens correct manga
- [ ] Shortcut opens correct manga

**Performance**:
- [ ] Smooth scrolling (60fps)
- [ ] Fast chapter list loading
- [ ] No memory leaks
- [ ] Proper image caching

**Estimated Time**: 1-2 days comprehensive testing

---

### Step 6.2: Regression Testing

**Test Activity AND Controller paths** (both must work):

**Test Matrix**:
```
Operation          | Activity Path | Controller Path
-------------------|---------------|------------------
Open from library  | ✓ Test        | ✓ Test
Open from recents  | ✓ Test        | ✓ Test
Open from search   | ✓ Test        | ✓ Test
Download chapters  | ✓ Test        | ✓ Test
Mark read          | ✓ Test        | ✓ Test
Add to library     | ✓ Test        | ✓ Test
Open reader        | ✓ Test        | ✓ Test
```

**Estimated Time**: 4-6 hours

---

### Step 6.3: Edge Case Testing

**Test Scenarios**:
- [ ] Manga with 1000+ chapters
- [ ] Manga with no chapters
- [ ] Manga from local source
- [ ] Manga with missing cover
- [ ] Network errors during load
- [ ] Rapid navigation (tap manga, immediate back, tap again)
- [ ] Multi-window mode
- [ ] Picture-in-picture mode (if applicable)
- [ ] Low memory conditions
- [ ] Airplane mode

**Estimated Time**: 3-4 hours

---

## Phase 7: Switch Default & Cleanup

### Step 7.1: Change Default to Activity

**After all testing passes**, switch default:

**File**: `app/src/main/java/eu/kanade/tachiyomi/data/preference/PreferencesHelper.kt`

```kotlin
fun useMangaDetailsActivity() = preferenceStore.getBoolean(
    "use_manga_details_activity", 
    true  // NOW: Default to Activity
)
```

**Announcement**: Update changelog, notify users of change

**Estimated Time**: 1 hour

---

### Step 7.2: Monitor for Issues

**Beta Period**: 1-2 weeks

**Collect Feedback**:
- Crash reports
- Performance metrics
- User feedback
- Edge case discoveries

**Rollback Threshold**: >5% crash rate increase → revert default to false

**Estimated Time**: Ongoing monitoring

---

### Step 7.3: Remove Legacy Code (Optional)

**Only after 100% confidence in Activity implementation**:

**Cleanup Steps**:
1. Remove feature flag preference
2. Delete `MangaDetailsController_LEGACY.kt`
3. Remove dual navigation code
4. Update all references to use Activity directly
5. Clean up unused imports

**WARNING**: This is permanent. Only do after extensive production testing.

**Estimated Time**: 2-3 hours

---

## Rollback Procedures

### Emergency Rollback (Immediate)

**If Activity implementation crashes or has critical bugs**:

**Step 1: Disable Feature Flag**
```kotlin
// PreferencesHelper.kt
fun useMangaDetailsActivity() = preferenceStore.getBoolean(
    "use_manga_details_activity", 
    false  // ROLLBACK: Use Controller
)
```

**Step 2: Push Hotfix**
- Change default in `PreferencesHelper.kt`
- Build APK
- Deploy immediately
- App uses Controller, no crashes

**Time to Rollback**: 10 minutes

---

### Controlled Rollback (Testing Phase)

**If Activity implementation incomplete or buggy**:

**Step 1: Keep Feature Flag Disabled**
- Don't change default from `false`
- Activity code exists but isn't used
- Controller continues working

**Step 2: Continue Development**
- Fix Activity issues
- Retest
- Enable flag when ready

**Time to Rollback**: 0 minutes (already safe)

---

### Complete Rollback (Abandon Migration)

**If Activity migration deemed not worth effort**:

**Step 1: Delete Activity Files**
```powershell
Remove-Item "MangaDetailsActivity.kt"
Remove-Item "MangaDetailsActivityPresenter.kt"  # If created
```

**Step 2: Restore Original Controller**
```powershell
Rename-Item "MangaDetailsController_LEGACY.kt" -NewName "MangaDetailsController.kt"
```

**Step 3: Edit Restored File**
```kotlin
// Change class name back
class MangaDetailsController :  // Was: MangaDetailsController_LEGACY
```

**Step 4: Remove Dual Navigation**
- Delete all `if (preferences.useMangaDetailsActivity().get())` blocks
- Keep only Controller navigation code
- Remove feature flag preference

**Step 5: Remove from Manifest**
```xml
<!-- Delete MangaDetailsActivity declaration -->
```

**Time to Rollback**: 30-45 minutes

---

## Risk Mitigation

### High-Risk Areas

1. **State Management**
   - **Risk**: Data loss on rotation or process death
   - **Mitigation**: Extensive lifecycle testing, proper savedInstanceState
   
2. **Back Stack Behavior**
   - **Risk**: Back button breaks, unexpected navigation
   - **Mitigation**: Test all navigation paths, handle task affinity

3. **Deep Links**
   - **Risk**: Shortcuts/notifications broken
   - **Mitigation**: Update all deep link handling, test thoroughly

4. **Memory Leaks**
   - **Risk**: Activity not releasing resources
   - **Mitigation**: Use LeakCanary, proper cleanup in onDestroy

5. **Presenter Lifecycle**
   - **Risk**: Presenter crashes due to lifecycle mismatch
   - **Mitigation**: ViewModel wrapper or careful lifecycle management

---

### Testing Strategy

**Phase Testing**:
- ✅ After Phase 2: Activity launches, back works
- ✅ After Phase 3: Core features work
- ✅ After Phase 4: Shared elements work
- ✅ After Phase 5: All entry points work
- ✅ After Phase 6: Full regression testing
- ✅ After Phase 7: Production monitoring

**Parallel Development**:
- Controller continues working during entire migration
- Feature flag allows instant rollback
- No "big bang" deployment risk

---

## Timeline Estimate

**Optimistic**: 3 days development + 2 days testing = 1 week

**Realistic**: 5 days development + 3 days testing + 1 week monitoring = 2 weeks

**Pessimistic**: 7 days development + 5 days testing + 2 weeks monitoring + debugging = 1 month

**Recommendation**: Plan for **2 weeks** focused work + monitoring period

---

## Success Criteria

**Migration is successful when**:
- ✅ All features from Controller work in Activity
- ✅ Shared element transitions work smoothly
- ✅ No increase in crash rate
- ✅ No performance regressions
- ✅ State management works correctly
- ✅ All navigation entry points work
- ✅ Users can opt-in/opt-out
- ✅ Rollback mechanism works

**Migration can be finalized when**:
- ✅ Beta testing shows <1% issue rate
- ✅ User feedback is positive
- ✅ All edge cases handled
- ✅ No critical bugs for 2 weeks

---

## Conclusion

This migration is **high-effort, high-risk, medium-reward**. 

**Pros**:
- ✅ True Material Design shared element transitions
- ✅ Standard Android Activity lifecycle
- ✅ Potentially better state management
- ✅ Future-proof architecture

**Cons**:
- ❌ 2000+ lines of code to migrate
- ❌ 2 weeks of focused development
- ❌ Risk of regressions
- ❌ Ongoing dual-path maintenance during transition

**Recommendation**: 
- Proceed if UX improvement justifies effort
- Use feature flag for safe rollback
- Keep Controller working as fallback
- Don't rush - thorough testing critical

**Alternative**: 
- Keep circular reveal (current Phase 3)
- Add thumbnail morphing overlay (Phase 4)
- 80% of visual benefit, 20% of effort
