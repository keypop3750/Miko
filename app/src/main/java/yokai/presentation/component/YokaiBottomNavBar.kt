package yokai.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R

/**
 * Navigation destination for the bottom nav bar.
 */
data class NavDestination(
    val id: Int,
    val label: String,
    val iconRes: Int,
    val selectedIconRes: Int = iconRes,
)

/**
 * Compose-based bottom navigation bar.
 * Uses MaterialTheme.colorScheme for reactive theme updates on mode changes.
 */
@Composable
fun YokaiBottomNavBar(
    destinations: List<NavDestination>,
    selectedId: Int,
    onDestinationClick: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No animation on background - instant color change to prevent lag
    val backgroundColor = MaterialTheme.colorScheme.surface
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(80.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        destinations.forEach { destination ->
            NavBarItem(
                destination = destination,
                selected = destination.id == selectedId,
                onClick = { onDestinationClick(destination) }
            )
        }
    }
}

@Composable
private fun RowScope.NavBarItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Animate colors for smooth transitions
    val iconTint by animateColorAsState(
        targetValue = if (selected) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "iconTint"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "textColor"
    )
    
    val indicatorColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.secondaryContainer
        else
            Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "indicatorColor"
    )
    
    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 40.dp)
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Icon with indicator background
        Box(
            modifier = Modifier
                .background(
                    color = indicatorColor,
                    shape = MaterialTheme.shapes.extraLarge
                )
                .padding(horizontal = 20.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    id = if (selected) destination.selectedIconRes else destination.iconRes
                ),
                contentDescription = destination.label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Label
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Default navigation destinations matching the app's bottom navigation.
 */
object NavDestinations {
    val Library = NavDestination(
        id = R.id.nav_library,
        label = "Library",
        iconRes = R.drawable.ic_library_outline_24dp,
        selectedIconRes = R.drawable.ic_library_outline_24dp
    )
    
    val Recents = NavDestination(
        id = R.id.nav_recents,
        label = "Recents",
        iconRes = R.drawable.ic_recents_outline_24dp,
        selectedIconRes = R.drawable.ic_recents_filled_24dp
    )
    
    val Browse = NavDestination(
        id = R.id.nav_browse,
        label = "Browse",
        iconRes = R.drawable.ic_browse_outline_24dp,
        selectedIconRes = R.drawable.ic_browse_24dp
    )
    
    val More = NavDestination(
        id = R.id.nav_swipes,
        label = "More",
        iconRes = R.drawable.ic_swipes_outline_24dp,
        selectedIconRes = R.drawable.ic_swipes_filled_24dp
    )
    
    val all = listOf(Library, Recents, Browse, More)
}
