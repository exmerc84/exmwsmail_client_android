package com.exmworkspace.exmwsmail.ui.mail

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.exmworkspace.exmwsmail.R

enum class MailCategory(@StringRes val labelResId: Int, val icon: ImageVector) {
    PERSONAL(R.string.cat_personal, Icons.Default.Person),
    SHOPPING(R.string.cat_shopping, Icons.Default.ShoppingCart),
    MESSAGES(R.string.cat_messages, Icons.AutoMirrored.Filled.Chat),
    NOTIFICATIONS(R.string.cat_notifications, Icons.Default.Campaign),
    ALL(R.string.cat_all, Icons.Default.AllInbox);
}
