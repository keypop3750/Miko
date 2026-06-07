package eu.kanade.tachiyomi.data.coil

import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import coil3.Image
import coil3.asDrawable
import coil3.target.ImageViewTarget
import eu.kanade.tachiyomi.util.novel.NovelCoverMetadata
import yokai.domain.novel.Novel

/**
 * Coil target for novel covers displayed in library/browse views.
 * Automatically extracts vibrant color from loaded images for instant theming.
 * 
 * Similar to LibraryMangaImageTarget but for novels.
 */
class LibraryNovelImageTarget(
    override val view: ImageView,
    private val novel: Novel,
) : ImageViewTarget(view) {

    override fun onSuccess(result: Image) {
        super.onSuccess(result)
        
        // Extract color from bitmap for instant theme application in details view
        try {
            val bitmap = (result.asDrawable(view.context.resources) as? BitmapDrawable)?.bitmap
                ?: result.asDrawable(view.context.resources)?.toBitmap()
            
            NovelCoverMetadata.extractAndCacheColor(novel.id, bitmap)
        } catch (e: Exception) {
            // Ignore errors during color extraction
        }
    }

    override fun onError(error: Image?) {
        super.onError(error)
        // Remove cached color if image fails to load
        NovelCoverMetadata.remove(novel.id)
    }
}
