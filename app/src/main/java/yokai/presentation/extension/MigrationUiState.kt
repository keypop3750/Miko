package yokai.presentation.extension

import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.Source

/**
 * Represents the UI state for the migration screen.
 */
data class MigrationUiState(
    val isLoading: Boolean = true,
    val currentView: MigrationViewType = MigrationViewType.SourceList,
    val sources: List<MigrationSourceUiItem> = emptyList(),
    val selectedSourceName: String? = null,
    val selectedSourceManga: List<MigrationMangaUiItem> = emptyList(),
    val sortOrder: MigrationSortOrder = MigrationSortOrder.Alphabetically,
    val error: String? = null,
)

/**
 * Whether showing source list or manga list.
 */
enum class MigrationViewType {
    SourceList,
    MangaList,
}

/**
 * Sort order for migration sources.
 */
enum class MigrationSortOrder {
    Alphabetically,
    MostEntries,
    Obsolete,
}

/**
 * UI-friendly representation of a migration source.
 */
data class MigrationSourceUiItem(
    val sourceId: Long,
    val name: String,
    val lang: String,
    val mangaCount: Int,
    val isUninstalled: Boolean,
    val isObsolete: Boolean,
    val iconUrl: String?,
)

/**
 * UI-friendly representation of a manga for migration.
 */
data class MigrationMangaUiItem(
    val mangaId: Long,
    val title: String,
    val coverUrl: String?,
    val sourceId: Long,
    val sourceName: String,
)

/**
 * Actions that can be performed on migration.
 */
sealed interface MigrationAction {
    data class SelectSource(val sourceId: Long) : MigrationAction
    data object DeselectSource : MigrationAction
    data class MigrateAll(val sourceId: Long) : MigrationAction
    data class MigrateManga(val mangaId: Long) : MigrationAction
    data class SetSortOrder(val order: MigrationSortOrder) : MigrationAction
}

/**
 * Convert domain models to UI models.
 */
fun Source.toMigrationUiItem(
    mangaCount: Int,
    isUninstalled: Boolean,
    isObsolete: Boolean,
): MigrationSourceUiItem {
    return MigrationSourceUiItem(
        sourceId = id,
        name = name,
        lang = lang,
        mangaCount = mangaCount,
        isUninstalled = isUninstalled,
        isObsolete = isObsolete,
        iconUrl = null, // Will be loaded async
    )
}

fun Manga.toMigrationUiItem(sourceName: String): MigrationMangaUiItem {
    return MigrationMangaUiItem(
        mangaId = id!!,
        title = title,
        coverUrl = thumbnail_url,
        sourceId = source,
        sourceName = sourceName,
    )
}
