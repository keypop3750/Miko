package eu.kanade.tachiyomi.ui.swipes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import co.touchlab.kermit.Logger
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yuyakaido.android.cardstackview.CardStackLayoutManager
import com.yuyakaido.android.cardstackview.CardStackListener
import com.yuyakaido.android.cardstackview.Direction
import com.yuyakaido.android.cardstackview.Duration
import com.yuyakaido.android.cardstackview.RewindAnimationSetting
import com.yuyakaido.android.cardstackview.StackFrom
import com.yuyakaido.android.cardstackview.SwipeAnimationSetting
import com.yuyakaido.android.cardstackview.SwipeableMethod
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SwipesActivityBinding
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * SwipesActivity - Tinder-style manga discovery interface
 * 
 * Activity-based architecture that "spoofs" being part of MainActivity:
 * - Displays MainActivity's bottom navigation bar (Swipes tab selected)
 * - Displays MainActivity's search toolbar (delegates to browse search)
 * - Seamless navigation flow: tap other tabs → return to MainActivity
 * 
 * Phase 1: Basic navigation spoofing and Activity structure
 * Phase 2: CardStackView integration with swipe mechanics
 * Phase 3+: Data management, filtering, recommendations
 */
class SwipesActivity : AppCompatActivity(), CardStackListener {

    private lateinit var binding: SwipesActivityBinding
    private lateinit var cardStackLayoutManager: CardStackLayoutManager
    private lateinit var cardAdapter: SwipeCardAdapter
    // Grace period flag to prevent interaction during swipe animations
    private var isSwipeInProgress = false
    
    // Flag to block navigation during processing
    private var isNavigationBlocked = false
    
    // Back press callback for navigation blocking during processing
    private val backPressCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // This will never be called when enabled=false
            // But if somehow it is, log and allow
            Logger.w { "🔒 [SWIPES] Back press during processing - should be blocked" }
        }
    }
    
    // Presenter for MVVM pattern - directly instantiated with Injekt
    private val presenter: SwipesPresenter by lazy {
        SwipesPresenter(
            sourceManager = Injekt.get(),
            context = applicationContext
        )
    }

    companion object {
        const val EXTRA_SELECTED_TAB = "selected_tab"

        fun newIntent(context: Context): Intent {
            return Intent(context, SwipesActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = SwipesActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Register back press callback for navigation blocking
        onBackPressedDispatcher.addCallback(this, backPressCallback)

        setupNavigationBar()
        setupSearchToolbar()
        setupCardStackView()
        observePresenter()
        
        // Initialize presenter and load data
        presenter.onCreate()
    }
    
    /**
     * Setup bottom navigation bar matching MainActivity
     * - Select Swipes tab by default
     * - Handle tab clicks to return to MainActivity with appropriate tab
     */
    private fun setupNavigationBar() {
        binding.bottomNav.apply {
            // Select Swipes tab
            selectedItemId = R.id.nav_swipes

            // Handle navigation item selections with blocking check
            setOnItemSelectedListener { item ->
                // Block navigation if processing a swipe
                if (isNavigationBlocked) {
                    Logger.w { "🔒 [SWIPES] Bottom nav blocked - swipe processing in progress" }
                    return@setOnItemSelectedListener false
                }
                
                when (item.itemId) {
                    R.id.nav_swipes -> {
                        // Already on Swipes, do nothing
                        true
                    }
                    else -> {
                        // Return to MainActivity with selected tab
                        val intent = MainActivity.newIntent(this@SwipesActivity).apply {
                            putExtra(EXTRA_SELECTED_TAB, item.itemId)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        finish()
                        true
                    }
                }
            }
        }
    }

    /**
     * Setup search toolbar that delegates to MainActivity browse search
     * Clicking search icon returns user to MainActivity browse tab
     */
    private fun setupSearchToolbar() {
        // Inflate menu
        binding.searchToolbar.inflateMenu(R.menu.swipes_menu)
        
        // Setup SearchView
        val searchItem = binding.searchToolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView
        
        searchView?.apply {
            queryHint = "Search manga..."
            maxWidth = Integer.MAX_VALUE
            
            // Handle search query submission
            setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        Logger.d { "🔍 [SEARCH] Query submitted: $it" }
                        presenter.searchManga(it)
                        clearFocus() // Hide keyboard
                    }
                    return true
                }
                
                override fun onQueryTextChange(newText: String?): Boolean {
                    // Optional: Could implement real-time search here
                    // For now, only search on submit
                    return false
                }
            })
            
            // Handle search view collapse/expand
            setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (!hasFocus && query.isNullOrEmpty()) {
                    // User closed search without query, clear search
                    presenter.clearSearch()
                }
            }
        }
        
        // Handle search expand/collapse
        searchItem?.setOnActionExpandListener(object : android.view.MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: android.view.MenuItem): Boolean {
                Logger.d { "🔍 [SEARCH] Search expanded" }
                return true
            }
            
            override fun onMenuItemActionCollapse(item: android.view.MenuItem): Boolean {
                Logger.d { "🔍 [SEARCH] Search collapsed" }
                presenter.clearSearch()
                return true
            }
        })
        
        // Handle other menu item clicks
        binding.searchToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_filter -> {
                    showFilterDialog()
                    true
                }
                R.id.action_history -> {
                    showHistoryDialog()
                    true
                }
                else -> false
            }
        }
        
        // Navigation icon now just for visual consistency
        // Removed MainActivity delegation - search is now internal
        binding.searchToolbar.navigationIcon = null
    }

    /**
     * Show filter dialog to configure recommendations
     * Phase 4.1: Filter system integration with tabbed interface
     */
    private fun showFilterDialog() {
        val currentFilters = presenter.getCurrentFilters()
        
        TabbedSwipesFilterSheet(
            activity = this,
            currentFilters = currentFilters,
            onFiltersApplied = { newFilters ->
                presenter.applyFilters(newFilters)
            }
        ).show()
    }
    
    /**
     * Show history dialog displaying last 50 swipe decisions
     * Phase 4.2: History Feature - View-only history
     */
    private fun showHistoryDialog() {
        SwipesHistorySheet(
            presenter = presenter
        ).show(supportFragmentManager, "swipes_history")
    }

    /**
     * Setup CardStackView with layout manager and adapter
     * Configured for Tinder-style swiping behavior
     */
    private fun setupCardStackView() {
        // Initialize layout manager with swipe settings
        cardStackLayoutManager = CardStackLayoutManager(this, this).apply {
            setStackFrom(StackFrom.Top)
            setVisibleCount(3)  // Show 3 cards in stack
            setTranslationInterval(8.0f)  // Spacing between cards
            setScaleInterval(0.95f)  // Scale difference between cards
            setMaxDegree(20.0f)  // Max rotation during swipe
            setDirections(Direction.HORIZONTAL)  // Left/right swipes only
            setCanScrollHorizontal(true)
            setCanScrollVertical(false)
            setSwipeableMethod(SwipeableMethod.AutomaticAndManual)  // Both drag and programmatic swipes
            setSwipeThreshold(0.4f)  // Increase threshold - user must swipe 40% of card width (was default 30%)
        }

        // Initialize adapter (no callback needed - prefetching handles everything)
        cardAdapter = SwipeCardAdapter()

        // Setup CardStackView
        binding.cardStackView.apply {
            layoutManager = cardStackLayoutManager
            adapter = cardAdapter
            itemAnimator = DefaultItemAnimator()
        }
    }

    /**
     * Load placeholder data for Phase 2 testing
     * Phase 3+ will replace with real manga data from sources
     */
    /**
     * Observe presenter state changes and update UI accordingly
     */
    private fun observePresenter() {
        // Observe card list updates
        lifecycleScope.launch {
            presenter.cards.collectLatest { cards ->
                Logger.d { "SWIPES: Cards received in observer: size=${cards.size}" }
                cardAdapter.setItems(cards)
                Logger.d { "SWIPES: After setItems, adapter.itemCount=${cardAdapter.itemCount}" }
                updateEmptyState()
            }
        }
        
        // Observe processing state for navigation blocking
        lifecycleScope.launch {
            presenter.isProcessingSwipe.collectLatest { isProcessing ->
                Logger.d { "🔒 [SWIPES] Processing state changed: $isProcessing" }
                updateNavigationBlocking(isProcessing)
            }
        }
        
        // Observe UI state changes (loading, error, etc.)
        lifecycleScope.launch {
            presenter.uiState.collectLatest { state ->
                Logger.d { "SWIPES: UI State changed to: $state" }
                when (state) {
                    is SwipesUiState.Loading -> {
                        // Show loading only if no cards yet (initial load)
                        if (cardAdapter.itemCount == 0) {
                            binding.loadingState.visibility = View.VISIBLE
                            binding.emptyState.visibility = View.GONE
                            binding.cardStackView.visibility = View.GONE
                        }
                        // If we already have cards, don't show loading (background loading)
                    }
                    is SwipesUiState.Success -> {
                        // Hide loading, show cards
                        binding.loadingState.visibility = View.GONE
                        binding.cardStackView.visibility = View.VISIBLE
                        binding.emptyState.visibility = View.GONE
                    }
                    is SwipesUiState.Error -> {
                        // Hide loading, show error in empty state
                        binding.loadingState.visibility = View.GONE
                        binding.emptyStateTitle.text = "Error loading recommendations"
                        binding.emptyStateSubtitle.text = state.message
                        updateEmptyState()
                    }
                    is SwipesUiState.NoSources -> {
                        // Hide loading, show "no sources" message
                        binding.loadingState.visibility = View.GONE
                        binding.emptyStateTitle.text = "No sources available"
                        binding.emptyStateSubtitle.text = "Add sources in Browse to get recommendations"
                        updateEmptyState()
                    }
                    is SwipesUiState.NoMoreCards -> {
                        binding.loadingState.visibility = View.GONE
                        binding.emptyStateTitle.text = "No more content to swipe"
                        binding.emptyStateSubtitle.text = "Add more sources in Browse or adjust your filters"
                        updateEmptyState()
                    }
                    is SwipesUiState.NoResults -> {
                        // Phase 4.2: Search returned no results
                        binding.loadingState.visibility = View.GONE
                        binding.emptyStateTitle.text = "No results found"
                        binding.emptyStateSubtitle.text = "No manga found for '${state.query}'\nTry a different search query"
                        updateEmptyState()
                    }
                }
            }
        }
    }

    /**
     * Update visibility of empty state based on card availability
     */
    private fun updateEmptyState() {
        val isEmpty = cardAdapter.itemCount == 0
        Logger.d { "SWIPES: updateEmptyState - isEmpty=$isEmpty, itemCount=${cardAdapter.itemCount}" }
        Logger.d { "SWIPES: Before update - cardStackView.visibility=${binding.cardStackView.visibility}, emptyState.visibility=${binding.emptyState.visibility}" }
        
        if (isEmpty) {
            // Show empty state with fade-in animation
            binding.cardStackView.visibility = View.GONE
            binding.emptyState.apply {
                visibility = View.VISIBLE
                animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }
        } else {
            // Show card stack, hide empty state
            binding.emptyState.apply {
                animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction { visibility = View.GONE }
                    .start()
            }
            binding.cardStackView.visibility = View.VISIBLE
            
            // TODO Phase 3: Check if running low on cards and trigger background loading
            // if (cardAdapter.itemCount < 5) { loadMoreCards() }
        }
    }

    // ==================== CardStackListener Implementation ====================

    override fun onCardDragging(direction: Direction, ratio: Float) {
        // CRITICAL: Block any new swipes if currently processing a swipe
        // This prevents multiple rapid swipes before category dialog appears
        if (presenter.isProcessingSwipe.value) {
            Logger.d { "🔒 [SWIPES] Blocking swipe gesture - processing in progress" }
            return  // Don't show overlays or allow swipe continuation
        }
        
        // Called continuously while card is being dragged
        // Show overlay feedback based on direction and ratio
        
        // Get the current top card view
        val topPosition = cardStackLayoutManager.topPosition
        if (topPosition >= 0 && topPosition < cardAdapter.itemCount) {
            val cardView = binding.cardStackView.findViewHolderForAdapterPosition(topPosition)?.itemView
            cardView?.let { view ->
                // Find overlay views in the card
                val leftOverlay = view.findViewById<View>(R.id.left_overlay)
                val rightOverlay = view.findViewById<View>(R.id.right_overlay)
                
                when (direction) {
                    Direction.Left -> {
                        // Show red overlay for left swipe (skip/blacklist)
                        leftOverlay?.apply {
                            visibility = View.VISIBLE
                            alpha = ratio  // Fade in based on swipe distance
                        }
                        rightOverlay?.visibility = View.GONE
                    }
                    Direction.Right -> {
                        // Show green overlay for right swipe (add to library)
                        rightOverlay?.apply {
                            visibility = View.VISIBLE
                            alpha = ratio  // Fade in based on swipe distance
                        }
                        leftOverlay?.visibility = View.GONE
                    }
                    else -> {
                        // Hide both overlays for other directions
                        leftOverlay?.visibility = View.GONE
                        rightOverlay?.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onCardSwiped(direction: Direction) {
        // Called when card finishes swiping off screen
        // Since CardStackView increments topPosition, the swiped card is at position 0
        val item = cardAdapter.getItemAt(0)
        val currentPosition = cardStackLayoutManager.topPosition

        // Enable grace period to prevent interaction during animation
        isSwipeInProgress = true
        binding.cardStackView.isEnabled = false

        when (direction) {
            Direction.Left -> {
                // Left swipe: Skip or blacklist
                item?.let {
                    println("Swiped LEFT (skip): ${it.title}")
                    Logger.d { "📱 [SWIPES] Card swiped LEFT: ${it.title}" }
                    presenter.onCardSwipedLeft(it)
                }
            }
            Direction.Right -> {
                // Right swipe: Add to library
                item?.let {
                    println("Swiped RIGHT (add to library): ${it.title}")
                    presenter.onCardSwipedRight(this, it)
                }
            }
            else -> {
                // Shouldn't happen with HORIZONTAL direction setting
            }
        }

        // Remove the swiped card at top position (presenter updates its internal state)
        val topPosition = cardStackLayoutManager.topPosition - 1 // Already incremented by CardStackView
        if (topPosition >= 0) {
            presenter.removeCard(topPosition)
        }
        
        // Prefetch details for upcoming cards (maintain 5-card lookahead)
        presenter.prefetchUpcomingCards(currentPosition)
        
        // Re-enable interaction after 500ms grace period
        // This prevents state corruption during category dialog and card transitions
        binding.cardStackView.postDelayed({
            isSwipeInProgress = false
            binding.cardStackView.isEnabled = true
        }, 500)
    }

    override fun onCardRewound() {
        // Called when undo/rewind happens
        // Phase 4+: Implement undo functionality
        updateEmptyState()
    }

    override fun onCardCanceled() {
        // Called when swipe is cancelled (returned to center)
        // Hide overlays on the current top card
        val topPosition = cardStackLayoutManager.topPosition
        if (topPosition >= 0 && topPosition < cardAdapter.itemCount) {
            val cardView = binding.cardStackView.findViewHolderForAdapterPosition(topPosition)?.itemView
            cardView?.let { view ->
                view.findViewById<View>(R.id.left_overlay)?.visibility = View.GONE
                view.findViewById<View>(R.id.right_overlay)?.visibility = View.GONE
            }
        }
    }

    override fun onCardAppeared(view: View, position: Int) {
        // Called when new card appears at top of stack
        // Trigger background loading when running low on cards
        val remaining = cardAdapter.itemCount - position
        if (remaining <= 5) {
            presenter.loadMoreRecommendations()
        }
    }
    
    override fun onCardDisappeared(view: View, position: Int) {
        // Card has left the visible stack area
        // No action needed for now
    }
    
    /**
     * Update navigation blocking state based on swipe processing
     * Blocks bottom nav, back button, AND swipe gestures during library operations
     * CRITICAL: Also disables CardStackView to prevent multiple rapid swipes
     */
    private fun updateNavigationBlocking(isProcessing: Boolean) {
        isNavigationBlocked = isProcessing
        
        // Enable/disable back press callback (enabled = block back button)
        backPressCallback.isEnabled = isProcessing
        
        // CRITICAL: Disable/enable CardStackView to block swipe gestures
        // This prevents users from swiping 2-3 cards before category dialog appears
        binding.cardStackView.isEnabled = !isProcessing
        
        Logger.d { "🔒 [SWIPES] Navigation blocking: ${if (isProcessing) "ENABLED" else "DISABLED"}" }
        Logger.d { "🔒 [SWIPES] CardStackView enabled: ${!isProcessing}" }
    }
    
    override fun onStop() {
        super.onStop()
        presenter.savePendingSwipe()
    }
    
    override fun onStart() {
        super.onStart()
        presenter.restorePendingSwipe()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }
}
