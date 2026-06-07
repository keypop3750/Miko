package eu.kanade.tachiyomi.ui.source.globalsearch

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.BaseComposeController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.novel.details.NovelDetailsControllerNew
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.util.addOrRemoveToFavorites
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import uy.kohesive.injekt.injectLazy
import yokai.core.mode.ModeManager
import yokai.i18n.MR
import yokai.presentation.globalsearch.GlobalSearchScreen
import yokai.presentation.globalsearch.GlobalSearchViewModel

/**
 * Compose-based controller for global search across all enabled sources.
 * 
 * This is the new Compose implementation replacing the legacy View-based GlobalSearchController.
 * It provides:
 * - Material3 themed UI matching BrowseScreen
 * - Parallel source searching with semaphore concurrency
 * - Real-time result updates
 * - Mode-aware filtering (manga vs novel)
 */
class ComposeGlobalSearchController(
    private val initialQuery: String = "",
    private val extensionFilter: String? = null,
    bundle: Bundle? = null,
) : BaseComposeController(bundle) {
    
    private val preferences: PreferencesHelper by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    
    private var snack: Snackbar? = null
    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // ViewModel instance that will persist across recompositions
    private var viewModel: GlobalSearchViewModel? = null
    
    constructor(query: String) : this(initialQuery = query)
    
    init {
        // Save arguments to bundle for state restoration
        args.putString(QUERY_KEY, initialQuery)
        extensionFilter?.let { args.putString(EXTENSION_FILTER_KEY, it) }
    }
    
    @Composable
    override fun ScreenContent() {
        val vm = viewModel ?: viewModel<GlobalSearchViewModel>().also { viewModel = it }
        
        // Observe mode changes - only re-search when mode actually changes
        val currentMode by ModeManager.currentMode.collectAsState()
        val previousMode = remember { mutableStateOf(currentMode) }
        
        // Re-search when mode changes (not on initial composition or view recreation)
        LaunchedEffect(currentMode) {
            if (previousMode.value != currentMode) {
                previousMode.value = currentMode
                vm.researchWithNewMode()
            }
        }
        
        GlobalSearchScreen(
            viewModel = vm,
            initialQuery = args.getString(QUERY_KEY) ?: initialQuery,
            onBackClick = { router.handleBack() },
            onMangaClick = { manga -> onMangaClick(manga) },
            onMangaLongClick = { manga -> onMangaLongClick(manga, vm) },
            onSourceClick = { source, query -> onSourceClick(source, query) }
        )
    }
    
    /**
     * Navigate to manga/novel details when a search result is clicked.
     */
    private fun onMangaClick(manga: Manga) {
        val source = sourceManager.getOrStub(manga.source) as? CatalogueSource

        if (source?.isNovelSource() == true) {
            val novel = yokai.domain.novel.Novel(
                id = manga.id ?: -1,
                source = manga.source,
                url = manga.url,
                title = manga.title,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                status = manga.status.toLong(),
                thumbnailUrl = manga.thumbnail_url,
                favorite = manga.favorite,
                lastUpdate = manga.last_update,
                initialized = manga.initialized,
                viewerFlags = manga.viewer_flags.toLong(),
                chapterFlags = manga.chapter_flags.toLong(),
                coverLastModified = manga.cover_last_modified,
                dateAdded = manga.date_added,
            )
            router.pushController(
                NovelDetailsControllerNew(novel, true)
                    .withFadeTransaction()
            )
        } else {
            router.pushController(
                MangaDetailsController(
                    manga = manga,
                    fromCatalogue = true,
                    shouldLockIfNeeded = activity is SearchActivity
                ).withFadeTransaction()
            )
        }
    }
    
    /**
     * Handle manga long click - add/remove from favorites.
     */
    private fun onMangaLongClick(manga: Manga, viewModel: GlobalSearchViewModel) {
        val view = view ?: return
        val activity = activity ?: return
        
        controllerScope.launchIO {
            withUIContext { snack?.dismiss() }
            snack = manga.addOrRemoveToFavorites(
                preferences = preferences,
                view = view,
                activity = activity,
                sourceManager = sourceManager,
                controller = this@ComposeGlobalSearchController,
                onMangaAdded = {
                    viewModel.updateMangaFavoriteStatus(manga.id!!, true)
                    snack = view.snack(MR.strings.added_to_library)
                },
                onMangaMoved = {
                    // Manga was moved between categories
                },
                onMangaDeleted = {
                    viewModel.updateMangaFavoriteStatus(manga.id!!, false)
                },
                scope = controllerScope
            )
            if (snack?.duration == Snackbar.LENGTH_INDEFINITE) {
                withUIContext {
                    (activity as? MainActivity)?.setUndoSnackBar(snack)
                }
            }
        }
    }
    
    /**
     * Navigate to source browser when source header is clicked.
     */
    private fun onSourceClick(source: CatalogueSource, query: String) {
        preferences.lastUsedCatalogueSource().set(source.id)
        router.pushController(
            BrowseSourceController(source, query).withFadeTransaction()
        )
    }
    
    override fun onDestroyView(view: android.view.View) {
        super.onDestroyView(view)
        snack?.dismiss()
        snack = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        controllerScope.cancel()
    }
    
    companion object {
        private const val QUERY_KEY = "query"
        private const val EXTENSION_FILTER_KEY = "extension_filter"
    }
}
