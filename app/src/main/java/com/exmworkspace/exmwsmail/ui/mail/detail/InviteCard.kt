package com.exmworkspace.exmwsmail.ui.mail.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exmworkspace.exmwsmail.data.mail.CalendarInvite
import com.exmworkspace.exmwsmail.data.mail.InviteReply
import com.exmworkspace.exmwsmail.ui.mail.DisplayLocale
import com.exmworkspace.exmwsmail.ui.theme.TintCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Accept / Tentative / Decline for a meeting request (§4.22), shown above the body so the
 * decision is the first thing on screen — that is what the mail is asking for.
 *
 * Once answered, the buttons give way to the choice: re-sending the same iTIP reply on every
 * visit would spam the organizer, and the card still has to say what was decided.
 */
@Composable
internal fun InviteCard(
    invite: CalendarInvite,
    answered: InviteReply?,
    sending: Boolean,
    onAnswer: (InviteReply) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TintCyan.container,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = TintCyan.content,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Invitación",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = invite.summary.ifBlank { "(sin título)" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            inviteWhen(invite)?.let { line ->
                Spacer(Modifier.padding(top = 10.dp))
                InviteDetail(Icons.Default.Event, line)
            }
            invite.location?.let { InviteDetail(Icons.Default.Place, it) }

            Spacer(Modifier.padding(top = 14.dp))
            when {
                sending -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Enviando respuesta…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                answered != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = answered.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = answered.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }

                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAnswer(InviteReply.ACCEPTED) },
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) { Text("Aceptar", maxLines = 1) }
                    OutlinedButton(
                        onClick = { onAnswer(InviteReply.TENTATIVE) },
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) { Text("Quizá", maxLines = 1) }
                    OutlinedButton(
                        onClick = { onAnswer(InviteReply.DECLINED) },
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) { Text("Rechazar", maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun InviteDetail(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val InviteReply.label: String
    get() = when (this) {
        InviteReply.ACCEPTED -> "Aceptaste la invitación"
        InviteReply.TENTATIVE -> "Respondiste “quizá”"
        InviteReply.DECLINED -> "Rechazaste la invitación"
    }

private val InviteReply.icon: ImageVector
    get() = when (this) {
        InviteReply.ACCEPTED -> Icons.Default.Check
        InviteReply.TENTATIVE -> Icons.Default.HelpOutline
        InviteReply.DECLINED -> Icons.Default.Close
    }

/**
 * "vie 15 ago 2026, 10:00–11:00" in the device's own zone — the invitation travels in UTC
 * but the user decides in local time. All-day events drop the clock entirely.
 */
private fun inviteWhen(invite: CalendarInvite): String? {
    val start = parseIsoUtc(invite.startAt) ?: return null
    val end = parseIsoUtc(invite.endAt)
    val day = SimpleDateFormat("EEE d MMM yyyy", DisplayLocale).format(start)
    if (invite.allDay) return "$day · todo el día"
    val clock = SimpleDateFormat("HH:mm", DisplayLocale)
    return buildString {
        append(day).append(", ").append(clock.format(start))
        if (end != null) append("–").append(clock.format(end))
    }
}

private fun parseIsoUtc(value: String?): Date? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(value)
    }.getOrNull()
}
