package com.exmworkspace.exmwsmail.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.ui.components.SenderAvatar
import com.exmworkspace.exmwsmail.data.remote.dto.ContactDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactGroupDto
import com.exmworkspace.exmwsmail.ui.components.ExmField
import com.exmworkspace.exmwsmail.ui.shell.AppBottomBar
import com.exmworkspace.exmwsmail.ui.shell.AppModule
import com.exmworkspace.exmwsmail.ui.shell.BottomAction
import com.exmworkspace.exmwsmail.ui.shell.BottomSearch
import com.exmworkspace.exmwsmail.ui.mail.ConfirmDialog

/**
 * The address book (§5): the user's own contacts and the corporate directory, which are two
 * separate endpoints and stay two separate tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    onCompose: (String) -> Unit,
    onOpenContact: (Long) -> Unit,
    onNewContact: () -> Unit,
    onOpenAttachments: () -> Unit = {},
    viewModel: ContactsViewModel = viewModel(factory = ContactsViewModel.Factory),
) {
    val contacts by viewModel.contacts.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val query by viewModel.query.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val counts by viewModel.counts.collectAsState()

    // Refresh on resume, not on first composition: returning from the editor restores this
    // entry rather than recomposing it, so a LaunchedEffect(Unit) would never fire again and
    // the list would still show the pre-edit values.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showManageGroups by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<ContactGroupDto?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }
    var pendingGroupDelete by remember { mutableStateOf<ContactGroupDto?>(null) }
    val notice by viewModel.notice.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.contacts))
                        counts?.let {
                            Text(
                                // Favourites only when there are any: "0 favoritos" is noise.
                                text = if (it.favorites > 0) {
                                    stringResource(R.string.contacts_counts, it.total, it.favorites)
                                } else {
                                    stringResource(R.string.contacts_count_only, it.total)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp).size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = stringResource(R.string.back_button),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::importFromEmails,
                        enabled = !importing,
                    ) {
                        if (importing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = stringResource(R.string.import_contacts),
                            )
                        }
                    }
                },
            )
        },
        // The search box and the primary action live in the app's persistent bottom bar, so
        // moving between modules no longer swaps the whole bottom of the screen.
        bottomBar = {
            AppBottomBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(
                    start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp,
                ),
                active = AppModule.CONTACTS,
                search = BottomSearch.Inline(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.contact_search_hint),
                ),
                action = BottomAction(
                    icon = Icons.Default.PersonAdd,
                    description = stringResource(R.string.new_contact),
                    onClick = onNewContact,
                ),
                onOpenMail = onBack,
                onOpenContacts = {},
                onOpenAttachments = onOpenAttachments,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = tab == ContactsTab.PERSONAL,
                    onClick = { viewModel.selectTab(ContactsTab.PERSONAL) },
                    label = { Text(stringResource(R.string.contacts_personal)) },
                )
                FilterChip(
                    selected = tab == ContactsTab.CORPORATE,
                    onClick = { viewModel.selectTab(ContactsTab.CORPORATE) },
                    label = { Text(stringResource(R.string.contacts_corporate)) },
                )
            }

            if (groups.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                GroupFilterMenu(
                    groups = groups,
                    selected = selectedGroup,
                    onSelect = viewModel::selectGroup,
                    onManage = { showManageGroups = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            when {
                loading && contacts.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                contacts.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = error ?: stringResource(R.string.no_contacts),
                        color = if (error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(contacts, key = { it.id ?: it.email.orEmpty() }) { contact ->
                        ContactRow(
                            modifier = Modifier.animateItem(),
                            contact = contact,
                            onOpen = {
                                val id = contact.id
                                // Directory entries have no record to edit — write instead.
                                if (tab == ContactsTab.PERSONAL && id != null) {
                                    onOpenContact(id)
                                } else {
                                    contact.cleanEmail.takeIf { it.isNotBlank() }?.let(onCompose)
                                }
                            },
                            onWrite = { contact.cleanEmail.takeIf { it.isNotBlank() }?.let(onCompose) },
                        )
                    }
                }
            }
        }
    }

    if (showManageGroups) {
        ManageGroupsDialog(
            groups = groups,
            onCreate = {
                showManageGroups = false
                creatingGroup = true
            },
            onEdit = { group ->
                showManageGroups = false
                editingGroup = group
            },
            onDelete = { group ->
                showManageGroups = false
                pendingGroupDelete = group
            },
            onDismiss = { showManageGroups = false },
        )
    }

    if (creatingGroup || editingGroup != null) {
        GroupEditorDialog(
            initial = editingGroup,
            onSave = { name, domain, color ->
                viewModel.saveGroup(editingGroup, name, domain, color)
                creatingGroup = false
                editingGroup = null
            },
            onDismiss = {
                creatingGroup = false
                editingGroup = null
            },
        )
    }

    pendingGroupDelete?.let { group ->
        ConfirmDialog(
            title = stringResource(R.string.delete_group),
            message = stringResource(R.string.delete_group_confirm, group.name.orEmpty()),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.deleteGroup(group)
                pendingGroupDelete = null
            },
            onDismiss = { pendingGroupDelete = null },
        )
    }

    notice?.let { message ->
        // Informational, so a single acknowledgement — offering "cancel" would imply the
        // action could still be undone.
        AlertDialog(
            onDismissRequest = viewModel::dismissNotice,
            title = { Text(stringResource(R.string.groups)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissNotice) {
                    Text(stringResource(R.string.understood))
                }
            },
        )
    }

}

@Composable
private fun ContactRow(
    contact: ContactDto,
    onOpen: () -> Unit,
    onWrite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SenderAvatar(
                name = contact.displayLabel,
                address = contact.cleanEmail,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.displayLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Indicator only — toggling lives in the editor now, so the row carries
                    // one action instead of three and favourites still read at a glance.
                    if (contact.isFavorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = stringResource(R.string.favorite),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                contact.cleanEmail.takeIf { it.isNotBlank() && it != contact.displayLabel }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                listOfNotNull(contact.jobTitle, contact.company)
                    .filter { it.isNotBlank() }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(" · ")
                    ?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
            // The one visible action. Tapping the row already opens the editor, so the old
            // pencil duplicated it; the favourite toggle moved into the editor's top bar.
            IconButton(onClick = onWrite) {
                Icon(
                    imageVector = Icons.Default.MailOutline,
                    contentDescription = stringResource(R.string.new_message),
                )
            }
        }
    }
}
