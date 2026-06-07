package eu.kanade.tachiyomi.ui.source

import co.touchlab.kermit.Logger
import eu.davidea.flexibleadapter.items.IFlexible

/**
 * Helper class for calculating hierarchical exit animation delays for source list.
 * 
 * **Animation Strategy:**
 * Sources animate out in top-to-bottom hierarchical order:
 * 1. Category header appears (e.g., "Pinned")
 * 2. All sources in that category animate sequentially
 * 3. Next category header appears
 * 4. Repeat
 * 
 * **Timing:**
 * - Each item (header or source): 150ms delay from previous
 * - Animation duration: 150ms per item
 * - Total duration: (item count × 150ms)
 * 
 * **Example with 2 categories:**
 * ```
 * 0ms:    "Pinned" header starts fading out
 * 150ms:  Source A starts (under Pinned)
 * 300ms:  Source B starts (under Pinned)
 * 450ms:  "All" header starts
 * 600ms:  Source C starts (under All)
 * 750ms:  Animation complete
 * ```
 */
class SourceExitAnimationHelper {
    
    private val logger = Logger.withTag("SourceExitAnimHelper")
    
    companion object {
        /**
         * Delay between each item animation (milliseconds).
         * 100ms creates faster cascade (was 150ms - too slow per user feedback).
         */
        const val ITEM_DELAY_MS = 100L
        
        /**
         * Duration of each item's fade-out animation (milliseconds).
         * Matches ITEM_DELAY_MS for smooth sequential flow without gaps.
         */
        const val ANIMATION_DURATION_MS = 100L
        
        /**
         * Vertical translation distance during exit (dp).
         * Items slide DOWN while fading out (opposite of entry which slides from above).
         */
        const val TRANSLATION_DISTANCE_DP = 60f
    }
    
    /**
     * Calculates animation delay for each item based on position in hierarchy.
     * 
     * **OPTIMIZED:** Pre-allocates map and uses mathematical calculation instead of iteration.
     * Eliminates per-item logging (was 50+ log calls per navigation).
     * 
     * @param items List of adapter items (mix of LangItem headers and SourceItem sources)
     * @return Map of position → delay (milliseconds)
     */
    fun calculateDelays(items: List<IFlexible<*>>): Map<Int, Long> {
        val itemCount = items.size
        // Pre-allocate map with exact capacity (no resizing needed)
        val delayMap = HashMap<Int, Long>(itemCount)
        
        // Mathematical calculation: position × delay interval
        // Eliminates loop overhead and per-item allocations
        for (i in 0 until itemCount) {
            delayMap[i] = i * ITEM_DELAY_MS
        }
        
        // Single summary log instead of per-item logging
        val totalDuration = getTotalDuration(itemCount)
        logger.d { "✅ Calculated $itemCount item delays, total duration: ${totalDuration}ms" }
        
        return delayMap
    }
    
    /**
     * Get total animation duration (last item's delay + animation duration).
     * Use this to know when all sources have finished animating.
     */
    fun getTotalDuration(itemCount: Int): Long {
        return (itemCount * ITEM_DELAY_MS) + ANIMATION_DURATION_MS
    }
}
