package yokai.domain.track.novel.interactor

import yokai.domain.track.novel.NovelTrackRepository

/**
 * Interactor for deleting novel tracks.
 */
class DeleteNovelTrack(
    private val novelTrackRepository: NovelTrackRepository
) {
    /**
     * Deletes a track by ID.
     */
    suspend fun await(id: Long) {
        novelTrackRepository.delete(id)
    }
    
    /**
     * Deletes a track for a specific novel and sync service.
     */
    suspend fun awaitByNovelIdAndSyncId(novelId: Long, syncId: Int) {
        novelTrackRepository.deleteByNovelIdAndSyncId(novelId, syncId)
    }
    
    /**
     * Deletes all tracks for a specific novel.
     */
    suspend fun awaitAllByNovelId(novelId: Long) {
        novelTrackRepository.deleteByNovelId(novelId)
    }
}
