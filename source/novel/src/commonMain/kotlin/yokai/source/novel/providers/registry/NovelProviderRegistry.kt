package yokai.source.novel.providers.registry

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import yokai.source.novel.NovelMainAPI
import yokai.source.novel.model.*
import yokai.source.novel.providers.error.NetworkResilienceManager
import yokai.source.novel.providers.error.ProviderErrorTracker
import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for novel providers with health monitoring and management
 * Handles provider discovery, initialization, and lifecycle management
 */
class NovelProviderRegistry {
    
    private val _providers = ConcurrentHashMap<String, ProviderEntry>()
    private val resilienceManager = NetworkResilienceManager()
    private val errorTracker = ProviderErrorTracker()
    
    // Provider status streams
    private val _providerStatus = MutableStateFlow<Map<String, ProviderStatus>>(emptyMap())
    val providerStatus: StateFlow<Map<String, ProviderStatus>> = _providerStatus.asStateFlow()
    
    private val _healthyProviders = MutableStateFlow<List<String>>(emptyList())
    val healthyProviders: StateFlow<List<String>> = _healthyProviders.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        // Register built-in providers
        registerBuiltInProviders()
        
        // Start health monitoring
        startHealthMonitoring()
    }
    
    /**
     * Registers a novel provider
     */
    fun registerProvider(provider: NovelMainAPI) {
        val entry = ProviderEntry(
            provider = provider,
            status = ProviderStatus.INACTIVE,
            registrationTime = System.currentTimeMillis(),
            lastHealthCheck = 0L,
            errorCount = 0
        )
        
        _providers[provider.id.toString()] = entry
        updateProviderStatus()
    }
    
    /**
     * Unregisters a provider
     */
    fun unregisterProvider(providerId: String) {
        _providers.remove(providerId)
        updateProviderStatus()
    }
    
    /**
     * Gets a provider by ID
     */
    fun getProvider(providerId: String): NovelMainAPI? {
        return _providers[providerId]?.provider
    }
    
    /**
     * Gets all registered providers
     */
    fun getAllProviders(): List<NovelMainAPI> {
        return _providers.values.map { it.provider }
    }
    
    /**
     * Gets healthy providers only
     */
    fun getHealthyProviders(): List<NovelMainAPI> {
        return _providers.values
            .filter { it.status == ProviderStatus.HEALTHY }
            .map { it.provider }
    }
    
    /**
     * Gets providers by language
     */
    fun getProvidersByLanguage(lang: String): List<NovelMainAPI> {
        return _providers.values
            .filter { it.provider.lang == lang }
            .map { it.provider }
    }
    
    /**
     * Gets providers with specific capabilities
     */
    fun getProvidersWithCapability(capability: ProviderCapability): List<NovelMainAPI> {
        return _providers.values
            .filter { hasCapability(it.provider, capability) }
            .map { it.provider }
    }
    
    /**
     * Searches across all healthy providers
     */
    suspend fun searchAll(
        query: String,
        maxProviders: Int = 5,
        timeoutMs: Long = 10000L
    ): SearchAllResult {
        val providers = getHealthyProviders().take(maxProviders)
        val results = mutableListOf<ProviderSearchResult>()
        val errors = mutableListOf<ProviderSearchError>()
        
        coroutineScope {
            providers.map { provider ->
                async {
                    try {
                        withTimeout(timeoutMs) {
                            val searchResults = resilienceManager.executeWithResilience(
                                provider.id.toString(),
                                { provider.searchNovels(query) }
                            )
                            
                            when (searchResults) {
                                is yokai.source.novel.providers.error.ResilienceResult.Success -> {
                                    ProviderSearchResult(provider.id.toString(), searchResults.data)
                                }
                                else -> {
                                    errors.add(ProviderSearchError(provider.id.toString(), "Search failed"))
                                    null
                                }
                            }
                        }
                    } catch (e: Exception) {
                            errorTracker.recordError(
                                provider.id.toString(),
                                NovelProviderError.UnknownError(e.message ?: "Search error"),
                                yokai.source.novel.providers.error.ErrorContext("search", query)
                            )
                        errors.add(ProviderSearchError(provider.id.toString(), e.message ?: "Unknown error"))
                        null
                    }
                }
            }.awaitAll().filterNotNull().forEach { results.add(it) }
        }
        
        return SearchAllResult(results, errors)
    }
    
    /**
     * Gets provider statistics
     */
    fun getProviderStatistics(): ProviderStatistics {
        val all = _providers.values
        val healthy = all.count { it.status == ProviderStatus.HEALTHY }
        val unhealthy = all.count { it.status == ProviderStatus.UNHEALTHY }
        val inactive = all.count { it.status == ProviderStatus.INACTIVE }
        
        return ProviderStatistics(
            totalProviders = all.size,
            healthyProviders = healthy,
            unhealthyProviders = unhealthy,
            inactiveProviders = inactive,
            languages = all.map { it.provider.lang }.distinct(),
            lastHealthCheck = all.maxOfOrNull { it.lastHealthCheck } ?: 0L
        )
    }
    
    /**
     * Forces health check for all providers
     */
    suspend fun forceHealthCheck() {
        _providers.values.forEach { entry ->
            checkProviderHealth(entry)
        }
        updateProviderStatus()
    }
    
    /**
     * Resets provider to healthy state (for recovery)
     */
    fun resetProvider(providerId: String) {
        _providers[providerId]?.let { entry ->
            _providers[providerId] = entry.copy(
                status = ProviderStatus.INACTIVE,
                errorCount = 0,
                lastHealthCheck = 0L
            )
            resilienceManager.resetProviderHealth(providerId)
        }
        updateProviderStatus()
    }
    
    /**
     * Gets detailed provider information
     */
    fun getProviderInfo(providerId: String): ProviderInfo? {
        val entry = _providers[providerId] ?: return null
        val provider = entry.provider
        val healthStatus = resilienceManager.getProviderHealth(providerId)
        val errorSummary = errorTracker.getProviderErrorSummary(providerId)
        
        return ProviderInfo(
            id = provider.id.toString(),
            name = provider.name,
            mainUrl = provider.mainUrl,
            lang = provider.lang,
            version = provider.version,
            capabilities = provider.capabilities,
            status = entry.status,
            healthStatus = healthStatus,
            errorSummary = errorSummary,
            registrationTime = entry.registrationTime,
            lastHealthCheck = entry.lastHealthCheck
        )
    }
    
    private fun registerBuiltInProviders() {
        // No built-in providers - all novel sources come from extensions
        // This ensures a clean extension-based architecture
        
        // Add more built-in providers here as they're implemented
        // registerProvider(NovelUpdatesProvider())
        // registerProvider(WebNovelProvider())
    }
    
    private fun startHealthMonitoring() {
        coroutineScope.launch {
            while (true) {
                try {
                    performHealthChecks()
                    delay(30000) // Check every 30 seconds
                } catch (e: Exception) {
                    // Log error but continue monitoring
                    delay(60000) // Wait longer on error
                }
            }
        }
    }
    
    private suspend fun performHealthChecks() {
        _providers.values.forEach { entry ->
            // Only check providers that haven't been checked recently
            if (System.currentTimeMillis() - entry.lastHealthCheck > 300000) { // 5 minutes
                checkProviderHealth(entry)
            }
        }
        updateProviderStatus()
    }
    
    private suspend fun checkProviderHealth(entry: ProviderEntry) {
        val provider = entry.provider
        val startTime = System.currentTimeMillis()
        
        try {
            // Perform a simple health check (basic search)
            val result = resilienceManager.executeWithResilience(
                provider.id.toString(),
                { provider.searchNovels("test", 1) },
                yokai.source.novel.providers.error.ResilienceConfig(timeoutMs = 10000L)
            )
            
            val newStatus = when (result) {
                is yokai.source.novel.providers.error.ResilienceResult.Success -> ProviderStatus.HEALTHY
                is yokai.source.novel.providers.error.ResilienceResult.CircuitBreakerOpen -> ProviderStatus.UNHEALTHY
                else -> ProviderStatus.DEGRADED
            }
            
            _providers[provider.id.toString()] = entry.copy(
                status = newStatus,
                lastHealthCheck = System.currentTimeMillis(),
                errorCount = if (newStatus == ProviderStatus.HEALTHY) 0 else entry.errorCount + 1
            )
            
        } catch (e: Exception) {
            _providers[provider.id.toString()] = entry.copy(
                status = ProviderStatus.UNHEALTHY,
                lastHealthCheck = System.currentTimeMillis(),
                errorCount = entry.errorCount + 1
            )
        }
    }
    
    private fun updateProviderStatus() {
        val statusMap = _providers.mapValues { it.value.status }
        _providerStatus.value = statusMap
        
        val healthy = _providers.values
            .filter { it.status == ProviderStatus.HEALTHY }
            .map { it.provider.id.toString() }
        _healthyProviders.value = healthy
    }
    
    private fun hasCapability(provider: NovelMainAPI, capability: ProviderCapability): Boolean {
        return when (capability) {
            ProviderCapability.SEARCH -> provider.capabilities.hasSearch
            ProviderCapability.BROWSE -> true // All providers support basic browsing
            ProviderCapability.LATEST -> provider.capabilities.hasLatestUpdates
            ProviderCapability.POPULAR -> provider.capabilities.hasPopular
            ProviderCapability.GENRE_FILTER -> provider.capabilities.hasGenreFilter
        }
    }
    
    fun destroy() {
        coroutineScope.cancel()
    }
}

/**
 * Provider registry entry
 */
data class ProviderEntry(
    val provider: NovelMainAPI,
    val status: ProviderStatus,
    val registrationTime: Long,
    val lastHealthCheck: Long,
    val errorCount: Int
)

/**
 * Provider status enumeration
 */
enum class ProviderStatus {
    HEALTHY,    // Working normally
    DEGRADED,   // Working but with issues
    UNHEALTHY,  // Not working
    INACTIVE    // Not yet tested
}

/**
 * Provider capabilities for filtering
 */
enum class ProviderCapability {
    SEARCH, BROWSE, LATEST, POPULAR, GENRE_FILTER
}

/**
 * Search all providers result
 */
data class SearchAllResult(
    val results: List<ProviderSearchResult>,
    val errors: List<ProviderSearchError>
)

data class ProviderSearchResult(
    val providerId: String,
    val results: List<NovelSearchResult>
)

data class ProviderSearchError(
    val providerId: String,
    val error: String
)

/**
 * Provider statistics
 */
data class ProviderStatistics(
    val totalProviders: Int,
    val healthyProviders: Int,
    val unhealthyProviders: Int,
    val inactiveProviders: Int,
    val languages: List<String>,
    val lastHealthCheck: Long
)

/**
 * Detailed provider information
 */
data class ProviderInfo(
    val id: String,
    val name: String,
    val mainUrl: String,
    val lang: String,
    val version: String,
    val capabilities: NovelProviderCapabilities,
    val status: ProviderStatus,
    val healthStatus: yokai.source.novel.providers.error.ProviderHealthStatus,
    val errorSummary: yokai.source.novel.providers.error.ProviderErrorSummary,
    val registrationTime: Long,
    val lastHealthCheck: Long
)