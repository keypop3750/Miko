package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelCategory
import eu.kanade.tachiyomi.data.backup.models.BackupNovelHighlight
import eu.kanade.tachiyomi.ui.novel.reader.NovelHighlightManager
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.category.NovelCategoryRepository
import yokai.domain.novel.Novel
import yokai.domain.novel.NovelChapter
import yokai.domain.novel.NovelRepository
import java.io.File

class NovelBackupRestorer(
    private val context: Context,
    private val novelRepository: NovelRepository = Injekt.get(),
    private val novelCategoryRepository: NovelCategoryRepository = Injekt.get(),
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val highlightsDir by lazy {
        File(context.getExternalFilesDir(null), "highlights").also { it.mkdirs() }
    }

    suspend fun restoreNovels(
        backupNovels: List<BackupNovel>,
        backupCategories: List<BackupNovelCategory>,
        onComplete: (Novel) -> Unit = {},
        onError: (Novel, Throwable) -> Unit = { _, _ -> },
    ) {
        backupNovels.forEach { backupNovel ->
            try {
                val novel = restoreNovel(backupNovel, backupCategories)
                onComplete(novel)
            } catch (e: Exception) {
                Logger.e(e) { "[RESTORE] Failed to restore novel: ${backupNovel.title}" }
                val dummy = Novel(source = backupNovel.source, url = backupNovel.url, title = backupNovel.title)
                onError(dummy, e)
            }
        }
    }

    private suspend fun restoreNovel(
        backupNovel: BackupNovel,
        backupCategories: List<BackupNovelCategory>,
    ): Novel {
        val existingNovel = novelRepository.getNovelByUrlAndSource(backupNovel.url, backupNovel.source)

        val novelId = if (existingNovel == null) {
            // Insert new novel
            val novel = Novel(
                source = backupNovel.source,
                url = backupNovel.url,
                title = backupNovel.title,
                author = backupNovel.author,
                description = backupNovel.description,
                genre = backupNovel.genre,
                status = backupNovel.status,
                posterUrl = backupNovel.posterUrl,
                isFavorite = backupNovel.favorite,
                dateAdded = backupNovel.dateAdded,
                chapterFlags = backupNovel.chapterFlags,
                wordCount = backupNovel.wordCount,
                chapterCount = backupNovel.chapterCount,
                vibrantCoverColor = backupNovel.vibrantCoverColor,
                filteredTranslators = backupNovel.filteredTranslators,
                lastUpdate = backupNovel.lastUpdate,
                initialized = backupNovel.initialized,
                coverLastModified = backupNovel.coverLastModified,
            )
            novelRepository.insertNovel(novel)
        } else {
            // Update existing novel
            val updated = existingNovel.copy(
                title = backupNovel.title,
                author = backupNovel.author,
                description = backupNovel.description,
                genre = backupNovel.genre,
                status = backupNovel.status,
                posterUrl = backupNovel.posterUrl,
                isFavorite = backupNovel.favorite,
                chapterFlags = backupNovel.chapterFlags,
                wordCount = backupNovel.wordCount,
                chapterCount = backupNovel.chapterCount,
                vibrantCoverColor = backupNovel.vibrantCoverColor,
                filteredTranslators = backupNovel.filteredTranslators,
                lastUpdate = backupNovel.lastUpdate,
                initialized = backupNovel.initialized,
                coverLastModified = backupNovel.coverLastModified,
            )
            novelRepository.updateNovel(updated)
            existingNovel.id
        }

        val novel = novelRepository.getNovelById(novelId)
            ?: throw IllegalStateException("Failed to retrieve novel after insert/update")

        // Restore chapters
        if (backupNovel.chapters.isNotEmpty()) {
            restoreChapters(novelId, backupNovel.chapters)
        }

        // Restore categories
        if (backupNovel.categories.isNotEmpty()) {
            restoreCategories(novelId, backupNovel.categories, backupCategories)
        }

        // Restore highlights
        if (backupNovel.highlights.isNotEmpty()) {
            restoreHighlights(novel, backupNovel.highlights)
        }

        return novel
    }

    private suspend fun restoreChapters(novelId: Long, chapters: List<eu.kanade.tachiyomi.data.backup.models.BackupNovelChapter>) {
        val dbChapters = novelRepository.getChaptersByNovelIdOnce(novelId)
        val dbChapterUrls = dbChapters.associateBy { it.url }

        val chaptersToInsert = mutableListOf<NovelChapter>()
        val chaptersToUpdate = mutableListOf<NovelChapter>()

        chapters.forEach { backupChapter ->
            val dbChapter = dbChapterUrls[backupChapter.url]
            val chapter = NovelChapter(
                id = dbChapter?.id ?: 0,
                novelId = novelId,
                url = backupChapter.url,
                title = backupChapter.name,
                chapterNumber = backupChapter.chapterNumber,
                volumeNumber = backupChapter.volumeNumber,
                wordCount = backupChapter.wordCount,
                read = backupChapter.read,
                bookmark = backupChapter.bookmark,
                sourceOrder = backupChapter.sourceOrder,
                dateFetch = backupChapter.dateFetch,
                dateUpload = backupChapter.dateUpload,
                lastReadPosition = backupChapter.lastReadPosition,
                readingTimeMs = backupChapter.readingTimeMs,
                translator = backupChapter.translator,
            )

            if (dbChapter != null) {
                // Merge with existing: keep whichever has more progress
                if (!dbChapter.read && chapter.read) {
                    chaptersToUpdate.add(chapter.copy(id = dbChapter.id))
                } else if (chapter.lastReadPosition > dbChapter.lastReadPosition) {
                    chaptersToUpdate.add(chapter.copy(id = dbChapter.id))
                }
            } else {
                chaptersToInsert.add(chapter)
            }
        }

        if (chaptersToInsert.isNotEmpty()) {
            novelRepository.insertChaptersBulk(chaptersToInsert)
        }
        chaptersToUpdate.forEach {
            novelRepository.updateChapter(it)
        }
    }

    private suspend fun restoreCategories(
        novelId: Long,
        categoryIndices: List<Int>,
        backupCategories: List<BackupNovelCategory>,
    ) {
        val dbCategories = novelCategoryRepository.getAll()
        val categoryIdsToAssign = mutableListOf<Long>()

        categoryIndices.forEach { index ->
            val backupCategory = backupCategories.getOrNull(index) ?: return@forEach
            val dbCategory = dbCategories.find { it.name == backupCategory.name }
            if (dbCategory != null) {
                dbCategory.id?.let { categoryIdsToAssign.add(it.toLong()) }
            } else {
                // Create new category
                val newCategory = eu.kanade.tachiyomi.data.database.models.CategoryImpl().apply {
                    name = backupCategory.name
                    order = backupCategory.order
                    flags = backupCategory.flags
                }
                val newId = novelCategoryRepository.insert(newCategory)
                newId?.let { categoryIdsToAssign.add(it) }
            }
        }

        if (categoryIdsToAssign.isNotEmpty()) {
            novelCategoryRepository.setNovelCategories(novelId, categoryIdsToAssign)
        }
    }

    private fun restoreHighlights(novel: Novel, highlights: List<BackupNovelHighlight>) {
        val key = NovelHighlightManager.NovelKey(
            title = novel.title,
            author = novel.author,
            description = novel.description,
        )

        // Group highlights by chapter
        val chapters = highlights.groupBy { it.chapterNumber }
            .map { (chapterNumber, hlList) ->
                NovelHighlightManager.ChapterHighlights(
                    chapterNumber = chapterNumber,
                    chapterTitle = hlList.firstOrNull()?.chapterTitle ?: "",
                    highlights = hlList.map {
                        NovelHighlightManager.HighlightEntry(
                            text = it.text,
                            paragraphIndex = it.paragraphIndex,
                            timestamp = it.timestamp,
                            color = it.color,
                            note = it.note,
                        )
                    },
                )
            }

        val data = NovelHighlightManager.NovelHighlightsData(
            novelTitle = novel.title,
            author = novel.author,
            description = novel.description,
            chapters = chapters,
        )

        val file = File(highlightsDir, "${key.fileName()}.json")
        try {
            file.writeText(json.encodeToString(NovelHighlightManager.NovelHighlightsData.serializer(), data))
        } catch (e: Exception) {
            Logger.e(e) { "[RESTORE] Failed to write highlights for ${novel.title}" }
        }
    }
}
