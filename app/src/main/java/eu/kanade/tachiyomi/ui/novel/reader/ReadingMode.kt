package eu.kanade.tachiyomi.ui.novel.reader

import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Reading mode options for novel reader (QuickNovel pattern).
 * Determines chapter loading and navigation behavior.
 */
enum class ReadingMode(val prefValue: Int, val stringRes: dev.icerock.moko.resources.StringResource) {
    /**
     * Default mode - Single chapter at a time, manual navigation.
     * Most memory-efficient, best for precise position tracking.
     */
    DEFAULT(0, MR.strings.reading_mode_default),
    
    /**
     * Infinite scroll - Seamless multi-chapter scrolling.
     * Automatically loads next/previous chapters when approaching end.
     * Best for binge reading sessions.
     */
    INFINITE_SCROLL(1, MR.strings.reading_mode_infinite_scroll),
    
    /**
     * Overscroll mode - Gesture-based chapter navigation.
     * Pull down/up to switch chapters with progress indicator.
     * Best for one-handed reading.
     */
    OVERSCROLL(2, MR.strings.reading_mode_overscroll);
    
    companion object {
        /**
         * Convert preference value to ReadingMode enum.
         * Defaults to DEFAULT if value is invalid.
         */
        fun fromPrefValue(value: Int): ReadingMode {
            return entries.find { it.prefValue == value } ?: DEFAULT
        }
        
        /**
         * Get all reading modes as display strings.
         * Used for spinner/dropdown UI.
         */
        fun getDisplayStrings(context: android.content.Context): List<String> {
            return entries.map { context.getString(it.stringRes) }
        }
    }
}
