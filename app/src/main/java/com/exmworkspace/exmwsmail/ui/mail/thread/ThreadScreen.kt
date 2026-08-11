package com.exmworkspace.exmwsmail.ui.mail.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.ui.mail.detail.AttachmentsCard
import com.exmworkspace.exmwsmail.ui.mail.detail.BodyEmpty
import com.exmworkspace.exmwsmail.ui.mail.detail.BodyLoading
import com.exmworkspace.exmwsmail.ui.mail.detail.BodySegments
import com.exmworkspace.exmwsmail.ui.mail.detail.BodyText
import com.exmworkspace.exmwsmail.ui.mail.detail.DetailActionBar
import com.exmworkspace.exmwsmail.ui.mail.detail.openAttachment
import com.exmworkspace.exmwsmail.ui.mail.detail.splitConversations
import com.exmworkspace.exmwsmail.ui.mail.MessageActionsSheet
import com.exmworkspace.exmwsmail.ui.mail.formatMessageDate
import com.exmworkspace.exmwsmail.ui.mail.sortedForDrawer
import java.util.Date

/**
 * The conversation, oldest first with the newest expanded — the pattern the web uses and
 * the doc asks mobile to mirror (§4.4). Only the expanded message loads its body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    messageId: Long,
    onBack: () -> Unit,
    onReply: (Long) -> Unit,
    onForward: (Long) -> Unit,
    viewModel: ThreadViewModel = viewModel(factory = ThreadViewModel.factoryFor(messageId)),
) {
    val messages by viewModel.messages.collectAsState()
    val expandedId by viewModel.expandedId.collectAsState()
    val detail by viewModel.expandedDetail.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val subject by viewModel.subject.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val closed by viewModel.closed.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }

    LaunchedEffect(closed) {
        if (closed) onBack()
    }

    // Acts on the message being read, matching the bottom bar; the one thread-wide entry it
    // keeps is marking the whole conversation read.
    val expandedMessage = messages.firstOrNull { it.id == expandedId }
    if (showActions && expandedMessage != null) {
        MessageActionsSheet(
            message = expandedMessage,
            folders = remember(folders) { folders.sortedForDrawer() },
            currentFolder = currentFolder,
            onToggleRead = {
                viewModel.markUnread()
                showActions = false
                onBack()
            },
            onMarkThreadRead = {
                viewModel.markThreadRead()
                showActions = false
            },
            onTogglePin = {
                viewModel.toggleFlag()
                showActions = false
            },
            onMove = { target ->
                viewModel.move(target)
                showActions = false
            },
            onSpam = {
                viewModel.markSpam()
                showActions = false
            },
            onSetColor = { color ->
                viewModel.setColor(color)
                showActions = false
            },
            onDelete = {
                viewModel.deleteExpanded()
                showActions = false
            },
            onDismiss = { showActions = false },
        )
    }

    val savedToCloud = stringResource(R.string.saved_to_cloud)
    val notice by viewModel.notice.collectAsState()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = subject.ifBlank { stringResource(R.string.thread_title) },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (messages.size > 1) {
                            Text(
                                text = stringResource(R.string.thread_messages, messages.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = stringResource(R.string.back_button),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                actions = {
                    // No "mark read" shortcut here: opening the conversation already marks
                    // the message being read, so it had nothing left to do. It stays in the
                    // overflow for the case that does matter — older messages still unread —
                    // and on the list's long-press sheet, where it works without opening.
                    IconButton(onClick = { showActions = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.message_actions),
                        )
                    }
                },
            )
        },
        bottomBar = {
            // Same bar as the single-message detail: opening a conversation shouldn't cost
            // the user star / mark-unread / delete.
            val target = expandedId
            if (target != null) {
                val expandedMessage = messages.firstOrNull { it.id == target }
                DetailActionBar(
                    isFlagged = expandedMessage?.flagged == true,
                    busy = busy,
                    onReply = { onReply(target) },
                    onForward = { onForward(target) },
                    onToggleFlag = viewModel::toggleFlag,
                    onMarkUnread = {
                        viewModel.markUnread()
                        onBack()
                    },
                    onDelete = viewModel::deleteExpanded,
                    canWrite = currentFolder?.canWrite ?: true,
                )
            }
        },
    ) { padding ->
        when {
            messages.isEmpty() && loading -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { BodyLoading() }

            messages.isEmpty() -> Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text(error ?: stringResource(R.string.no_messages)) }

            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    ThreadMessageCard(
                        message = msg,
                        expanded = expandedId == msg.id,
                        onToggle = { viewModel.toggle(msg.id) },
                    ) {
                        val body = detail.body
                        when {
                            body?.html != null -> {
                                val segments = remember(body.html) { splitConversations(body.html!!) }
                                BodySegments(segments)
                            }
                            body?.text != null -> BodyText(body.text!!)
                            expandedId == msg.id -> BodyLoading()
                            else -> BodyEmpty(stringResource(R.string.no_content))
                        }
                        if (detail.attachments.isNotEmpty() && detail.message?.id == msg.id) {
                            Spacer(Modifier.height(8.dp))
                            AttachmentsCard(
                                attachments = detail.attachments,
                                downloadingId = null,
                                onOpen = { att ->
                                    viewModel.openAttachment(att) { ready ->
                                        openAttachment(context, ready)
                                    }
                                },
                                onSaveToCloud = { att ->
                                    viewModel.saveAttachmentToCloud(att, savedToCloud)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadMessageCard(
    message: MessageEntity,
    expanded: Boolean,
    onToggle: () -> Unit,
    body: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (expanded) 4.dp else 2.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = message.from.ifBlank { message.fromAddress.orEmpty() },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (!message.seen) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatMessageDate(message.internalDate.takeIf { it > 0 }?.let { Date(it) }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!expanded && !message.snippet.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = message.snippet!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (message.hasAttachments) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                HorizontalDivider()
                body()
            }
        }
    }
}
