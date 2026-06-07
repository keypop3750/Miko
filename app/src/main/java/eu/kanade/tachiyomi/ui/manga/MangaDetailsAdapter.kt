package eu.kanade.tachiyomi.ui.manga

import android.view.ActionMode
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import dev.icerock.moko.resources.StringResource
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import yokai.util.lang.getString
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MangaDetailsControllerBinding
import eu.kanade.tachiyomi.databinding.MangaDetailsActivityBinding
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import eu.kanade.tachiyomi.ui.base.MaterialFastScroll
import eu.kanade.tachiyomi.ui.manga.chapter.BaseChapterAdapter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.system.isLTR
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class MangaDetailsAdapter private constructor(
    controllerOrDelegate: Any,
    val delegate: MangaDetailsInterface,
    val presenter: MangaDetailsPresenter,
    bindingProvider: (() -> Any)?,
) : BaseChapterAdapter<IFlexible<*>>(delegate) {

    // Constructor for Controller-based usage (existing code)
    constructor(controller: MangaDetailsController) : this(
        controllerOrDelegate = controller,
        delegate = controller,
        presenter = controller.presenter,
        bindingProvider = { controller.binding }
    )
    
    // Constructor for Activity-based usage with Activity binding
    constructor(
        delegate: MangaDetailsInterface,
        presenter: MangaDetailsPresenter,
        binding: MangaDetailsActivityBinding
    ) : this(
        controllerOrDelegate = delegate,
        delegate = delegate,
        presenter = presenter,
        bindingProvider = { binding }
    )
    
    // Legacy constructor for Activity-based usage with Controller binding
    constructor(
        delegate: MangaDetailsInterface,
        presenter: MangaDetailsPresenter,
        binding: MangaDetailsControllerBinding
    ) : this(
        controllerOrDelegate = delegate,
        delegate = delegate,
        presenter = presenter,
        bindingProvider = { binding }
    )

    val controller: MangaDetailsController? = controllerOrDelegate as? MangaDetailsController
    
    // Internal binding storage (supports both Controller and Activity bindings)
    private val bindingInternal: Any by lazy { 
        bindingProvider?.invoke() ?: throw IllegalStateException("Binding not available") 
    }
    
    // Type-safe binding access with duck-typing for common properties
    // Both MangaDetailsControllerBinding and MangaDetailsActivityBinding have the same view structure
    val binding: BindingWrapper by lazy {
        when (val b = bindingInternal) {
            is MangaDetailsControllerBinding -> BindingWrapper.Controller(b)
            is MangaDetailsActivityBinding -> BindingWrapper.Activity(b)
            else -> throw IllegalStateException("Unknown binding type: ${b.javaClass}")
        }
    }
    
    // Wrapper to provide common interface for both binding types
    sealed class BindingWrapper {
        abstract val recycler: RecyclerView
        abstract val swipeRefresh: SwipeRefreshLayout
        abstract val mangaCoverFull: ShapeableImageView
        abstract val fastScroller: MaterialFastScroll
        abstract val touchView: View
        abstract val tabletRecycler: RecyclerView
        abstract val tabletDivider: View
        abstract val tabletOverlay: View
        abstract val root: View
        
        class Controller(private val binding: MangaDetailsControllerBinding) : BindingWrapper() {
            override val recycler get() = binding.recycler
            override val swipeRefresh get() = binding.swipeRefresh
            override val mangaCoverFull get() = binding.mangaCoverFull
            override val fastScroller get() = binding.fastScroller
            override val touchView get() = binding.touchView
            override val tabletRecycler get() = binding.tabletRecycler
            override val tabletDivider get() = binding.tabletDivider
            override val tabletOverlay get() = binding.tabletOverlay
            override val root get() = binding.root
        }
        
        class Activity(private val binding: MangaDetailsActivityBinding) : BindingWrapper() {
            override val recycler get() = binding.recycler
            override val swipeRefresh get() = binding.swipeRefresh
            override val mangaCoverFull get() = binding.mangaCoverFull
            override val fastScroller get() = binding.fastScroller
            override val touchView get() = binding.touchView
            override val tabletRecycler get() = binding.tabletRecycler
            override val tabletDivider get() = binding.tabletDivider
            override val tabletOverlay get() = binding.tabletOverlay
            override val root get() = binding.root
        }
    }

    val preferences: PreferencesHelper by injectLazy()

    val hasShownSwipeTut
        get() = preferences.shownChapterSwipeTutorial()

    var items: List<ChapterItem> = emptyList()
        private set

    val decimalFormat = DecimalFormat(
        "#.###",
        DecimalFormatSymbols()
            .apply { decimalSeparator = '.' },
    )

    fun setChapters(items: List<ChapterItem>?) {
        this.items = items ?: emptyList()
        performFilter()
    }

    fun indexOf(item: ChapterItem): Int {
        return items.indexOf(item)
    }

    fun indexOf(chapterId: Long): Int {
        return currentItems.indexOfFirst { it is ChapterItem && it.id == chapterId }
    }

    fun performFilter() {
        val s = getFilter(String::class.java)
        if (s.isNullOrBlank()) {
            updateDataSet(items)
        } else {
            updateDataSet(
                items.filter { it.name.contains(s, true) },
            )
        }
    }

    override fun onItemSwiped(position: Int, direction: Int) {
        super.onItemSwiped(position, direction)
        when (direction) {
            ItemTouchHelper.RIGHT -> if (recyclerView.resources.isLTR) {
                delegate.bookmarkChapter(position)
            } else {
                delegate.toggleReadChapter(position)
            }
            ItemTouchHelper.LEFT -> if (recyclerView.resources.isLTR) {
                delegate.toggleReadChapter(position)
            } else {
                delegate.bookmarkChapter(position)
            }
        }
    }

    override fun onCreateBubbleText(position: Int): String {
        val chapter =
            getItem(position) as? ChapterItem ?: return recyclerView.context.getString(MR.strings.top)
        return when (val scrollType = presenter.scrollType) {
            MangaDetailsPresenter.MULTIPLE_VOLUMES, MangaDetailsPresenter.MULTIPLE_SEASONS -> {
                val volume = ChapterUtil.getGroupNumber(chapter)
                if (volume != null) {
                    recyclerView.context.getString(
                        if (scrollType == MangaDetailsPresenter.MULTIPLE_SEASONS) {
                            MR.strings.season_
                        } else {
                            MR.strings.volume_
                        },
                        volume,
                    )
                } else {
                    getChapterName(chapter)
                }
            }
            MangaDetailsPresenter.TENS_OF_CHAPTERS -> recyclerView.context.getString(
                MR.strings.chapters_,
                get10sRange(chapter.chapter_number),
            )
            else -> getChapterName(chapter)
        }
    }

    private fun getChapterName(item: ChapterItem): String {
        return if (item.chapter_number > 0) {
            recyclerView.context.getString(
                MR.strings.chapter_,
                decimalFormat.format(item.chapter_number),
            )
        } else {
            item.name
        }
    }

    private fun get10sRange(value: Float): String {
        val number = value.toInt()
        return if (number < 10) {
            "0-9"
        } else {
            val hundred = number / 10
            "${hundred}0-${hundred + 1}9"
        }
    }

    interface MangaDetailsInterface : MangaHeaderInterface, DownloadInterface

    interface MangaHeaderInterface {
        fun coverColor(): Int?
        fun accentColor(): Int?
        fun mangaPresenter(): MangaDetailsPresenter
        fun prepareToShareManga()
        fun openInWebView()
        fun startDownloadRange(position: Int)
        fun readNextChapter(readingButton: View)
        fun topCoverHeight(): Int
        fun showFloatingActionMode(view: TextView, content: String? = null, isTag: Boolean = false)
        fun showChapterFilter()
        fun favoriteManga(longPress: Boolean)
        fun copyContentToClipboard(content: String, label: StringResource, useToast: Boolean = false)
        fun copyContentToClipboard(content: String, label: Int, useToast: Boolean = false)
        fun customActionMode(view: TextView): ActionMode.Callback
        fun copyContentToClipboard(content: String, label: String?, useToast: Boolean = false)
        fun zoomImageFromThumb(thumbView: View)
        fun showTrackingSheet()
        fun updateScroll()
        fun setFavButtonPopup(popupView: View)
        
        /**
         * Enable or disable the SwipeRefreshLayout.
         * Used by MangaHeaderHolder to re-enable swipe after text selection.
         */
        fun setSwipeRefreshEnabled(enabled: Boolean)
        
        /**
         * Bookmark or unbookmark a chapter at the given position.
         * Called from swipe gesture handler.
         */
        fun bookmarkChapter(position: Int)
        
        /**
         * Toggle read/unread status of chapter at the given position.
         * Called from swipe gesture handler.
         */
        fun toggleReadChapter(position: Int)
    }
}
