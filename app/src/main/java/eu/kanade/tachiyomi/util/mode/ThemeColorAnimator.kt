package eu.kanade.tachiyomi.util.mode

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.Themes
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isDarkMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.core.content.ContentType

/**
 * Provides theme color extraction utilities for mode switching.
 * 
 * The actual animation is now handled by CircularRevealThemeAnimator,
 * which captures a screenshot and reveals the new theme underneath.
 * Compose UI updates reactively via YokaiTheme observing ModeManager.currentMode.
 * 
 * This class is retained for palette extraction and legacy color utilities.
 */
class ThemeColorAnimator(
    private val preferences: PreferencesHelper = Injekt.get()
) {
    
    companion object {
        /**
         * Key theme attributes for color extraction.
         */
        private val THEME_COLOR_ATTRS = intArrayOf(
            R.attr.colorPrimary,
            R.attr.colorPrimaryVariant,
            R.attr.colorOnPrimary,
            R.attr.colorSecondary,
            R.attr.colorOnSecondary,
            R.attr.colorSurface,
            R.attr.colorOnSurface,
            android.R.attr.colorBackground,
            android.R.attr.statusBarColor,
            android.R.attr.navigationBarColor,
            R.attr.colorSurfaceVariant,
            R.attr.actionBarTintColor,
        )
    }
    
    /**
     * Data class holding a snapshot of theme colors.
     */
    data class ThemeColorPalette(
        @ColorInt val colorPrimary: Int,
        @ColorInt val colorPrimaryVariant: Int,
        @ColorInt val colorOnPrimary: Int,
        @ColorInt val colorSecondary: Int,
        @ColorInt val colorOnSecondary: Int,
        @ColorInt val colorSurface: Int,
        @ColorInt val colorOnSurface: Int,
        @ColorInt val colorBackground: Int,
        @ColorInt val statusBarColor: Int,
        @ColorInt val navigationBarColor: Int,
        @ColorInt val colorSurfaceVariant: Int,
        @ColorInt val actionBarTintColor: Int,
        val isDark: Boolean,
        val isAmoled: Boolean,
    ) {
        /** Get colorControlHighlight based on theme (approximation) */
        @get:ColorInt
        val colorControlHighlight: Int
            get() = androidx.core.graphics.ColorUtils.setAlphaComponent(
                if (isDark) Color.WHITE else Color.BLACK,
                if (isDark) 51 else 31 // ~20% or ~12% alpha
            )
        
        /** Get textColorSecondary based on theme */
        @get:ColorInt
        val textColorSecondary: Int
            get() = androidx.core.graphics.ColorUtils.setAlphaComponent(
                colorOnSurface,
                178 // ~70% alpha
            )
    }
    
    /**
     * Extract the color palette for a given mode.
     */
    fun extractPaletteForMode(context: Context, mode: ContentType): ThemeColorPalette {
        val isDark = context.isDarkMode(preferences)
        
        val theme = if (mode == ContentType.NOVEL) {
            if (isDark) preferences.novelDarkTheme().get() else preferences.novelLightTheme().get()
        } else {
            if (isDark) preferences.darkTheme().get() else preferences.lightTheme().get()
        }
        
        val isAmoled = if (mode == ContentType.NOVEL) {
            isDark && preferences.novelThemeDarkAmoled().get()
        } else {
            isDark && preferences.themeDarkAmoled().get()
        }
        
        return extractPaletteFromTheme(context, theme, isDark, isAmoled)
    }
    
    /**
     * Extract color palette from a specific theme resource.
     */
    fun extractPaletteFromTheme(context: Context, theme: Themes, isDark: Boolean, isAmoled: Boolean): ThemeColorPalette {
        // Create a themed context wrapper to extract accurate colors
        val themedContext = androidx.appcompat.view.ContextThemeWrapper(context, theme.styleRes)
        
        // Apply amoled overlay if needed
        if (isAmoled) {
            themedContext.theme.applyStyle(R.style.ThemeOverlay_Tachiyomi_Amoled, true)
        }
        
        return ThemeColorPalette(
            colorPrimary = themedContext.getResourceColor(R.attr.colorPrimary),
            colorPrimaryVariant = themedContext.getResourceColor(R.attr.colorPrimaryVariant),
            colorOnPrimary = themedContext.getResourceColor(R.attr.colorOnPrimary),
            colorSecondary = themedContext.getResourceColor(R.attr.colorSecondary),
            colorOnSecondary = themedContext.getResourceColor(R.attr.colorOnSecondary),
            colorSurface = themedContext.getResourceColor(R.attr.colorSurface),
            colorOnSurface = themedContext.getResourceColor(R.attr.colorOnSurface),
            colorBackground = themedContext.getResourceColor(android.R.attr.colorBackground),
            statusBarColor = themedContext.getResourceColor(android.R.attr.statusBarColor),
            navigationBarColor = themedContext.getResourceColor(android.R.attr.navigationBarColor),
            colorSurfaceVariant = themedContext.getResourceColor(R.attr.colorSurfaceVariant),
            actionBarTintColor = themedContext.getResourceColor(R.attr.actionBarTintColor),
            isDark = isDark,
            isAmoled = isAmoled,
        )
    }
    
    /**
     * Get theme for a content mode.
     */
    fun getThemeForMode(mode: ContentType, isDark: Boolean): Themes {
        return if (mode == ContentType.NOVEL) {
            if (isDark) preferences.novelDarkTheme().get() else preferences.novelLightTheme().get()
        } else {
            if (isDark) preferences.darkTheme().get() else preferences.lightTheme().get()
        }
    }
    
    /**
     * Get amoled setting for a content mode.
     */
    fun getAmoledForMode(mode: ContentType, isDark: Boolean): Boolean {
        if (!isDark) return false
        return if (mode == ContentType.NOVEL) {
            preferences.novelThemeDarkAmoled().get()
        } else {
            preferences.themeDarkAmoled().get()
        }
    }
}
