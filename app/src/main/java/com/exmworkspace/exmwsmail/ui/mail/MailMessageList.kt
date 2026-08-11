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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.ui.components.SenderAvatar
import com.exmworkspace.exmwsmail.data.mail.FolderKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// Message list, swipeable rows, and centered loading/error/empty states.
// Extracted from MailScreen.

@Composable
internal fun MessageList(
    messages: List<MessageEntity>,
    listState: LazyListState,
    loadingOlder: Boolean,
    endReached: Boolean,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleRead: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    canWrite: Boolean = true,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelection: (Long) -> Unit = {},
) {
    val selectionMode = selectedIds.isNotEmpty()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The query floats pinned messages to the top; without a label that just reads as a
        // broken sort, so name both blocks — but only when there is actually a pin.
        val pinnedCount = messages.count { it.flagged }
        itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
            if (pinnedCount > 0 && (index == 0 || index == pinnedCount)) {
                ListSectionHeader(
                    text = stringResource(
                        if (index == 0) R.string.pinned_section else R.string.other_section
                    ),
                    icon = if (index == 0) Icons.Default.PushPin else null,
                )
            }
            SwipeableMessageRow(
                msg = msg,
                // Once a selection exists, tapping extends it rather than opening a message:
                // opening would throw away the set the user is still building.
                onOpen = {
                    if (selectionMode) onToggleSelection(msg.id) else onOpen(msg.id)
                },
                onDelete = { onDelete(msg.id) },
                onToggleRead = { onToggleRead(msg.id) },
                onLongPress = { onLongPress(msg.id) },
                canWrite = canWrite,
                selected = msg.id in selectedIds,
                selectionMode = selectionMode,
                // Rows glide to their new position when a refresh reorders or removes them
                // (delete, pin, new mail) instead of teleporting.
                modifier = Modifier.animateItem(),
            )
        }
        if (loadingOlder) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        } else if (endReached) {
            item {
                Text(
                    text = stringResource(R.string.end_reached),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SwipeableMessageRow(
    msg: MessageEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onToggleRead: () -> Unit,
    onLongPress: () -> Unit = {},
    /** False in a read-only shared folder: the backend answers 403 to any write (§4.20). */
    canWrite: Boolean = true,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /** True while any row is selected: swiping is off, because a swipe would act on one
     *  row while the user is clearly working on a set. */
    selectionMode: Boolean = false,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggleRead()
                    false // Don't dismiss, just trigger action
                }
                else -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f },
    )
    // A tick the finger can feel at the exact point the release changes meaning — the visual
    // colour shift alone is under the user's own thumb half the time.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.targetValue }
            .distinctUntilChanged()
            .collect { target ->
                if (target != SwipeToDismissBoxValue.Settled) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
    }
    SwipeToDismissBox(
        modifier = modifier,
        state = dismissState,
        // The overflow sheet already hides these actions here; letting the gesture through
        // would just earn a 403 and, for delete, animate the row away before it failed.
        enableDismissFromStartToEnd = canWrite && !selectionMode,
        enableDismissFromEndToStart = canWrite && !selectionMode,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            when (direction) {
                SwipeToDismissBoxValue.EndToStart -> {
                    val willDismiss = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
                    val bgColor = if (willDismiss) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.errorContainer
                    val onBg = if (willDismiss) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onErrorContainer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(bgColor, shape = RoundedCornerShape(14.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (willDismiss) {
                                Text(
                                    text = stringResource(R.string.swipe_to_delete),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = onBg,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = onBg,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    val willTrigger = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
                    val bgColor = if (willTrigger) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer
                    val onBg = if (willTrigger) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(bgColor, shape = RoundedCornerShape(14.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (msg.seen) Icons.Default.Drafts else Icons.Default.MarkEmailRead,
                                contentDescription = null,
                                tint = onBg,
                                modifier = Modifier.size(22.dp),
                            )
                            if (willTrigger) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(if (msg.seen) R.string.swipe_to_unread else R.string.swipe_to_read),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = onBg,
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        },
    ) {
        MessageRow(
            msg = msg,
            onClick = onOpen,
            onLongClick = onLongPress,
            selected = selected,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageRow(
    msg: MessageEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    selected: Boolean = false,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
    // The click handling lives on the inner Row, not on the card's own modifier: clipping
    // there to host combinedClickable also clipped the shadow the card draws, flattening it.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // The avatar doubles as the selection control, the way every mail client does it: in
        // selection mode it becomes the checkmark, so the row gains no extra column and the
        // tap target the user already aims at keeps working.
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            // The colour flag takes over the unread badge when set — same corner, no extra slot.
            val flagColor = MailColor.from(msg.color)?.swatch
            SenderAvatar(
                name = msg.from,
                address = msg.fromAddress,
                modifier = Modifier.padding(top = 2.dp),
                badge = flagColor ?: MaterialTheme.colorScheme.primary.takeIf { !msg.seen },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = msg.from.ifBlank { "(remitente desconocido)" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (!msg.seen) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatMessageDate(msg.internalDate.takeIf { it > 0 }?.let { java.util.Date(it) }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (msg.answered) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = msg.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (!msg.seen) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (msg.threadCount > 1) {
                    Spacer(Modifier.width(6.dp))
                    ThreadCountChip(msg.threadCount)
                }
                if (msg.flagged) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // Third line: the opening of the body. Kept out of the row above so a long
            // subject cannot eat into it — each gets its own full width.
            val preview = msg.snippet?.trim().orEmpty()
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    }
}

@Composable
private fun ListSectionHeader(text: String, icon: ImageVector?) {
    Row(
        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How many messages the conversation holds — the row stands for all of them (§4.4). */
@Composable
private fun ThreadCountChip(count: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Toca para reintentar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onRetry() },
            )
        }
    }
}

@Composable
internal fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

