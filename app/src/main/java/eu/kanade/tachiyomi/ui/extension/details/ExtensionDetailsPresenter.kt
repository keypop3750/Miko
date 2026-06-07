package eu.kanade.tachiyomi.ui.extension.details

import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.util.system.launchUI
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ExtensionDetailsPresenter(
    val pkgName: String,
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val novelExtensionManager: NovelExtensionManager = Injekt.get(),
) : BaseCoroutinePresenter<ExtensionDetailsController>() {

    // Check both manga and novel extensions
    val extension: Extension.Installed? = extensionManager.installedExtensionsFlow.value.find { it.pkgName == pkgName }
    val novelExtension: NovelExtension.Installed? = novelExtensionManager.installedExtensionsFlow.value.find { it.pkgName == pkgName }
    
    val isNovelExtension: Boolean = novelExtension != null

    override fun onCreate() {
        super.onCreate()
        bindToUninstalledExtension()
    }

    private fun bindToUninstalledExtension() {
        // Watch manga extensions
        extensionManager.installedExtensionsFlow
            .drop(1)
            .onEach { extensions ->
                if (extensions.none { it.pkgName == pkgName } && !isNovelExtension) {
                    presenterScope.launchUI { view?.onExtensionUninstalled() }
                }
            }
            .launchIn(presenterScope)
        
        // Watch novel extensions
        novelExtensionManager.installedExtensionsFlow
            .drop(1)
            .onEach { extensions ->
                if (extensions.none { it.pkgName == pkgName } && isNovelExtension) {
                    presenterScope.launchUI { view?.onExtensionUninstalled() }
                }
            }
            .launchIn(presenterScope)
    }

    fun uninstallExtension() {
        if (isNovelExtension) {
            novelExtension?.let { novelExtensionManager.uninstallExtension(it.pkgName) }
        } else {
            extension?.let { extensionManager.uninstallExtension(it.pkgName) }
        }
    }
}
