# QuickNovel Architecture Alignment Plan for Miko

## ✅ **Current Status (Already Implemented)**

### Phase 0: Foundation (COMPLETE)
- ✅ Static `NovelProviderRegistry` (QuickNovel pattern)
- ✅ Koin for NovelRepository (QuickNovel pattern)
- ✅ Manual ViewModel factory (Koin AndroidX not available)
- ✅ Database schema with chapter tracking
- ✅ Chapter ID retrieval fix (INSERT OR IGNORE + query)
- ✅ ViewModel structure created
- ✅ Activity structure created

## 🚀 **Implementation Tasks (QuickNovel Alignment)**

### Task 1: ViewModel Initialization (CRITICAL - BLOCKING WHITE SCREEN)
**Status:** ❌ NOT IMPLEMENTED  
**QuickNovel Reference:** `ReadActivityViewModel.kt` lines 1050-1200

**What's Missing:**
- `viewModel.initialize()` is NEVER CALLED in Activity `onCreate()`
- No chapter content loading logic
- No LiveData/StateFlow observers in Activity

**Implementation Steps:**

1. **Call `initialize()` in Activity** (NovelReaderActivity.kt onCreate)
   ```kotlin
   // AFTER setting up observers
   viewModel.initialize(novelId, if (chapterId != -1L) chapterId else null)
   ```

2. **Implement Content Loading in ViewModel** (NovelReaderViewModel.kt)
   - Load novel from repository ✅ (already exists)
   - Get provider from static registry ✅ (already exists)
   - **MISSING:** Fetch chapter HTML from provider
   - **MISSING:** Parse HTML to displayable format
   - **MISSING:** Update StateFlow with content

---

### Task 2: Chapter Content Fetching (CRITICAL)
**Status:** ⚠️ PARTIALLY IMPLEMENTED  
**QuickNovel Reference:** `ReadActivityViewModel.kt` lines 668-810 (`loadIndividualChapter`)

**What's Missing:**
```kotlin
// ViewModel needs this method:
private suspend fun loadChapterContent(chapter: NovelChapter) {
    _uiState.update { it.copy(isLoading = true, error = null) }
    
    try {
        // 1. Fetch HTML from provider
        val html = currentProvider?.getChapterText(chapter.url)
            ?: throw Exception("Provider returned null content")
        
        // 2. Clean/parse HTML (QuickNovel uses preParseHtml + Markwon)
        val cleanedContent = parseChapterHtml(html)
        
        // 3. Update state
        _chapterContent.value = cleanedContent
        _currentChapter.value = chapter
        _uiState.update { it.copy(isLoading = false, error = null) }
        
        // 4. Mark chapter as read
        markChapterAsRead(chapter.id)
        
    } catch (e: Exception) {
        _uiState.update { 
            it.copy(isLoading = false, error = e.message ?: "Failed to load chapter") 
        }
    }
}
```

**QuickNovel's Flow:**
```
book.getChapterData(index, reload)
  → ctx.getQuickChapter(meta, chapterData, index, reload)
    → provider.loadHtml(url)
      → return cleaned HTML string
```

**Miko's Equivalent:**
```
viewModel.loadChapterContent(chapter)
  → provider.getChapterText(chapter.url)
    → RoyalRoadProvider.getChapterText(url)
      → return HTML string
        → parseChapterHtml(html)
          → return displayable text
```

---

### Task 3: HTML Parsing & Display (CRITICAL)
**Status:** ❌ NOT IMPLEMENTED  
**QuickNovel Reference:** `ReadActivityViewModel.kt` lines 730-780

**QuickNovel's Approach:**
- Uses **Markwon** library to parse HTML → Spanned (styled text)
- `markwon.toMarkdown(rawText)` converts HTML to displayable format
- Handles images via Glide integration
- Creates TextSpans for TTS and character tracking

**Miko's Options:**

#### Option A: Markwon (Match QuickNovel - RECOMMENDED)
```kotlin
// Add to build.gradle.kts
dependencies {
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    implementation("io.noties.markwon:image-glide:4.6.2")
}

// In ViewModel
private val markwon: Markwon by lazy {
    Markwon.builder(context)
        .usePlugin(HtmlPlugin.create())
        .usePlugin(GlideImagesPlugin.create(context))
        .build()
}

private fun parseChapterHtml(html: String): Spanned {
    val rawText = preParseHtml(html) // Remove scripts, clean up
    return markwon.toMarkdown(rawText)
}
```

#### Option B: Android Html.fromHtml (Simpler, Less Features)
```kotlin
private fun parseChapterHtml(html: String): Spanned {
    val cleaned = cleanHtml(html)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(cleaned, Html.FROM_HTML_MODE_COMPACT)
    } else {
        Html.fromHtml(cleaned)
    }
}
```

**Recommendation:** Use **Markwon** to match QuickNovel's architecture.

---

### Task 4: Activity Observers (CRITICAL)
**Status:** ❌ NOT IMPLEMENTED  
**QuickNovel Reference:** `ReadActivity2.kt` lines 1075-1090

**What's Missing:**
```kotlin
// In NovelReaderActivity.observeViewModel()
private fun observeViewModel() {
    lifecycleScope.launch {
        // Observe chapter content changes
        viewModel.chapterContent.collectLatest { content ->
            binding.novelContentText.text = content
            // Reset scroll to top
            binding.novelScrollView.scrollTo(0, 0)
        }
    }
    
    lifecycleScope.launch {
        // Observe loading state
        viewModel.uiState.collectLatest { state ->
            binding.loadingProgress.isVisible = state.isLoading
            binding.novelContentText.isVisible = !state.isLoading && state.error == null
            binding.errorText.isVisible = state.error != null
            binding.errorText.text = state.error
        }
    }
    
    lifecycleScope.launch {
        // Observe chapter changes for UI updates
        viewModel.currentChapter.collectLatest { chapter ->
            currentChapter = chapter
            supportActionBar?.title = chapter?.title ?: "Loading..."
            updateNavigationButtons()
        }
    }
    
    lifecycleScope.launch {
        // Observe events (errors, toasts, etc)
        viewModel.events.collectLatest { event ->
            when (event) {
                is NovelReaderEvent.ShowError -> toast(event.message, Toast.LENGTH_LONG)
                is NovelReaderEvent.ShowToast -> toast(event.message, Toast.LENGTH_SHORT)
                // ... other events
            }
        }
    }
}
```

**QuickNovel's Pattern:**
```kotlin
observe(viewModel.chapter) { chapter ->
    textAdapter.submitList(chapter.data) {
        if (chapter.seekToDesired) {
            scrollToDesired()
        }
        onScroll()
    }
}
```

---

### Task 5: Provider Method Implementation
**Status:** ⚠️ NEEDS VERIFICATION  
**QuickNovel Reference:** `RoyalRoadProvider.kt` line 363+ (`loadHtml`)

**Check if providers have:**
```kotlin
suspend fun getChapterText(url: String): String
```

If not, need to add to `NovelMainAPI` interface and implement in all providers.

**Current Status:** Need to verify `RoyalRoadProvider` has this method.

---

### Task 6: Error Handling & Resource Pattern (MEDIUM PRIORITY)
**Status:** ⚠️ PARTIAL  
**QuickNovel Reference:** `ReadActivityViewModel.kt` uses `Resource<T>` sealed class

**QuickNovel's Resource Pattern:**
```kotlin
sealed class Resource<T> {
    data class Success<T>(val value: T) : Resource<T>()
    data class Failure<T>(
        val isNetworkError: Boolean,
        val errorCode: Int?,
        val errorResponse: String?,
        val errorString: String
    ) : Resource<T>()
    data class Loading<T>(val url: String? = null) : Resource<T>()
}
```

**Miko's Current Approach:**
```kotlin
data class NovelReaderUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
```

**Recommendation:** Keep Miko's simpler approach for now, or adopt Resource pattern if needed for consistency.

---

## 📋 **Implementation Priority**

### Phase 1: Make It Work (Fix White Screen) ⚡ IMMEDIATE
1. ✅ Call `viewModel.initialize()` in Activity onCreate (ALREADY EXISTS)
2. ❌ Implement `loadChapterContent()` in ViewModel
3. ❌ Add HTML parsing (Markwon or Html.fromHtml)
4. ❌ Add Activity observers for content display
5. ❌ Test: Click chapter → See content

### Phase 2: Content Loading & Display 📖 HIGH
1. Verify provider `getChapterText()` method exists
2. Implement HTML cleaning/parsing
3. Handle loading states properly
4. Add retry on failure
5. Test: Multiple chapters, different sources

### Phase 3: Chapter Navigation & Position Tracking 🧭 HIGH
1. Implement previous/next chapter navigation
2. Character-level position tracking
3. Resume reading from last position
4. Update reading progress in database
5. Test: Navigate between chapters, close/reopen app

### Phase 4: Reader Preferences & Settings ⚙️ MEDIUM
1. Font size adjustment
2. Background color themes
3. Brightness control
4. Screen orientation lock
5. Keep screen awake option

### Phase 5: Advanced Features 🚀 LOW
1. Bookmarking
2. Text-to-Speech (TTS)
3. Chapter caching
4. Offline reading
5. Reading statistics

---

## 🔍 **Critical Code Locations**

### Files to Modify (Priority Order):

1. **NovelReaderViewModel.kt** (CRITICAL)
   - Add `loadChapterContent()` method
   - Implement HTML fetching from provider
   - Add HTML parsing logic

2. **NovelReaderActivity.kt** (CRITICAL)
   - Verify `initialize()` is called ✅ (DONE)
   - Add StateFlow observers for content
   - Update UI on content changes

3. **NovelMainAPI.kt** (VERIFY)
   - Check if `getChapterText()` exists
   - If not, add interface method

4. **RoyalRoadProvider.kt** (VERIFY)
   - Verify `getChapterText()` implementation
   - Check HTML parsing logic

5. **build.gradle.kts** (IF USING MARKWON)
   - Add Markwon dependencies

---

## 🎯 **Next Immediate Actions**

1. ✅ **Verify initialization call** - DONE (line 93 in NovelReaderActivity)
2. ❌ **Implement `loadChapterContent()`** in ViewModel
3. ❌ **Add Markwon or Html.fromHtml** for parsing
4. ❌ **Add content observer** in Activity
5. ❌ **Test end-to-end** flow

---

## 📊 **Comparison Matrix**

| Feature | QuickNovel | Miko Status |
|---------|-----------|-------------|
| Static Provider Registry | ✅ | ✅ DONE |
| Koin for Repositories | ✅ | ✅ DONE |
| ViewModel Init Call | ✅ `viewModel.init(intent, context)` | ✅ DONE `viewModel.initialize(novelId, chapterId)` |
| Content Fetching | ✅ `book.getChapterData()` | ❌ NOT IMPLEMENTED |
| HTML Parsing | ✅ Markwon | ❌ NOT IMPLEMENTED |
| Content Observer | ✅ `observe(viewModel.chapter)` | ❌ NOT IMPLEMENTED |
| UI Update | ✅ `textAdapter.submitList()` | ❌ NOT IMPLEMENTED |
| Loading States | ✅ `Resource<T>` | ⚠️ PARTIAL `UiState` |
| Error Handling | ✅ Comprehensive | ⚠️ PARTIAL |
| Chapter Navigation | ✅ | ⚠️ STRUCTURE EXISTS |
| Position Tracking | ✅ Character-level | ⚠️ STRUCTURE EXISTS |
| Chapter Caching | ✅ | ❌ NOT IMPLEMENTED |

---

## 🚦 **Implementation Status Summary**

- **DONE (✅):** 30% - Foundation complete, structure ready
- **IN PROGRESS (⚠️):** 10% - Partial implementations exist
- **TODO (❌):** 60% - Core content loading not implemented

**Current Blocker:** No content fetching/display logic = White screen

**Fix Required:** Implement Tasks 1-4 from Phase 1 above.

---

**END OF ALIGNMENT PLAN**
