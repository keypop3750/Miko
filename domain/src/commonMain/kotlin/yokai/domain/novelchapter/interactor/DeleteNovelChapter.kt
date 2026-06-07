package yokai.domain.novelchapter.interactor

import yokai.domain.novelchapter.NovelChapterRepository

class DeleteNovelChapter(
    private val novelChapterRepository: NovelChapterRepository,
) {
    suspend fun await(chapterId: Long): Boolean {
        return novelChapterRepository.delete(chapterId)
    }

    suspend fun awaitAll(chapterIds: List<Long>): Boolean {
        var allSuccess = true
        chapterIds.forEach { chapterId ->
            val success = novelChapterRepository.delete(chapterId)
            if (!success) allSuccess = false
        }
        return allSuccess
    }
}
