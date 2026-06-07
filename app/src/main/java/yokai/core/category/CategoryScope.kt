package yokai.core.category

/**
 * Defines the scope of a category - which content types it applies to.
 * 
 * When creating a category:
 * - MANGA_ONLY: Creates category only in manga categories table
 * - NOVEL_ONLY: Creates category only in novel categories table
 * - BOTH: Creates category in BOTH tables with the same name
 * 
 * The "BOTH" scope means the category NAME exists for both modes, but the
 * CONTENT shown is always mode-specific (manga items in manga mode, novel items in novel mode).
 */
enum class CategoryScope {
    /**
     * Category exists only for manga.
     * Visible only when in manga mode.
     */
    MANGA_ONLY,
    
    /**
     * Category exists only for novels.
     * Visible only when in novel mode.
     */
    NOVEL_ONLY,
    
    /**
     * Category exists for both manga and novels.
     * The same category name is created in both tables.
     * In manga mode: shows manga items in this category
     * In novel mode: shows novel items in this category
     */
    BOTH
}
