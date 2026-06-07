package yokai.data.repository

import kotlinx.coroutines.flow.Flow
import yokai.data.DatabaseHandler
import yokai.domain.novel.Novel
import yokai.domain.novel.NovelChapter
import yokai.domain.novel.NovelCategory
import yokai.domain.novel.NovelReadingPosition
import yokai.domain.novel.NovelRepository
import yokai.domain.novel.models.NovelUpdate

/**
 * Implementation of NovelRepository following the atomic method approach.
 * Implements proper database operations using SQLDelight patterns from existing codebase.
 */
class NovelRepositoryImpl(
    private val handler: DatabaseHandler
) : NovelRepository {
    
    override suspend fun getAllNovels(): Flow<List<Novel>> {
        return handler.subscribeToList {
            novelsQueries.findAllNovels(::mapNovel)
        }
    }
    
    override suspend fun getFavoriteNovels(): Flow<List<Novel>> {
        return handler.subscribeToList {
            novelsQueries.findFavoriteNovels(::mapNovel)
        }
    }
    
    override suspend fun getNovelById(id: Long): Novel? {
        return handler.awaitOneOrNull {
            novelsQueries.findNovelById(id, ::mapNovel)
        }
    }
    
    override fun getNovelByIdAsFlow(id: Long): Flow<Novel?> {
        return handler.subscribeToOneOrNull {
            novelsQueries.findNovelById(id, ::mapNovel)
        }
    }
    
    override suspend fun getNovelByUrlAndSource(url: String, sourceId: Long): Novel? {
        return handler.awaitOneOrNull {
            novelsQueries.findNovelByUrlAndSource(url, sourceId, ::mapNovel)
        }
    }
    
    override fun getNovelByUrlAndSourceAsFlow(url: String, sourceId: Long): Flow<Novel?> {
        return handler.subscribeToOneOrNull {
            novelsQueries.findNovelByUrlAndSource(url, sourceId, ::mapNovel)
        }
    }
    
    override suspend fun insertNovel(novel: Novel): Long {
        return handler.awaitOneExecutable(true) {
            novelsQueries.insertNovel(
                source = novel.source,
                url = novel.url,
                title = novel.title,
                author = novel.author,
                description = novel.description,
                genre = novel.genre,
                status = novel.status.toLong(),
                poster_url = novel.posterUrl,
                favorite = novel.isFavorite,
                last_update = novel.lastUpdate,
                initialized = novel.initialized,
                date_added = novel.dateAdded,
                word_count = novel.wordCount?.toLong(),
                chapter_count = novel.chapterCount?.toLong(),
                cover_last_modified = novel.coverLastModified,
                vibrant_cover_color = novel.vibrantCoverColor?.toLong(),
                chapter_flags = novel.chapterFlags.toLong(),
                filtered_translators = novel.filteredTranslators
            )
            novelsQueries.selectLastInsertedRowId()
        }
    }
    
    override suspend fun updateNovel(novel: Novel) {
        handler.await(true) {
            novelsQueries.updateNovel(
                novelId = novel.id,
                title = novel.title,
                author = novel.author,
                description = novel.description,
                genre = novel.genre,
                status = novel.status.toLong(),
                poster_url = novel.posterUrl,
                favorite = novel.isFavorite,
                last_update = novel.lastUpdate,
                initialized = novel.initialized,
                word_count = novel.wordCount?.toLong(),
                chapter_count = novel.chapterCount?.toLong(),
                cover_last_modified = novel.coverLastModified,
                vibrant_cover_color = novel.vibrantCoverColor?.toLong(),
                chapter_flags = novel.chapterFlags.toLong(),
                filtered_translators = novel.filteredTranslators
            )
        }
    }
    
    override suspend fun update(update: NovelUpdate): Boolean {
        return try {
            val novel = getNovelById(update.id) ?: return false
            val updatedNovel = novel.copy(
                title = update.title ?: novel.title,
                author = update.author ?: novel.author,
                description = update.description ?: novel.description,
                genre = update.genres?.joinToString(", ") ?: novel.genre,
                status = update.status ?: novel.status,
                posterUrl = update.posterUrl ?: novel.posterUrl,
                isFavorite = update.inLibrary ?: novel.isFavorite,
                lastUpdate = update.lastUpdate ?: novel.lastUpdate,
                initialized = update.initialized ?: novel.initialized,
                coverLastModified = update.coverLastModified ?: novel.coverLastModified,
                vibrantCoverColor = update.vibrantCoverColor ?: novel.vibrantCoverColor,
                chapterFlags = update.chapterFlags ?: novel.chapterFlags
            )
            updateNovel(updatedNovel)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun updateAll(updates: List<NovelUpdate>): Boolean {
        return try {
            handler.await(true) {
                updates.forEach { update ->
                    val novel = handler.awaitOneOrNull {
                        novelsQueries.findNovelById(update.id, ::mapNovel)
                    } ?: return@forEach
                    
                    val updatedNovel = novel.copy(
                        title = update.title ?: novel.title,
                        author = update.author ?: novel.author,
                        description = update.description ?: novel.description,
                        genre = update.genres?.joinToString(", ") ?: novel.genre,
                        status = update.status ?: novel.status,
                        posterUrl = update.posterUrl ?: novel.posterUrl,
                        isFavorite = update.inLibrary ?: novel.isFavorite,
                        lastUpdate = update.lastUpdate ?: novel.lastUpdate,
                        initialized = update.initialized ?: novel.initialized,
                        coverLastModified = update.coverLastModified ?: novel.coverLastModified,
                        chapterFlags = update.chapterFlags ?: novel.chapterFlags
                    )
                    
                    novelsQueries.updateNovel(
                        novelId = updatedNovel.id,
                        title = updatedNovel.title,
                        author = updatedNovel.author,
                        description = updatedNovel.description,
                        genre = updatedNovel.genre,
                        status = updatedNovel.status.toLong(),
                        poster_url = updatedNovel.posterUrl,
                        favorite = updatedNovel.isFavorite,
                        last_update = updatedNovel.lastUpdate,
                        initialized = updatedNovel.initialized,
                        word_count = updatedNovel.wordCount?.toLong(),
                        chapter_count = updatedNovel.chapterCount?.toLong(),
                        cover_last_modified = updatedNovel.coverLastModified,
                        vibrant_cover_color = updatedNovel.vibrantCoverColor?.toLong(),
                        chapter_flags = updatedNovel.chapterFlags.toLong(),
                        filtered_translators = updatedNovel.filteredTranslators
                    )
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun deleteNovel(id: Long) {
        handler.await(true) {
            novelsQueries.deleteNovel(novelId = id)
        }
    }
    
    override suspend fun getChaptersByNovelId(novelId: Long): Flow<List<NovelChapter>> {
        return handler.subscribeToList {
            novel_chaptersQueries.getChaptersByNovelId(novel_id = novelId, mapper = ::mapNovelChapter)
        }
    }
    
    override fun getChaptersByNovelIdAsFlow(novelId: Long): Flow<List<NovelChapter>> {
        return handler.subscribeToList {
            novel_chaptersQueries.getChaptersByNovelId(novel_id = novelId, mapper = ::mapNovelChapter)
        }
    }
    
    override suspend fun getChaptersByNovelIdOnce(novelId: Long): List<NovelChapter> {
        // One-shot query (not Flow) for initial DB check
        return handler.awaitList {
            novel_chaptersQueries.getChaptersByNovelId(novel_id = novelId, mapper = ::mapNovelChapter)
        }
    }
    
    override suspend fun getChapterById(id: Long): NovelChapter? {
        return handler.awaitOneOrNull {
            novel_chaptersQueries.getChapterById(chapter_id = id, mapper = ::mapNovelChapter)
        }
    }
    
    override suspend fun getChapterByUrl(novelId: Long, url: String): NovelChapter? {
        return handler.awaitOneOrNull {
            novel_chaptersQueries.getChapterByUrl(novel_id = novelId, url = url, mapper = ::mapNovelChapter)
        }
    }
    
    override suspend fun insertChapter(chapter: NovelChapter): Long {
        return handler.awaitOneExecutable(true) {
            novel_chaptersQueries.insertChapter(
                novel_id = chapter.novelId,
                url = chapter.url,
                title = chapter.title,
                chapter_number = chapter.chapterNumber,
                volume_number = chapter.volumeNumber,
                word_count = chapter.wordCount?.toLong(),
                read = chapter.read,
                bookmark = chapter.bookmark,
                source_order = chapter.sourceOrder.toLong(),
                date_fetch = chapter.dateFetch,
                date_upload = chapter.dateUpload,
                last_read_position = chapter.lastReadPosition.toLong(),
                reading_time_ms = chapter.readingTimeMs,
                translator = chapter.translator
            )
            novel_chaptersQueries.selectLastInsertedRowId()
        }
    }
    
    override suspend fun insertChaptersBulk(chapters: List<NovelChapter>): List<NovelChapter> {
        // Use single transaction for all inserts - MAJOR PERFORMANCE IMPROVEMENT
        return handler.await(true) {
            val insertedChapters = chapters.mapIndexed { index, chapter ->
                novel_chaptersQueries.insertChapter(
                    novel_id = chapter.novelId,
                    url = chapter.url,
                    title = chapter.title,
                    chapter_number = chapter.chapterNumber,
                    volume_number = chapter.volumeNumber,
                    word_count = chapter.wordCount?.toLong(),
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    source_order = chapter.sourceOrder.toLong(),
                    date_fetch = chapter.dateFetch,
                    date_upload = chapter.dateUpload,
                    last_read_position = chapter.lastReadPosition.toLong(),
                    reading_time_ms = chapter.readingTimeMs,
                    translator = chapter.translator
                )
                val chapterId = novel_chaptersQueries.selectLastInsertedRowId().executeAsOne()
                
                // If INSERT OR IGNORE returned 0, fetch existing chapter ID
                val actualChapterId = if (chapterId == 0L) {
                    novel_chaptersQueries.getChapterByUrl(
                        novel_id = chapter.novelId,
                        url = chapter.url,
                        mapper = ::mapNovelChapter
                    ).executeAsOneOrNull()?.id ?: 0L
                } else {
                    chapterId
                }
                
                chapter.copy(id = actualChapterId)
            }
            
            // Verify chapters were inserted by doing a direct count query WITHIN the transaction
            val verifyCount = novel_chaptersQueries.countTotalChaptersByNovelId(chapters.first().novelId).executeAsOne()
            println("NovelRepositoryImpl: Bulk insert completed. DB count=$verifyCount for novelId=${chapters.first().novelId}")
            
            insertedChapters
        }
    }
    
    override suspend fun updateChapter(chapter: NovelChapter) {
        handler.await(true) {
            novel_chaptersQueries.updateChapter(
                chapter_id = chapter.id,
                title = chapter.title,
                chapter_number = chapter.chapterNumber,
                volume_number = chapter.volumeNumber,
                word_count = chapter.wordCount?.toLong(),
                read = chapter.read,
                bookmark = chapter.bookmark,
                source_order = chapter.sourceOrder.toLong(),
                date_fetch = chapter.dateFetch,
                date_upload = chapter.dateUpload,
                last_read_position = chapter.lastReadPosition.toLong(),
                reading_time_ms = chapter.readingTimeMs,
                translator = chapter.translator
            )
        }
    }
    
    override suspend fun markChapterRead(chapterId: Long, isRead: Boolean) {
        val currentTime = System.currentTimeMillis()
        handler.await(true) {
            novel_chaptersQueries.markChapterRead(
                chapter_id = chapterId,
                read = isRead,
                last_read_position = if (isRead) -1L else 0L,
                reading_time_ms = currentTime
            )
            
            // BUG FIX: Also insert into novel_history for Recents tab integration
            // This ensures novel reading history appears in the recents view
            if (isRead) {
                novel_historyQueries.upsertHistory(
                    novel_chapter_id = chapterId,
                    last_read = currentTime,
                    time_read = 0L,  // Will be accumulated by updateReadingProgress
                    character_position = 0L,  // Will be updated by updateReadingProgress
                    reading_session_id = null
                )
            }
        }
    }
    
    override suspend fun deleteChapter(id: Long) {
        handler.await(true) {
            novel_chaptersQueries.deleteChapter(chapter_id = id)
        }
    }
    
    override suspend fun updateReadingProgress(chapterId: Long, characterPosition: Int) {
        handler.await(true) {
            novel_chaptersQueries.updateReadingProgress(
                chapter_id = chapterId,
                last_read_position = characterPosition.toLong(),
                reading_time_ms = System.currentTimeMillis()
            )
        }
    }
    
    override suspend fun getReadingPosition(novelId: Long, chapterId: Long): NovelReadingPosition? {
        return handler.awaitOneOrNull {
            novel_chaptersQueries.getReadingPosition(chapter_id = chapterId, mapper = ::mapReadingPosition)
        }
    }
    
    override suspend fun getCategoriesForNovel(novelId: Long): Flow<List<NovelCategory>> {
        return handler.subscribeToList {
            novel_categoriesQueries.getCategoriesForNovel(novel_id = novelId, mapper = ::mapNovelCategory)
        }
    }
    
    override suspend fun getAllCategories(): Flow<List<NovelCategory>> {
        return handler.subscribeToList {
            novel_categoriesQueries.findAllCategories(mapper = ::mapNovelCategory)
        }
    }
    
    override suspend fun setCategories(novelId: Long, categoryIds: List<Long>) {
        handler.await(true) {
            // First delete all existing category assignments for this novel
            novel_categoriesQueries.deleteNovelCategories(novelId = novelId)
            
            // Then insert new category assignments
            categoryIds.forEach { categoryId ->
                novel_categoriesQueries.insertNovelCategory(novelId = novelId, categoryId = categoryId)
            }
        }
    }
    
    // Mapper functions following existing codebase patterns
    private fun mapNovel(
        _id: Long,
        source: Long,
        url: String,
        title: String,
        author: String?,
        description: String?,
        genre: String?,
        status: Long,
        poster_url: String?,
        favorite: Boolean,
        last_update: Long?,
        initialized: Boolean,
        date_added: Long?,
        word_count: Long?,
        chapter_count: Long?,
        cover_last_modified: Long,
        vibrant_cover_color: Long?,
        chapter_flags: Long,
        filtered_translators: String?
    ): Novel {
        return Novel(
            id = _id,
            source = source,
            url = url,
            title = title,
            author = author,
            description = description,
            genre = genre,
            status = status.toInt(),
            posterUrl = poster_url,
            isFavorite = favorite,
            lastUpdate = last_update ?: 0L,
            initialized = initialized,
            dateAdded = date_added ?: 0L,
            wordCount = word_count?.toInt(),
            chapterCount = chapter_count?.toInt(),
            coverLastModified = cover_last_modified,
            vibrantCoverColor = vibrant_cover_color?.toInt(),
            chapterFlags = chapter_flags.toInt(),
            filteredTranslators = filtered_translators
        )
    }
    
    private fun mapNovelChapter(
        _id: Long,
        novel_id: Long,
        url: String,
        title: String,
        chapter_number: Double,
        volume_number: Double?,
        word_count: Long?,
        read: Boolean,
        bookmark: Boolean,
        source_order: Long,
        date_fetch: Long,
        date_upload: Long,
        last_read_position: Long,
        reading_time_ms: Long,
        translator: String?
    ): NovelChapter {
        return NovelChapter(
            id = _id,
            novelId = novel_id,
            url = url,
            title = title,
            chapterNumber = chapter_number,
            volumeNumber = volume_number,
            wordCount = word_count?.toInt(),
            read = read,
            bookmark = bookmark,
            sourceOrder = source_order.toInt(),
            dateFetch = date_fetch,
            dateUpload = date_upload,
            lastReadPosition = last_read_position.toInt(),
            readingTimeMs = reading_time_ms,
            translator = translator
        )
    }
    
    private fun mapNovelCategory(
        _id: Long,
        name: String,
        sortOrder: Long,
        flags: Long
    ): NovelCategory {
        return NovelCategory(
            id = _id,
            name = name,
            sortOrder = sortOrder.toInt(),
            flags = flags.toInt()
        )
    }
    
    private fun mapReadingPosition(
        chapter_id: Long,
        last_read_position: Long,
        reading_time_ms: Long
    ): NovelReadingPosition {
        return NovelReadingPosition(
            chapterId = chapter_id,
            characterPosition = last_read_position.toInt(),
            readingTimeMs = reading_time_ms
        )
    }
}