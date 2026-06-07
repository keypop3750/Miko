# Yōkai Android Manga Reader - AI Development Guide

## Project Overview
Yōkai is a Kotlin/Android manga reader app forked from Tachiyomi/Mihon. It uses a multi-module architecture with Kotlin Multiplatform for shared business logic and Android-specific UI components.

## Architecture Principles

### CRITICAL DEFENSIVE RULES - NEVER VIOLATE
1. **ZERO STUBS ALLOWED**: Never use stubs, placeholders, temporary or minimalistic files - always use fully functional code.
2. **CONTENT-TYPE ISOLATION**: Mode switching must show ONLY the current mode's content - no mixed displays.
3. **NO CORNER CUTTING**: If something doesn't work, enhance the architecture - never simplify by breaking patterns.

### Multi-Module Structure
- **`:app`** - Main Android application with UI controllers and activities
- **`:core:main`** - Shared Kotlin multiplatform core logic 
- **`:data`** - Database layer with SQLDelight for persistence
- **`:domain`** - Business logic and use cases
- **`:source:api`** - Extension API for manga sources
- **`:presentation:core`** - Shared UI components and themes
- **`:i18n`** - Internationalization with MOKO resources

### Key Architectural Patterns
1. **MVP Pattern**: Controllers extend `BaseController`, work with Presenters that extend `BasePresenter`
2. **Repository Pattern**: Data access abstracted through repository interfaces in domain layer
3. **Source Extensions**: External manga sources implement `HttpSource` or `ParsedHttpSource` interfaces
4. **Database First**: SQLDelight generates type-safe database queries from `.sq` files

## Code Conventions

### Atomic Component Enhancement Method (Instead of File Replacement)
**CRITICAL for integrated development**: When enhancing existing files for content-type awareness, use this proven methodology:

1. **Interface Extraction**: Extract common behavior into ContentItem/ContentProvider interfaces
2. **Polymorphic Implementation**: Create implementations of common interfaces
3. **Behavioral Enhancement**: Modify existing controllers/presenters to use polymorphic interfaces
4. **Mode-Aware Switching**: Add ModeManager integration for reactive content updates

Example enhancement pattern (Use as reference):
```kotlin
// Before (manga-only)
class LibraryPresenter {
    fun loadMangaLibrary() { /* manga-specific logic */ }
}

// After (content-type-aware)
class LibraryPresenter {
    fun loadContent(): Flow<List<ContentItem>> = 
        ModeManager.currentMode.flatMapLatest { mode ->
            contentRepository.getContentByType(mode)
        }
}
```

### Navigation
- Use Conductor framework: Controllers extend `BaseController<ViewBinding>`
- Navigation via `router.pushController()`, NOT Android Navigation Component
- Example: `router.pushController(MangaDetailsController(mangaId).withFadeTransaction())`

### UI Components  
- Mix of traditional Android Views and Jetpack Compose
- Compose screens in `yokai.presentation.*` packages use Material3 theme
- Legacy controllers in `eu.kanade.tachiyomi.ui.*` use ViewBinding
- Always use `YokaiTheme` wrapper for Compose content

### Database Operations
- SQLDelight queries in `data/src/commonMain/sqldelight/tachiyomi/`
- Repository pattern: interfaces in `:domain`, implementations in `:data`
- Use `Flow<T>` for reactive queries, `suspend fun` for one-shot operations
- Example: `getManga.awaitById(mangaId)` or `getAllManga().collectLatest { }`

### Dependency Injection
- Uses Injekt (lightweight DI): `by injectLazy()` for lazy injection
- Register dependencies in `App.kt`: `Injekt.register { ImplementationClass() }`
- Prefer constructor injection in ViewModels, lazy injection in presenters

### Source Extensions
- Sources have unique `id: Long` (usually 6-digit numbers)
- Implement `HttpSource` for HTTP-based sources, `ParsedHttpSource` for HTML parsing
- Override: `getPopularManga()`, `getLatestUpdates()`, `search()`, `getMangaDetails()`, `getChapterList()`, `getPageList()`

## Critical Implementation Details

### Reader System
- **ReaderActivity**: Main reading interface with configurable viewers
- **BaseViewer**: Abstract viewer (PagerViewer, WebtoonViewer implementations)
- **ViewerChapters**: Manages current/prev/next chapter state and page loading
- **ReaderConfig**: Handles reading settings (zoom, orientation, navigation)

### Library Management  
- **LibraryPresenter**: Manages manga collection, categories, and sorting
- **LibraryController**: Handles library UI with grid/list modes and search
- Categories are separate entities that can contain multiple manga
- Manga status: `SManga.ONGOING`, `SManga.COMPLETED`, `SManga.LICENSED`, etc.

### Download System
- **DownloadManager**: Queues and manages chapter downloads
- Downloads stored in app's external files directory
- Check download status: `downloadManager.isChapterDownloaded(chapter, manga)`

## Build System

### Gradle Configuration
- Uses Kotlin DSL (`build.gradle.kts`) with custom plugins in `buildSrc/`
- Version catalogs in `gradle/*.versions.toml` for dependency management
- Custom plugins: `yokai.android.application`, `yokai.android.library`, etc.
- Multi-variant builds: `standard` (Google services) and `dev` flavors

### Build Commands
```bash
./gradlew assembleStandardRelease  # Production build
./gradlew assembleDevDebug         # Development build
./gradlew lintKotlin              # Code style check
./gradlew formatKotlin            # Auto-format code
```

## Testing Patterns
- Unit tests in `src/commonTest/` for shared logic
- Android tests in `src/androidTest/` for UI components  
- Use MockK for mocking, Truth for assertions
- Test database operations with in-memory SQLite

## Common Pitfalls
- DON'T use Android Navigation Component - use Conductor
- DON'T access database directly - use repositories
- DON'T hardcode strings - use `yokai.i18n.MR.strings.*` resources
- DON'T mix Compose and View systems in same component
- ALWAYS handle source errors gracefully (network, parsing failures)
- REMEMBER to register new sources in source managers

## Performance Considerations
- Use `launchIO` for background operations, `withUIContext` for UI updates
- Implement proper pagination in source `search()` and `getPopularManga()`
- Cache manga covers and chapter pages appropriately
- Use `Flow` operators like `collectLatest` to handle rapid state changes
- Optimize RecyclerView with DiffUtil for large manga lists

## Novel Integration Architecture - IMPLEMENTATION COMPLETE ✅
The codebase has successfully integrated novel reading capabilities alongside manga with complete architectural separation. Current status: ~95% complete with working novel reader system.

### Novel Integration Status (January 2025)
- **Core Systems**: ✅ Complete - Database, providers, reader UI fully functional
- **Active Work**: Phase 5.2 overlay enhancement (30% complete)
- **Architecture**: Complete separation with dedicated novel components
- **Current Files**: `NovelReaderActivity.kt` (678 lines), `NovelReaderViewModel.kt` (482 lines)

### Database Schema Separation
```sql
-- Novel content uses completely separate tables
CREATE TABLE novel (novel_id, source_id, url, title, author, ...);
CREATE TABLE novel_chapter (chapter_id, novel_id, character_position, ...);
-- NO shared columns with manga tables to prevent corruption
```

### Direct Content Flow Architecture
- **Novel Reader**: Bypasses `ViewerChapters` (manga page-based structure)
- **Continuous Scrolling**: Text flows directly from provider to reader
- **Character-Level Tracking**: Precise bookmark positions within text content
- **Independent Navigation**: Novel chapters don't use manga's page-based navigation

### Provider Template System
```kotlin
abstract class NovelProviderTemplate : NovelMainAPI() {
    // Multi-tier error recovery: retry → cache → alternatives → partial
    final override suspend fun getChapterContent(url: String): String {
        return withErrorRecovery(
            operation = { performGetChapterContent(url) },
            fallback = { getCachedChapterContent(url) },
            default = "Content temporarily unavailable"
        )
    }
}
```

### UI Component Separation
- **Type-Safe Adapters**: `NovelLibraryAdapter` vs `MangaLibraryAdapter` (no shared inheritance)
- **Independent Controllers**: `NovelDetailsController` completely separate from `MangaDetailsController`
- **Content Routing**: `ContentRouter` handles mode-aware navigation without conditionals
- **Library Items**: `LibraryItem.Novel` vs `LibraryItem.Manga` sealed interface prevents cross-contamination

### Critical Architectural Differences from Manga
1. **Content Structure**: Novels use continuous text vs manga's discrete pages
2. **Progress Tracking**: Character position vs page numbers
3. **Reader Experience**: Continuous scrolling vs page flipping/webtoon scrolling
4. **Caching Strategy**: Text compression vs image caching
5. **Provider API**: Text extraction vs image URL parsing
6. **Navigation**: Chapter-to-chapter text flow vs page-based transitions

### Current Implementation Status
- **✅ Phases 1-5.1**: Complete - Database, providers, reader, RecyclerView architecture
- **🔄 Phase 5.2**: Overlay enhancement (30% complete) - Creating manga-style UI overlay
- **⏳ Future Phases**: Advanced features, performance optimization, analytics
- **📋 Active Files**: Working on `novel_reader_nav.xml`, `novel_chapters_sheet.xml` creation