package yokai.source.novel.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

/**
 * HTTP client wrapper for novel providers
 * Provides QuickNovel-style extension functions for network operations
 */
class NovelHttpClient(private val client: OkHttpClient) {

    /**
     * Perform a GET request and return the response
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeout: Long? = null
    ): Response = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)
            .headers(Headers.headersOf(*headers.flatMap { listOf(it.key, it.value) }.toTypedArray()))

        val request = requestBuilder.build()
        
        try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw NetworkException("Failed to fetch: $url", e)
        }
    }

    /**
     * Perform a GET request and parse as Jsoup Document
     */
    suspend fun getDocument(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): Document = withContext(Dispatchers.IO) {
        val response = get(url, headers)
        
        if (!response.isSuccessful) {
            throw NetworkException("HTTP ${response.code} for $url")
        }
        
        val body = response.body?.string() 
            ?: throw NetworkException("Empty response body for $url")
        
        try {
            Jsoup.parse(body, url)
        } catch (e: Exception) {
            throw ParseException("Failed to parse HTML from $url", e)
        }
    }

    /**
     * Perform a GET request and return response body as string
     */
    suspend fun getString(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val response = get(url, headers)
        
        if (!response.isSuccessful) {
            throw NetworkException("HTTP ${response.code} for $url")
        }
        
        response.body?.string() 
            ?: throw NetworkException("Empty response body for $url")
    }

    /**
     * Check if a URL is accessible (returns 200 OK)
     */
    suspend fun check(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = get(url)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Exception thrown when network operations fail
 */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when HTML parsing fails
 */
class ParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Extension function to create NovelHttpClient from OkHttpClient
 */
fun OkHttpClient.asNovelClient(): NovelHttpClient = NovelHttpClient(this)
