package yokai.core.mode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import yokai.core.content.ContentType
import yokai.core.content.isManga
import yokai.core.content.isNovel

object ModeManager {
    private val _currentMode = MutableStateFlow(ContentType.MANGA)
    val currentMode: StateFlow<ContentType> = _currentMode.asStateFlow()
    
    // Current mode context for architectural guarantees
    val currentContext: ContentModeContext
        get() = when (_currentMode.value) {
            ContentType.MANGA -> MangaModeContext
            ContentType.NOVEL -> NovelModeContext
        }
    
    fun setMode(mode: ContentType) {
        _currentMode.value = mode
        saveState() // Auto-save on mode change
    }
    
    fun getCurrentMode(): ContentType = _currentMode.value
    
    fun toggleMode() {
        _currentMode.value = when (_currentMode.value) {
            ContentType.MANGA -> ContentType.NOVEL
            ContentType.NOVEL -> ContentType.MANGA
        }
        saveState() // Auto-save on toggle
    }
    
    fun isCurrentMode(mode: ContentType): Boolean = _currentMode.value == mode
    
    fun isMangaMode(): Boolean = _currentMode.value.isManga()
    fun isNovelMode(): Boolean = _currentMode.value.isNovel()
    
    // Mode state utilities
    fun ifMangaMode(action: () -> Unit) {
        if (isMangaMode()) action()
    }
    
    fun ifNovelMode(action: () -> Unit) {
        if (isNovelMode()) action()
    }
    
    fun <T> withMode(mangaAction: () -> T, novelAction: () -> T): T {
        return when (_currentMode.value) {
            ContentType.MANGA -> mangaAction()
            ContentType.NOVEL -> novelAction()
        }
    }
    
    // State persistence - will integrate with preferences in Phase 1
    private var persistenceManager: ModeStatePersistence? = null
    
    fun initializePersistence(persistence: ModeStatePersistence) {
        persistenceManager = persistence
        restoreState()
    }
    
    private fun saveState() {
        persistenceManager?.saveCurrentMode(_currentMode.value)
    }
    
    private fun restoreState() {
        persistenceManager?.getCurrentMode()?.let { savedMode ->
            _currentMode.value = savedMode
        }
    }
}

// Interface for mode state persistence
interface ModeStatePersistence {
    fun saveCurrentMode(mode: ContentType)
    fun getCurrentMode(): ContentType?
}

// Temporary implementation for Phase 1 (will be replaced with proper preferences in later phases)
class InMemoryModeStatePersistence : ModeStatePersistence {
    private var savedMode: ContentType? = null
    
    override fun saveCurrentMode(mode: ContentType) {
        savedMode = mode
    }
    
    override fun getCurrentMode(): ContentType? = savedMode
}