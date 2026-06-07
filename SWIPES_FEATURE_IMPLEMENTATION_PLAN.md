# Swipes Feature Implementation Plan
**Tinder-style Manga Discovery Interface**

## Overview
Create a new "Swipes" feature as the 4th navigation item that allows users to discover manga through Tinder-style card swiping. Users can swipe left (no/skip/blacklist) or right (yes/add to library) on manga recommendations from their configured sources.

## Phase 0: Research & Discovery Phase 🔍
**CRITICAL FIRST STEP - Must be completed before any implementation**

### 0.1 External Research - Swipe Mechanics & Best Practices
- **Research Tinder-style swipe implementations**:
  - Android gesture detection best practices
  - Card stack libraries and custom implementations
  - **Card flip animation techniques** (primary animation requirement)
  - Performance optimization for large card stacks
  - Speed limiting and gesture interaction control
  
- **Study existing swipe libraries**:
  - CardStackView libraries
  - Custom ViewPager2 implementations  
  - RecyclerView with swipe gestures
  - Animation frameworks (Lottie, native Android animations)
  - **Seamless queue management** without visual refresh

- **UX/UI Best Practices**:
  - Card design patterns for content display
  - Overlay feedback systems (red/green overlays)
  - Loading states and empty states
  - Gesture threshold tuning (how far to swipe before action)
  - **Navigation-away gesture cancellation** patterns
  - **Speed swiping prevention** mechanisms

### 0.2 Internal Codebase Research - Existing Components Analysis
**MANDATORY: Research existing app components to avoid reinventing functionality**

#### Navigation System Analysis
- **File**: `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`
- **Research Goals**: How navigation tabs are implemented, how to add 4th tab
- **Key Questions**: Bottom navigation setup, fragment/activity management, tab icons

#### Search Bar Integration Research  
- **Files to examine**:
  - Recent screen search implementation
  - Library screen search implementation  
  - Source browse search implementation
- **Research Goals**: Consistent search bar patterns, novel toggle implementation
- **Key Questions**: Search bar styling, functionality patterns, filter integration

#### Filter System Analysis
- **Files to examine**:
  - `RecentsController` filter dialog implementation
  - Source browse filter implementation (`BrowseController`)
  - Any existing filter bottom sheets or dialogs
- **Research Goals**: Reusable filter components, dialog patterns, data flow
- **Key Questions**: Filter data structures, UI patterns, persistence

#### Settings Integration Research
- **Files to examine**:
  - `app/src/main/java/eu/kanade/tachiyomi/ui/setting/` directory
  - Existing settings categories and preferences
  - Settings screen navigation and organization
- **Research Goals**: Settings screen patterns, preference management, new category creation
- **Key Questions**: Settings architecture, preference storage, UI patterns

#### Library Integration Research  
- **Files to examine**:
  - Library management code
  - Category selection implementations
  - "Add to library" functionality across the app
- **Research Goals**: Existing category selection patterns, library addition workflows
- **Key Questions**: Category data structures, selection UI patterns, persistence

#### Activity Architecture Research
- **Files to examine**:
  - `MangaDetailsActivity.kt` (current activity implementation)
  - `MainActivity.kt` 
  - Activity navigation patterns in the app
- **Research Goals**: Activity-based patterns vs Controller patterns, navigation flow
- **Key Questions**: Activity lifecycle management, data passing, theming consistency

#### Icon System Research
- **Files to examine**:
  - `app/src/main/res/drawable/` for existing icons
  - Navigation icon implementations
  - Menu icon patterns
- **Research Goals**: Icon naming conventions, existing card-like icons, vector drawable patterns
- **Key Questions**: Icon sizing, theming, naming patterns

### 0.3 Data Flow Research
- **Manga Source Integration**: How sources provide manga data, recommendation patterns
- **Database Schema**: Existing manga storage, how to add blacklist/swipe history
- **Preference Storage**: How app preferences are stored and managed
- **Network Management**: How manga fetching is handled, caching patterns

### 0.4 Research Deliverables
- **External Research Summary**: Document of swipe implementation approaches with pros/cons
- **Internal Component Inventory**: List of reusable components with integration points
- **Architecture Decision Document**: Choice of swipe library/approach with rationale
- **Integration Strategy**: How swipes will integrate with existing systems
- **Queue Management Strategy**: Seamless background queue updates without visual disruption
- **Speed Control Analysis**: Best practices for preventing rapid gesture conflicts
- **Animation Framework Selection**: Card flip animation implementation approach

---

## Phase 1: Core Infrastructure & Navigation Integration 🏗️

### 1.1 Navigation System Integration
- **Add 4th navigation tab** to main navigation
- **Icon creation/selection** - find or create cards icon (card stack visual)
- **Activity registration** in manifest and navigation flow
- **Tab highlighting and selection state** management

### 1.2 SwipesActivity Creation (Activity Architecture)
- **Create `SwipesActivity.kt`** extending AppCompatActivity
- **Layout creation** with consistent app theming
- **Search bar integration** (copy pattern from other screens)
- **Toolbar setup** with menu items (filter, 3-dots menu with history)
- **Basic Activity lifecycle** management
- **Queue position persistence** - save/restore user's position in swipe queue
- **Navigation-away gesture cancellation** - reset partial swipes when leaving screen
- **"No sources" empty state** implementation

### 1.3 Settings Integration Foundation
- **Create `SwipesPreferences`** data class for swipe settings
- **Add "Swipes" category** to settings screen
- **Basic preference screen** structure creation
- **Default preference values** setup:
  - NSFW content exclusion (default: excluded)
  - Queue preload size (default: 30)
  - Queue refresh threshold (default: 5 swipes)
  - Haptic feedback (default: enabled)
  - Gesture sensitivity (default: medium)
- **Blacklist management interface** preparation

---

## Phase 2: Swipe Mechanics & Card System 🃏

### 2.1 Swipe Library Integration
- **Library selection** based on Phase 0 research (CardStackView vs custom)
- **Dependency addition** and gradle configuration
- **Basic swipe detection** implementation
- **Card stack setup** with placeholder data

### 2.2 Card UI Design & Implementation
- **Card layout creation** (`swipe_card_layout.xml`)
  - Large manga cover image (primary visual)
  - **Fixed-height info section** at bottom (no encroachment on cover)
  - **Hierarchical info display**: Title → Author → Status → Tags → Description (truncated)
  - **In-card scrolling with parallax effect**:
    - User can scroll down within card bounds
    - Image moves up with parallax effect (slower than scroll)
    - Info section scrolls normally revealing full description
    - Card container remains fixed size (no expansion)
    - Creates makeshift details view within swipe card
  - Proper theming and styling consistency
- **Card adapter implementation** for dynamic content
- **Image loading integration** (using existing Coil setup)
  - **Cover quality optimization**: Use highest resolution available from source
  - Fallback to lower quality if high-res unavailable
  - Implement cover upscaling if needed (with quality preservation)

### 2.3 Swipe Gesture Implementation
- **Left/right swipe detection** with appropriate thresholds
- **Visual feedback system** (red/green overlays during swipe)
- **Card flip animation system** for card transitions (research-driven)
- **Gesture cancellation** (return to center if not swiped far enough)
- **Speed limiting mechanism** - prevent rapid successive swipes
- **Interaction window control** - brief uninteractable period during animations
- **Haptic feedback integration** for swipe actions

---

## Phase 3: Data Management & Source Integration 📊

### 3.1 Recommendation Data Pipeline
- **Source integration** for fetching from ALL configured sources
- **On-demand details fetching strategy**:
  - Initial fetch: Use `getPopularManga()` for basic info (title, cover, URL)
  - Details fetch: Call `getMangaDetails()` WITHOUT chapters when card is displayed
  - Fetch only metadata: description, author, tags, status (no chapter list)
  - Cache details to avoid re-fetching for same manga
  - Handle inconsistent source data gracefully (some sources provide more info than others)
- **NSFW content filtering** (exclude by default, user setting override)
- **Random selection algorithm** from available sources
- **Data aggregation** from multiple sources
- **Caching strategy** for offline/performance
- **Dynamic queue management**:
  - Initial preload: 30 items
  - Background addition: +5 items every 5 swipes
  - Speed swiping buffer: maintain 10+ items ahead
  - Seamless background updates (no UI refresh)

### 3.2 Swipe History & Blacklist System
- **Database schema** for swipe history (last 50 items)
  - Swiped manga IDs
  - Swipe direction (left/right)  
  - Timestamp
  - Action taken (skip/blacklist/add to library)
  - Manga title and cover URL for history display
- **Permanent blacklist management**
  - Storage and retrieval with checkmark system
  - Blacklist viewing/editing in settings
  - **Undo blacklist functionality** - moves item to top of queue
  - Blacklist scope: Swipes feature only (doesn't affect other app areas)
- **Library exclusion database** (hidden from user)
  - Automatic library scan on first setup
  - Periodic background updates to sync with library changes
  - Prevents showing already-owned manga in queue

### 3.3 Library Integration
- **"Add to library" workflow** integration with existing library system
- **Category selection** system (default category vs user choice)
- **"Ask every time" mode** with additional **batch selection option**:
  - Dialog: "Queue selections and batch-select categories later?"
  - Batch processing interface for queued library additions
- **Library addition confirmation** and feedback
- **Duplicate handling**: Toast "This item is already in the library" + add to exclusion database
- **Exclusion database updates**: Periodic sync to prevent showing owned manga

---

## Phase 4: Advanced Features & Filtering 🔧

### 4.1 Filter System Implementation
- **Filter dialog creation** (reusing existing patterns from recents/browse)
- **Source selection tab** - toggle sources on/off for recommendations
- **Content filtering tab** - genre, tags, demographic, type, content rating
- **NSFW content toggle** - override default exclusion
- **Filter persistence** and application to recommendation pipeline
- **Queue rebuild on filter changes** - wipe and regenerate queue when filters change
- **Filter UI integration** with existing app patterns

### 4.2 Search Integration & History Feature
- **Search bar functionality** for finding specific manga to swipe
- **Search results integration** with swipe interface
- **Novel toggle preparation** (marked as TODO for future)
- **Three-dots menu history feature**:
  - Display last 50 swiped items
  - Show small cover image, title, and action icon (✓ added / ✗ skipped/blacklisted)
  - Quick access to recent swipe decisions

### 4.3 Settings Screen Completion
- **Swipe action configuration**:
  - Left swipe behavior (skip vs blacklist)
  - Right swipe behavior (library category selection)
  - "Ask every time" toggle with batch selection sub-option
- **Content preferences**:
  - NSFW content inclusion toggle
  - Source selection for recommendations (ALL by default)
  - **Language filtering** (currently English-only, add multi-language support)
- **Queue behavior settings**:
  - Preload size (default: 30, customizable)
  - Refresh threshold (default: 5 swipes, customizable)
  - Speed limiting sensitivity
- **Blacklist management interface**:
  - View blacklisted items with covers and titles
  - Undo blacklist functionality
  - Bulk blacklist management
- **Feedback settings**:
  - Haptic feedback toggle
  - Gesture sensitivity adjustment

---

## Phase 5: Polish & User Experience 🎨

### 5.1 Visual Polish & Animations
- **Smooth card transitions** and animations
- **Loading states** for recommendation fetching
- **Empty states** when no more cards available
- **Error states** for network/source issues
- **Feedback animations** for successful actions

### 5.2 Performance Optimization
- **Image loading optimization** for large card stacks
- **Memory management** for card recycling
- **Network optimization** for recommendation fetching
- **Seamless background queue updates** - no visual refresh when adding items
- **Speed swiping optimization** - buffer management for rapid gestures
- **Database query optimization** for blacklist/history/exclusion database
- **Periodic exclusion database sync** - efficient library change detection

### 5.3 Accessibility & Usability
- **Accessibility labels** and navigation
- **Alternative input methods** (buttons for non-swipe users)
- **Keyboard navigation** support
- **Screen reader compatibility**

---

## Phase 6: Testing & Integration 🧪

### 6.1 Feature Testing
- **Swipe gesture testing** across different devices
- **Integration testing** with existing library/source systems
- **Performance testing** with large recommendation queues
- **Settings persistence testing**

### 6.2 User Experience Testing
- **Flow testing** from navigation to completion
- **Error scenario testing** (network issues, empty sources)
- **Settings interaction testing**
- **Memory leak testing** for long swipe sessions

---

## Technical Architecture Decisions

### Activity-Based Architecture
- **SwipesActivity** as main entry point (not Controller)
- **Consistent theming** with existing Activity implementations
- **Proper lifecycle management** for swipe state
- **Integration with existing Activity navigation patterns**

### Key Components Structure
```
SwipesActivity.kt           - Main swipe interface Activity
SwipeCardAdapter.kt         - Card content management  
SwipeGestureHandler.kt      - Swipe detection, speed limiting, and actions
SwipesPreferences.kt        - Settings and preferences
SwipesRepository.kt         - Data management and sources
BlacklistManager.kt         - Permanent blacklist functionality
ExclusionDatabase.kt        - Hidden library exclusion system
SwipeHistoryManager.kt      - Last 50 swipes tracking
QueueManager.kt             - Dynamic queue with seamless background updates
CategoryBatchProcessor.kt   - Batch library category selection
RecommendationEngine.kt     - Future: ML recommendations (Phase 7+)
```

### Integration Points
- **MainActivity navigation** - 4th tab addition with queue position persistence
- **Settings system** - New category with comprehensive swipe preferences
- **Library system** - Category selection, batch processing, and duplicate detection
- **Source system** - ALL configured sources with NSFW filtering
- **Database system** - Swipe history, permanent blacklist, and hidden exclusion database
- **Stats system** - Integration with existing stats tracking (future phase)
- **Existing library monitoring** - Periodic sync for exclusion database updates

---

## Future Phases (Post-MVP)

### Phase 7: Intelligence & Learning 🧠
- **User preference learning** from swipe data only (no other app behavior)
- **Recommendation algorithm** improvement with filter integration
- **Genre/tag preference detection** from swipe patterns
- **Adaptive NSFW content filtering** based on user behavior

### Phase 8: Novel Integration 📚
- **Novel source integration** with swipe interface
- **Novel-specific filtering** and preferences
- **Unified content type switching**

### Phase 9: Statistics & Analytics 📊
- **Stats integration** with existing app stats system:
  - Total swipes performed
  - Items added to library via swipes
  - Items blacklisted via swipes
  - Most swiped genres/tags
  - Swipe accuracy (items kept vs removed from library later)
- **Swipe pattern insights** for user dashboard

### Phase 10: Quality of Life Improvements ✨
- **Onboarding flow** for new users
- **Advanced gesture sensitivity** settings
- **Custom swipe animations** and themes
- **Undo functionality** for recent swipes (optional alternative to history)
- **Social features** (optional): swipe sharing, community trends

---

## Critical Success Criteria

### Must Have (MVP)
- ✅ Functional swipe left/right mechanics with card flip animation
- ✅ Add to library functionality (right swipe) with existing library integration
- ✅ Skip/blacklist functionality (left swipe) with permanent blacklist
- ✅ Dynamic recommendation queue (30 preload, +5 every 5 swipes)
- ✅ ALL configured sources integration with NSFW filtering
- ✅ Settings integration for comprehensive swipe preferences
- ✅ Queue position persistence across navigation
- ✅ Library exclusion database (prevent showing owned manga)
- ✅ Speed swiping prevention and gesture control
- ✅ "No sources" empty state handling

### Should Have (Enhanced)
- ✅ Advanced filtering with queue rebuild on changes
- ✅ Blacklist management with undo functionality
- ✅ Batch category selection option
- ✅ Swipe history (last 50 items) in three-dots menu
- ✅ Seamless background queue updates (no visual refresh)
- ✅ Haptic feedback integration
- ✅ Duplicate detection with toast notifications
- ✅ Navigation-away gesture cancellation

### Could Have (Future)
- ✅ Learning algorithm based on swipe data only
- ✅ Novel integration with unified content switching
- ✅ Stats integration with existing app statistics
- ✅ Onboarding flow for new users
- ✅ Advanced gesture sensitivity settings
- ✅ Social features and community trends

---

## Risk Assessment & Mitigation

### Technical Risks
- **Swipe library performance** - Mitigation: Thorough Phase 0 research and performance testing
- **Memory usage with large queues** - Mitigation: Implement proper recycling and caching
- **Integration complexity** - Mitigation: Leverage existing patterns from Phase 0 research

### UX Risks  
- **Learning curve for new interface** - Mitigation: Intuitive design (onboarding planned for future)
- **Speed swiping and accidental actions** - Mitigation: Speed limiting, interaction windows, and proper gesture thresholds
- **Empty recommendation queues** - Mitigation: "No sources" empty state, dynamic queue management
- **Queue position loss** - Mitigation: Persistent queue position across navigation

### Data Risks
- **Source availability** - Mitigation: Multiple source fallbacks and error handling, graceful degradation
- **Blacklist data loss** - Mitigation: Permanent storage with proper backup mechanisms
- **Library exclusion database sync** - Mitigation: Periodic background updates with conflict resolution
- **Performance with large datasets** - Mitigation: Database optimization, pagination, and efficient queue management
- **Queue corruption during rapid swiping** - Mitigation: Thread-safe queue operations and speed limiting

---

## Implementation Timeline Estimate

- **Phase 0 (Research)**: 1-2 weeks
- **Phase 1 (Infrastructure)**: 2-3 weeks (includes queue persistence, navigation handling)
- **Phase 2 (Swipe Mechanics)**: 3-4 weeks (includes card flip animation, speed limiting)
- **Phase 3 (Data Management)**: 3-4 weeks (includes exclusion database, dynamic queue)
- **Phase 4 (Advanced Features)**: 3-4 weeks (includes history, batch processing, filtering)
- **Phase 5 (Polish)**: 2-3 weeks (includes seamless updates, haptic feedback)
- **Phase 6 (Testing)**: 2-3 weeks (includes speed swiping, edge case testing)

**Total Estimated Timeline**: 14-23 weeks for full feature completion

**Future Phases Timeline**:
- **Phase 7 (Learning)**: 2-3 weeks
- **Phase 8 (Novel Integration)**: 2-3 weeks  
- **Phase 9 (Statistics)**: 1-2 weeks
- **Phase 10 (Quality of Life)**: 2-4 weeks

---

*This plan will be updated and refined based on findings from Phase 0 research and ongoing development insights.*