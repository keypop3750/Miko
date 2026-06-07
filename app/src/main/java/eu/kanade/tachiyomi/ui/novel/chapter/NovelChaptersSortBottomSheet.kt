package eu.kanade.tachiyomi.ui.novel.chapter

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isInvisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.databinding.NovelChapterSortBottomSheetBinding
import eu.kanade.tachiyomi.ui.novel.details.NovelDetailsControllerNew
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.checkHeightThen
import eu.kanade.tachiyomi.util.view.setBottomEdge
import eu.kanade.tachiyomi.widget.E2EBottomSheetDialog
import eu.kanade.tachiyomi.widget.SortTextView
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import yokai.domain.novel.Novel
import eu.kanade.tachiyomi.util.novel.sorting
import eu.kanade.tachiyomi.util.novel.sortDescending
import eu.kanade.tachiyomi.util.hideChapterTitle
import kotlin.math.max

class NovelChaptersSortBottomSheet(controller: NovelDetailsControllerNew) :
    E2EBottomSheetDialog<NovelChapterSortBottomSheetBinding>(controller.activity!!) {

    val activity = controller.activity!!

    private val presenter = controller.presenter

    override fun createBinding(inflater: LayoutInflater) = NovelChapterSortBottomSheetBinding.inflate(inflater)
    
    init {
        val height = activity.window.decorView.rootWindowInsetsCompat
            ?.getInsetsIgnoringVisibility(systemBars())?.bottom ?: 0
        sheetBehavior.peekHeight = 470.dpToPx + height

        sheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) {
                    if (progress.isNaN()) {
                        binding.pill.alpha = 0f
                    } else {
                        binding.pill.alpha = (1 - max(0f, progress)) * 0.25f
                    }
                }

                override fun onStateChanged(p0: View, state: Int) {
                    if (state == BottomSheetBehavior.STATE_EXPANDED) {
                        sheetBehavior.skipCollapsed = true
                    }
                }
            },
        )
    }

    override fun onStart() {
        super.onStart()
        sheetBehavior.skipCollapsed = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initGeneralPreferences()
        setBottomEdge(binding.hideTitles, activity)
        binding.settingsScrollView.checkHeightThen {
            val isScrollable =
                binding.settingsScrollView.height < binding.sortLayout.height +
                    binding.settingsScrollView.paddingTop + binding.settingsScrollView.paddingBottom
            binding.pill.isInvisible = isScrollable
        }
    }

    private fun initGeneralPreferences() {
        // Set up filter checkboxes for novels
        val novel = presenter.novelValue ?: return
        binding.chapterFilterLayout.root.setCheckboxes(novel, presenter.preferences)
        checkIfFilterMatchesDefault(binding.chapterFilterLayout.root)
        binding.chapterFilterLayout.root.setOnCheckedChangeListener(::setFilters)

        // Initialize sort buttons
        binding.byChapterNumber.state = SortTextView.State.NONE
        binding.byUploadDate.state = SortTextView.State.NONE
        binding.bySource.state = SortTextView.State.NONE

        val sortItem = when (novel.sorting) {
            Novel.CHAPTER_SORTING_NUMBER -> binding.byChapterNumber
            Novel.CHAPTER_SORTING_UPLOAD_DATE -> binding.byUploadDate
            else -> binding.bySource
        }

        sortItem.state = if (novel.sortDescending(presenter.preferences)) {
            SortTextView.State.DESCENDING
        } else {
            SortTextView.State.ASCENDING
        }

        checkIfSortMatchesDefault()
        binding.byChapterNumber.setOnSortChangeListener(::sortChanged)
        binding.byUploadDate.setOnSortChangeListener(::sortChanged)
        binding.bySource.setOnSortChangeListener(::sortChanged)

        // Hide title checkbox - controls whether to display chapter names or numbers
        binding.hideTitles.isChecked = novel.hideChapterTitle(presenter.preferences)
        binding.hideTitles.setOnCheckedChangeListener { _, isChecked ->
            presenter.hideTitle(isChecked)
            checkIfFilterMatchesDefault(binding.chapterFilterLayout.root)
        }

        // Set as default sort button
        binding.setAsDefaultSort.setOnClickListener {
            presenter.setGlobalChapterSort(
                novel.sorting,
                novel.sortDescending(presenter.preferences),
            )
            binding.setAsDefaultSort.isInvisible = true
            binding.resetAsDefaultSort.isInvisible = true
        }

        // Reset to default sort button
        binding.resetAsDefaultSort.setOnClickListener {
            presenter.resetSortingToDefault()

            val updatedNovel = presenter.novelValue ?: return@setOnClickListener
            binding.byChapterNumber.state = SortTextView.State.NONE
            binding.byUploadDate.state = SortTextView.State.NONE
            binding.bySource.state = SortTextView.State.NONE

            val sortItemNew = when (updatedNovel.sorting) {
                Novel.CHAPTER_SORTING_NUMBER -> binding.byChapterNumber
                Novel.CHAPTER_SORTING_UPLOAD_DATE -> binding.byUploadDate
                else -> binding.bySource
            }

            sortItemNew.state = if (updatedNovel.sortDescending(presenter.preferences)) {
                SortTextView.State.DESCENDING
            } else {
                SortTextView.State.ASCENDING
            }
            binding.setAsDefaultSort.isInvisible = true
            binding.resetAsDefaultSort.isInvisible = true
        }

        // Set as default filter button
        binding.chapterFilterLayout.setAsDefaultFilter.setOnClickListener {
            presenter.setGlobalChapterFilters(
                binding.chapterFilterLayout.showUnread.state,
                binding.chapterFilterLayout.showDownload.state,
                binding.chapterFilterLayout.showBookmark.state,
            )
            binding.chapterFilterLayout.setAsDefaultFilter.isInvisible = true
            binding.chapterFilterLayout.resetAsDefaultFilter.isInvisible = true
        }

        // Reset to default filter button
        binding.chapterFilterLayout.resetAsDefaultFilter.setOnClickListener {
            presenter.resetFilterToDefault()

            val updatedNovel = presenter.novelValue ?: return@setOnClickListener
            binding.chapterFilterLayout.root.setCheckboxes(updatedNovel, presenter.preferences)
            binding.hideTitles.isChecked = updatedNovel.hideChapterTitle(presenter.preferences)
            binding.chapterFilterLayout.setAsDefaultFilter.isInvisible = true
            binding.chapterFilterLayout.resetAsDefaultFilter.isInvisible = true
        }

        // Hide filter groups button (novels don't support scanlator filtering yet)
        // TODO: Implement scanlator filtering for novels (Phase 2.2 in implementation plan)
        binding.filterGroupsButton.isInvisible = true
    }

    private fun setFilters(filterLayout: ChapterFilterLayout) {
        presenter.setFilters(
            binding.chapterFilterLayout.showUnread.state,
            binding.chapterFilterLayout.showDownload.state,
            binding.chapterFilterLayout.showBookmark.state,
        )
        checkIfFilterMatchesDefault(filterLayout)
    }

    private fun checkIfFilterMatchesDefault(filterLayout: ChapterFilterLayout) {
        val matches = presenter.mangaFilterMatchesDefault()
        filterLayout.binding.setAsDefaultFilter.isInvisible = matches
        filterLayout.binding.resetAsDefaultFilter.isInvisible = matches
    }

    private fun checkIfSortMatchesDefault() {
        val matches = presenter.novelSortMatchesDefault()
        binding.setAsDefaultSort.isInvisible = matches
        binding.resetAsDefaultSort.isInvisible = matches
    }

    private fun sortChanged(sortTextView: SortTextView, state: SortTextView.State) {
        if (sortTextView != binding.byChapterNumber) {
            binding.byChapterNumber.state = SortTextView.State.NONE
        }
        if (sortTextView != binding.byUploadDate) {
            binding.byUploadDate.state = SortTextView.State.NONE
        }
        if (sortTextView != binding.bySource) {
            binding.bySource.state = SortTextView.State.NONE
        }
        presenter.setSortOrder(
            when (sortTextView) {
                binding.byChapterNumber -> Novel.CHAPTER_SORTING_NUMBER
                binding.byUploadDate -> Novel.CHAPTER_SORTING_UPLOAD_DATE
                else -> Novel.CHAPTER_SORTING_SOURCE
            },
            state == SortTextView.State.DESCENDING,
        )
        checkIfSortMatchesDefault()
    }
}
