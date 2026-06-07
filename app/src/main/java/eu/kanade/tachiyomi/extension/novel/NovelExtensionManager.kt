package eu.kanade.tachiyomi.extension.novel

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.novel.api.NovelExtensionApi
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.model.NovelLoadResult
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionInstallInfo
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionInstaller
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionLoader
import eu.kanade.tachiyomi.util.system.launchNow
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.parcelize.Parcelize
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.extension.interactor.TrustExtension
import yokai.source.novel.NovelMainAPI
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The manager of novel extensions installed as APKs.
 * Handles retrieval, installation, updating and removal of novel extensions.
 * Parallel to ExtensionManager for manga extensions.
 */
class NovelExtensionManager(
    private val context: Context,
    private val preferences: PreferencesHelper = Injekt.get(),
    private val trustExtension: TrustExtension = Injekt.get(),
) {

    /**
     * API where all the available novel extensions can be found.
     */
    private val api = NovelExtensionApi()

    /**
     * The installer which installs, updates and uninstalls the novel extensions.
     */
    private val installer by lazy { NovelExtensionInstaller(context) }

    private val iconMap = mutableMapOf<String, Drawable>()

    val downloadSharedFlow = installer.downloadSharedFlow

    private fun isDownloadActive(pkgName: String): Boolean {
        return installer.activeDownloads[pkgName] == true
    }

    /**
     * Relay used to notify the installed novel extensions.
     */
    private val _installedExtensionsFlow = MutableStateFlow(emptyList<NovelExtension.Installed>())
    val installedExtensionsFlow = _installedExtensionsFlow.asStateFlow()

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages().isSet()

    fun getAppIconForSource(source: NovelMainAPI): Drawable? {
        return getAppIconForSource(source.id)
    }

    fun getAppIconForSource(sourceId: Long): Drawable? {
        val pkgName = _installedExtensionsFlow.value
            .find { ext -> ext.sources.any { it.id == sourceId } }?.pkgName
        return if (pkgName != null) {
            try {
                return iconMap.getOrPut(pkgName) {
                    NovelExtensionLoader.getExtensionPackageInfoFromPkgName(context, pkgName)!!.applicationInfo!!
                        .loadIcon(context.packageManager)
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Get the icon URL for a novel source. Used for async loading in UI.
     * Prefers installed extension iconUrl, falls back to available extension.
     */
    fun getIconUrlForSource(sourceId: Long): String? {
        // First try installed extension
        val installedExt = _installedExtensionsFlow.value
            .find { ext -> ext.sources.any { it.id == sourceId } }
        if (!installedExt?.iconUrl.isNullOrEmpty()) {
            return installedExt?.iconUrl
        }
        
        // Fall back to available extension
        val availableExt = _availableExtensionsFlow.value
            .find { ext -> ext.sources.any { it.id == sourceId } }
        return availableExt?.iconUrl
    }

    /**
     * Relay used to notify the available novel extensions.
     */
    private val _availableExtensionsFlow = MutableStateFlow(emptyList<NovelExtension.Available>())
    val availableExtensionsFlow = _availableExtensionsFlow.asStateFlow()

    private var availableSources = hashMapOf<Long, NovelExtension.AvailableNovelSource>()

    private val _untrustedExtensionsFlow = MutableStateFlow(emptyList<NovelExtension.Untrusted>())
    val untrustedExtensionsFlow = _untrustedExtensionsFlow.asStateFlow()

    init {
        initExtensions()
        NovelExtensionInstallReceiver(InstallationListener()).register(context)
    }

    /**
     * Loads and registers the installed novel extensions.
     */
    private fun initExtensions() {
        val extensions = NovelExtensionLoader.loadExtensions(context)
        Logger.d { "[NOVEL_EXT_MGR] Loaded ${extensions.size} extensions" }
        extensions.forEach { result ->
            Logger.d { "[NOVEL_EXT_MGR] Result: $result" }
        }

        val installed = extensions
            .filterIsInstance<NovelLoadResult.Success>()
            .map { it.extension }
        Logger.d { "[NOVEL_EXT_MGR] Installed extensions: ${installed.map { it.pkgName }}" }
        _installedExtensionsFlow.value = installed

        val untrusted = extensions
            .filterIsInstance<NovelLoadResult.Untrusted>()
            .map { it.extension }
        Logger.d { "[NOVEL_EXT_MGR] Untrusted extensions: ${untrusted.map { it.pkgName }}" }
        _untrustedExtensionsFlow.value = untrusted
    }

    fun isInstalledByApp(extension: NovelExtension.Available): Boolean {
        return try {
            NovelExtensionLoader.getExtensionPackageInfoFromPkgName(context, extension.pkgName) != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getExtensionUpdates(force: Boolean) {
        val lastCheckKey = "last_novel_ext_check"
        val updateCountKey = "novel_extension_updates_count"
        
        val sharedPrefs = context.getSharedPreferences("novel_extension_prefs", Context.MODE_PRIVATE)
        
        if ((force && availableExtensionsFlow.value.isEmpty()) ||
            Date().time >= sharedPrefs.getLong(lastCheckKey, 0) + TimeUnit.HOURS.toMillis(6)
        ) {
            withIOContext {
                try {
                    findAvailableExtensions()
                    val pendingUpdates = NovelExtensionApi().checkForUpdates(
                        context,
                        availableExtensionsFlow.value.takeIf { it.isNotEmpty() },
                    )
                    sharedPrefs.edit().putInt(updateCountKey, pendingUpdates.size).apply()
                    sharedPrefs.edit().putLong(lastCheckKey, Date().time).apply()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Finds the available novel extensions in the API and updates [availableExtensionsFlow].
     */
    suspend fun findAvailableExtensions() {
        val extensions: List<NovelExtension.Available> = try {
            api.findExtensions()
        } catch (e: Exception) {
            Logger.e(e) { "Failed to find available novel extensions" }
            emptyList()
        }

        enableAdditionalSubLanguages(extensions)

        _availableExtensionsFlow.value = extensions
        updatedInstalledExtensionsStatuses(extensions)
        setupAvailableSourcesMap()
        emitToInstaller("Finished/Available/${extensions.size}", (InstallStep.Done to null))
    }

    private fun enableAdditionalSubLanguages(extensions: List<NovelExtension.Available>) {
        if (subLanguagesEnabledOnFirstRun || extensions.isEmpty()) {
            return
        }

        val availableLanguages = extensions
            .flatMap(NovelExtension.Available::sources)
            .distinctBy(NovelExtension.AvailableNovelSource::lang)
            .map(NovelExtension.AvailableNovelSource::lang)

        val deviceLanguage = Locale.getDefault().language
        val defaultLanguages = preferences.enabledLanguages().defaultValue()
        val languagesToEnable = availableLanguages.filter {
            it != deviceLanguage && it.startsWith(deviceLanguage) && !it.startsWith("en")
        }

        preferences.enabledLanguages().set(defaultLanguages + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    private fun setupAvailableSourcesMap() {
        availableSources = hashMapOf()
        _availableExtensionsFlow.value.map { it.sources }.flatten().forEach {
            availableSources[it.id] = it
        }
    }

    fun getStubSource(id: Long) = availableSources[id]

    private fun updatedInstalledExtensionsStatuses(availableExtensions: List<NovelExtension.Available>) {
        Logger.d { "[NOVEL_EXT_MGR] updatedInstalledExtensionsStatuses called with ${availableExtensions.size} available extensions" }
        if (availableExtensions.isEmpty()) {
            return
        }
        val mutInstalledExtensions = installedExtensionsFlow.value.toMutableList()
        Logger.d { "[NOVEL_EXT_MGR] Current installed extensions: ${mutInstalledExtensions.size}" }
        var changed = false
        for ((index, installedExt) in mutInstalledExtensions.withIndex()) {
            val pkgName = installedExt.pkgName
            val availableExt = availableExtensions.find { it.pkgName == pkgName }
            Logger.d { "[NOVEL_EXT_MGR] Checking installed ext: $pkgName, found available: ${availableExt != null}, availableIconUrl: ${availableExt?.iconUrl}" }

            val shouldBeObsolete = (availableExt == null)
            if (shouldBeObsolete != installedExt.isObsolete) {
                mutInstalledExtensions[index] = installedExt.copy(isObsolete = shouldBeObsolete)
                changed = true
            }
            if (availableExt != null) {
                val hasUpdate = installedExt.updateExists(availableExt)
                Logger.d { "[NOVEL_EXT_MGR] Update check: $pkgName installedVer=${installedExt.versionCode}/${installedExt.versionName} availableVer=${availableExt.versionCode}/${availableExt.versionName} hasUpdate=$hasUpdate" }
                if (installedExt.hasUpdate != hasUpdate) {
                    mutInstalledExtensions[index] = installedExt.copy(
                        hasUpdate = hasUpdate,
                        repoUrl = availableExt.repoUrl,
                        iconUrl = availableExt.iconUrl,
                    )
                } else {
                    mutInstalledExtensions[index] = installedExt.copy(
                        repoUrl = availableExt.repoUrl,
                        iconUrl = availableExt.iconUrl,
                    )
                }
                Logger.d { "[NOVEL_EXT_MGR] Updated installed ext $pkgName with iconUrl: ${availableExt.iconUrl}" }
                changed = true
            }
        }
        if (changed) {
            Logger.d { "[NOVEL_EXT_MGR] Emitting ${mutInstalledExtensions.size} updated installed extensions" }
            _installedExtensionsFlow.value = mutInstalledExtensions
        }
    }

    /**
     * Returns a flow of the installation process for the given novel extension.
     */
    suspend fun installExtension(extension: NovelExtensionInfo, scope: CoroutineScope): Flow<NovelExtensionInstallInfo> {
        return installer.downloadAndInstall(api.getApkUrl(extension), extension, scope)
    }

    fun cleanUpInstallation(pkgName: String) {
        installer.cleanUpInstallation(pkgName)
    }

    fun setInstallationResult(pkgName: String, result: Boolean) {
        installer.setInstallationResult(pkgName, result)
    }

    fun cancelInstallation(pkgName: String) {
        installer.cancelInstallation(pkgName)
    }

    /**
     * Sets the installation step to installing for the given package.
     */
    fun setInstalling(pkgName: String, sessionId: Int) {
        installer.setInstalling(pkgName, sessionId)
    }

    /**
     * Returns the installation info for the given package name.
     */
    fun getInstallInfo(pkgName: String): NovelExtensionInstallInfo? {
        val installStep = when {
            installer.activeDownloads[pkgName] == true -> InstallStep.Downloading
            else -> return null
        }
        return installStep to null
    }

    /**
     * Uninstalls the novel extension that matches the given package name.
     */
    fun uninstallExtension(pkgName: String) {
        installer.uninstallApk(pkgName)
    }

    /**
     * Trust an untrusted novel extension.
     */
    fun trust(extension: NovelExtension.Untrusted) {
        val untrustedPkgName = extension.pkgName
        trustExtension.trust(untrustedPkgName, extension.versionCode, extension.signatureHash)

        val nowTrustedExtensions = _untrustedExtensionsFlow.value
            .filter { it.pkgName == untrustedPkgName && it.signatureHash == extension.signatureHash }
        _untrustedExtensionsFlow.value -= nowTrustedExtensions

        launchNow {
            nowTrustedExtensions
                .map { extension ->
                    async {
                        NovelExtensionLoader.loadExtensionFromPkgName(
                            context,
                            extension.pkgName,
                        )
                    }.await()
                }
                .filterIsInstance<NovelLoadResult.Success>()
                .forEach { registerNewExtension(it.extension) }
        }
    }

    /**
     * Trust an untrusted novel extension by package name, version code, and signature hash.
     */
    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        val extension = _untrustedExtensionsFlow.value
            .find { it.pkgName == pkgName && it.versionCode == versionCode && it.signatureHash == signatureHash }
        if (extension != null) {
            trust(extension)
        }
    }

    /**
     * Get all loaded novel sources from installed extensions.
     */
    fun getInstalledSources(): List<NovelMainAPI> {
        return _installedExtensionsFlow.value.flatMap { it.sources }
    }

    /**
     * Get a specific novel source by ID.
     */
    fun getSourceById(id: Long): NovelMainAPI? {
        return getInstalledSources().find { it.id == id }
    }

    /**
     * Registers a newly installed novel extension.
     * Also looks up and sets the iconUrl from available extensions if not already set.
     */
    private fun registerNewExtension(extension: NovelExtension.Installed) {
        Logger.d { "[NOVEL_EXT_MGR] registerNewExtension: ${extension.pkgName}, current iconUrl=${extension.iconUrl}" }
        Logger.d { "[NOVEL_EXT_MGR] Available extensions count: ${_availableExtensionsFlow.value.size}" }
        
        // Look up iconUrl from available extensions if not set
        val extensionWithIcon = if (extension.iconUrl.isNullOrEmpty()) {
            val availableExt = _availableExtensionsFlow.value.find { it.pkgName == extension.pkgName }
            Logger.d { "[NOVEL_EXT_MGR] Found matching available ext: ${availableExt?.pkgName}, iconUrl=${availableExt?.iconUrl}" }
            if (availableExt != null) {
                extension.copy(
                    iconUrl = availableExt.iconUrl,
                    repoUrl = availableExt.repoUrl,
                )
            } else {
                extension
            }
        } else {
            extension
        }
        Logger.d { "[NOVEL_EXT_MGR] Final iconUrl for ${extension.pkgName}: ${extensionWithIcon.iconUrl}" }
        _installedExtensionsFlow.value += extensionWithIcon
        setupAvailableSourcesMap()

        // Emit event to trigger UI refresh (like manga ExtensionManager does)
        emitToInstaller(
            "Finished/${extension.pkgName}",
            InstallStep.Installed to null,
        )
    }

    /**
     * Registers a replaced novel extension.
     * Also looks up and sets the iconUrl from available extensions if not already set.
     */
    private fun registerUpdatedExtension(extension: NovelExtension.Installed) {
        // Look up iconUrl from available extensions if not set
        val extensionWithIcon = if (extension.iconUrl.isNullOrEmpty()) {
            val availableExt = _availableExtensionsFlow.value.find { it.pkgName == extension.pkgName }
            if (availableExt != null) {
                extension.copy(
                    iconUrl = availableExt.iconUrl,
                    repoUrl = availableExt.repoUrl,
                )
            } else {
                extension
            }
        } else {
            extension
        }
        
        val mutInstalledExtensions = _installedExtensionsFlow.value.toMutableList()
        val oldExtensionIndex = mutInstalledExtensions.indexOfFirst { it.pkgName == extensionWithIcon.pkgName }
        if (oldExtensionIndex != -1) {
            mutInstalledExtensions[oldExtensionIndex] = extensionWithIcon
        } else {
            mutInstalledExtensions.add(extensionWithIcon)
        }
        _installedExtensionsFlow.value = mutInstalledExtensions
        setupAvailableSourcesMap()
    }

    /**
     * Unregisters the novel extension with the given package name.
     */
    private fun unregisterExtension(pkgName: String) {
        val installedExtension = _installedExtensionsFlow.value.find { it.pkgName == pkgName }
        if (installedExtension != null) {
            _installedExtensionsFlow.value -= installedExtension
        }
        val untrustedExtension = _untrustedExtensionsFlow.value.find { it.pkgName == pkgName }
        if (untrustedExtension != null) {
            _untrustedExtensionsFlow.value -= untrustedExtension
        }
        setupAvailableSourcesMap()
    }

    private fun emitToInstaller(pkgName: String, content: NovelExtensionInstallInfo) {
        launchNow { installer._downloadsSharedFlow.emit(pkgName to content) }
    }

    /**
     * Check if an extension has an update available.
     */
    private fun NovelExtension.Installed.updateExists(availableExtension: NovelExtension.Available): Boolean {
        return (availableExtension.versionCode > versionCode || availableExtension.libVersion > libVersion)
    }

    /**
     * Listener for extension installation events.
     */
    private inner class InstallationListener : NovelExtensionInstallReceiver.Listener {
        override fun onExtensionInstalled(pkgName: String) {
            Logger.d { "[NOVEL_EXT_MGR] onExtensionInstalled: $pkgName" }
            launchNow {
                Logger.d { "[NOVEL_EXT_MGR] Loading extension from pkgName: $pkgName" }
                val result = NovelExtensionLoader.loadExtensionFromPkgName(context, pkgName)
                Logger.d { "[NOVEL_EXT_MGR] Load result: ${result::class.simpleName}" }
                when (result) {
                    is NovelLoadResult.Success -> {
                        Logger.d { "[NOVEL_EXT_MGR] Extension loaded successfully: ${result.extension.name}" }
                        registerNewExtension(result.extension)
                        Logger.d { "[NOVEL_EXT_MGR] After registerNewExtension, installed count: ${_installedExtensionsFlow.value.size}" }
                        // Notify installer of success
                        installer.setInstallationResult(pkgName, true)
                    }
                    is NovelLoadResult.Untrusted -> {
                        Logger.d { "[NOVEL_EXT_MGR] Extension is untrusted: ${result.extension.pkgName}" }
                        _untrustedExtensionsFlow.value += result.extension
                    }
                    else -> {
                        Logger.e { "[NOVEL_EXT_MGR] Unexpected load result: ${result::class.simpleName}" }
                        installer.setInstallationResult(pkgName, false)
                    }
                }
            }
        }

        override fun onExtensionUpdated(pkgName: String) {
            Logger.d { "Novel extension updated: $pkgName" }
            launchNow {
                val result = NovelExtensionLoader.loadExtensionFromPkgName(context, pkgName)
                when (result) {
                    is NovelLoadResult.Success -> {
                        registerUpdatedExtension(result.extension)
                        installer.setInstallationResult(pkgName, true)
                    }
                    is NovelLoadResult.Untrusted -> {
                        _untrustedExtensionsFlow.value += result.extension
                    }
                    else -> {
                        installer.setInstallationResult(pkgName, false)
                    }
                }
            }
        }

        override fun onExtensionUntrusted(pkgName: String) {
            Logger.d { "Novel extension untrusted: $pkgName" }
            launchNow {
                val result = NovelExtensionLoader.loadExtensionFromPkgName(context, pkgName)
                if (result is NovelLoadResult.Untrusted) {
                    _untrustedExtensionsFlow.value += result.extension
                }
            }
        }

        override fun onPackageUninstalled(pkgName: String) {
            Logger.d { "Novel extension uninstalled: $pkgName" }
            unregisterExtension(pkgName)
            emitToInstaller("Uninstalled/$pkgName", InstallStep.Done to null)
        }

        override fun onInstallationError(pkgName: String) {
            Logger.e { "Novel extension installation error: $pkgName" }
            installer.setInstallationResult(pkgName, false)
        }
    }

    /**
     * Data class for novel extension information used in installation.
     */
    @Parcelize
    data class NovelExtensionInfo(
        val apkName: String,
        val pkgName: String,
        val repoUrl: String,
    ) : Parcelable
}
