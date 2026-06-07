package yokai.core.content

interface ContentProvider {
    val id: Long
    val name: String
    val lang: String
    val version: String
    
    suspend fun search(query: String): List<ContentItem>
    suspend fun getDetails(url: String): ContentItem
    suspend fun getChapterContent(url: String): String
}