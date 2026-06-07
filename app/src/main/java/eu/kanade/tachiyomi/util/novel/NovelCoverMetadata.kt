package eu.kanade.tachiyomi.util.novel

import android.graphics.BitmapFactory
import androidx.annotation.ColorInt
import androidx.palette.graphics.Palette
import coil3.Bitmap
import eu.kanade.tachiyomi.data.coil.getBestColor
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import uy.kohesive.injekt.injectLazy

/**
 * Object that holds info about novel covers: dominant colors extracted from cover images.
 * Similar to MangaCoverMetadata but for novels.
 * 
 * Purpose: Pre-extract cover colors during library browsing so that when user opens
 * NovelDetailsController, the theme color is already known and can be applied immediately,
 * eliminating the flash of default app theme.
 */
object NovelCoverMetadata {
    private var vibrantCoverColorMap = ConcurrentHashMap<Long, Int>()
    private val preferences by injectLazy<PreferencesHelper>()

    /**
     * Load persisted novel cover colors from preferences.
     */
    fun load() {
        val colors = preferences.novelCoverColors().get()
        vibrantCoverColorMap = ConcurrentHashMap(
            colors.mapNotNull {
                val splits = it.split("|")
                val id = splits.firstOrNull()?.toLongOrNull()
                val color = splits.getOrNull(1)?.toIntOrNull()
                if (id != null && color != null) {
                    id to color
                } else {
                    null
                }
            }.toMap(),
        )
    }

    /**
     * Extract and cache vibrant color from novel cover image.
     * Called when novel covers are loaded in library/browse views.
     * 
     * @param novelId The novel's database ID
     * @param bitmap The loaded cover bitmap
     */
    fun extractAndCacheColor(novelId: Long?, bitmap: Bitmap?) {
        novelId ?: return
        bitmap ?: return
        
        // Don't re-extract if already cached
        if (vibrantCoverColorMap.containsKey(novelId)) return

        try {
            Palette.from(bitmap).generate { palette ->
                val color = palette?.getBestColor() ?: return@generate
                setVibrantColor(novelId, color)
            }
        } catch (e: Exception) {
            // Ignore palette extraction errors
        }
    }

    /**
     * Extract and cache vibrant color from novel cover file.
     * Used for downloaded/cached covers.
     */
    fun extractAndCacheColorFromFile(novelId: Long?, coverFile: File?) {
        novelId ?: return
        coverFile ?: return
        if (!coverFile.exists()) return

        // Don't re-extract if already cached
        if (vibrantCoverColorMap.containsKey(novelId)) return

        try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 4 // Downsample for performance
            }
            val bitmap = BitmapFactory.decodeFile(coverFile.path, options) ?: return
            
            Palette.from(bitmap).generate { palette ->
                val color = palette?.getBestColor() ?: return@generate
                setVibrantColor(novelId, color)
            }
        } catch (e: Exception) {
            // Ignore palette extraction errors
        }
    }

    /**
     * Set the vibrant color for a novel.
     */
    fun setVibrantColor(novelId: Long?, @ColorInt color: Int?) {
        novelId ?: return

        if (color == null) {
            vibrantCoverColorMap.remove(novelId)
            return
        }

        vibrantCoverColorMap[novelId] = color
    }

    /**
     * Get the cached vibrant color for a novel.
     * Returns null if not yet extracted.
     */
    fun getVibrantColor(novelId: Long?): Int? {
        return vibrantCoverColorMap[novelId]
    }

    /**
     * Remove cached color for a novel.
     * Called when novel is removed from library.
     */
    fun remove(novelId: Long?) {
        novelId ?: return
        vibrantCoverColorMap.remove(novelId)
    }

    /**
     * Save cached colors to preferences for persistence.
     */
    fun savePrefs() {
        val mapCopy = vibrantCoverColorMap.toMap()
        preferences.novelCoverColors().set(
            mapCopy.map { "${it.key}|${it.value}" }.toSet()
        )
    }
}
