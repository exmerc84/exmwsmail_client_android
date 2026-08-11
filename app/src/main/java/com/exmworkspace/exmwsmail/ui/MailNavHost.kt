package com.exmworkspace.exmwsmail.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.exmworkspace.exmwsmail.di.NotificationTarget
import kotlinx.coroutines.flow.filterNotNull
import com.exmworkspace.exmwsmail.ui.attachments.AttachmentsScreen
import com.exmworkspace.exmwsmail.ui.compose.ComposeScreen
import com.exmworkspace.exmwsmail.ui.followups.FollowupsScreen
import com.exmworkspace.exmwsmail.ui.contacts.ContactEditorScreen
import com.exmworkspace.exmwsmail.ui.contacts.ContactsScreen
import com.exmworkspace.exmwsmail.ui.mail.MailScreen
import com.exmworkspace.exmwsmail.ui.mail.detail.MessageDetailScreen
import com.exmworkspace.exmwsmail.ui.mail.detail.MessageSourceScreen
import com.exmworkspace.exmwsmail.ui.mail.detail.SourceMode
import com.exmworkspace.exmwsmail.ui.mail.thread.ThreadScreen
import com.exmworkspace.exmwsmail.ui.search.SearchScreen
import com.exmworkspace.exmwsmail.ui.settings.SettingsScreen

private const val ROUTE_MAIL = "mail"
private const val ROUTE_MESSAGE = "message/{messageId}"
// Routed by the representative message id, not the thread id: server-generated thread ids
// would otherwise have to be URL-encoded through the nav graph.
private const val ROUTE_THREAD = "thread/{messageId}"
private const val ROUTE_COMPOSE = "compose?messageId={messageId}&mode={mode}&to={to}"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_CONTACTS = "contacts"
private const val ROUTE_CONTACT_EDITOR = "contact?id={id}"
private const val ROUTE_ATTACHMENTS = "attachments"
private const val ROUTE_FOLLOWUPS = "followups"
private const val ROUTE_SOURCE = "source/{messageId}?mode={sourceMode}"
private const val ARG_SOURCE_MODE = "sourceMode"
private const val ARG_CONTACT_ID = "id"
private const val ARG_MESSAGE_ID = "messageId"
private const val ARG_COMPOSE_MODE = "mode"
private const val ARG_COMPOSE_TO = "to"

/** One shared duration keeps every screen change feeling like the same app. */
private const val NAV_MS = 260

/**
 * Opens the message a tapped notification named (§3 carries `folder` and `uid`).
 *
 * The notification cannot name the local row id — that is the app's own key — so the pair is
 * resolved here, syncing the folder if the message was never listed. Tapping used to just
 * raise the app and leave the user on the list, because the activity never read the extras
 * it had been putting on the intent all along.
 *
 * The target is cleared before navigating, so a failed lookup does not retry forever and a
 * recomposition cannot open the same message twice.
 */
@Composable
private fun OpenNotificationTarget(navController: NavHostController) {
    // The Context accessor, not the CreationExtras one — that extension only exists inside a
    // ViewModel factory initializer.
    val container = LocalContext.current.mailContainer()

    // Keyed on Unit and collecting, not keyed on the value: clearing the target inside an
    // effect keyed on that same target changed the key, so Compose restarted the block and
    // cancelled the lookup mid-flight — the app opened and simply stayed on the list.
    // `filterNotNull` swallows the clearing emission instead of tearing the collector down.
    LaunchedEffect(Unit) {
        container.pendingNotification.filterNotNull().collect { pending ->
            container.pendingNotification.value = null
            val email = container.authRepository.activeMailEmail() ?: return@collect
            val accountId = runCatching { container.mailRepository.ensureAccount(email) }
                .getOrNull() ?: return@collect
            val messageId = runCatching {
                container.mailRepository.findMessageIdForNotification(
                    accountId = accountId,
                    folderFullName = pending.folder,
                    uid = pending.uid,
                )
            }.getOrNull() ?: return@collect
            navController.navigate("message/$messageId")
        }
    }
}

@Composable
fun MailNavHost(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    OpenNotificationTarget(navController)
    NavHost(
        navController = navController,
        startDestination = ROUTE_MAIL,
        // Screens slide in over a fading predecessor instead of hard-cutting; popping runs
        // the same motion backwards, so back always feels like undoing the way in.
        enterTransition = {
            slideInHorizontally(animationSpec = tween(NAV_MS)) { it / 4 } +
                fadeIn(animationSpec = tween(NAV_MS))
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = tween(NAV_MS)) { -it / 8 } +
                fadeOut(animationSpec = tween(NAV_MS))
        },
        popEnterTransition = {
            slideInHorizontally(animationSpec = tween(NAV_MS)) { -it / 8 } +
                fadeIn(animationSpec = tween(NAV_MS))
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = tween(NAV_MS)) { it / 4 } +
                fadeOut(animationSpec = tween(NAV_MS))
        },
    ) {
        composable(ROUTE_MAIL) {
            MailScreen(
                onOpenMessage = { id -> navController.navigate("message/$id") },
                onOpenThread = { id -> navController.navigate("thread/$id") },
                onEditDraft = { id -> navController.navigate("compose?messageId=$id&mode=draft") },
                onCompose = { navController.navigate("compose?mode=new") },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenFollowups = { navController.navigate(ROUTE_FOLLOWUPS) },
                onOpenContacts = { navController.navigate(ROUTE_CONTACTS) },
                onOpenAttachments = { navController.navigate(ROUTE_ATTACHMENTS) },
                onSearch = { navController.navigate(ROUTE_SEARCH) },
            )
        }
        composable(ROUTE_FOLLOWUPS) {
            FollowupsScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_ATTACHMENTS) {
            AttachmentsScreen(
                onBack = { navController.popBackStack() },
                // Sideways between modules: replace rather than stack, so the back stack does
                // not grow one entry per hop around the module bar.
                onOpenContacts = {
                    navController.navigate(ROUTE_CONTACTS) { popUpTo(ROUTE_MAIL) }
                },
            )
        }
        composable(
            route = ROUTE_SOURCE,
            arguments = listOf(
                navArgument(ARG_MESSAGE_ID) { type = NavType.LongType },
                navArgument(ARG_SOURCE_MODE) {
                    type = NavType.StringType
                    defaultValue = SourceMode.SOURCE.name
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong(ARG_MESSAGE_ID) ?: return@composable
            val mode = entry.arguments?.getString(ARG_SOURCE_MODE)
                ?.let { runCatching { SourceMode.valueOf(it) }.getOrNull() }
                ?: SourceMode.SOURCE
            MessageSourceScreen(
                messageId = id,
                mode = mode,
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_CONTACTS) {
            ContactsScreen(
                onBack = { navController.popBackStack() },
                onCompose = { email ->
                    navController.navigate("compose?mode=new&to=${Uri.encode(email)}")
                },
                onOpenContact = { id -> navController.navigate("contact?id=$id") },
                onNewContact = { navController.navigate("contact?id=-1") },
                onOpenAttachments = {
                    navController.navigate(ROUTE_ATTACHMENTS) { popUpTo(ROUTE_MAIL) }
                },
            )
        }
        composable(
            route = ROUTE_CONTACT_EDITOR,
            arguments = listOf(
                navArgument(ARG_CONTACT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong(ARG_CONTACT_ID) ?: -1L
            ContactEditorScreen(
                contactId = id.takeIf { it > 0 },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = ROUTE_THREAD,
            arguments = listOf(navArgument(ARG_MESSAGE_ID) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(ARG_MESSAGE_ID) ?: return@composable
            ThreadScreen(
                messageId = id,
                onBack = { navController.popBackStack() },
                onReply = { msgId -> navController.navigate("compose?messageId=$msgId&mode=reply") },
                onForward = { msgId -> navController.navigate("compose?messageId=$msgId&mode=forward") },
            )
        }
        composable(
            route = ROUTE_MESSAGE,
            arguments = listOf(navArgument(ARG_MESSAGE_ID) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(ARG_MESSAGE_ID) ?: return@composable
            MessageDetailScreen(
                messageId = id,
                onBack = { navController.popBackStack() },
                onReply = { navController.navigate("compose?messageId=$id&mode=reply") },
                onForward = { navController.navigate("compose?messageId=$id&mode=forward") },
                onViewSource = { mode -> navController.navigate("source/$id?mode=${mode.name}") },
            )
        }
        composable(
            route = ROUTE_COMPOSE,
            arguments = listOf(
                navArgument(ARG_MESSAGE_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument(ARG_COMPOSE_MODE) {
                    type = NavType.StringType
                    defaultValue = "new"
                },
                navArgument(ARG_COMPOSE_TO) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            ComposeScreen(onBack = { navController.popBackStack() })
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignOut = onSignOut,
            )
        }
        composable(ROUTE_SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenMessage = { id -> navController.navigate("message/$id") },
            )
        }
    }
}
