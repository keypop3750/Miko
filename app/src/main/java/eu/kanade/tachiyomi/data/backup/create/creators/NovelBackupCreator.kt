package eu.kanade.tachiyomi.data.backup.create.creators

import android.content.Context
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelCategory
import eu.kanade.tachiyomi.data.backup.models.BackupNovelChapter
import eu.kanade.tachiyomi.data.backup.models.BackupNovelHighlight
import eu.kanade.tachiyomi.ui.novel.reader.NovelHighlightManager
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.data.DatabaseHandler
import yokai.domain.category.NovelCategoryRepository
import yokai.domain.novel.NovelRepository
import java.io.File

class NovelBackupCreator(
    private val context: Context,
    private val novelRepository: NovelRepository = Injekt.get(),
    private val novelCategoryRepository: NovelCategoryRepository = Injekt.get(),
    private val handler: DatabaseHandler = Injekt.get(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val highlightsDir by lazy {
        File(context.getExternalFilesDir(null), "highlights").also { it.mkdirs() }
    }

    suspend operator fun invoke(options: BackupOptions): Pair<List<BackupNovel>, List<BackupNovelCategory>> {
        if (!options.novelEntries) return Pair(emptyList(), emptyList())

        val novels = novelRepository.getAllNovels().first()
        Logger.d { "[BACKUP] Backing up ${novels.size} novels" }

        val allCategories = novelCategoryRepository.getAll()
        val backupCategories = allCategories.map {
            BackupNovelCategory(name = it.name, order = it.order, flags = it.flags)
        }

        val backupNovels = novels.map { novel ->
            backupNovel(novel, allCategories, options)
        }

        return Pair(backupNovels, backupCategories)
    }

    private suspend fun backupNovel(
        novel: yokai.domain.novel.Novel,
        allCategories: List<eu.kanade.tachiyomi.data.database.models.Category>,
        options: BackupOptions,
    ): BackupNovel {
        val backup = BackupNovel(
            source = novel.source,
            url = novel.url,
            title = novel.title,
            author = novel.author,
            description = novel.description,
            genre = novel.genre,
            status = novel.status,
            posterUrl = novel.posterUrl,
            favorite = novel.isFavorite,
            dateAdded = novel.dateAdded,
            chapterFlags = novel.chapterFlags,
            wordCount = novel.wordCount,
            chapterCount = novel.chapterCount,
            vibrantCoverColor = novel.vibrantCoverColor,
            filteredTranslators = novel.filteredTranslators,
            lastUpdate = novel.lastUpdate,
            initialized = novel.initialized,
            coverLastModified = novel.coverLastModified,
        )

        // Chapters
        val chapters = novelRepository.getChaptersByNovelIdOnce(novel.id)
        backup.chapters = chapters.map {
            BackupNovelChapter(
                url = it.url,
                name = it.title,
                read = it.read,
                bookmark = it.bookmark,
                lastReadPosition = it.lastReadPosition,
                dateFetch = it.dateFetch,
                dateUpload = it.dateUpload,
                chapterNumber = it.chapterNumber,
                sourceOrder = it.sourceOrder,
                volumeNumber = it.volumeNumber,
                wordCount = it.wordCount,
                readingTimeMs = it.readingTimeMs,
                translator = it.translator,
            )
        }

        // Categories
        val novelCategories = novelCategoryRepository.getAllByNovelId(novel.id)
        backup.categories = novelCategories.mapNotNull { cat ->
            allCategories.indexOfFirst { it.name == cat.name && it.order == cat.order }
        }.filter { it >= 0 }

        // Highlights (read from JSON files)
        backup.highlights = readHighlights(novel)

        return backup
    }

    private fun readHighlights(novel: yokai.domain.novel.Novel): List<BackupNovelHighlight> {
        val key = NovelHighlightManager.NovelKey(
            title = novel.title,
            author = novel.author,
            description = novel.description,
        )
        val file = File(highlightsDir, "${key.fileName()}.json")
        if (!file.exists()) return emptyList()

        return try {
            val data = json.decodeFromString(
                NovelHighlightManager.NovelHighlightsData.serializer(),
                file.readText(),
            )
            data.chapters.flatMap { ch ->
                ch.highlights.map {
                    BackupNovelHighlight(
                        chapterNumber = ch.chapterNumber,
                        chapterTitle = ch.chapterTitle,
                        text = it.text,
                        color = it.color,
                        note = it.note,
                        timestamp = it.timestamp,
                        paragraphIndex = it.paragraphIndex,
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "[BACKUP] Failed to read highlights for ${novel.title}" }
            emptyList()
        }
    }
}
