package yokai.presentation.library.displayoptions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yokai.i18n.MR
import yokai.presentation.extension.stringResource

/**
 * Categories tab content with category visibility options, hopper settings, and action link.
 */
@Composable
fun CategoriesTab(
    state: DisplayOptionsState,
    onAddCategoriesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showCategoryInTitle by state.showCategoryInTitle.collectAsState()
    val showAllCategories by state.showAllCategories.collectAsState()
    val collapsedDynamicAtBottom by state.collapsedDynamicAtBottom.collectAsState()
    val showEmptyCategoriesWhileFiltering by state.showEmptyCategoriesWhileFiltering.collectAsState()
    val hideHopperMode by state.hideHopperMode.collectAsState()
    val hopperLongPressAction by state.hopperLongPressAction.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Checkboxes
        LabeledCheckbox(
            checked = showCategoryInTitle,
            onCheckedChange = { 
                state.setShowCategoryInTitle(it) 
                state.onShowMiniBar?.invoke()
            },
            label = stringResource(MR.strings.always_show_current_category),
            enabled = showAllCategories, // Only enabled when "Show all categories" is checked
        )

        LabeledCheckbox(
            checked = showAllCategories,
            onCheckedChange = { 
                state.setShowAllCategories(it) 
                state.onLibraryUpdateRequired?.invoke()
            },
            label = stringResource(MR.strings.show_all_categories),
        )

        LabeledCheckboxWithSubtitle(
            checked = collapsedDynamicAtBottom,
            onCheckedChange = { 
                state.setCollapsedDynamicAtBottom(it) 
                state.onLibraryUpdateRequired?.invoke()
            },
            label = stringResource(MR.strings.move_dynamic_to_bottom),
            subtitle = stringResource(MR.strings.when_grouping_by_sources_tags),
        )

        LabeledCheckbox(
            checked = showEmptyCategoriesWhileFiltering,
            onCheckedChange = { 
                state.setShowEmptyCategoriesWhileFiltering(it) 
                state.onFilterUpdateRequired?.invoke()
            },
            label = stringResource(MR.strings.show_categories_while_filtering),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Hopper visibility dropdown
        DropdownPreference(
            label = stringResource(MR.strings.hide_category_hopper),
            selectedValue = hideHopperMode,
            options = listOf(
                DisplayOptionsState.HOPPER_ALWAYS_SHOW to stringResource(MR.strings.never),
                DisplayOptionsState.HOPPER_AUTO_HIDE to stringResource(MR.strings.hides_on_scroll),
                DisplayOptionsState.HOPPER_ALWAYS_HIDE to stringResource(MR.strings.always),
            ),
            onValueSelected = { 
                state.setHideHopperMode(it)
                state.onHopperVisibilityChanged?.invoke(it == DisplayOptionsState.HOPPER_ALWAYS_HIDE)
                state.onResetHopperY?.invoke()
            },
        )

        // Hopper long press action dropdown
        DropdownPreference(
            label = stringResource(MR.strings.category_hopper_long_press),
            selectedValue = hopperLongPressAction,
            options = listOf(
                0 to stringResource(MR.strings.search),
                1 to stringResource(MR.strings.expand_collapse_all_categories),
                2 to stringResource(MR.strings.display_options),
                3 to stringResource(MR.strings.group_library_by),
                4 to stringResource(MR.strings.open_random_series),
                5 to stringResource(MR.strings.open_random_series_global),
            ),
            onValueSelected = { state.setHopperLongPressAction(it) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Add/Edit categories button
        TextButton(
            onClick = onAddCategoriesClick,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(MR.strings.add_edit_categories),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun LabeledCheckboxWithSubtitle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.scale(0.85f),
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurface 
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun <T> DropdownPreference(
    label: String,
    selectedValue: T,
    options: List<Pair<T, String>>,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedValue }?.second ?: ""

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = selectedLabel,
                    transitionSpec = {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    },
                    label = "dropdown_label"
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            onValueSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
