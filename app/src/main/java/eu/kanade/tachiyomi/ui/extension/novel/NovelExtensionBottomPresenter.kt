package eu.kanade.tachiyomi.ui.extension.novel

import android.content.pm.PackageInstaller
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionInstaller
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionInstallInfo
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionLoader
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.extension.ExtensionIntallInfo
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.i18n.MR
import yokai.util.lang.getString

typealias NovelExtensionTuple =
    Triple<List<NovelExtension.Installed>, List<NovelExtension.Untrusted>, List<NovelExtension.Available>>

/**
 * Presenter for novel extension views (bottom sheet and controller).
 */
class NovelExtensionBottomPresenter : BaseCoroutinePresenter<NovelExtensionView>() {

    private var extensions = emptyList<NovelExtensionItem>()

    private val novelExtensionManager: NovelExtensionManager = Injekt.get()
    val preferences: PreferencesHelper = Injekt.get()

    // Use NovelExtensionInstallInfo type which allows Int progress or SessionInfo
    private var currentDownloads = hashMapOf<String, NovelExtensionInstallInfo>()

    private var firstLoad = true

    override fun onCreate() {
        presenterScope.launch {
            val extensionJob = async {
                novelExtensionManager.findAvailableExtensions()
                extensions = toItems(
                    Triple(
                        novelExtensionManager.installedExtensionsFlow.value,
                        novelExtensionManager.untrustedExtensionsFlow.value,
                        novelExtensionManager.availableExtensionsFlow.value,
                    ),
                )
                withContext(Dispatchers.Main) { view?.setExtensions(extensions, false) }
            }
            listOf(extensionJob).awaitAll()
        }
        
        // Observe changes to extension flows (handles trust, install, uninstall)
        presenterScope.launch {
            combine(
                novelExtensionManager.installedExtensionsFlow,
                novelExtensionManager.untrustedExtensionsFlow,
                novelExtensionManager.availableExtensionsFlow
            ) { installed, untrusted, available ->
                co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] Flow emitted: ${installed.size} installed, ${untrusted.size} untrusted, ${available.size} available" }
                installed.forEach { ext ->
                    co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] Installed ext ${ext.pkgName} iconUrl=${ext.iconUrl}" }
                }
                Triple(installed, untrusted, available)
            }.collectLatest { tuple ->
                extensions = toItems(tuple)
                co.touchlab.kermit.Logger.d { "[EXT_PRESENTER] Calling setExtensions with ${extensions.size} items" }
                withUIContext { view?.setExtensions(extensions) }
            }
        }
        
        presenterScope.launch {
            novelExtensionManager.downloadSharedFlow
                .collect {
                    if (it.first.startsWith("Finished") || it.first.startsWith("Uninstalled")) {
                        if (it.first.startsWith("Finished")) {
                            firstLoad = true
                            currentDownloads.clear()
                        }
                        extensions = toItems(
                            Triple(
                                novelExtensionManager.installedExtensionsFlow.value,
                                novelExtensionManager.untrustedExtensionsFlow.value,
                                novelExtensionManager.availableExtensionsFlow.value,
                            ),
                        )
                        withUIContext { view?.setExtensions(extensions) }
                        return@collect
                    }
                    val extension = extensions.find { item ->
                        it.first == item.extension.pkgName
                    } ?: return@collect
                    when (it.second.first) {
                        InstallStep.Installed -> {
                            // When installation completes, clear download tracking and do a full refresh
                            // This ensures the item transitions from Available to Installed section properly
                            currentDownloads.remove(extension.extension.pkgName)
                            // Small delay to let the extension flow update
                            kotlinx.coroutines.delay(100)
                            extensions = toItems(
                                Triple(
                                    novelExtensionManager.installedExtensionsFlow.value,
                                    novelExtensionManager.untrustedExtensionsFlow.value,
                                    novelExtensionManager.availableExtensionsFlow.value,
                                ),
                            )
                            withUIContext { view?.setExtensions(extensions) }
                            return@collect
                        }
                        InstallStep.Error -> {
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
            extensions = toItems(
                Triple(
                    novelExtensionManager.installedExtensionsFlow.value,
                    novelExtensionManager.untrustedExtensionsFlow.value,
                    novelExtensionManager.availableExtensionsFlow.value,
                ),
            )
            withContext(Dispatchers.Main) { view?.setExtensions(extensions, false) }
        }
    }

    @Synchronized
    private fun toItems(tuple: NovelExtensionTuple): List<NovelExtensionItem> {
        val context = view?.getViewContext() ?: return emptyList()
        val activeLangs = preferences.enabledLanguages().get()
        val showNsfwSources = preferences.showNsfwSources().get()

        val (installed, untrusted, available) = tuple

        val items = mutableListOf<NovelExtensionItem>()

        if (firstLoad) {
            val listOfExtensions = installed + untrusted + available
            listOfExtensions.forEach {
                val installInfo = getInstallInfo(it.pkgName) ?: return@forEach
                currentDownloads[it.pkgName] = installInfo
            }
            firstLoad = false
        }

        // Enrich installed extensions with iconUrl from available extensions if missing
        val installedWithIcons = installed.map { ext ->
            if (ext.iconUrl.isNullOrEmpty()) {
                val availableExt = available.find { it.pkgName == ext.pkgName }
                if (availableExt != null) {
                    ext.copy(iconUrl = availableExt.iconUrl)
                } else {
                    ext
                }
            } else {
                ext
            }
        }

        val updatesSorted = installedWithIcons.filter { it.hasUpdate && (showNsfwSources || !it.isNsfw) }.sortedBy { it.name }
        val sortOrder = InstalledExtensionsOrder.fromPreference(preferences)
        val installedSorted = installedWithIcons
            .filter { !it.hasUpdate && (showNsfwSources || !it.isNsfw) }
            .sortedWith(
                compareBy(
                    { !it.isObsolete },
                    {
                        when (sortOrder) {
                            InstalledExtensionsOrder.Name -> it.name
                            InstalledExtensionsOrder.RecentlyUpdated -> Long.MAX_VALUE - NovelExtensionLoader.extensionUpdateDate(context, it)
                            InstalledExtensionsOrder.RecentlyInstalled -> Long.MAX_VALUE - NovelExtensionLoader.extensionInstallDate(context, it)
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
            val header = NovelExtensionGroupItem(
                context.getString(
                    MR.plurals._updates_pending,
                    updatesSorted.size,
                    updatesSorted.size,
                ),
                updatesSorted.size,
                items.count { it.extension.pkgName in currentDownloads.keys } != updatesSorted.size,
            )
            items += updatesSorted.map { extension ->
                NovelExtensionItem(extension, header, currentDownloads[extension.pkgName])
            }
        }
        if (installedSorted.isNotEmpty() || untrustedSorted.isNotEmpty()) {
            val header = NovelExtensionGroupItem(
                context.getString(MR.strings.installed), 
                installedSorted.size + untrustedSorted.size, 
                installedSorting = preferences.installedExtensionsOrder().get()
            )
            items += installedSorted.map { extension ->
                NovelExtensionItem(extension, header, currentDownloads[extension.pkgName])
            }
            items += untrustedSorted.map { extension ->
                NovelExtensionItem(extension, header)
            }
        }
        if (availableSorted.isNotEmpty()) {
            val availableGroupedByLang = availableSorted
                .groupBy { LocaleHelper.getSourceDisplayName(it.lang, context) }
                .toSortedMap()

            availableGroupedByLang
                .forEach {
                    val header = NovelExtensionGroupItem(it.key, it.value.size)
                    items += it.value.map { extension ->
                        NovelExtensionItem(extension, header, currentDownloads[extension.pkgName])
                    }
                }
        }

        this.extensions = items
        return items
    }

    private fun getInstallInfo(pkgName: String): NovelExtensionInstallInfo? {
        return currentDownloads[pkgName]
    }

    fun getExtensionUpdateCount(): Int {
        return novelExtensionManager.installedExtensionsFlow.value.count { it.hasUpdate }
    }

    @Synchronized
    private fun updateInstallStep(
        extension: NovelExtension,
        state: InstallStep?,
        progressOrSession: Any?,  // Can be Int (progress) or PackageInstaller.SessionInfo
    ): NovelExtensionItem? {
        val extensions = extensions.toMutableList()
        val position = extensions.indexOfFirst { it.extension.pkgName == extension.pkgName }

        return if (position != -1) {
            val session = progressOrSession as? PackageInstaller.SessionInfo
            val downloadProgress = progressOrSession as? Int
            val item = extensions[position].copy(
                installStep = state,
                session = session,
                downloadProgress = downloadProgress,
            )
            extensions[position] = item

            this.extensions = extensions
            item
        } else {
            null
        }
    }

    fun cancelExtensionInstall(extItem: NovelExtensionItem) {
        // Novel extensions use pkgName for cancellation (no session ID)
        novelExtensionManager.cancelInstallation(extItem.extension.pkgName)
    }

    fun installExtension(extension: NovelExtension.Available) {
        presenterScope.launch {
            novelExtensionManager.installExtension(
                NovelExtensionManager.NovelExtensionInfo(
                    apkName = extension.apkName,
                    pkgName = extension.pkgName,
                    repoUrl = extension.repoUrl ?: "",
                ),
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

    fun updateExtension(extension: NovelExtension.Installed) {
        val availableExt =
            novelExtensionManager.availableExtensionsFlow.value.find { it.pkgName == extension.pkgName } ?: return
        installExtension(availableExt)
    }

    fun updateExtensions(extensions: List<NovelExtension.Installed>) {
        if (extensions.isEmpty()) return
        extensions.forEach {
            val pkgName = it.pkgName
            currentDownloads[pkgName] = InstallStep.Pending to null
            val item = updateInstallStep(it, InstallStep.Pending, null) ?: return@forEach
            view?.downloadUpdate(item)
        }
        // Note: Novel extensions don't have a batch installer job yet
        // For now, install them one by one
        extensions.forEach { extension ->
            novelExtensionManager.availableExtensionsFlow.value.find { it.pkgName == extension.pkgName }?.let {
                installExtension(it)
            }
        }
    }

    fun uninstallExtension(pkgName: String) {
        currentDownloads.remove(pkgName)
        novelExtensionManager.uninstallExtension(pkgName)
        refreshExtensions()
    }

    fun findAvailableExtensions() {
        presenterScope.launch {
            novelExtensionManager.findAvailableExtensions()
        }
    }

    fun trustExtension(extension: NovelExtension.Untrusted) {
        presenterScope.launch {
            novelExtensionManager.trust(extension)
        }
    }
}
