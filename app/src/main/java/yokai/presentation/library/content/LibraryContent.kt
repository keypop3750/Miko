package yokai.presentation.library.content

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.util.compose.textHint
import yokai.core.content.ContentType
import yokai.core.mode.ModeManager

/**
 * Layout mode for library content display
 */
enum class LibraryLayoutMode {
    LIST,
    COMPACT_GRID,
    COMFORTABLE_GRID,
    COVER_ONLY_GRID
}

/**
 * Main library content composable that renders library items
 * based on the current layout mode and category settings.
 * 
 * The content is wrapped in a Box with the theme's background color
 * to ensure proper theme switching when mode changes between manga/novel.
 */
@Composable
fun LibraryContent(
    state: LibraryContentUiState,
    layoutMode: LibraryLayoutMode,
    gridColumns: Int,
    showCategoryHeaders: Boolean,
    expandedCategories: Set<Int>,
    selectedItems: Set<Long>,
    showUnreadBadge: Boolean,
    showDownloadBadge: Boolean,
    showLanguageBadge: Boolean,
    showContinueButton: Boolean,
    showOutline: Boolean = false,
    unreadBadgeType: Int = UnreadBadgeMode.SHOW_COUNT,
    onItemClick: (LibraryContentItem) -> Unit,
    onItemLongClick: (LibraryContentItem) -> Unit,
    onContinueClick: (LibraryContentItem) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onCategoryExpandClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    hasActiveFilters: Boolean = false,
    // Single category mode
    currentCategoryIndex: Int = 0,
    currentCategoryId: Int? = null,
    showNumberOfItems: Boolean = false,
    onCategorySwipe: ((Int) -> Unit)? = null,
) {
    // Wrap in Box with theme background color to ensure proper mode-switching
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val innerModifier = Modifier
        when (state) {
            is LibraryContentUiState.Loading -> {
                ShimmerLoadingContent(
                    layoutMode = layoutMode,
                    gridColumns = gridColumns,
                    modifier = innerModifier,
                    contentPadding = contentPadding,
                )
            }
            is LibraryContentUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    modifier = innerModifier,
                )
            }
            is LibraryContentUiState.Success -> {
                if (state.isEmpty) {
                    EmptyLibraryContent(
                        hasActiveFilters = hasActiveFilters,
                        modifier = innerModifier,
                    )
                } else {
                    SuccessContent(
                        categories = state.categories,
                        layoutMode = layoutMode,
                        gridColumns = gridColumns,
                        showCategoryHeaders = showCategoryHeaders,
                        expandedCategories = expandedCategories,
                        selectedItems = selectedItems,
                        showUnreadBadge = showUnreadBadge,
                        showDownloadBadge = showDownloadBadge,
                        showLanguageBadge = showLanguageBadge,
                        showContinueButton = showContinueButton,
                        showOutline = showOutline,
                        unreadBadgeType = unreadBadgeType,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        onContinueClick = onContinueClick,
                        onCategoryClick = onCategoryClick,
                        onCategoryExpandClick = onCategoryExpandClick,
                        modifier = innerModifier,
                        contentPadding = contentPadding,
                        currentCategoryIndex = currentCategoryIndex,
                        currentCategoryId = currentCategoryId,
                        showNumberOfItems = showNumberOfItems,
                        onCategorySwipe = onCategorySwipe,
                    )
                }
            }
        }
    }
}

/**
 * Shimmer loading effect for library content
 */
@Composable
private fun ShimmerLoadingContent(
    layoutMode: LibraryLayoutMode,
    gridColumns: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnimation - 200f, 0f),
        end = Offset(translateAnimation, 0f),
    )
    
    if (layoutMode == LibraryLayoutMode.LIST) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            items(10) {
                ShimmerListItem(brush = brush)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(12) {
                ShimmerGridItem(brush = brush)
            }
        }
    }
}

@Composable
private fun ShimmerGridItem(brush: Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(brush),
    )
}

@Composable
private fun ShimmerListItem(brush: Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(brush),
    )
}

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuccessContent(
    categories: List<LibraryCategoryContent>,
    layoutMode: LibraryLayoutMode,
    gridColumns: Int,
    showCategoryHeaders: Boolean,
    expandedCategories: Set<Int>,
    selectedItems: Set<Long>,
    showUnreadBadge: Boolean,
    showDownloadBadge: Boolean,
    showLanguageBadge: Boolean,
    showContinueButton: Boolean,
    showOutline: Boolean,
    unreadBadgeType: Int,
    onItemClick: (LibraryContentItem) -> Unit,
    onItemLongClick: (LibraryContentItem) -> Unit,
    onContinueClick: (LibraryContentItem) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onCategoryExpandClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    currentCategoryIndex: Int = 0,
    currentCategoryId: Int? = null,
    showNumberOfItems: Boolean = false,
    onCategorySwipe: ((Int) -> Unit)? = null,
) {
    // Use Crossfade for smooth transitions between list and grid modes
    Crossfade(
        targetState = layoutMode,
        animationSpec = tween(durationMillis = 200),
        label = "layout_mode_transition",
    ) { currentLayoutMode ->
        if (currentLayoutMode == LibraryLayoutMode.LIST) {
            LibraryListContent(
                categories = categories,
                showCategoryHeaders = showCategoryHeaders,
                expandedCategories = expandedCategories,
                selectedItems = selectedItems,
                showUnreadBadge = showUnreadBadge,
                showDownloadBadge = showDownloadBadge,
                showLanguageBadge = showLanguageBadge,
                showContinueButton = showContinueButton,
                unreadBadgeType = unreadBadgeType,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                onContinueClick = onContinueClick,
                onCategoryClick = onCategoryClick,
                onCategoryExpandClick = onCategoryExpandClick,
                modifier = modifier,
                contentPadding = contentPadding,
            )
        } else {
            LibraryGridContent(
                categories = categories,
                layoutMode = currentLayoutMode,
                gridColumns = gridColumns,
                showCategoryHeaders = showCategoryHeaders,
                expandedCategories = expandedCategories,
                selectedItems = selectedItems,
                showUnreadBadge = showUnreadBadge,
                showDownloadBadge = showDownloadBadge,
                showLanguageBadge = showLanguageBadge,
                showContinueButton = showContinueButton,
                showOutline = showOutline,
                unreadBadgeType = unreadBadgeType,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                onContinueClick = onContinueClick,
                onCategoryClick = onCategoryClick,
                onCategoryExpandClick = onCategoryExpandClick,
                modifier = modifier,
                contentPadding = contentPadding,
                currentCategoryIndex = currentCategoryIndex,
                currentCategoryId = currentCategoryId,
                showNumberOfItems = showNumberOfItems,
                onCategorySwipe = onCategorySwipe,
            )
        }
    }
}

@Composable
private fun LibraryListContent(
    categories: List<LibraryCategoryContent>,
    showCategoryHeaders: Boolean,
    expandedCategories: Set<Int>,
    selectedItems: Set<Long>,
    showUnreadBadge: Boolean,
    showDownloadBadge: Boolean,
    showLanguageBadge: Boolean,
    showContinueButton: Boolean,
    unreadBadgeType: Int,
    onItemClick: (LibraryContentItem) -> Unit,
    onItemLongClick: (LibraryContentItem) -> Unit,
    onContinueClick: (LibraryContentItem) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onCategoryExpandClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        categories.forEach { categoryContent ->
            val isExpanded = expandedCategories.contains(categoryContent.category.id)
            
            if (showCategoryHeaders) {
                item(
                    key = "header_${categoryContent.category.id}",
                ) {
                    LibraryCategoryHeader(
                        category = categoryContent.category,
                        itemCount = categoryContent.items.size,
                        isExpanded = isExpanded,
                        showItemCount = true,
                        onClick = { onCategoryClick(categoryContent.category) },
                        onExpandClick = { onCategoryExpandClick(categoryContent.category) },
                    )
                }
            }
            
            if (!showCategoryHeaders || isExpanded) {
                items(
                    items = categoryContent.items,
                    // Include category ID in key to prevent duplicates when same item appears in multiple categories (e.g., grouping by tag)
                    key = { item -> "${categoryContent.category.id}_${item.uniqueKey}" },
                ) { item ->
                    LibraryListItem(
                        item = item,
                        isSelected = selectedItems.contains(item.id),
                        showUnreadBadge = showUnreadBadge,
                        showDownloadBadge = showDownloadBadge,
                        showLanguageBadge = showLanguageBadge,
                        showContinueButton = showContinueButton,
                        unreadBadgeType = unreadBadgeType,
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) },
                        onPlayClick = { onContinueClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryGridContent(
    categories: List<LibraryCategoryContent>,
    layoutMode: LibraryLayoutMode,
    gridColumns: Int,
    showCategoryHeaders: Boolean,
    expandedCategories: Set<Int>,
    selectedItems: Set<Long>,
    showUnreadBadge: Boolean,
    showDownloadBadge: Boolean,
    showLanguageBadge: Boolean,
    showContinueButton: Boolean,
    showOutline: Boolean,
    unreadBadgeType: Int,
    onItemClick: (LibraryContentItem) -> Unit,
    onItemLongClick: (LibraryContentItem) -> Unit,
    onContinueClick: (LibraryContentItem) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onCategoryExpandClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    currentCategoryIndex: Int = 0,
    currentCategoryId: Int? = null,
    showNumberOfItems: Boolean = false,
    onCategorySwipe: ((Int) -> Unit)? = null,
) {
    val itemSpacing = when (layoutMode) {
        LibraryLayoutMode.COMPACT_GRID -> 4.dp
        LibraryLayoutMode.COMFORTABLE_GRID -> 8.dp
        LibraryLayoutMode.COVER_ONLY_GRID -> 2.dp
        else -> 4.dp
    }
    
    // In single category mode (showCategoryHeaders = false), find category by ID
    val currentCategory = if (!showCategoryHeaders && categories.isNotEmpty()) {
        // First try to find by ID, then fall back to index
        currentCategoryId?.let { id -> 
            categories.find { it.category.id == id }
        } ?: categories.getOrNull(currentCategoryIndex)
    } else null
    
    // In single category mode, only show the current category's items
    val categoriesToShow = if (showCategoryHeaders) {
        categories
    } else {
        currentCategory?.let { listOf(it) } ?: emptyList()
    }
    
    // Use AnimatedContent for smooth category switching with fade animation
    // This creates the "dragon/overextend" release effect where items fade out and new ones fade in
    AnimatedContent(
        targetState = currentCategoryId,
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
        },
        label = "category_switch_animation",
        modifier = modifier.fillMaxSize(),
    ) { animatedCategoryId ->
        // Re-calculate category for this animation state
        val animatedCurrentCategory = if (!showCategoryHeaders && categories.isNotEmpty()) {
            animatedCategoryId?.let { id -> 
                categories.find { it.category.id == id }
            } ?: categories.getOrNull(currentCategoryIndex)
        } else null
        
        val animatedCategoriesToShow = if (showCategoryHeaders) {
            categories
        } else {
            animatedCurrentCategory?.let { listOf(it) } ?: emptyList()
        }
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            // Show single category title header when not showing all categories
            if (!showCategoryHeaders && animatedCurrentCategory != null) {
                item(
                    key = "single_category_header",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    SingleCategoryHeader(
                        categoryName = animatedCurrentCategory.category.name,
                        itemCount = animatedCurrentCategory.items.size,
                        showItemCount = showNumberOfItems,
                    )
                }
            }
            
            animatedCategoriesToShow.forEach { categoryContent ->
                val isExpanded = expandedCategories.contains(categoryContent.category.id)
                
                if (showCategoryHeaders) {
                    item(
                        key = "header_${categoryContent.category.id}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        LibraryCategoryHeader(
                            category = categoryContent.category,
                            itemCount = categoryContent.items.size,
                            isExpanded = isExpanded,
                            showItemCount = true,
                            onClick = { onCategoryClick(categoryContent.category) },
                            onExpandClick = { onCategoryExpandClick(categoryContent.category) },
                        )
                    }
                }
                
                if (!showCategoryHeaders || isExpanded) {
                    items(
                        items = categoryContent.items,
                        // Include category ID in key to prevent duplicates when same item appears in multiple categories (e.g., grouping by tag)
                        key = { item -> "${categoryContent.category.id}_${item.uniqueKey}" },
                    ) { item ->
                        LibraryGridItem(
                            item = item,
                            layoutMode = layoutMode,
                            isSelected = selectedItems.contains(item.id),
                            showUnreadBadge = showUnreadBadge,
                            showDownloadBadge = showDownloadBadge,
                            showLanguageBadge = showLanguageBadge,
                            showContinueButton = showContinueButton,
                            showOutline = showOutline,
                            unreadBadgeType = unreadBadgeType,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) },
                            onPlayClick = { onContinueClick(item) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty library state matching the View-based EmptyView design.
 * Shows HeartBroken icon and a single message with the mode name highlighted.
 */
@Composable
fun EmptyLibraryContent(
    hasActiveFilters: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val libraryType = if (ModeManager.currentMode.value == ContentType.NOVEL) "Novel" else "Manga"

        Image(
            imageVector = if (hasActiveFilters) Icons.Outlined.FilterAlt else Icons.Filled.HeartBroken,
            contentDescription = null,
            modifier = Modifier.size(128.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.textHint),
        )

        Text(
            text = if (hasActiveFilters) {
                AnnotatedString("No matches for filters")
            } else {
                buildAnnotatedString {
                    val fullMessage = "Your ${libraryType} library is empty, add series to your library from the browse tab."
                    val idx = fullMessage.indexOf(libraryType)
                    if (idx >= 0) {
                        append(fullMessage.substring(0, idx))
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append(libraryType)
                        }
                        append(fullMessage.substring(idx + libraryType.length))
                    } else {
                        append(fullMessage)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 64.dp, vertical = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.textHint,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Header shown in single category mode (when "show all categories" is off).
 * Displays the current category name and optionally the item count.
 */
@Composable
private fun SingleCategoryHeader(
    categoryName: String,
    itemCount: Int,
    showItemCount: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (showItemCount) {
                "$categoryName ($itemCount)"
            } else {
                categoryName
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}


