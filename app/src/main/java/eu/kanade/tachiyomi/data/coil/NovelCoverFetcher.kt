package eu.kanade.tachiyomi.data.coil

import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.File
import java.net.HttpURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.sink
import okio.source
import yokai.domain.novel.Novel

/**
 * Fetcher for Novel cover images.
 * Handles loading from URL with network caching and disk cache support.
 */
class NovelCoverFetcher(
    private val url: String?,
    private val sourceLazy: Lazy<HttpSource?>,
    private val options: Options,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val diskCacheKeyLazy: Lazy<String>,
    private val imageLoader: ImageLoader,
) : Fetcher {

    private val diskCacheKey: String
        get() = diskCacheKeyLazy.value

    private val fileScope = CoroutineScope(Job() + Dispatchers.IO)

    override suspend fun fetch(): FetchResult {
        if (url == null) error("No cover specified")
        
        return when (getResourceType(url)) {
            Type.URL -> httpLoader()
            Type.File -> {
                val file = File(url.substringAfter("file://"))
                fileLoader(file)
            }
            Type.URI -> fileUriLoader(url)
            null -> error("Invalid image")
        }
    }

    private fun fileLoader(file: File): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
                diskCacheKey = diskCacheKey,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private fun fileUriLoader(uri: String): FetchResult {
        val source = UniFile.fromUri(options.context, uri.toUri())!!
            .openInputStream()
            .source()
            .buffer()
        return SourceFetchResult(
            source = ImageSource(source = source, fileSystem = FileSystem.SYSTEM),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun httpLoader(): FetchResult {
        var snapshot = readFromDiskCache()
        try {
            // Fetch from disk cache
            if (snapshot != null) {
                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK,
                )
            }

            // Fetch from network
            val response = executeNetworkRequest()
            val responseBody = checkNotNull(response.body) { "Null response source" }
            try {
                // Read from disk cache
                snapshot = writeToDiskCache(response)
                if (snapshot != null) {
                    return SourceFetchResult(
                        source = snapshot.toImageSource(),
                        mimeType = "image/*",
                        dataSource = DataSource.NETWORK,
                    )
                }

                // Read from response if cache is unused or unusable
                return SourceFetchResult(
                    source = ImageSource(source = responseBody.source(), fileSystem = FileSystem.SYSTEM),
                    mimeType = "image/*",
                    dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
                )
            } catch (e: Exception) {
                responseBody.close()
                throw e
            }
        } catch (e: Exception) {
            snapshot?.close()
            throw e
        }
    }

    private suspend fun executeNetworkRequest(): Response {
        val client = sourceLazy.value?.client ?: callFactoryLazy.value
        val response = client.newCall(newRequest()).await()
        if (!response.isSuccessful && response.code != HttpURLConnection.HTTP_NOT_MODIFIED) {
            response.close()
            throw IOException(response.message)
        }
        return response
    }

    private fun newRequest(): Request {
        val request = Request.Builder().apply {
            url(url!!)

            val sourceHeaders = sourceLazy.value?.headers
            if (sourceHeaders != null) {
                headers(sourceHeaders)
            }

            when {
                options.networkCachePolicy.readEnabled -> {
                    // don't take up okhttp cache
                    cacheControl(CacheControl.FORCE_NETWORK)
                }
                else -> {
                    cacheControl(CacheControl.FORCE_NETWORK)
                }
            }
        }

        return request.build()
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? {
        return if (options.diskCachePolicy.readEnabled) {
            imageLoader.diskCache?.openSnapshot(diskCacheKey)
        } else {
            null
        }
    }

    private fun writeToDiskCache(response: Response): DiskCache.Snapshot? {
        if (!options.diskCachePolicy.writeEnabled) {
            return null
        }
        val editor = imageLoader.diskCache?.openEditor(diskCacheKey) ?: return null
        try {
            editor.data.toFile().sink().buffer().use { sink ->
                response.body.source().use { source ->
                    sink.writeAll(source)
                }
            }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            editor.abort()
            throw e
        }
    }

    private fun DiskCache.Snapshot.toImageSource(): ImageSource {
        return ImageSource(
            file = data,
            fileSystem = FileSystem.SYSTEM,
            diskCacheKey = diskCacheKey,
            closeable = this,
        )
    }

    private fun getResourceType(cover: String?): Type? {
        return when {
            cover.isNullOrEmpty() -> null
            cover.startsWith("http", true) || cover.startsWith("Custom-", true) -> Type.URL
            cover.startsWith("content") -> Type.URI
            cover.startsWith("/") || cover.startsWith("file://") -> Type.File
            else -> null
        }
    }

    private enum class Type {
        File, URL, URI
    }

    class NovelFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<Novel> {

        override fun create(data: Novel, options: Options, imageLoader: ImageLoader): Fetcher {
            return NovelCoverFetcher(
                url = data.posterUrl,
                sourceLazy = lazy { null }, // Novels don't use HttpSource
                options = options,
                callFactoryLazy = callFactoryLazy,
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                imageLoader = imageLoader,
            )
        }
    }
}
