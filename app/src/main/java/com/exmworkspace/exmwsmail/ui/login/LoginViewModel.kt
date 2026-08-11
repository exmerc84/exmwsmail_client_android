package com.exmworkspace.exmwsmail.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.exmworkspace.exmwsmail.data.repository.AuthRepository
import com.exmworkspace.exmwsmail.data.repository.LoginResult
import com.exmworkspace.exmwsmail.ui.appContainer
import kotlinx.coroutines.delay
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
    val captchaRequired: Boolean = false,
    val captchaSolved: Boolean = false,
    val captchaBusy: Boolean = false,
    val lockedSeconds: Int = 0,
) {
    val canSubmit: Boolean
        get() = !submitting && lockedSeconds == 0 && (!captchaRequired || captchaSolved)
}

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var challengeId: String? = null
    private var captchaToken: String? = null

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        if (current.email.isBlank() || current.password.isEmpty()) {
            _state.update { it.copy(error = "Introduce email y contraseña") }
            return
        }
        viewModelScope.launch { attemptLogin() }
    }

    private suspend fun attemptLogin() {
        val current = _state.value
        _state.update { it.copy(submitting = true, error = null) }

        when (val result = authRepository.signIn(current.email, current.password, captchaToken)) {
            is LoginResult.Success -> {
                // isLoggedIn flips and RootContent swaps the screen; nothing else to do.
                _state.update { it.copy(submitting = false, error = null) }
            }

            is LoginResult.CaptchaRequired -> {
                _state.update {
                    it.copy(
                        submitting = false,
                        captchaRequired = true,
                        captchaSolved = false,
                        error = "Verifica que eres humano para continuar",
                    )
                }
                loadChallenge()
            }

            is LoginResult.RateLimited -> {
                _state.update { it.copy(submitting = false, error = null) }
                startLockCountdown(result.retryAfterSeconds.toInt().coerceAtLeast(60))
            }

            is LoginResult.Failure -> {
                // The captcha token is single-use; a rejected password consumed it too.
                consumeCaptchaToken()
                _state.update { it.copy(submitting = false, error = result.message) }
                if (_state.value.captchaRequired) loadChallenge()
            }
        }
    }

    fun onCaptchaGesture(gesture: CaptchaGesture) {
        when (gesture) {
            is CaptchaGesture.TooCrude ->
                _state.update { it.copy(error = gesture.reason) }

            is CaptchaGesture.Completed -> viewModelScope.launch {
                val id = challengeId ?: loadChallenge() ?: run {
                    _state.update { it.copy(error = "No se pudo iniciar la verificación") }
                    return@launch
                }
                _state.update { it.copy(captchaBusy = true, error = null) }
                val token = runCatching {
                    authRepository.verifyCaptcha(id, gesture.durationMs, gesture.points)
                }.getOrNull()

                if (token == null) {
                    challengeId = null
                    _state.update {
                        it.copy(
                            captchaBusy = false,
                            captchaSolved = false,
                            error = "Verificación fallida, vuelve a deslizar",
                        )
                    }
                    loadChallenge()
                    return@launch
                }

                captchaToken = token
                _state.update { it.copy(captchaBusy = false, captchaSolved = true, error = null) }
                // The drag is an explicit user action, so continuing straight into the
                // login is a manual retry, not the auto-retry §11.1 warns against.
                attemptLogin()
            }
        }
    }

    private suspend fun loadChallenge(): String? {
        val id = runCatching { authRepository.requestCaptchaChallenge() }.getOrNull()
        challengeId = id
        return id
    }

    private fun consumeCaptchaToken() {
        captchaToken = null
        challengeId = null
        _state.update { it.copy(captchaSolved = false) }
    }

    private fun startLockCountdown(seconds: Int) {
        viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _state.update {
                    it.copy(
                        lockedSeconds = remaining,
                        error = "Demasiados intentos, espera ${remaining}s",
                    )
                }
                delay(1_000)
            }
            _state.update { it.copy(lockedSeconds = 0, error = null) }
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
