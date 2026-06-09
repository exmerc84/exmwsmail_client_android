package com.example.exmwsmail.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.exmwsmail.data.mail.ConnectionResult
import com.example.exmwsmail.data.repository.AuthRepository
import com.example.exmwsmail.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        if (current.email.isBlank() || current.password.isEmpty()) {
            _state.update { it.copy(error = "Introduce email y contraseña") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            val result = authRepository.signIn(current.email, current.password)
            _state.update {
                when (result) {
                    is ConnectionResult.Success -> it.copy(submitting = false, error = null)
                    is ConnectionResult.AuthFailure -> it.copy(
                        submitting = false,
                        error = "Credenciales incorrectas",
                    )
                    is ConnectionResult.NetworkFailure -> it.copy(
                        submitting = false,
                        error = "No se pudo conectar: ${result.message}",
                    )
                    is ConnectionResult.UnknownFailure -> it.copy(
                        submitting = false,
                        error = "Error: ${result.message}",
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LoginViewModel(appContainer().authRepository)
            }
        }
    }
}
