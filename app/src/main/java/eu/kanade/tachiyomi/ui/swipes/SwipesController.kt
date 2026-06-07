package eu.kanade.tachiyomi.ui.swipes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DefaultItemAnimator
import co.touchlab.kermit.Logger
import com.yuyakaido.android.cardstackview.CardStackLayoutManager
import com.yuyakaido.android.cardstackview.CardStackListener
import com.yuyakaido.android.cardstackview.Direction
import com.yuyakaido.android.cardstackview.StackFrom
import com.yuyakaido.android.cardstackview.SwipeableMethod
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.SwipesControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.BaseLegacyController
import eu.kanade.tachiyomi.ui.main.BottomSheetController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.RootSearchInterface
import eu.kanade.tachiyomi.util.view.activityBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * SwipesController - Tinder-style manga discovery interface
 * 
 * Controller-based architecture that integrates seamlessly with MainActivity:
 * - Uses Conductor framework like Library/Recents/Browse tabs
 * - Shares MainActivity's bottom navigation and app bar
 * - Proper back stack handling
 * 
 * This replaces the Activity-based SwipesActivity for proper navigation integration.
 */
class SwipesController(bundle: Bundle? = null) :
    BaseLegacyController<SwipesControllerBinding>(bundle),
    CardStackListener,
    RootSearchInterface,
    FloatingSearchInterface,
    BottomSheetController {

    init {
        setHasOptionsMenu(true)
    }

    private lateinit var cardStackLayoutManager: CardStackLayoutManager
    private lateinit var cardAdapter: SwipeCardAdapter
    
    // Grace period flag to prevent interaction during swipe animations
    private var isSwipeInProgress = false
    
    // Coroutine scope for the controller
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val presenter: SwipesPresenter by lazy {
        SwipesPresenter(
            sourceManager = Injekt.get(),
            context = applicationContext!!
        )
    }

    override fun createBinding(inflater: LayoutInflater): SwipesControllerBinding =
        SwipesControllerBinding.inflate(inflater)

    override fun getTitle(): String = view?.context?.getString(R.string.swipes) ?: "Swipes"

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        
        setupCardStackView()
        observePresenter()
        
        // Initialize presenter and load data
        presenter.onCreate()
    }

    /**
     * Setup CardStackView with layout manager and adapter
     * Configured for Tinder-style swiping behavior
     */
    private fun setupCardStackView() {
        val context = view?.context ?: return
        
        // Initialize layout manager with swipe settings
        cardStackLayoutManager = CardStackLayoutManager(context, this).apply {
            setStackFrom(StackFrom.Top)
            setVisibleCount(3)  // Show 3 cards in stack
            setTranslationInterval(8.0f)  // Spacing between cards
            setScaleInterval(0.95f)  // Scale difference between cards
            setMaxDegree(20.0f)  // Max rotation during swipe
            setDirections(Direction.HORIZONTAL)  // Left/right swipes only
            setCanScrollHorizontal(true)
            setCanScrollVertical(false)
            setSwipeableMethod(SwipeableMethod.AutomaticAndManual)  // Both drag and programmatic swipes
            setSwipeThreshold(0.4f)  // Increase threshold - user must swipe 40% of card width
        }

        // Initialize adapter
        cardAdapter = SwipeCardAdapter()

        // Setup CardStackView
        binding.cardStackView.apply {
            layoutManager = cardStackLayoutManager
            adapter = cardAdapter
            itemAnimator = DefaultItemAnimator()
        }
    }

    /**
     * Observe presenter state changes and update UI accordingly
     */
    private fun observePresenter() {
        // Observe card list updates
        controllerScope.launch {
            presenter.cards.collectLatest { cards ->
                Logger.d { "SWIPES: Cards received in observer: size=${cards.size}" }
                cardAdapter.setItems(cards)
                Logger.d { "SWIPES: After setItems, adapter.itemCount=${cardAdapter.itemCount}" }
                updateEmptyState()
            }
        }
        
        // Observe processing state
        controllerScope.launch {
            presenter.isProcessingSwipe.collectLatest { isProcessing ->
                Logger.d { "🔒 [SWIPES] Processing state changed: $isProcessing" }
                binding.cardStackView.isEnabled = !isProcessing
            }
        }
        
        // Observe UI state changes (loading, error, etc.)
        controllerScope.launch {
            presenter.uiState.collectLatest { state ->
                Logger.d { "SWIPES: UI State changed to: $state" }
                when (state) {
                    is SwipesUiState.Loading -> {
                        if (cardAdapter.itemCount == 0) {
                            binding.loadingState.visibility = View.VISIBLE
                            binding.emptyState.visibility = View.GONE
                            binding.cardStackView.visibility = View.GONE
                        }
                    }
                    is SwipesUiState.Success -> {
                        binding.loadingState.visibility = View.GONE
                        binding.cardStackView.visibility = View.VISIBLE
                        binding.emptyState.visibility = View.GONE
                    }
                    is SwipesUiState.Error -> {
                        binding.loadingState.visibility = View.GONE
                        binding.emptyStateTitle.text = "Error loading recommendations"
                        binding.emptyStateSubtitle.text = state.message
                        updateEmptyState()
                    }
                    is SwipesUiState.NoSources -> {
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
        
        if (isEmpty) {
            binding.cardStackView.visibility = View.GONE
            binding.emptyState.apply {
                visibility = View.VISIBLE
                animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }
        } else {
            binding.emptyState.apply {
                animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction { visibility = View.GONE }
                    .start()
            }
            binding.cardStackView.visibility = View.VISIBLE
        }
    }

    // ==================== Menu ====================

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.swipes_menu, menu)
        
        // Setup SearchView
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        
        searchView?.apply {
            queryHint = "Search manga..."
            maxWidth = Integer.MAX_VALUE
            
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        Logger.d { "🔍 [SEARCH] Query submitted: $it" }
                        presenter.searchManga(it)
                        clearFocus()
                    }
                    return true
                }
                
                override fun onQueryTextChange(newText: String?): Boolean = false
            })
        }
        
        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true
            
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                presenter.clearSearch()
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter -> {
                showFilterDialog()
                true
            }
            R.id.action_history -> {
                showHistoryDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Show filter dialog to configure recommendations
     */
    private fun showFilterDialog() {
        val activity = activity ?: return
        val currentFilters = presenter.getCurrentFilters()
        
        TabbedSwipesFilterSheet(
            activity = activity,
            currentFilters = currentFilters,
            onFiltersApplied = { newFilters ->
                presenter.applyFilters(newFilters)
            }
        ).show()
    }
    
    /**
     * Show history dialog displaying last 50 swipe decisions
     */
    private fun showHistoryDialog() {
        val act = (activity as? AppCompatActivity) ?: return
        SwipesHistorySheet(
            presenter = presenter
        ).show(act.supportFragmentManager, "swipes_history")
    }

    // ==================== CardStackListener Implementation ====================

    override fun onCardDragging(direction: Direction, ratio: Float) {
        if (presenter.isProcessingSwipe.value) {
            Logger.d { "🔒 [SWIPES] Blocking swipe gesture - processing in progress" }
            return
        }
        
        val topPosition = cardStackLayoutManager.topPosition
        if (topPosition >= 0 && topPosition < cardAdapter.itemCount) {
            val cardView = binding.cardStackView.findViewHolderForAdapterPosition(topPosition)?.itemView
            cardView?.let { view ->
                val leftOverlay = view.findViewById<View>(R.id.left_overlay)
                val rightOverlay = view.findViewById<View>(R.id.right_overlay)
                
                when (direction) {
                    Direction.Left -> {
                        leftOverlay?.apply {
                            visibility = View.VISIBLE
                            alpha = ratio
                        }
                        rightOverlay?.visibility = View.GONE
                    }
                    Direction.Right -> {
                        rightOverlay?.apply {
                            visibility = View.VISIBLE
                            alpha = ratio
                        }
                        leftOverlay?.visibility = View.GONE
                    }
                    else -> {
                        leftOverlay?.visibility = View.GONE
                        rightOverlay?.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onCardSwiped(direction: Direction) {
        val item = cardAdapter.getItemAt(0)
        val currentPosition = cardStackLayoutManager.topPosition

        isSwipeInProgress = true
        binding.cardStackView.isEnabled = false

        when (direction) {
            Direction.Left -> {
                item?.let {
                    Logger.d { "📱 [SWIPES] Card swiped LEFT: ${it.title}" }
                    presenter.onCardSwipedLeft(it)
                }
            }
            Direction.Right -> {
                item?.let {
                    activity?.let { act ->
                        presenter.onCardSwipedRight(act, it)
                    }
                }
            }
            else -> {}
        }

        val topPosition = cardStackLayoutManager.topPosition - 1
        if (topPosition >= 0) {
            presenter.removeCard(topPosition)
        }
        
        presenter.prefetchUpcomingCards(currentPosition)
        
        binding.cardStackView.postDelayed({
            isSwipeInProgress = false
            binding.cardStackView.isEnabled = true
        }, 500)
    }

    override fun onCardRewound() {
        updateEmptyState()
    }

    override fun onCardCanceled() {
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
        val remaining = cardAdapter.itemCount - position
        if (remaining <= 5) {
            presenter.loadMoreRecommendations()
        }
    }
    
    override fun onCardDisappeared(view: View, position: Int) {
        // No action needed
    }

    // ==================== BottomSheetController ====================

    override fun showSheet() {
        // Can be used for a filter bottom sheet
    }

    override fun hideSheet() {
        // Hide any open bottom sheet
    }

    override fun toggleSheet() {
        // Toggle filter sheet visibility
    }

    // ==================== Lifecycle ====================

    override fun onDestroyView(view: View) {
        presenter.savePendingSwipe()
        controllerScope.cancel()
        presenter.onDestroy()
        super.onDestroy()
    }
}
