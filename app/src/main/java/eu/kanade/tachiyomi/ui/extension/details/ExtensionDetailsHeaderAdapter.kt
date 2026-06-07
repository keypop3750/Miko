package eu.kanade.tachiyomi.ui.extension.details

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.CoverViewTarget
import eu.kanade.tachiyomi.databinding.ExtensionDetailHeaderBinding
import eu.kanade.tachiyomi.ui.extension.getApplicationIcon
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setText
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

class ExtensionDetailsHeaderAdapter(private val presenter: ExtensionDetailsPresenter) :
    RecyclerView.Adapter<ExtensionDetailsHeaderAdapter.HeaderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val view = parent.inflate(R.layout.extension_detail_header)
        return HeaderViewHolder(view)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind()
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.extension_detail_header
    }

    override fun getItemId(position: Int): Long {
        return presenter.pkgName.hashCode().toLong()
    }

    inner class HeaderViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val binding = ExtensionDetailHeaderBinding.bind(view)
            val context = view.context
            
            // Handle both manga and novel extensions
            if (presenter.isNovelExtension) {
                val novelExtension = presenter.novelExtension ?: return
                
                // Load icon from URL if available, otherwise use default
                if (!novelExtension.iconUrl.isNullOrEmpty()) {
                    binding.extensionIcon.load(novelExtension.iconUrl) {
                        target(CoverViewTarget(binding.extensionIcon))
                    }
                } else if (novelExtension.icon != null) {
                    binding.extensionIcon.setImageDrawable(novelExtension.icon)
                }
                
                binding.extensionTitle.text = novelExtension.name
                binding.extensionVersion.text = context.getString(MR.strings.version_, novelExtension.versionName)
                binding.extensionLang.text = context.getString(MR.strings.language_, LocaleHelper.getSourceDisplayName(novelExtension.lang, context))
                binding.extensionNsfw.isVisible = novelExtension.isNsfw
                binding.extensionPkg.text = novelExtension.pkgName

                binding.extensionUninstallButton.setOnClickListener {
                    // Novel extensions are always private (not shared)
                    context.materialAlertDialog()
                        .setTitle(novelExtension.name)
                        .setPositiveButton(MR.strings.remove) { _, _ ->
                            presenter.uninstallExtension()
                        }
                        .setNegativeButton(AR.string.cancel, null)
                        .show()
                }

                // Novel extensions don't have system app info
                binding.extensionAppInfoButton.isVisible = false
                binding.extensionUninstallButton.text = context.getString(MR.strings.remove)

                if (novelExtension.isObsolete) {
                    binding.extensionWarningBanner.isVisible = true
                    binding.extensionWarningBanner.setText(MR.strings.obsolete_extension_message)
                }
            } else {
                val extension = presenter.extension ?: return

                extension.getApplicationIcon(context)?.let { binding.extensionIcon.setImageDrawable(it) }
                binding.extensionTitle.text = extension.name
                binding.extensionVersion.text = context.getString(MR.strings.version_, extension.versionName)
                binding.extensionLang.text = context.getString(MR.strings.language_, LocaleHelper.getSourceDisplayName(extension.lang, context))
                binding.extensionNsfw.isVisible = extension.isNsfw
                binding.extensionPkg.text = extension.pkgName

                binding.extensionUninstallButton.setOnClickListener {
                    if (extension.isShared) {
                        presenter.uninstallExtension()
                    } else {
                        context.materialAlertDialog()
                            .setTitle(extension.name)
                            .setPositiveButton(MR.strings.remove) { _, _ ->
                                presenter.uninstallExtension()
                            }
                            .setNegativeButton(AR.string.cancel, null)
                            .show()
                    }
                }

                binding.extensionAppInfoButton.setOnClickListener {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", presenter.pkgName, null)
                    }
                    it.context.startActivity(intent)
                }

                binding.extensionAppInfoButton.isVisible = extension.isShared
                if (!extension.isShared) {
                    binding.extensionUninstallButton.text = context.getString(MR.strings.remove)
                }

                if (extension.isObsolete) {
                    binding.extensionWarningBanner.isVisible = true
                    binding.extensionWarningBanner.setText(MR.strings.obsolete_extension_message)
                }
            }
        }
    }
}
