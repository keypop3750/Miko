package yokai.domain.novel.models

data class NovelUpdate(
    val id: Long,
    val url: String? = null,
    val title: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val status: Int? = null,
    val posterUrl: String? = null,
    val initialized: Boolean? = null,
    var sourceId: Long? = null,
    var inLibrary: Boolean? = null,
    var lastUpdate: Long? = null,
    var dateAdded: Long? = null,
    var viewerFlags: Int? = null,
    var chapterFlags: Int? = null,
    var hideTitle: Boolean? = null,
    var coverLastModified: Long? = null,
    var vibrantCoverColor: Int? = null,
    var filteredTranslators: String? = null,
)
