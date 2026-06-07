package yokai.source.novel.providers.base

/**
 * Declarative site configuration for template-based providers
 * Reduces boilerplate and standardizes provider development
 */
data class SiteConfiguration(
    val baseUrl: String,
    val searchUrl: String,
    val rateLimitMs: Long = 1000L,
    val searchResultSelectors: SearchResultSelectors,
    val detailSelectors: DetailSelectors,
    val chapterSelectors: ChapterSelectors,
    val contentSelectors: ContentSelectors,
    val contentFilters: List<ContentFilter> = emptyList(),
    val urlPatterns: UrlPatterns = UrlPatterns(),
    val headers: Map<String, String> = emptyMap()
)

data class SearchResultSelectors(
    val container: String,
    val title: String,
    val url: String,
    val thumbnail: String? = null,
    val author: String? = null,
    val description: String? = null,
    val status: String? = null,
    val lastUpdate: String? = null
)

data class DetailSelectors(
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val status: String? = null,
    val genres: String? = null,
    val tags: String? = null
)

data class ChapterSelectors(
    val container: String,
    val title: String,
    val url: String,
    val date: String? = null,
    val scanlator: String? = null
)

data class ContentSelectors(
    val content: String,
    val title: String? = null,
    val nextChapter: String? = null,
    val previousChapter: String? = null
)

data class UrlPatterns(
    val novelUrl: Regex? = null,
    val chapterUrl: Regex? = null,
    val imageUrl: Regex? = null
)

/**
 * Content filtering system for cleaning up chapter content
 */
abstract class ContentFilter {
    abstract fun apply(content: String): String
    
    companion object {
        fun removeSelector(selector: String) = object : ContentFilter() {
            override fun apply(content: String): String {
                return org.jsoup.Jsoup.parse(content).apply {
                    select(selector).remove()
                }.html()
            }
        }
        
        fun removePattern(pattern: Regex) = object : ContentFilter() {
            override fun apply(content: String): String {
                return content.replace(pattern, "")
            }
        }
        
        fun removeText(text: String) = object : ContentFilter() {
            override fun apply(content: String): String {
                return content.replace(text, "")
            }
        }
        
        fun replacePattern(pattern: Regex, replacement: String) = object : ContentFilter() {
            override fun apply(content: String): String {
                return content.replace(pattern, replacement)
            }
        }
        
        // Common filters for novel sites
        val removeFooter = removeSelector("footer, .footer, .post-footer")
        val removeNavigation = removeSelector("nav, .nav, .navigation, .chapter-nav")
        val removeAds = removeSelector(".ad, .advertisement, .banner, .sponsor")
        val removeComments = removeSelector(".comments, .comment-section, #comments")
        val removeSocialShare = removeSelector(".share, .social-share, .social-buttons")
        val removeScript = removeSelector("script, style, iframe")
        
        // Text cleaning filters
        val cleanLineBreaks = replacePattern(Regex("\\n\\s*\\n\\s*\\n"), "\n\n")
        val removeExtraSpaces = replacePattern(Regex("\\s+"), " ")
        val fixParagraphs = replacePattern(Regex("</p>\\s*<p>"), "</p>\n<p>")
    }
}

/**
 * Common site configurations for popular novel site frameworks
 */
object CommonSiteConfigs {
    
    /**
     * Configuration for WordPress-based novel sites
     * Many novel sites use WordPress with similar structures
     */
    fun wordPressNovel(baseUrl: String): SiteConfiguration {
        return SiteConfiguration(
            baseUrl = baseUrl,
            searchUrl = "$baseUrl/?s={query}&post_type=wp-manga",
            searchResultSelectors = SearchResultSelectors(
                container = ".c-tabs-item__content",
                title = ".post-title h3 a",
                url = ".post-title h3 a",
                thumbnail = ".tab-thumb img",
                author = ".mg_author .summary-content",
                description = ".tab-summary .summary-content"
            ),
            detailSelectors = DetailSelectors(
                title = ".post-title h1",
                author = ".author-content a",
                description = ".summary__content .summary-content",
                thumbnail = ".summary_image img",
                status = ".post-status .summary-content",
                genres = ".genres-content a"
            ),
            chapterSelectors = ChapterSelectors(
                container = ".wp-manga-chapter",
                title = "a",
                url = "a",
                date = ".chapter-release-date"
            ),
            contentSelectors = ContentSelectors(
                content = ".reading-content, .text-left",
                title = ".breadcrumb li:last-child",
                nextChapter = ".next_page",
                previousChapter = ".prev_page"
            ),
            contentFilters = listOf(
                ContentFilter.removeAds,
                ContentFilter.removeNavigation,
                ContentFilter.removeComments,
                ContentFilter.removeSocialShare,
                ContentFilter.removeScript,
                ContentFilter.cleanLineBreaks
            )
        )
    }
    
    /**
     * Configuration for Madara theme-based sites
     * Popular manga/novel theme used by many sites
     */
    fun madaraTheme(baseUrl: String): SiteConfiguration {
        return SiteConfiguration(
            baseUrl = baseUrl,
            searchUrl = "$baseUrl/?s={query}&post_type=wp-manga",
            searchResultSelectors = SearchResultSelectors(
                container = ".search-wrap .c-tabs-item__content",
                title = ".post-title h3 a",
                url = ".post-title h3 a",
                thumbnail = ".tab-thumb img",
                author = ".mg_author .summary-content",
                status = ".mg_status .summary-content"
            ),
            detailSelectors = DetailSelectors(
                title = ".post-title h1",
                author = ".author-content a",
                description = ".summary__content p",
                thumbnail = ".summary_image img",
                status = ".post-status .summary-content",
                genres = ".genres-content a"
            ),
            chapterSelectors = ChapterSelectors(
                container = ".wp-manga-chapter",
                title = "a",
                url = "a",
                date = ".chapter-release-date"
            ),
            contentSelectors = ContentSelectors(
                content = ".reading-content",
                nextChapter = ".nav-previous a",
                previousChapter = ".nav-next a"
            ),
            contentFilters = listOf(
                ContentFilter.removeSelector(".pirate-notice"),
                ContentFilter.removeSelector(".donation-box"),
                ContentFilter.removeAds,
                ContentFilter.removeComments,
                ContentFilter.removeScript,
                ContentFilter.fixParagraphs
            )
        )
    }
    
    /**
     * Configuration for custom novel reading sites
     * Base configuration that can be customized
     */
    fun customNovel(
        baseUrl: String,
        searchUrl: String,
        searchContainer: String,
        titleSelector: String,
        urlSelector: String,
        contentSelector: String
    ): SiteConfiguration {
        return SiteConfiguration(
            baseUrl = baseUrl,
            searchUrl = searchUrl,
            searchResultSelectors = SearchResultSelectors(
                container = searchContainer,
                title = titleSelector,
                url = urlSelector
            ),
            detailSelectors = DetailSelectors(
                title = titleSelector
            ),
            chapterSelectors = ChapterSelectors(
                container = ".chapter-list li",
                title = "a",
                url = "a"
            ),
            contentSelectors = ContentSelectors(
                content = contentSelector
            ),
            contentFilters = listOf(
                ContentFilter.removeAds,
                ContentFilter.removeScript,
                ContentFilter.cleanLineBreaks
            )
        )
    }
}