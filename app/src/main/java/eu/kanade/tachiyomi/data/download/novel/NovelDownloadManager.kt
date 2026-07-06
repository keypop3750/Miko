package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import yokai.domain.novel.Novel
import yokai.domain.storage.StorageManager
import yokai.source.novel.NovelProviderRegistry
import org.json.JSONObject
import java.io.File
import java.net.URL

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
    private val storageManager: StorageManager by injectLazy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadMutex = Mutex()
    private val processMutex = Mutex()
    private val notifier = NovelDownloadNotifier(context)
    
    // Download queue and state
    private val _downloadQueue = MutableStateFlow<List<NovelDownload>>(emptyList())
    val downloadQueue: StateFlow<List<NovelDownload>> = _downloadQueue.asStateFlow()
    
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()
    
    // Active downloads for progress tracking
    private val _activeDownloads = MutableStateFlow<Map<Long, DownloadState>>(emptyMap())
    val activeDownloads: StateFlow<Map<Long, DownloadState>> = _activeDownloads.asStateFlow()

    // Track which novels need EPUB rebuild after downloads complete
    private val _novelsToCompile = mutableMapOf<Long, Novel>()

    companion object {
        private const val TAG = "NovelDownloadManager"
        private const val CHAPTER_FILE_EXTENSION = ".txt"
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 2000L

        // JSON keys for chapter file format
        private const val JSON_KEY_VERSION = "v"
        private const val JSON_KEY_TITLE = "title"
        private const val JSON_KEY_CONTENT = "content"
        private const val FILE_FORMAT_VERSION = 1
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
     * Directory structure for downloaded novels.
     * Uses the user-configured download location from StorageManager.
     */
    private fun getNovelDirectory(novel: Novel): File {
        val downloadsDir = storageManager.getDownloadsDirectory()
        val basePath = downloadsDir?.filePath ?: context.filesDir.absolutePath
        // If filePath is a content URI (SAF), fall back to internal storage for File-based ops
        val safePath = if (basePath.startsWith("content://")) {
            context.filesDir.absolutePath
        } else {
            basePath
        }
        return File(safePath, "novels/${novel.source}/${novel.id}")
    }

    private fun getChapterFile(novel: Novel, chapterId: Long): File {
        return File(getNovelDirectory(novel), "${chapterId}$CHAPTER_FILE_EXTENSION")
    }

    private fun getPosterFileInternal(novel: Novel): File {
        return File(getNovelDirectory(novel), "poster.jpg")
    }

    private fun getEpubFile(novel: Novel): File {
        return File(getNovelDirectory(novel), "local_epub.epub")
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
        val infos = chapters.sortedBy { it.chapterNumber }.map { it.toDownloadInfo() }
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

    /**
     * Move a chapter to the front of the download queue.
     */
    fun startDownloadNow(novel: Novel, chapter: PresenterNovelChapter) {
        scope.launch {
            downloadMutex.withLock {
                val queue = _downloadQueue.value.toMutableList()
                val index = queue.indexOfFirst { it.chapter.id == chapter.id && it.novel.id == novel.id }
                if (index >= 0) {
                    val download = queue.removeAt(index)
                    if (download.state == DownloadState.PENDING || download.state == DownloadState.FAILED) {
                        queue.add(0, download.copy(state = DownloadState.PENDING))
                        _downloadQueue.value = queue
                        Logger.d(TAG) { "Moved chapter to front of queue: ${chapter.title}" }
                    }
                }
            }
            startQueueProcessing()
        }
    }

    /**
     * Cancel a pending or downloading chapter.
     */
    fun cancelDownload(novel: Novel, chapter: PresenterNovelChapter) {
        scope.launch {
            downloadMutex.withLock {
                val queue = _downloadQueue.value.toMutableList()
                val index = queue.indexOfFirst { it.chapter.id == chapter.id && it.novel.id == novel.id }
                if (index >= 0) {
                    val download = queue.removeAt(index)
                    Logger.d(TAG) { "Cancelled download: ${download.chapter.title}" }
                    _downloadQueue.value = queue
                }
            }
            // Clear from active downloads so UI stops spinning
            _activeDownloads.value = _activeDownloads.value - chapter.id
        }
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
        val infos = chapters.sortedBy { it.chapterNumber }.map { it.toDownloadInfo() }
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
            val path = file.absolutePath

            if (!file.exists()) {
                Logger.d(TAG) { "Downloaded file missing for chapter $chapterId at $path" }
                // LEGACY FALLBACK: clear stale active download state if file is gone
                if (_activeDownloads.value[chapterId] == DownloadState.COMPLETED) {
                    _activeDownloads.value = _activeDownloads.value - chapterId
                    Logger.d(TAG) { "Cleared stale COMPLETED state for missing chapter $chapterId" }
                }
                return@withContext null
            }

            if (file.length() == 0L) {
                Logger.w(TAG) { "Downloaded file is 0 bytes for chapter $chapterId at $path" }
                file.delete()
                _activeDownloads.value = _activeDownloads.value - chapterId
                return@withContext null
            }

            try {
                val text = file.readText()

                // LEGACY SUPPORT: detect old format (plain text or title\ncontent) vs new JSON format
                val content = if (text.trimStart().startsWith("{")) {
                    // New JSON format
                    val json = JSONObject(text)
                    json.optString(JSON_KEY_CONTENT, "")
                } else {
                    // Legacy format: title\n<html content> (or plain text)
                    val firstNewline = text.indexOf('\n')
                    if (firstNewline > 0) text.substring(firstNewline + 1) else text
                }

                if (content.isBlank() || content.trimStart().startsWith("Error:")) {
                    Logger.w(TAG) { "Downloaded file has invalid content for chapter $chapterId at $path (total=${text.length})" }
                    // Delete corrupted legacy files so they get re-downloaded
                    file.delete()
                    _activeDownloads.value = _activeDownloads.value - chapterId
                    return@withContext null
                }

                Logger.d(TAG) { "Loaded downloaded content for chapter $chapterId: ${content.length} chars from $path" }
                content
            } catch (e: Exception) {
                Logger.e(TAG) { "Failed to read downloaded chapter $chapterId: ${e.message} at $path" }
                // If we can't parse it (e.g. invalid JSON), delete the file
                file.delete()
                _activeDownloads.value = _activeDownloads.value - chapterId
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

                // Mark novel for EPUB rebuild
                _novelsToCompile[novel.id] = novel

                Logger.d(TAG) { "Queued ${newDownloads.size} chapters for download" }
            }

            // Download cover image in parallel
            downloadPoster(novel)

            // Always start processing if there are pending downloads
            startQueueProcessing()
        }
    }

    private fun startQueueProcessing() {
        scope.launch {
            processMutex.withLock {
                processQueue()
            }
        }
    }

    /**
     * Download the novel cover/poster image for use in the compiled EPUB.
     */
    private fun downloadPoster(novel: Novel) {
        scope.launch(Dispatchers.IO) {
            val posterUrl = novel.posterUrl ?: return@launch
            val posterFile = getPosterFileInternal(novel)
            if (posterFile.exists() && posterFile.length() > 0) return@launch

            try {
                val url = URL(posterUrl)
                url.openStream().use { input ->
                    posterFile.parentFile?.mkdirs()
                    posterFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Logger.d(TAG) { "Downloaded poster for ${novel.title}" }
            } catch (e: Exception) {
                Logger.w(TAG) { "Failed to download poster: ${e.message}" }
            }
        }
    }
    
    private suspend fun downloadChapterInfo(novel: Novel, chapter: ChapterDownloadInfo): Boolean =
        withContext(Dispatchers.IO) {
            updateDownloadState(chapter.id, DownloadState.DOWNLOADING)
            Logger.d(TAG) { "Starting download for chapter: ${chapter.title} (id=${chapter.id}, url=${chapter.url})" }

            try {
                val provider = NovelProviderRegistry.getProvider(novel.source)
                if (provider == null) {
                    Logger.e(TAG) { "Provider not found for source ${novel.source}" }
                    updateDownloadState(chapter.id, DownloadState.FAILED)
                    return@withContext false
                }

                // Retry loop inspired by QuickNovel's BookDownloader2
                var lastError: String? = null
                for (attempt in 0..MAX_RETRY_COUNT) {
                    // Check if download was cancelled (removed from queue)
                    val stillQueued = _downloadQueue.value.any { it.chapter.id == chapter.id }
                    if (!stillQueued && _activeDownloads.value[chapter.id] != DownloadState.DOWNLOADING) {
                        Logger.d(TAG) { "Download cancelled for ${chapter.title}, aborting retries" }
                        return@withContext false
                    }

                    if (attempt > 0) {
                        Logger.d(TAG) { "Retrying download for ${chapter.title} (attempt $attempt/$MAX_RETRY_COUNT)" }
                        delay(RETRY_DELAY_MS)
                    }

                    try {
                        // Fetch content from provider
                        Logger.d(TAG) { "Fetching content from provider for: ${chapter.title}" }
                        val content = provider.getNovelChapterContent(chapter.url)
                        Logger.d(TAG) { "Received content for ${chapter.title}: length=${content.content.length}, title=${content.title}" }

                        // Detect provider error responses (e.g. "Error: no content returned")
                        if (content.content.isBlank() || content.content.trimStart().startsWith("Error:")) {
                            lastError = content.content.takeIf { it.isNotBlank() } ?: "Blank content"
                            Logger.e(TAG) { "Provider returned invalid content for ${chapter.title}: $lastError" }
                            continue // retry
                        }

                        // Save to file as JSON wrapper (prevents newline-in-title corruption)
                        val file = getChapterFile(novel, chapter.id)
                        val tempFile = File(file.parentFile, "${file.name}.tmp")
                        file.parentFile?.mkdirs()
                        val jsonWrapper = JSONObject().apply {
                            put(JSON_KEY_VERSION, FILE_FORMAT_VERSION)
                            put(JSON_KEY_TITLE, chapter.title)
                            put(JSON_KEY_CONTENT, content.content)
                        }
                        tempFile.writeText(jsonWrapper.toString())
                        tempFile.renameTo(file)
                        Logger.d(TAG) { "Saved chapter to disk: ${file.absolutePath}, size=${file.length()} bytes" }

                        // Verify file was written correctly
                        val verifyText = file.readText()
                        val verifyJson = JSONObject(verifyText)
                        val verifyContent = verifyJson.optString(JSON_KEY_CONTENT, "")
                        if (verifyContent.isBlank()) {
                            lastError = "Verification failed: content was corrupted"
                            file.delete()
                            continue // retry
                        }
                        Logger.d(TAG) { "Verified saved content: length=${verifyContent.length}" }

                        Logger.d(TAG) { "Downloaded chapter successfully: ${chapter.title}" }
                        updateDownloadState(chapter.id, DownloadState.COMPLETED)
                        return@withContext true

                    } catch (e: Exception) {
                        lastError = e.message
                        Logger.e(TAG) { "Download attempt $attempt failed for ${chapter.title}: ${e.message}" }
                        if (attempt == MAX_RETRY_COUNT) break
                    }
                }

                Logger.e(TAG) { "All download attempts failed for ${chapter.title}: $lastError" }
                updateDownloadState(chapter.id, DownloadState.FAILED)
                false

            } catch (e: Exception) {
                Logger.e(TAG) { "Failed to download chapter ${chapter.title}: ${e.message}" }
                e.printStackTrace()
                updateDownloadState(chapter.id, DownloadState.FAILED)
                false
            }
        }
    
    private suspend fun deleteChapterById(novel: Novel, chapterId: Long, title: String) = withContext(Dispatchers.IO) {
        try {
            val file = getChapterFile(novel, chapterId)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    invalidateEpub(novel)
                    Logger.d(TAG) { "Deleted chapter: $title" }
                } else {
                    Logger.w(TAG) { "Failed to delete chapter file (delete returned false): $title at ${file.absolutePath}" }
                }
            }
            // Also clear from active downloads so UI updates
            _activeDownloads.value = _activeDownloads.value - chapterId
        } catch (e: Exception) {
            Logger.e(TAG) { "Failed to delete chapter: ${e.message}" }
        }
    }
    
    /**
     * Process the download queue
     */
    private suspend fun processQueue() {
        if (_isDownloading.value) return
        _isDownloading.value = true

        var completedCount = 0
        var failedCount = 0
        val totalCount = _downloadQueue.value.count { it.state == DownloadState.PENDING }

        try {
            while (_downloadQueue.value.any { it.state == DownloadState.PENDING }) {
                val download = downloadMutex.withLock {
                    _downloadQueue.value.firstOrNull { it.state == DownloadState.PENDING }
                } ?: break

                notifier.showProgress(download.novel.title, completedCount, totalCount, download.chapter.title)

                val success = downloadChapterInfo(download.novel, download.chapter)

                if (success) {
                    completedCount++
                } else {
                    failedCount++
                }

                downloadMutex.withLock {
                    val updatedQueue = _downloadQueue.value.toMutableList()
                    val index = updatedQueue.indexOfFirst { it.chapter.id == download.chapter.id }
                    if (index >= 0) {
                        // Always remove from queue (success or failure)
                        // Failed downloads are tracked via _activeDownloads with FAILED state
                        updatedQueue.removeAt(index)
                        _downloadQueue.value = updatedQueue
                    }
                }
            }
        } finally {
            _isDownloading.value = false

            if (completedCount > 0 || failedCount > 0) {
                notifier.showComplete(completedCount, failedCount)
            } else {
                notifier.dismiss()
            }

            // Compile EPUBs for novels that had chapters downloaded
            compileQueuedEpubs()
        }
    }

    /**
     * Compile EPUBs for all novels that had chapters downloaded in this session.
     */
    private suspend fun compileQueuedEpubs() {
        val novelsToCompile = downloadMutex.withLock {
            val snapshot = _novelsToCompile.values.toList()
            _novelsToCompile.clear()
            snapshot
        }

        novelsToCompile.forEach { novel ->
            compileNovelEpub(novel)
        }
    }

    /**
     * Compile all downloaded chapters for a novel into an EPUB.
     */
    private suspend fun compileNovelEpub(novel: Novel) {
        try {
            val novelDir = getNovelDirectory(novel)
            val chapterFiles = novelDir
                .listFiles { f -> f.isFile && f.extension == "txt" && f.name != "poster.jpg" }
                ?.sortedBy { it.nameWithoutExtension.toLongOrNull() ?: 0L }
                ?: emptyList()

            if (chapterFiles.isEmpty()) return

            val posterFile = getPosterFileInternal(novel)
            val epubFile = getEpubFile(novel)
            NovelEpubCompiler.compile(
                context = context,
                novel = novel,
                author = novel.author,
                synopsis = novel.description,
                posterFile = posterFile.takeIf { it.exists() },
                chapterFiles = chapterFiles,
                epubOutputFile = epubFile,
            )
        } catch (e: Exception) {
            Logger.e(TAG) { "EPUB compilation failed: ${e.message}" }
        }
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
     * Get the compiled EPUB file for a novel if it exists and is valid.
     */
    fun getCompiledEpub(novel: Novel): File? {
        val file = getEpubFile(novel)
        return if (file.exists() && file.length() > NovelEpubCompiler.LOCAL_EPUB_MIN_SIZE) file else null
    }

    /**
     * Delete the compiled EPUB for a novel (forces rebuild on next access).
     */
    fun invalidateEpub(novel: Novel) {
        val file = getEpubFile(novel)
        if (file.exists()) {
            file.delete()
            Logger.d(TAG) { "Invalidated EPUB for ${novel.title}" }
        }
    }
    
    /**
     * Get all downloaded chapter IDs for a novel in a single disk scan.
     * Much faster than calling isChapterDownloaded() per chapter.
     */
    fun getDownloadedChapterIds(novel: Novel): Set<Long> {
        val dir = getNovelDirectory(novel)
        return dir
            .listFiles { f -> f.isFile && f.extension == "txt" && f.length() > 0 }
            ?.mapNotNull { it.nameWithoutExtension.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    /**
     * Get download count for a novel
     */
    fun getDownloadCount(novel: Novel): Int {
        return getDownloadedChapterIds(novel).size
    }

    /**
     * Returns true if the chapter is currently queued or actively downloading.
     */
    fun isChapterQueuedOrDownloading(novel: Novel, chapterId: Long): Boolean {
        return _downloadQueue.value.any { it.novel.id == novel.id && it.chapter.id == chapterId && it.state != DownloadState.COMPLETED }
    }
    
    /**
     * Compile all downloaded chapters into an EPUB for offline reading.
     * Can be called manually for chapters downloaded before auto-compilation.
     */
    suspend fun compileEpub(novel: Novel) {
        compileNovelEpub(novel)
    }

    /**
     * Get all downloaded chapter files for a novel, sorted by chapter ID.
     */
    fun getDownloadedChapterFiles(novel: Novel): List<File> {
        val dir = getNovelDirectory(novel)
        return dir
            .listFiles { f -> f.isFile && f.extension == "txt" }
            ?.sortedBy { it.nameWithoutExtension.toLongOrNull() ?: 0L }
            ?: emptyList()
    }

    /**
     * Get the downloaded poster/cover file for a novel.
     */
    fun getPosterFile(novel: Novel): File {
        return getPosterFileInternal(novel)
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
