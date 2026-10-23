package com.eats.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.data.CartLine
import com.eats.app.data.DishArt
import com.eats.app.data.money
import com.eats.app.order.FulfilmentMode
import com.eats.app.order.OrderUiState
import com.eats.app.order.OrderViewModel
import com.eats.app.order.Totals
import com.eats.app.ui.components.DishImage
import com.eats.app.ui.components.EatsPrimaryButton
import com.eats.app.ui.components.EatsTextField
import com.eats.app.ui.components.EmptyState
import com.eats.app.ui.components.ModeToggle
import com.eats.app.ui.components.Panel
import com.eats.app.ui.components.QuantityStepper
import com.eats.app.ui.components.ScreenHeader
import com.eats.app.ui.theme.successColor

/** The cart: items, promo code, driver tip and totals. "Checkout" continues to [CheckoutScreen]. */
@Composable
fun CartScreen(
    viewModel: OrderViewModel,
    onBack: () -> Unit,
    onAddMore: (restaurantId: String) -> Unit,
    onFindFood: () -> Unit,
    onCheckout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val restaurant = state.cartRestaurant
        val hasItems = restaurant != null && state.lines.isNotEmpty()

        ScreenHeader(title = "Your cart", onBack = onBack) {
            if (hasItems) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
        }

        if (restaurant == null || !hasItems) {
            EmptyState(
                art = DishArt.Burger,
                title = "Your cart is empty",
                message = "Add something from a restaurant and it will show up here.",
                actionLabel = "Find food",
                onAction = onFindFood,
            )
            return@Column
        }

        val totals = state.totals()
        val pickup = state.mode == FulfilmentMode.Pickup

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ModeToggle(state.mode, viewModel::setMode)

            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(restaurant.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (pickup) "Pick-up at ${restaurant.area}" else "${restaurant.etaMinutes} min delivery",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { onAddMore(restaurant.id) }) {
                        Text("Add more", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                    }
                }
                state.lines.forEachIndexed { i, line ->
                    HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    CartRow(
                        line = line,
                        onMinus = { viewModel.changeQty(i, -1) },
                        onPlus = { viewModel.changeQty(i, +1) },
                    )
                }
            }

            if (!pickup && !totals.freeDelivery && restaurant.freeDeliveryOver > 0) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "Add ${money(restaurant.freeDeliveryOver - totals.subtotal)} more for R0 delivery",
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            PromoPanel(state, viewModel)

            if (!pickup) TipPanel(selected = state.tip, onSelect = viewModel::setTip)

            Panel { TotalsLines(totals, pickup) }
            Spacer(Modifier.height(4.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            EatsPrimaryButton(text = "Checkout · ${money(totals.total)}", onClick = onCheckout)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear your cart?") },
            text = { Text("All items will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCart()
                    confirmClear = false
                }) { Text("Clear cart") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep items") } },
        )
    }
}

/** Subtotal, fees, tip, promo and total. Also used on the checkout screen. */
@Composable
fun TotalsLines(totals: Totals, pickup: Boolean) {
    TotalLine("Subtotal", money(totals.subtotal))
    TotalLine(if (pickup) "Pick-up" else "Delivery fee", if (totals.delivery == 0) "R0" else money(totals.delivery))
    if (!pickup) TotalLine("Service fee", money(totals.service))
    if (totals.tip > 0) TotalLine("Driver tip", money(totals.tip))
    if (totals.discount > 0) TotalLine("Promo ${OrderUiState.PROMO_CODE}", "−${money(totals.discount)}", highlight = true)
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Total", style = MaterialTheme.typography.titleLarge)
        Text(money(totals.total), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun TipPanel(selected: Int, onSelect: (Int) -> Unit) {
    Panel {
        Text("Tip your driver", style = MaterialTheme.typography.titleMedium)
        Text(
            "100% goes to them.",
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 10, 20, 30).forEach { amount ->
                val isSelected = selected == amount
                Surface(
                    onClick = { onSelect(amount) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                    border = if (isSelected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    // Centred both ways, whatever the font scale.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (amount == 0) "None" else money(amount), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun CartRow(line: CartLine, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        DishImage(line.dish, Modifier.size(56.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(line.dish.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            val summary = line.summary()
            if (summary.isNotEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            QuantityStepper(line.qty, onMinus, onPlus, Modifier.padding(top = 8.dp), buttonSize = 30.dp)
        }
        Text(money(line.total), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

/** Promo code field with validation. Shows a green confirmation once a code is applied. */
@Composable
private fun PromoPanel(state: OrderUiState, viewModel: OrderViewModel) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun apply() {
        error = when {
            code.isBlank() -> "Enter a promo code first."
            viewModel.applyPromo(code) -> null
            else -> "That code is not valid."
        }
    }

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (state.promoApplied) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = successColor(), modifier = Modifier.size(18.dp))
                    Text(
                        " ${OrderUiState.PROMO_CODE}: R${OrderUiState.PROMO_OFF} off applied",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = successColor(),
                    )
                    TextButton(onClick = { viewModel.removePromo(); code = "" }) { Text("Remove") }
                }
            } else {
                Text("Promo code", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EatsTextField(
                        value = code,
                        onValueChange = { code = it.uppercase(); error = null },
                        label = "Code",
                        modifier = Modifier.weight(1f),
                        error = error,
                        helper = "New here? Try ${OrderUiState.PROMO_CODE}.",
                        imeAction = ImeAction.Done,
                        onImeAction = { apply() },
                    )
                    OutlinedButton(
                        onClick = { apply() },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String, highlight: Boolean = false) {
    val color = if (highlight) successColor() else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = if (highlight) FontWeight.SemiBold else null)
    }
}
