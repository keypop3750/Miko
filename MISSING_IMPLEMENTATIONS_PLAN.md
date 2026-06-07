# Missing Implementations Plan - Novel Integration

**Document Created:** January 21, 2026  
**Status:** Active Development  
**Priority Legend:** 🔴 Critical | 🟡 Important | 🟢 Nice-to-have

---

## Executive Summary

This document tracks all missing or partial implementations needed to achieve feature parity between the manga and novel systems in Miko. The novel system is approximately 85% complete, with key gaps in downloads UI, tracking, and the extension architecture.

---

## 1. Library Features

### 1.1 Download Badges 🟡
**Status:** Partial  
**Current State:** `LibraryNovelItem.kt` has `downloadCount` property but it's not populated  
**Files to Modify:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryNovelItem.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryPresenter.kt`

**Implementation:**
```kotlin
// In LibraryPresenter when loading novels
novelItem.downloadCount = novelDownloadManager.getDownloadCount(novel)
```

**Effort:** 2 hours

---

### 1.2 Custom Cover Support 🟢
**Status:** Missing  
**Current State:** Novels use source cover only, no custom cover override  
**Files to Create/Modify:**
- Create `NovelCoverCache.kt` (similar to `MangaCoverCache.kt`)
- Modify `NovelDetailsControllerNew.kt` - Add cover edit dialog

**Reference:** `app/src/main/java/eu/kanade/tachiyomi/data/cache/CoverCache.kt`

**Effort:** 4 hours

---

## 2. Details View Features

### 2.1 Share Button 🟡
**Status:** Missing  
**Current State:** Header has no share functionality  
**Files to Modify:**
- `app/src/main/res/layout/novel_details_header_simple.xml` - Add share button
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsControllerNew.kt`

**Implementation:**
```kotlin
fun shareNovel() {
    val novel = presenter.novelValue ?: return
    val context = activity ?: return
    
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${novel.title}\n${novel.url}")
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}
```

**Effort:** 1 hour

---

### 2.2 Open in WebView 🟡
**Status:** Missing  
**Current State:** No way to view source page in WebView  
**Files to Modify:**
- `app/src/main/res/layout/novel_details_header_simple.xml` - Add webview button
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsControllerNew.kt`

**Implementation:**
```kotlin
fun openInWebView() {
    val novel = presenter.novelValue ?: return
    val source = presenter.source ?: return
    
    val activity = activity ?: return
    val intent = WebViewActivity.newIntent(activity, source.mainUrl, novel.url, novel.title)
    startActivity(intent)
}
```

**Effort:** 1 hour

---

### 2.3 Translator/Scanlator Filtering 🟢
**Status:** Missing  
**Current State:** No translator filter in chapter list  
**Reference:** `MangaDetailsController.kt` lines 1500-1600 (scanlator filter)

**Files to Modify:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsPresenter.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/chapter/NovelChaptersSortBottomSheet.kt`

**Effort:** 3 hours

---

### 2.4 Migration 🟢
**Status:** Missing (menu exists but not wired)  
**Current State:** `action_migrate` menu item does nothing  
**Files to Modify:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelDetailsControllerNew.kt`
- Create `NovelMigrationController.kt` or adapt existing migration UI

**Effort:** 8 hours (complex feature)

---

## 3. Browse/Source Features

### 3.1 Advanced Filters 🟡
**Status:** Missing  
**Current State:** Novel sources have no filter UI (tags, genres, status)  
**Reference:** `BrowseSourceController.kt` filter system

**Files to Create:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/source/browse/NovelBrowseSourceController.kt`
- Or modify existing to support novel source filters

**Note:** Requires extension API to expose filters

**Effort:** 6 hours

---

## 4. Reader Features

### 4.1 Reader Themes 🟡
**Status:** Missing  
**Current State:** Novel reader has basic dark/light, no sepia/custom themes  
**Files to Modify:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/reader/NovelReaderActivity.kt`
- Create `app/src/main/res/values/novel_reader_themes.xml`

**Themes to Add:**
- Light (white background, black text)
- Dark (dark gray background, light text)
- Sepia (cream background, dark brown text)
- AMOLED (pure black background, white text)
- Custom (user-defined colors)

**Effort:** 4 hours

---

### 4.2 Font Settings 🟡
**Status:** Partial  
**Current State:** Font size only, no font family selection  
**Files to Modify:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/reader/NovelReaderActivity.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/preference/PreferencesHelper.kt`

**Settings to Add:**
- Font family (serif, sans-serif, monospace, custom)
- Line height
- Paragraph spacing
- Text alignment
- Margins

**Effort:** 4 hours

---

## 5. Download Features

### 5.1 Download Queue UI 🔴
**Status:** Missing  
**Current State:** `NovelDownloadManager` exists but no UI to view queue  
**Files to Create:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/novel/NovelDownloadController.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/novel/NovelDownloadAdapter.kt`
- `app/src/main/res/layout/novel_download_controller.xml`

**Reference:** `app/src/main/java/eu/kanade/tachiyomi/ui/download/DownloadController.kt`

**Effort:** 6 hours

---

### 5.2 Download Notifications 🟡
**Status:** Missing  
**Current State:** No notification when downloads start/complete/fail  
**Files to Create:**
- `app/src/main/java/eu/kanade/tachiyomi/data/download/novel/NovelDownloadNotifier.kt`

**Reference:** `app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadNotifier.kt`

**Effort:** 3 hours

---

### 5.3 Download Progress Indicator 🟡
**Status:** Missing  
**Current State:** Chapter items don't show download progress  
**Files to Modify:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/details/NovelChapterAdapter.kt`
- `app/src/main/res/layout/novel_chapter_item.xml`

**Effort:** 2 hours

---

## 6. Tracking Features

### 6.1 Tracking Integration UI 🟡
**Status:** Partial  
**Current State:** DB models exist, tracking bottom sheet incomplete  
**Files to Create/Modify:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/track/NovelTrackingBottomSheet.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/novel/track/NovelTrackItem.kt`

**Trackers to Support:**
- Goodreads (priority - novels specific)
- AniList (if they support novels)
- Custom/local tracking

**Effort:** 8 hours

---

## 7. Extension Architecture 🔴

### 7.1 Novel Extension Framework
**Status:** Missing  
**Current State:** Sources hardcoded in `NovelProviderRegistry.kt`  
**Goal:** APK-based extensions like manga, loaded from GitHub repo

**Files to Create:**
```
app/src/main/java/eu/kanade/tachiyomi/extension/novel/
├── NovelExtensionManager.kt
├── NovelExtensionLoader.kt
├── NovelExtensionInstaller.kt
├── api/
│   └── NovelExtensionApi.kt
├── model/
│   ├── NovelExtension.kt
│   ├── NovelLoadResult.kt
│   └── NovelInstallStep.kt
└── util/
    └── NovelExtensionInstallReceiver.kt
```

**External Repo Structure:**
```
MikoNovelSources/
├── index.min.json
├── repo/
│   └── apk/
│       ├── miko-novel-en-royalroad-v1.0.apk
│       ├── miko-novel-en-scribblehub-v1.0.apk
│       └── ...
└── src/
    └── en/
        ├── royalroad/
        ├── scribblehub/
        └── ...
```

**Effort:** 20+ hours (major feature)

---

## Priority Implementation Order

### Phase 1: Critical (Week 1)
1. 🔴 Download Queue UI
2. 🔴 Extension Architecture (start)

### Phase 2: Important (Week 2)
3. 🟡 Download Badges in Library
4. 🟡 Share Button
5. 🟡 Open in WebView
6. 🟡 Download Notifications
7. 🟡 Reader Themes

### Phase 3: Nice-to-have (Week 3+)
8. 🟢 Custom Cover Support
9. 🟢 Translator Filtering
10. 🟢 Migration
11. 🟢 Advanced Source Filters
12. 🟡 Tracking Integration
13. 🟡 Font Settings

---

## Total Estimated Effort

| Priority | Hours |
|----------|-------|
| 🔴 Critical | 26 |
| 🟡 Important | 25 |
| 🟢 Nice-to-have | 15 |
| **Total** | **66 hours** |

---

## Dependencies & Blockers

1. **Extension Architecture** blocks:
   - Advanced source filters (need extension API)
   - Per-source settings
   - External source updates

2. **Tracking Integration** requires:
   - Goodreads API key/OAuth setup
   - Account linking UI

3. **Migration** requires:
   - Extension system (to have multiple sources)
   - Novel search across sources

---

## Notes

- All estimates are rough and may vary based on complexity discovered during implementation
- Manga implementations serve as reference for most features
- Extension architecture is the most impactful feature as it enables community contributions
