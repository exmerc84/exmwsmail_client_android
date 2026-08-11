package com.exmworkspace.exmwsmail.ui.attachments

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.data.mail.AttachmentType
import com.exmworkspace.exmwsmail.data.mail.attachmentTypeOf
import com.exmworkspace.exmwsmail.data.remote.dto.AttachmentBrowseDto
import com.exmworkspace.exmwsmail.ui.components.ExmField
import com.exmworkspace.exmwsmail.ui.components.SkeletonMessageList
import com.exmworkspace.exmwsmail.ui.shell.AppBottomBar
import com.exmworkspace.exmwsmail.ui.shell.AppModule
import com.exmworkspace.exmwsmail.ui.shell.BottomAction
import com.exmworkspace.exmwsmail.ui.shell.BottomSearch
import com.exmworkspace.exmwsmail.ui.mail.folderNameLabel
import com.exmworkspace.exmwsmail.ui.mail.formatBytes
import com.exmworkspace.exmwsmail.ui.theme.Tint
import com.exmworkspace.exmwsmail.ui.theme.TintAmber
import com.exmworkspace.exmwsmail.ui.theme.TintCyan
import com.exmworkspace.exmwsmail.ui.theme.TintGreen
import com.exmworkspace.exmwsmail.ui.theme.TintPlum
import com.exmworkspace.exmwsmail.ui.theme.TintRose
import com.exmworkspace.exmwsmail.ui.theme.TintStone
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Every attachment in the mailbox (§4.17), searchable, with save-to-cloud per row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentsScreen(
    onBack: () -> Unit,
    onOpenContacts: () -> Unit = {},
    viewModel: AttachmentsViewModel = viewModel(factory = AttachmentsViewModel.Factory),
) {
    val items by viewModel.items.collectAsState()
    val query by viewModel.query.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val notice by viewModel.notice.collectAsState()

    val opening by viewModel.opening.collectAsState()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val savedLabel = stringResource(R.string.saved_to_cloud)
    val context = LocalContext.current

    val todayLabel = stringResource(R.string.today)
    val yesterdayLabel = stringResource(R.string.yesterday)
    val undatedLabel = stringResource(R.string.undated)
    val groups = remember(items, todayLabel) {
        groupAttachmentsByDate(
            items = items,
            now = java.util.Date(),
            todayLabel = todayLabel,
            yesterdayLabel = yesterdayLabel,
            undatedLabel = undatedLabel,
        )
    }

    LaunchedEffect(listState) {
        snapshotOfLastIndex(listState)
            .distinctUntilChanged()
            .filter { (last, total) -> total > 0 && last >= total - 3 }
            .collect { viewModel.loadMore() }
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.attachments)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            AppBottomBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(
                    start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp,
                ),
                active = AppModule.ATTACHMENTS,
                search = BottomSearch.Inline(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.attachments_search_hint),
                ),
                // Attachments arrive with the mail; there is nothing to create here.
                action = null,
                onOpenMail = onBack,
                onOpenContacts = onOpenContacts,
                onOpenAttachments = {},
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                when {
                    // Ghost cards, like the mail list: shape while loading, not a lone dot.
                    loading && items.isEmpty() -> SkeletonMessageList()

                    items.isEmpty() -> Text(
                        text = stringResource(R.string.no_attachments),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 8.dp, end = 8.dp, top = 4.dp, bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        groups.forEach { group ->
                            item(key = "header-${group.label}") {
                                Text(
                                    text = group.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                                )
                            }
                            items(
                                items = group.items,
                                key = { "${it.folder}/${it.uid}/${it.index}" },
                            ) { item ->
                                AttachmentBrowseRow(
                                    item = item,
                                    downloading = opening == "${item.folder}/${item.uid}/${item.index}",
                                    onOpen = {
                                        viewModel.open(item) { file, mime ->
                                            openDownloadedFile(context, file, mime)
                                        }
                                    },
                                    onSaveToCloud = { viewModel.saveToCloud(item, savedLabel) },
                                )
                            }
                        }
                        if (loadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentBrowseRow(
    item: AttachmentBrowseDto,
    downloading: Boolean,
    onOpen: () -> Unit,
    onSaveToCloud: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Colour by family (PDF rose, sheets green…) so the eye can sweep the list for
            // "the PDF from yesterday" without reading a single filename.
            val type = attachmentTypeOf(item.contentType, item.filename)
            val tint = type.tint
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = tint.container,
                modifier = Modifier.size(38.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = tint.content,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subject.ifBlank { item.fromName ?: item.fromAddress.orEmpty() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // Localized: this payload only carries the raw IMAP name, and "INBOX"
                    // between two Spanish lines read as a glitch.
                    text = "${formatBytes(item.size)} · ${folderNameLabel(item.folder)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (downloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
            } else {
                // One visible action, like the contact rows: tapping the row already opens
                // the file, so the open button restated it and crowded the line.
                IconButton(onClick = onSaveToCloud) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = stringResource(R.string.save_to_cloud),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private val AttachmentType.icon: ImageVector
    get() = when (this) {
        AttachmentType.IMAGE -> Icons.Default.Image
        AttachmentType.PDF -> Icons.Default.PictureAsPdf
        AttachmentType.SHEET -> Icons.Default.TableChart
        AttachmentType.DOCUMENT -> Icons.Default.Description
        AttachmentType.ARCHIVE -> Icons.Default.FolderZip
        AttachmentType.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

private val AttachmentType.tint: Tint
    get() = when (this) {
        AttachmentType.IMAGE -> TintPlum
        AttachmentType.PDF -> TintRose
        AttachmentType.SHEET -> TintGreen
        AttachmentType.DOCUMENT -> TintCyan
        AttachmentType.ARCHIVE -> TintAmber
        AttachmentType.OTHER -> TintStone
    }

/** Hands the downloaded part to whatever app can display it, like the mail screen does. */
private fun openDownloadedFile(context: Context, file: java.io.File, mime: String?) {
    if (!file.exists()) return
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime?.takeIf { it.isNotBlank() } ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.open_with)),
        )
    }
}

private fun snapshotOfLastIndex(state: androidx.compose.foundation.lazy.LazyListState) =
    androidx.compose.runtime.snapshotFlow {
        val info = state.layoutInfo
        (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
    }
