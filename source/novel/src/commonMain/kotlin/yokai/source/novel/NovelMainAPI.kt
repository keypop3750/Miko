package yokai.source.novel

import yokai.core.content.ContentProvider
import yokai.source.novel.model.*
import yokai.source.novel.network.NovelHttpClient

/**
 * Base abstract class for novel providers
 * Novel source ID range: 6000L - 6999L (to avoid conflicts with manga sources)
 */
abstract class NovelMainAPI : ContentProvider {
    abstract override val id: Long              // Must be >= 6000L
    abstract override val name: String
    abstract val mainUrl: String
    abstract val hasMainPage: Boolean
    override val lang: String = "en"
    open val rateLimitTime: Long = 1000L // milliseconds between requests
    
    // HTTP client for network operations (injected from Android layer)
    lateinit var httpClient: NovelHttpClient
    
    // Provider capabilities
    open val capabilities: NovelProviderCapabilities = NovelProviderCapabilities()
    
    // Core novel provider methods
    abstract suspend fun searchNovels(query: String, page: Int = 1): List<NovelSearchResult>
    abstract suspend fun getNovelDetails(url: String): NovelDetails
    abstract suspend fun getChapterList(novelUrl: String): List<NovelChapter>
    abstract suspend fun getNovelChapterContent(chapterUrl: String): NovelContent
    
    // Optional methods for enhanced functionality
    open suspend fun getLatestUpdates(page: Int = 1): List<NovelSearchResult> = emptyList()
    open suspend fun getPopularNovels(page: Int = 1): List<NovelSearchResult> = emptyList()
    open suspend fun getBrowseNovels(page: Int = 1, filters: Map<String, String> = emptyMap()): List<NovelSearchResult> = emptyList()
    
    /**
     * Get comments for a chapter.
     * Only called if [capabilities] returns supportsComments = true.
     */
    open suspend fun getChapterComments(chapterUrl: String): List<yokai.source.novel.model.NovelComment> = emptyList()
    
    // Filter support
    open fun getFilterList(): List<NovelFilter> = emptyList()
    
    // Rate limiting support
    open fun getRateLimitStrategy(): RateLimitStrategy = RateLimitStrategy.Fixed(rateLimitTime)
    
    // Error recovery hooks
    open suspend fun handleNetworkError(error: Exception, retryCount: Int): Boolean = false
    open suspend fun handleParsingError(error: Exception, url: String): Boolean = false
    
    // Content validation
    open fun validateNovelUrl(url: String): Boolean = url.startsWith(mainUrl)
    open fun validateChapterUrl(url: String): Boolean = url.startsWith(mainUrl)
    
    // Implementation of ContentProvider interface
    override suspend fun search(query: String): List<yokai.core.content.ContentItem> {
        return searchNovels(query).map { it as yokai.core.content.ContentItem }
    }
    
    override suspend fun getDetails(url: String): yokai.core.content.ContentItem {
        return getNovelDetails(url)
    }
    
    override suspend fun getChapterContent(url: String): String {
        return getNovelChapterContent(url).content
    }
    
    override val version: String = "1.0.0"
}

/**
 * Rate limiting strategies for providers
 */
sealed class RateLimitStrategy {
    data class Fixed(val delayMs: Long) : RateLimitStrategy()
    data class Exponential(val baseDelayMs: Long, val maxDelayMs: Long = 30000L) : RateLimitStrategy()
    data class Adaptive(val minDelayMs: Long, val maxDelayMs: Long) : RateLimitStrategy()
    object None : RateLimitStrategy()
}

/**
 * Provider error types for recovery
 */
sealed class NovelProviderError : Exception() {
    data class NetworkError(val httpCode: Int, override val message: String) : NovelProviderError()
    data class ParsingError(val url: String, override val message: String) : NovelProviderError()
    data class RateLimitError(val retryAfterMs: Long) : NovelProviderError()
    data class ContentNotFoundError(val url: String) : NovelProviderError()
    data class UnknownError(override val message: String) : NovelProviderError()
}