package eu.kanade.tachiyomi.ui.swipes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import co.touchlab.kermit.Logger
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import eu.kanade.tachiyomi.databinding.SwipesHistorySheetBinding
import eu.kanade.tachiyomi.ui.swipes.history.SwipesHistoryAdapter
import eu.kanade.tachiyomi.ui.swipes.history.SwipesHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet dialog displaying swipe history (last 50 items)
 * Simple view-only history for reviewing swipe decisions
 * 
 * Phase 4.2: History Feature
 */
class SwipesHistorySheet(
    private val presenter: SwipesPresenter
) : BottomSheetDialogFragment() {

    private var _binding: SwipesHistorySheetBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: SwipesHistoryAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SwipesHistorySheetBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Logger.d { "📜 [HISTORY] onViewCreated called" }
        
        try {
            setupToolbar()
            Logger.d { "📜 [HISTORY] Toolbar setup complete" }
            
            setupRecyclerView()
            Logger.d { "📜 [HISTORY] RecyclerView setup complete" }
            
            loadHistory()
            Logger.d { "📜 [HISTORY] loadHistory() called" }
            
            setupClearButton()
            Logger.d { "📜 [HISTORY] Clear button setup complete" }
        } catch (e: Exception) {
            Logger.e(e) { "📜 [HISTORY] Error in onViewCreated: ${e.message}" }
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            dismiss()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = SwipesHistoryAdapter(
            onItemClick = { item ->
                // TODO Phase 5: Navigate to manga details
                Logger.d { "📜 [HISTORY] Item clicked: ${item.title}" }
            }
        )
        
        binding.historyRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SwipesHistorySheet.adapter
            setHasFixedSize(true)
        }
    }
    
    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                Logger.d { "📜 [HISTORY] Loading swipe history..." }
                val historyItems = withContext(Dispatchers.IO) {
                    presenter.getSwipeHistory()
                }
                
                Logger.d { "📜 [HISTORY] Loaded ${historyItems.size} history items" }
                if (historyItems.isEmpty()) {
                    showEmptyState()
                } else {
                    showHistoryList(historyItems)
                }
            } catch (e: Exception) {
                Logger.e(e) { "📜 [HISTORY] Failed to load swipe history" }
                showEmptyState()
            }
        }
    }
    
    private fun showEmptyState() {
        Logger.d { "📜 [HISTORY] Showing empty state" }
        binding.historyRecycler.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.clearHistoryButton.isEnabled = false
    }
    
    private fun showHistoryList(items: List<SwipesHistoryItem>) {
        Logger.d { "📜 [HISTORY] Showing ${items.size} items in list" }
        binding.historyRecycler.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.clearHistoryButton.isEnabled = true
        adapter.submitList(items)
    }
    
    private fun setupClearButton() {
        binding.clearHistoryButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        presenter.clearSwipeHistory()
                    }
                    
                    showEmptyState()
                    Logger.d { "📜 [HISTORY] History cleared" }
                } catch (e: Exception) {
                    Logger.e(e) { "📜 [HISTORY] Failed to clear history" }
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
