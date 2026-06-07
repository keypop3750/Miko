package eu.kanade.tachiyomi.ui.category

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.ui.library.LibrarySort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import yokai.core.category.CategoryScope
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager
import yokai.domain.category.interactor.DeleteCategories
import yokai.domain.category.interactor.DeleteNovelCategories
import yokai.domain.category.interactor.GetCategories
import yokai.domain.category.interactor.GetNovelCategories
import yokai.domain.category.interactor.InsertCategories
import yokai.domain.category.interactor.InsertNovelCategories
import yokai.domain.category.interactor.UpdateCategories
import yokai.domain.category.interactor.UpdateNovelCategories
import yokai.domain.category.models.CategoryUpdate
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Presenter of [CategoryController]. Used to manage the categories of the library.
 * Mode-aware: manages manga categories in MANGA mode and novel categories in NOVEL mode.
 */
class CategoryPresenter(
    private val controller: CategoryController,
) {
    // Manga category interactors
    private val deleteCategories: DeleteCategories by injectLazy()
    private val getCategories: GetCategories by injectLazy()
    private val insertCategories: InsertCategories by injectLazy()
    private val updateCategories: UpdateCategories by injectLazy()
    
    // Novel category interactors
    private val deleteNovelCategories: DeleteNovelCategories by injectLazy()
    private val getNovelCategories: GetNovelCategories by injectLazy()
    private val insertNovelCategories: InsertNovelCategories by injectLazy()
    private val updateNovelCategories: UpdateNovelCategories by injectLazy()

    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    /**
     * List containing categories.
     */
    private var categories: MutableList<Category> = mutableListOf()
    
    /**
     * Current content mode (determines whether we manage manga or novel categories)
     */
    private val currentMode: ContentType
        get() = ModeManager.currentMode.value

    /**
     * Called when the presenter is created.
     */
    fun getCategories() {
        if (categories.isNotEmpty()) {
            controller.setCategories(categories.map(::CategoryItem))
        }
        scope.launch(Dispatchers.IO) {
            categories.clear()
            categories.add(newCategory())
            categories.addAll(fetchCategoriesForCurrentMode())
            val catItems = categories.map(::CategoryItem)
            withContext(Dispatchers.Main) {
                controller.setCategories(catItems)
            }
        }
    }
    
    /**
     * Fetch categories based on current mode
     */
    private suspend fun fetchCategoriesForCurrentMode(): List<Category> {
        return when (currentMode) {
            ContentType.MANGA -> getCategories.await()
            ContentType.NOVEL -> getNovelCategories.await()
        }
    }

    private fun newCategory(): Category {
        val default =
            Category.create(controller.view?.context?.getString(MR.strings.create_new_category) ?: "")
        default.order = CREATE_CATEGORY_ORDER
        default.id = Int.MIN_VALUE
        return default
    }

    /**
     * Creates and adds a new category to the database.
     * The category is added to the appropriate table(s) based on scope.
     *
     * @param name The name of the category to create.
     * @param scope The scope determining which mode(s) the category applies to.
     */
    fun createCategory(name: String, scope: CategoryScope = when (currentMode) {
        ContentType.MANGA -> CategoryScope.MANGA_ONLY
        ContentType.NOVEL -> CategoryScope.NOVEL_ONLY
    }): Boolean {
        // Do not allow duplicate categories.
        if (categoryExists(name, null)) {
            controller.onCategoryExistsError()
            return false
        }

        // Create category.
        val cat = Category.create(name)

        // Set the new item in the last position.
        cat.order = (categories.maxOfOrNull { it.order } ?: 0) + 1

        // Set default sort
        cat.mangaSort = LibrarySort.Title.categoryValue
        
        // Insert into database based on scope
        runBlocking { 
            when (scope) {
                CategoryScope.MANGA_ONLY -> {
                    insertCategories.awaitOne(cat)
                }
                CategoryScope.NOVEL_ONLY -> {
                    insertNovelCategories.awaitOne(cat)
                }
                CategoryScope.BOTH -> {
                    // Create in BOTH tables with the same name
                    insertCategories.awaitOne(cat)
                    // Create a separate instance for novels (will get different ID)
                    val novelCat = Category.create(name)
                    novelCat.order = cat.order
                    novelCat.mangaSort = cat.mangaSort
                    insertNovelCategories.awaitOne(novelCat)
                }
            }
        }
        
        // Refresh categories for current mode
        val cats = runBlocking { fetchCategoriesForCurrentMode() }
        val newCat = cats.find { it.name == name } ?: return false
        categories.add(1, newCat)
        reorderCategories(categories)
        return true
    }
    
    /**
     * Legacy method for backward compatibility.
     * Creates category for current mode only.
     */
    fun createCategory(name: String): Boolean {
        return createCategory(name, when (currentMode) {
            ContentType.MANGA -> CategoryScope.MANGA_ONLY
            ContentType.NOVEL -> CategoryScope.NOVEL_ONLY
        })
    }

    /**
     * Deletes the given categories from the database.
     * Deletes from the appropriate table based on current mode.
     *
     * @param category The category to delete.
     */
    fun deleteCategory(category: Category?) {
        val safeCategory = category?.id ?: return
        scope.launch {
            when (currentMode) {
                ContentType.MANGA -> deleteCategories.awaitOne(safeCategory.toLong())
                ContentType.NOVEL -> deleteNovelCategories.awaitOne(safeCategory.toLong())
            }
            categories.remove(category)
            withContext(Dispatchers.Main) {
                controller.setCategories(categories.map(::CategoryItem))
            }
        }
    }

    /**
     * Reorders the given categories in the database.
     * Updates the appropriate table based on current mode.
     *
     * @param categories The list of categories to reorder.
     */
    fun reorderCategories(categories: List<Category>) {
        scope.launch {
            val updates: MutableList<CategoryUpdate> = mutableListOf()
            categories
                .filter { it.order != CREATE_CATEGORY_ORDER }
                .forEachIndexed { i, category ->
                    category.order = i - 1
                    updates.add(
                        CategoryUpdate(
                            id = category.id!!.toLong(),
                            order = category.order.toLong(),
                        )
                    )
                }
            when (currentMode) {
                ContentType.MANGA -> updateCategories.await(updates)
                ContentType.NOVEL -> updateNovelCategories.await(updates)
            }
            this@CategoryPresenter.categories = categories.sortedBy { it.order }.toMutableList()
            withContext(Dispatchers.Main) {
                controller.setCategories(this@CategoryPresenter.categories.map(::CategoryItem))
            }
        }
    }

    /**
     * Renames a category.
     * Updates the appropriate table based on current mode.
     *
     * @param category The category to rename.
     * @param name The new name of the category.
     */
    fun renameCategory(category: Category, name: String): Boolean {
        // Do not allow duplicate categories.
        if (categoryExists(name, category.id)) {
            controller.onCategoryExistsError()
            return false
        }
        if (name.isBlank()) {
            return false
        }

        category.name = name
        runBlocking {
            val update = CategoryUpdate(
                id = category.id!!.toLong(),
                name = category.name,
            )
            when (currentMode) {
                ContentType.MANGA -> updateCategories.awaitOne(update)
                ContentType.NOVEL -> updateNovelCategories.awaitOne(update)
            }
        }
        categories.find { it.id == category.id }?.name = name
        controller.setCategories(categories.map(::CategoryItem))
        return true
    }
    
    /**
     * Determines the scope of a category based on whether it exists in both tables.
     * This is used to determine edit restrictions.
     */
    fun getCategoryScope(category: Category): CategoryScope {
        // Check if category with same name exists in both tables
        val mangaCategories = runBlocking { getCategories.await() }
        val novelCategories = runBlocking { getNovelCategories.await() }
        
        val existsInManga = mangaCategories.any { it.name.equals(category.name, ignoreCase = true) }
        val existsInNovel = novelCategories.any { it.name.equals(category.name, ignoreCase = true) }
        
        return when {
            existsInManga && existsInNovel -> CategoryScope.BOTH
            currentMode == ContentType.MANGA -> CategoryScope.MANGA_ONLY
            else -> CategoryScope.NOVEL_ONLY
        }
    }
    
    /**
     * Updates a category's name and optionally upgrades its scope.
     * Scope can only be upgraded (single -> both), never downgraded.
     */
    fun updateCategory(category: Category, newName: String, originalScope: CategoryScope, newScope: CategoryScope) {
        // Validate
        if (newName.isBlank()) {
            return
        }
        
        // Check for duplicates (excluding the current category)
        if (categoryExists(newName, category.id)) {
            controller.onCategoryExistsError()
            return
        }
        
        scope.launch {
            // First, rename the category in current mode's table
            category.name = newName
            val update = CategoryUpdate(
                id = category.id!!.toLong(),
                name = newName,
            )
            when (currentMode) {
                ContentType.MANGA -> updateCategories.awaitOne(update)
                ContentType.NOVEL -> updateNovelCategories.awaitOne(update)
            }
            
            // If upgrading scope from single to both, create in the other table
            if (originalScope != CategoryScope.BOTH && newScope == CategoryScope.BOTH) {
                val newCat = Category.create(newName)
                newCat.order = category.order
                newCat.mangaSort = category.mangaSort
                
                when (currentMode) {
                    ContentType.MANGA -> {
                        // Category exists in manga, create in novel
                        insertNovelCategories.awaitOne(newCat)
                    }
                    ContentType.NOVEL -> {
                        // Category exists in novel, create in manga
                        insertCategories.awaitOne(newCat)
                    }
                }
            }
            
            // Also update the other table if it's a "both" category
            if (originalScope == CategoryScope.BOTH) {
                // Find and update in the other table by old name
                when (currentMode) {
                    ContentType.MANGA -> {
                        val novelCats = getNovelCategories.await()
                        val otherCat = novelCats.find { it.name.equals(category.name, ignoreCase = true) || it.id == category.id }
                        otherCat?.let {
                            val otherUpdate = CategoryUpdate(id = it.id!!.toLong(), name = newName)
                            updateNovelCategories.awaitOne(otherUpdate)
                        }
                    }
                    ContentType.NOVEL -> {
                        val mangaCats = getCategories.await()
                        val otherCat = mangaCats.find { it.name.equals(category.name, ignoreCase = true) || it.id == category.id }
                        otherCat?.let {
                            val otherUpdate = CategoryUpdate(id = it.id!!.toLong(), name = newName)
                            updateCategories.awaitOne(otherUpdate)
                        }
                    }
                }
            }
            
            // Refresh the list
            categories.find { it.id == category.id }?.name = newName
            withContext(Dispatchers.Main) {
                controller.setCategories(categories.map(::CategoryItem))
            }
        }
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun categoryExists(name: String, id: Int?): Boolean {
        return categories.any { it.name.equals(name, true) && id != it.id }
    }

    companion object {
        const val CREATE_CATEGORY_ORDER = -2
    }
}
