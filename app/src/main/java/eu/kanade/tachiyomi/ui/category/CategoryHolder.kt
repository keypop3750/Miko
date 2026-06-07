package eu.kanade.tachiyomi.ui.category

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.databinding.CategoriesItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.category.CategoryPresenter.Companion.CREATE_CATEGORY_ORDER
import eu.kanade.tachiyomi.util.system.getResourceColor
import java.util.*

/**
 * Holder used to display category items.
 *
 * @param view The view used by category items.
 * @param adapter The adapter containing this holder.
 */
class CategoryHolder(view: View, val adapter: CategoryAdapter) : BaseFlexibleViewHolder(view, adapter) {

    private val binding = CategoriesItemBinding.bind(view)
    init {
        // Click on edit button opens the edit/create dialog
        binding.editButton.setOnClickListener {
            adapter.categoryItemListener.onCategoryEdit(flexibleAdapterPosition)
        }
        
        // Click on the whole row also opens the edit/create dialog
        itemView.setOnClickListener {
            adapter.categoryItemListener.onCategoryEdit(flexibleAdapterPosition)
        }
    }

    var createCategory = false
    private var regularDrawable: Drawable? = null

    /**
     * Binds this holder with the given category.
     *
     * @param category The category to bind.
     */
    fun bind(category: Category) {
        // Set capitalized title.
        binding.title.text = category.name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        
        createCategory = category.order == CREATE_CATEGORY_ORDER
        if (createCategory) {
            binding.title.setTextColor(ContextCompat.getColor(itemView.context, R.color.material_on_background_disabled))
            regularDrawable = ContextCompat.getDrawable(
                itemView.context,
                R.drawable.ic_add_24dp,
            )
            binding.image.isVisible = false
            binding.editButton.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_add_24dp))
            binding.editButton.drawable?.mutate()?.setTint(
                ContextCompat.getColor(itemView.context, R.color.gray_button)
            )
            binding.reorder.setOnTouchListener { _, _ -> true }
        } else {
            binding.title.setTextColor(itemView.context.getResourceColor(R.attr.colorOnBackground))
            regularDrawable = ContextCompat.getDrawable(
                itemView.context,
                R.drawable.ic_drag_handle_24dp,
            )
            binding.image.isVisible = true
            binding.editButton.setImageDrawable(ContextCompat.getDrawable(itemView.context, R.drawable.ic_edit_24dp))
            binding.editButton.drawable?.mutate()?.setTint(
                ContextCompat.getColor(itemView.context, R.color.gray_button)
            )
            setDragHandleView(binding.reorder)
        }
        binding.reorder.setImageDrawable(regularDrawable)
        
        // Hide inline edit text - we use dialog now
        binding.editText.visibility = View.GONE
    }

    private fun showKeyboard() {
        val inputMethodManager: InputMethodManager =
            itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(
            binding.editText,
            WindowManager.LayoutParams
                .SOFT_INPUT_ADJUST_PAN,
        )
    }

    override fun onActionStateChanged(position: Int, actionState: Int) {
        super.onActionStateChanged(position, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            binding.root.isDragged = true
        }
    }

    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        adapter.categoryItemListener.onItemReleased(position)
        binding.root.isDragged = false
    }
}
