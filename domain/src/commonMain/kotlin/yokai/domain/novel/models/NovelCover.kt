package yokai.domain.novel.models

import yokai.domain.novel.Novel

data class NovelCover(
    val novelId: Long?,
    val sourceId: Long,
    val url: String,
    val lastModified: Long,
    val inLibrary: Boolean,
)

fun Novel.cover() = NovelCover(
    novelId = id,
    sourceId = source,
    url = posterUrl ?: "",
    lastModified = coverLastModified,
    inLibrary = isFavorite,
)
