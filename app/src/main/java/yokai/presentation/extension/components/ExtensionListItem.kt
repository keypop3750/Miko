package yokai.presentation.extension.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yokai.i18n.MR
import yokai.presentation.extension.ExtensionAction
import yokai.presentation.extension.ExtensionUiItem
import yokai.presentation.extension.stringResource

/**
 * Composable for a single extension item.
 * 
 * Displays extension icon, name, version, language, and action button.
 * Handles install/update/uninstall actions and shows progress.
 */
@Composable
fun ExtensionListItem(
    extension: ExtensionUiItem,
    onAction: (ExtensionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val errorColor = MaterialTheme.colorScheme.error
    val secondaryColor = MaterialTheme.colorScheme.secondary
    
    // State for trust confirmation dialog
    var showTrustDialog by remember { mutableStateOf(false) }
    
    // Trust confirmation dialog
    if (showTrustDialog && extension.isUntrusted) {
        TrustExtensionDialog(
            extensionName = extension.name,
            onConfirm = {
                showTrustDialog = false
                onAction(ExtensionAction.Trust(
                    pkgName = extension.pkgName,
                    versionCode = extension.versionCode,
                    signatureHash = extension.signatureHash
                ))
            },
            onDismiss = { showTrustDialog = false },
            onUninstall = {
                showTrustDialog = false
                onAction(ExtensionAction.Uninstall(extension.pkgName))
            },
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(backgroundColor)
            .clickable {
                if (extension.isInstalled && !extension.hasUpdate) {
                    onAction(ExtensionAction.OpenDetails(extension.pkgName))
                } else if (extension.isUntrusted) {
                    showTrustDialog = true
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Extension icon
            ExtensionIcon(
                extension = extension,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                )
                
                // Subtitle row: language, version, warning
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Language
                    val langDisplay = LocaleHelper.getSourceDisplayName(extension.lang, context)
                    Text(
                        text = langDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                    )
                    
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                    )
                    
                    // Version
                    Text(
                        text = extension.versionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                    )
                    
                    // Warning (obsolete/untrusted/nsfw)
                    val warning = when {
                        extension.isObsolete -> stringResource(MR.strings.obsolete)
                        extension.isUntrusted -> stringResource(MR.strings.untrusted)
                        extension.isNsfw -> "18+"
                        else -> null
                    }
                    if (warning != null) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor,
                        )
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (extension.isObsolete || extension.isUntrusted) errorColor else subtitleColor,
                            maxLines = 1,
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Action button and cancel
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExtensionActionButton(
                    extension = extension,
                    onAction = onAction,
                    onTrustClick = { showTrustDialog = true },
                )
                
                // Cancel button (only during install)
                AnimatedVisibility(
                    visible = extension.installStep != null && 
                              extension.installStep != InstallStep.Installed &&
                              extension.installStep != InstallStep.Error,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(
                        onClick = { onAction(ExtensionAction.CancelInstall(extension.pkgName)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = secondaryColor,
                        )
                    }
                }
            }
        }
        
        // Progress indicator at bottom
        AnimatedVisibility(
            visible = extension.installStep != null && 
                      extension.installStep != InstallStep.Installed &&
                      extension.installStep != InstallStep.Error,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val progress = extension.installProgress?.toFloat()?.div(100f)
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(horizontal = 12.dp),
                    color = secondaryColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(horizontal = 12.dp),
                    color = secondaryColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExtensionActionButton(
    extension: ExtensionUiItem,
    onAction: (ExtensionAction) -> Unit,
    onTrustClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonText = when {
        extension.installStep == InstallStep.Downloading -> stringResource(MR.strings.downloading)
        extension.installStep == InstallStep.Installing -> stringResource(MR.strings.installing)
        extension.installStep == InstallStep.Pending -> stringResource(MR.strings.pending)
        extension.installStep == InstallStep.Error -> stringResource(MR.strings.retry)
        extension.isUntrusted -> stringResource(MR.strings.trust)
        extension.hasUpdate -> stringResource(MR.strings.update)
        extension.isInstalled -> stringResource(MR.strings.settings)
        else -> stringResource(MR.strings.install)
    }
    
    val enabled = extension.installStep == null || extension.installStep == InstallStep.Error
    
    OutlinedButton(
        onClick = {
            when {
                extension.installStep == InstallStep.Error -> {
                    if (extension.isInstalled) {
                        onAction(ExtensionAction.Update(extension.pkgName))
                    } else {
                        onAction(ExtensionAction.Install(extension.pkgName))
                    }
                }
                extension.isUntrusted -> {
                    // Show confirmation dialog instead of trusting directly
                    onTrustClick()
                }
                extension.hasUpdate -> onAction(ExtensionAction.Update(extension.pkgName))
                extension.isInstalled -> onAction(ExtensionAction.OpenDetails(extension.pkgName))
                else -> onAction(ExtensionAction.Install(extension.pkgName))
            }
        },
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(
            text = buttonText,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun ExtensionIcon(
    extension: ExtensionUiItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var imageBitmap by remember(extension.pkgName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    LaunchedEffect(extension.pkgName, extension.icon, extension.iconUrl, extension.isUntrusted) {
        withContext(Dispatchers.IO) {
            imageBitmap = when {
                extension.icon != null -> {
                    extension.icon.toBitmap().asImageBitmap()
                }
                extension.iconUrl != null -> {
                    try {
                        val request = ImageRequest.Builder(context)
                            .data(extension.iconUrl)
                            .build()
                        val result = context.imageLoader.execute(request)
                        result.image?.toBitmap()?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }
                extension.isUntrusted -> {
                    // Load icon from package manager for untrusted (installed but not trusted) extensions
                    try {
                        context.packageManager.getApplicationIcon(extension.pkgName).toBitmap().asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }
                else -> null
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
                contentDescription = extension.name,
                modifier = Modifier.size(40.dp)
            )
        } else {
            // Placeholder
            Text(
                text = extension.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Confirmation dialog for trusting an untrusted extension.
 * Shows warning about potential risks and offers Trust/Uninstall options.
 */
@Composable
fun TrustExtensionDialog(
    extensionName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onUninstall: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(MR.strings.untrusted_extension))
        },
        text = {
            Text(text = stringResource(MR.strings.untrusted_extension_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(MR.strings.trust))
            }
        },
        dismissButton = {
            TextButton(onClick = onUninstall) {
                Text(text = stringResource(MR.strings.uninstall))
            }
        },
    )
}
