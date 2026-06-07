package eu.kanade.tachiyomi.ui.swipes

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.novel.NovelSourceWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Wrapper class to associate a manga with its source ID
 */
data class MangaWithSource(
    val manga: SManga,
    val sourceId: Long
)

/**
 * Repository for fetching manga recommendations from multiple sources for the Swipes feature
 * 
 * Handles:
 * - Multi-source manga fetching with parallel requests
 * - NSFW content filtering
 * - Random selection and shuffling
 * - Error handling and fallback strategies
 * 
 * Phase 3: Basic implementation with all configured sources
 * Phase 7+: ML-based recommendations and preference learning
 */
class SwipesRepository(
    private val sourceManager: SourceManager = Injekt.get()
) {

    /**
     * Fetch manga recommendations from all configured sources
     * 
     * @param count Number of manga to fetch (default: 30 for initial preload)
     * @param excludeNsfw Whether to exclude NSFW content (default: true)
     * @param enabledSourceIds Set of enabled source IDs (null = all sources)
     * @param blacklistedMangaUrls Set of blacklisted manga URLs to exclude
     * @param libraryMangaUrls Set of library manga URLs to exclude
     * @return List of manga with source info (wrapped in MangaWithSource)
     */
    suspend fun fetchRecommendations(
        count: Int = 30,
        excludeNsfw: Boolean = true,
        enabledSourceIds: Set<Long>? = null,
        blacklistedMangaUrls: Set<String> = emptySet(),
        libraryMangaUrls: Set<String> = emptySet()
    ): List<MangaWithSource> = withContext(Dispatchers.IO) {
        Logger.d { "📚 [SWIPES] Fetching $count recommendations (NSFW excluded: $excludeNsfw)" }
        
        // Get all catalogue sources - EXCLUDE NOVEL SOURCES
        val allSources = sourceManager.getCatalogueSources()
            .filterNot { it is NovelSourceWrapper } // Filter out novel sources - manga only!
            .filterIsInstance<eu.kanade.tachiyomi.source.online.HttpSource>() // Only HTTP sources
        Logger.d { "📚 [SWIPES] Found ${allSources.size} HTTP manga sources (novels excluded)" }
        
        // Apply source filtering - ONLY ENGLISH SOURCES
        val sources = if (enabledSourceIds != null && enabledSourceIds.isNotEmpty()) {
            // Use only specified sources (expanded from source groups) - filter to English only
            val enabledSources = allSources.filter { it.id in enabledSourceIds }
            val englishOnly = enabledSources.filter { it.lang == "en" }
            
            Logger.d { "📚 [SWIPES] Using enabled English-only sources: ${englishOnly.size} sources (filtered out ${enabledSources.size - englishOnly.size} non-English)" }
            englishOnly
        } else {
            // Use all sources - English ONLY
            val englishSources = allSources.filter { it.lang == "en" }
            
            Logger.d { "📚 [SWIPES] Using all English sources: ${englishSources.size} sources (filtered out ${allSources.size - englishSources.size} non-English)" }
            englishSources
        }
        
        if (sources.isEmpty()) {
            Logger.w { "📚 [SWIPES] No HTTP manga sources available for fetching recommendations" }
            return@withContext emptyList()
        }

        // SPEED OPTIMIZATION: Only query a subset of sources to match extension-browse speed.
        // Extension browse loads from 1 source; we pick a random 8 to get variety without
        // waiting for 50+ sources to respond.
        val selectedSources = sources.shuffled().take(8)
        Logger.d { "📚 [SWIPES] Querying ${selectedSources.size}/${sources.size} sources for speed" }

        // PERFORMANCE OPTIMIZATION: Limit concurrent source fetches to prevent cache contention
        val maxConcurrentSources = 4
        val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrentSources)

        Logger.d { "📚 [SWIPES] 🚀 Fetching with max $maxConcurrentSources concurrent sources" }

        // Fetch from each source in parallel with timeout AND concurrency control
        val results = selectedSources.map { source ->
            async {
                // Acquire semaphore to limit concurrency
                semaphore.acquire()
                try {
                    // Wrap in timeout to prevent hanging sources from blocking everything
                    withTimeoutOrNull(10.seconds) {  // Reduced from 15s for faster failures
                        try {
                            Logger.d { "📚 [SWIPES] Fetching from source: ${source.name} (${source.id})" }
                            
                            val startTime = System.currentTimeMillis()
                            
                            // Fetch popular manga from page 1
                            val mangasPage = source.getPopularManga(1)
                            
                            val fetchTime = System.currentTimeMillis() - startTime
                            Logger.d { "📚 [SWIPES] ✅ Fetched ${mangasPage.mangas.size} manga from ${source.name} (${fetchTime}ms)" }
                            
                            // Map to MangaWithSource WITHOUT fetching details (too slow/unreliable)
                            // Details can be loaded on-demand when card is displayed if needed
                            mangasPage.mangas.map { manga ->
                                MangaWithSource(manga, source.id)
                            }
                        } catch (e: Exception) {
                            Logger.e(e) { "📚 [SWIPES] ❌ Error fetching from source ${source.name}: ${e.message}" }
                            emptyList()
                        }
                    } ?: run {
                        // Timeout occurred
                        Logger.w { "📚 [SWIPES] ⏱️ Timeout fetching from source ${source.name} after 10 seconds" }
                        emptyList()
                    }
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll()
        
        // Flatten all results and filter
        val allManga = results.flatten()
        Logger.d { "📚 [SWIPES] Total manga fetched across all sources: ${allManga.size}" }
        
        // Step-by-step filtering with logging
        val afterDistinct = allManga.distinctBy { it.manga.url }
        Logger.d { "📚 [SWIPES] After removing duplicates: ${afterDistinct.size}" }
        
        val afterBlacklist = afterDistinct.filterNot { it.manga.url in blacklistedMangaUrls }
        Logger.d { "📚 [SWIPES] After blacklist filter: ${afterBlacklist.size}" }
        
        val afterLibrary = afterBlacklist.filterNot { it.manga.url in libraryMangaUrls }
        Logger.d { "📚 [SWIPES] After library filter: ${afterLibrary.size}" }
        
        val afterNsfw = if (excludeNsfw) {
            val beforeNsfwCount = afterLibrary.size
            val nsfwFiltered = afterLibrary.filter { !isNsfw(it.manga) }
            val removedCount = beforeNsfwCount - nsfwFiltered.size
            Logger.d { "📚 [SWIPES] After NSFW filter: ${nsfwFiltered.size} (removed $removedCount NSFW items)" }
            nsfwFiltered
        } else {
            Logger.d { "📚 [SWIPES] NSFW filter disabled, keeping all ${afterLibrary.size} items" }
            afterLibrary
        }
        
        val filtered = afterNsfw
            .shuffled()
            .take(count)
        
        Logger.d { "📚 [SWIPES] After filtering and shuffling: ${filtered.size} manga" }
        Logger.d { "📚 [SWIPES] Sample titles: ${filtered.take(3).map { it.manga.title }}" }
        
        filtered
    }

    /**
     * Fetch additional manga to add to the queue
     * Used for dynamic queue management during swiping
     * 
     * @param count Number of additional manga to fetch (default: 5)
     * @param currentMangaUrls Set of URLs already in queue to avoid duplicates
     * @param excludeNsfw Whether to exclude NSFW content
     * @param enabledSourceIds Set of enabled source IDs
     * @param blacklistedMangaUrls Set of blacklisted manga URLs
     * @param libraryMangaUrls Set of library manga URLs
     * @return List of new manga to add to queue
     */
    suspend fun fetchMoreRecommendations(
        count: Int = 5,
        currentMangaUrls: Set<String> = emptySet(),
        excludeNsfw: Boolean = true,
        enabledSourceIds: Set<Long>? = null,
        blacklistedMangaUrls: Set<String> = emptySet(),
        libraryMangaUrls: Set<String> = emptySet()
    ): List<MangaWithSource> {
        Logger.d { "📚 [SWIPES] Fetching $count more recommendations (current queue: ${currentMangaUrls.size})" }
        
        // Fetch more than needed to account for filtering
        val fetchCount = count * 3
        val recommendations = fetchRecommendations(
            count = fetchCount,
            excludeNsfw = excludeNsfw,
            enabledSourceIds = enabledSourceIds,
            blacklistedMangaUrls = blacklistedMangaUrls,
            libraryMangaUrls = libraryMangaUrls
        )
        
        // Filter out manga already in queue
        val newManga = recommendations
            .filterNot { it.manga.url in currentMangaUrls }
            .take(count)
        
        Logger.d { "📚 [SWIPES] Found ${newManga.size} new manga to add to queue" }
        
        return newManga
    }

    /**
     * Check if manga is NSFW based on comprehensive tag detection
     * 
     * Uses multi-layered detection:
     * 1. Title keywords (lewd, sexy, hentai, etc.)
     * 2. Explicit genre labels (hentai, adult, etc.)
     * 3. Sexual content tags (threesome, orgy, anal, etc.)
     * 4. Adult themes (incest, rape, etc.)
     * 5. Title patterns (doujinshi artist format [Artist])
     * 
     * Phase 3: Comprehensive tag-based detection
     * Phase 7+: Could add ML-based content rating detection
     * 
     * PUBLIC for use in presenter's post-fetch re-filtering
     */
    fun isNsfwPublic(manga: SManga): Boolean = isNsfw(manga)
    
    private fun isNsfw(manga: SManga): Boolean {
        // 1. Explicit NSFW genre labels
        val nsfwGenres = setOf(
            "hentai",
            "adult",
            "smut",
            "pornographic",
            "erotica",
            "doujinshi",
            "18+",
            "r-18",
            "r18"
        )
        
        // 2. Sexual content tags (explicit acts/themes)
        val sexualContentTags = setOf(
            "threesome",
            "orgy",
            "gangbang",
            "group sex",
            "sex",
            "intercourse",
            "blowjob",
            "fellatio",
            "cunnilingus",
            "anal",
            "bondage",
            "bdsm",
            "tentacles",
            "masturbation",
            "virginity",
            "defloration",
            "impregnation",
            "pregnant",
            "ahegao",
            "nakadashi",
            "creampie",
            "futanari",
            "femdom",
            "netorare",
            "ntr",
            "cheating",
            "affair",
            "solo female",      // Almost exclusively NSFW
            "solo male",        // Almost exclusively NSFW
            "sole female",      // Alternate spelling
            "sole male",        // Alternate spelling
            "yaoi",             // BL/NSFW
            "yuri",             // GL (often NSFW)
            "bikini"            // Often in NSFW doujinshi
        )
        
        // 3. Adult/disturbing themes
        val adultThemeTags = setOf(
            "incest",
            "loli",
            "lolicon",
            "shota",
            "shotacon",
            "rape",
            "non-con",
            "dubcon",
            "forced",
            "prostitution",
            "slavery",
            "sex slave",
            "mind break",
            "drugs",
            "aphrodisiac"
        )
        
        // 4. Mature/ecchi tags (borderline but often NSFW)
        val matureTags = setOf(
            "ecchi",
            "mature",
            "seinen",
            "nudity",
            "sexual violence",
            "sexual content",
            "explicit",
            "lewd",
            "pervert",
            "perverted",
            "harem",
            "reverse harem",
            "panty shot",
            "oppai",
            "breast",
            "boobs"
        )
        
        // Get all tags/genres from manga
        val allTags = (manga.getGenres() ?: emptyList()).map { it.lowercase().trim() }
        val description = manga.description?.lowercase() ?: ""
        val title = manga.title.lowercase()
        
        // Check 0: NSFW keywords in title (catches stuff before we even fetch tags)
        val titleNsfwKeywords = listOf(
            "hentai", "doujin", "lewd", "sex", "porn", "xxx", "adult", "18+",
            "ecchi", "ero", "pervert", "smut", "nsfw", "r18", "r-18",
            "nude", "naked", "erotic", "sexy", "harem", "milf", "slut",
            "breeding", "impregnation", "gangbang", "orgy", "ntr", "netorare",
            "incest", "loli", "shota", "rape", "tentacle", "ahegao",
            "solo female", "solo male", "sole female", "sole male"  // Common NSFW-only tags
        )
        if (titleNsfwKeywords.any { keyword -> title.contains(keyword) }) {
            Logger.d { "📚 [SWIPES] NSFW detected (title keyword): ${manga.title}" }
            return true
        }
        
        // Check 0.5: Doujinshi event code pattern (C94), (C103), etc. - 99% NSFW
        if (title.matches(Regex(".*\\(C\\d{1,3}\\).*", RegexOption.IGNORE_CASE))) {
            Logger.d { "📚 [SWIPES] NSFW detected (doujinshi event code): ${manga.title}" }
            return true
        }
        
        // Check 1: Explicit NSFW genres (immediate fail)
        if (allTags.any { it in nsfwGenres }) {
            Logger.d { "📚 [SWIPES] NSFW detected (explicit genre): ${manga.title}" }
            return true
        }
        
        // Check 2: Sexual content tags (immediate fail)
        if (allTags.any { it in sexualContentTags }) {
            Logger.d { "📚 [SWIPES] NSFW detected (sexual content tag): ${manga.title} - tags: ${allTags.filter { it in sexualContentTags }}" }
            return true
        }
        
        // Check 3: Adult themes (immediate fail)
        if (allTags.any { it in adultThemeTags }) {
            Logger.d { "📚 [SWIPES] NSFW detected (adult theme): ${manga.title} - tags: ${allTags.filter { it in adultThemeTags }}" }
            return true
        }
        
        // Check 4: Multiple mature tags = likely NSFW (2+ mature tags)
        val matureTagCount = allTags.count { it in matureTags }
        if (matureTagCount >= 2) {
            Logger.d { "📚 [SWIPES] NSFW detected (multiple mature tags: $matureTagCount): ${manga.title}" }
            return true
        }
        
        // Check 5: Doujinshi title pattern [Artist Name]
        if (title.matches(Regex("^\\[.+?\\].*"))) {
            Logger.d { "📚 [SWIPES] NSFW detected (doujinshi title pattern): ${manga.title}" }
            return true
        }
        
        // Check 6: Sexual keywords in description
        val sexualKeywords = listOf(
            "sex", "sexual", "pornographic", "explicit", "adult content",
            "mature content", "18+", "r-18", "nsfw"
        )
        if (sexualKeywords.any { keyword -> description.contains(keyword) }) {
            Logger.d { "📚 [SWIPES] NSFW detected (description keyword): ${manga.title}" }
            return true
        }
        
        return false
    }

    /**
     * Get human-readable status text
     */
    fun getStatusText(status: Int): String {
        return when (status) {
            SManga.ONGOING -> "Ongoing"
            SManga.COMPLETED -> "Completed"
            SManga.LICENSED -> "Licensed"
            SManga.PUBLISHING_FINISHED -> "Publishing Finished"
            SManga.CANCELLED -> "Cancelled"
            SManga.ON_HIATUS -> "On Hiatus"
            else -> "Unknown"
        }
    }

    /**
     * Data class representing a logical source group (e.g., "Comick" with multiple languages)
     */
    data class SourceGroup(
        val name: String,
        val sources: List<CatalogueSource>,
        val primarySource: CatalogueSource // Representative source for UI display
    ) {
        val id: Long = primarySource.id
    }
    
    /**
     * Get all available catalogue sources for filter dialog
     * Phase 4.1: Source selection support with logical grouping
     * 
     * Groups sources by name to prevent duplicate entries for different languages.
     * E.g., "Comick (English)", "Comick (Spanish)" -> Single "Comick" entry
     * 
     * When a source group is enabled, ALL language variants are used with fallback.
     * 
     * Filters out:
     * - Novel sources (NovelSourceWrapper)
     * - Non-HTTP sources (LocalSource, etc.)
     */
    fun getAvailableSourceGroups(): List<SourceGroup> {
        val allSources = sourceManager.getCatalogueSources()
        val httpSources = allSources
            .filterNot { it is NovelSourceWrapper } // Exclude novel sources
            .filterIsInstance<eu.kanade.tachiyomi.source.online.HttpSource>() // Only HTTP sources
        
        // Group sources by base name (remove language suffixes and country variants)
        val sourceGroups = httpSources
            .groupBy { source ->
                // Extract base name by removing common language/country patterns
                var baseName = source.name
                    .replace(Regex("""\s*\(.*\)$"""), "") // Remove (Language) suffix
                    .replace(Regex("""\s*-\s*[A-Z]{2}$"""), "") // Remove - EN suffix
                    .replace(Regex("""\s*\[[A-Z]{2}\]$"""), "") // Remove [EN] suffix
                    .replace(Regex("""\s+(Brasil|Brazil|Deutschland|España|France|Italia|Россия|日本|한국|中国)$""", RegexOption.IGNORE_CASE), "") // Remove country names
                    .replace(Regex("""\s+(PT-BR|ES-ES|EN-US|FR-FR|DE-DE|IT-IT|RU-RU|JA-JP|KO-KR|ZH-CN)$""", RegexOption.IGNORE_CASE), "") // Remove locale codes
                    .trim()
                
                // Special case handling for known source variants
                baseName = when (baseName.lowercase()) {
                    "comikey brasil" -> "Comikey"
                    "mangadex" -> "MangaDex"
                    "batoto" -> "Bato.to"
                    else -> baseName
                }
                baseName
            }
            .map { (baseName, sources) ->
                // Sort sources within group: English first, then by language
                val sortedSources = sources.sortedWith(compareBy<CatalogueSource> { 
                    if (it.lang == "en") 0 else 1 
                }.thenBy { it.lang }.thenBy { it.name })
                
                // Use first source (preferably English) as primary
                val primarySource = sortedSources.first()
                
                SourceGroup(
                    name = baseName,
                    sources = sortedSources,
                    primarySource = primarySource
                )
            }
            .sortedBy { it.name }
        
        Logger.d { "📱 [SWIPES] Source grouping: ${httpSources.size} individual -> ${sourceGroups.size} logical groups" }
        sourceGroups.forEach { group ->
            Logger.d { "📱 [SWIPES] Source group '${group.name}': ${group.sources.size} variants (${group.sources.map { "${it.lang}" }.joinToString(", ")})" }
        }
        
        return sourceGroups
    }
    
    /**
     * Get all source IDs for a given set of enabled source group IDs
     * Used to expand logical groups back to individual source IDs for fetching
     */
    fun expandSourceGroups(enabledGroupIds: Set<Long>): Set<Long> {
        if (enabledGroupIds.isEmpty()) {
            // No specific groups enabled = all groups enabled = all sources
            return emptySet() // Signal to use all sources
        }
        
        val sourceGroups = getAvailableSourceGroups()
        val allSourceIds = sourceGroups
            .filter { it.id in enabledGroupIds }
            .flatMap { it.sources.map { source -> source.id } }
            .toSet()
        
        Logger.d { "📱 [SWIPES] Expanded ${enabledGroupIds.size} groups to ${allSourceIds.size} individual sources" }
        
        return allSourceIds
    }
    
    /**
     * DEPRECATED: Use getAvailableSourceGroups() instead
     * Kept for backward compatibility during transition
     */
    @Deprecated("Use getAvailableSourceGroups() for logical source grouping")
    fun getAvailableSources(): List<CatalogueSource> {
        return getAvailableSourceGroups().map { it.primarySource }
    }

    /**
     * Fetch full manga details for a specific manga (on-demand)
     * This is called when a card is displayed to get complete metadata
     * 
     * @param manga The manga to fetch details for (with basic info from getPopularManga)
     * @param sourceId The source ID to fetch from
     * @return SManga with full details (description, author, tags, etc.) or original if fetch fails
     */
    suspend fun fetchMangaDetails(manga: SManga, sourceId: Long): SManga = withContext(Dispatchers.IO) {
        try {
            val source = sourceManager.getCatalogueSources()
                .find { it.id == sourceId }
                
            if (source == null) {
                Logger.w { "📚 [SWIPES] Source $sourceId not found for details fetch" }
                return@withContext manga
            }
            
            Logger.d { "📚 [SWIPES] Fetching details for '${manga.title}' from ${source.name}" }
            
            // Fetch full details with reduced timeout for faster failures
            // Original: 10s, Optimized: 6s (most sources respond in 1-3s, slow ones not worth waiting)
            val detailedManga = withTimeoutOrNull(6.seconds) {
                source.getMangaDetails(manga)
            }
            
            if (detailedManga != null) {
                Logger.d { "📚 [SWIPES] ✅ Successfully fetched details for '${manga.title}'" }
                detailedManga
            } else {
                Logger.w { "📚 [SWIPES] ⏱️ Timeout fetching details for '${manga.title}' after 6s" }
                manga // Return original on timeout
            }
        } catch (e: Exception) {
            Logger.w(e) { "📚 [SWIPES] Error fetching details for '${manga.title}': ${e.message}" }
            manga // Return original on error
        }
    }
    
    /**
     * Fetch chapter list for manga from source
     */
    suspend fun fetchChapterList(mangaUrl: String, sourceId: Long): List<SChapter> = withContext(Dispatchers.IO) {
        try {
            val source = sourceManager.getCatalogueSources()
                .find { it.id == sourceId }
                
            if (source == null) {
                Logger.w { "📚 [SWIPES] Source $sourceId not found for chapter fetch" }
                return@withContext emptyList()
            }
            
            Logger.d { "📚 [SWIPES] Fetching chapters from ${source.name}" }
            
            // Create SManga for chapter fetch
            val manga = SManga.create().apply {
                url = mangaUrl
            }
            
            // Fetch chapters with timeout
            val chapters = withTimeoutOrNull(8.seconds) {
                source.getChapterList(manga)
            }
            
            if (chapters != null) {
                Logger.d { "📚 [SWIPES] ✅ Successfully fetched ${chapters.size} chapters" }
                chapters
            } else {
                Logger.w { "📚 [SWIPES] ⏰ Chapter fetch timed out" }
                emptyList()
            }
        } catch (e: Exception) {
            Logger.w(e) { "📚 [SWIPES] Error fetching chapters: ${e.message}" }
            emptyList()
        }
    }
    
    /**
     * Fetch chapter count for manga from source (lightweight version of fetchChapterList)
     */
    suspend fun fetchChapterCount(mangaUrl: String, sourceId: Long): Int = withContext(Dispatchers.IO) {
        try {
            val chapters = fetchChapterList(mangaUrl, sourceId)
            chapters.size
        } catch (e: Exception) {
            Logger.w(e) { "📚 [SWIPES] Error fetching chapter count: ${e.message}" }
            0
        }
    }
    
    /**
     * Search manga across all sources with a query
     * Similar to fetchRecommendations but uses source search instead of popular manga
     * 
     * @param query Search query string
     * @param count Number of results to fetch (default: 30)
     * @param excludeNsfw Whether to exclude NSFW content
     * @param enabledSourceIds Set of enabled source IDs (null = all sources)
     * @param blacklistedMangaUrls Set of blacklisted manga URLs to exclude
     * @param libraryMangaUrls Set of library manga URLs to exclude
     * @return List of manga matching the search query
     */
    suspend fun searchManga(
        query: String,
        count: Int = 30,
        excludeNsfw: Boolean = true,
        enabledSourceIds: Set<Long>? = null,
        blacklistedMangaUrls: Set<String> = emptySet(),
        libraryMangaUrls: Set<String> = emptySet()
    ): List<MangaWithSource> = withContext(Dispatchers.IO) {
        Logger.d { "🔍 [SWIPES] Searching for '$query' (NSFW excluded: $excludeNsfw)" }
        
        if (query.isBlank()) {
            Logger.d { "🔍 [SWIPES] Empty query, falling back to recommendations" }
            return@withContext fetchRecommendations(
                count, excludeNsfw, enabledSourceIds, blacklistedMangaUrls, libraryMangaUrls
            )
        }
        
        // Get all catalogue sources - EXCLUDE NOVEL SOURCES
        val allSources = sourceManager.getCatalogueSources()
            .filterNot { it is NovelSourceWrapper }
            .filterIsInstance<eu.kanade.tachiyomi.source.online.HttpSource>()
        Logger.d { "🔍 [SWIPES] Found ${allSources.size} HTTP manga sources" }
        
        // Apply source filtering - ONLY ENGLISH SOURCES
        val sources = if (enabledSourceIds != null && enabledSourceIds.isNotEmpty()) {
            val enabledSources = allSources.filter { it.id in enabledSourceIds }
            val englishOnly = enabledSources.filter { it.lang == "en" }
            Logger.d { "🔍 [SWIPES] Using ${englishOnly.size} enabled English sources" }
            englishOnly
        } else {
            val englishSources = allSources.filter { it.lang == "en" }
            Logger.d { "🔍 [SWIPES] Using ${englishSources.size} English sources" }
            englishSources
        }
        
        if (sources.isEmpty()) {
            Logger.w { "🔍 [SWIPES] No HTTP manga sources available for search" }
            return@withContext emptyList()
        }
        
        // Limit concurrent searches
        val maxConcurrentSources = 8
        val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrentSources)
        
        Logger.d { "🔍 [SWIPES] 🚀 Searching with max $maxConcurrentSources concurrent sources" }
        
        // Search each source in parallel with timeout
        val results = sources.map { source ->
            async {
                semaphore.acquire()
                try {
                    withTimeoutOrNull(10.seconds) {
                        try {
                            Logger.d { "🔍 [SWIPES] Searching ${source.name} for '$query'" }
                            val startTime = System.currentTimeMillis()
                            
                            // Use source search with empty filters
                            val mangasPage = source.getSearchManga(1, query, FilterList())
                            
                            val fetchTime = System.currentTimeMillis() - startTime
                            Logger.d { "🔍 [SWIPES] ✅ Found ${mangasPage.mangas.size} results from ${source.name} (${fetchTime}ms)" }
                            
                            mangasPage.mangas.map { manga ->
                                MangaWithSource(manga, source.id)
                            }
                        } catch (e: Exception) {
                            Logger.e(e) { "🔍 [SWIPES] ❌ Error searching ${source.name}: ${e.message}" }
                            emptyList()
                        }
                    } ?: run {
                        Logger.w { "🔍 [SWIPES] ⏱️ Search timeout for ${source.name}" }
                        emptyList()
                    }
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll()
        
        // Flatten and filter results
        val allManga = results.flatten()
        Logger.d { "🔍 [SWIPES] Total search results: ${allManga.size}" }
        
        val afterDistinct = allManga.distinctBy { it.manga.url }
        Logger.d { "🔍 [SWIPES] After removing duplicates: ${afterDistinct.size}" }
        
        val afterBlacklist = afterDistinct.filterNot { it.manga.url in blacklistedMangaUrls }
        Logger.d { "🔍 [SWIPES] After blacklist filter: ${afterBlacklist.size}" }
        
        val afterLibrary = afterBlacklist.filterNot { it.manga.url in libraryMangaUrls }
        Logger.d { "🔍 [SWIPES] After library filter: ${afterLibrary.size}" }
        
        val afterNsfw = if (excludeNsfw) {
            val beforeCount = afterLibrary.size
            val filtered = afterLibrary.filter { !isNsfw(it.manga) }
            val removedCount = beforeCount - filtered.size
            Logger.d { "🔍 [SWIPES] After NSFW filter: ${filtered.size} (removed $removedCount)" }
            filtered
        } else {
            Logger.d { "🔍 [SWIPES] NSFW filter disabled, keeping ${afterLibrary.size} items" }
            afterLibrary
        }
        
        val filtered = afterNsfw
            .shuffled()
            .take(count)
        
        Logger.d { "🔍 [SWIPES] Final search results: ${filtered.size} manga" }
        Logger.d { "🔍 [SWIPES] Sample titles: ${filtered.take(3).map { it.manga.title }}" }
        
        filtered
    }

    /**
     * Create SManga from SwipeCardItem for library addition
     * Reconstructs source manga from card data
     */
    fun createSMangaFromCard(card: SwipeCardItem): SManga {
        return SManga.create().apply {
            url = card.url
            title = card.title
            thumbnail_url = card.coverUrl
            author = card.author.takeIf { it.isNotBlank() }
            description = card.description.takeIf { it.isNotBlank() }
            genre = card.tags.takeIf { it.isNotBlank() }
            // Status will be set as UNKNOWN by default if not specified
        }
    }
}

