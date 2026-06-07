package yokai.presentation.library.displayoptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yokai.i18n.MR
import yokai.presentation.extension.stringResource

/**
 * Badges tab content with unread badge options and checkbox settings.
 */
@Composable
fun BadgesTab(
    state: DisplayOptionsState,
    modifier: Modifier = Modifier,
) {
    val unreadBadgeType by state.unreadBadgeType.collectAsState()
    val hideStartReadingButton by state.hideStartReadingButton.collectAsState()
    val languageBadge by state.languageBadge.collectAsState()
    val downloadBadge by state.downloadBadge.collectAsState()
    val categoryNumberOfItems by state.categoryNumberOfItems.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Unread badge type radio group
        UnreadBadgeRadioGroup(
            selectedType = unreadBadgeType,
            onTypeSelected = { 
                state.setUnreadBadgeType(it)
                state.onUnreadBadgesChanged?.invoke()
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Checkboxes with extra spacing
        LabeledCheckbox(
            checked = hideStartReadingButton,
            onCheckedChange = { state.setHideStartReadingButton(it) },
            label = stringResource(MR.strings.hide_start_reading_button),
        )

        Spacer(modifier = Modifier.height(4.dp))

        LabeledCheckbox(
            checked = languageBadge,
            onCheckedChange = { 
                state.setLanguageBadge(it)
                state.onLanguageBadgesChanged?.invoke()
            },
            label = stringResource(MR.strings.language_badge),
        )

        Spacer(modifier = Modifier.height(4.dp))

        LabeledCheckbox(
            checked = downloadBadge,
            onCheckedChange = { 
                state.setDownloadBadge(it)
                state.onDownloadBadgesChanged?.invoke()
            },
            label = stringResource(MR.strings.download_badge),
        )

        Spacer(modifier = Modifier.height(4.dp))

        LabeledCheckbox(
            checked = categoryNumberOfItems,
            onCheckedChange = { state.setCategoryNumberOfItems(it) },
            label = stringResource(MR.strings.show_number_of_items),
        )
    }
}

@Composable
private fun UnreadBadgeRadioGroup(
    selectedType: Int,
    onTypeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        DisplayOptionsState.UNREAD_BADGE_HIDE to stringResource(MR.strings.hide_unread_badges),
        DisplayOptionsState.UNREAD_BADGE_SHOW to stringResource(MR.strings.show_unread_badges),
        DisplayOptionsState.UNREAD_BADGE_SHOW_COUNT to stringResource(MR.strings.show_unread_count),
    )

    Column(modifier = modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            LabeledRadioButton(
                selected = selectedType == value,
                onClick = { onTypeSelected(value) },
                label = label,
            )
        }
    }
}
