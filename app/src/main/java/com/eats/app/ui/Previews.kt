package com.eats.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.eats.app.auth.AuthViewModel
import com.eats.app.auth.FakeAuthRepository
import com.eats.app.auth.User
import com.eats.app.support.bot.Category
import com.eats.app.support.bot.ChatItem
import com.eats.app.support.bot.Complaint
import com.eats.app.support.bot.ComplaintEvent
import com.eats.app.support.bot.ComplaintStatus
import com.eats.app.support.bot.InfoCard
import com.eats.app.support.bot.Refund
import com.eats.app.support.bot.RefundMethod
import com.eats.app.support.bot.RefundStatus
import com.eats.app.support.bot.ReplySource
import com.eats.app.support.bot.Severity
import com.eats.app.data.SampleData
import com.eats.app.order.InMemoryUserDataStore
import com.eats.app.order.OrderViewModel
import com.eats.app.order.PastLine
import com.eats.app.order.PastOrder
import com.eats.app.order.UserData
import com.eats.app.ui.screens.CartScreen
import com.eats.app.ui.screens.CallAgentScreen
import com.eats.app.ui.screens.ChatScreen
import com.eats.app.ui.screens.ComplaintDetailScreen
import com.eats.app.ui.screens.ComplaintsScreen
import com.eats.app.ui.screens.CheckoutScreen
import com.eats.app.ui.screens.FavouritesScreen
import com.eats.app.ui.screens.ForgotPasswordScreen
import com.eats.app.ui.screens.HomeScreen
import com.eats.app.ui.screens.LoginScreen
import com.eats.app.ui.screens.OrderConfirmationScreen
import com.eats.app.ui.screens.OrdersScreen
import com.eats.app.ui.screens.ProfileScreen
import com.eats.app.ui.screens.RestaurantScreen
import com.eats.app.ui.screens.SearchScreen
import com.eats.app.ui.screens.SignUpScreen
import com.eats.app.ui.screens.SplashScreen
import com.eats.app.ui.screens.TrackingScreen
import com.eats.app.ui.theme.EatsTheme

// Open any of these in Android Studio's Split / Design view to see the screens without running the app.
// Each one is shown in light and dark.

private const val W = 360
private const val H = 780

@Preview(name = "Light", widthDp = W, heightDp = H, showBackground = true)
@Preview(name = "Dark", widthDp = W, heightDp = H, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private annotation class ScreenPreview

// ---------- Sample state ----------

private val demoUser = User("demo", "Thandi", "Nkosi", "821234567", "thandi@example.com")

private val pastOrder = PastOrder(
    id = "E-4802",
    restaurantId = "kota",
    restaurantName = "Kota King",
    placedAt = 1_760_000_000_000L,
    total = 98,
    pickup = false,
    lines = listOf(PastLine("Classic Kota", 1, 45), PastLine("Slap Chips", 2, 50)),
    rating = 5,
)

private fun auth() = AuthViewModel(FakeAuthRepository())

/** A signed-in OrderViewModel. Optionally with a cart, or with an order already placed. */
private fun orders(cart: Boolean = false, placed: Boolean = false, onTheWay: Boolean = false): OrderViewModel {
    val store = InMemoryUserDataStore()
    store.save(demoUser.id, UserData("60A Belgravia Crescent, Vincent", setOf("kota", "sakura"), listOf(pastOrder)))
    val vm = OrderViewModel(store)
    vm.bindUser(demoUser)
    if (cart || placed) {
        val restaurant = checkNotNull(SampleData.restaurant("smash"))
        val burger = restaurant.dishes.first()
        vm.addToCart(restaurant.id, burger, 2, mapOf("meal" to setOf(1)), "No pickles", burger.price + 32)
        vm.addToCart(restaurant.id, restaurant.dishes.first { it.id == "s8" }, 1, emptyMap(), "", 38)
    }
    if (placed) {
        vm.placeOrder("60A Belgravia Crescent, Vincent", autoAdvance = false)
        if (onTheWay) repeat(2) { vm.skipAhead() }
    }
    return vm
}

private val sampleChat = listOf(
    ChatItem(0, "Hi Thandi, I'm the eats support assistant. Sipho M. is on the way with your order, about 9 min out. How can I help?", false, 0),
    ChatItem(1, "my order never arrived", true, 0),
    ChatItem(2, "It hasn't been delivered yet. It's still on its way:", false, 0),
    ChatItem(3, "", false, 0, card = InfoCard("Smash and Co · E-4821", listOf("Status" to "On the way", "Arriving in" to "about 9 min", "Total" to "R214 (cash)"))),
    ChatItem(4, "Learned: \"no food yet\" means where's my order?", false, 0, notice = true),
    ChatItem(5, "Drivers follow our hygiene rules, but I can't confirm what each one wears.", false, 0, source = ReplySource.Ai),
)

private val sampleComplaint = Complaint(
    id = "8ATMMY", orderId = "E-4821", restaurantName = "Smash and Co", category = Category.MissingItems,
    subcategory = "Food Missing", severity = Severity.Medium, status = ComplaintStatus.Pending,
    createdAt = 1_760_000_000_000L, dueAt = 1_760_172_800_000L,
    description = "1 × Smash Burger missing from order E-4821 (R89). The customer asks for a refund.",
    refund = Refund(89, RefundMethod.Card, RefundStatus.Approved, "Missing: 1 × Smash Burger"),
    events = listOf(
        ComplaintEvent(1_760_000_000_000L, "Logged by the chatbot. Sent to Restaurant Operations.", "bot", ComplaintStatus.Open),
        ComplaintEvent(1_760_003_600_000L, "An agent is working on it. Refund of R89 approved.", "agent", ComplaintStatus.Pending),
    ),
    transcript = emptyList(),
)

// ---------- Signed out ----------

@ScreenPreview
@Composable
private fun SplashPreview() = EatsTheme { SplashScreen(onFinished = {}) }

@ScreenPreview
@Composable
private fun LoginPreview() = EatsTheme {
    LoginScreen(viewModel = auth(), onSignUpClick = {}, onForgotPasswordClick = {}, onSignedIn = {})
}

@ScreenPreview
@Composable
private fun SignUpPreview() = EatsTheme {
    SignUpScreen(viewModel = auth(), onBack = {}, onSignInClick = {}, onSignedUp = {})
}

@ScreenPreview
@Composable
private fun ForgotPasswordPreview() = EatsTheme {
    ForgotPasswordScreen(viewModel = auth(), onBack = {}, onDone = {})
}

// ---------- Tabs ----------

@ScreenPreview
@Composable
private fun HomePreview() = EatsTheme {
    HomeScreen(viewModel = orders(), onOpenRestaurant = {}, onSearch = {}, onHelp = {}, onMessage = {})
}

@ScreenPreview
@Composable
private fun SearchPreview() = EatsTheme {
    val vm = orders()
    vm.setQuery("pizza")
    SearchScreen(viewModel = vm, onOpenRestaurant = {}, onMessage = {})
}

@ScreenPreview
@Composable
private fun OrdersPreview() = EatsTheme {
    OrdersScreen(viewModel = orders(placed = true), onOpenTracking = {}, onOpenRestaurant = {}, onBrowse = {})
}

@ScreenPreview
@Composable
private fun ProfilePreview() = EatsTheme {
    ProfileScreen(
        auth = auth(), orders = orders(),
        onOpenFavourites = {}, onHelp = {}, onSignedOut = {}, onMessage = {},
    )
}

// ---------- Ordering ----------

@ScreenPreview
@Composable
private fun RestaurantPreview() = EatsTheme {
    RestaurantScreen(restaurantId = "smash", viewModel = orders(cart = true), onBack = {}, onOpenCart = {})
}

@ScreenPreview
@Composable
private fun CartPreview() = EatsTheme {
    CartScreen(viewModel = orders(cart = true), onBack = {}, onAddMore = {}, onFindFood = {}, onCheckout = {})
}

@ScreenPreview
@Composable
private fun CheckoutPreview() = EatsTheme {
    CheckoutScreen(viewModel = orders(cart = true), onBack = {}, onFindFood = {}, onOrderPlaced = {})
}

@ScreenPreview
@Composable
private fun ConfirmationPreview() = EatsTheme {
    OrderConfirmationScreen(viewModel = orders(placed = true), onTrack = {}, onDone = {})
}

@ScreenPreview
@Composable
private fun TrackingPreview() = EatsTheme {
    TrackingScreen(viewModel = orders(placed = true, onTheWay = true), onBack = {}, onDone = {}, onHelp = {})
}

@ScreenPreview
@Composable
private fun FavouritesPreview() = EatsTheme {
    FavouritesScreen(viewModel = orders(), onBack = {}, onOpenRestaurant = {})
}

@ScreenPreview
@Composable
private fun ChatPreview() = EatsTheme {
    ChatScreen(
        messages = sampleChat,
        quickReplies = listOf("Where's my order?", "Contact my driver", "Talk to a person"),
        typing = false,
        onSend = {},
        onBack = {},
    )
}

@ScreenPreview
@Composable
private fun ComplaintsPreview() = EatsTheme {
    ComplaintsScreen(complaints = listOf(sampleComplaint), onBack = {}, onOpen = {}, onOpenChat = {}, nowMillis = 1_760_010_000_000L)
}

@ScreenPreview
@Composable
private fun ComplaintDetailPreview() = EatsTheme {
    ComplaintDetailScreen(
        complaint = sampleComplaint,
        onBack = {}, onAddNote = {}, onClose = {}, onReopen = {}, onWithdraw = {}, onChase = {}, onCall = {},
        onDemoAdvance = {}, onDemoDecline = {}, nowMillis = 1_760_010_000_000L,
    )
}

@ScreenPreview
@Composable
private fun CallPreview() = EatsTheme { CallAgentScreen(onEnd = { _, _ -> }) }
