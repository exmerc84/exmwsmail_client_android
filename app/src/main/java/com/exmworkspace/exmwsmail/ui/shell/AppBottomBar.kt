package com.exmworkspace.exmwsmail.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.exmworkspace.exmwsmail.R

/** Which module owns the screen, so its icon reads as the current one. */
enum class AppModule { MAIL, CONTACTS, ATTACHMENTS }

/**
 * How the search pill behaves on this screen.
 *
 * Mail hands search to its own screen, so there the pill is a button. Inside a module the
 * pill *is* the search box for that module's list, which is why the whole bottom section
 * stays put when moving between modules instead of each screen growing its own field.
 */
sealed interface BottomSearch {
    data class Navigate(val onClick: () -> Unit) : BottomSearch
    data class Inline(
        val value: String,
        val onValueChange: (String) -> Unit,
        val placeholder: String,
    ) : BottomSearch
}

/** The primary action of the current module: compose in mail, new contact in contacts. */
data class BottomAction(
    val icon: ImageVector,
    val description: String,
    val onClick: () -> Unit,
)

/**
 * The app's persistent bottom section: search, the module's primary action, and the module
 * launcher. Every screen that lives inside the shell shows the same one; only the search
 * behaviour and the action button change.
 */
@Composable
fun AppBottomBar(
    active: AppModule,
    search: BottomSearch,
    action: BottomAction?,
    onOpenMail: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenAttachments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchPill(search = search, modifier = Modifier.weight(1f))
            if (action != null) {
                Surface(
                    onClick = action.onClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.description,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }

        ModulesBar(
            active = active,
            onOpenMail = onOpenMail,
            onOpenContacts = onOpenContacts,
            onOpenAttachments = onOpenAttachments,
        )
    }
}

@Composable
private fun SearchPill(search: BottomSearch, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = modifier
            .height(44.dp)
            .then(
                if (search is BottomSearch.Navigate) Modifier.clickable(onClick = search.onClick)
                else Modifier
            ),
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
            when (search) {
                is BottomSearch.Navigate -> Text(
                    text = stringResource(R.string.search),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is BottomSearch.Inline -> Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = search.value,
                        onValueChange = search.onValueChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (search.value.isEmpty()) {
                        Text(
                            text = search.placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Launcher for the modules. Calendar, notes, tasks and passwords live on the PIM API, a
 * separate backend (§9), so their icons are drawn muted and inert rather than looking live
 * and doing nothing when tapped.
 */
@Composable
private fun ModulesBar(
    active: AppModule,
    onOpenMail: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenAttachments: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModuleButton(
                icon = Icons.Default.Email,
                description = stringResource(R.string.mail_module),
                selected = active == AppModule.MAIL,
                onClick = if (active == AppModule.MAIL) null else onOpenMail,
            )
            ModuleButton(
                icon = Icons.Default.Contacts,
                description = stringResource(R.string.contacts),
                selected = active == AppModule.CONTACTS,
                onClick = if (active == AppModule.CONTACTS) null else onOpenContacts,
            )
            ModuleButton(
                icon = Icons.Default.CalendarMonth,
                description = stringResource(R.string.calendar),
                selected = false,
                onClick = null,
            )
            ModuleButton(
                icon = Icons.Default.StickyNote2,
                description = stringResource(R.string.notes),
                selected = false,
                onClick = null,
            )
            ModuleButton(
                icon = Icons.Default.TaskAlt,
                description = stringResource(R.string.tasks),
                selected = false,
                onClick = null,
            )
            ModuleButton(
                icon = Icons.Default.Lock,
                description = stringResource(R.string.passwords),
                selected = false,
                onClick = null,
            )
            ModuleButton(
                icon = Icons.Default.AttachFile,
                description = stringResource(R.string.attachments),
                selected = active == AppModule.ATTACHMENTS,
                onClick = if (active == AppModule.ATTACHMENTS) null else onOpenAttachments,
            )
        }
    }
}

/** @param onClick null while the module has no backend wired — the icon is drawn muted. */
@Composable
private fun ModuleButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(
                if (selected) Modifier.background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                ) else Modifier
            )
            // Seven slots share the width, so the touch padding is as tight as it can be
            // while each icon still keeps a comfortable target.
            .padding(horizontal = 9.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            // The current module also has no click, but it is not unavailable: only the
            // modules with no backend yet get the muted tint.
            tint = when {
                selected -> MaterialTheme.colorScheme.primary
                onClick == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(24.dp),
        )
    }
}
