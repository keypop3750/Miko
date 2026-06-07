package eu.kanade.tachiyomi.data.novel.download

import android.content.Context
import co.touchlab.kermit.Logger
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.novel.download.model.NovelDownload
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.launchNow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.domain.download.DownloadPreferences
import yokai.domain.novel.Novel
import yokai.domain.novel.NovelChapter
import yokai.source.novel.NovelMainAPI
import yokai.source.novel.NovelProviderRegistry
import java.io.BufferedOutputStream

/**
 * Novel chapter downloader. Downloads novel chapter text content and saves as HTML files.
 * Similar to manga Downloader but handles text content instead of images.
 */
class NovelDownloader(
    private val context: Context,
    private val provider: NovelDownloadProvider,
    private val cache: NovelDownloadCache,
) {
    private val preferences: PreferencesHelper by injectLazy()
    private val downloadPreferences: DownloadPreferences by injectLazy()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloaderJob: Job? = null

    /**
     * Whether the downloader is running.
     */
    val isRunning: Boolean
        get() = downloaderJob?.isActive ?: false

    /**
     * Whether the downloader is paused
     */
    @Volatile
    var isPaused: Boolean = false

    /**
     * Queue where active downloads are kept.
     */
    private val _queueState = MutableStateFlow<List<NovelDownload>>(emptyList())
    val queueState = _queueState.asStateFlow()

    /**
     * Starts the downloader. Returns true if started, false if already running or empty queue.
     */
    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != NovelDownload.State.DOWNLOADED }
        pending.forEach { 
            if (it.status != NovelDownload.State.QUEUE) {
                it.status = NovelDownload.State.QUEUE 
            }
        }

        isPaused = false
        launchDownloaderJob()

        return pending.isNotEmpty()
    }

    /**
     * Stops the downloader.
     */
    fun stop(reason: String? = null) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == NovelDownload.State.DOWNLOADING }
            .forEach { it.status = NovelDownload.State.ERROR }

        isPaused = false
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == NovelDownload.State.DOWNLOADING }
            .forEach { it.status = NovelDownload.State.QUEUE }
        isPaused = true
    }

    /**
     * Clears the download queue.
     */
    fun clearQueue() {
        cancelDownloaderJob()
        internalClearQueue()
    }

    /**
     * Creates download objects for chapters and adds them to queue.
     */
    fun queueChapters(novel: Novel, chapters: List<NovelChapter>, autoStart: Boolean) = scope.launch {
        if (chapters.isEmpty()) return@launch

        val source = NovelProviderRegistry.getProvider(novel.source)
        if (source == null) {
            Logger.e { "Cannot find source for novel: ${novel.title}" }
            return@launch
        }

        // Filter out chapters that are already downloaded
        val chaptersWithoutFile = async {
            chapters
                .filter { provider.findChapterFile(it, novel, source) == null }
                .sortedByDescending { it.sourceOrder }
        }

        val chaptersToQueue = chaptersWithoutFile.await()
            .filter { chapter -> queueState.value.none { it.chapter.id == chapter.id } }
            .map { NovelDownload(source, novel, it) }

        if (chaptersToQueue.isNotEmpty()) {
            addAllToQueue(chaptersToQueue)

            // Always start if autoStart is true, not just when queue was empty
            if (autoStart && !isRunning) {
                start()
            }
        }
    }

    /**
     * Removes downloads from queue.
     */
    fun removeFromQueue(chapter: NovelChapter) {
        removeFromQueueIf { it.chapter.id == chapter.id }
    }

    fun removeFromQueue(chapters: List<NovelChapter>) {
        removeFromQueueIf { it.chapter.id in chapters.map { it.id } }
    }

    fun removeFromQueue(novel: Novel) {
        removeFromQueueIf { it.novel.id == novel.id }
    }

    private inline fun removeFromQueueIf(predicate: (NovelDownload) -> Boolean) {
        _queueState.update { queue ->
            val downloads = queue.filter { predicate(it) }
            downloads.forEach { download ->
                if (download.status == NovelDownload.State.DOWNLOADING || 
                    download.status == NovelDownload.State.QUEUE) {
                    download.status = NovelDownload.State.NOT_DOWNLOADED
                }
            }
            queue - downloads.toSet()
        }
    }

    /**
     * Updates the queue order.
     */
    fun updateQueue(downloads: List<NovelDownload>) {
        val wasRunning = isRunning

        if (downloads.isEmpty()) {
            clearQueue()
            return
        }

        pause()
        internalClearQueue()
        addAllToQueue(downloads)

        if (wasRunning) {
            start()
        }
    }

    private fun launchDownloaderJob() {
        if (isRunning) return

        downloaderJob = scope.launch {
            try {
                while (true) {
                    val download = queueState.value
                        .firstOrNull { it.status == NovelDownload.State.QUEUE }
                        ?: break

                    downloadChapter(download)

                    // Small delay between downloads to prevent overwhelming the server
                    delay(500)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Logger.e(e) { "Downloader error: ${e.message}" }
                stop()
            }
        }
    }

    private fun cancelDownloaderJob() {
        downloaderJob?.cancel()
        downloaderJob = null
    }

    /**
     * Downloads a novel chapter.
     */
    private suspend fun downloadChapter(download: NovelDownload) {
        val novelDir = provider.getNovelDir(download.novel, download.source)

        val availSpace = DiskUtil.getAvailableStorageSpace(novelDir)
        if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
            download.status = NovelDownload.State.ERROR
            Logger.w { "Not enough disk space to download chapter ${download.chapter.title}" }
            return
        }

        try {
            download.status = NovelDownload.State.DOWNLOADING
            download.progress = 0

            // Fetch chapter text content from provider
            val chapterText = download.source.getChapterContent(download.chapter.url)
            
            if (chapterText.isNullOrBlank()) {
                throw Exception("Chapter content is empty")
            }

            download.textContent = chapterText
            download.progress = 50

            // Save to file
            val chapterFile = provider.getChapterFile(download.chapter, download.novel, download.source)
            
            chapterFile.openOutputStream().use { outputStream ->
                BufferedOutputStream(outputStream).use { bufferedStream ->
                    // Write chapter title as first line
                    val contentWithTitle = "${download.chapter.title}\n$chapterText"
                    bufferedStream.write(contentWithTitle.toByteArray(Charsets.UTF_8))
                }
            }

            download.progress = 100
            download.status = NovelDownload.State.DOWNLOADED

            // Update cache
            cache.addChapter(chapterFile, download.novel)

            Logger.i { "Downloaded novel chapter: ${download.chapter.title}" }

        } catch (e: Exception) {
            Logger.e(e) { "Failed to download chapter ${download.chapter.title}" }
            download.status = NovelDownload.State.ERROR
        }
    }

    private fun addAllToQueue(downloads: List<NovelDownload>) {
        _queueState.update {
            downloads.forEach { download ->
                download.status = NovelDownload.State.QUEUE
            }
            it + downloads
        }
    }

    private fun internalClearQueue() {
        _queueState.update {
            it.forEach { download ->
                if (download.status == NovelDownload.State.DOWNLOADING || 
                    download.status == NovelDownload.State.QUEUE) {
                    download.status = NovelDownload.State.NOT_DOWNLOADED
                }
            }
            emptyList()
        }
    }

    companion object {
        // Minimum required space to start a download: 50 MB (much less than manga)
        const val MIN_DISK_SPACE = 50 * 1024 * 1024
    }
}
