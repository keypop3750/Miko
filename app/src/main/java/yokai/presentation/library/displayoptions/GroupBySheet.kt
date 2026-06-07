package yokai.presentation.library.displayoptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.LibraryGroup
import kotlinx.coroutines.launch
import yokai.i18n.MR
import yokai.presentation.extension.stringResource

/**
 * Group by options for the library.
 */
data class GroupByItem(
    val id: Int,
    val stringRes: dev.icerock.moko.resources.StringResource,
    val drawableRes: Int,
)

/**
 * Group By bottom sheet for library grouping options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupBySheet(
    currentGroupType: Int,
    isLoggedIntoTracking: Boolean,
    hasCategoriesMoreThanOne: Boolean,
    onGroupBySelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    // Build the list of options - always show all options, some may be conditional
    val groupItems = buildList {
        add(GroupByItem(LibraryGroup.BY_DEFAULT, MR.strings.categories, R.drawable.ic_label_outline_24dp))
        add(GroupByItem(LibraryGroup.BY_TAG, MR.strings.tag, R.drawable.ic_style_24dp))
        add(GroupByItem(LibraryGroup.BY_SOURCE, MR.strings.sources, R.drawable.ic_browse_24dp))
        add(GroupByItem(LibraryGroup.BY_STATUS, MR.strings.status, R.drawable.ic_progress_clock_24dp))
        add(GroupByItem(LibraryGroup.BY_AUTHOR, MR.strings.author, R.drawable.ic_author_24dp))
        add(GroupByItem(LibraryGroup.BY_TRACK_STATUS, MR.strings.tracking_status, R.drawable.ic_sync_24dp))
        add(GroupByItem(LibraryGroup.BY_LANGUAGE, MR.strings.language, R.drawable.ic_translate_24dp))
        add(GroupByItem(LibraryGroup.UNGROUPED, MR.strings.ungrouped, R.drawable.ic_ungroup_24dp))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            // Title
            Text(
                text = stringResource(MR.strings.group_library_by),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Options
            groupItems.forEach { option ->
                val isSelected = option.id == currentGroupType
                GroupByOptionItem(
                    option = option,
                    isSelected = isSelected,
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onGroupBySelected(option.id)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun GroupByOptionItem(
    option: GroupByItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = option.drawableRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = stringResource(option.stringRes),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 14.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
