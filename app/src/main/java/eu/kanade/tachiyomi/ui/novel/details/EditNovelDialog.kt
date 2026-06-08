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
import eu.kanade.tachiyomi.databinding.EditNovelDialogBinding
import yokai.domain.novel.Novel
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.icon
// SManga not needed for novels
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.widget.TachiyomiTextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.novel.interactor.GetNovel
import yokai.domain.novel.interactor.UpdateNovel
import yokai.domain.novel.models.NovelUpdate
import yokai.domain.novel.models.cover
import yokai.i18n.MR
import yokai.util.coil.asTarget
import yokai.util.coil.loadNovel
import yokai.util.lang.getString
import android.R as AR
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.isNovelSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga

class EditNovelDialog : DialogController {

    private val novel: Novel

    private var customCoverUri: Uri? = null

    private var willResetCover = false
    private var migratedPosterUrl: String? = null

    private var _binding: EditNovelDialogBinding? = null
    val binding get() = _binding!!
    private val languages = mutableListOf<String>()

    private val infoController
        get() = targetController as NovelDetailsControllerNew

    constructor(target: NovelDetailsControllerNew, novel: Novel) : super(
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
        novel = runBlocking { Injekt.get<GetNovel>().awaitById(bundle.getLong(KEY_MANGA))!! }
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        _binding = EditNovelDialogBinding.inflate(activity!!.layoutInflater)
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

        binding.novelCover.loadNovel(novel)
        val isLocal = novel.isLocal()

        binding.novelLang.isVisible = isLocal
        if (isLocal) {
            if (novel.title != novel.url) {
                binding.title.append(novel.title)
            }
            binding.title.hint = "${context.getString(MR.strings.title)}: ${novel.url}"
            binding.novelAuthor.append(novel.author ?: "")
            binding.novelArtist.append(null /* novels do not have artist field */ ?: "")
            binding.novelDescription.append(novel.description ?: "")
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
            binding.novelLang.setEntries(
                languages.map {
                    LocaleHelper.getSourceDisplayName(it, binding.root.context)
                },
            )
            // TODO: Create LocalSource.getNovelLang() for novels
            binding.novelLang.setSelection(0)
        } else {
            // Match manga edit pattern: show current values as hints, leave fields empty.
            // Only populate text if user has previously customized (not tracked for novels yet).
            // Novels don't have artist field
            binding.novelArtist.isVisible = false

            binding.title.hint = "${context.getString(MR.strings.title)}: ${novel.title}"
            binding.novelAuthor.hint = "${context.getString(MR.strings.author)}: ${novel.author ?: ""}"
            binding.novelDescription.hint = "${context.getString(MR.strings.description)}: ${novel.description?.replace("\n", " ")?.chop(20) ?: ""}"

            // Long-click to restore current value (mimics manga's restore-original behavior)
            binding.title.appendOriginalTextOnLongClick(novel.title)
            binding.novelAuthor.appendOriginalTextOnLongClick(novel.author)
            binding.novelDescription.appendOriginalTextOnLongClick(novel.description)
        }
        setGenreTags(novel.genre?.split(",")?.map { it.trim() } ?: emptyList())
        // Novels use Int status (0-5), not SManga enum
        binding.novelStatus.setSelection(novel.status.coerceIn(0, 5))
        // Novels don't have series type
        binding.seriesType.isVisible = false
        // TODO: Novel sources don't have icon() method yet
        // if (!isLocal) {
        //     infoController.presenter.source.icon()?.let { icon ->
        //         val bitD = ImageUtil.resizeBitMapDrawable(icon, resources, 24.dpToPx)
        //         binding.novelStatus.originalIcon = bitD ?: icon
        //     }
        // }
        binding.novelGenresTags.clearFocus()
        // TODO: Implement cover changing in NovelDetailsControllerNew
        // binding.coverLayout.setOnClickListener {
        //     infoController.changeCover()
        // }
        binding.resetTags.setOnClickListener { resetTags() }
        // TODO: Novels don't have originalGenre - just show clear_tags
        binding.resetTags.text = context.getString(MR.strings.clear_tags)
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
            binding.novelCover.loadNovel(novel) {
                useCustomCover(false)
            }
            customCoverUri = null
            willResetCover = true
        }

        // Migrate buttons: hidden for local novels
        binding.migrateCover.isVisible = !isLocal
        binding.migrateInfo.isVisible = !isLocal
        binding.migrateCover.setOnClickListener { performMigrate(MigrateMode.COVER) }
        binding.migrateInfo.setOnClickListener { performMigrate(MigrateMode.INFO) }
    }

    private fun addTags(textCanBeBlank: Boolean = false) {
        if ((textCanBeBlank || !binding.addTagEditText.text.isNullOrBlank()) &&
            binding.addTagEditText.isVisible
        ) {
            val newTags = binding.addTagEditText.text.toString().split(",")
                .mapNotNull { tag -> tag.trim().takeUnless { it.isBlank() } }
            val tags: List<String> = binding.novelGenresTags.tags.toList() + newTags
            binding.addTagEditText.setText("")
            setGenreTags(tags)
            // Novels don't have seriesType
            // binding.seriesType.setSelection(novel.seriesType(customTags = tags.joinToString(", ")) - 1)
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
        with(binding.novelGenresTags) {
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
                    // TODO: Novels don't have seriesType
                    // val tags: List<String> = tags.toList() - (view as Chip).text.toString()
                    // binding.seriesType.setSelection(novel.seriesType(customTags = tags.joinToString(", ")) - 1)
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
        // TODO: Novels don't have originalGenres - just clear tags
        setGenreTags(emptyList())
    }

    fun updateCover(uri: Uri) {
        willResetCover = false
        binding.novelCover.load(uri)
        customCoverUri = uri
    }

    private fun onPositiveButtonClick() {
        addTags()
        infoController.presenter.updateNovel(
            binding.title.text.toString(),
            binding.novelAuthor.text.toString(),
            null, // novels don't have artist
            customCoverUri,
            binding.novelDescription.text.toString(),
            binding.novelGenresTags.tags.toList(),
            binding.novelStatus.selectedPosition,
            null, // novels don't have seriesType
            languages.getOrNull(binding.novelLang.selectedPosition),
            willResetCover,
        )

        // If a cover was migrated, also persist the posterUrl
        migratedPosterUrl?.let { posterUrl ->
            runBlocking {
                Injekt.get<UpdateNovel>().await(
                    NovelUpdate(id = novel.id, posterUrl = posterUrl)
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Migrate cover / info from another source
    // ------------------------------------------------------------------

    private enum class MigrateMode { COVER, INFO }

    private fun performMigrate(mode: MigrateMode) {
        val context = binding.root.context
        val sourceManager = Injekt.get<SourceManager>()
        val novelSources = sourceManager.getCatalogueSources()
            .filter { it.isNovelSource() }
            .filter { it.id != novel.source }
            .sortedBy { it.name }
            .toList()

        if (novelSources.isEmpty()) {
            context.toast("No other novel sources installed")
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(context.getString(MR.strings.select_source))
            .setItems(novelSources.map { it.name }.toTypedArray()) { _, which ->
                searchSource(novelSources[which], mode)
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }

    private fun searchSource(source: CatalogueSource, mode: MigrateMode) {
        val context = binding.root.context
        val progressDialog = android.app.ProgressDialog(context).apply {
            setMessage(context.getString(MR.strings.searching_))
            setCancelable(false)
            show()
        }

        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val page = source.getSearchManga(1, novel.title, FilterList())
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    if (page.mangas.isEmpty()) {
                        context.toast(context.getString(MR.strings.no_results_found))
                        return@withContext
                    }
                    showSearchResults(source, page.mangas, mode)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    context.toast("Search failed: ${e.message}")
                }
            }
        }
    }

    private fun showSearchResults(source: CatalogueSource, results: List<SManga>, mode: MigrateMode) {
        val context = binding.root.context
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Results from ${source.name}")
            .setItems(results.map { it.title }.toTypedArray()) { _, which ->
                fetchAndApply(source, results[which], mode)
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }

    private fun fetchAndApply(source: CatalogueSource, selected: SManga, mode: MigrateMode) {
        val context = binding.root.context
        val progressDialog = android.app.ProgressDialog(context).apply {
            setMessage("Fetching details…")
            setCancelable(false)
            show()
        }

        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val details = source.getMangaDetails(selected)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    applyMigration(details, mode)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    context.toast("Failed to fetch details: ${e.message}")
                }
            }
        }
    }

    private fun applyMigration(details: SManga, mode: MigrateMode) {
        val context = binding.root.context
        when (mode) {
            MigrateMode.COVER -> {
                migratedPosterUrl = details.thumbnail_url
                binding.novelCover.load(details.thumbnail_url)
                willResetCover = false
                customCoverUri = null
                context.toast(context.getString(MR.strings.migrate_cover_applied))
            }
            MigrateMode.INFO -> {
                binding.title.setText(details.title)
                binding.novelAuthor.setText(details.author)
                binding.novelDescription.setText(details.description)
                details.genre?.let { setGenreTags(it.split(",").map { g -> g.trim() }) }
                binding.novelStatus.setSelection(details.status.coerceIn(0, 5))
                context.toast(context.getString(MR.strings.migrate_info_applied))
            }
        }
    }

    private companion object {
        const val KEY_MANGA = "manga_id"
    }
}
