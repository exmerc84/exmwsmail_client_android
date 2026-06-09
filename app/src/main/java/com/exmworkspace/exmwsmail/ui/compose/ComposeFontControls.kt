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

// Font family, size, and text color controls. Extracted from ComposeFormatPanel.

@Composable
internal fun FontAndSizeRow(
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
internal fun ColorPickerButton(
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

