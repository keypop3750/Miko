package yokai.domain.novel

import kotlinx.coroutines.flow.Flow
import yokai.domain.novel.models.NovelUpdate

/**
 * Repository interface for novel operations.
 * This is a basic interface that will be expanded in Phase 1.
 */
interface NovelRepository {
    // Basic novel operations
    suspend fun getAllNovels(): Flow<List<Novel>>
    suspend fun getFavoriteNovels(): Flow<List<Novel>>
    suspend fun getNovelById(id: Long): Novel?
    fun getNovelByIdAsFlow(id: Long): Flow<Novel?> // Reactive database access for details page
    suspend fun getNovelByTitle(title: String): Novel?
    suspend fun getNovelByUrlAndSource(url: String, sourceId: Long): Novel?
    fun getNovelByUrlAndSourceAsFlow(url: String, sourceId: Long): Flow<Novel?> // Reactive access by URL
    suspend fun insertNovel(novel: Novel): Long
    suspend fun updateNovel(novel: Novel)
    suspend fun update(update: NovelUpdate): Boolean // Update with partial fields
    suspend fun updateAll(updates: List<NovelUpdate>): Boolean // Batch update
    suspend fun deleteNovel(id: Long)
    
    // Basic chapter operations
    suspend fun getChaptersByNovelId(novelId: Long): Flow<List<NovelChapter>>
    fun getChaptersByNovelIdAsFlow(novelId: Long): Flow<List<NovelChapter>> // Reactive chapter list for details page
    suspend fun getChaptersByNovelIdOnce(novelId: Long): List<NovelChapter> // One-shot DB query (no Flow)
    suspend fun getChapterById(id: Long): NovelChapter?
    suspend fun getChapterByUrl(novelId: Long, url: String): NovelChapter?
    suspend fun insertChapter(chapter: NovelChapter): Long
    suspend fun insertChaptersBulk(chapters: List<NovelChapter>): List<NovelChapter>
    suspend fun updateChapter(chapter: NovelChapter)
    suspend fun markChapterRead(chapterId: Long, isRead: Boolean)
    suspend fun deleteChapter(id: Long)
    
    // Reading progress operations (placeholder for Phase 1)
    suspend fun updateReadingProgress(chapterId: Long, characterPosition: Int)
    suspend fun getReadingPosition(novelId: Long, chapterId: Long): NovelReadingPosition?
    
    // Category operations (placeholder for Phase 1)
    suspend fun getCategoriesForNovel(novelId: Long): Flow<List<NovelCategory>>
    suspend fun getAllCategories(): Flow<List<NovelCategory>>
    suspend fun setCategories(novelId: Long, categoryIds: List<Long>)
}