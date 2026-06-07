package yokai.presentation.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.ui.library.LibraryGroup

/**
 * Hopper hide mode options
 */
enum class HopperHideMode {
    ALWAYS_SHOW,
    HIDE_ON_SCROLL,
    ALWAYS_HIDE
}

/**
 * Hopper long press action options
 */
enum class HopperLongPressAction {
    SEARCH,
    TOGGLE_CATEGORIES,
    DISPLAY_OPTIONS,
    GROUP_OPTIONS,
    RANDOM_CATEGORY,
    RANDOM_GLOBAL
}

/**
 * Hopper horizontal position
 */
enum class HopperGravity {
    LEFT,
    CENTER,
    RIGHT
}

/**
 * Category hopper - floating navigation control for jumping between categories
 * Matches the original Miko/Yokai rounded_category_hopper.xml design
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryHopper(
    categories: List<Category>,
    currentCategoryIndex: Int,
    groupType: Int,
    showItemCounts: Boolean,
    itemCountsPerCategory: Map<Int, Int>,
    isAtTop: Boolean,
    isAtBottom: Boolean,
    isVisible: Boolean,
    gravity: HopperGravity,
    onPreviousCategory: () -> Unit,
    onNextCategory: () -> Unit,
    onCategorySelected: (Category) -> Unit,
    onScrollToTop: () -> Unit,
    onScrollToBottom: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var showCategoryMenu by remember { mutableStateOf(false) }
    
    // Arrow alpha based on position
    val upArrowAlpha by animateFloatAsState(
        targetValue = if (isAtTop) 0.25f else 1f,
        label = "up_arrow_alpha"
    )
    val downArrowAlpha by animateFloatAsState(
        targetValue = if (isAtBottom) 0.25f else 1f,
        label = "down_arrow_alpha"
    )
    
    // Get the appropriate group icon
    val groupIcon = getGroupTypeIcon(groupType)
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier,
    ) {
        Box(
            contentAlignment = when (gravity) {
                HopperGravity.LEFT -> Alignment.CenterStart
                HopperGravity.RIGHT -> Alignment.CenterEnd
                HopperGravity.CENTER -> Alignment.Center
            },
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondary)
                        .padding(horizontal = 2.dp),
                ) {
                    // Up/Previous category button - compact size
                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPreviousCategory()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .alpha(upArrowAlpha)
                            .combinedClickable(
                                onClick = onPreviousCategory,
                                onLongClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onScrollToTop()
                                },
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandLess,
                            contentDescription = "Previous category",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    
                    // Center category button with menu - compact size
                    Box {
                        IconButton(
                            onClick = { showCategoryMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .combinedClickable(
                                    onClick = { showCategoryMenu = true },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onLongPress()
                                    },
                                ),
                        ) {
                            Icon(
                                imageVector = groupIcon,
                                contentDescription = "Categories",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        
                        // Category jump menu
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false },
                        ) {
                            categories.forEachIndexed { index, category ->
                                val itemCount = itemCountsPerCategory[category.id]
                                val isSelected = index == currentCategoryIndex
                                
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = buildString {
                                                append(category.name)
                                                if (showItemCounts && itemCount != null) {
                                                    append(" ($itemCount)")
                                                }
                                            },
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    },
                                    onClick = {
                                        showCategoryMenu = false
                                        onCategorySelected(category)
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Label,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    } else null,
                                )
                            }
                        }
                    }
                    
                    // Down/Next category button - compact size
                    IconButton(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNextCategory()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .alpha(downArrowAlpha)
                            .combinedClickable(
                                onClick = onNextCategory,
                                onLongClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onScrollToBottom()
                                },
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Next category",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Get the appropriate icon for the current group type
 */
@Composable
private fun getGroupTypeIcon(groupType: Int): ImageVector {
    return when (groupType) {
        LibraryGroup.BY_DEFAULT -> Icons.Default.Label      // Categories
        LibraryGroup.BY_TAG -> Icons.Default.Style          // Tags
        LibraryGroup.BY_SOURCE -> Icons.Default.Source      // Sources
        LibraryGroup.BY_STATUS -> Icons.Default.Schedule    // Status
        LibraryGroup.BY_TRACK_STATUS -> Icons.Default.Sync  // Tracking
        LibraryGroup.UNGROUPED -> Icons.Default.ViewList    // Ungrouped
        LibraryGroup.BY_AUTHOR -> Icons.Default.Person      // Author
        LibraryGroup.BY_LANGUAGE -> Icons.Default.Translate // Language
        else -> Icons.Default.Label
    }
}
