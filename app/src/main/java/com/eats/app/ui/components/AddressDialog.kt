package com.eats.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.eats.app.auth.Validators

/**
 * Dialog for entering or changing the delivery address. It validates before saving and
 * keeps the dialog open with an error message until the address looks usable.
 */
@Composable
fun AddressDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(current) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val error = if (submitted) Validators.address(address) else null

    fun save() {
        submitted = true
        if (Validators.address(address) == null) onSave(address.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delivery address") },
        text = {
            EatsTextField(
                value = address,
                onValueChange = { address = it },
                label = "Street, suburb, city",
                placeholder = "12 Oak Street, Vincent",
                error = error,
                imeAction = ImeAction.Done,
                onImeAction = { save() },
            )
        },
        confirmButton = { TextButton(onClick = { save() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
