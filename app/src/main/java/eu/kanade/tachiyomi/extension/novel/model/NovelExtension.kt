package eu.kanade.tachiyomi.extension.novel.model

import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable
import yokai.source.novel.NovelMainAPI

/**
 * Novel extension models - parallel to manga Extension but for novel sources.
 */
sealed class NovelExtension {

    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long
    abstract val libVersion: Double
    abstract val lang: String?
    abstract val isNsfw: Boolean

    /**
     * Installed novel extension with loaded sources.
     */
    data class Installed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        val pkgFactory: String?,
        val sources: List<NovelMainAPI>,
        val icon: Drawable?,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val isShared: Boolean,
        val repoUrl: String? = null,
        val iconUrl: String? = null,  // Fallback icon URL from repository
    ) : NovelExtension()

    /**
     * Available novel extension from a repo.
     */
    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        val apkName: String,
        val iconUrl: String,
        val sources: List<AvailableNovelSource>,
        val repoUrl: String? = null,
    ) : NovelExtension()

    /**
     * Metadata about an available novel source within an extension.
     */
    @Serializable
    data class AvailableNovelSource(
        val name: String,
        val id: Long,
        val lang: String,
        val baseUrl: String,
    )

    /**
     * Untrusted novel extension requiring user approval.
     */
    data class Untrusted(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        val signatureHash: String,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
    ) : NovelExtension()
}
