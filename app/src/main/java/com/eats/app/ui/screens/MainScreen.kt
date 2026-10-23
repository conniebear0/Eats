package com.eats.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.auth.AuthViewModel
import com.eats.app.order.OrderViewModel
import com.eats.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/** The tabs shown in the bottom bar. [Cart] opens its own screen instead of switching tab content. */
private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Search("Search", Icons.Filled.Search),
    Cart("Cart", Icons.Filled.ShoppingCart),
    Orders("Orders", Icons.AutoMirrored.Filled.List),
    Profile("Profile", Icons.Filled.Person),
}

/**
 * The signed-in home base: a bottom bar with Home, Search, Cart, Orders and Profile.
 * Home, Search, Orders and Profile switch in place (so Back from any of them returns to Home);
 * Cart and everything deeper are pushed on the navigation stack by the callbacks.
 */
@Composable
fun MainScreen(
    auth: AuthViewModel,
    orders: OrderViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onOpenRestaurant: (String) -> Unit,
    onOpenCart: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenFavourites: () -> Unit,
    onOpenChat: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenComplaints: () -> Unit = {},
    openComplaints: Int = 0,
) {
    val state by orders.state.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(Tab.Home.ordinal) }
    val tab = Tab.entries[tabIndex]

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { message ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    // Back from Search, Orders or Profile goes to Home first, then leaves the app.
    BackHandler(enabled = tab != Tab.Home) { tabIndex = Tab.Home.ordinal }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { if (item == Tab.Cart) onOpenCart() else tabIndex = item.ordinal },
                        icon = {
                            BadgedBox(
                                badge = {
                                    when {
                                        item == Tab.Cart && state.cartCount > 0 -> Badge { Text("${state.cartCount}") }
                                        item == Tab.Orders && state.activeOrder != null -> Badge()
                                    }
                                },
                            ) { Icon(item.icon, contentDescription = null) }
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.tertiary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                Tab.Home -> HomeScreen(
                    viewModel = orders,
                    onOpenRestaurant = onOpenRestaurant,
                    onSearch = { tabIndex = Tab.Search.ordinal },
                    onHelp = onOpenChat,
                    onMessage = say,
                )
                Tab.Search -> SearchScreen(orders, onOpenRestaurant, say)
                Tab.Orders -> OrdersScreen(
                    viewModel = orders,
                    onOpenTracking = onOpenTracking,
                    onOpenRestaurant = onOpenRestaurant,
                    onBrowse = { tabIndex = Tab.Home.ordinal },
                )
                Tab.Profile -> ProfileScreen(
                    auth = auth,
                    orders = orders,
                    themeMode = themeMode,
                    onThemeChange = onThemeChange,
                    onOpenFavourites = onOpenFavourites,
                    onHelp = onOpenChat,
                    onSignedOut = onSignedOut,
                    onMessage = say,
                    onComplaints = onOpenComplaints,
                    openComplaints = openComplaints,
                )
                Tab.Cart -> Unit // never selected: the Cart item opens its own screen
            }
        }
    }
}
