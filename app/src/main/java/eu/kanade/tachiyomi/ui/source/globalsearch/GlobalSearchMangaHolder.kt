package eu.kanade.tachiyomi.ui.source.globalsearch

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.isVisible
import coil3.dispose
import eu.kanade.tachiyomi.databinding.SourceGlobalSearchControllerCardItemBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.makeShapeCorners
import eu.kanade.tachiyomi.util.view.setCards
import yokai.domain.manga.models.cover
import yokai.util.coil.loadManga

/**
 * ViewHolder for manga items in global search with domino animation support.
 */
class GlobalSearchMangaHolder(view: View, adapter: GlobalSearchCardAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    private val binding = SourceGlobalSearchControllerCardItemBinding.bind(view)
    
    // Track last loaded thumbnail to avoid redundant image loads
    private var lastThumbnailUrl: String? = null
    private var hasPlayedAnimation = false
    
    init {
        itemView.setOnClickListener {
            val item = adapter.getItem(flexibleAdapterPosition)
            if (item != null) {
                adapter.mangaClickListener.onMangaClick(item.manga)
            }
        }
        val bottom = 2.dpToPx
        val others = 5.dpToPx
        (binding.constraintLayout.foreground as? RippleDrawable)?.apply {
            setLayerSize(1, 0, 0)
            for (i in 0 until numberOfLayers) {
                setLayerInset(i, others, others, others, bottom)
            }
        }
        binding.favoriteButton.shapeAppearanceModel =
            binding.card.makeShapeCorners(binding.card.radius, binding.card.radius)
        itemView.setOnLongClickListener {
            adapter.mangaClickListener.onMangaLongClick(flexibleAdapterPosition, adapter)
            true
        }
        setCards(adapter.showOutlines, binding.card, binding.favoriteButton)
    }

    fun bind(manga: Manga) {
        binding.title.text = manga.title
        binding.favoriteButton.isVisible = manga.favorite
        setImage(manga)
        
        // Play domino animation only once per view holder reuse
        if (!hasPlayedAnimation) {
            playDominoAnimation()
            hasPlayedAnimation = true
        }
    }
    
    /**
     * Reset animation state when view is recycled.
     */
    fun resetAnimationState() {
        hasPlayedAnimation = false
        lastThumbnailUrl = null
    }
    
    /**
     * Play a domino-style entrance animation.
     * Each item starts slightly after the previous one (handled by adapter position).
     */
    private fun playDominoAnimation() {
        val position = flexibleAdapterPosition
        if (position < 0) return
        
        // Start invisible and slightly scaled down
        itemView.alpha = 0f
        itemView.scaleX = 0.92f
        itemView.scaleY = 0.92f
        itemView.translationY = -20f
        
        // Stagger delay based on position (35ms per item)
        val delay = position * 35L
        
        // Fade in animation
        val fadeIn = ObjectAnimator.ofFloat(itemView, View.ALPHA, 0f, 1f).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
        }
        
        // Scale animation with overshoot
        val scaleX = ObjectAnimator.ofFloat(itemView, View.SCALE_X, 0.92f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(0.5f)
        }
        val scaleY = ObjectAnimator.ofFloat(itemView, View.SCALE_Y, 0.92f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(0.5f)
        }
        
        // Slide in from above
        val slideIn = ObjectAnimator.ofFloat(itemView, View.TRANSLATION_Y, -20f, 0f).apply {
            duration = 450
            interpolator = OvershootInterpolator(0.5f)
        }
        
        AnimatorSet().apply {
            playTogether(fadeIn, scaleX, scaleY, slideIn)
            startDelay = delay
            start()
        }
    }

    fun setImage(manga: Manga) {
        val thumbnailUrl = manga.thumbnail_url
        
        // Skip if thumbnail URL hasn't changed (avoid redundant loads)
        if (thumbnailUrl == lastThumbnailUrl) return
        lastThumbnailUrl = thumbnailUrl
        
        binding.itemImage.dispose()
        if (!thumbnailUrl.isNullOrEmpty()) {
            binding.itemImage.loadManga(manga.cover())
        }
    }
}
