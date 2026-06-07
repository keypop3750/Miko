package yokai.presentation.library.displayoptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import yokai.i18n.MR
import yokai.presentation.extension.stringResource

/**
 * Display Options bottom sheet with tabs for Display, Badges, and Categories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayOptionsSheet(
    state: DisplayOptionsState,
    onDismiss: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddCategoriesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Tab Row with Display, Badges, Categories + Settings icon
            DisplayOptionsTabRow(
                selectedTabIndex = pagerState.currentPage,
                onTabSelected = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                onSettingsClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        onSettingsClick()
                    }
                },
            )

            // Tab content - fixed height to prevent size changes between tabs
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
            ) { page ->
                when (page) {
                    0 -> DisplayTab(state = state)
                    1 -> BadgesTab(state = state)
                    2 -> CategoriesTab(
                        state = state,
                        onAddCategoriesClick = onAddCategoriesClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayOptionsTabRow(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        stringResource(MR.strings.display),
        stringResource(MR.strings.badges),
        stringResource(MR.strings.categories),
    )

    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
            if (selectedTabIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        color = if (selectedTabIndex == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                },
            )
        }

        // Settings icon as the last "tab"
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(MR.strings.settings),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
