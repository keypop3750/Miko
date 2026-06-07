package yokai.presentation.skeleton

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import eu.kanade.tachiyomi.databinding.SkeletonMangaListBinding

/**
 * Basic skeleton placeholder for novel items in list mode.
 * Matches EXACT layout of manga_list_item.xml with 10% opacity solid blocks.
 */
class SkeletonTextItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    private val binding: SkeletonMangaListBinding
    
    init {
        binding = SkeletonMangaListBinding.inflate(LayoutInflater.from(context), this, true)
        // Layout already has alpha=0.1 set on all skeleton views
    }
}
