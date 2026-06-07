package eu.kanade.tachiyomi.ui.extension.novel

import android.content.pm.PackageInstaller
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractSectionableItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionInstallInfo

/**
 * Item that contains novel extension information.
 *
 * @param extension Instance of [NovelExtension] containing extension information.
 * @param header The header for this item.
 */
data class NovelExtensionItem(
    val extension: NovelExtension,
    val header: NovelExtensionGroupItem? = null,
    val installStep: InstallStep? = null,
    val session: PackageInstaller.SessionInfo? = null,
    val downloadProgress: Int? = null,  // Direct progress for OkHttp downloads
) : AbstractSectionableItem<NovelExtensionHolder, NovelExtensionGroupItem>(header) {

    constructor(
        extension: NovelExtension,
        header: NovelExtensionGroupItem? = null,
        installInfo: NovelExtensionInstallInfo?,
    ) : this(
        extension, 
        header, 
        installInfo?.first, 
        installInfo?.second as? PackageInstaller.SessionInfo, 
        installInfo?.second as? Int
    )

    val sessionProgress: Int?
        get() = downloadProgress ?: session?.progress?.times(100)?.toInt()

    /**
     * Returns the layout resource of this item.
     */
    override fun getLayoutRes(): Int {
        return R.layout.extension_card_item
    }

    /**
     * Creates a new view holder for this item.
     */
    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): NovelExtensionHolder {
        return NovelExtensionHolder(view, adapter as NovelExtensionAdapter)
    }

    /**
     * Binds this item to the given view holder.
     */
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: NovelExtensionHolder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        if (payloads.isNullOrEmpty()) {
            holder.bind(this)
        } else {
            holder.bindButton(this)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NovelExtensionItem
        // Include iconUrl in comparison so that updates to iconUrl trigger rebinding
        val thisIconUrl = (extension as? NovelExtension.Installed)?.iconUrl ?: (extension as? NovelExtension.Available)?.iconUrl
        val otherIconUrl = (other.extension as? NovelExtension.Installed)?.iconUrl ?: (other.extension as? NovelExtension.Available)?.iconUrl
        return extension.pkgName == other.extension.pkgName && thisIconUrl == otherIconUrl
    }

    override fun hashCode(): Int {
        val iconUrl = (extension as? NovelExtension.Installed)?.iconUrl ?: (extension as? NovelExtension.Available)?.iconUrl
        return 31 * extension.pkgName.hashCode() + (iconUrl?.hashCode() ?: 0)
    }
}
