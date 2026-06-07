package eu.kanade.tachiyomi.ui.navigation

import com.bluelinelabs.conductor.Router
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.novel.details.NovelDetailsControllerNew
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import yokai.domain.novel.Novel

/**
 * Content-aware navigation router - supports both manga and novel navigation
 */
object ContentRouter {
    
    /**
     * Navigate to manga details page
     */
    fun navigateToMangaDetails(router: Router, manga: Manga) {
        router.pushController(MangaDetailsController(manga).withFadeTransaction())
    }
    
    /**
     * Navigate to novel details page
     */
    fun navigateToNovelDetails(router: Router, novel: Novel) {
        router.pushController(NovelDetailsControllerNew(novel).withFadeTransaction())
    }
}