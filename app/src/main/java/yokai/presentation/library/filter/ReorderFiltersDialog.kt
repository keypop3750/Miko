package yokai.presentation.library.filter

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import yokai.i18n.MR
import yokai.presentation.extension.stringResource

// Item height in dp - must match actual item height
private val ITEM_HEIGHT_DP = 48.dp

/**
 * Dialog to reorder filter groups with drag and drop.
 * Uses a continuous drag approach where the item follows the finger smoothly.
 */
@Composable
fun ReorderFiltersDialog(
    currentOrder: String,
    onReorder: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filterTypeOrder = remember(currentOrder) {
        currentOrder.mapNotNull { FilterType.fromChar(it) }
            .ifEmpty { FilterType.entries.toList() }
    }
    
    val localOrder = remember(filterTypeOrder) {
        mutableStateListOf(*filterTypeOrder.toTypedArray())
    }

    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    // Track cumulative drag offset from the original position
    var cumulativeDragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    
    // Convert dp to pixels for calculations
    val density = LocalDensity.current
    val itemHeightPx = with(density) { ITEM_HEIGHT_DP.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(MR.strings.reorder_filters),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 32.dp), // Align with item text (20dp icon + 12dp spacer)
            )
        },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(localOrder, key = { _, item -> item.name }) { index, filterType ->
                    val isDragging = index == draggedItemIndex
                    
                    // Calculate visual offset for the dragged item
                    val visualOffset = if (isDragging) cumulativeDragOffset else 0f
                    
                    DraggableFilterItem(
                        filterType = filterType,
                        isDragging = isDragging,
                        visualOffset = visualOffset,
                        onDragStart = { 
                            draggedItemIndex = index
                            cumulativeDragOffset = 0f 
                        },
                        onDrag = { delta ->
                            if (draggedItemIndex >= 0) {
                                // Add delta to cumulative offset
                                cumulativeDragOffset += delta
                                
                                // Calculate target index based on total offset
                                val positionsToMove = (cumulativeDragOffset / itemHeightPx).toInt()
                                val targetIndex = (draggedItemIndex + positionsToMove).coerceIn(0, localOrder.lastIndex)
                                
                                if (targetIndex != draggedItemIndex) {
                                    // Move item to new position
                                    val item = localOrder.removeAt(draggedItemIndex)
                                    localOrder.add(targetIndex, item)
                                    
                                    // Adjust cumulative offset to account for the move
                                    // Subtract the distance moved so the item stays under the finger
                                    val positionsMoved = targetIndex - draggedItemIndex
                                    cumulativeDragOffset -= positionsMoved * itemHeightPx
                                    
                                    // Update dragged index
                                    draggedItemIndex = targetIndex
                                }
                            }
                        },
                        onDragEnd = {
                            draggedItemIndex = -1
                            cumulativeDragOffset = 0f
                        },
                        modifier = Modifier.zIndex(if (isDragging) 1f else 0f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newOrder = localOrder.map { it.char }.joinToString("")
                    onReorder(newOrder)
                    onDismiss()
                },
            ) {
                Text(
                    text = stringResource(MR.strings.reorder),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(MR.strings.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    )
}

@Composable
private fun DraggableFilterItem(
    filterType: FilterType,
    isDragging: Boolean,
    visualOffset: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = visualOffset
            }
            .shadow(
                elevation = if (isDragging) 8.dp else 0.dp,
                shape = RoundedCornerShape(8.dp),
            )
            .scale(if (isDragging) 1.02f else 1f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                )
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT_DP)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drag handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(MR.strings.reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Filter name
            Text(
                text = stringResource(filterType.stringRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isDragging) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}
