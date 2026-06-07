package eu.kanade.tachiyomi.ui.novel.reader

import android.text.style.BackgroundColorSpan

/**
 * Marker span for novel text highlights. Extends BackgroundColorSpan so it works
 * across multi-line text natively while remaining identifiable for tap detection.
 */
class NovelHighlightSpan(color: Int) : BackgroundColorSpan(color)
