package eu.kanade.tachiyomi.ui.source.browse

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExploreOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.touchlab.kermit.Logger
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.BrowseSourceControllerBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.novel.browse.filter.NovelFilterBottomSheet
import eu.kanade.tachiyomi.ui.novel.browse.filter.NovelFilterState
import eu.kanade.tachiyomi.ui.novel.browse.filter.ContentRating
import eu.kanade.tachiyomi.ui.novel.browse.filter.ContentWarning
import eu.kanade.tachiyomi.ui.novel.browse.filter.Genre
import eu.kanade.tachiyomi.ui.novel.browse.filter.NovelStatus
import eu.kanade.tachiyomi.ui.novel.browse.filter.SortOption
import eu.kanade.tachiyomi.ui.novel.browse.filter.SortOrder
import eu.kanade.tachiyomi.ui.novel.browse.filter.ViolenceLevel
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.source.BrowseController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.addOrRemoveToFavorites
import eu.kanade.tachiyomi.util.system.connectivityManager
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.e
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.setTextInput
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.applyBottomAnimatedInsets
import eu.kanade.tachiyomi.util.view.fullAppBarHeight
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setAction
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.setNegativeButton
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setTitle
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import eu.kanade.tachiyomi.widget.EmptyView
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager
import yokai.domain.manga.interactor.GetManga
import yokai.domain.novel.interactor.GetNovel
import yokai.domain.novel.interactor.InsertNovel
import yokai.domain.novel.interactor.UpdateNovel
import yokai.domain.novelchapter.interactor.InsertNovelChapter
import yokai.domain.novel.models.NovelUpdate
import yokai.domain.source.browse.filter.models.SavedSearch
import yokai.i18n.MR
import yokai.presentation.animation.extensions.applyDominoAnimation
import yokai.presentation.animation.extensions.resetAnimationState
import yokai.presentation.core.icons.CustomIcons
import yokai.presentation.core.icons.LocalSource
import yokai.util.lang.getString
import eu.kanade.tachiyomi.ui.animation.MorphTransitionCoordinator

/**
 * Controller to manage the catalogues available in the app.
 */
open class BrowseSourceController(bundle: Bundle) :
    BaseCoroutineController<BrowseSourceControllerBinding, BrowseSourcePresenter>(bundle),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FloatingSearchInterface,
    FlexibleAdapter.EndlessScrollListener {

    /**
     * 🔧 CRITICAL FIX: Buffer for page data that arrives before view is attached.
     * 
     * Race condition scenario:
     * 1. Pre-fetch calls restartPager() → starts pager coroutine
     * 2. Cache HIT returns data IMMEDIATELY (< 50ms)
     * 3. withUIContext { view?.onAddPage() } executes at 22:22:05.723
     * 4. View not created until 22:22:05.911 (188ms later)
     * 5. onAddPage() called on null view → data lost
     * 6. Progress spinner shows → infinite loading
     * 
     * Solution: Buffer page data and replay it when view attaches.
     * 
     * **Note:** This buffer is SEPARATE from spinner logic!
     * - `pendingPageData` solves view lifecycle race (data arrives before view ready)
     * - `presenter.isLoading` solves spinner timing race (replaces timer-based scheduling)
     * Both work together but serve different purposes.
     */
    private val pendingPageData = mutableListOf<Pair<Int, List<BrowseSourceItem>>>()
    private var isViewReady = false

    constructor(
        source: CatalogueSource,
        searchQuery: String? = null,
        smartSearchConfig: BrowseController.SmartSearchConfig? = null,
        useLatest: Boolean = false,
    ) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, source.id)

            if (searchQuery != null) {
                putString(SEARCH_QUERY_KEY, searchQuery)
            }

            if (smartSearchConfig != null) {
                putParcelable(SMART_SEARCH_CONFIG_KEY, smartSearchConfig)
            }
            putBoolean(USE_LATEST_KEY, useLatest)
        },
    )

    constructor(source: CatalogueSource) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, source.id)
        },
    )

    private val getManga: GetManga by injectLazy()
    private val getNovel: GetNovel by injectLazy()
    private val insertNovel: InsertNovel by injectLazy()
    private val updateNovel: UpdateNovel by injectLazy()
    private val insertNovelChapter: InsertNovelChapter by injectLazy()

    /**
     * Logger instance for debugging
     */
    private val logger = Logger.withTag("BrowseSourceController")

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Adapter containing the list of manga from the catalogue.
     */
    private var adapter: FlexibleAdapter<IFlexible<*>>? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null

    /**
     * Recycler view with the list of results.
     */
    private var recycler: RecyclerView? = null

    /**
     * Endless loading item.
     */
    private var progressItem: ProgressItem? = null

    /** Current filter sheet (for manga sources) */
    private var filterSheet: SourceFilterSheet? = null
    
    /** Current novel filter sheet (for novel sources) */
    private var novelFilterSheet: NovelFilterBottomSheet? = null
    
    /** Current novel filter state */
    private var novelFilterState = NovelFilterState()
    
    /** Persist which tab was last viewed in the novel filter sheet */
    private var novelFilterLastTab = 0
    
    private var lastPosition: Int = -1
    
    // Basically a cache just so the filter sheet is shown faster
    var savedSearches by mutableStateOf(emptyList<SavedSearch>())

    private val isBehindGlobalSearch: Boolean
        get() = router.backstackSize >= 2 && router.backstack[router.backstackSize - 2].controller is GlobalSearchController

    /** Watch for manga data changes */
    private var watchJob: Job? = null

    init {
        setHasOptionsMenu(true)
    }

    override val mainRecycler: RecyclerView?
        get() = recycler

    override fun getTitle(): String? {
        return if (presenter.sourceIsInitialized) presenter.source.name else null
    }

    override fun getSearchTitle(): String? {
        return if (presenter.sourceIsInitialized) searchTitle(presenter.source.name) else null
    }

    // disabling for now, one day maybe it will source icons will good
//    override fun getBigIcon(): Drawable? {
//        return presenter.source.icon()
//    }

    override val presenter = BrowseSourcePresenter(
        args.getLong(SOURCE_ID_KEY),
        args.getString(SEARCH_QUERY_KEY),
        args.getBoolean(USE_LATEST_KEY),
    )

    /**
     * 🚀 OPTIMIZATION: Pre-fetch data before view is created.
     * Called from BrowseController when user clicks a source, BEFORE navigation starts.
     * This allows network requests to run in parallel with the transition animation.
     */
    fun preFetchData() {
        logger.d { "🚀 [PRE-FETCH] Starting data pre-fetch BEFORE navigation..." }
        
        try {
            // Initialize the source if not already done
            if (!presenter.sourceIsInitialized) {
                val source = presenter.sourceManager.getOrStub(args.getLong(SOURCE_ID_KEY))
                if (source is CatalogueSource) {
                    presenter.source = source
                    logger.d { "🚀 [PRE-FETCH] Source initialized: ${source.name}" }
                    // CRITICAL: Set mode BEFORE view is created to avoid theme flash
                    if (source.isNovelSource()) {
                        ModeManager.setMode(ContentType.NOVEL)
                        logger.d { "🚀 [PRE-FETCH] Set mode to NOVEL for ${source.name}" }
                    } else {
                        ModeManager.setMode(ContentType.MANGA)
                        logger.d { "🚀 [PRE-FETCH] Set mode to MANGA for ${source.name}" }
                    }
                } else {
                    logger.w { "🚀 [PRE-FETCH] Source is not a CatalogueSource, skipping pre-fetch" }
                    return
                }
            }

            // Start loading the first page immediately
            // This will populate the pager's flow before the view is even created
            logger.d { "🚀 [PRE-FETCH] Calling presenter.restartPager() to start data loading..." }
            presenter.restartPager()
            logger.d { "🚀 [PRE-FETCH] Pre-fetch initiated successfully - data will arrive during transition" }
        } catch (e: Exception) {
            logger.e(e) { "🚀 [PRE-FETCH] Error during pre-fetch, will retry in onViewCreated" }
            // Don't throw - let onViewCreated handle it
        }
    }

    override fun createBinding(inflater: LayoutInflater): BrowseSourceControllerBinding {
        val binding = BrowseSourceControllerBinding.inflate(inflater)
        
        // PHASE 3: Remove XML background if morph animations enabled
        // This prevents the opaque background from blocking the view underneath
        // during the overlapping transition
        if (MorphTransitionCoordinator.isEnabled()) {
            logger.d { "🎨 [PHASE-3] Animations enabled - removing XML background for transparency" }
            binding.root.background = null
        } else {
            logger.d { "🎨 [PHASE-3] Animations disabled - keeping XML background (opaque)" }
        }
        
        return binding
    }

    override fun onViewCreated(view: View) {
        logger.d { "🚀 [NAVIGATION] onViewCreated START - Controller created" }
        val startTime = System.currentTimeMillis()
        
        // ✨ PHASE 2: Conditional Morph Transition
        // Apply transparent background ONLY if animations are enabled
        val savedBackground = MorphTransitionCoordinator.applyConditionalTransparency(view)
        
        if (MorphTransitionCoordinator.isEnabled()) {
            // ✅ MORPH MODE: Overlapping transition with transparency
            logger.d { "🎬 [MORPH] Animations enabled - using morph transition" }
            
            // Use doOnPreDraw to ensure layout is complete before animating
            view.doOnPreDraw {
                // Initial state: Transparent and slightly translated down
                view.alpha = 0f
                view.translationY = 40f // Start slightly below
                
                // Fade in + slide up animation (coordinated with source exit)
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(MorphTransitionCoordinator.MORPH_DURATION)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .setStartDelay(0) // Start immediately
                    .withEndAction {
                        // Restore background AFTER morph animation completes
                        MorphTransitionCoordinator.restoreBackground(view, savedBackground)
                        logger.d { "🎬 [MORPH] Animation complete, background restored" }
                    }
                    .start()
                
                logger.d { "🎬 [MORPH] Started fade-in + slide-up (${MorphTransitionCoordinator.MORPH_DURATION}ms)" }
            }
        } else {
            // ✅ STANDARD MODE: Simple fade (existing behavior)
            logger.d { "🎬 [STANDARD] Animations disabled - using simple fade" }
            
            // Keep view fully opaque and in position (no morph effect)
            view.alpha = 1f
            view.translationY = 0f
            // Background remains opaque (savedBackground is null, so no transparency)
            
            logger.d { "🎬 [STANDARD] Using instant appearance (no animation)" }
        }
        
        super.onViewCreated(view)
        logger.d { "🚀 [NAVIGATION] super.onViewCreated() completed in ${System.currentTimeMillis() - startTime}ms" }

        // Initialize adapter, scroll listener and recycler views
        logger.d { "🚀 [NAVIGATION] Creating FlexibleAdapter..." }
        adapter = FlexibleAdapter(null, this, false)
        logger.d { "🚀 [NAVIGATION] Setting up RecyclerView..." }
        setupRecycler(view)
        logger.d { "🚀 [NAVIGATION] RecyclerView setup completed in ${System.currentTimeMillis() - startTime}ms from start" }

        // Show filter FAB based on source type
        val isNovel = presenter.source.isNovelSource()
        if (isNovel) {
            // Novel sources always show filter FAB (universal filter system)
            binding.fab.isVisible = true
            binding.fab.text = activity?.getString(MR.strings.filter) ?: "Filter"
            binding.fab.setOnClickListener { showNovelFilters() }
        } else {
            // Manga sources show filter FAB only if they have filters
            binding.fab.isVisible = presenter.sourceFilters.isNotEmpty()
            binding.fab.setOnClickListener { showFilters() }
        }

        logger.d { "🚀 [NAVIGATION] Configuring app bar..." }
        activityBinding?.appBar?.y = 0f
        activityBinding?.appBar?.updateAppBarAfterY(recycler)
        activityBinding?.appBar?.lockYPos = true
        if (!presenter.sourceIsInitialized) {
            logger.w { "🚀 [NAVIGATION] Source not initialized, exiting" }
            activity?.toast(MR.strings.source_not_installed)
            if (activity is SearchActivity) {
                activity?.finish()
            } else {
                router.popCurrentController()
            }
            return
        }

        // ⏳ [PHASE 3] Loading state now driven by presenter.isLoading StateFlow
        // Timer-based spinner scheduling removed in favor of reactive state observation
        // See setupStateObservers() for implementation
        
        // Use traditional progress spinner for loading
        // Skeleton system removed in favor of morph-in-place transition
        binding.skeletonGrid.visibility = View.GONE
        
        // ⏳ [CRITICAL] Set up state observers BEFORE starting pager
        // This ensures the observer is active when pager triggers state changes
        setupStateObservers()
        
        // Start pager to load content
        presenter.restartPager()
        
        logger.d { "🚀 [NAVIGATION] onViewCreated COMPLETE - Total time: ${System.currentTimeMillis() - startTime}ms" }
    }

    override fun onDestroyView(view: View) {
        logger.d { "🔥 [LIFECYCLE] onDestroyView - Cleaning up controller" }
        
        // PHASE 2: Cancel any pending background restoration to prevent memory leaks
        MorphTransitionCoordinator.cancelBackgroundRestoration(view)
        
        adapter = null
        snack = null
        recycler = null
        super.onDestroyView(view)
        logger.d { "🔥 [LIFECYCLE] onDestroyView COMPLETE" }
    }

    override fun onAttach(view: View) {
        logger.d { "🔗 [LIFECYCLE] onAttach - Controller attached to view hierarchy" }
        super.onAttach(view)
        
        // 🔧 CRITICAL FIX: Replay presenter-buffered data (from pre-fetch)
        presenter.flushPendingData()
        
        // 🔧 LEGACY: Also replay any controller-buffered data (edge cases)
        isViewReady = true
        
        if (pendingPageData.isNotEmpty()) {
            logger.d { "🔗 [LIFECYCLE] Replaying ${pendingPageData.size} controller-buffered page(s)" }
            
            // Sort by page number to ensure correct order
            pendingPageData.sortedBy { it.first }.forEach { (page, mangas) ->
                logger.d { "🔗 [LIFECYCLE] Replaying page $page with ${mangas.size} items" }
                onAddPage(page, mangas)
            }
            pendingPageData.clear()
            logger.d { "🔗 [LIFECYCLE] Controller buffer replay complete" }
        }
        
        logger.d { "🔗 [LIFECYCLE] onAttach COMPLETE" }
    }

    override fun onDetach(view: View) {
        logger.d { "🔌 [LIFECYCLE] onDetach - Controller detached from view hierarchy" }
        isViewReady = false  // Mark view as not ready when detaching
        super.onDetach(view)
        logger.d { "🔌 [LIFECYCLE] onDetach COMPLETE" }
    }

    override fun handleBack(): Boolean {
        // Normal back navigation - no animation needed
        return false
    }

    override fun onChangeEnded(changeHandler: ControllerChangeHandler, changeType: ControllerChangeType) {
        logger.d { "🏁 [TRANSITION] onChangeEnded - Type: $changeType, Handler: ${changeHandler.javaClass.simpleName}" }
        super.onChangeEnded(changeHandler, changeType)
        logger.d { "🏁 [TRANSITION] onChangeEnded COMPLETE" }
    }

    private fun setupRecycler(view: View) {
        var oldPosition = RecyclerView.NO_POSITION
        var oldOffset = 0f
        val oldRecycler = binding.catalogueView.getChildAt(1)
        if (oldRecycler is RecyclerView) {
            oldPosition = (oldRecycler.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                .takeIf { it != RecyclerView.NO_POSITION }
                ?: (oldRecycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            oldOffset = oldRecycler.layoutManager?.findViewByPosition(oldPosition)?.y?.minus(oldRecycler.paddingTop) ?: 0f
            oldRecycler.adapter = null

            binding.catalogueView.removeView(oldRecycler)
        }

        val recycler = if (presenter.preferences.browseAsList().get()) {
            RecyclerView(view.context).apply {
                id = R.id.recycler
                layoutManager = LinearLayoutManagerAccurateOffset(context)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            }
        } else {
            (binding.catalogueView.inflate(R.layout.manga_recycler_autofit) as AutofitRecyclerView).apply {
                setGridSize(preferences)

                (layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (adapter?.getItemViewType(position)) {
                            R.layout.manga_grid_item, null -> 1
                            else -> spanCount
                        }
                    }
                }
            }
        }
        recycler.clipToPadding = false
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        binding.catalogueView.addView(recycler, 1)
        
        scrollViewWith(
            recycler,
            true,
            afterInsets = { insets ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    binding.fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = insets.getInsets(systemBars() or ime()).bottom + 16.dpToPx
                    }
                }
                val bigToolbarHeight = fullAppBarHeight ?: 0
                binding.progress.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = (bigToolbarHeight + insets.getInsets(systemBars()).top) / 2
                }
                binding.emptyView.updatePadding(
                    top = (bigToolbarHeight + insets.getInsets(systemBars()).top),
                    bottom = insets.getInsets(systemBars()).bottom,
                )
            },
        )
        
        // Apply domino grid animation strategy AFTER scrollViewWith
        // scrollViewWith sets its own ItemAnimator for app bar handling, so we override it here
        // Pass callback to update app bar position during animations
        recycler.applyDominoAnimation(
            isEnabled = { preferences.sourceOpeningAnimation().get() },
            onAnimationUpdate = { activityBinding?.appBar?.updateAppBarAfterY(recycler) }
        )
        
        binding.fab.applyBottomAnimatedInsets(16.dpToPx)

        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 && !binding.fab.isExtended) {
                        binding.fab.extend()
                    } else if (dy > 0 && binding.fab.isExtended) {
                        binding.fab.shrink()
                    }
                }
            },
        )

        if (oldPosition != RecyclerView.NO_POSITION) {
            (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(oldPosition, oldOffset.roundToInt())
            if (oldPosition > 0 && (activity as? MainActivity)?.currentToolbar != activityBinding?.searchToolbar) {
                activityBinding?.appBar?.useSearchToolbarForMenu(true)
            }
        }
        this.recycler = recycler
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.browse_source, menu)
        // Don't show mode toggle in source view - it's only relevant for the main browse screen
        // setupModeToggle(menu)

        // Initialize search menu
        val searchItem = activityBinding?.searchToolbar?.searchItem
        val searchView = activityBinding?.searchToolbar?.searchView

        activityBinding?.searchToolbar?.setQueryHint("", !isBehindGlobalSearch && presenter.query.isBlank())
        if (presenter.query.isNotBlank()) {
            searchItem?.expandActionView()
            searchView?.setQuery(presenter.query, true)
            searchView?.clearFocus()
        } else if (activityBinding?.searchToolbar?.isSearchExpanded == true) {
            searchItem?.collapseActionView()
            searchView?.setQuery("", true)
        }

        updatePopularLatestIcon(menu)

        setOnQueryTextChangeListener(searchView, onlyOnSubmit = true, hideKbOnSubmit = true) {
            searchWithQuery(it ?: "")
            true
        }
        // Show next display mode
        updateDisplayMenuItem(menu)
    }

    private fun updatePopularLatestIcon(menu: Menu?) {
        menu?.findItem(R.id.action_popular_latest)?.apply {
            val icon = if (!presenter.useLatest) {
                R.drawable.ic_new_releases_24dp
            } else {
                R.drawable.ic_heart_24dp
            }
            setIcon(icon)
            title = activity?.getString(if (!presenter.useLatest) {
                MR.strings.latest
            } else {
                MR.strings.popular
            })
        }
    }

    private fun updateDisplayMenuItem(menu: Menu?, isListMode: Boolean? = null) {
        menu?.findItem(R.id.action_display_mode)?.apply {
            val icon = if (isListMode ?: presenter.preferences.browseAsList().get()) {
                R.drawable.ic_view_module_24dp
            } else {
                R.drawable.ic_view_list_24dp
            }
            setIcon(icon)
        }
    }

    private fun setupModeToggle(menu: Menu) {
        val modeToggle = menu.findItem(R.id.action_mode_toggle) ?: return
        updateModeToggleIcon(modeToggle)
        
        // Observe mode changes and update UI accordingly
        viewScope.launch {
            ModeManager.currentMode.collectLatest { mode ->
                updateModeToggleIcon(modeToggle)
                updateBrowseTitle(mode)
                // No toast - mode changes should be silent to avoid interrupting navigation flow
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

    private fun updateBrowseTitle(mode: ContentType) {
        // Update title to reflect current mode
        // This is currently handled by the existing title system
    }

    override fun onActionViewCollapse(item: MenuItem?) {
        if (isBehindGlobalSearch) {
            router.popController(this)
        } else {
            searchWithQuery("")
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val isHttpSource = presenter.source is HttpSource
        menu.findItem(R.id.action_open_in_web_view).isVisible = isHttpSource
        val supportsLatest = (presenter.source as? CatalogueSource)?.supportsLatest == true
        menu.findItem(R.id.action_popular_latest).isVisible = supportsLatest

        val isLocalSource = presenter.source is LocalSource
        menu.findItem(R.id.action_local_source_help).isVisible = isLocalSource
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> expandActionViewFromInteraction = true
            R.id.action_display_mode -> swapDisplayMode()
            R.id.action_open_in_web_view -> openInWebView()
            R.id.action_local_source_help -> openLocalSourceHelpGuide()
            R.id.action_popular_latest -> swapPopularLatest()
            R.id.action_mode_toggle -> {
                ModeManager.toggleMode()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun applyFilters() {
        val allDefault = presenter.filtersMatchDefault()
        showProgressBar()
        adapter?.clear()
        presenter.setSourceFilter(if (allDefault) FilterList() else presenter.sourceFilters)
        updatePopLatestIcons()
    }

    private fun showFilters() {
        if (filterSheet != null) return
        val oldFilters = mutableListOf<Any?>()
        for (i in presenter.sourceFilters) {
            if (i is Filter.Group<*>) {
                val subFilters = mutableListOf<Any?>()
                for (j in i.state) {
                    subFilters.add((j as Filter<*>).state)
                }
                oldFilters.add(subFilters)
            } else {
                oldFilters.add(i.state)
            }
        }

        filterSheet = SourceFilterSheet(
            activity = activity!!,
            searches = { savedSearches },
            onSearchClicked = {
                var matches = true
                for (i in presenter.sourceFilters.indices) {
                    val filter = oldFilters.getOrNull(i)
                    if (filter is List<*>) {
                        for (j in filter.indices) {
                            if (filter[j] !=
                                (
                                    (presenter.sourceFilters[i] as Filter.Group<*>).state[j] as
                                        Filter<*>
                                    ).state
                            ) {
                                matches = false
                                break
                            }
                        }
                    } else if (filter != presenter.sourceFilters[i].state) {
                        matches = false
                        break
                    }
                    if (!matches) break
                }
                if (!matches) {
                    applyFilters()
                }
            },
            onResetClicked = {
                presenter.appliedFilters = FilterList()
                val newFilters = presenter.source.getFilterList()
                presenter.sourceFilters = newFilters
                filterSheet?.setFilters(presenter.filterItems)
            },
            onSaveClicked = {
                viewScope.launchIO {
                    val names = presenter.loadSearches().map { it.name }
                    var searchName = ""
                    withUIContext {
                        activity!!.materialAlertDialog()
                            .setTitle(activity!!.getString(MR.strings.save_search))
                            .setTextInput(hint = activity!!.getString(MR.strings.save_search_hint)) { input ->
                                searchName = input
                            }
                            .setPositiveButton(MR.strings.save) { _, _ ->
                                if (searchName.isNotBlank() && searchName !in names) {
                                    presenter.saveSearch(searchName.trim(), presenter.query, presenter.sourceFilters)
                                    filterSheet?.scrollToTop()
                                } else {
                                    activity!!.toast(MR.strings.save_search_invalid_name)
                                }
                            }
                            .setNegativeButton(MR.strings.cancel, null)
                            .show()
                    }
                }
            },
            onSavedSearchClicked = ss@{ searchId ->
                viewScope.launchIO {
                    val search = presenter.loadSearch(searchId)  // Grab the latest data from database
                    if (search?.filters == null) return@launchIO

                    withUIContext {
                        presenter.sourceFilters = search.filters
                        filterSheet?.setFilters(presenter.filterItems)
                        // This will call onSaveClicked()
                        filterSheet?.dismiss()
                    }
                }
            },
            onDeleteSavedSearchClicked = { searchId ->
                activity!!.materialAlertDialog()
                    .setTitle(MR.strings.save_search_delete)
                    .setMessage(MR.strings.save_search_delete)
                    .setPositiveButton(MR.strings.cancel, null)
                    .setNegativeButton(android.R.string.ok) { _, _ -> presenter.deleteSearch(searchId) }
                    .show()
            }
        )
        filterSheet?.setFilters(presenter.filterItems)
        presenter.filtersChanged = false

        filterSheet?.setOnCancelListener { filterSheet = null }
        filterSheet?.setOnDismissListener { filterSheet = null }

        filterSheet?.show()
    }

    /**
     * Shows the novel filter bottom sheet.
     * This provides a universal filter system for novel sources.
     */
    private fun showNovelFilters() {
        val activity = activity ?: return
        
        // Get capabilities from the source if it's a NovelSourceWrapper
        val capabilities = (presenter.source as? eu.kanade.tachiyomi.source.novel.NovelSourceWrapper)
            ?.novelProvider?.capabilities 
            ?: yokai.source.novel.model.NovelProviderCapabilities()
        
        novelFilterSheet = NovelFilterBottomSheet(
            activity = activity,
            currentState = novelFilterState,
            initialTab = novelFilterLastTab,
            sourceCapabilities = capabilities,
            onFilterApplied = { newState ->
                novelFilterState = newState
                // Save which tab user was on
                novelFilterLastTab = novelFilterSheet?.getCurrentTab() ?: 0
                applyNovelFilters(newState)
                novelFilterSheet = null
            },
            onResetClicked = {
                novelFilterState = NovelFilterState()
            }
        )
        
        // Save tab position when sheet is dismissed without applying
        novelFilterSheet?.setOnDismissListener {
            novelFilterLastTab = novelFilterSheet?.getCurrentTab() ?: novelFilterLastTab
            novelFilterSheet = null
        }
        
        novelFilterSheet?.show()
    }

    /**
     * Applies the selected novel filters to the search.
     * This translates our universal filter state into source-specific FilterList.
     */
    private fun applyNovelFilters(filterState: NovelFilterState) {
        showProgressBar()
        adapter?.clear()
        
        logger.d { "🔍 [NOVEL FILTER] Applying filters: sort=${filterState.sortBy}, genres=${filterState.genres.size}, status=${filterState.status}, contentWarnings=${filterState.includedContentWarnings.size}" }
        
        // Build FilterList from NovelFilterState
        // The NovelSourceWrapper will convert this to Map<String, String> for the provider
        val filters = mutableListOf<Filter<*>>()
        
        // Add sort filter
        val sortOptions = SortOption.entries.map { it.displayName }.toTypedArray()
        val sortIndex = SortOption.entries.indexOf(filterState.sortBy)
        val ascending = filterState.sortOrder == SortOrder.ASCENDING
        filters.add(object : Filter.Sort("Sort By", sortOptions, Selection(sortIndex, ascending)) {})
        
        // Add genre filter as a Group of TriState filters
        val genreFilters = Genre.allGenres.map { genre ->
            object : Filter.TriState(genre.displayName) {
                init {
                    state = when {
                        filterState.excludedGenres.contains(genre) -> STATE_EXCLUDE
                        filterState.genres.contains(genre) -> STATE_INCLUDE
                        else -> STATE_IGNORE
                    }
                }
            }
        }
        filters.add(object : Filter.Group<Filter.TriState>("Genres", genreFilters) {})
        
        // Add content warnings filter as a Group of TriState filters
        val contentWarningFilters = ContentWarning.entries.map { warning ->
            object : Filter.TriState(warning.displayName) {
                init {
                    state = when {
                        filterState.excludedContentWarnings.contains(warning) -> STATE_EXCLUDE
                        filterState.includedContentWarnings.contains(warning) -> STATE_INCLUDE
                        else -> STATE_IGNORE
                    }
                }
            }
        }
        filters.add(object : Filter.Group<Filter.TriState>("Content Warnings", contentWarningFilters) {})
        
        // Add status filter
        val statusOptions = NovelStatus.entries.map { it.displayName }.toTypedArray()
        val statusIndex = NovelStatus.entries.indexOf(filterState.status)
        filters.add(object : Filter.Select<String>("Status", statusOptions, statusIndex) {})
        
        // Add content rating filter  
        val contentRatingOptions = ContentRating.entries.map { it.displayName }.toTypedArray()
        val contentRatingIndex = ContentRating.entries.indexOf(filterState.contentRating)
        filters.add(object : Filter.Select<String>("Content Rating", contentRatingOptions, contentRatingIndex) {})
        
        // Add violence level filter
        val violenceOptions = ViolenceLevel.entries.map { it.displayName }.toTypedArray()
        val violenceIndex = ViolenceLevel.entries.indexOf(filterState.violenceLevel)
        filters.add(object : Filter.Select<String>("Violence Level", violenceOptions, violenceIndex) {})
        
        // Add text filters for min/max chapters and rating
        filterState.minChapters?.let { min ->
            filters.add(object : Filter.Text("Min Chapters", min.toString()) {})
        }
        filterState.maxChapters?.let { max ->
            filters.add(object : Filter.Text("Max Chapters", max.toString()) {})
        }
        filterState.minRating?.let { rating ->
            filters.add(object : Filter.Text("Min Rating (0-5)", rating.toString()) {})
        }
        
        val filterList = FilterList(filters)
        logger.d { "🔍 [NOVEL FILTER] Created FilterList with ${filterList.size} filters" }
        
        // Use setSourceFilter to properly set filtersChanged flag and restart pager
        // This ensures BrowseSourcePager uses getSearchManga instead of getPopularManga
        presenter.setSourceFilter(filterList)
    }

    /**
     * Attempts to restart the request with a new genre-filtered query.
     * If the genre name can't be found the filters,
     * the standard searchWithQuery search method is used instead.
     *
     * @param genreName the name of the genre
     */
    fun searchWithGenre(genreName: String, useContains: Boolean = false) {
        presenter.sourceFilters = presenter.source.getFilterList()

        var filterList: FilterList? = null

        filter@ for (sourceFilter in presenter.sourceFilters) {
            if (sourceFilter is Filter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is Filter<*> &&
                        if (useContains) {
                            filter.name.contains(genreName, true)
                        } else {
                            filter.name.equals(genreName, true)
                        }
                    ) {
                        when (filter) {
                            is Filter.TriState -> filter.state = 1
                            is Filter.CheckBox -> filter.state = true
                            else -> break
                        }
                        filterList = presenter.sourceFilters
                        break@filter
                    }
                }
            } else if (sourceFilter is Filter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst {
                        if (useContains) {
                            it.contains(genreName, true)
                        } else {
                            it.equals(genreName, true)
                        }
                    }

                if (index != -1) {
                    sourceFilter.state = index
                    filterList = presenter.sourceFilters
                    break
                }
            }
        }

        if (filterList != null) {
            filterSheet?.setFilters(presenter.filterItems)

            showProgressBar()

            adapter?.clear()
            presenter.restartPager("", filterList)
        } else {
            if (!useContains) {
                searchWithGenre(genreName, true)
                return
            }
            searchWithQuery(genreName)
        }
    }

    private fun openInWebView() {
        val source = presenter.source as? HttpSource ?: return
        val activity = activity ?: return
        val intent = WebViewActivity.newIntent(
            activity,
            source.baseUrl,
            source.id,
            source.name,
        )
        startActivity(intent)
    }

    private fun openLocalSourceHelpGuide() {
        activity?.openInBrowser(LocalSource.HELP_URL)
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        logger.d { "🎬 [TRANSITION] onChangeStarted - Type: $type, Handler: ${handler.javaClass.simpleName}" }
        super.onChangeStarted(handler, type)
        if (type == ControllerChangeType.POP_ENTER) {
            // Returning to this controller - refresh favorite states
            logger.d { "🎬 [TRANSITION] POP_ENTER detected - refreshing favorite states" }
            refreshFavoriteStates()
            
            if (lastPosition > -1) {
                logger.d { "🎬 [TRANSITION] Resetting lastPosition: $lastPosition" }
                adapter?.notifyItemChanged(lastPosition, false)
                lastPosition = -1
            }
        }
        logger.d { "🎬 [TRANSITION] onChangeStarted COMPLETE" }
    }
    
    /**
     * Refresh the favorite status of all items in the adapter.
     * Called when returning to this controller (POP_ENTER) to ensure
     * visual favorite states match the database.
     */
    private fun refreshFavoriteStates() {
        val adapter = adapter ?: return
        val items = adapter.currentItems.filterIsInstance<BrowseSourceItem>()
        if (items.isEmpty()) return
        
        logger.d { "🔄 [REFRESH] Refreshing favorite states for ${items.size} items" }
        
        viewScope.launchIO {
            items.forEachIndexed { index, item ->
                val manga = item.manga
                val isNovelSource = presenter.source.isNovelSource()
                
                val currentFavorite = if (isNovelSource) {
                    // Check novel database for favorite status
                    val novel = getNovel.awaitByUrlAndSource(manga.url, manga.source)
                    novel?.isFavorite ?: false
                } else {
                    // Check manga database for favorite status
                    val dbManga = presenter.getMangaFromDb(manga.url, manga.source)
                    dbManga?.favorite ?: false
                }
                
                // Update if different
                if (manga.favorite != currentFavorite) {
                    logger.d { "🔄 [REFRESH] Updating favorite state for '${manga.title}': ${manga.favorite} -> $currentFavorite" }
                    manga.favorite = currentFavorite
                    withUIContext {
                        adapter.notifyItemChanged(adapter.currentItems.indexOf(item))
                    }
                }
            }
            logger.d { "🔄 [REFRESH] Favorite states refresh complete" }
        }
    }

    /**
     * Restarts the request with a new query.
     *
     * @param newQuery the new query.
     */
    private fun searchWithQuery(newQuery: String) {
        // If text didn't change, do nothing
        if (presenter.query == newQuery) {
            return
        }

        showProgressBar()
        adapter?.clear()
        
        // Reset animation state to allow items to animate on new search
        recycler?.resetAnimationState()

        presenter.restartPager(newQuery)
        updatePopLatestIcons()
    }

    /**
     * Called from the presenter when the network request is received.
     *
     * @param page the current page.
     * @param mangas the list of manga of the page.
     */
    fun onAddPage(page: Int, mangas: List<BrowseSourceItem>) {
        logger.d { "📦 [DATA] onAddPage called - Page: $page, Items: ${mangas.size}, ViewReady: $isViewReady" }
        
        //  CRITICAL FIX: Buffer data if view isn't ready yet
        if (!isViewReady) {
            logger.d { "📦 [DATA] View not ready - buffering page $page with ${mangas.size} items" }
            pendingPageData.add(page to mangas)
            return
        }
        
        val adapter = adapter ?: return
        
        logger.d { "📦 [DATA] Hiding progress bar..." }
        hideProgressBar()
        if (page == 1) {
            logger.d { "📦 [DATA] First page - clearing adapter and resetting animation state" }
            adapter.clear()
            resetProgressItem()
            
            // Reset animation state to allow first page items to animate
            recycler?.resetAnimationState()
            
            // Ensure recycler is visible (skeleton system removed)
            recycler?.visibility = View.VISIBLE
            recycler?.alpha = 1f
        }
        logger.d { "📦 [DATA] Adding ${mangas.size} items to adapter..." }
        adapter.onLoadMoreComplete(mangas)
        logger.d { "📦 [DATA] Items added to adapter, unlocking app bar" }
        if (isControllerVisible) {
            activityBinding?.appBar?.lockYPos = false
        }
        logger.d { "📦 [DATA] onAddPage COMPLETE" }
    }

    /**
     * Called from the presenter when the network request fails.
     *
     * @param error the error received.
     */
    fun onAddPageError(error: Throwable) {
        Logger.e(error)
        val adapter = adapter ?: return
        adapter.onLoadMoreComplete(null)
        hideProgressBar()

        snack?.dismiss()

        val message = getErrorMessage(error)
        val retryAction = {
            // If not the first page, show bottom binding.progress bar.
            if (adapter.mainItemCount > 0 && progressItem != null) {
                adapter.addScrollableFooterWithDelay(progressItem!!, 0, true)
            } else {
                showProgressBar()
            }
            presenter.requestNext()
        }

        if (adapter.isEmpty) {
            val actions = emptyList<EmptyView.Action>().toMutableList()

            actions += if (presenter.source is LocalSource) {
                EmptyView.Action(
                    MR.strings.local_source_help_guide,
                ) { openLocalSourceHelpGuide() }
            } else {
                EmptyView.Action(MR.strings.retry, retryAction)
            }

            if (presenter.source is HttpSource) {
                actions += EmptyView.Action(
                    MR.strings.open_in_webview,
                ) { openInWebView() }
            }

            binding.emptyView.show(
                if (presenter.source is HttpSource) {
                    Icons.Filled.ExploreOff
                } else {
                    CustomIcons.LocalSource
                },
                message,
                actions,
            )
        } else {
            snack = binding.sourceLayout.snack(message, Snackbar.LENGTH_INDEFINITE) {
                setAction(MR.strings.retry) { retryAction() }
            }
        }
        if (isControllerVisible) {
            activityBinding?.appBar?.lockYPos = false
        }
    }

    private fun getErrorMessage(error: Throwable): String {
        if (error is NoResultsException) {
            return activity!!.getString(MR.strings.no_results_found)
        }

        return when {
            error.message == null -> ""
            error.message!!.startsWith("HTTP error") -> "${error.message}: ${activity!!.getString(MR.strings.check_site_in_web)}"
            else -> error.message!!
        }
    }

    /**
     * Sets a new binding.progress item and reenables the scroll listener.
     */
    private fun resetProgressItem() {
        progressItem = ProgressItem()
        adapter?.endlessTargetCount = 0
        adapter?.setEndlessScrollListener(this, progressItem!!)
    }

    /**
     * Called by the adapter when scrolled near the bottom.
     */
    override fun onLoadMore(lastPosition: Int, currentPage: Int) {
        if (presenter.hasNextPage()) {
            presenter.requestNext()
        } else {
            adapter?.onLoadMoreComplete(null)
            adapter?.endlessTargetCount = 1
        }
    }

    override fun noMoreLoad(newItemsSize: Int) {
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the manga initialized
     */
    fun onMangaInitialized(manga: Manga) {
        getHolder(manga.id!!)?.setImage(manga)
    }

    /**
     * Swaps the current display mode.
     */
    private fun swapDisplayMode() {
        val view = view ?: return
        val adapter = adapter ?: return

        val isListMode = !presenter.preferences.browseAsList().get()
        presenter.preferences.browseAsList().set(isListMode)
        listOf(activityBinding?.toolbar?.menu, activityBinding?.searchToolbar?.menu).forEach {
            updateDisplayMenuItem(it, isListMode)
        }
        setupRecycler(view)
        // Initialize mangas if not on a metered connection
        if (!view.context.connectivityManager.isActiveNetworkMetered) {
            val mangas = (0 until adapter.itemCount).mapNotNull {
                (adapter.getItem(it) as? BrowseSourceItem)?.manga
            }
            presenter.initializeMangas(mangas)
        }
    }

    private fun swapPopularLatest() {
        val adapter = adapter ?: return

        presenter.useLatest = !presenter.useLatest
        showProgressBar()
        adapter.clear()
        updatePopLatestIcons()

        val searchItem = activityBinding?.searchToolbar?.searchItem
        searchItem?.collapseActionView()

        presenter.appliedFilters = FilterList()
        val newFilters = presenter.source.getFilterList()
        presenter.sourceFilters = newFilters
        presenter.filtersChanged = false

        presenter.restartPager("")
    }

    private fun updatePopLatestIcons() {
        listOf(activityBinding?.toolbar?.menu, activityBinding?.searchToolbar?.menu).forEach {
            updatePopularLatestIcon(it)
        }
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param manga the manga to find.
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(mangaId: Long): BrowseSourceHolder? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.flexibleAdapterPosition) as? BrowseSourceItem
            if (item != null && item.mangaId == mangaId) {
                return holder as BrowseSourceHolder
            }
        }

        return null
    }

    /**
     * Shows the binding.progress bar.
     */
    private fun showProgressBar() {
        binding.emptyView.isVisible = false
        binding.progress.isVisible = true
        snack?.dismiss()
        snack = null
    }

    /**
     * Hides active binding.progress bars.
     */
    private fun hideProgressBar() {
        binding.emptyView.isVisible = false
        binding.progress.isVisible = false
    }

    fun unsubscribe() {
        watchJob?.cancel()
        watchJob = null
    }

    /**
     * Workaround to fix data state de-sync issues when controller detached,
     * and attaching flow directly into Item caused some flickering issues.
     *
     * FIXME: Could easily be fixed by migrating to Compose.
     */
    private fun BrowseSourceItem.subscribe() {
        watchJob?.cancel()
        watchJob = viewScope.launch {
            getManga.subscribeByUrlAndSource(manga.url, manga.source).collectLatest {
                if (it == null) return@collectLatest
                val holder = getHolder(mangaId) ?: return@collectLatest
                updateManga(holder, it)
            }
        }
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(view: View?, position: Int): Boolean {
        val item = adapter?.getItem(position) as? BrowseSourceItem ?: return false
        item.subscribe()
        
        android.util.Log.d("BrowseSourceController", "=== NOVEL CLICK START ===")
        android.util.Log.d("BrowseSourceController", "Position: $position")
        android.util.Log.d("BrowseSourceController", "Item manga.id: ${item.manga.id}")
        android.util.Log.d("BrowseSourceController", "Item manga.title: '${item.manga.title}'")
        android.util.Log.d("BrowseSourceController", "Item manga.url: '${item.manga.url}'")
        android.util.Log.d("BrowseSourceController", "Source type: ${presenter.source?.javaClass?.simpleName}")
        
        // Mode Inheritance: Route to correct details controller based on source type
        if (presenter.source is eu.kanade.tachiyomi.source.novel.NovelSourceWrapper) {
            android.util.Log.d("BrowseSourceController", "Detected NovelSourceWrapper - converting to Novel")
            // Novel source - convert Manga to Novel and navigate
            val manga = item.manga
            val novel = yokai.domain.novel.Novel(
                id = manga.id ?: 0L,
                source = manga.source,
                url = manga.url,
                title = manga.title,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                status = manga.status,
                posterUrl = manga.thumbnail_url,
                isFavorite = manga.favorite,
                lastUpdate = manga.last_update,
                initialized = manga.initialized
            )
            android.util.Log.d("BrowseSourceController", "Created Novel object:")
            android.util.Log.d("BrowseSourceController", "  - novel.id: ${novel.id}")
            android.util.Log.d("BrowseSourceController", "  - novel.title: '${novel.title}'")
            android.util.Log.d("BrowseSourceController", "  - novel.url: '${novel.url}'")
            android.util.Log.d("BrowseSourceController", "  - novel.source: ${novel.source}")
            
            // Use NovelDetailsController directly with fromSource flag
            android.util.Log.d("BrowseSourceController", "Pushing NovelDetailsControllerNew with fromSource=true")
            router.pushController(
                eu.kanade.tachiyomi.ui.novel.details.NovelDetailsControllerNew(novel, true).withFadeTransaction()
            )
            android.util.Log.d("BrowseSourceController", "=== NOVEL CLICK END ===")
        } else {
            // Manga source - use ContentRouter
            router.pushController(MangaDetailsController(item.manga, true).withFadeTransaction())
        }
        
        lastPosition = position
        return false
    }

    /**
     * Animate skeleton grid fading out as RecyclerView items slide in.
     * Each skeleton fades synchronized with the corresponding item's domino animation.
     */
    private fun animateSkeletonToContent() {
        logger.d { "💀 [SKELETON] Starting skeleton-to-content transition..." }
        logger.d { "💀 [SKELETON] BEFORE animation - recycler?.visibility = ${recycler?.visibility}, skeletonGrid.visibility = ${binding.skeletonGrid.visibility}" }
        
        // 🔍 CAUSE #4: Track visibility race condition
        logger.w { "⚠️ [VISIBILITY-RACE] T=0ms: BOTH VIEWS STATE - Skeleton: ${binding.skeletonGrid.visibility}, RecyclerView: ${recycler?.visibility}" }
        
        // Fade in RecyclerView
        recycler?.visibility = View.VISIBLE
        recycler?.alpha = 0f
        logger.d { "💀 [SKELETON] AFTER setting recycler visible - recycler?.visibility = ${recycler?.visibility}, alpha = ${recycler?.alpha}" }
        logger.w { "⚠️ [VISIBILITY-RACE] Both views now VISIBLE! Skeleton will hide in ~440ms, RecyclerView fading in over 200ms" }
        
        recycler?.animate()
            ?.alpha(1f)
            ?.setDuration(200)
            ?.withStartAction {
                logger.d { "💀 [SKELETON] RecyclerView fade animation STARTED" }
                logger.w { "⚠️ [VISIBILITY-RACE] T=0ms: RecyclerView fade START (0→1 over 200ms)" }
                // Synchronize skeleton fade with domino animation
                syncSkeletonFadeWithDomino()
            }
            ?.withEndAction {
                logger.d { "💀 [SKELETON] RecyclerView fade animation COMPLETED - recycler now fully visible" }
                logger.w { "⚠️ [VISIBILITY-RACE] T=200ms: RecyclerView now FULLY VISIBLE (alpha=1), skeleton still visible!" }
            }
            ?.start()
    }
    
    /**
     * Fade each skeleton item synchronized with RecyclerView domino animation.
     * Creates the "slotting into place" effect.
     */
    private fun syncSkeletonFadeWithDomino() {
        val skeletonCount = binding.skeletonGrid.getItemCount()
        
        // 🔧 FIX: Always fade all skeleton items regardless of adapter content
        // The adapter is often empty (0 items) when this is called, causing no fades
        val itemsToFade = skeletonCount
        
        logger.d { "💀 [SKELETON] Syncing $itemsToFade skeleton fades with domino animation (skeleton count: $skeletonCount)" }
        
        // 🔍 COMPREHENSIVE STATE DUMP
        logViewHierarchyState("FADE_START")
        
        logger.d { "💀 [SKELETON] catalogueView children count: ${binding.catalogueView.childCount}" }
        for (i in 0 until binding.catalogueView.childCount) {
            val child = binding.catalogueView.getChildAt(i)
            logger.d { "💀 [SKELETON] Child $i during fade: ${child::class.simpleName}, visibility: ${child.visibility}, alpha: ${child.alpha}" }
        }
        
        // Fade each skeleton with 20ms stagger (matching domino animation)
        var successfulFades = 0
        for (i in 0 until itemsToFade) {
            val delay = i * 20L // Match domino 20ms stagger
            val viewHolder = binding.skeletonGrid.getViewHolderAt(i)
            if (viewHolder != null) {
                viewHolder.fadeOut(delay, 150)
                successfulFades++
            } else {
                logger.w { "💀 [SKELETON] ViewHolder at position $i is null - skeleton RecyclerView not laid out yet" }
            }
        }
        
        logger.d { "💀 [SKELETON] Successfully started $successfulFades/$itemsToFade skeleton fade animations" }
        
        // Hide skeleton grid after all animations complete
        val totalDuration = (itemsToFade * 20L) + 150L
        
        // 🔍 CAUSE #6: Track if hide() callback is dropped
        logger.w { "⚠️ [STUCK-SKELETON] Scheduling hide() in ${totalDuration}ms. View attached: ${view != null}, isAttached: $isAttached" }
        
        view?.postDelayed({
            logger.d { "💀 [SKELETON] BEFORE hide - skeletonGrid.visibility = ${binding.skeletonGrid.visibility}" }
            logger.w { "⚠️ [STUCK-SKELETON] hide() callback EXECUTED after ${totalDuration}ms. View still attached: ${view != null}, isAttached: $isAttached" }
            binding.skeletonGrid.hide()
            logger.d { "💀 [SKELETON] AFTER hide - skeletonGrid.visibility = ${binding.skeletonGrid.visibility}" }
            logger.w { "⚠️ [VISIBILITY-RACE] T=${totalDuration}ms: Skeleton NOW HIDDEN, race window closed" }
            logger.d { "💀 [SKELETON] Skeleton grid hidden after synchronized fade" }
            
            // 🔍 FINAL STATE DUMP
            logViewHierarchyState("COMPLETE")
            
            logger.d { "💀 [SKELETON] Final state - catalogueView children count: ${binding.catalogueView.childCount}" }
            for (i in 0 until binding.catalogueView.childCount) {
                val child = binding.catalogueView.getChildAt(i)
                logger.d { "💀 [SKELETON] Final child $i: ${child::class.simpleName}, visibility: ${child.visibility}" }
            }
        }, totalDuration)
    }
    
    /**
     * 🔍 DIAGNOSTIC: Log complete view hierarchy state for debugging
     */
    private fun logViewHierarchyState(phase: String) {
        logger.w { "═══════════════════════════════════════════════════════════" }
        logger.w { "📊 [VIEW-HIERARCHY-$phase] Complete state snapshot:" }
        logger.w { "  catalogueView (FrameLayout):" }
        logger.w { "    visibility: ${binding.catalogueView.visibility}" }
        logger.w { "    alpha: ${binding.catalogueView.alpha}" }
        logger.w { "    childCount: ${binding.catalogueView.childCount}" }
        
        for (i in 0 until binding.catalogueView.childCount) {
            val child = binding.catalogueView.getChildAt(i)
            logger.w { "    [$i] ${child::class.simpleName}:" }
            logger.w { "        visibility: ${child.visibility} (0=VISIBLE, 4=INVISIBLE, 8=GONE)" }
            logger.w { "        alpha: ${child.alpha}" }
            logger.w { "        bounds: [${child.left},${child.top}] to [${child.right},${child.bottom}]" }
            
            if (child is RecyclerView) {
                logger.w { "        itemCount: ${child.adapter?.itemCount ?: 0}" }
                logger.w { "        childCount: ${child.childCount}" }
            }
        }
        
        logger.w { "  Skeleton Grid:" }
        logger.w { "    visibility: ${binding.skeletonGrid.visibility}" }
        logger.w { "    alpha: ${binding.skeletonGrid.alpha}" }
        logger.w { "    itemCount: ${binding.skeletonGrid.getItemCount()}" }
        
        logger.w { "  Content RecyclerView:" }
        logger.w { "    visibility: ${recycler?.visibility}" }
        logger.w { "    alpha: ${recycler?.alpha}" }
        logger.w { "    itemCount: ${adapter?.itemCount ?: 0}" }
        logger.w { "═══════════════════════════════════════════════════════════" }
    }

    /**
     * Called when a manga is long clicked.
     *
     * Adds the manga to the default category if none is set it shows a list of categories for the user to put the manga
     * in, the list consists of the default category plus the user's categories. The default category is preselected on
     * new manga, and on already favorited manga the manga's categories are preselected.
     *
     * @param position the position of the element clicked.
     */
    override fun onItemLongClick(position: Int) {
        val manga = (adapter?.getItem(position) as? BrowseSourceItem?)?.manga ?: return
        val view = view ?: return
        val activity = activity ?: return
        
        android.util.Log.d("BrowseSourceController", "onItemLongClick: position=$position, manga=${manga.title}, source=${presenter.source.name}")
        android.util.Log.d("BrowseSourceController", "onItemLongClick: isNovelSource=${presenter.source.isNovelSource()}")
        
        // Check if this is a novel source
        if (presenter.source.isNovelSource()) {
            android.util.Log.d("BrowseSourceController", "=== NOVEL LONG-PRESS ADD START ===")
            android.util.Log.d("BrowseSourceController", "onItemLongClick: Handling as novel source")
            // Handle novel favoriting
            viewScope.launchIO {
                try {
                    withUIContext { snack?.dismiss() }
                    
                    android.util.Log.d("BrowseSourceController", "onItemLongClick: manga.id=${manga.id}, manga.favorite=${manga.favorite}")
                    android.util.Log.d("BrowseSourceController", "onItemLongClick: manga.url=${manga.url}")
                    android.util.Log.d("BrowseSourceController", "onItemLongClick: manga.source=${manga.source}")
                    android.util.Log.d("BrowseSourceController", "onItemLongClick: manga.title=${manga.title}")
                    
                    // IMPORTANT: First check if this novel already exists in DB by URL and source
                    val existingNovel = getNovel.awaitByUrlAndSource(manga.url, manga.source)
                    android.util.Log.d("BrowseSourceController", "onItemLongClick: existingNovel=${existingNovel?.id}, existingNovel.isFavorite=${existingNovel?.isFavorite}")
                    
                    if (existingNovel != null) {
                        // Novel already exists in DB - toggle favorite
                        android.util.Log.d("BrowseSourceController", "onItemLongClick: Novel exists in DB with id=${existingNovel.id}")
                        val newFavoriteStatus = !existingNovel.isFavorite
                        android.util.Log.d("BrowseSourceController", "onItemLongClick: Setting isFavorite to $newFavoriteStatus")
                        
                        updateNovel.await(NovelUpdate(
                            id = existingNovel.id,
                            inLibrary = newFavoriteStatus,
                            dateAdded = if (newFavoriteStatus) System.currentTimeMillis() else 0L
                        ))
                        
                        android.util.Log.d("BrowseSourceController", "onItemLongClick: updateNovel.await completed")
                        
                        // Update the manga object to reflect the change in UI
                        manga.favorite = newFavoriteStatus
                        manga.date_added = if (newFavoriteStatus) System.currentTimeMillis() else 0L
                        
                        withUIContext {
                            android.util.Log.d("BrowseSourceController", "onItemLongClick: Showing snackbar for favorite=$newFavoriteStatus")
                            adapter?.notifyItemChanged(position)
                            snack = if (newFavoriteStatus) {
                                view.snack(MR.strings.added_to_library)
                            } else {
                                view.snack(MR.strings.removed_from_library)
                            }
                        }
                    } else {
                        // Novel doesn't exist - insert it as favorite AND fetch full data
                        android.util.Log.d("BrowseSourceController", "onItemLongClick: Novel NOT in DB - inserting new")
                        android.util.Log.d("BrowseSourceController", "onItemLongClick: manga data dump:")
                        android.util.Log.d("BrowseSourceController", "  - title='${manga.title}'")
                        android.util.Log.d("BrowseSourceController", "  - author='${manga.author}'")
                        android.util.Log.d("BrowseSourceController", "  - description='${manga.description?.take(100)}'")
                        android.util.Log.d("BrowseSourceController", "  - genre='${manga.genre}'")
                        android.util.Log.d("BrowseSourceController", "  - status=${manga.status}")
                        android.util.Log.d("BrowseSourceController", "  - thumbnail_url='${manga.thumbnail_url}'")
                        android.util.Log.d("BrowseSourceController", "  - initialized=${manga.initialized}")
                        
                        // Show loading toast
                        withUIContext {
                            snack = view.snack(MR.strings.adding_to_library)
                        }
                        
                        // Use title from manga, or fallback to URL if title is blank
                        val novelTitle = if (manga.title.isNotBlank()) manga.title else "Unknown"
                        
                        // Create initial novel with basic data
                        var newNovel = yokai.domain.novel.Novel(
                            id = 0L, // Will be assigned by DB
                            source = manga.source,
                            url = manga.url,
                            title = novelTitle,
                            author = manga.author,
                            description = manga.description,
                            genre = manga.genre,
                            status = manga.status,
                            posterUrl = manga.thumbnail_url,
                            isFavorite = true, // Adding to library
                            lastUpdate = manga.last_update,
                            initialized = false, // Will be initialized after fetch
                            dateAdded = System.currentTimeMillis()
                        )
                        
                        android.util.Log.d("BrowseSourceController", "onItemLongClick: newNovel created with title='${newNovel.title}'")
                        android.util.Log.d("BrowseSourceController", "onItemLongClick: Calling insertNovel.await()")
                        val insertedId = insertNovel.await(newNovel)
                        android.util.Log.d("BrowseSourceController", "onItemLongClick: insertNovel returned id=$insertedId")
                        
                        // Update novel with correct ID
                        newNovel = newNovel.copy(id = insertedId)
                        
                        // Now fetch full details and chapters from source
                        try {
                            val source = presenter.source
                            android.util.Log.d("BrowseSourceController", "onItemLongClick: Fetching full details from source")
                            
                            // Fetch novel details from source
                            val fetchedDetails = source.getMangaDetails(manga)
                            android.util.Log.d("BrowseSourceController", "onItemLongClick: Fetched details - title='${fetchedDetails.title}', desc='${fetchedDetails.description?.take(50)}'")
                            
                            // Update novel with fetched details
                            updateNovel.await(NovelUpdate(
                                id = insertedId,
                                title = fetchedDetails.title,
                                author = fetchedDetails.author,
                                description = fetchedDetails.description,
                                posterUrl = fetchedDetails.thumbnail_url,
                                status = fetchedDetails.status,
                                genres = fetchedDetails.genre?.split(",")?.map { it.trim() },
                                initialized = true
                            ))
                            android.util.Log.d("BrowseSourceController", "onItemLongClick: Updated novel with fetched details")
                            
                            // Fetch chapters from source
                            android.util.Log.d("BrowseSourceController", "onItemLongClick: Fetching chapters from source")
                            val fetchedChapters = source.getChapterList(manga)
                            android.util.Log.d("BrowseSourceController", "onItemLongClick: Fetched ${fetchedChapters.size} chapters")
                            
                            if (fetchedChapters.isNotEmpty()) {
                                // Convert to domain chapters and insert
                                val chaptersToInsert = fetchedChapters.mapIndexed { index, sChapter ->
                                    yokai.domain.novel.NovelChapter(
                                        novelId = insertedId,
                                        url = sChapter.url,
                                        title = sChapter.name,
                                        chapterNumber = sChapter.chapter_number.toDouble(),
                                        volumeNumber = null,
                                        sourceOrder = index,
                                        dateFetch = System.currentTimeMillis(),
                                        dateUpload = sChapter.date_upload,
                                        translator = sChapter.scanlator
                                    )
                                }
                                insertNovelChapter.awaitBulk(chaptersToInsert)
                                android.util.Log.d("BrowseSourceController", "onItemLongClick: Inserted ${chaptersToInsert.size} chapters to DB")
                            }
                            
                        } catch (e: Exception) {
                            // Fetching details/chapters failed - novel is still added but without full data
                            android.util.Log.w("BrowseSourceController", "onItemLongClick: Failed to fetch full data: ${e.message}")
                            // Continue - novel is still added to library
                        }
                        
                        // Update the manga object to reflect the change in UI
                        manga.favorite = true
                        manga.date_added = System.currentTimeMillis()
                        
                        withUIContext {
                            android.util.Log.d("BrowseSourceController", "onItemLongClick: Showing success snackbar")
                            adapter?.notifyItemChanged(position)
                            snack = view.snack(MR.strings.added_to_library)
                        }
                    }
                    android.util.Log.d("BrowseSourceController", "=== NOVEL LONG-PRESS ADD END ===")
                } catch (e: Exception) {
                    android.util.Log.e("BrowseSourceController", "onItemLongClick: Error during novel favoriting", e)
                    withUIContext {
                        snack = view.snack("Error: ${e.message}")
                    }
                }
            }
        } else {
            // Handle manga favoriting (existing code)
            viewScope.launchIO {
                withUIContext { snack?.dismiss() }
                snack = manga.addOrRemoveToFavorites(
                    preferences,
                    view,
                    activity,
                    presenter.sourceManager,
                    this@BrowseSourceController,
                    onMangaAdded = {
                        adapter?.notifyItemChanged(position)
                        snack = view.snack(MR.strings.added_to_library)
                    },
                    onMangaMoved = { adapter?.notifyItemChanged(position) },
                    onMangaDeleted = { presenter.confirmDeletion(manga) },
                    scope = viewScope,
                )
                if (snack?.duration == Snackbar.LENGTH_INDEFINITE) {
                    withUIContext {
                        (activity as? MainActivity)?.setUndoSnackBar(snack)
                    }
                }
            }
        }
    }

    /**
     * Animates content appearing with fade effect.
     * Timeline: Starts immediately, uses RecyclerView's DominoGridStrategy
     */
    fun animateContentIn() {
        val recyclerView = recycler ?: return
        
        // Hide progress spinner immediately
        binding.progress.isVisible = false
        
        // Make RecyclerView visible (items will animate in via DominoGridStrategy)
        recyclerView.visibility = View.VISIBLE
        
        // The domino animation timing is controlled by DominoGridStrategy via AnimationConfig.DOMINO_GRID
        // which has been tuned to match the snapshot slide-out speed (400ms delay increment, 600ms duration)
        
        // FAB fade + scale (if filters available)
        if (binding.fab.isVisible) {
            binding.fab.alpha = 0f
            binding.fab.scaleX = 0.8f
            binding.fab.scaleY = 0.8f
            
            binding.fab.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(200)
                .setDuration(200)
                .start()
        }
    }
    
    /**
     * Callback to be invoked when content is loaded and ready to animate in.
     * Set by BrowseController to synchronize snapshot slide-out with source fade-in.
     */
    var onContentReadyCallback: (() -> Unit)? = null

    /**
     * Animates content disappearing (reverse fade).
     * Timeline: Fades out over 300ms
     */
    fun animateContentOut() {
        val recyclerView = recycler ?: return
        
        // FAB fade + scale out
        if (binding.fab.isVisible) {
            binding.fab.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(200)
                .start()
        }
        
        // Recycler fade out (slower)
        recyclerView.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                recyclerView.visibility = View.GONE
            }
            .start()
    }
    
    /**
     * Set up reactive loading state observation.
     * 
     * **Architecture: State-Driven Loading (Replaces Timer-Based Approach)**
     * 
     * **Old Approach (Removed):**
     * ```kotlin
     * // ❌ Race condition: Scheduled at t=0ms, fires at t=300ms
     * view.postDelayed({ binding.progress.isVisible = true }, 300)
     * // Problem: Data might arrive at t=50ms, spinner still shows at t=300ms!
     * ```
     * 
     * **New Approach (Current):**
     * ```kotlin
     * // ✅ Reactive: UI updates immediately when state changes
     * presenter.isLoading.collectLatest { binding.progress.isVisible = it }
     * // Benefit: Cache hits never trigger spinner (pager reports instant completion)
     * ```
     * 
     * **Flow:**
     * 1. User opens source → `presenter.restartPager()` sets `isLoading = true`
     * 2. **Cache hit path:** Pager finds cache → sets `isLoading = false` (t=~50ms)
     *    - Result: Spinner NEVER shows (state change before first frame render)
     * 3. **Network fetch path:** Pager fetches → sets `isLoading = false` after response
     *    - Result: Spinner shows appropriately during network request
     * 
     * **Pattern:** Follows existing `ModeManager.currentMode.collectLatest` pattern (line 541)
     * 
     * @see BrowseSourcePresenter.isLoading for state source
     * @see setupModeToggle for similar StateFlow observation pattern
     */
    private fun setupStateObservers() {
        presenter.presenterScope.launch {
            presenter.isLoading.collectLatest { isLoading ->
                withUIContext {
                    binding?.progress?.isVisible = isLoading
                    logger.d { "⏳ [STATE] UI received loading state: $isLoading" }
                    
                    // CRITICAL: Trigger animation callback when content finishes loading
                    // This allows BrowseController to wait for actual content before starting fade-in
                    if (!isLoading) {
                        logger.d { "🎬 [SYNC] Content loaded, triggering animation callback" }
                        onContentReadyCallback?.invoke()
                        onContentReadyCallback = null  // One-time callback
                    }
                }
            }
        }
    }

    companion object {
        const val SOURCE_ID_KEY = "sourceId"

        const val SEARCH_QUERY_KEY = "searchQuery"
        const val USE_LATEST_KEY = "useLatest"
        const val SMART_SEARCH_CONFIG_KEY = "smartSearchConfig"
    }
}
