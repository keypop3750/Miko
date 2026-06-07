package yokai.data.category

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import kotlinx.coroutines.flow.Flow
import yokai.data.DatabaseHandler
import yokai.domain.category.NovelCategoryRepository
import yokai.domain.category.models.CategoryUpdate

/**
 * Implementation of NovelCategoryRepository that uses SQLDelight
 * to manage novel categories separately from manga categories.
 */
class NovelCategoryRepositoryImpl(private val handler: DatabaseHandler) : NovelCategoryRepository {
    
    override suspend fun getAll(): List<Category> =
        handler.awaitList { novel_categoriesQueries.findAllCategories(::mapCategory) }

    override suspend fun getAllByNovelId(novelId: Long): List<Category> =
        handler.awaitList { novel_categoriesQueries.getCategoriesForNovel(novelId, ::mapCategory) }

    override fun getAllAsFlow(): Flow<List<Category>> =
        handler.subscribeToList { novel_categoriesQueries.findAllCategories(::mapCategory) }

    override suspend fun insert(category: Category): Long? =
        handler.awaitOneOrNullExecutable {
            novel_categoriesQueries.insert(
                name = category.name,
                sortOrder = category.order.toLong(),
                flags = category.flags.toLong(),
            )
            novel_categoriesQueries.selectLastInsertedRowId()
        }

    override suspend fun insertBulk(categories: List<Category>) {
        handler.await(true) {
            categories.forEach { category ->
                novel_categoriesQueries.insert(
                    name = category.name,
                    sortOrder = category.order.toLong(),
                    flags = category.flags.toLong(),
                )
            }
        }
    }

    override suspend fun update(update: CategoryUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            Logger.e { "Failed to update novel category with id '${update.id}'" }
            false
        }
    }

    override suspend fun updateAll(updates: List<CategoryUpdate>): Boolean {
        return try {
            partialUpdate(*updates.toTypedArray())
            true
        } catch (e: Exception) {
            Logger.e(e) { "Failed to bulk update novel categories" }
            false
        }
    }

    private suspend fun partialUpdate(vararg updates: CategoryUpdate) {
        handler.await(inTransaction = true) {
            updates.forEach { update ->
                novel_categoriesQueries.update(
                    id = update.id,
                    name = update.name,
                    sortOrder = update.order,
                    flags = update.flags,
                )
            }
        }
    }

    override suspend fun delete(id: Long) {
        handler.await { novel_categoriesQueries.delete(id) }
    }

    override suspend fun setNovelCategories(novelId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            // Clear existing assignments
            novel_categoriesQueries.deleteNovelCategories(novelId)
            // Add new assignments
            categoryIds.forEach { categoryId ->
                novel_categoriesQueries.insertNovelCategory(novelId, categoryId)
            }
        }
    }

    /**
     * Map database row to Category model.
     * Novel categories use the same Category interface but are stored separately.
     */
    private fun mapCategory(
        id: Long,
        name: String,
        sortOrder: Long,
        flags: Long,
    ): Category {
        return CategoryImpl().apply {
            this.id = id.toInt()
            this.name = name
            this.order = sortOrder.toInt()
            this.flags = flags.toInt()
        }
    }
}
