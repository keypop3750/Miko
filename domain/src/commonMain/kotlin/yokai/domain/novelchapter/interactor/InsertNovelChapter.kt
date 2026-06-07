package yokai.domain.novelchapter.interactor

import yokai.domain.novel.NovelChapter
import yokai.domain.novel.NovelRepository

class InsertNovelChapter(
    private val novelRepository: NovelRepository,
) {
    suspend fun await(chapter: NovelChapter) = novelRepository.insertChapter(chapter)

    suspend fun awaitBulk(chapters: List<NovelChapter>) = novelRepository.insertChaptersBulk(chapters)
}
