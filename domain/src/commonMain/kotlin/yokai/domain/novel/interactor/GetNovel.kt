package yokai.domain.novel.interactor

import yokai.domain.novel.Novel
import yokai.domain.novel.NovelRepository
import kotlinx.coroutines.flow.Flow

class GetNovel(
    private val novelRepository: NovelRepository,
) {
    suspend fun awaitAll(): Flow<List<Novel>> = novelRepository.getAllNovels()
    
    suspend fun awaitById(id: Long): Novel? = novelRepository.getNovelById(id)
    
    fun subscribeById(id: Long): Flow<Novel?> = novelRepository.getNovelByIdAsFlow(id)
    
    suspend fun awaitByUrlAndSource(url: String, sourceId: Long): Novel? = 
        novelRepository.getNovelByUrlAndSource(url, sourceId)
    
    fun subscribeByUrlAndSource(url: String, sourceId: Long): Flow<Novel?> = 
        novelRepository.getNovelByUrlAndSourceAsFlow(url, sourceId)
    
    suspend fun awaitFavorites(): Flow<List<Novel>> = novelRepository.getFavoriteNovels()
}
