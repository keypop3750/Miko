package eu.kanade.tachiyomi.ui.novel.reader.settings

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.NovelReaderGeneralLayoutBinding
import eu.kanade.tachiyomi.ui.novel.reader.NovelReaderActivity
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBackgroundColor
import eu.kanade.tachiyomi.util.bindToPreference
import eu.kanade.tachiyomi.util.lang.addBetaTag
import eu.kanade.tachiyomi.util.system.DeviceUtil
import uy.kohesive.injekt.injectLazy
import yokai.domain.ui.settings.ReaderPreferences
import yokai.util.lang.getString

/**
 * General settings view for novel reader.
 * Most manga-specific settings are disabled (reading mode, orientation, page numbers).
 * Keeps: background color, fullscreen, keep screen on.
 */
class NovelReaderGeneralView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    NestedScrollView(context, attrs) {

    lateinit var activity: NovelReaderActivity
    private lateinit var binding: NovelReaderGeneralLayoutBinding
    internal val preferences: PreferencesHelper by injectLazy()
    internal val readerPreferences: ReaderPreferences by injectLazy()

    init {
        clipToPadding = false
    }
    
    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = NovelReaderGeneralLayoutBinding.bind(this)
    }
    
    fun initGeneralPreferences() {
        // Disable manga-specific settings (they're grayed out in XML but we disable them here too)
        binding.viewerSeries.isEnabled = false
        binding.rotationMode.isEnabled = false
        binding.showPageNumber.isEnabled = false
        binding.alwaysShowChapterTransition.isEnabled = false
        
        // Set to default values for novels (not changeable)
        binding.viewerSeries.setSelection(4) // Long strip mode
        binding.rotationMode.setSelection(0) // Free rotation

        // Background color - ENABLED for novels
        binding.backgroundColor.setEntries(
            ReaderBackgroundColor.entries
                .map { context.getString(it.stringRes) },
        )
        val selection = ReaderBackgroundColor.indexFromPref(preferences.readerTheme().get())
        binding.backgroundColor.setSelection(selection)
        binding.backgroundColor.onItemSelectedListener = { position ->
            val backgroundColor = ReaderBackgroundColor.entries[position]
            preferences.readerTheme().set(backgroundColor.prefValue)
            (activity as? NovelReaderActivity)?.applyReaderTheme(backgroundColor.prefValue)
        }

        // Fullscreen - ENABLED for novels
        binding.fullscreen.bindToPreference(preferences.fullscreen()) {
            updatePrefs()
        }
        
        // Cutout handling - ENABLED for novels
        binding.cutoutShort.bindToPreference(readerPreferences.cutoutShort())
        binding.cutoutShort.text = binding.cutoutShort.text.toString().addBetaTag(context)
        
        // Keep screen on - ENABLED for novels
        binding.keepscreen.bindToPreference(preferences.keepScreenOn())

        updatePrefs()
    }

    private fun updatePrefs() {
        binding.cutoutShort.isVisible =
            DeviceUtil.hasCutout(context as NovelReaderActivity).ordinal >= DeviceUtil.CutoutSupport.MODERN.ordinal && preferences.fullscreen().get()
    }
}
