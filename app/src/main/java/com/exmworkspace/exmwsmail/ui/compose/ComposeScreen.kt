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
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ComposeScreen(
    onBack: () -> Unit,
    viewModel: ComposeViewModel = viewModel(factory = ComposeViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val context = LocalContext.current

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addAttachment(context, it) }
    }

    val toListNonEmpty = state.to.isNotEmpty() ||
        state.toDraft.trim().contains("@")
    val canSend = toListNonEmpty && !state.sending

    var bodyFocused by remember { mutableStateOf(false) }
    var formatPanelOpen by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val flyDistancePx = with(density) { (configuration.screenHeightDp + 80).dp.toPx() }
    val translationY = remember { Animatable(0f) }
    val sheetAlpha = remember { Animatable(1f) }

    LaunchedEffect(state.sent) {
        if (state.sent) {
            playSendSound(context)
            launch {
                sheetAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 320),
                )
            }
            translationY.animateTo(
                targetValue = -flyDistancePx,
                animationSpec = tween(durationMillis = 360, easing = FastOutLinearInEasing),
            )
            onBack()
        }
    }

    state.showAddDialogFor?.let { field ->
        AddRecipientDialog(
            field = field,
            onDismiss = viewModel::dismissAddDialog,
            onAdd = { email ->
                viewModel.addRecipient(field, email)
                viewModel.dismissAddDialog()
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 10.dp, end = 10.dp)
                .graphicsLayer {
                    this.translationY = translationY.value
                    this.alpha = sheetAlpha.value
                },
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                SheetHandle()
                TopActionRow(
                    canSend = canSend,
                    sending = state.sending,
                    bodyFocused = bodyFocused,
                    formatActive = formatPanelOpen,
                    onClose = onBack,
                    onSend = viewModel::send,
                    onToggleFormat = { formatPanelOpen = !formatPanelOpen },
                    onAttach = { fileLauncher.launch("*/*") },
                )
                Text(
                    text = stringResource(R.string.new_message),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    RecipientChipsRow(
                    label = stringResource(R.string.to_label),
                        recipients = state.to,
                        draft = state.toDraft,
                        enabled = !state.sending,
                        keyboardType = KeyboardType.Email,
                        onDraftChange = { viewModel.setDraft(RecipientField.TO, it) },
                        onCommitDraft = { viewModel.commitDraft(RecipientField.TO) },
                        onRemoveAt = { viewModel.removeRecipientAt(RecipientField.TO, it) },
                        onBackspaceEmpty = { viewModel.popLastRecipientIfDraftEmpty(RecipientField.TO) },
                        trailing = {
                            AddRecipientButton(onClick = { viewModel.openAddDialog(RecipientField.TO) })
                        },
                    )
                    RowDivider()

                    if (!state.ccBccExpanded) {
                        CollapsedCcBccRow(
                            email = userEmail ?: "",
                            onClick = viewModel::toggleCcBccExpanded,
                        )
                        RowDivider()
                    } else {
                        RecipientChipsRow(
                            label = stringResource(R.string.cc_label),
                            recipients = state.cc,
                            draft = state.ccDraft,
                            enabled = !state.sending,
                            keyboardType = KeyboardType.Email,
                            onDraftChange = { viewModel.setDraft(RecipientField.CC, it) },
                            onCommitDraft = { viewModel.commitDraft(RecipientField.CC) },
                            onRemoveAt = { viewModel.removeRecipientAt(RecipientField.CC, it) },
                            onBackspaceEmpty = {
                                viewModel.popLastRecipientIfDraftEmpty(RecipientField.CC)
                            },
                            trailing = {
                                AddRecipientButton(onClick = {
                                    viewModel.openAddDialog(RecipientField.CC)
                                })
                            },
                        )
                        RowDivider()
                        RecipientChipsRow(
                            label = stringResource(R.string.bcc_label),
                            recipients = state.bcc,
                            draft = state.bccDraft,
                            enabled = !state.sending,
                            keyboardType = KeyboardType.Email,
                            onDraftChange = { viewModel.setDraft(RecipientField.BCC, it) },
                            onCommitDraft = { viewModel.commitDraft(RecipientField.BCC) },
                            onRemoveAt = { viewModel.removeRecipientAt(RecipientField.BCC, it) },
                            onBackspaceEmpty = {
                                viewModel.popLastRecipientIfDraftEmpty(RecipientField.BCC)
                            },
                            trailing = {
                                AddRecipientButton(onClick = {
                                    viewModel.openAddDialog(RecipientField.BCC)
                                })
                            },
                        )
                        RowDivider()
                        StaticInfoRow(
                            label = stringResource(R.string.from_label),
                            value = userEmail ?: "",
                            onClick = viewModel::toggleCcBccExpanded,
                        )
                        RowDivider()
                    }

                    SubjectRow(
                        label = stringResource(R.string.subject_label),
                        value = state.subject,
                        onChange = viewModel::setSubject,
                        enabled = !state.sending,
                    )
                    RowDivider()

                    if (state.attachments.isNotEmpty()) {
                        AttachmentRow(
                            attachments = state.attachments,
                            onRemove = viewModel::removeAttachment
                        )
                        RowDivider()
                    }

                    BodyArea(
                        value = state.body,
                        state = state,
                        onChange = viewModel::setBody,
                        onBackspaceAtLineStart = viewModel::exitListAtCurrentLine,
                        enabled = !state.sending,
                        onFocusChanged = { bodyFocused = it },
                    )
                    state.quotedHtml?.let { quote ->
                        QuotedMessageCard(
                            header = state.quotedHeader,
                            html = quote,
                            onRemove = viewModel::removeQuote,
                        )
                    }
                }

                if (state.error != null) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }

                if (formatPanelOpen) {
                    FormatPanel(
                        viewModel = viewModel,
                        onDismiss = { formatPanelOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 5.dp)
                .background(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(3.dp),
                ),
        )
    }
}

@Composable
private fun TopActionRow(
    canSend: Boolean,
    sending: Boolean,
    bodyFocused: Boolean,
    formatActive: Boolean,
    onClose: () -> Unit,
    onSend: () -> Unit,
    onToggleFormat: () -> Unit,
    onAttach: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            onClick = onClose,
            enabled = true,
            background = MaterialTheme.colorScheme.surfaceContainerHigh,
            iconTint = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        
        CircleIconButton(
            onClick = onAttach,
            enabled = !sending,
            background = MaterialTheme.colorScheme.surfaceContainerHigh,
            iconTint = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = stringResource(R.string.attach_file),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))

        if (bodyFocused) {
            CircleIconButton(
                onClick = onToggleFormat,
                enabled = true,
                background = if (formatActive) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                iconTint = if (formatActive) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(
                    imageVector = Icons.Default.TextFormat,
                    contentDescription = "Formato",
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        CircleIconButton(
            onClick = onSend,
            enabled = canSend,
            background = if (canSend) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            iconTint = if (canSend) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Enviar",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    background: Color,
    iconTint: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        color = background,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides iconTint) {
                content()
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipientChipsRow(
    label: String,
    recipients: List<String>,
    draft: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    onDraftChange: (String) -> Unit,
    onCommitDraft: () -> Unit,
    onRemoveAt: (Int) -> Unit,
    onBackspaceEmpty: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.width(8.dp))
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            recipients.forEachIndexed { index, email ->
                RecipientChip(
                    email = email,
                    onRemove = { onRemoveAt(index) },
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = enabled,
                singleLine = false,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                    autoCorrect = false,
                ),
                keyboardActions = KeyboardActions(onDone = { onCommitDraft() }),
                modifier = Modifier
                    .defaultMinSize(minWidth = 120.dp)
                    .padding(top = 6.dp, bottom = 4.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.Backspace &&
                            draft.isEmpty() &&
                            recipients.isNotEmpty()
                        ) {
                            onBackspaceEmpty()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.padding(top = 4.dp)) {
                trailing()
            }
        }
    }
}

@Composable
private fun RecipientChip(email: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.height(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
            Spacer(Modifier.width(4.dp))
            Surface(
                onClick = onRemove,
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(20.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Eliminar destinatario",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedCcBccRow(email: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Cc/Cco, De:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StaticInfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val mod = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 20.dp, vertical = 14.dp)
    Row(
        modifier = mod,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SubjectRow(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
        )
    }
}

@Composable
private fun AddRecipientButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Añadir destinatario",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun BodyArea(
    value: TextFieldValue,
    state: ComposeUiState,
    onChange: (TextFieldValue) -> Unit,
    onBackspaceAtLineStart: () -> Boolean,
    enabled: Boolean,
    onFocusChanged: (Boolean) -> Unit,
) {
    val transformation = VisualTransformation { input ->
        buildBodyTransformedText(input.text, state)
    }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSynthesis = FontSynthesis.All,
            fontStyle = FontStyle.Normal,
            textDecoration = TextDecoration.None,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = transformation,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .heightIn(min = 280.dp)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace) {
                    val sel = value.selection
                    if (sel.collapsed) {
                        // Determine if cursor is at start of a line.
                        val pos = sel.start
                        val text = value.text
                        val isAtLineStart = pos == 0 ||
                            (pos > 0 && pos <= text.length && text[pos - 1] == '\n') ||
                            pos == text.length && pos == 0
                        if (isAtLineStart && onBackspaceAtLineStart()) {
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            },
    )
}

private data class LinePrefix(val origStart: Int, val origEnd: Int, val prefix: String)

private fun buildBodyTransformedText(
    text: CharSequence,
    state: ComposeUiState,
): TransformedText {
    val len = text.length
    if (len == 0 && state.listSpans.isEmpty()) {
        return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
    }

    // First pass: build line list with prefixes.
    val lines = mutableListOf<LinePrefix>()
    var pos = 0
    var lastSpan: ListSpan? = null
    var counter = 0
    while (pos <= len) {
        val nl = if (pos >= len) -1 else text.toString().indexOf('\n', pos)
        val end = if (nl == -1) len else nl
        val span = state.listSpans.firstOrNull { sp ->
            sp.start <= pos && sp.end >= end
        }
        val prefix = when (span?.kind) {
            ListKind.BULLET -> "•  "
            ListKind.NUMBERED -> {
                if (span !== lastSpan) counter = 0
                counter++
                "$counter.  "
            }
            null -> ""
        }
        lastSpan = span
        lines += LinePrefix(pos, end, prefix)
        if (nl == -1) break
        pos = nl + 1
    }

    // Build displayed text with prefixes inserted at line starts.
    val displayed = StringBuilder()
    val insertions = mutableListOf<Pair<Int, Int>>() // (origLineStart, prefixLen)
    lines.forEachIndexed { i, line ->
        if (line.prefix.isNotEmpty()) {
            insertions += line.origStart to line.prefix.length
            displayed.append(line.prefix)
        }
        displayed.append(text.subSequence(line.origStart, line.origEnd).toString())
        if (i < lines.lastIndex) displayed.append('\n')
    }
    val displayedText = displayed.toString()

    fun mapToTransformed(orig: Int): Int {
        val clamped = orig.coerceIn(0, len)
        var t = clamped
        for ((start, plen) in insertions) {
            if (start <= clamped) t += plen
        }
        return t
    }

    fun mapToOriginal(transformed: Int): Int {
        var t = transformed.coerceAtLeast(0)
        var crossed = 0
        for ((start, plen) in insertions) {
            val transformedStart = start + crossed
            if (transformedStart > t) break
            if (transformedStart + plen > t) {
                return start
            }
            crossed += plen
        }
        return (t - crossed).coerceIn(0, len)
    }

    val builder = AnnotatedString.Builder(displayedText)

    // Style the prefixes themselves with a muted color so they read as decorations.
    for ((start, plen) in insertions) {
        val tStart = mapToTransformed(start) - plen
        val tEnd = mapToTransformed(start)
        if (tEnd > tStart) {
            builder.addStyle(
                SpanStyle(color = Color(0xFF6B7280)),
                tStart,
                tEnd,
            )
        }
    }

    // Inline spans (B/I/U/S/color/size/family) per maximal range over original text.
    if (len > 0) {
        val origBoundaries = sortedSetOf(0, len)
        state.bodyStyles.forEach {
            origBoundaries += it.start.coerceIn(0, len); origBoundaries += it.end.coerceIn(0, len)
        }
        state.colorSpans.forEach {
            origBoundaries += it.start.coerceIn(0, len); origBoundaries += it.end.coerceIn(0, len)
        }
        state.sizeSpans.forEach {
            origBoundaries += it.start.coerceIn(0, len); origBoundaries += it.end.coerceIn(0, len)
        }
        state.familySpans.forEach {
            origBoundaries += it.start.coerceIn(0, len); origBoundaries += it.end.coerceIn(0, len)
        }
        val sortedB = origBoundaries.toList()
        for (i in 0 until sortedB.lastIndex) {
            val origRs = sortedB[i]
            val origRe = sortedB[i + 1]
            if (origRs >= origRe) continue
            val mid = origRs
            val activeBI = state.bodyStyles.filter { mid in it.start until it.end }
                .map { it.type }.toSet()
            val color = state.colorSpans.firstOrNull { mid in it.start until it.end }?.argb
            val size = state.sizeSpans.firstOrNull { mid in it.start until it.end }?.sp
            val family = state.familySpans.firstOrNull { mid in it.start until it.end }?.family
            val hasAny = activeBI.isNotEmpty() || color != null || size != null || family != null
            if (!hasAny) continue
            val deco = when {
                StyleType.UNDERLINE in activeBI && StyleType.STRIKE in activeBI ->
                    TextDecoration.combine(
                        listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                    )
                StyleType.UNDERLINE in activeBI -> TextDecoration.Underline
                StyleType.STRIKE in activeBI -> TextDecoration.LineThrough
                else -> null
            }
            val style = SpanStyle(
                fontWeight = if (StyleType.BOLD in activeBI) FontWeight.Bold else null,
                fontStyle = if (StyleType.ITALIC in activeBI) FontStyle.Italic else null,
                textDecoration = deco,
                color = color?.let { Color(it) } ?: Color.Unspecified,
                fontSize = size?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                fontFamily = when (family) {
                    FontFamilyChoice.SERIF -> FontFamily.Serif
                    FontFamilyChoice.MONO -> FontFamily.Monospace
                    FontFamilyChoice.DEFAULT -> FontFamily.SansSerif
                    null -> null
                },
                fontSynthesis = FontSynthesis.All,
            )
            builder.addStyle(style, mapToTransformed(origRs), mapToTransformed(origRe))
        }
    }

    // Paragraph alignment, mapped to displayed positions.
    state.alignSpans.forEach { sp ->
        val s = sp.start.coerceIn(0, len)
        val e = sp.end.coerceIn(0, len)
        if (s < e) {
            val align = when (sp.align) {
                TextAlignChoice.LEFT -> TextAlign.Start
                TextAlignChoice.CENTER -> TextAlign.Center
                TextAlignChoice.RIGHT -> TextAlign.End
            }
            builder.addStyle(
                ParagraphStyle(textAlign = align),
                mapToTransformed(s),
                mapToTransformed(e),
            )
        }
    }

    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = mapToTransformed(offset)
        override fun transformedToOriginal(offset: Int): Int = mapToOriginal(offset)
    }
    return TransformedText(builder.toAnnotatedString(), mapping)
}

@Composable
private fun FormatPanel(
    viewModel: ComposeViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val currentFamily = viewModel.currentFamily()
    val currentColor = viewModel.currentColor()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.format_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                CircleIconButton(
                    onClick = onDismiss,
                    enabled = true,
                    background = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // Row 1: B I U S
            StyleSegmentedRow(
                isActive = { viewModel.isStyleActiveOnSelection(it) },
                onToggle = viewModel::toggleStyle,
            )
            // Row 2: Family pill | − | + | color
            FontAndSizeRow(
                family = currentFamily,
                onPickFamily = viewModel::setFamily,
                onDecreaseSize = { viewModel.changeSize(-2) },
                onIncreaseSize = { viewModel.changeSize(+2) },
                currentColor = currentColor,
                onPickColor = viewModel::setColor,
            )
            // Row 3: bullet | numbered | left | center | right
            ListAndAlignRow(
                isBullet = viewModel.isListActive(ListKind.BULLET),
                isNumbered = viewModel.isListActive(ListKind.NUMBERED),
                onBullet = { viewModel.toggleList(ListKind.BULLET) },
                onNumbered = { viewModel.toggleList(ListKind.NUMBERED) },
                isLeft = viewModel.isAlignActive(TextAlignChoice.LEFT),
                isCenter = viewModel.isAlignActive(TextAlignChoice.CENTER),
                isRight = viewModel.isAlignActive(TextAlignChoice.RIGHT),
                onLeft = { viewModel.setAlignment(TextAlignChoice.LEFT) },
                onCenter = { viewModel.setAlignment(TextAlignChoice.CENTER) },
                onRight = { viewModel.setAlignment(TextAlignChoice.RIGHT) },
            )
        }
    }
}

@Composable
private fun StyleSegmentedRow(
    isActive: (StyleType) -> Boolean,
    onToggle: (StyleType) -> Unit,
) {
    val items = listOf(
        Triple(Icons.Default.FormatBold, R.string.style_bold, StyleType.BOLD),
        Triple(Icons.Default.FormatItalic, R.string.style_italic, StyleType.ITALIC),
        Triple(Icons.Default.FormatUnderlined, R.string.style_underline, StyleType.UNDERLINE),
        Triple(Icons.Default.StrikethroughS, R.string.style_strike, StyleType.STRIKE),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (icon, labelResId, type) ->
            StyleSegment(
                icon = icon,
                labelResId = labelResId,
                active = isActive(type),
                onClick = { onToggle(type) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StyleSegment(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelResId: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surface,
        tonalElevation = if (active) 0.dp else 1.dp,
        shadowElevation = if (active) 0.dp else 1.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(labelResId),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun FontAndSizeRow(
    family: FontFamilyChoice,
    onPickFamily: (FontFamilyChoice) -> Unit,
    onDecreaseSize: () -> Unit,
    onIncreaseSize: () -> Unit,
    currentColor: Long?,
    onPickColor: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FontFamilyPill(
            current = family,
            onPick = onPickFamily,
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeightOf(46),
        )
        SizeSegmentedPair(
            onDecrease = onDecreaseSize,
            onIncrease = onIncreaseSize,
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeightOf(46),
        )
        ColorPickerButton(
            current = currentColor,
            onPick = onPickColor,
            modifier = Modifier
                .size(46.dp),
        )
    }
}

private fun Modifier.fillMaxHeightOf(dpValue: Int): Modifier = this.height(dpValue.dp)

@Composable
private fun FontFamilyPill(
    current: FontFamilyChoice,
    onPick: (FontFamilyChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { expanded = true },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.font_family, current.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            FontFamilyChoice.entries.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = choice.displayName,
                            fontFamily = when (choice) {
                                FontFamilyChoice.SERIF -> FontFamily.Serif
                                FontFamilyChoice.MONO -> FontFamily.Monospace
                                FontFamilyChoice.DEFAULT -> FontFamily.SansSerif
                            },
                        )
                    },
                    onClick = {
                        onPick(choice)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SizeSegmentedPair(
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StyleSegment(
            icon = Icons.Default.Remove,
            labelResId = R.string.font_size_decrease,
            active = false,
            onClick = onDecrease,
            modifier = Modifier.weight(1f),
        )
        StyleSegment(
            icon = Icons.Default.Add,
            labelResId = R.string.font_size_increase,
            active = false,
            onClick = onIncrease,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ColorPickerButton(
    current: Long?,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = current ?: ColorPalette.first()
    Surface(
        onClick = { expanded = true },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Color(display), shape = CircleShape),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorPalette.forEach { argb ->
                    Surface(
                        onClick = {
                            onPick(argb)
                            expanded = false
                        },
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = Color(argb),
                        border = if (argb == current)
                            androidx.compose.foundation.BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.onSurface,
                            ) else null,
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun ListAndAlignRow(
    isBullet: Boolean,
    isNumbered: Boolean,
    onBullet: () -> Unit,
    onNumbered: () -> Unit,
    isLeft: Boolean,
    isCenter: Boolean,
    isRight: Boolean,
    onLeft: () -> Unit,
    onCenter: () -> Unit,
    onRight: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StyleSegment(
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            labelResId = R.string.list_bullet,
            active = isBullet,
            onClick = onBullet,
            modifier = Modifier.weight(1f),
        )
        StyleSegment(
            icon = Icons.Default.FormatListNumbered,
            labelResId = R.string.list_numbered,
            active = isNumbered,
            onClick = onNumbered,
            modifier = Modifier.weight(1f),
        )
        StyleSegment(
            icon = Icons.Default.FormatAlignLeft,
            labelResId = R.string.align_left,
            active = isLeft,
            onClick = onLeft,
            modifier = Modifier.weight(1f),
        )
        StyleSegment(
            icon = Icons.Default.FormatAlignCenter,
            labelResId = R.string.align_center,
            active = isCenter,
            onClick = onCenter,
            modifier = Modifier.weight(1f),
        )
        StyleSegment(
            icon = Icons.Default.FormatAlignRight,
            labelResId = R.string.align_right,
            active = isRight,
            onClick = onRight,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AttachmentRow(
    attachments: List<FileAttachment>,
    onRemove: (FileAttachment) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { att ->
            AttachmentChip(att, onRemove = { onRemove(att) })
        }
    }
}

@Composable
private fun AttachmentChip(att: FileAttachment, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(32.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = att.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Spacer(Modifier.width(4.dp))
            Surface(
                onClick = onRemove,
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                color = Color.Transparent
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
@Composable
private fun AddRecipientDialog(
    field: RecipientField,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch { focusRequester.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (field) {
                    RecipientField.TO -> stringResource(R.string.add_recipient)
                    RecipientField.CC -> stringResource(R.string.add_cc)
                    RecipientField.BCC -> stringResource(R.string.add_bcc)
                },
            )
        },
        text = {
            ExmField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                placeholder = "nombre@dominio.com",
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onAdd(email) }),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(email) },
                enabled = email.contains("@"),
            ) {
                Text("Añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun QuotedMessageCard(
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
                settings.loadWithOverviewMode = false
                isVerticalScrollBarEnabled = false
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
            webView.loadDataWithBaseURL(null, wrapQuotedHtml(html), "text/html", "UTF-8", null)
        },
    )
}

private fun playSendSound(context: android.content.Context) {
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

private fun wrapQuotedHtml(html: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
      html, body { margin: 0; padding: 0; background: transparent; }
      body { font-family: sans-serif; font-size: 14px; line-height: 1.4; padding: 12px; word-wrap: break-word; overflow-wrap: anywhere; color: #1f2937; }
      img { max-width: 100%; height: auto; }
      table { max-width: 100%; }
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
