package com.exmworkspace.exmwsmail.ui.mail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.data.remote.dto.RemoteAccountDto
import com.exmworkspace.exmwsmail.ui.components.ExmField
import com.exmworkspace.exmwsmail.ui.components.SenderAvatar

/**
 * Mailbox picker (§4.23): the primary account plus any auxiliary IMAP mailboxes, with alta
 * and baja. Switching re-points every mail surface through the `X-Account-Id` header; the
 * primary is the login identity, so it can neither carry a header nor be deleted.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun AccountSwitcherSheet(
    accounts: List<RemoteAccountDto>,
    activeServerId: Long?,
    onSelect: (RemoteAccountDto?) -> Unit,
    onAdd: (email: String, password: String, displayName: String?) -> Unit,
    onDelete: (RemoteAccountDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf<RemoteAccountDto?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    if (showAdd) {
        AddAccountDialog(
            onConfirm = { email, password, name ->
                onAdd(email, password, name)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    confirmDelete?.let { account ->
        ConfirmDialog(
            title = stringResource(R.string.account_delete),
            message = stringResource(R.string.account_delete_confirm, account.email),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                onDelete(account)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.accounts_title),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            accounts.forEach { account ->
                val isPrimary = account.isDefault
                val isActive =
                    if (activeServerId == null) isPrimary else account.id == activeServerId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Delete lives behind a long-press, like folder management: a
                        // standing trash button next to a switch tap invited accidents.
                        .combinedClickable(
                            onClick = {
                                onSelect(if (isPrimary) null else account)
                                onDismiss()
                            },
                            onLongClick = if (isPrimary) null else {
                                { confirmDelete = account }
                            },
                        )
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SenderAvatar(
                        name = account.displayName ?: account.email,
                        address = account.email,
                        size = 36.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = account.displayName?.takeIf { it.isNotBlank() }
                                ?: account.email,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (isPrimary) {
                                stringResource(R.string.account_primary, account.email)
                            } else {
                                account.email
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdd = true }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.account_add),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** Alta of an auxiliary IMAP mailbox. The user types the credentials; they travel straight to `POST /api/accounts/`. */
@Composable
private fun AddAccountDialog(
    onConfirm: (email: String, password: String, displayName: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    val valid = email.isNotBlank() && '@' in email && password.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_add)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.account_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(top = 12.dp))
                ExmField(
                    value = email,
                    onValueChange = { email = it },
                    label = stringResource(R.string.account_email_hint),
                )
                Spacer(Modifier.padding(top = 8.dp))
                ExmField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.account_password_hint),
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.padding(top = 8.dp))
                ExmField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = stringResource(R.string.account_name_hint),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(email.trim(), password, displayName.trim().ifBlank { null }) },
                enabled = valid,
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
