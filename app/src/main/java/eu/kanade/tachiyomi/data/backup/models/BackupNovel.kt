package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupNovel(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var author: String? = null,
    @ProtoNumber(5) var description: String? = null,
    @ProtoNumber(6) var genre: String? = null,
    @ProtoNumber(7) var status: Int = 0,
    @ProtoNumber(8) var posterUrl: String? = null,
    @ProtoNumber(9) var favorite: Boolean = true,
    @ProtoNumber(10) var dateAdded: Long = 0,
    @ProtoNumber(11) var chapterFlags: Int = 0,
    @ProtoNumber(12) var chapters: List<BackupNovelChapter> = emptyList(),
    @ProtoNumber(13) var categories: List<Int> = emptyList(),
    @ProtoNumber(14) var history: List<BackupNovelHistory> = emptyList(),
    @ProtoNumber(15) var wordCount: Int? = null,
    @ProtoNumber(16) var chapterCount: Int? = null,
    @ProtoNumber(17) var vibrantCoverColor: Int? = null,
    @ProtoNumber(18) var filteredTranslators: String? = null,
    @ProtoNumber(19) var highlights: List<BackupNovelHighlight> = emptyList(),
    @ProtoNumber(20) var lastUpdate: Long = 0,
    @ProtoNumber(21) var initialized: Boolean = false,
    @ProtoNumber(22) var coverLastModified: Long = 0,
)
