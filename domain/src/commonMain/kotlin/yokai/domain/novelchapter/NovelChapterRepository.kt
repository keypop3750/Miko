package yokai.domain.novelchapter

import kotlinx.coroutines.flow.Flow
import yokai.domain.novelchapter.models.NovelChapter
import yokai.domain.novelchapter.models.NovelChapterUpdate

interface NovelChapterRepository {
    // Basic chapter queries
    suspend fun getChapters(novelId: Long): List<NovelChapter>
    fun getChaptersAsFlow(novelId: Long): Flow<List<NovelChapter>>
    
    suspend fun getChapterById(id: Long): NovelChapter?
    suspend fun getChapterByUrl(url: String, novelId: Long): NovelChapter?
    
    // Read status queries
    suspend fun getUnreadChapters(novelId: Long): List<NovelChapter>
    suspend fun markChapterRead(chapterId: Long, read: Boolean)
    
    // Library metadata queries (NEW - for TODO items)
    suspend fun getUnreadCount(novelId: Long): Long
    suspend fun getTotalCount(novelId: Long): Long
    suspend fun getDownloadedCount(novelId: Long): Long
    suspend fun getLastReadChapterTitle(novelId: Long): String?
    
    // CRUD operations
    suspend fun insert(chapter: NovelChapter): Long?
    suspend fun update(update: NovelChapterUpdate): Boolean
    suspend fun updateAll(updates: List<NovelChapterUpdate>): Boolean
    suspend fun delete(chapterId: Long): Boolean
}
