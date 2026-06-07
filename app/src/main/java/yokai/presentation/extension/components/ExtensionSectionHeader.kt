package yokai.presentation.extension.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.extension.model.InstalledExtensionsOrder
import yokai.i18n.MR
import yokai.presentation.extension.ExtensionAction
import yokai.presentation.extension.ExtensionSection
import yokai.presentation.extension.stringResource

/**
 * Section header for extension lists.
 * 
 * Shows section title with optional "Update All" button or sort dropdown.
 */
@Composable
fun ExtensionSectionHeader(
    section: ExtensionSection,
    onAction: (ExtensionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Use solid background color for sticky header (not semi-transparent)
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.colorScheme.secondary
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(backgroundColor)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Section title
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Update All button (for updates section)
        if (section.showUpdateAll) {
            TextButton(
                onClick = { onAction(ExtensionAction.UpdateAll) },
            ) {
                Text(
                    text = stringResource(MR.strings.update_all),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryColor,
                )
            }
        }
        
        // Sort dropdown (for installed section)
        if (section.sortingOption != null) {
            SortDropdown(
                currentSort = section.sortingOption,
                onSortSelected = { onAction(ExtensionAction.SetSortOrder(it)) }
            )
        }
    }
}

@Composable
private fun SortDropdown(
    currentSort: Int,
    onSortSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    
    val sortOptions = listOf(
        InstalledExtensionsOrder.Name.ordinal to stringResource(MR.strings.name),
        InstalledExtensionsOrder.RecentlyUpdated.ordinal to stringResource(MR.strings.recently_updated),
        InstalledExtensionsOrder.RecentlyInstalled.ordinal to stringResource(MR.strings.recently_installed),
        InstalledExtensionsOrder.Language.ordinal to stringResource(MR.strings.language),
    )
    
    val currentSortName = sortOptions.find { it.first == currentSort }?.second ?: stringResource(MR.strings.name)
    
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentSortName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sortOptions.forEach { (order, name) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (order == currentSort) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSortSelected(order)
                        expanded = false
                    },
                )
            }
        }
    }
}
