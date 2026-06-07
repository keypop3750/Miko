package yokai.presentation.library.content

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Library grid item for displaying manga/novel covers in grid layouts.
 * Supports compact, comfortable, and cover-only modes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridItem(
    item: LibraryContentItem,
    layoutMode: LibraryLayoutMode,
    isSelected: Boolean,
    showUnreadBadge: Boolean,
    showDownloadBadge: Boolean,
    showLanguageBadge: Boolean,
    showContinueButton: Boolean,
    showOutline: Boolean = false,
    unreadBadgeType: Int = UnreadBadgeMode.SHOW_COUNT,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isCompact = layoutMode == LibraryLayoutMode.COMPACT_GRID
    val isCoverOnly = layoutMode == LibraryLayoutMode.COVER_ONLY_GRID
    val isComfortable = layoutMode == LibraryLayoutMode.COMFORTABLE_GRID
    
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.95f else 1f,
        label = "selection_scale"
    )
    
    // Border for outline mode
    val borderModifier = if (showOutline) {
        Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        )
    } else {
        Modifier
    }
    
    Column(
        modifier = modifier
            .scale(selectionScale)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        // Cover card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(borderModifier),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Cover image using project's Coil pattern
                CoverImage(
                    coverUrl = item.thumbnailUrl,
                    title = item.title,
                    modifier = Modifier.fillMaxSize(),
                )
                
                // Gradient overlay for compact mode title
                if (isCompact) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.8f),
                                    ),
                                ),
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp,
                        )
                    }
                }
                
                // Cover-only mode: NO title by default (title removed per spec)
                // In the original Miko, cover-only shows title behind the image
                
                // Badges - positioned at TOP-LEFT for all grid items (matching original Miko design)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                ) {
                    LibraryBadges(
                        unreadCount = item.unreadCount,
                        downloadCount = item.downloadCount,
                        language = item.language,
                        showUnreadBadge = showUnreadBadge,
                        showDownloadBadge = showDownloadBadge,
                        showLanguageBadge = showLanguageBadge,
                        unreadBadgeType = unreadBadgeType,
                    )
                }
                
                // Selection indicator
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.scale(1.5f),
                        )
                    }
                }
                
                // Play/Continue reading button
                // - Top-right for compact mode (badges at top-left)
                // - Bottom-right for comfortable and cover-only modes
                if (showContinueButton && item.unreadCount > 0) {
                    // Match Miko-master's round_play_background.xml styling
                    val buttonAlignment = if (isCompact) Alignment.TopEnd else Alignment.BottomEnd
                    Box(
                        modifier = Modifier
                            .align(buttonAlignment)
                            .padding(6.dp)
                            .size(30.dp)
                            .alpha(if (isSelected) 0.5f else 1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                color = Color(0xAD212121) // Semi-transparent dark background
                            )
                            .combinedClickable(
                                onClick = onPlayClick,
                                onLongClick = onLongClick,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Continue reading",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        
        // Title and subtitle for comfortable grid mode (below the card)
        if (isComfortable) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                )
                
                val subtitle = when (item) {
                    is LibraryContentItem.MangaItem -> {
                        listOfNotNull(item.author, item.artist)
                            .distinct()
                            .joinToString(", ")
                            .takeIf { it.isNotBlank() }
                    }
                    is LibraryContentItem.NovelItem -> item.author
                    is LibraryContentItem.Placeholder -> null
                }
                
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Cover image component using the Coil pattern from the project.
 */
@Composable
private fun CoverImage(
    coverUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var imageBitmap by remember(coverUrl) { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(coverUrl) {
        if (!coverUrl.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(coverUrl)
                        .build()
                    val result = context.imageLoader.execute(request)
                    imageBitmap = result.image?.toBitmap()
                } catch (e: Exception) {
                    imageBitmap = null
                }
            }
        }
    }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        imageBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
