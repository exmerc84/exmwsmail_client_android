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
import com.exmworkspace.exmwsmail.service.MailIdleService
import com.exmworkspace.exmwsmail.ui.login.LoginScreen

@Composable
fun RootContent(
    viewModel: RootViewModel = viewModel(factory = RootViewModel.Factory),
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val context = LocalContext.current

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — service runs regardless */ }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            MailIdleService.start(context)
        } else {
            MailIdleService.stop(context)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (isLoggedIn) {
            MailNavHost(onSignOut = viewModel::signOut)
        } else {
            LoginScreen()
        }
    }
}
