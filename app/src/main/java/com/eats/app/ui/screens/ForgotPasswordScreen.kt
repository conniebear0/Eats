package com.eats.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.eats.app.ui.components.EatsPrimaryButton
import com.eats.app.ui.components.EatsTextField
import com.eats.app.ui.components.ScreenHeader

/**
 * Password reset in two steps. Step 1 asks for the email or mobile number and requests a code.
 * Step 2 asks for the code and a new password. With no server to send an SMS or email, the code is
 * shown on screen (marked as a demo); a real backend would deliver it instead.
 */
@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var step by rememberSaveable { mutableIntStateOf(1) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var demoCode by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.clearError() }
    // Back on step 2 returns to step 1 instead of leaving the screen.
    BackHandler(enabled = step == 2) {
        step = 1
        submitted = false
        viewModel.clearError()
    }

    val identifierError = if (submitted && step == 1) Validators.emailOrMobile(identifier) else null
    val codeError = if (submitted && step == 2) Validators.resetCode(code) else null
    val passwordError = if (submitted && step == 2) Validators.newPassword(password) else null
    val confirmError = if (submitted && step == 2) Validators.confirmPassword(password, confirm) else null
    val linkColor = MaterialTheme.colorScheme.tertiary

    fun requestCode() {
        submitted = true
        if (Validators.emailOrMobile(identifier) != null) return
        focusManager.clearFocus()
        viewModel.requestPasswordReset(identifier.trim()) { sent ->
            demoCode = sent
            step = 2
            submitted = false
        }
    }

    fun resetPassword() {
        submitted = true
        if (Validators.resetCode(code) != null ||
            Validators.newPassword(password) != null ||
            Validators.confirmPassword(password, confirm) != null
        ) return
        focusManager.clearFocus()
        viewModel.resetPassword(identifier.trim(), code.trim(), password) {
            Toast.makeText(context, "Password updated. Sign in with your new password.", Toast.LENGTH_LONG).show()
            onDone()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Reset password", onBack = if (step == 2) ({ step = 1; submitted = false }) else onBack)
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            if (step == 1) {
                Text(
                    "Enter the email or mobile number you signed up with and we will send you a 6 digit code.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                EatsTextField(
                    value = identifier,
                    onValueChange = { identifier = it; viewModel.clearError() },
                    label = "Email or mobile number",
                    error = identifierError,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    onImeAction = { requestCode() },
                )
            } else {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "Demo mode: your code is $demoCode. A live app would send it by SMS or email. It expires in 10 minutes.",
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(20.dp))
                EatsTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6); viewModel.clearError() },
                    label = "6 digit code",
                    error = codeError,
                    keyboardType = KeyboardType.NumberPassword,
                )
                Spacer(Modifier.height(8.dp))
                EatsTextField(
                    value = password,
                    onValueChange = { password = it; viewModel.clearError() },
                    label = "New password",
                    error = passwordError,
                    helper = "At least 8 characters",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Hide" else "Show", color = linkColor, fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                EatsTextField(
                    value = confirm,
                    onValueChange = { confirm = it; viewModel.clearError() },
                    label = "Confirm new password",
                    error = confirmError,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onImeAction = { resetPassword() },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                )
            }

            ui.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            EatsPrimaryButton(
                text = if (step == 1) "Send code" else "Reset password",
                onClick = { if (step == 1) requestCode() else resetPassword() },
                loading = ui.loading,
            )
            if (step == 2) {
                TextButton(onClick = { requestCode() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Send a new code", color = linkColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
