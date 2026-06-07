package yokai.presentation.library.displayoptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yokai.i18n.MR
import yokai.presentation.extension.stringResource
import kotlin.math.roundToInt

/**
 * Display tab content with layout options, grid size slider, and checkboxes.
 */
@Composable
fun DisplayTab(
    state: DisplayOptionsState,
    modifier: Modifier = Modifier,
) {
    val libraryLayout by state.libraryLayout.collectAsState()
    val gridSize by state.gridSize.collectAsState()
    val uniformGrid by state.uniformGrid.collectAsState()
    val useStaggeredGrid by state.useStaggeredGrid.collectAsState()
    val outlineOnCovers by state.outlineOnCovers.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Layout radio group
        LayoutRadioGroup(
            selectedLayout = libraryLayout,
            onLayoutSelected = { state.setLibraryLayout(it) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Grid size slider
        GridSizeSlider(
            value = gridSize,
            onValueChange = { state.setGridSize(it) },
            onReset = { state.resetGridSize() },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Checkboxes
        LabeledCheckbox(
            checked = uniformGrid,
            onCheckedChange = { state.setUniformGrid(it) },
            label = stringResource(MR.strings.uniform_grid_covers),
        )

        LabeledCheckbox(
            checked = useStaggeredGrid,
            onCheckedChange = { state.setUseStaggeredGrid(it) },
            label = buildAnnotatedString {
                append(stringResource(MR.strings.use_staggered_grid))
                withStyle(
                    SpanStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        baselineShift = BaselineShift.Superscript,
                        color = MaterialTheme.colorScheme.primary,
                    )
                ) {
                    append("BETA")
                }
            }.toString(),
            enabled = !uniformGrid, // Disabled when uniform grid is enabled
            showBetaTag = true,
        )

        LabeledCheckbox(
            checked = outlineOnCovers,
            onCheckedChange = { state.setOutlineOnCovers(it) },
            label = stringResource(MR.strings.show_outline_around_covers),
        )
    }
}

@Composable
private fun LayoutRadioGroup(
    selectedLayout: Int,
    onLayoutSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layouts = listOf(
        DisplayOptionsState.LAYOUT_LIST to stringResource(MR.strings.list),
        DisplayOptionsState.LAYOUT_COMPACT_GRID to stringResource(MR.strings.compact_grid),
        DisplayOptionsState.LAYOUT_COMFORTABLE_GRID to stringResource(MR.strings.comfortable_grid),
        DisplayOptionsState.LAYOUT_COVER_ONLY_GRID to stringResource(MR.strings.cover_only_grid),
    )

    Column(modifier = modifier.selectableGroup()) {
        layouts.forEach { (layoutValue, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedLayout == layoutValue,
                        onClick = { onLayoutSelected(layoutValue) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedLayout == layoutValue,
                    onClick = null, // null because parent handles click
                    modifier = Modifier.scale(0.85f),
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun GridSizeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Convert internal value to display value
    // Internal: -0.5 to 3.0, Display: 0 to 7 (steps)
    val displayValue = ((value + 0.5f) * 2f).roundToInt().toFloat()
    val rowCount = calculateRowCount(displayValue)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = stringResource(MR.strings.grid_size),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(MR.strings._per_row, rowCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Slider(
            value = displayValue,
            onValueChange = { newDisplayValue ->
                val newInternalValue = (newDisplayValue / 2f) - 0.5f
                onValueChange(newInternalValue)
            },
            valueRange = 0f..7f,
            steps = 6, // 7 steps total (0-7)
            modifier = Modifier.weight(2f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            ),
        )

        TextButton(onClick = onReset) {
            Text(
                text = stringResource(MR.strings.reset),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Calculate the number of columns per row based on the slider value.
 * This is a simplified calculation - the actual implementation may vary.
 */
private fun calculateRowCount(displayValue: Float): Int {
    // Approximate calculation based on typical screen widths
    // Display value 0 = largest covers, 7 = smallest covers
    return when (displayValue.roundToInt()) {
        0 -> 1
        1 -> 2
        2 -> 2
        3 -> 3
        4 -> 4
        5 -> 5
        6 -> 6
        7 -> 7
        else -> 3
    }
}

@Composable
fun LabeledCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showBetaTag: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = checked,
                onClick = { if (enabled) onCheckedChange(!checked) },
                role = Role.Checkbox,
            )
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null, // null because parent handles click
            enabled = enabled,
            modifier = Modifier.scale(0.85f),
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
        if (showBetaTag) {
            Text(
                text = buildAnnotatedString {
                    append(label.removeSuffix("BETA"))
                    withStyle(
                        SpanStyle(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            baselineShift = BaselineShift.Superscript,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    ) {
                        append("BETA")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun LabeledRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // null because parent handles click
            modifier = Modifier.scale(0.85f),
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
