package eu.kanade.tachiyomi.util.novel

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import yokai.domain.novel.Novel

/**
 * Extension functions for Novel to access chapter filter and sort settings.
 * Mirrors the pattern from Manga.kt extension functions.
 */

/**
 * Check if the novel uses local (per-novel) sort settings or global preferences.
 */
val Novel.usesLocalSort: Boolean
    get() = (chapterFlags and Novel.CHAPTER_SORT_LOCAL_MASK) == Novel.CHAPTER_SORT_LOCAL

/**
 * Check if the novel uses local (per-novel) filter settings or global preferences.
 */
val Novel.usesLocalFilter: Boolean
    get() = (chapterFlags and Novel.CHAPTER_FILTER_LOCAL_MASK) == Novel.CHAPTER_FILTER_LOCAL

/**
 * Get the sort order for this novel (source, number, or upload date).
 */
val Novel.sorting: Int
    get() = chapterFlags and Novel.CHAPTER_SORTING_MASK

/**
 * Get the read filter for this novel.
 */
val Novel.readFilter: Int
    get() = chapterFlags and Novel.CHAPTER_READ_MASK

/**
 * Get the bookmark filter for this novel.
 */
val Novel.bookmarkedFilter: Int
    get() = chapterFlags and Novel.CHAPTER_BOOKMARKED_MASK

/**
 * Get the chapter display mode (name vs number).
 */
val Novel.chapterDisplayMode: Int
    get() = chapterFlags and Novel.CHAPTER_DISPLAY_MASK

/**
 * Check if this novel sorts chapters in descending order.
 */
fun Novel.sortDescending(preferences: PreferencesHelper): Boolean =
    if (usesLocalSort) {
        (chapterFlags and Novel.CHAPTER_SORT_DIR_DESC) == Novel.CHAPTER_SORT_DIR_DESC
    } else {
        preferences.chaptersDescAsDefault().get()
    }

/**
 * Get the chapter sorting order for this novel (uses local or global settings).
 */
fun Novel.chapterOrder(preferences: PreferencesHelper): Int =
    if (usesLocalSort) sorting else preferences.sortChapterOrder().get()

/**
 * Get the read filter for this novel (uses local or global settings).
 */
fun Novel.readFilter(preferences: PreferencesHelper): Int =
    if (usesLocalFilter) readFilter else preferences.filterChapterByRead().get()

/**
 * Get the bookmark filter for this novel (uses local or global settings).
 */
fun Novel.bookmarkedFilter(preferences: PreferencesHelper): Int =
    if (usesLocalFilter) bookmarkedFilter else preferences.filterChapterByBookmarked().get()

/**
 * Get the downloaded filter for this novel (novels don't support downloads, so always returns 0).
 */
fun Novel.downloadedFilter(preferences: PreferencesHelper): Int = 0

/**
 * Get the hide chapter title setting for this novel (uses global preference).
 */
fun Novel.hideChapterTitle(preferences: PreferencesHelper): Boolean =
    preferences.hideChapterTitlesByDefault().get()

/**
 * Set the chapter sorting order for this novel.
 */
fun Novel.setChapterOrder(order: Int): Novel {
    val newFlags = (chapterFlags and Novel.CHAPTER_SORTING_MASK.inv()) or (order and Novel.CHAPTER_SORTING_MASK)
    return copy(chapterFlags = newFlags or Novel.CHAPTER_SORT_LOCAL)
}

/**
 * Set the sort direction (ascending/descending) for this novel.
 */
fun Novel.setChapterSortDescending(descending: Boolean): Novel {
    val flag = if (descending) Novel.CHAPTER_SORT_DIR_DESC else Novel.CHAPTER_SORT_DIR_ASC
    val newFlags = (chapterFlags and Novel.CHAPTER_SORT_DIR_MASK.inv()) or flag
    return copy(chapterFlags = newFlags or Novel.CHAPTER_SORT_LOCAL)
}

/**
 * Set the read filter for this novel.
 */
fun Novel.setReadFilter(filter: Int): Novel {
    val newFlags = (chapterFlags and Novel.CHAPTER_READ_MASK.inv()) or (filter and Novel.CHAPTER_READ_MASK)
    return copy(chapterFlags = newFlags or Novel.CHAPTER_FILTER_LOCAL)
}

/**
 * Set the bookmark filter for this novel.
 */
fun Novel.setBookmarkFilter(filter: Int): Novel {
    val newFlags = (chapterFlags and Novel.CHAPTER_BOOKMARKED_MASK.inv()) or (filter and Novel.CHAPTER_BOOKMARKED_MASK)
    return copy(chapterFlags = newFlags or Novel.CHAPTER_FILTER_LOCAL)
}

/**
 * Set the chapter display mode for this novel.
 */
fun Novel.setChapterDisplayMode(mode: Int): Novel {
    val newFlags = (chapterFlags and Novel.CHAPTER_DISPLAY_MASK.inv()) or (mode and Novel.CHAPTER_DISPLAY_MASK)
    return copy(chapterFlags = newFlags)
}

/**
 * Reset sorting to use global preferences.
 */
fun Novel.setSortToGlobal(): Novel {
    val newFlags = (chapterFlags and Novel.CHAPTER_SORT_LOCAL_MASK.inv()) or 
                   (Novel.CHAPTER_SORT_FILTER_GLOBAL and Novel.CHAPTER_SORT_LOCAL_MASK)
    return copy(chapterFlags = newFlags)
}

/**
 * Reset filtering to use global preferences.
 */
fun Novel.setFilterToGlobal(): Novel {
    val newFlags = (chapterFlags and Novel.CHAPTER_FILTER_LOCAL_MASK.inv()) or 
                   (Novel.CHAPTER_SORT_FILTER_GLOBAL and Novel.CHAPTER_FILTER_LOCAL_MASK)
    return copy(chapterFlags = newFlags)
}

/**
 * Set filtering to use local (per-novel) settings.
 */
fun Novel.setFilterToLocal(): Novel {
    val newFlags = (chapterFlags and Novel.CHAPTER_FILTER_LOCAL_MASK.inv()) or Novel.CHAPTER_FILTER_LOCAL
    return copy(chapterFlags = newFlags)
}
