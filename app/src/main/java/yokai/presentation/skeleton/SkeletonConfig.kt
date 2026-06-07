package yokai.presentation.skeleton

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R

/**
 * Configuration for skeleton placeholder loading states.
 * 
 * Design decisions:
 * - 10% opacity: Very subtle appearance that doesn't dominate the screen
 * - Slow shimmer (2.5s): Prevents looking like a visual bug on fast loads
 * - Theme-aware: Different colors for light/dark modes
 */
data class SkeletonConfig(
    val baseColor: Int,
    val highlightColor: Int,
    val shimmerEnabled: Boolean = true,
    val animationDuration: Long = 2500, // 2.5 second cycle (slow shimmer)
    val cornerRadius: Float = 8f
) {
    companion object {
        /**
         * Create theme-aware skeleton configuration.
         */
        fun from(context: Context): SkeletonConfig {
            val isDarkMode = when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> true
                Configuration.UI_MODE_NIGHT_NO -> false
                else -> false // Default to light mode for undefined
            }
            
            return if (isDarkMode) {
                SkeletonConfig(
                    baseColor = 0x1AFFFFFF, // 10% white for dark mode (reduced from 15%)
                    highlightColor = 0x33FFFFFF, // 20% white for shimmer highlight
                    shimmerEnabled = true,
                    animationDuration = 2500,
                    cornerRadius = 8f
                )
            } else {
                SkeletonConfig(
                    baseColor = 0x1A000000, // 10% black for light mode (reduced from 15%)
                    highlightColor = 0x33000000, // 20% black for shimmer highlight
                    shimmerEnabled = true,
                    animationDuration = 2500,
                    cornerRadius = 8f
                )
            }
        }
        
        /**
         * Get the actual color with alpha applied.
         */
        fun applyAlpha(color: Int): Int {
            return color
        }
    }
}
