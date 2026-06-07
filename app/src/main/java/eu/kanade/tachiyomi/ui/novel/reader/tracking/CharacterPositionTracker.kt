package eu.kanade.tachiyomi.ui.novel.reader.tracking

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.novel.NovelRepository
import yokai.domain.novel.NovelChapter

/**
 * Character-level position tracking service for novel reading.
 * Provides precise bookmark positions within text content and reading analytics.
 */
class CharacterPositionTracker(
    private val novelRepository: NovelRepository = Injekt.get()
) {

    // Current position state
    private val _currentPosition = MutableStateFlow(CharacterPosition())
    val currentPosition: StateFlow<CharacterPosition> = _currentPosition.asStateFlow()

    // Reading session state
    private val _readingSession = MutableStateFlow(ReadingSession())
    val readingSession: StateFlow<ReadingSession> = _readingSession.asStateFlow()

    // Position history for undo/redo functionality
    private val positionHistory = mutableListOf<CharacterPosition>()
    private var historyIndex = -1

    /**
     * Update the current character position within a chapter
     */
    fun updatePosition(
        novelId: Long,
        chapterId: Long,
        characterPosition: Int,
        totalCharacters: Int,
        scrollPosition: Int = 0
    ) {
        val position = CharacterPosition(
            novelId = novelId,
            chapterId = chapterId,
            characterPosition = characterPosition,
            totalCharacters = totalCharacters,
            scrollPosition = scrollPosition,
            timestamp = System.currentTimeMillis()
        )

        _currentPosition.value = position
        
        // Add to history
        addToHistory(position)
        
        // Update reading session analytics
        updateReadingSession(position)
    }

    /**
     * Save the current position to the database
     */
    suspend fun savePosition() {
        val position = _currentPosition.value
        if (position.chapterId > 0) {
            novelRepository.updateReadingProgress(position.chapterId, position.characterPosition)
        }
    }

    /**
     * Load saved position from the database
     */
    suspend fun loadSavedPosition(novelId: Long, chapterId: Long): CharacterPosition? {
        val savedPosition = novelRepository.getReadingPosition(novelId, chapterId)
        return savedPosition?.let { saved ->
            CharacterPosition(
                novelId = novelId,
                chapterId = chapterId,
                characterPosition = saved.characterPosition,
                scrollPosition = saved.scrollPosition,
                timestamp = saved.lastReadAt
            )
        }
    }

    /**
     * Calculate reading progress as a percentage
     */
    fun getReadingProgress(): Float {
        val position = _currentPosition.value
        return if (position.totalCharacters > 0) {
            position.characterPosition.toFloat() / position.totalCharacters.toFloat()
        } else 0f
    }

    /**
     * Get estimated reading time remaining
     */
    fun getEstimatedReadingTime(averageWordsPerMinute: Int = 200): Int {
        val position = _currentPosition.value
        val remainingCharacters = position.totalCharacters - position.characterPosition
        val estimatedWords = remainingCharacters / 5 // Rough estimate: 5 characters per word
        return (estimatedWords / averageWordsPerMinute).coerceAtLeast(0)
    }

    /**
     * Create a bookmark at the current position
     */
    suspend fun createBookmark(title: String = "Bookmark"): NovelBookmark {
        val position = _currentPosition.value
        val bookmark = NovelBookmark(
            id = System.currentTimeMillis(), // Temporary ID
            novelId = position.novelId,
            chapterId = position.chapterId,
            characterPosition = position.characterPosition,
            title = title,
            createdAt = System.currentTimeMillis()
        )
        
        // TODO: Save bookmark to database when bookmark system is implemented
        return bookmark
    }

    /**
     * Get reading statistics for the current session
     */
    fun getReadingStatistics(): ReadingStatistics {
        val session = _readingSession.value
        val position = _currentPosition.value
        
        return ReadingStatistics(
            sessionDuration = System.currentTimeMillis() - session.startTime,
            charactersRead = position.characterPosition - session.startPosition,
            averageReadingSpeed = calculateReadingSpeed(),
            positionsVisited = positionHistory.size,
            bookmarksCreated = 0 // TODO: Implement bookmark counting
        )
    }

    /**
     * Navigate to previous position in history
     */
    fun navigateToPreviousPosition(): CharacterPosition? {
        if (historyIndex > 0) {
            historyIndex--
            val position = positionHistory[historyIndex]
            _currentPosition.value = position
            return position
        }
        return null
    }

    /**
     * Navigate to next position in history
     */
    fun navigateToNextPosition(): CharacterPosition? {
        if (historyIndex < positionHistory.size - 1) {
            historyIndex++
            val position = positionHistory[historyIndex]
            _currentPosition.value = position
            return position
        }
        return null
    }

    /**
     * Start a new reading session
     */
    fun startReadingSession(startPosition: Int = 0) {
        _readingSession.value = ReadingSession(
            startTime = System.currentTimeMillis(),
            startPosition = startPosition,
            isActive = true
        )
        
        // Clear position history for new session
        positionHistory.clear()
        historyIndex = -1
    }

    /**
     * End the current reading session
     */
    suspend fun endReadingSession() {
        val session = _readingSession.value
        if (session.isActive) {
            savePosition()
            _readingSession.value = session.copy(
                endTime = System.currentTimeMillis(),
                isActive = false
            )
        }
    }

    private fun addToHistory(position: CharacterPosition) {
        // Only add if position changed significantly (more than 100 characters)
        if (positionHistory.isEmpty() || 
            kotlin.math.abs(positionHistory.last().characterPosition - position.characterPosition) > 100) {
            
            // Remove any positions after current index (for undo/redo functionality)
            if (historyIndex < positionHistory.size - 1) {
                positionHistory.subList(historyIndex + 1, positionHistory.size).clear()
            }
            
            positionHistory.add(position)
            historyIndex = positionHistory.size - 1
            
            // Limit history size
            if (positionHistory.size > 50) {
                positionHistory.removeAt(0)
                historyIndex--
            }
        }
    }

    private fun updateReadingSession(position: CharacterPosition) {
        val session = _readingSession.value
        if (session.isActive) {
            _readingSession.value = session.copy(
                lastPosition = position.characterPosition,
                lastUpdateTime = position.timestamp
            )
        }
    }

    private fun calculateReadingSpeed(): Float {
        val session = _readingSession.value
        val sessionDurationMinutes = (System.currentTimeMillis() - session.startTime) / 60000f
        val charactersRead = session.lastPosition - session.startPosition
        val wordsRead = charactersRead / 5f // Rough estimate
        
        return if (sessionDurationMinutes > 0) wordsRead / sessionDurationMinutes else 0f
    }
}

/**
 * Character position data class with precise location tracking
 */
data class CharacterPosition(
    val novelId: Long = 0,
    val chapterId: Long = 0,
    val characterPosition: Int = 0,
    val totalCharacters: Int = 0,
    val scrollPosition: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Reading session tracking for analytics
 */
data class ReadingSession(
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0,
    val startPosition: Int = 0,
    val lastPosition: Int = 0,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)

/**
 * Reading statistics for user insights
 */
data class ReadingStatistics(
    val sessionDuration: Long,
    val charactersRead: Int,
    val averageReadingSpeed: Float, // Words per minute
    val positionsVisited: Int,
    val bookmarksCreated: Int
)

/**
 * Novel bookmark data class
 */
data class NovelBookmark(
    val id: Long,
    val novelId: Long,
    val chapterId: Long,
    val characterPosition: Int,
    val title: String,
    val note: String = "",
    val createdAt: Long
)