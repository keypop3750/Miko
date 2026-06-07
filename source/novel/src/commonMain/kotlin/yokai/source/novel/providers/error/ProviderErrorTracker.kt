package yokai.source.novel.providers.error

import kotlinx.coroutines.flow.*
import yokai.source.novel.model.NovelProviderError
import yokai.source.novel.model.ParseError
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Provider error tracking and analytics system
 * Monitors error patterns, generates insights, and provides debugging information
 */
class ProviderErrorTracker {
    
    private val errorHistory = ConcurrentHashMap<String, MutableList<ErrorRecord>>()
    private val errorStats = ConcurrentHashMap<String, ErrorStatistics>()
    private val errorPatterns = ConcurrentHashMap<String, ErrorPattern>()
    
    // Error events stream
    private val _errorEvents = MutableSharedFlow<ErrorEvent>()
    val errorEvents: SharedFlow<ErrorEvent> = _errorEvents.asSharedFlow()
    
    // Analytics data
    private val _analytics = MutableStateFlow(ErrorAnalytics())
    val analytics: StateFlow<ErrorAnalytics> = _analytics.asStateFlow()
    
    /**
     * Records an error for a specific provider
     */
    fun recordError(
        providerId: String,
        error: NovelProviderError,
        context: ErrorContext
    ) {
        val errorRecord = ErrorRecord(
            timestamp = System.currentTimeMillis(),
            error = error,
            context = context,
            stackTrace = Thread.currentThread().stackTrace.take(10).map { it.toString() }
        )
        
        // Add to history
        errorHistory.getOrPut(providerId) { mutableListOf() }.add(errorRecord)
        
        // Update statistics
        updateErrorStatistics(providerId, error)
        
        // Detect patterns
        detectErrorPatterns(providerId, errorRecord)
        
        // Emit error event
        _errorEvents.tryEmit(ErrorEvent(providerId, errorRecord))
        
        // Update analytics
        updateAnalytics()
        
        // Clean old records (keep last 1000 per provider)
        cleanOldRecords(providerId)
    }
    
    /**
     * Gets error summary for a provider
     */
    fun getProviderErrorSummary(providerId: String): ProviderErrorSummary {
        val records = errorHistory[providerId] ?: emptyList()
        val stats = errorStats[providerId] ?: ErrorStatistics()
        val patterns = errorPatterns[providerId]
        
        return ProviderErrorSummary(
            providerId = providerId,
            totalErrors = records.size,
            recentErrors = records.takeLast(10),
            errorsByType = stats.errorsByType.toMap(),
            mostCommonError = stats.getMostCommonError(),
            errorRate = calculateErrorRate(providerId),
            detectedPattern = patterns,
            recommendations = generateRecommendations(providerId, patterns)
        )
    }
    
    /**
     * Gets errors for a specific time range
     */
    fun getErrorsInRange(
        providerId: String,
        startTime: Long,
        endTime: Long
    ): List<ErrorRecord> {
        return errorHistory[providerId]?.filter { 
            it.timestamp in startTime..endTime 
        } ?: emptyList()
    }
    
    /**
     * Gets overall error analytics
     */
    fun getErrorAnalytics(): ErrorAnalytics {
        return _analytics.value
    }
    
    /**
     * Gets providers with highest error rates
     */
    fun getProbleraticProviders(limit: Int = 10): List<ProviderErrorInfo> {
        return errorStats.entries
            .map { (providerId, stats) ->
                ProviderErrorInfo(
                    providerId = providerId,
                    errorCount = stats.totalErrors.get(),
                    errorRate = calculateErrorRate(providerId),
                    lastErrorTime = errorHistory[providerId]?.lastOrNull()?.timestamp ?: 0L,
                    dominantErrorType = stats.getMostCommonError()
                )
            }
            .sortedByDescending { it.errorRate }
            .take(limit)
    }
    
    /**
     * Clears error history for a provider
     */
    fun clearProviderErrors(providerId: String) {
        errorHistory.remove(providerId)
        errorStats.remove(providerId)
        errorPatterns.remove(providerId)
        updateAnalytics()
    }
    
    /**
     * Exports error data for analysis
     */
    fun exportErrorData(providerId: String? = null): ErrorExport {
        return if (providerId != null) {
            ErrorExport(
                providers = mapOf(providerId to (errorHistory[providerId] ?: emptyList())),
                exportTime = System.currentTimeMillis(),
                analytics = _analytics.value
            )
        } else {
            ErrorExport(
                providers = errorHistory.toMap(),
                exportTime = System.currentTimeMillis(),
                analytics = _analytics.value
            )
        }
    }
    
    private fun updateErrorStatistics(providerId: String, error: NovelProviderError) {
        val stats = errorStats.getOrPut(providerId) { ErrorStatistics() }
        stats.totalErrors.incrementAndGet()
        stats.errorsByType.compute(error::class.simpleName ?: "Unknown") { _, count ->
            (count ?: 0) + 1
        }
        stats.lastErrorTime = System.currentTimeMillis()
    }
    
    private fun detectErrorPatterns(providerId: String, errorRecord: ErrorRecord) {
        val recentErrors = errorHistory[providerId]?.takeLast(10) ?: return
        
        // Check for repeating error patterns
        val errorTypes = recentErrors.map { it.error::class.simpleName }
        val pattern = findRepeatingPattern(errorTypes)
        
        if (pattern != null) {
            errorPatterns[providerId] = ErrorPattern(
                type = ErrorPatternType.REPEATING,
                description = "Repeating pattern: ${pattern.joinToString(" -> ")}",
                frequency = calculatePatternFrequency(errorTypes, pattern),
                severity = determinePatterSeverity(pattern)
            )
        }
        
        // Check for escalating errors (increasing severity)
        val severities = recentErrors.map { getErrorSeverity(it.error) }
        if (isEscalatingPattern(severities)) {
            errorPatterns[providerId] = ErrorPattern(
                type = ErrorPatternType.ESCALATING,
                description = "Error severity is escalating",
                frequency = 1.0,
                severity = ErrorSeverity.HIGH
            )
        }
        
        // Check for burst patterns (many errors in short time)
        val now = System.currentTimeMillis()
        val recentCount = recentErrors.count { now - it.timestamp < 60000 } // Last minute
        if (recentCount >= 5) {
            errorPatterns[providerId] = ErrorPattern(
                type = ErrorPatternType.BURST,
                description = "Error burst detected: $recentCount errors in last minute",
                frequency = recentCount.toDouble() / 60.0, // Errors per second
                severity = ErrorSeverity.HIGH
            )
        }
    }
    
    private fun findRepeatingPattern(errorTypes: List<String?>): List<String>? {
        // Simple pattern detection - look for sequences that repeat
        for (patternLength in 2..5) {
            if (errorTypes.size < patternLength * 2) continue
            
            val pattern = errorTypes.takeLast(patternLength)
            val previousPattern = errorTypes.dropLast(patternLength).takeLast(patternLength)
            
            if (pattern == previousPattern && pattern.all { it != null }) {
                return pattern.filterNotNull()
            }
        }
        return null
    }
    
    private fun calculatePatternFrequency(errorTypes: List<String?>, pattern: List<String>): Double {
        var count = 0
        val patternSize = pattern.size
        
        for (i in 0..errorTypes.size - patternSize) {
            val slice = errorTypes.subList(i, i + patternSize).filterNotNull()
            if (slice == pattern) count++
        }
        
        return count.toDouble() / maxOf(1, errorTypes.size - patternSize + 1)
    }
    
    private fun determinePatterSeverity(pattern: List<String>): ErrorSeverity {
        return when {
            pattern.any { it.contains("Network") || it.contains("Timeout") } -> ErrorSeverity.MEDIUM
            pattern.any { it.contains("Parse") || it.contains("Content") } -> ErrorSeverity.LOW
            pattern.any { it.contains("Authentication") || it.contains("RateLimit") } -> ErrorSeverity.HIGH
            else -> ErrorSeverity.MEDIUM
        }
    }
    
    private fun getErrorSeverity(error: NovelProviderError): Int {
        return when (error) {
            is NovelProviderError.NetworkError -> 3
            is NovelProviderError.RateLimitError -> 4
            is NovelProviderError.AuthenticationError -> 5
            is NovelProviderError.ParsingError -> 2
            is NovelProviderError.ContentNotFoundError -> 1
            is NovelProviderError.UnknownError -> 3
        }
    }
    
    private fun isEscalatingPattern(severities: List<Int>): Boolean {
        if (severities.size < 3) return false
        
        val recentTrend = severities.takeLast(3)
        return recentTrend[0] < recentTrend[1] && recentTrend[1] <= recentTrend[2]
    }
    
    private fun calculateErrorRate(providerId: String): Double {
        val records = errorHistory[providerId] ?: return 0.0
        if (records.isEmpty()) return 0.0
        
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600000 // 1 hour
        val recentErrors = records.count { it.timestamp > oneHourAgo }
        
        return recentErrors.toDouble() / 60.0 // Errors per minute
    }
    
    private fun generateRecommendations(
        providerId: String,
        pattern: ErrorPattern?
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        pattern?.let {
            when (it.type) {
                ErrorPatternType.REPEATING -> {
                    recommendations.add("Consider implementing retry logic with exponential backoff")
                    recommendations.add("Check if provider has changed their website structure")
                }
                ErrorPatternType.ESCALATING -> {
                    recommendations.add("Monitor provider closely - may require immediate attention")
                    recommendations.add("Consider temporarily disabling provider if errors continue")
                }
                ErrorPatternType.BURST -> {
                    recommendations.add("Implement circuit breaker to prevent cascade failures")
                    recommendations.add("Increase rate limiting delay for this provider")
                }
            }
        }
        
        val stats = errorStats[providerId]
        stats?.let { s ->
            val mostCommon = s.getMostCommonError()
            when (mostCommon) {
                "NetworkError" -> recommendations.add("Check network connectivity and DNS resolution")
                "ParseError" -> recommendations.add("Provider may have updated their HTML structure")
                "RateLimitError" -> recommendations.add("Increase delay between requests")
                "AuthenticationError" -> recommendations.add("Check if provider requires login or CAPTCHA")
            }
        }
        
        return recommendations.distinct()
    }
    
    private fun updateAnalytics() {
        val totalErrors = errorHistory.values.sumOf { it.size }
        val activeProviders = errorHistory.keys.size
        val now = System.currentTimeMillis()
        val recentErrors = errorHistory.values.flatten().count { now - it.timestamp < 3600000 }
        
        _analytics.value = ErrorAnalytics(
            totalErrors = totalErrors,
            recentErrors = recentErrors,
            activeProviders = activeProviders,
            averageErrorsPerProvider = if (activeProviders > 0) totalErrors.toDouble() / activeProviders else 0.0,
            lastUpdated = now
        )
    }
    
    private fun cleanOldRecords(providerId: String) {
        val records = errorHistory[providerId]
        if (records != null && records.size > 1000) {
            val toKeep = records.takeLast(1000)
            errorHistory[providerId] = toKeep.toMutableList()
        }
    }
}

/**
 * Context information for error tracking
 */
data class ErrorContext(
    val operation: String,
    val url: String? = null,
    val userAgent: String? = null,
    val httpStatus: Int? = null,
    val responseTime: Long? = null,
    val retryAttempt: Int = 0,
    val customData: Map<String, Any> = emptyMap()
)

/**
 * Individual error record
 */
data class ErrorRecord(
    val timestamp: Long,
    val error: NovelProviderError,
    val context: ErrorContext,
    val stackTrace: List<String>
)

/**
 * Error statistics for a provider
 */
class ErrorStatistics {
    val totalErrors = AtomicLong(0)
    val errorsByType = ConcurrentHashMap<String, Int>()
    var lastErrorTime: Long = 0
    
    fun getMostCommonError(): String? {
        return errorsByType.maxByOrNull { it.value }?.key
    }
}

/**
 * Detected error pattern
 */
data class ErrorPattern(
    val type: ErrorPatternType,
    val description: String,
    val frequency: Double,
    val severity: ErrorSeverity
)

enum class ErrorPatternType {
    REPEATING, ESCALATING, BURST
}

enum class ErrorSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Provider error summary
 */
data class ProviderErrorSummary(
    val providerId: String,
    val totalErrors: Int,
    val recentErrors: List<ErrorRecord>,
    val errorsByType: Map<String, Int>,
    val mostCommonError: String?,
    val errorRate: Double,
    val detectedPattern: ErrorPattern?,
    val recommendations: List<String>
)

/**
 * Provider error information for rankings
 */
data class ProviderErrorInfo(
    val providerId: String,
    val errorCount: Long,
    val errorRate: Double,
    val lastErrorTime: Long,
    val dominantErrorType: String?
)

/**
 * Error analytics data
 */
data class ErrorAnalytics(
    val totalErrors: Int = 0,
    val recentErrors: Int = 0,
    val activeProviders: Int = 0,
    val averageErrorsPerProvider: Double = 0.0,
    val lastUpdated: Long = 0
)

/**
 * Error event for real-time monitoring
 */
data class ErrorEvent(
    val providerId: String,
    val errorRecord: ErrorRecord
)

/**
 * Error data export
 */
data class ErrorExport(
    val providers: Map<String, List<ErrorRecord>>,
    val exportTime: Long,
    val analytics: ErrorAnalytics
)