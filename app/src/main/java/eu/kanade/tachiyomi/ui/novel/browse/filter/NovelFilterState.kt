package eu.kanade.tachiyomi.ui.novel.browse.filter

import kotlinx.serialization.Serializable

/**
 * Universal novel filter state that works across all novel sources.
 * This provides a consistent filtering experience regardless of the source.
 * 
 * NOTE: The UI should dynamically show only filters supported by the current source.
 * Use SourceCapabilities to determine what to display.
 */
@Serializable
data class NovelFilterState(
    val sortBy: SortOption = SortOption.POPULAR,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val status: NovelStatus = NovelStatus.ANY,
    val genres: Set<Genre> = emptySet(),
    val excludedGenres: Set<Genre> = emptySet(),
    val demographic: Demographic = Demographic.ANY,
    val contentRating: ContentRating = ContentRating.ANY,
    val languageLevel: LanguageLevel = LanguageLevel.ANY,
    val violenceLevel: ViolenceLevel = ViolenceLevel.ANY,
    // Content warnings (tri-state: ignore/include/exclude)
    val includedContentWarnings: Set<ContentWarning> = emptySet(),
    val excludedContentWarnings: Set<ContentWarning> = emptySet(),
    val minChapters: Int? = null,
    val maxChapters: Int? = null,
    val minRating: Float? = null,
    val tags: List<String> = emptyList(),
) {
    fun isDefault(): Boolean = this == NovelFilterState()
    
    fun activeFilterCount(): Int {
        var count = 0
        if (sortBy != SortOption.POPULAR) count++
        if (status != NovelStatus.ANY) count++
        if (genres.isNotEmpty()) count++
        if (excludedGenres.isNotEmpty()) count++
        if (demographic != Demographic.ANY) count++
        if (contentRating != ContentRating.ANY) count++
        if (languageLevel != LanguageLevel.ANY) count++
        if (violenceLevel != ViolenceLevel.ANY) count++
        if (includedContentWarnings.isNotEmpty()) count++
        if (excludedContentWarnings.isNotEmpty()) count++
        if (minChapters != null) count++
        if (maxChapters != null) count++
        if (minRating != null) count++
        if (tags.isNotEmpty()) count++
        return count
    }
}

@Serializable
enum class SortOption(val displayName: String, val queryValue: String) {
    POPULAR("Popular", "popular"),
    LAST_UPDATED("Last Updated", "last_updated"),
    NEWEST("Newest", "newest"),
    RATING("Rating", "rating"),
    MOST_CHAPTERS("Most Chapters", "most_chapters"),
    ALPHABETICAL("Alphabetical", "alphabetical"),
    VIEWS("Views", "views"),
    TRENDING("Trending", "trending");
    
    companion object {
        fun fromQueryValue(value: String): SortOption {
            // Handle legacy values for backwards compatibility
            return when (value) {
                "updated" -> LAST_UPDATED
                "chapters" -> MOST_CHAPTERS
                "hot" -> TRENDING  // Map "hot" to TRENDING for backwards compatibility
                else -> entries.find { it.queryValue == value } ?: POPULAR
            }
        }
    }
}

@Serializable
enum class SortOrder(val displayName: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

@Serializable
enum class NovelStatus(val displayName: String, val queryValue: String) {
    ANY("Any", ""),
    ONGOING("Ongoing", "ongoing"),
    COMPLETED("Completed", "completed"),
    HIATUS("Hiatus", "hiatus"),
    DROPPED("Dropped", "dropped");
    
    companion object {
        fun fromQueryValue(value: String): NovelStatus {
            return entries.find { it.queryValue == value } ?: ANY
        }
    }
}

@Serializable
enum class Genre(val displayName: String, val queryValue: String) {
    // Primary genres (alphabetical)
    ACTION("Action", "action"),
    ADVENTURE("Adventure", "adventure"),
    COMEDY("Comedy", "comedy"),
    CONTEMPORARY("Contemporary", "contemporary"),
    DRAMA("Drama", "drama"),
    FANTASY("Fantasy", "fantasy"),
    HISTORICAL("Historical", "historical"),
    HORROR("Horror", "horror"),
    MYSTERY("Mystery", "mystery"),
    PSYCHOLOGICAL("Psychological", "psychological"),
    ROMANCE("Romance", "romance"),
    SATIRE("Satire", "satire"),
    SCI_FI("Sci-Fi", "sci_fi"),
    SHORT_STORY("Short Story", "one_shot"),
    SLICE_OF_LIFE("Slice of Life", "slice_of_life"),
    SUPERNATURAL("Supernatural", "supernatural"),
    THRILLER("Thriller", "thriller"),
    TRAGEDY("Tragedy", "tragedy"),
    
    // AI Content Tags (Royal Road specific)
    AI_ASSISTED("AI-Assisted Content", "ai_assisted"),
    AI_GENERATED("AI-Generated Content", "ai_generated"),
    
    // Popular Tags / Sub-genres
    ANTI_HERO("Anti-Hero Lead", "anti-hero_lead"),
    ARTIFICIAL_INTELLIGENCE("Artificial Intelligence", "artificial_intelligence"),
    ATTRACTIVE_LEAD("Attractive Lead", "attractive_lead"),
    CULTIVATION("Cultivation", "cultivation"),
    CYBERPUNK("Cyberpunk", "cyberpunk"),
    DUNGEON("Dungeon", "dungeon"),
    DYSTOPIA("Dystopia", "dystopia"),
    FEMALE_LEAD("Female Lead", "female_lead"),
    FIRST_CONTACT("First Contact", "first_contact"),
    GAMELIT("GameLit", "gamelit"),
    GENDER_BENDER("Gender Bender", "gender_bender"),
    GRIMDARK("Grimdark", "grimdark"),
    HARD_SF("Hard Sci-Fi", "hard_sci-fi"),
    HAREM("Harem", "harem"),
    HIGH_FANTASY("High Fantasy", "high_fantasy"),
    ISEKAI("Isekai", "isekai"),
    LITRPG("LitRPG", "litrpg"),
    LOW_FANTASY("Low Fantasy", "low_fantasy"),
    MAGIC("Magic", "magic"),
    MALE_LEAD("Male Lead", "male_lead"),
    MARTIAL_ARTS("Martial Arts", "martial_arts"),
    MULTIPLE_LEAD("Multiple Lead Characters", "multiple_lead"),
    MYTHOS("Mythos", "mythos"),
    NON_HUMAN_LEAD("Non-Human Lead", "non-human_lead"),
    PORTAL_FANTASY("Portal Fantasy/Isekai", "portal_fantasy_isekai"),
    POST_APOCALYPTIC("Post Apocalyptic", "post_apocalyptic"),
    PROGRESSION("Progression", "progression"),
    REINCARNATION("Reincarnation", "reincarnation"),
    RULING_CLASS("Ruling Class", "ruling_class"),
    SCHOOL_LIFE("School Life", "school_life"),
    SECRET_IDENTITY("Secret Identity", "secret_identity"),
    SOFT_SF("Soft Sci-Fi", "soft_sci-fi"),
    SPACE_OPERA("Space Opera", "space_opera"),
    SPORTS("Sports", "sports"),
    STEAMPUNK("Steampunk", "steampunk"),
    STRATEGY("Strategy", "strategy"),
    STRONG_LEAD("Strong Lead", "strong_lead"),
    SUPER_HEROES("Super Heroes", "super_heroes"),
    SURVIVAL("Survival", "survival"),
    TIME_LOOP("Time Loop", "loop"),
    TIME_TRAVEL("Time Travel", "time_travel"),
    URBAN_FANTASY("Urban Fantasy", "urban_fantasy"),
    VILLAINOUS_LEAD("Villainous Lead", "villainous_lead"),
    VIRTUAL_REALITY("Virtual Reality", "virtual_reality"),
    WAR_MILITARY("War and Military", "war_and_military"),
    WUXIA("Wuxia", "wuxia"),
    XIANXIA("Xianxia", "xianxia"),
    XUANHUAN("Xuanhuan", "xuanhuan"),
    YOUNG_ADULT("Young Adult", "reader-young_adult"),
    
    // Asian novel specific genres
    EASTERN("Eastern", "eastern"),
    MATURE("Mature", "mature"),
    YAOI("Yaoi", "yaoi"),
    SHOUNEN_AI("Shounen Ai", "shounen_ai"),
    JOSEI("Josei", "josei"),
    SHOUJO("Shoujo", "shoujo"),
    SMUT("Smut", "smut"),
    MECHA("Mecha", "mecha"),
    MARTIAL("Martial", "martial"),
    SEINEN("Seinen", "seinen"),
    LOLICON("Lolicon", "lolicon"),
    ADULT("Adult", "adult"),
    ECCHI("Ecchi", "ecchi"),
    GENDER_BENDER_NOVEL("Gender Bender", "gender_bender_novel"),
    WEBTOONS("Webtoons", "webtoons"),
    MANHUA("Manhua", "manhua");

    companion object {
        fun fromQueryValue(value: String): Genre? {
            return entries.find { it.queryValue == value }
        }
        
        // All genres in alphabetical order for the single "Genres" dropdown
        val allGenres: List<Genre>
            get() = entries.sortedBy { it.displayName }
    }
}

@Serializable
enum class Demographic(val displayName: String, val queryValue: String) {
    ANY("Any", ""),
    SHOUNEN("Shounen", "shounen"),
    SHOUJO("Shoujo", "shoujo"),
    SEINEN("Seinen", "seinen"),
    JOSEI("Josei", "josei"),
    KIDS("Kids", "kids");
    
    companion object {
        fun fromQueryValue(value: String): Demographic {
            return entries.find { it.queryValue == value } ?: ANY
        }
    }
}

@Serializable
enum class ContentRating(val displayName: String, val queryValue: String) {
    ANY("Any", ""),
    EVERYONE("Everyone", "everyone"),
    TEEN("Teen", "teen"),
    MATURE("Mature", "mature"),
    ADULT("Adult (18+)", "adult");
    
    companion object {
        fun fromQueryValue(value: String): ContentRating {
            return entries.find { it.queryValue == value } ?: ANY
        }
    }
}

@Serializable
enum class LanguageLevel(val displayName: String, val queryValue: String) {
    ANY("Any", ""),
    NONE("None", "none"),
    MILD("Mild", "mild"),
    MODERATE("Moderate", "moderate"),
    FREQUENT("Frequent", "frequent");
    
    companion object {
        fun fromQueryValue(value: String): LanguageLevel {
            return entries.find { it.queryValue == value } ?: ANY
        }
    }
}

@Serializable
enum class ViolenceLevel(val displayName: String, val queryValue: String) {
    ANY("Any", ""),
    NONE("None", "none"),
    MILD("Mild", "mild"),
    MODERATE("Moderate", "moderate"),
    GRAPHIC("Graphic", "graphic"),
    GORE("Gore", "gore");
    
    companion object {
        fun fromQueryValue(value: String): ViolenceLevel {
            return entries.find { it.queryValue == value } ?: ANY
        }
    }
}

/**
 * Content warnings that can be included or excluded in browse results.
 * These are tri-state: ignore (show all), include (only show with warning), exclude (hide with warning).
 * 
 * Query values should match the source's expected values.
 */
@Serializable
enum class ContentWarning(val displayName: String, val queryValue: String) {
    AI_ASSISTED("AI-Assisted Content", "ai_assisted"),
    AI_GENERATED("AI-Generated Content", "ai_generated"),
    GRAPHIC_VIOLENCE("Graphic Violence", "graphic_violence"),
    PROFANITY("Profanity", "profanity"),
    SENSITIVE_CONTENT("Sensitive Content", "sensitive_content"),
    SEXUAL_CONTENT("Sexual Content", "sexual_content");
    
    companion object {
        fun fromQueryValue(value: String): ContentWarning? {
            return entries.find { it.queryValue == value }
        }
        
        /**
         * All content warnings in display order.
         */
        val allWarnings: List<ContentWarning>
            get() = entries.toList()
    }
}
