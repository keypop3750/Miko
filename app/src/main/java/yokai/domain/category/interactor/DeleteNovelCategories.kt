package yokai.domain.category.interactor

import yokai.domain.category.NovelCategoryRepository

class DeleteNovelCategories(
    private val novelCategoryRepository: NovelCategoryRepository,
) {
    suspend fun awaitOne(id: Long) = novelCategoryRepository.delete(id)
}
