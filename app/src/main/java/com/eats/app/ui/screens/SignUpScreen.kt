package com.eats.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.auth.AuthViewModel
import com.eats.app.auth.SignUpForm
import com.eats.app.auth.Validators
import com.eats.app.ui.components.EatsPrimaryButton
import com.eats.app.ui.components.EatsTextField

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onSignInClick: () -> Unit,
    onSignedUp: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val linkColor = MaterialTheme.colorScheme.tertiary

    LaunchedEffect(Unit) { viewModel.clearError() }

    val firstNameError = if (submitted) Validators.required(firstName, "Enter your first name") else null
    val lastNameError = if (submitted) Validators.required(lastName, "Enter your last name") else null
    val mobileError = if (submitted) Validators.mobile(mobile) else null
    val emailError = if (submitted) Validators.email(email) else null
    val passwordError = if (submitted) Validators.newPassword(password) else null
    val termsError = submitted && !accepted

    fun submit() {
        submitted = true
        val valid = Validators.required(firstName, "") == null &&
            Validators.required(lastName, "") == null &&
            Validators.mobile(mobile) == null &&
            Validators.email(email) == null &&
            Validators.newPassword(password) == null &&
            accepted
        if (valid) {
            focusManager.clearFocus()
            viewModel.signUp(
                SignUpForm(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    mobile = mobile.filter { it.isDigit() }.removePrefix("0"),
                    email = email.trim(),
                    password = password,
                ),
                onSignedUp,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        Spacer(Modifier.height(8.dp))
        Text("Create your account", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tell us a little about you to get started.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EatsTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = "First name",
                modifier = Modifier.weight(1f),
                error = firstNameError,
            )
            EatsTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = "Last name",
                modifier = Modifier.weight(1f),
                error = lastNameError,
            )
        }
        Spacer(Modifier.height(8.dp))
        EatsTextField(
            value = mobile,
            onValueChange = { mobile = it.filter { c -> c.isDigit() }.take(10) },
            label = "Mobile number",
            placeholder = "82 123 4567",
            prefix = "+27 ",
            error = mobileError,
            keyboardType = KeyboardType.Phone,
        )
        Spacer(Modifier.height(8.dp))
        EatsTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            error = emailError,
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(8.dp))
        EatsTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            helper = "At least 8 characters",
            error = passwordError,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { focusManager.clearFocus() },
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

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = buildAnnotatedString {
                        append("I agree to the ")
                        withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
                            append("Terms of Use")
                        }
                        append(" and ")
                        withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
                            append("Privacy Policy")
                        }
                        append(".")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (termsError) {
                    Text(
                        "Accept the terms to continue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        ui.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        EatsPrimaryButton(text = "Create account", onClick = { submit() }, loading = ui.loading)

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Already have an account?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSignInClick) {
                Text("Sign in", color = linkColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
