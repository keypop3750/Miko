package eu.kanade.tachiyomi.util.novel

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.novel.Novel
import yokai.domain.novelchapter.models.NovelChapter

/**
 * Filters novel chapters based on read and bookmark status.
 * Adapted from ChapterFilter for novel-specific filtering (no download filtering for novels).
 */
class NovelChapterFilter(
    val preferences: PreferencesHelper = Injekt.get()
) {

    /**
     * Filters chapters based on the novel's filter settings.
     * 
     * @param chapters List of chapters to filter
     * @param novel The novel containing filter preferences
     * @return Filtered list of chapters
     */
    fun filterChapters(chapters: List<NovelChapter>, novel: Novel): List<NovelChapter> {
        val readEnabled = novel.readFilter(preferences) == Novel.CHAPTER_SHOW_READ
        val unreadEnabled = novel.readFilter(preferences) == Novel.CHAPTER_SHOW_UNREAD
        val bookmarkEnabled = novel.bookmarkedFilter(preferences) == Novel.CHAPTER_SHOW_BOOKMARKED
        val notBookmarkEnabled = novel.bookmarkedFilter(preferences) == Novel.CHAPTER_SHOW_NOT_BOOKMARKED
        
        // Get filtered translators (translators/uploaders)
        val filteredTranslators = novel.filteredTranslators?.split(",")?.toSet() ?: emptySet()

        // If none of the filters are enabled, skip filtering
        return if (readEnabled || unreadEnabled || bookmarkEnabled || notBookmarkEnabled || filteredTranslators.isNotEmpty()) {
            chapters.filter { chapter ->
                // Filter out chapters by translator
                if (filteredTranslators.isNotEmpty() && chapter.translator in filteredTranslators) {
                    return@filter false
                }
                
                // Filter out chapters that don't match the criteria
                if (readEnabled && chapter.read.not()) return@filter false
                if (unreadEnabled && chapter.read) return@filter false
                if (bookmarkEnabled && chapter.bookmark.not()) return@filter false
                if (notBookmarkEnabled && chapter.bookmark) return@filter false
                
                return@filter true
            }
        } else {
            chapters
        }
    }

    /**
     * Filters chapters for the reader.
     * 
     * @param chapters List of chapters to filter
     * @param novel The novel containing filter preferences
     * @param selectedChapter The currently selected chapter (will always be included)
     * @return Filtered list of chapters for reader navigation
     */
    fun filterChaptersForReader(
        chapters: List<NovelChapter>,
        novel: Novel,
        selectedChapter: NovelChapter? = null
    ): List<NovelChapter> {
        var filteredChapters = chapters
        
        // If filter prefs aren't enabled don't even filter
        if (!preferences.skipRead().get() && !preferences.skipFiltered().get()) {
            return filteredChapters
        }

        if (preferences.skipRead().get()) {
            filteredChapters = filteredChapters.filter { !it.read }
        }
        
        if (preferences.skipFiltered().get()) {
            filteredChapters = filterChapters(filteredChapters, novel)
        }

        // Add the selected chapter to the list in case it was filtered out
        if (selectedChapter != null) {
            val find = filteredChapters.find { it.id == selectedChapter.id }
            if (find == null) {
                filteredChapters = filteredChapters + selectedChapter
            }
        }

        return filteredChapters
    }
}
