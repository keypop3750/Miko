package eu.kanade.tachiyomi.source

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.source.novel.NovelSourceWrapper
import eu.kanade.tachiyomi.source.online.HttpSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Source.includeLangInName(enabledLanguages: Set<String>, extensionManager: ExtensionManager? = null): Boolean {
    val httpSource = this as? HttpSource ?: return true
    val extManager = extensionManager ?: Injekt.get()
    val allExt = httpSource.getExtension(extManager)?.lang == "all"
    val onlyAll = httpSource.extOnlyHasAllLanguage(extManager)
    val isMultiLingual = enabledLanguages.filterNot { it == "all" }.size > 1
    return (isMultiLingual && allExt) || (lang == "all" && !onlyAll)
}

fun Source.nameBasedOnEnabledLanguages(enabledLanguages: Set<String>, extensionManager: ExtensionManager? = null): String {
    return if (includeLangInName(enabledLanguages, extensionManager)) toString() else name
}

/**
 * Get the icon for a source. For novel sources, uses NovelExtensionManager.
 * For manga sources, returns extension icons from ExtensionManager.
 */
fun Source.icon(): Drawable? {
    // Handle novel sources - get icon from NovelExtensionManager
    if (this is NovelSourceWrapper) {
        // NovelSourceWrapper exposes id, use that to find the icon
        return Injekt.get<NovelExtensionManager>().getAppIconForSource(this.id)
    }
    
    // Default to ExtensionManager for manga sources
    return Injekt.get<ExtensionManager>().getAppIconForSource(this)
}

/**
 * Get the icon URL for a source. Returns URL for novel sources that support it.
 * Returns null for manga sources (they use drawable icons).
 */
fun Source.iconUrl(): String? {
    if (this is NovelSourceWrapper) {
        return Injekt.get<NovelExtensionManager>().getIconUrlForSource(this.id)
    }
    return null
}

/**
 * Check if this source is a novel source.
 */
fun Source.isNovelSource(): Boolean = this is NovelSourceWrapper

fun HttpSource.getExtension(extensionManager: ExtensionManager? = null): Extension.Installed? =
    (extensionManager ?: Injekt.get()).installedExtensionsFlow.value.find { it.sources.contains(this) }

fun HttpSource.extOnlyHasAllLanguage(extensionManager: ExtensionManager? = null) =
    getExtension(extensionManager)?.sources?.all { it.lang == "all" } ?: true
