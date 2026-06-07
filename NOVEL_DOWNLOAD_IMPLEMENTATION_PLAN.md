# Novel Download Functionality - Complete Implementation Plan

## Executive Summary
This document outlines the complete architecture and implementation plan for adding novel chapter download functionality to Miko, based on analysis of:
1. **Miko's manga download system** - Image-based page downloads with queue management
2. **QuickNovel's approach** - Text-based chapter downloads with EPUB generation
3. **Novel-specific requirements** - Text storage, streaming, and offline reading

---

## 1. Core Architecture Overview

### 1.1 Key Differences: Manga vs Novel Downloads

| Aspect | Manga Downloads | Novel Downloads |
|--------|----------------|-----------------|
| **Content Type** | Images (Page objects) | Text (HTML/Plain text) |
| **Storage Format** | Individual page files (jpg/png) | Chapter text files (.txt/.html) |
| **Size** | Large (MB per chapter) | Small (KB per chapter) |
| **Caching Strategy** | Image caching with Coil/Glide | Text caching in memory/disk |
| **Progress Tracking** | Pages downloaded / total pages | Chapters downloaded / total |
| **Queue Priority** | Source-based (5 sources concurrently) | Single-threaded sequential |
| **Rate Limiting** | Per-source rate limiting | Per-provider rate limiting |

### 1.2 System Components (Novel-Specific)

```
┌─────────────────────────────────────────────────────────┐
│                  NovelDownloadManager                   │
│  - Queue management                                     │
│  - Download lifecycle coordination                      │
│  - Status tracking                                      │
└─────────────────┬───────────────────────────────────────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
┌───▼───┐   ┌─────▼─────┐   ┌──▼────────┐
│ Queue │   │ Downloader│   │  Storage  │
│       │   │           │   │           │
│ State │   │ Executor  │   │ Provider  │
└───────┘   └───────────┘   └───────────┘
                  │
        ┌─────────┼─────────┐
        │         │         │
    ┌───▼───┐ ┌───▼───┐ ┌───▼────┐
    │Source │ │ Cache │ │Notifier│
    │Access │ │       │ │        │
    └───────┘ └───────┘ └────────┘
```

---

## 2. Implementation Phases

### Phase 1: Core Download Infrastructure (Week 1)

#### 2.1 Create Novel Download Domain Models
**File:** `domain/src/commonMain/kotlin/yokai/domain/novel/download/models/NovelDownload.kt`

```kotlin
package yokai.domain.novel.download.models

import yokai.domain.novelchapter.models.NovelChapter
import yokai.domain.novel.models.Novel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a novel chapter download with status tracking.
 * Similar to manga Download but adapted for text content.
 */
data class NovelDownload(
    val novel: Novel,
    val chapter: NovelChapter,
    val source: Long, // Source ID
    
    // Download status
    private val _status: MutableStateFlow<State> = MutableStateFlow(State.NOT_DOWNLOADED),
    val status: StateFlow<State> = _status,
    
    // Progress (not really needed for text but kept for UI consistency)
    var downloadedBytes: Long = 0,
    var totalBytes: Long = 0,
) {
    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }
    
    fun setStatus(newStatus: State) {
        _status.value = newStatus
    }
    
    val statusFlow = _status
}
```

#### 2.2 Create Novel Download Storage Provider
**File:** `data/src/commonMain/kotlin/yokai/data/download/novel/NovelDownloadProvider.kt`

```kotlin
package yokai.data.download.novel

import android.content.Context
import com.hippo.unifile.UniFile
import yokai.domain.novel.models.Novel
import yokai.domain.novelchapter.models.NovelChapter
import yokai.domain.source.NovelSource

/**
 * Manages file system storage for downloaded novel chapters.
 * Structure: /novels/{source_id}/{novel_id}/{chapter_id}.txt
 */
class NovelDownloadProvider(
    private val context: Context
) {
    private val downloadsDir: UniFile
        get() = UniFile.fromFile(context.filesDir)!!
            .createDirectory("novels")!!
    
    /**
     * Returns the download directory for a novel.
     * Structure: /novels/{source_id}/{novel_id}/
     */
    fun getNovelDir(source: NovelSource, novel: Novel): UniFile {
        return downloadsDir
            .createDirectory(source.id.toString())!!
            .createDirectory(getNovelDirName(novel))!!
    }
    
    /**
     * Returns the chapter file for a specific chapter.
     * File: {chapter_id}.txt
     */
    fun getChapterFile(source: NovelSource, novel: Novel, chapter: NovelChapter): UniFile? {
        val novelDir = getNovelDir(source, novel)
        return novelDir.createFile("${chapter.id}.txt")
    }
    
    /**
     * Checks if a chapter is downloaded.
     */
    fun isChapterDownloaded(source: NovelSource, novel: Novel, chapter: NovelChapter): Boolean {
        val file = getChapterFile(source, novel, chapter)
        return file?.exists() == true && (file.length() ?: 0) > 100 // Minimum valid size
    }
    
    /**
     * Deletes a chapter file.
     */
    fun deleteChapter(source: NovelSource, novel: Novel, chapter: NovelChapter) {
        getChapterFile(source, novel, chapter)?.delete()
    }
    
    /**
     * Deletes all chapters for a novel.
     */
    fun deleteNovel(source: NovelSource, novel: Novel) {
        getNovelDir(source, novel).delete()
    }
    
    /**
     * Gets sanitized novel directory name.
     */
    private fun getNovelDirName(novel: Novel): String {
        return novel.id.toString().sanitizeFilename()
    }
    
    private fun String.sanitizeFilename(): String {
        val reservedChars = "|\\?*<\":>+[]/'."
        return this.map { if (it in reservedChars) '_' else it }.joinToString("")
    }
}
```

#### 2.3 Create Novel Download Cache
**File:** `data/src/commonMain/kotlin/yokai/data/download/novel/NovelDownloadCache.kt`

```kotlin
package yokai.data.download.novel

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import yokai.domain.novel.models.Novel
import yokai.domain.novelchapter.models.NovelChapter
import yokai.domain.source.NovelSource

/**
 * Tracks which novel chapters are downloaded.
 * Similar to manga DownloadCache but for text files.
 */
class NovelDownloadCache(
    private val context: Context,
    private val provider: NovelDownloadProvider,
) {
    private val _downloadedChapters = MutableStateFlow<Set<Long>>(emptySet())
    val downloadedChapters: Flow<Set<Long>> = _downloadedChapters.asStateFlow()
    
    /**
     * Initialize cache by scanning filesystem.
     */
    suspend fun initialize() {
        val downloaded = mutableSetOf<Long>()
        // Scan all downloaded chapter files
        val novelsDir = provider.downloadsDir
        // TODO: Implement filesystem scan
        _downloadedChapters.value = downloaded
    }
    
    /**
     * Check if chapter is downloaded.
     */
    fun isChapterDownloaded(chapter: NovelChapter): Boolean {
        return chapter.id in _downloadedChapters.value
    }
    
    /**
     * Mark chapter as downloaded.
     */
    fun addChapter(chapterId: Long) {
        _downloadedChapters.value = _downloadedChapters.value + chapterId
    }
    
    /**
     * Remove chapter from cache.
     */
    fun removeChapter(chapterId: Long) {
        _downloadedChapters.value = _downloadedChapters.value - chapterId
    }
}
```

---

### Phase 2: Download Manager & Queue (Week 2)

#### 2.4 Create Novel Downloader
**File:** `app/src/main/java/eu/kanade/tachiyomi/data/download/novel/NovelDownloader.kt`

```kotlin
package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import yokai.data.download.novel.NovelDownloadProvider
import yokai.domain.novel.download.models.NovelDownload
import yokai.domain.novelchapter.models.NovelChapter
import yokai.source.api.NovelProviderTemplate
import java.io.IOException

/**
 * Executes novel chapter downloads.
 * Downloads chapters sequentially from NovelProviderTemplate sources.
 */
class NovelDownloader(
    private val context: Context,
    private val provider: NovelDownloadProvider,
    private val logger: Logger = Logger.withTag("NovelDownloader"),
) {
    private var downloaderJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _queueState = MutableStateFlow<List<NovelDownload>>(emptyList())
    val queueState = _queueState.asStateFlow()
    
    var isRunning = false
        private set
    
    /**
     * Start downloading from the queue.
     */
    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) return false
        
        launchDownloaderJob()
        return true
    }
    
    /**
     * Stop all downloads.
     */
    fun stop(reason: String? = null) {
        downloaderJob?.cancel()
        downloaderJob = null
        isRunning = false
        
        // Mark downloading chapters as queued
        queueState.value
            .filter { it.status.value == NovelDownload.State.DOWNLOADING }
            .forEach { it.setStatus(NovelDownload.State.QUEUE) }
    }
    
    /**
     * Add chapters to download queue.
     */
    fun queueChapters(novel: Novel, chapters: List<NovelChapter>, autoStart: Boolean = true) {
        val newDownloads = chapters.map { chapter ->
            NovelDownload(
                novel = novel,
                chapter = chapter,
                source = novel.source
            )
        }
        
        _queueState.value = _queueState.value + newDownloads
        
        if (autoStart) {
            start()
        }
    }
    
    /**
     * Launch the download job.
     */
    private fun launchDownloaderJob() {
        if (isRunning) return
        isRunning = true
        
        downloaderJob = scope.launch {
            queueState
                .map { queue ->
                    queue.firstOrNull { 
                        it.status.value == NovelDownload.State.QUEUE 
                    }
                }
                .distinctUntilChanged()
                .collectLatest { download ->
                    if (download != null) {
                        downloadChapter(download)
                    } else {
                        stop()
                    }
                }
        }
    }
    
    /**
     * Download a single chapter.
     */
    private suspend fun downloadChapter(download: NovelDownload) {
        withContext(Dispatchers.IO) {
            try {
                download.setStatus(NovelDownload.State.DOWNLOADING)
                
                // Get source and fetch chapter content
                val source = getNovelSource(download.source) ?: throw IOException("Source not found")
                val content = source.getChapterContent(download.chapter.url)
                
                // Save to file
                val file = provider.getChapterFile(source, download.novel, download.chapter)
                    ?: throw IOException("Could not create chapter file")
                
                file.openOutputStream().use { output ->
                    output.write(content.toByteArray(Charsets.UTF_8))
                }
                
                download.setStatus(NovelDownload.State.DOWNLOADED)
                logger.d { "Downloaded chapter: ${download.chapter.title}" }
                
            } catch (e: Exception) {
                logger.e(e) { "Error downloading chapter: ${download.chapter.title}" }
                download.setStatus(NovelDownload.State.ERROR)
            }
        }
    }
    
    /**
     * Remove download from queue.
     */
    fun removeFromQueue(download: NovelDownload) {
        _queueState.value = _queueState.value - download
    }
    
    private fun getNovelSource(sourceId: Long): NovelProviderTemplate? {
        // TODO: Get from source manager
        return null
    }
}
```

#### 2.5 Create Novel Download Manager
**File:** `app/src/main/java/eu/kanade/tachiyomi/data/download/novel/NovelDownloadManager.kt`

```kotlin
package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import yokai.data.download.novel.NovelDownloadCache
import yokai.data.download.novel.NovelDownloadProvider
import yokai.domain.novel.models.Novel
import yokai.domain.novelchapter.models.NovelChapter

/**
 * Manager for novel chapter downloads.
 * Public API for downloading, querying, and managing novel downloads.
 */
class NovelDownloadManager(
    private val context: Context,
    private val provider: NovelDownloadProvider,
    private val cache: NovelDownloadCache,
    private val downloader: NovelDownloader,
) {
    
    val queueState = downloader.queueState
    val isRunning: Boolean get() = downloader.isRunning
    
    /**
     * Download chapters.
     */
    fun downloadChapters(novel: Novel, chapters: List<NovelChapter>, autoStart: Boolean = true) {
        downloader.queueChapters(novel, chapters, autoStart)
    }
    
    /**
     * Check if chapter is downloaded.
     */
    fun isChapterDownloaded(chapter: NovelChapter): Boolean {
        return cache.isChapterDownloaded(chapter)
    }
    
    /**
     * Delete downloaded chapter.
     */
    fun deleteChapter(source: NovelSource, novel: Novel, chapter: NovelChapter) {
        provider.deleteChapter(source, novel, chapter)
        cache.removeChapter(chapter.id)
    }
    
    /**
     * Start downloads.
     */
    fun startDownloads(): Boolean {
        return downloader.start()
    }
    
    /**
     * Stop downloads.
     */
    fun stopDownloads(reason: String? = null) {
        downloader.stop(reason)
    }
    
    /**
     * Clear download queue.
     */
    fun clearQueue() {
        downloader.clearQueue()
    }
}
```

---

### Phase 3: UI Integration (Week 3)

#### 2.6 Update NovelDetailsControllerNew
Add download functionality to existing controller:

```kotlin
// In NovelDetailsControllerNew.kt

/**
 * Download a novel chapter.
 * Replaces placeholder implementation.
 */
fun downloadChapter(position: Int) {
    val chapters = presenter.chapters.value
    if (position < 0 || position >= chapters.size) return
    
    val chapter = chapters[position]
    val novel = presenter.novelValue ?: return
    
    // Check if already downloaded
    if (novelDownloadManager.isChapterDownloaded(chapter)) {
        // Show delete option
        MaterialAlertDialogBuilder(view!!.context)
            .setTitle(R.string.delete_chapter)
            .setMessage(R.string.confirm_delete_chapter)
            .setPositiveButton(R.string.delete) { _, _ ->
                novelDownloadManager.deleteChapter(novel.source, novel, chapter)
                adapter?.notifyItemChanged(position)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    } else {
        // Start download
        novelDownloadManager.downloadChapters(novel, listOf(chapter))
        Snackbar.make(
            view!!,
            getString(R.string.download_started, chapter.title),
            Snackbar.LENGTH_SHORT
        ).show()
    }
}

/**
 * Download multiple chapters.
 */
private fun downloadChapters(chapters: List<NovelChapter>) {
    val novel = presenter.novelValue ?: return
    novelDownloadManager.downloadChapters(novel, chapters)
    
    Snackbar.make(
        view!!,
        getString(R.string.download_started_multiple, chapters.size),
        Snackbar.LENGTH_SHORT
    ).show()
}
```

#### 2.7 Update NovelChapterAdapter
Show download status in chapter items:

```kotlin
// In NovelChapterAdapter.kt - bind() method

fun bind(chapter: NovelChapter) {
    // ... existing code ...
    
    // Update download button state based on download status
    updateDownloadButton(chapter)
}

private fun updateDownloadButton(chapter: NovelChapter) {
    val isDownloaded = novelDownloadManager?.isChapterDownloaded(chapter) ?: false
    val isDownloading = downloadState?.get(chapter.id) == DownloadState.DOWNLOADING
    
    with(binding.downloadButton) {
        when {
            isDownloaded -> {
                // Show checkmark for downloaded
                downloadBorder.isVisible = false
                downloadIcon.isVisible = true
                downloadProgress.isVisible = false
                downloadProgressIndeterminate.isVisible = false
                downloadIcon.setImageResource(R.drawable.ic_check_24dp)
            }
            isDownloading -> {
                // Show progress for downloading
                downloadBorder.isVisible = false
                downloadIcon.isVisible = false
                downloadProgress.isVisible = true
                downloadProgressIndeterminate.isVisible = false
            }
            else -> {
                // Show download icon for not downloaded
                downloadBorder.isVisible = true
                downloadIcon.isVisible = true
                downloadProgress.isVisible = false
                downloadProgressIndeterminate.isVisible = false
                downloadIcon.setImageResource(R.drawable.ic_download_24dp)
            }
        }
    }
}
```

---

### Phase 4: Offline Reading Support (Week 4)

#### 2.8 Update NovelReaderViewModel
Add offline chapter loading:

```kotlin
// In NovelReaderViewModel.kt

/**
 * Load chapter content with download fallback.
 */
suspend fun loadChapterContent(chapterUrl: String): String {
    val chapter = currentChapter ?: throw IllegalStateException("No chapter loaded")
    
    // Try loading from download first
    if (novelDownloadManager.isChapterDownloaded(chapter)) {
        val file = downloadProvider.getChapterFile(source, novel, chapter)
        val content = file?.openInputStream()?.use { it.readBytes().toString(Charsets.UTF_8) }
        if (content != null) {
            return content
        }
    }
    
    // Fallback to online fetch
    return fetchChapterContentOnline(chapterUrl)
}
```

---

### Phase 5: Background Downloads & Notifications (Week 5)

#### 2.9 Create Download Notification Service
**File:** `app/src/main/java/eu/kanade/tachiyomi/data/download/novel/NovelDownloadNotifier.kt`

```kotlin
package eu.kanade.tachiyomi.data.download.novel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import yokai.domain.novel.download.models.NovelDownload

/**
 * Manages notifications for novel downloads.
 */
class NovelDownloadNotifier(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    companion object {
        private const val CHANNEL_ID = "novel_download_channel"
        private const val NOTIFICATION_ID = 1002
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Novel Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Novel chapter download progress"
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    fun onDownloadStarted(download: NovelDownload) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading ${download.novel.title}")
            .setContentText("Chapter: ${download.chapter.title}")
            .setSmallIcon(R.drawable.ic_download_24dp)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    fun onDownloadComplete(download: NovelDownload) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText("${download.novel.title} - ${download.chapter.title}")
            .setSmallIcon(R.drawable.ic_check_24dp)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    fun dismiss() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
```

---

## 3. Dependency Injection Setup

### 3.1 Register Components in Koin

**File:** `app/src/main/java/eu/kanade/tachiyomi/App.kt`

```kotlin
// Add to Koin module
module {
    // Novel Download components
    single { NovelDownloadProvider(androidContext()) }
    single { NovelDownloadCache(androidContext(), get()) }
    single { NovelDownloader(androidContext(), get()) }
    single { NovelDownloadManager(androidContext(), get(), get(), get()) }
    single { NovelDownloadNotifier(androidContext()) }
}
```

---

## 4. Testing Strategy

### 4.1 Unit Tests
- `NovelDownloadProvider` - File system operations
- `NovelDownloadCache` - Cache state management
- `NovelDownloader` - Download logic

### 4.2 Integration Tests
- End-to-end download flow
- Queue management
- Error handling and recovery

### 4.3 Manual Testing Scenarios
1. Download single chapter → Verify file created
2. Download multiple chapters → Verify queue ordering
3. Pause/resume download → Verify state persistence
4. Delete downloaded chapter → Verify file removed
5. Offline reading → Verify content loads from disk
6. Network error during download → Verify error handling

---

## 5. Key Differences from QuickNovel Implementation

| Aspect | QuickNovel | Miko Novel Downloads |
|--------|------------|---------------------|
| **EPUB Generation** | Creates full EPUB after download | Individual text files (no EPUB) |
| **Storage Location** | External storage (Downloads/Epub) | Internal app storage (/novels) |
| **Queue System** | Work Manager based | Coroutine-based like manga |
| **Progress Tracking** | Byte-level progress | Chapter-level progress |
| **Notification** | Detailed with ETA | Simple progress notification |
| **Rate Limiting** | Provider-specific mutex | Source-based throttling |

### Why Not EPUB for Miko?
1. **Consistency** - Manga uses individual page files, novels should use individual chapter files
2. **Flexibility** - Easier to implement partial downloads and resume
3. **Performance** - No EPUB generation overhead on every read
4. **Simplicity** - Matches existing reader architecture

---

## 6. Implementation Timeline

### Week 1: Core Infrastructure
- ✅ NovelDownload model
- ✅ NovelDownloadProvider
- ✅ NovelDownloadCache
- ⏳ Unit tests

### Week 2: Download Manager
- ⏳ NovelDownloader
- ⏳ NovelDownloadManager
- ⏳ Queue management
- ⏳ Integration tests

### Week 3: UI Integration
- ⏳ Update NovelDetailsControllerNew
- ⏳ Update NovelChapterAdapter
- ⏳ Download status indicators
- ⏳ Manual testing

### Week 4: Offline Reading
- ⏳ Update NovelReaderViewModel
- ⏳ Offline content loading
- ⏳ Cache management

### Week 5: Background & Polish
- ⏳ Download notifications
- ⏳ Background download service
- ⏳ Error handling improvements
- ⏳ Performance optimization

---

## 7. Open Questions & Future Enhancements

### Open Questions
1. **EPUB Export** - Should we add EPUB export as optional feature?
2. **Batch Downloads** - Download all unread chapters with one tap?
3. **Auto-Delete** - Delete chapters after reading?
4. **Storage Limits** - Implement storage quota warnings?

### Future Enhancements
1. **Smart Downloads** - Auto-download next chapters based on reading progress
2. **Download Filters** - Download only bookmarked/unread chapters
3. **Backup/Restore** - Include downloads in backup system
4. **Cloud Sync** - Sync download status across devices (far future)

---

## 8. References

### Code Files to Study
- `app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadManager.kt`
- `app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt`
- `QuickNovel/app/src/main/java/com/lagradost/quicknovel/BookDownloader2.kt`
- Existing manga download architecture patterns

### Key Libraries
- **Kotlin Coroutines** - Async download execution
- **Kotlin Flow** - Reactive state management
- **UniFile** - File system abstraction
- **Koin** - Dependency injection

---

## Appendix A: File Structure

```
Miko/
├── domain/
│   └── src/commonMain/kotlin/yokai/domain/
│       └── novel/download/
│           └── models/
│               └── NovelDownload.kt                    [NEW]
├── data/
│   └── src/commonMain/kotlin/yokai/data/
│       └── download/novel/
│           ├── NovelDownloadProvider.kt                [NEW]
│           └── NovelDownloadCache.kt                   [NEW]
└── app/
    └── src/main/java/eu/kanade/tachiyomi/
        ├── data/download/novel/
        │   ├── NovelDownloader.kt                      [NEW]
        │   ├── NovelDownloadManager.kt                 [NEW]
        │   └── NovelDownloadNotifier.kt                [NEW]
        └── ui/novel/details/
            ├── NovelDetailsControllerNew.kt            [MODIFY]
            └── NovelChapterAdapter.kt                  [MODIFY]
```

---

## Appendix B: Database Schema (Future)

If download tracking needs persistence:

```sql
CREATE TABLE novel_download (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chapter_id INTEGER NOT NULL,
    novel_id INTEGER NOT NULL,
    source_id INTEGER NOT NULL,
    status INTEGER NOT NULL DEFAULT 0,
    downloaded_at INTEGER,
    FOREIGN KEY (chapter_id) REFERENCES novel_chapter(id) ON DELETE CASCADE,
    FOREIGN KEY (novel_id) REFERENCES novel(id) ON DELETE CASCADE
);

CREATE INDEX novel_download_chapter_id ON novel_download(chapter_id);
CREATE INDEX novel_download_status ON novel_download(status);
```

---

**Document Version:** 1.0  
**Last Updated:** 2025-10-24  
**Status:** Planning Complete - Ready for Implementation
