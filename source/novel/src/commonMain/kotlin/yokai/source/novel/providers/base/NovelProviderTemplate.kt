package yokai.source.novel.providers.base

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import yokai.source.novel.NovelMainAPI
import yokai.source.novel.model.*

/**
 * Template-based provider system for declarative novel provider development
 * Reduces boilerplate and standardizes provider patterns
 */
abstract class NovelProviderTemplate : NovelMainAPI() {
    
    abstract val siteConfig: SiteConfiguration
    
    // Template methods using site configuration
    override suspend fun searchNovels(query: String, page: Int): List<NovelSearchResult> {
        val searchUrl = buildSearchUrl(query, page)
        val document = fetchDocument(searchUrl)
        return parseSearchResults(document)
    }
    
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        return getBrowseNovels(page, mapOf("sort" to "popular"))
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        return getBrowseNovels(page, mapOf("sort" to "latest"))
    }
    
    override suspend fun getNovelDetails(url: String): NovelDetails {
        val document = fetchDocument(url)
        return parseNovelDetails(document, url)
    }
    
    override suspend fun getChapterList(novelUrl: String): List<NovelChapter> {
        val document = fetchDocument(novelUrl)
        return parseChapterList(document, novelUrl)
    }
    
    override suspend fun getNovelChapterContent(chapterUrl: String): NovelContent {
        val document = fetchDocument(chapterUrl)
        val content = parseChapterContent(document)
        val filteredContent = applyContentFilters(content)
        return NovelContent(
            chapterUrl = chapterUrl,
            content = filteredContent,
            title = parseChapterTitle(document),
            nextChapterUrl = parseNextChapterUrl(document),
            previousChapterUrl = parsePreviousChapterUrl(document),
            wordCount = countWords(filteredContent),
            readingTimeMinutes = NovelContent.calculateReadingTime(countWords(filteredContent))
        )
    }
    
    // Protected template methods for customization
    protected open fun buildSearchUrl(query: String, page: Int): String {
        return siteConfig.searchUrl
            .replace("{query}", query)
            .replace("{page}", page.toString())
    }
    
    protected open fun parseSearchResults(document: Document): List<NovelSearchResult> {
        return document.select(siteConfig.searchResultSelectors.container).mapNotNull { element ->
            try {
                NovelSearchResult(
                    title = selectText(element, siteConfig.searchResultSelectors.title),
                    url = selectUrl(element, siteConfig.searchResultSelectors.url),
                    thumbnailUrl = selectImageUrl(element, siteConfig.searchResultSelectors.thumbnail),
                    author = selectTextOptional(element, siteConfig.searchResultSelectors.author),
                    description = selectTextOptional(element, siteConfig.searchResultSelectors.description),
                    status = parseStatus(selectTextOptional(element, siteConfig.searchResultSelectors.status)).value,
                    sourceId = id,
                    sourceName = name,
                    lastUpdate = parseDate(selectTextOptional(element, siteConfig.searchResultSelectors.lastUpdate))
                )
            } catch (e: Exception) {
                null // Skip invalid results
            }
        }
    }
    
    protected open fun parseNovelDetails(document: Document, url: String): NovelDetails {
        return NovelDetails(
            title = selectText(document, siteConfig.detailSelectors.title),
            url = url,
            author = selectTextOptional(document, siteConfig.detailSelectors.author),
            description = selectTextOptional(document, siteConfig.detailSelectors.description),
            thumbnailUrl = selectImageUrl(document, siteConfig.detailSelectors.thumbnail),
            status = parseStatus(selectTextOptional(document, siteConfig.detailSelectors.status)).value,
            genres = selectTexts(document, siteConfig.detailSelectors.genres),
            tags = selectTexts(document, siteConfig.detailSelectors.tags),
            sourceId = id,
            sourceName = name,
            chapters = parseChapterList(document, url)
        )
    }
    
    protected open fun parseChapterList(document: Document, novelUrl: String): List<NovelChapter> {
        return document.select(siteConfig.chapterSelectors.container).mapIndexedNotNull { index, element ->
            try {
                NovelChapter(
                    title = selectText(element, siteConfig.chapterSelectors.title),
                    url = selectUrl(element, siteConfig.chapterSelectors.url),
                    dateUpload = parseDate(selectTextOptional(element, siteConfig.chapterSelectors.date)),
                    chapterNumber = parseChapterNumber(selectText(element, siteConfig.chapterSelectors.title)),
                    scanlator = selectTextOptional(element, siteConfig.chapterSelectors.scanlator),
                    sourceOrder = index
                )
            } catch (e: Exception) {
                null // Skip invalid chapters
            }
        }
    }
    
    protected open fun parseChapterContent(document: Document): String {
        val contentSelector = siteConfig.contentSelectors.content
        val contentElement = document.selectFirst(contentSelector)
        return contentElement?.html() ?: ""
    }
    
    protected open fun parseChapterTitle(document: Document): String? {
        return selectTextOptional(document, siteConfig.contentSelectors.title)
    }
    
    protected open fun parseNextChapterUrl(document: Document): String? {
        return selectUrlOptional(document, siteConfig.contentSelectors.nextChapter)
    }
    
    protected open fun parsePreviousChapterUrl(document: Document): String? {
        return selectUrlOptional(document, siteConfig.contentSelectors.previousChapter)
    }
    
    // Content filtering
    protected open fun applyContentFilters(content: String): String {
        var filteredContent = content
        siteConfig.contentFilters.forEach { filter ->
            filteredContent = filter.apply(filteredContent)
        }
        return filteredContent.trim()
    }
    
    // Utility methods
    private suspend fun fetchDocument(url: String): Document {
        // Use injected HTTP client for proper network handling
        return httpClient.getDocument(url)
    }
    
    private fun selectText(element: Element, selector: String): String {
        return element.selectFirst(selector)?.text() ?: throw Exception("Required selector not found: $selector")
    }
    
    private fun selectTextOptional(element: Element, selector: String?): String? {
        return if (selector != null) element.selectFirst(selector)?.text() else null
    }
    
    private fun selectTexts(element: Element, selector: String?): List<String> {
        return if (selector != null) element.select(selector).map { it.text() } else emptyList()
    }
    
    private fun selectUrl(element: Element, selector: String): String {
        val href = element.selectFirst(selector)?.attr("href") ?: throw Exception("URL selector not found: $selector")
        return if (href.startsWith("http")) href else mainUrl.trimEnd('/') + "/" + href.trimStart('/')
    }
    
    private fun selectUrlOptional(element: Element, selector: String?): String? {
        if (selector == null) return null
        val href = element.selectFirst(selector)?.attr("href") ?: return null
        return if (href.startsWith("http")) href else mainUrl.trimEnd('/') + "/" + href.trimStart('/')
    }
    
    private fun selectImageUrl(element: Element, selector: String?): String? {
        if (selector == null) return null
        val src = element.selectFirst(selector)?.attr("src") ?: return null
        return if (src.startsWith("http")) src else mainUrl.trimEnd('/') + "/" + src.trimStart('/')
    }
    
    private fun parseStatus(statusText: String?): NovelStatus {
        return if (statusText != null) NovelStatus.fromString(statusText) else NovelStatus.UNKNOWN
    }
    
    private fun parseDate(dateText: String?): Long {
        // Basic date parsing - would be enhanced with proper date parsing
        return if (dateText != null) System.currentTimeMillis() else 0L
    }
    
    private fun parseChapterNumber(title: String): Float {
        // Extract chapter number from title
        val regex = Regex("""(?:chapter|ch\.?)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        val match = regex.find(title)
        return match?.groups?.get(1)?.value?.toFloatOrNull() ?: 0f
    }
    
    private fun countWords(content: String): Int {
        // Remove HTML tags and count words
        val plainText = content.replace(Regex("<[^>]*>"), " ")
        return plainText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    }
}