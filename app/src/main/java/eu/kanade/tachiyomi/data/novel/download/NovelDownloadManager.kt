package eu.kanade.tachiyomi.data.novel.download

import android.content.Context
import co.touchlab.kermit.Logger
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.novel.download.model.NovelDownload
import eu.kanade.tachiyomi.util.system.launchIO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.novel.Novel
import yokai.domain.novel.NovelChapter
import yokai.source.novel.NovelMainAPI
import yokai.source.novel.NovelProviderRegistry

/**
 * Manager for novel chapter downloads.
 * This class manages the download queue and provides an interface for querying downloaded chapters.
 * 
 * Similar to manga DownloadManager but adapted for novel text content.
 */
class NovelDownloadManager(
    val context: Context,
    private val provider: NovelDownloadProvider = Injekt.get(),
    private val cache: NovelDownloadCache = Injekt.get(),
) {

    /**
     * Downloader whose only task is to download novel chapters.
     */
    private val downloader = NovelDownloader(context, provider, cache)

    val isRunning: Boolean 
        get() = downloader.isRunning

    /**
     * Downloads queue, where the pending chapters are stored.
     */
    val queueState
        get() = downloader.queueState

    /**
     * Tells the downloader to begin downloads.
     *
     * @return true if it's started, false otherwise (empty queue).
     */
    fun startDownloads(): Boolean {
        return downloader.start()
    }

    /**
     * Tells the downloader to stop downloads.
     *
     * @param reason an optional reason for being stopped, used to notify the user.
     */
    fun stopDownloads(reason: String? = null) {
        downloader.stop(reason)
    }

    /**
     * Tells the downloader to pause downloads.
     */
    fun pauseDownloads() {
        downloader.pause()
    }

    /**
     * Empties the download queue.
     */
    fun clearQueue() {
        downloader.clearQueue()
    }

    /**
     * Returns true if the downloader is paused.
     */
    fun isPaused() = !downloader.isRunning

    /**
     * Returns true if the queue has downloads.
     */
    fun hasQueue() = queueState.value.isNotEmpty()

    /**
     * Tells the downloader to enqueue the given list of chapters.
     *
     * @param novel the novel of the chapters.
     * @param chapters the list of chapters to enqueue.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun downloadChapters(novel: Novel, chapters: List<NovelChapter>, autoStart: Boolean = true) {
        Logger.d { "NovelDownloadManager.downloadChapters called: novel=${novel.title}, chapters=${chapters.size}, autoStart=$autoStart" }
        chapters.forEach { chapter ->
            Logger.d { "  - Chapter to download: ${chapter.title} (chapterNumber=${chapter.chapterNumber})" }
        }
        downloader.queueChapters(novel, chapters, autoStart)
        Logger.d { "NovelDownloadManager.downloadChapters: queued chapters, current queue size = ${queueState.value.size}" }
    }

    /**
     * Tells the downloader to enqueue the given list of downloads at the start of the queue.
     *
     * @param downloads the list of downloads to enqueue.
     */
    fun addDownloadsToStartOfQueue(downloads: List<NovelDownload>) {
        if (downloads.isEmpty()) return
        reorderQueue(downloads + queueState.value)
    }

    /**
     * Reorders the download queue.
     *
     * @param downloads value to set the download queue to
     */
    fun reorderQueue(downloads: List<NovelDownload>) {
        downloader.updateQueue(downloads)
    }

    /**
     * Tells the downloader to start the download now.
     *
     * @param chapter the chapter to download now.
     */
    fun startDownloadNow(chapter: NovelChapter) {
        val download = queueState.value.find { it.chapter.id == chapter.id } ?: return
        val queue = queueState.value.toMutableList()
        queue.remove(download)
        queue.add(0, download)
        reorderQueue(queue)
        if (isPaused()) {
            startDownloads()
        }
    }

    /**
     * Returns the download from queue if the chapter is queued for download
     * else it will return null.
     *
     * @param chapter the chapter to check.
     */
    fun getChapterDownloadOrNull(chapter: NovelChapter): NovelDownload? {
        return queueState.value
            .firstOrNull { it.chapter.id == chapter.id && it.chapter.novelId == chapter.novelId }
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapter the chapter to check.
     * @param novel the novel of the chapter.
     * @param skipCache whether to skip the directory cache and check in the filesystem.
     */
    fun isChapterDownloaded(chapter: NovelChapter, novel: Novel, skipCache: Boolean = false): Boolean {
        return cache.isChapterDownloaded(chapter, novel, skipCache)
    }

    /**
     * Returns the amount of downloaded chapters for a novel.
     *
     * @param novel the novel to check.
     */
    fun getDownloadCount(novel: Novel): Int {
        return cache.getDownloadCount(novel)
    }

    /**
     * Calls delete chapter, which deletes a temp download.
     *
     * @param download the download to cancel.
     */
    fun deletePendingDownload(download: NovelDownload) {
        deletePendingDownloads(download)
    }

    /**
     * Deletes the pending downloads for the given list of downloads.
     *
     * @param downloads the list of downloads to delete.
     */
    fun deletePendingDownloads(vararg downloads: NovelDownload) {
        val downloadsByNovel = downloads.groupBy { it.novel.id }
        downloadsByNovel.map { (_, downloads) ->
            val chapters = downloads.map { it.chapter }
            downloader.removeFromQueue(chapters)
        }
    }

    /**
     * Deletes the pending downloads for the given novel.
     *
     * @param novel the novel whose downloads will be deleted.
     */
    fun deletePendingDownloads(novel: Novel) {
        downloader.removeFromQueue(novel)
    }

    /**
     * Deletes a list of chapters from disk.
     * @param chapters the list of chapters to delete.
     * @param novel the novel of the chapters.
     * @param source the source of the chapters.
     */
    fun deleteChapters(
        chapters: List<NovelChapter>,
        novel: Novel,
        source: NovelMainAPI,
    ) {
        launchIO {
            removeFromDownloadQueue(chapters)

            val chapterFiles = provider.findChapterFiles(chapters, novel, source)
            chapterFiles.forEach { it.delete() }
            cache.removeChapters(chapters, novel)

            // Delete novel directory if empty
            if (cache.getDownloadCount(novel) == 0) {
                chapterFiles.firstOrNull()?.parentFile?.delete()
            }
        }
    }

    /**
     * Deletes the directory of a downloaded novel.
     *
     * @param novel the novel to delete.
     * @param source the source of the novel.
     */
    fun deleteNovel(novel: Novel, source: NovelMainAPI, removeQueued: Boolean = true) {
        launchIO {
            if (removeQueued) {
                downloader.removeFromQueue(novel)
            }
            provider.findNovelDir(novel, source)?.delete()
            cache.removeNovel(novel)

            // Delete source directory if empty
            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
                cache.removeSource(source)
            }
        }
    }

    private fun removeFromDownloadQueue(chapters: List<NovelChapter>) {
        val wasRunning = downloader.isRunning
        if (wasRunning) {
            downloader.pause()
        }

        downloader.removeFromQueue(chapters)

        if (wasRunning) {
            if (queueState.value.isEmpty()) {
                downloader.stop()
            } else {
                downloader.start()
            }
        }
    }

    /**
     * Returns status flow for downloads.
     */
    fun statusFlow(): Flow<NovelDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.statusFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> 
                    download.status == NovelDownload.State.DOWNLOADING 
                }.asFlow(),
            )
        }

    /**
     * Returns progress flow for downloads.
     */
    fun progressFlow(): Flow<NovelDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.progressFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> 
                    download.status == NovelDownload.State.DOWNLOADING 
                }.asFlow(),
            )
        }

    /**
     * Refreshes the download cache.
     */
    fun refreshCache() {
        cache.forceRenewCache()
    }
}
