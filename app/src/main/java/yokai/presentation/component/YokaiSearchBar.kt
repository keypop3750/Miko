package yokai.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R
import yokai.core.content.ContentType

/**
 * Data class representing a menu item in the search bar.
 */
data class SearchBarMenuItem(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val visible: Boolean = true,
)

/**
 * Compose-based search bar that replicates the View-based FloatingToolbar.
 * 
 * Features:
 * - Card container with 24dp corner radius
 * - Leading navigation icon (search or back arrow)
 * - Optional incognito indicator
 * - Title and optional subtitle
 * - Trailing menu icons
 * - Search mode with text input
 * - Reactive theming via MaterialTheme.colorScheme
 */
@Composable
fun YokaiSearchBar(
    title: String,
    subtitle: String? = null,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSearchClose: () -> Unit = {},
    onSearchSubmit: (String) -> Unit = {},  // Called when Enter/Search key pressed
    isIncognito: Boolean = false,
    isRoot: Boolean = true,
    onNavigationClick: () -> Unit = {},
    menuItems: List<SearchBarMenuItem> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Request focus when search becomes active
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }
    
    // Colors from theme - these update reactively when mode changes
    val cardBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    co.touchlab.kermit.Logger.d { "🔍 [SEARCH_BAR] surfaceVariant=${cardBackgroundColor.value.toString(16)}" }
    val contentColor = MaterialTheme.colorScheme.onSurface
    val contentColorAlpha = contentColor.copy(alpha = 0.78f)
    val subtitleColorAlpha = contentColor.copy(alpha = 0.59f)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(cardBackgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = MaterialTheme.colorScheme.primary),
                    enabled = !isSearchActive,
                    onClick = onSearchClick
                )
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Navigation icon (search or back)
            IconButton(
                onClick = {
                    if (isSearchActive) {
                        onSearchClose()
                        focusManager.clearFocus()
                    } else {
                        onNavigationClick()
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = when {
                        isSearchActive -> Icons.AutoMirrored.Filled.ArrowBack
                        isRoot -> Icons.Default.Search
                        else -> Icons.AutoMirrored.Filled.ArrowBack
                    },
                    contentDescription = if (isSearchActive || !isRoot) "Back" else "Search",
                    tint = contentColorAlpha,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Incognito indicator
            AnimatedVisibility(
                visible = isIncognito,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row {
                    Icon(
                        painter = painterResource(R.drawable.ic_incognito_circle_24dp),
                        contentDescription = "Incognito mode",
                        tint = contentColorAlpha,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
            
            // Title/Subtitle or Search Input
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isSearchActive) {
                    // Search text field
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = contentColor,
                            fontSize = 16.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                onSearchSubmit(searchQuery)
                                focusManager.clearFocus()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = contentColorAlpha.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                } else {
                    // Title and subtitle display
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColorAlpha,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = subtitleColorAlpha,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            
            // Clear button when searching
            AnimatedVisibility(
                visible = isSearchActive && searchQuery.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    onClick = { onSearchQueryChange("") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = contentColorAlpha,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Menu items (only show when not searching)
            if (!isSearchActive) {
                menuItems.filter { it.visible }.forEach { item ->
                    IconButton(
                        onClick = item.onClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.contentDescription,
                            tint = contentColorAlpha
                        )
                    }
                }
            }
        }
    }
}

/**
 * Creates the standard menu items for the Browse screen.
 */
@Composable
fun browseModeMenuItems(
    currentMode: ContentType,
    onFilterClick: () -> Unit,
    onModeToggle: () -> Unit,
    onMenuClick: () -> Unit,
): List<SearchBarMenuItem> {
    return listOf(
        SearchBarMenuItem(
            icon = Icons.Default.FilterList,
            contentDescription = "Filter sources",
            onClick = onFilterClick
        ),
        SearchBarMenuItem(
            icon = if (currentMode == ContentType.MANGA) Icons.Default.Book else Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = if (currentMode == ContentType.MANGA) "Switch to Novel mode" else "Switch to Manga mode",
            onClick = onModeToggle
        ),
        SearchBarMenuItem(
            icon = Icons.Default.MoreVert,
            contentDescription = "More options",
            onClick = onMenuClick
        )
    )
}
