package eu.kanade.tachiyomi.extension.novel.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import co.touchlab.kermit.Logger
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelLoadResult
import eu.kanade.tachiyomi.util.lang.Hash
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.extension.interactor.TrustExtension
import yokai.source.novel.NovelMainAPI
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/**
 * Class that handles the loading of novel extensions installed in the system.
 * Parallel to ExtensionLoader for manga extensions.
 */
@SuppressLint("PackageManagerGetSignatures")
internal object NovelExtensionLoader {

    private val preferences: PreferencesHelper by injectLazy()
    private val trustExtension: TrustExtension by injectLazy()
    private val loadNsfwSource by lazy {
        preferences.showNsfwSources().get()
    }

    // Novel extension specific identifiers
    private const val NOVEL_EXTENSION_FEATURE = "yokai.novel.extension"
    private const val METADATA_NOVEL_SOURCE_CLASS = "yokai.novel.extension.class"
    private const val METADATA_NOVEL_SOURCE_FACTORY = "yokai.novel.extension.factory"
    private const val METADATA_NSFW = "yokai.novel.extension.nsfw"
    private const val METADATA_HAS_README = "yokai.novel.extension.hasReadme"
    private const val METADATA_HAS_CHANGELOG = "yokai.novel.extension.hasChangelog"
    
    const val LIB_VERSION_MIN = 1.0
    const val LIB_VERSION_MAX = 1.5

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
        PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val PRIVATE_EXTENSION_EXTENSION = "novelext"

    private fun getPrivateExtensionDir(context: Context) = File(context.filesDir, "novel_exts")

    /**
     * Install a private novel extension from a file.
     */
    fun installPrivateExtensionFile(context: Context, file: File): Boolean {
        Logger.d { "[NOVEL_LOADER] Installing from file: ${file.absolutePath}" }
        Logger.d { "[NOVEL_LOADER] File size: ${file.length()} bytes" }
        
        val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
        Logger.d { "[NOVEL_LOADER] PackageInfo: ${packageInfo?.packageName}" }
        
        if (packageInfo == null) {
            Logger.e { "[NOVEL_LOADER] Failed to parse APK package info" }
            return false
        }
        
        val isNovelExt = isPackageANovelExtension(packageInfo)
        Logger.d { "[NOVEL_LOADER] Is novel extension: $isNovelExt" }
        Logger.d { "[NOVEL_LOADER] reqFeatures: ${packageInfo.reqFeatures?.map { it.name }}" }
        
        if (!isNovelExt) {
            Logger.e { "[NOVEL_LOADER] Package is not a novel extension (missing $NOVEL_EXTENSION_FEATURE feature)" }
            return false
        }
        
        val extension = packageInfo
        val currentExtension = getExtensionPackageInfoFromPkgName(context, extension.packageName)

        if (currentExtension != null) {
            if (PackageInfoCompat.getLongVersionCode(extension) <
                PackageInfoCompat.getLongVersionCode(currentExtension)
            ) {
                Logger.e { "Installed novel extension version is higher. Downgrading is not allowed." }
                return false
            }

            val extensionSignatures = getSignatures(extension)
            if (extensionSignatures.isNullOrEmpty()) {
                Logger.e { "Novel extension to be installed is not signed." }
                return false
            }

            if (!extensionSignatures.containsAll(getSignatures(currentExtension)!!)) {
                Logger.e { "Installed novel extension signature is not matched." }
                return false
            }
        }

        val target = File(getPrivateExtensionDir(context), "${extension.packageName}.$PRIVATE_EXTENSION_EXTENSION")
        return try {
            getPrivateExtensionDir(context).mkdirs()
            file.copyTo(target, overwrite = true)
            if (currentExtension != null) {
                NovelExtensionInstallReceiver.notifyReplaced(context, extension.packageName)
            } else {
                NovelExtensionInstallReceiver.notifyAdded(context, extension.packageName)
            }
            true
        } catch (e: Exception) {
            Logger.e(e) { "Failed to copy novel extension file." }
            target.delete()
            false
        }
    }

    /**
     * Uninstall a private novel extension.
     */
    fun uninstallPrivateExtension(context: Context, pkgName: String) {
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION").delete()
    }

    private fun getExtensionsPackages(context: Context): List<NovelExtensionInfo> {
        val pkgManager = context.packageManager

        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val sharedExtPkgs = installedPkgs
            .asSequence()
            .filter { isPackageANovelExtension(it) }
            .map { NovelExtensionInfo(packageInfo = it, isShared = true) }

        val privateExtPkgs = getPrivateExtensionDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension == PRIVATE_EXTENSION_EXTENSION }
            ?.mapNotNull {
                it.setReadOnly()
                val path = it.absolutePath
                pkgManager.getPackageArchiveInfo(path, PACKAGE_FLAGS)
                    ?.apply { applicationInfo!!.fixBasePaths(path) }
            }
            ?.filter { isPackageANovelExtension(it) }
            ?.map { NovelExtensionInfo(packageInfo = it, isShared = false) }
            ?: emptySequence()

        return (sharedExtPkgs + privateExtPkgs)
            .distinctBy { it.packageInfo.packageName }
            .mapNotNull { sharedPkg ->
                val privatePkg = privateExtPkgs
                    .singleOrNull { it.packageInfo.packageName == sharedPkg.packageInfo.packageName }
                selectExtensionPackage(sharedPkg, privatePkg)
            }
            .toList()
    }

    /**
     * Load all installed novel extensions.
     */
    fun loadExtensions(context: Context): List<NovelLoadResult> {
        val extPkgs = getExtensionsPackages(context)
        return runBlocking {
            val deferred = extPkgs.map {
                async { loadExtension(context, it) }
            }
            deferred.awaitAll()
        }
    }

    /**
     * Load novel extensions asynchronously.
     */
    suspend fun loadExtensionAsync(context: Context): List<NovelLoadResult> {
        val extPkgs = getExtensionsPackages(context)
        return withIOContext {
            val deferred = extPkgs.map {
                async { loadExtension(context, it) }
            }
            deferred.awaitAll()
        }
    }

    /**
     * Load a novel extension from package name.
     */
    suspend fun loadExtensionFromPkgName(context: Context, pkgName: String): NovelLoadResult {
        val extensionPackage = getExtensionInfoFromPkgName(context, pkgName)
        if (extensionPackage == null) {
            Logger.e { "Novel extension package is not found ($pkgName)" }
            return NovelLoadResult.Error
        }
        return loadExtension(context, extensionPackage)
    }

    fun getExtensionPackageInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        return getExtensionInfoFromPkgName(context, pkgName)?.packageInfo
    }

    fun isExtensionPrivate(context: Context, pkgName: String): Boolean =
        getExtensionInfoFromPkgName(context, pkgName)?.isShared == false

    fun extensionInstallDate(context: Context, extension: NovelExtension.Installed): Long {
        return try {
            if (!extension.isShared) {
                val file = privateExtensionFile(context, extension.pkgName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val attr = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                    attr.creationTime().toMillis()
                } else {
                    file.lastModified()
                }
            } else {
                context.packageManager.getPackageInfo(extension.pkgName, 0).firstInstallTime
            }
        } catch (e: java.lang.Exception) {
            0
        }
    }

    fun extensionUpdateDate(context: Context, extension: NovelExtension.Installed): Long {
        return try {
            if (!extension.isShared) {
                privateExtensionFile(context, extension.pkgName).lastModified()
            } else {
                context.packageManager.getPackageInfo(extension.pkgName, 0).lastUpdateTime
            }
        } catch (e: java.lang.Exception) {
            0
        }
    }

    private fun privateExtensionFile(context: Context, pkgName: String): File =
        File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION")

    private fun getExtensionInfoFromPkgName(context: Context, pkgName: String): NovelExtensionInfo? {
        val privateExtensionFile = File(getPrivateExtensionDir(context), "$pkgName.$PRIVATE_EXTENSION_EXTENSION")
        val privatePkg = if (privateExtensionFile.isFile) {
            privateExtensionFile.setReadOnly()
            context.packageManager.getPackageArchiveInfo(privateExtensionFile.absolutePath, PACKAGE_FLAGS)
                ?.takeIf { isPackageANovelExtension(it) }
                ?.let {
                    it.applicationInfo!!.fixBasePaths(privateExtensionFile.absolutePath)
                    NovelExtensionInfo(
                        packageInfo = it,
                        isShared = false,
                    )
                }
        } else {
            null
        }

        val sharedPkg = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
                .takeIf { isPackageANovelExtension(it) }
                ?.let {
                    NovelExtensionInfo(
                        packageInfo = it,
                        isShared = true,
                    )
                }
        } catch (error: PackageManager.NameNotFoundException) {
            null
        }

        return selectExtensionPackage(sharedPkg, privatePkg)
    }

    /**
     * Load a novel extension.
     */
    private suspend fun loadExtension(context: Context, extensionInfo: NovelExtensionInfo): NovelLoadResult {
        val pkgManager = context.packageManager
        val pkgInfo = extensionInfo.packageInfo
        val appInfo = pkgInfo.applicationInfo!!
        val pkgName = pkgInfo.packageName

        val extName = pkgManager.getApplicationLabel(appInfo).toString()
            .substringAfter("Miko: ")
            .substringAfter("Yokai: ")
            .substringAfter("Miko Novel: ")
        val versionName = pkgInfo.versionName

        if (versionName.isNullOrEmpty()) {
            Logger.w { "Missing versionName for novel extension $extName" }
            return NovelLoadResult.Error
        }

        // Validate lib version
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            Logger.w {
                "Novel lib version is $libVersion, while only versions $LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed"
            }
            return NovelLoadResult.Error
        }

        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        val signatures = getSignatures(pkgInfo)
        if (signatures.isNullOrEmpty()) {
            Logger.w { "Novel package $pkgName isn't signed" }
            return NovelLoadResult.Error
        } else if (!trustExtension.isTrusted(pkgInfo, signatures)) {
            val extension = NovelExtension.Untrusted(
                extName,
                pkgName,
                versionName,
                versionCode,
                libVersion,
                signatures.last(),
            )
            Logger.w { "Novel extension $pkgName isn't trusted" }
            return NovelLoadResult.Untrusted(extension)
        }

        val isNsfw = appInfo.metaData?.getInt(METADATA_NSFW) == 1
        if (!loadNsfwSource && isNsfw) {
            Logger.w { "NSFW novel extension $pkgName not allowed" }
            return NovelLoadResult.Error
        }

        val classLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)

        val sourceClassName = appInfo.metaData?.getString(METADATA_NOVEL_SOURCE_CLASS)
        if (sourceClassName.isNullOrEmpty()) {
            Logger.e { "Novel extension $extName missing source class metadata" }
            return NovelLoadResult.Error
        }

        val sources = sourceClassName
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap {
                try {
                    val obj = Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()
                    when {
                        obj is NovelMainAPI -> listOf(obj)
                        obj is NovelSourceFactory -> obj.createSources()
                        // Handle extension lib's NovelSource and NovelSourceFactory
                        obj.javaClass.name.contains("NovelSourceFactory") || 
                        obj.javaClass.interfaces.any { iface -> iface.name.contains("NovelSourceFactory") } -> {
                            // Extension's NovelSourceFactory - use reflection to call createSources()
                            val method = obj.javaClass.getMethod("createSources")
                            @Suppress("UNCHECKED_CAST")
                            val extSources = method.invoke(obj) as List<Any>
                            extSources.map { extSource -> NovelSourceAdapter(extSource) }
                        }
                        obj.javaClass.name.contains("NovelSource") ||
                        obj.javaClass.superclass?.name?.contains("NovelSource") == true -> {
                            // Single extension NovelSource
                            listOf(NovelSourceAdapter(obj))
                        }
                        else -> throw Exception("Unknown novel source class type! ${obj.javaClass}")
                    }
                } catch (e: Throwable) {
                    Logger.e(e) { "Novel extension load error: $extName. Class: $it" }
                    return NovelLoadResult.Error
                }
            }

        // Inject HTTP client into sources
        val httpClient: OkHttpClient = try {
            val networkHelper = Injekt.get<Any>(Class.forName("eu.kanade.tachiyomi.network.NetworkHelper"))
            val clientField = networkHelper.javaClass.getDeclaredField("client")
            clientField.isAccessible = true
            val client = clientField.get(networkHelper) as OkHttpClient
            Logger.d { "[NOVEL_LOADER] Got OkHttpClient from NetworkHelper" }
            client
        } catch (e: Exception) {
            Logger.e(e) { "[NOVEL_LOADER] Failed to get OkHttpClient from NetworkHelper, using default" }
            OkHttpClient.Builder().build()
        }

        sources.forEach { source ->
            try {
                // Inject HTTP client into the source
                if (source is NovelSourceAdapter) {
                    Logger.d { "[NOVEL_LOADER] Injecting HTTP client into NovelSourceAdapter: ${source.name}" }
                    source.injectHttpClient(httpClient)
                } else {
                    // Try reflection for native sources
                    Logger.d { "[NOVEL_LOADER] Injecting HTTP client into native source: ${source.javaClass.name}" }
                    val field = source.javaClass.superclass?.getDeclaredField("client")
                        ?: source.javaClass.getDeclaredField("client")
                    field.isAccessible = true
                    field.set(source, httpClient)
                    Logger.d { "[NOVEL_LOADER] Injected HTTP client into ${source.javaClass.simpleName}.client" }
                }
            } catch (e: Exception) {
                Logger.e(e) { "[NOVEL_LOADER] Failed to inject HTTP client into ${source.javaClass.name}" }
            }
        }

        val langs = sources.map { it.lang }.toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        val extension = NovelExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            sources = sources,
            pkgFactory = appInfo.metaData?.getString(METADATA_NOVEL_SOURCE_FACTORY),
            icon = appInfo.loadIcon(pkgManager),
            isShared = extensionInfo.isShared,
        )
        return NovelLoadResult.Success(extension)
    }

    private fun selectExtensionPackage(shared: NovelExtensionInfo?, private: NovelExtensionInfo?): NovelExtensionInfo? {
        when {
            private == null && shared != null -> return shared
            shared == null && private != null -> return private
            shared == null && private == null -> return null
        }

        return if (PackageInfoCompat.getLongVersionCode(shared!!.packageInfo) >=
            PackageInfoCompat.getLongVersionCode(private!!.packageInfo)
        ) {
            shared
        } else {
            private
        }
    }

    /**
     * Returns true if the given package is a novel extension.
     */
    private fun isPackageANovelExtension(pkgInfo: PackageInfo): Boolean =
        pkgInfo.reqFeatures.orEmpty().any { it.name == NOVEL_EXTENSION_FEATURE }

    /**
     * Returns the signatures of the package or null if it's not signed.
     */
    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo!!
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
            ?.map { Hash.sha256(it.toByteArray()) }
            ?.toList()
    }

    private fun ApplicationInfo.fixBasePaths(apkPath: String) {
        if (sourceDir == null) {
            sourceDir = apkPath
        }
        if (publicSourceDir == null) {
            publicSourceDir = apkPath
        }
    }

    private data class NovelExtensionInfo(
        val packageInfo: PackageInfo,
        val isShared: Boolean,
    )
}

/**
 * Factory interface for creating multiple novel sources from a single extension.
 */
interface NovelSourceFactory {
    fun createSources(): List<NovelMainAPI>
}
