package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupNovelChapter(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var read: Boolean = false,
    @ProtoNumber(4) var bookmark: Boolean = false,
    @ProtoNumber(5) var lastReadPosition: Int = 0,
    @ProtoNumber(6) var dateFetch: Long = 0,
    @ProtoNumber(7) var dateUpload: Long = 0,
    @ProtoNumber(8) var chapterNumber: Double = 0.0,
    @ProtoNumber(9) var sourceOrder: Int = 0,
    @ProtoNumber(10) var volumeNumber: Double? = null,
    @ProtoNumber(11) var wordCount: Int? = null,
    @ProtoNumber(12) var readingTimeMs: Long = 0,
    @ProtoNumber(13) var translator: String? = null,
)
