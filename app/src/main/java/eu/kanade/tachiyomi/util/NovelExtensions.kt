package eu.kanade.tachiyomi.util

import android.app.Activity
import android.content.Context
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.category.addtolibrary.SetNovelCategoriesSheet
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import yokai.domain.novel.Novel
import eu.kanade.tachiyomi.source.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.category.interactor.GetCategories
import yokai.domain.category.interactor.GetNovelCategories

/**
 * Extension functions for Novel domain model.
 * Mirrors MangaExtensions.kt for consistency.
 */

fun Novel.isLocal() = source == LocalSource.ID

/**
 * Determines whether to hide chapter titles based on novel filtering preferences.
 * Returns false for local novels (always show titles).
 * Otherwise returns the preference setting.
 */
fun Novel.hideChapterTitle(preferences: PreferencesHelper): Boolean {
    if (isLocal()) return false
    // TODO: Implement usesLocalFilter check if Novel domain supports it
    // For now, use global preference
    return preferences.hideChapterTitlesByDefault().get()
}

suspend fun Novel.shouldDownloadNewChapters(
    prefs: PreferencesHelper,
    getCategories: GetCategories = Injekt.get()
): Boolean {
    if (!isFavorite) return false

    // Boolean to determine if user wants to automatically download new chapters.
    val downloadNewChapters = prefs.downloadNewChapters().get()
    if (!downloadNewChapters) return false

    val includedCategories = prefs.downloadNewChaptersInCategories().get().map(String::toInt)
    val excludedCategories = prefs.excludeCategoriesInDownloadNew().get().map(String::toInt)
    if (includedCategories.isEmpty() && excludedCategories.isEmpty()) return true

    // TODO: Implement category retrieval for novels
    // For now, return true if downloadNewChapters is enabled
    return true
}

/**
 * Move a single novel to categories
 */
suspend fun Novel.moveNovelCategories(activity: Activity, onNovelMoved: () -> Unit) {
    moveNovelCategories(activity, false, onNovelMoved)
}

suspend fun Novel.moveNovelCategories(
    activity: Activity,
    addingToLibrary: Boolean,
    onNovelMoved: () -> Unit,
) {
    val getNovelCategories: GetNovelCategories = Injekt.get()
    val categories = getNovelCategories.await()
    val categoriesForNovel = this.id?.let { novelId -> 
        getNovelCategories.awaitByNovelId(novelId) 
    }.orEmpty()
    val ids = categoriesForNovel.mapNotNull { it.id }.toTypedArray()
    withUIContext {
        SetNovelCategoriesSheet(
            activity,
            this@moveNovelCategories,
            categories.toMutableList(),
            ids,
            addingToLibrary,
        ) {
            onNovelMoved()
        }.show()
    }
}

/**
 * Move multiple novels to categories
 */
suspend fun List<Novel>.moveNovelCategories(
    activity: Activity,
    onNovelMoved: () -> Unit,
) {
    if (this.isEmpty()) return

    val getNovelCategories: GetNovelCategories = Injekt.get()
    val categories = getNovelCategories.await()
    val novelCategories = map { novel ->
        novel.id?.let { novelId -> getNovelCategories.awaitByNovelId(novelId) }.orEmpty()
    }
    val commonCategories = if (novelCategories.isEmpty()) {
        emptySet()
    } else {
        novelCategories.reduce { set1, set2 -> 
            set1.intersect(set2.toSet()).toMutableList() 
        }.toSet()
    }
    val mixedCategories = novelCategories.flatten().distinct().subtract(commonCategories).toMutableList()

    withUIContext {
        SetNovelCategoriesSheet(
            activity,
            this@moveNovelCategories,
            categories.toMutableList(),
            categories.map {
                when (it) {
                    in commonCategories -> TriStateCheckBox.State.CHECKED
                    in mixedCategories -> TriStateCheckBox.State.IGNORE
                    else -> TriStateCheckBox.State.UNCHECKED
                }
            }.toTypedArray(),
            false,
        ) {
            onNovelMoved()
        }.show()
    }
}
