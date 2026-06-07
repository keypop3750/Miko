package yokai.domain.novel.interactor

import yokai.domain.novel.Novel
import yokai.domain.novel.NovelRepository

class InsertNovel(
    private val novelRepository: NovelRepository,
) {
    suspend fun await(novel: Novel): Long = novelRepository.insertNovel(novel)
    
    suspend fun awaitAll(novels: List<Novel>): List<Long> = novels.map { novelRepository.insertNovel(it) }
}
