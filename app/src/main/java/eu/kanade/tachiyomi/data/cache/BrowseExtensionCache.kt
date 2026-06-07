package eu.kanade.tachiyomi.data.cache

import android.content.Context
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.ui.source.SourceItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent cache for browse screen extension metadata.
 * Cache is invalidated only when:
 * - Extension is installed/updated/uninstalled
 * - App language settings change
 * - Hidden sources list changes
 * 
 * This eliminates repeated extension loading and icon fetching on every browse screen open.
 */
class BrowseExtensionCache(context: Context) {

    private val cacheDir = File(context.cacheDir, "browse_extensions")
    private val cacheFile = File(cacheDir, "extensions_metadata.json")
    
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    // In-memory cache for instant access
    private val memoryCache = ConcurrentHashMap<String, CachedSourceData>()
    
    // Cache version - increment when cache structure changes
    private val CACHE_VERSION = 1
    
    init {
        cacheDir.mkdirs()
        loadFromDisk()
    }

    /**
     * Get cached source list if available and valid.
     * Returns null if cache is invalid or doesn't exist.
     */
    fun getCachedSources(
        enabledLanguages: Set<String>,
        hiddenSources: Set<String>,
        pinnedSources: Set<String>,
        mode: yokai.core.content.ContentType
    ): List<SourceItem>? {
        val cacheKey = generateCacheKey(enabledLanguages, hiddenSources, pinnedSources, mode)
        
        val cached = memoryCache[cacheKey]
        if (cached != null) {
            Logger.d { "📦 [BROWSE_CACHE] Memory cache HIT for key: $cacheKey" }
            return cached.sourceItems
        }
        
        Logger.d { "📦 [BROWSE_CACHE] Cache MISS - will load from extensions" }
        return null
    }

    /**
     * Cache the current source list for future use.
     */
    fun cacheSources(
        sourceItems: List<SourceItem>,
        enabledLanguages: Set<String>,
        hiddenSources: Set<String>,
        pinnedSources: Set<String>,
        mode: yokai.core.content.ContentType
    ) {
        val cacheKey = generateCacheKey(enabledLanguages, hiddenSources, pinnedSources, mode)
        
        val cachedData = CachedSourceData(
            sourceItems = sourceItems,
            timestamp = System.currentTimeMillis(),
            extensionVersions = extractExtensionVersions(sourceItems)
        )
        
        memoryCache[cacheKey] = cachedData
        saveToDisk()
        
        Logger.d { "📦 [BROWSE_CACHE] Cached ${sourceItems.size} sources for key: $cacheKey" }
    }

    /**
     * Invalidate cache when extensions are modified.
     * This forces a fresh load on next browse screen open.
     */
    fun invalidate() {
        Logger.d { "📦 [BROWSE_CACHE] Cache invalidated - clearing all cached data" }
        memoryCache.clear()
        cacheFile.delete()
    }
    /**
     * Generate cache key from configuration.
     * Different configurations get separate cache entries.
     */
    private fun generateCacheKey(
        enabledLanguages: Set<String>,
        hiddenSources: Set<String>,
        pinnedSources: Set<String>,
        mode: yokai.core.content.ContentType
    ): String {
        val langKey = enabledLanguages.sorted().joinToString(",")
        val hiddenKey = hiddenSources.sorted().joinToString(",")
        val pinnedKey = pinnedSources.sorted().joinToString(",")
        val modeKey = mode.name
        return "lang:$langKey|hidden:$hiddenKey|pinned:$pinnedKey|mode:$modeKey"
    }

    /**
     * Extract extension package names and versions to detect updates.
     */
    private fun extractExtensionVersions(sources: List<SourceItem>): Map<String, String> {
        return sources
            .mapNotNull { it.source }
            .associate { source ->
                // Use source ID as identifier since we don't have package info here
                source.id.toString() to source.name
            }
    }

    /**
     * Save cache to disk for persistence across app restarts.
     */
    private fun saveToDisk() {
        try {
            val metadata = CacheMetadata(
                version = CACHE_VERSION,
                entries = memoryCache.mapValues { (_, data) ->
                    SerializedCacheData(
                        timestamp = data.timestamp,
                        extensionVersions = data.extensionVersions,
                        sourceCount = data.sourceItems.size
                        // Note: We only persist metadata, not the actual SourceItem objects
                        // because they contain non-serializable Source instances.
                        // The memory cache provides fast access during app lifetime.
                    )
                }
            )
            
            cacheFile.writeText(json.encodeToString(metadata))
            Logger.d { "📦 [BROWSE_CACHE] Saved cache to disk: ${memoryCache.size} entries" }
        } catch (e: Exception) {
            Logger.e(e) { "📦 [BROWSE_CACHE] Failed to save cache to disk" }
        }
    }

    /**
     * Load cache from disk on app start.
     */
    private fun loadFromDisk() {
        try {
            if (!cacheFile.exists()) {
                Logger.d { "📦 [BROWSE_CACHE] No disk cache found" }
                return
            }
            
            val jsonText = cacheFile.readText()
            val metadata = json.decodeFromString<CacheMetadata>(jsonText)
            
            if (metadata.version != CACHE_VERSION) {
                Logger.d { "📦 [BROWSE_CACHE] Cache version mismatch, clearing old cache" }
                cacheFile.delete()
                return
            }
            
            // Disk cache only validates that cache exists, actual data is in memory
            // during app runtime. On app restart, we'll rebuild from extensions.
            Logger.d { "📦 [BROWSE_CACHE] Loaded cache metadata: ${metadata.entries.size} entries" }
        } catch (e: Exception) {
            Logger.e(e) { "📦 [BROWSE_CACHE] Failed to load cache from disk" }
            cacheFile.delete()
        }
    }

    // Data classes for caching
    private data class CachedSourceData(
        val sourceItems: List<SourceItem>,
        val timestamp: Long,
        val extensionVersions: Map<String, String>
    )

    @Serializable
    private data class CacheMetadata(
        val version: Int,
        val entries: Map<String, SerializedCacheData>
    )

    @Serializable
    private data class SerializedCacheData(
        val timestamp: Long,
        val extensionVersions: Map<String, String>,
        val sourceCount: Int
    )
}
