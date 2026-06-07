package eu.kanade.tachiyomi.ui.novel.reader

import android.content.Context
import yokai.core.mode.ContentModeResolver
import yokai.core.content.ContentType

/**
 * Helper functions for opening the novel reader from various parts of the application.
 * Provides easy access to novel reading functionality while respecting mode context.
 * 
 * NOTE: Opening a novel does NOT change the global library mode. The mode is only
 * changed by explicit user action (toggle button). This allows users to view novels
 * while staying in manga mode and vice versa.
 */
object NovelReaderNavigator {

    /**
     * Open novel reader for a specific novel.
     * Does not change the global mode - viewing content is independent of library mode.
     */
    fun openNovel(context: Context, novelId: Long, chapterId: Long? = null) {
        // Use the novel-specific context directly without changing global mode
        val novelContext = ContentModeResolver.getContextForType(ContentType.NOVEL)
        novelContext.openReader(context, novelId, chapterId)
    }

    /**
     * Open novel reader for a specific chapter.
     * Does not change the global mode - viewing content is independent of library mode.
     */
    fun openChapter(context: Context, chapterId: Long) {
        // Use the novel-specific context directly without changing global mode
        val novelContext = ContentModeResolver.getContextForType(ContentType.NOVEL)
        novelContext.openChapter(context, chapterId)
    }

    /**
     * Continue reading from where the user left off
     */
    fun continueReading(context: Context, novelId: Long) {
        // This will open the novel reader which will automatically 
        // load the saved reading position
        openNovel(context, novelId)
    }

    /**
     * Open novel reader with specific reading preferences applied
     */
    fun openWithPreferences(
        context: Context, 
        novelId: Long, 
        chapterId: Long? = null,
        preferences: yokai.core.novel.reader.NovelReaderPreferences? = null
    ) {
        // TODO: Apply preferences before opening reader
        // For now, just open normally
        openNovel(context, novelId, chapterId)
    }
}

/**
 * Extension functions for easy access from controllers and activities
 */
fun Context.openNovelReader(novelId: Long, chapterId: Long? = null) {
    NovelReaderNavigator.openNovel(this, novelId, chapterId)
}

fun Context.openNovelChapter(chapterId: Long) {
    NovelReaderNavigator.openChapter(this, chapterId)
}

fun Context.continueReadingNovel(novelId: Long) {
    NovelReaderNavigator.continueReading(this, novelId)
}