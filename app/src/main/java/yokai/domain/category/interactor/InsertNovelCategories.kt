package yokai.domain.category.interactor

import eu.kanade.tachiyomi.data.database.models.Category
import yokai.domain.category.NovelCategoryRepository

class InsertNovelCategories(
    private val novelCategoryRepository: NovelCategoryRepository,
) {
    suspend fun await(categories: List<Category>) = novelCategoryRepository.insertBulk(categories)
    suspend fun awaitOne(category: Category) = novelCategoryRepository.insert(category)
}
