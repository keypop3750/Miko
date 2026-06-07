package eu.kanade.tachiyomi.data.coil

import android.view.View
import android.widget.ImageView
import androidx.core.view.isVisible
import coil3.Image
import coil3.target.ImageViewTarget
import eu.kanade.tachiyomi.R

/**
 * Custom Coil target for novel covers that handles error/missing image states
 * by showing a placeholder drawable instead of leaving the ImageView empty.
 * This prevents layout breakage when cover URLs are null or fail to load.
 */
class NovelCoverViewTarget(
    view: ImageView,
    val progress: View? = null,
    val scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP,
) : ImageViewTarget(view) {

    override fun onError(error: Image?) {
        progress?.isVisible = false
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        // Use the default novel cover placeholder which matches the app's visual style
        view.setImageResource(R.drawable.default_novel_cover)
    }

    override fun onStart(placeholder: Image?) {
        progress?.isVisible = true
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        // Show the default novel cover placeholder while loading
        // This provides a consistent, non-jarring placeholder experience
        view.setImageResource(R.drawable.default_novel_cover)
    }

    override fun onSuccess(result: Image) {
        progress?.isVisible = false
        view.scaleType = scaleType
        super.onSuccess(result)
    }
}
