package yokai.source.novel

import okhttp3.OkHttpClient
import yokai.source.novel.network.NovelHttpClient

/**
 * Dynamic registry for novel providers.
 * 
 * Sources come from installed novel extensions (APKs) - managed by NovelExtensionManager.
 * All novel sources are provided via extensions to maintain a clean architecture.
 */
object NovelProviderRegistry {
    
    /**
     * Lazy-initialized HTTP client for novel providers.
     * Accessed via Koin bridge to get NetworkHelper's OkHttpClient.
     */
    private val httpClient: NovelHttpClient by lazy {
        NovelHttpClient(getOkHttpClientFromKoin())
    }
    
    /**
     * Callback to get sources from NovelExtensionManager.
     * Set by the app layer during initialization.
     */
    private var extensionSourcesProvider: (() -> List<NovelMainAPI>)? = null
    
    /**
     * Register the extension sources provider.
     * Called by the app during initialization to connect to NovelExtensionManager.
     */
    fun registerExtensionSourcesProvider(provider: () -> List<NovelMainAPI>) {
        extensionSourcesProvider = provider
    }
    
    /**
     * Get all available novel providers from installed extensions.
     */
    val providers: List<NovelMainAPI>
        get() {
            val extensionSources = extensionSourcesProvider?.invoke() ?: emptyList()
            return extensionSources.sortedBy { it.name }
        }
    
    /**
     * Get all registered novel providers.
     */
    fun getAllProviders(): List<NovelMainAPI> = providers
    
    /**
     * Get provider by unique source ID (must be >= 6000).
     */
    fun getProvider(id: Long): NovelMainAPI? = providers.find { it.id == id }
    
    /**
     * Get provider by name.
     */
    fun getProviderByName(name: String): NovelMainAPI? = providers.find { it.name == name }
    
    /**
     * Get all providers for a specific language code.
     */
    fun getProvidersByLanguage(lang: String): List<NovelMainAPI> = 
        providers.filter { it.lang == lang }
    
    /**
     * Check if any extension sources are installed.
     */
    fun hasExtensionSources(): Boolean {
        val extensionSources = extensionSourcesProvider?.invoke() ?: emptyList()
        return extensionSources.isNotEmpty()
    }
    
    /**
     * Get count of available sources.
     */
    fun getSourceCount(): Int = providers.size
    
    /**
     * Bridge to access OkHttpClient from Koin-registered NetworkHelper.
     * This is the ONLY cross-DI bridge point needed.
     */
    private fun getOkHttpClientFromKoin(): OkHttpClient {
        return try {
            // Access Koin's NetworkHelper to get the OkHttpClient
            val networkHelper = org.koin.java.KoinJavaComponent.get<Any>(
                Class.forName("eu.kanade.tachiyomi.network.NetworkHelper")
            )
            
            // Use reflection to get the 'client' property
            val clientField = networkHelper.javaClass.getDeclaredField("client")
            clientField.isAccessible = true
            clientField.get(networkHelper) as OkHttpClient
        } catch (e: Exception) {
            // Fallback: create basic OkHttpClient if bridge fails
            println("Warning: Failed to get OkHttpClient from Koin, using default: ${e.message}")
            OkHttpClient.Builder().build()
        }
    }
}