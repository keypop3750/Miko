package eu.kanade.tachiyomi.ui.novel.reader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages novel text highlights: persistence, .md export, and retrieval.
 * Highlights are stored as JSON per novel (for fast access) and exported to .md.
 * Supports colors, notes, merging overlapping highlights, and deletion.
 */
class NovelHighlightManager(context: Context) {

    private val appContext = context.applicationContext
    private val highlightsDir = File(appContext.getExternalFilesDir(null), "highlights")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    companion object {
        // 5 distinct highlight colors (ARGB hex strings for serialization portability)
        const val COLOR_YELLOW = "#FFFF8D"   // Warm yellow
        const val COLOR_GREEN = "#B9F6CA"    // Mint green
        const val COLOR_BLUE = "#B3E5FC"     // Light blue
        const val COLOR_PINK = "#F8BBD0"    // Soft pink
        const val COLOR_PURPLE = "#E1BEE7"  // Light purple

        val DEFAULT_COLORS = listOf(COLOR_YELLOW, COLOR_GREEN, COLOR_BLUE, COLOR_PINK, COLOR_PURPLE)
    }

    init {
        highlightsDir.mkdirs()
    }

    /**
     * Save a highlight for the given novel and chapter.
     * If the selected text overlaps with an existing highlight, they are merged.
     */
    fun saveHighlight(
        novelKey: NovelKey,
        chapterNumber: Double,
        chapterTitle: String,
        selectedText: String,
        paragraphIndex: Int = 0,
        color: String = COLOR_YELLOW,
        note: String? = null,
        posterUrl: String? = null,
    ) {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val data = loadData(novelKey)
            val existingChapter = data.chapters.find { it.chapterNumber == chapterNumber }
            val existingHighlights = existingChapter?.highlights ?: emptyList()

            // Check for overlapping highlights and merge them
            val (mergedHighlights, wasMerged) = mergeIfOverlapping(
                existingHighlights,
                selectedText,
                color,
                note
            )

            val chapterHighlights = if (existingChapter != null) {
                existingChapter.copy(highlights = mergedHighlights)
            } else {
                ChapterHighlights(
                    chapterNumber = chapterNumber,
                    chapterTitle = chapterTitle,
                    highlights = mergedHighlights
                )
            }

            val updatedChapters = data.chapters.filter { it.chapterNumber != chapterNumber } + chapterHighlights
            val updatedData = data.copy(
                chapters = updatedChapters,
                posterUrl = posterUrl ?: data.posterUrl,
            )
            writeJson(novelKey, updatedData)
            exportToMd(novelKey, updatedData)
        }
    }

    /**
     * Delete a specific highlight by its text and timestamp.
     */
    fun deleteHighlight(novelKey: NovelKey, chapterNumber: Double, highlightText: String, timestamp: Long) {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val data = loadData(novelKey)
            val chapter = data.chapters.find { it.chapterNumber == chapterNumber } ?: return@launch
            val updatedHighlights = chapter.highlights.filterNot {
                it.text == highlightText && it.timestamp == timestamp
            }
            if (updatedHighlights.isEmpty()) {
                // Remove empty chapter
                val updatedChapters = data.chapters.filter { it.chapterNumber != chapterNumber }
                val updatedData = data.copy(chapters = updatedChapters)
                writeJson(novelKey, updatedData)
                exportToMd(novelKey, updatedData)
            } else {
                val updatedChapter = chapter.copy(highlights = updatedHighlights)
                val updatedChapters = data.chapters.filter { it.chapterNumber != chapterNumber } + updatedChapter
                val updatedData = data.copy(chapters = updatedChapters)
                writeJson(novelKey, updatedData)
                exportToMd(novelKey, updatedData)
            }
        }
    }

    /**
     * Update the note for a specific highlight.
     */
    fun updateHighlightNote(novelKey: NovelKey, chapterNumber: Double, highlightText: String, timestamp: Long, note: String?) {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val data = loadData(novelKey)
            val chapter = data.chapters.find { it.chapterNumber == chapterNumber } ?: return@launch
            val updatedHighlights = chapter.highlights.map {
                if (it.text == highlightText && it.timestamp == timestamp) {
                    it.copy(note = note)
                } else it
            }
            val updatedChapter = chapter.copy(highlights = updatedHighlights)
            val updatedChapters = data.chapters.filter { it.chapterNumber != chapterNumber } + updatedChapter
            val updatedData = data.copy(chapters = updatedChapters)
            writeJson(novelKey, updatedData)
            exportToMd(novelKey, updatedData)
        }
    }

    /**
     * Get all highlights for a specific chapter.
     */
    fun getChapterHighlights(novelKey: NovelKey, chapterNumber: Double): List<HighlightEntry> {
        return loadData(novelKey).chapters.find { it.chapterNumber == chapterNumber }?.highlights ?: emptyList()
    }

    /**
     * Get all highlights for a novel, grouped by chapter.
     */
    fun getAllHighlights(novelKey: NovelKey): NovelHighlightsData {
        return loadData(novelKey)
    }

    /**
     * Check if a novel has any highlights.
     */
    fun hasHighlights(novelKey: NovelKey): Boolean {
        return loadData(novelKey).chapters.any { it.highlights.isNotEmpty() }
    }

    /**
     * Get all novels that have at least one highlight.
     * Returns a list of NovelHighlightsData sorted by most recent highlight.
     */
    fun getAllNovelsWithHighlights(): List<NovelHighlightsData> {
        if (!highlightsDir.exists() || !highlightsDir.isDirectory) return emptyList()
        val files = highlightsDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                val data = json.decodeFromString(NovelHighlightsData.serializer(), file.readText())
                if (data.chapters.any { it.highlights.isNotEmpty() }) data else null
            } catch (_: Exception) { null }
        }.sortedByDescending { novel ->
            novel.chapters.flatMap { it.highlights }.maxOfOrNull { it.timestamp } ?: 0L
        }
    }

    /**
     * Count total highlights across all novels.
     */
    fun getTotalHighlightCount(novelKey: NovelKey): Int {
        return loadData(novelKey).chapters.sumOf { it.highlights.size }
    }

    /**
     * Merge selected text with an existing highlight if they overlap.
     * Returns the updated list and whether a merge occurred.
     */
    private fun mergeIfOverlapping(
        existing: List<HighlightEntry>,
        newText: String,
        newColor: String,
        newNote: String?
    ): Pair<List<HighlightEntry>, Boolean> {
        // Find any highlight that shares at least one word with the new text
        val overlapping = existing.find { existingHl ->
            val existingWords = existingHl.text.split(Regex("\\s+")).toSet()
            val newWords = newText.split(Regex("\\s+")).toSet()
            existingWords.intersect(newWords).isNotEmpty()
        }

        return if (overlapping != null) {
            // Merge: combine text (union), keep newer color/note, use latest timestamp
            val mergedText = if (overlapping.text.contains(newText) || newText.contains(overlapping.text)) {
                if (overlapping.text.length >= newText.length) overlapping.text else newText
            } else {
                // Partial overlap: just take the longer one or combine them
                val combined = "$newText ${overlapping.text}".trim()
                combined
            }
            val merged = overlapping.copy(
                text = mergedText,
                color = newColor,
                note = newNote ?: overlapping.note,
                timestamp = System.currentTimeMillis()
            )
            existing.filterNot { it === overlapping } + merged to true
        } else {
            existing + HighlightEntry(
                text = newText,
                color = newColor,
                note = newNote,
                timestamp = System.currentTimeMillis()
            ) to false
        }
    }

    private fun loadData(novelKey: NovelKey): NovelHighlightsData {
        val file = File(highlightsDir, "${novelKey.fileName()}.json")
        if (!file.exists()) return NovelHighlightsData(
            novelTitle = novelKey.title,
            author = novelKey.author,
            description = novelKey.description,
            chapters = emptyList()
        )
        return try {
            json.decodeFromString(NovelHighlightsData.serializer(), file.readText())
        } catch (e: Exception) {
            NovelHighlightsData(
                novelTitle = novelKey.title,
                author = novelKey.author,
                description = novelKey.description,
                chapters = emptyList()
            )
        }
    }

    private fun writeJson(novelKey: NovelKey, data: NovelHighlightsData) {
        val file = File(highlightsDir, "${novelKey.fileName()}.json")
        file.writeText(json.encodeToString(data))
    }

    /**
     * Export highlights to a .md file in the same highlights directory.
     * Each highlight is shown as a callout with a colored bar and optional note.
     */
    private fun exportToMd(novelKey: NovelKey, data: NovelHighlightsData) {
        val file = File(highlightsDir, "${novelKey.fileName()}.md")
        val sb = StringBuilder()
        sb.appendLine("# ${data.novelTitle}")
        if (!data.author.isNullOrBlank()) sb.appendLine("**Author:** ${data.author}")
        if (!data.description.isNullOrBlank()) sb.appendLine("**Description:** ${data.description}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        val sortedChapters = data.chapters.sortedBy { it.chapterNumber }
        for (ch in sortedChapters) {
            if (ch.highlights.isEmpty()) continue
            sb.appendLine("### ${ch.chapterTitle.ifBlank { "Chapter ${ch.chapterNumber}" }}")
            for (hl in ch.highlights) {
                val colorBar = hl.color?.let { "![color]($it) " } ?: ""
                sb.appendLine("> $colorBar\"${hl.text}\" — ${dateFormat.format(Date(hl.timestamp))}")
                if (!hl.note.isNullOrBlank()) {
                    sb.appendLine("> *Note: ${hl.note}*")
                }
                sb.appendLine()
            }
        }
        file.writeText(sb.toString())
    }

    /**
     * Key for identifying a novel across migrations.
     * Uses title + author for matching (source/ID may change on migration).
     */
    @Serializable
    data class NovelKey(
        val title: String,
        val author: String? = null,
        val description: String? = null,
    ) {
        fun fileName(): String {
            val base = title.trim().replace(Regex("[^a-zA-Z0-9\\s-]"), "").replace(Regex("\\s+"), "_")
            val auth = author?.trim()?.replace(Regex("[^a-zA-Z0-9\\s-]"), "")?.replace(Regex("\\s+"), "_")
            return if (auth.isNullOrBlank()) base else "${base}_$auth"
        }
    }

    @Serializable
    data class NovelHighlightsData(
        val novelTitle: String,
        val author: String? = null,
        val description: String? = null,
        val posterUrl: String? = null,
        val chapters: List<ChapterHighlights> = emptyList(),
    )

    @Serializable
    data class ChapterHighlights(
        val chapterNumber: Double,
        val chapterTitle: String = "",
        val highlights: List<HighlightEntry> = emptyList(),
    )

    @Serializable
    data class HighlightEntry(
        val text: String,
        val paragraphIndex: Int = 0,
        val timestamp: Long,
        val color: String? = COLOR_YELLOW,
        val note: String? = null,
    )
}
