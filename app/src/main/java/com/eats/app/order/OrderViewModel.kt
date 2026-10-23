package com.eats.app.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eats.app.auth.User
import com.eats.app.data.CartLine
import com.eats.app.data.Dish
import com.eats.app.data.Restaurant
import com.eats.app.data.SampleData
import com.eats.app.support.bot.BotLine
import com.eats.app.support.bot.BotOrder
import com.eats.app.support.bot.OrderState
import com.eats.app.support.bot.PayKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FulfilmentMode { Delivery, Pickup }

enum class PayMethod(val badge: String) {
    Cash("CASH"),
    Card("CARD");

    fun label(pickup: Boolean): String = when (this) {
        Cash -> if (pickup) "Cash at pick-up" else "Cash on delivery"
        Card -> if (pickup) "Card at pick-up" else "Card on delivery"
    }
}

enum class HomeFilter(val label: String) {
    Offers("Offers"),
    Fast("Under 30 min"),
    TopRated("Top rated"),
    LowFee("Low delivery fee"),
}

/** Tracking steps. In this prototype an order moves through them in real time, over the restaurant's delivery estimate. */
enum class OrderStep(val title: String, val detail: String) {
    Received("Order received", "The kitchen has your order."),
    Preparing("Being prepared", "Your plate is on the pass."),
    OnTheWay("On the way", "Your driver is heading to you."),
    Delivered("Delivered", "Enjoy your meal."),
}

/** The order being tracked right now. [address] and [pay] are snapshots taken at checkout. */
data class ActiveOrder(
    val id: String,
    val restaurantId: String,
    val lines: List<CartLine>,
    val total: Int,
    val pickup: Boolean,
    val etaMinutes: Int,
    val address: String,
    val instructions: String,
    val pay: PayMethod,
    val placedAt: Long,
    val seconds: Int = 0,
    val step: OrderStep = OrderStep.Received,
    val rating: Int = 0,
    /** Cancelled by the customer (through the support chat) or from the demo panel. */
    val cancelled: Boolean = false,
    /** Demo panel: how many minutes past the promised time the order is running. */
    val lateMinutes: Int = 0,
    val deliveredAt: Long? = null,
    /** False once the demo panel has taken over, so the live countdown stops moving it. */
    val autoAdvance: Boolean = true,
) {
    val delivered: Boolean get() = step == OrderStep.Delivered && !cancelled

    /** Delivered or cancelled: nothing more will happen to it. */
    val finished: Boolean get() = delivered || cancelled

    /**
     * The whole simulation runs in real time over the restaurant's estimate (about 15 to 30 minutes), so
     * the countdown matches what the chatbot says and there's time to test it. Skip ahead and the demo
     * panel jump straight to a step.
     */
    val totalSeconds: Int get() = max(1, etaMinutes) * 60

    /** When step [i] starts, in seconds after the order was placed. */
    fun stepAt(i: Int): Int = (STEP_SHARE[i] * totalSeconds).roundToInt()

    val minutesLeft: Int
        get() = max(1, ceil((totalSeconds - min(seconds, totalSeconds)) / 60.0).toInt()) + lateMinutes

    /** 0..1 along the route. The rider only moves while the order is on the way. */
    val routeProgress: Float
        get() = when (step) {
            OrderStep.Received, OrderStep.Preparing -> 0f
            OrderStep.OnTheWay -> min(1f, (seconds - stepAt(2)) / (totalSeconds - stepAt(2)).toFloat())
            OrderStep.Delivered -> 1f
        }

    companion object {
        /** Share of the estimate at which each step starts: received, preparing (20%), on the way (50%), delivered. */
        val STEP_SHARE = doubleArrayOf(0.0, 0.2, 0.5, 1.0)
        const val DRIVER_NAME = "Sipho M."
        const val DRIVER_VEHICLE = "Scooter, EC 482 GP"
    }
}

data class Totals(
    val subtotal: Int,
    val delivery: Int,
    val service: Int,
    val tip: Int,
    val discount: Int,
    val freeDelivery: Boolean,
) {
    val total: Int get() = max(0, subtotal + delivery + service + tip - discount)
}

data class OrderUiState(
    val userId: String? = null,
    val userName: String = "",
    val address: String = "",
    val mode: FulfilmentMode = FulfilmentMode.Delivery,
    val query: String = "",
    val category: String? = null,
    val filter: HomeFilter? = null,
    val favourites: Set<String> = emptySet(),
    val cartRestaurantId: String? = null,
    val lines: List<CartLine> = emptyList(),
    val promoApplied: Boolean = false,
    val tip: Int = DEFAULT_TIP,
    val pay: PayMethod = PayMethod.Cash,
    val activeOrder: ActiveOrder? = null,
    val history: List<PastOrder> = emptyList(),
) {
    val cartCount: Int get() = lines.sumOf { it.qty }
    val cartRestaurant: Restaurant? get() = cartRestaurantId?.let(SampleData::restaurant)

    fun totals(): Totals {
        val restaurant = cartRestaurant
        val subtotal = lines.sumOf { it.total }
        val pickup = mode == FulfilmentMode.Pickup
        val free = restaurant != null && restaurant.freeDeliveryOver > 0 && subtotal >= restaurant.freeDeliveryOver
        val delivery = if (pickup || restaurant == null || free) 0 else restaurant.deliveryFee
        return Totals(
            subtotal = subtotal,
            delivery = delivery,
            service = if (pickup || lines.isEmpty()) 0 else SERVICE_FEE,
            tip = if (pickup || lines.isEmpty()) 0 else tip,
            discount = if (promoApplied) min(PROMO_OFF, subtotal) else 0,
            freeDelivery = free,
        )
    }

    companion object {
        const val SERVICE_FEE = 6
        const val DEFAULT_TIP = 10
        const val PROMO_CODE = "EATSFIRST"
        const val PROMO_OFF = 30
    }
}

/**
 * Holds the cart, the Home filters and search, the order being tracked, and the signed-in user's
 * saved address, favourites and order history (through [store]).
 * One instance is shared by every screen after login. Call [bindUser] when the user changes.
 *
 * Order progress is simulated here. Swap the bodies of [placeOrder] and the ticking loop for real API calls.
 */
class OrderViewModel(private val store: UserDataStore) : ViewModel() {
    private val _state = MutableStateFlow(OrderUiState())
    val state: StateFlow<OrderUiState> = _state.asStateFlow()

    private var tickJob: Job? = null

    // --- User ---

    /** Loads the saved data of [user], or clears everything when [user] is null (signed out). */
    fun bindUser(user: User?) {
        if (user?.id == _state.value.userId) return
        tickJob?.cancel()
        _state.value = if (user == null) {
            OrderUiState()
        } else {
            val saved = store.load(user.id)
            OrderUiState(
                userId = user.id,
                userName = user.firstName,
                address = saved.address,
                favourites = saved.favourites,
                history = saved.history,
            )
        }
    }

    fun setAddress(address: String) {
        _state.update { it.copy(address = address.trim()) }
        persist()
    }

    private fun persist() {
        val s = _state.value
        val id = s.userId ?: return
        store.save(id, UserData(s.address, s.favourites, s.history))
    }

    // --- Home and search ---
    fun setMode(mode: FulfilmentMode) = _state.update { it.copy(mode = mode) }

    fun selectCategory(id: String) = _state.update { it.copy(category = if (it.category == id) null else id) }

    fun toggleFilter(filter: HomeFilter) = _state.update { it.copy(filter = if (it.filter == filter) null else filter) }

    fun clearFilters() = _state.update { it.copy(category = null, filter = null) }

    fun setQuery(query: String) = _state.update { it.copy(query = query.take(40)) }

    /** Flips the favourite flag and returns true when the restaurant is now a favourite. */
    fun toggleFavourite(restaurantId: String): Boolean {
        _state.update {
            it.copy(favourites = if (restaurantId in it.favourites) it.favourites - restaurantId else it.favourites + restaurantId)
        }
        persist()
        return restaurantId in _state.value.favourites
    }

    // --- Cart ---
    /** True when adding from [restaurantId] would replace what is already in the cart. */
    fun conflictsWithCart(restaurantId: String): Boolean {
        val s = _state.value
        return s.cartRestaurantId != null && s.cartRestaurantId != restaurantId && s.lines.isNotEmpty()
    }

    fun clearCart() = _state.update { it.copy(cartRestaurantId = null, lines = emptyList(), promoApplied = false) }

    fun addToCart(restaurantId: String, dish: Dish, qty: Int, selections: Map<String, Set<Int>>, note: String, unit: Int) {
        _state.update { s ->
            val base = if (s.cartRestaurantId == restaurantId) s.lines else emptyList()
            val existing = base.indexOfFirst { it.dish.id == dish.id && it.selections == selections && it.note == note }
            val lines = if (existing >= 0) {
                base.mapIndexed { i, l -> if (i == existing) l.copy(qty = l.qty + qty) else l }
            } else {
                base + CartLine(dish, qty, selections, note, unit)
            }
            s.copy(
                cartRestaurantId = restaurantId,
                lines = lines,
                promoApplied = if (s.cartRestaurantId == restaurantId) s.promoApplied else false,
            )
        }
    }

    /** Changes a cart line's quantity. A quantity of 0 removes the line. */
    fun changeQty(index: Int, delta: Int) = _state.update { s ->
        val lines = s.lines.mapIndexedNotNull { i, l ->
            if (i != index) l else (l.qty + delta).takeIf { it > 0 }?.let { l.copy(qty = it) }
        }
        if (lines.isEmpty()) s.copy(lines = emptyList(), cartRestaurantId = null, promoApplied = false) else s.copy(lines = lines)
    }

    /** Returns true when the code is valid. */
    fun applyPromo(code: String): Boolean {
        val ok = code.trim().equals(OrderUiState.PROMO_CODE, ignoreCase = true)
        if (ok) _state.update { it.copy(promoApplied = true) }
        return ok
    }

    fun removePromo() = _state.update { it.copy(promoApplied = false) }

    fun setTip(amount: Int) = _state.update { it.copy(tip = amount) }

    fun setPay(method: PayMethod) = _state.update { it.copy(pay = method) }

    // --- Order and tracking ---
    /**
     * Turns the cart into the active order. A delivery [address] is also saved to the profile.
     * [autoAdvance] = false keeps the order on step 1, which is what the previews want.
     */
    fun placeOrder(address: String, instructions: String = "", autoAdvance: Boolean = true) {
        val s = _state.value
        val restaurant = s.cartRestaurant ?: return
        if (s.lines.isEmpty()) return
        val pickup = s.mode == FulfilmentMode.Pickup
        val order = ActiveOrder(
            id = "E-${(1000..9999).random()}",
            restaurantId = restaurant.id,
            lines = s.lines,
            total = s.totals().total,
            pickup = pickup,
            etaMinutes = restaurant.etaMinutes,
            address = if (pickup) "" else address.trim(),
            instructions = instructions.trim(),
            pay = s.pay,
            placedAt = System.currentTimeMillis(),
        )
        _state.update {
            it.copy(
                activeOrder = order,
                address = if (pickup || address.isBlank()) it.address else address.trim(),
                cartRestaurantId = null,
                lines = emptyList(),
                promoApplied = false,
            )
        }
        persist()
        if (autoAdvance) startTicking()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val order = _state.value.activeOrder ?: break
                if (order.finished || !order.autoAdvance) break
                setSeconds(order.seconds + 1)
            }
        }
    }

    private fun stepFor(order: ActiveOrder, seconds: Int): OrderStep {
        val index = ActiveOrder.STEP_SHARE.indices.last { seconds >= order.stepAt(it) }
        return OrderStep.entries[index]
    }

    /** Moves the order forward. The moment it is delivered it is also added to the saved history. */
    private fun setSeconds(seconds: Int) {
        val s = _state.value
        val order = s.activeOrder ?: return
        val step = stepFor(order, seconds)
        val updated = order.copy(
            seconds = seconds,
            step = step,
            deliveredAt = if (step == OrderStep.Delivered) order.deliveredAt ?: System.currentTimeMillis() else null,
        )
        val justDelivered = updated.delivered && s.history.none { it.id == updated.id }
        _state.value = s.copy(
            activeOrder = updated,
            history = if (justDelivered) listOf(updated.toPast()) + s.history else s.history,
        )
        if (justDelivered) persist()
    }

    private fun ActiveOrder.toPast() = PastOrder(
        id = id,
        restaurantId = restaurantId,
        restaurantName = SampleData.restaurant(restaurantId)?.name.orEmpty(),
        placedAt = placedAt,
        total = total,
        pickup = pickup,
        lines = lines.map { PastLine(it.dish.name, it.qty, it.total) },
        rating = rating,
        deliveredAt = deliveredAt ?: System.currentTimeMillis(),
        pay = pay.name,
        address = address,
    )

    /** Demo button: jump to the next step. */
    fun skipAhead() {
        val o = _state.value.activeOrder ?: return
        if (o.finished) return
        setSeconds(o.stepAt(o.step.ordinal + 1))
    }

    fun rateOrder(stars: Int) {
        _state.update { s ->
            val id = s.activeOrder?.id
            s.copy(
                activeOrder = s.activeOrder?.copy(rating = stars),
                history = s.history.map { if (it.id == id) it.copy(rating = stars) else it },
            )
        }
        persist()
    }

    // --- Changes made by the support chat ---

    /** The chatbot cancels an order the kitchen hasn't started (or one that is running late). */
    fun cancelActive() {
        tickJob?.cancel()
        _state.update { s -> s.copy(activeOrder = s.activeOrder?.copy(cancelled = true, autoAdvance = false)) }
    }

    /** The chatbot updates the address before the driver sets off. */
    fun changeActiveAddress(address: String) {
        _state.update { s -> s.copy(activeOrder = s.activeOrder?.copy(address = address.trim())) }
    }

    // --- Demo panel on the tracking screen (stands in for the restaurant and driver) ---

    fun demoSetStep(step: OrderStep) {
        tickJob?.cancel()
        _state.update { s -> s.copy(activeOrder = s.activeOrder?.copy(cancelled = false, autoAdvance = false)) }
        setSeconds(_state.value.activeOrder?.stepAt(step.ordinal) ?: return)
    }

    fun demoCancel() = cancelActive()

    fun demoSetLate(minutes: Int) {
        _state.update { s -> s.copy(activeOrder = s.activeOrder?.copy(lateMinutes = minutes)) }
    }

    fun demoResume() {
        _state.update { s -> s.copy(activeOrder = s.activeOrder?.copy(autoAdvance = true, cancelled = false)) }
        startTicking()
    }

    /**
     * What the support chatbot knows about this customer's orders: the tracked order first, then past
     * orders (newest first).
     */
    fun botOrders(): List<BotOrder> {
        val s = _state.value
        val active = s.activeOrder
        return listOfNotNull(active?.toBot()) + s.history.filter { it.id != active?.id }.map { it.toBot() }
    }

    private fun ActiveOrder.toBot(): BotOrder {
        val restaurant = SampleData.restaurant(restaurantId)
        val state = when {
            cancelled -> OrderState.Cancelled
            step == OrderStep.Received -> OrderState.Received
            step == OrderStep.Preparing -> OrderState.Preparing
            step == OrderStep.OnTheWay -> OrderState.OnTheWay
            else -> OrderState.Delivered
        }
        val hasDriver = !pickup && (state == OrderState.OnTheWay || state == OrderState.Delivered)
        return BotOrder(
            id = id,
            restaurantName = restaurant?.name.orEmpty(),
            restaurantArea = restaurant?.area.orEmpty(),
            lines = lines.map { BotLine(it.dish.name, it.qty, it.total) },
            total = total,
            deliveryFee = restaurant?.deliveryFee ?: 0,
            freeDeliveryOver = restaurant?.freeDeliveryOver ?: 0,
            pickup = pickup,
            address = address,
            pay = if (pay == PayMethod.Card) PayKind.Card else PayKind.Cash,
            state = state,
            minutesLeft = if (state.inProgress) minutesLeft else null,
            minutesLate = if (state.inProgress) lateMinutes else 0,
            placedAt = placedAt,
            deliveredAt = if (state == OrderState.Delivered) deliveredAt ?: placedAt else null,
            driverName = if (hasDriver) ActiveOrder.DRIVER_NAME else null,
            driverVehicle = if (hasDriver) ActiveOrder.DRIVER_VEHICLE else null,
            rating = rating,
            active = true,
        )
    }

    private fun PastOrder.toBot(): BotOrder {
        val restaurant = SampleData.restaurant(restaurantId)
        return BotOrder(
            id = id,
            restaurantName = restaurantName,
            restaurantArea = restaurant?.area.orEmpty(),
            lines = lines.map { BotLine(it.name, it.qty, it.total) },
            total = total,
            deliveryFee = restaurant?.deliveryFee ?: 0,
            freeDeliveryOver = restaurant?.freeDeliveryOver ?: 0,
            pickup = pickup,
            address = address,
            pay = if (pay == PayMethod.Card.name) PayKind.Card else PayKind.Cash,
            state = OrderState.Delivered,
            minutesLeft = null,
            minutesLate = 0,
            placedAt = placedAt,
            deliveredAt = if (deliveredAt > 0) deliveredAt else placedAt,
            driverName = if (pickup) null else ActiveOrder.DRIVER_NAME,
            driverVehicle = if (pickup) null else ActiveOrder.DRIVER_VEHICLE,
            rating = rating,
            active = false,
        )
    }

    fun finishOrder() {
        tickJob?.cancel()
        _state.update { it.copy(activeOrder = null) }
    }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }
}
