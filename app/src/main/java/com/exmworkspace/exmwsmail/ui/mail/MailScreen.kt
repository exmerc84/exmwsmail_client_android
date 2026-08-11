package com.exmworkspace.exmwsmail.ui.mail

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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.NotificationsActive
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
import com.exmworkspace.exmwsmail.R
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.exmworkspace.exmwsmail.ui.shell.AppBottomBar
import com.exmworkspace.exmwsmail.ui.shell.AppModule
import com.exmworkspace.exmwsmail.ui.shell.BottomAction
import com.exmworkspace.exmwsmail.ui.shell.BottomSearch
import com.exmworkspace.exmwsmail.ui.components.SkeletonMessageList
import com.exmworkspace.exmwsmail.data.local.entity.FolderEntity
import com.exmworkspace.exmwsmail.data.local.entity.MessageEntity
import com.exmworkspace.exmwsmail.data.mail.FolderKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScreen(
    onOpenMessage: (Long) -> Unit,
    onOpenThread: (Long) -> Unit,
    onEditDraft: (Long) -> Unit,
    onCompose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFollowups: () -> Unit = {},
    onOpenContacts: () -> Unit,
    onOpenAttachments: () -> Unit,
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
    val quota by viewModel.quota.collectAsState()
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    val shares by viewModel.shares.collectAsState()
    val sharesLoading by viewModel.sharesLoading.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val followupCount by viewModel.followupCount.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val activeAccount by viewModel.activeAccount.collectAsState()
    var showAccounts by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Re-counted every time the drawer opens: the user marks reminders done on the followups
    // screen, and the badge has to be right the next time they look at it, not once per launch.
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) viewModel.refreshFollowupCount()
    }
    val sortedFolders = remember(folders) { folders.sortedForDrawer() }
    val listState = rememberLazyListState()

    var actionsForMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var manageFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var shareFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var renameFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var confirmDeleteFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var confirmEmptyFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var bottomMenuOpen by remember { mutableStateOf(false) }

    // A row stands for a thread; a single-message thread goes straight to the message, and
    // a draft opens back in the composer instead of a read-only view.
    val openRow: (Long) -> Unit = { messageId ->
        val msg = messages.firstOrNull { it.id == messageId }
        when {
            selectedFolder?.kind == FolderKind.DRAFTS -> onEditDraft(messageId)
            msg != null && msg.threadCount > 1 && msg.threadId != null -> onOpenThread(messageId)
            else -> onOpenMessage(messageId)
        }
    }

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

    // Infinite scroll. Keyed on listState alone on purpose: re-keying on messages.size
    // restarted the collector every time a page landed, and its first emission read the
    // pre-layout measurements — still "near the bottom" — so each page immediately
    // requested the next and the whole mailbox drained in one burst. snapshotFlow already
    // re-emits when the list grows, so it needs no help.
    val endReachedNow by rememberUpdatedState(endReached)
    // Paging the folder only makes sense on the unfiltered list. Under a category chip the
    // visible list is a short server-fetched slice that stays short no matter how many folder
    // pages arrive, so the "near the bottom" test never stops being true: the spinner is an
    // extra item, so it appearing and disappearing shifts the last index and re-triggers the
    // check, and the app pages the whole folder in a loop for nothing.
    val paginationEnabled by rememberUpdatedState(selectedCategory == MailCategory.ALL)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (last, total) -> total > 0 && last >= total - 5 }
            .collect {
                if (!endReachedNow && paginationEnabled) viewModel.loadMore()
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
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Everything except the account footer scrolls: on a small phone the
                    // old fixed block (five stacked cards) covered most of the folder list.
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
                            onManage = { folder -> manageFolder = folder },
                            onCreateFolder = { showCreateFolder = true },
                        )
                        DrawerActionsCard(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            actions = listOf(
                                DrawerAction(
                                    icon = Icons.Default.NotificationsActive,
                                    label = stringResource(R.string.followups),
                                    badge = followupCount,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        onOpenFollowups()
                                    },
                                ),
                                DrawerAction(
                                    icon = Icons.Default.Settings,
                                    label = stringResource(R.string.settings),
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        onOpenSettings()
                                    },
                                ),
                            ),
                        )
                    }
                    // The one thing that stays pinned: who is signed in, with storage as a
                    // thin line inside the same card instead of its own block.
                    AccountFooterCard(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        // Standing in an auxiliary mailbox, the card names *it* — the
                        // display name belongs to the login identity, not to the aux.
                        displayName = if (activeAccount == null) displayName else "",
                        email = userEmail,
                        quota = quota,
                        onClick = {
                            viewModel.refreshAccounts()
                            showAccounts = true
                        },
                    )
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
                    if (selectedFolder?.kind == com.exmworkspace.exmwsmail.data.mail.FolderKind.INBOX) {
                        CategoryFilterRow(
                            selected = selectedCategory,
                            onSelect = viewModel::selectCategory,
                            counts = categoryCounts,
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
                                // Ghost rows with the real rows' silhouette: the screen keeps
                                // its shape while it loads instead of showing a lone spinner.
                                messages.isEmpty() && refreshing -> SkeletonMessageList()
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
                                    onOpen = openRow,
                                    onDelete = viewModel::deleteMessage,
                                    onToggleRead = viewModel::toggleRead,
                                    onLongPress = { id ->
                                        actionsForMessage = messages.firstOrNull { it.id == id }
                                    },
                                    canWrite = selectedFolder?.canWrite ?: true,
                                )
                            }
                        }
                    }
                }
                BottomBarBackdrop(
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    AppBottomBar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            // No navigationBarsPadding here: the Scaffold's own content insets
                            // already exclude the gesture bar, and applying it twice left a
                            // band of dead background under the bar.
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                        active = AppModule.MAIL,
                        search = BottomSearch.Navigate(onSearch),
                        action = BottomAction(
                            icon = Icons.Default.Edit,
                            description = stringResource(R.string.compose),
                            onClick = onCompose,
                        ),
                        onOpenMail = {},
                        onOpenContacts = onOpenContacts,
                        onOpenAttachments = onOpenAttachments,
                    )
                }
            }
        }
    }

    actionsForMessage?.let { message ->
        MessageActionsSheet(
            message = message,
            folders = sortedFolders,
            currentFolder = selectedFolder,
            onToggleRead = {
                viewModel.toggleRead(message.id)
                actionsForMessage = null
            },
            onMarkThreadRead = {
                viewModel.markThreadRead(message.id)
                actionsForMessage = null
            },
            onTogglePin = {
                viewModel.togglePin(message.id)
                actionsForMessage = null
            },
            onMove = { target ->
                viewModel.moveToFolder(message.id, target)
                actionsForMessage = null
            },
            onSpam = {
                viewModel.markSpam(message.id)
                actionsForMessage = null
            },
            onSetColor = { color ->
                viewModel.setColor(message.id, color)
                actionsForMessage = null
            },
            onDelete = {
                viewModel.deleteMessage(message.id)
                actionsForMessage = null
            },
            onDismiss = { actionsForMessage = null },
        )
    }

    shareFolder?.let { folder ->
        FolderShareSheet(
            folder = folder,
            shares = shares,
            loading = sharesLoading,
            onShare = { grantee, canWrite ->
                viewModel.shareFolder(folder, grantee, canWrite)
            },
            onRevoke = { share -> viewModel.revokeShare(folder, share.grantee) },
            onDismiss = { shareFolder = null },
        )
    }

    manageFolder?.let { folder ->
        FolderActionsSheet(
            folder = folder,
            onShare = {
                manageFolder = null
                shareFolder = folder
                viewModel.loadShares(folder)
            },
            onRename = {
                renameFolder = folder
                manageFolder = null
            },
            onEmpty = {
                confirmEmptyFolder = folder
                manageFolder = null
            },
            onDelete = {
                confirmDeleteFolder = folder
                manageFolder = null
            },
            onDismiss = { manageFolder = null },
        )
    }

    if (showAccounts) {
        AccountSwitcherSheet(
            accounts = accounts,
            activeServerId = activeAccount?.serverId,
            onSelect = viewModel::switchAccount,
            onDismiss = { showAccounts = false },
        )
    }

    if (showCreateFolder) {
        FolderNameDialog(
            title = stringResource(R.string.new_folder),
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolder = false
            },
            onDismiss = { showCreateFolder = false },
        )
    }

    renameFolder?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.rename_folder),
            confirmLabel = stringResource(R.string.rename),
            initialValue = folder.fullName,
            onConfirm = { name ->
                viewModel.renameFolder(folder, name)
                renameFolder = null
            },
            onDismiss = { renameFolder = null },
        )
    }

    confirmDeleteFolder?.let { folder ->
        ConfirmDialog(
            title = stringResource(R.string.delete_folder),
            message = stringResource(R.string.delete_folder_confirm, folderLabel(folder)),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.deleteFolder(folder)
                confirmDeleteFolder = null
            },
            onDismiss = { confirmDeleteFolder = null },
        )
    }

    confirmEmptyFolder?.let { folder ->
        ConfirmDialog(
            title = stringResource(R.string.empty_folder),
            message = stringResource(R.string.empty_folder_confirm, folderLabel(folder)),
            confirmLabel = stringResource(R.string.empty),
            onConfirm = {
                viewModel.emptyFolder(folder)
                confirmEmptyFolder = null
            },
            onDismiss = { confirmEmptyFolder = null },
        )
    }
}

