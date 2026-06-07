package yokai.domain.category.interactor

import yokai.domain.novel.NovelRepository

/**
 * Interactor for setting novel categories.
 * Uses NovelRepository which manages the novels_categories join table.
 */
class SetNovelCategories(
    private val novelRepository: NovelRepository,
) {
    suspend fun await(novelId: Long?, categories: List<Long>) {
        novelRepository.setCategories(novelId ?: return, categories)
    }
}
