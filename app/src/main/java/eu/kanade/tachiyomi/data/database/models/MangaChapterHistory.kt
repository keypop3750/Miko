package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.domain.manga.models.Manga

/**
 * Object containing manga, chapter and history
 *
 * @param manga object containing manga
 * @param chapter object containing chater
 * @param history object containing history
 */
data class MangaChapterHistory(val manga: Manga, val chapter: Chapter, val history: History, var extraChapters: List<ChapterHistory> = emptyList()) {

    companion object {
        fun createBlank() = MangaChapterHistory(
            MangaImpl(null, -1, ""),
            ChapterImpl(),
            HistoryImpl(),
        )

        fun mapper(
            // manga
            mangaId: Long,
            source: Long,
            mangaUrl: String,
            artist: String?,
            author: String?,
            description: String?,
            genre: String?,
            title: String,
            status: Long,
            thumbnailUrl: String?,
            favorite: Boolean,
            lastUpdate: Long?,
            initialized: Boolean,
            viewer: Long,
            hideTitle: Boolean,
            chapterFlags: Long,
            dateAdded: Long?,
            filteredScanlators: String?,
            updateStrategy: Long,
            coverLastModified: Long,
            // chapter
            chapterId: Long?,
            chapterMangaId: Long?,
            chapterUrl: String?,
            name: String?,
            scanlator: String?,
            read: Boolean?,
            bookmark: Boolean?,
            lastPageRead: Long?,
            pagesLeft: Long?,
            chapterNumber: Double?,
            sourceOrder: Long?,
            dateFetch: Long?,
            dateUpload: Long?,
            // history
            historyId: Long?,
            historyChapterId: Long?,
            historyLastRead: Long?,
            historyTimeRead: Long?,
        ): MangaChapterHistory {
            val manga = Manga.mapper(
                id = mangaId,
                source = source,
                url = mangaUrl,
                artist = artist,
                author = author,
                description = description,
                genre = genre,
                title = title,
                status = status,
                thumbnailUrl = thumbnailUrl,
                favorite = favorite,
                lastUpdate = lastUpdate,
                initialized = initialized,
                viewerFlags = viewer,
                hideTitle = hideTitle,
                chapterFlags = chapterFlags,
                dateAdded = dateAdded,
                filteredScanlators = filteredScanlators,
                updateStrategy = updateStrategy,
                coverLastModified = coverLastModified,
            )

            val chapter = try {
                Chapter.mapper(
                    id = chapterId!!,
                    mangaId = chapterMangaId!!,
                    url = chapterUrl!!,
                    name = name!!,
                    scanlator = scanlator,
                    read = read!!,
                    bookmark = bookmark!!,
                    lastPageRead = lastPageRead!!,
                    pagesLeft = pagesLeft!!,
                    chapterNumber = chapterNumber!!,
                    sourceOrder = sourceOrder!!,
                    dateFetch = dateFetch!!,
                    dateUpload = dateUpload!!,
                )
            } catch (_: NullPointerException) {
                ChapterImpl()
            }

            val history = try {
                History.mapper(
                    id = historyId!!,
                    chapterId = historyChapterId!!,
                    lastRead = historyLastRead,
                    timeRead = historyTimeRead,
                )
            } catch (_: NullPointerException) {
                HistoryImpl().apply {
                    historyChapterId?.let { chapter_id = it }
                    historyLastRead?.let { last_read = it }
                    historyTimeRead?.let { time_read = it }
                }
            }

            return MangaChapterHistory(manga, chapter, history)
        }

        /**
         * Novel mapper - converts novel query result types to MangaChapterHistory
         * Novel queries return slightly different types (non-nullable integers for booleans, etc.)
         */
        fun novelMapper(
            // novel (mapped as manga)
            mangaId: Long,
            source: Long,
            mangaUrl: String,
            artist: String?,
            author: String?,
            description: String?,
            genre: String?,
            title: String,
            status: Long,
            thumbnailUrl: String?,
            favorite: Boolean,
            lastUpdate: Long?,
            initialized: Boolean,
            viewer: Long,
            hideTitle: Long,  // Novel returns Long, not Boolean
            chapterFlags: Long,
            dateAdded: Long?,
            filteredScanlators: String?,
            updateStrategy: Long,
            coverLastModified: Long,
            // novel chapter (mapped as chapter)
            chapterId: Long,  // Non-null from novel_chapters
            chapterMangaId: Long,  // Non-null from novel_chapters
            chapterUrl: String,  // Non-null from novel_chapters
            name: String,  // Non-null from novel_chapters
            scanlator: String?,
            read: Long,  // Novel chapters use Long for boolean
            bookmark: Long,  // Novel chapters use Long for boolean
            lastPageRead: Long,  // Non-null from novel_chapters
            pagesLeft: Long,  // Non-null
            chapterNumber: Double,  // Non-null from novel_chapters
            sourceOrder: Long,  // Non-null from novel_chapters
            dateFetch: Long,  // Non-null from novel_chapters
            dateUpload: Long,  // Non-null from novel_chapters
            // novel history
            historyId: Long,  // Non-null from novel_history
            historyChapterId: Long,  // Non-null from novel_history
            historyLastRead: Long?,
            historyTimeRead: Long?,
        ): MangaChapterHistory {
            // Delegate to main mapper by converting types
            return mapper(
                mangaId = mangaId,
                source = source,
                mangaUrl = mangaUrl,
                artist = artist,
                author = author,
                description = description,
                genre = genre,
                title = title,
                status = status,
                thumbnailUrl = thumbnailUrl,
                favorite = favorite,
                lastUpdate = lastUpdate,
                initialized = initialized,
                viewer = viewer,
                hideTitle = hideTitle != 0L,  // Convert Long to Boolean
                chapterFlags = chapterFlags,
                dateAdded = dateAdded,
                filteredScanlators = filteredScanlators,
                updateStrategy = updateStrategy,
                coverLastModified = coverLastModified,
                chapterId = chapterId,
                chapterMangaId = chapterMangaId,
                chapterUrl = chapterUrl,
                name = name,
                scanlator = scanlator,
                read = read != 0L,  // Convert Long to Boolean
                bookmark = bookmark != 0L,  // Convert Long to Boolean
                lastPageRead = lastPageRead,
                pagesLeft = pagesLeft,
                chapterNumber = chapterNumber,
                sourceOrder = sourceOrder,
                dateFetch = dateFetch,
                dateUpload = dateUpload,
                historyId = historyId,
                historyChapterId = historyChapterId,
                historyLastRead = historyLastRead,
                historyTimeRead = historyTimeRead,
            )
        }

        /**
         * Novel mapper for UNION queries - handles nullable chapter/history fields
         * Used by getRecentsAll which combines multiple queries with UNION
         */
        fun novelMapperNullable(
            // novel (mapped as manga)
            mangaId: Long,
            source: Long,
            mangaUrl: String,
            artist: String?,
            author: String?,
            description: String?,
            genre: String?,
            title: String,
            status: Long,
            thumbnailUrl: String?,
            favorite: Boolean,
            lastUpdate: Long?,
            initialized: Boolean,
            viewer: Long,
            hideTitle: Long,  // Novel returns Long, not Boolean
            chapterFlags: Long,
            dateAdded: Long?,
            filteredScanlators: String?,
            updateStrategy: Long,
            coverLastModified: Long,
            // novel chapter (nullable from UNION)
            chapterId: Long?,
            chapterMangaId: Long?,
            chapterUrl: String?,
            name: String?,
            scanlator: String?,
            read: Long?,  // Novel chapters use Long for boolean
            bookmark: Long?,  // Novel chapters use Long for boolean
            lastPageRead: Long?,
            pagesLeft: Long?,
            chapterNumber: Double?,
            sourceOrder: Long?,
            dateFetch: Long?,
            dateUpload: Long?,
            // novel history (nullable from UNION)
            historyId: Long?,
            historyChapterId: Long?,
            historyLastRead: Long?,
            historyTimeRead: Long?,
        ): MangaChapterHistory {
            // Delegate to main mapper by converting types
            return mapper(
                mangaId = mangaId,
                source = source,
                mangaUrl = mangaUrl,
                artist = artist,
                author = author,
                description = description,
                genre = genre,
                title = title,
                status = status,
                thumbnailUrl = thumbnailUrl,
                favorite = favorite,
                lastUpdate = lastUpdate,
                initialized = initialized,
                viewer = viewer,
                hideTitle = hideTitle != 0L,  // Convert Long to Boolean
                chapterFlags = chapterFlags,
                dateAdded = dateAdded,
                filteredScanlators = filteredScanlators,
                updateStrategy = updateStrategy,
                coverLastModified = coverLastModified,
                chapterId = chapterId,
                chapterMangaId = chapterMangaId,
                chapterUrl = chapterUrl,
                name = name,
                scanlator = scanlator,
                read = read?.let { it != 0L },  // Convert Long? to Boolean?
                bookmark = bookmark?.let { it != 0L },  // Convert Long? to Boolean?
                lastPageRead = lastPageRead,
                pagesLeft = pagesLeft,
                chapterNumber = chapterNumber,
                sourceOrder = sourceOrder,
                dateFetch = dateFetch,
                dateUpload = dateUpload,
                historyId = historyId,
                historyChapterId = historyChapterId,
                historyLastRead = historyLastRead,
                historyTimeRead = historyTimeRead,
            )
        }
    }
}

data class ChapterHistory(val chapter: Chapter, var history: History? = null) : Chapter by chapter
