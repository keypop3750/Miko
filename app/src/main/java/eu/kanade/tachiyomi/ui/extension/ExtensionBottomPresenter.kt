package eu.kanade.tachiyomi.ui.extension

import android.content.pm.PackageInstaller
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.extension.ExtensionInstallerJob
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.ui.migration.BaseMigrationPresenter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.core.mode.ModeManager
import yokai.i18n.MR
import yokai.util.lang.getString

typealias ExtensionTuple =
    Triple<List<Extension.Installed>, List<Extension.Untrusted>, List<Extension.Available>>
typealias ExtensionIntallInfo = Pair<InstallStep, PackageInstaller.SessionInfo?>

// More flexible type that can hold either SessionInfo (manga) or Int (novel download progress)
typealias FlexibleExtensionInstallInfo = Pair<InstallStep, Any?>

/**
 * Presenter of [ExtensionBottomSheet].
 * Handles both manga and novel extensions based on current mode.
 */
class ExtensionBottomPresenter : BaseMigrationPresenter<ExtensionBottomSheet>() {

    private var extensions = emptyList<ExtensionItem>()
    
    private val novelExtensionManager: NovelExtensionManager = Injekt.get()

    val downloadManager: DownloadManager = Injekt.get()

    // Use flexible type to support both manga (SessionInfo) and novel (Int progress) downloads
    private var currentDownloads = hashMapOf<String, FlexibleExtensionInstallInfo>()

    private var firstLoad = true

    // Conversion functions to merge novel extensions with manga extensions
    private fun NovelExtension.Installed.toExtension(): Extension.Installed {
        return Extension.Installed(
            name = name,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            pkgFactory = pkgFactory,
            sources = emptyList(), // Novel sources are not manga Source type
            icon = icon,
            hasUpdate = hasUpdate,
            isObsolete = isObsolete,
            isShared = isShared,
            repoUrl = repoUrl,
        )
    }

    private fun NovelExtension.Available.toExtension(): Extension.Available {
        return Extension.Available(
            name = name,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            isNsfw = isNsfw,
            apkName = apkName,
            iconUrl = iconUrl,
            sources = sources.map { source ->
                Extension.AvailableSource(
                    name = source.name,
                    id = source.id,
                    lang = source.lang,
                    baseUrl = source.baseUrl,
                )
            },
            repoUrl = repoUrl,
        )
    }

    private fun NovelExtension.Untrusted.toExtension(): Extension.Untrusted {
        return Extension.Untrusted(
            name = name,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            signatureHash = signatureHash,
            lang = lang,
            isNsfw = isNsfw,
        )
    }

    /**
     * Gets extension tuple for the current mode.
     * In manga mode: only manga extensions from ExtensionManager
     * In novel mode: only novel extensions from NovelExtensionManager (converted to Extension type)
     * 
     * This is the correct architectural approach - extensions are separated by their source manager,
     * not by package name filtering. This ensures instant display without any post-filtering.
     */
    private fun getExtensionsForCurrentMode(): ExtensionTuple {
        return if (ModeManager.isNovelMode()) {
            // Novel mode: only show novel extensions from NovelExtensionManager
            val installed = novelExtensionManager.installedExtensionsFlow.value
            val untrusted = novelExtensionManager.untrustedExtensionsFlow.value
            val available = novelExtensionManager.availableExtensionsFlow.value
            co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] Novel mode - installed: ${installed.size}, untrusted: ${untrusted.size}, available: ${available.size}" }
            co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] Novel installed pkgs: ${installed.map { it.pkgName }}" }
            Triple(
                installed.map { it.toExtension() },
                untrusted.map { it.toExtension() },
                available.map { it.toExtension() },
            )
        } else {
            // Manga mode: only show manga extensions from ExtensionManager
            Triple(
                extensionManager.installedExtensionsFlow.value,
                extensionManager.untrustedExtensionsFlow.value,
                extensionManager.availableExtensionsFlow.value,
            )
        }
    }

    /**
     * Checks if an extension belongs to the current mode based on its source manager.
     * Uses the extension's presence in the respective manager's flow, not package name.
     */
    private fun isNovelExtension(pkgName: String): Boolean {
        return novelExtensionManager.installedExtensionsFlow.value.any { it.pkgName == pkgName } ||
            novelExtensionManager.untrustedExtensionsFlow.value.any { it.pkgName == pkgName } ||
            novelExtensionManager.availableExtensionsFlow.value.any { it.pkgName == pkgName }
    }

    override fun onCreate() {
        super.onCreate()
        co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] onCreate called, isNovelMode=${ModeManager.isNovelMode()}" }
        
        // Run first time migration
        presenterScope.launch { firstTimeMigration() }
        
        // Observe mode changes and refresh extensions
        presenterScope.launch {
            ModeManager.currentMode.collectLatest { mode ->
                co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] Mode changed to: $mode" }
                firstLoad = true
                currentDownloads.clear()
                if (ModeManager.isNovelMode()) {
                    novelExtensionManager.findAvailableExtensions()
                } else {
                    extensionManager.findAvailableExtensions()
                }
                val tuple = getExtensionsForCurrentMode()
                co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] Mode change refresh: installed=${tuple.first.size}, untrusted=${tuple.second.size}, available=${tuple.third.size}" }
                extensions = toItems(tuple)
                co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] Mode change - created ${extensions.size} items" }
                withContext(Dispatchers.Main) { view?.setExtensions(extensions, false) }
            }
        }
        // Observe manga extension downloads (only relevant in manga mode, but we track anyway)
        presenterScope.launch {
            extensionManager.downloadSharedFlow
                .collect {
                    // Skip if in novel mode
                    if (ModeManager.isNovelMode()) return@collect
                    
                    if (it.first.startsWith("Finished") || it.first.startsWith("Uninstalled")) {
                        if (it.first.startsWith("Finished")) {
                            firstLoad = true
                            currentDownloads.clear()
                        }
                        extensions = toItems(getExtensionsForCurrentMode())
                        withUIContext { view?.setExtensions(extensions) }
                        return@collect
                    }
                    val extension = extensions.find { item ->
                        it.first == item.extension.pkgName
                    } ?: return@collect
                    when (it.second.first) {
                        InstallStep.Installed, InstallStep.Error -> {
                            currentDownloads.remove(extension.extension.pkgName)
                        }
                        else -> {
                            currentDownloads[extension.extension.pkgName] = it.second
                        }
                    }
                    val item = updateInstallStep(extension.extension, it.second.first, it.second.second)
                    if (item != null) {
                        withUIContext { view?.downloadUpdate(item) }
                    }
                }
        }
        // Observe novel extension downloads (only relevant in novel mode, but we track anyway)
        presenterScope.launch {
            novelExtensionManager.downloadSharedFlow
                .collect {
                    // Skip if in manga mode
                    if (!ModeManager.isNovelMode()) return@collect
                    
                    if (it.first.startsWith("Finished") || it.first.startsWith("Uninstalled")) {
                        if (it.first.startsWith("Finished")) {
                            firstLoad = true
                            currentDownloads.clear()
                        }
                        extensions = toItems(getExtensionsForCurrentMode())
                        withUIContext { view?.setExtensions(extensions) }
                        return@collect
                    }
                    val extension = extensions.find { item ->
                        it.first == item.extension.pkgName
                    } ?: return@collect
                    when (it.second.first) {
                        InstallStep.Installed, InstallStep.Error -> {
                            currentDownloads.remove(extension.extension.pkgName)
                        }
                        else -> {
                            currentDownloads[extension.extension.pkgName] = it.second
                        }
                    }
                    val item = updateInstallStep(extension.extension, it.second.first, it.second.second)
                    if (item != null) {
                        withUIContext { view?.downloadUpdate(item) }
                    }
                }
        }
    }

    fun refreshExtensions() {
        presenterScope.launch {
            extensions = toItems(getExtensionsForCurrentMode())
            withContext(Dispatchers.Main) { view?.setExtensions(extensions, false) }
        }
    }

    @Synchronized
    private fun toItems(tuple: ExtensionTuple): List<ExtensionItem> {
        val context = view?.context ?: return emptyList()
        val activeLangs = preferences.enabledLanguages().get()
        val showNsfwSources = preferences.showNsfwSources().get()
        
        // No mode filtering needed here - extensions are already mode-specific from getExtensionsForCurrentMode()
        val (installed, untrusted, available) = tuple

        val items = mutableListOf<ExtensionItem>()

        if (firstLoad) {
            co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] firstLoad=true, clearing and rebuilding currentDownloads" }
            currentDownloads.clear()  // Clear any stale downloads from previous mode
            val listOfExtensions = installed + untrusted + available
            listOfExtensions.forEach {
                // Check the appropriate manager based on current mode
                val installInfo = if (ModeManager.isNovelMode()) {
                    novelExtensionManager.getInstallInfo(it.pkgName)
                } else {
                    extensionManager.getInstallInfo(it.pkgName)
                } ?: return@forEach
                co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] Active download: ${it.pkgName} -> $installInfo" }
                currentDownloads[it.pkgName] = installInfo
            }
            firstLoad = false
            co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] currentDownloads after firstLoad: ${currentDownloads.keys}" }
        }

        val updatesSorted = installed.filter { it.hasUpdate && (showNsfwSources || !it.isNsfw) }.sortedBy { it.name }
        val sortOrder = InstalledExtensionsOrder.fromPreference(preferences)
        val installedSorted = installed
            .filter { !it.hasUpdate && (showNsfwSources || !it.isNsfw) }
            .sortedWith(
                compareBy(
                    { !it.isObsolete },
                    {
                        when (sortOrder) {
                            InstalledExtensionsOrder.Name -> it.name
                            InstalledExtensionsOrder.RecentlyUpdated -> Long.MAX_VALUE - ExtensionLoader.extensionUpdateDate(context, it)
                            InstalledExtensionsOrder.RecentlyInstalled -> Long.MAX_VALUE - ExtensionLoader.extensionInstallDate(context, it)
                            InstalledExtensionsOrder.Language -> it.lang
                        }
                    },
                    { it.name },
                ),
            )
        val untrustedSorted = untrusted.sortedBy { it.name }
        val availableSorted = available
            // Filter out already installed extensions and disabled languages
            .filter { avail ->
                installed.none { it.pkgName == avail.pkgName } &&
                    untrusted.none { it.pkgName == avail.pkgName } &&
                    (avail.lang in activeLangs) &&
                    (showNsfwSources || !avail.isNsfw)
            }
            .sortedBy { it.name }

        if (updatesSorted.isNotEmpty()) {
            val header = ExtensionGroupItem(
                context.getString(
                    MR.plurals._updates_pending,
                    updatesSorted.size,
                    updatesSorted.size,
                ),
                updatesSorted.size,
                items.count { it.extension.pkgName in currentDownloads.keys } != updatesSorted.size,
            )
            items += updatesSorted.map { extension ->
                ExtensionItem(extension, header, currentDownloads[extension.pkgName])
            }
        }
        if (installedSorted.isNotEmpty() || untrustedSorted.isNotEmpty()) {
            val header = ExtensionGroupItem(context.getString(MR.strings.installed), installedSorted.size + untrustedSorted.size, installedSorting = preferences.installedExtensionsOrder().get())
            items += installedSorted.map { extension ->
                val downloadInfo = currentDownloads[extension.pkgName]
                co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] Creating item for installed ${extension.pkgName}, downloadInfo=$downloadInfo" }
                ExtensionItem(extension, header, downloadInfo)
            }
            items += untrustedSorted.map { extension ->
                ExtensionItem(extension, header)
            }
        }
        if (availableSorted.isNotEmpty()) {
            val availableGroupedByLang = availableSorted
                .groupBy { LocaleHelper.getSourceDisplayName(it.lang, context) }
                .toSortedMap()

            availableGroupedByLang
                .forEach {
                    val header = ExtensionGroupItem(it.key, it.value.size)
                    items += it.value.map { extension ->
                        ExtensionItem(extension, header, currentDownloads[extension.pkgName])
                    }
                }
        }

        this.extensions = items
        return items
    }

    fun getExtensionUpdateCount(): Int = preferences.extensionUpdatesCount().get()

    @Synchronized
    private fun updateInstallStep(
        extension: Extension,
        state: InstallStep?,
        progressOrSession: Any?,
    ): ExtensionItem? {
        val extensions = extensions.toMutableList()
        val position = extensions.indexOfFirst { it.extension.pkgName == extension.pkgName }

        return if (position != -1) {
            val item = extensions[position].copy(
                installStep = state,
                session = progressOrSession as? PackageInstaller.SessionInfo,
            )
            extensions[position] = item

            this.extensions = extensions
            item
        } else {
            null
        }
    }

    fun cancelExtensionInstall(extItem: ExtensionItem) {
        // Check if it's a novel extension using the manager lookup
        if (isNovelExtension(extItem.extension.pkgName)) {
            // Novel extensions use pkgName for cancellation (no session ID)
            novelExtensionManager.cancelInstallation(extItem.extension.pkgName)
        } else {
            // Manga extensions use session ID
            val sessionId = extItem.session?.sessionId ?: return
            extensionManager.cancelInstallation(sessionId)
        }
    }

    fun installExtension(extension: Extension.Available) {
        // Check if it's a novel extension using the manager lookup
        val isNovel = isNovelExtension(extension.pkgName)
        
        presenterScope.launch {
            if (isNovel) {
                // Find the original novel extension and install via NovelExtensionManager
                val novelExtension = novelExtensionManager.availableExtensionsFlow.value
                    .find { it.pkgName == extension.pkgName }
                if (novelExtension != null) {
                    val extensionInfo = NovelExtensionManager.NovelExtensionInfo(
                        apkName = novelExtension.apkName,
                        pkgName = novelExtension.pkgName,
                        repoUrl = novelExtension.repoUrl ?: "",
                    )
                    novelExtensionManager.installExtension(
                        extensionInfo,
                        presenterScope,
                    ).collect {
                        when (it.first) {
                            InstallStep.Installed, InstallStep.Error -> {
                                currentDownloads.remove(extension.pkgName)
                            }
                            else -> {
                                currentDownloads[extension.pkgName] = it
                            }
                        }
                        val item = updateInstallStep(extension, it.first, it.second)
                        if (item != null) {
                            withUIContext { view?.downloadUpdate(item) }
                        }
                    }
                }
            } else {
                extensionManager.installExtension(
                    ExtensionManager.ExtensionInfo(extension),
                    presenterScope,
                ).collect {
                    when (it.first) {
                        InstallStep.Installed, InstallStep.Error -> {
                            currentDownloads.remove(extension.pkgName)
                        }
                        else -> {
                            currentDownloads[extension.pkgName] = it
                        }
                    }
                    val item = updateInstallStep(extension, it.first, it.second)
                    if (item != null) {
                        withUIContext { view?.downloadUpdate(item) }
                    }
                }
            }
        }
    }

    fun updateExtension(extension: Extension.Installed) {
        // Check both manga and novel available extensions
        val availableExt = extensionManager.availableExtensionsFlow.value.find { it.pkgName == extension.pkgName }
            ?: novelExtensionManager.availableExtensionsFlow.value.find { it.pkgName == extension.pkgName }?.toExtension()
            ?: return
        installExtension(availableExt)
    }

    fun updateExtensions(extensions: List<Extension.Installed>) {
        if (extensions.isEmpty()) return
        val context = view?.context ?: return
        extensions.forEach {
            val pkgName = it.pkgName
            currentDownloads[pkgName] = InstallStep.Pending to null
            val item = updateInstallStep(it, InstallStep.Pending, null) ?: return@forEach
            view?.downloadUpdate(item)
        }
        // Separate novel and manga extensions using reliable manager lookup
        val (novelExtensionsList, mangaExtensions) = extensions.partition { 
            isNovelExtension(it.pkgName) 
        }
        
        // Start manga extension updates
        if (mangaExtensions.isNotEmpty()) {
            ExtensionInstallerJob.start(
                context,
                mangaExtensions.mapNotNull { extension ->
                    extensionManager.availableExtensionsFlow.value.find { it.pkgName == extension.pkgName }
                },
            )
        }
        
        // Start novel extension updates (individually since no batch installer)
        novelExtensionsList.forEach { extension ->
            val availableNovel = novelExtensionManager.availableExtensionsFlow.value
                .find { it.pkgName == extension.pkgName }
            if (availableNovel != null) {
                presenterScope.launch {
                    val extensionInfo = NovelExtensionManager.NovelExtensionInfo(
                        apkName = availableNovel.apkName,
                        pkgName = availableNovel.pkgName,
                        repoUrl = availableNovel.repoUrl ?: "",
                    )
                    novelExtensionManager.installExtension(
                        extensionInfo,
                        presenterScope,
                    ).collect { /* handled by downloadSharedFlow */ }
                }
            }
        }
    }

    fun uninstallExtension(pkgName: String) {
        // Clear any stuck download state so UI doesn't show "Downloading" forever
        currentDownloads.remove(pkgName)
        // Check if it's a novel extension using manager lookup
        if (isNovelExtension(pkgName)) {
            novelExtensionManager.uninstallExtension(pkgName)
        } else {
            extensionManager.uninstallExtension(pkgName)
        }
        refreshExtensions()
    }

    fun findAvailableExtensions() {
        presenterScope.launch {
            // Find extensions based on current mode for efficiency
            if (ModeManager.isNovelMode()) {
                novelExtensionManager.findAvailableExtensions()
            } else {
                extensionManager.findAvailableExtensions()
            }
        }
    }

    fun trustExtension(pkgName: String, versionCode: Long, signatureHash: String) {
        presenterScope.launch {
            // Check if it's a novel extension using manager lookup
            if (isNovelExtension(pkgName)) {
                novelExtensionManager.trust(pkgName, versionCode, signatureHash)
            } else {
                extensionManager.trust(pkgName, versionCode, signatureHash)
            }
        }
    }
}
