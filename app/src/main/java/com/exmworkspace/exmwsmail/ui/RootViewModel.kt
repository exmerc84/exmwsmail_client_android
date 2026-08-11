package com.exmworkspace.exmwsmail.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.service.DeviceRegistrar
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RootViewModel(
    private val authRepository: AuthRepository,
    private val deviceRegistrar: DeviceRegistrar,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn

    init {
        // Cold start: trade the stored refresh token for a fresh access token before the
        // first screen makes a request (§11.6). A 401 here clears the session and the UI
        // falls back to the login screen on its own.
        viewModelScope.launch { authRepository.restoreSession() }
    }

    fun onSessionReady() {
        viewModelScope.launch { deviceRegistrar.register() }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                RootViewModel(container.authRepository, container.deviceRegistrar)
            }
        }
    }
}
