package eu.kanade.tachiyomi.ui.category

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.RadioButton
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MangaCategoryDialogBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.library.LibrarySort
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setTitle
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.injectLazy
import yokai.core.category.CategoryScope
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager
import yokai.domain.category.interactor.GetCategories
import yokai.domain.category.interactor.GetNovelCategories
import yokai.domain.category.interactor.InsertCategories
import yokai.domain.category.interactor.InsertNovelCategories
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

class ManageCategoryDialog(bundle: Bundle? = null) :
    DialogController(bundle) {

    constructor(category: Category?, updateLibrary: ((Int?) -> Unit)) : this() {
        this.updateLibrary = updateLibrary
        this.category = category
    }

    private var updateLibrary: ((Int?) -> Unit)? = null
    private var category: Category? = null

    private val preferences by injectLazy<PreferencesHelper>()
    private val getCategories by injectLazy<GetCategories>()
    private val getNovelCategories by injectLazy<GetNovelCategories>()
    private val insertCategories by injectLazy<InsertCategories>()
    private val insertNovelCategories by injectLazy<InsertNovelCategories>()

    lateinit var binding: MangaCategoryDialogBinding
    
    private val currentMode: ContentType
        get() = ModeManager.currentMode.value

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val dialog = dialog(activity!!).create()
        onViewCreated()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                if (onPositiveButtonClick()) {
                    dialog.dismiss()
                }
            }
        }
        return dialog
    }

    fun dialog(activity: Activity): MaterialAlertDialogBuilder {
        return activity.materialAlertDialog().apply {
            setTitle(if (category == null) MR.strings.new_category else MR.strings.manage_category)
            binding = MangaCategoryDialogBinding.inflate(activity.layoutInflater)
            setView(binding.root)
            setNegativeButton(AR.string.cancel, null)
            setPositiveButton(MR.strings.save) { dialog, _ ->
                if (onPositiveButtonClick()) {
                    dialog.dismiss()
                }
            }
        }
    }

    fun show(activity: Activity) {
        val dialog = dialog(activity).create()
        onViewCreated()
        dialog.setOnShowListener {
            binding.title.requestFocus()
        }
        dialog.show()
    }

    private fun onPositiveButtonClick(): Boolean {
        val text = binding.title.text.toString()
        val categoryExists = categoryExists(text)
        val category = this.category ?: Category.create(text)
        if (category.id != 0) {
            if (text.isNotBlank() && !categoryExists &&
                !text.equals(this.category?.name ?: "", true)
            ) {
                category.name = text
                if (this.category == null) {
                    // New category - check scope selection
                    val scope = getSelectedScope()
                    
                    // FIXME: Don't do blocking
                    runBlocking {
                        when (scope) {
                            CategoryScope.MANGA_ONLY -> {
                                val categories = getCategories.await()
                                category.order = (categories.maxOfOrNull { it.order } ?: 0) + 1
                                category.mangaSort = LibrarySort.Title.categoryValue
                                category.id = insertCategories.awaitOne(category)?.toInt()
                            }
                            CategoryScope.NOVEL_ONLY -> {
                                val categories = getNovelCategories.await()
                                category.order = (categories.maxOfOrNull { it.order } ?: 0) + 1
                                category.mangaSort = LibrarySort.Title.categoryValue
                                category.id = insertNovelCategories.awaitOne(category)?.toInt()
                            }
                            CategoryScope.BOTH -> {
                                // Create in BOTH tables
                                val mangaCategories = getCategories.await()
                                category.order = (mangaCategories.maxOfOrNull { it.order } ?: 0) + 1
                                category.mangaSort = LibrarySort.Title.categoryValue
                                category.id = insertCategories.awaitOne(category)?.toInt()
                                
                                // Create a separate instance for novels
                                val novelCat = Category.create(text)
                                val novelCategories = getNovelCategories.await()
                                novelCat.order = (novelCategories.maxOfOrNull { it.order } ?: 0) + 1
                                novelCat.mangaSort = LibrarySort.Title.categoryValue
                                insertNovelCategories.awaitOne(novelCat)
                            }
                        }
                    }
                    this.category = category
                } else {
                    // Editing existing category - just update current mode's table
                    runBlocking {
                        when (currentMode) {
                            ContentType.MANGA -> insertCategories.awaitOne(category)
                            ContentType.NOVEL -> insertNovelCategories.awaitOne(category)
                        }
                    }
                }
            } else if (categoryExists) {
                binding.categoryTextLayout.error =
                    binding.categoryTextLayout.context.getString(MR.strings.category_with_name_exists)
                return false
            } else if (text.isBlank()) {
                binding.categoryTextLayout.error =
                    binding.categoryTextLayout.context.getString(MR.strings.category_cannot_be_blank)
                return false
            }
        }
        when (
            updatePref(
                preferences.downloadNewChaptersInCategories(),
                preferences.excludeCategoriesInDownloadNew(),
                binding.downloadNew,
            )
        ) {
            true -> preferences.downloadNewChapters().set(true)
            false -> preferences.downloadNewChapters().set(false)
            else -> {}
        }
        if (preferences.libraryUpdateInterval().get() > 0 &&
            updatePref(
                    preferences.libraryUpdateCategories(),
                    preferences.libraryUpdateCategoriesExclude(),
                    binding.includeGlobal,
                ) == false
        ) {
            preferences.libraryUpdateInterval().set(0)
            LibraryUpdateJob.setupTask(preferences.context, 0)
        }
        updateLibrary?.invoke(category.id)
        return true
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun categoryExists(name: String): Boolean {
        // FIXME: Don't do blocking
        // Check in the appropriate table(s) based on scope
        return runBlocking {
            when (currentMode) {
                ContentType.MANGA -> getCategories.await()
                ContentType.NOVEL -> getNovelCategories.await()
            }
        }.any {
            it.name.equals(name, true) && category?.id != it.id
        }
    }
    
    /**
     * Get the selected scope from radio buttons.
     * Default to current mode if no scope container is visible.
     */
    private fun getSelectedScope(): CategoryScope {
        return when (binding.scopeRadioGroup.checkedRadioButtonId) {
            R.id.scope_manga -> CategoryScope.MANGA_ONLY
            R.id.scope_novel -> CategoryScope.NOVEL_ONLY
            R.id.scope_both -> CategoryScope.BOTH
            else -> when (currentMode) {
                ContentType.MANGA -> CategoryScope.MANGA_ONLY
                ContentType.NOVEL -> CategoryScope.NOVEL_ONLY
            }
        }
    }

    fun onViewCreated() {
        if ((category?.id ?: 0) <= 0 && category != null) {
            binding.categoryTextLayout.isVisible = false
        }
        binding.editCategories.isVisible = category != null
        
        // Show scope selection only for new categories
        val isNewCategory = category == null
        binding.scopeContainer.isVisible = isNewCategory
        if (isNewCategory) {
            // Default to current mode
            when (currentMode) {
                ContentType.MANGA -> binding.scopeRadioGroup.check(R.id.scope_manga)
                ContentType.NOVEL -> binding.scopeRadioGroup.check(R.id.scope_novel)
            }
        }
        
        binding.editCategories.setOnClickListener {
            router.popCurrentController()
            router.pushController(CategoryController().withFadeTransaction())
        }
        binding.title.addTextChangedListener {
            binding.categoryTextLayout.error = null
        }
        binding.title.hint =
            category?.name ?: binding.editCategories.context.getString(MR.strings.category)
        binding.title.append(category?.name ?: "")
        val downloadNew = preferences.downloadNewChapters().get()
        setCheckbox(
            binding.downloadNew,
            preferences.downloadNewChaptersInCategories(),
            preferences.excludeCategoriesInDownloadNew(),
            true,
        )
        if (downloadNew && preferences.downloadNewChaptersInCategories().get().isEmpty()) {
            binding.downloadNew.isVisible = false
        } else if (!downloadNew) {
            binding.downloadNew.isVisible = true
        }
        if (!downloadNew) {
            binding.downloadNew.isChecked = false
        }
        setCheckbox(
            binding.includeGlobal,
            preferences.libraryUpdateCategories(),
            preferences.libraryUpdateCategoriesExclude(),
            preferences.libraryUpdateInterval().get() > 0,
        )
    }

    /** Update a pref based on checkbox, and return if the pref is not empty */
    private fun updatePref(
        categories: Preference<Set<String>>,
        excludeCategories: Preference<Set<String>>,
        box: TriStateCheckBox,
    ): Boolean? {
        val categoryId = category?.id ?: return null
        if (!box.isVisible) return null
        val updateCategories = categories.get().toMutableSet()
        val excludeUpdateCategories = excludeCategories.get().toMutableSet()
        when (box.state) {
            TriStateCheckBox.State.CHECKED -> {
                updateCategories.add(categoryId.toString())
                excludeUpdateCategories.remove(categoryId.toString())
            }
            TriStateCheckBox.State.IGNORE -> {
                updateCategories.remove(categoryId.toString())
                excludeUpdateCategories.add(categoryId.toString())
            }
            TriStateCheckBox.State.UNCHECKED -> {
                updateCategories.remove(categoryId.toString())
                excludeUpdateCategories.remove(categoryId.toString())
            }
        }
        categories.set(updateCategories)
        excludeCategories.set(excludeUpdateCategories)
        return updateCategories.isNotEmpty()
    }

    private fun setCheckbox(
        box: TriStateCheckBox,
        categories: Preference<Set<String>>,
        excludeCategories: Preference<Set<String>>,
        shouldShow: Boolean,
    ) {
        val updateCategories = categories.get()
        val excludeUpdateCategories = excludeCategories.get()
        box.isVisible = (updateCategories.isNotEmpty() || excludeUpdateCategories.isNotEmpty()) && shouldShow
        if (shouldShow) {
            box.state = when {
                updateCategories.any { category?.id == it.toIntOrNull() } -> TriStateCheckBox.State.CHECKED
                excludeUpdateCategories.any { category?.id == it.toIntOrNull() } -> TriStateCheckBox.State.IGNORE
                else -> TriStateCheckBox.State.UNCHECKED
            }
        }
    }
}
