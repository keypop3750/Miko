package yokai.source.novel.providers.en

import org.jsoup.nodes.Document
import yokai.source.novel.providers.base.NovelProviderTemplate
import yokai.source.novel.providers.base.SiteConfiguration
import yokai.source.novel.providers.base.SearchResultSelectors
import yokai.source.novel.providers.base.DetailSelectors
import yokai.source.novel.providers.base.ChapterSelectors
import yokai.source.novel.providers.base.ContentSelectors
import yokai.source.novel.providers.base.ContentFilter
import yokai.source.novel.model.*

/**
 * RoyalRoad provider - Using template system with correct selectors from QuickNovel
 */
class RoyalRoadProvider : NovelProviderTemplate() {
    
    override val id: Long = 6001L
    override val name = "RoyalRoad"
    override val mainUrl = "https://www.royalroad.com"
    override val lang = "en"
    override val hasMainPage = true
    override val rateLimitTime: Long = 500L
    
    override val siteConfig = SiteConfiguration(
        baseUrl = mainUrl,
        searchUrl = "$mainUrl/fictions/search?title={query}&page={page}",
        rateLimitMs = 500L,
        
        // Search result selectors based on QuickNovel's working implementation
        searchResultSelectors = SearchResultSelectors(
            container = "div.fiction-list-item",
            title = "div.search-content > h2.fiction-title > a",
            url = "div.search-content > h2.fiction-title > a",
            thumbnail = "figure.text-center > a > img",
            author = null, // Not in search results
            description = null, // Not in search results
            status = null,
            lastUpdate = null
        ),
        
        // Detail page selectors
        detailSelectors = DetailSelectors(
            title = "h1.font-white",
            author = "h4.font-white > span > a",
            description = "div.description > div", // Gets all description paragraphs
            thumbnail = "div.fic-header > div > div.cover-art-container > img",
            status = "div.col-md-8 > div.margin-bottom-10 > span.label",
            genres = "span.tags > a",
            tags = "span.tags > a"
        ),
        
        // Chapter list selectors
        chapterSelectors = ChapterSelectors(
            container = "div.portlet-body > table > tbody > tr",
            title = "td:first-child > a",
            url = "", // Special handling - use data-url attribute
            date = "td:last-child > a > time"
        ),
        
        // Chapter content selectors
        contentSelectors = ContentSelectors(
            content = "div.chapter-content",
            title = "h1.font-white",
            nextChapter = null, // RoyalRoad uses JS navigation
            previousChapter = null
        ),
        
        // Content filters to remove RoyalRoad-specific elements
        contentFilters = listOf(
            ContentFilter.removeSelector("script"),
            ContentFilter.removeSelector("style"),
            ContentFilter.removeSelector("noscript")
        )
    )
    
    // Override to map to RoyalRoad's actual filter names
    override suspend fun getPopularNovels(page: Int): List<NovelSearchResult> {
        return getBrowseNovels(page, mapOf("sort" to "best-rated"))
    }
    
    override suspend fun getLatestUpdates(page: Int): List<NovelSearchResult> {
        return getBrowseNovels(page, mapOf("sort" to "latest-updates"))
    }
    
    // Override to handle RoyalRoad's special chapter URL in data-url attribute  
    override fun parseChapterList(document: Document, novelUrl: String): List<NovelChapter> {
        android.util.Log.d("RoyalRoadProvider", "=== parseChapterList START ===")
        android.util.Log.d("RoyalRoadProvider", "Container selector: ${siteConfig.chapterSelectors.container}")
        
        val selectedElements = document.select(siteConfig.chapterSelectors.container)
        android.util.Log.d("RoyalRoadProvider", "Selected elements count: ${selectedElements.size}")
        
        // Log first 3 elements for inspection
        selectedElements.take(3).forEachIndexed { idx, elem ->
            android.util.Log.d("RoyalRoadProvider", "Element $idx HTML: ${elem.html().take(200)}")
            android.util.Log.d("RoyalRoadProvider", "Element $idx attributes: ${elem.attributes()}")
            android.util.Log.d("RoyalRoadProvider", "Element $idx data-url: '${elem.attr("data-url")}'")
            
            // Try alternative URL extraction
            val tdLink = elem.selectFirst("td:first-child > a")
            android.util.Log.d("RoyalRoadProvider", "Element $idx td link href: '${tdLink?.attr("href") ?: "NOT FOUND"}'")
        }
        
        var successCount = 0
        var skipCount = 0
        var errorCount = 0
        
        return selectedElements.mapIndexedNotNull { index, element ->
            try {
                // RoyalRoad stores chapter URL in data-url attribute
                val cUrl = element.attr("data-url")
                android.util.Log.d("RoyalRoadProvider", "Processing index $index: data-url='$cUrl'")
                
                if (cUrl.isNullOrBlank()) {
                    android.util.Log.d("RoyalRoadProvider", "Index $index: data-url is blank, trying alternative")
                    
                    // Try getting URL from td > a href as fallback
                    val tdLink = element.selectFirst("td:first-child > a")
                    val hrefUrl = tdLink?.attr("href")
                    android.util.Log.d("RoyalRoadProvider", "Index $index: href fallback='$hrefUrl'")
                    
                    if (hrefUrl.isNullOrBlank()) {
                        android.util.Log.d("RoyalRoadProvider", "Index $index: No URL found, skipping")
                        skipCount++
                        return@mapIndexedNotNull null
                    }
                    
                    val title = tdLink.text()
                    if (title.isBlank()) {
                        android.util.Log.d("RoyalRoadProvider", "Index $index: No title found, skipping")
                        skipCount++
                        return@mapIndexedNotNull null
                    }
                    
                    android.util.Log.d("RoyalRoadProvider", "Index $index: ✅ SUCCESS (href) - title='$title'")
                    successCount++
                    
                    return@mapIndexedNotNull NovelChapter(
                        title = title,
                        url = if (hrefUrl.startsWith("http")) hrefUrl else "$mainUrl${if (hrefUrl.startsWith("/")) hrefUrl else "/$hrefUrl"}",
                        dateUpload = 0L,
                        chapterNumber = extractChapterNumber(title),
                        scanlator = null,
                        sourceOrder = index
                    )
                }
                
                val td = element.select("> td")
                val title = td.getOrNull(0)?.selectFirst("> a")?.text()
                
                if (title.isNullOrBlank()) {
                    android.util.Log.d("RoyalRoadProvider", "Index $index: Title extraction failed, skipping")
                    skipCount++
                    return@mapIndexedNotNull null
                }
                
                android.util.Log.d("RoyalRoadProvider", "Index $index: ✅ SUCCESS (data-url) - title='$title'")
                successCount++
                
                NovelChapter(
                    title = title,
                    url = if (cUrl.startsWith("http")) cUrl else "$mainUrl${if (cUrl.startsWith("/")) cUrl else "/$cUrl"}",
                    dateUpload = 0L, // RoyalRoad uses relative dates
                    chapterNumber = extractChapterNumber(title),
                    scanlator = null,
                    sourceOrder = index
                )
            } catch (e: Exception) {
                android.util.Log.e("RoyalRoadProvider", "Index $index: ❌ EXCEPTION: ${e.message}", e)
                errorCount++
                null
            }
        }.also { chapters ->
            android.util.Log.d("RoyalRoadProvider", "=== parseChapterList RESULTS ===")
            android.util.Log.d("RoyalRoadProvider", "Total chapters created: ${chapters.size}")
            android.util.Log.d("RoyalRoadProvider", "Success count: $successCount")
            android.util.Log.d("RoyalRoadProvider", "Skip count: $skipCount")
            android.util.Log.d("RoyalRoadProvider", "Error count: $errorCount")
            android.util.Log.d("RoyalRoadProvider", "=== parseChapterList END ===")
        }
    }
    
    // Override to handle RoyalRoad's description paragraphs
    override fun parseNovelDetails(document: Document, url: String): NovelDetails {
        android.util.Log.d("RoyalRoadProvider", "=== parseNovelDetails START ===")
        android.util.Log.d("RoyalRoadProvider", "URL: $url")
        
        // Log document structure around chapter table
        val chapterTable = document.selectFirst("div.portlet-body > table")
        android.util.Log.d("RoyalRoadProvider", "Chapter table found: ${chapterTable != null}")
        
        if (chapterTable != null) {
            android.util.Log.d("RoyalRoadProvider", "Table HTML (first 500 chars): ${chapterTable.html().take(500)}")
            val tbody = chapterTable.selectFirst("tbody")
            android.util.Log.d("RoyalRoadProvider", "Tbody found: ${tbody != null}")
            if (tbody != null) {
                val rows = tbody.select("tr")
                android.util.Log.d("RoyalRoadProvider", "TR rows in tbody: ${rows.size}")
            }
        } else {
            android.util.Log.d("RoyalRoadProvider", "Looking for any table with chapters...")
            val allTables = document.select("table")
            android.util.Log.d("RoyalRoadProvider", "Total tables found: ${allTables.size}")
        }
        
        val title = document.selectFirst(siteConfig.detailSelectors.title ?: "")?.text()
            ?: throw Exception("Title not found")
        
        val author = document.selectFirst(siteConfig.detailSelectors.author ?: "")?.text()
        val thumbnailUrl = document.selectFirst(siteConfig.detailSelectors.thumbnail ?: "")?.attr("src")
            ?.let { if (it.startsWith("http")) it else "$mainUrl${if (it.startsWith("/")) it else "/$it"}" }
        
        // Get description - handle multiple paragraphs
        val synoDescript = document.select(siteConfig.detailSelectors.description ?: "")
        val synoParts = synoDescript.select("> p")
        val description = if (synoParts.isEmpty() && synoDescript.hasText()) {
            synoDescript.text()
        } else {
            synoParts.joinToString(separator = "\n\n") { it.text() }
        }
        
        // Parse status
        val statusElements = document.select(siteConfig.detailSelectors.status ?: "")
        var status = NovelStatus.UNKNOWN
        for (s in statusElements) {
            if (s.hasText()) {
                status = when (s.text().lowercase()) {
                    "ongoing" -> NovelStatus.ONGOING
                    "completed" -> NovelStatus.COMPLETED
                    "hiatus" -> NovelStatus.HIATUS
                    else -> NovelStatus.UNKNOWN
                }
                if (status != NovelStatus.UNKNOWN) break
            }
        }
        
        // Get tags
        val tags = document.select(siteConfig.detailSelectors.tags ?: "").map { it.text() }
        
        // Get chapters - CALL OVERRIDDEN METHOD, NOT PARENT
        android.util.Log.d("RoyalRoadProvider", "About to call parseChapterList() - overridden version")
        val chapters = parseChapterList(document, url)  // Changed from super.parseChapterList()
        android.util.Log.d("RoyalRoadProvider", "parseChapterList() returned ${chapters.size} chapters")
        
        return NovelDetails(
            title = title,
            url = url,
            author = author,
            description = description,
            thumbnailUrl = thumbnailUrl,
            status = status.value,
            genres = tags, // Use tags as genres (RoyalRoad doesn't separate them)
            tags = tags,
            sourceId = id,
            sourceName = name,
            chapters = chapters
        )
    }
    
    // Override to remove hidden content via CSS styles (QuickNovel's approach)
    override fun parseChapterContent(document: Document): String {
        val styles = document.select("style")
        val hiddenRegex = Regex("^\\s*(\\..*)\\s*\\{", RegexOption.MULTILINE)
        val chap = document.selectFirst(siteConfig.contentSelectors.content ?: "")
        
        // Remove elements hidden by CSS
        styles.forEach { style ->
            hiddenRegex.findAll(style.toString()).forEach {
                val className = it.groupValues[1]
                if (className.isNotEmpty()) {
                    chap?.select(className)?.remove()
                }
            }
        }
        
        // Get HTML content
        var html = chap?.html() ?: ""
        
        // Remove "- break -" sections that appear between scenes
        html = html.replace(Regex("-\\s*break\\s*-", RegexOption.IGNORE_CASE), "")
        
        // Clean up excessive whitespace
        html = html.replace(Regex("\\s{3,}"), "  ")
        
        return html
    }
    
    // Helper method to extract chapter number from title
    private fun extractChapterNumber(title: String): Float {
        val patterns = listOf(
            Regex("""Chapter (\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""Ch\.? (\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:\.\d+)?)[\.\-].*"""),
        )
        
        for (pattern in patterns) {
            val match = pattern.find(title)
            if (match != null) {
                return match.groups[1]?.value?.toFloatOrNull() ?: 0f
            }
        }
        
        return 0f
    }
    
    // Override browse to support filtering with genres, status, etc.
    override suspend fun getBrowseNovels(page: Int, filters: Map<String, String>): List<NovelSearchResult> {
        val urlBuilder = StringBuilder("$mainUrl/fictions/")
        
        // Determine base path from sort (default to best-rated)
        val sortValue = filters["sort"] ?: "popular"
        val basePath = when (sortValue) {
            "popular" -> "best-rated"
            "best-rated" -> "best-rated"
            "last_updated", "latest-updates" -> "latest-updates"
            "newest" -> "newest"
            "rating" -> "best-rated"
            "views", "weekly-popular" -> "weekly-popular"
            "trending" -> "trending"
            else -> "best-rated"
        }
        urlBuilder.append(basePath)
        
        // Build query parameters
        val params = mutableListOf<String>()
        params.add("page=$page")
        
        // Genre filters (tagsAdd=genre1,genre2)
        filters["genres"]?.let { genres ->
            if (genres.isNotBlank()) {
                val genreValues = genres.split(",").map { mapGenreToRoyalRoad(it.trim()) }
                if (genreValues.isNotEmpty()) {
                    params.add("tagsAdd=${genreValues.joinToString(",")}")
                }
            }
        }
        
        // Excluded genres (tagsRemove=genre1,genre2)
        filters["exclude_genres"]?.let { excludeGenres ->
            if (excludeGenres.isNotBlank()) {
                val excludeValues = excludeGenres.split(",").map { mapGenreToRoyalRoad(it.trim()) }
                if (excludeValues.isNotEmpty()) {
                    params.add("tagsRemove=${excludeValues.joinToString(",")}")
                }
            }
        }
        
        // Status filter
        filters["status"]?.let { status ->
            val mappedStatus = when (status.lowercase()) {
                "ongoing" -> "ONGOING"
                "completed" -> "COMPLETED"
                "hiatus" -> "HIATUS"
                "dropped" -> "DROPPED"
                else -> null
            }
            mappedStatus?.let { params.add("status=$it") }
        }
        
        // Min/max chapters
        filters["min_chapters"]?.toIntOrNull()?.let { min ->
            params.add("minPages=$min")
        }
        filters["max_chapters"]?.toIntOrNull()?.let { max ->
            params.add("maxPages=$max")
        }
        
        // Min rating (Royal Road uses 0-50 internally, our UI uses 0-5)
        filters["min_rating_(0-5)"]?.toFloatOrNull()?.let { rating ->
            val ratingInt = (rating * 10).toInt()
            params.add("minRating=$ratingInt")
        }
        
        // Content warnings / violence level
        filters["violence_level"]?.let { violence ->
            when (violence.lowercase()) {
                "none" -> params.add("contentWarning=show_all")
                "graphic", "gore" -> params.add("contentWarning=everything")
            }
        }
        
        // Order (ascending/descending)
        filters["order"]?.let { order ->
            if (order == "asc") {
                params.add("orderBy=asc")
            }
        }
        
        // Build final URL
        if (params.isNotEmpty()) {
            urlBuilder.append("?")
            urlBuilder.append(params.joinToString("&"))
        }
        
        val browseUrl = urlBuilder.toString()
        android.util.Log.d("RoyalRoadProvider", "Fetching filtered browse URL: $browseUrl")
        
        val document = httpClient.getDocument(browseUrl)
        
        // Browse uses slightly different structure than search
        return document.select("div.fiction-list-item").mapNotNull { h ->
            try {
                val head = h.selectFirst("> div") ?: return@mapNotNull null
                val hInfo = head.selectFirst("> h2.fiction-title > a") ?: return@mapNotNull null
                
                val title = hInfo.text()
                val url = hInfo.attr("href")
                    .let { if (it.startsWith("http")) it else "$mainUrl${if (it.startsWith("/")) it else "/$it"}" }
                val posterUrl = h.selectFirst("> figure > a > img")?.attr("src")
                    ?.let { if (it.startsWith("http")) it else "$mainUrl${if (it.startsWith("/")) it else "/$it"}" }
                
                NovelSearchResult(
                    title = title,
                    url = url,
                    thumbnailUrl = posterUrl,
                    author = null,
                    description = null,
                    status = NovelStatus.UNKNOWN.value,
                    sourceId = id,
                    sourceName = name
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Map genre names from filter state to Royal Road's URL format
     */
    private fun mapGenreToRoyalRoad(genre: String): String {
        return when (genre.lowercase().replace(" ", "_").replace("-", "_")) {
            // Primary genres
            "action" -> "action"
            "adventure" -> "adventure"
            "comedy" -> "comedy"
            "contemporary" -> "contemporary"
            "drama" -> "drama"
            "fantasy" -> "fantasy"
            "historical" -> "historical"
            "horror" -> "horror"
            "mystery" -> "mystery"
            "psychological" -> "psychological"
            "romance" -> "romance"
            "satire" -> "satire"
            "sci_fi", "sci-fi" -> "sci_fi"
            "short_story", "one_shot" -> "one_shot"
            "slice_of_life" -> "slice_of_life"
            "supernatural" -> "supernatural"
            "thriller" -> "thriller"
            "tragedy" -> "tragedy"
            // Popular tags
            "anti_hero_lead", "anti-hero_lead" -> "anti-hero_lead"
            "artificial_intelligence" -> "artificial_intelligence"
            "attractive_lead" -> "attractive_lead"
            "cultivation" -> "cultivation"
            "cyberpunk" -> "cyberpunk"
            "dungeon" -> "dungeon"
            "dystopia" -> "dystopia"
            "female_lead" -> "female_lead"
            "first_contact" -> "first_contact"
            "gamelit" -> "gamelit"
            "gender_bender" -> "gender_bender"
            "grimdark" -> "grimdark"
            "hard_sci_fi", "hard_sci-fi" -> "hard_sci-fi"
            "harem" -> "harem"
            "high_fantasy" -> "high_fantasy"
            "isekai" -> "isekai"
            "litrpg" -> "litrpg"
            "low_fantasy" -> "low_fantasy"
            "magic" -> "magic"
            "male_lead" -> "male_lead"
            "martial_arts" -> "martial_arts"
            "multiple_lead" -> "multiple_lead"
            "mythos" -> "mythos"
            "non_human_lead", "non-human_lead" -> "non-human_lead"
            "portal_fantasy_isekai", "portal_fantasy/isekai" -> "portal_fantasy_isekai"
            "post_apocalyptic" -> "post_apocalyptic"
            "progression" -> "progression"
            "reincarnation" -> "reincarnation"
            "ruling_class" -> "ruling_class"
            "school_life" -> "school_life"
            "secret_identity" -> "secret_identity"
            "soft_sci_fi", "soft_sci-fi" -> "soft_sci-fi"
            "space_opera" -> "space_opera"
            "sports" -> "sports"
            "steampunk" -> "steampunk"
            "strategy" -> "strategy"
            "strong_lead" -> "strong_lead"
            "super_heroes" -> "super_heroes"
            "survival" -> "survival"
            "time_loop", "loop" -> "loop"
            "time_travel" -> "time_travel"
            "urban_fantasy" -> "urban_fantasy"
            "villainous_lead" -> "villainous_lead"
            "virtual_reality" -> "virtual_reality"
            "war_and_military" -> "war_and_military"
            "wuxia" -> "wuxia"
            "xianxia" -> "xianxia"
            "young_adult", "reader_young_adult" -> "reader-young_adult"
            else -> genre.lowercase().replace(" ", "_")
        }
    }
    
    override fun getFilterList(): List<NovelFilter> {
        return listOf(
            NovelFilter.Select(
                name = "Sort",
                key = "sort",
                options = listOf(
                    NovelFilter.Option("Best Rated", "best-rated"),
                    NovelFilter.Option("Latest Updates", "latest-updates"),
                    NovelFilter.Option("Trending", "trending"),
                    NovelFilter.Option("New Releases", "new-releases")
                )
            )
        )
    }
}