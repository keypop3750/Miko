package eu.kanade.tachiyomi.ui.library

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HeartBroken
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.github.florent37.viewtooltip.ViewTooltip
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import dev.icerock.moko.resources.StringResource
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.items.IHeader
import eu.davidea.flexibleadapter.items.ISectionable
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.LibraryControllerBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.MiniSearchView
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.base.controller.FadeChangeHandler
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.category.ManageCategoryDialog
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_AUTHOR
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_DEFAULT
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_LANGUAGE
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_SOURCE
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_STATUS
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_TAG
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_TRACK_STATUS
import eu.kanade.tachiyomi.ui.library.LibraryGroup.UNGROUPED
import eu.kanade.tachiyomi.ui.library.display.TabbedLibraryDisplaySheet
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet
import eu.kanade.tachiyomi.ui.main.BottomSheetController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.manga.MangaDetailsActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.ui.novel.reader.NovelReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.moveCategories
import eu.kanade.tachiyomi.util.moveNovelCategories
import eu.kanade.tachiyomi.ui.source.BrowseController
import yokai.presentation.component.SearchBarContext
import yokai.presentation.library.filter.FilterGroup
import yokai.presentation.library.filter.FilterOption
import yokai.presentation.library.filter.FilterSheetScreen
import yokai.presentation.library.filter.FilterType
import yokai.presentation.library.filter.LibraryFilterSheet
import yokai.presentation.library.filter.LibraryFilterState
import yokai.presentation.library.displayoptions.DisplayOptionsSheet
import yokai.presentation.library.displayoptions.DisplayOptionsState
import yokai.presentation.library.displayoptions.GroupBySheet
import yokai.presentation.library.content.LibraryContent
import yokai.presentation.library.content.LibraryContentBridge
import yokai.presentation.library.content.LibraryContentItem
import yokai.presentation.library.content.LibraryContentUiState
import yokai.presentation.library.content.LibraryLayoutMode
import yokai.presentation.theme.YokaiTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.disableItems
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.getResourceDrawable
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isImeVisible
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.fullAppBarHeight
import eu.kanade.tachiyomi.util.view.getItemView
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.isHidden
import eu.kanade.tachiyomi.util.view.isSettling
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setAction
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.setTitle
import eu.kanade.tachiyomi.util.view.smoothScrollToTop
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.text
import eu.kanade.tachiyomi.util.view.ThemeTransitionHelper
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.EmptyView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager
import yokai.domain.ui.UiPreferences
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

open class LibraryController(
    bundle: Bundle? = null,
    val uiPreferences: UiPreferences = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
) : BaseCoroutineController<LibraryControllerBinding, LibraryPresenter>(bundle),
    ActionMode.Callback,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FlexibleAdapter.OnItemMoveListener,
    LibraryCategoryAdapter.LibraryListener,
    BottomSheetController,
    RootSearchInterface,
    FloatingSearchInterface {

    init {
        setHasOptionsMenu(true)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    /**
     * Position of the active category (mode-specific).
     */
    private var activeCategory: Int = when (ModeManager.currentMode.value) {
        ContentType.MANGA -> preferences.lastUsedMangaCategory().get()
        ContentType.NOVEL -> preferences.lastUsedNovelCategory().get()
    }
    private var lastUsedCategory: Int = when (ModeManager.currentMode.value) {
        ContentType.MANGA -> preferences.lastUsedMangaCategory().get()
        ContentType.NOVEL -> preferences.lastUsedNovelCategory().get()
    }

    private var justStarted = true

    /**
     * Action mode for selections.
     */
    private var actionMode: ActionMode? = null

    private var libraryLayout: Int = preferences.libraryLayout().get()

    var singleCategory: Boolean = false
        private set
    var hopperAnimation: ValueAnimator? = null
    var catGestureDetector: GestureDetector? = null

    /**
     * Library search query.
     */
    private var query = ""

    val isSubClass: Boolean
        get() = this is FilteredLibraryController

    /**
     * Currently selected mangas.
     */
    private val selectedMangas = mutableSetOf<Manga>()
    
    /**
     * Currently selected novels.
     */
    private val selectedNovels = mutableSetOf<yokai.domain.novel.Novel>()

    private var mAdapter: LibraryCategoryAdapter? = null
    private val adapter: LibraryCategoryAdapter
        get() = mAdapter!!

    private var lastClickPosition = -1

    private var lastItemPosition: Int? = null
    private var lastItem: IFlexible<*>? = null

    override var presenter = LibraryPresenter()

    private var observeLater: Boolean = false
    var searchItem = SearchGlobalItem()

    var snack: Snackbar? = null
    var displaySheet: TabbedLibraryDisplaySheet? = null

    // Compose filter state
    private val composeFilterState = LibraryFilterState()
    private var useComposeFilter = false // Disabled - using View-based filter
    private var isComposeFilterSheetVisible = false
    
    // Compose display options state
    private val displayOptionsState = DisplayOptionsState()
    private var showComposeDisplayOptions = androidx.compose.runtime.mutableStateOf(false)
    
    // Compose group by state
    private var showComposeGroupBy = androidx.compose.runtime.mutableStateOf(false)
    
    // Compose library content (Phase 3 migration)
    private val libraryContentBridge = yokai.presentation.library.content.LibraryContentBridge()
    private val useComposeLibraryContent: Boolean
        get() = preferences.useComposeLibraryContent().get()

    private var scrollDistance = 0f
    private val scrollDistanceTilHidden = 1000.dpToPx
    private var textAnim: ViewPropertyAnimator? = null
    private var hasExpanded = false

    val hasActiveFilters: Boolean
        get() = presenter.hasActiveFilters

    var hopperGravity: Int = preferences.hopperGravity().get()
        @SuppressLint("RtlHardcoded")
        set(value) {
            field = value
            binding.jumperCategoryText.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                anchorGravity = when (value) {
                    0 -> Gravity.RIGHT or Gravity.CENTER_VERTICAL
                    2 -> Gravity.LEFT or Gravity.CENTER_VERTICAL
                    else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
                gravity = anchorGravity
            }
        }

    private var filterTooltip: ViewTooltip? = null
    private var isAnimatingHopper: Boolean? = null
    private var animatorSet: AnimatorSet? = null
    var hasMovedHopper = preferences.shownHopperSwipeTutorial().get()
    private var shouldScrollToTop = false
    private val showCategoryInTitle
        get() = preferences.showCategoryInTitle().get() && presenter.showAllCategories
    private lateinit var elevateAppBar: ((Boolean) -> Unit)
    private var hopperOffset = 0f
    private val maxHopperOffset: Float
        get() = if (activityBinding?.bottomNav != null && !isSubClass) {
            55f.dpToPx
        } else {
            (
                view?.rootWindowInsetsCompat?.getInsets(systemBars())?.bottom?.toFloat()
                    ?: 0f
                ) + 55f.dpToPx
        }

    override val mainRecycler: RecyclerView
        get() = binding.libraryGridRecycler.recycler
    private var staggeredBundle: Parcelable? = null
    private var staggeredObserver: ViewTreeObserver.OnGlobalLayoutListener? = null
    var isPoppingIn = false
    var tempItems: List<LibraryItem>? = null

    // Dynamically injected into the search bar, controls category visibility during search
    private var showAllCategoriesView: ImageView? = null
    override fun getTitle(): String? {
        setSubtitle()
        return view?.context?.getString(MR.strings.library)
    }

    override fun getSearchTitle(): String? {
        setSubtitle()
        return searchTitle(
            if (preferences.showLibrarySearchSuggestions().get() &&
                preferences.librarySearchSuggestion().get().isNotBlank()
            ) {
                "\"${preferences.librarySearchSuggestion().get()}\""
            } else {
                view?.context?.getString(MR.strings.your_library)?.lowercase(Locale.ROOT)
            },
        )
    }

    val cb = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
        override fun onStart(
            animation: WindowInsetsAnimationCompat,
            bounds: WindowInsetsAnimationCompat.BoundsCompat,
        ): WindowInsetsAnimationCompat.BoundsCompat {
            hopperOffset = 0f
            updateHopperY()
            return bounds
        }

        override fun onProgress(
            insets: WindowInsetsCompat,
            runningAnimations: List<WindowInsetsAnimationCompat>,
        ): WindowInsetsCompat {
            updateHopperY(insets)
            return insets
        }

        override fun onEnd(animation: WindowInsetsAnimationCompat) {
            updateHopperY()
        }
    }

    private var scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val recyclerCover = binding.recyclerCover
            if (!recyclerCover.isClickable && isAnimatingHopper != true) {
                if (preferences.autohideHopper().get()) {
                    hopperOffset += dy
                    hopperOffset = hopperOffset.coerceIn(0f, maxHopperOffset)
                }
                if (!preferences.hideBottomNavOnScroll().get() || activityBinding?.bottomNav == null ||
                    isSubClass
                ) {
                    updateFilterSheetY()
                }
                if (!binding.fastScroller.isFastScrolling) {
                    updateSmallerViewsTopMargins()
                }
                updateHopperAlpha()
            }
            if (!useComposeFilter && !binding.filterBottomSheet.filterBottomSheet.sheetBehavior.isHidden()) {
                scrollDistance += abs(dy)
                if (scrollDistance > scrollDistanceTilHidden) {
                    binding.filterBottomSheet.filterBottomSheet.sheetBehavior?.hide()
                    scrollDistance = 0f
                }
            } else {
                scrollDistance = 0f
            }
            val currentCategory = getHeader()?.category ?: return
            if (currentCategory.order != activeCategory) {
                saveActiveCategory(currentCategory)
                if (!showCategoryInTitle && presenter.categories.size > 1 && dy != 0 && recyclerView.translationY == 0f) {
                    showCategoryText(currentCategory.name)
                }
            }
            val savedCurrentCategory = getHeader(true)?.category ?: return
            if (savedCurrentCategory.order != lastUsedCategory) {
                lastUsedCategory = savedCurrentCategory.order
                if (!isSubClass) {
                    // Save to mode-specific preference
                    val currentMode = ModeManager.currentMode.value
                    when (currentMode) {
                        ContentType.MANGA -> preferences.lastUsedMangaCategory().set(savedCurrentCategory.order)
                        ContentType.NOVEL -> preferences.lastUsedNovelCategory().set(savedCurrentCategory.order)
                    }
                    preferences.lastUsedCategory().set(savedCurrentCategory.order)
                }
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> {
                    binding.fastScroller.showScrollbar()
                }
                RecyclerView.SCROLL_STATE_IDLE -> {
                    updateHopperPosition()
                }
            }
            if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                removeStaggeredObserver()
            }
        }
    }

    fun updateHopperAlpha() {
        binding.roundedCategoryHopper.upCategory.alpha = if (isAtTop()) 0.25f else 1f
        binding.roundedCategoryHopper.downCategory.alpha = if (isAtBottom()) 0.25f else 1f
    }

    private fun removeStaggeredObserver() {
        if (staggeredObserver != null) {
            binding.libraryGridRecycler.recycler.viewTreeObserver.removeOnGlobalLayoutListener(
                staggeredObserver,
            )
            staggeredObserver = null
        }
    }

    fun updateFilterSheetY() {
        // Skip if using Compose filter
        if (useComposeFilter) {
            updateHopperY()
            return
        }
        
        val bottomBar = if (!isSubClass) activityBinding?.bottomNav else null
        val systemInsets = view?.rootWindowInsetsCompat?.getInsets(systemBars())
        val bottomSheet = binding.filterBottomSheet.filterBottomSheet
        if (bottomBar != null) {
            bottomSheet.translationY = if (bottomSheet.sheetBehavior.isHidden()) {
                bottomBar.translationY - bottomBar.height
            } else {
                0f
            }
            val pad = bottomBar.translationY - bottomBar.height
            val padding = max((-pad).toInt(), systemInsets?.bottom ?: 0)
            bottomSheet.updatePaddingRelative(bottom = padding)

            bottomSheet.sheetBehavior?.peekHeight = 60.dpToPx + padding
            updateHopperY()
            binding.fastScroller.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = -pad.toInt()
            }
        } else {
            bottomSheet.updatePaddingRelative(bottom = systemInsets?.bottom ?: 0)
            updateHopperY()
            bottomSheet.sheetBehavior?.peekHeight = 60.dpToPx + (systemInsets?.bottom ?: 0)
        }
    }

    fun updateHopperPosition() {
        val shortAnimationDuration = resources?.getInteger(
            AR.integer.config_shortAnimTime,
        ) ?: 0
        if (preferences.autohideHopper().get()) {
            val bottomBar = if (isSubClass) null else activityBinding?.bottomNav
            // Flow same snap rules as bottom nav
            val closerToHopperBottom = hopperOffset > maxHopperOffset / 2
            val halfWayBottom = bottomBar?.height?.toFloat()?.div(2) ?: 0f
            val closerToBottom = (bottomBar?.translationY ?: 0f) > halfWayBottom
            val atTop = !binding.libraryGridRecycler.recycler.canScrollVertically(-1)
            val closerToEdge =
                if (preferences.hideBottomNavOnScroll().get() && bottomBar != null) {
                    closerToBottom && !atTop
                } else {
                    closerToHopperBottom
                }
            val end = if (closerToEdge) maxHopperOffset else 0f
            hopperAnimation?.cancel()
            val alphaAnimation = ValueAnimator.ofFloat(hopperOffset, end)
            alphaAnimation.addUpdateListener { valueAnimator ->
                hopperOffset = valueAnimator.animatedValue as Float
                updateHopperY()
            }
            alphaAnimation.doOnEnd {
                hopperOffset = end
                updateHopperY()
            }
            alphaAnimation.duration = shortAnimationDuration.toLong()
            hopperAnimation = alphaAnimation
            alphaAnimation.start()
        }
    }

    fun saveActiveCategory(category: Category) {
        activeCategory = category.order
        val headerItem = getHeader() ?: return
        binding.headerTitle.text = headerItem.category.name
        setActiveCategory()
    }

    private fun setActiveCategory() {
        val currentCategory = presenter.categories.indexOfFirst {
            if (presenter.showAllCategories) it.order == activeCategory else presenter.currentCategoryId == it.id
        }
        if (currentCategory > -1) {
            binding.categoryRecycler.setCategories(currentCategory)
            binding.headerTitle.text = presenter.categories[currentCategory].name
            setSubtitle()
            // Update Compose bridge with current category index and ID
            libraryContentBridge.setCurrentCategoryIndex(currentCategory)
            libraryContentBridge.setCurrentCategoryId(presenter.categories[currentCategory].id)
        }
    }

    fun showMiniBar() {
        binding.headerCard.isVisible = showCategoryInTitle
        setSubtitle()
    }

    private fun setSubtitle() {
        if (isBindingInitialized && !singleCategory && presenter.showAllCategories &&
            !binding.headerTitle.text.isNullOrBlank() && !binding.recyclerCover.isClickable &&
            isControllerVisible
        ) {
            activityBinding?.searchToolbar?.subtitle = binding.headerTitle.text.toString()
        } else {
            activityBinding?.searchToolbar?.subtitle = null
        }
    }

    fun showCategoryText(name: String) {
        textAnim?.cancel()
        binding.jumperCategoryText.alpha = 1f
        binding.jumperCategoryText.text = name
        textAnim = binding.jumperCategoryText.animate().alpha(0f).setDuration(250L).setStartDelay(
            2000,
        )
        textAnim?.start()
    }

    fun isAtTop(): Boolean {
        return if (presenter.showAllCategories) {
            !binding.libraryGridRecycler.recycler.canScrollVertically(-1)
        } else {
            getVisibleHeader()?.category?.order == presenter.categories.minOfOrNull { it.order }
        }
    }

    fun isAtBottom(): Boolean {
        return if (presenter.showAllCategories) {
            !binding.libraryGridRecycler.recycler.canScrollVertically(1)
        } else {
            getVisibleHeader()?.category?.order == presenter.categories.maxOfOrNull { it.order }
        }
    }

    private fun showFilterTip() {
        if (preferences.shownFilterTutorial().get() || !hasExpanded) return
        if (filterTooltip != null) return
        val activityBinding = activityBinding ?: return
        val activity = activity ?: return
        val icon = (activityBinding.bottomNav ?: activityBinding.sideNav)?.getItemView(R.id.nav_library) ?: return
        filterTooltip =
            ViewTooltip.on(activity, icon).autoHide(false, 0L).align(ViewTooltip.ALIGN.START)
                .position(ViewTooltip.Position.TOP)
                .text(MR.strings.tap_library_to_show_filters)
                .textColor(activity.getResourceColor(R.attr.colorOnSecondary))
                .color(activity.getResourceColor(R.attr.colorSecondary))
                .textSize(TypedValue.COMPLEX_UNIT_SP, 15f).withShadow(false)
                .corner(30).arrowWidth(15).arrowHeight(15).distanceWithView(0)

        filterTooltip?.show()
    }

    private fun openRandomManga(global: Boolean) {
        val items =
            if (global) { presenter.currentLibraryItems } else { adapter.currentItems }
                .filterIsInstance<LibraryMangaItem>()
                .filter { !it.manga.manga.initialized || it.manga.unread > 0 }
        if (items.isNotEmpty()) {
            val item = items.random() as LibraryMangaItem
            openManga(item.manga.manga)
        }
    }

    internal fun showGroupOptions() {
        if (useComposeFilter) {
            showComposeGroupBy.value = true
        } else {
            val groupItems = mutableListOf(BY_DEFAULT, BY_TAG, BY_SOURCE, BY_STATUS, BY_AUTHOR)
            if (presenter.isLoggedIntoTracking) {
                groupItems.add(BY_TRACK_STATUS)
            }
            groupItems.add(BY_LANGUAGE)
            if (presenter.isCategoryMoreThanOne()) {
                groupItems.add(UNGROUPED)
            }
            val items = groupItems.map { id ->
                MaterialMenuSheet.MenuSheetItem(
                    id,
                    LibraryGroup.groupTypeDrawableRes(id),
                    LibraryGroup.groupTypeStringRes(id, presenter.isCategoryMoreThanOne()),
                )
            }
            MaterialMenuSheet(
                activity!!,
                items,
                activity!!.getString(MR.strings.group_library_by),
                presenter.groupType,
            ) { _, item ->
                if (!isSubClass) {
                    preferences.groupLibraryBy().set(item)
                }
                presenter.groupType = item
                shouldScrollToTop = true
                presenter.updateLibrary()
                true
            }.show()
        }
    }

    internal fun showDisplayOptions() {
        if (useComposeFilter) {
            // Close filter menu when display options open
            if (isComposeFilterSheetVisible) {
                hideComposeFilterSheet()
            }
            showComposeDisplayOptions.value = true
            // Hopper stays in place - display options sheet goes on top
        } else {
            if (displaySheet == null) {
                displaySheet = TabbedLibraryDisplaySheet(this)
                displaySheet?.show()
            }
        }
    }

    internal fun closeTip() {
        if (filterTooltip != null) {
            filterTooltip?.close()
            filterTooltip = null
            if (!isSubClass) {
                preferences.shownFilterTutorial().set(true)
            }
        }
    }

    override fun createBinding(inflater: LayoutInflater) = LibraryControllerBinding.inflate(inflater)

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        mAdapter = LibraryCategoryAdapter(this)
        adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        setRecyclerLayout()
        binding.libraryGridRecycler.recycler.setHasFixedSize(true)
        binding.libraryGridRecycler.recycler.adapter = adapter

        adapter.fastScroller = binding.fastScroller
        binding.fastScroller.controller = this
        binding.libraryGridRecycler.recycler.addOnScrollListener(scrollListener)

        binding.swipeRefresh.setStyle()

        binding.recyclerCover.setOnClickListener {
            showCategories(false)
        }
        binding.categoryRecycler.onCategoryClicked = {
            showCategories(show = false, closeSearch = true, category = it)
            scrollToHeader(it)
        }
        binding.categoryRecycler.setOnTouchListener { _, _ ->
            val searchView = activityBinding?.searchToolbar?.menu?.findItem(R.id.action_search)?.actionView
                ?: return@setOnTouchListener false
            val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm!!.hideSoftInputFromWindow(searchView.windowToken, 0)
            false
        }
        setupFilterSheet()
        setupComposeLibraryContent()
        setUpHopper()
        setPreferenceFlows()
        LibraryUpdateJob.updateFlow.onEach(::onUpdateManga).launchIn(viewScope)
        viewScope.launchUI {
            LibraryUpdateJob.isRunningFlow(view.context).collect {
                adapter.getHeaderPositions().forEach {
                    val holder = (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(it) as? LibraryHeaderHolder) ?: return@forEach
                    val category = holder.category ?: return@forEach
                    holder.notifyStatus(LibraryUpdateJob.categoryInQueue(category.id), category)
                }
            }
        }

        elevateAppBar =
            scrollViewWith(
                binding.libraryGridRecycler.recycler,
                swipeRefreshLayout = binding.swipeRefresh,
                ignoreInsetVisibility = true,
                afterInsets = { insets ->
                    val systemInsets = insets.ignoredSystemInsets
                    binding.categoryRecycler.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin = systemInsets.top + (activityBinding?.searchToolbar?.height ?: 0) + 12.dpToPx
                    }
                    updateSmallerViewsTopMargins()
                    binding.headerCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin = systemInsets.top + 4.dpToPx
                    }
                    updateFilterSheetY()
                },
                onLeavingController = {
                    binding.headerCard.isVisible = false
                },
                onBottomNavUpdate = {
                    updateFilterSheetY()
                },
            )

        viewScope.launchUI {
            delay(50)
            updateHopperY()
        }
        setSwipeRefresh()

        ViewCompat.setWindowInsetsAnimationCallback(view, cb)

        if (selectedMangas.isNotEmpty()) {
            createActionModeIfNeeded()
        }

        if (presenter.libraryItemsToDisplay.isNotEmpty() && !isSubClass) {
            presenter.restoreLibrary()
            if (justStarted) {
                val activityBinding = activityBinding ?: return
                val bigToolbarHeight = fullAppBarHeight ?: return
                if (lastUsedCategory > 0) {
                    activityBinding.appBar.y =
                        -bigToolbarHeight + activityBinding.cardFrame.height.toFloat()
                    activityBinding.appBar.useSearchToolbarForMenu(true)
                }
                activityBinding.appBar.lockYPos = true
            }
        } else {
            binding.recyclerLayout.alpha = 0f
        }
        
        // Enable the unified Compose search bar for Library
        setupUnifiedSearchBar()
    }
    
    /**
     * Setup the unified Compose search bar for Library.
     * This replaces the old View-based FloatingToolbar when the Library is displayed.
     */
    private fun setupUnifiedSearchBar() {
        val libraryTitle = view?.context?.getString(MR.strings.library) ?: "Library"
        (activity as? MainActivity)?.let { mainActivity ->
            mainActivity.enableUnifiedSearchBar(
                context = SearchBarContext.LIBRARY,
                title = libraryTitle,
                subtitle = null
            )
            // Set up callbacks for the unified search bar
            mainActivity.unifiedSearchBarState.apply {
                onSearchQueryChange = { query -> search(query) }
                onSearchClose = { search("") }
                onFilterClick = { 
                    if (useComposeFilter) {
                        // Filter button behavior: first click = expanded, second click = display options
                        handleFilterButtonClick()
                    } else {
                        // Toggle old filter sheet behavior
                        val filterSheet = binding.filterBottomSheet.filterBottomSheet
                        if (filterSheet.sheetBehavior.isHidden()) {
                            filterSheet.sheetBehavior?.expand()
                        } else {
                            // Show display options on second click
                            showDisplayOptions()
                        }
                    }
                }
                onModeToggle = { 
                    // Mode toggle handled by the search bar itself
                }
                onMenuClick = { 
                    // Menu click handled by the search bar itself (calls showOverflowMenu)
                }
                setVisible(true)
            }
        }
    }

    private fun updateSmallerViewsTopMargins() {
        val activityBinding = activityBinding ?: return
        val bigToolbarHeight = fullAppBarHeight ?: return
        val value = max(
            0,
            bigToolbarHeight + activityBinding.appBar.y.roundToInt(),
        ) + activityBinding.appBar.paddingTop
        if (value != binding.fastScroller.marginTop) {
            binding.fastScroller.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = value
            }
            binding.emptyView.updatePadding(
                top = bigToolbarHeight + activityBinding.appBar.paddingTop,
                bottom = binding.libraryGridRecycler.recycler.paddingBottom,
            )
            binding.progress.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = (bigToolbarHeight + activityBinding.appBar.paddingTop) / 2
            }
        }
    }

    private fun setSwipeRefresh() = with(binding.swipeRefresh) {
        setOnRefreshListener {
            isRefreshing = false
            if (!LibraryUpdateJob.isRunning(context)) {
                when {
                    !presenter.showAllCategories && presenter.groupType == BY_DEFAULT -> {
                        presenter.currentCategory?.let {
                            updateLibrary(it)
                        }
                    }
                    !presenter.showAllCategories -> updateCategory(0)
                    else -> updateLibrary()
                }
            }
        }
    }

    private fun setupFilterSheet() {
        if (useComposeFilter) {
            setupComposeFilterSheet()
            // Hide the old View-based filter sheet
            binding.filterBottomSheet.filterBottomSheet.isGone = true
        } else {
            // Use old View-based filter sheet
            binding.filterBottomSheet.filterBottomSheet.onCreate(this)

            binding.filterBottomSheet.filterBottomSheet.onGroupClicked = {
                when (it) {
                    FilterBottomSheet.ACTION_REFRESH -> onRefresh()
                    FilterBottomSheet.ACTION_FILTER -> onFilterChanged()
                    FilterBottomSheet.ACTION_HIDE_FILTER_TIP -> showFilterTip()
                    FilterBottomSheet.ACTION_DISPLAY -> showDisplayOptions()
                    FilterBottomSheet.ACTION_EXPAND_COLLAPSE_ALL -> presenter.toggleAllCategoryVisibility()
                    FilterBottomSheet.ACTION_GROUP_BY -> showGroupOptions()
                }
            }
        }
    }

    private fun setupComposeFilterSheet() {
        // DISABLED - Compose filter sheet removed from layout
        // This function is never called when useComposeFilter = false
        return
    }
    
    /**
     * Setup the Compose library content view.
     * This provides a hybrid approach where the Compose content can be enabled/disabled.
     */
    private fun setupComposeLibraryContent() {
        if (!useComposeLibraryContent) return
        
        binding.composeView.apply {
            isVisible = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YokaiTheme {
                    val uiState by libraryContentBridge.uiState.collectAsState()
                    val selectedItems by libraryContentBridge.selectedItems.collectAsState()
                    val expandedCategories by libraryContentBridge.expandedCategories.collectAsState()
                    
                    // Observe reactive display settings
                    val layoutMode by libraryContentBridge.layoutMode.collectAsState()
                    val gridColumns by libraryContentBridge.gridColumns.collectAsState()
                    val showCategoryHeaders by libraryContentBridge.showCategoryHeaders.collectAsState()
                    val showUnreadBadge by libraryContentBridge.showUnreadBadge.collectAsState()
                    val showDownloadBadge by libraryContentBridge.showDownloadBadge.collectAsState()
                    val showLanguageBadge by libraryContentBridge.showLanguageBadge.collectAsState()
                    val showContinueButton by libraryContentBridge.showContinueButton.collectAsState()
                    val showOutline by libraryContentBridge.showOutline.collectAsState()
                    val unreadBadgeType by libraryContentBridge.unreadBadgeType.collectAsState()
                    val currentCategoryIndex by libraryContentBridge.currentCategoryIndex.collectAsState()
                    val currentCategoryId by libraryContentBridge.currentCategoryId.collectAsState()
                    val showNumberOfItems by libraryContentBridge.showNumberOfItems.collectAsState()
                    
                    // Calculate top padding for content to not go behind the app bar
                    // The search bar/app bar height is approximately 56dp + status bar
                    val topPaddingDp = 64 // actionBarSize (56dp) + some extra padding
                    
                    LibraryContent(
                        state = uiState,
                        layoutMode = layoutMode,
                        gridColumns = gridColumns,
                        showCategoryHeaders = showCategoryHeaders,
                        expandedCategories = expandedCategories,
                        selectedItems = selectedItems,
                        showUnreadBadge = showUnreadBadge,
                        showDownloadBadge = showDownloadBadge,
                        showLanguageBadge = showLanguageBadge,
                        showContinueButton = showContinueButton,
                        showOutline = showOutline,
                        unreadBadgeType = unreadBadgeType,
                        currentCategoryIndex = currentCategoryIndex,
                        currentCategoryId = currentCategoryId,
                        showNumberOfItems = showNumberOfItems,
                        onItemClick = { item -> handleComposeItemClick(item) },
                        onItemLongClick = { item -> handleComposeItemLongClick(item) },
                        onContinueClick = { item -> handleComposeContinueClick(item) },
                        onCategoryClick = { category -> handleComposeCategoryClick(category) },
                        onCategoryExpandClick = { category -> 
                            libraryContentBridge.toggleCategoryExpansion(category.id ?: 0)
                        },
                        hasActiveFilters = hasActiveFilters,
                        onGettingStartedClick = {
                            activity?.openInBrowser("https://tachiyomi.org/docs/guides/getting-started#_2-adding-sources")
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = topPaddingDp.dp,
                            bottom = 80.dp, // Bottom nav + FAB space
                            start = 4.dp,
                            end = 4.dp,
                        ),
                    )
                }
            }
        }
        
        // Hide all old View-based content when using Compose
        binding.swipeRefresh.isVisible = false
        binding.emptyView.isVisible = false
    }
    
    /**
     * Handle item click from Compose library content.
     * In selection mode, syncs with selectedMangas set for action mode operations.
     */
    private fun handleComposeItemClick(item: LibraryContentItem) {
        if (libraryContentBridge.isInSelectionMode) {
            libraryContentBridge.toggleSelection(item.id)
            // Sync with selectedMangas set for action mode operations
            syncComposeSelectionToSelectedMangas(item)
            if (!libraryContentBridge.isInSelectionMode) {
                destroyActionModeIfNeeded()
            }
        } else {
            when (item) {
                is LibraryContentItem.MangaItem -> {
                    // Find the corresponding LibraryManga and open it
                    val libraryItem = presenter.currentLibraryItems
                        .filterIsInstance<LibraryMangaItem>()
                        .find { it.manga.manga.id == item.id }
                    libraryItem?.let { openManga(it.manga.manga) }
                }
                is LibraryContentItem.NovelItem -> {
                    // Open novel details using the proper novel details controller
                    val libraryItem = presenter.currentLibraryItems
                        .filterIsInstance<LibraryNovelItem>()
                        .find { it.novel.id == item.id }
                    libraryItem?.let { openNovel(it.novel) }
                }
                is LibraryContentItem.Placeholder -> { /* No-op */ }
            }
        }
    }
    
    /**
     * Sync Compose selection state with the View-based selectedMangas/selectedNovels sets.
     * This is needed for action mode operations like move to category.
     */
    private fun syncComposeSelectionToSelectedMangas(item: LibraryContentItem) {
        when (item) {
            is LibraryContentItem.MangaItem -> {
                val libraryItem = presenter.currentLibraryItems
                    .filterIsInstance<LibraryMangaItem>()
                    .find { it.manga.manga.id == item.id }
                libraryItem?.let { 
                    val manga = it.manga.manga
                    if (libraryContentBridge.selectedItems.value.contains(item.id)) {
                        selectedMangas.add(manga)
                    } else {
                        selectedMangas.remove(manga)
                    }
                }
            }
            is LibraryContentItem.NovelItem -> {
                val libraryItem = presenter.currentLibraryItems
                    .filterIsInstance<LibraryNovelItem>()
                    .find { it.novel.id == item.id }
                libraryItem?.let { 
                    val novel = it.novel
                    if (libraryContentBridge.selectedItems.value.contains(item.id)) {
                        selectedNovels.add(novel)
                    } else {
                        selectedNovels.remove(novel)
                    }
                }
            }
            is LibraryContentItem.Placeholder -> { /* No-op */ }
        }
    }
    
    /**
     * Handle item long click from Compose library content.
     * Syncs Compose selection state with the View-based selectedMangas set
     * which is used by the action mode for operations like move to category.
     */
    private fun handleComposeItemLongClick(item: LibraryContentItem) {
        libraryContentBridge.toggleSelection(item.id)
        
        // Sync with selectedMangas set for action mode operations
        syncComposeSelectionToSelectedMangas(item)
        
        if (libraryContentBridge.isInSelectionMode) {
            createActionModeIfNeeded()
        } else {
            destroyActionModeIfNeeded()
        }
    }
    
    /**
     * Handle continue/play click from Compose library content
     */
    private fun handleComposeContinueClick(item: LibraryContentItem) {
        val activity = activity ?: return
        when (item) {
            is LibraryContentItem.MangaItem -> {
                val libraryItem = presenter.currentLibraryItems
                    .filterIsInstance<LibraryMangaItem>()
                    .find { it.manga.manga.id == item.id }
                libraryItem?.let { 
                    val manga = it.manga.manga
                    val chapter = presenter.getFirstUnread(manga) ?: return@let
                    startActivity(ReaderActivity.newIntent(activity, manga, chapter))
                }
            }
            is LibraryContentItem.NovelItem -> {
                // Open novel reader at the last read position
                val intent = NovelReaderActivity.newIntent(activity, item.id)
                startActivity(intent)
            }
            is LibraryContentItem.Placeholder -> { /* No-op */ }
        }
    }
    
    /**
     * Handle category header click from Compose library content
     */
    private fun handleComposeCategoryClick(category: Category) {
        // Scroll to category or handle category-specific actions
        scrollToHeader(category.order)
    }
    
    /**
     * Handles filter button click in search bar:
     * First click: Show filter sheet expanded
     * Second click: Show display options
     */
    private fun handleFilterButtonClick() {
        if (!isComposeFilterSheetVisible) {
            // First click: Show filter sheet fully expanded
            composeFilterState.expand()
            showComposeFilterSheet()
        } else {
            // Second click: Show display options (and keep filter sheet visible)
            showDisplayOptions()
        }
    }
    
    /**
     * Cycles through filter sheet states for nav icon tap:
     * collapsed → expanded → hidden (3 taps total to close)
     */
    private fun cycleComposeFilterSheetState() {
        if (!isComposeFilterSheetVisible) {
            // State: Hidden → Collapsed (show filter row only)
            composeFilterState.collapse()
            showComposeFilterSheet()
        } else if (!composeFilterState.isExpanded()) {
            // State: Collapsed → Expanded (show full sheet)
            composeFilterState.expand()
        } else {
            // State: Expanded → Hidden (close the filter sheet)
            hideComposeFilterSheet()
        }
    }
    
    private fun toggleComposeFilterSheet() {
        isComposeFilterSheetVisible = !isComposeFilterSheetVisible
        if (isComposeFilterSheetVisible) {
            showComposeFilterSheetWithAnimation()
        } else {
            hideComposeFilterSheetWithAnimation()
        }
    }
    
    private fun showComposeFilterSheet() {
        // DISABLED - Compose filter sheet removed
        return
    }
    
    private fun hideComposeFilterSheet() {
        // DISABLED - Compose filter sheet removed
        return
    }
    
    private fun showComposeFilterSheetWithAnimation() {
        // DISABLED - Compose filter sheet removed
        return
    }
    
    private fun hideComposeFilterSheetWithAnimation() {
        // DISABLED - Compose filter sheet removed
        return
    }
    
    /**
     * Animate the hopper when the Compose filter sheet shows/hides.
     * @param showing true if filter sheet is appearing, false if disappearing
     * @param filterSheetHeight the height of the filter sheet
     */
    private fun animateHopperForComposeFilterSheet(showing: Boolean, filterSheetHeight: Float) {
        val currentY = binding.categoryHopperFrame.y
        val targetY = if (showing) {
            // Move hopper up by the filter sheet height
            currentY - filterSheetHeight
        } else {
            // Move hopper back down
            currentY + filterSheetHeight
        }
        
        binding.categoryHopperFrame.animate()
            .y(targetY)
            .setDuration(200)
            .start()
    }
    
    /**
     * Update hopper Y position to account for Compose filter sheet visibility.
     */
    private fun updateHopperForComposeFilterSheet(visible: Boolean) {
        // This is called after animation completes to ensure final position is correct
        updateHopperY()
    }
    
    private fun updateComposeFilterGroups() {
        val groups = FilterType.entries.map { type ->
            FilterGroup(
                id = type,
                name = activity?.getString(type.stringRes) ?: type.name,
                options = getFilterOptionsForType(type),
            )
        }
        composeFilterState.setFilterGroups(groups)
        composeFilterState.setGroupBy(presenter.groupType)
        
        // Load filter order from preferences
        val savedFilterOrder = preferences.filterOrder().get()
        composeFilterState.setFilterOrder(savedFilterOrder)
        
        // Also update active filters from current preference values
        val activeFilters = mutableMapOf<FilterType, Int>()
        val unread = preferences.filterUnread().get()
        if (unread != 0) activeFilters[FilterType.Unread] = unread
        val downloaded = preferences.filterDownloaded().get()
        if (downloaded != 0) activeFilters[FilterType.Downloaded] = downloaded
        val completed = preferences.filterCompleted().get()
        if (completed != 0) activeFilters[FilterType.Status] = completed
        val tracked = preferences.filterTracked().get()
        if (tracked != 0) activeFilters[FilterType.Tracking] = tracked
        val mangaType = preferences.filterMangaType().get()
        if (mangaType != 0) activeFilters[FilterType.SeriesType] = mangaType
        val contentType = preferences.filterContentType().get()
        if (contentType != 0) activeFilters[FilterType.ContentType] = contentType
        val bookmarked = preferences.filterBookmarked().get()
        if (bookmarked != 0) activeFilters[FilterType.Bookmarked] = bookmarked
        
        // Update state with active filters
        activeFilters.forEach { (type, value) ->
            composeFilterState.setActiveFilter(type, value)
        }
    }
    
    private fun getFilterOptionsForType(type: FilterType): List<FilterOption> {
        val context = activity ?: return emptyList()
        return when (type) {
            FilterType.ReadProgress -> listOf(
                FilterOption(context.getString(MR.strings.all)),
                FilterOption(context.getString(MR.strings.not_started)),
                FilterOption(context.getString(MR.strings.in_progress)),
            )
            FilterType.Unread -> listOf(
                FilterOption(context.getString(MR.strings.all)),
                FilterOption(context.getString(MR.strings.unread)),
                FilterOption(context.getString(MR.strings.read)),
            )
            FilterType.Downloaded -> listOf(
                FilterOption(context.getString(MR.strings.all)),
                FilterOption(context.getString(MR.strings.downloaded)),
                FilterOption(context.getString(MR.strings.not_downloaded)),
            )
            FilterType.Status -> listOf(
                FilterOption(context.getString(MR.strings.all)),
                FilterOption(context.getString(MR.strings.completed)),
                FilterOption(context.getString(MR.strings.ongoing)),
            )
            FilterType.SeriesType -> listOf(
                FilterOption(context.getString(MR.strings.all)),
                FilterOption(context.getString(MR.strings.manga)),
                FilterOption(context.getString(MR.strings.manhwa)),
                FilterOption(context.getString(MR.strings.manhua)),
                FilterOption(context.getString(MR.strings.comic)),
            )
            FilterType.Bookmarked -> listOf(
                FilterOption(context.getString(MR.strings.all)),
                FilterOption(context.getString(MR.strings.bookmarked)),
                FilterOption(context.getString(MR.strings.not_bookmarked)),
            )
            FilterType.Tracking -> listOf(
                FilterOption(context.getString(MR.strings.all)),
                FilterOption(context.getString(MR.strings.tracked)),
                FilterOption(context.getString(MR.strings.not_tracked)),
            )
            FilterType.ContentType -> listOf(
                FilterOption(context.getString(MR.strings.all)),
                FilterOption(context.getString(MR.strings.sfw)),
                FilterOption(context.getString(MR.strings.nsfw)),
            )
        }
    }
    
    private fun applyComposeFilter(type: FilterType, value: Int) {
        // Apply filter based on type and value using preferences
        // value 0 = All (no filter), 1+ = specific filter option
        when (type) {
            FilterType.ReadProgress -> {
                // This is handled specially - maps to filterUnread preference
                // Not directly supported in the same way, skip for now
            }
            FilterType.Unread -> {
                // 0=All, 1=Unread, 2=Read
                preferences.filterUnread().set(value)
            }
            FilterType.Downloaded -> {
                // 0=All, 1=Downloaded, 2=Not downloaded
                preferences.filterDownloaded().set(value)
            }
            FilterType.Status -> {
                // 0=All, 1=Ongoing, 2=Completed, etc.
                preferences.filterCompleted().set(value)
            }
            FilterType.SeriesType -> {
                // 0=All, 1=Manga, 2=Manhwa, etc.
                preferences.filterMangaType().set(value)
            }
            FilterType.Bookmarked -> {
                // 0=All, 1=Bookmarked, 2=Not bookmarked
                preferences.filterBookmarked().set(value)
            }
            FilterType.Tracking -> {
                // 0=All, 1=Tracked, 2=Not tracked
                preferences.filterTracked().set(value)
            }
            FilterType.ContentType -> {
                // 0=All, 1=Manga, 2=Novel
                preferences.filterContentType().set(value)
            }
        }
        onFilterChanged()
    }

    @SuppressLint("RtlHardcoded", "ClickableViewAccessibility")
    private fun setUpHopper() {
        binding.categoryHopperFrame.isVisible = false
        binding.roundedCategoryHopper.downCategory.setOnClickListener {
            jumpToNextCategory(true)
        }
        binding.roundedCategoryHopper.upCategory.setOnClickListener {
            jumpToNextCategory(false)
        }
        binding.roundedCategoryHopper.downCategory.setOnLongClickListener {
            binding.libraryGridRecycler.recycler.scrollToPosition(adapter.itemCount - 1)
            true
        }
        binding.roundedCategoryHopper.upCategory.setOnLongClickListener {
            binding.libraryGridRecycler.recycler.smoothScrollToTop()
            true
        }
        binding.roundedCategoryHopper.categoryButton.setOnClickListener {
            val items = presenter.categories.map { category ->
                MaterialMenuSheet.MenuSheetItem(
                    category.order,
                    text = category.name +
                        if (adapter.showNumber && adapter.itemsPerCategory[category.id] != null) {
                            " (${adapter.itemsPerCategory[category.id]})"
                        } else {
                            ""
                        },
                )
            }
            if (items.isEmpty()) return@setOnClickListener
            MaterialMenuSheet(
                activity!!,
                items,
                it.context.getString(MR.strings.jump_to_category),
                activeCategory,
                300.dpToPx,
            ) { _, item ->
                scrollToHeader(item)
                true
            }.show()
        }
        catGestureDetector = GestureDetector(binding.root.context, LibraryCategoryGestureDetector(this))

        binding.roundedCategoryHopper.categoryButton.setOnLongClickListener {
            when (preferences.hopperLongPressAction().get()) {
                5 -> openRandomManga(true)
                4 -> openRandomManga(false)
                3 -> showGroupOptions()
                2 -> showDisplayOptions()
                1 -> if (canCollapseOrExpandCategory() != null) presenter.toggleAllCategoryVisibility()
                else -> if (!isSubClass) {
                    activityBinding?.searchToolbar?.menu?.performIdentifierAction(
                        R.id.action_search,
                        0,
                    )
                }
            }
            true
        }

        // Always use saved gravity preference (default is 1=CENTER)
        // This removes the random positioning on first use, keeping the hopper in center until user moves it
        val gravityPref = preferences.hopperGravity().get()
        hideHopper(preferences.hideHopper().get())
        binding.categoryHopperFrame.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            // Use TOP gravity since updateHopperY() sets the actual Y position
            // Horizontal gravity: 0=LEFT, 1=CENTER, 2=RIGHT
            gravity = Gravity.TOP or when (gravityPref) {
                0 -> Gravity.LEFT
                2 -> Gravity.RIGHT
                else -> Gravity.CENTER
            }
        }
        hopperGravity = gravityPref

        val gestureDetector = GestureDetector(binding.root.context, LibraryGestureDetector(this))
        with(binding.roundedCategoryHopper) {
            listOf(categoryHopperLayout, upCategory, downCategory, categoryButton).forEach {
                it.setOnTouchListener { _, event ->
                    if (event?.action == MotionEvent.ACTION_DOWN) {
                        animatorSet?.end()
                    }
                    if (event?.action == MotionEvent.ACTION_UP) {
                        val result = gestureDetector.onTouchEvent(event)
                        if (!result) {
                            binding.categoryHopperFrame.animate().setDuration(150L).translationX(0f)
                                .start()
                        }
                        result
                    } else {
                        gestureDetector.onTouchEvent(event)
                    }
                }
            }
        }
    }

    fun handleGeneralEvent(event: MotionEvent) {
        if (presenter.showAllCategories) return
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            val result = catGestureDetector?.onTouchEvent(event) ?: false
            if (!result && binding.libraryGridRecycler.recycler.translationX != 0f) {
                binding.libraryGridRecycler.recycler.animate().setDuration(150L)
                    .translationX(0f)
                    .start()
            }
        } else {
            catGestureDetector?.onTouchEvent(event)
        }
    }

    open fun updateHopperY(windowInsets: WindowInsetsCompat? = null) {
        val view = view ?: return
        val insets = windowInsets ?: view.rootWindowInsetsCompat
        val bottomNav = if (isSubClass) null else activityBinding?.bottomNav
        val listOfYs = mutableListOf<Float>()
        
        // Only use old filter sheet for positioning (Compose filter disabled)
        listOfYs.add(binding.filterBottomSheet.filterBottomSheet.y)
        listOfYs.add(bottomNav?.y ?: binding.filterBottomSheet.filterBottomSheet.y)
        
        val insetBottom = insets?.getInsets(systemBars())?.bottom ?: 0
        if (!preferences.autohideHopper().get() || bottomNav == null) {
            listOfYs.add(view.height - (insetBottom).toFloat())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && insets?.isImeVisible() == true) {
            val insetKey = insets.getInsets(ime() or systemBars()).bottom
            listOfYs.add(view.height - (insetKey).toFloat())
        }
        val defaultY = binding.filterBottomSheet.filterBottomSheet.y
        binding.categoryHopperFrame.y = -binding.categoryHopperFrame.height +
            (listOfYs.minOrNull() ?: defaultY) +
            hopperOffset +
            binding.libraryGridRecycler.recycler.translationY
        if (view.height - insetBottom < binding.categoryHopperFrame.y) {
            binding.jumperCategoryText.translationY =
                -(binding.categoryHopperFrame.y - (view.height - insetBottom)) +
                binding.libraryGridRecycler.recycler.translationY
        } else {
            binding.jumperCategoryText.translationY = binding.libraryGridRecycler.recycler.translationY
        }
    }

    fun resetHopperY() {
        hopperOffset = 0f
    }

    fun hideHopper(hide: Boolean) {
        binding.categoryHopperFrame.isVisible = !singleCategory && !hide
        binding.jumperCategoryText.isVisible = !hide
    }

    /**
     * Refresh hopper (pill) colors to match the current theme/mode.
     * Called when mode changes to update View-based hopper UI.
     * The hopper should use mode-specific colors (green for manga, blue for novel).
     */
    private fun refreshHopperColors() {
        val context = activity ?: return
        
        // Get mode-specific colors
        val currentMode = ModeManager.currentMode.value
        val backgroundColor = when (currentMode) {
            ContentType.MANGA -> context.getResourceColor(R.attr.colorSecondary)
            ContentType.NOVEL -> context.getResourceColor(R.attr.colorTertiary)
        }
        val iconColor = when (currentMode) {
            ContentType.MANGA -> context.getResourceColor(R.attr.colorOnSecondary)
            ContentType.NOVEL -> context.getResourceColor(R.attr.colorOnTertiary)
        }
        
        // Update the MaterialCardView card background color
        (binding.roundedCategoryHopper.root as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(backgroundColor)
        
        // Update the inner layout background color
        binding.roundedCategoryHopper.categoryHopperLayout.setBackgroundColor(backgroundColor)
        
        // Update button tints
        binding.roundedCategoryHopper.upCategory.setColorFilter(iconColor)
        binding.roundedCategoryHopper.downCategory.setColorFilter(iconColor)
        binding.roundedCategoryHopper.categoryButton.setColorFilter(iconColor)
    }

    fun jumpToNextCategory(next: Boolean): Boolean {
        val category = getVisibleHeader() ?: run {
            android.util.Log.d("LibraryController", "jumpToNextCategory: getVisibleHeader returned null")
            return false
        }
        android.util.Log.d("LibraryController", "jumpToNextCategory: next=$next, currentCategory=${category.category.name}, showAllCategories=${presenter.showAllCategories}")
        if (presenter.showAllCategories) {
            if (!next) {
                val fPosition = binding.libraryGridRecycler.recycler.findFirstVisibleItemPosition()
                if (fPosition > adapter.currentItems.indexOf(category)) {
                    scrollToHeader(category.category.order)
                    return true
                }
            }
            val newOffset = adapter.headerItems.indexOf(category) + (if (next) 1 else -1)
            android.util.Log.d("LibraryController", "jumpToNextCategory (showAllCategories): newOffset=$newOffset, headerItems.size=${adapter.headerItems.size}")
            return if (if (!next) newOffset > -1 else newOffset < adapter.headerItems.size) {
                val newCategory = (adapter.headerItems[newOffset] as LibraryHeaderItem).category
                val newOrder = newCategory.order
                android.util.Log.d("LibraryController", "jumpToNextCategory: scrolling to ${newCategory.name} (order=$newOrder)")
                scrollToHeader(newOrder)
                showCategoryText(newCategory.name)
                true
            } else {
                android.util.Log.d("LibraryController", "jumpToNextCategory: at boundary, scrolling to ${if (next) "end" else "start"}")
                binding.libraryGridRecycler.recycler.scrollToPosition(if (next) adapter.itemCount - 1 else 0)
                true
            }
        } else {
            // Use allCategories for navigation to include empty categories
            val navCategories = presenter.allCategories.ifEmpty { presenter.categories }
            android.util.Log.d("LibraryController", "jumpToNextCategory (!showAllCategories): navCategories.size=${navCategories.size}, currentCategoryId=${presenter.currentCategoryId}")
            navCategories.forEachIndexed { idx, cat -> 
                android.util.Log.d("LibraryController", "  [$idx] ${cat.name} (id=${cat.id}, order=${cat.order})")
            }
            val newOffset =
                navCategories.indexOfFirst { presenter.currentCategoryId == it.id } +
                    (if (next) 1 else -1)
            android.util.Log.d("LibraryController", "jumpToNextCategory: newOffset=$newOffset")
            if (if (!next) {
                newOffset > -1
            } else {
                    newOffset < navCategories.size
                }
            ) {
                val newCategory = navCategories[newOffset]
                val newCategoryId = newCategory.id ?: 0
                android.util.Log.d("LibraryController", "jumpToNextCategory: switching to ${newCategory.name} (id=$newCategoryId, order=${newCategory.order})")
                // Use scrollToHeaderById to avoid issues with non-unique orders (common in novel categories)
                scrollToHeaderById(newCategoryId)
                showCategoryText(newCategory.name)
                hopperAnimation?.cancel()
                hopperOffset = 0f
                updateHopperY()
                return true
            } else {
                android.util.Log.d("LibraryController", "jumpToNextCategory: newOffset out of bounds")
            }
        }
        return false
    }

    fun visibleHeaderHolder(): LibraryHeaderHolder? {
        return adapter.getHeaderPositions().firstOrNull()?.let {
            binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(it) as? LibraryHeaderHolder
        }
    }

    private fun getHeader(firstCompletelyVisible: Boolean = false): LibraryHeaderItem? {
        val position = if (firstCompletelyVisible) {
            binding.libraryGridRecycler.recycler.findFirstCompletelyVisibleItemPosition()
        } else {
            -1
        }
        if (position > 0) {
            when (val item = adapter.getItem(position)) {
                is LibraryHeaderItem -> return item
                is LibraryItem -> return item.header
            }
        } else {
            val fPosition = binding.libraryGridRecycler.recycler.findFirstVisibleItemPosition()
            when (val item = adapter.getItem(fPosition)) {
                is LibraryHeaderItem -> return item
                is LibraryItem -> return item.header
            }
        }
        return null
    }

    private fun getVisibleHeader(): LibraryHeaderItem? {
        val fPosition = binding.libraryGridRecycler.recycler.findFirstVisibleItemPosition()
        when (val item = adapter.getItem(fPosition)) {
            is LibraryHeaderItem -> return item
            is LibraryItem -> return item.header
        }
        return adapter.headerItems.firstOrNull() as? LibraryHeaderItem
    }

    private fun anchorView(): View {
        return if (binding.categoryHopperFrame.isVisible) {
            binding.categoryHopperFrame
        } else {
            binding.filterBottomSheet.filterBottomSheet
        }
    }

    private fun updateLibrary(category: Category? = null) {
        val view = view ?: return
        LibraryUpdateJob.startNow(view.context, category)
        snack = view.snack(MR.strings.updating_library) {
            anchorView = anchorView()
            this.view.elevation = 15f.dpToPx
            setAction(MR.strings.cancel) {
                LibraryUpdateJob.stop(context)
                viewScope.launchUI {
                    NotificationReceiver.dismissNotification(
                        context,
                        Notifications.ID_LIBRARY_PROGRESS,
                    )
                }
            }
        }
    }

    private fun setRecyclerLayout() {
        with(binding.libraryGridRecycler.recycler) {
            val bottomNav = if (isSubClass) null else activityBinding?.bottomNav
            viewScope.launchUI {
                updatePaddingRelative(
                    bottom = 50.dpToPx + (bottomNav?.height ?: 0),
                )
            }
            useStaggered(preferences, uiPreferences)
            if (libraryLayout == LibraryItem.LAYOUT_LIST) {
                spanCount = 1
                updatePaddingRelative(
                    start = 0,
                    end = 0,
                )
            } else {
                setGridSize(preferences)
                updatePaddingRelative(
                    start = 5.dpToPx,
                    end = 5.dpToPx,
                )
            }
            (manager as? GridLayoutManager)?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    if (libraryLayout == LibraryItem.LAYOUT_LIST) return managerSpanCount
                    val item = this@LibraryController.mAdapter?.getItem(position)
                    return if (item is LibraryHeaderItem || item is SearchGlobalItem || item is LibraryPlaceholderItem) {
                        managerSpanCount
                    } else {
                        1
                    }
                }
            }
        }
    }

    private fun setPreferenceFlows() {
        listOf(
            preferences.libraryLayout(),
            uiPreferences.uniformGrid(),
            preferences.gridSize(),
            preferences.useStaggeredGrid(),
        ).forEach {
            it.changes()
                .drop(1)
                .onEach {
                    reattachAdapter()
                }
                .launchIn(viewScope)
        }
        preferences.hideStartReadingButton().register()
        uiPreferences.outlineOnCovers().register { adapter.showOutline = it }
        preferences.categoryNumberOfItems().register { adapter.showNumber = it }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun <T> Preference<T>.register(onChanged: ((T) -> Unit)? = null) {
        changes()
            .drop(1)
            .onEach {
                onChanged?.invoke(it)
                adapter.notifyDataSetChanged()
            }
            .launchIn(viewScope)
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type.isEnter) {
            // Re-enable the unified Compose search bar when returning to Library
            if (type == ControllerChangeType.POP_ENTER) {
                setupUnifiedSearchBar()
                presenter.updateLibrary()
                isPoppingIn = true
            }
            
            if (!useComposeFilter) {
                binding.filterBottomSheet.filterBottomSheet.isVisible = true
            }
            binding.recyclerCover.isClickable = false
            binding.recyclerCover.isFocusable = false
            singleCategory = presenter.categories.size <= 1

            if (binding.libraryGridRecycler.recycler.manager is StaggeredGridLayoutManager && staggeredBundle != null) {
                binding.libraryGridRecycler.recycler.manager.onRestoreInstanceState(staggeredBundle)
                staggeredBundle = null
            }
        } else {
            // Disable the unified search bar when leaving Library
            // Don't show the old app bar if going to Browse (which uses its own Compose search bar)
            val targetController = router.backstack.lastOrNull()?.controller
            val goingToBrowse = targetController is BrowseController
            (activity as? MainActivity)?.disableUnifiedSearchBar(showOldAppBar = !goingToBrowse)
            
            saveStaggeredState()
            updateFilterSheetY()
            closeTip()
            if (!useComposeFilter && binding.filterBottomSheet.filterBottomSheet.sheetBehavior.isHidden()) {
                binding.filterBottomSheet.filterBottomSheet.isInvisible = true
            }
            activityBinding?.searchToolbar?.setOnLongClickListener(null)
        }
    }

    override fun onChangeEnded(
        changeHandler: ControllerChangeHandler,
        changeType: ControllerChangeType,
    ) {
        super.onChangeEnded(changeHandler, changeType)
        if (isPoppingIn) {
            isPoppingIn = false
            tempItems?.let { onNextLibraryUpdate(it) }
            tempItems = null
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (!isBindingInitialized) return
        updateFilterSheetY()
        if (observeLater) {
            presenter.updateLibrary()
        }
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        observeLater = true
    }

    override fun onDestroyView(view: View) {
        destroyActionModeIfNeeded()
        if (isBindingInitialized) {
            binding.libraryGridRecycler.recycler.removeOnScrollListener(scrollListener)
            binding.fastScroller.controller = null
        }
        displaySheet?.dismiss()
        displaySheet = null
        mAdapter = null
        saveStaggeredState()

        showAllCategoriesView?.let {
            (activityBinding?.searchToolbar?.searchView as? MiniSearchView)?.removeSearchModifierIcon(it)
        }

        // FIX: Clear unified search bar callbacks to prevent memory leak.
        // MainActivity holds unifiedSearchBarState for the app lifecycle;
        // lambdas capturing this controller would prevent GC otherwise.
        (activity as? MainActivity)?.unifiedSearchBarState?.clearCallbacks()

        super.onDestroyView(view)
    }

    open fun onNextLibraryUpdate(mangaMap: List<LibraryItem>, freshStart: Boolean = false) {
        if (isPoppingIn) {
            tempItems = mangaMap
            return
        }
        view ?: return
        destroyActionModeIfNeeded()
        
        // Update Compose content bridge if enabled
        if (useComposeLibraryContent) {
            libraryContentBridge.updateContent(mangaMap, presenter.categories)
            // When using Compose, hide the old emptyView completely - Compose handles empty state
            binding.emptyView.hide()
        }
        
        if (mangaMap.isNotEmpty()) {
            if (!binding.progress.isVisible) {
                (activity as? MainActivity)?.showNotificationPermissionPrompt()
            }
            binding.emptyView.hide()
        } else if (!useComposeLibraryContent) {
            // Only show old View-based empty state when NOT using Compose
            binding.emptyView.show(
                Icons.Filled.HeartBroken,
                if (hasActiveFilters) {
                    MR.strings.no_matches_for_filters
                } else {
                    MR.strings.library_is_empty_add_from_browse
                },
                if (!hasActiveFilters) {
                    listOf(
                        EmptyView.Action(MR.strings.getting_started_guide) {
                            activity?.openInBrowser("https://tachiyomi.org/docs/guides/getting-started#_2-adding-sources")
                        },
                    )
                } else {
                    emptyList()
                },
            )
        }
        adapter.setItems(mangaMap)
        if (binding.libraryGridRecycler.recycler.translationX != 0f) {
            val time = binding.root.resources.getInteger(
                AR.integer.config_shortAnimTime,
            ).toLong()
            viewScope.launchUI {
                delay(time / 2)
                binding.libraryGridRecycler.recycler.translationX = 0f
            }
        }
        singleCategory = presenter.categories.size <= 1

        binding.progress.isVisible = false
        (activity as? MainActivity)?.splashState?.ready = true

        if (!freshStart) {
            justStarted = false
        } // else binding.recyclerLayout.alpha = 1f
        if (binding.recyclerLayout.alpha == 0f) {
            binding.recyclerLayout.animate().alpha(1f).setDuration(500).start()
        }
        if (justStarted && freshStart && !isSubClass) {
            val activeC = activeCategory
            scrollToHeader(activeCategory)
            binding.libraryGridRecycler.recycler.post {
                if (isControllerVisible) {
                    activityBinding?.appBar?.y = 0f
                    activityBinding?.appBar?.updateAppBarAfterY(binding.libraryGridRecycler.recycler)
                    if (activeC > 0) {
                        activityBinding?.appBar?.useSearchToolbarForMenu(true)
                    }
                }
            }

            if (binding.libraryGridRecycler.recycler.manager is StaggeredGridLayoutManager && isControllerVisible) {
                staggeredObserver = ViewTreeObserver.OnGlobalLayoutListener {
                    binding.libraryGridRecycler.recycler.postOnAnimation {
                        if (!isControllerVisible) return@postOnAnimation
                        scrollToHeader(activeC, false)
                        activityBinding?.appBar?.y = 0f
                        activityBinding?.appBar?.updateAppBarAfterY(binding.libraryGridRecycler.recycler)
                        if (activeC > 0) {
                            activityBinding?.appBar?.useSearchToolbarForMenu(true)
                        }
                    }
                }
                binding.libraryGridRecycler.recycler.viewTreeObserver.addOnGlobalLayoutListener(staggeredObserver)
                viewScope.launchUI {
                    delay(500)
                    removeStaggeredObserver()
                    if (!isControllerVisible) return@launchUI
                    if (activeC > 0) {
                        activityBinding?.appBar?.useSearchToolbarForMenu(true)
                    }
                }
            }
        }
        if (isControllerVisible) {
            activityBinding?.appBar?.lockYPos = false
        }
        binding.libraryGridRecycler.recycler.post {
            elevateAppBar(binding.libraryGridRecycler.recycler.canScrollVertically(-1))
            setActiveCategory()
        }

        binding.categoryHopperFrame.isVisible = !singleCategory && !preferences.hideHopper().get()
        adapter.isLongPressDragEnabled = canDrag()
        binding.categoryRecycler.setCategories(
            presenter.categories,
            if (adapter.showNumber) {
                adapter.itemsPerCategory
            } else {
                emptyMap()
            },
        )
        if (!useComposeFilter) {
            with(binding.filterBottomSheet.root) {
                viewScope.launch {
                    checkForManhwa(presenter.sourceManager)
                }
                updateGroupTypeButton(presenter.groupType)
                setExpandText(canCollapseOrExpandCategory())
            }
        }
        if (shouldScrollToTop) {
            binding.libraryGridRecycler.recycler.scrollToPosition(0)
            shouldScrollToTop = false
        }
        if (isControllerVisible) {
            binding.headerTitle.setOnClickListener {
                val recycler = binding.libraryGridRecycler.recycler
                if (!singleCategory) {
                    showCategories(recycler.translationY == 0f)
                }
            }
            if (!hasMovedHopper && isAnimatingHopper == null) {
                showSlideAnimation()
            }
            setSubtitle()
            showMiniBar()
        }
        updateHopperAlpha()
        val isSingleCategory = !presenter.showAllCategories && !presenter.forceShowAllCategories
        val context = binding.roundedCategoryHopper.root.context
        binding.roundedCategoryHopper.upCategory.setImageDrawable(
            context.contextCompatDrawable(
                if (isSingleCategory) {
                    R.drawable.ic_arrow_start_24dp
                } else {
                    R.drawable.ic_expand_less_24dp
                },
            ),
        )
        binding.roundedCategoryHopper.downCategory.setImageDrawable(
            context.contextCompatDrawable(
                if (isSingleCategory) {
                    R.drawable.ic_arrow_end_24dp
                } else {
                    R.drawable.ic_expand_more_24dp
                },
            ),
        )
        binding.roundedCategoryHopper.categoryButton.setImageDrawable(
            context.contextCompatDrawable(
                LibraryGroup.groupTypeDrawableRes(presenter.groupType),
            ),
        )
    }

    private fun showSlideAnimation() {
        isAnimatingHopper = true
        val slide = 25f.dpToPx
        val animatorSet = AnimatorSet()
        this.animatorSet = animatorSet
        val animations = listOf(
            slideAnimation(0f, slide, 200),
            slideAnimation(slide, -slide),
            slideAnimation(-slide, slide),
            slideAnimation(slide, -slide),
            slideAnimation(-slide, 0f, 233),
        )
        animatorSet.playSequentially(animations)
        animatorSet.startDelay = 1250
        animatorSet.doOnEnd {
            binding.categoryHopperFrame.translationX = 0f
            isAnimatingHopper = false
            this.animatorSet = null
        }
        animatorSet.start()
    }

    private fun slideAnimation(from: Float, to: Float, duration: Long = 400): ObjectAnimator {
        return ObjectAnimator.ofFloat(binding.categoryHopperFrame, View.TRANSLATION_X, from, to)
            .setDuration(duration)
    }

    open fun showCategories(show: Boolean, closeSearch: Boolean = false, category: Int = -1) {
        binding.recyclerCover.isClickable = show
        binding.recyclerCover.isFocusable = show
        (activity as? MainActivity)?.apply {
            reEnableBackPressedCallBack()
            if (show && !binding.appBar.compactSearchMode && binding.appBar.useLargeToolbar) {
                binding.appBar.compactSearchMode = binding.appBar.useLargeToolbar && show
                if (binding.appBar.compactSearchMode) {
                    setFloatingToolbar(true)
                    mainRecycler.requestApplyInsets()
                    binding.appBar.y = 0f
                    binding.appBar.updateAppBarAfterY(mainRecycler)
                }
            } else if (!show && binding.appBar.compactSearchMode && binding.appBar.useLargeToolbar &&
                resources.configuration.screenHeightDp >= 600
            ) {
                binding.appBar.compactSearchMode = false
                mainRecycler.requestApplyInsets()
            }
        }
        if (closeSearch) {
            activityBinding?.searchToolbar?.searchItem?.collapseActionView()
        }
        val full = binding.categoryRecycler.height.toFloat() + binding.categoryRecycler.marginTop
        val translateY = if (show) full else 0f
        binding.libraryGridRecycler.recycler.animate().translationY(translateY).apply {
            setUpdateListener {
                activityBinding?.appBar?.updateAppBarAfterY(binding.libraryGridRecycler.recycler)
                updateHopperY()
            }
        }.start()
        binding.recyclerShadow.animate().translationY(translateY - 8.dpToPx).start()
        binding.recyclerCover.animate().translationY(translateY).start()
        binding.recyclerCover.animate().alpha(if (show) 0.75f else 0f).start()
        activityBinding?.appBar?.updateAppBarAfterY(binding.libraryGridRecycler.recycler)
        binding.swipeRefresh.isEnabled = !show
        setSubtitle()
        binding.categoryRecycler.isInvisible = !show
        if (show) {
            binding.categoryRecycler.post {
                binding.categoryRecycler.scrollToCategory(activeCategory)
            }
            binding.fastScroller.hideScrollbar()
            elevateAppBar(false)
            if (!useComposeFilter) {
                binding.filterBottomSheet.filterBottomSheet.sheetBehavior?.hide()
            }
        } else {
            val notAtTop = binding.libraryGridRecycler.recycler.canScrollVertically(-1)
            elevateAppBar((notAtTop || category > 0) && category != 0)
        }
    }

    fun scrollToCategory(category: Category?) {
        if (category != null && activeCategory != category.order) {
            scrollToHeader(category.order)
        }
    }

    /**
     * Scroll to a category by its ID (for non-showAllCategories mode)
     * More reliable than using order which may be non-unique for novels
     */
    private fun scrollToHeaderById(categoryId: Int, removeObserver: Boolean = true) {
        if (removeObserver) {
            removeStaggeredObserver()
        }
        shouldScrollToTop = true
        presenter.switchSectionById(categoryId)
        activeCategory = presenter.allCategories.find { it.id == categoryId }?.order ?: categoryId
        setActiveCategory()
    }

    private fun scrollToHeader(pos: Int, removeObserver: Boolean = true) {
        if (removeObserver) {
            removeStaggeredObserver()
        }
        if (!presenter.showAllCategories) {
            shouldScrollToTop = true
            presenter.switchSection(pos)
            activeCategory = pos
            setActiveCategory()
            return
        }
        val headerPosition = mAdapter?.indexOf(pos) ?: return
        if (headerPosition > -1) {
            val activityBinding = activityBinding ?: return
            val index = adapter.headerItems.indexOf(adapter.getItem(headerPosition))
            val appbarOffset = if (index <= 0) 0 else -fullAppBarHeight!! + activityBinding.cardFrame.height
            val previousHeader = adapter.headerItems.getOrNull(index - 1) as? LibraryHeaderItem
            binding.libraryGridRecycler.recycler.scrollToPositionWithOffset(
                headerPosition,
                (
                    when {
                        headerPosition == 0 -> 0
                        previousHeader?.category?.isHidden == true -> (-3).dpToPx
                        else -> (-30).dpToPx
                    }
                    ) + appbarOffset,
            )
            (adapter.getItem(headerPosition) as? LibraryHeaderItem)?.category?.let {
                saveActiveCategory(it)
            }
            activeCategory = pos
            if (!isSubClass) {
                // Save to mode-specific preference
                val currentMode = ModeManager.currentMode.value
                when (currentMode) {
                    ContentType.MANGA -> preferences.lastUsedMangaCategory().set(pos)
                    ContentType.NOVEL -> preferences.lastUsedNovelCategory().set(pos)
                }
                preferences.lastUsedCategory().set(pos)
            }
            binding.libraryGridRecycler.recycler.post {
                if (isControllerVisible) {
                    activityBinding.appBar.y = 0f
                    activityBinding.appBar.updateAppBarAfterY(binding.libraryGridRecycler.recycler)
                }
            }
        }
    }

    private fun onRefresh() {
        showCategories(false)
        presenter.updateLibrary()
        destroyActionModeIfNeeded()
    }

    /**
     * Called when a filter is changed.
     */
    private fun onFilterChanged() {
        presenter.requestFilterUpdate()
        destroyActionModeIfNeeded()
    }

    private fun reattachAdapter() {
        libraryLayout = preferences.libraryLayout().get()
        setRecyclerLayout()
        val position = binding.libraryGridRecycler.recycler.findFirstVisibleItemPosition()
        binding.libraryGridRecycler.recycler.adapter = adapter
        binding.libraryGridRecycler.recycler.scrollToPositionWithOffset(position, 0)
    }

    fun search(query: String?): Boolean {
        val isShowAllCategoriesSet = preferences.showAllCategories().get()
        if (!query.isNullOrBlank() && this.query.isBlank() && !isShowAllCategoriesSet) {
            presenter.forceShowAllCategories = preferences.showAllCategoriesWhenSearchingSingleCategory().get()
            presenter.updateLibrary()
        } else if (query.isNullOrBlank() && this.query.isNotBlank() && !isShowAllCategoriesSet) {
            if (!isSubClass) {
                preferences.showAllCategoriesWhenSearchingSingleCategory()
                    .set(presenter.forceShowAllCategories)
            }
            presenter.forceShowAllCategories = false
            presenter.updateLibrary()
        }

        if (query != this.query && !query.isNullOrBlank()) {
            binding.libraryGridRecycler.recycler.scrollToPosition(0)
        }
        this.query = query ?: ""
        showAllCategoriesView?.isGone = isShowAllCategoriesSet || presenter.groupType != BY_DEFAULT || this.query.isBlank()
        showAllCategoriesView?.isSelected = presenter.forceShowAllCategories
        if (this.query.isNotBlank()) {
            searchItem.string = this.query
            if (adapter.scrollableHeaders.isEmpty() && !isSubClass) {
                adapter.addScrollableHeader(searchItem)
            }
        } else if (this.query.isBlank() && adapter.scrollableHeaders.isNotEmpty()) {
            adapter.removeAllScrollableHeaders()
        }
        adapter.setFilter(query)
        if (presenter.currentLibraryItems.isEmpty()) return true
        viewScope.launchUI {
            adapter.performFilterAsync()
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        // Get positions of currently selected items BEFORE clearing
        val selectedPositions = adapter.selectedPositions.toList()
        
        selectedMangas.clear()
        selectedNovels.clear()
        // Also clear Compose selection state
        libraryContentBridge.clearSelection()
        actionMode = null
        adapter.mode = SelectableAdapter.Mode.SINGLE
        adapter.clearSelection()
        
        // Only update the previously selected items instead of all items
        // This prevents covers from reloading on all items
        selectedPositions.forEach { position ->
            (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
        }
        // Also update headers to refresh selection state
        updateHeaders(true)
        
        lastClickPosition = -1
        adapter.isLongPressDragEnabled = canDrag()
    }

    private fun setSelection(manga: Manga, selected: Boolean) {
        val currentMode = adapter.mode
        if (selected) {
            if (selectedMangas.add(manga)) {
                val positions = adapter.allIndexOf(manga)
                if (adapter.mode != SelectableAdapter.Mode.MULTI) {
                    adapter.mode = SelectableAdapter.Mode.MULTI
                }
                launchUI {
                    delay(100)
                    adapter.isLongPressDragEnabled = false
                }
                positions.forEach { position ->
                    adapter.addSelection(position)
                    (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
                }
            }
        } else {
            if (selectedMangas.remove(manga)) {
                val positions = adapter.allIndexOf(manga)
                lastClickPosition = -1
                if (selectedMangas.isEmpty()) {
                    adapter.mode = SelectableAdapter.Mode.SINGLE
                    adapter.isLongPressDragEnabled = canDrag()
                }
                positions.forEach { position ->
                    adapter.removeSelection(position)
                    (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
                }
            }
        }
        updateHeaders(currentMode != adapter.mode)
    }

    private fun setSelection(novel: yokai.domain.novel.Novel, selected: Boolean) {
        android.util.Log.d("LibraryController", "setSelection(novel): ${novel.title}, selected=$selected")
        val currentMode = adapter.mode
        if (selected) {
            if (selectedNovels.add(novel)) {
                val positions = adapter.allIndexOf(novel)
                android.util.Log.d("LibraryController", "setSelection(novel): Found ${positions.size} positions for novel")
                if (adapter.mode != SelectableAdapter.Mode.MULTI) {
                    adapter.mode = SelectableAdapter.Mode.MULTI
                }
                launchUI {
                    delay(100)
                    adapter.isLongPressDragEnabled = false
                }
                positions.forEach { position ->
                    adapter.addSelection(position)
                    (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
                }
            }
        } else {
            if (selectedNovels.remove(novel)) {
                val positions = adapter.allIndexOf(novel)
                lastClickPosition = -1
                if (selectedNovels.isEmpty()) {
                    adapter.mode = SelectableAdapter.Mode.SINGLE
                    adapter.isLongPressDragEnabled = canDrag()
                }
                positions.forEach { position ->
                    adapter.removeSelection(position)
                    (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
                }
            }
        }
        updateHeaders(currentMode != adapter.mode)
    }

    private fun updateHeaders(changedMode: Boolean = false) {
        val headerPositions = adapter.getHeaderPositions()
        headerPositions.forEach {
            if (changedMode) {
                adapter.notifyItemChanged(it)
            } else {
                (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(it) as? LibraryHeaderHolder)?.setSelection()
            }
        }
    }

    override fun startReading(position: Int, view: View?) {
        if (adapter.mode == SelectableAdapter.Mode.MULTI) {
            toggleSelection(position)
            return
        }
        val activity = activity ?: return
        
        when (val item = adapter.getItem(position)) {
            is LibraryMangaItem -> {
                val manga = item.manga.manga
                val chapter = presenter.getFirstUnread(manga) ?: return
                activity.apply {
                    // Mode Inheritance ensures library only shows current mode's content
                    // Quick-read navigates directly to appropriate reader
                    if (view != null) {
                        val (intent, bundle) = ReaderActivity
                            .newIntentWithTransitionOptions(activity, manga, chapter, view)
                        startActivity(intent, bundle)
                    } else {
                        startActivity(ReaderActivity.newIntent(activity, manga, chapter))
                    }
                }
            }
            is LibraryNovelItem -> {
                val novel = item.novel
                // Launch novel reader directly (first unread chapter will be loaded by the reader)
                val intent = NovelReaderActivity.newIntent(activity, novel.id)
                activity.startActivity(intent)
            }
            else -> return
        }
        destroyActionModeIfNeeded()
    }

    private fun toggleSelection(position: Int) {
        when (val item = adapter.getItem(position)) {
            is LibraryMangaItem -> setSelection(item.manga.manga, !adapter.isSelected(position))
            is LibraryNovelItem -> setSelection(item.novel, !adapter.isSelected(position))
            else -> return
        }
        invalidateActionMode()
    }

    override fun canDrag(): Boolean {
        val filterOff = !hasActiveFilters && presenter.groupType == BY_DEFAULT
        return filterOff && adapter.mode != SelectableAdapter.Mode.MULTI
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(view: View?, position: Int): Boolean {
        val item = adapter.getItem(position)
        return if (adapter.mode == SelectableAdapter.Mode.MULTI) {
            snack?.dismiss()
            lastClickPosition = position
            toggleSelection(position)
            false
        } else {
            // Extract click coordinates from view tags (set by LibraryGridHolder)
            val clickX = view?.getTag(R.id.tag_click_x) as? Float ?: (view?.width?.div(2f) ?: 0f)
            val clickY = view?.getTag(R.id.tag_click_y) as? Float ?: (view?.height?.div(2f) ?: 0f)
            
            when (item) {
                is LibraryMangaItem -> openManga(item.manga.manga, view, clickX, clickY)
                is LibraryNovelItem -> openNovel(item.novel)
            }
            false
        }
    }

    private fun saveStaggeredState() {
        if (binding.libraryGridRecycler.recycler.manager is StaggeredGridLayoutManager) {
            staggeredBundle = binding.libraryGridRecycler.recycler.manager.onSaveInstanceState()
        }
    }

    private fun openManga(
        manga: Manga,
        sourceView: View? = null,
        clickX: Float = sourceView?.width?.div(2f) ?: 0f,
        clickY: Float = sourceView?.height?.div(2f) ?: 0f
    ) {
        // Phase 3: Check if shared element transitions are enabled
        val useCircularReveal = preferences.enableSharedElementTransitions().get()
        
        // Phase 2: Log click coordinates for verification
        android.util.Log.d("LibraryController", "📍 Library item clicked at ($clickX, $clickY) - Manga: ${manga.title} [CircularReveal: $useCircularReveal]")
        
        // Mode Inheritance: Route to correct details controller based on source type
        val source = presenter.sourceManager.getOrStub(manga.source)
        
        android.util.Log.d("LibraryController", "=== NAVIGATION DEBUG START ===")
        android.util.Log.d("LibraryController", "Manga: ${manga.title}")
        android.util.Log.d("LibraryController", "Manga ID: ${manga.id}")
        android.util.Log.d("LibraryController", "Source: ${source::class.simpleName}")
        android.util.Log.d("LibraryController", "Is Novel Source: ${source is eu.kanade.tachiyomi.source.novel.NovelSourceWrapper}")
        
        if (source is eu.kanade.tachiyomi.source.novel.NovelSourceWrapper) {
            // Novel source - use ContentRouter for novel navigation
            android.util.Log.d("LibraryController", "✅ ROUTING: Novel source detected → NovelDetailsControllerNew")
            router.pushController(
                eu.kanade.tachiyomi.ui.novel.details.NovelDetailsControllerNew(manga.id!!).withFadeTransaction()
            )
        } else {
            // Manga source - check feature flag for Activity vs Controller
            val useActivity = preferences.useMangaDetailsActivity().get()
            
            android.util.Log.d("LibraryController", "--- Feature Flag Check ---")
            android.util.Log.d("LibraryController", "Preference object: ${preferences::class.simpleName}")
            android.util.Log.d("LibraryController", "useMangaDetailsActivity().get() = $useActivity")
            android.util.Log.d("LibraryController", "Activity available: ${activity != null}")
            
            // PHASE 2 TEST: Check if Activity migration feature flag is enabled
            if (useActivity) {
                // NEW PATH: Launch Activity (Phase 2 test)
                android.util.Log.d("LibraryController", "✅ ROUTING: Activity enabled → MangaDetailsActivity")
                android.util.Log.d("LibraryController", "🚀 Launching MangaDetailsActivity for: ${manga.title}")
                
                try {
                    val (intent, bundle) = MangaDetailsActivity.newIntentWithTransitionOptions(
                        activity!!,
                        manga.id!!,
                        sourceView,
                        fromSource = false
                    )
                    android.util.Log.d("LibraryController", "Intent created: $intent")
                    android.util.Log.d("LibraryController", "Bundle: $bundle")
                    activity?.startActivity(intent, bundle)
                    android.util.Log.d("LibraryController", "✅ Activity launched successfully")
                    android.util.Log.d("LibraryController", "=== NAVIGATION DEBUG END (Activity) ===")
                    return
                } catch (e: Exception) {
                    android.util.Log.e("LibraryController", "❌ Failed to launch Activity: ${e.message}", e)
                    android.util.Log.d("LibraryController", "Falling back to Controller...")
                }
            } else {
                android.util.Log.d("LibraryController", "❌ ROUTING: Activity disabled → MangaDetailsController (Controller path)")
            }
            
            // Manga source - use appropriate transition based on preference
            val controller = MangaDetailsController(manga)
            
            if (useCircularReveal && sourceView != null) {
                // Convert view-local coordinates to screen coordinates for the details view
                val location = IntArray(2)
                sourceView.getLocationInWindow(location)
                val screenX = location[0] + clickX
                val screenY = location[1] + clickY
                
                // Use circular reveal transition
                val transaction = com.bluelinelabs.conductor.RouterTransaction.with(controller)
                    .pushChangeHandler(
                        eu.kanade.tachiyomi.ui.base.controller.CircularRevealChangeHandler(
                            screenX,
                            screenY,
                            350L
                        )
                    )
                    .popChangeHandler(FadeChangeHandler())
                
                router.pushController(transaction)
            } else {
                // Fallback to fade transition
                router.pushController(controller.withFadeTransaction())
            }
        }
    }

    private fun openNovel(novel: yokai.domain.novel.Novel) {
        // Open novel details using ContentRouter
        eu.kanade.tachiyomi.ui.navigation.ContentRouter.navigateToNovelDetails(router, novel)
    }

    /**
     * Called when a manga or novel is long clicked.
     *
     * @param position the position of the element clicked.
     */
    override fun onItemLongClick(position: Int) {
        val item = adapter.getItem(position)
        android.util.Log.d("LibraryController", "onItemLongClick: position=$position, item=${item?.javaClass?.simpleName}, isManga=${item is LibraryMangaItem}, isNovel=${item is LibraryNovelItem}")
        // Handle both manga and novel items
        if (item !is LibraryMangaItem && item !is LibraryNovelItem) {
            android.util.Log.w("LibraryController", "onItemLongClick: Item is not a manga or novel item, returning")
            return
        }
        snack?.dismiss()
        if (libraryLayout == LibraryItem.LAYOUT_COVER_ONLY_GRID && actionMode == null) {
            val title = when (item) {
                is LibraryMangaItem -> item.manga.manga.title
                is LibraryNovelItem -> item.novel.title
                else -> ""
            }
            snack = view?.snack(title) {
                anchorView = activityBinding?.bottomNav
                view.elevation = 15f.dpToPx
            }
        }
        android.util.Log.d("LibraryController", "onItemLongClick: Creating action mode, calling setSelection")
        createActionModeIfNeeded()
        when {
            lastClickPosition == -1 -> setSelection(position)
            lastClickPosition > position -> for (i in position until lastClickPosition) setSelection(
                i,
            )
            lastClickPosition < position -> for (i in lastClickPosition + 1..position) setSelection(
                i,
            )
            else -> setSelection(position)
        }
        lastClickPosition = position
    }

    override fun globalSearch(query: String) {
        router.pushController(GlobalSearchController(query).withFadeTransaction())
    }

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        val position = viewHolder?.bindingAdapterPosition ?: return
        binding.swipeRefresh.isEnabled = actionState != ItemTouchHelper.ACTION_STATE_DRAG
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            if (lastItemPosition != null &&
                position != lastItemPosition &&
                lastItem == adapter.getItem(position)
            ) {
                // because for whatever reason you can repeatedly tap on a currently dragging manga
                adapter.removeSelection(position)
                (binding.libraryGridRecycler.recycler.findViewHolderForAdapterPosition(position) as? LibraryHolder)?.toggleActivation()
                adapter.moveItem(position, lastItemPosition!!)
            } else {
                isDragging = true
                lastItem = adapter.getItem(position)
                lastItemPosition = position
                onItemLongClick(position)
            }
        }
    }

    private fun onUpdateManga(mangaId: Long?) {
        if (mangaId == LibraryUpdateJob.STARTING_UPDATE_SOURCE) return
        if (mangaId == null) {
            adapter.getHeaderPositions().forEach { adapter.notifyItemChanged(it) }
        } else {
            presenter.updateLibrary()
        }
    }

    private fun setSelection(position: Int, selected: Boolean = true) {
        when (val item = adapter.getItem(position)) {
            is LibraryMangaItem -> setSelection(item.manga.manga, selected)
            is LibraryNovelItem -> setSelection(item.novel, selected)
            else -> return
        }
        invalidateActionMode()
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        // Because padding a recycler causes it to scroll up we have to scroll it back down... wild
        val fromItem = adapter.getItem(fromPosition)
        val toItem = adapter.getItem(toPosition)
        if (binding.libraryGridRecycler.recycler.layoutManager !is StaggeredGridLayoutManager && (
            (fromItem is LibraryItem && toItem is LibraryItem) || fromItem == null
            )
        ) {
            binding.libraryGridRecycler.recycler.scrollBy(
                0,
                binding.libraryGridRecycler.recycler.paddingTop,
            )
        }
        if (lastItemPosition == toPosition) {
            lastItemPosition = null
        } else if (lastItemPosition == null) lastItemPosition = fromPosition
    }

    override fun shouldMoveItem(fromPosition: Int, toPosition: Int): Boolean {
        if (adapter.isSelected(fromPosition)) toggleSelection(fromPosition)
        val item = adapter.getItem(fromPosition)
        // Handle both manga and novel items
        if (item !is LibraryMangaItem && item !is LibraryNovelItem) return false
        val newHeader = adapter.getSectionHeader(toPosition) as? LibraryHeaderItem
        if (toPosition < 1) return false
        
        return when (item) {
            is LibraryMangaItem -> {
                (adapter.getItem(toPosition) !is LibraryHeaderItem) && (
                    newHeader?.category?.id == item.manga.category || !presenter.mangaIsInCategory(
                        item.manga,
                        newHeader?.category?.id,
                    )
                )
            }
            is LibraryNovelItem -> {
                // For novels, just check it's not moving to a header
                // Novel category movement is handled separately
                (adapter.getItem(toPosition) !is LibraryHeaderItem)
            }
            else -> false
        }
    }

    override fun onItemReleased(position: Int) {
        lastItem = null
        isDragging = false
        binding.swipeRefresh.isEnabled = true
        if (mAdapter == null || adapter.selectedItemCount > 0) {
            lastItemPosition = null
            return
        }
        destroyActionModeIfNeeded()
        // if nothing moved
        if (lastItemPosition == null) return
        
        val item = adapter.getItem(position)
        val newHeader = adapter.getSectionHeader(position) as? LibraryHeaderItem
        
        when (item) {
            is LibraryMangaItem -> {
                val libraryItems = getSectionItems(adapter.getSectionHeader(position), item)
                    .filterIsInstance<LibraryMangaItem>()
                val mangaIds = libraryItems.mapNotNull { it.manga.manga.id }
                if (newHeader?.category?.id == item.manga.category) {
                    presenter.rearrangeCategory(item.manga.category, mangaIds)
                } else {
                    if (presenter.mangaIsInCategory(item.manga, newHeader?.category?.id)) {
                        adapter.moveItem(position, lastItemPosition!!)
                        snack = view?.snack(MR.strings.already_in_category) {
                            anchorView = anchorView()
                            view.elevation = 15f.dpToPx
                        }
                        return
                    }
                    if (newHeader?.category != null) {
                        moveMangaToCategory(
                            item.manga,
                            newHeader.category,
                            mangaIds,
                        )
                    }
                }
            }
            is LibraryNovelItem -> {
                val libraryItems = getSectionItems(adapter.getSectionHeader(position), item)
                    .filterIsInstance<LibraryNovelItem>()
                val novelIds = libraryItems.map { it.novel.id }
                val categoryId = newHeader?.category?.id
                presenter.rearrangeNovelCategory(categoryId, novelIds)
            }
            else -> return
        }
        lastItemPosition = null
    }

    private fun getSectionItems(header: IHeader<*>, skipItem: ISectionable<*, *>): List<ISectionable<*, *>> {
        val sectionItems: MutableList<ISectionable<*, *>> = ArrayList()
        var startPosition: Int = adapter.getGlobalPositionOf(header)
        var item = adapter.getItem(++startPosition) as? LibraryItem
        while (item?.header == header || item == skipItem) {
            sectionItems.add(item as ISectionable<*, *>)
            item = adapter.getItem(++startPosition) as? LibraryItem
        }
        return sectionItems
    }

    private fun moveMangaToCategory(
        manga: LibraryManga,
        category: Category?,
        mangaIds: List<Long>,
    ) {
        if (category?.id == null) return
        val oldCatId = manga.category
        presenter.moveMangaToCategory(manga, category.id, mangaIds)
        snack?.dismiss()
        snack = view?.snack(
            view!!.context!!.getString(MR.strings.moved_to_, category.name),
        ) {
            anchorView = anchorView()
            view.elevation = 15f.dpToPx
            setAction(MR.strings.undo) {
                manga.category = category.id!!
                presenter.moveMangaToCategory(manga, oldCatId, mangaIds)
            }
        }
    }

    override fun updateCategory(position: Int): Boolean {
        val category = (adapter.getItem(position) as? LibraryHeaderItem)?.category ?: return false
        val inQueue = LibraryUpdateJob.categoryInQueue(category.id)
        snack?.dismiss()
        snack = view?.snack(
            view!!.context!!.getString(
                when {
                    inQueue -> MR.strings._already_in_queue
                    LibraryUpdateJob.isRunning(view!!.context) -> MR.strings.adding_category_to_queue
                    else -> MR.strings.updating_
                },
                category.name,
            ),
            Snackbar.LENGTH_LONG,
        ) {
            anchorView = anchorView()
            view.elevation = 15f.dpToPx
            setAction(MR.strings.cancel) {
                LibraryUpdateJob.stop(context)
                viewScope.launchUI {
                    NotificationReceiver.dismissNotification(
                        context,
                        Notifications.ID_LIBRARY_PROGRESS,
                    )
                }
            }
        }
        if (!inQueue) {
            LibraryUpdateJob.startNow(
                view!!.context,
                category,
                mangaToUse = if (category.isDynamic) {
                    presenter.getMangaInCategories(category.id)
                } else {
                    null
                },
            )
        }
        return true
    }

    override fun toggleCategoryVisibility(position: Int) {
        if (!presenter.showAllCategories) {
            showCategories(true)
            return
        }
        val catId = (adapter.getItem(position) as? LibraryHeaderItem)?.category?.id ?: return
        presenter.toggleCategoryVisibility(catId)
    }

    /**
     * Nullable Boolean to tell is all is collapsed/expanded/applicable
     * true = all categories are expanded
     * false = all or some categories are collapsed
     * null = is in single category mode
     */
    fun canCollapseOrExpandCategory(): Boolean? {
        if (singleCategory || !presenter.showAllCategories || isSubClass) {
            return null
        }
        return presenter.allCategoriesExpanded()
    }

    override fun manageCategory(position: Int) {
        val category = (adapter.getItem(position) as? LibraryHeaderItem)?.category ?: return
        if (!category.isDynamic) {
            ManageCategoryDialog(category) {
                presenter.updateLibrary()
            }.showDialog(router)
        }
    }

    override fun sortCategory(catId: Int, sortBy: Char) {
        val category = presenter.categories.find { it.id == catId }
        if (category?.isDynamic == false && sortBy == LibrarySort.DragAndDrop.categoryValue) {
            val item = adapter.findCategoryHeader(catId) ?: return
            val libraryItems = adapter.getSectionItems(item)
                .filterIsInstance<LibraryMangaItem>()
            val mangaIds = libraryItems.mapNotNull { (it as? LibraryMangaItem)?.manga?.manga?.id }
            presenter.rearrangeCategory(catId, mangaIds)
        } else {
            presenter.sortCategory(catId, sortBy)
        }
    }

    override fun selectAll(position: Int) {
        val header = adapter.getSectionHeader(position) ?: return
        val items = adapter.getSectionItemPositions(header)
        val allSelected = allSelected(position)
        for (i in items) setSelection(i, !allSelected)
    }

    override fun allSelected(position: Int): Boolean {
        val header = adapter.getSectionHeader(position) ?: return false
        val items = adapter.getSectionItemPositions(header)
        return items.all { adapter.isSelected(it) }
    }

    //region sheet methods
    override fun showSheet() {
        closeTip()
        if (useComposeFilter) {
            composeFilterState.showFilterDialog(true)
            return
        }
        val sheetBehavior = binding.filterBottomSheet.filterBottomSheet.sheetBehavior
        when {
            sheetBehavior.isHidden() -> sheetBehavior?.collapse()
            !sheetBehavior.isExpanded() -> sheetBehavior?.expand()
            else -> showDisplayOptions()
        }
    }

    override fun hideSheet() {
        if (useComposeFilter) {
            composeFilterState.showFilterDialog(false)
            return
        }
        val sheetBehavior = binding.filterBottomSheet.filterBottomSheet.sheetBehavior
        when {
            sheetBehavior.isExpanded() -> sheetBehavior?.collapse()
            !sheetBehavior.isHidden() -> binding.filterBottomSheet.filterBottomSheet.sheetBehavior?.hide()
        }
    }

    override fun toggleSheet() {
        closeTip()
        if (useComposeFilter) {
            // Cycle through filter sheet states: hidden → collapsed → expanded → display options
            cycleComposeFilterSheetState()
            return
        }
        when {
            binding.filterBottomSheet.filterBottomSheet.sheetBehavior.isHidden() -> binding.filterBottomSheet.filterBottomSheet.sheetBehavior?.collapse()
            !binding.filterBottomSheet.filterBottomSheet.sheetBehavior.isExpanded() -> binding.filterBottomSheet.filterBottomSheet.sheetBehavior?.expand()
            else -> binding.filterBottomSheet.filterBottomSheet.sheetBehavior?.hide()
        }
    }

    override fun canStillGoBack(): Boolean {
        if (useComposeFilter) {
            return isBindingInitialized && (
                binding.recyclerCover.isClickable ||
                    isComposeFilterSheetVisible ||
                    composeFilterState.showFilterDialog.value ||
                    composeFilterState.currentScreen.value != yokai.presentation.library.filter.FilterSheetScreen.MAIN
                )
        }
        return isBindingInitialized && (
            binding.recyclerCover.isClickable ||
                binding.filterBottomSheet.filterBottomSheet.sheetBehavior.isExpanded()
            )
    }

    override fun handleBack(): Boolean {
        if (binding.recyclerCover.isClickable) {
            showCategories(false)
            return true
        }
        if (useComposeFilter) {
            if (composeFilterState.showFilterDialog.value) {
                composeFilterState.showFilterDialog(false)
                return true
            }
            // Handle GroupBy screen back navigation
            if (composeFilterState.goBack()) {
                return true
            }
            if (isComposeFilterSheetVisible) {
                hideComposeFilterSheet()
                return true
            }
            return false
        }
        if (binding.filterBottomSheet.filterBottomSheet.sheetBehavior.isExpanded()) {
            binding.filterBottomSheet.filterBottomSheet.sheetBehavior?.collapse()
            return true
        }
        return false
    }
    //endregion

    //region Toolbar options methods
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.library, menu)
        setupModeToggle(menu)

        val searchItem = activityBinding?.searchToolbar?.searchItem
        val searchView = activityBinding?.searchToolbar?.searchView
        activityBinding?.searchToolbar?.setQueryHint(view?.context?.getString(MR.strings.library_search_hint), query.isEmpty())

        showAllCategoriesView = showAllCategoriesView ?: (searchView as? MiniSearchView)?.addSearchModifierIcon { context ->
            ImageView(context).apply {
                isSelected = presenter.forceShowAllCategories
                isGone = true
                setOnClickListener {
                    presenter.forceShowAllCategories = !presenter.forceShowAllCategories
                    presenter.updateLibrary()
                    isSelected = presenter.forceShowAllCategories
                }
                val pad = 12.dpToPx
                setPadding(pad, 0, pad, 0)
                setImageResource(R.drawable.ic_show_all_categories_24dp)
                background = context.getResourceDrawable(R.attr.selectableItemBackgroundBorderless)
                imageTintList = ColorStateList.valueOf(context.getResourceColor(R.attr.actionBarTintColor))
                compatToolTipText = view?.context?.getString(MR.strings.show_all_categories)
            }
        }!!

        if (query.isNotEmpty()) {
            if (activityBinding?.searchToolbar?.isSearchExpanded != true) {
                searchItem?.expandActionView()
                searchView?.setQuery(query, true)
                searchView?.clearFocus()
            } else {
                searchView?.setQuery(query, false)
            }
            search(query)
        } else if (activityBinding?.searchToolbar?.isSearchExpanded == true) {
            searchItem?.collapseActionView()
        }

        setOnQueryTextChangeListener(activityBinding?.searchToolbar?.searchView) {
            if (!it.isNullOrEmpty() && binding.recyclerCover.isClickable) {
                showCategories(false)
            }
            search(it)
        }
    }

    private fun setupModeToggle(menu: Menu) {
        val modeToggle = menu.findItem(R.id.action_mode_toggle) ?: return
        updateModeToggleIcon(modeToggle)
        
        // Observe mode changes and update UI accordingly
        // Skip initial value since we already handled it above
        viewScope.launchUI {
            ModeManager.currentMode
                .drop(1)
                .collect { mode ->
                    updateModeToggleIcon(modeToggle)
                    // Restore mode-specific category position
                    val newCategory = when (mode) {
                        ContentType.MANGA -> preferences.lastUsedMangaCategory().get()
                        ContentType.NOVEL -> preferences.lastUsedNovelCategory().get()
                    }
                    activeCategory = newCategory
                    lastUsedCategory = newCategory
                    // No need to call presenter.updateLibrary() - presenter's own mode observer handles it
                    updateLibraryTitle(mode) // Update toolbar title
                    refreshHopperColors() // Update hopper pill colors for new theme
                }
        }
    }

    private fun updateModeToggleIcon(menuItem: MenuItem) {
        val currentMode = ModeManager.currentMode.value
        val icon = when (currentMode) {
            ContentType.MANGA -> R.drawable.ic_book_24dp // Manga icon
            ContentType.NOVEL -> R.drawable.ic_library_books_24dp // Novel icon
        }
        val title = when (currentMode) {
            ContentType.MANGA -> "Switch to Novel Mode"
            ContentType.NOVEL -> "Switch to Manga Mode"
        }
        menuItem.setIcon(icon)
        menuItem.title = title
    }

    private fun updateLibraryTitle(mode: ContentType) {
        // Update activity title to reflect current mode
        activity?.title = when (mode) {
            ContentType.MANGA -> activity?.getString(MR.strings.library)
            ContentType.NOVEL -> "Novel Library"
        }
    }

    override fun onActionViewExpand(item: MenuItem?) {
        if (!binding.recyclerCover.isClickable && query.isBlank() &&
            !singleCategory && presenter.showAllCategories
        ) {
            showCategories(true)
        }
    }

    override fun onActionViewCollapse(item: MenuItem?) {
        if (binding.recyclerCover.isClickable) {
            showCategories(false)
        }
    }

    override fun onSearchActionViewLongClickQuery(): String? {
        if (preferences.showLibrarySearchSuggestions().get()) {
            val suggestion = preferences.librarySearchSuggestion().get().takeIf { it.isNotBlank() }
            return suggestion?.removeSuffix("…")
        }
        return null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> expandActionViewFromInteraction = true
            R.id.action_filter -> {
                hasExpanded = true
                if (useComposeFilter) {
                    composeFilterState.showFilterDialog(true)
                } else {
                    val sheetBehavior = binding.filterBottomSheet.filterBottomSheet.sheetBehavior
                    if (!sheetBehavior.isExpanded() && !sheetBehavior.isSettling()) {
                        sheetBehavior?.expand()
                    } else {
                        showDisplayOptions()
                    }
                }
            }
            R.id.action_mode_toggle -> {
                // Save current category position for the current mode before switching
                val currentMode = ModeManager.currentMode.value
                when (currentMode) {
                    ContentType.MANGA -> preferences.lastUsedMangaCategory().set(activeCategory)
                    ContentType.NOVEL -> preferences.lastUsedNovelCategory().set(activeCategory)
                }
                
                // Get the anchor view for the circular reveal animation
                // Use multiple strategies to find the menu item view
                val toolbar = activityBinding?.toolbar
                val anchorView = toolbar?.let { tb ->
                    // First try direct findViewById on toolbar
                    tb.findViewById<View>(R.id.action_mode_toggle)
                        ?: run {
                            // Fallback: iterate toolbar children to find ActionMenuItemView
                            val actionMenuView = (0 until tb.childCount)
                                .map { tb.getChildAt(it) }
                                .find { it is androidx.appcompat.widget.ActionMenuView }
                                as? androidx.appcompat.widget.ActionMenuView
                            actionMenuView?.let { amv ->
                                (0 until amv.childCount)
                                    .map { amv.getChildAt(it) }
                                    .find { child -> child.id == R.id.action_mode_toggle }
                            }
                        }
                }
                
                // Get the root view for the animation
                val rootView = activityBinding?.mainContent ?: view?.parent as? ViewGroup
                
                if (rootView != null && activity != null) {
                    // Provide immediate visual feedback then animate
                    ThemeTransitionHelper.animateButtonPress(anchorView) {
                        ThemeTransitionHelper.animateThemeChange(
                            activity = activity!!,
                            anchorView = anchorView,
                            rootView = rootView,
                            onThemeChange = { ModeManager.toggleMode() },
                            duration = 250L  // Faster animation
                        )
                    }
                } else {
                    ModeManager.toggleMode()
                }
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
    //endregion

    //region Action Mode Methods
    /**
     * Creates the action mode if it's not created already.
     */
    private fun createActionModeIfNeeded() {
        if (actionMode == null) {
            actionMode = (activity as AppCompatActivity).startSupportActionMode(this)
            val view = activity?.window?.currentFocus ?: return
            val imm =
                activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    ?: return
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    fun showCategoriesController() {
        router.pushController(CategoryController().withFadeTransaction())
        displaySheet?.dismiss()
    }

    /**
     * Destroys the action mode.
     */
    private fun destroyActionModeIfNeeded() {
        actionMode?.finish()
    }

    /**
     * Invalidates the action mode, forcing it to refresh its content.
     */
    private fun invalidateActionMode() {
        actionMode?.invalidate()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.library_selection, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val mangaCount = selectedMangas.size
        val novelCount = selectedNovels.size
        val totalCount = mangaCount + novelCount
        val currentMode = ModeManager.currentMode.value
        
        // Destroy action mode if there are no items selected.
        val migrationItem = menu.findItem(R.id.action_migrate)
        val shareItem = menu.findItem(R.id.action_share)
        val categoryItem = menu.findItem(R.id.action_move_to_category)
        
        // Show category option only when there's more than one category
        categoryItem.isVisible = presenter.isCategoryMoreThanOne()
        
        // Migration and share only available for manga (novels don't support migration)
        if (currentMode == ContentType.MANGA) {
            migrationItem.isVisible = selectedMangas.any { it.source != LocalSource.ID }
            shareItem.isVisible = migrationItem.isVisible
        } else {
            // In novel mode, hide manga-specific options
            migrationItem.isVisible = false
            shareItem.isVisible = false
        }
        
        if (totalCount == 0) {
            destroyActionModeIfNeeded()
        } else {
            mode.title = view?.context?.getString(MR.strings.selected_, totalCount)
        }
        return false
    }
    //endregion

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_move_to_category -> showChangeMangaCategoriesSheet()
            R.id.action_share -> shareManga()
            R.id.action_delete -> {
                val options = arrayOf(
                    MR.strings.remove_downloads,
                    MR.strings.remove_from_library,
                )
                    .map { activity!!.getString(it) }
                activity!!.materialAlertDialog()
                    .setTitle(MR.strings.remove)
                    .setMultiChoiceItems(
                        options.toTypedArray(),
                        options.map { true }.toBooleanArray(),
                    ) { dialog, position, _ ->
                        if (position == 0) {
                            val listView = (dialog as AlertDialog).listView
                            listView.setItemChecked(position, true)
                        }
                    }
                    .setPositiveButton(MR.strings.remove) { dialog, _ ->
                        val listView = (dialog as AlertDialog).listView
                        if (listView.isItemChecked(1)) {
                            deleteMangasFromLibrary()
                        } else {
                            val mangas = selectedMangas.toList()
                            presenter.confirmDeletion(mangas, false)
                        }
                    }
                    .setNegativeButton(AR.string.cancel, null)
                    .show().apply {
                        disableItems(arrayOf(options.first()))
                    }
            }
            R.id.action_download_unread -> {
                presenter.downloadUnread(selectedMangas.toList())
            }
            R.id.action_mark_as_read -> {
                activity!!.materialAlertDialog()
                    .setMessage(MR.strings.mark_all_chapters_as_read)
                    .setPositiveButton(MR.strings.mark_as_read) { _, _ ->
                        markReadStatus(MR.strings.marked_as_read, true)
                    }
                    .setNegativeButton(AR.string.cancel, null)
                    .show()
            }
            R.id.action_mark_as_unread -> {
                activity!!.materialAlertDialog()
                    .setMessage(MR.strings.mark_all_chapters_as_unread)
                    .setPositiveButton(MR.strings.mark_as_unread) { _, _ ->
                        markReadStatus(MR.strings.marked_as_unread, false)
                    }
                    .setNegativeButton(AR.string.cancel, null)
                    .show()
            }
            R.id.action_migrate -> {
                val skipPre = preferences.skipPreMigration().get()
                PreMigrationController.navigateToMigration(
                    skipPre,
                    router,
                    selectedMangas.filter { !it.isLocal() }.mapNotNull { it.id },
                )
                destroyActionModeIfNeeded()
            }
            else -> return false
        }
        return true
    }

    private fun markReadStatus(resource: StringResource, markRead: Boolean) {
        val mapMangaChapters = presenter.markReadStatus(selectedMangas.toList(), markRead)
        destroyActionModeIfNeeded()
        snack?.dismiss()
        snack = view?.snack(resource, Snackbar.LENGTH_INDEFINITE) {
            anchorView = anchorView()
            view.elevation = 15f.dpToPx
            var undoing = false
            setAction(MR.strings.undo) {
                presenter.undoMarkReadStatus(mapMangaChapters)
                undoing = true
            }
            addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        super.onDismissed(transientBottomBar, event)
                        if (!undoing) {
                            presenter.confirmMarkReadStatus(
                                mapMangaChapters,
                                markRead,
                            )
                        }
                    }
                },
            )
        }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    private fun shareManga() {
        val context = view?.context ?: return
        val mangas = selectedMangas.toList()
        val urlList = presenter.getMangaUrls(mangas)
        if (urlList.isEmpty()) return
        val urls = presenter.getMangaUrls(mangas).joinToString("\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/*"
            putExtra(Intent.EXTRA_TEXT, urls)
        }
        startActivity(Intent.createChooser(intent, context.getString(MR.strings.share)))
    }

    open fun deleteMangasFromLibrary() {
        val currentMode = ModeManager.currentMode.value
        
        if (currentMode == ContentType.MANGA) {
            val mangas = selectedMangas.toList()
            presenter.removeMangaFromLibrary(mangas)
            destroyActionModeIfNeeded()
            snack?.dismiss()
            snack = view?.snack(
                activity?.getString(MR.strings.removed_from_library) ?: "",
                Snackbar.LENGTH_INDEFINITE,
            ) {
                anchorView = anchorView()
                view.elevation = 15f.dpToPx
                var undoing = false
                setAction(MR.strings.undo) {
                    presenter.reAddMangas(mangas)
                    undoing = true
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            if (!undoing) presenter.confirmDeletion(mangas)
                        }
                    },
                )
            }
            (activity as? MainActivity)?.setUndoSnackBar(snack)
        } else {
            // Novel mode - delete novels from library
            val novels = selectedNovels.toList()
            presenter.removeNovelsFromLibrary(novels)
            destroyActionModeIfNeeded()
            snack?.dismiss()
            snack = view?.snack(
                activity?.getString(MR.strings.removed_from_library) ?: "",
                Snackbar.LENGTH_INDEFINITE,
            ) {
                anchorView = anchorView()
                view.elevation = 15f.dpToPx
                var undoing = false
                setAction(MR.strings.undo) {
                    presenter.reAddNovels(novels)
                    undoing = true
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            if (!undoing) presenter.confirmNovelDeletion(novels)
                        }
                    },
                )
            }
            (activity as? MainActivity)?.setUndoSnackBar(snack)
        }
    }

    /**
     * Move the selected manga/novel to a list of categories.
     */
    private fun showChangeMangaCategoriesSheet() {
        val activity = activity ?: return
        val currentMode = ModeManager.currentMode.value
        
        viewScope.launchIO {
            if (currentMode == ContentType.MANGA) {
                selectedMangas.toList().moveCategories(activity) {
                    presenter.updateLibrary()
                    destroyActionModeIfNeeded()
                }
            } else {
                // Novel mode - show novel categories sheet
                selectedNovels.toList().moveNovelCategories(activity) {
                    presenter.updateLibrary()
                    destroyActionModeIfNeeded()
                }
            }
        }
    }
}
