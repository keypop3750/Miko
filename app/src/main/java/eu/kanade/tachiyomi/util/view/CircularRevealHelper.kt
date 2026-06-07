package eu.kanade.tachiyomi.util.view

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import eu.kanade.tachiyomi.ui.library.LibraryGridHolder
import eu.kanade.tachiyomi.ui.library.LibraryHolder
import eu.kanade.tachiyomi.ui.library.LibraryListHolder
import kotlin.math.hypot
import kotlin.math.max

/**
 * Utility functions for circular reveal shared element transitions.
 * Provides coordinate calculations, radius computation, and view data extraction
 * for library → manga details circular reveal animation.
 */
object CircularRevealHelper {
    
    /**
     * Calculates the ending radius for circular reveal to cover the entire view.
     * Uses the diagonal distance from reveal center to the farthest corner.
     * 
     * **Algorithm**:
     * 1. Find distance from center to each corner
     * 2. Take maximum distance (farthest corner)
     * 3. This ensures reveal completely covers the view
     * 
     * @param view The view to be revealed
     * @param centerX Reveal center X coordinate (click position)
     * @param centerY Reveal center Y coordinate (click position)
     * @return Radius that will cover entire view from center point
     */
    fun calculateEndRadius(
        view: View,
        centerX: Int,
        centerY: Int
    ): Float {
        // Distance from center to right/left edge
        val maxX = max(centerX, view.width - centerX)
        
        // Distance from center to bottom/top edge
        val maxY = max(centerY, view.height - centerY)
        
        // Pythagorean theorem: diagonal distance
        return hypot(maxX.toDouble(), maxY.toDouble()).toFloat()
    }
    
    /**
     * Converts local view coordinates to screen (window) coordinates.
     * Required because circular reveal operates in screen coordinates,
     * but touch events provide view-local coordinates.
     * 
     * @param view The view that received the touch
     * @param localX X coordinate within the view
     * @param localY Y coordinate within the view
     * @return Pair of (screenX, screenY) in window coordinates
     */
    fun convertToScreenCoordinates(
        view: View,
        localX: Float,
        localY: Float
    ): Pair<Int, Int> {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        
        return Pair(
            (location[0] + localX).toInt(),
            (location[1] + localY).toInt()
        )
    }
    
    /**
     * Extracts the thumbnail drawable from a library holder.
     * Works with both grid and list layouts.
     * 
     * **Note**: May return null if:
     * - Image hasn't loaded yet
     * - Holder type is unknown
     * - View is being recycled
     * 
     * @param holder Library item holder (grid or list)
     * @return Drawable from the cover thumbnail, or null if unavailable
     */
    fun extractThumbnailDrawable(holder: LibraryHolder): Drawable? {
        // Find the ImageView by ID instead of accessing private binding
        val thumbnailView = holder.itemView.findViewById<android.widget.ImageView>(
            eu.kanade.tachiyomi.R.id.cover_thumbnail
        )
        return thumbnailView?.drawable
    }
    
    /**
     * Extracts the thumbnail view bounds in screen coordinates.
     * Used for positioning the morphing thumbnail overlay during transition.
     * 
     * @param holder Library item holder (grid or list)
     * @return Rectangle defining thumbnail position, or null if unavailable
     */
    fun extractThumbnailBounds(holder: LibraryHolder): Rect? {
        // Find the ImageView by ID instead of accessing private binding
        val thumbnailView = holder.itemView.findViewById<android.widget.ImageView>(
            eu.kanade.tachiyomi.R.id.cover_thumbnail
        ) ?: return null
        
        val location = IntArray(2)
        thumbnailView.getLocationInWindow(location)
        
        return Rect(
            location[0],
            location[1],
            location[0] + thumbnailView.width,
            location[1] + thumbnailView.height
        )
    }
}
