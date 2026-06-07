package yokai.presentation.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Fast scroll component for library grid.
 * Shows a draggable thumb on the right side with a bubble showing the current position.
 */
@Composable
fun BoxScope.LibraryFastScroll(
    gridState: LazyGridState,
    itemCount: Int,
    getBubbleText: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    
    // Track container height for scroll calculations
    var containerHeight by remember { mutableIntStateOf(0) }
    
    // Track if user is currently dragging
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    
    // Show fast scroll after a delay to avoid flickering
    var showFastScroll by remember { mutableStateOf(false) }
    
    // Calculate if scrollbar should be visible (only when there are enough items)
    val shouldShowScrollbar by remember(itemCount) {
        derivedStateOf { itemCount > 20 }
    }
    
    // Calculate current scroll position (0.0 to 1.0)
    val scrollProgress by remember {
        derivedStateOf {
            if (gridState.layoutInfo.totalItemsCount == 0) {
                0f
            } else {
                val firstVisibleItem = gridState.firstVisibleItemIndex
                val totalItems = gridState.layoutInfo.totalItemsCount
                firstVisibleItem.toFloat() / totalItems.coerceAtLeast(1)
            }
        }
    }
    
    // Calculate bubble text for current position
    val bubbleText by remember {
        derivedStateOf {
            if (itemCount > 0) {
                val index = (scrollProgress * itemCount).toInt().coerceIn(0, itemCount - 1)
                getBubbleText(index)
            } else {
                ""
            }
        }
    }
    
    // Auto-hide fast scroll after inactivity
    LaunchedEffect(isDragging, gridState.isScrollInProgress) {
        if (isDragging || gridState.isScrollInProgress) {
            showFastScroll = true
        } else {
            delay(1500)
            showFastScroll = false
        }
    }
    
    // Thumb alpha animation
    val thumbAlpha by animateFloatAsState(
        targetValue = if (showFastScroll || isDragging) 1f else 0f,
        animationSpec = tween(150),
        label = "thumb_alpha"
    )
    
    if (shouldShowScrollbar) {
        Box(
            modifier = modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(24.dp)
                .padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
                .alpha(thumbAlpha)
                .onSizeChanged { containerHeight = it.height }
                .pointerInput(itemCount, containerHeight) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            
                            // Calculate new position
                            val height = containerHeight.toFloat()
                            if (height <= 0) return@detectVerticalDragGestures
                            dragOffset = (dragOffset + dragAmount).coerceIn(0f, height)
                            
                            // Calculate target item index
                            val progress = dragOffset / height
                            val targetIndex = (progress * itemCount).toInt().coerceIn(0, itemCount - 1)
                            
                            // Scroll to target
                            coroutineScope.launch {
                                gridState.scrollToItem(targetIndex)
                            }
                        }
                    )
                },
        ) {
            // Track background
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    ),
            )
            
            // Draggable thumb
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val maxOffset = (containerHeight * 0.9f).roundToInt()
                        val offset = if (isDragging) {
                            dragOffset.roundToInt()
                        } else {
                            (scrollProgress * maxOffset).roundToInt()
                        }
                        IntOffset(0, offset)
                    }
                    .width(16.dp)
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                )
            }
            
            // Bubble showing current position
            AnimatedVisibility(
                visible = isDragging && bubbleText.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-40).dp)
                    .offset {
                        val maxOffset = (containerHeight * 0.9f).roundToInt()
                        val offset = (scrollProgress * maxOffset).roundToInt()
                        IntOffset(0, offset)
                    },
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = bubbleText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Fast scroll component for lazy list (alternative for list layouts)
 */
@Composable
fun BoxScope.LibraryFastScrollList(
    listState: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int,
    getBubbleText: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    
    // Track container height for scroll calculations
    var containerHeight by remember { mutableIntStateOf(0) }
    
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var showFastScroll by remember { mutableStateOf(false) }
    
    val shouldShowScrollbar by remember(itemCount) {
        derivedStateOf { itemCount > 10 }
    }
    
    val scrollProgress by remember {
        derivedStateOf {
            if (listState.layoutInfo.totalItemsCount == 0) {
                0f
            } else {
                val firstVisibleItem = listState.firstVisibleItemIndex
                val totalItems = listState.layoutInfo.totalItemsCount
                firstVisibleItem.toFloat() / totalItems.coerceAtLeast(1)
            }
        }
    }
    
    val bubbleText by remember {
        derivedStateOf {
            if (itemCount > 0) {
                val index = (scrollProgress * itemCount).toInt().coerceIn(0, itemCount - 1)
                getBubbleText(index)
            } else {
                ""
            }
        }
    }
    
    LaunchedEffect(isDragging, listState.isScrollInProgress) {
        if (isDragging || listState.isScrollInProgress) {
            showFastScroll = true
        } else {
            delay(1500)
            showFastScroll = false
        }
    }
    
    val thumbAlpha by animateFloatAsState(
        targetValue = if (showFastScroll || isDragging) 1f else 0f,
        animationSpec = tween(150),
        label = "thumb_alpha"
    )
    
    if (shouldShowScrollbar) {
        Box(
            modifier = modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(24.dp)
                .padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
                .alpha(thumbAlpha)
                .onSizeChanged { containerHeight = it.height }
                .pointerInput(itemCount, containerHeight) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            
                            val height = containerHeight.toFloat()
                            if (height <= 0) return@detectVerticalDragGestures
                            dragOffset = (dragOffset + dragAmount).coerceIn(0f, height)
                            
                            val progress = dragOffset / height
                            val targetIndex = (progress * itemCount).toInt().coerceIn(0, itemCount - 1)
                            
                            coroutineScope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    )
                },
        ) {
            // Track
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
            )
            
            // Thumb
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val maxOffset = (containerHeight * 0.9f).roundToInt()
                        val offset = if (isDragging) {
                            dragOffset.roundToInt()
                        } else {
                            (scrollProgress * maxOffset).roundToInt()
                        }
                        IntOffset(0, offset)
                    }
                    .width(16.dp)
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                )
            }
            
            // Bubble
            AnimatedVisibility(
                visible = isDragging && bubbleText.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-40).dp)
                    .offset {
                        val maxOffset = (containerHeight * 0.9f).roundToInt()
                        val offset = (scrollProgress * maxOffset).roundToInt()
                        IntOffset(0, offset)
                    },
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = bubbleText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
