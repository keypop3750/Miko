package yokai.data.novelchapter

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import yokai.data.DatabaseHandler
import yokai.domain.novelchapter.NovelChapterRepository
import yokai.domain.novelchapter.models.NovelChapter
import yokai.domain.novelchapter.models.NovelChapterUpdate

class NovelChapterRepositoryImpl(
    private val handler: DatabaseHandler
) : NovelChapterRepository {
    
    override suspend fun getChapters(novelId: Long): List<NovelChapter> =
        handler.awaitList { 
            novel_chaptersQueries.getChaptersByNovelId(novelId) { 
                _id, novel_id, url, title, chapter_number, volume_number, word_count, 
                read, bookmark, source_order, date_fetch, date_upload, 
                last_read_position, reading_time_ms, translator ->
                NovelChapter(
                    id = _id,
                    novelId = novel_id,
                    url = url,
                    title = title,
                    chapterNumber = chapter_number,
                    volumeNumber = volume_number ?: 0.0,
                    wordCount = word_count ?: 0,
                    read = read,
                    bookmark = bookmark,
                    sourceOrder = source_order,
                    dateFetch = date_fetch,
                    dateUpload = date_upload,
                    lastReadPosition = last_read_position,
                    readingTimeMs = reading_time_ms,
                    translator = translator
                )
            }
        }
    
    override fun getChaptersAsFlow(novelId: Long): Flow<List<NovelChapter>> =
        handler.subscribeToList { 
            novel_chaptersQueries.getChaptersByNovelId(novelId) { 
                _id, novel_id, url, title, chapter_number, volume_number, word_count, 
                read, bookmark, source_order, date_fetch, date_upload, 
                last_read_position, reading_time_ms, translator ->
                NovelChapter(
                    id = _id,
                    novelId = novel_id,
                    url = url,
                    title = title,
                    chapterNumber = chapter_number,
                    volumeNumber = volume_number ?: 0.0,
                    wordCount = word_count ?: 0,
                    read = read,
                    bookmark = bookmark,
                    sourceOrder = source_order,
                    dateFetch = date_fetch,
                    dateUpload = date_upload,
                    lastReadPosition = last_read_position,
                    readingTimeMs = reading_time_ms,
                    translator = translator
                )
            }
        }
    
    override suspend fun getChapterById(id: Long): NovelChapter? =
        handler.awaitOneOrNull { 
            novel_chaptersQueries.getChapterById(id) { 
                _id, novel_id, url, title, chapter_number, volume_number, word_count, 
                read, bookmark, source_order, date_fetch, date_upload, 
                last_read_position, reading_time_ms, translator ->
                NovelChapter(
                    id = _id,
                    novelId = novel_id,
                    url = url,
                    title = title,
                    chapterNumber = chapter_number,
                    volumeNumber = volume_number ?: 0.0,
                    wordCount = word_count ?: 0,
                    read = read,
                    bookmark = bookmark,
                    sourceOrder = source_order,
                    dateFetch = date_fetch,
                    dateUpload = date_upload,
                    lastReadPosition = last_read_position,
                    readingTimeMs = reading_time_ms,
                    translator = translator
                )
            }
        }
    
    override suspend fun getChapterByUrl(url: String, novelId: Long): NovelChapter? =
        handler.awaitOneOrNull { 
            novel_chaptersQueries.getChapterByUrl(novelId, url) { 
                _id, novel_id, url, title, chapter_number, volume_number, word_count, 
                read, bookmark, source_order, date_fetch, date_upload, 
                last_read_position, reading_time_ms, translator ->
                NovelChapter(
                    id = _id,
                    novelId = novel_id,
                    url = url,
                    title = title,
                    chapterNumber = chapter_number,
                    volumeNumber = volume_number ?: 0.0,
                    wordCount = word_count ?: 0,
                    read = read,
                    bookmark = bookmark,
                    sourceOrder = source_order,
                    dateFetch = date_fetch,
                    dateUpload = date_upload,
                    lastReadPosition = last_read_position,
                    readingTimeMs = reading_time_ms,
                    translator = translator
                )
            }
        }
    
    override suspend fun getUnreadChapters(novelId: Long): List<NovelChapter> =
        getChapters(novelId).filter { !it.read }
    
    // ===== LIBRARY METADATA QUERIES (TODO ITEM IMPLEMENTATIONS) =====
    
    override suspend fun getUnreadCount(novelId: Long): Long =
        handler.awaitOne { 
            novel_chaptersQueries.countUnreadChaptersByNovelId(novelId) 
        }
    
    override suspend fun getTotalCount(novelId: Long): Long =
        handler.awaitOne { 
            novel_chaptersQueries.countTotalChaptersByNovelId(novelId) 
        }
    
    override suspend fun getDownloadedCount(novelId: Long): Long =
        handler.awaitOne { 
            novel_chaptersQueries.countDownloadedChaptersByNovelId(novelId) 
        }
    
    override suspend fun getLastReadChapterTitle(novelId: Long): String? =
        handler.awaitOneOrNull { 
            novel_chaptersQueries.getLastReadChapterByNovelId(novelId) { title, _, _ -> 
                title 
            }
        }
    
    override suspend fun markChapterRead(chapterId: Long, read: Boolean) {
        handler.await { 
            novel_chaptersQueries.markChapterRead(read, 0L, 0L, chapterId) 
        }
    }
    
    override suspend fun insert(chapter: NovelChapter): Long? {
        return handler.await(inTransaction = true) {
            novel_chaptersQueries.insertChapter(
                novel_id = chapter.novelId,
                url = chapter.url,
                title = chapter.title,
                chapter_number = chapter.chapterNumber,
                volume_number = chapter.volumeNumber,
                word_count = chapter.wordCount,
                read = chapter.read,
                bookmark = chapter.bookmark,
                source_order = chapter.sourceOrder,
                date_fetch = chapter.dateFetch,
                date_upload = chapter.dateUpload,
                last_read_position = chapter.lastReadPosition,
                reading_time_ms = chapter.readingTimeMs,
                translator = chapter.translator
            )
            novel_chaptersQueries.selectLastInsertedRowId().executeAsOne()
        }
    }
    
    override suspend fun update(update: NovelChapterUpdate): Boolean =
        handler.await {
            novel_chaptersQueries.updateChapter(
                chapter_id = update.id,
                title = update.title,
                chapter_number = update.chapterNumber,
                volume_number = update.volumeNumber,
                word_count = update.wordCount,
                read = update.read,
                bookmark = update.bookmark,
                source_order = update.sourceOrder,
                date_fetch = update.dateFetch,
                date_upload = update.dateUpload,
                last_read_position = update.lastReadPosition,
                reading_time_ms = update.readingTimeMs,
                translator = update.translator
            )
        }.let { true }
    
    override suspend fun updateAll(updates: List<NovelChapterUpdate>): Boolean =
        try {
            updates.forEach { update(it) }
            true
        } catch (e: Exception) {
            Logger.e("NovelChapterRepositoryImpl", e) { "Failed to update all chapters" }
            false
        }
    
    override suspend fun delete(chapterId: Long): Boolean =
        handler.await { 
            novel_chaptersQueries.deleteChapter(chapterId) 
        }.let { true }
}
