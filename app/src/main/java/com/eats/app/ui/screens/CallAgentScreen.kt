package com.eats.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val AGENT = "Nomsa"

/**
 * "Call an agent" from the support chat. A demo for now: it rings, connects to an agent and
 * counts the call time, but no real call is made. Swap in a dialler intent or a VoIP SDK later.
 */
@Composable
fun CallAgentScreen(onEnd: (agent: String, seconds: Int) -> Unit) {
    var connected by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var hold by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(RING_MS)
        connected = true
        while (true) {
            delay(1000)
            seconds++
        }
    }
    BackHandler { onEnd(AGENT, if (connected) seconds else 0) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(112.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (connected) AGENT.take(1) else "e",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(
            if (connected) "$AGENT from eats support" else "eats support",
            Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            when {
                !connected -> "Calling…"
                hold -> "On hold · ${clock(seconds)}"
                else -> "Connected · ${clock(seconds)}"
            },
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (connected) {
            Text(
                "$AGENT can see your chat, your order and your complaints, so you don't have to start over.",
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CallToggle("Mute", muted) { muted = !muted }
            CallToggle("Speaker", speaker) { speaker = !speaker }
            CallToggle("Hold", hold) { hold = !hold }
        }
        Spacer(Modifier.height(32.dp))
        Surface(
            onClick = { onEnd(AGENT, if (connected) seconds else 0) },
            shape = CircleShape,
            color = EndRed,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Call, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(32.dp).rotate(135f))
            }
        }
        Text(
            "Demo call: no real call is made.",
            Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CallToggle(label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text(label.take(1), style = MaterialTheme.typography.titleLarge) }
        }
        Text(label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

private const val RING_MS = 2500L
private val EndRed = Color(0xFFDC2626)
