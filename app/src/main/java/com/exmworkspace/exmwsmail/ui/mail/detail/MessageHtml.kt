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

// Email-HTML sanitizing, viewport wrapping, and quote splitting.
// Extracted from MessageDetailScreen.

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

internal fun wrapHtmlForViewport(html: String): String {
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
        /* White, not transparent — and white in dark mode too. A message's HTML picks its
           own colours for a light background (this one hard-codes #1f2937 text, senders do
           the same), so letting the app's surface show through turned every forwarded header
           into dark-grey text on a dark-grey screen. Rendering mail on "paper" is what Gmail
           and Outlook do for exactly this reason: the sender's markup cannot be re-themed
           safely. */
        background: #ffffff !important;
        width: 100% !important;
        max-width: 100% !important;
        overflow-x: hidden !important;
      }
      html { padding: 0 !important; }
      body {
        font-family: sans-serif;
        font-size: 15px;
        line-height: 1.6;
        /* Must outrank the sender's own "body { padding: 0 }" — and note that our reset
           above used to carry !important on padding, which silently cancelled this and
           left plain-text mail flush against the card edge. */
        padding: 16px !important;
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

