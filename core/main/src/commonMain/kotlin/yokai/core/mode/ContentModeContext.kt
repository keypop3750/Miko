package yokai.core.mode

import android.content.Context
import android.content.Intent

// Abstract interface for mode-specific routing and component creation
interface ContentModeContext {
    // Content navigation
    fun openContent(context: Context, contentId: Long)
    fun openChapter(context: Context, chapterId: Long)
    fun openReader(context: Context, contentId: Long, chapterId: Long? = null)
    
    // Component factories (will be properly typed in later phases)
    fun createLibraryAdapter(): ContentLibraryAdapter
    fun createSourceAdapter(): ContentSourceAdapter
    fun getContentRepository(): ContentRepository
    fun getDetailsController(contentId: Long): BaseController<*>
    
    // Mode-specific operations
    fun getContentType(): yokai.core.content.ContentType
    fun isCompatibleContent(contentId: Long): Boolean
    
    companion object {
        val currentContext: ContentModeContext
            get() = ContentModeResolver.getCurrentContext()
    }
}

// Abstract base classes for type safety (placeholder implementations)
abstract class ContentLibraryAdapter
abstract class ContentSourceAdapter  
abstract class ContentRepository
abstract class BaseController<T>

object MangaModeContext : ContentModeContext {
    override fun openContent(context: Context, contentId: Long) {
        // Route to manga details - will use real MangaDetailsActivity in Phase 4
        val intent = Intent().apply {
            putExtra("content_id", contentId)
            putExtra("content_type", "manga")
        }
        // TODO: Launch MangaDetailsActivity when available
        println("Opening manga content: $contentId")
    }
    
    override fun openChapter(context: Context, chapterId: Long) {
        // Route to manga reader - guaranteed manga-only access
        val intent = Intent().apply {
            putExtra("chapter_id", chapterId)
            putExtra("content_type", "manga")
        }
        // TODO: Launch ReaderActivity when available
        println("Opening manga chapter: $chapterId")
    }
    
    override fun openReader(context: Context, contentId: Long, chapterId: Long?) {
        if (chapterId != null) {
            openChapter(context, chapterId)
        } else {
            // Open first unread chapter
            println("Opening manga reader for content: $contentId")
        }
    }
    
    override fun createLibraryAdapter(): ContentLibraryAdapter {
        return MangaLibraryAdapter() // Guaranteed manga-only adapter
    }
    
    override fun createSourceAdapter(): ContentSourceAdapter {
        return MangaSourceAdapter() // Guaranteed manga-only sources
    }
    
    override fun getContentRepository(): ContentRepository {
        return MangaRepository() // Manga-specific repository
    }
    
    override fun getDetailsController(contentId: Long): BaseController<*> {
        return MangaDetailsController(contentId)
    }
    
    override fun getContentType(): yokai.core.content.ContentType {
        return yokai.core.content.ContentType.MANGA
    }
    
    override fun isCompatibleContent(contentId: Long): Boolean {
        // Check if contentId refers to manga content
        return true // Placeholder - will implement proper validation in Phase 3
    }
}

object NovelModeContext : ContentModeContext {
    override fun openContent(context: Context, contentId: Long) {
        // Route to novel details - will use real NovelDetailsActivity in Phase 4
        val intent = Intent().apply {
            putExtra("content_id", contentId)
            putExtra("content_type", "novel")
        }
        // TODO: Launch NovelDetailsActivity when available
        println("Opening novel content: $contentId")
    }
    
    override fun openChapter(context: Context, chapterId: Long) {
        // Route to novel reader for specific chapter
        openReader(context, -1, chapterId)
    }
    
    override fun openReader(context: Context, contentId: Long, chapterId: Long?) {
        // Route to NovelReaderActivity
        try {
            val activityClass = Class.forName("eu.kanade.tachiyomi.ui.novel.reader.NovelReaderActivity")
            val newIntentMethod = activityClass.getDeclaredMethod("newIntent", Context::class.java, Long::class.java, Long::class.java)
            val intent = newIntentMethod.invoke(null, context, contentId, chapterId) as Intent
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback if reflection fails
            val intent = Intent().apply {
                setClassName(context, "eu.kanade.tachiyomi.ui.novel.reader.NovelReaderActivity")
                putExtra("novel_id", contentId)
                chapterId?.let { putExtra("chapter_id", it) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
    
    override fun createLibraryAdapter(): ContentLibraryAdapter {
        return NovelLibraryAdapter() // Guaranteed novel-only adapter
    }
    
    override fun createSourceAdapter(): ContentSourceAdapter {
        return NovelSourceAdapter() // Guaranteed novel-only sources
    }
    
    override fun getContentRepository(): ContentRepository {
        return NovelRepository() // Novel-specific repository
    }
    
    override fun getDetailsController(contentId: Long): BaseController<*> {
        return NovelDetailsController(contentId)
    }
    
    override fun getContentType(): yokai.core.content.ContentType {
        return yokai.core.content.ContentType.NOVEL
    }
    
    override fun isCompatibleContent(contentId: Long): Boolean {
        // Check if contentId refers to novel content
        return true // Placeholder - will implement proper validation in Phase 3
    }
}

// Context resolver based on current mode
object ContentModeResolver {
    fun getCurrentContext(): ContentModeContext {
        return when (ModeManager.getCurrentMode()) {
            yokai.core.content.ContentType.MANGA -> MangaModeContext
            yokai.core.content.ContentType.NOVEL -> NovelModeContext
        }
    }
    
    fun getContextForType(type: yokai.core.content.ContentType): ContentModeContext {
        return when (type) {
            yokai.core.content.ContentType.MANGA -> MangaModeContext
            yokai.core.content.ContentType.NOVEL -> NovelModeContext
        }
    }
}

// Placeholder implementations for type safety
private class MangaLibraryAdapter : ContentLibraryAdapter()
private class MangaSourceAdapter : ContentSourceAdapter()
private class MangaRepository : ContentRepository()
private class MangaDetailsController(private val contentId: Long) : BaseController<Any>()

private class NovelLibraryAdapter : ContentLibraryAdapter()
private class NovelSourceAdapter : ContentSourceAdapter()
private class NovelRepository : ContentRepository()
private class NovelDetailsController(private val contentId: Long) : BaseController<Any>()