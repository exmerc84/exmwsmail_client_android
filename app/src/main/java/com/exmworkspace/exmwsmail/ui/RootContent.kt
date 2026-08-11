package com.exmworkspace.exmwsmail.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exmworkspace.exmwsmail.service.MailNotifications
import com.exmworkspace.exmwsmail.ui.login.LoginScreen

@Composable
fun RootContent(
    viewModel: RootViewModel = viewModel(factory = RootViewModel.Factory),
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val context = LocalContext.current

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Push still registers; only the system tray display depends on this. */ }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        MailNotifications.ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.onSessionReady()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (isLoggedIn) {
            MailNavHost(onSignOut = viewModel::signOut)
        } else {
            LoginScreen()
        }
    }
}
