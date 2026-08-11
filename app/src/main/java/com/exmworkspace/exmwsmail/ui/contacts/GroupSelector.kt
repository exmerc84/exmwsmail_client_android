package com.exmworkspace.exmwsmail.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.data.remote.dto.ContactGroupDto
import com.exmworkspace.exmwsmail.ui.components.ExmField

/**
 * Group picker for the editor.
 *
 * A dropdown rather than a chip row: chips only work while the list is short, and an account
 * with twenty groups would push them off screen with no way to reach the rest.
 *
 * "New group" is just a free-text name — the backend has no group-creation endpoint, groups
 * exist because contacts carry the name.
 */
@Composable
internal fun GroupSelectField(
    value: String,
    groups: List<ContactGroupDto>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            ExmField(
                value = value,
                onValueChange = onValueChange,
                label = stringResource(R.string.new_group),
                placeholder = stringResource(R.string.new_group_hint),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                creating = false
                onValueChange("")
            }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel),
                )
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.contact_group),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val current = groups.firstOrNull { it.name == value }
                if (value.isNotBlank()) {
                    GroupDot(current?.color)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = value.ifBlank { stringResource(R.string.no_group) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(14.dp),
                // Scrolls on its own, so the list stays usable however many groups exist.
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.no_group)) },
                    onClick = {
                        onValueChange("")
                        expanded = false
                    },
                )
                groups.forEach { group ->
                    val name = group.name ?: return@forEach
                    DropdownMenuItem(
                        text = { Text("$name · ${group.contactCount}") },
                        leadingIcon = { GroupDot(group.color) },
                        onClick = {
                            onValueChange(name)
                            expanded = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.new_group)) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = {
                        onValueChange("")
                        creating = true
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Group filter for the list. A dropdown for the same reason as the editor's: a chip row
 * stops working once the account has more groups than fit on screen.
 */
@Composable
internal fun GroupFilterMenu(
    groups: List<ContactGroupDto>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .background(
                    if (selected == null) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(20.dp),
                )
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val current = groups.firstOrNull { it.name == selected }
            if (selected != null) {
                GroupDot(current?.color)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = selected ?: stringResource(R.string.all_groups),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.contact_group),
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all_groups)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            groups.forEach { group ->
                val name = group.name ?: return@forEach
                DropdownMenuItem(
                    text = { Text("$name · ${group.contactCount}") },
                    leadingIcon = { GroupDot(group.color) },
                    onClick = {
                        onSelect(name)
                        expanded = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manage_groups)) },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = {
                    onManage()
                    expanded = false
                },
            )
        }
    }
}

/** The backend assigns each group a colour; fall back to the theme when it is missing. */
@Composable
internal fun GroupDot(hexColor: String?) {
    val color = remember(hexColor) { parseHexColor(hexColor) }
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color ?: Color.Transparent, CircleShape)
            .border(
                width = if (color == null) 1.dp else 0.dp,
                color = if (color == null) MaterialTheme.colorScheme.outlineVariant
                else Color.Transparent,
                shape = CircleShape,
            ),
    )
}

internal fun parseHexColor(hex: String?): Color? {
    val raw = hex?.trim()?.removePrefix("#") ?: return null
    if (raw.length != 6) return null
    val value = raw.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}
