# 🤖 AI Agent Project Guide - Yōkai Novel Integration Complete Reference

## 📋 **PROJECT OVERVIEW**

**Yōkai** is a Kotlin/Android manga reader app forked from Tachiyomi/Mihon with comprehensive **novel reading integration**. The project uses multi-module architecture with Kotlin Multiplatform shared logic and has successfully integrated novel reading capabilities alongside the existing manga system.

### **🎯 Current Project State (January 2025)**
- **Novel Integration**: ~95% Complete
- **Active Work**: Phase 5.2 Overlay Enhancement (30% complete)
- **Core Systems**: Fully functional novel reading with database, providers, and UI
- **Next Phase**: Complete manga-style overlay implementation

---

## 🏗️ **ARCHITECTURE FOUNDATIONS**

### **Multi-Module Structure**
```
:app                  # Main Android application & UI controllers
:core:main           # Shared Kotlin multiplatform core logic
:data                # Database layer with SQLDelight
:domain              # Business logic and use cases
:source:api          # Extension API for manga sources
:source:novel        # Novel provider system (NEW)
:presentation:core   # Shared UI components and themes
:i18n                # Internationalization with MOKO resources
```

### **Critical Architectural Patterns**
1. **MVP Pattern**: Controllers extend `BaseController`, work with Presenters extending `BasePresenter`
2. **Repository Pattern**: Data access abstracted through domain interfaces
3. **Provider System**: External sources implement `HttpSource`/`ParsedHttpSource` (manga) or `NovelMainAPI` (novels)
4. **Database First**: SQLDelight generates type-safe queries from `.sq` files
5. **Mode Separation**: Complete architectural separation between manga and novel content

---

## 📚 **NOVEL INTEGRATION ARCHITECTURE**

### **🔑 Core Novel Components** (100% Complete)

#### **1. Database Schema** (`data/src/commonMain/sqldelight/tachiyomi/`)
```sql
-- Complete separation from manga tables
CREATE TABLE novel (novel_id, source_id, url, title, author, description, ...);
CREATE TABLE novel_chapter (chapter_id, novel_id, character_position, ...);
CREATE TABLE novel_bookmark (bookmark_id, novel_id, chapter_id, character_position, ...);
```

#### **2. Provider System** (`source/novel/`)
- **`NovelMainAPI.kt`**: Abstract base class for novel sources
- **`NovelProviderRegistry.kt`**: Central provider management
- **`RoyalRoadProvider.kt`**: Example implementation
- **Template Pattern**: Standardized error handling and content extraction

#### **3. Repository Layer** (`data/src/commonMain/kotlin/`)
- **`NovelRepository.kt`**: Data layer abstraction
- **Koin Integration**: Dependency injection throughout novel system
- **Reactive Queries**: Flow-based data streaming

#### **4. Reader System** (`app/src/main/java/.../ui/novel/`)
- **`NovelReaderActivity.kt`** (678 lines): Main reading interface
- **`NovelReaderViewModel.kt`** (482 lines): Business logic layer
- **`NovelContentAdapter.kt`**: RecyclerView paragraph rendering
- **Character Position Tracking**: Precise bookmark persistence

### **🎨 Novel Reader Technical Details**

#### **Content Rendering Architecture**
```kotlin
// RecyclerView-based paragraph system
NovelContentAdapter -> List<NovelContentItem>
├── ParagraphItem (main text)
├── ChapterHeaderItem (chapter titles)
└── LoadingItem (chapter transitions)

// Markwon HTML parsing
Markwon.Builder(context)
    .usePlugin(HtmlPlugin.create())
    .build()
    .toMarkdown(htmlContent) -> Spanned
```

#### **Position Tracking System**
```kotlin
// Character-level precision
data class NovelProgress(
    val chapterId: Long,
    val characterPosition: Int,
    val totalCharacters: Int,
    val percentComplete: Float
)

// Debounced position saving (300ms)
private val positionSaveJob = scope.launch {
    delay(300)
    novelRepository.updateProgress(progress)
}
```

#### **Gesture Detection System**
```kotlin
// GestureDetectorWithLongTap pattern
class GestureDetectorWithLongTap : GestureDetector.SimpleOnGestureListener() {
    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        // Toggle overlay visibility
        toggleReaderOverlay()
        return true
    }
    
    override fun onLongPress(e: MotionEvent) {
        // Enable text selection mode
        performHapticFeedback()
        enableTextSelection()
    }
}
```

---

## 🚧 **CURRENT ACTIVE WORK: PHASE 5.2 OVERLAY**

### **📊 Implementation Status**
- **Phase 5.2.1**: Navigation Bar Layout ❌ **NOT CREATED**
- **Phase 5.2.2**: Bottom Sheet Layout ❌ **NOT CREATED** 
- **Phase 5.2.3**: Activity Layout Update ❌ **INCOMPLETE**
- **Phase 5.2.4**: Controller Integration ❌ **NOT STARTED**
- **Phase 5.2.5**: ViewModel Updates ❌ **NOT STARTED**

### **🎯 Current vs Target Architecture**

#### **Current Novel Layout** (Basic)
```xml
<!-- novel_reader_activity.xml - Current State -->
CoordinatorLayout
├── RelativeLayout (content)
│   └── RecyclerView (✅ Working)
├── AppBarLayout (basic toolbar)
└── LinearLayout (simple bottom controls)
```

#### **Target Layout** (Manga-Style)
```xml
<!-- Target: Match reader_activity.xml complexity -->
CoordinatorLayout
├── FrameLayout (viewer_container)
├── AppBarLayout (enhanced toolbar)
├── FrameLayout (nav_layout) ← MISSING
│   └── include (novel_reader_nav) ← MISSING  
└── LinearLayout (novel_chapters_sheet) ← MISSING
```

### **🛠️ Files to Create for Phase 5.2**
1. **`novel_reader_nav.xml`** - Navigation bar with chapter controls
2. **`novel_chapters_sheet.xml`** - Bottom sheet with 4 action buttons
3. Update **`novel_reader_activity.xml`** - Complex overlay structure
4. Update **`NovelReaderActivity.kt`** - Wire new overlay components
5. Update **`NovelReaderViewModel.kt`** - Chapter navigation integration

---

## 🔧 **DEVELOPMENT PATTERNS & CONVENTIONS**

### **Navigation System**
- **Framework**: Conductor (NOT Android Navigation Component)
- **Pattern**: `router.pushController(ControllerName().withFadeTransaction())`
- **Controllers**: Extend `BaseController<ViewBinding>`

### **UI Architecture**
- **Mix**: Traditional Android Views + Jetpack Compose
- **Compose Screens**: `yokai.presentation.*` packages with Material3
- **Legacy Controllers**: `eu.kanade.tachiyomi.ui.*` with ViewBinding
- **Theme Wrapper**: Always use `YokaiTheme` for Compose content

### **Database Operations**
- **Queries**: SQLDelight in `data/src/commonMain/sqldelight/tachiyomi/`
- **Pattern**: Interfaces in `:domain`, implementations in `:data`
- **Reactive**: `Flow<T>` for streams, `suspend fun` for one-shot
- **Example**: `getManga.awaitById(id)` or `getAllManga().collectLatest { }`

### **Dependency Injection**
- **Framework**: Injekt (lightweight DI)
- **Lazy Injection**: `by injectLazy()` in presenters
- **Registration**: `Injekt.register { Implementation() }` in `App.kt`
- **Constructor Injection**: Preferred in ViewModels

---

## 📖 **NOVEL PROVIDER DEVELOPMENT**

### **Provider Template Pattern**
```kotlin
abstract class NovelProviderTemplate : NovelMainAPI() {
    // Error recovery hierarchy: retry → cache → alternatives → fallback
    final override suspend fun getChapterContent(url: String): String {
        return withErrorRecovery(
            operation = { performGetChapterContent(url) },
            fallback = { getCachedChapterContent(url) },
            default = "Content temporarily unavailable"
        )
    }
    
    // Template methods for subclasses
    abstract suspend fun performGetChapterContent(url: String): String
    abstract suspend fun searchNovels(query: String): List<SearchResult>
    abstract suspend fun getNovelDetails(url: String): NovelDetails
}
```

### **Provider Registration**
```kotlin
// NovelProviderRegistry.kt
object NovelProviderRegistry {
    private val providers = mapOf(
        1001L to RoyalRoadProvider(),
        1002L to NovelFullProvider(),
        // Add new providers here
    )
    
    fun getProvider(sourceId: Long): NovelMainAPI? = providers[sourceId]
    fun getAllProviders(): List<NovelMainAPI> = providers.values.toList()
}
```

---

## 🧪 **TESTING PATTERNS**

### **Novel Provider Testing**
```kotlin
// Test search functionality
val results = provider.search("test query")
assertThat(results).hasSize(greaterThan(0))

// Test novel loading
val novel = provider.load(results.first().url)
assertThat(novel.chapters).isNotEmpty()

// Test chapter content
val content = provider.loadHtml(novel.chapters.first().url)
assertThat(content).isNotNull()
assertThat(content).contains("expected content")
```

### **Database Testing**
```kotlin
// Use in-memory SQLite for tests
@Test
fun testNovelInsertion() = runTest {
    val novel = testNovel()
    novelRepository.insert(novel)
    
    val retrieved = novelRepository.getById(novel.id)
    assertThat(retrieved).isEqualTo(novel)
}
```

---

## ⚠️ **CRITICAL IMPLEMENTATION RULES**

### **🚫 NEVER VIOLATE THESE**
1. **ZERO STUBS**: Never use placeholders - always implement fully functional code
2. **CONTENT-TYPE ISOLATION**: Mode switching shows ONLY current mode's content
3. **NO CORNER CUTTING**: Enhance architecture rather than simplify when stuck
4. **DATABASE SEPARATION**: Never mix manga/novel tables or foreign keys
5. **PROVIDER ISOLATION**: Novel providers completely separate from manga sources

### **🔒 Defensive Architecture Patterns**
- **Mode Context Strategy**: Eliminates `if (isNovel)` conditionals
- **Compile-time Safety**: Wrong-mode access architecturally impossible
- **Error Recovery**: Multi-tier fallback in all provider operations
- **State Consistency**: All novel state management through StateFlow patterns

---

## 🛣️ **DEVELOPMENT ROADMAP**

### **✅ COMPLETED PHASES** (95% Complete)
- **Phase 1**: Database Schema ✅
- **Phase 2**: Provider System ✅  
- **Phase 3**: Repository Layer ✅
- **Phase 4**: Basic Reader UI ✅
- **Phase 5.1**: RecyclerView Architecture ✅

### **🔄 ACTIVE PHASE: 5.2 Overlay Enhancement** (30% Complete)
**Current Focus**: Create manga-style overlay system
**Files Needed**: 
- `novel_reader_nav.xml` (navigation bar)
- `novel_chapters_sheet.xml` (bottom sheet)
- Enhanced `novel_reader_activity.xml`
- Controller/ViewModel integration

### **⏳ FUTURE PHASES**
- **Phase 6**: Advanced Features (TTS, bookmarks, annotations)
- **Phase 7**: Performance Optimization
- **Phase 8**: Analytics & Insights

---

## 🔍 **DEBUGGING & TROUBLESHOOTING**

### **Common Issues & Solutions**
1. **Provider Registration**: Check `NovelProviderRegistry` for source ID conflicts
2. **Database Migration**: SQLDelight auto-generates migrations from schema changes
3. **Theme Issues**: Ensure `YokaiTheme` wrapper for all Compose content
4. **Navigation Problems**: Use Conductor router, not Android Navigation
5. **Memory Leaks**: Use `collectLatest` not `collect` for Flow subscriptions

### **Logging Patterns**
```kotlin
// Use consistent logging throughout
private val logger = LoggerFactory.getLogger(NovelReaderActivity::class.java)

logger.debug("Loading chapter: ${chapter.title}")
logger.error("Provider error", exception)
```

---

## 📚 **KEY REFERENCE FILES**

### **Architecture Documents**
- `NOVEL_INTEGRATION_ROADMAP.md` - High-level progress tracking
- `NOVEL_OVERLAY_MANGA_STYLE_PLAN.md` - Phase 5.2 detailed implementation
- `MEGA_PLAN_4_COMPLETE.md` - Complete integration architecture (archived)

### **Core Implementation Files**
- `NovelReaderActivity.kt` (678 lines) - Main reader controller
- `NovelReaderViewModel.kt` (482 lines) - Business logic
- `NovelMainAPI.kt` - Provider base class
- `NovelRepository.kt` - Data layer abstraction
- `novel_reader_activity.xml` - Current layout (needs Phase 5.2 enhancement)

### **Reference Implementations**
- `ReaderActivity.kt` - Manga reader for overlay pattern reference
- `reader_activity.xml` - Target layout structure
- `reader_nav.xml` - Navigation bar pattern
- `ReaderChapterSheet.kt` - Bottom sheet implementation pattern

---

## 🎯 **NEXT STEPS FOR AI AGENTS**

### **Immediate Priorities**
1. **Complete Phase 5.2**: Focus on overlay implementation
2. **Create Missing Layouts**: `novel_reader_nav.xml`, `novel_chapters_sheet.xml`
3. **Test Integration**: Ensure gesture detection works with new overlay
4. **Match Manga UX**: Consistent behavior between manga/novel readers

### **Long-term Goals**
1. **Performance Optimization**: Profile RecyclerView rendering
2. **Advanced Features**: TTS integration, reading analytics
3. **Provider Expansion**: Add more novel sources using template pattern
4. **User Experience**: Reading customization, theme options

---

## 💡 **SUCCESS PATTERNS**

### **What Works Well**
- **Separation of Concerns**: Novel system completely independent
- **Template Pattern**: Provider development is standardized
- **Database Design**: Clean schema without manga dependencies
- **State Management**: Reactive patterns with StateFlow
- **Error Handling**: Multi-tier recovery in provider operations

### **Architectural Decisions**
- **RecyclerView over ScrollView**: Better performance for long content
- **Character Position Tracking**: More precise than percentage-based
- **Markwon HTML Parser**: Robust rendering with plugin support
- **Koin Dependency Injection**: Lightweight and effective for novel system
- **SQLDelight**: Type-safe database queries reduce runtime errors

---

**🎯 CURRENT STATUS**: Novel integration is architecturally complete and functionally working. The primary remaining work is Phase 5.2 overlay enhancement to match manga reader's sophisticated UI patterns.

**📋 FOR NEW AI AGENTS**: Start by understanding the existing working novel reader, then focus on completing the overlay enhancement following the established patterns in the manga reader implementation.

**🔗 CRITICAL FILES TO READ FIRST**: 
1. `NovelReaderActivity.kt` - Understand current implementation
2. `NOVEL_INTEGRATION_ROADMAP.md` - Project status overview  
3. `ReaderActivity.kt` - Target patterns for overlay enhancement
4. `NOVEL_OVERLAY_MANGA_STYLE_PLAN.md` - Detailed Phase 5.2 plan