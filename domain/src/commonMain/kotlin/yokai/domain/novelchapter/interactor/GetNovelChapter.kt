package yokai.domain.novelchapter.interactor

import kotlinx.coroutines.flow.Flow
import yokai.domain.novelchapter.NovelChapterRepository
import yokai.domain.novelchapter.models.NovelChapter

class GetNovelChapter(
    private val chapterRepository: NovelChapterRepository
) {
    suspend fun awaitAll(novelId: Long): List<NovelChapter> = 
        chapterRepository.getChapters(novelId)
    
    fun subscribeByNovelId(novelId: Long): Flow<List<NovelChapter>> = 
        chapterRepository.getChaptersAsFlow(novelId)
    
    suspend fun awaitUnread(novelId: Long): List<NovelChapter> = 
        chapterRepository.getUnreadChapters(novelId)
    
    suspend fun awaitById(id: Long): NovelChapter? = 
        chapterRepository.getChapterById(id)
    
    // Library metadata helpers
    suspend fun getUnreadCount(novelId: Long) = 
        chapterRepository.getUnreadCount(novelId)
    
    suspend fun getTotalCount(novelId: Long) = 
        chapterRepository.getTotalCount(novelId)
    
    suspend fun getDownloadedCount(novelId: Long) = 
        chapterRepository.getDownloadedCount(novelId)
    
    suspend fun getLastReadChapterTitle(novelId: Long) = 
        chapterRepository.getLastReadChapterTitle(novelId)
}
