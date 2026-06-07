package eu.kanade.tachiyomi.data.coil

import android.graphics.drawable.BitmapDrawable
import coil3.key.Keyer
import coil3.request.Options

/**
 * Keyer for BitmapDrawable objects to enable Coil3 memory caching.
 * 
 * This keyer is needed when loading extension/source icons, which are BitmapDrawable
 * instances returned from PackageManager.getApplicationIcon(). Without this keyer,
 * Coil cannot cache these drawables in memory, causing repeated decoding on every scroll.
 * 
 * The cache key is based on the drawable's identity hashcode since these drawables
 * are loaded from the APK and remain stable for a given package/source.
 */
class BitmapDrawableKeyer : Keyer<BitmapDrawable> {
    override fun key(data: BitmapDrawable, options: Options): String {
        // Use the drawable's identity hash as the cache key
        // This ensures the same BitmapDrawable instance always gets the same key
        // Extension icons are stable - they don't change unless the extension is updated
        return "bitmap_drawable:${System.identityHashCode(data)}"
    }
}
