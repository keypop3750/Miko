package eu.kanade.tachiyomi.ui.setting

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import co.touchlab.kermit.Logger
import com.google.android.material.tabs.TabLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * A custom preference that displays a TabLayout for switching between Manga and Novel theme modes.
 */
class TabLayoutPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    var onTabSelected: ((isNovelMode: Boolean) -> Unit)? = null
    private var tabLayout: TabLayout? = null
    private var currentTab: Int = 0
    private var isListenerAttached = false

    init {
        layoutResource = R.layout.pref_tab_layout
        isSelectable = false
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        // Make sure the preference view is visible
        holder.itemView.visibility = View.VISIBLE
        
        tabLayout = holder.itemView.findViewById(R.id.theme_mode_tabs)
        
        Logger.d { "TabLayoutPreference: onBindViewHolder called, tabLayout = $tabLayout" }
        
        tabLayout?.apply {
            // Remove existing tabs if any
            removeAllTabs()
            
            // Add Manga and Novel tabs
            addTab(newTab().setText("Manga"))
            addTab(newTab().setText("Novel"))
            
            // Restore selected tab (without triggering listener)
            isListenerAttached = false
            getTabAt(currentTab)?.select()
            
            // Remove old listeners
            clearOnTabSelectedListeners()
            
            // Set up listener
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    if (!isListenerAttached) {
                        isListenerAttached = true
                        return
                    }
                    tab?.position?.let { pos ->
                        Logger.d { "TabLayoutPreference: Tab selected, position = $pos" }
                        currentTab = pos
                        onTabSelected?.invoke(pos == 1)
                    }
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
            
            // Mark listener as attached after initial selection
            post { isListenerAttached = true }
        }
    }

    fun setSelectedTab(isNovelMode: Boolean) {
        currentTab = if (isNovelMode) 1 else 0
        tabLayout?.apply {
            isListenerAttached = false
            getTabAt(currentTab)?.select()
            post { isListenerAttached = true }
        }
    }
}
