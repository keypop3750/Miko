package eu.kanade.tachiyomi.ui.source.globalsearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePaddingRelative
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.SourceGlobalSearchControllerBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.ui.main.SearchControllerInterface
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.util.addOrRemoveToFavorites
import eu.kanade.tachiyomi.util.system.extensionIntentForText
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.toolbarHeight
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * LEGACY: This controller shows and manages the different search results in global search.
 * 
 * This View-based implementation is still functional but should be migrated to Compose UI
 * to match the new BrowseScreen architecture. The controller searches across all enabled
 * sources and displays results grouped by source.
 * 
 * TODO: Rebuild as Compose-based GlobalSearchScreen with:
 * - Compose UI matching BrowseScreen styling
 * - ViewModel for state management (similar to BrowseViewModel)
 * - Reactive theme updates with ModeManager
 * - Better performance with LazyColumn
 * 
 * This controller should only handle UI actions, IO actions should be done by [GlobalSearchPresenter]
 * [GlobalSearchCardAdapter.OnMangaClickListener] called when manga is clicked in global search
 */
open class GlobalSearchController(
    protected val initialQuery: String? = null,
    val extensionFilter: String? = null,
    bundle: Bundle? = null,
) : BaseCoroutineController<SourceGlobalSearchControllerBinding, GlobalSearchPresenter>(bundle),
    SearchControllerInterface,
    GlobalSearchAdapter.OnTitleClickListener,
    GlobalSearchCardAdapter.OnMangaClickListener {

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Adapter containing search results grouped by lang.
     */
    protected var adapter: GlobalSearchAdapter? = null

    private var customTitle: String? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null
    private var lastPosition: Int = -1

    /**
     * Called when controller is initialized.
     */
    init {
        setHasOptionsMenu(true)
    }

    override fun createBinding(inflater: LayoutInflater) = SourceGlobalSearchControllerBinding.inflate(inflater)

    override fun getSearchTitle(): String? {
        return customTitle ?: presenter.query
    }

    override val presenter = GlobalSearchPresenter(initialQuery, extensionFilter)

    override fun onTitleClick(position: Int) {
        val source = adapter?.getItem(position)?.source ?: return
        preferences.lastUsedCatalogueSource().set(source.id)
        router.pushController(BrowseSourceController(source, presenter.query).withFadeTransaction())
        lastPosition = position
    }

    /**
     * Called when manga in global search is clicked, opens manga.
     *
     * @param manga clicked item containing manga information.
     */
    override fun onMangaClick(manga: Manga) {
        // Open MangaController.
        lastPosition = adapter?.currentItems?.indexOfFirst { it.source.id == manga.source } ?: -1
        router.pushController(
            MangaDetailsController(manga, true, shouldLockIfNeeded = activity is SearchActivity)
                .withFadeTransaction(),
        )
    }

    /**
     * Called when manga in global search is long clicked.
     *
     * @param position clicked item containing manga information.
     */
    override fun onMangaLongClick(position: Int, adapter: GlobalSearchCardAdapter) {
        val manga = adapter.getItem(position)?.manga ?: return

        val view = view ?: return
        val activity = activity ?: return
        viewScope.launchIO {
            withUIContext { snack?.dismiss() }
            snack = manga.addOrRemoveToFavorites(
                preferences,
                view,
                activity,
                presenter.sourceManager,
                this@GlobalSearchController,
                onMangaAdded = { migrationInfo ->
                    migrationInfo?.let { (source, stillFaved) ->
                        val index = this@GlobalSearchController.adapter
                            ?.currentItems?.indexOfFirst { it.source.id == source } ?: return@let
                        val item = this@GlobalSearchController.adapter?.getItem(index) ?: return@let
                        val oldMangaIndex = item.results?.indexOfFirst {
                            it.manga.title.lowercase() == manga.title.lowercase()
                        } ?: return@let
                        val oldMangaItem = item.results.getOrNull(oldMangaIndex)
                        oldMangaItem?.manga?.favorite = stillFaved
                        val holder = binding.recycler.findViewHolderForAdapterPosition(index) as? GlobalSearchHolder
                        holder?.updateManga(oldMangaIndex)
                    }
                    adapter.notifyItemChanged(position)
                    snack = view.snack(MR.strings.added_to_library)
                },
                onMangaMoved = { adapter.notifyItemChanged(position) },
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

    override fun showFloatingBar() =
        activity !is SearchActivity ||
            customTitle == null ||
            extensionFilter == null

    /**
     * Adds items to the options menu.
     *
     * @param menu menu containing options.
     * @param inflater used to load the menu xml.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate menu.
        inflater.inflate(R.menu.catalogue_new_list, menu)

        // Find and setup mode toggle on the searchToolbar (it's defined in search.xml menu)
        val searchToolbarMenu = activityBinding?.searchToolbar?.menu
        searchToolbarMenu?.findItem(R.id.action_mode_toggle)?.let { modeToggle ->
            modeToggle.isVisible = true
            setupModeToggle(modeToggle)
        }

        // Setup the mode toggle item click handler on searchToolbar
        activityBinding?.searchToolbar?.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_mode_toggle) {
                ModeManager.toggleMode()
                true
            } else {
                false
            }
        }

        // Initialize search menu
        activityBinding?.searchToolbar?.setQueryHint(view?.context?.getString(MR.strings.global_search), false)
        activityBinding?.searchToolbar?.searchItem?.expandActionView()
        activityBinding?.searchToolbar?.searchView?.setQuery(presenter.query, false)

        if (presenter.query.isBlank()) {
            activityBinding?.searchToolbar?.searchView?.requestFocus()
        }

        setOnQueryTextChangeListener(activityBinding?.searchToolbar?.searchView, onlyOnSubmit = true, hideKbOnSubmit = true) {
            // try to handle the query as a manga URL
            applicationContext?.extensionIntentForText(it ?: "")?.let {
                startActivity(it)
            }
            presenter.search(it ?: "")
            setTitle() // Update toolbar title
            true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_mode_toggle -> {
                // Toggle mode and re-search
                ModeManager.toggleMode()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Setup the mode toggle button.
     * Shows novel icon when in novel mode, manga icon when in manga mode.
     */
    private fun setupModeToggle(modeToggle: MenuItem) {
        updateModeToggleIcon(modeToggle)
        
        // Track the initial mode to detect actual changes
        var lastMode = ModeManager.currentMode.value
        
        // Observe mode changes and re-search when mode changes
        viewScope.launch {
            ModeManager.currentMode.collectLatest { mode ->
                updateModeToggleIcon(modeToggle)
                // Only re-search if the mode actually changed (not on initial collection)
                if (mode != lastMode) {
                    lastMode = mode
                    // Re-search with new mode's sources
                    presenter.researchWithNewMode()
                }
            }
        }
    }

    /**
     * Update the mode toggle icon based on current mode.
     */
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

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type.isEnter && isControllerVisible) {
            val searchView = activityBinding?.searchToolbar?.searchView ?: return
            val searchItem = activityBinding?.searchToolbar?.searchItem ?: return
            searchItem.expandActionView()
            searchView.setQuery(presenter.query, false)
            if (presenter.query.isNotBlank()) {
                searchView.clearFocus()
            }
        }
        if (type == ControllerChangeType.POP_ENTER && lastPosition > -1) {
            val holder = binding.recycler.findViewHolderForAdapterPosition(lastPosition) as? GlobalSearchHolder
            holder?.updateAll()
            lastPosition = -1
        }
    }

    override fun onActionViewExpand(item: MenuItem?) {
        val searchView = activityBinding?.searchToolbar?.searchView ?: return
        searchView.setQuery(presenter.query, false)
    }

    override fun onActionViewCollapse(item: MenuItem?) {
        if (activity is SearchActivity) {
            (activity as? SearchActivity)?.onBackPressedDispatcher?.onBackPressed()
        } else if (customTitle == null) {
            router.popCurrentController()
        }
    }

    /**
     * Called when the view is created
     *
     * @param view view of controller
     */
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        adapter = GlobalSearchAdapter(this)

        binding.recycler.updatePaddingRelative(
            top = (toolbarHeight ?: 0) +
                (activityBinding?.root?.rootWindowInsetsCompat?.getInsets(systemBars())?.top ?: 0),
        )

        // Create recycler and set adapter.
        binding.recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(view.context)
        binding.recycler.adapter = adapter
        scrollViewWith(binding.recycler, padBottom = true)
        if (extensionFilter != null) {
            customTitle = view.context?.getString(MR.strings.loading)
            setTitle()
        }

        // try to handle the query as a manga URL
        applicationContext?.extensionIntentForText(presenter.query)?.let {
            startActivity(it)
        }
    }

    override fun onDestroyView(view: View) {
        // Remove the mode toggle from searchToolbar to avoid duplicates
        activityBinding?.searchToolbar?.menu?.removeItem(R.id.action_mode_toggle)
        adapter = null
        super.onDestroyView(view)
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        super.onSaveViewState(view, outState)
        adapter?.onSaveInstanceState(outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        adapter?.onRestoreInstanceState(savedViewState)
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param source used to find holder containing source
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(source: CatalogueSource): GlobalSearchHolder? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.flexibleAdapterPosition)
            if (item != null && source.id == item.source.id) {
                return holder as GlobalSearchHolder
            }
        }

        return null
    }

    /**
     * Add search result to adapter.
     *
     * @param searchResult result of search.
     */
    fun setItems(searchResult: List<GlobalSearchItem>) {
        if (extensionFilter != null) {
            val results = searchResult.firstOrNull()?.results
            if (results != null && searchResult.size == 1 && results.size == 1) {
                val manga = results.first().manga
                router.replaceTopController(
                    MangaDetailsController(manga, true, shouldLockIfNeeded = true)
                        .withFadeTransaction(),
                )
                return
            } else if (results != null) {
                (activity as? SearchActivity)?.setFloatingToolbar(true)
                customTitle = null
                setTitle()
                activity?.invalidateOptionsMenu()
                activityBinding?.appBar?.updateAppBarAfterY(binding.recycler)
            }
        }
        adapter?.updateDataSet(searchResult)
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the initialized manga.
     */
    fun onMangaInitialized(source: CatalogueSource, manga: Manga) {
        getHolder(source)?.setImage(manga)
    }
}
