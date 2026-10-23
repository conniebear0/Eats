package com.eats.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.data.DishArt
import com.eats.app.data.SampleData
import com.eats.app.order.OrderViewModel
import com.eats.app.ui.components.EmptyState
import com.eats.app.ui.components.RestaurantCard
import com.eats.app.ui.components.ScreenHeader

/** Restaurants the user has hearted. Un-hearting removes them from the list straight away. */
@Composable
fun FavouritesScreen(
    viewModel: OrderViewModel,
    onBack: () -> Unit,
    onOpenRestaurant: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favourites = SampleData.restaurants.filter { it.id in state.favourites }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = "Favourites", onBack = onBack)
        if (favourites.isEmpty()) {
            EmptyState(
                art = DishArt.Koek,
                title = "No favourites yet",
                message = "Tap the heart on a restaurant to keep it here for next time.",
                actionLabel = "Find food",
                onAction = onBack,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(favourites, key = { it.id }) { r ->
                    RestaurantCard(
                        restaurant = r,
                        favourite = true,
                        onClick = { onOpenRestaurant(r.id) },
                        onFavourite = { viewModel.toggleFavourite(r.id) },
                    )
                }
            }
        }
    }
}
