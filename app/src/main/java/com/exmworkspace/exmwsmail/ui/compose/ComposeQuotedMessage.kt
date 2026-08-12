package com.exmworkspace.exmwsmail.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.data.mail.FileAttachment
import androidx.compose.material.icons.filled.AttachFile
import android.media.MediaPlayer
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exmworkspace.exmwsmail.ui.components.ExmField
import com.exmworkspace.exmwsmail.ui.mail.detail.InlineImageWebViewClient
import com.exmworkspace.exmwsmail.ui.mailContainer
import kotlinx.coroutines.launch

// Quoted-original card, its HTML viewer, and the send sound helper.
// Extracted from ComposeScreen.

@Composable
internal fun QuotedMessageCard(
    header: String?,
    html: String,
    onRemove: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = header ?: stringResource(R.string.original_message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = onRemove,
                    modifier = Modifier.size(26.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Quitar mensaje original",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            QuotedHtmlView(html = html)
        }
    }
}

@Composable
private fun QuotedHtmlView(html: String) {
    var contentHeightDp by remember(html) { mutableIntStateOf(0) }
    val baseModifier = Modifier.fillMaxWidth()
    val sizedModifier = if (contentHeightDp > 0)
        baseModifier.height(contentHeightDp.dp) else baseModifier
    val httpClient = androidx.compose.ui.platform.LocalContext.current.mailContainer().httpClient
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
                settings.useWideViewPort = false
                // The same safety net the detail view uses: if some element still overflows,
                // the page zooms out instead of laying out wider than the view.
                settings.loadWithOverviewMode = true
                isVerticalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                setBackgroundColor(0xFFFFFFFF.toInt())
                // The quoted body carries the original's inline images as API URLs, which
                // only load with the session's Bearer token.
                webViewClient = InlineImageWebViewClient(httpClient) { view ->
                    view.post { contentHeightDp = view.measuredContentHeightDp() }
                    view.postDelayed({
                        contentHeightDp = view.measuredContentHeightDp()
                    }, 250)
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, wrapQuotedHtml(html), "text/html", "UTF-8", null)
        },
    )
}

internal fun playSendSound(context: android.content.Context) {
    val resId = context.resources.getIdentifier("send_swoosh", "raw", context.packageName)
    if (resId == 0) return
    runCatching {
        MediaPlayer.create(context, resId)?.apply {
            setOnCompletionListener { runCatching { release() } }
            setOnErrorListener { mp, _, _ -> runCatching { mp.release() }; true }
            setVolume(0.9f, 0.9f)
            start()
        }
    }
}

/**
 * Height the quote actually occupies, in dp.
 *
 * `contentHeight` is in CSS pixels at scale 1; once `loadWithOverviewMode` zooms a wide mail
 * out, the rendered height is that number times the scale. Reading it raw is what made the
 * card balloon on exactly the mails that needed zooming out. Capped as a last resort so a
 * pathological page cannot hand back a card thousands of dp tall.
 */
private fun WebView.measuredContentHeightDp(): Int {
    @Suppress("DEPRECATION")
    val scaled = (contentHeight * scale).toInt()
    return scaled.coerceIn(40, MAX_QUOTE_HEIGHT_DP)
}

private const val MAX_QUOTE_HEIGHT_DP = 2400

private fun wrapQuotedHtml(html: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
      /* White, like the message detail: the original's markup picks its colours for a light
         background, and on a dark theme a transparent body left it grey-on-grey. */
      html, body { margin: 0; padding: 0; background: #ffffff; width: 100%; max-width: 100%; overflow-x: hidden; }
      body { font-family: sans-serif; font-size: 14px; line-height: 1.4; padding: 12px; word-wrap: break-word; overflow-wrap: anywhere; color: #1f2937; }
      /* Cap every element, the way the detail view does. Without this a marketing mail with
         a fixed 600px table laid out at its natural width, and the height the WebView then
         reported stretched the quote card far past its actual content — a tall empty box. */
      * { max-width: 100% !important; box-sizing: border-box !important; }
      img { max-width: 100%; height: auto; }
      table { max-width: 100%; table-layout: fixed; }
      pre { white-space: pre-wrap; word-wrap: break-word; }
      blockquote { margin-left: 8px; padding-left: 8px; border-left: 2px solid #cbd5e1; color: #4b5563; }
      a { color: #1a73e8; }
    </style>
    </head>
    <body>
    $html
    </body>
    </html>
""".trimIndent()
