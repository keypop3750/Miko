package eu.kanade.tachiyomi.ui.novel.details

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.view.isVisible
import coil3.load
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.useCustomCover
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.databinding.EditMangaDialogBinding
import yokai.domain.novel.Novel
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.widget.TachiyomiTextInputEditText
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
// TODO: Create GetNovel interactor and cover() extension
// import yokai.domain.novel.interactor.GetNovel
// import yokai.domain.novel.models.cover
import yokai.i18n.MR
import yokai.util.coil.asTarget
import yokai.util.coil.loadNovel
import yokai.util.lang.getString
import android.R as AR

class EditNovelDialog : DialogController {

    private val novel: Novel

    private var customCoverUri: Uri? = null

    private var willResetCover = false

    lateinit var binding: EditMangaDialogBinding
    private val languages = mutableListOf<String>()

    private val infoController
        get() = targetController as NovelDetailsController

    constructor(target: NovelDetailsController, novel: Novel) : super(
        Bundle()
            .apply {
                putLong(KEY_MANGA, novel.id)
            },
    ) {
        targetController = target
        this.novel = novel
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        // TODO: Implement GetNovel interactor
        novel = Novel(id = bundle.getLong(KEY_MANGA), source = 0, url = "", title = "", isFavorite = false)
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = EditMangaDialogBinding.inflate(activity!!.layoutInflater)
        val dialog = activity!!.materialAlertDialog().apply {
            setView(binding.root)
            setNegativeButton(AR.string.cancel, null)
            setPositiveButton(MR.strings.save) { _, _ -> onPositiveButtonClick() }
        }
        onViewCreated()
        val updateScrollIndicators = {
            binding.scrollIndicatorDown.isVisible = binding.scrollView.canScrollVertically(1)
        }
        binding.scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            updateScrollIndicators()
        }
        binding.scrollView.post {
            updateScrollIndicators()
        }
        return dialog.create()
    }

    fun onViewCreated() {
        val context = binding.root.context

        binding.mangaCover.loadNovel(novel)
        val isLocal = novel.isLocal()

        binding.mangaLang.isVisible = isLocal
        if (isLocal) {
            if (novel.title != novel.url) {
                binding.title.append(novel.title)
            }
            binding.title.hint = "${context.getString(MR.strings.title)}: ${novel.url}"
            binding.mangaAuthor.append(novel.author ?: "")
            binding.mangaArtist.append("") // Novels don't have separate artist field
            binding.mangaDescription.append(novel.description ?: "")
            val preferences = infoController.presenter.preferences
            val extensionManager: ExtensionManager by injectLazy()
            val activeLangs = preferences.enabledLanguages().get()

            languages.add("")
            languages.addAll(
                extensionManager.availableExtensionsFlow.value.groupBy { it.lang }.keys
                    .sortedWith(
                        compareBy(
                            { it !in activeLangs },
                            { LocaleHelper.getSourceDisplayName(it, binding.root.context) },
                        ),
                    )
                    .filter { it != "all" && it != "other" },
            )
            binding.mangaLang.setEntries(
                languages.map {
                    LocaleHelper.getSourceDisplayName(it, binding.root.context)
                },
            )
            // TODO: Implement novel language selection if needed
            binding.mangaLang.setSelection(0)
        } else {
            // For non-local novels, show current values
            binding.title.append(novel.title)
            binding.mangaAuthor.append(novel.author ?: "")
            binding.mangaArtist.append("") // Novels don't have separate artist field
            binding.mangaDescription.append(novel.description ?: "")
        }
        setGenreTags(novel.genre?.split(",")?.map { it.trim() } ?: emptyList())
        
        // Status dropdown
        binding.mangaStatus.setSelection(novel.status.coerceIn(SManga.UNKNOWN, SManga.ON_HIATUS))
        
        // Series type - novels may not use this, default to 0
        binding.seriesType.setSelection(0)
        binding.seriesType.onItemSelectedListener = {
            binding.resetsReadingMode.isVisible = false // TODO: Implement if needed
        }
        binding.mangaGenresTags.clearFocus()
        binding.coverLayout.setOnClickListener {
            infoController.changeCover()
        }
        binding.resetTags.setOnClickListener { resetTags() }
        binding.resetTags.text = context.getString(
            if (novel.genre.isNullOrBlank() || isLocal) {
                MR.strings.clear_tags
            } else {
                MR.strings.reset_tags
            },
        )
        binding.addTagChip.setOnClickListener {
            binding.addTagChip.isVisible = false
            binding.addTagEditText.isVisible = true
            binding.addTagEditText.requestFocus()
            showKeyboard()
        }
        binding.addTagEditText.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus && v.parent != null) {
                addTags()
            }
        }
        binding.addTagEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTags(true)
                binding.addTagEditText.clearFocus()
                hideKeyboard()
            } else {
                binding.addTagChip.isVisible = true
                binding.addTagEditText.isVisible = false
            }
            true
        }

        binding.resetCover.isVisible = !isLocal
        binding.resetCover.setOnClickListener {
            // TODO: Implement novel cover() extension
            /*
            binding.mangaCover.loadManga(
                novel.cover(),
                target = binding.mangaCover.asTarget(),
            ) {
                useCustomCover(false)
            }
            */
            customCoverUri = null
            willResetCover = true
        }
    }

    private fun addTags(textCanBeBlank: Boolean = false) {
        if ((textCanBeBlank || !binding.addTagEditText.text.isNullOrBlank()) &&
            binding.addTagEditText.isVisible
        ) {
            val newTags = binding.addTagEditText.text.toString().split(",")
                .mapNotNull { tag -> tag.trim().takeUnless { it.isBlank() } }
            val tags: List<String> = binding.mangaGenresTags.tags.toList() + newTags
            binding.addTagEditText.setText("")
            setGenreTags(tags)
            binding.seriesType.setSelection(novel.seriesType(customTags = tags.joinToString(", ")) - 1)
            binding.addTagChip.isVisible = true
            binding.addTagEditText.isVisible = false
        }
    }

    private fun TachiyomiTextInputEditText.appendOriginalTextOnLongClick(originalText: String?) {
        setOnLongClickListener {
            if (this.text.isNullOrBlank()) {
                this.append(originalText ?: "")
                true
            } else {
                false
            }
        }
    }

    private fun showKeyboard() {
        val inputMethodManager: InputMethodManager =
            binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(
            binding.addTagEditText,
            WindowManager.LayoutParams
                .SOFT_INPUT_ADJUST_PAN,
        )
    }

    private fun hideKeyboard() {
        val inputMethodManager: InputMethodManager =
            binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.addTagEditText.windowToken, 0)
    }

    private fun setGenreTags(genres: List<String>) {
        with(binding.mangaGenresTags) {
            val addTagChip = binding.addTagChip
            val addTagEditText = binding.addTagEditText
            removeAllViews()
            val dark = context.isInNightMode()
            val amoled = infoController.presenter.preferences.themeDarkAmoled().get()
            val baseTagColor = context.getResourceColor(R.attr.background)
            val bgArray = FloatArray(3)
            val accentArray = FloatArray(3)

            ColorUtils.colorToHSL(baseTagColor, bgArray)
            ColorUtils.colorToHSL(context.getResourceColor(R.attr.colorSecondary), accentArray)
            val downloadedColor = ColorUtils.setAlphaComponent(
                ColorUtils.HSLToColor(
                    floatArrayOf(
                        bgArray[0],
                        bgArray[1],
                        (
                            when {
                                amoled && dark -> 0.1f
                                dark -> 0.225f
                                else -> 0.85f
                            }
                            ),
                    ),
                ),
                199,
            )
            val textColor = ColorUtils.HSLToColor(
                floatArrayOf(
                    accentArray[0],
                    accentArray[1],
                    if (dark) 0.945f else 0.175f,
                ),
            )
            genres.map { genreText ->
                val chip = LayoutInflater.from(binding.root.context).inflate(
                    R.layout.genre_chip,
                    this,
                    false,
                ) as Chip
                val id = View.generateViewId()
                chip.id = id
                chip.chipBackgroundColor = ColorStateList.valueOf(downloadedColor)
                chip.setTextColor(textColor)
                chip.text = genreText
                chip.isCloseIconVisible = true
                chip.setOnCloseIconClickListener { view ->
                    this.removeView(view)
                    val tags: List<String> = tags.toList() - (view as Chip).text.toString()
                    binding.seriesType.setSelection(
                        novel.seriesType(
                            customTags = tags.joinToString(
                                ", ",
                            ),
                        ) - 1,
                    )
                }
                this.addView(chip)
            }
            addView(addTagChip)
            addView(addTagEditText)
        }
    }

    private val ChipGroup.tags: Array<String>
        get() = children
            .toList()
            .filterIsInstance<Chip>()
            .filter { it.isCloseIconVisible }
            .map { it.text.toString() }
            .toTypedArray()

    private fun resetTags() {
        if (novel.genre.isNullOrBlank() || novel.isLocal()) {
            setGenreTags(emptyList())
        } else {
            // Novel.genre is a comma-separated string, not a method
            setGenreTags(novel.genre?.split(",")?.map { it.trim() }.orEmpty())
            binding.seriesType.setSelection(0) // TODO: Implement novel.seriesType() if needed
            binding.resetsReadingMode.isVisible = false
        }
    }

    fun updateCover(uri: Uri) {
        willResetCover = false
        binding.mangaCover.load(uri)
        customCoverUri = uri
    }

    private fun onPositiveButtonClick() {
        addTags()
        // TODO: Implement updateNovel in NovelDetailsPresenter
        infoController.presenter.updateNovel(
            binding.title.text.toString(),
            binding.mangaAuthor.text.toString(),
            "", // Novels don't have separate artist field
            customCoverUri,
            binding.mangaDescription.text.toString(),
            binding.mangaGenresTags.tags,
            binding.mangaStatus.selectedPosition,
            if (binding.resetsReadingMode.isVisible) binding.seriesType.selectedPosition + 1 else null,
            languages.getOrNull(binding.mangaLang.selectedPosition),
            willResetCover,
        )
    }

    private companion object {
        const val KEY_MANGA = "manga_id"
    }
}



