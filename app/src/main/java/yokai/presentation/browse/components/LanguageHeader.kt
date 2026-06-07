package yokai.presentation.browse.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Composable for displaying language/section headers in the browse source list.
 * 
 * Uses MaterialTheme.colorScheme.onSurface for text color, matching the View-based
 * header which uses textAppearanceBodySmall (inherits theme text color).
 * No background color is applied to match the original design.
 */
@Composable
fun LanguageHeader(
    title: String,
    isSpecialSection: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Use onSurface color to match View-based textAppearanceBodySmall
    // No background - matches original source_header_item.xml
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}
