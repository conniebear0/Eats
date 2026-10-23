package com.eats.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eats.app.support.bot.ChatItem
import com.eats.app.support.bot.InfoCard
import com.eats.app.ui.components.ScreenHeader

/**
 * Support chat. Bot answers can carry a small card (order, refund, complaint). Buttons under the
 * chat send their label as if typed. The phone icon opens the (demo) call screen; the menu opens
 * My complaints, what the bot has learned, and the AI helper settings.
 */
@Composable
fun ChatScreen(
    messages: List<ChatItem>,
    quickReplies: List<String>,
    typing: Boolean,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    onCall: () -> Unit = {},
    onComplaints: () -> Unit = {},
    onLearned: () -> Unit = {},
    onAiSettings: () -> Unit = {},
) {
    var input by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // "Learned: ..." lines are for the team (see "What I've learned"), not for customers.
    val shown = remember(messages) { messages.filterNot { it.notice && it.text.startsWith("Learned:") } }

    LaunchedEffect(shown.size, typing) {
        val count = shown.size + if (typing) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                ScreenHeader(title = "Support", onBack = onBack) {
                    IconButton(onClick = onCall) { Icon(Icons.Filled.Call, contentDescription = "Call an agent") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("My complaints") }, onClick = { menuOpen = false; onComplaints() })
                            DropdownMenuItem(text = { Text("What I've learned") }, onClick = { menuOpen = false; onLearned() })
                            DropdownMenuItem(text = { Text("AI helper settings") }, onClick = { menuOpen = false; onAiSettings() })
                        }
                    }
                }
                Text(
                    "eats assistant · replies instantly",
                    Modifier.padding(start = 20.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(8.dp),
                ) {
                    if (quickReplies.isNotEmpty() && !typing) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(quickReplies) { option ->
                                AssistChip(onClick = { onSend(option) }, label = { Text(option) })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("Type a message") },
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                        )
                        IconButton(
                            onClick = { onSend(input); input = "" },
                            enabled = input.isNotBlank() && !typing,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.id }) { item ->
                when {
                    item.notice -> Notice(item.text)
                    item.card != null -> CardBubble(item.card)
                    else -> MessageBubble(item)
                }
            }
            if (typing) item(key = "typing") { TypingBubble() }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatItem) {
    val fromUser = message.fromUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = if (fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 290.dp),
        ) {
            Text(message.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
        }
        // No "AI answer" / "Learned" tags: customers don't need to know how a reply was produced.
    }
}

@Composable
private fun CardBubble(card: InfoCard) {
    Column(
        Modifier
            .widthIn(max = 300.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(card.title, style = MaterialTheme.typography.titleMedium)
        card.rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TypingBubble() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text("Thinking…", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
