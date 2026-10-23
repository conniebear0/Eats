package com.eats.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.data.Dish
import com.eats.app.data.Restaurant
import com.eats.app.data.SampleData
import com.eats.app.data.money
import com.eats.app.order.FulfilmentMode
import com.eats.app.order.OrderViewModel
import com.eats.app.ui.components.CircleIconSurface
import com.eats.app.ui.components.DishImage
import com.eats.app.ui.components.DishPlate
import com.eats.app.ui.components.ModeToggle
import com.eats.app.ui.components.TagPill
import com.eats.app.ui.theme.HeartRed
import com.eats.app.ui.theme.StarYellow
import kotlinx.coroutines.launch

// Fixed rows above the menu: banner, info, promos, sticky "Explore menu" header.
private const val ROWS_BEFORE_MENU = 4

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RestaurantScreen(
    restaurantId: String,
    viewModel: OrderViewModel,
    onBack: () -> Unit,
    onOpenCart: () -> Unit,
) {
    val restaurant = SampleData.restaurant(restaurantId) ?: return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val chipState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var openDish by remember { mutableStateOf<Dish?>(null) }
    var pendingDish by remember { mutableStateOf<Dish?>(null) } // waiting for "start a new cart?" answer

    // Index of the first LazyColumn row of every menu section (header row, then its dishes).
    val sectionStarts = remember(restaurant) {
        var next = ROWS_BEFORE_MENU
        restaurant.menu.map { section -> next.also { next += 1 + section.items.size } }
    }
    val selectedSection by remember(sectionStarts) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            sectionStarts.indexOfLast { it <= first }.coerceAtLeast(0)
        }
    }
    LaunchedEffect(selectedSection) { chipState.animateScrollToItem(selectedSection) }

    val inCart = state.cartRestaurantId == restaurant.id && state.lines.isNotEmpty()
    fun qtyInCart(dish: Dish) =
        if (state.cartRestaurantId == restaurant.id) state.lines.filter { it.dish.id == dish.id }.sumOf { it.qty } else 0

    fun onDishClick(dish: Dish) {
        if (viewModel.conflictsWithCart(restaurant.id)) pendingDish = dish else openDish = dish
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (inCart) 100.dp else 32.dp),
        ) {
            item { Banner(restaurant) }
            item {
                RestaurantInfo(
                    restaurant = restaurant,
                    mode = state.mode,
                    onModeChange = viewModel::setMode,
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (restaurant.freeDeliveryOver > 0) {
                        item { Ribbon("R0 delivery fee on orders ${money(restaurant.freeDeliveryOver)}+") }
                    }
                    item { Ribbon("Code EATSFIRST: R30 off your first order") }
                }
            }
            stickyHeader {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        Text(
                            "Explore menu",
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LazyRow(
                            state = chipState,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(restaurant.menu) { i, section ->
                                FilterChip(
                                    selected = i == selectedSection,
                                    onClick = {
                                        scope.launch {
                                            listState.animateScrollToItem(
                                                sectionStarts[i],
                                                scrollOffset = -with(density) { 104.dp.roundToPx() },
                                            )
                                        }
                                    },
                                    label = { Text(section.title, style = MaterialTheme.typography.labelLarge) },
                                    shape = RoundedCornerShape(19.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                                        selectedLabelColor = MaterialTheme.colorScheme.background,
                                    ),
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            restaurant.menu.forEach { section ->
                item(key = "h-${section.title}") {
                    Text(
                        section.title,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                itemsIndexed(section.items, key = { _, d -> d.id }) { i, dish ->
                    DishRow(
                        dish = dish,
                        qtyInCart = qtyInCart(dish),
                        showDivider = i < section.items.lastIndex,
                        onClick = { onDishClick(dish) },
                    )
                }
            }
        }

        // Floating back / favourite / share over the banner.
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CircleIconSurface(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val fav = restaurant.id in state.favourites
                CircleIconSurface(onClick = {
                    val now = viewModel.toggleFavourite(restaurant.id)
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(if (now) "Saved to favourites" else "Removed from favourites")
                    }
                }) {
                    Icon(
                        if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favourite",
                        tint = if (fav) HeartRed else MaterialTheme.colorScheme.onBackground,
                    )
                }
                CircleIconSurface(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Hungry? Try ${restaurant.name} on eats: ${restaurant.cuisine}, ${restaurant.etaMinutes} min.")
                    }
                    context.startActivity(Intent.createChooser(send, "Share ${restaurant.name}"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (inCart) 84.dp else 8.dp),
        )

        if (inCart) {
            val totals = state.totals()
            Surface(
                onClick = onOpenCart,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 8.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.22f)) {
                        Text(
                            "${state.cartCount}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text("View cart", style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp))
                    Text(money(totals.subtotal), style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp))
                }
            }
        }
    }

    openDish?.let { dish ->
        DishSheet(
            dish = dish,
            onDismiss = { openDish = null },
            onAdd = { qty, selections, note, unit ->
                viewModel.addToCart(restaurant.id, dish, qty, selections, note, unit)
                openDish = null
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(
                        message = "Added $qty × ${dish.name}",
                        actionLabel = "View cart",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenCart()
                }
            },
        )
    }

    pendingDish?.let { dish ->
        val current = state.cartRestaurant
        AlertDialog(
            onDismissRequest = { pendingDish = null },
            title = { Text("Start a new cart?") },
            text = {
                Text("Your cart has items from ${current?.name ?: "another restaurant"}. Adding from ${restaurant.name} will clear it.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCart()
                    pendingDish = null
                    openDish = dish
                }) { Text("Start new cart") }
            },
            dismissButton = { TextButton(onClick = { pendingDish = null }) { Text("Keep current cart") } },
        )
    }
}

@Composable
private fun Banner(restaurant: Restaurant) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant),
                ),
            ),
    ) {
        if (restaurant.photo != null) {
            Image(
                painter = painterResource(restaurant.photo),
                contentDescription = restaurant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            DishPlate(
                art = restaurant.art,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 40.dp, y = 60.dp)
                    .size(250.dp)
                    .rotate(-12f),
            )
        }
    }
}

@Composable
private fun RestaurantInfo(restaurant: Restaurant, mode: FulfilmentMode, onModeChange: (FulfilmentMode) -> Unit) {
    val pickup = mode == FulfilmentMode.Pickup
    Column(
        Modifier
            .offset(y = (-26).dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 20.dp, end = 20.dp, top = 20.dp),
    ) {
        Text(restaurant.name, style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = StarYellow, modifier = Modifier.size(15.dp))
            Text(" ${restaurant.rating}", fontWeight = FontWeight.Bold)
            Text(
                " (${restaurant.reviews}) · ${restaurant.cuisine}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${restaurant.area}, East London",
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (restaurant.tags.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                restaurant.tags.forEachIndexed { i, t -> TagPill(t, positive = i > 0) }
            }
        }
        ModeToggle(mode, onModeChange, Modifier.padding(top = 14.dp))
        Row(
            Modifier
                .padding(vertical = 14.dp)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
        ) {
            StatCell(
                value = if (pickup || restaurant.deliveryFee == 0) "Free" else money(restaurant.deliveryFee),
                label = if (pickup) "Pick-up" else "Delivery fee",
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(1.5.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            StatCell("${restaurant.etaMinutes} min", "Earliest arrival", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Ribbon(text: String) {
    Surface(
        modifier = Modifier.width(250.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun DishRow(dish: Dish, qtyInCart: Int, showDivider: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp)) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 14.dp)) {
                Text(dish.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    dish.description,
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(money(dish.price), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    dish.tag?.let { TagPill(it, positive = !it.contains("most")) }
                }
            }
            Box(Modifier.size(104.dp)) {
                DishImage(dish, Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).height(36.dp),
                    shape = CircleShape,
                    color = if (qtyInCart > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                    contentColor = if (qtyInCart > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                    shadowElevation = 3.dp,
                ) {
                    Box(Modifier.padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                        if (qtyInCart > 0) Text("$qtyInCart", style = MaterialTheme.typography.labelLarge)
                        else Icon(Icons.Filled.Add, contentDescription = "Add ${dish.name}", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
