package com.example.exmwsmail.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.res.stringResource
import com.example.exmwsmail.R
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.exmwsmail.data.local.entity.FolderEntity
import com.example.exmwsmail.data.local.entity.MessageEntity
import com.example.exmwsmail.data.mail.FolderKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScreen(
    onOpenMessage: (Long) -> Unit,
    onCompose: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    viewModel: MailViewModel = viewModel(factory = MailViewModel.Factory),
) {
    val folders by viewModel.folders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val loadingOlder by viewModel.loadingOlder.collectAsState()
    val endReached by viewModel.endReached.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val displayName by viewModel.displayName.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sortedFolders = remember(folders) { folders.sortedForDrawer() }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedFolder?.id) {
        listState.scrollToItem(0)
    }

    // When a brand-new message arrives (top message id changes) and the user is still
    // near the top of the list, jump to position 0 so they actually see it. If they're
    // scrolled down reading older mail we don't disturb them.
    val topMessageId = messages.firstOrNull()?.id
    LaunchedEffect(topMessageId) {
        if (topMessageId == null) return@LaunchedEffect
        if (listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(listState, messages.size, endReached) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (last, total) -> total > 0 && last >= total - 5 }
            .collect {
                if (!endReached) viewModel.loadMore()
            }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 8.dp, top = 12.dp, bottom = 12.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp)),
                drawerShape = RoundedCornerShape(20.dp),
                drawerTonalElevation = 4.dp,
                windowInsets = WindowInsets(0),
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.folders_title),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        FolderListCard(
                            folders = sortedFolders,
                            selectedId = selectedFolder?.id,
                            onSelect = { folder ->
                                viewModel.selectFolder(folder.id)
                                scope.launch { drawerState.close() }
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UserCard(displayName = displayName, email = userEmail)
                        DrawerActionCard(
                            icon = Icons.Default.Settings,
                            label = stringResource(R.string.settings),
                            onClick = {
                                scope.launch { drawerState.close() }
                                onOpenSettings()
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selectedFolder?.let { folderLabel(it) } ?: "EXM WS Mail") },
                    navigationIcon = {
                        Surface(
                            onClick = { scope.launch { drawerState.open() } },
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
                                    contentDescription = stringResource(R.string.folders_title),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::refresh,
                            enabled = !refreshing,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    },
                )
            },
        ) { padding ->
            val hazeState = remember { HazeState() }
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (selectedFolder?.kind == com.example.exmwsmail.data.mail.FolderKind.INBOX) {
                        CategoryFilterRow(
                            selected = selectedCategory,
                            onSelect = viewModel::selectCategory,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .hazeSource(hazeState),
                    ) {
                        PullToRefreshBox(
                            isRefreshing = refreshing,
                            onRefresh = viewModel::refresh,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when {
                                messages.isEmpty() && refreshing -> CenteredLoading()
                                messages.isEmpty() && error != null -> CenteredError(
                                    error!!,
                                    viewModel::refresh
                                )
                                messages.isEmpty() -> CenteredText(stringResource(R.string.no_messages))
                                else -> MessageList(
                                    messages = messages,
                                    listState = listState,
                                    loadingOlder = loadingOlder,
                                    endReached = endReached,
                                    onOpen = onOpenMessage,
                                    onDelete = viewModel::deleteMessage,
                                    onToggleRead = viewModel::toggleRead,
                                )
                            }
                        }
                    }
                }
                BottomBarBackdrop(
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    BottomMailBar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        onOpenFilter = { /* TODO */ },
                        onSearch = onSearch,
                        onCompose = onCompose,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<MessageEntity>,
    listState: LazyListState,
    loadingOlder: Boolean,
    endReached: Boolean,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleRead: (Long) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = { it.id }) { msg ->
            SwipeableMessageRow(
                msg = msg,
                onOpen = { onOpen(msg.id) },
                onDelete = { onDelete(msg.id) },
                onToggleRead = { onToggleRead(msg.id) },
            )
        }
        if (loadingOlder) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        } else if (endReached) {
            item {
                Text(
                    text = stringResource(R.string.end_reached),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwipeableMessageRow(
    msg: MessageEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onToggleRead: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onToggleRead()
                    false // Don't dismiss, just trigger action
                }
                else -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            when (direction) {
                SwipeToDismissBoxValue.EndToStart -> {
                    val willDismiss = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
                    val bgColor = if (willDismiss) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.errorContainer
                    val onBg = if (willDismiss) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onErrorContainer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(bgColor, shape = RoundedCornerShape(14.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (willDismiss) {
                                Text(
                                    text = stringResource(R.string.swipe_to_delete),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = onBg,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = onBg,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    val willTrigger = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
                    val bgColor = if (willTrigger) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer
                    val onBg = if (willTrigger) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(bgColor, shape = RoundedCornerShape(14.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (msg.seen) Icons.Default.Drafts else Icons.Default.MarkEmailRead,
                                contentDescription = null,
                                tint = onBg,
                                modifier = Modifier.size(22.dp),
                            )
                            if (willTrigger) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(if (msg.seen) R.string.swipe_to_unread else R.string.swipe_to_read),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = onBg,
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        },
    ) {
        MessageRow(msg = msg, onClick = onOpen)
    }
}

@Composable
private fun MessageRow(msg: MessageEntity, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(
                    color = if (!msg.seen) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = msg.from.ifBlank { "(remitente desconocido)" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (!msg.seen) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatMessageDate(msg.internalDate.takeIf { it > 0 }?.let { java.util.Date(it) }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (msg.answered) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = msg.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (!msg.seen) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (msg.flagged) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Toca para reintentar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onRetry() },
            )
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryFilterRow(
    selected: MailCategory,
    onSelect: (MailCategory) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(MailCategory.entries.toList(), key = { it.name }) { cat ->
            CategoryChip(
                category = cat,
                isSelected = selected == cat,
                onClick = { onSelect(cat) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    category: MailCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    if (isSelected) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.height(40.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(category.labelResId),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    } else {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
            modifier = Modifier.size(40.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = stringResource(category.labelResId),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FolderListCard(
    folders: List<FolderEntity>,
    selectedId: Long?,
    onSelect: (FolderEntity) -> Unit,
) {
    if (folders.isEmpty()) return
    val firstCustomIndex = folders.indexOfFirst { it.kind == FolderKind.OTHER }
    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        folders.forEachIndexed { index, folder ->
            if (index == firstCustomIndex && firstCustomIndex > 0) {
                CustomFoldersHeader()
            }
            FolderCard(
                folder = folder,
                isSelected = selectedId == folder.id,
                onClick = { onSelect(folder) },
            )
        }
    }
}

@Composable
private fun CustomFoldersHeader() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
    Text(
        text = "Tus carpetas",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun FolderCard(
    folder: FolderEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp,
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = folderIcon(folder.kind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = folderLabel(folder),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun UserCard(displayName: String, email: String?) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val primary = displayName.ifBlank { email ?: "—" }
                Text(
                    text = primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (displayName.isNotBlank() && !email.isNullOrBlank()) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// Frosted-glass strip (iOS-style) at the bottom: Haze blurs whatever the message list is
// rendering behind it, and a pointer interceptor swallows every gesture so taps on this
// strip never reach the LazyColumn underneath. The bar slot is laid out via BottomCenter.
@Composable
private fun BottomBarBackdrop(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tintColor = MaterialTheme.colorScheme.surface
    val glassStyle = remember(tintColor) {
        HazeStyle(
            backgroundColor = tintColor,
            blurRadius = 14.dp,
            tints = listOf(HazeTint(tintColor.copy(alpha = 0.06f))),
        )
    }
    val glassShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    // Same top-rounded shape, but the bottom edge is pushed far past the box's real
    // bottom. Modifier.shadow casts elevation around the *outline*, so by sending the
    // bottom outline off-screen we get a clean shadow only above the top curve.
    val shadowShape = remember { TopRoundedExtendedShape(cornerRadius = 24.dp) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .shadow(elevation = 10.dp, shape = shadowShape, clip = false)
            .clip(glassShape)
            .hazeEffect(state = hazeState, style = glassStyle)
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
        content = content,
    )
}

private class TopRoundedExtendedShape(
    private val cornerRadius: Dp,
    private val bottomExtension: Dp = 200.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val extra = with(density) { bottomExtension.toPx() }
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, size.height + extra),
                    topLeft = CornerRadius(r, r),
                    topRight = CornerRadius(r, r),
                    bottomLeft = CornerRadius.Zero,
                    bottomRight = CornerRadius.Zero,
                )
            )
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun BottomMailBar(
    modifier: Modifier = Modifier,
    onOpenFilter: () -> Unit,
    onSearch: () -> Unit,
    onCompose: () -> Unit,
) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                onClick = onOpenFilter,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.size(44.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filtros",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                onClick = onSearch,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Buscar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                onClick = onCompose,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
                modifier = Modifier.size(44.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Redactar",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
}
