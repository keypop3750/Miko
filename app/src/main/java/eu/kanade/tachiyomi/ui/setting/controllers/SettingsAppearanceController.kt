package eu.kanade.tachiyomi.ui.setting.controllers

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import yokai.util.lang.getString
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.TabLayoutPreference
import eu.kanade.tachiyomi.ui.setting.ThemePreference
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.defaultValue
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.onChange
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.summaryMRes as summaryRes
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.ui.setting.themePreference
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes
import eu.kanade.tachiyomi.util.system.SideNavMode
import eu.kanade.tachiyomi.util.system.appDelegateNightMode
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getPrefTheme
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.moveRecyclerViewUp
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager
import kotlin.math.max
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsAppearanceController : SettingsLegacyController() {

    var lastThemeXLight: Int? = null
    var lastThemeXDark: Int? = null
    var themePreference: ThemePreference? = null
    
    /** Tracks if we're currently showing novel theme settings */
    private var isNovelMode: Boolean = false
    private var tabLayoutPreference: TabLayoutPreference? = null
    private var amoledPreference: androidx.preference.SwitchPreferenceCompat? = null

    @SuppressLint("NotifyDataSetChanged")
    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.appearance

        // Add the tab layout preference at the very top
        val tabPref = TabLayoutPreference(context).apply {
            key = "theme_mode_tabs_pref"
            this@SettingsAppearanceController.isNovelMode = ModeManager.isNovelMode()
            setSelectedTab(this@SettingsAppearanceController.isNovelMode)
            
            onTabSelected = { novelMode ->
                this@SettingsAppearanceController.isNovelMode = novelMode
                
                // Switch the actual app mode
                val newMode = if (novelMode) ContentType.NOVEL else ContentType.MANGA
                ModeManager.setMode(newMode)
                
                // Update theme preference to show correct themes
                updateThemePreferenceMode()
            }
        }
        tabLayoutPreference = tabPref
        addPreference(tabPref)

        preferenceCategory {
            titleRes = MR.strings.app_theme

            themePreference = themePreference {
                key = "theme_preference"
                titleRes = MR.strings.app_theme
                lastScrollPostionLight = lastThemeXLight
                lastScrollPostionDark = lastThemeXDark
                summary = context.getString(context.getPrefTheme(preferences).nameRes)
                activity = this@SettingsAppearanceController.activity
                // Set novel mode based on current mode
                isNovelTheme = this@SettingsAppearanceController.isNovelMode
            }

            switchPreference {
                key = "night_mode_switch"
                isPersistent = false
                titleRes = MR.strings.follow_system_theme
                isChecked =
                    preferences.nightMode().get() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

                onChange {
                    if (it == true) {
                        preferences.nightMode().set(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        activity?.recreate()
                    } else {
                        preferences.nightMode().set(context.appDelegateNightMode())
                        themePreference?.fastAdapterLight?.notifyDataSetChanged()
                        themePreference?.fastAdapterDark?.notifyDataSetChanged()
                    }
                    true
                }
                preferences.nightMode().changes().onEach { mode ->
                    isChecked = mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }.launchIn(viewScope)
            }

            amoledPreference = switchPreference {
                key = Keys.themeDarkAmoled
                titleRes = MR.strings.pure_black_dark_mode
                defaultValue = false

                preferences.nightMode().changesIn(viewScope) { mode ->
                    isVisible = mode != AppCompatDelegate.MODE_NIGHT_NO
                }

                onChange {
                    if (context.isInNightMode()) {
                        activity?.recreate()
                    } else {
                        themePreference?.fastAdapterDark?.notifyDataSetChanged()
                    }
                    true
                }
            }
        }

        preferenceCategory {
            switchPreference {
                bindTo(preferences.useLargeToolbar())
                titleRes = MR.strings.expanded_toolbar
                summaryRes = MR.strings.show_larger_toolbar

                onChange {
                    val useLarge = it as Boolean
                    activityBinding?.appBar?.setToolbarModeBy(this@SettingsAppearanceController, !useLarge)
                    activityBinding?.appBar?.hideBigView(!useLarge, !useLarge)
                    activityBinding?.toolbar?.alpha = 1f
                    activityBinding?.toolbar?.translationY = 0f
                    activityBinding?.toolbar?.isVisible = true
                    activityBinding?.appBar?.doOnNextLayout {
                        listView.requestApplyInsets()
                        listView.post {
                            if (useLarge) {
                                moveRecyclerViewUp(true)
                            } else {
                                activityBinding?.appBar?.updateAppBarAfterY(listView)
                            }
                        }
                    }
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.details_page
            switchPreference {
                key = Keys.themeMangaDetails
                titleRes = MR.strings.theme_buttons_based_on_cover
                defaultValue = true
            }
        }

        preferenceCategory {
            titleRes = MR.strings.animation
            switchPreference {
                key = Keys.sourceOpeningAnimation
                titleRes = MR.strings.source_opening_animation
                summaryRes = MR.strings.source_opening_animation_summary
                defaultValue = true
            }
            
            switchPreference {
                key = "enable_shared_element_transitions"
                titleRes = MR.strings.enable_shared_element_transitions
                summaryRes = MR.strings.enable_shared_element_transitions_summary
                defaultValue = false
            }
            
            switchPreference {
                key = "use_manga_details_activity"
                titleRes = MR.strings.use_manga_details_activity
                summaryRes = MR.strings.use_manga_details_activity_summary
                defaultValue = true
            }
        }

        preferenceCategory {
            titleRes = MR.strings.navigation

            switchPreference {
                key = Keys.hideBottomNavOnScroll
                titleRes = MR.strings.hide_bottom_nav
                summaryRes = MR.strings.hides_on_scroll
                defaultValue = true
            }

            intListPreference(activity) {
                key = Keys.sideNavIconAlignment
                titleRes = MR.strings.side_nav_icon_alignment
                entriesRes = arrayOf(MR.strings.top, MR.strings.center, MR.strings.bottom)
                entryRange = 0..2
                defaultValue = 1
                isVisible = max(
                    context.resources.displayMetrics.widthPixels,
                    context.resources.displayMetrics.heightPixels,
                ) >= 720.dpToPx
            }

            intListPreference(activity) {
                key = Keys.sideNavMode
                titleRes = MR.strings.use_side_navigation
                val values = SideNavMode.entries
                entriesRes = values.map { it.stringRes }.toTypedArray()
                entryValues = values.map { it.prefValue }
                defaultValue = SideNavMode.DEFAULT.prefValue

                onChange {
                    activity?.recreate()
                    true
                }
            }

            infoPreference(MR.strings.by_default_side_nav_info)
        }
    }

    /**
     * Update the theme preference to show manga or novel themes based on selected tab.
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun updateThemePreferenceMode() {
        themePreference?.isNovelTheme = isNovelMode
        
        // Update the AMOLED preference key to use the correct preference
        amoledPreference?.key = if (isNovelMode) Keys.novelThemeDarkAmoled else Keys.themeDarkAmoled
        
        // Refresh the checked state of AMOLED preference
        amoledPreference?.isChecked = if (isNovelMode) {
            preferences.novelThemeDarkAmoled().get()
        } else {
            preferences.themeDarkAmoled().get()
        }
        
        // Refresh the theme preference summary
        themePreference?.summary = if (isNovelMode) {
            val theme = if (view?.context?.isInNightMode() == true) {
                preferences.novelDarkTheme().get()
            } else {
                preferences.novelLightTheme().get()
            }
            view?.context?.getString(theme.nameRes)
        } else {
            view?.context?.let { ctx ->
                ctx.getString(ctx.getPrefTheme(preferences).nameRes)
            }
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        themePreference = null
        tabLayoutPreference = null
        amoledPreference = null
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        outState.putInt(::lastThemeXLight.name, themePreference?.lastScrollPostionLight ?: 0)
        outState.putInt(::lastThemeXDark.name, themePreference?.lastScrollPostionDark ?: 0)
        outState.putBoolean("is_novel_mode", isNovelMode)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        lastThemeXLight = savedViewState.getInt(::lastThemeXLight.name)
        lastThemeXDark = savedViewState.getInt(::lastThemeXDark.name)
        isNovelMode = savedViewState.getBoolean("is_novel_mode", false)
        themePreference?.lastScrollPostionLight = lastThemeXLight
        themePreference?.lastScrollPostionDark = lastThemeXDark
        
        // Restore tab selection
        tabLayoutPreference?.setSelectedTab(isNovelMode)
        updateThemePreferenceMode()
    }
}
