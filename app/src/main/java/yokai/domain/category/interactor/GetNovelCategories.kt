package yokai.domain.category.interactor

import yokai.domain.category.NovelCategoryRepository

/**
 * Interactor for getting novel categories.
 * Uses NovelCategoryRepository which accesses the novel_categories table
 * (separate from manga categories).
 */
class GetNovelCategories(
    private val novelCategoryRepository: NovelCategoryRepository,
) {
    suspend fun await() = novelCategoryRepository.getAll()
    suspend fun awaitByNovelId(novelId: Long?) = novelId?.let { novelCategoryRepository.getAllByNovelId(it) }.orEmpty()
    fun subscribe() = novelCategoryRepository.getAllAsFlow()
}
