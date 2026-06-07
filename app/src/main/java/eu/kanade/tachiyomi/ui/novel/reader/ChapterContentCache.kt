package eu.kanade.tachiyomi.ui.novel.reader

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import yokai.domain.novel.NovelChapter

/**
 * Cache for pre-loaded chapter content (infinite scroll optimization).
 * Manages memory by limiting cached chapters using LRU eviction.
 * 
 * Thread-safe implementation using Mutex for coroutine synchronization.
 */
class ChapterContentCache(
    private val maxCachedChapters: Int = 3
) {
    private val cache = LinkedHashMap<Long, ChapterContent>(maxCachedChapters, 0.75f, true)
    private val cacheMutex = Mutex()
    
    /**
     * Get cached chapter content by chapter ID.
     * Returns null if not cached.
     */
    suspend fun get(chapterId: Long): ChapterContent? = cacheMutex.withLock {
        cache[chapterId]
    }
    
    /**
     * Cache chapter content with LRU eviction.
     * Automatically removes oldest chapter when cache limit exceeded.
     */
    suspend fun put(chapterId: Long, content: ChapterContent) = cacheMutex.withLock {
        cache[chapterId] = content
        
        // Enforce cache size limit (LRU eviction)
        while (cache.size > maxCachedChapters) {
            val oldestKey = cache.keys.first()
            cache.remove(oldestKey)
            android.util.Log.d("ChapterContentCache", "Evicted chapter $oldestKey (cache full)")
        }
    }
    
    /**
     * Clear all cached content.
     * Called when switching novels or reading modes.
     */
    suspend fun clear() = cacheMutex.withLock {
        cache.clear()
        android.util.Log.d("ChapterContentCache", "Cache cleared")
    }
    
    /**
     * Get cache statistics for debugging.
     */
    suspend fun getStats(): CacheStats = cacheMutex.withLock {
        CacheStats(
            size = cache.size,
            maxSize = maxCachedChapters,
            cachedChapterIds = cache.keys.toList()
        )
    }
    
    /**
     * Cached chapter content with RAW HTML (preserves <p> tags for re-parsing).
     * CRITICAL: Must store original HTML, not parsed text, to prevent empty content bug.
     */
    data class ChapterContent(
        val chapter: NovelChapter,
        val rawHtml: String,  // Changed from paragraphs: List<String> to preserve HTML structure
        val totalCharacters: Int
    )
    
    /**
     * Cache statistics for debugging and monitoring.
     */
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val cachedChapterIds: List<Long>
    )
}
