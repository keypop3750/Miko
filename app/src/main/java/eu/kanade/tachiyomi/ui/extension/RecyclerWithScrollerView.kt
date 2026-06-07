package eu.kanade.tachiyomi.ui.extension

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.RecyclerWithScrollerBinding

/**
 * Interface for extension bottom sheets that can work with RecyclerWithScrollerView
 */
interface ExtensionBottomSheetLike {
    val sheetBehavior: BottomSheetBehavior<*>?
}

class RecyclerWithScrollerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    var binding: RecyclerWithScrollerBinding? = null
    fun setUp(sheet: ExtensionBottomSheetLike, binding: RecyclerWithScrollerBinding, height: Int) {
        binding.recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.recycler.setHasFixedSize(true)
        binding.recycler.addItemDecoration(ExtensionDividerItemDecoration(context))
        binding.recycler.updatePaddingRelative(bottom = height)
        binding.recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE ||
                        newState == RecyclerView.SCROLL_STATE_SETTLING
                    ) {
                        sheet.sheetBehavior?.isDraggable = true
                    } else {
                        sheet.sheetBehavior?.isDraggable = !recyclerView.canScrollVertically(-1)
                    }
                }
            },
        )

        this.binding = binding
    }
    
    // Legacy overload for ExtensionBottomSheet 
    fun setUp(sheet: ExtensionBottomSheet, binding: RecyclerWithScrollerBinding, height: Int) {
        setUp(sheet as ExtensionBottomSheetLike, binding, height)
    }

    fun onBind(adapter: FlexibleAdapter<IFlexible<*>>) {
        binding?.recycler?.adapter = adapter
        adapter.fastScroller = binding?.fastScroller
    }
}
