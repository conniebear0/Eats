package com.eats.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eats.app.data.DishArt
import com.eats.app.support.bot.Complaint
import com.eats.app.support.bot.ComplaintStatus
import com.eats.app.support.bot.Fmt
import com.eats.app.support.bot.Severity
import com.eats.app.support.bot.SupportStore
import com.eats.app.ui.components.EatsOutlinedButton
import com.eats.app.ui.components.EatsPrimaryButton
import com.eats.app.ui.components.EmptyState
import com.eats.app.ui.components.Panel
import com.eats.app.ui.components.ScreenHeader
import com.eats.app.ui.components.TagPill

/** Every complaint the support chat logged for this customer, newest first. */
@Composable
fun ComplaintsScreen(
    complaints: List<Complaint>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenChat: () -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = "My complaints", onBack = onBack)
        if (complaints.isEmpty()) {
            EmptyState(
                art = DishArt.Burger,
                title = "No complaints",
                message = "If something goes wrong with an order, the support chat logs it here so you can follow every step.",
                actionLabel = "Open support chat",
                onAction = onOpenChat,
            )
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(complaints, key = { it.id }) { c ->
                Panel(Modifier.clip(RoundedCornerShape(20.dp)).clickable { onOpen(c.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.reference, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusPill(c, nowMillis)
                    }
                    Text(c.subcategory, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(c.restaurantName, c.orderId?.let { "Order $it" }, Fmt.dateTime(c.createdAt)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    StatusTracker(c.status)
                    Text(
                        responseLine(c, nowMillis),
                        Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (c.overdue(nowMillis)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                    )
                    c.refund?.let {
                        Text(
                            "Refund ${Fmt.money(it.amount)} ${it.method.label}: ${it.status.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** One complaint: progress, what happened, the chat transcript, and what the customer can do next. */
@Composable
fun ComplaintDetailScreen(
    complaint: Complaint?,
    onBack: () -> Unit,
    onAddNote: (String) -> Unit,
    onClose: () -> Unit,
    onReopen: () -> Unit,
    onWithdraw: () -> Unit,
    onChase: () -> Unit,
    onCall: () -> Unit,
    onDemoAdvance: () -> Unit,
    onDemoDecline: () -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
) {
    if (complaint == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(title = "Complaint", onBack = onBack)
            EmptyState(art = DishArt.Burger, title = "Complaint not found", message = "It may belong to another account.")
        }
        return
    }
    val c = complaint
    var note by rememberSaveable { mutableStateOf("") }
    var showTranscript by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = c.reference, onBack = onBack) { StatusPill(c, nowMillis) }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(c.subcategory, style = MaterialTheme.typography.headlineSmall)
            Panel {
                StatusTracker(c.status)
                Spacer(Modifier.height(12.dp))
                DetailRow("Category", "${c.category.label} · ${c.category.department}")
                DetailRow("Priority", "${c.severity.label} (respond ${c.severity.responseText})")
                DetailRow("Response", responseLine(c, nowMillis))
                c.orderId?.let { DetailRow("Order", listOfNotNull(it, c.restaurantName).joinToString(" · ")) }
                DetailRow("Logged", Fmt.dateTime(c.createdAt))
                c.refund?.let { DetailRow("Refund", "${Fmt.money(it.amount)} ${it.method.label} · ${it.status.label}") }
                if (c.escalated) DetailRow("Supervisor", "Raised to a supervisor")
            }

            Text("What happened", style = MaterialTheme.typography.titleLarge)
            Panel {
                Text(c.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // The AI helper's case summary is for the agent, so it isn't shown to the customer.
                c.events.filter { it.by != SupportStore.BY_ASSISTANT }.forEach { e ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        Fmt.dateTime(e.at) + " · " + when (e.by) {
                            "agent" -> "Support agent"
                            "you" -> "You"
                            SupportStore.BY_ASSISTANT -> "AI helper"
                            else -> "eats assistant"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(e.text, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (c.transcript.isNotEmpty()) {
                TextButton(onClick = { showTranscript = !showTranscript }) {
                    Text(if (showTranscript) "Hide chat transcript" else "Show chat transcript (${c.transcript.size} messages)")
                }
                if (showTranscript) {
                    Panel {
                        c.transcript.forEach { t ->
                            Text(
                                (if (t.fromCustomer) "You: " else "eats: ") + t.text,
                                Modifier.padding(vertical = 3.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (t.fromCustomer) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text("Add information", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                placeholder = { Text("e.g. the bag was sealed, I have a photo") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            EatsOutlinedButton("Add to complaint", onClick = {
                if (note.isNotBlank()) {
                    onAddNote(note)
                    note = ""
                }
            })

            when {
                c.status == ComplaintStatus.Resolved -> {
                    EatsPrimaryButton("It's sorted, close it", onClick = onClose)
                    EatsOutlinedButton("Still not fixed, reopen it", onClick = onReopen)
                }
                c.status.isOpen -> {
                    EatsPrimaryButton("Call an agent", onClick = onCall)
                    EatsOutlinedButton("Ask a supervisor to look at it", onClick = onChase)
                    TextButton(onClick = onWithdraw) { Text("Withdraw this complaint", color = MaterialTheme.colorScheme.error) }
                }
                else -> EatsOutlinedButton("Reopen this complaint", onClick = onReopen)
            }

            if (c.status.isOpen) {
                Panel {
                    Text("Demo: support agent", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Stands in for the agent so you can show the whole journey.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onDemoAdvance) {
                        Text(if (c.status == ComplaintStatus.Open) "Agent picks it up (Pending)" else "Agent resolves it (Resolved)")
                    }
                    if (c.refund != null) TextButton(onClick = onDemoDecline) { Text("Agent declines the refund") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(0.35f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.65f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(c: Complaint, now: Long) {
    when {
        c.overdue(now) -> TagPill("Overdue")
        c.status == ComplaintStatus.Resolved || c.status == ComplaintStatus.Closed -> TagPill(c.status.label, positive = true)
        c.severity == Severity.Critical && c.status.isOpen -> TagPill("Critical")
        else -> TagPill(c.status.label)
    }
}

/** Open → Pending → Resolved → Closed. */
@Composable
private fun StatusTracker(status: ComplaintStatus) {
    val steps = listOf(ComplaintStatus.Open, ComplaintStatus.Pending, ComplaintStatus.Resolved, ComplaintStatus.Closed)
    val reachedIndex = when (status) {
        ComplaintStatus.Unresolved -> -1
        else -> steps.indexOf(status)
    }
    if (status == ComplaintStatus.Unresolved) {
        Text("Withdrawn: closed without a fix", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { i, step ->
            val reached = i <= reachedIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(
                            if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                )
                Text(
                    step.label,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (step == status) FontWeight.Bold else FontWeight.Normal,
                    color = if (reached) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun responseLine(c: Complaint, now: Long): String = when {
    !c.status.isOpen -> when (c.status) {
        ComplaintStatus.Resolved -> "Resolved. Close it if you're happy, or reopen it."
        ComplaintStatus.Closed -> "Closed. No further action needed."
        else -> "Closed without a fix."
    }
    c.overdue(now) -> "Overdue: was due ${Fmt.due(c.dueAt, now)}"
    c.severity == Severity.Critical -> "A person is handling it now"
    else -> "A person will respond by ${Fmt.due(c.dueAt, now)}"
}
