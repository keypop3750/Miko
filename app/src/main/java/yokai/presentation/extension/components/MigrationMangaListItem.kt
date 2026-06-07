package yokai.presentation.extension.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yokai.presentation.extension.MigrationMangaUiItem

/**
 * Composable for a single manga item in the migration list.
 * 
 * Shows manga cover, title, and source name.
 */
@Composable
fun MigrationMangaListItem(
    manga: MigrationMangaUiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Manga cover
        MangaCover(
            coverUrl = manga.coverUrl,
            title = manga.title,
            modifier = Modifier
                .height(56.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp)),
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Manga info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            
            Text(
                text = manga.sourceName,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MangaCover(
    coverUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var imageBitmap by remember(coverUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    
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
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                painter = BitmapPainter(imageBitmap!!.asImageBitmap()),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            // Fallback: Show first letter of title
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
