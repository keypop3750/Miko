package eu.kanade.tachiyomi.data.novel.download

import android.content.Context
import co.touchlab.kermit.Logger
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.system.launchIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.novel.Novel
import yokai.domain.novel.NovelChapter
import yokai.domain.storage.StorageManager
import yokai.source.novel.NovelMainAPI
import yokai.source.novel.NovelProviderRegistry
import java.util.concurrent.TimeUnit

/**
 * Cache for tracking downloaded novel chapters.
 * Similar to DownloadCache but optimized for text files instead of image directories.
 * 
 * Novel chapters are stored as .html or .txt files, so we check file existence
 * rather than directory structure.
 */
class NovelDownloadCache(
    private val context: Context,
    private val provider: NovelDownloadProvider = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
) {

    val scope = CoroutineScope(Dispatchers.IO)

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .onStart { emit(Unit) }
        .shareIn(scope, SharingStarted.Lazily, 1)

    /**
     * Cache invalidation interval (1 hour)
     */
    private val renewInterval = TimeUnit.HOURS.toMillis(1)

    /**
     * The last time the cache was refreshed.
     */
    private var lastRenew = 0L
    private var renewalJob: Job? = null

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing = _isInitializing
        .debounce(1000L)
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    private val rootDownloadsDirLock = Mutex()
    
    /**
     * In-memory cache: sourceId -> novelTitle -> Set<chapterFileNames>
     */
    private var downloadCache = mutableMapOf<Long, MutableMap<String, MutableSet<String>>>()

    init {
        storageManager.changes
            .onEach { forceRenewCache() }
            .launchIn(scope)
    }

    /**
     * Returns true if the chapter is downloaded.
     *
     * @param chapter the chapter to check.
     * @param novel the novel of the chapter.
     * @param skipCache whether to skip the cache and check filesystem.
     */
    fun isChapterDownloaded(chapter: NovelChapter, novel: Novel, skipCache: Boolean): Boolean {
        if (skipCache) {
            val source = NovelProviderRegistry.getProvider(novel.source) ?: return false
            return provider.findChapterFile(chapter, novel, source) != null
        }

        renewCache()

        val sourceCache = downloadCache[novel.source] ?: return false
        val novelDirName = provider.getNovelDirName(novel)
        val novelCache = sourceCache[novelDirName] ?: return false
        val validFileNames = provider.getValidChapterFileNames(chapter)
        
        return validFileNames.any { it in novelCache }
    }

    /**
     * Returns the amount of downloaded chapters for a novel.
     *
     * @param novel the novel to check.
     */
    fun getDownloadCount(novel: Novel): Int {
        renewCache()

        val sourceCache = downloadCache[novel.source] ?: return 0
        val novelCache = sourceCache[provider.getNovelDirName(novel)] ?: return 0
        
        return novelCache.size
    }

    /**
     * Adds a chapter to the download cache.
     *
     * @param chapterFile the chapter file.
     * @param novel the novel of the chapter.
     */
    fun addChapter(chapterFile: UniFile, novel: Novel) {
        scope.launch {
            rootDownloadsDirLock.withLock {
                val sourceCache = downloadCache.getOrPut(novel.source) { mutableMapOf() }
                val novelDirName = provider.getNovelDirName(novel)
                val novelCache = sourceCache.getOrPut(novelDirName) { mutableSetOf() }
                
                chapterFile.name?.let { fileName ->
                    novelCache.add(fileName)
                }
            }
            notifyChanges()
        }
    }

    /**
     * Removes a chapter from the download cache.
     *
     * @param chapter the chapter to remove.
     * @param novel the novel of the chapter.
     */
    fun removeChapter(chapter: NovelChapter, novel: Novel) {
        scope.launch {
            rootDownloadsDirLock.withLock {
                val sourceCache = downloadCache[novel.source] ?: return@withLock
                val novelCache = sourceCache[provider.getNovelDirName(novel)] ?: return@withLock
                
                provider.getValidChapterFileNames(chapter).forEach { fileName ->
                    novelCache.remove(fileName)
                }
            }
            notifyChanges()
        }
    }

    /**
     * Removes multiple chapters from the download cache.
     *
     * @param chapters the chapters to remove.
     * @param novel the novel of the chapters.
     */
    fun removeChapters(chapters: List<NovelChapter>, novel: Novel) {
        scope.launch {
            rootDownloadsDirLock.withLock {
                val sourceCache = downloadCache[novel.source] ?: return@withLock
                val novelCache = sourceCache[provider.getNovelDirName(novel)] ?: return@withLock
                
                chapters.forEach { chapter ->
                    provider.getValidChapterFileNames(chapter).forEach { fileName ->
                        novelCache.remove(fileName)
                    }
                }
            }
            notifyChanges()
        }
    }

    /**
     * Removes a novel from the download cache.
     *
     * @param novel the novel to remove.
     */
    fun removeNovel(novel: Novel) {
        scope.launch {
            rootDownloadsDirLock.withLock {
                val sourceCache = downloadCache[novel.source] ?: return@withLock
                sourceCache.remove(provider.getNovelDirName(novel))
            }
            notifyChanges()
        }
    }

    /**
     * Removes a source from the download cache.
     *
     * @param source the source to remove.
     */
    fun removeSource(source: NovelMainAPI) {
        scope.launch {
            rootDownloadsDirLock.withLock {
                downloadCache.remove(source.id)
            }
            notifyChanges()
        }
    }

    /**
     * Triggers a cache refresh if needed.
     */
    private fun renewCache() {
        if (lastRenew + renewInterval < System.currentTimeMillis()) {
            if (renewalJob?.isActive != true) {
                renewalJob = scope.launchIO {
                    delay(1000)
                    _isInitializing.emit(true)
                    var sources = 0
                    var novels = 0
                    var chapters = 0

                    try {
                        rootDownloadsDirLock.withLock {
                            val novelsDir = storageManager.getDownloadsDirectory()
                                ?.findFile("novels")
                            
                            val newCache = mutableMapOf<Long, MutableMap<String, MutableSet<String>>>()
                            
                            novelsDir?.listFiles()?.forEach { sourceDir ->
                                if (!sourceDir.isDirectory) return@forEach
                                
                                // Find source by directory name
                                val sourceName = sourceDir.name ?: return@forEach
                                val source = NovelProviderRegistry.getAllProviders()
                                    .find { provider.getSourceDirName(it) == sourceName }
                                    ?: return@forEach
                                
                                sources++
                                val sourceCache = mutableMapOf<String, MutableSet<String>>()
                                
                                sourceDir.listFiles()?.forEach { novelDir ->
                                    if (!novelDir.isDirectory) return@forEach
                                    
                                    novels++
                                    val novelDirName = novelDir.name ?: return@forEach
                                    val chapterFiles = mutableSetOf<String>()
                                    
                                    novelDir.listFiles()?.forEach { chapterFile ->
                                        val fileName = chapterFile.name ?: return@forEach
                                        if (chapterFile.isFile && 
                                            (fileName.endsWith(".html") || fileName.endsWith(".txt"))) {
                                            chapterFiles.add(fileName)
                                            chapters++
                                        }
                                    }
                                    
                                    sourceCache[novelDirName] = chapterFiles
                                }
                                
                                newCache[source.id] = sourceCache
                            }
                            
                            downloadCache = newCache
                            lastRenew = System.currentTimeMillis()
                        }
                        
                        Logger.i { "Novel download cache initialized: $sources sources, $novels novels, $chapters chapters" }
                    } catch (e: Exception) {
                        Logger.e(e) { "Failed to refresh novel download cache" }
                    } finally {
                        _isInitializing.emit(false)
                    }
                }
            }
        }
    }

    /**
     * Forces a cache refresh.
     */
    fun forceRenewCache() {
        lastRenew = 0L
        renewCache()
    }

    private fun notifyChanges() {
        _changes.trySend(Unit)
    }
}
