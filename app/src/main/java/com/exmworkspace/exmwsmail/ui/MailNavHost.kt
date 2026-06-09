package com.exmworkspace.exmwsmail.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.exmworkspace.exmwsmail.ui.compose.ComposeScreen
import com.exmworkspace.exmwsmail.ui.mail.MailScreen
import com.exmworkspace.exmwsmail.ui.mail.detail.MessageDetailScreen
import com.exmworkspace.exmwsmail.ui.search.SearchScreen
import com.exmworkspace.exmwsmail.ui.settings.SettingsScreen

private const val ROUTE_MAIL = "mail"
private const val ROUTE_MESSAGE = "message/{messageId}"
private const val ROUTE_COMPOSE = "compose?messageId={messageId}&mode={mode}"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_SETTINGS = "settings"
private const val ARG_MESSAGE_ID = "messageId"
private const val ARG_COMPOSE_MODE = "mode"

@Composable
fun MailNavHost(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_MAIL) {
        composable(ROUTE_MAIL) {
            MailScreen(
                onOpenMessage = { id -> navController.navigate("message/$id") },
                onCompose = { navController.navigate("compose?mode=new") },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onSearch = { navController.navigate(ROUTE_SEARCH) },
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
