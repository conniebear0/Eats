package com.eats.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.data.DishArt
import com.eats.app.data.money
import com.eats.app.order.ActiveOrder
import com.eats.app.order.OrderViewModel
import com.eats.app.order.PastOrder
import com.eats.app.ui.components.EmptyState
import com.eats.app.ui.components.Panel
import com.eats.app.ui.components.ScreenHeader
import com.eats.app.ui.components.TagPill
import com.eats.app.ui.theme.StarYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.eats.app.data.SampleData

/** Orders tab: the order in progress (tap to track it) and everything delivered before. */
@Composable
fun OrdersScreen(
    viewModel: OrderViewModel,
    onOpenTracking: () -> Unit,
    onOpenRestaurant: (String) -> Unit,
    onBrowse: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val active = state.activeOrder
    val past = state.history.filter { it.id != active?.id }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Your orders", onBack = null)
        if (active == null && past.isEmpty()) {
            EmptyState(
                art = DishArt.Burger,
                title = "No orders yet",
                message = "When you place an order it will show up here, so you can track it and order again.",
                actionLabel = "Find food",
                onAction = onBrowse,
            )
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (active != null) {
                item { Heading("In progress") }
                item { ActiveOrderCard(active, onClick = onOpenTracking) }
            }
            if (past.isNotEmpty()) {
                item { Heading("Past orders") }
                items(past, key = { it.id }) { order ->
                    PastOrderCard(order, onOrderAgain = { onOpenRestaurant(order.restaurantId) })
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun ActiveOrderCard(order: ActiveOrder, onClick: () -> Unit) {
    val name = SampleData.restaurant(order.restaurantId)?.name.orEmpty()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(order.id, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                when {
                    order.cancelled -> "Cancelled. You won't be charged."
                    order.delivered -> "Delivered. Tap to rate your meal."
                    else -> "${order.step.title} · about ${order.minutesLeft} min"
                },
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${order.lines.sumOf { it.qty }} items · ${money(order.total)}",
                Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Track order",
                Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun PastOrderCard(order: PastOrder, onOrderAgain: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(order.restaurantName, style = MaterialTheme.typography.titleMedium)
                Text(
                    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(order.placedAt)) +
                        " · " + order.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TagPill(if (order.pickup) "Pick-up" else "Delivered", positive = true)
        }
        order.lines.forEach { line ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${line.qty}× ${line.name}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(line.total), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Total ${money(order.total)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (order.rating > 0) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = StarYellow, modifier = Modifier.size(16.dp))
                Text(" ${order.rating}", fontWeight = FontWeight.Bold)
            }
        }
        TextButton(onClick = onOrderAgain, Modifier.padding(top = 4.dp)) {
            Text("Order again", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
        }
    }
}
