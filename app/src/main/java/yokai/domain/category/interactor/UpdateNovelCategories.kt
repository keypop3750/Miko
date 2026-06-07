package yokai.domain.category.interactor

import yokai.domain.category.NovelCategoryRepository
import yokai.domain.category.models.CategoryUpdate

class UpdateNovelCategories(
    private val novelCategoryRepository: NovelCategoryRepository,
) {
    suspend fun await(updates: List<CategoryUpdate>) = novelCategoryRepository.updateAll(updates)
    suspend fun awaitOne(update: CategoryUpdate) = novelCategoryRepository.update(update)
}
