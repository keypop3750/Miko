package yokai.source.novel.providers.error

import kotlinx.coroutines.delay
import yokai.source.novel.model.NovelProviderError
import yokai.source.novel.model.ParseError

/**
 * Multi-tier error recovery system for novel providers
 * Handles network failures, parsing errors, and rate limiting
 */
class NovelErrorRecovery {
    
    private val retryStrategies = mapOf(
        NovelProviderError.NetworkError::class to NetworkRetryStrategy(),
        NovelProviderError.RateLimitError::class to RateLimitRetryStrategy(),
        NovelProviderError.ParsingError::class to ParseRetryStrategy(),
        NovelProviderError.ContentNotFoundError::class to ContentNotFoundStrategy(),
        NovelProviderError.AuthenticationError::class to AuthenticationStrategy()
    )
    
    /**
     * Attempts to recover from an error using appropriate strategy
     */
    suspend fun <T> recoverFromError(
        error: NovelProviderError,
        operation: suspend () -> T,
        context: RecoveryContext = RecoveryContext()
    ): RecoveryResult<T> {
        val strategy = retryStrategies[error::class] ?: return RecoveryResult.Failed(error)
        
        return try {
            strategy.recover(error, operation, context)
        } catch (e: Exception) {
            RecoveryResult.Failed(NovelProviderError.UnknownError(e.message ?: "Recovery failed"))
        }
    }
    
    /**
     * Executes operation with automatic error recovery
     */
    suspend fun <T> withRecovery(
        operation: suspend () -> T,
        context: RecoveryContext = RecoveryContext()
    ): RecoveryResult<T> {
        return try {
            val result = operation()
            RecoveryResult.Success(result)
        } catch (e: Exception) {
            val error = mapExceptionToError(e)
            recoverFromError(error, operation, context.incrementAttempt())
        }
    }
    
    private fun mapExceptionToError(exception: Exception): NovelProviderError {
        return when (exception) {
            is java.net.UnknownHostException -> NovelProviderError.NetworkError("Host unreachable: ${exception.message}")
            is java.net.SocketTimeoutException -> NovelProviderError.NetworkError("Connection timeout")
            is java.io.IOException -> NovelProviderError.NetworkError("Network error: ${exception.message}")
            is IllegalArgumentException -> NovelProviderError.ParsingError("Invalid data: ${exception.message}")
            else -> NovelProviderError.UnknownError(exception.message ?: "Unknown error")
        }
    }
}

/**
 * Context for error recovery operations
 */
data class RecoveryContext(
    val attemptNumber: Int = 1,
    val maxAttempts: Int = 3,
    val providerId: String = "",
    val operationType: String = "",
    val originalUrl: String = "",
    val customData: Map<String, Any> = emptyMap()
) {
    fun incrementAttempt() = copy(attemptNumber = attemptNumber + 1)
    fun hasAttemptsLeft() = attemptNumber < maxAttempts
}

/**
 * Result of error recovery operation
 */
sealed class RecoveryResult<T> {
    data class Success<T>(val data: T) : RecoveryResult<T>()
    data class Recovered<T>(val data: T, val recoveryInfo: String) : RecoveryResult<T>()
    data class Failed<T>(val error: NovelProviderError) : RecoveryResult<T>()
    data class PartialSuccess<T>(val data: T, val warnings: List<String>) : RecoveryResult<T>()
}

/**
 * Base interface for retry strategies
 */
abstract class RetryStrategy {
    abstract suspend fun <T> recover(
        error: NovelProviderError,
        operation: suspend () -> T,
        context: RecoveryContext
    ): RecoveryResult<T>
    
    protected suspend fun retryWithDelay(delayMs: Long) {
        if (delayMs > 0) delay(delayMs)
    }
}

/**
 * Network error recovery strategy
 * Handles connection failures, timeouts, and DNS issues
 */
class NetworkRetryStrategy : RetryStrategy() {
    override suspend fun <T> recover(
        error: NovelProviderError,
        operation: suspend () -> T,
        context: RecoveryContext
    ): RecoveryResult<T> {
        if (!context.hasAttemptsLeft()) {
            return RecoveryResult.Failed(error)
        }
        
        // Exponential backoff for network errors
        val delayMs = calculateNetworkDelay(context.attemptNumber)
        retryWithDelay(delayMs)
        
        return try {
            val result = operation()
            RecoveryResult.Recovered(result, "Recovered from network error after ${context.attemptNumber} attempts")
        } catch (e: Exception) {
            val newError = mapNetworkException(e)
            recover(newError, operation, context.incrementAttempt())
        }
    }
    
    private fun calculateNetworkDelay(attempt: Int): Long {
        return minOf(1000L * (1 shl attempt), 30000L) // Max 30 seconds
    }
    
    private fun mapNetworkException(e: Exception): NovelProviderError {
        return when (e) {
            is java.net.UnknownHostException -> NovelProviderError.NetworkError("DNS resolution failed")
            is java.net.SocketTimeoutException -> NovelProviderError.NetworkError("Connection timeout")
            else -> NovelProviderError.NetworkError("Network error: ${e.message}")
        }
    }
}

/**
 * Rate limit error recovery strategy
 * Handles HTTP 429 and similar rate limiting responses
 */
class RateLimitRetryStrategy : RetryStrategy() {
    override suspend fun <T> recover(
        error: NovelProviderError,
        operation: suspend () -> T,
        context: RecoveryContext
    ): RecoveryResult<T> {
        if (!context.hasAttemptsLeft()) {
            return RecoveryResult.Failed(error)
        }
        
        // Wait longer for rate limits
        val delayMs = calculateRateLimitDelay(context.attemptNumber)
        retryWithDelay(delayMs)
        
        return try {
            val result = operation()
            RecoveryResult.Recovered(result, "Recovered from rate limit after ${delayMs}ms delay")
        } catch (e: Exception) {
            if (isRateLimitException(e)) {
                recover(error, operation, context.incrementAttempt())
            } else {
                RecoveryResult.Failed(NovelProviderError.UnknownError(e.message ?: "Unknown error"))
            }
        }
    }
    
    private fun calculateRateLimitDelay(attempt: Int): Long {
        return when (attempt) {
            1 -> 5000L   // 5 seconds
            2 -> 15000L  // 15 seconds
            3 -> 60000L  // 1 minute
            else -> 120000L // 2 minutes
        }
    }
    
    private fun isRateLimitException(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("rate limit") || 
               message.contains("too many requests") ||
               message.contains("429")
    }
}

/**
 * Parse error recovery strategy
 * Attempts to use fallback selectors or parsing methods
 */
class ParseRetryStrategy : RetryStrategy() {
    override suspend fun <T> recover(
        error: NovelProviderError,
        operation: suspend () -> T,
        context: RecoveryContext
    ): RecoveryResult<T> {
        // Parse errors usually don't benefit from simple retries
        // Instead, we might try alternative parsing approaches
        return when (context.operationType) {
            "search" -> attemptFallbackSearch(operation, context)
            "chapter_content" -> attemptFallbackContentParsing(operation, context)
            else -> RecoveryResult.Failed(error)
        }
    }
    
    private suspend fun <T> attemptFallbackSearch(
        operation: suspend () -> T,
        context: RecoveryContext
    ): RecoveryResult<T> {
        // Could implement fallback search strategies here
        return RecoveryResult.Failed(NovelProviderError.ParsingError("No fallback search available"))
    }
    
    private suspend fun <T> attemptFallbackContentParsing(
        operation: suspend () -> T,
        context: RecoveryContext
    ): RecoveryResult<T> {
        // Could implement fallback content parsing here
        return RecoveryResult.Failed(NovelProviderError.ParsingError("No fallback content parsing available"))
    }
}

/**
 * Content not found error strategy
 * Handles missing chapters, deleted content, etc.
 */
class ContentNotFoundStrategy : RetryStrategy() {
    override suspend fun <T> recover(
        error: NovelProviderError,
        operation: suspend () -> T,
        context: RecoveryContext
    ): RecoveryResult<T> {
        // Content not found errors usually aren't recoverable
        // But we might try alternative URLs or mirrors
        return RecoveryResult.Failed(error)
    }
}

/**
 * Authentication error strategy
 * Handles login requirements, CAPTCHA, etc.
 */
class AuthenticationStrategy : RetryStrategy() {
    override suspend fun <T> recover(
        error: NovelProviderError,
        operation: suspend () -> T,
        context: RecoveryContext
    ): RecoveryResult<T> {
        // Authentication errors typically require user intervention
        return RecoveryResult.Failed(error)
    }
}