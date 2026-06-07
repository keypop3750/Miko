package eu.kanade.tachiyomi.ui.extension.novel

import android.animation.AnimatorInflater
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.text.scale
import androidx.core.view.isGone
import androidx.core.view.isVisible
import coil3.dispose
import coil3.load
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.CoverViewTarget
import eu.kanade.tachiyomi.databinding.ExtensionCardItemBinding
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.extension.novel.util.NovelExtensionLoader
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import eu.kanade.tachiyomi.util.view.resetStrokeColor
import eu.kanade.tachiyomi.util.view.setText
import yokai.i18n.MR
import yokai.util.lang.getString
import java.util.*
import android.R as AR

class NovelExtensionHolder(view: View, val adapter: NovelExtensionAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    private val context = view.context
    private val binding = ExtensionCardItemBinding.bind(view)
    
    init {
        binding.extButton.setOnClickListener {
            adapter.buttonClickListener.onButtonClick(flexibleAdapterPosition)
        }
        binding.cancelButton.setOnClickListener {
            adapter.buttonClickListener.onCancelClick(flexibleAdapterPosition)
        }
    }

    fun bind(item: NovelExtensionItem) {
        val extension = item.extension

        // Set extension name
        val infoText = mutableListOf(extension.versionName)
        binding.date.isVisible = false
        
        if (extension is NovelExtension.Installed && !extension.hasUpdate) {
            when (InstalledExtensionsOrder.fromValue(adapter.installedSortOrder)) {
                InstalledExtensionsOrder.RecentlyUpdated -> {
                    NovelExtensionLoader.extensionUpdateDate(itemView.context, extension)
                        .takeUnless { it == 0L }?.let {
                            binding.date.isVisible = true
                            binding.date.text = itemView.context.timeSpanFromNow(MR.strings.updated_, it)
                            infoText.add("")
                        }
                }
                InstalledExtensionsOrder.RecentlyInstalled -> {
                    NovelExtensionLoader.extensionInstallDate(itemView.context, extension)
                        .takeUnless { it == 0L }?.let {
                            binding.date.isVisible = true
                            binding.date.text =
                                itemView.context.timeSpanFromNow(
                                    if (extension.isShared) {
                                        MR.strings.installed_
                                    } else {
                                        MR.strings.added_
                                    },
                                    it,
                                )
                            infoText.add("")
                        }
                }
                else -> binding.date.isVisible = false
            }
        } else {
            binding.date.isVisible = false
        }
        
        binding.lang.isVisible = binding.date.isGone
        binding.extTitle.text = if (infoText.size > 1) {
            buildSpannedString {
                append(extension.name + " ")
                color(binding.extTitle.context.getResourceColor(AR.attr.textColorSecondary)) {
                    scale(0.75f) {
                        append(LocaleHelper.getLocalizedDisplayName(extension.lang))
                    }
                }
            }
        } else {
            extension.name
        }

        binding.version.text = infoText.joinToString(" • ")
        binding.lang.text = LocaleHelper.getLocalizedDisplayName(extension.lang)
        binding.warning.text = when {
            extension.isNsfw -> itemView.context.getString(MR.strings.nsfw_short)
            else -> ""
        }.plusRepo(extension).uppercase(Locale.ROOT)
        
        binding.installProgress.progress = item.sessionProgress ?: 0
        binding.installProgress.isVisible = item.sessionProgress != null
        binding.cancelButton.isVisible = item.sessionProgress != null

        binding.sourceImage.dispose()

        when (extension) {
            is NovelExtension.Available -> {
                binding.sourceImage.load(extension.iconUrl) {
                    target(CoverViewTarget(binding.sourceImage))
                }
            }
            is NovelExtension.Installed -> {
                // For installed extensions, prefer loading from icon URL (more reliable for private extensions)
                // Fallback to drawable icon if URL is not available
                val iconUrl = extension.iconUrl
                co.touchlab.kermit.Logger.d { "[EXT_HOLDER] Installed ${extension.pkgName} iconUrl=$iconUrl, hasIcon=${extension.icon != null}" }
                when {
                    !iconUrl.isNullOrEmpty() -> {
                        co.touchlab.kermit.Logger.d { "[EXT_HOLDER] Loading icon from URL: $iconUrl" }
                        binding.sourceImage.load(iconUrl) {
                            target(CoverViewTarget(binding.sourceImage))
                        }
                    }
                    extension.icon != null -> {
                        co.touchlab.kermit.Logger.d { "[EXT_HOLDER] Loading icon from drawable" }
                        binding.sourceImage.load(extension.icon)
                    }
                    else -> {
                        co.touchlab.kermit.Logger.w { "[EXT_HOLDER] No icon available for ${extension.pkgName}" }
                    }
                }
            }
            is NovelExtension.Untrusted -> {
                binding.sourceImage.setImageDrawable(
                    context.contextCompatDrawable(R.drawable.ic_report_24dp)
                )
            }
        }
        bindButton(item)
    }

    private fun String.plusRepo(extension: NovelExtension): String {
        val repoText = when {
            extension is NovelExtension.Untrusted -> itemView.context.getString(MR.strings.untrusted)
            extension is NovelExtension.Installed && extension.isObsolete -> itemView.context.getString(MR.strings.obsolete)
            else -> ""
        }

        if (repoText.isEmpty()) return this

        return if (isEmpty()) {
            this
        } else {
            "$this • "
        } + repoText
    }

    @Suppress("ResourceType")
    fun bindButton(item: NovelExtensionItem) = with(binding.extButton) {
        if (item.installStep == InstallStep.Done) return@with
        isEnabled = true
        isClickable = true
        isActivated = false

        binding.installProgress.progress = item.sessionProgress ?: 0
        binding.cancelButton.isVisible = item.sessionProgress != null
        binding.installProgress.isVisible = item.sessionProgress != null
        
        val extension = item.extension
        val installStep = item.installStep
        strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
        rippleColor = ColorStateList.valueOf(context.getResourceColor(R.attr.colorControlHighlight))
        stateListAnimator = null
        
        if (installStep != null) {
            setText(
                when (installStep) {
                    InstallStep.Pending -> MR.strings.pending
                    InstallStep.Downloading -> MR.strings.downloading
                    InstallStep.Loading -> MR.strings.loading
                    InstallStep.Installing -> MR.strings.installing
                    InstallStep.Installed -> MR.strings.installed
                    InstallStep.Error -> MR.strings.retry
                    else -> return@with
                },
            )
            if (installStep != InstallStep.Error) {
                isEnabled = false
                isClickable = false
            }
        } else if (extension is NovelExtension.Installed) {
            when {
                extension.hasUpdate -> {
                    isActivated = true
                    stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.animator.icon_btn_state_list_anim)
                    rippleColor = ColorStateList.valueOf(context.getColor(R.color.on_secondary_highlight))
                    setText(MR.strings.update)
                }
                else -> {
                    resetStrokeColor()
                    setText(MR.strings.settings)
                }
            }
        } else if (extension is NovelExtension.Untrusted) {
            resetStrokeColor()
            setText(MR.strings.trust)
        } else {
            resetStrokeColor()
            setText(if (adapter.installPrivately) MR.strings.add else MR.strings.install)
        }
    }
}
