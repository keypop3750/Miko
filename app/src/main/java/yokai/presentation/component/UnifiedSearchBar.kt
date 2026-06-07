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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R
import yokai.core.content.ContentType

/**
 * The context/screen where the search bar is being used.
 * Determines behavior of certain buttons (e.g., filter button).
 */
enum class SearchBarContext {
    /** Library screen - filter shows filter sheet, then display options on second click */
    LIBRARY,
    /** Browse screen - filter opens source filter settings */
    BROWSE
}

/**
 * State for tracking filter button toggle behavior in Library context.
 */
enum class FilterToggleState {
    /** No sheet is open */
    CLOSED,
    /** Filter bottom sheet is expanded */
    FILTER_EXPANDED,
    /** Display options sheet is open */
    DISPLAY_OPEN
}

/**
 * Unified Compose-based search bar that works across Library and Browse screens.
 * 
 * This search bar is designed to be shared between screens, eliminating the need
 * for rebuilding when navigating between Library and Browse modes.
 * 
 * ## Features:
 * 1. **Magnifying glass icon** - Indicates search functionality; becomes back arrow when searching
 * 2. **Dynamic text** - Title/subtitle that changes based on context (library count, source name, etc.)
 * 3. **Filter button** - Context-dependent behavior:
 *    - Library: First click expands filter sheet, second click shows display options
 *    - Browse: Opens source filter settings
 * 4. **Mode toggle button** - Switches between Manga and Novel modes
 * 5. **Three-dot menu** - Opens overflow menu (Incognito, Settings, Stats, About, Help)
 * 
 * ## Reactive Theming:
 * Uses MaterialTheme.colorScheme which updates automatically when ModeManager.currentMode
 * changes, enabling smooth theme transitions without view recreation.
 */
@Composable
fun UnifiedSearchBar(
    // Title and subtitle
    title: String,
    subtitle: String? = null,
    
    // Search state
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSearchClose: () -> Unit = {},
    onSearchSubmit: (String) -> Unit = {},
    
    // Context state
    context: SearchBarContext = SearchBarContext.LIBRARY,
    currentMode: ContentType = ContentType.MANGA,
    isIncognito: Boolean = false,
    
    // Filter toggle state (for Library context)
    filterToggleState: FilterToggleState = FilterToggleState.CLOSED,
    
    // Navigation
    isRoot: Boolean = true,
    onNavigationClick: () -> Unit = {},
    
    // Actions
    onFilterClick: () -> Unit = {},
    onModeToggle: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    
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
            // 1. Navigation icon (search or back arrow)
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
            
            // 2. Title/Subtitle or Search Input (dynamic text)
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
                // 3. Filter button
                IconButton(
                    onClick = onFilterClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = when (context) {
                            SearchBarContext.LIBRARY -> {
                                when (filterToggleState) {
                                    FilterToggleState.CLOSED -> "Open filter"
                                    FilterToggleState.FILTER_EXPANDED -> "Open display options"
                                    FilterToggleState.DISPLAY_OPEN -> "Open filter"
                                }
                            }
                            SearchBarContext.BROWSE -> "Filter sources"
                        },
                        tint = contentColorAlpha
                    )
                }
                
                // 4. Mode toggle button
                IconButton(
                    onClick = onModeToggle,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (currentMode == ContentType.MANGA) {
                            Icons.Default.Book
                        } else {
                            Icons.AutoMirrored.Filled.MenuBook
                        },
                        contentDescription = if (currentMode == ContentType.MANGA) {
                            "Switch to Novel mode"
                        } else {
                            "Switch to Manga mode"
                        },
                        tint = contentColorAlpha
                    )
                }
                
                // 5. Three-dot menu
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = contentColorAlpha
                    )
                }
            }
        }
    }
}

/**
 * Creates search bar title for Library context based on content count.
 */
fun librarySearchBarTitle(mangaCount: Int, mode: ContentType): String {
    return when (mode) {
        ContentType.MANGA -> if (mangaCount == 1) "1 manga" else "$mangaCount manga"
        ContentType.NOVEL -> if (mangaCount == 1) "1 novel" else "$mangaCount novels"
    }
}

/**
 * Creates search bar title for Browse context.
 */
fun browseSearchBarTitle(): String = "Search sources"
