package com.exmworkspace.exmwsmail.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity

/**
 * Contextual bar that replaces the title bar while rows are selected.
 *
 * Only the actions that make sense on a set: read state, move, delete. Per-message things
 * (pin, colour, remind me, AI, view source) stay on the message itself — folding them in here
 * would mean answering "what does colouring twelve messages mean" for no real gain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
    count: Int,
    canWrite: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    /**
     * The single-message sheet — pin, colour, remind me, AI, view source. Offered only with
     * exactly one row picked, because none of those answer "what does this mean for twelve
     * messages"; without it, long-press would have quietly cost the list every one of them.
     */
    onMore: (() -> Unit)? = null,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        title = {
            // The bare count, not "N seleccionados": with five actions beside it the phrase
            // wrapped to three lines on a normal phone. The checkmarks on screen already say
            // what the number refers to.
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.select_all),
                )
            }
            // A read-only shared folder answers 403 to every one of these (§4.20), so the
            // bar offers nothing but leaving the selection.
            if (canWrite) {
                IconButton(onClick = onMarkRead) {
                    Icon(
                        Icons.Default.MarkEmailRead,
                        contentDescription = stringResource(R.string.mark_read),
                    )
                }
                IconButton(onClick = onMarkUnread) {
                    Icon(
                        Icons.Default.Drafts,
                        contentDescription = stringResource(R.string.mark_unread_long),
                    )
                }
                IconButton(onClick = onMove) {
                    Icon(
                        Icons.Default.DriveFileMove,
                        contentDescription = stringResource(R.string.move_to_folder),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                if (onMore != null && count == 1) {
                    IconButton(onClick = onMore) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                }
            }
        },
    )
}

/** Destination picker for a batch move — the same list the single-message sheet offers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveSelectionSheet(
    folders: List<FolderEntity>,
    currentFolder: FolderEntity?,
    count: Int,
    onMove: (FolderEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.move_selection_title, count),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // Read-only shares are not valid destinations: the copy would 403 on arrival.
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
