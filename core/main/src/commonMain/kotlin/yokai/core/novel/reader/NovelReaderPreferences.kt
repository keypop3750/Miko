package yokai.core.novel.reader

data class NovelReaderPreferences(
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val textAlignment: TextAlignment = TextAlignment.JUSTIFY,
    val fontFamily: String = "default",
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val textColor: Int = 0xFF000000.toInt(),
    val readingType: NovelReadingType = NovelReadingType.DEFAULT,
    val orientation: NovelOrientationType = NovelOrientationType.DEFAULT,
    val showReadingProgress: Boolean = true,
    val autoScrollSpeed: Float = 1.0f,
    val keepScreenOn: Boolean = true,
    val ttsVoice: String = "default"
)

enum class NovelReadingType(val prefValue: Int) {
    DEFAULT(0),           // Standard scrolling
    INF_SCROLL(1),        // Infinite scroll
    BTT_SCROLL(2),        // Bottom to top scroll
    OVERSCROLL(3)         // Overscroll gestures
}

enum class NovelOrientationType(val prefValue: Int, val flag: Int) {
    DEFAULT(0, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    PORTRAIT(1, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LANDSCAPE(2, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    LOCKED_PORTRAIT(3, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED),
    REVERSE_PORTRAIT(4, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    LOCKED_LANDSCAPE(5, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
}

enum class TextAlignment(val value: Int) {
    LEFT(0),
    CENTER(1),
    JUSTIFY(2),
    RIGHT(3)
}