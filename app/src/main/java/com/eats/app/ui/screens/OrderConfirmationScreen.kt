package com.eats.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.data.money
import com.eats.app.order.OrderViewModel
import com.eats.app.ui.components.EatsOutlinedButton
import com.eats.app.ui.components.EatsPrimaryButton
import com.eats.app.ui.components.Panel

/**
 * Shown right after an order is placed. Back and "Back to home" both return Home;
 * "Track order" opens live tracking.
 */
@Composable
fun OrderConfirmationScreen(
    viewModel: OrderViewModel,
    onTrack: () -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    val order = viewModel.state.collectAsStateWithLifecycle().value.activeOrder

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text("Order placed!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (order == null) "Your order is on its way to the kitchen."
            else "Order ${order.id} is being prepared. ${if (order.pickup) "It will be ready" else "It should arrive"} in about ${order.etaMinutes} min.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (order != null) {
            Spacer(Modifier.height(20.dp))
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${order.lines.sumOf { it.qty }} items",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(money(order.total), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    order.pay.label(order.pickup),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        EatsPrimaryButton(text = "Track order", onClick = onTrack)
        Spacer(Modifier.height(12.dp))
        EatsOutlinedButton(text = "Back to home", onClick = onDone)
    }
}
