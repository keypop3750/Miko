package yokai.source.novel.model

/**
 * Provider error types for novel sources
 */
sealed class NovelProviderError : Exception() {
    data class NetworkError(override val message: String) : NovelProviderError()
    data class ParsingError(override val message: String) : NovelProviderError()
    data class RateLimitError(val retryAfterMs: Long, override val message: String = "Rate limit exceeded") : NovelProviderError()
    data class ContentNotFoundError(override val message: String) : NovelProviderError()
    data class AuthenticationError(override val message: String) : NovelProviderError()
    data class UnknownError(override val message: String) : NovelProviderError()
}

/**
 * Alias for legacy ParseError references
 */
typealias ParseError = NovelProviderError.ParsingError