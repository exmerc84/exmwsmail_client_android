package com.exmworkspace.exmwsmail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.exmworkspace.exmwsmail.data.mail.avatarColorIndex
import com.exmworkspace.exmwsmail.data.mail.avatarInitial
import com.exmworkspace.exmwsmail.ui.theme.MutedTints

/**
 * Circle with the sender's initial on a colour that is stable per address, so a sender keeps
 * the same colour across sessions and screens. Muted container tones on purpose — the list
 * should read as a professional tool, not a sticker sheet.
 *
 * [badge] paints a small dot on the avatar's top-right corner; the mail list uses it for the
 * unread/colour-flag marker so the row does not need a separate dot column.
 */
@Composable
fun SenderAvatar(
    name: String?,
    address: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    badge: Color? = null,
) {
    val key = address?.takeIf { it.isNotBlank() } ?: name.orEmpty()
    val tint = MutedTints[avatarColorIndex(key, MutedTints.size)]
    val container = tint.container
    val content = tint.content
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = avatarInitial(name, address),
                color = content,
                // Scales with the avatar so the contacts screen can reuse it at other sizes.
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(size * 0.3f)
                    .clip(CircleShape)
                    // Ring in the screen background colour so the dot reads as sitting on
                    // top of the avatar instead of merging with it.
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(size * 0.05f),
            ) {
                Box(
                    modifier = Modifier
                        .size(size * 0.2f)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(badge),
                )
            }
        }
    }
}

