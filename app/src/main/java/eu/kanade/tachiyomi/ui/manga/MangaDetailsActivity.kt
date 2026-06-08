package eu.kanade.tachiyomi.ui.manga

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.graphics.ColorUtils
import androidx.core.view.updatePadding
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.SizeResolver
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.google.android.material.snackbar.Snackbar
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.getBestColor
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.dominantCoverColors
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.data.database.models.vibrantCoverColor
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.databinding.MangaDetailsActivityBinding
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.chapter.ChaptersSortBottomSheet
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.util.addOrRemoveToFavorites
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.moveCategories
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collectLatest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.manga.models.cover
import yokai.presentation.core.Constants
import yokai.util.lang.getString
import kotlin.math.roundToInt
import kotlin.math.max
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.manga.MangaHeaderHolder
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterHolder

/**
 * Activity-based implementation of Manga Details screen.
 * 
 * Replaces MangaDetailsController to enable true shared element transitions.
 * 
 * **Migration Status**: Phase 2 - Skeleton
 * 
 * Architecture:
 * - Extends AppCompatActivity (standard Android Activity)
 * - Uses ViewBinding (same layout as Controller)
 * - Integrates with MangaDetailsPresenter (shared with Controller)
 * - Supports Activity shared element transitions
 * 
 * Key Differences from Controller:
 * - Activity lifecycle (onCreate/onDestroy) vs Controller (onAttach/onDetach)
 * - Intent extras instead of Bundle arguments
 * - System back stack instead of Conductor router
 * - Native shared element support via ActivityOptions
 * 
 * @see MangaDetailsController for original Controller implementation
 */
class MangaDetailsActivity : 
    AppCompatActivity(), 
    MangaDetailsAdapter.MangaDetailsInterface,
    eu.davidea.flexibleadapter.FlexibleAdapter.OnItemClickListener,
    eu.davidea.flexibleadapter.FlexibleAdapter.OnItemLongClickListener,
    ActionMode.Callback {
    
    private lateinit var binding: MangaDetailsActivityBinding
    private lateinit var presenter: MangaDetailsPresenter
    private lateinit var adapter: MangaDetailsAdapter
    
    private var mangaId: Long = -1L
    private var fromSource: Boolean = false
    
    // Cover theming colors (Phase 2.1)
    private var coverColor: Int? = null
    private var accentColor: Int? = null
    private var headerColor: Int? = null
    
    // Toolbar theming animation (Phase 2.1)
    private var colorAnimator: android.animation.ValueAnimator? = null
    private var toolbarIsColored = false
    
    // ActionMode for chapter selection (Phase 3)
    private var actionMode: ActionMode? = null
    private var startingRangeChapterPos: Int? = null
    private var rangeMode: RangeMode? = null
    
    /**
     * Range selection mode for batch operations.
     * Determines what action to apply to selected chapter range.
     */
    private enum class RangeMode {
        Download,
        RemoveDownload,
        Read,
        Unread
    }
    
    companion object {
        private const val EXTRA_MANGA_ID = "manga_id"
        private const val EXTRA_FROM_SOURCE = "from_source"
        private const val TRANSITION_NAME_COVER = "manga_cover_transition"
        
        /**
         * Create intent for launching MangaDetailsActivity.
         * 
         * @param context Context to create intent
         * @param mangaId ID of manga to display
         * @param fromSource Whether opened from source catalog
         * @return Intent for launching activity
         */
        fun newIntent(
            context: Context,
            mangaId: Long,
            fromSource: Boolean = false
        ): Intent {
            return Intent(context, MangaDetailsActivity::class.java).apply {
                putExtra(EXTRA_MANGA_ID, mangaId)
                putExtra(EXTRA_FROM_SOURCE, fromSource)
            }
        }
        
        /**
         * Create intent with shared element transition options.
         * 
         * @param context Context to create intent
         * @param mangaId ID of manga to display
         * @param sharedElement Source view for shared element (manga cover)
         * @param fromSource Whether opened from source catalog
         * @return Pair of Intent and ActivityOptions bundle
         * 
         * Usage:
         * ```
         * val (intent, bundle) = MangaDetailsActivity.newIntentWithTransitionOptions(...)
         * startActivity(intent, bundle)
         * ```
         */
        fun newIntentWithTransitionOptions(
            context: Context,
            mangaId: Long,
            sharedElement: View?,
            fromSource: Boolean = false
        ): Pair<Intent, Bundle?> {
            val intent = newIntent(context, mangaId, fromSource)
            
            val activityOptions = if (sharedElement != null) {
                ActivityOptions.makeSceneTransitionAnimation(
                    context as android.app.Activity,
                    sharedElement,
                    TRANSITION_NAME_COVER
                )
            } else {
                null
            }
            
            return intent to activityOptions?.toBundle()
        }
    }
    
    //region MangaHeaderInterface implementations
    override fun coverColor(): Int? = coverColor
    override fun accentColor(): Int? = accentColor
    override fun topCoverHeight(): Int {
        // EXPERIMENTER FIX: Since we removed appbar_scrolling_view_behavior,
        // RecyclerView now starts at screen top (like Controller).
        // topView needs to reserve space for toolbar + status bar.
        val statusBarHeight = window.decorView.rootWindowInsets?.systemWindowInsetTop ?: 0
        val toolbarHeight = binding.toolbar.height
        val totalHeight = statusBarHeight + toolbarHeight
        android.util.Log.e("MangaDetailsActivity", "🔍 topCoverHeight() = $totalHeight (status=$statusBarHeight + toolbar=$toolbarHeight)")
        return totalHeight
    }
    override fun mangaPresenter(): MangaDetailsPresenter = presenter
    override fun updateScroll() {
        // TODO Phase 3.6: Update scroll-related toolbar animations
    }
    override fun setSwipeRefreshEnabled(enabled: Boolean) {
        binding.swipeRefresh.isEnabled = enabled
    }
    //endregion
    
    /**
     * Called when activity is created.
     * Sets up window transitions, binding, and presenter.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable window transitions if we're using shared elements
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        
        // CRITICAL: Allow content to draw behind system bars (status bar, nav bar)
        // This matches MainActivity's setup and allows backdrop to extend to screen edges
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        super.onCreate(savedInstanceState)
        
        // Extract intent extras
        mangaId = intent.getLongExtra(EXTRA_MANGA_ID, -1L)
        fromSource = intent.getBooleanExtra(EXTRA_FROM_SOURCE, false)
        
        if (mangaId == -1L) {
            // Invalid manga ID, finish activity
            finish()
            return
        }
        
        // Initialize presenter BEFORE setting up views
        presenter = MangaDetailsPresenter(mangaId)
        
        // NOTE: We don't use presenter.attachView() because presenter is typed for MangaDetailsController
        // Instead, we manually refresh the UI in response to presenter callbacks (pull-based approach)
        
        // Setup view binding with Activity-optimized layout
        binding = MangaDetailsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply window insets to toolbar - pushes it below status bar
        binding.root.doOnApplyWindowInsetsCompat { v, insets, _ ->
            val systemInsets = insets.systemWindowInsets
            binding.appBar.updatePadding(
                top = systemInsets.top,
            )
            insets
        }
        
        // Setup scroll listener for toolbar hide/show and status bar opacity
        setupScrollBehavior()
        
        // 🔬 DIAGNOSTIC: Check what actually got inflated
        android.util.Log.e("MangaDetailsActivity", "=== LAYOUT DIAGNOSTIC ===")
        android.util.Log.e("MangaDetailsActivity", "Binding class: ${binding.javaClass.simpleName}")
        android.util.Log.e("MangaDetailsActivity", "Root view: ${binding.root.javaClass.simpleName}")
        
        // Check if we have the views we expect
        val toolbarExists = try { binding.toolbar; true } catch (e: Throwable) { false }
        val appBarExists = try { binding.appBar; true } catch (e: Throwable) { false }
        android.util.Log.e("MangaDetailsActivity", "Has toolbar property: $toolbarExists")
        android.util.Log.e("MangaDetailsActivity", "Has appBar property: $appBarExists")
        
        // Measure the toolbar
        binding.root.post {
            try {
                val toolbar = binding.toolbar
                android.util.Log.e("MangaDetailsActivity", "Toolbar class: ${toolbar.javaClass.simpleName}")
                android.util.Log.e("MangaDetailsActivity", "Toolbar visibility: ${toolbar.visibility} (0=VISIBLE, 4=INVISIBLE, 8=GONE)")
                android.util.Log.e("MangaDetailsActivity", "Toolbar width: ${toolbar.width}, height: ${toolbar.height}")
                android.util.Log.e("MangaDetailsActivity", "Toolbar alpha: ${toolbar.alpha}")
                
                val appBar = binding.appBar
                android.util.Log.e("MangaDetailsActivity", "AppBar visibility: ${appBar.visibility}")
                android.util.Log.e("MangaDetailsActivity", "AppBar width: ${appBar.width}, height: ${appBar.height}")
            } catch (e: Throwable) {
                android.util.Log.e("MangaDetailsActivity", "ERROR accessing views: ${e.message}")
                e.printStackTrace()
            }
        }
        
        // Setup toolbar (no manual visibility needed - layout has it visible by default)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Make status bar transparent for immersive cover
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        // DON'T remove layout behavior - we need it to position content below toolbar!
        // The scroll behavior tells SwipeRefreshLayout to start BELOW the AppBarLayout
        // Without it, content draws behind the toolbar
        
        // Setup shared element transition name for cover
        // This matches the transitionName set on the library cover image
        binding.mangaCoverFull?.let { coverView ->
            ViewCompat.setTransitionName(coverView, TRANSITION_NAME_COVER)
        }
        
        // Postpone enter transition until content is loaded
        // This ensures smooth shared element transition
        supportPostponeEnterTransition()
        
        // Setup RecyclerView and adapter
        setupRecyclerView()
        
        // Load manga data
        setupPlaceholder()
    }
    
    /**
     * Setup RecyclerView with adapter and layout manager.
     * Ported from Controller.setRecycler() lines 448-510
     */
    private fun setupRecyclerView() {
        // Create adapter for manga details (chapters + header)
        // Use 'this' as delegate since Activity implements MangaDetailsInterface
        adapter = MangaDetailsAdapter(
            delegate = this,
            presenter = presenter,
            binding = binding
        )
        
        // Register click listeners explicitly
        // FlexibleAdapter needs to be told about the listeners
        adapter.mItemClickListener = this
        adapter.mItemLongClickListener = this
        
        // NOTE: Swipe enable moved to updateHeader() - must be AFTER RecyclerView attachment
        // FlexibleAdapter throws IllegalStateException if swipe enabled before RecyclerView is set
        
        // Setup RecyclerView BUT DON'T ATTACH ADAPTER YET
        // We'll attach it in updateHeader() after data loads
        // This prevents RecyclerView from creating view holders before presenter.manga is initialized
        binding.recycler.layoutManager = LinearLayoutManagerAccurateOffset(this)
        binding.recycler.addItemDecoration(MangaDetailsDivider(this))
        binding.recycler.setHasFixedSize(true)
        
        // NOTE: FastScroller attachment moved to updateHeader() after adapter is attached
        // FastScroller library requires adapter to be attached to RecyclerView first
        
        // Setup unified scroll behavior for toolbar hide/show, theming, and parallax
        setupScrollBehavior()
    }
    
    /**
     * Setup unified scroll listener for toolbar hide/show, status bar opacity, and parallax.
     * Combines toolbar animation, color transitions, and backdrop effects in one listener.
     */
    private fun setupScrollBehavior() {
        var lastY = 0f
        
        binding.recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val appBar = binding.appBar
                    val atTop = !recyclerView.canScrollVertically(-1)
                    
                    // 1. Hide/show toolbar based on scroll direction
                    if (dy > 0) {
                        // Scrolling down - hide toolbar
                        val newY = kotlin.math.max(-appBar.height.toFloat(), appBar.y - dy)
                        appBar.y = newY
                        lastY = newY
                    } else if (dy < 0) {
                        // Scrolling up - show toolbar
                        val newY = kotlin.math.min(0f, appBar.y - dy)
                        appBar.y = newY
                        lastY = newY
                    }
                    
                    // Reset toolbar position when at top
                    if (atTop) {
                        appBar.y = 0f
                        lastY = 0f
                    }
                    
                    // 2. Update toolbar/status bar colors based on scroll position
                    val shouldColorToolbar = !atTop
                    colorToolbar(shouldColorToolbar)
                    
                    // 3. Update toolbar title alpha
                    updateToolbarTitleAlpha(isScrollingDown = dy > 0)
                    
                    // 4. Backdrop parallax effect (slower scroll rate)
                    val header = getHeader()
                    val tY = header?.binding?.backdrop?.translationY ?: 0f
                    header?.binding?.backdrop?.translationY = kotlin.math.max(0f, tY + dy * 0.25f)
                    if (atTop) header?.binding?.backdrop?.translationY = 0f
                }
                
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    
                    val atTop = !recyclerView.canScrollVertically(-1)
                    
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val appBar = binding.appBar
                        val halfWay = appBar.height.toFloat() / 2
                        val closerToTop = kotlin.math.abs(appBar.y) > halfWay
                        
                        // Determine target Y position
                        val targetY = if (closerToTop && !atTop) {
                            -appBar.height.toFloat()
                        } else {
                            0f
                        }
                        
                        // Coordinate animations: toolbar position + color transition
                        val duration = 200L
                        
                        // Animate toolbar position
                        appBar.animate()
                            .y(targetY)
                            .setDuration(duration)
                            .start()
                        
                        lastY = targetY
                        
                        // When snapping to top, ensure colors transition smoothly
                        if (atTop) {
                            // Reset backdrop
                            getHeader()?.binding?.backdrop?.translationY = 0f
                            // Animate colors to transparent (same duration as toolbar snap)
                            colorToolbar(false, animate = true)
                        }
                    }
                    
                    // Always update title alpha when scroll state changes
                    updateToolbarTitleAlpha()
                }
            }
        )
    }
    
    /**
     * Update toolbar title alpha based on scroll position.
     * Phase 2.2: Title fade animation
     */
    private fun updateToolbarTitleAlpha(isScrollingDown: Boolean = false) {
        val atTop = !binding.recycler.canScrollVertically(-1)
        val alpha = if (atTop) 0f else 1f
        
        // Fade in/out toolbar title
        // Note: ActionBar title alpha needs custom implementation
        // For now, we'll just hide/show the title
        supportActionBar?.title = if (atTop) "" else presenter.manga.title
    }
    
    /**
     * Get the header view holder for accessing header UI elements.
     * Phase 2.2: Helper method
     */
    private fun getHeader(): MangaHeaderHolder? {
        return binding.recycler.findViewHolderForAdapterPosition(0) as? MangaHeaderHolder
    }
    
    /**
     * Setup SwipeRefreshLayout to refresh manga and chapters.
     * Ported from Controller.onViewCreated() line 450
     */
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            presenter.refreshAll()
        }
        binding.swipeRefresh.isRefreshing = presenter.isLoading
    }
    
    /**
     * Initialize presenter and load manga data.
     * EXPERIMENTAL: Fixed initialization order for proper theming.
     */
    private fun setupPlaceholder() {
        android.util.Log.d("MangaDetailsActivity", "=== setupPlaceholder() CALLED ===")
        
        // Setup SwipeRefresh
        setupSwipeRefresh()
        
        // CRITICAL: Initialize colors BEFORE loading data
        // This ensures theme is applied immediately when header is first rendered
        coverColor = null
        setAccentColorValue()  // Initialize with stored vibrant color or null
        setHeaderColorValue()  // Initialize with stored vibrant color or null
        
        lifecycleScope.launch {
            android.util.Log.d("MangaDetailsActivity", "=== Coroutine started ===")
            try {
                android.util.Log.d("MangaDetailsActivity", "About to call refreshMangaFromDb()")
                // Fetch manga data from database
                presenter.refreshMangaFromDb()
                android.util.Log.d("MangaDetailsActivity", "refreshMangaFromDb() completed")
                
                // NOW manga exists - reinitialize colors with stored vibrantCoverColor
                android.util.Log.d("MangaDetailsActivity", "Reinitializing colors with manga data")
                setAccentColorValue()  // Will use presenter.manga.vibrantCoverColor now
                setHeaderColorValue()  // Will use presenter.manga.vibrantCoverColor now
                
                // Initialize chapter sorting BEFORE fetching chapters
                android.util.Log.d("MangaDetailsActivity", "About to call syncData()")
                presenter.syncData()
                android.util.Log.d("MangaDetailsActivity", "syncData() completed")
                
                // Fetch chapters
                android.util.Log.d("MangaDetailsActivity", "About to call getChaptersNow()")
                presenter.getChaptersNow()
                android.util.Log.d("MangaDetailsActivity", "getChaptersNow() completed, chapters count: ${presenter.chapters.size}")
                
                // Update UI with header and chapters
                android.util.Log.d("MangaDetailsActivity", "About to call updateHeader()")
                updateHeader()
                android.util.Log.d("MangaDetailsActivity", "updateHeader() completed")
                
                // Apply initial colors if themeMangaDetails is enabled
                if (presenter.preferences.themeMangaDetails().get()) {
                    setItemColors()
                }
                
                // Start palette extraction (async - will refine colors when done)
                android.util.Log.d("MangaDetailsActivity", "About to call setPaletteColor()")
                setPaletteColor()
                android.util.Log.d("MangaDetailsActivity", "setPaletteColor() completed")
                
                // Basic data loaded, can start transition now
                supportStartPostponedEnterTransition()
                
                // Show manga title in action bar
                supportActionBar?.title = if (presenter.isMangaLateInitInitialized()) {
                    presenter.manga.title
                } else {
                    "Loading..."
                }
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                
                // Update chapter UI one more time to ensure it's populated
                android.util.Log.d("MangaDetailsActivity", "About to call updateChapters()")
                updateChapters()
                android.util.Log.d("MangaDetailsActivity", "updateChapters() completed")
                
                // Start observing download status changes
                android.util.Log.d("MangaDetailsActivity", "About to start observeDownloadStatusChanges()")
                observeDownloadStatusChanges()
                android.util.Log.d("MangaDetailsActivity", "observeDownloadStatusChanges() started")
                
                android.util.Log.d("MangaDetailsActivity", "=== All setup complete ===")
                
            } catch (e: Exception) {
                // Failed to load, just finish activity
                android.util.Log.e("MangaDetailsActivity", "Failed to load manga", e)
                e.printStackTrace()
                supportStartPostponedEnterTransition()
                finish()
            }
        }
    }
    
    /**
     * Update SwipeRefresh loading indicator.
     * Called by presenter when loading state changes.
     */
    fun setRefresh(enabled: Boolean) {
        binding.swipeRefresh.isRefreshing = enabled
    }
    
    /**
     * Update manga header and chapter list.
     * Called by presenter when manga data changes.
     */
    /**
     * Update header UI with manga metadata.
     * Ported from Controller.updateHeader() lines 819-825
     */
    fun updateHeader() {
        binding.swipeRefresh.isRefreshing = presenter.isLoading
        
        // Attach adapter if not already attached (first time only)
        if (binding.recycler.adapter == null) {
            binding.recycler.adapter = adapter
            
            // Enable swipe gestures AFTER RecyclerView attachment
            // FlexibleAdapter requires recyclerView to be non-null before enabling swipe
            adapter.isSwipeEnabled = true
            
            // CRITICAL: Attach FastScroller AFTER adapter is attached to RecyclerView
            // FastScroller library requires adapter.getRecyclerView() to be non-null
            adapter.fastScroller = binding.fastScroller
        }
        
        val adapter = binding.recycler.adapter as? MangaDetailsAdapter ?: return
        adapter.setChapters(presenter.chapters)
        addMangaHeader()
        updateMenuVisibility()
    }
    
    /**
     * Update chapter list in RecyclerView.
     * Ported from Controller.updateChapters() lines 827-835
     */
    fun updateChapters() {
        binding.swipeRefresh.isRefreshing = presenter.isLoading
        val adapter = binding.recycler.adapter as? MangaDetailsAdapter ?: return
        adapter.setChapters(presenter.chapters)
        addMangaHeader()
        updateMenuVisibility()
    }
    
    /**
     * Add manga header item to adapter if not already present.
     * The header contains manga cover, title, description, buttons, etc.
     * Ported from Controller.addMangaHeader() lines 837-848
     */
    private fun addMangaHeader() {
        val adapter = binding.recycler.adapter as? MangaDetailsAdapter ?: return
        
        // Only add header if not already present
        if (adapter.scrollableHeaders.isEmpty()) {
            adapter.removeAllScrollableHeaders()
            adapter.addScrollableHeader(presenter.headerItem)
        }
    }
    
    /**
     * Update toolbar menu item visibility based on presenter state.
     */
    private fun updateMenuVisibility() {
        // Menu items will be updated when implemented
        supportInvalidateOptionsMenu()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh chapters when returning to screen (e.g., after reading)
        lifecycleScope.launch {
            updateChapters()
        }
        
        // Phase 1.3: Observe download status changes for real-time UI updates
        // Only start observing if manga is already loaded, otherwise it will be started after loading
        if (presenter.isMangaLateInitInitialized()) {
            observeDownloadStatusChanges()
        }
    }
    
    /**
     * Observes download manager status changes to update chapter list in real-time.
     * Phase 1.3: Real-Time Download Status Updates
     */
    private fun observeDownloadStatusChanges() {
        // Guard: Only proceed if manga is initialized
        if (!presenter.isMangaLateInitInitialized()) {
            android.util.Log.w("MangaDetailsActivity", "observeDownloadStatusChanges called before manga initialized")
            return
        }
        
        lifecycleScope.launch {
            // Get download manager instance
            val downloadManager = Injekt.get<eu.kanade.tachiyomi.data.download.DownloadManager>()
            
            android.util.Log.d("MangaDetailsActivity", "Setting up download status flow for manga: ${presenter.manga.id}")
            
            // Collect download status changes filtered to this manga
            downloadManager.statusFlow()
                .filter { download ->
                    download.manga.id == presenter.manga.id
                }
                .catch { error ->
                    android.util.Log.e("MangaDetailsActivity", "Download status error", error)
                }
                .collect { download ->
                    android.util.Log.d("MangaDetailsActivity", "Download status update: ${download.chapter.name} -> ${download.status}")
                    
                    // Update the chapter item's status
                    val chapterItem = presenter.chapters.find { it.id == download.chapter.id }
                    chapterItem?.status = download.status
                    
                    // Find the chapter position in adapter
                    val chapterId = download.chapter.id ?: return@collect
                    val position = adapter.indexOf(chapterId)
                    
                    // Update specific chapter item if found - use payload to avoid full rebind
                    if (position >= 0 && chapterItem != null) {
                        withContext(Dispatchers.Main) {
                            // Use payload to only update download button, not entire ViewHolder
                            adapter.notifyItemChanged(position, chapterItem)
                        }
                    }
                }
        }
        
        lifecycleScope.launch {
            // Also observe download progress for real-time progress updates
            val downloadManager = Injekt.get<eu.kanade.tachiyomi.data.download.DownloadManager>()
            
            // Double-check manga is still initialized (in case it was destroyed)
            if (!presenter.isMangaLateInitInitialized()) {
                android.util.Log.w("MangaDetailsActivity", "Manga no longer initialized, skipping progress flow")
                return@launch
            }
            
            android.util.Log.d("MangaDetailsActivity", "Setting up download progress flow for manga: ${presenter.manga.id}")
            
            downloadManager.progressFlow()
                .filter { download ->
                    download.manga.id == presenter.manga.id
                }
                .catch { error ->
                    android.util.Log.e("MangaDetailsActivity", "Download progress error", error)
                }
                .collect { download ->
                    android.util.Log.d("MangaDetailsActivity", "Download progress update: ${download.chapter.name} -> ${download.progress}")
                    
                    // Update the chapter item's download reference
                    val chapterItem = presenter.chapters.find { it.id == download.chapter.id }
                    chapterItem?.download = download
                    
                    // Update specific chapter's progress
                    val chapterId = download.chapter.id ?: return@collect
                    val position = adapter.indexOf(chapterId)
                    
                    // Use payload to avoid flickering - only update download button
                    if (position >= 0 && chapterItem != null) {
                        withContext(Dispatchers.Main) {
                            adapter.notifyItemChanged(position, chapterItem)
                        }
                    }
                }
        }
        
        lifecycleScope.launch {
            // Observe download queue changes for download button state
            val downloadManager = Injekt.get<eu.kanade.tachiyomi.data.download.DownloadManager>()
            
            if (!presenter.isMangaLateInitInitialized()) {
                android.util.Log.w("MangaDetailsActivity", "Manga no longer initialized, skipping queue flow")
                return@launch
            }
            
            android.util.Log.d("MangaDetailsActivity", "Setting up download queue flow for manga: ${presenter.manga.id}")
            
            downloadManager.queueState.collectLatest { queue ->
                android.util.Log.d("MangaDetailsActivity", "Download queue changed, updating chapters")
                
                // Update all chapters based on current queue state
                presenter.chapters.forEach { chapterItem ->
                    val download = queue.find { it.chapter.id == chapterItem.id }
                    chapterItem.download = download
                }
                
                // Refresh all chapter items
                withContext(Dispatchers.Main) {
                    updateChapters()
                }
            }
        }
    }
    
    //region Toolbar Theming (Phase 2.1)
    
    /**
     * Animates toolbar background color from transparent to themed color.
     * Ported from Controller lines 528-568.
     */
    private fun colorToolbar(isColor: Boolean, animate: Boolean = true) {
        if (isColor == toolbarIsColored) return
        toolbarIsColored = isColor
        
        val scrollingColor = headerColor ?: getResourceColor(R.attr.colorPrimaryVariant)
        val topColor = ColorUtils.setAlphaComponent(scrollingColor, 0)
        // Make status bar fully opaque when scrolled (not 87%)
        val scrollingStatusColor = scrollingColor
        
        if (animate) {
            colorAnimator?.cancel()
            colorAnimator = android.animation.ValueAnimator.ofFloat(
                if (toolbarIsColored) 0f else 1f,
                if (toolbarIsColored) 1f else 0f,
            ).apply {
                duration = 250 // milliseconds
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    supportActionBar?.setBackgroundDrawable(
                        android.graphics.drawable.ColorDrawable(
                            ColorUtils.blendARGB(topColor, scrollingColor, value)
                        )
                    )
                    window?.statusBarColor = if (toolbarIsColored) {
                        ColorUtils.blendARGB(topColor, scrollingStatusColor, value)
                    } else {
                        android.graphics.Color.TRANSPARENT
                    }
                }
                start()
            }
        } else {
            // Immediate change without animation
            supportActionBar?.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(
                    if (isColor) scrollingColor else topColor
                )
            )
            window?.statusBarColor = if (isColor) scrollingStatusColor else android.graphics.Color.TRANSPARENT
        }
    }
    
    /**
     * Sets status bar and toolbar colors based on scroll state.
     * Ported from Controller lines 631-644.
     */
    private fun setStatusBarAndToolbar() {
        val topColor = android.graphics.Color.TRANSPARENT
        val scrollingColor = headerColor ?: getResourceColor(R.attr.colorPrimaryVariant)
        val scrollingStatusColor = ColorUtils.setAlphaComponent(
            scrollingColor,
            (0.87f * 255).roundToInt()
        )
        
        window?.statusBarColor = if (toolbarIsColored) scrollingStatusColor else topColor
        supportActionBar?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                if (toolbarIsColored) scrollingColor else topColor
            )
        )
    }
    
    /**
     * Apply accent color to UI elements like chips, buttons, FAB.
     * Phase 2.1: Item color application.
     */
    private fun setItemColors() {
        val accentColor = accentColor ?: return
        
        android.util.Log.d("ColorDebug", """
            setItemColors() called with ORIGINAL vibrant color:
            - accentColor: ${String.format("#%08X", accentColor)}
            - Activity theming enabled: ${presenter.preferences.themeMangaDetails().get()}
            - Header exists: ${getHeader() != null}
        """.trimIndent())
        
        // Now that we use the original vibrant color (not adjusted), calling updateColors() should work perfectly!
        getHeader()?.updateColors()
        
        // Update visible chapter holders with themed colors  
        if (adapter.itemCount > 1) {
            presenter.chapters.forEach { chapter ->
                val chapterHolder = binding.recycler.findViewHolderForItemId(chapter.id!!) as? ChapterHolder
                    ?: return@forEach
                chapterHolder.notifyStatus(
                    chapter.status,
                    presenter.isLockedFromSearch,
                    chapter.progress,
                )
            }
        }
    }
    
    //endregion
    
    //region Cover color extraction (Phase 3.5)
    /**
     * Extract palette color from manga cover for theming.
     * Ported from Controller.setPaletteColor() lines 590-648
     */
    fun setPaletteColor() {
        val request = ImageRequest.Builder(this)
            .data(presenter.manga.cover())
            .size(SizeResolver.ORIGINAL)
            .allowHardware(false)
            .target(
                onSuccess = { image ->
                    val drawable = image.asDrawable(resources)
                    
                    val copy = (drawable as? BitmapDrawable)?.let {
                        BitmapDrawable(
                            resources,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                it.bitmap.copy(Bitmap.Config.HARDWARE, false)
                            else
                                it.bitmap.copy(it.bitmap.config!!, false),
                        )
                    } ?: drawable
                    
                    val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        Palette.from(bitmap).generate { palette ->
                            if (presenter.preferences.themeMangaDetails().get()) {
                                launchUI {
                                    // Extract vibrant color for UI theming (Controller behavior)
                                    val vibrantColor = palette?.getBestColor() ?: return@launchUI
                                    presenter.manga.vibrantCoverColor = vibrantColor
                                    
                                    // Apply vibrant color to all UI elements
                                    setAccentColorValue(vibrantColor)
                                    setHeaderColorValue(vibrantColor)
                                    setItemColors()              // Phase 2.1: Apply colors to UI elements
                                    setStatusBarAndToolbar()     // Phase 2.1: Apply toolbar theming
                                }
                            } else {
                                setCoverColorValue()
                            }
                        }
                    }
                    binding.mangaCoverFull.setImageDrawable(copy)
                },
                onError = {
                    val file = presenter.coverCache.getCoverFile(presenter.manga.thumbnail_url, !presenter.manga.favorite)
                    if (file != null && file.exists()) {
                        file.delete()
                        setPaletteColor()
                    }
                },
            ).build()
        imageLoader.enqueue(request)
    }
    
    /**
     * Set accent color from manga cover.
     * FIXED: Use original vibrant color with optional saturation capping for better readability.
     */
    private fun setAccentColorValue(colorToUse: Int? = null) {
        setCoverColorValue(colorToUse)
        
        // Use the original vibrant color with saturation capping for very intense colors
        accentColor = if (presenter.preferences.themeMangaDetails().get()) {
            // Guard: Only access manga if initialized
            val vibrantColor = if (presenter.isMangaLateInitInitialized()) {
                presenter.manga.vibrantCoverColor
            } else null
            
            (colorToUse ?: vibrantColor)?.let { originalColor ->
                // Check if color is overly saturated and needs adjustment
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(originalColor, hsv)
                val saturation = hsv[1] // 0.0 to 1.0
                val brightness = hsv[2] // 0.0 to 1.0
                
                val finalColor = if (saturation > 0.75f) {
                    // Cap saturation for intense colors to 0.65 for better readability
                    // More aggressive capping: saturation > 0.75 (instead of 0.85) and no brightness requirement
                    hsv[1] = 0.65f
                    val cappedColor = android.graphics.Color.HSVToColor(hsv)
                    
                    android.util.Log.d("ColorDebug", """
                        Saturation capped for intense color:
                        - originalColor: ${String.format("#%08X", originalColor)}
                        - saturation: $saturation -> 0.65
                        - brightness: $brightness
                        - cappedColor: ${String.format("#%08X", cappedColor)}
                    """.trimIndent())
                    
                    cappedColor
                } else {
                    android.util.Log.d("ColorDebug", """
                        Using original color (saturation OK):
                        - originalColor: ${String.format("#%08X", originalColor)}
                        - saturation: $saturation
                        - brightness: $brightness
                    """.trimIndent())
                    
                    originalColor
                }
                
                android.util.Log.d("ColorDebug", """
                    setAccentColorValue() final result:
                    - colorToUse: ${colorToUse?.let { String.format("#%08X", it) }}
                    - vibrantColor: ${vibrantColor?.let { String.format("#%08X", it) }}
                    - finalColor: ${String.format("#%08X", finalColor)}
                    - isNightMode: ${isInNightMode()}
                """.trimIndent())
                
                finalColor
            }
        } else {
            null
        }
    }
    
    /**
     * Set cover color value for theming.
     * Ported from Controller.setCoverColorValue() lines 298-324
     */
    private fun setCoverColorValue(colorToUse: Int? = null) {
        val colorBack = getResourceColor(R.attr.background)
        
        // Guard: Only access manga if initialized
        val vibrantColor = if (presenter.isMangaLateInitInitialized()) {
            presenter.manga.vibrantCoverColor
        } else null
        
        coverColor = (
            if (presenter.preferences.themeMangaDetails().get()) {
                (colorToUse ?: vibrantColor)
            } else {
                ColorUtils.blendARGB(
                    getResourceColor(R.attr.colorSecondary),
                    colorBack,
                    0.5f,
                )
            }
        )?.let {
            val dominant = it
            val domLum = ColorUtils.calculateLuminance(dominant)
            val lumWrongForTheme = (if (isInNightMode()) domLum > 0.8 else domLum <= 0.2)
            val blendFactor = if (lumWrongForTheme) 0.9f else 0.7f
            
            ColorUtils.blendARGB(
                it,
                colorBack,
                blendFactor,
            )
        }
    }
    
    /**
     * Set header color for toolbar theming.
     * Ported from Controller.setHeaderColorValue() lines 343-359
     */
    private fun setHeaderColorValue(colorToUse: Int? = null) {
        // Guard: Only access manga if initialized
        val vibrantColor = if (presenter.isMangaLateInitInitialized()) {
            presenter.manga.vibrantCoverColor
        } else null
        
        headerColor = if (presenter.preferences.themeMangaDetails().get()) {
            (colorToUse ?: vibrantColor)?.let { color ->
                val newColor = makeColorFrom(color, getResourceColor(R.attr.colorPrimaryVariant))
                // Set navigation bar color (matching Controller)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 || isInNightMode()) {
                    window?.navigationBarColor = ColorUtils.setAlphaComponent(
                        newColor,
                        Color.alpha(window?.navigationBarColor ?: Color.BLACK),
                    )
                }
                newColor
            }
        } else {
            null
        }
        setRefreshStyle()
    }
    
    /**
     * Apply refresh indicator colors based on theme.
     * Ported from Controller.setRefreshStyle() lines 326-341
     */
    private fun setRefreshStyle() {
        with(binding.swipeRefresh) {
            if (presenter.preferences.themeMangaDetails().get() && accentColor != null && headerColor != null) {
                val newColor = makeColorFrom(
                    hueOf = accentColor!!,
                    satAndLumOf = getResourceColor(R.attr.actionBarTintColor),
                )
                setColorSchemeColors(newColor)
                setProgressBackgroundColorSchemeColor(headerColor!!)
            } else {
                // Reset to default style
                setColorSchemeColors(getResourceColor(R.attr.actionBarTintColor))
                setProgressBackgroundColorSchemeColor(getResourceColor(R.attr.colorPrimaryVariant))
            }
        }
    }
    
    /**
     * Create color from hue of one color and saturation/luminance of another.
     * Ported from Controller.makeColorFrom() lines 361-372
     */
    @ColorInt
    private fun makeColorFrom(@ColorInt hueOf: Int, @ColorInt satAndLumOf: Int): Int {
        val satLumArray = FloatArray(3)
        val hueArray = FloatArray(3)
        ColorUtils.colorToHSL(satAndLumOf, satLumArray)
        ColorUtils.colorToHSL(hueOf, hueArray)
        return ColorUtils.HSLToColor(
            floatArrayOf(
                hueArray[0],
                satLumArray[1],
                satLumArray[2],
            ),
        )
    }
    //endregion
    
    //region Interface method implementations (Phase 3.6)
    
    /**
     * Called when user taps the "Read Next Chapter" button.
     * Opens the next unread chapter in the reader.
     */
    override fun readNextChapter(readingButton: View) {
        val item = presenter.getNextUnreadChapter()
        if (item != null) {
            openChapter(item.chapter, readingButton)
        } else {
            binding.root.snack(yokai.i18n.MR.strings.next_chapter_not_found, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
        }
    }
    
    /**
     * Called when user taps download button on a chapter.
     * Downloads the chapter if not downloaded, deletes if already downloaded.
     */
    override fun downloadChapter(position: Int) {
        val chapter = (adapter.getItem(position) as? ChapterItem) ?: return
        
        if (chapter.status != Download.State.NOT_DOWNLOADED && chapter.status != Download.State.ERROR) {
            // Chapter is downloaded, delete it
            presenter.deleteChapter(chapter)
            
            // Update UI immediately with payload to avoid flicker
            chapter.status = Download.State.NOT_DOWNLOADED
            chapter.download = null
            adapter.notifyItemChanged(position, chapter)
        } else {
            // Chapter not downloaded, download it
            if (chapter.status == Download.State.ERROR) {
                eu.kanade.tachiyomi.data.download.DownloadJob.start(this)
            } else {
                // Update status IMMEDIATELY before queueing to avoid flicker
                chapter.status = Download.State.QUEUE
                adapter.notifyItemChanged(position, chapter)  // Use payload
                
                downloadChapters(listOf(chapter))
            }
        }
    }
    
    /**
     * Starts download or remove download range selection.
     */
    override fun startDownloadRange(position: Int) {
        createActionModeIfNeeded()
        val chapterItem = (adapter.getItem(position) as? ChapterItem) ?: return
        rangeMode = if (chapterItem.status in listOf(Download.State.NOT_DOWNLOADED, Download.State.ERROR)) {
            RangeMode.Download
        } else {
            RangeMode.RemoveDownload
        }
        onItemClick(null, position)
    }
    
    /**
     * Called to start downloading a chapter immediately (priority download).
     */
    override fun startDownloadNow(position: Int) {
        val chapter = (adapter.getItem(position) as? ChapterItem) ?: return
        presenter.startDownloadingNow(chapter)
    }
    
    /**
     * Called when user taps a chapter to read it.
     */
    private fun openChapter(chapter: Chapter, sharedElement: View? = null) {
        if (sharedElement != null) {
            val (intent, bundle) = eu.kanade.tachiyomi.ui.reader.ReaderActivity
                .newIntentWithTransitionOptions(this, presenter.manga, chapter, sharedElement)
            
            // Pass visible chapter range for optimizations
            val layoutManager = binding.recycler.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            val firstPos = layoutManager?.findFirstVisibleItemPosition() ?: -1
            val lastPos = layoutManager?.findLastVisibleItemPosition() ?: -1
            val chapterRange = if (firstPos > -1 && lastPos > -1) {
                (firstPos..lastPos).mapNotNull {
                    (adapter.getItem(it) as? ChapterItem)?.chapter?.id
                }.toLongArray()
            } else {
                longArrayOf()
            }
            intent.putExtra(eu.kanade.tachiyomi.ui.reader.ReaderActivity.VISIBLE_CHAPTERS, chapterRange)
            startActivity(intent, bundle)
        } else {
            startActivity(eu.kanade.tachiyomi.ui.reader.ReaderActivity.newIntent(this, presenter.manga, chapter))
        }
    }
    
    /**
     * Called when user clicks a chapter item.
     * Opens the chapter in reader or handles ActionMode selection.
     */
    override fun onItemClick(view: View?, position: Int): Boolean {
        val chapterItem = (adapter.getItem(position) as? ChapterItem) ?: return false
        val chapter = chapterItem.chapter
        
        android.util.Log.d("MangaDetailsActivity", "👆 onItemClick: position=$position, actionMode=$actionMode, startingPos=$startingRangeChapterPos")
        
        // Handle range selection if ActionMode is active
        if (actionMode != null) {
            if (startingRangeChapterPos == null) {
                // First selection: mark starting position
                adapter.addSelection(position)
                (binding.recycler.findViewHolderForAdapterPosition(position) as? BaseFlexibleViewHolder)
                    ?.toggleActivation()
                (binding.recycler.findViewHolderForAdapterPosition(position) as? ChapterHolder)
                    ?.notifyStatus(Download.State.CHECKED, false, 0)
                startingRangeChapterPos = position
                actionMode?.invalidate()
            } else {
                // Second selection: execute range action
                val rangeMode = rangeMode ?: return false
                val startingPosition = startingRangeChapterPos ?: return false
                
                // Build chapter list in range
                var chapterList = listOf<ChapterItem>()
                when {
                    startingPosition > position ->
                        chapterList = presenter.chapters.subList(position - 1, startingPosition)
                    startingPosition <= position ->
                        chapterList = presenter.chapters.subList(startingPosition - 1, position)
                }
                
                // Execute range action
                when (rangeMode) {
                    RangeMode.Download -> downloadChapters(chapterList)
                    RangeMode.RemoveDownload -> massDeleteChapters(
                        chapterList.filter { it.status != Download.State.NOT_DOWNLOADED },
                        false,
                    )
                    RangeMode.Read -> markAsRead(chapterList)
                    RangeMode.Unread -> markAsUnread(chapterList)
                }
                
                // Clean up and exit ActionMode
                presenter.fetchChapters(false)
                adapter.removeSelection(startingPosition)
                (binding.recycler.findViewHolderForAdapterPosition(startingPosition) as? BaseFlexibleViewHolder)
                    ?.toggleActivation()
                startingRangeChapterPos = null
                this.rangeMode = null
                destroyActionModeIfNeeded()
            }
            return false
        }
        
        // Normal click: Open chapter in reader
        openChapter(chapter, view)
        return false
    }

    /**
     * Called when user long-presses a chapter item.
     * Shows chapter options menu with batch operations.
     */
    override fun onItemLongClick(position: Int) {
        val adapter = adapter ?: return
        val item = (adapter.getItem(position) as? ChapterItem) ?: return
        val descending = presenter.sortDescending()
        
        val items = mutableListOf(
            MaterialMenuSheet.MenuSheetItem(
                0,
                if (descending) R.drawable.ic_eye_down_24dp else R.drawable.ic_eye_up_24dp,
                yokai.i18n.MR.strings.mark_previous_as_read,
            ),
            MaterialMenuSheet.MenuSheetItem(
                1,
                if (descending) R.drawable.ic_eye_off_down_24dp else R.drawable.ic_eye_off_up_24dp,
                yokai.i18n.MR.strings.mark_previous_as_unread,
            ),
            MaterialMenuSheet.MenuSheetItem(
                2,
                R.drawable.ic_eye_range_24dp,
                yokai.i18n.MR.strings.mark_range_as_read,
            ),
            MaterialMenuSheet.MenuSheetItem(
                3,
                R.drawable.ic_eye_off_range_24dp,
                yokai.i18n.MR.strings.mark_range_as_unread,
            ),
        )
        
        if (presenter.getChapterUrl(item.chapter) != null) {
            items.add(
                0,
                MaterialMenuSheet.MenuSheetItem(
                    4,
                    R.drawable.ic_open_in_webview_24dp,
                    yokai.i18n.MR.strings.open_in_webview,
                ),
            )
        }
        
        val lastRead = presenter.allHistory.find { it.chapter_id == item.id }?.let {
            timeSpanFromNow(yokai.i18n.MR.strings.read_, it.last_read) + "\n"
        }
        
        val menuSheet = MaterialMenuSheet(this, items, item.name, subtitle = lastRead) { _, itemPos ->
            when (itemPos) {
                0 -> markPreviousAs(item, true)
                1 -> markPreviousAs(item, false)
                2 -> startReadRange(position, RangeMode.Read)
                3 -> startReadRange(position, RangeMode.Unread)
                4 -> openChapterInWebView(item)
            }
            true
        }
        menuSheet.show()
    }
    
    /**
     * Downloads a list of chapters and shows appropriate snackbars.
     */
    private fun downloadChapters(chapters: List<ChapterItem>) {
        presenter.downloadChapters(chapters)
        
        // Show "Add to library" snackbar if manga not favorited
        if (!presenter.manga.favorite) {
            val text = getString(
                yokai.i18n.MR.strings.add_x_to_library,
                presenter.manga.seriesType(this).lowercase(java.util.Locale.ROOT),
            )
            binding.root.snack(text, com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE) {
                setAction(getString(yokai.i18n.MR.strings.add)) {
                    toggleMangaFavorite()
                }
            }
        }
    }
    
    /**
     * Shows the chapter filter bottom sheet.
     */
    override fun showChapterFilter() {
        ChaptersSortBottomSheet(this, presenter, accentColor) {
            // Callback when filters/sorting change
            // Add delay to allow presenter's async operations to complete
            lifecycleScope.launch {
                kotlinx.coroutines.delay(100) // Wait for presenter to update chapters
                updateChapters()
            }
        }.show()
    }
    
    /**
     * Shows floating action mode for text selection and search.
     */
    override fun showFloatingActionMode(view: TextView, content: String?, isTag: Boolean) {
        // TODO Phase 3.6: Implement floating action mode
        // For now, just copy to clipboard
        if (content != null) {
            copyContentToClipboard(content, "Content")
        }
    }
    
    /**
     * Toggles manga favorite status or shows category selection.
     */
    override fun favoriteManga(longPress: Boolean) {
        if (longPress) {
            showCategoriesSheet()
        } else if (!presenter.manga.favorite) {
            toggleMangaFavorite()
        } else {
            // Show popup menu for already favorited manga
            // TODO Phase 3.6: Implement favorite popup menu
            toggleMangaFavorite()
        }
    }
    
    /**
     * Copies content to clipboard with appropriate label.
     */
    override fun copyContentToClipboard(content: String, label: dev.icerock.moko.resources.StringResource, useToast: Boolean) {
        val labelText = getString(label)
        copyContentToClipboard(content, labelText, useToast)
    }
    
    override fun copyContentToClipboard(content: String, label: Int, useToast: Boolean) {
        val labelText = getString(label)
        copyContentToClipboard(content, labelText, useToast)
    }
    
    override fun copyContentToClipboard(content: String, label: String?, useToast: Boolean) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label ?: "Content", content)
        clipboard.setPrimaryClip(clip)
        
        if (useToast) {
            android.widget.Toast.makeText(this, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            binding.root.snack("Copied to clipboard", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
        }
    }
    
    /**
     * Returns custom action mode callback for text selection.
     */
    override fun customActionMode(view: TextView): android.view.ActionMode.Callback {
        // TODO Phase 3.6: Return proper action mode callback
        return object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
    }
    
    override fun showEditDialog() {
        showEditMangaDialog()
    }

    /**
     * Zooms the cover image from thumbnail to full screen.
     */
    override fun zoomImageFromThumb(thumbView: View) {
        val drawable = binding.mangaCoverFull.drawable ?: return
        drawable.alpha = 255
        // TODO Phase 3.6: FullCoverDialog needs Activity support
        // val fullCoverDialog = eu.kanade.tachiyomi.ui.manga.FullCoverDialog(this, drawable, thumbView)
        // fullCoverDialog.show()
    }
    
    /**
     * Shows the tracking bottom sheet.
     */
    override fun showTrackingSheet() {
        // TODO Phase 3.6: Implement tracking sheet
        // trackingBottomSheet = TrackingBottomSheet(this)
        // trackingBottomSheet?.show()
    }
    
    /**
     * Prepares to share manga (copies URL to clipboard or opens share sheet).
     */
    override fun prepareToShareManga() {
        val context = this
        val source = presenter.source as? eu.kanade.tachiyomi.source.online.HttpSource ?: return
        
        try {
            val url = source.mangaDetailsRequest(presenter.manga).url.toString()
            val sharingIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, url)
            }
            startActivity(android.content.Intent.createChooser(sharingIntent, getString(yokai.i18n.MR.strings.share)))
        } catch (e: Exception) {
            binding.root.snack("Error sharing manga", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
        }
    }
    
    /**
     * Opens manga in WebView.
     */
    override fun openInWebView() {
        val context = this
        val source = presenter.source as? eu.kanade.tachiyomi.source.online.HttpSource ?: return
        
        try {
            val url = source.mangaDetailsRequest(presenter.manga).url.toString()
            val intent = eu.kanade.tachiyomi.ui.webview.WebViewActivity.newIntent(context, url, source.id)
            startActivity(intent)
        } catch (e: Exception) {
            binding.root.snack("Error opening WebView", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
        }
    }
    
    /**
     * Sets up popup menu for favorite button.
     */
    override fun setFavButtonPopup(popupView: View) {
        if (!presenter.manga.favorite) {
            popupView.setOnTouchListener(null)
            return
        }
        
        // TODO Phase 3.6: Setup popup menu with drag-to-open
        // val popup = makeFavPopup(popupView, presenter.getCategories())
        // popupView.setOnTouchListener(popup?.dragToOpenListener)
    }
    
    /**
     * Helper method to show category selection sheet.
     */
    private fun showCategoriesSheet() {
        val adding = !presenter.manga.favorite
        lifecycleScope.launch(Dispatchers.IO) {
            presenter.manga.moveCategories(this@MangaDetailsActivity, adding) {
                lifecycleScope.launch(Dispatchers.Main) {
                    updateHeader()
                    if (adding) {
                        binding.root.snack(yokai.i18n.MR.strings.added_to_library, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                    }
                }
            }
        }
    }
    
    /**
     * Helper method to toggle manga favorite status.
     */
    private fun toggleMangaFavorite() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Note: This function expects a Controller, but we're in an Activity.
            // For now, keeping the implementation simple without duplicate checking.
            // TODO Phase 3.6: Implement full addOrRemoveToFavorites with duplicate checking
            
            if (!presenter.manga.favorite) {
                // Add to favorites
                presenter.manga.favorite = true
                lifecycleScope.launch(Dispatchers.Main) {
                    updateHeader()
                    showCategoriesSheet()
                    binding.root.snack(yokai.i18n.MR.strings.added_to_library, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                }
            } else {
                // Remove from favorites
                presenter.manga.favorite = false
                lifecycleScope.launch(Dispatchers.Main) {
                    updateHeader()
                    presenter.confirmDeletion()
                }
            }
        }
    }
    //endregion
    
    //region Menu handling
    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.manga_details, menu)

        // Phase 2.1: Apply toolbar theming when menu is created
        colorToolbar(binding.recycler.canScrollVertically(-1))

        // Guard: manga may not be initialized yet if menu is created before DB load completes
        if (!presenter.isMangaLateInitInitialized()) {
            // Hide all action items until manga loads
            listOf(
                R.id.action_edit, R.id.action_download, R.id.action_mark_all_as_read,
                R.id.action_mark_all_as_unread, R.id.action_remove_downloads,
                R.id.remove_non_bookmarked, R.id.action_migrate, R.id.action_search,
            ).forEach { menu.findItem(it)?.isVisible = false }
            return true
        }

        updateMenuVisibility(menu)

        // Update migrate menu title with series type
        menu.findItem(R.id.action_migrate)?.title = getString(
            yokai.i18n.MR.strings.migrate_,
            presenter.manga.seriesType(this),
        )
        
        // Update download menu titles
        menu.findItem(R.id.download_next)?.title = 
            getString(yokai.i18n.MR.plurals.next_unread_chapters, 1, 1)
        menu.findItem(R.id.download_next_5)?.title = 
            getString(yokai.i18n.MR.plurals.next_unread_chapters, 5, 5)
        
        // Setup search view
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as? androidx.appcompat.widget.SearchView
        searchView?.queryHint = getString(yokai.i18n.MR.strings.search_chapters)
        
        searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText ?: ""
                adapter.setFilter(query)
                adapter.performFilter()
                return true
            }
        })
        
        return true
    }
    
    private fun updateMenuVisibility(menu: android.view.Menu) {
        // Edit only visible if favorited or local
        menu.findItem(R.id.action_edit)?.isVisible = 
            (presenter.manga.favorite || presenter.manga.isLocal()) && !presenter.isLockedFromSearch
        
        // Download only visible if online source
        menu.findItem(R.id.action_download)?.isVisible = 
            !presenter.isLockedFromSearch && !presenter.manga.isLocal()
        
        // Mark as read only visible if there are unread chapters
        menu.findItem(R.id.action_mark_all_as_read)?.isVisible = 
            presenter.getNextUnreadChapter() != null && !presenter.isLockedFromSearch
        
        // Mark as unread only visible if there are read chapters
        menu.findItem(R.id.action_mark_all_as_unread)?.isVisible = 
            presenter.anyRead() && !presenter.isLockedFromSearch
        
        // Remove downloads only visible if has downloads
        menu.findItem(R.id.action_remove_downloads)?.isVisible = 
            presenter.hasDownloads() && !presenter.isLockedFromSearch && !presenter.manga.isLocal()
        
        // Remove non-bookmarked only visible if has bookmarks
        menu.findItem(R.id.remove_non_bookmarked)?.isVisible = 
            presenter.hasBookmark() && !presenter.isLockedFromSearch
        
        // Migrate only visible if favorited and online
        menu.findItem(R.id.action_migrate)?.isVisible = 
            !presenter.isLockedFromSearch && !presenter.manga.isLocal() && presenter.manga.favorite
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            R.id.action_edit -> {
                // Open edit manga dialog with activity context
                showEditMangaDialog()
            }
            R.id.action_open_in_web_view -> openInWebView()
            R.id.action_refresh_tracking -> presenter.refreshTracking(true)
            R.id.action_migrate -> {
                // Check network connection before migrating
                if (!this.isOnline()) {
                    binding.root.snack(getString(yokai.i18n.MR.strings.no_network_connection))
                } else {
                    // Launch MainActivity with migration controller
                    // Migration uses Controller architecture in MainActivity (with proper toolbar)
                    val intent = Intent(this, MainActivity::class.java).apply {
                        action = MainActivity.SHORTCUT_MIGRATE
                        putExtra("manga_id", presenter.manga.id!!)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                }
            }
            R.id.action_mark_all_as_read -> {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setMessage(getString(yokai.i18n.MR.strings.mark_all_chapters_as_read))
                    .setPositiveButton(getString(yokai.i18n.MR.strings.mark_as_read)) { _, _ ->
                        markAsRead(presenter.chapters)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.action_mark_all_as_unread -> {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setMessage(getString(yokai.i18n.MR.strings.mark_all_chapters_as_unread))
                    .setPositiveButton(getString(yokai.i18n.MR.strings.mark_as_unread)) { _, _ ->
                        markAsUnread(presenter.chapters)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.remove_all, R.id.remove_read, R.id.remove_non_bookmarked, R.id.remove_custom -> {
                massDeleteChapters(item.itemId)
            }
            R.id.download_next, R.id.download_next_5, R.id.download_custom, 
            R.id.download_unread, R.id.download_all -> {
                downloadChaptersMenu(item.itemId)
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
    
    private fun downloadChaptersMenu(choice: Int) {
        val chaptersToDownload = when (choice) {
            R.id.download_next -> presenter.getUnreadChaptersSorted().take(1)
            R.id.download_next_5 -> presenter.getUnreadChaptersSorted().take(5)
            R.id.download_custom -> {
                // Create ActionMode and wait for user to select range for downloading
                createActionModeIfNeeded()
                rangeMode = RangeMode.Download
                return
            }
            R.id.download_unread -> presenter.chapters.filter { !it.read }
            R.id.download_all -> presenter.allChapters
            else -> emptyList()
        }
        
        if (chaptersToDownload.isNotEmpty()) {
            downloadChapters(chaptersToDownload)
        }
    }
    
    private fun markAsRead(chapters: List<ChapterItem>) {
        presenter.markChaptersRead(chapters, true)
        updateHeader()
    }
    
    private fun markAsUnread(chapters: List<ChapterItem>) {
        presenter.markChaptersRead(chapters, false)
        updateHeader()
    }
    
    private fun massDeleteChapters(choice: Int) {
        android.util.Log.d("MangaDetailsActivity", "🗑️ massDeleteChapters called with choice: $choice (remove_custom=${R.id.remove_custom})")
        val chaptersToDelete = when (choice) {
            R.id.remove_all -> presenter.allChapters
            R.id.remove_read -> presenter.allChapters.filter { it.read }
            R.id.remove_non_bookmarked -> presenter.allChapters.filter { !it.bookmark }
            R.id.remove_custom -> {
                android.util.Log.d("MangaDetailsActivity", "🎯 Custom range selected - creating ActionMode")
                // Create ActionMode and wait for user to select range
                // User clicks first chapter to start, second to complete range
                createActionModeIfNeeded()
                rangeMode = RangeMode.RemoveDownload
                android.util.Log.d("MangaDetailsActivity", "✅ ActionMode created, rangeMode set to RemoveDownload")
                return
            }
            else -> emptyList()
        }.filter { it.isDownloaded }
        
        val isEverything = choice == R.id.remove_all
        
        if (chaptersToDelete.isEmpty() && !isEverything) {
            binding.root.snack(yokai.i18n.MR.strings.no_chapters_to_delete, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
            return
        }
        
        val message = if (isEverything) {
            getString(yokai.i18n.MR.strings.remove_all_downloads)
        } else {
            getString(yokai.i18n.MR.plurals.remove_n_chapters, chaptersToDelete.size, chaptersToDelete.size)
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setPositiveButton(getString(yokai.i18n.MR.strings.remove)) { _, _ ->
                presenter.deleteChapters(chaptersToDelete, isEverything = isEverything)
                // Update UI immediately after deletion
                updateChapters()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    /**
     * Mass delete chapters from a list (used by range selection).
     */
    private fun massDeleteChapters(chapters: List<ChapterItem>, isEverything: Boolean) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setMessage(
                if (isEverything) {
                    getString(yokai.i18n.MR.strings.remove_all_downloads)
                } else {
                    getString(yokai.i18n.MR.plurals.remove_n_chapters, chapters.size, chapters.size)
                }
            )
            .setPositiveButton(getString(yokai.i18n.MR.strings.remove)) { _, _ ->
                presenter.deleteChapters(chapters, isEverything = isEverything)
                // Update UI immediately after deletion
                updateChapters()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    //region Chapter Actions Helper Methods
    
    /**
     * Bookmarks or unbookmarks a chapter (called from swipe gesture).
     */
    override fun bookmarkChapter(position: Int) {
        val chapterItem = (adapter.getItem(position) as? ChapterItem) ?: return
        presenter.bookmarkChapters(listOf(chapterItem), !chapterItem.chapter.bookmark)
        
        // Reset swipe visual state and update item with new bookmark status
        adapter.notifyItemChanged(position)
    }
    
    /**
     * Toggles read/unread status of a chapter (called from swipe gesture).
     */
    override fun toggleReadChapter(position: Int) {
        val chapterItem = (adapter.getItem(position) as? ChapterItem) ?: return
        presenter.markChaptersRead(listOf(chapterItem), !chapterItem.chapter.read)
        
        // Reset swipe visual state and update item with new read status
        adapter.notifyItemChanged(position)
    }
    
    /**
     * Marks all chapters before the selected chapter as read or unread.
     */
    private fun markPreviousAs(chapter: ChapterItem, read: Boolean) {
        val chapters = if (presenter.sortDescending()) presenter.chapters.reversed() else presenter.chapters
        val chapterPos = chapters.indexOf(chapter)
        if (chapterPos != -1) {
            val chaptersToMark = chapters.take(chapterPos)
            if (read) {
                markAsRead(chaptersToMark)
            } else {
                markAsUnread(chaptersToMark)
            }
        }
    }
    
    /**
     * Opens the chapter in a web browser.
     */
    private fun openChapterInWebView(item: ChapterItem) {
        val url = presenter.getChapterUrl(item.chapter) ?: return
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        startActivity(intent)
    }
    
    /**
     * Shows edit manga dialog using a simple Activity-based dialog.
     * This is a simplified version that doesn't require Conductor router.
     */
    private fun showEditMangaDialog() {
        lifecycleScope.launchWhenCreated {
            val manga = presenter.manga
            val dialog = EditMangaDialog.createActivityDialog(
                activity = this@MangaDetailsActivity,
                manga = manga,
                presenter = presenter
            )
            dialog.show()
        }
    }
    
    /**
     * Starts read/unread range selection.
     */
    private fun startReadRange(position: Int, mode: RangeMode) {
        createActionModeIfNeeded()
        rangeMode = mode
        onItemClick(null, position)
    }
    
    /**
     * Creates ActionMode if not already active.
     */
    private fun createActionModeIfNeeded() {
        if (actionMode == null) {
            actionMode = startSupportActionMode(this)
            val view = window?.currentFocus ?: return
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager ?: return
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            if (adapter.mode != SelectableAdapter.Mode.MULTI) {
                adapter.mode = SelectableAdapter.Mode.MULTI
            }
        }
    }
    
    /**
     * Destroys ActionMode if active.
     */
    private fun destroyActionModeIfNeeded() {
        actionMode?.finish()
    }
    
    /**
     * Range selection modes for batch operations.
     */
    //endregion
    
    //region ActionMode.Callback (Range Selection)
    
    override fun onCreateActionMode(mode: ActionMode, menu: android.view.Menu): Boolean {
        adapter.mode = SelectableAdapter.Mode.MULTI
        adapter.clearSelection()
        return true
    }
    
    override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu): Boolean {
        mode.title = if (startingRangeChapterPos == null) {
            getString(yokai.i18n.MR.strings.select_starting_chapter)
        } else {
            getString(yokai.i18n.MR.strings.select_ending_chapter)
        }
        return false
    }
    
    override fun onActionItemClicked(mode: ActionMode, item: android.view.MenuItem): Boolean {
        return true
    }
    
    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        if (startingRangeChapterPos != null && rangeMode in setOf(RangeMode.Download, RangeMode.RemoveDownload)) {
            val item = adapter.getItem(startingRangeChapterPos!!) as? ChapterItem
            (binding.recycler.findViewHolderForAdapterPosition(startingRangeChapterPos!!) as? ChapterHolder)?.notifyStatus(
                item?.status ?: Download.State.NOT_DOWNLOADED,
                false,
                0,
            )
        }
        rangeMode = null
        startingRangeChapterPos = null
        adapter.mode = SelectableAdapter.Mode.IDLE
        adapter.clearSelection()
    }
    
    //endregion
    
    override fun onDestroy() {
        // Cleanup presenter resources - check if initialized first
        if (::presenter.isInitialized) {
            presenter.onDestroy()
        }
        super.onDestroy()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // TODO Phase 3: Save state (scroll position, etc.)
        outState.putLong(EXTRA_MANGA_ID, mangaId)
        outState.putBoolean(EXTRA_FROM_SOURCE, fromSource)
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // TODO Phase 3: Restore state
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
    }
}
