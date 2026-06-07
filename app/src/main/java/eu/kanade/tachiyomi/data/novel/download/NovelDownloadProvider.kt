package eu.kanade.tachiyomi.data.novel.download

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy
import yokai.domain.download.DownloadPreferences
import yokai.domain.novel.Novel
import yokai.domain.novel.NovelChapter
import yokai.domain.storage.StorageManager
import yokai.i18n.MR
import yokai.source.novel.NovelMainAPI
import yokai.util.lang.getString

/**
 * Provider for managing novel download file storage.
 * Similar to DownloadProvider but handles text files instead of image directories.
 * 
 * Path scheme: /<root downloads dir>/novels/<source name>/<novel>/<chapter>.txt
 */
class NovelDownloadProvider(private val context: Context) {

    private val downloadPreferences: DownloadPreferences by injectLazy()
    private val storageManager: StorageManager by injectLazy()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The root directory for downloads.
     */
    private var downloadsDir = storageManager.getDownloadsDirectory()

    init {
        storageManager.changes.onEach {
            downloadsDir = storageManager.getDownloadsDirectory()
        }.launchIn(scope)
    }

    /**
     * Returns the download directory for a novel. For internal use only.
     *
     * @param novel the novel to query.
     * @param source the source of the novel.
     */
    internal fun getNovelDir(novel: Novel, source: NovelMainAPI): UniFile {
        try {
            return downloadsDir
                ?.createDirectory("novels")!! // Separate novels from manga
                .createDirectory(getSourceDirName(source))!!
                .createDirectory(getNovelDirName(novel))!!
        } catch (e: NullPointerException) {
            throw Exception(context.getString(MR.strings.invalid_download_location))
        }
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    fun findSourceDir(source: NovelMainAPI): UniFile? {
        return downloadsDir
            ?.findFile("novels")
            ?.findFile(getSourceDirName(source))
    }

    /**
     * Returns the download directory for a novel if it exists.
     *
     * @param novel the novel to query.
     * @param source the source of the novel.
     */
    fun findNovelDir(novel: Novel, source: NovelMainAPI): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getNovelDirName(novel))
    }

    /**
     * Returns the download file for a chapter if it exists.
     * Novels store chapter text as .txt or .html files.
     *
     * @param chapter the chapter to query.
     * @param novel the novel of the chapter.
     * @param source the source of the chapter.
     */
    fun findChapterFile(chapter: NovelChapter, novel: Novel, source: NovelMainAPI): UniFile? {
        val novelDir = findNovelDir(novel, source)
        val chapterFileName = getChapterFileName(chapter)
        
        // Try both .txt and .html extensions
        return novelDir?.findFile("$chapterFileName.txt")
            ?: novelDir?.findFile("$chapterFileName.html")
    }

    /**
     * Returns a list of downloaded chapter files that exist.
     *
     * @param chapters the chapters to query.
     * @param novel the novel of the chapters.
     * @param source the source of the chapters.
     */
    fun findChapterFiles(
        chapters: List<NovelChapter>,
        novel: Novel,
        source: NovelMainAPI
    ): List<UniFile> {
        val novelDir = findNovelDir(novel, source) ?: return emptyList()
        return chapters.mapNotNull { chapter ->
            findChapterFile(chapter, novel, source)
        }
    }

    /**
     * Returns the download file for a chapter.
     *
     * @param chapter the chapter to query.
     * @param novel the novel of the chapter.
     * @param source the source of the chapter.
     */
    fun getChapterFile(chapter: NovelChapter, novel: Novel, source: NovelMainAPI): UniFile {
        val novelDir = getNovelDir(novel, source)
        val chapterFileName = getChapterFileName(chapter)
        return novelDir.createFile("$chapterFileName.html")!!
    }

    /**
     * Returns the source directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: NovelMainAPI): String {
        return DiskUtil.buildValidFilename(source.name)
    }

    /**
     * Returns the source directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: Source): String {
        return DiskUtil.buildValidFilename(source.name)
    }

    /**
     * Returns the novel directory name for a novel.
     *
     * @param novel the novel to query.
     */
    fun getNovelDirName(novel: Novel): String {
        return DiskUtil.buildValidFilename(novel.title)
    }

    /**
     * Returns the chapter file name for a chapter.
     *
     * @param chapter the chapter to query.
     */
    fun getChapterFileName(chapter: NovelChapter): String {
        return DiskUtil.buildValidFilename(chapter.chapterNumber.toString())
    }

    /**
     * Returns valid downloaded chapter file names.
     *
     * @param chapter the chapter to query.
     */
    fun getValidChapterFileNames(chapter: NovelChapter): List<String> {
        val chapterName = DiskUtil.buildValidFilename(chapter.chapterNumber.toString())
        
        return listOf(
            "$chapterName.html",
            "$chapterName.txt"
        )
    }
}
