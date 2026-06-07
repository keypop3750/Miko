package eu.kanade.tachiyomi.ui.novel.reader

import android.graphics.Color
import android.graphics.Typeface
import yokai.core.novel.reader.TextAlignment

/**
 * Configuration for text rendering in novel reader (QuickNovel pattern).
 * Centralizes all text appearance settings for TextAdapter.
 */
data class TextConfig(
    val textSize: Float = 16f,
    val textColor: Int = Color.BLACK,
    val backgroundColor: Int = Color.WHITE,
    val textFont: Typeface? = null,
    val lineSpacing: Float = 1.5f,
    val paragraphSpacing: Int = 16, // dp
    val horizontalPadding: Int = 16, // dp
    val verticalPadding: Int = 24,   // dp
    val bionicReading: Boolean = false,
    val isTextSelectable: Boolean = true,
    val toolbarHeight: Int = 0,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val chapterTitle: String = "", // Chapter title for header
    val chapterNumber: String = ""  // Chapter number for header
)
