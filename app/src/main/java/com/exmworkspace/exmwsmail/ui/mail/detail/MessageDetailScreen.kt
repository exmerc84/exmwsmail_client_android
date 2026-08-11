package com.exmworkspace.exmwsmail.ui.mail.detail

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exmworkspace.exmwsmail.data.local.entity.AttachmentEntity
import com.exmworkspace.exmwsmail.ui.ai.AiResultSheet
import com.exmworkspace.exmwsmail.ui.ai.TranslateLanguage
import com.exmworkspace.exmwsmail.ui.ai.TranslateLanguageSheet
import com.exmworkspace.exmwsmail.ui.followups.RemindMeSheet
import com.exmworkspace.exmwsmail.ui.mail.MessageActionsSheet
import com.exmworkspace.exmwsmail.ui.mail.sortedForDrawer
import com.exmworkspace.exmwsmail.ui.mailContainer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    messageId: Long,
    onBack: () -> Unit,
    onReply: () -> Unit = {},
    onForward: () -> Unit = {},
    onViewSource: (SourceMode) -> Unit = {},
    viewModel: MessageDetailViewModel = viewModel(factory = MessageDetailViewModel.factoryFor(messageId)),
) {
    val detail by viewModel.detail.collectAsState()
    val loadingBody by viewModel.loadingBody.collectAsState()
    val error by viewModel.error.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val downloadingAttachment by viewModel.downloadingAttachment.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val invite by viewModel.invite.collectAsState()
    val inviteReply by viewModel.inviteReply.collectAsState()
    val sendingReply by viewModel.sendingReply.collectAsState()
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }

    val savedToCloud = stringResource(R.string.saved_to_cloud)
    val summaryTitle = stringResource(R.string.ai_summary_title)
    val translateTitle = stringResource(R.string.ai_translate_title)
    // Remembered so the banner can name the language the body is now in.
    var translateLanguage by remember { mutableStateOf(TranslateLanguage.SPANISH) }
    var showTranslatePicker by remember { mutableStateOf(false) }
    val followupCreated = stringResource(R.string.followup_created)
    var showRemindMe by remember { mutableStateOf(false) }
    val aiResult by viewModel.aiResult.collectAsState()
    val aiTitle by viewModel.aiTitle.collectAsState()
    val translatedBody by viewModel.translatedBody.collectAsState()
    val translating by viewModel.translating.collectAsState()

    androidx.compose.runtime.LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    detail.message?.let { message ->
        if (showActions) {
            MessageActionsSheet(
                message = message,
                folders = remember(folders) { folders.sortedForDrawer() },
                currentFolder = currentFolder,
                onToggleRead = {
                    // Reading it is what marked it; the only useful direction is back.
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
                    viewModel.deleteMessage()
                    showActions = false
                },
                onDismiss = { showActions = false },
                onRemindMe = {
                    showActions = false
                    showRemindMe = true
                },
                onSummarize = {
                    showActions = false
                    viewModel.summarize(summaryTitle)
                },
                onTranslate = {
                    showActions = false
                    showTranslatePicker = true
                },
                onViewSource = {
                    showActions = false
                    onViewSource(SourceMode.SOURCE)
                },
                onViewHeaders = {
                    showActions = false
                    onViewSource(SourceMode.HEADERS)
                },
            )
        }
    }

    if (showTranslatePicker) {
        TranslateLanguageSheet(
            onPick = { language ->
                showTranslatePicker = false
                translateLanguage = language
                viewModel.translateInline(language.apiValue)
            },
            onDismiss = { showTranslatePicker = false },
        )
    }

    if (showRemindMe) {
        RemindMeSheet(
            onPick = { dueAt ->
                showRemindMe = false
                viewModel.remindMe(dueAt, followupCreated)
            },
            onDismiss = { showRemindMe = false },
        )
    }

    aiResult?.let { result ->
        AiResultSheet(
            title = aiTitle,
            result = result,
            onDismiss = viewModel::dismissAi,
        )
    }

    val notice by viewModel.notice.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("") },
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
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = stringResource(R.string.back_button),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                },
                actions = {
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
            DetailActionBar(
                isFlagged = detail.message?.flagged == true,
                busy = busy,
                onReply = onReply,
                onForward = onForward,
                onToggleFlag = viewModel::toggleFlag,
                onMarkUnread = {
                    viewModel.markUnread()
                    onBack()
                },
                onDelete = viewModel::deleteMessage,
                canWrite = currentFolder?.canWrite ?: true,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val message = detail.message
            // Above the body: the mail is asking for a decision, so the decision leads.
            invite?.let {
                InviteCard(
                    invite = it,
                    answered = inviteReply,
                    sending = sendingReply,
                    onAnswer = viewModel::answerInvite,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (message != null) {
                MessageCard(message = message) {
                    // The translation stands in for the body, so it renders through the very
                    // same path — same layout, same inline images, same quote splitting.
                    val shown = translatedBody ?: detail.body
                    if (translatedBody != null || translating) {
                        TranslatedBanner(
                            languageLabel = stringResource(translateLanguage.labelRes),
                            translating = translating,
                            onShowOriginal = viewModel::showOriginal,
                        )
                    }
                    when {
                        loadingBody && shown == null -> BodyLoading()
                        error != null && shown == null -> BodyError(error!!)
                        shown?.html != null -> {
                            val segments = remember(shown.html) {
                                splitConversations(shown.html!!)
                            }
                            BodySegments(segments)
                        }
                        shown?.text != null -> BodyText(shown.text!!)
                        else -> BodyEmpty(stringResource(R.string.no_content))
                    }
                }
            }

            if (detail.attachments.isNotEmpty()) {
                AttachmentsCard(
                    attachments = detail.attachments,
                    downloadingId = downloadingAttachment,
                    // Bytes are fetched on demand: the API ships attachment metadata with
                    // the message and the file itself only when asked for.
                    onOpen = { att ->
                        viewModel.openAttachment(att) { ready -> openAttachment(context, ready) }
                    },
                    onSaveToCloud = { att -> viewModel.saveAttachmentToCloud(att, savedToCloud) },
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MessageCard(
    message: MessageEntity,
    body: @Composable () -> Unit,
) {
    var expanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = message.subject.ifBlank { stringResource(R.string.no_subject) },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.from.ifBlank { stringResource(R.string.unknown_sender) },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                        val dateStr = formatFullDate(message.internalDate)
                        if (dateStr.isNotBlank()) {
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = if (expanded) stringResource(R.string.hide_details) else stringResource(R.string.show_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        message.to?.let {
                            Text(
                                text = stringResource(R.string.to_header, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        message.cc?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.cc_header, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            body()
        }
    }
}

@Composable
internal fun BodySegments(segments: List<ConversationSegment>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        segments.forEachIndexed { index, segment ->
            if (segment.isQuote) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.previous_message),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HtmlPart(html = segment.html)
                    }
                }
            } else {
                HtmlPart(html = segment.html)
            }
            if (index < segments.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/**
 * Sits where the message will change, both while the model works and afterwards. Translating
 * a long mail takes a good few seconds, so it says so plainly instead of leaving the body
 * looking untouched and the user wondering whether the tap registered.
 */
@Composable
private fun TranslatedBanner(
    languageLabel: String,
    translating: Boolean,
    onShowOriginal: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (translating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (translating) {
                        stringResource(R.string.translating_to, languageLabel)
                    } else {
                        stringResource(R.string.translated_to, languageLabel)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                if (translating) {
                    Text(
                        text = stringResource(R.string.translate_wait),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!translating) {
                Text(
                    text = stringResource(R.string.show_original),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onShowOriginal),
                )
            }
        }
    }
}

@Composable
internal fun BodyText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun BodyEmpty(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun HtmlPart(html: String) {
    var contentHeightDp by remember(html) { mutableIntStateOf(0) }
    val baseModifier = Modifier.fillMaxWidth()
    val sizedModifier = if (contentHeightDp > 0) baseModifier.height(contentHeightDp.dp) else baseModifier
    val httpClient = LocalContext.current.mailContainer().httpClient

    AndroidView(
        modifier = sizedModifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.loadsImagesAutomatically = true
                settings.blockNetworkLoads = false
                settings.defaultTextEncodingName = "UTF-8"
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.setSupportZoom(true)
                // useWideViewPort=false → ignore email's own <meta viewport> and lay out
                // at the WebView's actual width. loadWithOverviewMode is the safety net
                // that zooms out if some element still overflows.
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                setBackgroundColor(0x00000000)
                webViewClient = InlineImageWebViewClient(httpClient) { view ->
                    view.post { contentHeightDp = view.contentHeight.coerceAtLeast(40) }
                    view.postDelayed({
                        contentHeightDp = view.contentHeight.coerceAtLeast(40)
                    }, 250)
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, wrapHtmlForViewport(html), "text/html", "UTF-8", null)
        },
    )
}

// Many emails arrive as a full HTML document (Mailchimp/Brevo/etc.). Nesting them inside
// our wrapper produces two stacked documents and the inner <meta viewport> wins, breaking
// the layout. Extract just the styles from <head> and the inner content of <body>.
@Composable
internal fun BodyLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun BodyError(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.error_body), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DetailActionBar(
    isFlagged: Boolean,
    busy: Boolean,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onToggleFlag: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit,
    /** False in a read-only shared folder: every write answers 403 there (§4.20). */
    canWrite: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailAction(
                icon = Icons.AutoMirrored.Filled.Reply,
                label = stringResource(R.string.reply),
                onClick = onReply,
            )
            DetailAction(
                icon = Icons.AutoMirrored.Filled.Send,
                label = stringResource(R.string.forward),
                onClick = onForward,
            )
            // Reply/forward survive read-only shares — they write to the user's own Sent,
            // not to the shared folder. The three below do write here, so they go.
            if (canWrite) {
                DetailAction(
                    // The API calls this pin (§4.6) and so does the webmail; "destacar" made
                    // it read like a separate, second flag next to the colour ones.
                    icon = if (isFlagged) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    label = if (isFlagged) stringResource(R.string.unpin) else stringResource(R.string.pin),
                    tint = if (isFlagged) MaterialTheme.colorScheme.primary else null,
                    onClick = onToggleFlag,
                )
                DetailAction(
                    icon = Icons.Default.MarkEmailUnread,
                    label = stringResource(R.string.mark_unread),
                    onClick = onMarkUnread,
                )
                DetailAction(
                    icon = Icons.Default.Delete,
                    label = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    enabled = !busy,
                    loading = busy,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
internal fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = Modifier.size(44.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = tint ?: MaterialTheme.colorScheme.error,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint ?: MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

