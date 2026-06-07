package eu.kanade.tachiyomi.ui.library

import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.view.activityBinding
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

class LibraryCategoryGestureDetector(private val controller: LibraryController?) : GestureDetector
.SimpleOnGestureListener() {
    var locked = false
    var cancelled = false
    private val poa = 1.7f
    private var startingX = 0f
    private var startingY = 0f

    override fun onDown(e: MotionEvent): Boolean {
        locked = false
        startingX = e.x
        startingY = e.y
        controller ?: return false
        val startingOnLibraryView = listOf(
            controller.activityBinding?.bottomNav,
            controller.binding.filterBottomSheet.root,
            controller.binding.categoryHopperFrame,
            controller.activityBinding?.appBar,
            controller.visibleHeaderHolder()?.itemView,
        ).none {
            it ?: return false
            val viewRect = Rect()
            it.getGlobalVisibleRect(viewRect)
            viewRect.contains(e.x.toInt(), e.y.toInt())
        }
        cancelled = !startingOnLibraryView
        return startingOnLibraryView
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        val firstEvent = e1 ?: return false
        val controller = controller ?: return false
        val distance = startingX - e2.x
        val totalDistanceY = startingY - e2.y
        controller.binding.libraryGridRecycler.recycler.translationX =
            if (!cancelled) abs(distance / 50).pow(poa) * -sign(distance / 50) else 0f
        if (!locked && abs(distance) > 35 && !cancelled) {  // Reduced from 50 for earlier lock
            val ev2 = MotionEvent.obtain(firstEvent)
            ev2.action = MotionEvent.ACTION_CANCEL
            controller.binding.swipeRefresh.dispatchTouchEvent(ev2)
            ev2.recycle()
            locked = true
        } else if (abs(totalDistanceY) > 60 && !locked) {  // Increased from 50 to be less sensitive to vertical
            cancelled = true
            return false
        }
        return super.onScroll(firstEvent, e2, distanceX, distanceY)
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        locked = false
        if (cancelled) {
            cancelled = false
            return false
        }
        cancelled = false
        val controller = controller ?: return false
        var result = false
        val diffY = e2.y - startingY
        val diffX = e2.x - startingX
        val recycler = controller.binding.libraryGridRecycler.recycler
        var moved = false
        val isSwipingLeft = diffX < 0  // Swiping left means going to next category
        val isLTR = controller.binding.root.resources.isLTR
        
        if (abs(diffX) >= abs(diffY) &&
            abs(diffX) > SWIPE_THRESHOLD * 3 &&
            abs(velocityX) > SWIPE_VELOCITY_THRESHOLD &&
            sign(diffX) == sign(velocityX)
        ) {
            val goingNext = (diffX >= 0).xor(isLTR)
            
            // Animate slide-out in the direction of swipe
            val slideOutDistance = recycler.width.toFloat() * if (isSwipingLeft) -1f else 1f
            val slideInDistance = recycler.width.toFloat() * if (isSwipingLeft) 1f else -1f
            
            // Slide out animation
            recycler.animate()
                .translationX(slideOutDistance * 0.3f)
                .alpha(0.3f)
                .setDuration(SWIPE_ANIMATION_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    // Actually move to next category
                    moved = controller.jumpToNextCategory(goingNext)
                    
                    if (moved) {
                        // Position for slide-in from opposite side
                        recycler.translationX = slideInDistance * 0.3f
                        recycler.alpha = 0.3f
                        
                        // Slide in animation
                        recycler.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(SWIPE_ANIMATION_DURATION)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    } else {
                        // If couldn't move (at boundary), bounce back
                        recycler.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(SWIPE_ANIMATION_DURATION)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                }
                .start()
            
            result = true
        }
        
        if (!result) {
            // Cancelled or didn't meet threshold - animate back to original position
            val animator = recycler.animate().setDuration(150L)
            animator.translationX(0f)
            animator.alpha(1f)
            animator.withEndAction { 
                recycler.translationX = 0f 
                recycler.alpha = 1f
            }
            animator.start()
        }
        return result
    }

    private companion object {
        const val SWIPE_THRESHOLD = 35  // Reduced from 50 for more responsive swiping
        const val SWIPE_VELOCITY_THRESHOLD = 80  // Reduced from 100 for easier triggering
        const val SWIPE_ANIMATION_DURATION = 180L
    }
}
