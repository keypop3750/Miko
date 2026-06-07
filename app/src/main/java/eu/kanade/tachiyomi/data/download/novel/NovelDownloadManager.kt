package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import yokai.domain.novel.Novel
import yokai.source.novel.NovelProviderRegistry
import java.io.File

// Type aliases for the two different NovelChapter types
typealias PresenterNovelChapter = yokai.domain.novelchapter.models.NovelChapter
typealias DomainNovelChapter = yokai.domain.novel.NovelChapter

/**
 * Simple data class for chapter info needed for downloads.
 * Abstracts away the two different NovelChapter types in the codebase.
 */
data class ChapterDownloadInfo(
    val id: Long,
    val url: String,
    val title: String
)

/**
 * Manager for novel chapter downloads.
 * Downloads chapter content as text files for offline reading.
 * 
 * Based on QuickNovel's BookDownloader2 approach:
 * - Stores chapters as text files with format: title\n<html content>
 * - Uses directory structure: /<sourceId>/<novelId>/<chapterId>.txt
 * - Provides download status tracking
 */
class NovelDownloadManager(private val context: Context) {
    
    private val preferences by injectLazy<PreferencesHelper>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadMutex = Mutex()
    
    // Download queue and state
    private val _downloadQueue = MutableStateFlow<List<NovelDownload>>(emptyList())
    val downloadQueue: StateFlow<List<NovelDownload>> = _downloadQueue.asStateFlow()
    
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()
    
    // Active downloads for progress tracking
    private val _activeDownloads = MutableStateFlow<Map<Long, DownloadState>>(emptyMap())
    val activeDownloads: StateFlow<Map<Long, DownloadState>> = _activeDownloads.asStateFlow()
    
    companion object {
        private const val TAG = "NovelDownloadManager"
        private const val CHAPTER_FILE_EXTENSION = ".txt"
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 2000L
    }
    
    // ========== Extension functions to convert chapter types ==========
    
    private fun PresenterNovelChapter.toDownloadInfo() = ChapterDownloadInfo(
        id = this.id,
        url = this.url,
        title = this.title
    )
    
    private fun DomainNovelChapter.toDownloadInfo() = ChapterDownloadInfo(
        id = this.id,
        url = this.url,
        title = this.title
    )
    
    // ========== Directory and file helpers ==========
    
    /**
     * Directory structure for downloaded novels
     */
    private fun getNovelDirectory(novel: Novel): File {
        return File(context.filesDir, "novels/${novel.source}/${novel.id}")
    }
    
    private fun getChapterFile(novel: Novel, chapterId: Long): File {
        return File(getNovelDirectory(novel), "${chapterId}$CHAPTER_FILE_EXTENSION")
    }
    
    // ========== Public API for PresenterNovelChapter (NovelDetailsPresenter) ==========
    
    /**
     * Check if a chapter is downloaded (for Presenter chapter type)
     */
    fun isChapterDownloaded(novel: Novel, chapter: PresenterNovelChapter): Boolean {
        return isChapterDownloadedById(novel, chapter.id)
    }
    
    /**
     * Get downloaded chapter content (for Presenter chapter type)
     */
    suspend fun getDownloadedContent(novel: Novel, chapter: PresenterNovelChapter): String? {
        return getDownloadedContentById(novel, chapter.id)
    }
    
    /**
     * Queue chapters for download (for Presenter chapter type)
     */
    fun queueChapters(novel: Novel, chapters: List<PresenterNovelChapter>) {
        val infos = chapters.map { it.toDownloadInfo() }
        queueChapterInfos(novel, infos)
    }
    
    /**
     * Download a single chapter immediately (for Presenter chapter type)
     */
    suspend fun downloadChapter(novel: Novel, chapter: PresenterNovelChapter): Boolean {
        return downloadChapterInfo(novel, chapter.toDownloadInfo())
    }
    
    /**
     * Delete downloaded chapter (for Presenter chapter type)
     */
    suspend fun deleteChapter(novel: Novel, chapter: PresenterNovelChapter) {
        deleteChapterById(novel, chapter.id, chapter.title)
    }
    
    // ========== Public API for DomainNovelChapter (NovelReaderViewModel) ==========
    
    /**
     * Check if a chapter is downloaded (for Domain chapter type)
     */
    fun isChapterDownloaded(novel: Novel, chapter: DomainNovelChapter): Boolean {
        return isChapterDownloadedById(novel, chapter.id)
    }
    
    /**
     * Get downloaded chapter content (for Domain chapter type)
     */
    suspend fun getDownloadedContent(novel: Novel, chapter: DomainNovelChapter): String? {
        return getDownloadedContentById(novel, chapter.id)
    }
    
    /**
     * Queue chapters for download (for Domain chapter type)
     */
    fun queueDomainChapters(novel: Novel, chapters: List<DomainNovelChapter>) {
        val infos = chapters.map { it.toDownloadInfo() }
        queueChapterInfos(novel, infos)
    }
    
    /**
     * Download a single chapter immediately (for Domain chapter type)
     */
    suspend fun downloadChapter(novel: Novel, chapter: DomainNovelChapter): Boolean {
        return downloadChapterInfo(novel, chapter.toDownloadInfo())
    }
    
    /**
     * Delete downloaded chapter (for Domain chapter type)
     */
    suspend fun deleteChapter(novel: Novel, chapter: DomainNovelChapter) {
        deleteChapterById(novel, chapter.id, chapter.title)
    }
    
    // ========== Core implementation using IDs ==========
    
    private fun isChapterDownloadedById(novel: Novel, chapterId: Long): Boolean {
        val file = getChapterFile(novel, chapterId)
        return file.exists() && file.length() > 0
    }
    
    private suspend fun getDownloadedContentById(novel: Novel, chapterId: Long): String? = 
        withContext(Dispatchers.IO) {
            val file = getChapterFile(novel, chapterId)
            if (!file.exists()) return@withContext null
            
            try {
                val text = file.readText()
                // Format: title\n<html content>
                val firstNewline = text.indexOf('\n')
                if (firstNewline > 0) {
                    text.substring(firstNewline + 1)
                } else {
                    text
                }
            } catch (e: Exception) {
                Logger.e(TAG) { "Failed to read downloaded chapter: ${e.message}" }
                null
            }
        }
    
    private fun queueChapterInfos(novel: Novel, chapters: List<ChapterDownloadInfo>) {
        scope.launch {
            downloadMutex.withLock {
                val newDownloads = chapters
                    .filter { !isChapterDownloadedById(novel, it.id) }
                    .map { NovelDownload(novel, it, DownloadState.PENDING) }
                
                val currentQueue = _downloadQueue.value.toMutableList()
                currentQueue.addAll(newDownloads)
                _downloadQueue.value = currentQueue
                
                Logger.d(TAG) { "Queued ${newDownloads.size} chapters for download" }
            }
            
            // Start processing if not already running
            if (!_isDownloading.value) {
                processQueue()
            }
        }
    }
    
    private suspend fun downloadChapterInfo(novel: Novel, chapter: ChapterDownloadInfo): Boolean = 
        withContext(Dispatchers.IO) {
            updateDownloadState(chapter.id, DownloadState.DOWNLOADING)
            
            try {
                val provider = NovelProviderRegistry.getProvider(novel.source)
                if (provider == null) {
                    Logger.e(TAG) { "Provider not found for source ${novel.source}" }
                    updateDownloadState(chapter.id, DownloadState.FAILED)
                    return@withContext false
                }
                
                // Fetch content from provider
                val content = provider.getNovelChapterContent(chapter.url)
                
                // Save to file
                val file = getChapterFile(novel, chapter.id)
                file.parentFile?.mkdirs()
                file.writeText("${chapter.title}\n${content.content}")
                
                Logger.d(TAG) { "Downloaded chapter: ${chapter.title}" }
                updateDownloadState(chapter.id, DownloadState.COMPLETED)
                true
                
            } catch (e: Exception) {
                Logger.e(TAG) { "Failed to download chapter ${chapter.title}: ${e.message}" }
                updateDownloadState(chapter.id, DownloadState.FAILED)
                false
            }
        }
    
    private suspend fun deleteChapterById(novel: Novel, chapterId: Long, title: String) = withContext(Dispatchers.IO) {
        try {
            val file = getChapterFile(novel, chapterId)
            if (file.exists()) {
                file.delete()
                Logger.d(TAG) { "Deleted chapter: $title" }
            }
        } catch (e: Exception) {
            Logger.e(TAG) { "Failed to delete chapter: ${e.message}" }
        }
    }
    
    /**
     * Process the download queue
     */
    private suspend fun processQueue() {
        _isDownloading.value = true
        
        while (_downloadQueue.value.isNotEmpty()) {
            val download = downloadMutex.withLock {
                _downloadQueue.value.firstOrNull { it.state == DownloadState.PENDING }
            } ?: break
            
            val success = downloadChapterInfo(download.novel, download.chapter)
            
            downloadMutex.withLock {
                val updatedQueue = _downloadQueue.value.toMutableList()
                val index = updatedQueue.indexOfFirst { it.chapter.id == download.chapter.id }
                if (index >= 0) {
                    if (success) {
                        updatedQueue.removeAt(index)
                    } else {
                        // Mark as failed but keep in queue for retry
                        updatedQueue[index] = download.copy(state = DownloadState.FAILED)
                    }
                    _downloadQueue.value = updatedQueue
                }
            }
        }
        
        _isDownloading.value = false
    }
    
    /**
     * Delete downloaded chapter
     */
    suspend fun deleteChapter(novel: Novel, chapterId: Long, title: String = "unknown") = withContext(Dispatchers.IO) {
        deleteChapterById(novel, chapterId, title)
    }
    
    /**
     * Delete all downloaded chapters for a novel
     */
    suspend fun deleteNovel(novel: Novel) = withContext(Dispatchers.IO) {
        try {
            val dir = getNovelDirectory(novel)
            if (dir.exists()) {
                dir.deleteRecursively()
                Logger.d(TAG) { "Deleted all chapters for novel: ${novel.title}" }
            }
        } catch (e: Exception) {
            Logger.e(TAG) { "Failed to delete novel downloads: ${e.message}" }
        }
    }
    
    /**
     * Get download count for a novel
     */
    fun getDownloadCount(novel: Novel): Int {
        val dir = getNovelDirectory(novel)
        return dir.listFiles()?.count { it.extension == "txt" } ?: 0
    }
    
    /**
     * Cancel pending downloads for a novel
     */
    fun cancelDownloads(novel: Novel) {
        scope.launch {
            downloadMutex.withLock {
                val updatedQueue = _downloadQueue.value.filterNot { it.novel.id == novel.id }
                _downloadQueue.value = updatedQueue
            }
        }
    }
    
    /**
     * Clear failed downloads from queue
     */
    fun clearFailedDownloads() {
        scope.launch {
            downloadMutex.withLock {
                val updatedQueue = _downloadQueue.value.filterNot { it.state == DownloadState.FAILED }
                _downloadQueue.value = updatedQueue
            }
        }
    }
    
    private fun updateDownloadState(chapterId: Long, state: DownloadState) {
        _activeDownloads.value = _activeDownloads.value + (chapterId to state)
    }
}

/**
 * Represents a novel chapter download
 */
data class NovelDownload(
    val novel: Novel,
    val chapter: ChapterDownloadInfo,
    val state: DownloadState
)

/**
 * Download state enum
 */
enum class DownloadState {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}
