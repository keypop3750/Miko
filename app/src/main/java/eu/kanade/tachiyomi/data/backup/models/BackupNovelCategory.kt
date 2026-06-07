package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupNovelCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Int = 0,
    @ProtoNumber(100) var flags: Int = 0,
)
