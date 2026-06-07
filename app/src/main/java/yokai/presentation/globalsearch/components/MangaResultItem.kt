package yokai.presentation.globalsearch.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import eu.kanade.tachiyomi.domain.manga.models.Manga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Individual manga card in global search results.
 * 
 * Displays:
 * - Cover image with favorite badge
 * - Title (max 2 lines)
 * 
 * Supports click and long-click actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaResultItem(
    manga: Manga,
    coverUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(100.dp)
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Cover image container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Cover image using Coil pattern from the project
            MangaCoverImage(
                coverUrl = coverUrl,
                title = manga.title,
                modifier = Modifier.fillMaxSize()
            )
            
            // Favorite badge (top-start corner)
            if (manga.favorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = Color.Red,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        
        // Title
        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
    }
}

/**
 * Cover image component using the Coil pattern from the project.
 * Shows a shimmer placeholder while loading, solid dark color if no image.
 */
@Composable
private fun MangaCoverImage(
    coverUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var imageBitmap by remember(coverUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember(coverUrl) { mutableStateOf(true) }
    
    LaunchedEffect(coverUrl) {
        isLoading = true
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
        isLoading = false
    }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            imageBitmap != null -> {
                Image(
                    painter = BitmapPainter(imageBitmap!!.asImageBitmap()),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            isLoading -> {
                // Show shimmer placeholder while loading
                ShimmerPlaceholder(modifier = Modifier.fillMaxSize())
            }
            else -> {
                // Fallback: Solid dark color (no letter)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }
        }
    }
}

/**
 * Simple pulsing placeholder for loading states.
 * Uses alpha animation only for better performance.
 */
@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha = transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha"
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha.value))
    )
}

/**
 * Skeleton manga item for loading state in source rows.
 */
@Composable
fun MangaResultItemSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(100.dp)
    ) {
        // Skeleton cover with curved corners matching manga covers
        ShimmerPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(4.dp))
        )
        
        // Skeleton title line 1
        ShimmerPlaceholder(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(height = 10.dp, width = 80.dp)
                .clip(RoundedCornerShape(2.dp))
        )
        // Skeleton title line 2 
        ShimmerPlaceholder(
            modifier = Modifier
                .padding(top = 2.dp, bottom = 4.dp)
                .size(height = 10.dp, width = 60.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}
