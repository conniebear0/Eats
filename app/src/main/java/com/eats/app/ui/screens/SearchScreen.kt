package com.eats.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.data.DishArt
import com.eats.app.data.Restaurant
import com.eats.app.data.SampleData
import com.eats.app.order.OrderViewModel
import com.eats.app.ui.components.EmptyState
import com.eats.app.ui.components.RestaurantCard
import com.eats.app.ui.components.ScreenHeader

private val suggestions = listOf("Kota", "Burger", "Pizza", "Sushi", "Chicken", "Curry", "Koeksisters")

/**
 * Search tab. Finds restaurants by name, cuisine, area or any dish on their menu,
 * and shows which dishes matched so the result is easy to trust.
 */
@Composable
fun SearchScreen(
    viewModel: OrderViewModel,
    onOpenRestaurant: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query = state.query
    val results = remember(query) { SampleData.search(query) }
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Search", onBack = null)
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text("Restaurants, dishes, cuisines") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        )

        when {
            query.isBlank() -> {
                Text(
                    "Popular searches",
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(suggestions) { word ->
                        AssistChip(onClick = { viewModel.setQuery(word) }, label = { Text(word) })
                    }
                }
            }
            results.isEmpty() -> EmptyState(
                art = DishArt.Drink,
                title = "No results for \"${query.trim()}\"",
                message = "Check the spelling or try a cuisine like pizza or sushi.",
                actionLabel = "Clear search",
                onAction = { viewModel.setQuery("") },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    Text(
                        "${results.size} ${if (results.size == 1) "restaurant" else "restaurants"} found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(results, key = { it.id }) { r ->
                    Column {
                        RestaurantCard(
                            restaurant = r,
                            favourite = r.id in state.favourites,
                            onClick = { onOpenRestaurant(r.id) },
                            onFavourite = {
                                val now = viewModel.toggleFavourite(r.id)
                                onMessage(if (now) "${r.name} saved to favourites" else "${r.name} removed from favourites")
                            },
                        )
                        matchedDishes(r, query).takeIf { it.isNotEmpty() }?.let { names ->
                            Text(
                                "Has: ${names.joinToString(", ")}",
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Up to three dish names on [restaurant]'s menu that contain a word of [query]. */
private fun matchedDishes(restaurant: Restaurant, query: String): List<String> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    return restaurant.dishes
        .filter { dish -> words.any { it in dish.name.lowercase() } }
        .map { it.name }
        .take(3)
}
