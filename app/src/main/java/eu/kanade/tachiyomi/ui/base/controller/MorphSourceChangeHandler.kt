package eu.kanade.tachiyomi.ui.base.controller

import android.animation.Animator
import android.animation.AnimatorSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.bluelinelabs.conductor.changehandler.AnimatorChangeHandler

/**
 * Morph-in-place transition handler for Browse → BrowseSource navigation.
 * 
 * **OVERLAPPING TRANSITION TECHNIQUE:**
 * This handler creates a "perfect morph" effect by keeping BOTH views visible
 * during the transition. The technique is:
 * 
 * 1. BrowseController (source list) starts animating OUT (sources slide down)
 * 2. AT 50% MARK: Navigation triggers, BrowseSourceController pushes OVER TOP
 * 3. BrowseController STAYS VISIBLE underneath (doesn't disappear)
 * 4. BrowseSourceController animates IN (content fades/slides up)
 * 5. Result: You see sources exiting DOWN while content enters UP simultaneously
 * 
 * **Why This Works:**
 * - Instant (0ms) view swap, but "from" view stays in container via removesFromViewOnPush=false
 * - "To" view added on top with initial transparency
 * - Both controllers handle their own animations manually
 * - Creates layered, overlapping effect (like Instagram/Twitter transitions)
 * 
 * **Timing:**
 * - Source exit: 100ms delays, total ~500-800ms depending on count
 * - Navigation starts: At 50% mark (250-400ms)
 * - Content entry: Starts immediately when view added (0ms)
 * - Nav bar slide: 600ms (slower, more dramatic)
 * - Overlap: ~250-400ms where both animations visible
 * 
 * **CRITICAL PARAMETER: removesFromViewOnPush=false**
 * By default, AnimatorChangeHandler removes the "from" view on push, causing immediate detachment.
 * We pass FALSE to keep the "from" view in the container during the transition, allowing overlap.
 * 
 * **References:**
 * - Similar to Android's shared element transitions but manual
 * - Used by Reddit (Apollo), Twitter, Instagram for smooth morphs
 * - Based on translucent activity technique (Medium article by Aitor Viana)
 */
class MorphSourceChangeHandler(
    private val isReverse: Boolean = false
) : AnimatorChangeHandler(
    400L,  // 400ms duration - matches BrowseSourceController entry animation
    false  // CRITICAL: Keep "from" view in container for overlapping effect
) {

    /**
     * Creates an instant animator with special layering behavior.
     * 
     * **KEY:** The "to" view starts with its own initial state (set in onViewCreated).
     * We don't modify it here - BrowseSourceController handles its own entry animation.
     */
    override fun getAnimator(
        container: ViewGroup,
        from: View?,
        to: View?,
        isPush: Boolean,
        toAddedToContainer: Boolean
    ): Animator {
        android.util.Log.d("MorphChangeHandler", "═══════════════════════════════════════════════════════")
        android.util.Log.d("MorphChangeHandler", "🎬 [ANIMATOR] getAnimator() called - TRANSITION STARTING")
        android.util.Log.d("MorphChangeHandler", "═══════════════════════════════════════════════════════")
        android.util.Log.d("MorphChangeHandler", "   Direction: ${if (isPush) "PUSH (forward)" else "POP (back)"}")
        android.util.Log.d("MorphChangeHandler", "   isReverse: $isReverse")
        android.util.Log.d("MorphChangeHandler", "   removesFromViewOnPush: $removesFromViewOnPush (FALSE = view stays in container!)")
        android.util.Log.d("MorphChangeHandler", "")
        android.util.Log.d("MorphChangeHandler", "   FROM view (BrowseController):")
        android.util.Log.d("MorphChangeHandler", "      Type: ${from?.javaClass?.simpleName}")
        android.util.Log.d("MorphChangeHandler", "      State: alpha=${from?.alpha}, translationY=${from?.translationY}")
        android.util.Log.d("MorphChangeHandler", "      Will stay visible: ${!removesFromViewOnPush}")
        android.util.Log.d("MorphChangeHandler", "")
        android.util.Log.d("MorphChangeHandler", "   TO view (BrowseSourceController):")
        android.util.Log.d("MorphChangeHandler", "      Type: ${to?.javaClass?.simpleName}")
        android.util.Log.d("MorphChangeHandler", "      Added to container: $toAddedToContainer")
        android.util.Log.d("MorphChangeHandler", "      Initial state: alpha=${to?.alpha}, translationY=${to?.translationY}")
        android.util.Log.d("MorphChangeHandler", "")
        android.util.Log.d("MorphChangeHandler", "   Container:")
        android.util.Log.d("MorphChangeHandler", "      Child count: ${container.childCount}")
        android.util.Log.d("MorphChangeHandler", "      Children: ${(0 until container.childCount).map { container.getChildAt(it)?.javaClass?.simpleName }}")
        android.util.Log.d("MorphChangeHandler", "")
        android.util.Log.d("MorphChangeHandler", "   Animation strategy:")
        android.util.Log.d("MorphChangeHandler", "      Duration: $animationDuration ms (from parent constructor)")
        android.util.Log.d("MorphChangeHandler", "      We DON'T modify views - they handle their own animations")
        android.util.Log.d("MorphChangeHandler", "      Creating dummy 400ms animator to keep transition alive")
        android.util.Log.d("MorphChangeHandler", "")
        
        // Don't modify "to" view - it will handle its own entry animation
        // Don't modify "from" view - it stays visible during the overlap
        
        // Return animator with 400ms duration to match BrowseSourceController's manual animation
        // CRITICAL: AnimatorSet needs at least one child animator, otherwise it completes instantly!
        // We create a dummy no-op animator on the container to keep the transition alive for 400ms
        return AnimatorSet().apply {
            play(android.animation.ObjectAnimator.ofFloat(container, View.ALPHA, 1f, 1f).setDuration(400))
            android.util.Log.d("MorphChangeHandler", "   ⏱️ Created AnimatorSet with 400ms no-op animator on container")
            android.util.Log.d("MorphChangeHandler", "   This keeps Conductor's transition lifecycle active while views animate themselves")
            android.util.Log.d("MorphChangeHandler", "")
            
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    val timestamp = System.currentTimeMillis()
                    android.util.Log.d("MorphChangeHandler", "🎬 [ANIMATOR] onAnimationStart @ t=$timestamp")
                    android.util.Log.d("MorphChangeHandler", "   Container children: ${container.childCount}")
                    container.children.forEachIndexed { index, child ->
                        android.util.Log.d("MorphChangeHandler", "      Child $index: ${child.javaClass.simpleName} (alpha=${child.alpha}, visible=${child.visibility == View.VISIBLE})")
                    }
                    android.util.Log.d("MorphChangeHandler", "   Animation duration: ${animation.duration}ms (should be 400ms)")
                    android.util.Log.d("MorphChangeHandler", "   ✅ Both views should now be visible for crossfade!")
                }
                
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    val timestamp = System.currentTimeMillis()
                    android.util.Log.d("MorphChangeHandler", "� [ANIMATOR] onAnimationEnd @ t=$timestamp")
                    android.util.Log.d("MorphChangeHandler", "   Container children: ${container.childCount}")
                    container.children.forEachIndexed { index, child ->
                        android.util.Log.d("MorphChangeHandler", "      Child $index: ${child.javaClass.simpleName} (alpha=${child.alpha}, visible=${child.visibility == View.VISIBLE})")
                    }
                    android.util.Log.d("MorphChangeHandler", "   Transition complete - resetFromView() will be called next")
                    
                    if (removesFromViewOnPush && isPush) {
                        android.util.Log.d("MorphChangeHandler", "   ⚠️ WARNING: Would remove 'from' view now (but we set removesFromViewOnPush=false)")
                    } else {
                        android.util.Log.d("MorphChangeHandler", "   ✅ 'from' view remains in container (removesFromViewOnPush=false)")
                    }
                }
                
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    android.util.Log.d("MorphChangeHandler", "❌ [ANIMATOR] onAnimationCancel - Transition was interrupted!")
                }
                
                override fun onAnimationRepeat(animation: android.animation.Animator) {
                    // No-op
                }
            })
        }
    }

    override fun resetFromView(from: View) {
        android.util.Log.d("MorphChangeHandler", "🔄 [RESET] resetFromView() called")
        android.util.Log.d("MorphChangeHandler", "   isReverse: $isReverse")
        android.util.Log.d("MorphChangeHandler", "   from view: ${from.javaClass.simpleName}")
        android.util.Log.d("MorphChangeHandler", "   current state -> alpha: ${from.alpha}, translationY: ${from.translationY}")
        
        // ✅ CRITICAL FIX: NEVER reset the view state!
        // Conductor calls this method at various lifecycle points, and any modification
        // here will destroy the carefully orchestrated exit animations happening in BrowseController.
        //
        // The "from" view (BrowseController) should continue its exit animation uninterrupted
        // while the "to" view (BrowseSourceController) animates in on top.
        //
        // This is fundamentally different from FadeChangeHandler which animates the views
        // directly in getAnimator(). In our case, both views handle their own animations,
        // so resetFromView() should be a pure NO-OP.
        
        android.util.Log.d("MorphChangeHandler", "   🚫 NO-OP: Not modifying view state (manual animation approach)")
        android.util.Log.d("MorphChangeHandler", "   View remains in container: removesFromViewOnPush=$removesFromViewOnPush")
        android.util.Log.d("MorphChangeHandler", "   This allows the crossfade effect to work correctly")
        
        // DO ABSOLUTELY NOTHING - Let the views animate themselves!
    }

    override fun copy(): AnimatorChangeHandler {
        return MorphSourceChangeHandler(isReverse)
    }
}
