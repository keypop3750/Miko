package yokai.presentation.extension.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yokai.i18n.MR
import yokai.presentation.extension.MigrationAction
import yokai.presentation.extension.MigrationSourceUiItem
import yokai.presentation.extension.stringResource

/**
 * Composable for a single migration source item.
 * 
 * Shows source icon, name, manga count, and "Migrate All" button.
 */
@Composable
fun MigrationSourceListItem(
    source: MigrationSourceUiItem,
    onAction: (MigrationAction) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    val secondaryColor = MaterialTheme.colorScheme.secondary
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Source icon
        MigrationSourceIcon(
            iconUrl = source.iconUrl,
            sourceName = source.name,
            modifier = Modifier.size(40.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Source info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Source name
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (source.isUninstalled || source.isObsolete) errorColor else textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                
                // Warning badge
                if (source.isUninstalled || source.isObsolete) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (source.isUninstalled) {
                            stringResource(MR.strings.uninstalled)
                        } else {
                            stringResource(MR.strings.obsolete)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = errorColor,
                    )
                }
            }
            
            // Language and count
            Row {
                val langDisplay = LocaleHelper.getSourceDisplayName(source.lang, context)
                Text(
                    text = langDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                )
                Text(
                    text = " • ${source.mangaCount} ${stringResource(MR.strings.manga)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                )
            }
        }
        
        // Migrate All button
        TextButton(
            onClick = { onAction(MigrationAction.MigrateAll(source.sourceId)) },
        ) {
            Text(
                text = stringResource(MR.strings.all),
                style = MaterialTheme.typography.labelMedium,
                color = secondaryColor,
            )
        }
    }
}

@Composable
fun MigrationSourceIcon(
    iconUrl: String?,
    sourceName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var imageBitmap by remember(iconUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    LaunchedEffect(iconUrl) {
        if (iconUrl != null) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(iconUrl)
                        .build()
                    val result = context.imageLoader.execute(request)
                    imageBitmap = result.image?.toBitmap()?.asImageBitmap()
                } catch (e: Exception) {
                    imageBitmap = null
                }
            }
        }
    }
    
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBitmap != null) {
            Image(
                painter = BitmapPainter(imageBitmap!!),
                contentDescription = sourceName,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Text(
                text = sourceName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
