package yokai.presentation.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import yokai.i18n.MR

/**
 * Action mode toolbar shown when items are selected in the library.
 * Provides batch operations for selected manga/novels.
 */
@Composable
fun LibraryActionMode(
    selectionCount: Int,
    hasCategories: Boolean,
    hasNonLocalItems: Boolean,
    isVisible: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onMoveToCategory: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onMarkAsRead: () -> Unit,
    onMarkAsUnread: () -> Unit,
    onMigrate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible && selectionCount > 0,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 4.dp),
        ) {
            // Left side: Close button and selection count
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(MR.strings.close),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "$selectionCount selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            
            // Right side: Action buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Select all
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = stringResource(MR.strings.select_all),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                
                // Move to category (only if multiple categories exist)
                if (hasCategories) {
                    IconButton(onClick = onMoveToCategory) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = stringResource(MR.strings.move_to_categories),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                
                // Share (only for non-local items)
                if (hasNonLocalItems) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(MR.strings.share),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                
                // Delete
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(MR.strings.delete),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                
                // Download unread
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(MR.strings.download_unread),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                
                // Mark as read
                IconButton(onClick = onMarkAsRead) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(MR.strings.mark_as_read),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                
                // Mark as unread
                IconButton(onClick = onMarkAsUnread) {
                    Icon(
                        imageVector = Icons.Default.RemoveDone,
                        contentDescription = stringResource(MR.strings.mark_as_unread),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                
                // Migrate (only for non-local items)
                if (hasNonLocalItems) {
                    IconButton(onClick = onMigrate) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = stringResource(MR.strings.migrate),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Delete confirmation dialog for library items
 */
@Composable
fun LibraryDeleteDialog(
    selectionCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (removeDownloads: Boolean, removeFromLibrary: Boolean) -> Unit,
) {
    var removeDownloads by remember { mutableStateOf(true) }
    var removeFromLibrary by remember { mutableStateOf(true) }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(MR.strings.remove_from_library))
        },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = "Remove $selectionCount item(s)?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                
                // Remove downloads checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = removeDownloads,
                        onCheckedChange = { removeDownloads = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Also delete downloads")
                }
                
                // Remove from library checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = removeFromLibrary,
                        onCheckedChange = { removeFromLibrary = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(MR.strings.remove_from_library))
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(removeDownloads, removeFromLibrary) },
            ) {
                Text(stringResource(MR.strings.remove))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.cancel))
            }
        },
    )
}

/**
 * Category selection dialog for moving items
 */
@Composable
fun MoveToCategoryDialog(
    categories: List<eu.kanade.tachiyomi.data.database.models.Category>,
    onDismiss: () -> Unit,
    onCategorySelected: (eu.kanade.tachiyomi.data.database.models.Category) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(MR.strings.move_to_categories))
        },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                items(categories.size) { index ->
                    val category = categories[index]
                    androidx.compose.material3.TextButton(
                        onClick = { onCategorySelected(category) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = category.name,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.cancel))
            }
        },
    )
}
