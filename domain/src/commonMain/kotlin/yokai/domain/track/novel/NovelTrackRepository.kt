package yokai.domain.track.novel

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for novel tracking operations.
 */
interface NovelTrackRepository {
    /**
     * Gets all tracks for a specific novel.
     */
    suspend fun getTracksByNovelId(novelId: Long): List<NovelTrack>
    
    /**
     * Observes all tracks for a specific novel as a Flow.
     */
    fun getTracksByNovelIdAsFlow(novelId: Long): Flow<List<NovelTrack>>
    
    /**
     * Gets a track by its ID.
     */
    suspend fun getTrackById(id: Long): NovelTrack?
    
    /**
     * Gets a track for a specific novel and sync service.
     */
    suspend fun getTrackByNovelIdAndSyncId(novelId: Long, syncId: Int): NovelTrack?
    
    /**
     * Inserts a new track.
     * @return The ID of the inserted track
     */
    suspend fun insert(track: NovelTrack): Long
    
    /**
     * Updates an existing track.
     */
    suspend fun update(track: NovelTrack)
    
    /**
     * Deletes a track by ID.
     */
    suspend fun delete(id: Long)
    
    /**
     * Deletes a track for a specific novel and sync service.
     */
    suspend fun deleteByNovelIdAndSyncId(novelId: Long, syncId: Int)
    
    /**
     * Deletes all tracks for a specific novel.
     */
    suspend fun deleteByNovelId(novelId: Long)
}
