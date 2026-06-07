package yokai.core.mode

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import yokai.core.content.ContentType

// Preference-based implementation for proper persistence
class PreferenceModeStatePersistence(private val preferences: PreferencesHelper) : ModeStatePersistence {
    
    override fun saveCurrentMode(mode: ContentType) {
        preferences.getStringPref("current_content_mode").set(mode.name)
    }
    
    override fun getCurrentMode(): ContentType? {
        return try {
            val savedMode = preferences.getStringPref("current_content_mode", ContentType.MANGA.name).get()
            ContentType.valueOf(savedMode)
        } catch (e: Exception) {
            ContentType.MANGA // Default fallback
        }
    }
}