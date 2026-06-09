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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    viewModel: MessageDetailViewModel = viewModel(factory = MessageDetailViewModel.factoryFor(messageId)),
) {
    val detail by viewModel.detail.collectAsState()
    val loadingBody by viewModel.loadingBody.collectAsState()
    val error by viewModel.error.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    Scaffold(
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
            if (message != null) {
                MessageCard(message = message) {
                    when {
                        loadingBody && detail.body == null -> BodyLoading()
                        error != null && detail.body == null -> BodyError(error!!)
                        detail.body?.html != null -> {
                            val segments = remember(detail.body!!.html) {
                                splitConversations(detail.body!!.html!!)
                            }
                            BodySegments(segments)
                        }
                        detail.body?.text != null -> BodyText(detail.body!!.text!!)
                        else -> BodyEmpty(stringResource(R.string.no_content))
                    }
                }
            }

            if (detail.attachments.isNotEmpty()) {
                AttachmentsCard(
                    attachments = detail.attachments,
                    onOpen = { att -> openAttachment(context, att) },
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
private fun BodySegments(segments: List<ConversationSegment>) {
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

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun BodyEmpty(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HtmlPart(html: String) {
    var contentHeightDp by remember(html) { mutableIntStateOf(0) }
    val baseModifier = Modifier.fillMaxWidth()
    val sizedModifier = if (contentHeightDp > 0) baseModifier.height(contentHeightDp.dp) else baseModifier

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
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.post { contentHeightDp = view.contentHeight.coerceAtLeast(40) }
                        view.postDelayed({
                            contentHeightDp = view.contentHeight.coerceAtLeast(40)
                        }, 250)
                    }
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
private fun sanitizeEmailHtml(raw: String): String {
    val bodyMatch = Regex("(?is)<body[^>]*>(.*?)</body>").find(raw)
    val content = if (bodyMatch != null) {
        val bodyInner = bodyMatch.groupValues[1]
        val headMatch = Regex("(?is)<head[^>]*>(.*?)</head>").find(raw)
        val headStyles = headMatch?.groupValues?.get(1)?.let { head ->
            Regex("(?is)<style[^>]*>.*?</style>").findAll(head).joinToString("\n") { it.value }
        } ?: ""
        headStyles + bodyInner
    } else raw
    return stripFixedDimensions(content)
}

// max-width:100% caps an element visually but the underlying width attribute (or inline
// style="width:600px") still drives table-layout, so a hero <img width="800"> can still
// push its <td>/<table> beyond the viewport. Strip those explicit pixel sizes so the
// browser falls back to natural dimensions, which our CSS then constrains to 100%.
private fun stripFixedDimensions(html: String): String {
    var out = html
    // HTML width/height attributes with numeric or pixel values. Keep "100%", "auto", etc.
    val attrRegex = Regex(
        """(?i)\s+(?:width|height)\s*=\s*(?:"\s*\d+(?:\s*px)?\s*"|'\s*\d+(?:\s*px)?\s*'|\d+(?:px)?(?=[\s/>]))"""
    )
    out = attrRegex.replace(out, "")
    // Inline CSS: strip "width: NNNpx" and "min-width: NNNpx" but keep "max-width".
    val styleWidthRegex = Regex(
        """(?i)(?<![a-z-])(?:min-)?width\s*:\s*\d+(?:\.\d+)?\s*px\s*;?"""
    )
    out = styleWidthRegex.replace(out, "")
    return out
}

private fun wrapHtmlForViewport(html: String): String {
    val safe = sanitizeEmailHtml(html)
    return """
    <!DOCTYPE html>
    <html>
    <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0">
    <style>
      html, body {
        margin: 0 !important;
        padding: 0 !important;
        background: transparent !important;
        width: 100% !important;
        max-width: 100% !important;
        overflow-x: hidden !important;
      }
      body {
        font-family: sans-serif;
        font-size: 16px;
        line-height: 1.5;
        padding: 16px;
        color: #1f2937;
        word-wrap: break-word;
        overflow-wrap: anywhere;
        word-break: break-word;
      }
      /* Cap every element so a stray fixed width can't push the page wider than the WebView. */
      * {
        max-width: 100% !important;
        box-sizing: border-box !important;
      }
      img, video, iframe {
        max-width: 100% !important;
        height: auto !important;
        border-radius: 8px;
      }
      /* Neutralize HTML width attributes and inline min-widths typical of email templates. */
      table, td, th, tr {
        max-width: 100% !important;
        min-width: 0 !important;
        height: auto !important;
      }
      table[width], td[width], th[width] {
        width: auto !important;
      }
      table {
        border-collapse: collapse;
      }
      td, th {
        word-break: break-word !important;
        overflow-wrap: anywhere !important;
      }
      pre {
        white-space: pre-wrap;
        word-wrap: break-word;
        background: #f3f4f6;
        padding: 12px;
        border-radius: 6px;
        font-size: 14px;
        max-width: 100% !important;
        overflow-x: auto;
      }
      blockquote {
        margin: 12px 0;
        padding-left: 12px;
        border-left: 3px solid #e5e7eb;
        color: #6b7280;
      }
      a { color: #2563eb; text-decoration: none; word-break: break-all; }
    </style>
    </head>
    <body>
    $safe
    </body>
    </html>
    """.trimIndent()
}

internal data class ConversationSegment(val html: String, val isQuote: Boolean)

internal fun splitConversations(html: String): List<ConversationSegment> {
    val result = mutableListOf<ConversationSegment>()
    val len = html.length
    var depth = 0
    var bqOpenStart = -1
    var bqInnerStart = -1
    var lastEnd = 0
    var i = 0

    while (i < len) {
        val ch = html[i]
        if (ch == '<') {
            val end = html.indexOf('>', i)
            if (end < 0) break
            val inside = html.substring(i + 1, end)
            val isClose = inside.startsWith("/")
            val nameStart = if (isClose) 1 else 0
            var nameEnd = nameStart
            while (nameEnd < inside.length && inside[nameEnd].isLetterOrDigit()) nameEnd++
            val tagName = inside.substring(nameStart, nameEnd).lowercase()

            if (tagName == "blockquote") {
                if (!isClose) {
                    if (depth == 0) {
                        val pre = html.substring(lastEnd, i).trim()
                        if (pre.isNotEmpty()) result.add(ConversationSegment(pre, false))
                        bqOpenStart = i
                        bqInnerStart = end + 1
                    }
                    depth++
                } else if (depth > 0) {
                    depth--
                    if (depth == 0 && bqOpenStart >= 0) {
                        val inner = html.substring(bqInnerStart, i).trim()
                        if (inner.isNotEmpty()) result.add(ConversationSegment(inner, true))
                        bqOpenStart = -1
                        bqInnerStart = -1
                        lastEnd = end + 1
                    }
                }
            }
            i = end + 1
        } else {
            i++
        }
    }

    if (lastEnd < len) {
        val tail = html.substring(lastEnd).trim()
        if (tail.isNotEmpty()) result.add(ConversationSegment(tail, false))
    }

    return if (result.isEmpty()) listOf(ConversationSegment(html, false)) else result
}

@Composable
private fun BodyLoading() {
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
private fun BodyError(message: String) {
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
private fun AttachmentsCard(
    attachments: List<AttachmentEntity>,
    onOpen: (AttachmentEntity) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.attachments_title, attachments.size),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            attachments.forEach { att ->
                AttachmentRow(att, onOpen = { onOpen(att) })
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AttachmentRow(att: AttachmentEntity, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = att.filename,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = "${formatSize(att.sizeBytes)} · ${att.mimeType}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onOpen) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.open_attachment)
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 1024.0 && unit < units.lastIndex) {
        size /= 1024.0
        unit++
    }
    return if (unit == 0) "${bytes} ${units[unit]}"
    else String.format(Locale.getDefault(), "%.1f %s", size, units[unit])
}

private fun formatFullDate(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val fmt = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
    return fmt.format(Date(epochMs))
}

private fun openAttachment(context: Context, att: AttachmentEntity) {
    val file = File(att.localPath)
    if (!file.exists()) return
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, att.mimeType.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_with)))
    }
}

@Composable
private fun DetailActionBar(
    isFlagged: Boolean,
    busy: Boolean,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onToggleFlag: () -> Unit,
    onMarkUnread: () -> Unit,
    onDelete: () -> Unit,
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
            DetailAction(
                icon = if (isFlagged) Icons.Default.Star else Icons.Default.StarBorder,
                label = if (isFlagged) stringResource(R.string.unstar) else stringResource(R.string.star),
                tint = if (isFlagged) MaterialTheme.colorScheme.tertiary else null,
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

@Composable
private fun DetailAction(
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

