package com.exmworkspace.exmwsmail.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.data.mail.FolderKind

/** The colour flags the backend accepts, mapped to swatches (§4.18). */
internal enum class MailColor(val apiValue: String, val swatch: Color, val labelResId: Int) {
    RED("red", Color(0xFFE53935), R.string.color_red),
    ORANGE("orange", Color(0xFFFB8C00), R.string.color_orange),
    GREEN("green", Color(0xFF43A047), R.string.color_green),
    BLUE("blue", Color(0xFF1E88E5), R.string.color_blue),
    PURPLE("purple", Color(0xFF8E24AA), R.string.color_purple);

    companion object {
        fun from(value: String?): MailColor? = entries.firstOrNull { it.apiValue == value }
    }
}

private enum class ActionsPage { MAIN, MOVE }

/**
 * Long-press / overflow actions for a message. The row represents a thread, so the
 * mark-read and move actions operate on the whole conversation (§4.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageActionsSheet(
    message: MessageEntity,
    folders: List<FolderEntity>,
    currentFolder: FolderEntity?,
    onToggleRead: () -> Unit,
    onMarkThreadRead: () -> Unit,
    onTogglePin: () -> Unit,
    onMove: (FolderEntity) -> Unit,
    onSpam: () -> Unit,
    onSetColor: (String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** Null on screens that cannot reach these — the list rows, for instance. */
    onRemindMe: (() -> Unit)? = null,
    onSummarize: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
    /** Null where the raw message is not reachable — the list rows, for instance. */
    onViewSource: (() -> Unit)? = null,
    onViewHeaders: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by remember { mutableStateOf(ActionsPage.MAIN) }
    val canWrite = currentFolder?.canWrite ?: true
    val haptics = LocalHapticFeedback.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                text = message.subject.ifBlank { message.from },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (message.threadCount > 1) {
                    stringResource(R.string.thread_messages, message.threadCount)
                } else {
                    message.from
                },
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.padding(top = 8.dp))
            HorizontalDivider()

            if (!canWrite) {
                // A read-only shared folder answers 403 to any write, so don't offer them.
                SheetNote(stringResource(R.string.shared_readonly))
                return@Column
            }

            when (page) {
                ActionsPage.MAIN -> {
                    ColorPickerRow(
                        selected = MailColor.from(message.color),
                        onSelect = onSetColor,
                    )
                    HorizontalDivider()
                    // Swiping the row toggles this too, but a gesture nobody can see is not
                    // a discoverable place for it.
                    SheetAction(
                        icon = if (message.seen) Icons.Default.MarkEmailUnread
                        else Icons.Default.Drafts,
                        label = if (message.seen) stringResource(R.string.mark_unread_long)
                        else stringResource(R.string.mark_read),
                        onClick = onToggleRead,
                    )
                    if (message.threadCount > 1) {
                        SheetAction(
                            icon = Icons.Default.MarkEmailRead,
                            label = stringResource(R.string.mark_thread_read),
                            onClick = onMarkThreadRead,
                        )
                    }
                    SheetAction(
                        icon = if (message.flagged) Icons.Outlined.PushPin
                        else Icons.Default.PushPin,
                        label = if (message.flagged) stringResource(R.string.unpin)
                        else stringResource(R.string.pin),
                        // Pinning visibly reorders the list only after the sheet closes; the
                        // tick confirms the tap landed before that happens.
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTogglePin()
                        },
                    )
                    SheetAction(
                        icon = Icons.Default.DriveFileMove,
                        label = stringResource(R.string.move_to_folder),
                        onClick = { page = ActionsPage.MOVE },
                    )
                    if (onRemindMe != null) {
                        SheetAction(
                            icon = Icons.Default.NotificationsActive,
                            label = stringResource(R.string.remind_me),
                            onClick = onRemindMe,
                        )
                    }
                    if (onSummarize != null) {
                        SheetAction(
                            icon = Icons.Default.AutoAwesome,
                            label = stringResource(R.string.ai_summary),
                            onClick = onSummarize,
                        )
                    }
                    if (onTranslate != null) {
                        SheetAction(
                            icon = Icons.Default.Translate,
                            label = stringResource(R.string.ai_translate),
                            onClick = onTranslate,
                        )
                    }
                    if (onViewSource != null) {
                        SheetAction(
                            icon = Icons.Default.Code,
                            label = stringResource(R.string.view_source),
                            onClick = onViewSource,
                        )
                    }
                    if (onViewHeaders != null) {
                        SheetAction(
                            icon = Icons.AutoMirrored.Filled.ListAlt,
                            label = stringResource(R.string.view_headers),
                            onClick = onViewHeaders,
                        )
                    }
                    if (currentFolder?.kind != FolderKind.JUNK) {
                        SheetAction(
                            icon = Icons.Default.Report,
                            label = stringResource(R.string.mark_spam),
                            onClick = onSpam,
                        )
                    }
                    SheetAction(
                        icon = Icons.Default.Delete,
                        label = stringResource(R.string.delete),
                        onClick = onDelete,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }

                ActionsPage.MOVE -> {
                    val targets = folders.filter { it.id != currentFolder?.id && it.canWrite }
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(targets, key = { it.id }) { folder ->
                            SheetAction(
                                icon = folderIcon(folder.kind),
                                label = folderLabel(folder),
                                onClick = { onMove(folder) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerRow(selected: MailColor?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MailColor.entries.forEach { color ->
            Swatch(
                color = color.swatch,
                selected = selected == color,
                contentDescription = stringResource(color.labelResId),
                onClick = { onSelect(if (selected == color) null else color.apiValue) },
            )
        }
        Spacer(Modifier.width(2.dp))
        Swatch(
            color = Color.Transparent,
            selected = selected == null,
            contentDescription = stringResource(R.string.no_color),
            onClick = { onSelect(null) },
        )
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = if (color == Color.Transparent) MaterialTheme.colorScheme.onSurface
                else Color.White,
            )
        }
    }
}
