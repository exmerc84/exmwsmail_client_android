package com.exmworkspace.exmwsmail.ui.followups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.ui.mail.SheetAction
import com.exmworkspace.exmwsmail.ui.mail.SheetNote
import com.exmworkspace.exmwsmail.ui.mail.DisplayLocale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * When to be reminded about a message. Fixed choices rather than a date picker: this is
 * decided in a second while reading, and "tomorrow" is the answer almost every time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindMeSheet(
    onPick: (dueAtIso: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = Date()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.remind_me),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            HorizontalDivider()

            FollowupWhen.entries.forEach { choice ->
                val due = followupDueDate(choice, now)
                SheetAction(
                    icon = Icons.Default.NotificationsActive,
                    label = stringResource(choice.labelRes()) + " · " + shortDate(due),
                    onClick = { onPick(formatDueAt(due)) },
                )
            }
            SheetNote(stringResource(R.string.followup_hint))
        }
    }
}

private fun FollowupWhen.labelRes(): Int = when (this) {
    FollowupWhen.THIS_EVENING -> R.string.followup_this_evening
    FollowupWhen.TOMORROW -> R.string.followup_tomorrow
    FollowupWhen.NEXT_WEEK -> R.string.followup_next_week
}

private fun shortDate(date: Date): String =
    SimpleDateFormat("EEE d MMM, HH:mm", DisplayLocale).format(date)
