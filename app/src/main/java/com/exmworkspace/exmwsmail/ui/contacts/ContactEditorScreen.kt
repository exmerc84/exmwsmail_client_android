package com.exmworkspace.exmwsmail.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.ui.components.ExmField
import com.exmworkspace.exmwsmail.ui.mail.ConfirmDialog

/**
 * Create or edit a contact (§5). Imported contacts are editable too — the backend keeps the
 * `source` flag, it does not lock the record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditorScreen(
    contactId: Long?,
    onBack: () -> Unit,
    viewModel: ContactEditorViewModel = viewModel(
        factory = ContactEditorViewModel.factoryFor(contactId)
    ),
) {
    val form by viewModel.form.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(saved) {
        if (saved) onBack()
    }

    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_contact),
            message = stringResource(
                R.string.delete_contact_confirm,
                form.displayName.ifBlank { form.email },
            ),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.isNew) R.string.new_contact else R.string.edit_contact
                        )
                    )
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
                    // Favourite moved here from the list row: the row keeps a single action
                    // (write) and this saves with the rest of the form.
                    IconButton(
                        onClick = { viewModel.update { it.copy(isFavorite = !it.isFavorite) } },
                    ) {
                        Icon(
                            imageVector = if (form.isFavorite) Icons.Default.Star
                            else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.favorite),
                            tint = if (form.isFavorite) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = viewModel::save,
                        enabled = form.canSave && !saving,
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.save),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            ContactEditorHeader(
                displayName = form.displayName,
                email = form.email,
                fallbackTitle = stringResource(
                    if (viewModel.isNew) R.string.new_contact else R.string.edit_contact
                ),
            )

            EditorSection(
                title = stringResource(R.string.section_identity),
                icon = Icons.Default.Person,
            ) {
                ExmField(
                    value = form.email,
                    onValueChange = { v -> viewModel.update { it.copy(email = v) } },
                    label = stringResource(R.string.contact_email),
                    placeholder = "nombre@dominio.com",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExmField(
                    value = form.displayName,
                    onValueChange = { v -> viewModel.update { it.copy(displayName = v) } },
                    label = stringResource(R.string.contact_name),
                    modifier = Modifier.fillMaxWidth(),
                )
                GroupSelectField(
                    value = form.groupName,
                    groups = groups,
                    onValueChange = { v -> viewModel.update { it.copy(groupName = v) } },
                )
            }

            EditorSection(
                title = stringResource(R.string.section_organization),
                icon = Icons.Default.Business,
            ) {
                ExmField(
                    value = form.company,
                    onValueChange = { v -> viewModel.update { it.copy(company = v) } },
                    label = stringResource(R.string.contact_company),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExmField(
                    value = form.jobTitle,
                    onValueChange = { v -> viewModel.update { it.copy(jobTitle = v) } },
                    label = stringResource(R.string.contact_job_title),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExmField(
                    value = form.department,
                    onValueChange = { v -> viewModel.update { it.copy(department = v) } },
                    label = stringResource(R.string.contact_department),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            EditorSection(
                title = stringResource(R.string.section_reach),
                icon = Icons.Default.Call,
            ) {
                ExmField(
                    value = form.phone,
                    onValueChange = { v -> viewModel.update { it.copy(phone = v) } },
                    label = stringResource(R.string.contact_phone),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExmField(
                    value = form.mobile,
                    onValueChange = { v -> viewModel.update { it.copy(mobile = v) } },
                    label = stringResource(R.string.contact_mobile),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                ExmField(
                    value = form.website,
                    onValueChange = { v -> viewModel.update { it.copy(website = v) } },
                    label = stringResource(R.string.contact_website),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            EditorSection(
                title = stringResource(R.string.contact_notes),
                icon = Icons.AutoMirrored.Filled.Notes,
            ) {
                ExmField(
                    value = form.notes,
                    onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                    label = stringResource(R.string.contact_notes),
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Destructive, so it sits at the very bottom rather than next to the row in the
            // list, where it was one mis-tap away from deleting the wrong contact.
            if (!viewModel.isNew) {
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_contact))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
