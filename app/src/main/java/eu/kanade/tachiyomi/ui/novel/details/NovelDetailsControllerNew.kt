package eu.kanade.tachiyomi.ui.novel.details

import android.animation.AnimatorInflater
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.touchlab.kermit.Logger
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.SizeResolver
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.data.coil.getBestColor
import com.google.android.material.chip.Chip
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.NovelDetailsControllerBinding
import eu.kanade.tachiyomi.databinding.NovelDetailsHeaderSimpleBinding
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.novel.chapter.NovelChaptersSortBottomSheet
import eu.kanade.tachiyomi.ui.novel.reader.NovelHighlightsActivity
import eu.kanade.tachiyomi.ui.novel.reader.NovelReaderActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.resetStrokeColor
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setAction
import eu.kanade.tachiyomi.util.view.setTextColorAlpha
import eu.kanade.tachiyomi.util.view.snack
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import yokai.domain.novel.Novel
import yokai.domain.novelchapter.models.NovelChapter
import yokai.domain.novel.models.cover
import yokai.i18n.MR
import yokai.presentation.core.Constants
import yokai.util.coil.loadNovel
import yokai.util.lang.getString
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.novel.NovelCoverMetadata
import eu.kanade.tachiyomi.data.coil.getBestColor
import android.R as AR

/**
 * Custom-built NovelDetailsController - lean implementation with only essential features.
 * Built from scratch to avoid manga system complexity.
 * 
 * Features:
 * - Display novel metadata (cover, title, author, description, genres, status)
 * - List chapters with read status and bookmarks
 * - Mark chapters read/unread, toggle bookmarks
 * - Open chapters in NovelReaderActivity
 * - Add/remove from library
 * - Edit novel metadata
 * - Search chapters
 * - Refresh from source
 */
class NovelDetailsControllerNew : BaseCoroutineController<NovelDetailsControllerBinding, NovelDetailsPresenter> {

    private val logger = Logger.withTag("NovelDetailsController")
    
    private var adapter: NovelChapterAdapter? = null
    private var headerBinding: NovelDetailsHeaderSimpleBinding? = null
    private var searchQuery = ""
    private var toolbarIsColored = false
    private var currentToolbarYOffset = 0f  // Tracks toolbar's current Y translation
    private var headerHeight = 0
    
    // Cover-based theming colors
    private var coverColor: Int? = null
    private var accentColor: Int? = null
    private var headerColor: Int? = null
    
    // Range marking state
    private var rangeMode: RangeMode? = null
    private var startingRangeChapterPos: Int? = null
    
    // Reusable Snackbar instance for swipe actions (performance optimization)
    private var actionSnackbar: Snackbar? = null

    constructor(novelId: Long) : super(Bundle().apply {
        putLong(Constants.MANGA_EXTRA, novelId)
    }) {
        android.util.Log.d("NovelDetailsController", "=== CONSTRUCTOR 1: novelId ===")
        android.util.Log.d("NovelDetailsController", "novelId parameter: $novelId")
        this.presenter = NovelDetailsPresenter(novelId)
    }

    constructor(novel: Novel) : super(Bundle().apply {
        putLong(Constants.MANGA_EXTRA, novel.id)
    }) {
        android.util.Log.d("NovelDetailsController", "=== CONSTRUCTOR 2: novel ===")
        android.util.Log.d("NovelDetailsController", "novel.id: ${novel.id}")
        android.util.Log.d("NovelDetailsController", "novel.title: '${novel.title}'")
        android.util.Log.d("NovelDetailsController", "novel.url: '${novel.url}'")
        this.presenter = NovelDetailsPresenter(novel.id).apply {
            setCurrentNovel(novel)
        }
    }
    
    constructor(novel: Novel, fromCatalogue: Boolean) : super(Bundle().apply {
        putLong(Constants.MANGA_EXTRA, novel.id)
        putBoolean("from_catalogue", fromCatalogue)
    }) {
        android.util.Log.d("NovelDetailsController", "=== CONSTRUCTOR 3: novel + fromCatalogue ===")
        android.util.Log.d("NovelDetailsController", "novel.id: ${novel.id}")
        android.util.Log.d("NovelDetailsController", "novel.title: '${novel.title}'")
        android.util.Log.d("NovelDetailsController", "novel.url: '${novel.url}'")
        android.util.Log.d("NovelDetailsController", "fromCatalogue: $fromCatalogue")
        this.presenter = NovelDetailsPresenter(novel.id).apply {
            setCurrentNovel(novel)
        }
    }

    constructor(bundle: Bundle) : this(bundle.getLong(Constants.MANGA_EXTRA)) {
        android.util.Log.d("NovelDetailsController", "=== CONSTRUCTOR 4: bundle ===")
        android.util.Log.d("NovelDetailsController", "Bundle novelId: ${bundle.getLong(Constants.MANGA_EXTRA)}")
    }

    override val presenter: NovelDetailsPresenter

    override fun getTitle(): String? = presenter.novelValue?.title // Title alpha controlled by scroll listener

    override fun createBinding(inflater: LayoutInflater) =
        NovelDetailsControllerBinding.inflate(inflater)
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        
        // Initialize colors on view creation
        coverColor = null
        setAccentColorValue()
        setHeaderColorValue()
        
        // Set initial transparent status bar BEFORE any scrolling happens
        val activity = activity as? AppCompatActivity
        activity?.window?.statusBarColor = Color.TRANSPARENT
        
        setupRecyclerView()
        setupSwipeRefresh()
        observePresenterState()
        setPaletteColor()
        
        logger.d { "View created for novel ${presenter.novelValue?.id}" }
    }

    private fun setupRecyclerView() {
        adapter = NovelChapterAdapter(
            onChapterClick = { chapter -> openChapter(chapter) },
            onChapterLongClick = { chapter -> showChapterMenu(chapter) },
            onDownloadClick = { position -> downloadChapter(position) },
            onSwipeLeft = { position -> toggleReadChapter(position) },
            onSwipeRight = { position -> bookmarkChapter(position) },
            onSwipeStateChanged = { isSwiping -> binding.swipeRefresh.isEnabled = !isSwiping }
        )
        
        binding.recycler.layoutManager = LinearLayoutManager(view?.context)
        binding.recycler.adapter = adapter
        binding.recycler.setHasFixedSize(true)
        
        // Attach swipe helper for chapter actions
        val swipeHelper = adapter?.createSwipeHelper()
        swipeHelper?.attachToRecyclerView(binding.recycler)
        
        // Connect FastScroller to RecyclerView (CRITICAL: must be done AFTER adapter is set)
        // This prevents null RecyclerView crash when scrolling
        binding.fastScroller.setRecyclerView(binding.recycler)
        
        // Setup window insets for edge-to-edge display (content scrolls under status bar)
        val appbarHeight = activityBinding?.appBar?.attrToolbarHeight ?: 0
        val offset = 10.dpToPx
        binding.swipeRefresh.setDistanceToTriggerSync(70.dpToPx)
        
        scrollViewWith(
            binding.recycler,
            padBottom = true,
            customPadding = true,
            swipeRefreshLayout = binding.swipeRefresh,
            afterInsets = { insets ->
                setInsets(insets, appbarHeight, offset)
            },
            liftOnScroll = {
                colorToolbar(it)
            },
        )
        
        // Initialize toolbar: VISIBLE but title hidden (matches manga behavior)
        // Toolbar stays visible (translationY = 0) so icons are always accessible
        // Title alpha = 0 initially, will appear when scrolling down then back up
        activityBinding?.appBar?.translationY = 0f
        activityBinding?.toolbar?.toolbarTitle?.setTextColorAlpha(0)
        currentToolbarYOffset = 0f
        
        // Add scroll listener for toolbar slide/fade animation
        binding.recycler.addOnScrollListener(
            object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    updateToolbarPosition(dy)
                }
                
                override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    // When user stops scrolling, auto-complete the toolbar animation
                    if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                        autoCompleteToolbarAnimation()
                    }
                }
            }
        )
        
        // Setup header
        setupHeaderView()
    }
    
    private fun setupHeaderView() {
        val headerView = LayoutInflater.from(view!!.context)
            .inflate(R.layout.novel_details_header_simple, binding.recycler, false)
        headerBinding = NovelDetailsHeaderSimpleBinding.bind(headerView)
        
        // Set click listeners
        headerBinding?.favoriteButton?.setOnClickListener {
            toggleFavorite()
        }
        
        headerBinding?.startReadingButton?.setOnClickListener {
            startReading()
        }
        
        headerBinding?.trackButton?.setOnClickListener {
            activity?.toast(MR.strings.tracking)
        }
        
        headerBinding?.webviewButton?.setOnClickListener {
            openInWebView()
        }
        
        headerBinding?.shareButton?.setOnClickListener {
            shareNovel()
        }
        
        // Cover image click opens edit dialog
        headerBinding?.novelCover?.setOnClickListener {
            showEditDialog()
        }
        
        // Setup expand/collapse description
        headerBinding?.moreButton?.setOnClickListener {
            expandDescription()
        }
        
        headerBinding?.lessButton?.setOnClickListener {
            collapseDescription()
        }
        
        // Setup chapter header click listeners
        headerBinding?.chapterHeaderLayout?.setOnClickListener {
            showChapterFilterSheet()
        }
        
        headerBinding?.filterButton?.setOnClickListener {
            showChapterFilterSheet()
        }
        
        // Apply accent color to filter button if theming is enabled
        accentColor?.let { color ->
            headerBinding?.filterButton?.imageTintList = ColorStateList.valueOf(color)
        }
        
        // Add header to adapter
        adapter?.setHeaderView(headerView)
        
        // Update chapter count initially
        updateChapterHeader()
    }
    
    private fun expandDescription() {
        headerBinding?.apply {
            novelDescription.maxLines = Integer.MAX_VALUE
            novelGenresTags.isVisible = true
            moreButtonGroup.isVisible = false
            lessButton.isVisible = true
            
            logger.d { "Expanded description - Genre tags count: ${novelGenresTags.childCount}, visibility: ${novelGenresTags.isVisible}" }
        }
    }
    
    private fun collapseDescription() {
        headerBinding?.apply {
            novelDescription.maxLines = 3
            novelGenresTags.isVisible = false
            moreButtonGroup.isVisible = true
            lessButton.isVisible = false
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            presenter.refreshAll()
        }
    }

    private fun observePresenterState() {
        // Observe novel updates
        viewScope.launch {
            presenter.novel.collectLatest { novel ->
                novel?.let {
                    updateHeader(it)
                    // CRITICAL FIX: Re-apply theming after updateHeader() to override any checked() stroke resets
                    updateHeaderColors()
                }
            }
        }
        
        // Observe chapters
        viewScope.launch {
            presenter.chapters.collectLatest { chapters ->
                updateChapterList(chapters)
            }
        }
        
        // Observe loading state
        viewScope.launch {
            presenter.isLoading.collectLatest { isLoading ->
                binding.swipeRefresh.isRefreshing = isLoading
            }
        }
        
        // Observe errors
        viewScope.launch {
            presenter.error.collectLatest { error ->
                error?.let { showError(it) }
            }
        }
    }

    private fun updateHeader(novel: Novel) {
        // Don't set toolbar title here - it's handled by scroll listener
        
        headerBinding?.apply {
            title.text = novel.title
            novelAuthor.text = novel.author ?: activity?.getString(MR.strings.unknown)
            novelStatus.text = when (novel.status.toInt()) {
                1 -> activity?.getString(MR.strings.ongoing)
                2 -> activity?.getString(MR.strings.completed)
                3 -> activity?.getString(MR.strings.licensed)
                else -> activity?.getString(MR.strings.unknown_status)
            }
            novelSource.text = presenter.source?.name ?: "Unknown"
            novelDescription.text = novel.description ?: ""
            
            // Load cover image with placeholder handling
            // If novel has no cover URL, show the placeholder with book icon
            if (novel.posterUrl.isNullOrBlank()) {
                coverPlaceholder.isVisible = true
                coverPlaceholderIcon.isVisible = true
                novelCover.isVisible = false
            } else {
                // Load the cover - keep novelCover hidden until success to avoid
                // showing Coil's default placeholder alongside our custom one
                coverPlaceholder.isVisible = true
                coverPlaceholderIcon.isVisible = true
                novelCover.isVisible = false
                novelCover.imageTintList = null
                novelCover.loadNovel(novel) {
                    listener(
                        onSuccess = { _, _ ->
                            coverPlaceholder.isVisible = false
                            coverPlaceholderIcon.isVisible = false
                            novelCover.isVisible = true
                            novelCover.scaleType = ImageView.ScaleType.CENTER_CROP
                        },
                        onError = { _, _ ->
                            coverPlaceholder.isVisible = true
                            coverPlaceholderIcon.isVisible = true
                            novelCover.isVisible = false
                        }
                    )
                }
            }
            
            // Load blurred backdrop image
            loadBackdrop()
            
            // Update favorite button
            if (novel.isFavorite) {
                favoriteButton.text = activity?.getString(MR.strings.in_library)
                favoriteButton.setIconResource(R.drawable.ic_heart_24dp)
            } else {
                favoriteButton.text = activity?.getString(MR.strings.add_to_library)
                favoriteButton.setIconResource(R.drawable.ic_heart_outline_24dp)
            }
            favoriteButton.checked(novel.isFavorite)
            
            // Update track button to match favorite button's styling (functionality is just a toast for now)
            // This ensures both buttons have identical appearance when unchecked
            trackButton.text = activity?.getString(MR.strings.tracking)
            trackButton.setIconResource(R.drawable.ic_sync_24dp)
            trackButton.checked(false)  // Always unchecked since tracking not implemented yet
            
            // Note: Icon tints will be set in updateButtonColors() after accentColor is properly set
            // This prevents the green flash issue by ensuring checked() uses correct accentColor
            
            // Update genres (initially hidden until description is expanded)
            val genreString = novel.genre
            val genresList = if (genreString.isNullOrBlank()) {
                emptyList()
            } else {
                // Use ", " separator matching manga's getGenres() format
                genreString.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
            }
            logger.d { "Genre string: '${novel.genre}', parsed list: ${genresList.size} items" }
            if (genresList.isNotEmpty()) {
                val chipContext = view?.context ?: return
                novelGenresTags.removeAllViews()
                genresList.forEach { genre ->
                    // CRITICAL FIX: Inflate from layout like manga to get proper chip style with no stroke
                    val chip = LayoutInflater.from(chipContext).inflate(
                        R.layout.genre_chip,
                        novelGenresTags,
                        false
                    ) as Chip
                    chip.id = View.generateViewId()
                    chip.text = genre
                    chip.isClickable = false
                    novelGenresTags.addView(chip)
                }
                // Start hidden - will be shown when description is expanded
                novelGenresTags.isVisible = false
                logger.d { "Added ${genresList.size} genre chips to ChipGroup" }
                
                // CRITICAL FIX: Apply theming immediately after creating chips
                // This ensures chips have proper ColorStateList even before updateHeaderColors() is called
                applyGenreChipTheming()
            } else {
                novelGenresTags.isVisible = false
                logger.d { "No genres to display" }
            }
            
            // Ensure description starts collapsed
            novelDescription.maxLines = 3
            moreButtonGroup.isVisible = true
            lessButton.isVisible = false
            
            // Update start reading button
            val nextChapter = presenter.getNextUnreadChapter()
            if (nextChapter != null) {
                startReadingButton.text = "Continue: ${nextChapter.title}"
            } else {
                startReadingButton.text = activity?.getString(MR.strings.start_reading)
            }
        }
        
        logger.d { "Header updated for: ${novel.title}" }
    }

    private fun updateChapterList(chapters: List<NovelChapter>) {
        val filteredChapters = if (searchQuery.isBlank()) {
            chapters
        } else {
            chapters.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
        
        adapter?.updateChapters(filteredChapters)
        updateChapterHeader()
        logger.d { "Chapter list updated: ${filteredChapters.size} chapters" }
    }
    
    private fun updateChapterHeader() {
        headerBinding?.apply {
            val chapterCount = presenter.chapters.value.size
            chaptersTitle.text = view?.context?.getString(MR.plurals.chapters_plural, chapterCount, chapterCount)
            filtersText.text = presenter.currentFilters()
        }
    }
    
    private fun showChapterFilterSheet() {
        NovelChaptersSortBottomSheet(this).show()
    }

    private fun openChapter(chapter: NovelChapter) {
        android.util.Log.d("NovelDetailsController", "=== openChapter START ===")
        android.util.Log.d("NovelDetailsController", "chapter.id: ${chapter.id}")
        android.util.Log.d("NovelDetailsController", "chapter.novelId: ${chapter.novelId}")
        android.util.Log.d("NovelDetailsController", "chapter.title: '${chapter.title}'")
        android.util.Log.d("NovelDetailsController", "chapter.url: '${chapter.url}'")
        
        // Check if we're in range marking mode
        if (rangeMode != null) {
            if (completeRangeMarking(chapter)) {
                android.util.Log.d("NovelDetailsController", "Range marking completed, not opening chapter")
                return  // Range marking completed, don't open the chapter
            }
        }
        
        val activity = activity as? AppCompatActivity ?: return
        val novel = presenter.novelValue ?: return
        
        android.util.Log.d("NovelDetailsController", "presenter.novelValue:")
        android.util.Log.d("NovelDetailsController", "  - novel.id: ${novel.id}")
        android.util.Log.d("NovelDetailsController", "  - novel.title: '${novel.title}'")
        android.util.Log.d("NovelDetailsController", "  - novel.url: '${novel.url}'")
        
        logger.i { "Opening chapter: ${chapter.title}" }
        
        android.util.Log.d("NovelDetailsController", "Creating reader intent with:")
        android.util.Log.d("NovelDetailsController", "  - novelId: ${novel.id}")
        android.util.Log.d("NovelDetailsController", "  - chapterId: ${chapter.id}")
        
        val intent = NovelReaderActivity.newIntent(activity, novel.id, chapter.id)
        activity.startActivity(intent)
        android.util.Log.d("NovelDetailsController", "=== openChapter END ===")
    }

    private fun showChapterMenu(chapter: NovelChapter) {
        val activity = activity ?: return
        val novel = presenter.novelValue ?: return
        
        // Get sort order to determine icon direction (like manga does)
        val descending = presenter.sortDescending()
        
        val items = mutableListOf(
            MaterialMenuSheet.MenuSheetItem(
                0,
                if (descending) R.drawable.ic_eye_down_24dp else R.drawable.ic_eye_up_24dp,
                MR.strings.mark_previous_as_read
            ),
            MaterialMenuSheet.MenuSheetItem(
                1,
                if (descending) R.drawable.ic_eye_off_down_24dp else R.drawable.ic_eye_off_up_24dp,
                MR.strings.mark_previous_as_unread
            ),
            MaterialMenuSheet.MenuSheetItem(
                2,
                R.drawable.ic_eye_range_24dp,
                MR.strings.mark_range_as_read
            ),
            MaterialMenuSheet.MenuSheetItem(
                3,
                R.drawable.ic_eye_off_range_24dp,
                MR.strings.mark_range_as_unread
            ),
        )
        
        // Add "Open in WebView" at the top if URL is available
        if (novel.url.isNotBlank()) {
            items.add(
                0,
                MaterialMenuSheet.MenuSheetItem(
                    4,
                    R.drawable.ic_open_in_webview_24dp,
                    MR.strings.open_in_webview
                )
            )
        }
        
        MaterialMenuSheet(activity, items, chapter.title) { _, position ->
            when (position) {
                4 -> openChapterInWebView(chapter)
                0 -> markPreviousAsRead(chapter, read = true)
                1 -> markPreviousAsRead(chapter, read = false)
                2 -> startRangeMarking(chapter, RangeMode.Read)
                3 -> startRangeMarking(chapter, RangeMode.Unread)
            }
            true
        }.show()
        
        logger.d { "Showing menu for chapter: ${chapter.title}" }
    }
    
    private fun openChapterInWebView(chapter: NovelChapter) {
        val url = chapter.url
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(url)
        }
        activity?.startActivity(intent)
    }
    
    private fun toggleChapterRead(chapter: NovelChapter) {
        presenter.markChapterRead(chapter, !chapter.read)
        activity?.toast(if (chapter.read) MR.strings.marked_as_unread else MR.strings.marked_as_read)
    }
    
    private fun toggleChapterBookmark(chapter: NovelChapter) {
        presenter.toggleBookmark(chapter)
        activity?.toast(if (chapter.bookmark) MR.strings.removed_bookmark else MR.strings.bookmarked)
    }
    
    /**
     * Swipe action handler: Bookmark/unbookmark a chapter by position.
     * Shows a snackbar with undo option (matches manga implementation).
     */
    fun bookmarkChapter(position: Int) {
        val chapters = presenter.chapters.value
        if (position < 0 || position >= chapters.size) return
        
        val chapter = chapters[position]
        val wasBookmarked = chapter.bookmark
        
        presenter.toggleBookmark(chapter)
        
        // Reuse Snackbar instance for better performance
        showActionSnackbar(
            message = if (wasBookmarked) MR.strings.removed_bookmark else MR.strings.bookmarked,
            action = { presenter.toggleBookmark(chapter) }
        )
    }
    
    /**
     * Download button click handler: Download a novel chapter.
     * Uses NovelDownloadManager to queue chapter for offline reading.
     */
    fun downloadChapter(position: Int) {
        val chapters = presenter.chapters.value
        if (position < 0 || position >= chapters.size) return
        
        val chapter = chapters[position]
        val currentView = view ?: return
        
        // Check if already downloaded
        if (presenter.isChapterDownloaded(chapter)) {
            // Show option to delete download
            Snackbar.make(
                currentView,
                "Chapter already downloaded",
                Snackbar.LENGTH_LONG
            ).setAction("Delete") {
                presenter.deleteDownloadedChapters(listOf(chapter))
                Snackbar.make(currentView, "Download deleted", Snackbar.LENGTH_SHORT).show()
            }.show()
            return
        }
        
        // Queue chapter for download
        presenter.downloadChapters(listOf(chapter))
        Snackbar.make(
            currentView,
            "Downloading: ${chapter.title}",
            Snackbar.LENGTH_SHORT
        ).show()
    }
    
    /**
     * Swipe action handler: Toggle read/unread status by position.
     * Shows a snackbar with undo option (matches manga implementation).
     */
    fun toggleReadChapter(position: Int) {
        val chapters = presenter.chapters.value
        if (position < 0 || position >= chapters.size) return
        
        val chapter = chapters[position]
        val wasRead = chapter.read
        
        presenter.markChapterRead(chapter, !wasRead)
        
        // Reuse Snackbar instance for better performance
        showActionSnackbar(
            message = if (wasRead) MR.strings.marked_as_unread else MR.strings.marked_as_read,
            action = { presenter.markChapterRead(chapter, wasRead) }
        )
    }
    
    /**
     * Shows a reusable Snackbar for swipe actions with undo capability.
     * Optimized to reuse a single Snackbar instance instead of creating new ones.
     */
    private fun showActionSnackbar(message: dev.icerock.moko.resources.StringResource, action: () -> Unit) {
        val currentView = view ?: return
        
        // Dismiss existing snackbar if showing
        actionSnackbar?.dismiss()
        
        // Create or reuse snackbar
        actionSnackbar = Snackbar.make(currentView, currentView.context.getString(message.resourceId), Snackbar.LENGTH_LONG).apply {
            setAction(MR.strings.undo) { action() }
            show()
        }
    }
    
    private fun markPreviousAsRead(chapter: NovelChapter, read: Boolean) {
        val allChapters = presenter.chapters.value
        
        // Get chapters in logical order (highest chapter number first if descending, lowest first otherwise)
        // We need to get chapters sorted by chapter number, then find those with LOWER chapter numbers
        val sortedByNumber = allChapters.sortedBy { it.chapterNumber }
        
        // Find chapters with lower chapter numbers than the selected one
        val previousChapters = sortedByNumber.filter { it.chapterNumber < chapter.chapterNumber }
        
        if (previousChapters.isNotEmpty()) {
            presenter.markMultipleRead(previousChapters, read = read)
            activity?.toast(if (read) MR.strings.marked_as_read else MR.strings.marked_as_unread)
        }
    }
    
    private fun toggleFavorite() {
        val novel = presenter.novelValue ?: return
        viewScope.launch {
            // Call the update novel method with toggled favorite status
            presenter.updateNovelFavoriteStatus(!novel.isFavorite)
            if (!novel.isFavorite) {
                activity?.toast(MR.strings.added_to_library)
            } else {
                activity?.toast(MR.strings.removed_from_library)
            }
        }
    }
    
    private fun startReading() {
        val nextChapter = presenter.getNextUnreadChapter()
        if (nextChapter != null) {
            openChapter(nextChapter)
        } else {
            // Start from first chapter
            val firstChapter = presenter.chapters.value.firstOrNull()
            if (firstChapter != null) {
                openChapter(firstChapter)
            } else {
                activity?.toast("No chapters available")
            }
        }
    }
    
    private fun openInWebView() {
        val novel = presenter.novelValue ?: return
        val url = novel.url
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(url)
        }
        activity?.startActivity(intent)
    }
    
    private fun shareNovel() {
        val novel = presenter.novelValue ?: return
        val url = novel.url
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${novel.title}\n$url")
        }
        activity?.startActivity(Intent.createChooser(intent, activity?.getString(MR.strings.share)))
    }

    private fun showError(message: String) {
        binding.swipeRefresh.isRefreshing = false
        view?.snack(message)
        logger.e { "Error: $message" }
    }

    //region Menu
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.novel_details, menu)
        
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        
        searchView?.apply {
            queryHint = resources?.getString(yokai.i18n.MR.strings.search_chapters.resourceId)
            
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    performSearch(query ?: "")
                    return true
                }
                
                override fun onQueryTextChange(newText: String?): Boolean {
                    performSearch(newText ?: "")
                    return true
                }
            })
            
            if (searchQuery.isNotEmpty()) {
                searchItem.expandActionView()
                setQuery(searchQuery, false)
            }
        }

        // Show Export EPUB only if a compiled EPUB exists
        menu.findItem(R.id.action_export_epub)?.isVisible = presenter.hasCompiledEpub()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_highlights -> {
                val novel = presenter.novelValue ?: return true
                startActivity(
                    NovelHighlightsActivity.newIntent(
                        activity!!,
                        novelTitle = novel.title,
                        novelAuthor = novel.author,
                        posterUrl = novel.posterUrl,
                    )
                )
                return true
            }
            R.id.action_export_epub -> {
                exportEpub()
                return true
            }
            R.id.action_edit -> {
                showEditDialog()
                return true
            }
            R.id.action_refresh_tracking -> {
                presenter.refreshAll()
                return true
            }
            R.id.action_mark_all_as_read -> {
                markAllAsRead()
                return true
            }
            R.id.action_mark_all_as_unread -> {
                markAllAsUnread()
                return true
            }
            // Download actions
            R.id.download_next -> {
                downloadNextUnreadChapters(1)
                return true
            }
            R.id.download_next_5 -> {
                downloadNextUnreadChapters(5)
                return true
            }
            R.id.download_unread -> {
                downloadAllUnreadChapters()
                return true
            }
            R.id.download_all -> {
                downloadAllChapters()
                return true
            }
            R.id.download_custom -> {
                showCustomDownloadDialog()
                return true
            }
            // Remove download actions
            R.id.remove_all -> {
                deleteAllDownloads()
                return true
            }
            R.id.remove_read -> {
                deleteReadDownloads()
                return true
            }
            R.id.remove_non_bookmarked -> {
                deleteNonBookmarkedDownloads()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    //endregion

    //region Actions
    private fun performSearch(query: String) {
        searchQuery = query
        updateChapterList(presenter.chapters.value)
    }

    private fun showEditDialog() {
        val novel = presenter.novelValue ?: return
        EditNovelDialog(this, novel).showDialog(router)
    }

    private fun markAllAsRead() {
        val chapters = presenter.chapters.value
        presenter.markMultipleRead(chapters, read = true)
        activity?.toast(MR.strings.marked_as_read)
    }

    private fun markAllAsUnread() {
        val chapters = presenter.chapters.value
        presenter.markMultipleRead(chapters, read = false)
        activity?.toast(MR.strings.marked_as_unread)
    }
    
    //region Download Actions
    
    private fun downloadNextUnreadChapters(count: Int) {
        val chapters = presenter.chapters.value
        val unreadChapters = chapters.filter { !it.read && !presenter.isChapterDownloaded(it) }
            .take(count)
        
        if (unreadChapters.isEmpty()) {
            activity?.toast("No unread chapters to download")
            return
        }
        
        presenter.downloadChapters(unreadChapters)
        activity?.toast("Downloading ${unreadChapters.size} chapter(s)")
    }
    
    private fun downloadAllUnreadChapters() {
        val chapters = presenter.chapters.value
        val unreadChapters = chapters.filter { !it.read && !presenter.isChapterDownloaded(it) }
        
        if (unreadChapters.isEmpty()) {
            activity?.toast("No unread chapters to download")
            return
        }
        
        presenter.downloadChapters(unreadChapters)
        activity?.toast("Downloading ${unreadChapters.size} chapter(s)")
    }
    
    private fun downloadAllChapters() {
        val chapters = presenter.chapters.value
        val chaptersToDownload = chapters.filter { !presenter.isChapterDownloaded(it) }
        
        if (chaptersToDownload.isEmpty()) {
            activity?.toast("All chapters already downloaded")
            return
        }
        
        presenter.downloadChapters(chaptersToDownload)
        activity?.toast("Downloading ${chaptersToDownload.size} chapter(s)")
    }
    
    private fun showCustomDownloadDialog() {
        val chapters = presenter.chapters.value
        if (chapters.isEmpty()) {
            activity?.toast("No chapters available")
            return
        }
        
        // Show number picker dialog for custom download count
        val context = activity ?: return
        val undownloadedCount = chapters.count { !presenter.isChapterDownloaded(it) }
        
        if (undownloadedCount == 0) {
            activity?.toast("All chapters already downloaded")
            return
        }
        
        // Use MaterialAlertDialogBuilder with number input
        val input = android.widget.EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Number of chapters (max: $undownloadedCount)"
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Download chapters")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val count = input.text.toString().toIntOrNull() ?: 0
                if (count > 0) {
                    val chaptersToDownload = chapters
                        .filter { !presenter.isChapterDownloaded(it) }
                        .take(count)
                    presenter.downloadChapters(chaptersToDownload)
                    activity?.toast("Downloading ${chaptersToDownload.size} chapter(s)")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun deleteAllDownloads() {
        val chapters = presenter.chapters.value
        val downloadedChapters = chapters.filter { presenter.isChapterDownloaded(it) }
        
        if (downloadedChapters.isEmpty()) {
            activity?.toast("No downloaded chapters")
            return
        }
        
        presenter.deleteDownloadedChapters(downloadedChapters)
        activity?.toast("Deleted ${downloadedChapters.size} download(s)")
    }
    
    private fun deleteReadDownloads() {
        val chapters = presenter.chapters.value
        val downloadedReadChapters = chapters.filter { it.read && presenter.isChapterDownloaded(it) }
        
        if (downloadedReadChapters.isEmpty()) {
            activity?.toast("No read downloaded chapters")
            return
        }
        
        presenter.deleteDownloadedChapters(downloadedReadChapters)
        activity?.toast("Deleted ${downloadedReadChapters.size} download(s)")
    }
    
    private fun deleteNonBookmarkedDownloads() {
        val chapters = presenter.chapters.value
        val downloadedNonBookmarked = chapters.filter { 
            !it.bookmark && presenter.isChapterDownloaded(it) 
        }
        
        if (downloadedNonBookmarked.isEmpty()) {
            activity?.toast("No non-bookmarked downloaded chapters")
            return
        }
        
        presenter.deleteDownloadedChapters(downloadedNonBookmarked)
        activity?.toast("Deleted ${downloadedNonBookmarked.size} download(s)")
    }

    private fun exportEpub() {
        val epubFile = presenter.getCompiledEpub()
        val novel = presenter.novelValue ?: return
        if (epubFile == null) {
            activity?.toast("No compiled EPUB available")
            return
        }

        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val safeName = novel.title.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim()
            val destFile = java.io.File(downloadsDir, "$safeName.epub")

            // Handle duplicate names
            var counter = 1
            var finalDest = destFile
            while (finalDest.exists()) {
                finalDest = java.io.File(downloadsDir, "$safeName ($counter).epub")
                counter++
            }

            epubFile.copyTo(finalDest, overwrite = false)
            activity?.toast("Exported EPUB to Downloads: ${finalDest.name}")
        } catch (e: Exception) {
            activity?.toast("Failed to export EPUB: ${e.message}")
        }
    }
    
    //endregion
    
    //region Cover-based theming
    
    /**
     * Sets the accent color based on vibrant cover color with luminance adjustment.
     * Used for button icons and interactive elements.
     */
    private fun setAccentColorValue(colorToUse: Int? = null) {
        val context = view?.context ?: return
        setCoverColorValue(colorToUse)
        accentColor = if (presenter.preferences.themeMangaDetails().get()) {
            (colorToUse ?: presenter.novelValue?.vibrantCoverColor)?.let {
                val luminance = ColorUtils.calculateLuminance(it).toFloat()
                if (if (!context.isInNightMode()) luminance > 0.4 else luminance <= 0.6) {
                    ColorUtils.blendARGB(
                        it,
                        context.contextCompatColor(R.color.colorOnDownloadBadgeDayNight),
                        (if (!context.isInNightMode()) luminance else -(luminance - 1))
                            .toFloat() * if (context.isInNightMode()) 0.33f else 0.5f,
                    )
                } else {
                    it
                }
            }
        } else null
    }
    
    /**
     * Sets the cover/header background color by blending vibrant color with theme background.
     * Ensures proper contrast for overlaid content.
     */
    private fun setCoverColorValue(colorToUse: Int? = null) {
        val context = view?.context ?: return
        val vibrantCoverColor = colorToUse ?: presenter.novelValue?.vibrantCoverColor
        val night = context.isInNightMode()
        coverColor = when {
            presenter.preferences.themeMangaDetails().get() && vibrantCoverColor != null -> {
                val lum = ColorUtils.calculateLuminance(vibrantCoverColor).toFloat()
                val lumWrongForTheme = (night && lum > 0.8) || (!night && lum <= 0.2)
                
                ColorUtils.blendARGB(
                    vibrantCoverColor,
                    context.getResourceColor(R.attr.background),
                    if (lumWrongForTheme) 0.9f else 0.7f
                )
            }
            else -> null
        }
    }
    
    /**
     * Sets the header/toolbar color (currently transparent, can be animated on scroll).
     */
    private fun setHeaderColorValue(colorToUse: Int? = null) {
        headerColor = if (presenter.preferences.themeMangaDetails().get()) {
            (colorToUse ?: presenter.novelValue?.vibrantCoverColor)?.let {
                ColorUtils.setAlphaComponent(it, 0)
            }
        } else null
    }
    
    /**
     * Extracts color from cover image using Palette API and applies to UI.
     * Checks for cached color first to avoid re-extraction on every open.
     * Implementation matches manga's setPaletteColor() 1:1 for consistency.
     */
    private fun setPaletteColor() {
        val view = view ?: return
        val novel = presenter.novelValue ?: return
        
        // Check if we have a cached color - if so, use it instead of re-extracting
        novel.vibrantCoverColor?.let { cachedColor ->
            logger.d { "Using cached vibrantCoverColor: ${cachedColor.toString(16)}" }
            setAccentColorValue(cachedColor)
            setHeaderColorValue(cachedColor)
            updateHeaderColors()
            setItemColors()
            coverColor?.let { color -> setBackDrop(color) }
            return
        }

        val request = ImageRequest.Builder(view.context)
            .data(novel)
            .size(SizeResolver.ORIGINAL)
            .allowHardware(false)
            .target(
                onSuccess = { result ->
                    val drawable = result.asDrawable(view.context.resources)

                    val copy = (drawable as? BitmapDrawable)?.let {
                        BitmapDrawable(
                            view.context.resources,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                it.bitmap.copy(Bitmap.Config.HARDWARE, false)
                            else
                                it.bitmap.copy(it.bitmap.config!!, false),
                        )
                    } ?: drawable

                    // Don't use 'copy', Palette doesn't like its bitmap, could be caused by mutability is disabled,
                    // or perhaps because it's HARDWARE configured, not entirely sure why, the behaviour is not
                    // documented by Google.
                    val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    // Generate the Palette on a background thread.
                    if (bitmap != null) {
                        Palette.from(bitmap).generate { palette ->
                            if (presenter.preferences.themeMangaDetails().get()) {
                                launchUI {
                                    val vibrantColor = palette?.getBestColor() ?: return@launchUI
                                    // Save color to novel and DB for caching
                                    presenter.novelValue?.vibrantCoverColor = vibrantColor
                                    presenter.updateNovelCoverColor(vibrantColor)
                                    setAccentColorValue(vibrantColor)
                                    setHeaderColorValue(vibrantColor)
                                    updateHeaderColors()
                                    setItemColors()
                                    // Set backdrop color after extracting palette
                                    coverColor?.let { color -> setBackDrop(color) }
                                }
                            } else {
                                setCoverColorValue()
                                // Apply background color even if theming disabled (optional)
                                coverColor?.let { color -> setBackDrop(color) }
                            }
                        }
                    }
                    // Note: We don't set the image here as it's already loaded by loadNovel()
                },
                onError = { _ ->
                    // If cover loading fails, we just won't have theming colors
                    logger.w { "Failed to load cover for palette extraction" }
                },
            ).build()
        view.context.imageLoader.enqueue(request)
    }
    
    /**
     * Apply colors to header UI elements (buttons, icons, chips).
     */
    private fun updateHeaderColors() {
        val binding = headerBinding ?: return
        val accentColor = this.accentColor ?: return
        val context = view?.context ?: return
        
        with(binding) {
            // Icon buttons (ImageView)
            shareButton.imageTintList = ColorStateList.valueOf(accentColor)
            webviewButton.imageTintList = ColorStateList.valueOf(accentColor)
            
            // Filter button in chapter header
            filterButton?.imageTintList = ColorStateList.valueOf(accentColor)
            
            // Material buttons with state lists
            val states = arrayOf(
                intArrayOf(-AR.attr.state_enabled), // Disabled state
                intArrayOf()                         // Enabled state
            )
            val colors = intArrayOf(
                ColorUtils.setAlphaComponent(accentColor, 43),  // 17% opacity when disabled
                accentColor                                       // Full opacity when enabled
            )
            
            startReadingButton.backgroundTintList = ColorStateList(states, colors)
            
            // More/Less buttons
            TextViewCompat.setCompoundDrawableTintList(moreButton, ColorStateList.valueOf(accentColor))
            moreButton.setTextColor(accentColor)
            TextViewCompat.setCompoundDrawableTintList(lessButton, ColorStateList.valueOf(accentColor))
            lessButton.setTextColor(accentColor)
            
            // Apply genre chip theming
            applyGenreChipTheming()
        }
        
        // CRITICAL: Update button colors AFTER accentColor is set
        // This prevents green flash by ensuring checked() uses correct accentColor
        updateButtonColors()
        
        logger.d { "Applied cover-based theming with accent color: ${Integer.toHexString(accentColor)}" }
    }
    
    /**
     * Updates favorite and track button colors following manga's pattern.
     * MUST be called AFTER accentColor is set to prevent green flash.
     * 
     * Pattern from MangaHeaderHolder.updateColors():
     * 1. Set icon tints explicitly with accentColor
     * 2. Call checked() again to refresh button state with correct colors
     */
    private fun updateButtonColors() {
        val binding = headerBinding ?: return
        val currentAccentColor = accentColor ?: return
        val novel = presenter.novelValue ?: return
        
        with(binding) {
            // Set icon tints explicitly (prevents green flash)
            trackButton.iconTint = ColorStateList.valueOf(currentAccentColor)
            favoriteButton.iconTint = ColorStateList.valueOf(currentAccentColor)
            
            // Refresh checked state to apply proper background/stroke colors
            // Now that accentColor field is set, checked() will use it instead of falling back to green
            favoriteButton.checked(novel.isFavorite)
            trackButton.checked(false) // Tracking not implemented yet
        }
        
        logger.d { "Updated button colors with accent: ${Integer.toHexString(currentAccentColor)}" }
    }
    
    /**
     * Apply themed colors to genre chips with ColorStateList.
     * Extracted to separate function so it can be called both:
     * 1. When chips are created in updateHeader()
     * 2. When theming changes in updateHeaderColors()
     */
    private fun applyGenreChipTheming() {
        val binding = headerBinding ?: return
        val accentColor = this.accentColor ?: return
        val context = view?.context ?: return
        
        if (binding.novelGenresTags.childCount == 0) return
        
        val backgroundColor = context.getResourceColor(R.attr.background)
        val bgArray = FloatArray(3)
        val accentArray = FloatArray(3)
        ColorUtils.colorToHSL(backgroundColor, bgArray)
        ColorUtils.colorToHSL(accentColor, accentArray)
        
        val isDark = context.isInNightMode()
        val isAMOLED = backgroundColor == Color.BLACK
        
        // Tag background: accent hue + background saturation + theme-appropriate luminance with alpha
        val downloadedColor = ColorUtils.setAlphaComponent(
            ColorUtils.HSLToColor(
                floatArrayOf(
                    accentArray[0],  // Use accent hue
                    bgArray[1],      // Use background saturation
                    when {
                        isAMOLED && isDark -> 0.1f        // Very dark for AMOLED
                        isDark -> 0.225f                   // Dark gray
                        else -> 0.85f                      // Light gray
                    }
                )
            ),
            199  // Alpha 199 for subtle transparency like manga
        )
        
        // Text color: accent hue + accent saturation + high contrast luminance
        val textColor = ColorUtils.HSLToColor(
            floatArrayOf(
                accentArray[0],                      // Accent hue
                accentArray[1],                      // Accent saturation
                if (isDark) 0.945f else 0.175f       // Very bright or very dark
            )
        )
        
        // Create ColorStateList with two states like manga (normal + activated)
        val chipStates = arrayOf(
            intArrayOf(-AR.attr.state_activated),  // Not activated (normal)
            intArrayOf()                            // Activated (selected)
        )
        val chipColors = intArrayOf(
            downloadedColor,                        // Normal state color
            ColorUtils.blendARGB(                   // Activated state (blended)
                downloadedColor,
                context.getResourceColor(R.attr.colorControlNormal),
                0.25f
            )
        )
        val colorStateList = ColorStateList(chipStates, chipColors)
        
        // Apply to all chips with filled style (ColorStateList for state-aware colors)
        for (i in 0 until binding.novelGenresTags.childCount) {
            val chip = binding.novelGenresTags.getChildAt(i) as? Chip ?: continue
            chip.chipBackgroundColor = colorStateList  // Use ColorStateList instead of single color
            chip.setTextColor(textColor)
            // Don't override chipCornerRadius - use default from layout
        }
        
        logger.d { "Applied genre chip theming to ${binding.novelGenresTags.childCount} chips" }
    }
    
    /**
     * Apply accent colors to chapter list items (download icons).
     * Updates all visible chapter ViewHolders to use the extracted accent color.
     */
    private fun setItemColors() {
        val accentColor = this.accentColor ?: return
        val adapter = this.adapter ?: return
        
        // Set the accent color on the adapter - this will trigger rebind of all items
        adapter.accentColor = accentColor
    }
    
    /**
     * Sets the backdrop solid color background.
     * @param color The color to apply to the backdrop
     */
    private fun setBackDrop(color: Int) {
        val binding = headerBinding ?: return
        binding.trueBackdrop.setBackgroundColor(color)
    }
    
    /**
     * Sets the top padding height for window insets (status bar).
     * Extension function to match manga's pattern.
     */
    private fun View.setTopHeight(newHeight: Int) {
        val binding = headerBinding ?: return
        if (newHeight == binding.topView.height) return
        binding.topView.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
            height = newHeight
        }
    }
    
    /**
     * Loads the blurred cover image into the backdrop ImageView.
     * Uses blur transformation to create the atmospheric background effect.
     */
    private fun loadBackdrop() {
        val binding = headerBinding ?: return
        val novel = presenter.novelValue ?: return
        val context = view?.context ?: return
        
        // Load cover with blur transformation (using Android 12+ API)
        binding.backdrop.loadNovel(novel) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                target(
                    onSuccess = { result ->
                        val drawable = result.asDrawable(context.resources)
                        val bitmap = (drawable as? BitmapDrawable)?.bitmap
                        
                        if (bitmap != null) {
                            // Apply RenderEffect blur on Android 12+
                            binding.backdrop.setImageDrawable(drawable)
                            binding.backdrop.setRenderEffect(
                                RenderEffect.createBlurEffect(
                                    8f, 8f,
                                    Shader.TileMode.MIRROR
                                )
                            )
                        }
                    }
                )
            }
        }
    }
    
    //endregion
    
    /**
     * Handles window insets to enable edge-to-edge display.
     * Adds padding for status bar so content can scroll underneath.
     * Matches manga's setInsets() implementation.
     */
    private fun setInsets(insets: WindowInsetsCompat, appbarHeight: Int, offset: Int) {
        val systemInsets = insets.ignoredSystemInsets
        binding.recycler.updatePaddingRelative(bottom = systemInsets.bottom)
        
        headerHeight = appbarHeight + systemInsets.top
        binding.swipeRefresh.setProgressViewOffset(false, (-40).dpToPx, headerHeight + offset)
        
        headerBinding?.let { it.root.setTopHeight(headerHeight) }
        
        binding.fastScroller.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = headerHeight
            bottomMargin = systemInsets.bottom
        }
        binding.fastScroller.scrollOffset = headerHeight
    }
    
    private fun colorToolbar(isColor: Boolean, animate: Boolean = true) {
        if (isColor == toolbarIsColored) return
        val activity = activity as? AppCompatActivity ?: return
        toolbarIsColored = isColor
        
        // Keep bars solid color when scrolled (no opacity animation)
        // Only the SLIDE animation provides visual feedback
        val scrollingColor = activity.getResourceColor(R.attr.colorPrimaryVariant)
        
        // Set solid colors immediately - no animation needed
        // The slide animation is the visual cue for scrolling
        if (toolbarIsColored) {
            activityBinding?.appBar?.setBackgroundColor(scrollingColor)
            activity.window?.statusBarColor = scrollingColor
        } else {
            activityBinding?.appBar?.setBackgroundColor(Color.TRANSPARENT)
            activity.window?.statusBarColor = Color.TRANSPARENT
        }
    }
    
    /**
     * Updates toolbar position with slide animation that follows scroll direction.
     * 
     * Correct Flow:
     * 1. Initial: Toolbar visible (Y=0), title hidden (alpha=0)
     * 2. Scroll DOWN: Toolbar slides UP (hides), title stays hidden
     * 3. Scroll UP: Toolbar slides DOWN (shows), title appears
     * 4. Near top (at header): Title fades out, toolbar stays visible
     * 
     * Scroll direction:
     * - dy > 0 = scrolling DOWN (content moves up) = HIDE toolbar
     * - dy < 0 = scrolling UP (content moves down) = SHOW toolbar
     */
    private fun updateToolbarPosition(dy: Int) {
        val appBar = activityBinding?.appBar ?: return
        val toolbarTextView = activityBinding?.toolbar?.toolbarTitle ?: return
        val layoutManager = binding.recycler.layoutManager as? LinearLayoutManager ?: return
        val activity = activity as? AppCompatActivity ?: return
        
        val appBarHeight = appBar.height.toFloat()
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val firstCompletelyVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
        val scrollOffset = binding.recycler.computeVerticalScrollOffset()
        
        // Get colors - use colorSurface like manga does for correct appearance
        val scrollingColor = activity.getResourceColor(R.attr.colorSurface)
        val topColor = ColorUtils.setAlphaComponent(scrollingColor, 0)
        
        // Check if we're near the top (trigger transparency when scrolled near author name)
        // Threshold: 150dp scroll = roughly where author name is in header
        // Simplified check: scroll offset only, no position requirement
        val isNearTop = scrollOffset < 150.dpToPx
        
        if (isNearTop) {
            // TRANSPARENT MODE: Near top, keep toolbar visible with transparent background
            appBar.translationY = 0f
            currentToolbarYOffset = 0f
            
            // Keep background transparent when near top
            appBar.setBackgroundColor(Color.TRANSPARENT)
            activity.window?.statusBarColor = Color.TRANSPARENT
            
            // Fade title based on scroll offset (same logic as manga)
            val fadeAlpha = ((scrollOffset - 20.dpToPx).coerceIn(0, 255) / 255f)
            toolbarTextView.setTextColorAlpha((fadeAlpha * 255).roundToInt())
            
        } else {
            // SLIDE MODE: Away from top, slide toolbar and show/hide title
            // Bars stay SOLID COLOR (no opacity changes) - only slide animation
            
            // Calculate new Y position based on scroll direction
            // dy > 0 (scroll down) → delta negative → toolbar moves up (hides)
            // dy < 0 (scroll up) → delta positive → toolbar moves down (shows)
            val delta = -dy * 0.5f
            val newY = (currentToolbarYOffset + delta).coerceIn(-appBarHeight, 0f)
            
            appBar.translationY = newY
            currentToolbarYOffset = newY
            
            // Title visibility based on toolbar position
            // When toolbar is visible (Y close to 0), show title
            // When toolbar is hidden (Y close to -height), hide title
            val visibilityRatio = ((newY + appBarHeight) / appBarHeight).coerceIn(0f, 1f)
            toolbarTextView.setTextColorAlpha((visibilityRatio * 255).roundToInt())
            
            // Keep background SOLID - no opacity animation
            appBar.setBackgroundColor(scrollingColor)
            activity.window?.statusBarColor = scrollingColor
        }
    }
    
    /**
     * Auto-completes the toolbar slide animation after user stops scrolling.
     * If toolbar is more than 50% hidden, animates to fully hidden.
     * If toolbar is more than 50% visible, animates to fully visible.
     */
    private fun autoCompleteToolbarAnimation() {
        val appBar = activityBinding?.appBar ?: return
        val toolbarTextView = activityBinding?.toolbar?.toolbarTitle ?: return
        val layoutManager = binding.recycler.layoutManager as? LinearLayoutManager ?: return
        val activity = activity as? AppCompatActivity ?: return
        
        val appBarHeight = appBar.height.toFloat()
        val firstCompletelyVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
        val scrollOffset = binding.recycler.computeVerticalScrollOffset()
        
        // Don't auto-complete in transparent mode (near top)
        val isNearTop = scrollOffset < 150.dpToPx
        if (isNearTop) return
        
        // Determine target position based on current position
        val currentY = currentToolbarYOffset
        val threshold = -appBarHeight / 2f
        val targetY = if (currentY < threshold) -appBarHeight else 0f
        
        // Skip if already at target
        if (currentY == targetY) return
        
        // Get colors
        val scrollingColor = activity.getResourceColor(R.attr.colorSurface)
        val topColor = ColorUtils.setAlphaComponent(scrollingColor, 0)
        
        // Animate to target position
        ValueAnimator.ofFloat(currentY, targetY).apply {
            duration = 150 // Short, snappy animation
            addUpdateListener { animator ->
                val animatedY = animator.animatedValue as Float
                appBar.translationY = animatedY
                currentToolbarYOffset = animatedY
                
                // Update both background AND title alpha during animation
                val visibilityRatio = ((animatedY + appBarHeight) / appBarHeight).coerceIn(0f, 1f)
                appBar.setBackgroundColor(ColorUtils.blendARGB(topColor, scrollingColor, visibilityRatio))
                toolbarTextView.setTextColorAlpha((visibilityRatio * 255).roundToInt())
            }
            start()
        }
    }
    
    //endregion

    override fun onDestroyView(view: View) {
        // Restore normal window insets when leaving novel details
        adapter = null
        super.onDestroyView(view)
    }
    
    /**
     * Sets the checked state of a MaterialButton with proper visual styling.
     * When checked: filled background with accent color blend, transparent stroke
     * When unchecked: transparent background, themed stroke color
     * Matches manga's button styling pattern.
     */
    private fun MaterialButton.checked(checked: Boolean) {
        val context = context ?: return
        val currentAccentColor = accentColor ?: context.getResourceColor(R.attr.colorSecondary)
        
        if (checked) {
            // Filled state: no outline, only background fill
            stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.icon_btn_state_list_anim)
            backgroundTintList = ColorStateList.valueOf(
                ColorUtils.blendARGB(
                    currentAccentColor,
                    context.getResourceColor(R.attr.background),
                    0.706f,  // 70.6% blend for filled appearance
                ),
            )
            strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
        } else {
            // Outline state: accent color stroke with 40% opacity, background color fill
            stateListAnimator = null
            strokeColor = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(currentAccentColor, 102)  // 40% opacity (102/255)
            )
            backgroundTintList = ColorStateList.valueOf(context.getResourceColor(R.attr.background))
        }
    }
    
    //region NovelHeaderInterface
    interface NovelHeaderInterface {
        fun accentColor(): Int?
        fun coverColor(): Int?
    }
    
    // Implement the interface
    fun accentColor(): Int? = accentColor
    fun coverColor(): Int? = coverColor
    //endregion
    
    //region Range marking
    
    /**
     * Starts range marking mode. When another chapter is clicked, all chapters
     * in the range will be marked as read/unread based on the mode.
     */
    private fun startRangeMarking(chapter: NovelChapter, mode: RangeMode) {
        val adapter = adapter ?: return
        val chapters = presenter.chapters.value
        
        // Find chapter position
        val position = chapters.indexOf(chapter)
        if (position == -1) {
            logger.w { "Chapter not found in list for range marking" }
            return
        }
        
        // Store the starting position and mode
        startingRangeChapterPos = position
        rangeMode = mode
        
        // Show toast to inform user
        activity?.toast(
            when (mode) {
                RangeMode.Read -> MR.strings.mark_range_as_read
                RangeMode.Unread -> MR.strings.mark_range_as_unread
            }
        )
        
        logger.d { "Started range marking mode: $mode at position $position" }
    }
    
    /**
     * Called when a chapter is clicked during range marking mode.
     * Marks all chapters in the range and exits range marking mode.
     */
    private fun completeRangeMarking(chapter: NovelChapter): Boolean {
        val startPos = startingRangeChapterPos ?: return false
        val mode = rangeMode ?: return false
        
        val chapters = presenter.chapters.value
        val endPos = chapters.indexOf(chapter)
        
        if (endPos == -1) {
            logger.w { "End chapter not found in list for range marking" }
            return false
        }
        
        // Get the chapter range (inclusive)
        val chapterRange = if (startPos <= endPos) {
            chapters.subList(startPos, endPos + 1)
        } else {
            chapters.subList(endPos, startPos + 1)
        }
        
        // Mark the range based on mode
        when (mode) {
            RangeMode.Read -> {
                presenter.markMultipleRead(chapterRange, read = true)
                activity?.toast(MR.strings.marked_as_read)
            }
            RangeMode.Unread -> {
                presenter.markMultipleRead(chapterRange, read = false)
                activity?.toast(MR.strings.marked_as_unread)
            }
        }
        
        // Clear range marking state
        startingRangeChapterPos = null
        rangeMode = null
        
        logger.d { "Completed range marking: ${chapterRange.size} chapters" }
        return true
    }
    
    /**
     * Range marking modes for chapter operations.
     */
    private enum class RangeMode {
        Read,
        Unread,
    }
    
    //endregion

    companion object {
        fun newInstance(novelId: Long): NovelDetailsControllerNew {
            return NovelDetailsControllerNew(novelId)
        }
        
        fun newInstance(novel: Novel): NovelDetailsControllerNew {
            return NovelDetailsControllerNew(novel)
        }
    }
}
