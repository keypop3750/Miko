package yokai.data.track.novel

import kotlinx.coroutines.flow.Flow
import yokai.data.DatabaseHandler
import yokai.domain.track.novel.NovelTrack
import yokai.domain.track.novel.NovelTrackRepository

/**
 * Repository implementation for novel tracking operations.
 */
class NovelTrackRepositoryImpl(
    private val handler: DatabaseHandler
) : NovelTrackRepository {
    
    override suspend fun getTracksByNovelId(novelId: Long): List<NovelTrack> {
        return handler.awaitList {
            novel_trackQueries.getTracksByNovelId(novelId, ::mapNovelTrack)
        }
    }
    
    override fun getTracksByNovelIdAsFlow(novelId: Long): Flow<List<NovelTrack>> {
        return handler.subscribeToList {
            novel_trackQueries.getTracksByNovelId(novelId, ::mapNovelTrack)
        }
    }
    
    override suspend fun getTrackById(id: Long): NovelTrack? {
        return handler.awaitOneOrNull {
            novel_trackQueries.getTrackById(id, ::mapNovelTrack)
        }
    }
    
    override suspend fun getTrackByNovelIdAndSyncId(novelId: Long, syncId: Int): NovelTrack? {
        return handler.awaitOneOrNull {
            novel_trackQueries.getTrackByNovelIdAndSyncId(novelId, syncId.toLong(), ::mapNovelTrack)
        }
    }
    
    override suspend fun insert(track: NovelTrack): Long {
        return handler.awaitOneExecutable(true) {
            novel_trackQueries.insertTrack(
                novelId = track.novelId,
                syncId = track.syncId.toLong(),
                remoteId = track.remoteId,
                libraryId = track.libraryId,
                title = track.title,
                lastChapterRead = track.lastChapterRead.toDouble(),
                totalChapters = track.totalChapters.toLong(),
                status = track.status.toLong(),
                score = track.score.toDouble(),
                remoteUrl = track.remoteUrl,
                startDate = track.startDate,
                finishDate = track.finishDate
            )
            novel_trackQueries.selectLastInsertedRowId()
        }
    }
    
    override suspend fun update(track: NovelTrack) {
        handler.await(true) {
            novel_trackQueries.updateTrack(
                id = track.id,
                remoteId = track.remoteId,
                libraryId = track.libraryId,
                title = track.title,
                lastChapterRead = track.lastChapterRead.toDouble(),
                totalChapters = track.totalChapters.toLong(),
                status = track.status.toLong(),
                score = track.score.toDouble(),
                remoteUrl = track.remoteUrl,
                startDate = track.startDate,
                finishDate = track.finishDate
            )
        }
    }
    
    override suspend fun delete(id: Long) {
        handler.await(true) {
            novel_trackQueries.deleteTrack(id)
        }
    }
    
    override suspend fun deleteByNovelIdAndSyncId(novelId: Long, syncId: Int) {
        handler.await(true) {
            novel_trackQueries.deleteTrackByNovelIdAndSyncId(novelId, syncId.toLong())
        }
    }
    
    override suspend fun deleteByNovelId(novelId: Long) {
        handler.await(true) {
            novel_trackQueries.deleteTracksByNovelId(novelId)
        }
    }
    
    /**
     * Maps database row to NovelTrack domain model.
     */
    private fun mapNovelTrack(
        _id: Long,
        novel_id: Long,
        sync_id: Long,
        remote_id: Long,
        library_id: Long?,
        title: String,
        last_chapter_read: Double,
        total_chapters: Long,
        status: Long,
        score: Double,
        remote_url: String,
        start_date: Long,
        finish_date: Long
    ): NovelTrack {
        return NovelTrack(
            id = _id,
            novelId = novel_id,
            syncId = sync_id.toInt(),
            remoteId = remote_id,
            libraryId = library_id,
            title = title,
            lastChapterRead = last_chapter_read.toFloat(),
            totalChapters = total_chapters.toInt(),
            status = status.toInt(),
            score = score.toFloat(),
            remoteUrl = remote_url,
            startDate = start_date,
            finishDate = finish_date
        )
    }
}
