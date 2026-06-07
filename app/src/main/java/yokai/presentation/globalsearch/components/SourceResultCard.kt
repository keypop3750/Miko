package yokai.presentation.globalsearch.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.delay
import yokai.presentation.globalsearch.SourceSearchResult

/**
 * Card displaying search results from a single source.
 * 
 * Shows:
 * - Source name and language (with inline "No results" when empty)
 * - Loading spinner while searching
 * - Skeleton placeholders during loading
 * - Horizontal scrolling row of manga results with domino animation
 * - Arrow icon to view all results from source
 * 
 * @param animatedSourceKeys Set of source keys that have already played their animation.
 *        This is passed from the parent to persist across recompositions.
 */
@Composable
fun SourceResultCard(
    sourceResult: SourceSearchResult,
    animatedSourceKeys: MutableSet<String>,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    onSourceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Source header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = sourceResult.results?.isNotEmpty() == true) { 
                    onSourceClick() 
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Source name with optional highlight indicator
                    val prefix = if (sourceResult.isHighlighted) "▶ " else ""
                    Text(
                        text = "$prefix${sourceResult.source.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Inline "No results" when search completed but empty
                    if (!sourceResult.isLoading && sourceResult.results?.isEmpty() == true) {
                        Text(
                            text = "• No results",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                
                // Language subtitle
                if (sourceResult.source.lang.isNotEmpty()) {
                    Text(
                        text = LocaleHelper.getLocalizedDisplayName(sourceResult.source.lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            // Loading indicator or arrow icon
            when {
                sourceResult.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                sourceResult.results?.isNotEmpty() == true -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View all results",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
        
        // Results content
        when {
            sourceResult.isLoading -> {
                // Show skeleton placeholders while loading
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(5) { // Show 5 skeleton items
                        MangaResultItemSkeleton()
                    }
                }
            }
            sourceResult.results.isNullOrEmpty() -> {
                // No results - no content shown (inline message is in header)
            }
            else -> {
                // Horizontal scrolling manga row with domino animation
                // Use source ID only as animation key - we want to animate ONCE per source per search session
                // The animatedSourceKeys set is passed from parent and persists across recompositions
                val animationKey = "${sourceResult.source.id}"
                val alreadyAnimated = animationKey in animatedSourceKeys
                
                // Mark as animated immediately to prevent re-animation on recomposition
                LaunchedEffect(animationKey) {
                    animatedSourceKeys.add(animationKey)
                }
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = sourceResult.results,
                        // Use index to avoid duplicate key crash when same manga appears multiple times
                        key = { index, item -> "${sourceResult.source.id}_${index}_${item.manga.id}" }
                    ) { index, mangaItem ->
                        // Show immediately if already animated, otherwise animate
                        if (alreadyAnimated) {
                            // No animation - show directly
                            MangaResultItem(
                                manga = mangaItem.manga,
                                coverUrl = mangaItem.coverUrl,
                                onClick = { onMangaClick(mangaItem.manga) },
                                onLongClick = { onMangaLongClick(mangaItem.manga) }
                            )
                        } else {
                            // Animate on first appearance
                            var visible by remember { mutableStateOf(false) }
                            
                            LaunchedEffect(Unit) {
                                delay(index * 35L) // Gentle staggered delay
                                visible = true
                            }
                            
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(
                                    animationSpec = tween(durationMillis = 400)
                                ) + slideInVertically(
                                    initialOffsetY = { -it / 6 }, // Reduced movement
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessVeryLow
                                    )
                                ) + scaleIn(
                                    initialScale = 0.92f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessVeryLow
                                    )
                                )
                            ) {
                                MangaResultItem(
                                    manga = mangaItem.manga,
                                    coverUrl = mangaItem.coverUrl,
                                    onClick = { onMangaClick(mangaItem.manga) },
                                    onLongClick = { onMangaLongClick(mangaItem.manga) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
