package com.eats.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.data.DishArt
import com.eats.app.data.SampleData
import com.eats.app.data.money
import com.eats.app.order.ActiveOrder
import com.eats.app.order.OrderStep
import com.eats.app.order.OrderViewModel
import com.eats.app.ui.components.DishPlate
import com.eats.app.ui.components.EatsOutlinedButton
import com.eats.app.ui.components.EatsPrimaryButton
import com.eats.app.ui.components.Panel
import com.eats.app.ui.components.ScreenHeader
import com.eats.app.ui.theme.StarYellow

/**
 * Follows the active order. In this prototype the order advances by itself in real time over the
 * restaurant's delivery estimate
 * (see OrderViewModel). Replace that with push updates or polling from your backend.
 */
@Composable
fun TrackingScreen(
    viewModel: OrderViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onHelp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val order = state.activeOrder
    if (order == null) {
        NoActiveOrder(onBack)
        return
    }
    val restaurant = SampleData.restaurant(order.restaurantId)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = when {
                order.cancelled -> "Cancelled"
                order.delivered -> "Delivered"
                else -> "Track order"
            },
            onBack = onBack,
        ) {
            Text(order.id, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusHeader(order, restaurant?.name.orEmpty(), restaurant?.area.orEmpty())
            RouteMap(progress = order.routeProgress)
            Panel {
                OrderStep.entries.forEachIndexed { i, step ->
                    StepRow(
                        title = if (step == OrderStep.OnTheWay && order.pickup) "Ready for pick-up" else step.title,
                        detail = step.detail,
                        done = order.step.ordinal > i,
                        current = order.step.ordinal == i,
                        last = i == OrderStep.entries.lastIndex,
                    )
                }
            }

            if (order.delivered) {
                RatingPanel(order.rating, restaurant?.name.orEmpty(), viewModel::rateOrder)
            } else if (!order.pickup && !order.cancelled) {
                DriverPanel()
            }

            Panel {
                Text(restaurant?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                order.lines.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${l.qty}× ${l.dish.name}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(l.total), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total · ${order.pay.label(order.pickup)}", style = MaterialTheme.typography.titleMedium)
                    Text(money(order.total), style = MaterialTheme.typography.titleMedium)
                }
            }

            DemoPanel(order, viewModel)
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (order.finished) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EatsOutlinedButton("Help", onClick = onHelp, modifier = Modifier.weight(1f))
                    EatsPrimaryButton("Done", onClick = { viewModel.finishOrder(); onDone() }, modifier = Modifier.weight(2f))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EatsOutlinedButton("Help", onClick = onHelp, modifier = Modifier.weight(1f))
                    EatsOutlinedButton("Skip ahead (demo)", onClick = viewModel::skipAhead, modifier = Modifier.weight(2f))
                }
            }
        }
    }
}

@Composable
private fun NoActiveOrder(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DishPlate(DishArt.Burger, Modifier.size(110.dp), steam = true)
        Text("No active order", Modifier.padding(top = 16.dp, bottom = 20.dp), style = MaterialTheme.typography.titleLarge)
        EatsOutlinedButton("Back to home", onClick = onBack)
    }
}

@Composable
private fun StatusHeader(order: ActiveOrder, restaurantName: String, area: String) {
    val pulse = rememberInfiniteTransition(label = "steam").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "steamAlpha",
    )
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (order.cancelled) {
                Text("Order cancelled.", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "You won't be charged, because you pay on ${if (order.pickup) "collection" else "delivery"}.",
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (order.delivered) {
                Text("Bon appétit.", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Your order from $restaurantName has arrived.",
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${order.minutesLeft}", fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp)
                    Text(
                        " min",
                        Modifier.padding(bottom = 6.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (order.pickup) "Ready for pick-up at $area" else "Arriving at ${order.address}",
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (order.lateMinutes > 0) {
                    Text(
                        "Running about ${order.lateMinutes} min late",
                        Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        val art = SampleData.restaurant(order.restaurantId)?.art ?: DishArt.Burger
        DishPlate(art, Modifier.size(76.dp), steam = !order.finished, steamAlpha = pulse.value)
    }
}

// Route in a 340 x 190 box: restaurant bottom-left, home top-right.
private const val ROUTE = "M44 150 C100 150 70 55 150 55 S210 120 296 40"

@Composable
private fun RouteMap(progress: Float) {
    val rider by animateFloatAsState(progress, animationSpec = tween(900, easing = LinearEasing), label = "rider")
    val route = remember { PathParser().parsePathString(ROUTE).toPath() }
    val measure = remember { PathMeasure().also { it.setPath(route, false) } }
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val street = MaterialTheme.colorScheme.outlineVariant
    val brand = MaterialTheme.colorScheme.primary
    val page = MaterialTheme.colorScheme.background

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(340f / 190f)
            .background(bg, RoundedCornerShape(24.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
    ) {
        scale(size.width / 340f, size.width / 340f, pivot = Offset.Zero) {
            // streets
            listOf(55f, 120f).forEach { y -> drawLine(street, Offset(0f, y), Offset(340f, y), 10f, StrokeCap.Round) }
            listOf(70f, 190f, 280f).forEach { x -> drawLine(street, Offset(x, 0f), Offset(x, 190f), 10f, StrokeCap.Round) }
            listOf(130f, 235f).forEach { x -> drawLine(street, Offset(x, 0f), Offset(x, 190f), 3f) }
            drawLine(street, Offset(0f, 160f), Offset(340f, 160f), 3f)

            // dotted route
            drawPath(
                route,
                brand.copy(alpha = 0.55f),
                style = Stroke(5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 11f))),
            )

            // restaurant
            drawCircle(page, 15f, Offset(44f, 150f))
            drawCircle(brand, 15f, Offset(44f, 150f), style = Stroke(3f))
            drawCircle(brand, 7f, Offset(44f, 150f))

            // home
            drawCircle(brand, 16f, Offset(296f, 40f))
            translate(296f, 40f) {
                val house = PathParser().parsePathString("M-6 1 L0 -5 L6 1 V7 H-6Z").toPath()
                drawPath(house, Color.White)
            }

            // rider
            val pos = measure.getPosition(measure.length * rider)
            drawCircle(brand.copy(alpha = 0.22f), 17f, pos)
            drawCircle(brand, 10f, pos)
            drawCircle(Color.White, 10f, pos, style = Stroke(3f))
        }
    }
}

@Composable
private fun StepRow(title: String, detail: String, done: Boolean, current: Boolean, last: Boolean) {
    val brand = MaterialTheme.colorScheme.primary
    val line = if (done) brand else MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!last) {
                    drawLine(line, Offset(13.dp.toPx(), 32.dp.toPx()), Offset(13.dp.toPx(), size.height), 2.dp.toPx())
                }
            },
    ) {
        Box(
            Modifier
                .size(28.dp)
                .then(
                    if (current) Modifier.background(brand.copy(alpha = 0.2f), CircleShape).padding(3.dp) else Modifier,
                )
                .border(2.dp, if (done || current) brand else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .background(if (done) brand else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Column(Modifier.padding(start = 12.dp, bottom = if (last) 0.dp else 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (done || current) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DriverPanel() {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("S", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(ActiveOrder.DRIVER_NAME, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = StarYellow, modifier = Modifier.size(13.dp))
                    Text(
                        " 4.9 · ${ActiveOrder.DRIVER_VEHICLE}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingPanel(rating: Int, restaurantName: String, onRate: (Int) -> Unit) {
    Panel {
        Text(
            "How was your meal?",
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            (1..5).forEach { i ->
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "$i star${if (i > 1) "s" else ""}",
                    tint = if (rating >= i) StarYellow else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onRate(i) }
                        .padding(5.dp),
                )
            }
        }
        if (rating > 0) {
            Text(
                "Thanks, we passed it on to $restaurantName.",
                Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/**
 * Demo panel: stands in for the restaurant and driver so every support scenario can be shown
 * (late, delivered but not received, cancelled...). Remove it for a real release.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoPanel(order: ActiveOrder, viewModel: OrderViewModel) {
    Panel {
        Text("Demo panel", style = MaterialTheme.typography.titleMedium)
        Text(
            "Stands in for the restaurant and driver. Set up a situation here, then tap Help to ask the support chat about it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Order status", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(OrderStep.entries) { step ->
                FilterChip(
                    selected = !order.cancelled && order.step == step,
                    onClick = { viewModel.demoSetStep(step) },
                    label = { Text(if (step == OrderStep.OnTheWay && order.pickup) "Ready" else step.title) },
                )
            }
            item {
                FilterChip(selected = order.cancelled, onClick = viewModel::demoCancel, label = { Text("Cancelled") })
            }
        }
        Text("Running late", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf(0, 15, 30)) { minutes ->
                FilterChip(
                    selected = order.lateMinutes == minutes,
                    onClick = { viewModel.demoSetLate(minutes) },
                    label = { Text(if (minutes == 0) "On time" else "$minutes min late") },
                )
            }
        }
        if (!order.autoAdvance && !order.finished) {
            TextButton(onClick = viewModel::demoResume) { Text("Resume the live countdown") }
        }
    }
}
