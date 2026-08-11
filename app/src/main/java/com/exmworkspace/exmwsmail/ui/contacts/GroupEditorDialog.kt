package com.exmworkspace.exmwsmail.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.data.remote.dto.ContactGroupDto
import com.exmworkspace.exmwsmail.data.remote.dto.ContactGroupUpsertDto
import com.exmworkspace.exmwsmail.ui.components.ExmField

/** The palette the backend already uses for its own groups. */
private val GroupPalette = listOf(
    "#e11d48", "#f59e0b", "#eab308", "#22c55e",
    "#14b8a6", "#3b82f6", "#6366f1", "#a855f7",
)

/**
 * Create or edit a group: name, domain and colour (§5.1).
 *
 * Domain is not cosmetic — the backend auto-assigns every existing contact of that domain to
 * the group, and releases the ones of the previous domain when it changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupEditorDialog(
    initial: ContactGroupDto?,
    onSave: (name: String, domain: String, color: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var domain by remember { mutableStateOf(initial?.domain.orEmpty()) }
    var color by remember {
        mutableStateOf(
            initial?.color?.takeIf { it.isNotBlank() }
                ?: ContactGroupUpsertDto.DEFAULT_GROUP_COLOR
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.new_group else R.string.edit_group
                )
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                ExmField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.group_name),
                    placeholder = stringResource(R.string.group_name_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                ExmField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = stringResource(R.string.group_domain),
                    placeholder = stringResource(R.string.group_domain_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.group_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GroupPalette.forEach { hex ->
                        ColorSwatch(
                            hex = hex,
                            selected = hex.equals(color, ignoreCase = true),
                            onClick = { color = hex },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), domain.trim(), color) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val swatch = parseHexColor(hex) ?: Color.Gray
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(swatch, CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White,
            )
        }
    }
}
