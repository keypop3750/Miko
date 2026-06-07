package yokai.domain.track.novel.interactor

import kotlinx.coroutines.flow.Flow
import yokai.domain.track.novel.NovelTrack
import yokai.domain.track.novel.NovelTrackRepository

/**
 * Interactor for retrieving novel tracks.
 */
class GetNovelTrack(
    private val novelTrackRepository: NovelTrackRepository
) {
    /**
     * Gets all tracks for a specific novel.
     */
    suspend fun awaitAllByNovelId(novelId: Long): List<NovelTrack> {
        return novelTrackRepository.getTracksByNovelId(novelId)
    }
    
    /**
     * Subscribes to tracks for a specific novel.
     */
    fun subscribeByNovelId(novelId: Long): Flow<List<NovelTrack>> {
        return novelTrackRepository.getTracksByNovelIdAsFlow(novelId)
    }
    
    /**
     * Gets a track by its ID.
     */
    suspend fun awaitById(id: Long): NovelTrack? {
        return novelTrackRepository.getTrackById(id)
    }
    
    /**
     * Gets a track for a specific novel and sync service.
     */
    suspend fun awaitByNovelIdAndSyncId(novelId: Long, syncId: Int): NovelTrack? {
        return novelTrackRepository.getTrackByNovelIdAndSyncId(novelId, syncId)
    }
}
