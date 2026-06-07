package yokai.presentation.browse.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.presentation.browse.BrowseSourceItem

/**
 * Composable for displaying a single source item in the browse list.
 * 
 * Uses MaterialTheme.colorScheme for all colors, enabling automatic
 * recomposition when the theme changes (e.g., mode switch).
 * Colors are applied directly without animation for instant theme switching.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourceListItem(
    source: BrowseSourceItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPinClick: () -> Unit,
    onLatestClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Use direct colors - no animation for instant theme switching
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Source icon
        SourceIcon(
            sourceId = source.sourceId,
            iconUrl = source.iconUrl,
            sourceName = source.name,
            isNovelSource = source.isNovelSource,
            modifier = Modifier.size(40.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Source name
        Text(
            text = source.name,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        // Latest button (if supported)
        if (source.supportsLatest) {
            TextButton(onClick = onLatestClick) {
                Text(
                    text = "Latest",
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryColor
                )
            }
        }
        
        // Pin button
        IconButton(onClick = onPinClick) {
            Icon(
                imageVector = if (source.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (source.isPinned) "Unpin" else "Pin",
                tint = if (source.isPinned) secondaryColor else onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceIcon(
    sourceId: Long,
    iconUrl: String?,
    sourceName: String,
    isNovelSource: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sourceManager = remember { Injekt.get<SourceManager>() }
    val novelExtensionManager = remember { Injekt.get<eu.kanade.tachiyomi.extension.novel.NovelExtensionManager>() }
    
    // Try to get cached icon synchronously first to avoid placeholder flash
    // For novel sources, try to get the installed extension's icon drawable
    val initialBitmap = remember(sourceId, isNovelSource) {
        try {
            if (isNovelSource) {
                // For novel sources, get icon from installed extension
                val installedExtensions = novelExtensionManager.installedExtensionsFlow.value
                val extension = installedExtensions.find { ext ->
                    ext.sources.any { it.id == sourceId }
                }
                extension?.icon?.toBitmap()
            } else {
                // For manga sources, get icon from SourceManager
                val source = sourceManager.get(sourceId)
                source?.icon()?.toBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }
    
    var imageBitmap by remember(sourceId) { mutableStateOf(initialBitmap) }
    
    // Load icon asynchronously only if not already loaded from cache
    LaunchedEffect(sourceId, iconUrl) {
        if (imageBitmap != null) return@LaunchedEffect
        
        withContext(Dispatchers.IO) {
            try {
                // For novel sources, first try the extension icon drawable
                if (isNovelSource) {
                    val installedExtensions = novelExtensionManager.installedExtensionsFlow.value
                    val extension = installedExtensions.find { ext ->
                        ext.sources.any { it.id == sourceId }
                    }
                    if (extension?.icon != null) {
                        imageBitmap = extension.icon.toBitmap()
                        return@withContext
                    }
                }
                
                // Then try URL if available
                if (!iconUrl.isNullOrEmpty()) {
                    val request = ImageRequest.Builder(context)
                        .data(iconUrl)
                        .build()
                    val result = context.imageLoader.execute(request)
                    imageBitmap = result.image?.toBitmap()
                } else if (!isNovelSource) {
                    // For manga sources, get icon from SourceManager
                    val source = sourceManager.get(sourceId)
                    val drawable = source?.icon()
                    imageBitmap = drawable?.toBitmap()
                }
            } catch (e: Exception) {
                // Failed to load, will show fallback
            }
        }
    }
    
    if (imageBitmap != null) {
        Image(
            painter = BitmapPainter(imageBitmap!!.asImageBitmap()),
            contentDescription = "$sourceName icon",
            modifier = modifier.clip(CircleShape)
        )
    } else {
        // Fallback: Show first letter of source name
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = sourceName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
