package com.eats.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.data.Category
import com.eats.app.data.DishArt
import com.eats.app.data.Restaurant
import com.eats.app.data.SampleData
import com.eats.app.order.HomeFilter
import com.eats.app.order.OrderUiState
import com.eats.app.order.OrderViewModel
import com.eats.app.ui.components.AddressDialog
import com.eats.app.ui.components.DishPlate
import com.eats.app.ui.components.ModeToggle
import com.eats.app.ui.components.RestaurantCard
import com.eats.app.ui.components.SectionTitle

private val popularIds = setOf("kota", "smash", "braai", "sugar")

private fun OrderUiState.matches(r: Restaurant): Boolean {
    if (category != null && category !in r.categories) return false
    return when (filter) {
        HomeFilter.Offers -> r.freeDeliveryOver > 0
        HomeFilter.Fast -> r.etaMinutes <= 22
        HomeFilter.TopRated -> r.rating >= 4.6
        HomeFilter.LowFee -> r.deliveryFee <= 10
        null -> true
    }
}

/**
 * Home tab: delivery address, greeting, delivery/pick-up toggle, categories, filters
 * and the restaurant feed. Shown inside [MainScreen], which supplies the bottom bar.
 *
 * @param onMessage shows a short snackbar message.
 */
@Composable
fun HomeScreen(
    viewModel: OrderViewModel,
    onOpenRestaurant: (String) -> Unit,
    onSearch: () -> Unit,
    onHelp: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editAddress by rememberSaveable { mutableStateOf(false) }

    val restaurants = remember(state.category, state.filter) { SampleData.restaurants.filter { state.matches(it) } }
    val filtering = state.category != null || state.filter != null

    fun toggleFavourite(r: Restaurant) {
        val now = viewModel.toggleFavourite(r.id)
        onMessage(if (now) "${r.name} saved to favourites" else "${r.name} removed from favourites")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item {
            HomeHeader(
                address = state.address,
                onAddress = { editAddress = true },
                onHelp = onHelp,
                onBell = { onMessage("No new notifications") },
            )
        }
        item {
            Text(
                greeting(state.userName),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        item {
            ModeToggle(
                mode = state.mode,
                onChange = viewModel::setMode,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { HomeSearchField(onClick = onSearch) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(SampleData.categories, key = { it.id }) { c ->
                    CategoryPlate(c, selected = state.category == c.id, onClick = { viewModel.selectCategory(c.id) })
                }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(HomeFilter.entries.toList(), key = { it.name }) { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.toggleFilter(f) },
                        label = { Text(f.label, style = MaterialTheme.typography.labelLarge) },
                        shape = RoundedCornerShape(19.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                            selectedLabelColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                }
            }
        }

        if (!filtering) {
            item {
                val featured = SampleData.restaurants.first()
                FeatureCard(restaurant = featured, onClick = { onOpenRestaurant(featured.id) })
            }
            item { SectionTitle("Popular in your area", "Hand picked") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(SampleData.restaurants.filter { it.id in popularIds }, key = { it.id }) { r ->
                        RestaurantCard(
                            restaurant = r,
                            favourite = r.id in state.favourites,
                            onClick = { onOpenRestaurant(r.id) },
                            onFavourite = { toggleFavourite(r) },
                            modifier = Modifier.width(218.dp),
                        )
                    }
                }
            }
        }

        item {
            val title = SampleData.categories.firstOrNull { it.id == state.category }?.label ?: "All restaurants"
            SectionTitle(title, "${restaurants.size} open now")
        }
        if (restaurants.isEmpty()) {
            item { EmptyResults(onClear = viewModel::clearFilters) }
        } else {
            items(restaurants, key = { "all-${it.id}" }) { r ->
                RestaurantCard(
                    restaurant = r,
                    favourite = r.id in state.favourites,
                    onClick = { onOpenRestaurant(r.id) },
                    onFavourite = { toggleFavourite(r) },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
                )
            }
        }
    }

    if (editAddress) {
        AddressDialog(
            current = state.address,
            onSave = {
                viewModel.setAddress(it)
                editAddress = false
                onMessage("Delivery address updated")
            },
            onDismiss = { editAddress = false },
        )
    }
}

@Composable
private fun greeting(name: String) = buildAnnotatedString {
    append("Hungry")
    if (name.isNotBlank()) {
        append(", ")
        pushStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary))
        append(name)
        pop()
    }
    append("?")
}

@Composable
private fun HomeHeader(address: String, onAddress: () -> Unit, onHelp: () -> Unit, onBell: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onAddress),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    "DELIVER TO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        address.ifBlank { "Add your address" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Change address", modifier = Modifier.size(20.dp))
                }
            }
        }
        IconButton(onClick = onHelp) { Icon(Icons.Filled.Info, contentDescription = "Help and support") }
        IconButton(onClick = onBell) { Icon(Icons.Filled.Notifications, contentDescription = "Notifications") }
    }
}

/** Looks like a search field, but opens the Search tab when tapped. */
@Composable
private fun HomeSearchField(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(25.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Kota, sushi, koeksisters…",
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryPlate(category: Category, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DishPlate(
            art = category.art,
            modifier = Modifier
                .size(68.dp)
                .then(if (selected) Modifier.rotate(-6f) else Modifier),
        )
        Text(
            category.label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** A restaurant in the brand colour with a tilted plate. */
@Composable
private fun FeatureCard(restaurant: Restaurant, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(176.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 60.dp, y = 90.dp)
                    .size(240.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape),
            )
            DishPlate(
                art = restaurant.art,
                steam = false,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 26.dp, y = 30.dp)
                    .size(190.dp)
                    .rotate(14f),
            )
            Column(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "TONIGHT'S PICK",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        "${restaurant.name} is on a roll",
                        modifier = Modifier.padding(top = 6.dp).width(170.dp),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 28.sp),
                    )
                }
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White, contentColor = Color(0xFF111111)) {
                    Text(
                        "R0 delivery over R${restaurant.freeDeliveryOver}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyResults(onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DishPlate(DishArt.Drink, Modifier.size(96.dp))
        Text("Nothing matches yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Try a different filter.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClear) { Text("Clear filters") }
    }
}
