package com.eats.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.auth.AuthViewModel
import com.eats.app.auth.Validators
import com.eats.app.ui.components.EatsOutlinedButton
import com.eats.app.ui.components.EatsPrimaryButton
import com.eats.app.ui.components.EatsTextField
import com.eats.app.ui.components.EatsWordmark
import com.eats.app.ui.components.OrDivider

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onSignUpClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSignedIn: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.clearError() }

    val identifierError = if (submitted) Validators.emailOrMobile(identifier) else null
    val passwordError = if (submitted && password.isEmpty()) "Enter your password" else null
    val linkColor = MaterialTheme.colorScheme.tertiary

    fun submit() {
        submitted = true
        if (Validators.emailOrMobile(identifier) == null && password.isNotEmpty()) {
            focusManager.clearFocus()
            viewModel.login(identifier.trim(), password, onSignedIn)
        }
    }

    fun notConnected(what: String) {
        Toast.makeText(context, "$what isn't connected yet", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        EatsWordmark(Modifier.height(34.dp))

        Spacer(Modifier.height(36.dp))
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Sign in to order from restaurants near you.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        EatsTextField(
            value = identifier,
            onValueChange = {
                identifier = it
                viewModel.clearError()
            },
            label = "Email or mobile number",
            error = identifierError,
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(8.dp))
        EatsTextField(
            value = password,
            onValueChange = {
                password = it
                viewModel.clearError()
            },
            label = "Password",
            error = passwordError,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailing = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(
                        text = if (showPassword) "Hide" else "Show",
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )

        TextButton(
            onClick = onForgotPasswordClick,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Forgot password?", color = linkColor, fontWeight = FontWeight.SemiBold)
        }

        ui.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        EatsPrimaryButton(text = "Sign in", onClick = { submit() }, loading = ui.loading)

        Spacer(Modifier.height(20.dp))
        OrDivider()
        Spacer(Modifier.height(20.dp))

        EatsOutlinedButton(text = "Continue with Google", onClick = { notConnected("Google sign-in") })
        Spacer(Modifier.height(12.dp))
        EatsOutlinedButton(text = "Continue with Apple", onClick = { notConnected("Apple sign-in") })

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "New to eats?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSignUpClick) {
                Text("Create account", color = linkColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
