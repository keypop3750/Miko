package yokai.presentation.extension

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep

/**
 * Represents the UI state for the extension screen.
 */
data class ExtensionUiState(
    val isLoading: Boolean = true,
    val sections: List<ExtensionSection> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null,
)

/**
 * Represents a section of extensions (Updates, Installed, or a language group).
 */
data class ExtensionSection(
    val title: String,
    val itemCount: Int,
    val showUpdateAll: Boolean = false,
    val extensions: List<ExtensionUiItem>,
    val sortingOption: Int? = null, // Only for Installed section
)

/**
 * UI-friendly representation of an extension item.
 * 
 * Decoupled from FlexibleAdapter - pure data class for Compose.
 */
data class ExtensionUiItem(
    val pkgName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val signatureHash: String,
    val lang: String,
    val isNsfw: Boolean,
    val installStep: InstallStep?,
    val installProgress: Int?, // 0-100 for download progress
    val hasUpdate: Boolean,
    val isObsolete: Boolean,
    val isInstalled: Boolean,
    val isUntrusted: Boolean,
    val iconUrl: String?, // For available extensions
    val icon: Drawable?, // For installed extensions
    val repoUrl: String?,
    val sources: List<String>, // Source names for display
) {
    companion object {
        fun fromExtension(
            extension: Extension,
            installStep: InstallStep? = null,
            installProgress: Int? = null,
        ): ExtensionUiItem {
            return when (extension) {
                is Extension.Installed -> ExtensionUiItem(
                    pkgName = extension.pkgName,
                    name = extension.name,
                    versionName = extension.versionName,
                    versionCode = extension.versionCode,
                    signatureHash = "",  // Installed extensions are already trusted
                    lang = extension.lang,
                    isNsfw = extension.isNsfw,
                    installStep = installStep,
                    installProgress = installProgress,
                    hasUpdate = extension.hasUpdate,
                    isObsolete = extension.isObsolete,
                    isInstalled = true,
                    isUntrusted = false,
                    iconUrl = null,
                    icon = extension.icon,
                    repoUrl = extension.repoUrl,
                    sources = extension.sources.map { it.name },
                )
                is Extension.Available -> ExtensionUiItem(
                    pkgName = extension.pkgName,
                    name = extension.name,
                    versionName = extension.versionName,
                    versionCode = extension.versionCode,
                    signatureHash = "",  // Not applicable for available extensions
                    lang = extension.lang,
                    isNsfw = extension.isNsfw,
                    installStep = installStep,
                    installProgress = installProgress,
                    hasUpdate = false,
                    isObsolete = false,
                    isInstalled = false,
                    isUntrusted = false,
                    iconUrl = extension.iconUrl,
                    icon = null,
                    repoUrl = extension.repoUrl,
                    sources = extension.sources.map { it.name },
                )
                is Extension.Untrusted -> ExtensionUiItem(
                    pkgName = extension.pkgName,
                    name = extension.name,
                    versionName = extension.versionName,
                    versionCode = extension.versionCode,
                    signatureHash = extension.signatureHash,
                    lang = extension.lang ?: "",
                    isNsfw = extension.isNsfw,
                    installStep = installStep,
                    installProgress = installProgress,
                    hasUpdate = false,
                    isObsolete = false,
                    isInstalled = false,
                    isUntrusted = true,
                    iconUrl = null,
                    icon = null,
                    repoUrl = null,
                    sources = emptyList(),
                )
            }
        }
    }
}

/**
 * Actions that can be performed on extensions.
 */
sealed interface ExtensionAction {
    data class Install(val pkgName: String) : ExtensionAction
    data class Update(val pkgName: String) : ExtensionAction
    data class Uninstall(val pkgName: String) : ExtensionAction
    data class CancelInstall(val pkgName: String) : ExtensionAction
    data class Trust(val pkgName: String, val versionCode: Long, val signatureHash: String) : ExtensionAction
    data class OpenDetails(val pkgName: String) : ExtensionAction
    data object UpdateAll : ExtensionAction
    data class SetSortOrder(val order: Int) : ExtensionAction
}
