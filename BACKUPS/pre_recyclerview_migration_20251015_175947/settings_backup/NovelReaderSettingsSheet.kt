package eu.kanade.tachiyomi.ui.novel.reader.settings

import android.animation.ValueAnimator
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.material.tabs.TabLayout
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import yokai.util.lang.getString
import eu.kanade.tachiyomi.databinding.ReaderColorFilterBinding
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.ui.novel.reader.NovelReaderActivity
import eu.kanade.tachiyomi.ui.reader.settings.ReaderFilterView
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.collapse
import eu.kanade.tachiyomi.util.view.expand
import eu.kanade.tachiyomi.util.view.isCollapsed
import eu.kanade.tachiyomi.widget.TabbedBottomSheetDialog

/**
 * Settings bottom sheet for novel reader.
 * Based on TabbedReaderSettingsSheet but adapted for novel-specific settings.
 * 
 * Key differences from manga reader:
 * - General tab: Grays out reading mode/orientation (novels always scroll vertically)
 * - Text Settings tab: Novel-specific text size, line height, alignment
 * - Filter tab: Reuses same color filter logic
 */
class NovelReaderSettingsSheet(
    val readerActivity: NovelReaderActivity,
    showColorFilterSettings: Boolean = false,
) : TabbedBottomSheetDialog(readerActivity) {
    
    private val generalView: NovelReaderGeneralView = View.inflate(
        readerActivity,
        R.layout.novel_reader_general_layout,
        null,
    ) as NovelReaderGeneralView
    
    private val textSettingsView: NovelReaderTextSettingsView = View.inflate(
        readerActivity,
        R.layout.novel_reader_text_settings_layout,
        null,
    ) as NovelReaderTextSettingsView

    override var offset = 0

    override fun getTabViews(): List<View> = listOf(
        generalView,
        textSettingsView,
    )

    override fun getTabTitles(): List<dev.icerock.moko.resources.StringResource> = listOf(
        MR.strings.general,
        MR.strings.text_settings,
    )

    init {
        generalView.activity = readerActivity
        generalView.initGeneralPreferences()
        textSettingsView.activity = readerActivity
        textSettingsView.initGeneralPreferences()

        binding.menu.isVisible = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            binding.menu.tooltipText = context.getString(MR.strings.reader_settings)
        }
        binding.menu.setImageDrawable(
            ContextCompat.getDrawable(
                context,
                R.drawable.ic_outline_settings_24dp,
            ),
        )
        binding.menu.setOnClickListener {
            val intent = SearchActivity.openReaderSettings(readerActivity)
            readerActivity.startActivity(intent)
            dismiss()
        }

        binding.pager.adapter?.notifyDataSetChanged()
        // Since we only have 2 tabs (no filter), always expand
        sheetBehavior.expand()
        sheetBehavior.skipCollapsed = true
    }

    override fun onStart() {
        super.onStart()
        sheetBehavior.skipCollapsed = true
    }

    override fun dismiss() {
        super.dismiss()
        readerActivity.binding.appBar.isVisible = true
    }
}
