package yokai.presentation.library.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.database.models.Category

/**
 * Category header that can be expanded/collapsed
 */
@Composable
fun LibraryCategoryHeader(
    category: Category,
    itemCount: Int,
    isExpanded: Boolean,
    showItemCount: Boolean,
    onClick: () -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        label = "expand_rotation"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            // Category name
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            
            // Item count badge
            if (showItemCount && itemCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = itemCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        
        // Expand/collapse icon
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(24.dp)
                .rotate(rotationAngle)
                .clickable(onClick = onExpandClick),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Sticky category header for showing current category while scrolling
 */
@Composable
fun StickyLibraryCategoryHeader(
    categoryName: String,
    itemCount: Int,
    showItemCount: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = categoryName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        
        if (showItemCount && itemCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($itemCount)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Compact category indicator for single-category mode
 */
@Composable
fun CategoryIndicator(
    categoryName: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = categoryName,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
    )
}
