package com.eats.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.auth.Validators
import com.eats.app.data.DishArt
import com.eats.app.data.money
import com.eats.app.order.FulfilmentMode
import com.eats.app.order.OrderViewModel
import com.eats.app.order.PayMethod
import com.eats.app.ui.components.EatsPrimaryButton
import com.eats.app.ui.components.EatsTextField
import com.eats.app.ui.components.EmptyState
import com.eats.app.ui.components.Panel
import com.eats.app.ui.components.ScreenHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Last step before ordering: where it goes, how to pay, and a final look at the totals.
 * A delivery address is required and validated. Placing the order shows a loading button,
 * then [onOrderPlaced] moves on to the confirmation screen.
 */
@Composable
fun CheckoutScreen(
    viewModel: OrderViewModel,
    onBack: () -> Unit,
    onFindFood: () -> Unit,
    onOrderPlaced: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var address by rememberSaveable { mutableStateOf(state.address) }
    var instructions by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var placing by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }

    val restaurant = state.cartRestaurant
    val pickup = state.mode == FulfilmentMode.Pickup

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        ScreenHeader(title = "Checkout", onBack = if (placing) null else onBack)

        if (restaurant == null || state.lines.isEmpty()) {
            // After placing, the cart empties a moment before the screen closes. Show nothing then.
            if (!placed) {
                EmptyState(
                    art = DishArt.Burger,
                    title = "Nothing to check out",
                    message = "Your cart is empty.",
                    actionLabel = "Find food",
                    onAction = onFindFood,
                )
            }
            return@Column
        }

        val totals = state.totals()
        val addressError = if (submitted && !pickup) Validators.address(address) else null

        fun placeOrder() {
            submitted = true
            if (!pickup && Validators.address(address) != null) return
            focus.clearFocus()
            placing = true
            scope.launch {
                delay(900) // pretend to talk to the server
                placed = true
                viewModel.placeOrder(address, instructions)
                onOrderPlaced()
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Panel {
                Text("Order summary", style = MaterialTheme.typography.titleMedium)
                Text(
                    restaurant.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("${line.qty} × ${line.dish.name}", style = MaterialTheme.typography.bodyLarge)
                            val summary = line.summary()
                            if (summary.isNotEmpty()) {
                                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(money(line.total), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Panel {
                if (pickup) {
                    Text("Pick-up", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Collect at ${restaurant.name}, ${restaurant.area}. Ready in about ${restaurant.etaMinutes} min.",
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Delivery address", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    EatsTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = "Street, suburb, city",
                        error = addressError,
                        helper = "We will save this to your profile.",
                    )
                    Spacer(Modifier.height(6.dp))
                    EatsTextField(
                        value = instructions,
                        onValueChange = { instructions = it.take(120) },
                        label = "Delivery instructions (optional)",
                        placeholder = "Gate code, which door, call on arrival",
                        imeAction = ImeAction.Done,
                        onImeAction = { focus.clearFocus() },
                    )
                }
            }

            Panel {
                Text("Pay with", style = MaterialTheme.typography.titleMedium)
                PayMethod.entries.forEach { method ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.setPay(method) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.width(58.dp).height(40.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    method.badge,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Text(
                            method.label(pickup),
                            Modifier.weight(1f).padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        RadioButton(
                            selected = state.pay == method,
                            onClick = { viewModel.setPay(method) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }

            Panel { TotalsLines(totals, pickup) }
            Spacer(Modifier.height(4.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            EatsPrimaryButton(
                text = "Place order · ${money(totals.total)}",
                loading = placing,
                onClick = { placeOrder() },
            )
        }
    }
}
