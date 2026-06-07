package yokai.presentation.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import yokai.i18n.MR

/**
 * Library top app bar with expandable search functionality.
 * Matches the original Yokai/Miko search bar behavior.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryTopAppBar(
    title: String,
    searchQuery: String,
    searchSuggestion: String?,
    isSearchActive: Boolean,
    showAllCategoriesInSearch: Boolean,
    hasActiveFilters: Boolean,
    isIncognitoMode: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onToggleShowAllCategories: () -> Unit,
    onFilterClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onHelpClick: () -> Unit,
    onToggleIncognito: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier,
) {
    var showOverflowMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Auto-focus search field when search becomes active
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    
    TopAppBar(
        title = {
            if (isSearchActive) {
                // Search mode - show search field
                SearchField(
                    query = searchQuery,
                    suggestion = searchSuggestion,
                    onQueryChange = onSearchQueryChange,
                    onSearch = { /* Search submitted */ },
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Normal mode - show title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        navigationIcon = {
            if (isSearchActive) {
                IconButton(onClick = {
                    onSearchQueryChange("")
                    onSearchActiveChange(false)
                    keyboardController?.hide()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(MR.strings.back),
                    )
                }
            }
        },
        actions = {
            // Show all categories toggle (only during search)
            AnimatedVisibility(
                visible = isSearchActive && !showAllCategoriesInSearch,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(onClick = onToggleShowAllCategories) {
                    Icon(
                        imageVector = Icons.Default.CollectionsBookmark,
                        contentDescription = "Show all categories",
                    )
                }
            }
            
            // Search icon (only when not searching)
            if (!isSearchActive) {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(MR.strings.search),
                    )
                }
            } else {
                // Clear search button
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(MR.strings.clear),
                        )
                    }
                }
            }
            
            // Filter button with badge
            Box {
                IconButton(onClick = onFilterClick) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = stringResource(MR.strings.filter),
                        tint = if (hasActiveFilters) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                
                // Active filter indicator
                if (hasActiveFilters) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            
            // Overflow menu
            Box {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(MR.strings.more),
                    )
                }
                
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                ) {
                    // Incognito mode toggle
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isIncognitoMode) {
                                    "Turn off incognito mode"
                                } else {
                                    "Turn on incognito mode"
                                },
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onToggleIncognito()
                        },
                    )
                    
                    // Settings
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.settings)) },
                        onClick = {
                            showOverflowMenu = false
                            onSettingsClick()
                        },
                    )
                    
                    // Stats
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.statistics)) },
                        onClick = {
                            showOverflowMenu = false
                            onStatsClick()
                        },
                    )
                    
                    // About
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.about)) },
                        onClick = {
                            showOverflowMenu = false
                            onAboutClick()
                        },
                    )
                    
                    // Help
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.help)) },
                        onClick = {
                            showOverflowMenu = false
                            onHelpClick()
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    )
}

/**
 * Custom search field that shows a placeholder with search suggestion
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchField(
    query: String,
    suggestion: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            // Placeholder with suggestion
            if (query.isEmpty()) {
                Text(
                    text = if (suggestion != null) {
                        "Search \"$suggestion\""
                    } else {
                        "Search your library"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { /* Focus field */ },
                            onLongClick = {
                                // Long press to insert suggestion
                                if (suggestion != null) {
                                    onQueryChange(suggestion)
                                }
                            },
                        ),
                )
            }
            
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        onSearch()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
    }
}
