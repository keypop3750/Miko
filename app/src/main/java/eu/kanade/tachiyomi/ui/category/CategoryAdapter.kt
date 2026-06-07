package eu.kanade.tachiyomi.ui.category

import eu.davidea.flexibleadapter.FlexibleAdapter

/**
 * Custom adapter for categories.
 *
 * @param controller The containing controller.
 */
class CategoryAdapter(controller: CategoryController) :
    FlexibleAdapter<CategoryItem>(null, controller, true) {

    /**
     * Listener called when an item of the list is released.
     */
    val categoryItemListener: CategoryItemListener = controller

    interface CategoryItemListener {
        /**
         * Called when an item of the list is released.
         */
        fun onItemReleased(position: Int)
        fun onCategoryRename(position: Int, newName: String): Boolean
        fun onItemDelete(position: Int)
        fun onCategoryEdit(position: Int)
    }
}
