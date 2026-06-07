package yokai.source.novel.providers.error

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Network resilience manager for novel providers
 * Handles connection health, circuit breakers, and network optimization
 */
class NetworkResilienceManager {
    
    private val providerHealthStates = ConcurrentHashMap<String, ProviderHealthState>()
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()
    private val rateLimiters = ConcurrentHashMap<String, RateLimiter>()
    
    // Network metrics
    private val _networkMetrics = MutableStateFlow(NetworkMetrics())
    val networkMetrics: StateFlow<NetworkMetrics> = _networkMetrics.asStateFlow()
    
    /**
     * Executes network operation with resilience features
     */
    suspend fun <T> executeWithResilience(
        providerId: String,
        operation: suspend () -> T,
        config: ResilienceConfig = ResilienceConfig()
    ): ResilienceResult<T> {
        val circuitBreaker = getCircuitBreaker(providerId, config)
        val rateLimiter = getRateLimiter(providerId, config)
        
        // Check circuit breaker
        if (!circuitBreaker.canExecute()) {
            return ResilienceResult.CircuitBreakerOpen(circuitBreaker.getFailureReason())
        }
        
        // Apply rate limiting
        rateLimiter.acquire()
        
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = withTimeout(config.timeoutMs) {
                operation()
            }
            
            // Record success
            val duration = System.currentTimeMillis() - startTime
            recordSuccess(providerId, duration)
            circuitBreaker.recordSuccess()
            
            ResilienceResult.Success(result)
            
        } catch (e: TimeoutCancellationException) {
            recordTimeout(providerId)
            circuitBreaker.recordFailure()
            ResilienceResult.Timeout("Operation timed out after ${config.timeoutMs}ms")
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            recordFailure(providerId, e, duration)
            circuitBreaker.recordFailure()
            ResilienceResult.Failed(e)
        }
    }
    
    /**
     * Gets provider health status
     */
    fun getProviderHealth(providerId: String): ProviderHealthStatus {
        val healthState = providerHealthStates[providerId] ?: return ProviderHealthStatus(
            providerId = providerId,
            isHealthy = false,
            successRate = 0.0,
            averageResponseTime = 0L,
            lastError = null,
            circuitBreakerState = CircuitBreakerState.CLOSED,
            requestCount = 0L,
            errorCount = 0L
        )
        val circuitBreaker = circuitBreakers[providerId]
        
        return ProviderHealthStatus(
            providerId = providerId,
            isHealthy = healthState.isHealthy(),
            successRate = healthState.getSuccessRate(),
            averageResponseTime = healthState.getAverageResponseTime(),
            lastError = healthState.lastError,
            circuitBreakerState = circuitBreaker?.state ?: CircuitBreakerState.CLOSED,
            requestCount = healthState.totalRequests.get(),
            errorCount = healthState.errorCount.get()
        )
    }
    
    /**
     * Gets overall network health
     */
    fun getNetworkHealth(): NetworkHealth {
        val allProviders = providerHealthStates.values
        val healthyCount = allProviders.count { it.isHealthy() }
        val totalCount = allProviders.size
        
        return NetworkHealth(
            overallHealthy = healthyCount > totalCount / 2,
            healthyProviders = healthyCount,
            totalProviders = totalCount,
            networkLatency = calculateAverageLatency(),
            activeConnections = calculateActiveConnections()
        )
    }
    
    /**
     * Resets provider health state (useful for manual recovery)
     */
    fun resetProviderHealth(providerId: String) {
        providerHealthStates.remove(providerId)
        circuitBreakers[providerId]?.reset()
        rateLimiters.remove(providerId)
    }
    
    private fun getCircuitBreaker(providerId: String, config: ResilienceConfig): CircuitBreaker {
        return circuitBreakers.getOrPut(providerId) {
            CircuitBreaker(
                failureThreshold = config.circuitBreakerFailureThreshold,
                recoveryTimeoutMs = config.circuitBreakerRecoveryTimeout,
                halfOpenMaxCalls = config.circuitBreakerHalfOpenMaxCalls
            )
        }
    }
    
    private fun getRateLimiter(providerId: String, config: ResilienceConfig): RateLimiter {
        return rateLimiters.getOrPut(providerId) {
            RateLimiter(
                maxRequestsPerSecond = config.maxRequestsPerSecond,
                burstSize = config.burstSize
            )
        }
    }
    
    private fun recordSuccess(providerId: String, duration: Long) {
        val healthState = providerHealthStates.getOrPut(providerId) { ProviderHealthState() }
        healthState.recordSuccess(duration)
        updateNetworkMetrics()
    }
    
    private fun recordFailure(providerId: String, error: Exception, duration: Long) {
        val healthState = providerHealthStates.getOrPut(providerId) { ProviderHealthState() }
        healthState.recordFailure(error, duration)
        updateNetworkMetrics()
    }
    
    private fun recordTimeout(providerId: String) {
        val healthState = providerHealthStates.getOrPut(providerId) { ProviderHealthState() }
        healthState.recordTimeout()
        updateNetworkMetrics()
    }
    
    private fun updateNetworkMetrics() {
        val metrics = NetworkMetrics(
            totalRequests = providerHealthStates.values.sumOf { it.totalRequests.get() },
            totalErrors = providerHealthStates.values.sumOf { it.errorCount.get() },
            averageResponseTime = calculateAverageLatency(),
            activeProviders = providerHealthStates.size
        )
        _networkMetrics.value = metrics
    }
    
    private fun calculateAverageLatency(): Long {
        val states = providerHealthStates.values
        return if (states.isEmpty()) 0L else states.map { it.getAverageResponseTime() }.average().toLong()
    }
    
    private fun calculateActiveConnections(): Int {
        return circuitBreakers.values.count { it.state == CircuitBreakerState.CLOSED }
    }
}

/**
 * Configuration for network resilience features
 */
data class ResilienceConfig(
    val timeoutMs: Long = 30000L,
    val maxRequestsPerSecond: Int = 5,
    val burstSize: Int = 10,
    val circuitBreakerFailureThreshold: Int = 5,
    val circuitBreakerRecoveryTimeout: Long = 60000L,
    val circuitBreakerHalfOpenMaxCalls: Int = 3
)

/**
 * Result of resilient network operation
 */
sealed class ResilienceResult<T> {
    data class Success<T>(val data: T) : ResilienceResult<T>()
    data class Failed<T>(val error: Exception) : ResilienceResult<T>()
    data class Timeout<T>(val message: String) : ResilienceResult<T>()
    data class CircuitBreakerOpen<T>(val reason: String) : ResilienceResult<T>()
    data class RateLimited<T>(val retryAfterMs: Long) : ResilienceResult<T>()
}

/**
 * Provider health tracking
 */
class ProviderHealthState {
    val totalRequests = AtomicLong(0)
    val successCount = AtomicLong(0)
    val errorCount = AtomicLong(0)
    val timeoutCount = AtomicLong(0)
    private val responseTimes = mutableListOf<Long>()
    private val maxResponseTimeHistory = 100
    
    @Volatile
    var lastError: Exception? = null
    
    @Volatile
    var lastSuccessTime: Long = 0
    
    fun recordSuccess(responseTime: Long) {
        totalRequests.incrementAndGet()
        successCount.incrementAndGet()
        lastSuccessTime = System.currentTimeMillis()
        
        synchronized(responseTimes) {
            responseTimes.add(responseTime)
            if (responseTimes.size > maxResponseTimeHistory) {
                responseTimes.removeAt(0)
            }
        }
    }
    
    fun recordFailure(error: Exception, responseTime: Long) {
        totalRequests.incrementAndGet()
        errorCount.incrementAndGet()
        lastError = error
        
        synchronized(responseTimes) {
            responseTimes.add(responseTime)
            if (responseTimes.size > maxResponseTimeHistory) {
                responseTimes.removeAt(0)
            }
        }
    }
    
    fun recordTimeout() {
        totalRequests.incrementAndGet()
        timeoutCount.incrementAndGet()
    }
    
    fun isHealthy(): Boolean {
        val total = totalRequests.get()
        if (total == 0L) return true
        
        val successRate = successCount.get().toDouble() / total
        return successRate >= 0.8 && getAverageResponseTime() < 10000 // 80% success rate, <10s response
    }
    
    fun getSuccessRate(): Double {
        val total = totalRequests.get()
        return if (total == 0L) 1.0 else successCount.get().toDouble() / total
    }
    
    fun getAverageResponseTime(): Long {
        synchronized(responseTimes) {
            return if (responseTimes.isEmpty()) 0L else responseTimes.average().toLong()
        }
    }
}

/**
 * Circuit breaker implementation
 */
class CircuitBreaker(
    private val failureThreshold: Int,
    private val recoveryTimeoutMs: Long,
    private val halfOpenMaxCalls: Int
) {
    @Volatile
    var state: CircuitBreakerState = CircuitBreakerState.CLOSED
    
    private val failureCount = AtomicLong(0)
    private val halfOpenCallCount = AtomicLong(0)
    private var lastFailureTime: Long = 0
    private var lastFailureReason: String = ""
    
    fun canExecute(): Boolean {
        return when (state) {
            CircuitBreakerState.CLOSED -> true
            CircuitBreakerState.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime > recoveryTimeoutMs) {
                    state = CircuitBreakerState.HALF_OPEN
                    halfOpenCallCount.set(0)
                    true
                } else {
                    false
                }
            }
            CircuitBreakerState.HALF_OPEN -> halfOpenCallCount.get() < halfOpenMaxCalls
        }
    }
    
    fun recordSuccess() {
        when (state) {
            CircuitBreakerState.HALF_OPEN -> {
                if (halfOpenCallCount.incrementAndGet() >= halfOpenMaxCalls) {
                    state = CircuitBreakerState.CLOSED
                    failureCount.set(0)
                }
            }
            CircuitBreakerState.CLOSED -> {
                failureCount.set(0)
            }
            else -> { /* no-op */ }
        }
    }
    
    fun recordFailure() {
        when (state) {
            CircuitBreakerState.CLOSED -> {
                if (failureCount.incrementAndGet() >= failureThreshold) {
                    state = CircuitBreakerState.OPEN
                    lastFailureTime = System.currentTimeMillis()
                    lastFailureReason = "Failure threshold exceeded"
                }
            }
            CircuitBreakerState.HALF_OPEN -> {
                state = CircuitBreakerState.OPEN
                lastFailureTime = System.currentTimeMillis()
                lastFailureReason = "Failed during half-open state"
            }
            else -> { /* no-op */ }
        }
    }
    
    fun reset() {
        state = CircuitBreakerState.CLOSED
        failureCount.set(0)
        halfOpenCallCount.set(0)
    }
    
    fun getFailureReason(): String = lastFailureReason
}

enum class CircuitBreakerState {
    CLOSED, OPEN, HALF_OPEN
}

/**
 * Rate limiter implementation
 */
class RateLimiter(
    private val maxRequestsPerSecond: Int,
    private val burstSize: Int
) {
    private val tokens = AtomicLong(burstSize.toLong())
    private var lastRefillTime = System.currentTimeMillis()
    
    suspend fun acquire() {
        while (!tryAcquire()) {
            delay(100) // Wait 100ms before retrying
        }
    }
    
    private fun tryAcquire(): Boolean {
        refillTokens()
        return tokens.getAndDecrement() > 0
    }
    
    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val timePassed = now - lastRefillTime
        
        if (timePassed >= 1000) { // Refill every second
            val tokensToAdd = (timePassed / 1000) * maxRequestsPerSecond
            val newTokens = minOf(tokens.get() + tokensToAdd, burstSize.toLong())
            tokens.set(newTokens)
            lastRefillTime = now
        }
    }
}

/**
 * Data classes for health monitoring
 */
data class ProviderHealthStatus(
    val providerId: String,
    val isHealthy: Boolean,
    val successRate: Double,
    val averageResponseTime: Long,
    val lastError: Exception?,
    val circuitBreakerState: CircuitBreakerState,
    val requestCount: Long,
    val errorCount: Long
)

data class NetworkHealth(
    val overallHealthy: Boolean,
    val healthyProviders: Int,
    val totalProviders: Int,
    val networkLatency: Long,
    val activeConnections: Int
)

data class NetworkMetrics(
    val totalRequests: Long = 0,
    val totalErrors: Long = 0,
    val averageResponseTime: Long = 0,
    val activeProviders: Int = 0
)