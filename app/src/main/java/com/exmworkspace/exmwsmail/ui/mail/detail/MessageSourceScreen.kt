package com.exmworkspace.exmwsmail.ui.mail.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.ui.mailContainer
import kotlinx.coroutines.launch

/** What the screen is showing — the whole message, or only its headers (§4.17). */
enum class SourceMode { SOURCE, HEADERS }

/**
 * "Ver original": the message exactly as it arrived. Monospaced and scrollable in both
 * directions, since header lines are long and must not be re-wrapped to stay readable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSourceScreen(
    messageId: Long,
    mode: SourceMode,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = context.mailContainer().mailRepository
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var text by remember(messageId, mode) { mutableStateOf<String?>(null) }
    var error by remember(messageId, mode) { mutableStateOf<String?>(null) }

    LaunchedEffect(messageId, mode) {
        try {
            val message = repository.findMessage(messageId) ?: error("Mensaje no encontrado")
            text = when (mode) {
                SourceMode.SOURCE -> repository.messageSource(message)
                SourceMode.HEADERS -> repository.messageHeaders(message)
            }
        } catch (e: Exception) {
            error = e.message ?: e::class.java.simpleName
        }
    }

    val copiedLabel = stringResource(R.string.copied)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (mode == SourceMode.SOURCE) R.string.view_source
                            else R.string.view_headers
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    val body = text
                    if (!body.isNullOrEmpty()) {
                        IconButton(
                            onClick = {
                                copyToClipboard(context, body)
                                scope.launch { snackbar.showSnackbar(copiedLabel) }
                            },
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                error != null -> Text(
                    text = error!!,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )

                text == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text = text!!,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("EXM WS Mail", value))
}
