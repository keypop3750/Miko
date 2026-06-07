package eu.kanade.tachiyomi.ui.novel.reader.settings

import android.content.Context
import android.util.AttributeSet
import androidx.core.widget.NestedScrollView
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.NovelReaderTextSettingsLayoutBinding
import eu.kanade.tachiyomi.ui.novel.reader.NovelReaderActivity
import uy.kohesive.injekt.injectLazy

/**
 * Text settings view for novel reader.
 * Controls text size, line height, paragraph spacing, and alignment.
 */
class NovelReaderTextSettingsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    NestedScrollView(context, attrs) {

    lateinit var activity: NovelReaderActivity
    private lateinit var binding: NovelReaderTextSettingsLayoutBinding
    internal val preferences: PreferencesHelper by injectLazy()

    override fun onFinishInflate() {
        super.onFinishInflate()
        clipToPadding = false
        binding = NovelReaderTextSettingsLayoutBinding.bind(this)
    }
    
    fun initGeneralPreferences() {
        // Text Size Slider
        binding.textSizeSlider.value = preferences.novelTextSize().get().toFloat()
        binding.textSizeValue.text = "${preferences.novelTextSize().get()}sp"
        binding.textSizeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val textSize = value.toInt()
                preferences.novelTextSize().set(textSize)
                binding.textSizeValue.text = "${textSize}sp"
                (activity as? NovelReaderActivity)?.updateTextSettings()
            }
        }

        // Line Height Slider
        binding.lineHeightSlider.value = preferences.novelLineHeight().get()
        binding.lineHeightValue.text = "%.1fx".format(preferences.novelLineHeight().get())
        binding.lineHeightSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                preferences.novelLineHeight().set(value)
                binding.lineHeightValue.text = "%.1fx".format(value)
                (activity as? NovelReaderActivity)?.updateTextSettings()
            }
        }

        // Paragraph Spacing Slider
        binding.paragraphSpacingSlider.value = preferences.novelParagraphSpacing().get().toFloat()
        binding.paragraphSpacingValue.text = "${preferences.novelParagraphSpacing().get()}dp"
        binding.paragraphSpacingSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val spacing = value.toInt()
                preferences.novelParagraphSpacing().set(spacing)
                binding.paragraphSpacingValue.text = "${spacing}dp"
                (activity as? NovelReaderActivity)?.updateTextSettings()
            }
        }

        // Text Alignment (Native Android justification API 26+)
        // 0 = Left, 1 = Center, 2 = Justify
        binding.textAlignment.bindToPreference(preferences.novelTextAlignment()) {
            (activity as? NovelReaderActivity)?.updateTextSettings()
        }
    }
}
