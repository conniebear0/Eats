package com.eats.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eats.app.support.bot.AiSettings
import com.eats.app.support.bot.LearningMemory
import kotlinx.coroutines.launch

/** "What I've learned": the wording the bot picked up from corrections, with a way to undo it. */
@Composable
fun LearnedDialog(
    learned: List<LearningMemory.Learned>,
    mistakes: Int,
    onForget: (String) -> Unit,
    onForgetAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What I've learned") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "When I misunderstand and you show me what you meant, I remember it on this phone. " +
                        "Mistakes corrected so far: $mistakes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (learned.isEmpty()) {
                    Text("Nothing yet.", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
                learned.forEach { l ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("\"${l.phrase}\"", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "means: ${l.intent.label} · taught by " +
                                    (if (l.source == LearningMemory.Source.Ai) "the AI helper" else "a customer") +
                                    (if (l.hits > 1) " · used ${l.hits}×" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onForget(l.phrase) }) { Text("Forget") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (learned.isNotEmpty()) TextButton(onClick = onForgetAll) { Text("Forget everything") }
        },
    )
}

/**
 * Settings for the AI helper. A Groq key is built in (AiSettings.BUILT_IN_KEY) so it works out of the box;
 * paste another key here to replace it. Clearing the field goes back to the built-in key.
 */
@Composable
fun AiSettingsDialog(
    current: AiSettings,
    onSave: (AiSettings) -> Unit,
    onTest: suspend (AiSettings) -> String,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf(current.apiKey) }
    var model by remember { mutableStateOf(current.model) }
    var url by remember { mutableStateOf(current.baseUrl) }
    var enabled by remember { mutableStateOf(current.enabled) }
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun settings() = AiSettings(key.trim(), url.trim().ifBlank { AiSettings.DEFAULT_BASE_URL }, model.trim().ifBlank { AiSettings.DEFAULT_MODEL }, enabled)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI helper") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "The chatbot works without AI. With a key, the AI reads every message you type and either answers it " +
                        "or hands it to the bot's own rules, which still decide refunds, priority and order changes. " +
                        "It can never approve refunds. A Groq key is built into this test version; paste a different one " +
                        "here to replace it (free keys: console.groq.com/keys).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Use the AI helper", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("API address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(
                    onClick = {
                        testing = true
                        result = "Testing…"
                        scope.launch {
                            result = onTest(settings())
                            testing = false
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Test connection") }
                result?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(settings()); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
