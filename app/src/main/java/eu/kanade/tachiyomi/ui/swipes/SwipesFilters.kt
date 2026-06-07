package eu.kanade.tachiyomi.ui.swipes

/**
 * Data class representing all active filters for the Swipes feature
 * 
 * @param enabledSourceIds Set of source IDs to include in recommendations (empty = all sources)
 * @param excludeNsfw Whether to exclude NSFW content from recommendations
 * @param includedGenres Set of genres that MUST be present (empty = any genre)
 * @param excludedGenres Set of genres that must NOT be present (empty = no genre exclusions)
 */
data class SwipesFilters(
    val enabledSourceIds: Set<Long> = emptySet(), // Empty = all sources enabled
    val excludeNsfw: Boolean = true, // Default: exclude NSFW
    val includedGenres: Set<String> = emptySet(), // Empty = no genre requirements
    val excludedGenres: Set<String> = emptySet(), // Empty = no genre exclusions
) {
    companion object {
        /**
         * Default filter configuration
         */
        val DEFAULT = SwipesFilters(
            enabledSourceIds = emptySet(),
            excludeNsfw = true,
            includedGenres = emptySet(),
            excludedGenres = emptySet()
        )
    }
    
    /**
     * Check if filters are set to default values
     */
    fun isDefault(): Boolean = this == DEFAULT
    
    /**
     * Check if a source ID is enabled
     * @return true if source should be included in recommendations
     */
    fun isSourceEnabled(sourceId: Long): Boolean {
        // If no sources specified, all are enabled
        return enabledSourceIds.isEmpty() || sourceId in enabledSourceIds
    }
}
