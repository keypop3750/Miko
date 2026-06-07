package eu.kanade.tachiyomi.ui.novel.reader

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * Computes theme-aware highlight colors based on the app's accent color
 * and the reader's current background color.
 */
object HighlightColorUtils {

    /**
     * Generate 5 distinct highlight colors that are readable against the given background.
     * Colors are derived from the accent color with hue rotations, then blended with
     * the background so text remains legible.
     */
    fun computeColors(accentColor: Int, backgroundColor: Int): List<Int> {
        val bgLuminance = ColorUtils.calculateLuminance(backgroundColor)
        val isDarkBg = bgLuminance < 0.5

        // Base hues to rotate by (degrees)
        val hueRotations = listOf(0f, 45f, 90f, 180f, 270f)

        return hueRotations.map { rotation ->
            val hsv = FloatArray(3)
            Color.colorToHSV(accentColor, hsv)
            hsv[0] = (hsv[0] + rotation) % 360f

            // Adjust saturation and value based on background
            if (isDarkBg) {
                // Dark background: lower value (darker), moderate saturation
                hsv[1] = (hsv[1] * 0.75f + 0.15f).coerceIn(0.55f, 0.9f)
                hsv[2] = (hsv[2] * 0.5f + 0.4f).coerceIn(0.5f, 0.72f)
            } else {
                // Light background: higher value (lighter), moderate saturation
                hsv[1] = (hsv[1] * 0.7f + 0.1f).coerceIn(0.45f, 0.85f)
                hsv[2] = (hsv[2] * 0.25f + 0.75f).coerceIn(0.82f, 0.98f)
            }

            val rotated = Color.HSVToColor(hsv)
            // Blend with background at ~35% opacity for subtlety
            val alpha = if (isDarkBg) 0.55f else 0.45f
            ColorUtils.blendARGB(backgroundColor, rotated, alpha)
        }
    }

    /**
     * Convert an ARGB color to a hex string for serialization.
     */
    fun toHex(color: Int): String = String.format("#%08X", color)

    /**
     * Parse a hex string back to ARGB.
     */
    fun fromHex(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (e: Exception) {
        Color.YELLOW
    }
}
