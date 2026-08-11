package com.exmworkspace.exmwsmail.ui.mail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.res.stringResource
import com.exmworkspace.exmwsmail.R
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import com.exmworkspace.exmwsmail.data.remote.dto.QuotaDto
import com.exmworkspace.exmwsmail.ui.components.SenderAvatar
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.data.mail.sharedOwnerLabel
import com.exmworkspace.exmwsmail.data.mail.splitDrawerFolders
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// Navigation drawer cards: folders, user header, drawer actions.
// Extracted from MailScreen.

@Composable
internal fun FolderListCard(
    folders: List<FolderEntity>,
    selectedId: Long?,
    onSelect: (FolderEntity) -> Unit,
    onManage: (FolderEntity) -> Unit = {},
    onCreateFolder: (() -> Unit)? = null,
) {
    if (folders.isEmpty()) return
    val sections = remember(folders) { splitDrawerFolders(folders) }
    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        FolderGroupCard(sections.system, selectedId, onSelect, onManage)
        // Always rendered, even with no custom folders yet: it carries the create action, and
        // hiding it would leave a brand-new account with no way to make its first folder.
        FolderSectionHeader("Tus carpetas", action = onCreateFolder)
        FolderGroupCard(sections.own, selectedId, onSelect, onManage)
        if (sections.sharedWithMe.isNotEmpty()) {
            FolderSectionHeader("Compartidas conmigo")
            FolderGroupCard(sections.sharedWithMe, selectedId, onSelect, onManage)
        }
    }
}

/**
 * One card per section with hairline-separated rows inside — the Apple Mail grouping the
 * user asked for, instead of a stack of individual cards. The divider is inset to the text
 * edge so the icon column stays visually continuous.
 */
@Composable
private fun FolderGroupCard(
    folders: List<FolderEntity>,
    selectedId: Long?,
    onSelect: (FolderEntity) -> Unit,
    onManage: (FolderEntity) -> Unit,
) {
    if (folders.isEmpty()) return
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            folders.forEachIndexed { index, folder ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 50.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                FolderRow(
                    folder = folder,
                    isSelected = selectedId == folder.id,
                    onClick = { onSelect(folder) },
                    onLongClick = { onManage(folder) },
                )
            }
        }
    }
}

/**
 * Section label, optionally with the action that belongs to that section — creating a folder
 * sits on "Tus carpetas" because that is the only section it can add to, not on the drawer
 * title where it read as applying to the system folders too.
 */
@Composable
private fun FolderSectionHeader(text: String, action: (() -> Unit)? = null) {
    // The grouped cards already separate the blocks, so the header needs no rule of its own.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = stringResource(R.string.new_folder),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = action)
                    .padding(6.dp)
                    .size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: FolderEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // Selection marks the row inside the group, Apple-style, not a whole card.
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val owner = folder.sharedOwner
        // Outlined glyph in the single accent colour — the Apple Mail look the user asked
        // for. The per-role tints stayed where they were liked: avatars and attachments.
        Icon(
            imageVector = if (owner != null) Icons.Outlined.FolderShared
            else folderIconOutlined(folder.kind),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folderLabel(folder),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || folder.unseenCount > 0) FontWeight.SemiBold
                else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Whose folder it is. Two people can name a folder the same thing, so the
            // section header alone does not say which one the user is opening.
            if (owner != null) {
                Text(
                    text = sharedOwnerLabel(owner),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Counters come from the backend cache and refresh right after every action,
        // so the badge can be trusted without a round-trip to IMAP (§4.1).
        if (folder.unseenCount > 0) {
            Spacer(Modifier.width(8.dp))
            UnreadBadge(count = folder.unseenCount)
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
    }
}

/** One entry of the drawer's grouped actions card. */
internal data class DrawerAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    /** Shown only when positive — a "0" badge announces nothing worth announcing. */
    val badge: Int = 0,
    val onClick: () -> Unit,
)

/**
 * Reminders / settings as one grouped card in the scrolling area, in the same visual grammar
 * as the folder groups. They used to be individual cards pinned under the list, and on small
 * phones that fixed block covered most of the folders.
 */
@Composable
internal fun DrawerActionsCard(
    actions: List<DrawerAction>,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column {
            actions.forEachIndexed { index, action ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 50.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = action.onClick)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (action.badge > 0) {
                        UnreadBadge(count = action.badge)
                        Spacer(Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * The drawer's only pinned element: who is signed in, doubling as the account switcher
 * (§4.23), with storage (§4.15) folded in as a thin line instead of its own block. The
 * quota line hides when the backend reports none — "0 B de 0 B" would read as an empty
 * mailbox rather than as no data.
 */
@Composable
internal fun AccountFooterCard(
    displayName: String,
    email: String?,
    quota: QuotaDto?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(
                    name = displayName.ifBlank { email },
                    address = email,
                    size = 34.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName.ifBlank { email ?: "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (displayName.isNotBlank() && !email.isNullOrBlank()) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = stringResource(R.string.accounts_title),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (quota != null && quota.total > 0L) {
                val fraction = (quota.used.toFloat() / quota.total.toFloat()).coerceIn(0f, 1f)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = when {
                        fraction >= 0.9f -> MaterialTheme.colorScheme.error
                        fraction >= 0.75f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.storage_usage,
                        formatBytes(quota.used),
                        formatBytes(quota.total),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** Binary units, which is what mail servers report and what the webmail shows. */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value >= 100) "${value.roundToInt()} ${units[unit]}"
    else String.format(Locale.US, "%.1f %s", value, units[unit])
}

// Frosted-glass strip (iOS-style) at the bottom: Haze blurs whatever the message list is
// rendering behind it, and a pointer interceptor swallows every gesture so taps on this
// strip never reach the LazyColumn underneath. The bar slot is laid out via BottomCenter.
