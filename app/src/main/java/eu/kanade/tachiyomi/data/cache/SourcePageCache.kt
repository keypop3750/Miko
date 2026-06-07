package eu.kanade.tachiyomi.data.cache

import android.content.Context
import android.util.LruCache
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Cache for browse source pages with disk persistence.
 * 
 * Design decisions:
 * - 24-hour TTL: Browse screens cached for good UX, but individual manga clicks fetch fresh
 * - Weekly cleanup: Removes cache entries older than 7 days
 * - Search invalidation: Search always fetches fresh data
 * - Disk persistence: Cache survives app restarts
 */
class SourcePageCache(private val context: Context) {
    
    @Serializable
    private data class CachedPage(
        val mangas: List<SerializableManga>,
        val hasNextPage: Boolean,
        val timestamp: Long
    )
    
    @Serializable
    private data class SerializableManga(
        val url: String,
        val title: String,
        val thumbnail_url: String? = null,
        val initialized: Boolean = false,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: String? = null,
        val status: Int = 0
    )
    
    // In-memory cache for fast access
    private val memoryCache = LruCache<String, CachedPage>(MAX_MEMORY_CACHE_SIZE)
    
    // Disk cache directory
    private val cacheDir = File(context.cacheDir, "source_pages").apply {
        if (!exists()) mkdirs()
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Get cached page if available and not expired.
     * Checks memory cache first, then disk cache.
     */
    fun getCached(
        sourceId: Long,
        page: Int,
        query: String,
        filters: FilterList
    ): Pair<List<SManga>, Boolean>? {
        val cacheKey = buildCacheKey(sourceId, page, query, filters)
        
        // Check memory cache first
        memoryCache.get(cacheKey)?.let { cached ->
            if (!isExpired(cached.timestamp)) {
                Logger.d { "🗄️ [CACHE] Memory cache HIT for source $sourceId page $page" }
                return cached.toMangasPage()
            } else {
                Logger.d { "🗄️ [CACHE] Memory cache entry expired for source $sourceId page $page" }
                memoryCache.remove(cacheKey)
            }
        }
        
        // Check disk cache
        val diskCached = readFromDisk(cacheKey)
        if (diskCached != null && !isExpired(diskCached.timestamp)) {
            Logger.d { "🗄️ [CACHE] Disk cache HIT for source $sourceId page $page" }
            // Populate memory cache
            memoryCache.put(cacheKey, diskCached)
            return diskCached.toMangasPage()
        } else if (diskCached != null) {
            Logger.d { "🗄️ [CACHE] Disk cache entry expired for source $sourceId page $page" }
            deleteCacheFile(cacheKey)
        }
        
        Logger.d { "🌐 [CACHE] Cache MISS for source $sourceId page $page - will fetch from network" }
        return null
    }
    
    /**
     * Cache a page result to both memory and disk.
     */
    fun putCache(
        sourceId: Long,
        page: Int,
        query: String,
        filters: FilterList,
        mangas: List<SManga>,
        hasNextPage: Boolean
    ) {
        val cacheKey = buildCacheKey(sourceId, page, query, filters)
        val cachedPage = CachedPage(
            mangas = mangas.map { it.toSerializable() },
            hasNextPage = hasNextPage,
            timestamp = System.currentTimeMillis()
        )
        
        // Store in memory cache
        memoryCache.put(cacheKey, cachedPage)
        
        // Store in disk cache
        writeToDisk(cacheKey, cachedPage)
        
        Logger.d { "🗄️ [CACHE] Cached source $sourceId page $page with ${mangas.size} items" }
    }
    
    /**
     * Invalidate all cache entries for a source.
     * Used when source settings change.
     */
    fun invalidateSource(sourceId: Long) {
        Logger.d { "🗄️ [CACHE] Invalidating all cache for source $sourceId" }
        
        // Clear from memory cache
        val keysToRemove = mutableListOf<String>()
        memoryCache.snapshot().keys.forEach { key ->
            if (key.startsWith("$sourceId:")) {
                keysToRemove.add(key)
            }
        }
        keysToRemove.forEach { memoryCache.remove(it) }
        
        // Clear from disk cache
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("$sourceId-")) {
                file.delete()
            }
        }
    }
    
    /**
     * Clear all cache (memory and disk).
     */
    fun clearAll() {
        Logger.d { "🗄️ [CACHE] Clearing all cache" }
        memoryCache.evictAll()
        cacheDir.listFiles()?.forEach { it.delete() }
    }
    
    /**
     * Clean up cache entries older than 7 days.
     * Should be called weekly via background task.
     */
    fun cleanupOldEntries() {
        val cutoffTime = System.currentTimeMillis() - CLEANUP_AGE_MILLIS
        var deletedCount = 0
        
        cacheDir.listFiles()?.forEach { file ->
            try {
                val cached = json.decodeFromString<CachedPage>(file.readText())
                if (cached.timestamp < cutoffTime) {
                    file.delete()
                    deletedCount++
                }
            } catch (e: Exception) {
                // Corrupted file, delete it
                Logger.w(e) { "🗄️ [CACHE] Corrupted cache file: ${file.name}" }
                file.delete()
                deletedCount++
            }
        }
        
        Logger.d { "🗄️ [CACHE] Weekly cleanup: deleted $deletedCount old cache entries" }
    }
    
    // Private helper methods
    
    private fun buildCacheKey(
        sourceId: Long,
        page: Int,
        query: String,
        filters: FilterList
    ): String {
        // Hash filters to keep key manageable
        val filtersHash = filters.hashCode()
        val queryHash = query.hashCode()
        return "$sourceId:$page:$queryHash:$filtersHash"
    }
    
    private fun isExpired(timestamp: Long): Boolean {
        val age = System.currentTimeMillis() - timestamp
        return age > TTL_MILLIS
    }
    
    private fun readFromDisk(cacheKey: String): CachedPage? {
        val file = getCacheFile(cacheKey)
        if (!file.exists()) return null
        
        return try {
            val content = file.readText()
            json.decodeFromString<CachedPage>(content)
        } catch (e: Exception) {
            Logger.e(e) { "🗄️ [CACHE] Failed to read cache file: $cacheKey" }
            file.delete() // Delete corrupted file
            null
        }
    }
    
    private fun writeToDisk(cacheKey: String, cachedPage: CachedPage) {
        val file = getCacheFile(cacheKey)
        try {
            val content = json.encodeToString(cachedPage)
            file.writeText(content)
        } catch (e: Exception) {
            Logger.e(e) { "🗄️ [CACHE] Failed to write cache file: $cacheKey" }
        }
    }
    
    private fun getCacheFile(cacheKey: String): File {
        // Replace colons with dashes for valid filename
        val filename = cacheKey.replace(":", "-")
        return File(cacheDir, "$filename.json")
    }
    
    private fun deleteCacheFile(cacheKey: String) {
        getCacheFile(cacheKey).delete()
    }
    
    private fun SManga.toSerializable() = SerializableManga(
        url = url,
        title = title,
        thumbnail_url = thumbnail_url,
        initialized = initialized,
        author = author,
        artist = artist,
        description = description,
        genre = genre,
        status = status
    )
    
    private fun SerializableManga.toSManga() = SManga.create().apply {
        url = this@toSManga.url
        title = this@toSManga.title
        thumbnail_url = this@toSManga.thumbnail_url
        initialized = this@toSManga.initialized
        author = this@toSManga.author
        artist = this@toSManga.artist
        description = this@toSManga.description
        genre = this@toSManga.genre
        status = this@toSManga.status
    }
    
    private fun CachedPage.toMangasPage(): Pair<List<SManga>, Boolean> {
        return mangas.map { it.toSManga() } to hasNextPage
    }
    
    companion object {
        private const val MAX_MEMORY_CACHE_SIZE = 50 // ~50 pages in memory
        private val TTL_MILLIS = TimeUnit.HOURS.toMillis(24) // 24 hours
        private val CLEANUP_AGE_MILLIS = TimeUnit.DAYS.toMillis(7) // 7 days
    }
}
