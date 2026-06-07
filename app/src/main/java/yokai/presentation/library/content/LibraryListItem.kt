package yokai.presentation.library.content

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Library list item for displaying manga/novel in list layout mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryListItem(
    item: LibraryContentItem,
    isSelected: Boolean,
    showUnreadBadge: Boolean,
    showDownloadBadge: Boolean,
    showLanguageBadge: Boolean,
    showContinueButton: Boolean,
    unreadBadgeType: Int = UnreadBadgeMode.SHOW_COUNT,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        label = "selection_scale"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .scale(selectionScale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover thumbnail
            Box {
                Card(
                    modifier = Modifier
                        .width(64.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    ListCoverImage(
                        coverUrl = item.thumbnailUrl,
                        title = item.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                    )
                }
                
                // Selection overlay on cover
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Title, subtitle, and badges
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                // Title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                
                // Subtitle (author/artist)
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                
                // Badges row
                Spacer(modifier = Modifier.height(4.dp))
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
            
            // Play button - Match Miko-master's round_play_background.xml styling
            if (showContinueButton && item.unreadCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .alpha(if (isSelected) 0.5f else 1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(color = Color(0xAD212121)) // Semi-transparent dark background
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
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Cover image component for list items using the Coil pattern from the project.
 */
@Composable
private fun ListCoverImage(
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
