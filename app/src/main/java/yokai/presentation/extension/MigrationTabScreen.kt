package yokai.presentation.extension

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import yokai.i18n.MR
import yokai.presentation.extension.components.MigrationMangaListItem
import yokai.presentation.extension.components.MigrationSourceListItem
import yokai.presentation.extension.stringResource

/**
 * Compose screen for the Migration tab.
 * 
 * Shows either source list or manga list based on selection state.
 * Supports navigation between source and manga views.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationTabScreen(
    viewModel: MigrationViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val backgroundColor = MaterialTheme.colorScheme.background
    
    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.refresh() },
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        AnimatedContent(
            targetState = state.currentView,
            transitionSpec = {
                if (targetState == MigrationViewType.MangaList) {
                    // Entering manga list: slide in from right
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else {
                    // Returning to source list: slide in from left
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "migration_view_transition",
        ) { viewType ->
            when (viewType) {
                MigrationViewType.SourceList -> {
                    MigrationSourceListContent(
                        state = state,
                        onAction = viewModel::onAction,
                    )
                }
                MigrationViewType.MangaList -> {
                    MigrationMangaListContent(
                        state = state,
                        onAction = viewModel::onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun MigrationSourceListContent(
    state: MigrationUiState,
    onAction: (MigrationAction) -> Unit,
) {
    when {
        state.isLoading && state.sources.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.sources.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(MR.strings.no_sources_to_migrate),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(
                    items = state.sources,
                    key = { it.sourceId },
                ) { source ->
                    MigrationSourceListItem(
                        source = source,
                        onAction = onAction,
                        onClick = { onAction(MigrationAction.SelectSource(source.sourceId)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MigrationMangaListContent(
    state: MigrationUiState,
    onAction: (MigrationAction) -> Unit,
) {
    when {
        state.selectedSourceManga.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(MR.strings.no_manga_in_source),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(
                    items = state.selectedSourceManga,
                    key = { it.mangaId },
                ) { manga ->
                    MigrationMangaListItem(
                        manga = manga,
                        onClick = { onAction(MigrationAction.MigrateManga(manga.mangaId)) },
                    )
                }
            }
        }
    }
}
