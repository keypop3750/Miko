package yokai.data.repository

import kotlinx.coroutines.flow.Flow
import yokai.domain.novel.Novel
import yokai.domain.novel.NovelChapter
import yokai.domain.novel.NovelCategory
import yokai.domain.novel.NovelReadingPosition

interface NovelRepository {
    suspend fun getAllNovels(): Flow<List<Novel>>
    
    suspend fun getFavoriteNovels(): Flow<List<Novel>>
    
    suspend fun getNovelById(id: Long): Novel?
    
    suspend fun getNovelByUrlAndSource(url: String, sourceId: Long): Novel?
    
    suspend fun insertNovel(novel: Novel): Long
    
    suspend fun updateNovel(novel: Novel)
    
    suspend fun deleteNovel(id: Long)
    
    suspend fun getChaptersByNovelId(novelId: Long): Flow<List<NovelChapter>>
    
    suspend fun getChapterById(id: Long): NovelChapter?
    
    suspend fun insertChapter(chapter: NovelChapter): Long
    
    suspend fun updateChapter(chapter: NovelChapter)
    
    suspend fun markChapterRead(chapterId: Long, isRead: Boolean)
    
    suspend fun deleteChapter(id: Long)
    
    suspend fun updateReadingProgress(chapterId: Long, characterPosition: Int)
    
    suspend fun getReadingPosition(novelId: Long, chapterId: Long): NovelReadingPosition?
    
    suspend fun getCategoriesForNovel(novelId: Long): Flow<List<NovelCategory>>
    
    suspend fun getAllCategories(): Flow<List<NovelCategory>>
}