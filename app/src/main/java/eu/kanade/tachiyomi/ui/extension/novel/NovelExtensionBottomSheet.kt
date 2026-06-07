package eu.kanade.tachiyomi.ui.extension.novel

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.NovelExtensionsBottomSheetBinding
import eu.kanade.tachiyomi.databinding.RecyclerWithScrollerBinding
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.ui.extension.ExtensionBottomSheetLike
import eu.kanade.tachiyomi.ui.extension.RecyclerViewPagerAdapter
import eu.kanade.tachiyomi.ui.extension.RecyclerWithScrollerView
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.source.BrowseController
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.popupMenu
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.setNegativeButton
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setText
import eu.kanade.tachiyomi.util.view.setTitle
import eu.kanade.tachiyomi.util.view.smoothScrollToTop
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.base.BasePreferences
import yokai.domain.base.BasePreferences.ExtensionInstaller
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

class NovelExtensionBottomSheet @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs),
    NovelExtensionAdapter.OnButtonClickListener,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    NovelExtensionView,
    ExtensionBottomSheetLike {

    private val basePreferences: BasePreferences by injectLazy()

    override var sheetBehavior: BottomSheetBehavior<*>? = null

    var shouldCallApi = false

    /**
     * Adapter containing the list of novel extensions
     */
    private var extAdapter: NovelExtensionAdapter? = null

    val presenter = NovelExtensionBottomPresenter()

    private var extensions: List<NovelExtensionItem> = emptyList()
    var canExpand = false
    private lateinit var binding: NovelExtensionsBottomSheetBinding

    lateinit var controller: BrowseController
    var boundViews = arrayListOf<RecyclerWithScrollerView>()

    val extensionFrameLayout: RecyclerWithScrollerView?
        get() = binding.pager.findViewWithTag("NovelTabbedRecycler0") as? RecyclerWithScrollerView

    var isExpanding = false
    var extQuery = ""
    
    // NovelExtensionView implementation
    override fun getViewContext(): Context = context

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = NovelExtensionsBottomSheetBinding.bind(this)
    }

    fun onCreate(controller: BrowseController) {
        // Initialize adapter, scroll listener and recycler views
        presenter.attachView(this)
        extAdapter = NovelExtensionAdapter(this)
        extAdapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        sheetBehavior = BottomSheetBehavior.from(this)
        
        binding.pager.adapter = TabbedSheetAdapter()
        this.controller = controller
        
        binding.pager.doOnApplyWindowInsetsCompat { _, insets, _ ->
            val bottomBar = controller.activityBinding?.bottomNav
            val bottomH = bottomBar?.height ?: insets.getInsets(systemBars()).bottom
            extensionFrameLayout?.binding?.recycler?.updatePaddingRelative(bottom = bottomH)
        }
        
        presenter.onCreate()
        updateExtTitle()

        binding.sheetLayout.setOnClickListener {
            if (!sheetBehavior.isExpanded()) {
                sheetBehavior?.expand()
                fetchOnlineExtensionsIfNeeded()
            } else {
                sheetBehavior?.collapse()
            }
        }
    }

    private fun View.doOnApplyWindowInsetsCompat(action: (View, androidx.core.view.WindowInsetsCompat, Any?) -> Unit) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            action(v, insets, null)
            insets
        }
    }

    fun fetchOnlineExtensionsIfNeeded() {
        if (shouldCallApi) {
            presenter.findAvailableExtensions()
            shouldCallApi = false
        }
    }

    fun updateExtTitle() {
        val extCount = presenter.getExtensionUpdateCount()
        binding.toolbarTitle.text = if (extCount > 0) {
            context.getString(MR.strings.extensions) + " ($extCount)"
        } else {
            context.getString(MR.strings.extensions)
        }
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
        (controller.activity as? MainActivity)?.showNotificationPermissionPrompt()
        if (basePreferences.extensionInstaller().get() != ExtensionInstaller.SHIZUKU &&
            !presenter.preferences.hasPromptedBeforeUpdateAll().get()
        ) {
            controller.activity!!.materialAlertDialog()
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
        controller.activity?.materialAlertDialog()
            ?.setTitle(extension.name)
            ?.setMessage("Extension details coming soon.\n\nSources: ${extension.sources.joinToString { it.name }}")
            ?.setPositiveButton(AR.string.ok, null)
            ?.show()
    }

    private fun openTrustDialog(extension: NovelExtension.Untrusted) {
        val activity = controller.activity ?: return
        activity.materialAlertDialog()
            .setTitle(MR.strings.untrusted_extension)
            .setMessage(MR.strings.untrusted_extension_message)
            .setPositiveButton(MR.strings.trust) { _, _ ->
                presenter.trustExtension(extension)
            }
            .setNegativeButton(MR.strings.uninstall) { _, _ ->
                uninstallExtension(extension.pkgName)
            }.show()
    }

    override fun setExtensions(extensions: List<NovelExtensionItem>, updateController: Boolean) {
        this.extensions = extensions
        drawExtensions()
    }

    fun drawExtensions() {
        if (extQuery.isNotBlank()) {
            extAdapter?.updateDataSet(
                extensions.filter {
                    it.extension.name.contains(extQuery, ignoreCase = true)
                },
            )
        } else {
            extAdapter?.updateDataSet(extensions)
        }
        updateExtTitle()
        updateExtUpdateAllButton()
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

    private fun uninstallExtension(pkgName: String) {
        presenter.uninstallExtension(pkgName)
    }

    private fun uninstallExtension(extName: String, pkgName: String) {
        controller.activity!!.materialAlertDialog()
            .setTitle(extName)
            .setPositiveButton(MR.strings.remove) { _, _ ->
                presenter.uninstallExtension(pkgName)
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }

    fun setCanInstallPrivately(installPrivately: Boolean) {
        extAdapter?.installPrivately = installPrivately
    }

    fun onDestroy() {
        presenter.onDestroy()
    }

    fun updatedNestedRecyclers() {
        extensionFrameLayout?.binding?.recycler?.isNestedScrollingEnabled = true
    }

    private inner class TabbedSheetAdapter : RecyclerViewPagerAdapter() {

        override fun getCount(): Int {
            return 1
        }

        override fun getPageTitle(position: Int): CharSequence {
            return context.getString(MR.strings.extensions)
        }

        /**
         * Creates a new view for this adapter.
         *
         * @return a new view.
         */
        override fun createView(container: ViewGroup): View {
            val binding = RecyclerWithScrollerBinding.inflate(
                LayoutInflater.from(container.context),
                container,
                false,
            )
            val view: RecyclerWithScrollerView = binding.root
            val height = this@NovelExtensionBottomSheet.controller.activityBinding?.bottomNav?.height
                ?: view.rootWindowInsetsCompat?.getInsets(systemBars())?.bottom ?: 0
            view.setUp(this@NovelExtensionBottomSheet, binding, height)

            return view
        }

        /**
         * Binds a view with a position.
         *
         * @param view the view to bind.
         * @param position the position in the adapter.
         */
        override fun bindView(view: View, position: Int) {
            (view as RecyclerWithScrollerView).onBind(extAdapter!!)
            view.setTag("NovelTabbedRecycler$position")
            boundViews.add(view)
        }

        /**
         * Recycles a view.
         *
         * @param view the view to recycle.
         * @param position the position in the adapter.
         */
        override fun recycleView(view: View, position: Int) {
            boundViews.remove(view)
        }

        /**
         * Returns the position of the view.
         */
        override fun getItemPosition(obj: Any): Int {
            val view = (obj as? RecyclerWithScrollerView) ?: return POSITION_NONE
            val index = if (view.binding?.recycler?.adapter == extAdapter) 0 else -1
            return if (index == -1) POSITION_NONE else index
        }
    }
}
