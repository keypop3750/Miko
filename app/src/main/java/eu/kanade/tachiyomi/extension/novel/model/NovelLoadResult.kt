package eu.kanade.tachiyomi.extension.novel.model

/**
 * Load result for novel extensions.
 */
sealed interface NovelLoadResult {

    data class Success(val extension: NovelExtension.Installed) : NovelLoadResult
    data class Untrusted(val extension: NovelExtension.Untrusted) : NovelLoadResult
    data object Error : NovelLoadResult
}
