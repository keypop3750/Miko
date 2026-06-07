package eu.kanade.tachiyomi.data.metadata

import yokai.domain.metadata.MetadataSearchResult
import kotlin.math.max

/**
 * Intelligent matching algorithm for manga/manhwa/webtoon titles
 * Handles alternative titles, romanization differences, and fuzzy matching
 * 
 * Uses strict >90% confidence threshold for Swipes metadata enhancement
 */
class MetadataMatcher {
    
    companion object {
        private const val EXACT_MATCH_THRESHOLD = 1.0f
        private const val NORMALIZED_MATCH_THRESHOLD = 0.95f
        private const val ALT_TITLE_MATCH_THRESHOLD = 0.90f
        private const val FUZZY_MATCH_THRESHOLD = 0.85f
        private const val MINIMUM_CONFIDENCE = 0.70f
        
        // Strict threshold for production use
        private const val STRICT_THRESHOLD = 0.90f
    }
    
    /**
     * Calculate confidence score for a metadata match
     * 
     * @param sourceTitle Original title from manga source
     * @param alternativeTitles Alternative titles from source (extracted from description)
     * @param candidateTitle Main title from metadata provider
     * @param candidateAltTitles Alternative titles from metadata provider
     * @return Confidence score 0.0-1.0, or null if below minimum threshold
     */
    fun calculateConfidence(
        sourceTitle: String,
        alternativeTitles: List<String>,
        candidateTitle: String,
        candidateAltTitles: List<String>
    ): Float? {
        
        // 1. Exact match (case-insensitive)
        if (sourceTitle.equals(candidateTitle, ignoreCase = true)) {
            return EXACT_MATCH_THRESHOLD
        }
        
        // 2. Check alternative titles for exact match
        val allSourceTitles = listOf(sourceTitle) + alternativeTitles
        val allCandidateTitles = listOf(candidateTitle) + candidateAltTitles
        
        for (srcTitle in allSourceTitles) {
            for (candTitle in allCandidateTitles) {
                if (srcTitle.equals(candTitle, ignoreCase = true)) {
                    return ALT_TITLE_MATCH_THRESHOLD
                }
            }
        }
        
        // 3. Normalized match (remove special chars, extra spaces)
        val normalizedSource = sourceTitle.normalize()
        val normalizedCandidate = candidateTitle.normalize()
        
        if (normalizedSource.equals(normalizedCandidate, ignoreCase = true)) {
            return NORMALIZED_MATCH_THRESHOLD
        }
        
        // 4. Check normalized alternative titles
        val normalizedSourceTitles = allSourceTitles.map { it.normalize() }
        val normalizedCandidateTitles = allCandidateTitles.map { it.normalize() }
        
        for (srcTitle in normalizedSourceTitles) {
            for (candTitle in normalizedCandidateTitles) {
                if (srcTitle.equals(candTitle, ignoreCase = true)) {
                    return FUZZY_MATCH_THRESHOLD
                }
            }
        }
        
        // 5. Levenshtein distance for fuzzy matching (most computationally expensive)
        val bestSimilarity = findBestSimilarity(normalizedSourceTitles, normalizedCandidateTitles)
        
        return if (bestSimilarity >= MINIMUM_CONFIDENCE) bestSimilarity else null
    }
    
    /**
     * Select best match from multiple candidates
     * Returns null if no match exceeds 90% confidence (strict matching)
     */
    fun selectBestMatch(
        sourceTitle: String,
        alternativeTitles: List<String>,
        candidates: List<MetadataSearchResult>
    ): MetadataSearchResult? {
        
        val scoredCandidates = candidates.mapNotNull { candidate ->
            val confidence = calculateConfidence(
                sourceTitle,
                alternativeTitles,
                candidate.title,
                candidate.alternativeTitles
            )
            
            if (confidence != null && confidence >= STRICT_THRESHOLD) {
                candidate.copy(confidence = confidence) to confidence
            } else {
                null
            }
        }
        
        // Return highest confidence match, or null if none meet threshold
        return scoredCandidates
            .maxByOrNull { it.second }
            ?.first
    }
    
    /**
     * Find best similarity score between two lists of titles
     */
    private fun findBestSimilarity(
        sourceTitles: List<String>,
        candidateTitles: List<String>
    ): Float {
        var bestSimilarity = 0.0f
        
        for (srcTitle in sourceTitles) {
            for (candTitle in candidateTitles) {
                val similarity = calculateSimilarity(srcTitle, candTitle)
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                }
            }
        }
        
        return bestSimilarity
    }
    
    /**
     * Normalize title for comparison
     * - Remove special characters
     * - Collapse whitespace
     * - Lowercase
     * - Remove common suffixes like "(Webtoon)", "[Official]", etc.
     */
    private fun String.normalize(): String {
        return this
            // Remove content in parentheses (often contains format info)
            .replace(Regex("""\(.*?\)"""), "")
            // Remove content in brackets (often contains status/official tags)
            .replace(Regex("""\[.*?\]"""), "")
            // Remove common suffixes
            .replace(Regex("""(?i)\s*(webtoon|manhwa|manhua|manga|official|colored?).*$"""), "")
            // Keep only letters, numbers, and spaces
            .replace(Regex("""[^\p{L}\p{N}\s]"""), "")
            // Collapse multiple spaces
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()
    }
    
    /**
     * Calculate similarity using Levenshtein distance
     * Returns value 0.0-1.0 where 1.0 is identical
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        
        val distance = levenshteinDistance(s1, s2)
        val maxLength = max(s1.length, s2.length)
        
        return if (maxLength == 0) 1.0f else 1.0f - (distance.toFloat() / maxLength)
    }
    
    /**
     * Levenshtein distance implementation
     * Measures minimum number of single-character edits needed to change one string into another
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        // Optimization: if one string is empty, distance is length of other
        if (len1 == 0) return len2
        if (len2 == 0) return len1
        
        // Create distance matrix
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        // Initialize first row and column
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        // Fill matrix
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1].equals(s2[j - 1], ignoreCase = true)) 0 else 1
                
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[len1][len2]
    }
}
