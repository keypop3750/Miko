package eu.kanade.tachiyomi.ui.novel.reader

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.slider.Slider
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.NovelReaderActivityBinding
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBackgroundColor
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.novel.Novel
import yokai.domain.novel.NovelChapter

/**
 * Novel reader activity with continuous scrolling and character-level position tracking.
 * This activity provides a dedicated reading interface for novels, bypassing the manga 
 * page-based structure for optimized text reading experience.
 * 
 * Uses Koin for dependency injection following QuickNovel pattern.
 */
class NovelReaderActivity : BaseActivity<NovelReaderActivityBinding>() {

    // Phase 4.4: Made internal so settings view can access it for reading mode changes
    internal val viewModel: NovelReaderViewModel by lazy {
        try {
            android.util.Log.d("NovelReaderActivity", "Attempting to get NovelRepository from Koin...")
            val repository: yokai.domain.novel.NovelRepository = org.koin.java.KoinJavaComponent.get(
                yokai.domain.novel.NovelRepository::class.java
            )
            android.util.Log.d("NovelReaderActivity", "Repository obtained successfully: $repository")

            android.util.Log.d("NovelReaderActivity", "Getting SourceManager from Injekt...")
            val sourceManager: eu.kanade.tachiyomi.source.SourceManager = Injekt.get()
            android.util.Log.d("NovelReaderActivity", "SourceManager obtained successfully: $sourceManager")

            android.util.Log.d("NovelReaderActivity", "Creating ViewModel with repository and sourceManager...")
            val vm = androidx.lifecycle.ViewModelProvider(
                this,
                object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return NovelReaderViewModel(repository, sourceManager) as T
                    }
                }
            )[NovelReaderViewModel::class.java]
            android.util.Log.d("NovelReaderActivity", "ViewModel created successfully: $vm")
            vm
        } catch (e: Exception) {
            android.util.Log.e("NovelReaderActivity", "FAILED to create ViewModel", e)
            throw e
        }
    }

    // MIGRATION: Replaced NovelContentAdapter with TextAdapter (Phase 3 - Option A)
    // RecyclerView adapter for paragraph-level content display with QuickNovel pattern
    private val contentAdapter: TextAdapter by lazy {
        val theme = preferences.readerTheme().get()
        val backgroundColor = eu.kanade.tachiyomi.util.system.ThemeUtil.readerBackgroundColor(
            theme,
            getResourceColor(R.attr.background)
        )
        val textColor = when (ReaderBackgroundColor.fromPreference(theme)) {
            ReaderBackgroundColor.GRAY -> android.graphics.Color.WHITE
            ReaderBackgroundColor.BLACK -> android.graphics.Color.WHITE
            ReaderBackgroundColor.WHITE -> android.graphics.Color.BLACK
            else -> getResourceColor(R.attr.colorOnBackground)
        }
        
        TextAdapter(
            textConfig = TextConfig(
                textSize = textPreferences.novelTextSize().get().toFloat(),
                textColor = textColor,
                backgroundColor = backgroundColor,
                horizontalPadding = 16,  // Default 16dp horizontal padding
                verticalPadding = 8,      // Default 8dp vertical padding
                lineSpacing = textPreferences.novelLineHeight().get(),
                paragraphSpacing = textPreferences.novelParagraphSpacing().get(),
                isTextSelectable = true
            ),
            onNavigationClick = { direction ->
                when (direction) {
                    TextItem.LoadDirection.PREVIOUS -> viewModel.navigateToPreviousChapter()
                    TextItem.LoadDirection.NEXT -> viewModel.navigateToNextChapter()
                }
            },
            onCommentsClick = { chapterId ->
                showCommentsDialog(chapterId)
            }
        ).also { adapter ->
            adapter.setHighlightManager(highlightManager)
        }
    }
    
    // PreferencesHelper for reading text settings (renamed to avoid conflict with BaseActivity.preferences)
    private val textPreferences: eu.kanade.tachiyomi.data.preference.PreferencesHelper by uy.kohesive.injekt.injectLazy()

    private var novel: Novel? = null
    private var currentChapter: NovelChapter? = null
    private var isControlsVisible = false

    // Bottom sheet and bottom sheet button controls
    private val bottomSheet: eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterSheet by lazy { 
        findViewById(R.id.novel_chapters_bottom_sheet) 
    }
    private val chaptersButton: ImageButton by lazy {
        findViewById(R.id.chapters_button)
    }
    private val highlightsButton: ImageButton by lazy {
        findViewById(R.id.highlights_button)
    }
    private val settingsButton: ImageButton by lazy {
        findViewById(R.id.settings_button)
    }

    private val highlightManager: NovelHighlightManager by lazy {
        NovelHighlightManager(this)
    }
    private val infoButton: ImageButton by lazy { 
        findViewById(R.id.info_button) 
    }
    // REMOVED: Navigation buttons (nav_layout removed from layout)
    // private val leftChapterButton: ImageButton by lazy {
    //     findViewById(R.id.left_chapter)
    // }
    // private val rightChapterButton: ImageButton by lazy {
    //     findViewById(R.id.right_chapter)
    // }
    // Slider removed - novels don't need progress slider
    // private val chapterProgressSeekbar: Slider by lazy {
    //     findViewById(R.id.chapter_progress_seekbar)
    // }
    // private val leftChapterText: TextView by lazy {
    //     findViewById(R.id.left_chapter_text)
    // }
    // private val rightChapterText: TextView by lazy {
    //     findViewById(R.id.right_chapter_text)
    // }
    // REMOVED: Progress indicators (nav_layout removed from layout)
    // private val leftProgressIndicator: CircularProgressIndicator by lazy {
    //     findViewById(R.id.left_progress)
    // }
    // private val rightProgressIndicator: CircularProgressIndicator by lazy {
    //     findViewById(R.id.right_progress)
    // }
    
    // Phase 4: Debouncing for scroll position tracking
    private val scrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var scrollDebounceRunnable: Runnable? = null
    private val SCROLL_DEBOUNCE_DELAY = 500L // Wait 500ms after scroll stops before saving position
    
    // FIX: Prevent overlapping scroll operations during spacing changes
    private var isRestoringScroll = false
    private val layoutListeners = mutableListOf<android.view.ViewTreeObserver.OnGlobalLayoutListener>()
    
    // FIX: Prevent updateSliderPosition from overriding user drag for 1 second after release
    private var lastUserDragTime = 0L
    private val USER_DRAG_PROTECTION_WINDOW = 1000L // 1 second protection after user releases slider

    // FIX: Prevent scroll listener from fighting with programmatic scrolls (100% slider issue)
    private var isProgrammaticScroll = false

    // FIX: Cache last valid character position for rapid spacing changes
    // When user changes spacing rapidly, post{post{}} hasn't completed layout yet
    // This prevents calculateCurrentCharacterPosition() from returning 0
    private var lastValidCharacterPosition = 0

    // Phase 5: Gesture detector for tap vs long-press (Settings overlay access)
    private val gestureListener = object : GestureDetectorWithLongTap.Listener() {
        override fun onSingleTapConfirmed(ev: MotionEvent): Boolean {
            toggleControlsVisibility()
            return true
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            // Long press on RecyclerView enables text selection
            // RecyclerView's children (TextViews) handle this via textIsSelectable
            binding.novelRecyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    
    private val gestureDetector by lazy {
        GestureDetectorWithLongTap(this, gestureListener)
    }

    companion object {
        const val EXTRA_NOVEL_ID = "novel_id"
        const val EXTRA_CHAPTER_ID = "chapter_id"

        fun newIntent(context: Context, novelId: Long, chapterId: Long? = null): Intent {
            return Intent(context, NovelReaderActivity::class.java).apply {
                putExtra(EXTRA_NOVEL_ID, novelId)
                chapterId?.let { putExtra(EXTRA_CHAPTER_ID, it) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        android.util.Log.d("NovelReaderActivity", "onCreate - Text: ${textPreferences.novelTextSize().get()}sp, Line: ${textPreferences.novelLineHeight().get()}, Spacing: ${textPreferences.novelParagraphSpacing().get()}")
        
        // FIX: Apply theme BEFORE view inflation to prevent white flash
        // Calculate theme colors before setContentView() to ensure proper initial background
        val initialTheme = preferences.readerTheme().get()
        val initialBackgroundColor = eu.kanade.tachiyomi.util.system.ThemeUtil.readerBackgroundColor(
            initialTheme,
            getResourceColor(R.attr.background)
        )
        
        // Initialize binding and set content view
        binding = NovelReaderActivityBinding.inflate(layoutInflater)
        
        // Apply background immediately after inflation, before setContentView
        binding.rootCoordinator.setBackgroundColor(initialBackgroundColor)
        binding.novelContentContainer.setBackgroundColor(initialBackgroundColor)
        
        setContentView(binding.root)

        val novelId = intent.getLongExtra(EXTRA_NOVEL_ID, -1)
        val chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, -1)

        if (novelId == -1L) {
            toast("Novel ID not found", Toast.LENGTH_SHORT)
            finish()
            return
        }

        setupToolbar()
        setupControls()
        // CRITICAL: Hide controls initially - only show after content loads and user interaction
        hideControls()
        setupBackHandler()
        setupTheme()
        setupRecyclerView()
        
        // Initialize ViewModel context for Markwon (Phase 1)
        viewModel.initContext(this)
        
        // Initialize with novel and chapter
        viewModel.initialize(novelId, if (chapterId != -1L) chapterId else null)
        
        // FIX: Theme the loading indicator to match app theme (Phase 0)
        binding.loadingIndicator.setInvertMode(isInvertedFromTheme())
        
        observeViewModel()
        observeTextPreferences()
    }
    
    private fun setupTheme() {
        // Observe theme changes like manga reader does
        lifecycleScope.launch {
            preferences.readerTheme().changes().collect { theme ->
                applyReaderTheme(theme)
            }
        }
        
        // Apply initial theme
        applyReaderTheme(preferences.readerTheme().get())
    }
    
    /**
     * BUG FIX: Observe text preferences and auto-update adapter when settings change.
     * Previously, changes only applied via updateTextSettings() called manually from settings UI.
     * Now settings are reactive - changes take effect immediately even during reading.
     */
    private fun observeTextPreferences() {
        // Observe text size changes
        lifecycleScope.launch {
            textPreferences.novelTextSize().changes().collect { _ ->
                android.util.Log.d("NovelReaderActivity", "Text size changed - updating adapter")
                updateTextSettings()
            }
        }
        
        // Observe line height changes
        lifecycleScope.launch {
            textPreferences.novelLineHeight().changes().collect { _ ->
                android.util.Log.d("NovelReaderActivity", "Line height changed - updating adapter")
                updateTextSettings()
            }
        }
        
        // Observe paragraph spacing changes
        lifecycleScope.launch {
            textPreferences.novelParagraphSpacing().changes().collect { _ ->
                android.util.Log.d("NovelReaderActivity", "Paragraph spacing changed - updating adapter")
                updateTextSettings()
            }
        }
    }
    
    internal fun applyReaderTheme(theme: Int) {
        // Use the same theme system as manga reader
        val backgroundColor = eu.kanade.tachiyomi.util.system.ThemeUtil.readerBackgroundColor(
            theme,
            getResourceColor(R.attr.background)
        )
        
        // Convert reader background color to appropriate text color
        val textColor = when (ReaderBackgroundColor.fromPreference(theme)) {
            ReaderBackgroundColor.GRAY -> android.graphics.Color.WHITE
            ReaderBackgroundColor.BLACK -> android.graphics.Color.WHITE
            ReaderBackgroundColor.WHITE -> android.graphics.Color.BLACK
            else -> getResourceColor(R.attr.colorOnBackground)
        }
        
        // Apply to ALL background layers (Phase 2: RecyclerView uses adapter theming)
        binding.rootCoordinator.setBackgroundColor(backgroundColor)
        binding.novelContentContainer.setBackgroundColor(backgroundColor)
        binding.novelRecyclerView.setBackgroundColor(backgroundColor)
        
        // Update loading indicator theme when theme changes (Phase 0)
        binding.loadingIndicator.setInvertMode(isInvertedFromTheme())
        
        // BUG FIX: Update adapter's textConfig with new colors before notifying
        // This ensures rebind uses correct text/background colors
        val currentConfig = contentAdapter.textConfig
        val updatedConfig = currentConfig.copy(
            textColor = textColor,
            backgroundColor = backgroundColor
        )
        contentAdapter.updateTextConfig(updatedConfig)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        
        // Set toolbar background (90% alpha surface for brighter overlay)
        binding.appBar.setBackgroundColor(getColor(R.color.surface_alpha))
        
        // Tint navigation icon (back button) to match theme
        binding.toolbar.navigationIcon?.setTint(getResourceColor(R.attr.actionBarTintColor))
        
        // Initially hide toolbar
        binding.appBar.isVisible = false
    }

    private fun setupControls() {
        // Initialize bottom sheet behavior
        setupBottomSheet()
        
        // Bottom sheet button controls
        chaptersButton.setOnClickListener {
            toggleBottomSheet()
        }
        
        // Webview button - Open chapter in webview (like manga reader)
        findViewById<ImageButton>(R.id.webview_button).setOnClickListener {
            val chapter = viewModel.currentChapter.value ?: return@setOnClickListener
            val intent = WebViewActivity.newIntent(
                this,
                chapter.url,
                null,
                chapter.title
            )
            startActivity(intent)
        }
        
        highlightsButton.setOnClickListener {
            showHighlightsDialog()
        }
        
        settingsButton.setOnClickListener {
            eu.kanade.tachiyomi.ui.novel.reader.settings.NovelReaderSettingsSheet(this).show()
        }
        
        // REMOVED: Navigation button click listeners (buttons removed with nav_layout)
        // leftChapterButton.setOnClickListener {
        //     viewModel.navigateToPreviousChapter()
        // }
        // 
        // rightChapterButton.setOnClickListener {
        //     viewModel.navigateToNextChapter()
        // }
        
        // Slider removed - novels don't need progress slider
        // chapterProgressSeekbar.addOnChangeListener { _, value, fromUser ->
        //     if (fromUser) {
        //         lastUserDragTime = System.currentTimeMillis()
        //         viewModel.seekToPosition(value / 100f)
        //     }
        // }
        
        // Text labels removed with slider
        // leftChapterText.visibility = android.view.View.GONE
        // rightChapterText.visibility = android.view.View.GONE
    }

    /**
     * Setup RecyclerView for paragraph-level content display (Phase 2).
     * Replaces ScrollView-based rendering with RecyclerView for natural paragraph spacing.
     */
    private fun setupRecyclerView() {
        binding.novelRecyclerView.apply {
            // Use LinearLayoutManager for vertical scrolling
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@NovelReaderActivity)
            
            // Disable item change animations (prevents crossfade when updating alignment/colors)
            itemAnimator = null
            
            // Set adapter
            adapter = contentAdapter
            
            // Phase 5: Add gesture detection for tap vs long-press
            // This enables overlay access (tap) while preserving text selection (long-press)
            addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: MotionEvent): Boolean {
                    gestureDetector.onTouchEvent(e)
                    return false // Don't intercept, let RecyclerView handle scrolling
                }
            })
            
            // Scroll listener for position tracking (Phase 4: RecyclerView-based bookmark tracking)
            // PERFORMANCE FIX: Debounce scroll events - only track position after user stops scrolling
            // Phase 4.3: Add infinite scroll detection for multi-chapter reading
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    // Slider removed - no progress slider for novels
                    if (!isProgrammaticScroll) {
                        // Update character position cache during scroll
                        // This ensures we always have a fresh value for rapid spacing changes
                        val currentPosition = calculateCurrentCharacterPosition()
                        if (currentPosition > 0) {
                            lastValidCharacterPosition = currentPosition
                        }
                        
                        // Phase 4.3: Infinite scroll detection
                        checkInfiniteScrollThreshold(recyclerView, dy)
                        
                        // FIX: Update visible chapter for toolbar title during infinite scroll
                        updateVisibleChapterFromScroll(recyclerView)
                    }
                    
                    // Cancel previous debounce callback
                    scrollDebounceRunnable?.let { scrollHandler.removeCallbacks(it) }
                    
                    // Schedule new callback to execute after scroll stops (500ms delay)
                    scrollDebounceRunnable = Runnable {
                        if (!isProgrammaticScroll) {
                            updateReadingProgress()
                        }
                    }
                    scrollHandler.postDelayed(scrollDebounceRunnable!!, SCROLL_DEBOUNCE_DELAY)
                }
            })
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isControlsVisible) {
                    hideControls()
                } else {
                    finish()
                }
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                updateUI(state)
            }
        }

        // Observe novel info for toolbar updates
        lifecycleScope.launch {
            viewModel.novel.collectLatest { novel ->
                this@NovelReaderActivity.novel = novel
                updateToolbarInfo(novel, currentChapter)
                novel?.let {
                    contentAdapter.setNovelInfo(it.title, it.author)
                }
            }
        }

        // Observe current chapter for toolbar and navigation updates
        lifecycleScope.launch {
            viewModel.currentChapter.collectLatest { chapter ->
                this@NovelReaderActivity.currentChapter = chapter
                updateToolbarInfo(novel, chapter)
                updateChapterProgress()
                chapter?.let {
                    contentAdapter.setChapterInfo(it.title, it.chapterNumber)
                }
            }
        }

        // Phase 3: Observe content items and update RecyclerView adapter
        lifecycleScope.launch {
            viewModel.contentItems.collectLatest { items ->
                if (items.isNotEmpty()) {
                    android.util.Log.d("NovelReaderActivity", "Content loaded: ${items.size} items")
                    
                    // BUG FIX: Only scroll on initial chapter load, not on infinite scroll appends
                    // Capture value BEFORE submitList to ensure correct state
                    val shouldScroll = viewModel.shouldScrollOnNextUpdate.value
                    val currentProgress = viewModel.readingProgress.value
                    val characterPosition = viewModel.getCurrentCharacterPosition()
                    
                    android.util.Log.d("NovelReaderActivity", "Scroll restoration: shouldScroll=$shouldScroll, shouldScrollTo=${currentProgress.shouldScrollTo}, charPos=$characterPosition")
                    
                    // CRITICAL FIX: Use submitList CALLBACK instead of post{}
                    // The callback runs AFTER DiffUtil finishes and adapter is updated
                    // This fixes timing issues where scroll happened before content was ready
                    contentAdapter.submitList(items) {
                        // Callback runs after adapter update is complete
                        binding.novelRecyclerView.post {
                            if (shouldScroll && currentProgress.shouldScrollTo && characterPosition >= 0) {
                                android.util.Log.d("NovelReaderActivity", "Restoring scroll position to character $characterPosition")
                                scrollToCharacterPosition(characterPosition)
                                
                                // Reset flags after scroll restoration to prevent re-scrolling
                                viewModel.clearScrollRestorationFlag()
                            }
                            
                            // NOW show content (after scroll restoration is complete)
                            binding.loadingIndicator.hide()
                            binding.novelRecyclerView.isVisible = true
                            // CRITICAL: Hide ALL overlay controls after content loads - only show on user tap
                            hideControls()
                        }
                    }
                } else {
                    android.util.Log.d("NovelReaderActivity", "Empty items list - showing loading indicator")
                }
            }
        }

        // Phase 3: Observe loading state from old chapterContent flow (for loading indicator)
        lifecycleScope.launch {
            viewModel.chapterContent.collectLatest { content ->
                if (content.startsWith("Loading")) {
                    android.util.Log.d("NovelReaderActivity", "Loading state detected - showing indicator")
                    binding.loadingIndicator.show()
                    binding.novelRecyclerView.isVisible = false
                    // CRITICAL: Hide ALL overlay controls during loading
                    hideControls()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.readerPreferences.collectLatest { preferences ->
                applyReaderPreferences(preferences)
            }
        }

        lifecycleScope.launch {
            viewModel.readingProgress.collectLatest { progress ->
                updateReadingProgress(progress)
            }
        }

        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                handleViewModelEvent(event)
            }
        }
        
        // REMOVED: Navigation button state observers (buttons removed with nav_layout)
        // lifecycleScope.launch {
        //     viewModel.chapters.collectLatest { chapters ->
        //         val currentIndex = chapters.indexOfFirst { it.id == currentChapter?.id }
        //         
        //         leftChapterButton.isEnabled = currentIndex > 0
        //         rightChapterButton.isEnabled = currentIndex >= 0 && currentIndex < chapters.size - 1
        //         
        //         leftChapterButton.alpha = if (currentIndex > 0) 1.0f else 0.5f
        //         rightChapterButton.alpha = if (currentIndex >= 0 && currentIndex < chapters.size - 1) 1.0f else 0.5f
        //     }
        // }
        // 
        // lifecycleScope.launch {
        //     viewModel.isLoadingPrevious.collectLatest { isLoading ->
        //         leftProgressIndicator.isVisible = isLoading
        //         leftChapterButton.isVisible = !isLoading
        //     }
        // }
        // 
        // lifecycleScope.launch {
        //     viewModel.isLoadingNext.collectLatest { isLoading ->
        //         rightProgressIndicator.isVisible = isLoading
        //         rightChapterButton.isVisible = !isLoading
        //     }
        // }
        
    }
    
    // Phase 5.2.4: Bottom sheet management
    // Chapter adapter for bottom sheet chapter list
    private val chapterAdapter = NovelChapterAdapter { chapter ->
        // Load selected chapter and collapse bottom sheet
        viewModel.loadChapterById(chapter.id)
        toggleBottomSheet()
    }

    private fun setupBottomSheet() {
        val sheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet as android.view.View)
        bottomSheet.sheetBehavior = sheetBehavior
        
        // Set initial state to collapsed
        sheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
        
        // Setup chapter RecyclerView
        val chapterRecyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.chapter_recycler)
        chapterRecyclerView.adapter = chapterAdapter
        chapterRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        chapterRecyclerView.alpha = 0f
        
        // Observe chapters from ViewModel and populate adapter
        lifecycleScope.launch {
            viewModel.chapters.collectLatest { chapters ->
                android.util.Log.d("NovelReaderActivity", "Chapters updated: ${chapters.size} chapters")
                chapterAdapter.submitList(chapters)
            }
        }
        
        // Observe current chapter to highlight it in the list
        lifecycleScope.launch {
            viewModel.currentChapter.collectLatest { chapter ->
                chapterAdapter.setCurrentChapter(chapter?.id)
            }
        }
        
        // Setup callback for state changes
        sheetBehavior.addBottomSheetCallback(
            object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: android.view.View, slideOffset: Float) {
                    // Update chapter list visibility based on slide offset
                    chapterRecyclerView.alpha = kotlin.math.max(slideOffset, 0f)
                    
                    // REMOVED: Navigation bar alpha animation (nav_layout removed from layout)
                }
                
                override fun onStateChanged(bottomSheet: android.view.View, newState: Int) {
                    when (newState) {
                        com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED -> {
                            chapterRecyclerView.isClickable = true
                            chapterRecyclerView.isFocusable = true
                        }
                        com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED -> {
                            chapterRecyclerView.isClickable = false
                            chapterRecyclerView.isFocusable = false
                        }
                    }
                }
            }
        )
    }
    
    private fun showHighlightsDialog() {
        val novel = this.novel ?: return
        startActivity(
            NovelHighlightsActivity.newIntent(
                this,
                novel.title,
                novel.author,
                novel.posterUrl,
                novel.vibrantCoverColor,
                getCurrentBackgroundColor()
            )
        )
    }

    private fun showCommentsDialog(chapterId: Long) {
        // Create dialog view
        val dialogView = layoutInflater.inflate(R.layout.dialog_novel_comments, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.comments_recycler)
        val loadingView = dialogView.findViewById<android.widget.ProgressBar>(R.id.comments_loading)
        val emptyView = dialogView.findViewById<TextView>(R.id.comments_empty)
        val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.comments_close)

        // Setup RecyclerView
        val commentsAdapter = NovelCommentsAdapter()
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = commentsAdapter

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()

        // Fetch comments
        lifecycleScope.launch {
            try {
                loadingView.isVisible = true
                recyclerView.isVisible = false
                emptyView.isVisible = false

                val comments = viewModel.fetchComments(chapterId)

                loadingView.isVisible = false

                if (comments.isEmpty()) {
                    emptyView.isVisible = true
                    recyclerView.isVisible = false
                } else {
                    recyclerView.isVisible = true
                    emptyView.isVisible = false
                    commentsAdapter.submitList(comments)
                }
            } catch (e: Exception) {
                loadingView.isVisible = false
                emptyView.text = "Failed to load comments: ${e.message}"
                emptyView.isVisible = true
                recyclerView.isVisible = false
                android.util.Log.e("NovelReader", "Error loading comments", e)
            }
        }
    }

    private fun toggleBottomSheet() {
        val sheetBehavior = bottomSheet.sheetBehavior ?: return
        if (sheetBehavior.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED) {
            sheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
        } else {
            sheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
    }
    
    /**
     * Phase 4.3: Infinite scroll threshold detection.
     * Triggers next/previous chapter loading when user scrolls near edges.
     * Only active in INFINITE_SCROLL mode, respects loading states to prevent duplicate loads.
     * 
     * @param recyclerView The RecyclerView being scrolled
     * @param dy Vertical scroll delta (positive = scrolling down, negative = scrolling up)
     */
    private fun checkInfiniteScrollThreshold(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        dy: Int
    ) {
        val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            ?: return
        
        // BUG FIX: Only trigger in INFINITE_SCROLL mode
        if (viewModel.readingMode.value != ReadingMode.INFINITE_SCROLL) {
            return
        }
        
        val totalItemCount = layoutManager.itemCount
        if (totalItemCount == 0) return
        
        // Check if scrolling down (forward) near bottom (80% threshold)
        if (dy > 0) {
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            val scrollPercentage = (lastVisiblePosition + 1).toFloat() / totalItemCount.toFloat()
            
            if (scrollPercentage >= 0.80f && !viewModel.isLoadingNextChapter) {
                android.util.Log.d("NovelReaderActivity", "=== SCROLL TRIGGER ===")
                android.util.Log.d("NovelReaderActivity", "Infinite scroll threshold reached (80%) - loading next chapter")
                android.util.Log.d("NovelReaderActivity", "Last visible position: $lastVisiblePosition / Total items: $totalItemCount")
                android.util.Log.d("NovelReaderActivity", "Scroll percentage: ${(scrollPercentage * 100).toInt()}%")
                lifecycleScope.launch {
                    viewModel.loadNextChapterInBackground()
                }
            }
        }
        // Check if scrolling up (backward) near top (20% threshold)
        else if (dy < 0) {
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            val scrollPercentage = firstVisiblePosition.toFloat() / totalItemCount.toFloat()
            
            if (scrollPercentage <= 0.20f && !viewModel.isLoadingPreviousChapter) {
                android.util.Log.d("NovelReaderActivity", "=== SCROLL TRIGGER ===")
                android.util.Log.d("NovelReaderActivity", "Infinite scroll threshold reached (top 20%) - loading previous chapter")
                android.util.Log.d("NovelReaderActivity", "First visible position: $firstVisiblePosition / Total items: $totalItemCount")
                android.util.Log.d("NovelReaderActivity", "Scroll percentage: ${(scrollPercentage * 100).toInt()}%")
                lifecycleScope.launch {
                    viewModel.loadPreviousChapterInBackground()
                }
            }
        }
    }
    
    /**
     * Detect which chapter is currently visible on screen and update the toolbar title.
     * This fixes the issue where the chapter number isn't reflected in the top bar during infinite scroll.
     * 
     * @param recyclerView The RecyclerView being scrolled
     */
    private fun updateVisibleChapterFromScroll(recyclerView: androidx.recyclerview.widget.RecyclerView) {
        val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            ?: return
        
        // Get the first visible item position
        val firstVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
        if (firstVisiblePosition == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
            return
        }
        
        // Get the item at this position from the adapter
        val visibleItem = contentAdapter.currentList.getOrNull(firstVisiblePosition) ?: return
        
        // Extract chapter ID from the visible item
        val visibleChapterId = when (visibleItem) {
            is TextItem.Paragraph -> visibleItem.chapterId
            is TextItem.ChapterHeader -> visibleItem.chapterId
            is TextItem.Loading -> visibleItem.chapterId
            is TextItem.Error -> visibleItem.chapterId
            is TextItem.ChapterNavigation -> null // Navigation buttons don't belong to a specific chapter
            is TextItem.CommentsButton -> null
        }
        
        // Only update if we found a valid chapter ID and it's different from current
        if (visibleChapterId != null && visibleChapterId > 0) {
            val currentChapterId = currentChapter?.id ?: 0L
            if (visibleChapterId != currentChapterId) {
                android.util.Log.d("NovelReaderActivity", "Visible chapter changed: $currentChapterId -> $visibleChapterId")
                viewModel.updateCurrentChapterById(visibleChapterId)
            }
        }
    }
    
    private fun navigateToNovelDetails() {
        val novel = this.novel ?: return
        // Navigate to novel details screen
        // TODO: Implement navigation to novel details (similar to manga reader's info button)
        toast("Novel details coming soon")
    }

    private fun updateUI(state: NovelReaderUiState) {
        // Slider removed - no progress slider for novels
        // if (!chapterProgressSeekbar.isPressed) {
        //     val rawProgress = state.readingProgress * 100f
        //     chapterProgressSeekbar.value = roundToNearestStep(rawProgress, 1f)
        // }
    }

    private fun applyReaderPreferences(preferences: yokai.core.novel.reader.NovelReaderPreferences) {
        // TODO Phase 5: Text settings will be applied via adapter ViewHolders
        // For now, just apply background color
        android.util.Log.d("NovelReaderActivity", "applyReaderPreferences() - Phase 5 will implement text settings")
        
        // Apply background color
        binding.novelContentContainer.setBackgroundColor(preferences.backgroundColor)

        // Apply orientation
        requestedOrientation = preferences.orientation.flag

        // Apply keep screen on
        if (preferences.keepScreenOn) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Get the absolute Y coordinate of the RecyclerView's visible content area top.
     * Returns position in window coordinate space (not view-relative).
     * Pattern from QuickNovel's getTopY() - ensures line positions are comparable across views.
     */
    private fun getContentTopY(): Int {
        val location = IntArray(2)
        binding.novelRecyclerView.getLocationInWindow(location)
        return location[1] + binding.novelRecyclerView.paddingTop
    }

    /**
     * Get the absolute Y coordinate of the RecyclerView's visible content area bottom.
     * Returns position in window coordinate space (not view-relative).
     * Pattern from QuickNovel's getBottomY() - ensures line positions are comparable across views.
     */
    private fun getContentBottomY(): Int {
        val location = IntArray(2)
        binding.novelRecyclerView.getLocationInWindow(location)
        return location[1] + binding.novelRecyclerView.height - binding.novelRecyclerView.paddingBottom
    }

    /**
     * Data class representing a single visual line of text.
     * Based on QuickNovel's TextVisualLine architecture for precise character-to-pixel mapping.
     * Each line corresponds to how TextView actually wraps text, NOT paragraph boundaries.
     * 
     * MIGRATION: Changed to use absolute coordinates in window space for cross-view comparability.
     * absoluteStartChar/absoluteEndChar are chapter-global positions (not paragraph-relative).
     * absoluteTop/absoluteBottom are window Y coordinates (via getLocationInWindow).
     */
    private data class TextVisualLine(
        val absoluteStartChar: Int,  // Absolute character position in chapter
        val absoluteEndChar: Int,    // Absolute character position in chapter
        val absoluteTop: Int,        // Absolute Y coordinate in window space
        val absoluteBottom: Int,     // Absolute Y coordinate in window space
        val paragraphIndex: Int      // Which paragraph this line belongs to (for debugging)
    )

    /**
     * Extract precise line-level character positions from visible TextViews.
     * Uses TextView.layout to get actual line wrapping, handles variable text density.
     * This is the key to accurate slider boundaries - no linear interpolation assumptions.
     */
    private fun extractVisibleLines(): List<TextVisualLine> {
        val lines = mutableListOf<TextVisualLine>()
        val layoutManager = binding.novelRecyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return lines
        
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        
        if (firstVisible == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return lines
        
        for (position in firstVisible..lastVisible) {
            // Get ViewHolder and extract TextView
            val viewHolder = binding.novelRecyclerView.findViewHolderForAdapterPosition(position)
            if (viewHolder !is TextAdapter.ParagraphViewHolder) continue
            
            // MIGRATION: Access TextView directly from itemView (standard RecyclerView pattern)
            val textView = viewHolder.itemView.findViewById<TextView>(R.id.paragraph_text) ?: continue
            val layout = textView.layout ?: continue
            
            val paragraph = contentAdapter.currentList.getOrNull(position) as? TextItem.Paragraph ?: continue
            
            // Get absolute Y position of TextView in window coordinates
            val viewLocation = IntArray(2)
            textView.getLocationInWindow(viewLocation)
            val textViewTopY = viewLocation[1]
            
            // Extract each line from TextView's layout
            for (lineIndex in 0 until layout.lineCount) {
                val lineStartChar = layout.getLineStart(lineIndex)
                val lineEndChar = layout.getLineEnd(lineIndex)
                
                // Calculate absolute character indexes in content (chapter-global)
                val absoluteStartChar = paragraph.startCharIndex + lineStartChar
                val absoluteEndChar = paragraph.startCharIndex + lineEndChar
                
                // Calculate absolute pixel positions in window space
                val lineTop = textViewTopY + textView.paddingTop + layout.getLineTop(lineIndex)
                val lineBottom = textViewTopY + textView.paddingTop + layout.getLineBottom(lineIndex)
                
                lines.add(TextVisualLine(
                    absoluteStartChar = absoluteStartChar,
                    absoluteEndChar = absoluteEndChar,
                    absoluteTop = lineTop,
                    absoluteBottom = lineBottom,
                    paragraphIndex = position
                ))
            }
        }
        
        android.util.Log.d("NovelReaderActivity", "Extracted ${lines.size} visual lines from ${lastVisible - firstVisible + 1} paragraphs")
        return lines
    }

    /**
     * Get all visible lines from all visible RecyclerView items.
     * MIGRATION: This method MUST be called AFTER scroll operations complete (inside post{}).
     * Unlike extractVisibleLines(), this aggregates lines from ALL visible items and
     * returns them with absolute coordinates for comparison against screen boundaries.
     * Pattern from QuickNovel's getAllLines() - enables verify-then-adjust scroll algorithm.
     */
    private fun getAllVisibleLines(): List<TextVisualLine> {
        return extractVisibleLines() // For now, just delegate to existing method
        // TODO: If needed, add additional validation or filtering here
    }

    /**
     * Slider removed - novels don't need progress slider.
     * Scroll position tracking still handled by bookmark system.
     */
    private fun updateSliderPosition() {
        // Slider removed - no progress slider for novels
        // if (chapterProgressSeekbar.isPressed) return
        // val timeSinceUserDrag = System.currentTimeMillis() - lastUserDragTime
        // if (timeSinceUserDrag < USER_DRAG_PROTECTION_WINDOW) {
        //     android.util.Log.d("NovelReaderActivity", "Slider update BLOCKED - protecting user drag (${timeSinceUserDrag}ms since release)")
        //     return
        // }
        
        // Slider removed - no longer updating slider position
        // val lines = extractVisibleLines()
        // if (lines.isEmpty()) return
        // ... rest of slider update logic removed
    }

    /**
     * Calculate current character position from RecyclerView scroll state.
     * Returns the character index of the top-most visible content.
     * Used for saving scroll position across layout changes (spacing adjustments).
     */
    /**
     * Calculate current reading position for bookmarks.
     * MIGRATION: Uses first FULLY visible line (QuickNovel pattern) instead of firstVisibleItemPosition.
     * This ensures bookmarks reference content that's actually on screen, not half-scrolled off.
     */
    private fun calculateCurrentCharacterPosition(): Int {
        val visibleLines = getAllVisibleLines()
        if (visibleLines.isEmpty()) return 0
        
        val contentTopY = getContentTopY()
        
        // Find the first FULLY visible line (line.absoluteTop >= contentTopY)
        val firstFullyVisibleLine = visibleLines.firstOrNull { it.absoluteTop >= contentTopY }
            ?: visibleLines.firstOrNull() // Fallback to first line if none fully visible
            ?: return 0
        
        // Return the absolute character position where this line starts
        return firstFullyVisibleLine.absoluteStartChar
    }

    /**
     * Phase 4: Calculate character position from RecyclerView scroll state.
     * Tracks reading progress across paragraph items for accurate bookmarks.
     * 
     * Algorithm:
     * 1. Find first visible paragraph in RecyclerView
     * 2. Sum character counts of all previous paragraphs
     * 3. Add partial offset within visible paragraph based on scroll position
     * 4. Update ViewModel with calculated character position
     */
    private fun updateReadingProgress() {
        val layoutManager = binding.novelRecyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
        
        // Get first visible paragraph position
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePosition == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
            return
        }
        
        // MIGRATION: Get all items from ListAdapter (currentList instead of getItems())
        val items = contentAdapter.currentList
        if (items.isEmpty()) return
        
        var characterPosition = 0
        
        // Sum characters in all paragraphs before first visible
        for (i in 0 until firstVisiblePosition) {
            val item = items.getOrNull(i) as? TextItem.Paragraph ?: continue
            characterPosition = item.endCharIndex + 1 // +1 for paragraph separator
        }
        
        // Calculate partial scroll within visible paragraph
        val firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition)
        if (firstVisibleView != null) {
            val scrollOffset = -firstVisibleView.top
            val totalHeight = firstVisibleView.height
            
            if (totalHeight > 0) {
                val scrollPercent = (scrollOffset.toFloat() / totalHeight).coerceIn(0f, 1f)
                
                val visibleItem = items.getOrNull(firstVisiblePosition) as? TextItem.Paragraph
                if (visibleItem != null) {
                    val paragraphChars = visibleItem.endCharIndex - visibleItem.startCharIndex
                    characterPosition += (paragraphChars * scrollPercent).toInt()
                }
            }
        }
        
        // Update ViewModel with calculated position
        viewModel.updateCharacterPosition(characterPosition)
    }
    
    /**
     * Phase 4: Scroll to saved bookmark position.
     * Converts character position to RecyclerView item index and scrolls.
     */
    private fun updateReadingProgress(progress: NovelReadingProgress) {
        if (!progress.shouldScrollTo) return
        
        val characterPosition = viewModel.getCurrentCharacterPosition()
        // Allow 0 (chapter start) but prevent negative values
        if (characterPosition < 0) return
        
        // Pass the slider progress so we know if it's exactly 100%
        scrollToCharacterPosition(characterPosition, progress.progress)
    }

    /**
     * Phase 4.5: Alternative chunk-based position restoration (demonstration).
     * Shows how to use ViewModel's chunk-based position API for direct paragraph access.
     * More efficient than character-based search when chunk info is available.
     * 
     * @param chapterId Chapter identifier
     * @param chunkIndex Paragraph/chunk index within chapter
     * @param characterOffset Character offset within the chunk
     */
    private fun scrollToChunkPosition(chapterId: Long, chunkIndex: Int, characterOffset: Int) {
        val items = contentAdapter.currentList
        
        // Use TextAdapter's built-in chunk finder (more efficient than scanning)
        val adapterPosition = contentAdapter.findItemPosition(chapterId, chunkIndex)
        
        if (adapterPosition < 0) {
            android.util.Log.w("NovelReaderActivity", "Chunk not found: chapter=$chapterId chunk=$chunkIndex")
            // Fallback: Convert to character position and use standard scrolling
            viewModel.restoreCharacterPosition(chapterId, chunkIndex, characterOffset)?.let { charPos ->
                scrollToCharacterPosition(charPos)
            }
            return
        }
        
        val layoutManager = binding.novelRecyclerView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
        
        isProgrammaticScroll = true
        
        // Scroll to target paragraph
        layoutManager.scrollToPositionWithOffset(adapterPosition, 0)
        
        // Wait for layout, then fine-tune based on character offset within chunk
        binding.novelRecyclerView.post {
            // For now, just align to top of chunk (character offset not yet implemented)
            // Future enhancement: Use getAllVisibleLines() to find exact character position
            
            binding.novelRecyclerView.postDelayed({
                isProgrammaticScroll = false
            }, 300)
        }
    }
    
    /**
     * Phase 4: Scroll RecyclerView to specific character position.
     * Uses same algorithm as updateReadingProgress() for EXACT accuracy.
     * This matches the bookmark system which is proven accurate.
     * 
     * @param sliderProgress Optional slider position (0.0-1.0). If provided and == 1.0,
     *                       uses aggressive scroll to absolute bottom. Otherwise uses
     *                       normal character-based scrolling.
     */
    /**
     * Scroll to a specific character position in the chapter.
     * MIGRATION: Uses QuickNovel's verify-then-adjust pattern instead of assumption-based scroll.
     * Pattern: scrollToPositionWithOffset() → post{} → getAllVisibleLines() → find target → scrollBy(delta)
     */
    private fun scrollToCharacterPosition(characterPosition: Int, sliderProgress: Float? = null) {
        // Special case for character 0 - scroll to absolute beginning
        if (characterPosition == 0) {
            android.util.Log.d("NovelReaderActivity", "Seeking to character 0 - scrolling to absolute top")
            
            isProgrammaticScroll = true
            
            binding.novelRecyclerView.post {
                binding.novelRecyclerView.scrollToPosition(0)
                // Force scroll to very top after layout
                binding.novelRecyclerView.post {
                    binding.novelRecyclerView.scrollBy(0, -9999)
                    
                    // Re-enable scroll listener after scroll completes
                    binding.novelRecyclerView.postDelayed({
                        isProgrammaticScroll = false
                    }, 300)
                }
            }
            return
        }
        
        val items = contentAdapter.currentList
        if (items.isEmpty()) {
            android.util.Log.w("NovelReaderActivity", "Cannot scroll - no items in adapter")
            return
        }
        
        // Find all paragraphs
        val paragraphs = items.filterIsInstance<TextItem.Paragraph>()
        if (paragraphs.isEmpty()) {
            android.util.Log.w("NovelReaderActivity", "No paragraphs found")
            return
        }
        
        // Edge case: If seeking to/beyond last character AND slider is at 100%, scroll to ABSOLUTE bottom
        // FIX: Only use aggressive scroll when slider is EXACTLY at 100%
        // This prevents 95%/98% from showing the same content as 100%
        val lastCharIndex = paragraphs.last().endCharIndex
        val isSliderAt100 = sliderProgress != null && sliderProgress >= 0.995f // Allow small tolerance
        
        if (characterPosition >= lastCharIndex && isSliderAt100) {
            android.util.Log.d("NovelReaderActivity", "100% slider - scrolling to absolute bottom")
            
            val layoutManager = binding.novelRecyclerView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
            val lastPosition = contentAdapter.itemCount - 1
            
            isProgrammaticScroll = true
            
            // Scroll to absolute bottom using aggressive scroll
            binding.novelRecyclerView.post {
                layoutManager.scrollToPosition(lastPosition)
                
                binding.novelRecyclerView.post {
                    // Scroll down by large amount to ensure hitting absolute bottom
                    val scrollAmount = Int.MAX_VALUE / 2
                    binding.novelRecyclerView.scrollBy(0, scrollAmount)
                    
                    binding.novelRecyclerView.post {
                        isProgrammaticScroll = false
                    }
                }
            }
            return
        }
        
        // NEW ALGORITHM: Verify-then-adjust pattern from QuickNovel
        // No assumptions, no boundary detection - just verify actual positions after scroll
        
        // Find paragraph containing target character
        // FIX: Use <= for inclusive end (endCharIndex is the LAST char in paragraph)
        val targetParagraph = paragraphs.find { paragraph ->
            characterPosition >= paragraph.startCharIndex && characterPosition <= paragraph.endCharIndex
        }
        
        if (targetParagraph == null) {
            android.util.Log.w("NovelReaderActivity", "No paragraph found for character $characterPosition")
            return
        }
        
        // Find target paragraph's adapter position
        val targetAdapterPosition = items.indexOf(targetParagraph)
        if (targetAdapterPosition < 0) {
            android.util.Log.w("NovelReaderActivity", "Paragraph not found in adapter items")
            return
        }
        
        val progressPercent = (characterPosition.toFloat() / paragraphs.last().endCharIndex * 100).toInt()
        android.util.Log.d("NovelReaderActivity", "Scrolling to character $characterPosition ($progressPercent%) in paragraph $targetAdapterPosition")
        
        val layoutManager = binding.novelRecyclerView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
        
        // Step 1: Rough scroll to target paragraph
        layoutManager.scrollToPositionWithOffset(targetAdapterPosition, 1)
        
        // Step 2: Wait for layout completion, then fine-tune based on ACTUAL positions
        binding.novelRecyclerView.post {
            // Get all visible lines from the NEW state (after rough scroll)
            val visibleLines = getAllVisibleLines()
            
            // Find the line containing our target character
            val targetLine = visibleLines.firstOrNull { line ->
                characterPosition >= line.absoluteStartChar && 
                characterPosition < line.absoluteEndChar
            }
            
            if (targetLine != null) {
                // Calculate delta from ACTUAL line position to screen top
                val delta = targetLine.absoluteTop - getContentTopY()
                
                android.util.Log.d("NovelReaderActivity", 
                    "Fine-tuning: line at ${targetLine.absoluteTop}, " +
                    "screen top at ${getContentTopY()}, scrolling $delta px")
                
                binding.novelRecyclerView.scrollBy(0, delta)
            } else {
                android.util.Log.w("NovelReaderActivity", 
                    "Target line not found after scroll - character may not be visible yet")
            }
        }
    }

    private fun toggleControlsVisibility() {
        if (isControlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    private fun showControls() {
        isControlsVisible = true
        binding.appBar.isVisible = true
        // REMOVED: nav_layout visibility (navigation bar removed from layout)
        bottomSheet.isVisible = true
    }

    private fun hideControls() {
        isControlsVisible = false
        binding.appBar.isVisible = false
        // REMOVED: nav_layout visibility (navigation bar removed from layout)
        bottomSheet.isVisible = false
    }

    private fun openChapterInWebView() {
        val chapter = currentChapter ?: return
        val novel = this.novel ?: return
        
        // Open chapter URL in webview
        val intent = WebViewActivity.newIntent(
            this,
            chapter.url,
            novel.source,
            novel.title
        )
        startActivity(intent)
    }

    private fun updateToolbarInfo(novel: Novel?, chapter: NovelChapter?) {
        supportActionBar?.apply {
            title = novel?.title ?: "Novel Reader"
            subtitle = if (chapter != null) NovelChapterAdapter.cleanChapterTitle(chapter.title) else "Chapter"
        }
    }

    private fun updateChapterProgress() {
        // Slider removed - no progress slider for novels
        // lifecycleScope.launch {
        //     viewModel.readingProgress.collectLatest { progress ->
        //         val rawProgress = (progress.progress * 100f).coerceIn(0f, 100f)
        //         chapterProgressSeekbar.value = roundToNearestStep(rawProgress, 1f)
        //     }
        // }
    }

    private fun handleViewModelEvent(event: NovelReaderEvent) {
        when (event) {
            is NovelReaderEvent.ShowError -> {
                toast(event.message, Toast.LENGTH_LONG)
            }
            is NovelReaderEvent.ShowMessage -> {
                toast(event.message, Toast.LENGTH_SHORT)
            }
            is NovelReaderEvent.ChapterChanged -> {
                // FIX: Don't show toast for chapter changes - user can see chapter title in toolbar
                // Just hide controls after chapter load completes
                hideControls()
            }
            is NovelReaderEvent.BookmarkToggled -> {
                val message = if (event.isBookmarked) "Bookmarked" else "Bookmark removed"
                toast(message, Toast.LENGTH_SHORT)
            }
            is NovelReaderEvent.ScrollToPosition -> {
                // BUG FIX: Scroll to specific position after manual chapter navigation
                // This ensures scroll bar reflects current chapter only (not accumulated infinite scroll)
                binding.novelRecyclerView.post {
                    val layoutManager = binding.novelRecyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                    if (layoutManager != null) {
                        when (event.position) {
                            0 -> {
                                // Scroll to top (next chapter navigation)
                                layoutManager.scrollToPositionWithOffset(0, 0)
                                android.util.Log.d("NovelReaderActivity", "Scrolled to top after next chapter")
                            }
                            -1 -> {
                                // Scroll to bottom (previous chapter navigation)
                                val lastPosition = contentAdapter.itemCount - 1
                                if (lastPosition >= 0) {
                                    layoutManager.scrollToPositionWithOffset(lastPosition, 0)
                                    android.util.Log.d("NovelReaderActivity", "Scrolled to bottom after previous chapter")
                                }
                            }
                            else -> {
                                // Scroll to specific position (future use)
                                layoutManager.scrollToPositionWithOffset(event.position, 0)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Novel reader should not show menu items in toolbar - matches manga reader behavior
        // Settings and share are available through bottom sheet overlay instead
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Exit reader and return to novel details page
                finish()
                true
            }
            R.id.action_reader_settings -> {
                // TODO: Open reader settings
                toast("Settings coming in Phase 5", Toast.LENGTH_SHORT)
                true
            }
            R.id.action_share -> {
                shareChapter()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shareChapter() {
        val novel = this.novel ?: return
        val chapter = this.currentChapter ?: return
        
        val shareText = "${novel.title} - ${chapter.title}"
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share Chapter"))
    }

    /**
     * Determines if the loading indicator should be inverted based on current theme.
     * Pattern from PagerPageHolder.kt line 127 (manga reader).
     */
    private fun isInvertedFromTheme(): Boolean {
        val backgroundColor = eu.kanade.tachiyomi.util.system.ThemeUtil.readerBackgroundColor(
            preferences.readerTheme().get(),
            getResourceColor(R.attr.background)
        )
        return when (backgroundColor) {
            android.graphics.Color.WHITE -> this.isInNightMode()
            android.graphics.Color.BLACK -> !this.isInNightMode()
            else -> false
        }
    }

    /**
     * Provides current text color for adapter ViewHolders (Phase 2).
     * Called by NovelContentAdapter to apply theme colors to paragraph text.
     */
    fun getCurrentTextColor(): Int {
        val theme = preferences.readerTheme().get()
        return when (ReaderBackgroundColor.fromPreference(theme)) {
            ReaderBackgroundColor.GRAY -> android.graphics.Color.WHITE
            ReaderBackgroundColor.BLACK -> android.graphics.Color.WHITE
            ReaderBackgroundColor.WHITE -> android.graphics.Color.BLACK
            else -> getResourceColor(R.attr.colorOnBackground)
        }
    }

    /**
     * Provides current background color for adapter ViewHolders (Phase 2).
     * Called by NovelContentAdapter to apply theme colors to paragraph backgrounds.
     */
    fun getCurrentBackgroundColor(): Int {
        return eu.kanade.tachiyomi.util.system.ThemeUtil.readerBackgroundColor(
            preferences.readerTheme().get(),
            getResourceColor(R.attr.background)
        )
    }

    /**
     * Handles paragraph click events (Phase 2).
     * Future: Implement paragraph-level interactions (copy, highlight, TTS).
     * MIGRATION: Updated from NovelContentItem.Paragraph to TextItem.Paragraph
     */
    fun onParagraphClicked(paragraph: TextItem.Paragraph) {
        // Currently no-op - interactions will be added in future phases
        android.util.Log.d("NovelReaderActivity", "Paragraph clicked: ${paragraph.paragraphIndex}")
    }

    override fun onPause() {
        super.onPause()
        
        // Phase 4: Cancel any pending scroll tracking to ensure final position is saved
        scrollDebounceRunnable?.let { scrollHandler.removeCallbacks(it) }
        
        updateReadingProgress() // Save final position immediately
        
        // Save current reading position to database
        viewModel.saveCurrentPosition()
    }

    override fun onResume() {
        super.onResume()
        
        android.util.Log.d("NovelReaderActivity", "onResume - Re-syncing text settings")
        
        // FIX: Re-sync adapter with preferences when returning to chapter
        // This ensures spacing/settings persist after leaving and coming back
        updateTextSettings()
        
        // Update reading session
        viewModel.updateReadingSession()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Phase 4: Clean up scroll handler to prevent memory leaks
        scrollDebounceRunnable?.let { scrollHandler.removeCallbacks(it) }
        scrollDebounceRunnable = null
    }

    /**
     * Phase 5: Update text settings in the adapter.
     * QuickNovel approach: Store settings directly in adapter, call notifyDataSetChanged().
     * Called when user changes text size, line height, paragraph spacing, or alignment.
     * 
     * FIX: No adapter swap needed - adapter now handles notifyDataSetChanged() internally.
     * FIX: Single post{} instead of double-nested post{post{}} to minimize visual flash.
     */
    fun updateTextSettings() {
        android.util.Log.d("NovelReaderActivity", "updateTextSettings - Starting")
        
        // CRITICAL: Save position BEFORE any adapter changes while views are still valid
        val savedCharacterPosition = calculateCurrentCharacterPosition()
        
        android.util.Log.d("NovelReaderActivity", "Saved character position: $savedCharacterPosition")
        
        // MIGRATION: Read current settings and create TextConfig
        val theme = preferences.readerTheme().get()
        val backgroundColor = eu.kanade.tachiyomi.util.system.ThemeUtil.readerBackgroundColor(
            theme,
            getResourceColor(R.attr.background)
        )
        val textColor = when (ReaderBackgroundColor.fromPreference(theme)) {
            ReaderBackgroundColor.GRAY -> android.graphics.Color.WHITE
            ReaderBackgroundColor.BLACK -> android.graphics.Color.WHITE
            ReaderBackgroundColor.WHITE -> android.graphics.Color.BLACK
            else -> getResourceColor(R.attr.colorOnBackground)
        }
        
        // FIX: Apply background color to RecyclerView for live updates
        binding.novelRecyclerView.setBackgroundColor(backgroundColor)
        binding.rootCoordinator.setBackgroundColor(backgroundColor)
        binding.novelContentContainer.setBackgroundColor(backgroundColor)
        
        // Convert Int alignment preference to TextAlignment enum
        val textAlignment = when (textPreferences.novelTextAlignment().get()) {
            0 -> yokai.core.novel.reader.TextAlignment.LEFT
            1 -> yokai.core.novel.reader.TextAlignment.CENTER
            2 -> yokai.core.novel.reader.TextAlignment.JUSTIFY
            3 -> yokai.core.novel.reader.TextAlignment.RIGHT
            else -> yokai.core.novel.reader.TextAlignment.LEFT
        }
        
        val textConfig = TextConfig(
            textSize = textPreferences.novelTextSize().get().toFloat(),
            textColor = textColor,
            backgroundColor = backgroundColor,
            horizontalPadding = 16,  // Default 16dp horizontal padding
            verticalPadding = 8,      // Default 8dp vertical padding
            lineSpacing = textPreferences.novelLineHeight().get(),
            paragraphSpacing = textPreferences.novelParagraphSpacing().get(),
            isTextSelectable = true,
            textAlignment = textAlignment,
            chapterTitle = currentChapter?.title ?: "",
            chapterNumber = "Chapter ${viewModel.chapters.value.indexOf(currentChapter) + 1}"
        )
        
        android.util.Log.d("NovelReaderActivity", "Applying settings: text=${textConfig.textSize}sp, spacing=${textConfig.paragraphSpacing}, position=$savedCharacterPosition")
        
        // FIX: Prevent overlapping scroll operations
        if (isRestoringScroll) {
            android.util.Log.d("NovelReaderActivity", "Scroll restoration already in progress - ignoring")
            return
        }
        
        isRestoringScroll = true
        
        // Clean up any existing layout listeners to prevent duplicates
        layoutListeners.forEach { listener ->
            binding.novelRecyclerView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
        layoutListeners.clear()
        
        // MIGRATION: updateTextConfig instead of updateTextSettings
        // Adapter calls notifyDataSetChanged() internally - NO adapter swap needed!
        // This eliminates the visual flash caused by adapter=null
        contentAdapter.updateTextConfig(textConfig)
        
        // Use character-based restoration (stable across layout changes)
        val listener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // Remove listener immediately to avoid repeated calls
                binding.novelRecyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                layoutListeners.remove(this)
                
                android.util.Log.d("NovelReaderActivity", "Layout complete - restoring to character $savedCharacterPosition")
                
                // Use existing character-based scroll method which handles all edge cases
                scrollToCharacterPosition(savedCharacterPosition)
                
                // Re-enable scroll restoration after completion
                isRestoringScroll = false
            }
        }
        layoutListeners.add(listener)
        binding.novelRecyclerView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        
        android.util.Log.d("NovelReaderActivity", "updateTextSettings complete - awaiting layout")
    }
    
    /**
     * Rounds a value to the nearest multiple of the given step size.
     * Required because MaterialSlider with stepSize=2 only accepts even values (0, 2, 4, ..., 100).
     * 
     * @param value The raw value to round
     * @param stepSize The step size to round to (e.g., 2f for stepSize=2)
     * @return The rounded value that's a valid multiple of stepSize
     */
    private fun roundToNearestStep(value: Float, stepSize: Float): Float {
        return (value / stepSize).roundToInt() * stepSize
    }
}