package eu.kanade.tachiyomi.util.novel

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.novel.Novel
import yokai.domain.novelchapter.models.NovelChapter

/**
 * Sorts novel chapters based on the novel's sort settings.
 * Adapted from ChapterSort for novel-specific sorting.
 */
class NovelChapterSort(
    val novel: Novel,
    val novelChapterFilter: NovelChapterFilter = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get()
) {

    /**
     * Gets chapters sorted and optionally filtered.
     * 
     * @param rawChapters List of chapters to sort
     * @param andFiltered Whether to also apply filters
     * @param filterForReader Whether to filter for reader (includes skipRead/skipFiltered prefs)
     * @param currentChapter Current chapter for reader filtering
     * @return Sorted (and optionally filtered) list of chapters
     */
    fun getChaptersSorted(
        rawChapters: List<NovelChapter>,
        andFiltered: Boolean = true,
        filterForReader: Boolean = false,
        currentChapter: NovelChapter? = null,
    ): List<NovelChapter> {
        val chapters = when {
            filterForReader -> novelChapterFilter.filterChaptersForReader(
                rawChapters,
                novel,
                currentChapter,
            )
            andFiltered -> novelChapterFilter.filterChapters(rawChapters, novel)
            else -> rawChapters
        }

        return chapters.sortedWith(sortComparator())
    }

    /**
     * Gets the next chapter based on current sort settings.
     * 
     * @param rawChapters List of chapters
     * @param andFiltered Whether to apply filters
     * @return Next chapter or null
     */
    fun getNextChapter(rawChapters: List<NovelChapter>, andFiltered: Boolean = true): NovelChapter? {
        val chapters = when {
            andFiltered -> novelChapterFilter.filterChapters(rawChapters, novel)
            else -> rawChapters
        }
        return chapters.sortedWith(sortComparator(true)).firstOrNull()
    }

    /**
     * Gets the next unread chapter based on current sort settings.
     * 
     * @param rawChapters List of chapters
     * @param andFiltered Whether to apply filters
     * @return Next unread chapter or null
     */
    fun getNextUnreadChapter(rawChapters: List<NovelChapter>, andFiltered: Boolean = true): NovelChapter? {
        val chapters = when {
            andFiltered -> novelChapterFilter.filterChapters(rawChapters, novel)
            else -> rawChapters
        }

        return chapters.sortedWith(sortComparator(true)).find { !it.read }
    }

    /**
     * Creates a comparator based on the novel's sort settings.
     * 
     * @param ignoreAsc Force ascending order (for "next chapter" queries)
     * @return Comparator for sorting chapters
     */
    fun sortComparator(ignoreAsc: Boolean = false): Comparator<NovelChapter> {
        val sortDescending = !ignoreAsc && novel.sortDescending(preferences)
        val sortFunction: (NovelChapter, NovelChapter) -> Int =
            when (novel.chapterOrder(preferences)) {
                Novel.CHAPTER_SORTING_SOURCE -> when (sortDescending) {
                    true -> { c1, c2 -> c1.sourceOrder.compareTo(c2.sourceOrder) }
                    false -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
                }
                Novel.CHAPTER_SORTING_NUMBER -> when (sortDescending) {
                    true -> { c1, c2 -> c2.chapterNumber.toString().compareToCaseInsensitiveNaturalOrder(c1.chapterNumber.toString()) }
                    false -> { c1, c2 -> c1.chapterNumber.toString().compareToCaseInsensitiveNaturalOrder(c2.chapterNumber.toString()) }
                }
                Novel.CHAPTER_SORTING_UPLOAD_DATE -> when (sortDescending) {
                    true -> { c1, c2 -> c2.dateUpload.compareTo(c1.dateUpload) }
                    false -> { c1, c2 -> c1.dateUpload.compareTo(c2.dateUpload) }
                }
                else -> { c1, c2 -> c1.sourceOrder.compareTo(c2.sourceOrder) }
            }
        return Comparator(sortFunction)
    }
}
