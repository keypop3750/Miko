package yokai.presentation.library.filter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.LibraryGroup
import yokai.i18n.MR
import yokai.presentation.extension.stringResource

/**
 * Group by options matching LibraryGroup constants.
 */
data class GroupByOption(
    val id: Int,
    val stringRes: dev.icerock.moko.resources.StringResource,
    val drawableRes: Int,
)

val groupByOptions = listOf(
    GroupByOption(LibraryGroup.BY_DEFAULT, MR.strings.categories, R.drawable.ic_label_outline_24dp),
    GroupByOption(LibraryGroup.BY_TAG, MR.strings.tag, R.drawable.ic_style_24dp),
    GroupByOption(LibraryGroup.BY_SOURCE, MR.strings.sources, R.drawable.ic_browse_24dp),
    GroupByOption(LibraryGroup.BY_STATUS, MR.strings.status, R.drawable.ic_progress_clock_24dp),
    GroupByOption(LibraryGroup.BY_AUTHOR, MR.strings.author, R.drawable.ic_author_24dp),
    GroupByOption(LibraryGroup.BY_TRACK_STATUS, MR.strings.tracking_status, R.drawable.ic_sync_24dp),
    GroupByOption(LibraryGroup.BY_LANGUAGE, MR.strings.language, R.drawable.ic_translate_24dp),
    GroupByOption(LibraryGroup.UNGROUPED, MR.strings.ungrouped, R.drawable.ic_ungroup_24dp),
)

/**
 * Filter sheet with switchable screens (main filters vs group by selection).
 * Group by slides up/down vertically like a normal menu.
 */
@Composable
fun LibraryFilterSheet(
    state: LibraryFilterState,
    onDisplayOptionsClick: () -> Unit,
    onGroupByClick: () -> Unit = { state.setCurrentScreen(FilterSheetScreen.GROUP_BY) },
    modifier: Modifier = Modifier,
) {
    val activeFilters by state.activeFilters.collectAsState()
    val filterGroups by state.filterGroups.collectAsState()
    val showFilterDialog by state.showFilterDialog.collectAsState()
    val showReorderDialog by state.showReorderDialog.collectAsState()
    val currentScreen by state.currentScreen.collectAsState()
    val groupByType by state.groupByType.collectAsState()
    val filterOrder by state.filterOrder.collectAsState()
    val expansionState by state.expansionState.collectAsState()

    val isGroupByScreen = currentScreen == FilterSheetScreen.GROUP_BY
    val isExpanded = expansionState == FilterSheetExpansion.EXPANDED

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp),
        ) {
            // Main filter content - hide when showing group by
            AnimatedVisibility(
                visible = !isGroupByScreen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                MainFilterContent(
                    filterGroups = filterGroups,
                    filterOrder = filterOrder,
                    activeFilters = activeFilters,
                    groupByType = groupByType,
                    isExpanded = isExpanded,
                    onFilterButtonClick = { state.showFilterDialog(true) },
                    onFilterClick = { type, index -> state.setActiveFilter(type, index) },
                    onGroupByClick = onGroupByClick,
                    onDisplayOptionsClick = onDisplayOptionsClick,
                )
            }

            // Group by content - slides up when shown with click-outside-to-close
            AnimatedVisibility(
                visible = isGroupByScreen,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                GroupByContent(
                    currentGroupType = groupByType,
                    onGroupBySelected = { type ->
                        state.setGroupBy(type)
                        state.setCurrentScreen(FilterSheetScreen.MAIN)
                    },
                    onDismiss = { state.setCurrentScreen(FilterSheetScreen.MAIN) },
                )
            }
        }
    }

    // Dialogs
    if (showFilterDialog) {
        FilterDialog(
            filterGroups = filterGroups,
            activeFilters = activeFilters,
            onFilterChange = { type, index -> state.setActiveFilter(type, index) },
            onReorderClick = {
                state.showFilterDialog(false)
                state.showReorderDialog(true)
            },
            onDismiss = { state.showFilterDialog(false) },
            onClearFilters = { state.clearFilters() },
        )
    }

    if (showReorderDialog) {
        ReorderFiltersDialog(
            currentOrder = filterOrder,
            onReorder = { newOrder -> state.updateFilterOrder(newOrder) },
            onDismiss = { state.showReorderDialog(false) },
        )
    }
}

/**
 * Main filter content with chips and menu buttons.
 */
@Composable
private fun MainFilterContent(
    filterGroups: List<FilterGroup>,
    filterOrder: String,
    activeFilters: Map<FilterType, Int>,
    groupByType: Int,
    isExpanded: Boolean,
    onFilterButtonClick: () -> Unit,
    onFilterClick: (FilterType, Int) -> Unit,
    onGroupByClick: () -> Unit,
    onDisplayOptionsClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Scrollable row with filter button and all filter chips
        QuickFilterRow(
            filterGroups = filterGroups,
            filterOrder = filterOrder,
            activeFilters = activeFilters,
            onFilterButtonClick = onFilterButtonClick,
            onFilterClick = onFilterClick,
        )

        // Only show menu buttons when expanded
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))

                // Group library by button - smaller to match original
                FilterMenuButton(
                    icon = {
                        Icon(
                            painter = painterResource(id = LibraryGroup.groupTypeDrawableRes(groupByType)),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    text = stringResource(MR.strings.group_library_by),
                    onClick = onGroupByClick,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                // Display options button - smaller to match original
                FilterMenuButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    text = stringResource(MR.strings.display_options),
                    onClick = onDisplayOptionsClick,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/**
 * Group by selection content - compact design matching original.
 * Includes click-outside-to-close functionality.
 */
@Composable
private fun GroupByContent(
    currentGroupType: Int,
    onGroupBySelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* Consume clicks on the content itself */ },
            ),
    ) {
        // Clickable header area to dismiss
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Title - smaller
            Text(
                text = stringResource(MR.strings.group_library_by),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }

        // Options - compact list
        groupByOptions.forEach { option ->
            val isSelected = option.id == currentGroupType
            GroupByOptionRow(
                option = option,
                isSelected = isSelected,
                onClick = { onGroupBySelected(option.id) },
            )
        }
        
        // Bottom padding area that can be tapped to dismiss
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
}

/**
 * Single group by option row - compact design.
 */
@Composable
private fun GroupByOptionRow(
    option: GroupByOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon - smaller size
        Icon(
            painter = painterResource(id = option.drawableRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Label - smaller text
        Text(
            text = stringResource(option.stringRes),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * Horizontal scrollable row with filter button and ALL filter chips.
 * Filter types are grouped as paired buttons for related options.
 */
@Composable
private fun QuickFilterRow(
    filterGroups: List<FilterGroup>,
    filterOrder: String,
    activeFilters: Map<FilterType, Int>,
    onFilterButtonClick: () -> Unit,
    onFilterClick: (FilterType, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    
    val orderedGroups = filterOrder.mapNotNull { char ->
        FilterType.fromChar(char)?.let { type ->
            filterGroups.find { it.id == type }
        }
    }.ifEmpty { filterGroups }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Filter button (scrolls with chips, no indicator)
        FilterIconButton(onClick = onFilterButtonClick)

        // All filter chips in order - pair related filters together
        orderedGroups.forEach { group ->
            // All filter types should use paired buttons
            val options = group.options.drop(1) // Drop "All" option
            if (options.isNotEmpty()) {
                PairedFilterButtons(
                    options = options.map { it },
                    selectedIndex = activeFilters[group.id]?.let { it - 1 } ?: -1,
                    onOptionClick = { index ->
                        val actualIndex = index + 1
                        val currentValue = activeFilters[group.id] ?: 0
                        onFilterClick(group.id, if (currentValue == actualIndex) 0 else actualIndex)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = stringResource(MR.strings.filter),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PairedFilterButtons(
    options: List<FilterOption>,
    selectedIndex: Int,
    onOptionClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSelection = selectedIndex >= 0

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = selectedIndex == index
            val isFirst = index == 0
            val isLast = index == options.lastIndex
            
            AnimatedVisibility(
                visible = !hasSelection || isSelected,
                enter = fadeIn(tween(150)) + expandHorizontally(tween(150)),
                exit = fadeOut(tween(150)) + shrinkHorizontally(tween(150)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(
                                topStart = if (isFirst || (hasSelection && isSelected)) 20.dp else 0.dp,
                                bottomStart = if (isFirst || (hasSelection && isSelected)) 20.dp else 0.dp,
                                topEnd = if (isLast || (hasSelection && isSelected)) 20.dp else 0.dp,
                                bottomEnd = if (isLast || (hasSelection && isSelected)) 20.dp else 0.dp,
                            ))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onOptionClick(index) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                    if (!isLast && !hasSelection) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary 
                else MaterialTheme.colorScheme.primary,
                RoundedCornerShape(20.dp),
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                   else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun FilterMenuButton(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
