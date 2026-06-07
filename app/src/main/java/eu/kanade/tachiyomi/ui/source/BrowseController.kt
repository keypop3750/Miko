package eu.kanade.tachiyomi.ui.source

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Build
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.activity.BackEventCompat
import androidx.appcompat.widget.SearchView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnNextLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.RecyclerView
import co.touchlab.kermit.Logger
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.BrowseControllerBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.animation.MorphTransitionCoordinator
import eu.kanade.tachiyomi.ui.base.controller.BaseLegacyController
import eu.kanade.tachiyomi.ui.extension.ExtensionFilterController
import eu.kanade.tachiyomi.ui.main.BottomSheetController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsBrowseController
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsSourcesController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.util.system.dpToPx
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager
import eu.kanade.tachiyomi.util.system.getBottomGestureInsets
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.spToPx
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.checkHeightThen
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isCompose
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.onAnimationsFinished
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setAction
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.toolbarHeight
import eu.kanade.tachiyomi.util.view.updateGradiantBGRadius
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.util.view.withMorphTransition
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import uy.kohesive.injekt.injectLazy
import yokai.domain.base.BasePreferences
import yokai.domain.base.BasePreferences.ExtensionInstaller
import yokai.i18n.MR
import yokai.presentation.browse.BrowseScreen
import yokai.presentation.browse.BrowseSourceItem
import yokai.presentation.browse.BrowseViewModel
import yokai.presentation.extension.repo.ExtensionRepoController
import yokai.presentation.theme.YokaiTheme
import yokai.util.lang.getString
import java.util.*
import kotlin.math.max

/**
 * This controller shows and manages the different catalogues enabled by the user.
 * Uses Compose for the source list to enable reactive theme updates on mode changes.
 * [SourceAdapter.SourceListener] call function data on browse item click.
 */
class BrowseController :
    BaseLegacyController<BrowseControllerBinding>(),
    FlexibleAdapter.OnItemClickListener,
    SourceAdapter.SourceListener,
    RootSearchInterface,
    FloatingSearchInterface,
    BottomSheetController {

    private val logger = Logger.withTag("BrowseController")
    
    private val basePreferences: BasePreferences by injectLazy()

    /**
     * Application preferences.
     */
    private val preferences: PreferencesHelper by injectLazy()
    
    /**
     * ViewModel for Compose-based source list.
     * Exposes sources as StateFlow for reactive Compose observation.
     */
    private val browseViewModel: BrowseViewModel by lazy { BrowseViewModel() }

    companion object {
        const val HELP_URL = "https://tachiyomi.org/docs/guides/source-migration"
    }

    /**
     * Adapter containing sources (kept for compatibility, but unused with Compose).
     */
    private var adapter: SourceAdapter? = null

    var extQuery = ""
        private set

    var headerHeight = 0
    
    // StateFlow for Compose to observe header height changes
    private val headerHeightState = MutableStateFlow(0)

    var showingExtensions = false

    var snackbar: Snackbar? = null

    private var ogRadius = 0f
    private var deviceRadius = 0f to 0f
    private var lastScale = 1f

    override val mainRecycler: RecyclerView
        get() = binding.sourceRecycler

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? = view?.context?.getString(MR.strings.browse)

    override fun getSearchTitle(): String? {
        return searchTitle(view?.context?.getString(MR.strings.sources)?.lowercase(Locale.ROOT))
    }

    val presenter = SourcePresenter(this)

    override fun createBinding(inflater: LayoutInflater) = BrowseControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        
        val isReturning = adapter != null
        adapter = SourceAdapter(this)
        // Create binding.sourceRecycler and set adapter.
        binding.sourceRecycler.layoutManager = LinearLayoutManagerAccurateOffset(view.context)

        binding.sourceRecycler.adapter = adapter
        binding.sourceRecycler.onAnimationsFinished {
            (activity as? MainActivity)?.splashState?.ready = true
        }
        adapter?.isSwipeEnabled = true
        adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        scrollViewWith(
            binding.sourceRecycler,
            afterInsets = {
                headerHeight = binding.sourceRecycler.paddingTop
                binding.sourceRecycler.updatePaddingRelative(
                    bottom = (activityBinding?.bottomNav?.height ?: it.getBottomGestureInsets()) + 58.spToPx,
                )
                if (activityBinding?.bottomNav == null) {
                    setBottomPadding()
                }
                deviceRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val wInsets = it.toWindowInsets()
                    val lCorner = wInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                    val rCorner = wInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                    (lCorner?.radius?.toFloat() ?: 0f) to (rCorner?.radius?.toFloat() ?: 0f)
                } else {
                    ogRadius to ogRadius
                }
            },
            onBottomNavUpdate = {
                setBottomPadding()
            },
        )
        if (!isReturning) {
            activityBinding?.appBar?.lockYPos = true
        }
        binding.sourceRecycler.post {
            setBottomSheetTabs(if (binding.bottomSheet.root.sheetBehavior.isCollapsed()) 0f else 1f)
            binding.sourceRecycler.updatePaddingRelative(
                bottom = (activityBinding?.bottomNav?.height ?: 0) + 58.spToPx,
            )
            updateTitleAndMenu()
        }

        binding.bottomSheet.root.onCreate(this)
        
        // Update bottom sheet state for current mode (initial state only)
        updateBottomSheetForMode()
        
        // Mode observer for source list and bottom sheet updates
        viewScope.launch {
            yokai.core.mode.ModeManager.currentMode
                .drop(1) // Skip initial value (already handled above)
                .collectLatest { mode ->
                    // Update sources for new mode
                    presenter.updateSources()
                    // Update bottom sheet
                    updateBottomSheetForMode()
                }
        }

        basePreferences.extensionInstaller().changes()
            .drop(1)
            .onEach {
                binding.bottomSheet.root.setCanInstallPrivately(it == ExtensionInstaller.PRIVATE)
            }
            .launchIn(viewScope)

        binding.bottomSheet.root.sheetBehavior?.isGestureInsetBottomIgnored = true

        binding.bottomSheet.root.sheetBehavior?.addBottomSheetCallback(
            object : BottomSheetBehavior
            .BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, progress: Float) {
                    val oldShow = showingExtensions
                    showingExtensions = progress > 0.92f
                    if (oldShow != showingExtensions) {
                        updateTitleAndMenu()
                        (activity as? MainActivity)?.reEnableBackPressedCallBack()
                    }
                    binding.bottomSheet.root.apply {
                        if (lastScale != 1f && scaleY != 1f) {
                            val scaleProgress = ((1f - progress) * (1f - lastScale)) + lastScale
                            scaleX = scaleProgress
                            scaleY = scaleProgress
                            for (i in 0 until childCount) {
                                val childView = getChildAt(i)
                                childView.scaleY = scaleProgress
                            }
                        }
                    }
                    binding.bottomSheet.sheetToolbar.isVisible = true
                    setBottomSheetTabs(max(0f, progress))
                }

                override fun onStateChanged(p0: View, state: Int) {
                    if (state == BottomSheetBehavior.STATE_SETTLING) {
                        binding.bottomSheet.root.updatedNestedRecyclers()
                    } else if (state == BottomSheetBehavior.STATE_EXPANDED && binding.bottomSheet.root.isExpanding) {
                        binding.bottomSheet.root.updatedNestedRecyclers()
                        binding.bottomSheet.root.isExpanding = false
                    }

                    binding.bottomSheet.root.apply {
                        if ((
                            state == BottomSheetBehavior.STATE_COLLAPSED ||
                                state == BottomSheetBehavior.STATE_EXPANDED ||
                                state == BottomSheetBehavior.STATE_HIDDEN
                            ) &&
                            scaleY != 1f
                        ) {
                            scaleX = 1f
                            scaleY = 1f
                            pivotY = 0f
                            translationX = 0f
                            for (i in 0 until childCount) {
                                val childView = getChildAt(i)
                                childView.scaleY = 1f
                            }
                            lastScale = 1f
                        }
                    }

                    val extBottomSheet = binding.bottomSheet.root
                    if (state == BottomSheetBehavior.STATE_EXPANDED ||
                        state == BottomSheetBehavior.STATE_COLLAPSED
                    ) {
                        binding.bottomSheet.root.sheetBehavior?.isDraggable = true
                        showingExtensions = state == BottomSheetBehavior.STATE_EXPANDED
                        binding.bottomSheet.sheetToolbar.isVisible = showingExtensions
                        updateTitleAndMenu()
                        if (state == BottomSheetBehavior.STATE_EXPANDED) {
                            extBottomSheet.fetchOnlineExtensionsIfNeeded()
                        } else {
                            extBottomSheet.shouldCallApi = true
                        }
                    }

                    retainViewMode = if (state == BottomSheetBehavior.STATE_EXPANDED) {
                        RetainViewMode.RETAIN_DETACH
                    } else {
                        RetainViewMode.RELEASE_DETACH
                    }
                    binding.bottomSheet.sheetLayout.isClickable = state == BottomSheetBehavior.STATE_COLLAPSED
                    binding.bottomSheet.sheetLayout.isFocusable = state == BottomSheetBehavior.STATE_COLLAPSED
                    if (state == BottomSheetBehavior.STATE_COLLAPSED || state == BottomSheetBehavior.STATE_EXPANDED) {
                        setBottomSheetTabs(if (state == BottomSheetBehavior.STATE_COLLAPSED) 0f else 1f)
                    }
                }
            },
        )

        if (showingExtensions) {
            binding.bottomSheet.root.sheetBehavior?.expand()
        }
        ogRadius = view.resources.getDimension(R.dimen.rounded_radius)

        setSheetToolbar()
        presenter.onCreate()
        if (presenter.sourceItems.isNotEmpty()) {
            setSources(presenter.sourceItems, presenter.lastUsedItem)
        } else {
            binding.sourceRecycler.checkHeightThen {
                binding.sourceRecycler.scrollToPosition(0)
            }
        }
    }

    private fun updateSheetMenu() {
        binding.bottomSheet.sheetToolbar.title =
            if (binding.bottomSheet.tabs.selectedTabPosition != 0) {
                binding.bottomSheet.root.currentSourceTitle
                    ?: view?.context?.getString(MR.strings.source_migration)
            } else {
                view?.context?.getString(MR.strings.extensions)
            }
        val onExtensionTab = binding.bottomSheet.tabs.selectedTabPosition == 0
        if (binding.bottomSheet.sheetToolbar.menu.findItem(if (onExtensionTab) R.id.action_search else R.id.action_migration_guide) != null) {
            return
        }
        val oldSearchView = binding.bottomSheet.sheetToolbar.menu.findItem(R.id.action_search)?.actionView as? SearchView
        oldSearchView?.setOnQueryTextListener(null)
        binding.bottomSheet.sheetToolbar.menu.clear()
        binding.bottomSheet.sheetToolbar.inflateMenu(
            if (binding.bottomSheet.tabs.selectedTabPosition == 0) {
                R.menu.extension_main
            } else {
                R.menu.migration_main
            },
        )

        val id = when (PreferenceValues.MigrationSourceOrder.fromPreference(preferences)) {
            PreferenceValues.MigrationSourceOrder.Alphabetically -> R.id.action_sort_alpha
            PreferenceValues.MigrationSourceOrder.MostEntries -> R.id.action_sort_largest
            PreferenceValues.MigrationSourceOrder.Obsolete -> R.id.action_sort_obsolete
        }
        binding.bottomSheet.sheetToolbar.menu.findItem(id)?.isChecked = true

        // Initialize search option.
        binding.bottomSheet.sheetToolbar.menu.findItem(R.id.action_search)?.let { searchItem ->
            val searchView = searchItem.actionView as SearchView

            // Change hint to show global search.
            searchView.queryHint = view?.context?.getString(MR.strings.search_extensions)
            if (extQuery.isNotEmpty()) {
                searchView.setOnQueryTextListener(null)
                searchItem.expandActionView()
                searchView.setQuery(extQuery, true)
                searchView.clearFocus()
            } else {
                searchItem.collapseActionView()
            }
            // Create query listener which opens the global search view.
            setOnQueryTextChangeListener(searchView) {
                extQuery = it ?: ""
                binding.bottomSheet.root.drawExtensions()
                true
            }
        }
    }

    private fun setSheetToolbar() {
        binding.bottomSheet.sheetToolbar.setOnMenuItemClickListener { item ->
            val sorting = when (item.itemId) {
                R.id.action_sort_alpha -> PreferenceValues.MigrationSourceOrder.Alphabetically
                R.id.action_sort_largest -> PreferenceValues.MigrationSourceOrder.MostEntries
                R.id.action_sort_obsolete -> PreferenceValues.MigrationSourceOrder.Obsolete
                else -> null
            }
            if (sorting != null) {
                preferences.migrationSourceOrder().set(sorting.value)
                binding.bottomSheet.root.presenter.refreshMigrations()
                item.isChecked = true
                return@setOnMenuItemClickListener true
            }
            when (item.itemId) {
                // Initialize option to open catalogue settings.
                R.id.action_filter -> {
                    router.pushController(ExtensionFilterController().withFadeTransaction())
                }
                R.id.action_migration_guide -> {
                    activity?.openInBrowser(HELP_URL)
                }
                R.id.action_sources_settings -> {
                    router.pushController(SettingsBrowseController().withFadeTransaction())
                }
                R.id.action_extension_repos_settings -> {
                    router.pushController(ExtensionRepoController().withFadeTransaction())
                }
            }
            return@setOnMenuItemClickListener true
        }
        binding.bottomSheet.sheetToolbar.setNavigationOnClickListener {
            binding.bottomSheet.root.sheetBehavior?.collapse()
        }
        updateSheetMenu()
    }

    fun updateTitleAndMenu() {
        if (isControllerVisible) {
            val activity = (activity as? MainActivity) ?: return
            (activity as? MainActivity)?.setStatusBarColorTransparent(showingExtensions)
            updateSheetMenu()
        }
    }

    fun setBottomSheetTabs(progress: Float) {
        val bottomSheet = binding.bottomSheet.root
        val halfStepProgress = (max(0.5f, progress) - 0.5f) * 2
        binding.bottomSheet.tabs.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = (
                (
                    activityBinding?.appBar?.paddingTop
                        ?.minus(9f.dpToPx)
                        ?.plus(toolbarHeight ?: 0) ?: 0f
                    ) * halfStepProgress
                ).toInt()
        }
        binding.bottomSheet.pill.alpha = (1 - progress) * 0.25f
        binding.bottomSheet.sheetToolbar.alpha = progress
        
        // Send progress to BrowseViewModel so Compose YokaiSearchBar can fade
        browseViewModel.updateSheetProgress(progress)

        binding.bottomSheet.root.updateGradiantBGRadius(
            ogRadius,
            deviceRadius,
            progress,
            binding.bottomSheet.sheetLayout,
        )

        val selectedColor = ColorUtils.setAlphaComponent(
            bottomSheet.context.getResourceColor(R.attr.tabBarIconColor),
            (progress * 255).toInt(),
        )
        val unselectedColor = ColorUtils.setAlphaComponent(
            bottomSheet.context.getResourceColor(R.attr.actionBarTintColor),
            153,
        )
        binding.bottomSheet.pager.alpha = progress * 10
        binding.bottomSheet.tabs.setSelectedTabIndicatorColor(selectedColor)
        binding.bottomSheet.tabs.setTabTextColors(
            ColorUtils.blendARGB(
                bottomSheet.context.getResourceColor(R.attr.actionBarTintColor),
                unselectedColor,
                progress,
            ),
            ColorUtils.blendARGB(
                bottomSheet.context.getResourceColor(R.attr.actionBarTintColor),
                selectedColor,
                progress,
            ),
        )

        /*binding.bottomSheet.sheetLayout.backgroundTintList = ColorStateList.valueOf(
            ColorUtils.blendARGB(
                bottomSheet.context.getResourceColor(R.attr.colorPrimaryVariant),
                bottomSheet.context.getResourceColor(R.attr.colorSurface),
                progress
            )
        )*/
    }

    private fun setBottomPadding() {
        val bottomBar = activityBinding?.bottomNav
        val pad = bottomBar?.translationY?.minus(bottomBar.height) ?: 0f
        val padding = max(
            (-pad).toInt(),
            view?.rootWindowInsetsCompat?.getBottomGestureInsets() ?: 0,
        )
        binding.bottomSheet.root.sheetBehavior?.peekHeight = 56.spToPx + padding
        binding.bottomSheet.root.extensionFrameLayout?.binding?.fastScroller?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = -pad.toInt()
        }
        binding.bottomSheet.root.migrationFrameLayout?.binding?.fastScroller?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = -pad.toInt()
        }
        binding.sourceRecycler.updatePaddingRelative(
            bottom = (
                activityBinding?.bottomNav?.height
                    ?: view?.rootWindowInsetsCompat?.getBottomGestureInsets() ?: 0
                ) + 58.spToPx,
        )
    }

    override fun showSheet() {
        if (!isBindingInitialized) return
        binding.bottomSheet.root.sheetBehavior?.expand()
    }

    override fun hideSheet() {
        if (!isBindingInitialized) return
        binding.bottomSheet.root.sheetBehavior?.collapse()
    }
    
    /**
     * Update the bottom sheet based on current mode.
     * In novel mode, the bottom sheet stays visible but shows novel extensions.
     */
    private fun updateBottomSheetForMode() {
        if (!isBindingInitialized) return
        // Bottom sheet stays visible in both modes - the tabs will show mode-appropriate content
        // The ExtensionBottomSheet will handle mode-awareness internally
        binding.bottomSheet.root.updateForMode()
    }

    override fun toggleSheet() {
        if (!binding.bottomSheet.root.sheetBehavior.isCollapsed()) {
            binding.bottomSheet.root.sheetBehavior?.collapse()
        } else {
            binding.bottomSheet.root.sheetBehavior?.expand()
        }
    }

    override fun canStillGoBack(): Boolean = showingExtensions

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        if (showingExtensions && !binding.bottomSheet.root.canStillGoBack()) {
            binding.bottomSheet.root.sheetBehavior?.startBackProgress(backEvent)
        }
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        if (showingExtensions && !binding.bottomSheet.root.canStillGoBack()) {
            binding.bottomSheet.root.sheetBehavior?.updateBackProgress(backEvent)
        } else {
            super.handleOnBackProgressed(backEvent)
        }
    }

    override fun handleOnBackCancelled() {
        if (showingExtensions && !binding.bottomSheet.root.canStillGoBack()) {
            binding.bottomSheet.root.sheetBehavior?.cancelBackProgress()
        } else {
            super.handleOnBackCancelled()
        }
    }

    override fun handleBack(): Boolean {
        if (showingExtensions) {
            if (binding.bottomSheet.root.canGoBack()) {
                lastScale = binding.bottomSheet.root.scaleX
                binding.bottomSheet.root.sheetBehavior?.collapse()
            }
            return true
        }
        return false
    }

    override fun onDestroyView(view: View) {
        adapter = null
        binding.bottomSheet.root.onDestroy()
        super.onDestroyView(view)
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (!type.isPush) {
            binding.bottomSheet.root.updateExtTitle()
            binding.bottomSheet.root.presenter.refreshExtensions()
            presenter.updateSources()
            if (type.isEnter && isControllerVisible) {
                updateSheetMenu()
            }
        }
        if (!type.isEnter) {
            binding.bottomSheet.root.canExpand = false
            activityBinding?.appBar?.alpha = 1f
            // Show app bar when leaving Browse
            activityBinding?.appBar?.isVisible = true
            activityBinding?.appBar?.isInvisible = router.isCompose
            binding.bottomSheet.sheetToolbar.menu.findItem(R.id.action_search)?.let { searchItem ->
                val searchView = searchItem.actionView as SearchView
                searchView.clearFocus()
            }
        } else {
            binding.bottomSheet.root.presenter.refreshMigrations()
            updateTitleAndMenu()
        }
        setBottomPadding()
    }

    override fun onChangeEnded(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeEnded(handler, type)
        if (type.isEnter) {
            binding.bottomSheet.root.canExpand = true
            setBottomPadding()
            updateTitleAndMenu()
            // Notify ViewModel that we're visible - triggers YokaiTheme SideEffect to update status bar
            browseViewModel.onControllerVisible()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (!isBindingInitialized) return
        binding.bottomSheet.root.presenter.refreshExtensions()
        binding.bottomSheet.root.presenter.refreshMigrations()
        setBottomPadding()
        if (showingExtensions) {
            updateSheetMenu()
        }
    }

    override fun onItemClick(view: View, position: Int): Boolean {
        // Legacy adapter click - no longer used with Compose source list
        val item = adapter?.getItem(position) as? SourceItem ?: return false
        val source = item.source
        openCatalogue(source, BrowseSourceController(source))
        return false
    }

    fun hideCatalogue(position: Int) {
        // Legacy method - kept for interface compatibility
        val source = (adapter?.getItem(position) as? SourceItem)?.source ?: return
        hideSource(source.id)
    }
    
    /**
     * Hide a source by ID. Used by both legacy adapter and Compose UI.
     */
    private fun hideSource(sourceId: Long) {
        val current = preferences.hiddenSources().get()
        preferences.hiddenSources().set(current + sourceId.toString())
        
        // ViewModel will automatically refresh sources
        browseViewModel.hideSource(sourceId)

        snackbar = view?.snack(MR.strings.source_hidden, Snackbar.LENGTH_INDEFINITE) {
            anchorView = binding.bottomSheet.root
            setAction(MR.strings.undo) {
                val newCurrent = preferences.hiddenSources().get()
                preferences.hiddenSources().set(newCurrent - sourceId.toString())
                browseViewModel.unhideSource(sourceId)
            }
        }
        (activity as? MainActivity)?.setUndoSnackBar(snackbar)
    }

    private fun pinCatalogue(source: Source, isPinned: Boolean) {
        // Use ViewModel which handles preference updates and source refresh
        browseViewModel.pinSource(source.id)
    }

    /**
     * Called when browse is clicked in [SourceAdapter]
     * Legacy method for adapter compatibility.
     */
    override fun onPinClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return
        val isPinned = item.isPinned ?: item.header?.code?.equals(SourcePresenter.PINNED_KEY)
            ?: false
        pinCatalogue(item.source, isPinned)
    }

    /**
     * Called when latest is clicked in [SourceAdapter]
     * Legacy method for adapter compatibility.
     */
    override fun onLatestClick(position: Int) {
        val item = adapter?.getItem(position) as? SourceItem ?: return
        openCatalogue(item.source, BrowseSourceController(item.source, useLatest = true))
    }

    /**
     * Opens a catalogue with the given controller.
     */
    private fun openCatalogue(source: CatalogueSource, controller: BrowseSourceController) {
        // Auto-switch to novel mode when opening a novel source
        if (source is eu.kanade.tachiyomi.source.novel.NovelSourceWrapper) {
            ModeManager.setMode(ContentType.NOVEL)
        }

        if (!preferences.incognitoMode().get()) {
            preferences.lastUsedCatalogueSource().set(source.id)
            if (source !is LocalSource) {
                val list = preferences.lastUsedSources().get().toMutableSet()
                list.removeAll { it.startsWith("${source.id}:") }
                list.add("${source.id}:${Date().time}")
                val sortedList = list.filter { it.split(":").size == 2 }
                    .sortedByDescending { it.split(":").last().toLong() }
                preferences.lastUsedSources()
                    .set(sortedList.take(2).toSet())
            }
        }

        activityBinding?.searchToolbar?.searchQueryHint = "Search ${source.name}"
        router.pushController(controller.withFadeTransaction())
    }

    override fun expandSearch() {
        if (showingExtensions) {
            binding.bottomSheet.root.sheetBehavior?.collapse()
        } else {
            activityBinding?.searchToolbar?.menu?.findItem(R.id.action_search)?.expandActionView()
        }
    }

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate menu
        inflater.inflate(R.menu.catalogue_main, menu)

        setupModeToggle(menu)

        // Initialize search option.
        val searchView = activityBinding?.searchToolbar?.searchView

        // Change hint to show global search.
        activityBinding?.searchToolbar?.searchQueryHint = view?.context?.getString(MR.strings.global_search)

        // Create query listener which opens the global search view.
        setOnQueryTextChangeListener(searchView, true) {
            if (!it.isNullOrBlank()) performGlobalSearch(it)
            true
        }
    }

    private fun performGlobalSearch(query: String) {
        router.pushController(GlobalSearchController(query).withFadeTransaction())
    }

    /**
     * Called when an option menu item has been selected by the user.
     *
     * @param item The selected item.
     * @return True if this event has been consumed, false if it has not.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Initialize option to open catalogue settings.
            R.id.action_filter -> {
                router.pushController(SettingsSourcesController().withFadeTransaction())
            }
            R.id.action_migration_guide -> {
                activity?.openInBrowser(HELP_URL)
            }
            R.id.action_sources_settings -> {
                router.pushController(SettingsBrowseController().withFadeTransaction())
            }
            R.id.action_mode_toggle -> {
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
                    eu.kanade.tachiyomi.util.view.ThemeTransitionHelper.animateButtonPress(anchorView) {
                        eu.kanade.tachiyomi.util.view.ThemeTransitionHelper.animateThemeChange(
                            activity = activity!!,
                            anchorView = anchorView,
                            rootView = rootView,
                            onThemeChange = { yokai.core.mode.ModeManager.toggleMode() },
                            duration = 250L  // Faster animation
                        )
                    }
                } else {
                    yokai.core.mode.ModeManager.toggleMode()
                }
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupModeToggle(menu: Menu) {
        val modeToggle = menu.findItem(R.id.action_mode_toggle) ?: return
        updateModeToggleIcon(modeToggle)
        
        // Observe mode changes to update the toggle icon only
        // Source updates are handled by the consolidated observer in onViewCreated
        viewScope.launch {
            yokai.core.mode.ModeManager.currentMode
                .drop(1) // Skip initial - already handled above
                .collectLatest { mode ->
                    updateModeToggleIcon(modeToggle)
                }
        }
    }

    private fun updateModeToggleIcon(menuItem: MenuItem) {
        val currentMode = yokai.core.mode.ModeManager.currentMode.value
        val icon = when (currentMode) {
            yokai.core.content.ContentType.MANGA -> R.drawable.ic_book_24dp // Manga icon
            yokai.core.content.ContentType.NOVEL -> R.drawable.ic_library_books_24dp // Novel icon
        }
        val title = when (currentMode) {
            yokai.core.content.ContentType.MANGA -> "Switch to Novel Mode"
            yokai.core.content.ContentType.NOVEL -> "Switch to Manga Mode"
        }
        menuItem.setIcon(icon)
        menuItem.title = title
    }

    /**
     * Called to update adapter containing sources.
     */
    fun setSources(sources: List<IFlexible<*>>, lastUsed: SourceItem?) {
        adapter?.updateDataSet(sources, false)
        setLastUsedSource(lastUsed)
        if (isControllerVisible) {
            activityBinding?.appBar?.lockYPos = false
        }
    }

    /**
     * Called to set the last used catalogue at the top of the view.
     */
    fun setLastUsedSource(item: SourceItem?) {
        adapter?.removeAllScrollableHeaders()
        if (item != null) {
            adapter?.addScrollableHeader(item)
            adapter?.addScrollableHeader(LangItem(SourcePresenter.LAST_USED_KEY))
        }
    }

    @Parcelize
    data class SmartSearchConfig(val origTitle: String, val origMangaId: Long) : Parcelable
}
