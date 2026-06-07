package yokai.domain.track.novel.interactor

import yokai.domain.track.novel.NovelTrack
import yokai.domain.track.novel.NovelTrackRepository

/**
 * Interactor for inserting or updating novel tracks.
 */
class InsertNovelTrack(
    private val novelTrackRepository: NovelTrackRepository
) {
    /**
     * Inserts a new track.
     * @return The ID of the inserted track
     */
    suspend fun await(track: NovelTrack): Long {
        return novelTrackRepository.insert(track)
    }
    
    /**
     * Updates an existing track.
     */
    suspend fun awaitUpdate(track: NovelTrack) {
        novelTrackRepository.update(track)
    }
    
    /**
     * Inserts or updates a track (upsert operation).
     */
    suspend fun awaitUpsert(track: NovelTrack): Long {
        // Check if track already exists
        val existing = novelTrackRepository.getTrackByNovelIdAndSyncId(track.novelId, track.syncId)
        
        return if (existing != null) {
            // Update existing track
            val updated = track.copy(id = existing.id)
            novelTrackRepository.update(updated)
            existing.id
        } else {
            // Insert new track
            novelTrackRepository.insert(track)
        }
    }
}
