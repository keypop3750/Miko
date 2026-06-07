package yokai.source.novel.model

/**
 * Base class for novel filters
 */
sealed class NovelFilter {
    abstract val name: String
    abstract val key: String
    
    /**
     * Single selection filter
     */
    data class Select(
        override val name: String,
        override val key: String,
        val options: List<Option>,
        val defaultValue: String? = null
    ) : NovelFilter()
    
    /**
     * Multiple selection filter
     */
    data class MultiSelect(
        override val name: String,
        override val key: String,
        val options: List<Option>,
        val defaultValues: List<String> = emptyList()
    ) : NovelFilter()
    
    /**
     * Text input filter
     */
    data class Text(
        override val name: String,
        override val key: String,
        val placeholder: String? = null,
        val defaultValue: String = ""
    ) : NovelFilter()
    
    /**
     * Checkbox filter
     */
    data class CheckBox(
        override val name: String,
        override val key: String,
        val defaultValue: Boolean = false
    ) : NovelFilter()
    
    /**
     * Range filter (for ratings, years, etc.)
     */
    data class Range(
        override val name: String,
        override val key: String,
        val min: Int,
        val max: Int,
        val defaultMin: Int? = null,
        val defaultMax: Int? = null
    ) : NovelFilter()
    
    /**
     * Filter option
     */
    data class Option(
        val displayName: String,
        val value: String
    )
}