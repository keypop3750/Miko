package yokai.domain.recents.interactor

import eu.kanade.tachiyomi.data.database.models.MangaChapter
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import yokai.core.content.ContentType
import yokai.domain.chapter.ChapterRepository
import yokai.domain.history.HistoryRepository
import yokai.util.limitAndOffset

class GetRecents(
    private val chapterRepository: ChapterRepository,
    private val historyRepository: HistoryRepository,
) {
    suspend fun awaitUpdates(
        filterScanlators: Boolean,
        isResuming: Boolean,
        search: String = "",
        offset: Long = 0L,
        contentType: ContentType = ContentType.MANGA,
    ): List<MangaChapter> {
        val (limit, actualOffset) = limitAndOffset(true, isResuming, offset)
        val contentTypeValue = contentType.value.toLong()

        return chapterRepository.getRecents(filterScanlators, search, limit, actualOffset, contentTypeValue)
    }

    suspend fun awaitUngrouped(
        filterScanlators: Boolean,
        isResuming: Boolean,
        search: String = "",
        offset: Long = 0L,
        contentType: ContentType = ContentType.MANGA,
    ): List<MangaChapterHistory> {
        val (limit, actualOffset) = limitAndOffset(true, isResuming, offset)
        val contentTypeValue = contentType.value.toLong()

        return historyRepository.getRecentsUngrouped(filterScanlators, search, limit, actualOffset, contentTypeValue)
    }

    suspend fun awaitBySeries(
        filterScanlators: Boolean,
        isResuming: Boolean,
        search: String = "",
        offset: Long = 0L,
        contentType: ContentType = ContentType.MANGA,
    ): List<MangaChapterHistory> {
        val (limit, actualOffset) = limitAndOffset(true, isResuming, offset)
        val contentTypeValue = contentType.value.toLong()

        return historyRepository.getRecentsBySeries(filterScanlators, search, limit, actualOffset, contentTypeValue)
    }

    suspend fun awaitAll(
        includeRead: Boolean,
        filterScanlators: Boolean,
        isEndless: Boolean,
        isResuming: Boolean,
        search: String = "",
        offset: Long = 0L,
        contentType: ContentType = ContentType.MANGA,
    ): List<MangaChapterHistory> {
        val (limit, actualOffset) = limitAndOffset(isEndless, isResuming, offset)
        val contentTypeValue = contentType.value.toLong()

        return historyRepository.getRecentsAll(includeRead, filterScanlators, search, limit, actualOffset, contentTypeValue)
    }

    suspend fun awaitUpdates(limit: Long = 0L, contentType: ContentType = ContentType.MANGA): List<MangaChapterHistory> {
        val contentTypeValue = contentType.value.toLong()
        
        return historyRepository.getRecentsAll(
            includeRead = false,
            filterScanlators = true,
            search = "",
            limit = limit,
            offset = 0L,
            contentType = contentTypeValue
        )
    }
}
