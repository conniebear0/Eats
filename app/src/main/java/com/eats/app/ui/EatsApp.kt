package com.eats.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eats.app.auth.AuthViewModel
import com.eats.app.auth.LocalAuthRepository
import com.eats.app.order.LocalUserDataStore
import com.eats.app.order.OrderViewModel
import com.eats.app.support.SupportViewModel
import com.eats.app.support.bot.ChatEvent
import com.eats.app.ui.screens.AiSettingsDialog
import com.eats.app.ui.screens.CallAgentScreen
import com.eats.app.ui.screens.ComplaintDetailScreen
import com.eats.app.ui.screens.ComplaintsScreen
import com.eats.app.ui.screens.LearnedDialog
import com.eats.app.ui.screens.CartScreen
import com.eats.app.ui.screens.ChatScreen
import com.eats.app.ui.screens.CheckoutScreen
import com.eats.app.ui.screens.FavouritesScreen
import com.eats.app.ui.screens.ForgotPasswordScreen
import com.eats.app.ui.screens.LoginScreen
import com.eats.app.ui.screens.MainScreen
import com.eats.app.ui.screens.OrderConfirmationScreen
import com.eats.app.ui.screens.RestaurantScreen
import com.eats.app.ui.screens.SignUpScreen
import com.eats.app.ui.screens.SplashScreen
import com.eats.app.ui.screens.TrackingScreen
import com.eats.app.ui.theme.ThemeMode

/**
 * Every screen in the app.
 *
 * Signed out:  SPLASH -> LOGIN <-> SIGN_UP, LOGIN -> FORGOT_PASSWORD
 * Signed in:   HOME (tabs: Home, Search, Orders, Profile) -> RESTAURANT -> CART -> CHECKOUT
 *              -> CONFIRMATION -> TRACK. FAVOURITES and CHAT open from Home or Profile.
 */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGN_UP = "signup"
    const val FORGOT_PASSWORD = "forgot"
    const val HOME = "home"
    const val RESTAURANT = "restaurant/{id}"
    const val CART = "cart"
    const val CHECKOUT = "checkout"
    const val CONFIRMATION = "confirmation"
    const val TRACK = "track"
    const val FAVOURITES = "favourites"
    const val CHAT = "chat"
    const val CALL = "call"
    const val COMPLAINTS = "complaints"
    const val COMPLAINT = "complaint/{id}"

    fun restaurant(id: String) = "restaurant/$id"
    fun complaint(id: String) = "complaint/$id"

    /** Screens that need a signed-in user. If the session ends on one of these, the app returns to Login. */
    val signedInOnly = setOf(HOME, RESTAURANT, CART, CHECKOUT, CONFIRMATION, TRACK, FAVOURITES, CHAT, CALL, COMPLAINTS, COMPLAINT)
}

/** Clears the whole back stack and shows Login, so Back cannot return to a signed-in screen. */
private fun NavController.goToLogin() {
    navigate(Routes.LOGIN) { popUpTo(graph.id) { inclusive = true } }
}

@Composable
fun EatsApp(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    val nav = rememberNavController()
    val appContext = LocalContext.current.applicationContext

    // One instance of each for the whole app, so the cart and session survive moving between screens.
    val auth: AuthViewModel = viewModel(
        factory = viewModelFactory { initializer { AuthViewModel(LocalAuthRepository(appContext)) } },
    )
    val orders: OrderViewModel = viewModel(
        factory = viewModelFactory { initializer { OrderViewModel(LocalUserDataStore(appContext)) } },
    )
    val support: SupportViewModel = viewModel(
        factory = viewModelFactory { initializer { SupportViewModel(appContext, orders) } },
    )
    val complaints by support.complaints.collectAsStateWithLifecycle()

    // Load (or clear) the per-user data whenever the signed-in user changes, including session restore.
    val user by auth.user.collectAsStateWithLifecycle()
    LaunchedEffect(user?.id) {
        orders.bindUser(user)
        support.bindUser(user)
    }
    // The chat asks for screens: a complaint it just logged, or the call screen.
    LaunchedEffect(Unit) {
        support.events.collect { event ->
            when (event) {
                is ChatEvent.OpenComplaint -> nav.navigate(Routes.complaint(event.id)) { launchSingleTop = true }
                ChatEvent.StartCall -> nav.navigate(Routes.CALL) { launchSingleTop = true }
            }
        }
    }
    // Access control: if the session ends while on a protected screen, go back to Login.
    LaunchedEffect(user) {
        val route = nav.currentBackStackEntry?.destination?.route
        if (user == null && route != null && route in Routes.signedInOnly) nav.goToLogin()
    }

    NavHost(
        navController = nav,
        startDestination = Routes.SPLASH,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    val next = if (auth.signedIn) Routes.HOME else Routes.LOGIN
                    nav.navigate(next) { popUpTo(Routes.SPLASH) { inclusive = true } }
                },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = auth,
                onSignUpClick = { nav.navigate(Routes.SIGN_UP) },
                onForgotPasswordClick = { nav.navigate(Routes.FORGOT_PASSWORD) },
                onSignedIn = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(
                viewModel = auth,
                onBack = { nav.popBackStack() },
                onSignInClick = { nav.popBackStack() },
                onSignedUp = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                viewModel = auth,
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack() },
            )
        }
        composable(Routes.HOME) {
            MainScreen(
                auth = auth,
                orders = orders,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                onOpenRestaurant = { id -> nav.navigate(Routes.restaurant(id)) { launchSingleTop = true } },
                onOpenCart = { nav.navigate(Routes.CART) { launchSingleTop = true } },
                onOpenTracking = { nav.navigate(Routes.TRACK) { launchSingleTop = true } },
                onOpenFavourites = { nav.navigate(Routes.FAVOURITES) { launchSingleTop = true } },
                onOpenChat = { nav.navigate(Routes.CHAT) { launchSingleTop = true } },
                onSignedOut = { nav.goToLogin() },
                onOpenComplaints = { nav.navigate(Routes.COMPLAINTS) { launchSingleTop = true } },
                openComplaints = complaints.count { it.status.isOpen },
            )
        }
        composable(
            route = Routes.RESTAURANT,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            RestaurantScreen(
                restaurantId = entry.arguments?.getString("id").orEmpty(),
                viewModel = orders,
                onBack = { nav.popBackStack() },
                onOpenCart = { nav.navigate(Routes.CART) { launchSingleTop = true } },
            )
        }
        composable(Routes.FAVOURITES) {
            FavouritesScreen(
                viewModel = orders,
                onBack = { nav.popBackStack() },
                onOpenRestaurant = { id -> nav.navigate(Routes.restaurant(id)) { launchSingleTop = true } },
            )
        }
        composable(Routes.CART) {
            CartScreen(
                viewModel = orders,
                onBack = { nav.popBackStack() },
                onAddMore = { id ->
                    // Go back to the restaurant if that is where the user came from, otherwise open it.
                    val previous = nav.previousBackStackEntry
                    val cameFromIt = previous?.destination?.route == Routes.RESTAURANT &&
                        previous.arguments?.getString("id") == id
                    if (cameFromIt) nav.popBackStack() else nav.navigate(Routes.restaurant(id))
                },
                onFindFood = { nav.popBackStack(Routes.HOME, inclusive = false) },
                onCheckout = { nav.navigate(Routes.CHECKOUT) { launchSingleTop = true } },
            )
        }
        composable(Routes.CHECKOUT) {
            CheckoutScreen(
                viewModel = orders,
                onBack = { nav.popBackStack() },
                onFindFood = { nav.popBackStack(Routes.HOME, inclusive = false) },
                onOrderPlaced = {
                    // Drop restaurant, cart and checkout so Back from the confirmation lands on Home.
                    nav.navigate(Routes.CONFIRMATION) { popUpTo(Routes.HOME) }
                },
            )
        }
        composable(Routes.CONFIRMATION) {
            OrderConfirmationScreen(
                viewModel = orders,
                onTrack = { nav.navigate(Routes.TRACK) { popUpTo(Routes.HOME) } },
                onDone = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.TRACK) {
            TrackingScreen(
                viewModel = orders,
                onBack = { nav.popBackStack() },
                onDone = { nav.popBackStack(Routes.HOME, inclusive = false) },
                onHelp = { nav.navigate(Routes.CHAT) { launchSingleTop = true } },
            )
        }
        composable(Routes.CHAT) {
            val items by support.items.collectAsStateWithLifecycle()
            val chips by support.chips.collectAsStateWithLifecycle()
            val typing by support.typing.collectAsStateWithLifecycle()
            val ai by support.aiSettings.collectAsStateWithLifecycle()
            val learned by support.learned.collectAsStateWithLifecycle()
            val mistakes by support.mistakes.collectAsStateWithLifecycle()
            var showLearned by rememberSaveable { mutableStateOf(false) }
            var showAi by rememberSaveable { mutableStateOf(false) }
            // The bot greets the customer (and looks up their order) the first time Support opens.
            LaunchedEffect(Unit) { support.open() }
            ChatScreen(
                messages = items,
                quickReplies = chips,
                typing = typing,
                onSend = support::send,
                onBack = { nav.popBackStack() },
                onCall = { nav.navigate(Routes.CALL) { launchSingleTop = true } },
                onComplaints = { nav.navigate(Routes.COMPLAINTS) { launchSingleTop = true } },
                onLearned = { showLearned = true },
                onAiSettings = { showAi = true },
            )
            if (showLearned) {
                LearnedDialog(
                    learned = learned,
                    mistakes = mistakes,
                    onForget = support::forget,
                    onForgetAll = support::forgetAll,
                    onDismiss = { showLearned = false },
                )
            }
            if (showAi) {
                AiSettingsDialog(
                    current = ai,
                    onSave = support::saveAiSettings,
                    onTest = { support.testAi(it) },
                    onDismiss = { showAi = false },
                )
            }
        }
        composable(Routes.CALL) {
            CallAgentScreen(onEnd = { agent, seconds ->
                support.callEnded(agent, seconds)
                nav.popBackStack()
            })
        }
        composable(Routes.COMPLAINTS) {
            ComplaintsScreen(
                complaints = complaints,
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.complaint(id)) },
                onOpenChat = { nav.navigate(Routes.CHAT) { launchSingleTop = true } },
            )
        }
        composable(
            route = Routes.COMPLAINT,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ComplaintDetailScreen(
                complaint = complaints.firstOrNull { it.id == id },
                onBack = { nav.popBackStack() },
                onAddNote = { support.addNote(id, it) },
                onClose = { support.closeComplaint(id) },
                onReopen = { support.reopenComplaint(id) },
                onWithdraw = { support.withdrawComplaint(id) },
                onChase = { support.chaseComplaint(id) },
                onCall = { nav.navigate(Routes.CALL) { launchSingleTop = true } },
                onDemoAdvance = { support.advanceComplaint(id) },
                onDemoDecline = { support.declineRefund(id) },
            )
        }
    }
}
