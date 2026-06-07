package yokai.core.di

import android.app.Application
import eu.kanade.tachiyomi.data.novel.download.NovelDownloadCache
import eu.kanade.tachiyomi.data.novel.download.NovelDownloadManager
import eu.kanade.tachiyomi.data.novel.download.NovelDownloadProvider
import org.koin.dsl.module
import yokai.data.novelchapter.NovelChapterRepositoryImpl
import yokai.data.repository.NovelRepositoryImpl
import yokai.domain.novel.NovelRepository
import yokai.domain.novelchapter.NovelChapterRepository
import yokai.domain.novelchapter.interactor.DeleteNovelChapter
import yokai.domain.novelchapter.interactor.GetNovelChapter
import yokai.domain.novelchapter.interactor.InsertNovelChapter
import yokai.domain.novelchapter.interactor.UpdateNovelChapter
import yokai.domain.novel.interactor.GetNovel
import yokai.domain.novel.interactor.InsertNovel
import yokai.domain.novel.interactor.UpdateNovel
import yokai.data.track.novel.NovelTrackRepositoryImpl
import yokai.domain.track.novel.NovelTrackRepository
import yokai.domain.track.novel.interactor.GetNovelTrack
import yokai.domain.track.novel.interactor.InsertNovelTrack
import yokai.domain.track.novel.interactor.DeleteNovelTrack

/**
 * Koin module for novel reading features.
 * Follows QuickNovel pattern: only components that benefit from DI are registered here.
 * NovelProviderRegistry uses static access pattern (no DI needed).
 * 
 * Note: NovelReaderViewModel uses ViewModelProvider.Factory for instantiation,
 * not direct Koin injection, to follow Android ViewModel best practices.
 */
val novelModule = module {
    
    // Novel Repository - needs DatabaseHandler from app module
    single<NovelRepository> { 
        NovelRepositoryImpl(handler = get())
    }
    
    // Novel Chapter Repository - for library metadata (unread counts, total chapters, etc.)
    single<NovelChapterRepository> {
        NovelChapterRepositoryImpl(handler = get())
    }
    
    // Novel Interactors
    single { GetNovel(novelRepository = get()) }
    single { InsertNovel(novelRepository = get()) }
    single { UpdateNovel(novelRepository = get()) }
    
    // Novel Chapter Interactors
    single { GetNovelChapter(chapterRepository = get()) }
    single { InsertNovelChapter(novelRepository = get()) }
    single { UpdateNovelChapter(chapterRepository = get()) }
    single { DeleteNovelChapter(novelChapterRepository = get()) }
    
    // Novel Tracking Repository - for Goodreads/AniList integration
    single<NovelTrackRepository> {
        NovelTrackRepositoryImpl(handler = get())
    }
    
    // Novel Tracking Interactors
    single { GetNovelTrack(novelTrackRepository = get()) }
    single { InsertNovelTrack(novelTrackRepository = get()) }
    single { DeleteNovelTrack(novelTrackRepository = get()) }
    
    // Novel Download System - for offline reading
    single { NovelDownloadProvider(context = get<Application>()) }
    single { NovelDownloadCache(context = get<Application>(), provider = get(), storageManager = get()) }
    single { NovelDownloadManager(context = get<Application>(), provider = get(), cache = get()) }
    
    // Future novel-specific components:
    // - Novel preferences
    // - Novel use cases
    // - Novel-specific services
}