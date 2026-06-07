package yokai.presentation.extension

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import co.touchlab.kermit.Logger
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.extension.ExtensionBottomSheetLike
import eu.kanade.tachiyomi.ui.extension.details.ExtensionDetailsController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.ui.source.BrowseController
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.util.view.isExpanded
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import yokai.presentation.extension.repo.ExtensionRepoController
import yokai.presentation.theme.YokaiTheme
import uy.kohesive.injekt.injectLazy
import yokai.domain.base.BasePreferences
import yokai.domain.base.BasePreferences.ExtensionInstaller

/**
 * Compose-based implementation of the Extension Bottom Sheet.
 * 
 * This is a drop-in replacement for ExtensionBottomSheet that uses Compose UI internally
 * while maintaining compatibility with BrowseController's existing integration points.
 */
class ComposeExtensionBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs), ExtensionBottomSheetLike {

    private val basePreferences: BasePreferences by injectLazy()

    override var sheetBehavior: BottomSheetBehavior<*>? = null

    private var composeView: ComposeView? = null
    private var extensionViewModel: ExtensionViewModel? = null
    private var migrationViewModel: MigrationViewModel? = null

    lateinit var controller: BrowseController

    var shouldCallApi = false
    var isExpanding = false
    var canExpand = false

    // State for toolbar title (when showing migration manga list)
    var currentSourceTitle: String? by mutableStateOf(null)
        private set

    // Track if we can navigate back (in migration manga list)
    private var inMangaList = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        composeView = findViewById(R.id.compose_content)
    }

    /**
     * Initialize the bottom sheet with the controller.
     * Called from BrowseController.onViewCreated.
     */
    fun onCreate(controller: BrowseController) {
        this.controller = controller
        sheetBehavior = BottomSheetBehavior.from(this)

        Logger.d { "[COMPOSE_SHEET] onCreate - initializing Compose content" }

        // Get ViewModels from the ViewModelStoreOwner
        val viewModelStoreOwner = findViewTreeViewModelStoreOwner()
        if (viewModelStoreOwner != null) {
            extensionViewModel = ViewModelProvider(viewModelStoreOwner)[ExtensionViewModel::class.java]
            migrationViewModel = ViewModelProvider(viewModelStoreOwner)[MigrationViewModel::class.java]
        }

        composeView?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                YokaiTheme {
                    ExtensionBottomSheetContent(
                        extensionViewModel = extensionViewModel ?: return@YokaiTheme,
                        migrationViewModel = migrationViewModel ?: return@YokaiTheme,
                        onNavigateToExtensionDetails = { pkgName ->
                            navigateToExtensionDetails(pkgName)
                        },
                        onNavigateToMigration = { sourceId, sourceName, mangaIds ->
                            navigateToMigration(sourceId, sourceName, mangaIds)
                        },
                        onNavigateToMangaMigration = { mangaId ->
                            navigateToMangaMigration(mangaId)
                        },
                        onNavigateToExtensionRepos = {
                            navigateToExtensionRepos()
                        },
                        onNavigateToLanguages = {
                            navigateToLanguages()
                        },
                    )
                }
            }
        }
    }

    // Navigation functions

    private fun navigateToExtensionDetails(pkgName: String) {
        controller.router.pushController(
            ExtensionDetailsController(pkgName).withFadeTransaction()
        )
    }

    private fun navigateToMigration(sourceId: Long, sourceName: String, mangaIds: List<Long>) {
        currentSourceTitle = sourceName
        inMangaList = true
        controller.updateTitleAndMenu()
        
        // If navigating to PreMigration for multiple manga
        if (mangaIds.isNotEmpty()) {
            controller.router.pushController(
                PreMigrationController.create(mangaIds).withFadeTransaction()
            )
        }
    }

    private fun navigateToMangaMigration(mangaId: Long) {
        controller.router.pushController(
            PreMigrationController.create(listOf(mangaId)).withFadeTransaction()
        )
    }

    private fun navigateToExtensionRepos() {
        controller.router.pushController(
            ExtensionRepoController().withFadeTransaction()
        )
    }

    private fun navigateToLanguages() {
        controller.router.pushController(
            eu.kanade.tachiyomi.ui.extension.ExtensionFilterController().withFadeTransaction()
        )
    }

    // Interface methods for BrowseController compatibility

    fun updateForMode() {
        Logger.d { "[COMPOSE_SHEET] updateForMode - refreshing extension list" }
        extensionViewModel?.refresh()
    }

    fun updatedNestedRecyclers() {
        // No-op for Compose - scrolling is handled by LazyColumn
    }

    fun fetchOnlineExtensionsIfNeeded() {
        if (shouldCallApi) {
            Logger.d { "[COMPOSE_SHEET] fetchOnlineExtensionsIfNeeded - refreshing" }
            extensionViewModel?.refresh()
            shouldCallApi = false
        }
    }

    fun updateExtTitle() {
        // The Compose UI handles update badges internally via ViewModel state
    }

    fun setCanInstallPrivately(canInstall: Boolean) {
        // Handled by ViewModel observing preferences
    }

    fun drawExtensions() {
        // No-op for Compose - UI is reactive
    }

    fun canGoBack(): Boolean {
        if (migrationViewModel?.handleBack() == true) {
            currentSourceTitle = null
            inMangaList = false
            controller.updateTitleAndMenu()
            return true
        }
        return false
    }

    fun canStillGoBack(): Boolean {
        return inMangaList
    }

    /**
     * Scroll the current list to top.
     */
    fun scrollToTop() {
        // Could be implemented by passing a scroll state to Compose
    }

    /**
     * Check if either tab has scrolled content.
     */
    fun hasScrolledLists(): Boolean {
        return false // Compose handles this differently
    }

    // Stub methods for compatibility - these were used by the View-based adapter

    val extensionFrameLayout: View? = null
    val migrationFrameLayout: View? = null
    
    val adapters: List<Any?> = emptyList()
    
    val presenter: Any? = null // Compose uses ViewModels instead

    fun isOnView(view: View): Boolean = false
}
