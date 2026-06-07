package eu.kanade.tachiyomi.ui.extension.novel

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.databinding.NovelExtensionControllerBinding
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.ui.base.controller.BaseLegacyController
import eu.kanade.tachiyomi.ui.extension.ExtensionDividerItemDecoration
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.getBottomGestureInsets
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.popupMenu
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.setNegativeButton
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setText
import eu.kanade.tachiyomi.util.view.setTitle
import uy.kohesive.injekt.injectLazy
import yokai.domain.base.BasePreferences
import yokai.domain.base.BasePreferences.ExtensionInstaller
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

/**
 * Controller that displays novel extensions.
 * Only visible in novel mode.
 */
class NovelExtensionController :
    BaseLegacyController<NovelExtensionControllerBinding>(),
    NovelExtensionAdapter.OnButtonClickListener,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    NovelExtensionView {

    private val basePreferences: BasePreferences by injectLazy()

    private var extAdapter: NovelExtensionAdapter? = null
    val presenter = NovelExtensionBottomPresenter()

    private var extensions: List<NovelExtensionItem> = emptyList()
    
    override fun getViewContext(): Context? = view?.context

    override fun getTitle(): String? = view?.context?.getString(MR.strings.novel_extensions)

    override fun createBinding(inflater: LayoutInflater) = NovelExtensionControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        
        presenter.attachView(this)
        extAdapter = NovelExtensionAdapter(this)
        extAdapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.adapter = extAdapter
        binding.recycler.setHasFixedSize(true)
        binding.recycler.addItemDecoration(ExtensionDividerItemDecoration(view.context))

        scrollViewWith(
            binding.recycler,
            afterInsets = {
                binding.recycler.updatePaddingRelative(
                    bottom = (activityBinding?.bottomNav?.height ?: it.getBottomGestureInsets()),
                )
            },
        )

        binding.swipeRefresh.setOnRefreshListener {
            presenter.findAvailableExtensions()
        }

        presenter.onCreate()
        presenter.findAvailableExtensions()
    }

    override fun setExtensions(extensions: List<NovelExtensionItem>, updateController: Boolean) {
        this.extensions = extensions
        binding.swipeRefresh.isRefreshing = false
        drawExtensions()
    }

    private fun drawExtensions() {
        extAdapter?.updateDataSet(extensions)
        updateTitle()
    }

    private fun updateTitle() {
        val extCount = presenter.getExtensionUpdateCount()
        if (extCount > 0) {
            activity?.title = "${view?.context?.getString(MR.strings.novel_extensions)} ($extCount)"
        } else {
            activity?.title = view?.context?.getString(MR.strings.novel_extensions)
        }
    }

    override fun downloadUpdate(item: NovelExtensionItem) {
        extAdapter?.updateItem(item, item.installStep)
        updateExtUpdateAllButton()
    }

    private fun updateExtUpdateAllButton() {
        val updateHeader =
            extAdapter?.headerItems?.find { it is NovelExtensionGroupItem && it.canUpdate != null } as? NovelExtensionGroupItem
                ?: return
        val items = extAdapter?.getSectionItemPositions(updateHeader) ?: return
        updateHeader.canUpdate = items.any {
            val extItem = (extAdapter?.getItem(it) as? NovelExtensionItem) ?: return
            extItem.installStep == null || extItem.installStep == InstallStep.Error
        }
        extAdapter?.updateItem(updateHeader)
    }

    override fun onButtonClick(position: Int) {
        val extension = (extAdapter?.getItem(position) as? NovelExtensionItem)?.extension ?: return
        when (extension) {
            is NovelExtension.Installed -> {
                if (!extension.hasUpdate) {
                    openDetails(extension)
                } else {
                    presenter.updateExtension(extension)
                }
            }
            is NovelExtension.Available -> {
                presenter.installExtension(extension)
            }
            is NovelExtension.Untrusted -> {
                openTrustDialog(extension)
            }
        }
    }

    override fun onCancelClick(position: Int) {
        val extension = (extAdapter?.getItem(position) as? NovelExtensionItem) ?: return
        presenter.cancelExtensionInstall(extension)
    }

    override fun onUpdateAllClicked(position: Int) {
        (activity as? MainActivity)?.showNotificationPermissionPrompt()
        if (basePreferences.extensionInstaller().get() != ExtensionInstaller.SHIZUKU &&
            !presenter.preferences.hasPromptedBeforeUpdateAll().get()
        ) {
            activity!!.materialAlertDialog()
                .setTitle(MR.strings.update_all)
                .setMessage(MR.strings.some_extensions_may_prompt)
                .setPositiveButton(AR.string.ok) { _, _ ->
                    presenter.preferences.hasPromptedBeforeUpdateAll().set(true)
                    updateAllExtensions(position)
                }
                .show()
        } else {
            updateAllExtensions(position)
        }
    }

    override fun onExtSortClicked(view: TextView, position: Int) {
        view.popupMenu(
            InstalledExtensionsOrder.entries.map { it.value to it.nameRes },
            presenter.preferences.installedExtensionsOrder().get(),
        ) {
            presenter.preferences.installedExtensionsOrder().set(itemId)
            extAdapter?.installedSortOrder = itemId
            view.setText(InstalledExtensionsOrder.fromValue(itemId).nameRes)
            presenter.refreshExtensions()
        }
    }

    private fun updateAllExtensions(position: Int) {
        val header = (extAdapter?.getSectionHeader(position)) as? NovelExtensionGroupItem ?: return
        val items = extAdapter?.getSectionItemPositions(header)
        val extensions = items?.mapNotNull {
            val extItem = (extAdapter?.getItem(it) as? NovelExtensionItem) ?: return
            val extension = extItem.extension
            if ((extItem.installStep == null || extItem.installStep == InstallStep.Error) &&
                extension is NovelExtension.Installed && extension.hasUpdate
            ) {
                extension
            } else {
                null
            }
        }.orEmpty()
        presenter.updateExtensions(extensions)
    }

    override fun onItemClick(view: View?, position: Int): Boolean {
        val extension = (extAdapter?.getItem(position) as? NovelExtensionItem)?.extension ?: return false
        if (extension is NovelExtension.Installed) {
            openDetails(extension)
        } else if (extension is NovelExtension.Untrusted) {
            openTrustDialog(extension)
        }
        return false
    }

    override fun onItemLongClick(position: Int) {
        val extension = (extAdapter?.getItem(position) as? NovelExtensionItem)?.extension ?: return
        if (extension is NovelExtension.Installed || extension is NovelExtension.Untrusted) {
            uninstallExtension(extension.name, extension.pkgName)
        }
    }

    private fun openDetails(extension: NovelExtension.Installed) {
        // TODO: Implement NovelExtensionDetailsController
        activity?.materialAlertDialog()
            ?.setTitle(extension.name)
            ?.setMessage("Extension details coming soon.\n\nSources: ${extension.sources.joinToString { it.name }}")
            ?.setPositiveButton(AR.string.ok, null)
            ?.show()
    }

    private fun openTrustDialog(extension: NovelExtension.Untrusted) {
        activity?.materialAlertDialog()
            ?.setTitle(MR.strings.untrusted_extension)
            ?.setMessage(MR.strings.untrusted_extension_message)
            ?.setPositiveButton(MR.strings.trust) { _, _ ->
                presenter.trustExtension(extension)
            }
            ?.setNegativeButton(MR.strings.uninstall) { _, _ ->
                uninstallExtension(extension.pkgName)
            }?.show()
    }

    private fun uninstallExtension(pkgName: String) {
        presenter.uninstallExtension(pkgName)
    }

    private fun uninstallExtension(extName: String, pkgName: String) {
        activity?.materialAlertDialog()
            ?.setTitle(extName)
            ?.setPositiveButton(MR.strings.remove) { _, _ ->
                presenter.uninstallExtension(pkgName)
            }
            ?.setNegativeButton(AR.string.cancel, null)
            ?.show()
    }

    override fun onDestroyView(view: View) {
        extAdapter = null
        super.onDestroyView(view)
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }
}
