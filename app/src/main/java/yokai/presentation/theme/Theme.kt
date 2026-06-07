package yokai.presentation.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isDarkMode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager

/**
 * Reactive Compose theme that automatically updates when mode changes.
 * 
 * Uses ModeManager.currentMode as a StateFlow to trigger recomposition
 * when the mode switches between MANGA and NOVEL. This eliminates the need
 * for manual color updates or activity recreation.
 * 
 * Also updates system bars (status bar) reactively via SideEffect.
 */
@Composable
fun YokaiTheme(
    preferences: PreferencesHelper = Injekt.get(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    
    // Observe mode changes reactively - this triggers recomposition on mode switch
    val currentMode by ModeManager.currentMode.collectAsState()
    
    // Get isDark state
    val isDark = context.isDarkMode(preferences)
    
    // Build color scheme based on current mode and dark state
    // NO remember caching - we want fresh colors every time mode changes
    // buildColorScheme creates its own ContextThemeWrapper with the correct theme
    val colorScheme = buildColorScheme(context, currentMode, preferences, isDark)
    
    // Update system bars reactively when theme changes
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Status bar uses background color to match the app background
            window.statusBarColor = colorScheme.background.toArgb()
            
            // Update status bar icons to be light/dark based on theme
            val wic = WindowInsetsControllerCompat(window, view)
            wic.isAppearanceLightStatusBars = !isDark
        }
    }
    
    // Use key() to force full recomposition of the subtree when mode changes
    // This ensures all children get the new color scheme
    key(currentMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

/**
 * Build a Material3 ColorScheme based on the current mode.
 * Extracts colors from the Android theme resources for the specified mode.
 */
private fun buildColorScheme(
    context: android.content.Context,
    mode: ContentType,
    preferences: PreferencesHelper,
    isDark: Boolean
): ColorScheme {
    // Get mode-specific theme
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
    
    co.touchlab.kermit.Logger.d { "🎨 [YOKAI_THEME] Building color scheme for mode=$mode, theme=${theme.name}, isDark=$isDark, isAmoled=$isAmoled, themeRes=${theme.styleRes}" }
    
    // Create a context wrapper with the theme to extract colors
    val themedContext = androidx.appcompat.view.ContextThemeWrapper(context, theme.styleRes)
    if (isAmoled) {
        themedContext.theme.applyStyle(eu.kanade.tachiyomi.R.style.ThemeOverlay_Tachiyomi_Amoled, true)
    }
    
    // Extract colors from the themed context
    val primary = Color(themedContext.getResourceColor(androidx.appcompat.R.attr.colorPrimary))
    val onPrimary = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorOnPrimary))
    val primaryContainer = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorPrimaryContainer))
    val onPrimaryContainer = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorOnPrimaryContainer))
    val secondary = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorSecondary))
    val onSecondary = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorOnSecondary))
    val secondaryContainer = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorSecondaryContainer))
    val onSecondaryContainer = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorOnSecondaryContainer))
    val tertiary = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorTertiary))
    val onTertiary = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorOnTertiary))
    val background = Color(themedContext.getResourceColor(android.R.attr.colorBackground))
    val onBackground = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorOnBackground))
    val surface = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorSurface))
    val onSurface = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorOnSurface))
    // Use colorPrimaryVariant for surfaceVariant to match search bar styling
    val surfaceVariantColor = themedContext.getResourceColor(eu.kanade.tachiyomi.R.attr.colorPrimaryVariant)
    val surfaceVariant = Color(surfaceVariantColor)
    co.touchlab.kermit.Logger.d { "🎨 [YOKAI_THEME] surfaceVariant=0x${Integer.toHexString(surfaceVariantColor)}, primary=0x${Integer.toHexString(themedContext.getResourceColor(androidx.appcompat.R.attr.colorPrimary))}" }
    val onSurfaceVariant = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
    val error = Color(themedContext.getResourceColor(android.R.attr.colorError))
    val onError = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorOnError))
    val outline = Color(themedContext.getResourceColor(com.google.android.material.R.attr.colorOutline))
    
    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            onError = onError,
            outline = outline,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            onError = onError,
            outline = outline,
        )
    }
}
