package yokai.presentation.extension

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import yokai.presentation.extension.components.ExtensionListItem
import yokai.presentation.extension.components.ExtensionSectionHeader
import yokai.presentation.extension.stringResource

/**
 * Compose screen for the Extensions tab.
 * 
 * Displays extensions grouped by section (Updates, Installed, Available by language).
 * Supports pull-to-refresh and search filtering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionTabScreen(
    viewModel: ExtensionViewModel,
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
        when {
            state.isLoading && state.sections.isEmpty() -> {
                // Initial loading
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.sections.isEmpty() -> {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(MR.strings.no_results_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
            else -> {
                // Extension list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp), // Bottom sheet peek height
                ) {
                    state.sections.forEach { section ->
                        // Section header (sticky)
                        stickyHeader(key = "header_${section.title}") {
                            ExtensionSectionHeader(
                                section = section,
                                onAction = viewModel::onAction,
                            )
                        }
                        
                        // Extension items
                        items(
                            items = section.extensions,
                            key = { it.pkgName },
                        ) { extension ->
                            ExtensionListItem(
                                extension = extension,
                                onAction = viewModel::onAction,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search bar component for extension filtering.
 */
@Composable
fun ExtensionSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // This will be integrated with the bottom sheet toolbar
    // For now, the search is handled by the parent component
}
