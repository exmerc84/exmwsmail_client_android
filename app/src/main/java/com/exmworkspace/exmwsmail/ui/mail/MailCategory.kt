package com.exmworkspace.exmwsmail.ui.mail

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.exmworkspace.exmwsmail.R

/**
 * The AI categories the backend assigns to each message (`ia_category`, §4.2). The values
 * must match the server's strings exactly — they are what arrives on the wire and what
 * `GET /messages?category=` expects.
 */
enum class MailCategory(
    @StringRes val labelResId: Int,
    val icon: ImageVector,
    /** null on [ALL], which applies no filter. */
    val apiValue: String?,
) {
    ALL(R.string.cat_all, Icons.Default.AllInbox, null),
    PERSONAL(R.string.cat_personal, Icons.Default.Person, "Personal"),
    COMERCIAL(R.string.cat_commercial, Icons.Default.ShoppingCart, "Comercial"),
    SOCIAL(R.string.cat_social, Icons.AutoMirrored.Filled.Chat, "Social"),
    NOTIFICACION(R.string.cat_notifications, Icons.Default.Campaign, "Notificación"),
    TRANSACCIONAL(R.string.cat_transactional, Icons.Default.ReceiptLong, "Transaccional"),
    URGENTE(R.string.cat_urgent, Icons.Default.PriorityHigh, "Urgente");
}
