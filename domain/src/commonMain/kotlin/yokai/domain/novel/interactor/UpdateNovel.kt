package yokai.domain.novel.interactor

import yokai.domain.novel.NovelRepository
import yokai.domain.novel.models.NovelUpdate

class UpdateNovel(
    private val novelRepository: NovelRepository,
) {
    suspend fun await(update: NovelUpdate): Boolean = novelRepository.update(update)
    
    suspend fun awaitAll(updates: List<NovelUpdate>): Boolean = novelRepository.updateAll(updates)
}
