package eu.kanade.tachiyomi.ui.novel.chapter

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.NovelChapterFilterLayoutBinding
import yokai.domain.novel.Novel
import eu.kanade.tachiyomi.util.novel.bookmarkedFilter
import eu.kanade.tachiyomi.util.novel.downloadedFilter
import eu.kanade.tachiyomi.util.novel.readFilter
import eu.kanade.tachiyomi.widget.TriStateCheckBox

class ChapterFilterLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    lateinit var binding: NovelChapterFilterLayoutBinding
    private var mOnCheckedChangeListener: OnCheckedChangeListener? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = NovelChapterFilterLayoutBinding.bind(this)
        binding.showAll.setOnCheckedChangeListener(::checkedFilter)
        binding.showUnread.setOnCheckedChangeListener(::checkedFilter)
        binding.showDownload.setOnCheckedChangeListener(::checkedFilter)
        binding.showBookmark.setOnCheckedChangeListener(::checkedFilter)
        
        // Set up filter preset chips
        setupFilterPresets()
    }

    /**
     * Initializes the quick filter preset chips with click listeners.
     * Each preset applies a common filter combination for quick access.
     */
    private fun setupFilterPresets() {
        binding.presetAll.setOnClickListener {
            applyPreset(FilterPreset.ALL)
        }
        binding.presetUnread.setOnClickListener {
            applyPreset(FilterPreset.UNREAD)
        }
        binding.presetDownloaded.setOnClickListener {
            applyPreset(FilterPreset.DOWNLOADED)
        }
        binding.presetBookmarked.setOnClickListener {
            applyPreset(FilterPreset.BOOKMARKED)
        }
        binding.presetClear.setOnClickListener {
            applyPreset(FilterPreset.CLEAR)
        }
    }

    /**
     * Applies a filter preset by setting all checkbox states in a single optimized operation.
     * Uses a map-based approach to reduce repetitive code and improve performance.
     */
    private fun applyPreset(preset: FilterPreset) {
        val states = presetStateMap[preset] ?: return
        val checkboxes = listOf(
            binding.showAll,
            binding.showUnread,
            binding.showDownload,
            binding.showBookmark
        )
        
        checkboxes.forEachIndexed { index, checkbox ->
            checkbox.setState(states[index], false)
        }
        
        // Trigger the change listener after applying preset
        mOnCheckedChangeListener?.onCheckedChanged(this)
    }

    private enum class FilterPreset {
        ALL, UNREAD, DOWNLOADED, BOOKMARKED, CLEAR
    }
    
    companion object {
        /**
         * Pre-defined state combinations for each filter preset.
         * Order: [showAll, showUnread, showDownload, showBookmark]
         */
        private val presetStateMap = mapOf(
            FilterPreset.ALL to listOf(
                TriStateCheckBox.State.CHECKED,
                TriStateCheckBox.State.UNCHECKED,
                TriStateCheckBox.State.UNCHECKED,
                TriStateCheckBox.State.UNCHECKED
            ),
            FilterPreset.UNREAD to listOf(
                TriStateCheckBox.State.UNCHECKED,
                TriStateCheckBox.State.CHECKED,
                TriStateCheckBox.State.UNCHECKED,
                TriStateCheckBox.State.UNCHECKED
            ),
            FilterPreset.DOWNLOADED to listOf(
                TriStateCheckBox.State.UNCHECKED,
                TriStateCheckBox.State.UNCHECKED,
                TriStateCheckBox.State.CHECKED,
                TriStateCheckBox.State.UNCHECKED
            ),
            FilterPreset.BOOKMARKED to listOf(
                TriStateCheckBox.State.UNCHECKED,
                TriStateCheckBox.State.UNCHECKED,
                TriStateCheckBox.State.UNCHECKED,
                TriStateCheckBox.State.CHECKED
            ),
            FilterPreset.CLEAR to listOf(
                TriStateCheckBox.State.CHECKED,
                TriStateCheckBox.State.UNCHECKED,
                TriStateCheckBox.State.UNCHECKED,
                TriStateCheckBox.State.UNCHECKED
            )
        )
    }

    private fun checkedFilter(checkBox: TriStateCheckBox, state: TriStateCheckBox.State) {
        if (state != TriStateCheckBox.State.UNCHECKED) {
            if (binding.showAll == checkBox && state == TriStateCheckBox.State.CHECKED) {
                binding.showUnread.setState(TriStateCheckBox.State.UNCHECKED, true)
                binding.showDownload.setState(TriStateCheckBox.State.UNCHECKED)
                binding.showBookmark.setState(TriStateCheckBox.State.UNCHECKED)
            } else {
                if (binding.showAll == checkBox) {
                    binding.showAll.state = TriStateCheckBox.State.CHECKED
                } else {
                    binding.showAll.setState(TriStateCheckBox.State.UNCHECKED, true)
                }
            }
        } else if (
            binding.showUnread.isUnchecked &&
            binding.showDownload.isUnchecked &&
            binding.showBookmark.isUnchecked
        ) {
            binding.showAll.setState(TriStateCheckBox.State.CHECKED, true)
        }
        mOnCheckedChangeListener?.onCheckedChanged(this)
    }

    fun setCheckboxes(novel: Novel, preferences: PreferencesHelper) {
        binding.showUnread.state = when (novel.readFilter(preferences)) {
            Novel.CHAPTER_SHOW_UNREAD -> TriStateCheckBox.State.CHECKED
            Novel.CHAPTER_SHOW_READ -> TriStateCheckBox.State.IGNORE
            else -> TriStateCheckBox.State.UNCHECKED
        }
        binding.showDownload.state = when (novel.downloadedFilter(preferences)) {
            Novel.CHAPTER_SHOW_DOWNLOADED -> TriStateCheckBox.State.CHECKED
            Novel.CHAPTER_SHOW_NOT_DOWNLOADED -> TriStateCheckBox.State.IGNORE
            else -> TriStateCheckBox.State.UNCHECKED
        }
        binding.showBookmark.state = when (novel.bookmarkedFilter(preferences)) {
            Novel.CHAPTER_SHOW_BOOKMARKED -> TriStateCheckBox.State.CHECKED
            Novel.CHAPTER_SHOW_NOT_BOOKMARKED -> TriStateCheckBox.State.IGNORE
            else -> TriStateCheckBox.State.UNCHECKED
        }

        binding.showAll.isChecked = binding.showUnread.isUnchecked &&
            binding.showDownload.isUnchecked &&
            binding.showBookmark.isUnchecked
    }

    /**
     * Register a callback to be invoked when the checked state of this button
     * changes.
     *
     * @param listener the callback to call on checked state change
     */
    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        mOnCheckedChangeListener = listener
    }

    /**
     * Interface definition for a callback to be invoked when one of the check states in this view
     * changes
     */
    fun interface OnCheckedChangeListener {
        /**
         * Called when the checked state of a compound button has changed.
         *
         * @param filterLayout The view containing the changed state
         */
        fun onCheckedChanged(filterLayout: ChapterFilterLayout)
    }
}
