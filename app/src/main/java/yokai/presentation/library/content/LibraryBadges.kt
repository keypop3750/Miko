package yokai.presentation.library.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Badge display modes matching preferences
 */
object UnreadBadgeMode {
    const val HIDE = 0
    const val SHOW_DOT = 1
    const val SHOW_COUNT = 2
}

/**
 * Combined badge display for library grid/list items
 * Shows unread count, download count, and language badge
 */
@Composable
fun LibraryBadges(
    unreadCount: Int,
    downloadCount: Int,
    language: String?,
    showUnreadBadge: Boolean,
    showDownloadBadge: Boolean,
    showLanguageBadge: Boolean,
    unreadBadgeType: Int = UnreadBadgeMode.SHOW_COUNT,
    modifier: Modifier = Modifier,
) {
    if (unreadCount <= 0 && downloadCount <= 0 && language.isNullOrBlank()) {
        return
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Unread badge - show count or dot based on badge type
        if (unreadCount > 0 && showUnreadBadge) {
            UnreadBadge(
                count = unreadCount,
                showCount = unreadBadgeType == UnreadBadgeMode.SHOW_COUNT,
            )
        }
        
        // Download badge
        if (downloadCount > 0 && showDownloadBadge) {
            DownloadBadge(count = downloadCount)
        }
        
        // Language badge
        if (!language.isNullOrBlank() && showLanguageBadge) {
            LanguageBadge(language = language)
        }
    }
}

/**
 * Unread count/dot badge - uses original square-ish design
 * When showCount is false, show the same badge shape but without the number
 */
@Composable
fun UnreadBadge(
    count: Int,
    showCount: Boolean,
    modifier: Modifier = Modifier,
) {
    // Use consistent padding for square-ish badge shape (matching original design)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (showCount) {
            Text(
                text = if (count > 999) "999+" else count.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 13.sp,
            )
        } else {
            // Show badge without number - use invisible text to maintain badge size
            // This matches original Miko behavior where badge is shown but text color matches background
            Text(
                text = "0",
                color = MaterialTheme.colorScheme.primary, // Same as background = invisible
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 13.sp,
            )
        }
    }
}

/**
 * Download count badge
 */
@Composable
fun DownloadBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.tertiary)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = MaterialTheme.colorScheme.onTertiary,
        )
        Text(
            text = if (count > 999) "999+" else count.toString(),
            color = MaterialTheme.colorScheme.onTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 12.sp,
        )
    }
}

/**
 * Language flag/code badge
 */
@Composable
fun LanguageBadge(
    language: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = language.uppercase().take(3),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 11.sp,
        )
    }
}

/**
 * Compact badge for cover-only grid mode (overlay style)
 */
@Composable
fun CompactBadgeOverlay(
    unreadCount: Int,
    downloadCount: Int,
    showUnreadBadge: Boolean,
    showDownloadBadge: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasUnread = unreadCount > 0 && showUnreadBadge
    val hasDownload = downloadCount > 0 && showDownloadBadge
    
    if (!hasUnread && !hasDownload) return
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(bottomStart = 8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasUnread) {
            Text(
                text = if (unreadCount > 999) "999+" else unreadCount.toString(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        
        if (hasDownload) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color.White,
            )
            Text(
                text = downloadCount.toString(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
