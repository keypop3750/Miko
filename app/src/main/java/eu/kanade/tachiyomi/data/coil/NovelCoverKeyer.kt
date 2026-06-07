package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import yokai.domain.novel.Novel

/**
 * Keyer for Novel objects to enable Coil3 image loading and caching.
 * Extracts the poster URL and last modified timestamp for cache key generation.
 */
class NovelKeyer : Keyer<Novel> {
    override fun key(data: Novel, options: Options): String {
        // Use poster URL as primary key, with last modified timestamp for cache busting
        return "${data.posterUrl};${data.coverLastModified}"
    }
}
