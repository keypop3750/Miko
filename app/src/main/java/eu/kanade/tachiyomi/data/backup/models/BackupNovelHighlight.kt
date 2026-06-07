package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupNovelHighlight(
    @ProtoNumber(1) var chapterNumber: Double,
    @ProtoNumber(2) var chapterTitle: String = "",
    @ProtoNumber(3) var text: String,
    @ProtoNumber(4) var color: String? = null,
    @ProtoNumber(5) var note: String? = null,
    @ProtoNumber(6) var timestamp: Long,
    @ProtoNumber(7) var paragraphIndex: Int = 0,
)
