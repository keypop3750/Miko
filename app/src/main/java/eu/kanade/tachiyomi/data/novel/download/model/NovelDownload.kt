package eu.kanade.tachiyomi.data.novel.download.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import yokai.domain.novel.Novel
import yokai.domain.novel.NovelChapter
import yokai.source.novel.NovelMainAPI

/**
 * Download model for novel chapters.
 * Unlike manga downloads which track image pages, novel downloads track text content progress.
 */
class NovelDownload(
    val source: NovelMainAPI,
    val novel: Novel,
    val chapter: NovelChapter
) {

    /**
     * Text content of the chapter (HTML or plain text)
     */
    var textContent: String? = null

    /**
     * Total characters in the chapter content
     */
    val totalCharacters: Int
        get() = textContent?.length ?: 0

    /**
     * Download state flow for reactive updates
     */
    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()

    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    /**
     * Progress percentage (0-100)
     * For text downloads, this is typically 0 (not started) or 100 (complete)
     * since text downloads are atomic operations
     */
    @Transient
    private val _progressFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = _progressFlow.asStateFlow()

    var progress: Int
        get() = _progressFlow.value
        set(value) {
            _progressFlow.value = value
        }

    /**
     * Download states for novel chapters
     */
    enum class State(val value: Int) {
        CHECKED(-1),
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
        ;

        companion object {
            val default = NOT_DOWNLOADED
            
            fun fromValue(value: Int): State {
                return entries.find { it.value == value } ?: default
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NovelDownload) return false

        return chapter.id == other.chapter.id
    }

    override fun hashCode(): Int {
        return chapter.id.hashCode()
    }
}
