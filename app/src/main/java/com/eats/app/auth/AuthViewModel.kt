package com.eats.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Drives Login, Sign up and Forgot password. [user] is the signed-in person (null when signed out)
 * and is restored from the saved session when the app starts.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    private val _user = MutableStateFlow(repository.restoreSession())
    val user: StateFlow<User?> = _user.asStateFlow()

    val signedIn: Boolean get() = _user.value != null

    fun login(identifier: String, password: String, onSuccess: () -> Unit) =
        submit({ repository.login(identifier, password) }) { user ->
            _user.value = user
            onSuccess()
        }

    fun signUp(form: SignUpForm, onSuccess: () -> Unit) =
        submit({ repository.signUp(form) }) { user ->
            _user.value = user
            onSuccess()
        }

    /** Step 1 of password reset. [onCode] receives the code to show to the user. */
    fun requestPasswordReset(identifier: String, onCode: (String) -> Unit) =
        submit({ repository.requestPasswordReset(identifier) }, onCode)

    /** Step 2 of password reset. */
    fun resetPassword(identifier: String, code: String, newPassword: String, onSuccess: () -> Unit) =
        submit({ repository.resetPassword(identifier, code, newPassword) }) { onSuccess() }

    fun clearError() {
        if (_ui.value.error != null) _ui.value = AuthUiState()
    }

    fun signOut() {
        repository.logout()
        _user.value = null
    }

    /** Runs [call] with a loading state, then either reports its error or hands the value to [onSuccess]. */
    private fun <T> submit(call: suspend () -> Result<T>, onSuccess: (T) -> Unit) {
        if (_ui.value.loading) return
        _ui.value = AuthUiState(loading = true)
        viewModelScope.launch {
            call().fold(
                onSuccess = { value ->
                    _ui.value = AuthUiState()
                    onSuccess(value)
                },
                onFailure = { error ->
                    _ui.value = AuthUiState(error = error.message ?: "Something went wrong. Try again.")
                },
            )
        }
    }
}
