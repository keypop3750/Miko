package yokai.domain.novelchapter.interactor

import yokai.domain.novelchapter.NovelChapterRepository
import yokai.domain.novelchapter.models.NovelChapterUpdate

class UpdateNovelChapter(
    private val chapterRepository: NovelChapterRepository
) {
    suspend fun await(chapter: NovelChapterUpdate) = 
        chapterRepository.update(chapter)
    
    suspend fun awaitAll(chapters: List<NovelChapterUpdate>) = 
        chapterRepository.updateAll(chapters)
}
