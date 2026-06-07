package eu.kanade.tachiyomi.ui.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.CategoriesControllerBinding
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseLegacyController
import eu.kanade.tachiyomi.ui.category.CategoryPresenter.Companion.CREATE_CATEGORY_ORDER
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.liftAppbarWith
import eu.kanade.tachiyomi.util.view.setAction
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setTitle
import eu.kanade.tachiyomi.util.view.snack
import yokai.core.category.CategoryScope
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

/**
 * Controller to manage the categories for the users' library.
 * Mode-aware: manages manga categories in MANGA mode and novel categories in NOVEL mode.
 */
class CategoryController(bundle: Bundle? = null) :
    BaseLegacyController<CategoriesControllerBinding>(bundle),
    FlexibleAdapter.OnItemMoveListener,
    SmallToolbarInterface,
    CategoryAdapter.CategoryItemListener {

    /**
     * Adapter containing category items.
     */
    private var adapter: CategoryAdapter? = null

    /**
     * Undo helper used for restoring a deleted category.
     */
    private var snack: Snackbar? = null

    /**
     * Creates the presenter for this controller. Not to be manually called.
     */
    private val presenter = CategoryPresenter(this)

    /**
     * Returns the toolbar title to show when this controller is attached.
     * Shows "Edit Manga Categories" or "Edit Novel Categories" based on current mode.
     */
    override fun getTitle(): String? {
        return "Edit Categories"
    }

    override fun createBinding(inflater: LayoutInflater) = CategoriesControllerBinding.inflate(inflater)

    /**
     * Called after view inflation. Used to initialize the view.
     *
     * @param view The view of this controller.
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        liftAppbarWith(binding.recycler, true, changeMarginsInstead = true)

        adapter = CategoryAdapter(this@CategoryController)
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.adapter = adapter
        adapter?.isHandleDragEnabled = true
        adapter?.isPermanentDelete = false

        presenter.getCategories()
    }

    /**
     * Called when the view is being destroyed. Used to release references and remove callbacks.
     *
     * @param view The view of this controller.
     */
    override fun onDestroyView(view: View) {
        // Manually call callback to delete categories if required
        snack?.dismiss()
        view.clearFocus()
        confirmDelete()
        snack = null
        adapter = null
        super.onDestroyView(view)
    }

    override fun handleBack(): Boolean {
        view?.clearFocus()
        confirmDelete()
        return super.handleBack()
    }

    /**
     * Called from the presenter when the categories are updated.
     *
     * @param categories The new list of categories to display.
     */
    fun setCategories(categories: List<CategoryItem>) {
        adapter?.updateDataSet(categories)
    }

    override fun onCategoryRename(position: Int, newName: String): Boolean {
        val category = adapter?.getItem(position)?.category ?: return false
        if (category.order == CREATE_CATEGORY_ORDER) {
            // This shouldn't happen anymore - we handle create via onItemClick
            return true
        }
        if (newName.isBlank()) {
            activity?.toast(MR.strings.category_cannot_be_blank)
            return false
        }
        return (presenter.renameCategory(category, newName))
    }
    
    /**
     * Called when the edit button (pen icon) is clicked on a category.
     * Opens the edit dialog for the category.
     */
    override fun onCategoryEdit(position: Int) {
        val category = adapter?.getItem(position)?.category ?: return
        if (category.order == CREATE_CATEGORY_ORDER) {
            // Create new category
            showCreateCategoryDialog()
        } else {
            // Edit existing category
            showEditCategoryDialog(category)
        }
    }
    
    /**
     * Shows a dialog for creating a new category with scope selection.
     * User can choose Manga/Webtoon, Novel, or Both.
     */
    private fun showCreateCategoryDialog() {
        val context = activity ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_category, null)
        
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.category_name_input)
        val scopeRadioGroup = dialogView.findViewById<RadioGroup>(R.id.scope_radio_group)
        val scopeInfo = dialogView.findViewById<TextView>(R.id.scope_info)
        val scopeManga = dialogView.findViewById<android.widget.RadioButton>(R.id.scope_manga)
        val scopeNovel = dialogView.findViewById<android.widget.RadioButton>(R.id.scope_novel)
        
        // Default to current mode
        when (ModeManager.currentMode.value) {
            ContentType.MANGA -> scopeRadioGroup.check(R.id.scope_manga)
            ContentType.NOVEL -> scopeRadioGroup.check(R.id.scope_novel)
        }
        
        // Show info text when "Both" is selected
        scopeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            scopeInfo.isVisible = checkedId == R.id.scope_both
        }
        
        MaterialAlertDialogBuilder(context)
            .setTitle("Create Category")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text?.toString()?.trim() ?: ""
                if (name.isBlank()) {
                    context.toast(MR.strings.category_cannot_be_blank)
                    return@setPositiveButton
                }
                
                val scope = when (scopeRadioGroup.checkedRadioButtonId) {
                    R.id.scope_manga -> CategoryScope.MANGA_ONLY
                    R.id.scope_novel -> CategoryScope.NOVEL_ONLY
                    R.id.scope_both -> CategoryScope.BOTH
                    else -> when (ModeManager.currentMode.value) {
                        ContentType.MANGA -> CategoryScope.MANGA_ONLY
                        ContentType.NOVEL -> CategoryScope.NOVEL_ONLY
                    }
                }
                
                presenter.createCategory(name, scope)
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }
    
    /**
     * Shows a dialog for editing an existing category.
     * Scope restrictions:
     * - Manga/Webtoon categories can only upgrade to Both (cannot switch to Novel)
     * - Novel categories can only upgrade to Both (cannot switch to Manga/Webtoon)
     * - Both categories cannot change scope (only rename or delete)
     */
    private fun showEditCategoryDialog(category: eu.kanade.tachiyomi.data.database.models.Category) {
        val context = activity ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_create_category, null)
        
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.category_name_input)
        val scopeRadioGroup = dialogView.findViewById<RadioGroup>(R.id.scope_radio_group)
        val scopeInfo = dialogView.findViewById<TextView>(R.id.scope_info)
        val scopeManga = dialogView.findViewById<android.widget.RadioButton>(R.id.scope_manga)
        val scopeNovel = dialogView.findViewById<android.widget.RadioButton>(R.id.scope_novel)
        val scopeBoth = dialogView.findViewById<android.widget.RadioButton>(R.id.scope_both)
        
        // Prefill name
        nameInput.setText(category.name)
        
        // Determine original scope based on current mode (we're viewing this table's categories)
        val originalScope = presenter.getCategoryScope(category)
        
        // Set up scope restrictions based on original scope
        when (originalScope) {
            CategoryScope.MANGA_ONLY -> {
                scopeRadioGroup.check(R.id.scope_manga)
                // Can only upgrade to Both, cannot switch to Novel
                scopeNovel.isEnabled = false
                scopeNovel.alpha = 0.5f
            }
            CategoryScope.NOVEL_ONLY -> {
                scopeRadioGroup.check(R.id.scope_novel)
                // Can only upgrade to Both, cannot switch to Manga
                scopeManga.isEnabled = false
                scopeManga.alpha = 0.5f
            }
            CategoryScope.BOTH -> {
                scopeRadioGroup.check(R.id.scope_both)
                // Cannot change scope at all
                scopeManga.isEnabled = false
                scopeManga.alpha = 0.5f
                scopeNovel.isEnabled = false
                scopeNovel.alpha = 0.5f
                scopeBoth.isEnabled = false
                scopeBoth.alpha = 0.5f
            }
        }
        
        // Show info text when "Both" is selected
        scopeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            scopeInfo.isVisible = checkedId == R.id.scope_both
        }
        
        MaterialAlertDialogBuilder(context)
            .setTitle("Edit Category")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text?.toString()?.trim() ?: ""
                if (name.isBlank()) {
                    context.toast(MR.strings.category_cannot_be_blank)
                    return@setPositiveButton
                }
                
                val newScope = when (scopeRadioGroup.checkedRadioButtonId) {
                    R.id.scope_manga -> CategoryScope.MANGA_ONLY
                    R.id.scope_novel -> CategoryScope.NOVEL_ONLY
                    R.id.scope_both -> CategoryScope.BOTH
                    else -> originalScope
                }
                
                // Update category name and optionally upgrade scope
                presenter.updateCategory(category, name, originalScope, newScope)
            }
            .setNeutralButton("Delete") { _, _ ->
                // Confirm deletion
                activity!!.materialAlertDialog()
                    .setTitle(MR.strings.confirm_category_deletion)
                    .setMessage(MR.strings.confirm_category_deletion_message)
                    .setPositiveButton(MR.strings.delete) { _, _ ->
                        presenter.deleteCategory(category)
                    }
                    .setNegativeButton(AR.string.cancel, null)
                    .show()
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }

    override fun onItemDelete(position: Int) {
        activity!!.materialAlertDialog()
            .setTitle(MR.strings.confirm_category_deletion)
            .setMessage(MR.strings.confirm_category_deletion_message)
            .setPositiveButton(MR.strings.delete) { _, _ ->
                deleteCategory(position)
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }

    private fun deleteCategory(position: Int) {
        confirmDelete()
        adapter?.removeItem(position)
        snack =
            view?.snack(MR.strings.category_deleted, Snackbar.LENGTH_INDEFINITE) {
                var undoing = false
                setAction(MR.strings.undo) {
                    adapter?.restoreDeletedItems()
                    undoing = true
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            if (!undoing) confirmDelete()
                        }
                    },
                )
            }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    /**
     * Called when an item is released from a drag.
     *
     * @param position The position of the released item.
     */
    override fun onItemReleased(position: Int) {
        val adapter = adapter ?: return
        val categories = (0 until adapter.itemCount).mapNotNull { adapter.getItem(it)?.category }
        presenter.reorderCategories(categories)
    }

    fun confirmDelete() {
        val adapter = adapter ?: return
        presenter.deleteCategory(adapter.deletedItems.map { it.category }.firstOrNull())
        adapter.confirmDeletion()
        snack = null
    }

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {}

    override fun onItemMove(fromPosition: Int, toPosition: Int) {}

    override fun shouldMoveItem(fromPosition: Int, toPosition: Int): Boolean {
        return toPosition > 0
    }

    /**
     * Called from the presenter when a category with the given name already exists.
     */
    fun onCategoryExistsError() {
        activity?.toast(MR.strings.category_with_name_exists)
    }
}
