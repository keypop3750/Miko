package eu.kanade.tachiyomi.extension.novel.util

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import yokai.source.novel.NovelMainAPI
import yokai.source.novel.model.NovelChapter
import yokai.source.novel.model.NovelComment
import yokai.source.novel.model.NovelContent
import yokai.source.novel.model.NovelDetails
import yokai.source.novel.model.NovelFilter
import yokai.source.novel.model.NovelProviderCapabilities
import yokai.source.novel.model.NovelSearchResult
import yokai.source.novel.model.NovelStatus
import yokai.source.novel.network.NovelHttpClient
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adapter that wraps extension library's NovelSource to implement NovelMainAPI.
 * This allows extensions built with the yokai.extension.novel.lib library to work
 * with Miko's internal novel provider system.
 */
class NovelSourceAdapter(private val source: Any) : NovelMainAPI() {
    
    private var okHttpClient: OkHttpClient? = null
    
    override val id: Long
        get() = getSourceProperty("id") as? Long ?: 0L
    
    override val name: String
        get() = getSourceProperty("name") as? String ?: "Unknown"
    
    override val mainUrl: String
        get() = getSourceProperty("baseUrl") as? String ?: ""
    
    override val hasMainPage: Boolean
        get() = getSourceProperty("hasMainPage") as? Boolean ?: false
    
    override val lang: String
        get() = getSourceProperty("lang") as? String ?: "en"
    
    override val rateLimitTime: Long
        get() = getSourceProperty("rateLimitMs") as? Long ?: 1000L
    
    override val capabilities: NovelProviderCapabilities
        get() = getCapabilitiesFromExtension()
    
    /**
     * Read capabilities from the extension's getCapabilities() method.
     * Falls back to default capabilities if the method doesn't exist.
     */
    private fun getCapabilitiesFromExtension(): NovelProviderCapabilities {
        return try {
            val capabilitiesObj = getSourceProperty("capabilities")
            if (capabilitiesObj != null) {
                // Extension has a getCapabilities() method - read from it
                convertToNovelProviderCapabilities(capabilitiesObj)
            } else {
                // Try calling getCapabilities() method
                val getCapabilitiesMethod = source.javaClass.getMethod("getCapabilities")
                val extCapabilities = getCapabilitiesMethod.invoke(source)
                if (extCapabilities != null) {
                    convertToNovelProviderCapabilities(extCapabilities)
                } else {
                    NovelProviderCapabilities()
                }
            }
        } catch (e: Exception) {
            Logger.d { "[NOVEL_ADAPTER] No capabilities found for ${name}, using defaults" }
            NovelProviderCapabilities()
        }
    }
    
    /**
     * Convert extension's SourceCapabilities to NovelProviderCapabilities
     */
    private fun convertToNovelProviderCapabilities(extCapabilities: Any): NovelProviderCapabilities {
        return try {
            val clazz = extCapabilities.javaClass
            
            @Suppress("UNCHECKED_CAST")
            val supportedSorts = getFieldValue(extCapabilities, "supportedSorts") as? List<String> ?: listOf("popular")
            val supportsSortDirection = getFieldValue(extCapabilities, "supportsSortDirection") as? Boolean ?: true
            
            @Suppress("UNCHECKED_CAST")
            val supportedGenres = getFieldValue(extCapabilities, "supportedGenres") as? List<String> ?: emptyList()
            val supportsGenreExclusion = getFieldValue(extCapabilities, "supportsGenreExclusion") as? Boolean ?: false
            
            @Suppress("UNCHECKED_CAST")
            val supportedStatuses = getFieldValue(extCapabilities, "supportedStatuses") as? List<String> ?: emptyList()
            
            @Suppress("UNCHECKED_CAST")
            val supportedContentWarnings = getFieldValue(extCapabilities, "supportedContentWarnings") as? List<String> ?: emptyList()
            val supportsContentWarningExclusion = getFieldValue(extCapabilities, "supportsContentWarningExclusion") as? Boolean ?: false
            
            val supportsChapterCountFilter = getFieldValue(extCapabilities, "supportsChapterCountFilter") as? Boolean ?: false
            val supportsRatingFilter = getFieldValue(extCapabilities, "supportsRatingFilter") as? Boolean ?: false
            val supportsSearch = getFieldValue(extCapabilities, "supportsSearch") as? Boolean ?: true
            val supportsAuthorFilter = getFieldValue(extCapabilities, "supportsAuthorFilter") as? Boolean ?: false
            val supportsComments = getFieldValue(extCapabilities, "supportsComments") as? Boolean ?: false
            
            Logger.d { "[NOVEL_ADAPTER] Read capabilities for $name: sorts=$supportedSorts, genres=${supportedGenres.size}, contentWarnings=$supportedContentWarnings" }
            
            NovelProviderCapabilities(
                hasSearch = supportsSearch,
                hasLatestUpdates = hasMainPage,
                hasPopular = hasMainPage,
                supportedSorts = supportedSorts,
                supportsSortDirection = supportsSortDirection,
                supportedGenres = supportedGenres,
                supportsGenreExclusion = supportsGenreExclusion,
                supportedStatuses = supportedStatuses,
                supportedContentWarnings = supportedContentWarnings,
                supportsContentWarningExclusion = supportsContentWarningExclusion,
                supportsChapterCountFilter = supportsChapterCountFilter,
                supportsRatingFilter = supportsRatingFilter,
                supportsAuthorFilter = supportsAuthorFilter,
                supportsComments = supportsComments
            )
        } catch (e: Exception) {
            Logger.e(e) { "[NOVEL_ADAPTER] Error converting capabilities for $name" }
            NovelProviderCapabilities()
        }
    }
    
    private fun getFieldValue(obj: Any, fieldName: String): Any? {
        return try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(obj)
        } catch (e: NoSuchFieldException) {
            // Try getter method
            try {
                val getter = obj.javaClass.getMethod("get${fieldName.replaceFirstChar { it.uppercase() }}")
                getter.invoke(obj)
            } catch (e2: Exception) {
                // Try component method (for data classes)
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Inject HTTP client into the wrapped source.
     */
    fun injectHttpClient(client: OkHttpClient) {
        this.okHttpClient = client
        try {
            // Find the 'client' field in the class hierarchy (it's in NovelSource parent class)
            var clientField: java.lang.reflect.Field? = null
            var currentClass: Class<*>? = source.javaClass
            
            while (currentClass != null && clientField == null) {
                try {
                    clientField = currentClass.getDeclaredField("client")
                } catch (e: NoSuchFieldException) {
                    currentClass = currentClass.superclass
                }
            }
            
            if (clientField != null) {
                clientField.isAccessible = true
                clientField.set(source, client)
                Logger.d { "[NOVEL_ADAPTER] Injected OkHttpClient into ${source.javaClass.simpleName}.client" }
            } else {
                Logger.w { "[NOVEL_ADAPTER] Could not find 'client' field in ${source.javaClass.name} or its parents" }
            }
        } catch (e: Exception) {
            // Field not found or different type - this is OK
            Logger.w { "[NOVEL_ADAPTER] Could not inject OkHttpClient into ${source.javaClass.name}: ${e.message}" }
        }
    }
    
    override suspend fun searchNovels(query: String, page: Int): List<NovelSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val results = invokeSuspendListMethod("search", query, page)
                Logger.d { "[NOVEL_ADAPTER] search returned ${results.size} results for $name" }
                results.map { convertToNovelSearchResult(it) }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] Error in searchNovels for ${name}" }
                emptyList()
            }
        }
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        return withContext(Dispatchers.IO) {
            try {
                val result = invokeSuspendMethod("getNovelDetails", url)
                if (result != null) {
                    convertToNovelDetails(result)
                } else {
                    NovelDetails(
                        title = "Error",
                        url = url,
                        sourceId = id,
                        sourceName = name,
                        chapters = emptyList()
                    )
                }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] Error in getNovelDetails for ${name}" }
                NovelDetails(
                    title = "Error",
                    url = url,
                    sourceId = id,
                    sourceName = name,
                    chapters = emptyList()
                )
            }
        }
    }
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        return withContext(Dispatchers.IO) {
            try {
                val results = invokeSuspendListMethod("getChapterList", novelUrl)
                Logger.d { "[NOVEL_ADAPTER] getChapterList returned ${results.size} chapters for $name" }
                results.mapIndexed { index, chapter -> convertToNovelChapter(chapter, index) }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] Error in getChapterList for ${name}" }
                emptyList()
            }
        }
    }
    
    override suspend fun getNovelChapterContent(chapterUrl: String): NovelContent {
        return withContext(Dispatchers.IO) {
            try {
                val result = invokeSuspendMethod("getChapterContent", chapterUrl)
                when (result) {
                    is String -> NovelContent(chapterUrl = chapterUrl, content = result)
                    null -> NovelContent(chapterUrl = chapterUrl, content = "Error: no content returned")
                    else -> convertToNovelContent(result, chapterUrl)
                }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] Error in getNovelChapterContent for ${name}" }
                NovelContent(chapterUrl = chapterUrl, content = "Error loading chapter content: ${e.message}")
            }
        }
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val results = invokeSuspendListMethod("getLatestUpdates", page)
                Logger.d { "[NOVEL_ADAPTER] getLatestUpdates returned ${results.size} results for $name" }
                results.map { convertToNovelSearchResult(it) }
            } catch (e: Exception) {
                Logger.d { "[NOVEL_ADAPTER] getLatestUpdates not available for ${name}: ${e.message}" }
                emptyList()
            }
        }
    }
    
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                val results = invokeSuspendListMethodWithInt("getPopularNovels", page)
                Logger.d { "[NOVEL_ADAPTER] getPopularNovels returned ${results.size} results for $name" }
                results.map { convertToNovelSearchResult(it) }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] getPopularNovels error for ${name}: ${e.message}" }
                emptyList()
            }
        }
    }
    
    override suspend fun getChapterComments(chapterUrl: String): List<yokai.source.novel.model.NovelComment> {
        return withContext(Dispatchers.IO) {
            try {
                val results = invokeSuspendListMethod("getChapterComments", chapterUrl)
                Logger.d { "[NOVEL_ADAPTER] getChapterComments returned ${results.size} comments for $name" }
                results.map { convertToNovelComment(it) }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] Error in getChapterComments for ${name}" }
                emptyList()
            }
        }
    }
    
    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d { "[NOVEL_ADAPTER] getBrowseNovels called for $name with filters: $filters" }
                val results = invokeSuspendBrowseMethod("getBrowseNovels", page, filters)
                Logger.d { "[NOVEL_ADAPTER] getBrowseNovels returned ${results.size} results for $name" }
                results.map { convertToNovelSearchResult(it) }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] getBrowseNovels error for ${name}: ${e.message}" }
                // Fall back to getPopularNovels if getBrowseNovels is not implemented
                getPopularNovels(page)
            }
        }
    }
    
    /**
     * Invokes the getBrowseNovels suspend function with Int and Map parameters.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun invokeSuspendBrowseMethod(methodName: String, page: Int, filters: Map<String, String>): List<Any> {
        return suspendCancellableCoroutine { cont ->
            try {
                // Log all methods matching this name to debug
                Logger.d { "[NOVEL_ADAPTER] Looking for $methodName in ${source.javaClass.name}" }
                
                val matchingMethods = source.javaClass.methods.filter { it.name == methodName }
                Logger.d { "[NOVEL_ADAPTER] Found ${matchingMethods.size} methods named $methodName" }
                matchingMethods.forEach { m ->
                    Logger.d { "[NOVEL_ADAPTER]   Method: ${m.name}(${m.parameterTypes.joinToString(", ") { it.name }}) paramCount=${m.parameterCount}" }
                }
                
                // Also log methods that might be related (contains browse)
                val browseMethods = source.javaClass.methods.filter { 
                    it.name.contains("browse", ignoreCase = true) || it.name.contains("Browse") 
                }
                Logger.d { "[NOVEL_ADAPTER] Found ${browseMethods.size} methods containing 'browse'" }
                browseMethods.forEach { m ->
                    Logger.d { "[NOVEL_ADAPTER]   Browse method: ${m.name}(${m.parameterTypes.joinToString(", ") { it.simpleName }})" }
                }
                
                // Find suspend method with Int, Map, and Continuation parameters
                // Be more flexible with Map matching (could be Map, HashMap, LinkedHashMap, etc.)
                val method = source.javaClass.methods.find { m ->
                    m.name == methodName &&
                    m.parameterCount == 3 &&
                    (m.parameterTypes[0] == Int::class.javaPrimitiveType || m.parameterTypes[0] == Int::class.java) &&
                    Map::class.java.isAssignableFrom(m.parameterTypes[1]) &&
                    m.parameterTypes[2].name.contains("Continuation")
                }
                
                if (method == null) {
                    Logger.w { "[NOVEL_ADAPTER] Could not find suspend method $methodName(int, Map, Continuation) for ${name}" }
                    // Try alternative: look for method by parameter count and name only
                    val altMethod = source.javaClass.methods.find { m ->
                        m.name == methodName && m.parameterCount == 3
                    }
                    if (altMethod != null) {
                        Logger.d { "[NOVEL_ADAPTER] Found alt method with 3 params: ${altMethod.parameterTypes.joinToString(", ") { it.name }}" }
                    }
                    cont.resume(emptyList())
                    return@suspendCancellableCoroutine
                }
                
                Logger.d { "[NOVEL_ADAPTER] Invoking $methodName($page, ${filters.size} filters, Continuation)" }
                Logger.d { "[NOVEL_ADAPTER] Method signature: ${method.parameterTypes.joinToString(", ") { it.name }}" }
                
                // Create a continuation wrapper
                val continuation = object : Continuation<Any?> {
                    override val context: CoroutineContext = cont.context
                    
                    override fun resumeWith(result: Result<Any?>) {
                        Logger.d { "[NOVEL_ADAPTER] Continuation.resumeWith called for $methodName" }
                        result.fold(
                            onSuccess = { value ->
                                Logger.d { "[NOVEL_ADAPTER] $methodName success, value type: ${value?.javaClass?.name}" }
                                val list = value as? List<Any> ?: emptyList()
                                Logger.d { "[NOVEL_ADAPTER] $methodName completed with ${list.size} items" }
                                if (!cont.isCompleted) {
                                    cont.resume(list)
                                }
                            },
                            onFailure = { e ->
                                Logger.e(e) { "[NOVEL_ADAPTER] $methodName failed" }
                                if (!cont.isCompleted) {
                                    cont.resumeWithException(e)
                                }
                            }
                        )
                    }
                }
                
                val result = method.invoke(source, page, filters, continuation)
                Logger.d { "[NOVEL_ADAPTER] $methodName invoke returned: ${result?.javaClass?.name ?: "null"}, isSuspended=${result === COROUTINE_SUSPENDED}" }
                
                // If the method doesn't suspend (returns immediately), handle the result
                if (result !== COROUTINE_SUSPENDED && !cont.isCompleted) {
                    val list = result as? List<Any> ?: emptyList()
                    Logger.d { "[NOVEL_ADAPTER] $methodName returned immediately with ${list.size} items" }
                    cont.resume(list)
                }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] Error invoking $methodName" }
                if (!cont.isCompleted) {
                    cont.resumeWithException(e)
                }
            }
        }
    }

    /**
     * Invokes a suspend function via reflection using a proper Continuation.
     * This handles the COROUTINE_SUSPENDED marker correctly.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun invokeSuspendListMethodWithInt(methodName: String, page: Int): List<Any> {
        return suspendCancellableCoroutine { cont ->
            try {
                // Log all available methods for debugging
                Logger.d { "[NOVEL_ADAPTER] Source class: ${source.javaClass.name}" }
                Logger.d { "[NOVEL_ADAPTER] Available methods:" }
                source.javaClass.methods.filter { it.name.contains(methodName, ignoreCase = true) || it.name == methodName }.forEach { m ->
                    Logger.d { "[NOVEL_ADAPTER]   - ${m.name}(${m.parameterTypes.joinToString(", ") { it.name }}) -> ${m.returnType.name}" }
                }
                
                // Find suspend method with Int parameter and Continuation
                val method = source.javaClass.methods.find { m ->
                    m.name == methodName &&
                    m.parameterCount == 2 &&
                    (m.parameterTypes[0] == Int::class.javaPrimitiveType || m.parameterTypes[0] == Int::class.java) &&
                    m.parameterTypes[1].name.contains("Continuation")
                }
                
                if (method == null) {
                    Logger.e { "[NOVEL_ADAPTER] Could not find suspend method $methodName(int, Continuation)" }
                    // Try to find any method with this name
                    val anyMethod = source.javaClass.methods.find { it.name == methodName }
                    if (anyMethod != null) {
                        Logger.d { "[NOVEL_ADAPTER] Found method with name $methodName: ${anyMethod.parameterTypes.joinToString(", ") { it.name }}" }
                    }
                    cont.resume(emptyList())
                    return@suspendCancellableCoroutine
                }
                
                Logger.d { "[NOVEL_ADAPTER] Invoking $methodName(${page}, Continuation)" }
                
                // Check if HTTP client was injected
                try {
                    var clientField: java.lang.reflect.Field? = null
                    var currentClass: Class<*>? = source.javaClass
                    while (currentClass != null && clientField == null) {
                        try {
                            clientField = currentClass.getDeclaredField("client")
                        } catch (e: NoSuchFieldException) {
                            currentClass = currentClass.superclass
                        }
                    }
                    if (clientField != null) {
                        clientField.isAccessible = true
                        val client = clientField.get(source)
                        Logger.d { "[NOVEL_ADAPTER] HTTP client check: ${if (client != null) "INITIALIZED" else "NULL - NOT INJECTED!"}" }
                    } else {
                        Logger.w { "[NOVEL_ADAPTER] Could not find 'client' field to verify injection" }
                    }
                } catch (e: Exception) {
                    Logger.e(e) { "[NOVEL_ADAPTER] Error checking HTTP client" }
                }
                
                // Create a continuation wrapper that will receive the result
                val continuation = object : Continuation<Any?> {
                    override val context: CoroutineContext = cont.context
                    
                    override fun resumeWith(result: Result<Any?>) {
                        Logger.d { "[NOVEL_ADAPTER] Continuation.resumeWith called for $methodName" }
                        result.fold(
                            onSuccess = { value ->
                                Logger.d { "[NOVEL_ADAPTER] $methodName success, value type: ${value?.javaClass?.name}" }
                                val list = value as? List<Any> ?: emptyList()
                                Logger.d { "[NOVEL_ADAPTER] $methodName completed with ${list.size} items" }
                                if (!cont.isCompleted) {
                                    cont.resume(list)
                                }
                            },
                            onFailure = { error ->
                                Logger.e(error) { "[NOVEL_ADAPTER] $methodName failed in continuation" }
                                if (!cont.isCompleted) {
                                    cont.resume(emptyList())
                                }
                            }
                        )
                    }
                }
                
                // Invoke the suspend function
                val result = method.invoke(source, page, continuation)
                
                Logger.d { "[NOVEL_ADAPTER] method.invoke returned: ${result?.javaClass?.name}, isSuspended=${result === COROUTINE_SUSPENDED}" }
                
                // Check if the result is the COROUTINE_SUSPENDED marker
                // The suspend function returns COROUTINE_SUSPENDED when it suspends
                // Otherwise it returns the actual result directly
                if (result === COROUTINE_SUSPENDED) {
                    Logger.d { "[NOVEL_ADAPTER] $methodName suspended, waiting for continuation callback" }
                    // Do nothing - the continuation will be called later
                } else {
                    // Direct result - handle it
                    val list = result as? List<Any> ?: emptyList()
                    Logger.d { "[NOVEL_ADAPTER] $methodName returned immediately with ${list.size} items" }
                    if (!cont.isCompleted) {
                        cont.resume(list)
                    }
                }
            } catch (e: java.lang.reflect.InvocationTargetException) {
                // Log the actual underlying exception, not just the wrapper
                val actualException = e.targetException ?: e.cause ?: e
                Logger.e(actualException) { "[NOVEL_ADAPTER] InvocationTargetException in $methodName: ${actualException.message}" }
                Logger.e { "[NOVEL_ADAPTER] Full stack trace: ${actualException.stackTraceToString()}" }
                if (!cont.isCompleted) {
                    cont.resume(emptyList())
                }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] Error invoking $methodName: ${e.message}" }
                Logger.e { "[NOVEL_ADAPTER] Full stack trace: ${e.stackTraceToString()}" }
                if (!cont.isCompleted) {
                    cont.resumeWithException(e)
                }
            }
        }
    }
    
    /**
     * Generic helper to invoke a suspend method with a single String argument and return the result.
     * Uses suspendCancellableCoroutine to properly handle Kotlin suspend functions.
     */
    private suspend fun invokeSuspendMethod(methodName: String, arg: String): Any? {
        return suspendCancellableCoroutine { cont ->
            try {
                // Find method with Continuation parameter (suspend function)
                val method = source.javaClass.methods.find { m ->
                    m.name == methodName && 
                    m.parameterCount == 2 &&
                    m.parameterTypes[0] == String::class.java &&
                    m.parameterTypes[1].name.contains("Continuation")
                }
                
                if (method == null) {
                    // Try regular method without Continuation
                    val regularMethod = try {
                        source.javaClass.getMethod(methodName, String::class.java)
                    } catch (e: Exception) {
                        Logger.e { "[NOVEL_ADAPTER] Could not find method $methodName(String)" }
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    Logger.d { "[NOVEL_ADAPTER] Invoking regular method $methodName(String)" }
                    val result = regularMethod.invoke(source, arg)
                    cont.resume(result)
                    return@suspendCancellableCoroutine
                }
                
                Logger.d { "[NOVEL_ADAPTER] Invoking suspend method $methodName(String, Continuation)" }
                
                val continuation = object : Continuation<Any?> {
                    override val context: CoroutineContext = cont.context
                    override fun resumeWith(result: Result<Any?>) {
                        result.fold(
                            onSuccess = { cont.resume(it) },
                            onFailure = { cont.resumeWithException(it) }
                        )
                    }
                }
                
                val result = method.invoke(source, arg, continuation)
                if (result !== COROUTINE_SUSPENDED) {
                    cont.resume(result)
                }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] Failed to invoke $methodName: ${e.message}" }
                cont.resume(null)
            }
        }
    }
    
    /**
     * Generic helper to invoke a suspend method that returns a List.
     * Uses suspendCancellableCoroutine to properly handle Kotlin suspend functions.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun invokeSuspendListMethod(methodName: String, arg: Any): List<Any> {
        return suspendCancellableCoroutine { cont ->
            try {
                // Find method with Continuation parameter (suspend function)
                val methods = source.javaClass.methods.filter { m ->
                    m.name == methodName && m.parameterTypes.any { it.name.contains("Continuation") }
                }
                
                val method = methods.find { m ->
                    when (arg) {
                        is Int -> m.parameterTypes[0] == Int::class.javaPrimitiveType || m.parameterTypes[0] == Int::class.java
                        is String -> m.parameterTypes[0] == String::class.java
                        else -> true
                    }
                }
                
                if (method == null) {
                    // Try regular method
                    val paramType = when (arg) {
                        is Int -> Int::class.javaPrimitiveType
                        is String -> String::class.java
                        else -> arg.javaClass
                    }
                    val regularMethod = try {
                        source.javaClass.getMethod(methodName, paramType)
                    } catch (e: NoSuchMethodException) {
                        if (arg is Int) {
                            source.javaClass.getMethod(methodName, Int::class.java)
                        } else {
                            Logger.e { "[NOVEL_ADAPTER] Could not find method $methodName" }
                            cont.resume(emptyList())
                            return@suspendCancellableCoroutine
                        }
                    }
                    Logger.d { "[NOVEL_ADAPTER] Invoking regular method $methodName" }
                    val result = regularMethod.invoke(source, arg) as? List<Any> ?: emptyList()
                    cont.resume(result)
                    return@suspendCancellableCoroutine
                }
                
                Logger.d { "[NOVEL_ADAPTER] Invoking suspend method $methodName" }
                
                val continuation = object : Continuation<Any?> {
                    override val context: CoroutineContext = cont.context
                    override fun resumeWith(result: Result<Any?>) {
                        result.fold(
                            onSuccess = { value ->
                                val list = value as? List<Any> ?: emptyList()
                                cont.resume(list)
                            },
                            onFailure = { cont.resume(emptyList()) }
                        )
                    }
                }
                
                val result = method.invoke(source, arg, continuation)
                if (result !== COROUTINE_SUSPENDED) {
                    val list = result as? List<Any> ?: emptyList()
                    cont.resume(list)
                }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] Failed to invoke $methodName: ${e.message}" }
                cont.resume(emptyList())
            }
        }
    }
    
    /**
     * Generic helper to invoke a suspend method with two arguments (String, Int) that returns a List.
     * Uses suspendCancellableCoroutine to properly handle Kotlin suspend functions.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun invokeSuspendListMethod(methodName: String, arg1: String, arg2: Int): List<Any> {
        return suspendCancellableCoroutine { cont ->
            try {
                // Find suspend method
                val method = source.javaClass.methods.find { m ->
                    m.name == methodName && 
                    m.parameterCount == 3 &&
                    m.parameterTypes[0] == String::class.java &&
                    (m.parameterTypes[1] == Int::class.javaPrimitiveType || m.parameterTypes[1] == Int::class.java) &&
                    m.parameterTypes[2].name.contains("Continuation")
                }
                
                if (method == null) {
                    // Try regular method
                    val regularMethod = try {
                        source.javaClass.getMethod(methodName, String::class.java, Int::class.javaPrimitiveType)
                    } catch (e: NoSuchMethodException) {
                        try {
                            source.javaClass.getMethod(methodName, String::class.java, Int::class.java)
                        } catch (e2: NoSuchMethodException) {
                            Logger.e { "[NOVEL_ADAPTER] Could not find method $methodName(String, Int)" }
                            cont.resume(emptyList())
                            return@suspendCancellableCoroutine
                        }
                    }
                    Logger.d { "[NOVEL_ADAPTER] Invoking regular method $methodName(String, Int)" }
                    val result = regularMethod.invoke(source, arg1, arg2) as? List<Any> ?: emptyList()
                    cont.resume(result)
                    return@suspendCancellableCoroutine
                }
                
                Logger.d { "[NOVEL_ADAPTER] Invoking suspend method $methodName(String, Int, Continuation)" }
                
                val continuation = object : Continuation<Any?> {
                    override val context: CoroutineContext = cont.context
                    override fun resumeWith(result: Result<Any?>) {
                        result.fold(
                            onSuccess = { value ->
                                val list = value as? List<Any> ?: emptyList()
                                cont.resume(list)
                            },
                            onFailure = { cont.resume(emptyList()) }
                        )
                    }
                }
                
                val result = method.invoke(source, arg1, arg2, continuation)
                if (result !== COROUTINE_SUSPENDED) {
                    val list = result as? List<Any> ?: emptyList()
                    cont.resume(list)
                }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_ADAPTER] Failed to invoke $methodName: ${e.message}" }
                cont.resume(emptyList())
            }
        }
    }
    
    // Helper methods to convert extension lib types to Miko internal types
    
    private fun getSourceProperty(name: String): Any? {
        return try {
            val field = source.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.get(source)
        } catch (e: NoSuchFieldException) {
            try {
                // Try getter method
                val getter = source.javaClass.getMethod("get${name.replaceFirstChar { it.uppercase() }}")
                getter.invoke(source)
            } catch (e2: Exception) {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun convertToNovelSearchResult(obj: Any): NovelSearchResult {
        val title = getProperty(obj, "title") as? String ?: ""
        val url = getProperty(obj, "url") as? String ?: ""
        val coverUrl = getProperty(obj, "coverUrl") as? String
        val author = getProperty(obj, "author") as? String
        
        return NovelSearchResult(
            title = title,
            url = url,
            thumbnailUrl = coverUrl,
            author = author,
            sourceId = id,
            sourceName = name
        )
    }
    
    private fun convertToNovelDetails(obj: Any): NovelDetails {
        val title = getProperty(obj, "title") as? String ?: ""
        val url = getProperty(obj, "url") as? String ?: ""
        val author = getProperty(obj, "author") as? String
        val description = getProperty(obj, "description") as? String
        val coverUrl = getProperty(obj, "coverUrl") as? String
        @Suppress("UNCHECKED_CAST")
        val genres = getProperty(obj, "genres") as? List<String> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val tags = getProperty(obj, "tags") as? List<String> ?: emptyList()
        val statusObj = getProperty(obj, "status")
        val status = convertStatus(statusObj)
        
        return NovelDetails(
            title = title,
            url = url,
            author = author,
            description = description,
            thumbnailUrl = coverUrl,
            genres = genres,
            tags = tags,
            status = status.value,
            sourceId = id,
            sourceName = name,
            chapters = emptyList() // Chapters loaded separately
        )
    }
    
    private fun convertToNovelChapter(obj: Any, index: Int): NovelChapter {
        val title = getProperty(obj, "name") as? String 
            ?: getProperty(obj, "title") as? String 
            ?: "Chapter ${index + 1}"
        val url = getProperty(obj, "url") as? String ?: ""
        val dateUpload = getProperty(obj, "dateUpload") as? Long ?: 0L
        val chapterNumber = getProperty(obj, "chapterNumber") as? Float ?: (index + 1).toFloat()
        val scanlator = getProperty(obj, "scanlator") as? String
        
        return NovelChapter(
            title = title,
            url = url,
            dateUpload = dateUpload,
            chapterNumber = chapterNumber,
            scanlator = scanlator,
            sourceOrder = index
        )
    }
    
    private fun convertToNovelContent(obj: Any, chapterUrl: String): NovelContent {
        val content = getProperty(obj, "content") as? String ?: ""
        val title = getProperty(obj, "title") as? String
        val nextChapterUrl = getProperty(obj, "nextChapterUrl") as? String
        val prevChapterUrl = getProperty(obj, "prevChapterUrl") as? String
        
        return NovelContent(
            chapterUrl = chapterUrl,
            content = content,
            title = title,
            nextChapterUrl = nextChapterUrl,
            previousChapterUrl = prevChapterUrl
        )
    }
    
    private fun convertToNovelComment(obj: Any): yokai.source.novel.model.NovelComment {
        val id = getProperty(obj, "id") as? String ?: ""
        val userName = getProperty(obj, "userName") as? String ?: "Anonymous"
        val avatarUrl = getProperty(obj, "avatarUrl") as? String
        val content = getProperty(obj, "content") as? String ?: ""
        val likes = (getProperty(obj, "likes") as? Number)?.toInt() ?: 0
        val replyCount = (getProperty(obj, "replyCount") as? Number)?.toInt() ?: 0
        val date = (getProperty(obj, "date") as? Number)?.toLong() ?: 0L
        
        @Suppress("UNCHECKED_CAST")
        val replies = (getProperty(obj, "replies") as? List<Any>)?.map { convertToNovelComment(it) } ?: emptyList()
        
        return yokai.source.novel.model.NovelComment(
            id = id,
            userName = userName,
            avatarUrl = avatarUrl,
            content = content,
            likes = likes,
            replyCount = replyCount,
            date = date,
            replies = replies
        )
    }
    
    private fun convertStatus(statusObj: Any?): NovelStatus {
        if (statusObj == null) return NovelStatus.UNKNOWN
        
        val statusName = statusObj.toString().uppercase()
        return when {
            statusName.contains("ONGOING") -> NovelStatus.ONGOING
            statusName.contains("COMPLETED") || statusName.contains("COMPLETE") -> NovelStatus.COMPLETED
            statusName.contains("HIATUS") -> NovelStatus.HIATUS
            statusName.contains("CANCELLED") || statusName.contains("CANCELED") -> NovelStatus.CANCELLED
            statusName.contains("LICENSED") -> NovelStatus.LICENSED
            else -> NovelStatus.UNKNOWN
        }
    }
    
    private fun getProperty(obj: Any, name: String): Any? {
        return try {
            val field = obj.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.get(obj)
        } catch (e: NoSuchFieldException) {
            try {
                val getter = obj.javaClass.getMethod("get${name.replaceFirstChar { it.uppercase() }}")
                getter.invoke(obj)
            } catch (e2: Exception) {
                // Try component accessor for data classes
                try {
                    val component = obj.javaClass.methods.find { 
                        it.name.startsWith("component") && it.parameterCount == 0 
                    }
                    component?.invoke(obj)
                } catch (e3: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
