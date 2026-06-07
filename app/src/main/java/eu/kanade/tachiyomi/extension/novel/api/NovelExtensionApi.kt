package eu.kanade.tachiyomi.extension.novel.api

import android.content.Context
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelLoadResult
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.extension.repo.interactor.GetExtensionRepo
import yokai.domain.extension.repo.model.ExtensionRepo

/**
 * API for fetching novel extensions from repos.
 * 
 * Uses the SAME extension repo system as manga extensions.
 * Users add repos via Settings -> Browse -> Extension Repos.
 * The app will fetch from all registered repos that contain novel extensions.
 */
internal class NovelExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val getExtensionRepo: GetExtensionRepo by injectLazy()

    /**
     * Find all available novel extensions from all registered repos.
     * Fetches from the same repos as manga extensions - repos can contain both.
     */
    suspend fun findExtensions(): List<NovelExtension.Available> {
        return withIOContext {
            // Get all registered extension repos (same system as manga)
            val repos = getExtensionRepo.getAll()
            
            if (repos.isEmpty()) {
                Logger.d { "No extension repos registered. Users need to add a repo first." }
                return@withIOContext emptyList()
            }
            
            // Fetch novel extensions from all repos in parallel
            repos.map { repo ->
                async {
                    try {
                        getNovelExtensions(repo.baseUrl)
                    } catch (e: Exception) {
                        Logger.e(e) { "Failed to get novel extensions from ${repo.baseUrl}" }
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
    }

    /**
     * Fetch novel extensions from a specific repo URL.
     */
    private suspend fun getNovelExtensions(repoBaseUrl: String): List<NovelExtension.Available> {
        return try {
            val response = networkService.client
                .newCall(GET("$repoBaseUrl/index.min.json"))
                .awaitSuccess()

            response
                .parseAs<List<NovelExtensionJsonObject>>()
                .toNovelExtensions(repoBaseUrl)
        } catch (e: Throwable) {
            Logger.e(e) { "Failed to get novel extensions from $repoBaseUrl" }
            emptyList()
        }
    }

    /**
     * Check for updates to installed novel extensions.
     */
    suspend fun checkForUpdates(
        context: Context,
        prefetchedExtensions: List<NovelExtension.Available>? = null
    ): List<NovelExtension.Available> {
        return withIOContext {
            val extensions = prefetchedExtensions ?: findExtensions()

            val extensionManager: NovelExtensionManager = Injekt.get()
            val installedExtensions = extensionManager.installedExtensionsFlow.value.ifEmpty {
                NovelExtensionLoader.loadExtensionAsync(context)
                    .filterIsInstance<NovelLoadResult.Success>()
                    .map { it.extension }
            }

            val extensionsWithUpdate = mutableListOf<NovelExtension.Available>()
            for (installedExt in installedExtensions) {
                val pkgName = installedExt.pkgName
                val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
                val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
                val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
                val hasUpdate = hasUpdatedVer || hasUpdatedLib
                if (hasUpdate) {
                    extensionsWithUpdate.add(availableExt)
                }
            }

            extensionsWithUpdate
        }
    }

    /**
     * Convert JSON response to NovelExtension.Available list.
     * Only includes extensions with mikoNovel=1 marker (true Miko novel extensions).
     * This filtering ensures manga extensions with "novel" in their name are excluded.
     */
    private fun List<NovelExtensionJsonObject>.toNovelExtensions(repoUrl: String): List<NovelExtension.Available> {
        return this
            .filter {
                // Only include extensions marked as mikoNovel=1
                // This is our unique marker that distinguishes true novel extensions
                // from manga extensions that happen to have "novel" in their name
                val isMikoNovel = it.mikoNovel == 1
                if (!isMikoNovel) {
                    return@filter false
                }
                
                val libVersion = it.extractLibVersion()
                libVersion >= NovelExtensionLoader.LIB_VERSION_MIN && libVersion <= NovelExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                NovelExtension.Available(
                    name = it.name.substringAfter("Miko: ").substringAfter("Yokai: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources?.map { source ->
                        NovelExtension.AvailableNovelSource(
                            name = source.name,
                            id = source.id.toLong(),
                            lang = source.lang,
                            baseUrl = source.baseUrl,
                        )
                    } ?: emptyList(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    /**
     * Get the APK URL for a novel extension.
     */
    fun getApkUrl(extension: NovelExtensionManager.NovelExtensionInfo): String {
        return "${extension.repoUrl}/apk/${extension.apkName}"
    }

    private fun NovelExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDoubleOrNull() ?: 1.0
    }
}

/**
 * JSON structure for novel extension metadata in index.min.json.
 */
@Serializable
private data class NovelExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val mikoNovel: Int = 0,  // Marker for true Miko novel extensions
    val hasReadme: Int = 0,
    val hasChangelog: Int = 0,
    val sources: List<NovelSourceJsonObject>?,
)

@Serializable
private data class NovelSourceJsonObject(
    val name: String,
    val id: String,
    val lang: String,
    val baseUrl: String,
)
