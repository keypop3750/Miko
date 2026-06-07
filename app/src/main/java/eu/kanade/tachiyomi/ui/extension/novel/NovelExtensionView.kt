package eu.kanade.tachiyomi.ui.extension.novel

import android.content.Context

/**
 * Interface for novel extension views (bottom sheet and controller).
 * This allows the presenter to work with both view types.
 */
interface NovelExtensionView {
    fun getViewContext(): Context?
    fun setExtensions(extensions: List<NovelExtensionItem>, updateController: Boolean = true)
    fun downloadUpdate(item: NovelExtensionItem)
}
