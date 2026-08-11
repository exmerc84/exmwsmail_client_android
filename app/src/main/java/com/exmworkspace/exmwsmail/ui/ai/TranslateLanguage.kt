package com.exmworkspace.exmwsmail.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
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

/**
 * Target languages offered for translation.
 *
 * [apiValue] is the language written in Spanish because that is what the endpoint's own
 * examples use (`"language": "inglés"`), and it is the prompt the model actually receives.
 */
enum class TranslateLanguage(val labelRes: Int, val apiValue: String) {
    SPANISH(R.string.lang_spanish, "español"),
    ENGLISH(R.string.lang_english, "inglés"),
    PORTUGUESE(R.string.lang_portuguese, "portugués"),
    FRENCH(R.string.lang_french, "francés"),
    GERMAN(R.string.lang_german, "alemán"),
    ITALIAN(R.string.lang_italian, "italiano"),
}

/** Which language to translate into. Asked every time — mail arrives in several. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateLanguageSheet(
    onPick: (TranslateLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.translate_to),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            HorizontalDivider()

            TranslateLanguage.entries.forEach { language ->
                SheetAction(
                    icon = Icons.Default.Translate,
                    label = stringResource(language.labelRes),
                    onClick = { onPick(language) },
                )
            }
        }
    }
}
