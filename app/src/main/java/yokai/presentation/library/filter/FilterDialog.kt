package yokai.presentation.library.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import yokai.i18n.MR
import yokai.presentation.extension.stringResource

/**
 * Filter dialog as a draggable bottom sheet - not full screen, can be dragged down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    filterGroups: List<FilterGroup>,
    activeFilters: Map<FilterType, Int>,
    onFilterChange: (FilterType, Int) -> Unit,
    onReorderClick: () -> Unit,
    onDismiss: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Use skipPartiallyExpanded = true to prevent partial expansion on scroll
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null, // Remove drag handle
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp), // Max height 700dp
        ) {
            // Header
            FilterDialogHeader(
                onReorderClick = onReorderClick,
                onCloseClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
            )

            // Filter sections (scrollable)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(filterGroups, key = { it.id }) { group ->
                    FilterSection(
                        group = group,
                        selectedIndex = activeFilters[group.id] ?: 0,
                        onOptionSelected = { index ->
                            onFilterChange(group.id, index)
                        },
                    )
                }
            }

            // Bottom buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Clear filters button
                OutlinedButton(
                    onClick = onClearFilters,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = stringResource(MR.strings.clear_filters),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                // Apply button
                Button(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = stringResource(MR.strings.apply),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * Header with title, reorder button, and close button.
 */
@Composable
private fun FilterDialogHeader(
    onReorderClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(MR.strings.filter),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Reorder filters button
            TextButton(onClick = onReorderClick) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(MR.strings.reorder_filters),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Close button
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(MR.strings.close),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * A filter section with title and radio options.
 */
@Composable
fun FilterSection(
    group: FilterGroup,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section title
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Options
        group.options.forEachIndexed { index, option ->
            FilterOptionRow(
                option = option,
                isSelected = selectedIndex == index,
                onClick = { onOptionSelected(index) },
            )
        }
    }
}

/**
 * Single filter option row with highlighted background for selected items.
 * Uses surfaceVariant for better visibility in dark themes.
 */
@Composable
private fun FilterOptionRow(
    option: FilterOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent,
            )
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
