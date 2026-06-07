package yokai.presentation.extension

import android.app.Application
import android.content.Context
import android.content.pm.PackageInstaller
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionInstallerJob
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.core.mode.ModeManager
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * ViewModel for the Compose-based Extension screen.
 * 
 * Handles both manga and novel extensions based on current mode.
 * Exposes state as StateFlow for reactive Compose observation.
 */
class ExtensionViewModel(
    private val context: Context = Injekt.get<Application>(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val novelExtensionManager: NovelExtensionManager = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
) : ViewModel() {
    
    private val _state = MutableStateFlow(ExtensionUiState())
    val state: StateFlow<ExtensionUiState> = _state.asStateFlow()
    
    // Events for one-shot actions (navigation, toasts, etc.)
    private val _events = MutableSharedFlow<ExtensionEvent>()
    val events = _events.asSharedFlow()
    
    // Track current downloads: pkgName -> (step, progress)
    private val currentDownloads = mutableMapOf<String, Pair<InstallStep, Int?>>()
    
    // Cache of extensions for quick access
    private var installedExtensions = emptyList<Extension.Installed>()
    private var availableExtensions = emptyList<Extension.Available>()
    private var untrustedExtensions = emptyList<Extension.Untrusted>()
    
    init {
        Logger.d { "[EXT_VM] Initializing ExtensionViewModel" }
        
        // Observe mode changes
        viewModelScope.launch {
            ModeManager.currentMode.collectLatest { mode ->
                Logger.d { "[EXT_VM] Mode changed to: $mode" }
                currentDownloads.clear()
                refreshAvailableExtensions()
                refreshExtensionList()
            }
        }
        
        // Observe manga extension downloads
        viewModelScope.launch {
            extensionManager.downloadSharedFlow.collect { (event, installInfo) ->
                if (ModeManager.isNovelMode()) return@collect
                handleDownloadEvent(event, installInfo.first, installInfo.second)
            }
        }
        
        // Observe novel extension downloads
        viewModelScope.launch {
            novelExtensionManager.downloadSharedFlow.collect { (event, installInfo) ->
                if (!ModeManager.isNovelMode()) return@collect
                handleDownloadEvent(event, installInfo.first, installInfo.second)
            }
        }
        
        // Observe extension list changes
        viewModelScope.launch {
            extensionManager.installedExtensionsFlow.collect {
                if (!ModeManager.isNovelMode()) {
                    installedExtensions = it
                    refreshExtensionList()
                }
            }
        }
        
        viewModelScope.launch {
            novelExtensionManager.installedExtensionsFlow.collect {
                if (ModeManager.isNovelMode()) {
                    installedExtensions = it.map { ext -> ext.toExtension() }
                    refreshExtensionList()
                }
            }
        }
    }
    
    fun onAction(action: ExtensionAction) {
        when (action) {
            is ExtensionAction.Install -> installExtension(action.pkgName)
            is ExtensionAction.Update -> updateExtension(action.pkgName)
            is ExtensionAction.Uninstall -> uninstallExtension(action.pkgName)
            is ExtensionAction.CancelInstall -> cancelInstall(action.pkgName)
            is ExtensionAction.Trust -> trustExtension(action.pkgName, action.versionCode, action.signatureHash)
            is ExtensionAction.OpenDetails -> openExtensionDetails(action.pkgName)
            is ExtensionAction.UpdateAll -> updateAllExtensions()
            is ExtensionAction.SetSortOrder -> setSortOrder(action.order)
        }
    }
    
    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        refreshExtensionList()
    }
    
    fun refresh() {
        viewModelScope.launch {
            refreshAvailableExtensions()
            refreshExtensionList()
        }
    }
    
    private suspend fun refreshAvailableExtensions() {
        if (ModeManager.isNovelMode()) {
            novelExtensionManager.findAvailableExtensions()
        } else {
            extensionManager.findAvailableExtensions()
        }
    }
    
    private fun refreshExtensionList() {
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(isLoading = true) }
            
            val (installed, untrusted, available) = getExtensionsForCurrentMode()
            installedExtensions = installed
            untrustedExtensions = untrusted
            availableExtensions = available
            
            val sections = buildSections(installed, untrusted, available)
            
            _state.update {
                it.copy(
                    isLoading = false,
                    sections = sections,
                )
            }
        }
    }
    
    private fun getExtensionsForCurrentMode(): Triple<List<Extension.Installed>, List<Extension.Untrusted>, List<Extension.Available>> {
        return if (ModeManager.isNovelMode()) {
            Triple(
                novelExtensionManager.installedExtensionsFlow.value.map { it.toExtension() },
                novelExtensionManager.untrustedExtensionsFlow.value.map { it.toExtension() },
                novelExtensionManager.availableExtensionsFlow.value.map { it.toExtension() },
            )
        } else {
            Triple(
                extensionManager.installedExtensionsFlow.value,
                extensionManager.untrustedExtensionsFlow.value,
                extensionManager.availableExtensionsFlow.value,
            )
        }
    }
    
    private fun buildSections(
        installed: List<Extension.Installed>,
        untrusted: List<Extension.Untrusted>,
        available: List<Extension.Available>,
    ): List<ExtensionSection> {
        val sections = mutableListOf<ExtensionSection>()
        val activeLangs = preferences.enabledLanguages().get()
        val showNsfwSources = preferences.showNsfwSources().get()
        val searchQuery = _state.value.searchQuery.lowercase()
        
        // Filter by search query
        val filteredInstalled = installed.filter { 
            (showNsfwSources || !it.isNsfw) && 
            (searchQuery.isEmpty() || it.name.lowercase().contains(searchQuery))
        }
        val filteredUntrusted = untrusted.filter {
            searchQuery.isEmpty() || it.name.lowercase().contains(searchQuery)
        }
        val filteredAvailable = available.filter {
            (it.lang in activeLangs) &&
            (showNsfwSources || !it.isNsfw) &&
            installed.none { inst -> inst.pkgName == it.pkgName } &&
            untrusted.none { untr -> untr.pkgName == it.pkgName } &&
            (searchQuery.isEmpty() || it.name.lowercase().contains(searchQuery))
        }
        
        // Updates section
        val updates = filteredInstalled.filter { it.hasUpdate }.sortedBy { it.name }
        if (updates.isNotEmpty()) {
            sections.add(
                ExtensionSection(
                    title = context.getString(MR.plurals._updates_pending, updates.size, updates.size),
                    itemCount = updates.size,
                    showUpdateAll = updates.size > currentDownloads.size,
                    extensions = updates.map { ext ->
                        val download = currentDownloads[ext.pkgName]
                        ExtensionUiItem.fromExtension(ext, download?.first, download?.second)
                    },
                )
            )
        }
        
        // Installed section
        val sortOrder = InstalledExtensionsOrder.fromPreference(preferences)
        val installedNoUpdates = filteredInstalled.filter { !it.hasUpdate }
            .sortedWith(
                compareBy(
                    { !it.isObsolete },
                    {
                        when (sortOrder) {
                            InstalledExtensionsOrder.Name -> it.name
                            InstalledExtensionsOrder.RecentlyUpdated -> Long.MAX_VALUE.toString()
                            InstalledExtensionsOrder.RecentlyInstalled -> Long.MAX_VALUE.toString()
                            InstalledExtensionsOrder.Language -> it.lang
                        }
                    },
                    { it.name },
                )
            )
        
        if (installedNoUpdates.isNotEmpty() || filteredUntrusted.isNotEmpty()) {
            val allInstalled = installedNoUpdates.map { ext ->
                val download = currentDownloads[ext.pkgName]
                ExtensionUiItem.fromExtension(ext, download?.first, download?.second)
            } + filteredUntrusted.map { ext ->
                ExtensionUiItem.fromExtension(ext)
            }
            
            sections.add(
                ExtensionSection(
                    title = context.getString(MR.strings.installed),
                    itemCount = allInstalled.size,
                    showUpdateAll = false,
                    extensions = allInstalled,
                    sortingOption = sortOrder.ordinal,
                )
            )
        }
        
        // Available sections (grouped by language)
        val availableByLang = filteredAvailable
            .sortedBy { it.name }
            .groupBy { LocaleHelper.getSourceDisplayName(it.lang, context) }
            .toSortedMap()
        
        availableByLang.forEach { (lang, exts) ->
            sections.add(
                ExtensionSection(
                    title = lang,
                    itemCount = exts.size,
                    showUpdateAll = false,
                    extensions = exts.map { ext ->
                        val download = currentDownloads[ext.pkgName]
                        ExtensionUiItem.fromExtension(ext, download?.first, download?.second)
                    },
                )
            )
        }
        
        return sections
    }
    
    private fun handleDownloadEvent(event: String, step: InstallStep, session: Any?) {
        viewModelScope.launch {
            if (event.startsWith("Finished") || event.startsWith("Uninstalled")) {
                if (event.startsWith("Finished")) {
                    currentDownloads.clear()
                }
                refreshExtensionList()
                return@launch
            }
            
            val pkgName = event
            when (step) {
                InstallStep.Installed, InstallStep.Error -> {
                    currentDownloads.remove(pkgName)
                }
                else -> {
                    val progress = when (session) {
                        is PackageInstaller.SessionInfo -> (session.progress * 100).toInt()
                        is Int -> session
                        else -> null
                    }
                    currentDownloads[pkgName] = step to progress
                }
            }
            
            // Update just the affected item in state
            refreshExtensionList()
        }
    }
    
    private fun installExtension(pkgName: String) {
        val extension = availableExtensions.find { it.pkgName == pkgName } ?: return
        
        viewModelScope.launch {
            currentDownloads[pkgName] = InstallStep.Pending to null
            refreshExtensionList()
            
            if (isNovelExtension(pkgName)) {
                val novelExt = novelExtensionManager.availableExtensionsFlow.value
                    .find { it.pkgName == pkgName } ?: return@launch
                    
                val info = NovelExtensionManager.NovelExtensionInfo(
                    apkName = novelExt.apkName,
                    pkgName = novelExt.pkgName,
                    repoUrl = novelExt.repoUrl ?: "",
                )
                novelExtensionManager.installExtension(info, viewModelScope).collect { /* handled by flow */ }
            } else {
                extensionManager.installExtension(
                    ExtensionManager.ExtensionInfo(extension),
                    viewModelScope,
                ).collect { /* handled by flow */ }
            }
        }
    }
    
    private fun updateExtension(pkgName: String) {
        val availableExt = availableExtensions.find { it.pkgName == pkgName }
            ?: novelExtensionManager.availableExtensionsFlow.value.find { it.pkgName == pkgName }?.toExtension()
            ?: return
        installExtension(availableExt.pkgName)
    }
    
    private fun updateAllExtensions() {
        val updates = installedExtensions.filter { it.hasUpdate }
        if (updates.isEmpty()) return
        
        viewModelScope.launch {
            updates.forEach { ext ->
                currentDownloads[ext.pkgName] = InstallStep.Pending to null
            }
            refreshExtensionList()
            
            // Separate novel and manga extensions
            val (novelExts, mangaExts) = updates.partition { isNovelExtension(it.pkgName) }
            
            // Start manga extension updates
            if (mangaExts.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    _events.emit(ExtensionEvent.StartBatchInstall(mangaExts.mapNotNull { ext ->
                        extensionManager.availableExtensionsFlow.value.find { it.pkgName == ext.pkgName }
                    }))
                }
            }
            
            // Start novel extension updates
            novelExts.forEach { ext ->
                val availableNovel = novelExtensionManager.availableExtensionsFlow.value
                    .find { it.pkgName == ext.pkgName }
                if (availableNovel != null) {
                    val info = NovelExtensionManager.NovelExtensionInfo(
                        apkName = availableNovel.apkName,
                        pkgName = availableNovel.pkgName,
                        repoUrl = availableNovel.repoUrl ?: "",
                    )
                    novelExtensionManager.installExtension(info, viewModelScope).collect { /* handled */ }
                }
            }
        }
    }
    
    private fun uninstallExtension(pkgName: String) {
        viewModelScope.launch {
            if (isNovelExtension(pkgName)) {
                novelExtensionManager.uninstallExtension(pkgName)
            } else {
                extensionManager.uninstallExtension(pkgName)
            }
        }
    }
    
    private fun cancelInstall(pkgName: String) {
        viewModelScope.launch {
            if (isNovelExtension(pkgName)) {
                novelExtensionManager.cancelInstallation(pkgName)
            } else {
                // Manga extensions need session ID - not easily accessible here
                // For now, just remove from downloads
                currentDownloads.remove(pkgName)
                refreshExtensionList()
            }
        }
    }
    
    private fun trustExtension(pkgName: String, versionCode: Long, signatureHash: String) {
        viewModelScope.launch {
            if (isNovelExtension(pkgName)) {
                novelExtensionManager.trust(pkgName, versionCode, signatureHash)
            } else {
                extensionManager.trust(pkgName, versionCode, signatureHash)
            }
        }
    }
    
    private fun openExtensionDetails(pkgName: String) {
        viewModelScope.launch {
            val ext = installedExtensions.find { it.pkgName == pkgName } ?: return@launch
            _events.emit(ExtensionEvent.OpenDetails(pkgName))
        }
    }
    
    private fun setSortOrder(order: Int) {
        preferences.installedExtensionsOrder().set(order)
        refreshExtensionList()
    }
    
    private fun isNovelExtension(pkgName: String): Boolean {
        return novelExtensionManager.installedExtensionsFlow.value.any { it.pkgName == pkgName } ||
            novelExtensionManager.untrustedExtensionsFlow.value.any { it.pkgName == pkgName } ||
            novelExtensionManager.availableExtensionsFlow.value.any { it.pkgName == pkgName }
    }
    
    // Conversion functions for novel extensions
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
            sources = emptyList(),
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
}

/**
 * One-shot events from ViewModel to UI.
 */
sealed interface ExtensionEvent {
    data class OpenDetails(val pkgName: String) : ExtensionEvent
    data class ShowError(val message: String) : ExtensionEvent
    data class StartBatchInstall(val extensions: List<Extension.Available>) : ExtensionEvent
}
