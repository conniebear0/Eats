package com.eats.app.support.bot

/*
 * Plain Kotlin models for the support chatbot. No Android code, so the whole bot can be unit-tested
 * on a computer (see app/src/test) and ported one-to-one to the HTML test build.
 */

// ---------------------------------------------------------------------------------------------
// What the bot knows about an order
// ---------------------------------------------------------------------------------------------

enum class OrderState(val label: String) {
    Received("Order received"),
    Preparing("Being prepared"),
    OnTheWay("On the way"),
    Delivered("Delivered"),
    Cancelled("Cancelled");

    val inProgress: Boolean get() = this == Received || this == Preparing || this == OnTheWay
}

enum class PayKind(val label: String) { Cash("cash"), Card("card") }

data class BotLine(val name: String, val qty: Int, val total: Int) {
    val unit: Int get() = if (qty > 0) total / qty else total
}

/**
 * A snapshot of one order, built by the app from its own order data (OrderViewModel).
 * Money is in whole rands, like the rest of the app.
 */
data class BotOrder(
    val id: String,
    val restaurantName: String,
    val restaurantArea: String,
    val lines: List<BotLine>,
    val total: Int,
    val deliveryFee: Int,
    val freeDeliveryOver: Int,
    val pickup: Boolean,
    val address: String,
    val pay: PayKind,
    val state: OrderState,
    /** Minutes until it arrives; null when it is not in progress. */
    val minutesLeft: Int?,
    /** How many minutes past the promised time; 0 when on time. */
    val minutesLate: Int,
    val placedAt: Long,
    val deliveredAt: Long?,
    val driverName: String?,
    val driverVehicle: String?,
    val rating: Int,
    /** True for the order currently being tracked, false for past orders. */
    val active: Boolean,
) {
    val itemCount: Int get() = lines.sumOf { it.qty }
    val subtotal: Int get() = lines.sumOf { it.total }
}

// ---------------------------------------------------------------------------------------------
// Complaints, as in the Uber Eats complaint data warehouse (categories, severity, statuses)
// ---------------------------------------------------------------------------------------------

/** Severity matrix from the complaint repository: how fast a person must respond. */
enum class Severity(val label: String, val responseHours: Int, val responseText: String) {
    Low("Low", 72, "within 72 hours"),
    Medium("Medium", 48, "within 48 hours"),
    High("High", 24, "within 24 hours"),
    /** "Immediately": a person is assigned at once; after an hour it counts as overdue. */
    Critical("Critical", 1, "immediately");

    fun raised(): Severity = if (this == Critical) Critical else entries[ordinal + 1]
}

/** Dim_ComplaintCategory, plus "Order Change" for requests that are not complaints about a failure. */
enum class Category(val label: String, val department: String) {
    DeliveryFailure("Delivery Failure", "Logistics"),
    RefundIssue("Refund Issue", "Finance"),
    PaymentIssue("Payment Issue", "Finance"),
    CustomerService("Customer Service", "Customer Support"),
    MissingItems("Missing Items", "Restaurant Operations"),
    FoodQuality("Food Quality", "Restaurant Operations"),
    WrongOrder("Wrong Order", "Restaurant Operations"),
    DeliveryDelay("Delivery Delay", "Logistics"),
    OrderChange("Order Change", "Customer Support"),
    /** Added from the Hellopeter research: rude drivers, drivers asking for extra money. */
    DriverConduct("Driver Conduct", "Logistics"),
}

/**
 * Dim_Resolution, with each status defined (the repository asks for this before relying on the
 * resolution rate):
 *  Open      logged, waiting for an agent
 *  Pending   an agent is working on it (or waiting on the restaurant, driver or bank)
 *  Resolved  the agent has fixed it; the customer can confirm or reopen
 *  Closed    no further action needed (customer confirmed, or 7 days after Resolved)
 *  Unresolved  closed without a fix the customer accepted (reopened twice, or the customer gave up)
 */
enum class ComplaintStatus(val label: String) {
    Open("Open"),
    Pending("Pending"),
    Resolved("Resolved"),
    Closed("Closed"),
    Unresolved("Unresolved");

    val isOpen: Boolean get() = this == Open || this == Pending
}

enum class RefundMethod(val label: String, val timing: String) {
    Card("back to your card", "within 3 to 5 business days, depending on your bank"),
    BankTransfer("by bank transfer", "within 3 to 5 business days"),
    EatsCredit("as eats credit", "straight away"),
}

enum class RefundStatus(val label: String) { Requested("Requested"), Approved("Approved"), Paid("Paid"), Declined("Declined") }

data class Refund(val amount: Int, val method: RefundMethod, val status: RefundStatus, val reason: String)

data class ComplaintEvent(val at: Long, val text: String, val by: String, val status: ComplaintStatus? = null)

data class TranscriptLine(val fromCustomer: Boolean, val text: String, val at: Long)

data class Complaint(
    val id: String,
    val orderId: String?,
    val restaurantName: String?,
    val category: Category,
    val subcategory: String,
    val severity: Severity,
    val status: ComplaintStatus,
    val createdAt: Long,
    val dueAt: Long,
    val description: String,
    val refund: Refund?,
    val events: List<ComplaintEvent>,
    val transcript: List<TranscriptLine>,
    val reopened: Int = 0,
    val escalated: Boolean = false,
) {
    val reference: String get() = "C-$id"
    fun overdue(now: Long): Boolean = status.isOpen && now > dueAt
}

// ---------------------------------------------------------------------------------------------
// Conversation
// ---------------------------------------------------------------------------------------------

/** Everything the customer can mean. The decision table in SupportEngine handles each one. */
enum class Intent(val label: String) {
    // Order problems (the 8 complaint categories)
    WhereIsOrder("Where's my order?"),
    NeverArrived("My order never arrived"),
    Late("My order is late"),
    MissingItem("Something's missing"),
    WrongOrder("Wrong order or item"),
    FoodQuality("Problem with my food"),
    FoodSafety("I got sick / allergy / foreign object"),
    RefundStatus("Where's my refund?"),
    PaymentProblem("Charged twice or wrong charge"),
    PoorSupport("Nobody has helped me"),
    // Changes
    CancelOrder("Cancel my order"),
    ChangeAddress("Change delivery address"),
    ModifyOrder("Change my order"),
    ContactDriver("Contact my driver"),
    DriverIssue("Problem with my driver"),
    // Questions about the order or the service
    OrderDetails("What did I order?"),
    Fees("Fees and delivery cost"),
    Promo("Promo codes"),
    Tip("Tipping"),
    PaymentMethods("How can I pay?"),
    Pickup("Pick-up orders"),
    Allergens("Allergies and ingredients"),
    Account("My account"),
    Rate("Rate my order"),
    DeliveryInstructions("Delivery instructions"),
    ComplaintStatus("My complaints"),
    // Conversation
    TalkToHuman("Talk to a person"),
    CallAgent("Call an agent"),
    SmallTalk("Chat with the assistant"),
    Ack("Okay"),
    ServiceInfo("Delivery areas and hours"),
    WillBeCharged("Will I be charged?"),
    Correction("That's not what I meant"),
    Greeting("Hello"),
    Thanks("Thanks"),
    Goodbye("Bye"),
    Unknown("Something else");

    companion object {
        /** The main menu shown when the bot needs the customer to choose. */
        val menu = listOf(WhereIsOrder, MissingItem, WrongOrder, FoodQuality, RefundStatus, PaymentProblem, CancelOrder, ComplaintStatus)
        /** Intents the AI helper may choose from. */
        val aiChoices = entries.filter { it != Unknown && it != Correction }
    }
}

/** A button under the chat. The label is what the customer sees (and what the chat sends). */
data class QuickReply(val action: Action, val label: String)

sealed class Action {
    data class Pick(val intent: Intent) : Action()
    data class ChooseOrder(val orderId: String) : Action()
    data class ChooseItem(val index: Int) : Action()
    data class ChooseQty(val qty: Int) : Action()
    data class ChooseMethod(val method: RefundMethod) : Action()
    data class ChooseQuality(val kind: QualityKind) : Action()
    data class ChooseCharge(val kind: ChargeKind) : Action()
    data class OpenComplaint(val complaintId: String) : Action()
    data object WholeOrderWrong : Action()
    data object SomeItemsWrong : Action()
    data object Redeliver : Action()
    data object FullRefund : Action()
    data object ItemRefund : Action()
    data object TellRestaurant : Action()
    data object CheckedEverywhere : Action()
    data object FoundIt : Action()
    data object WasCharged : Action()
    data object KeepWaiting : Action()
    data object ConfirmCancel : Action()
    data object KeepOrder : Action()
    data object ChaseComplaint : Action()
    data object Helped : Action()
    data object NotHelped : Action()
    data object Human : Action()
    data object Call : Action()
    data object Menu : Action()
    /** "You also mentioned ...": sort out the second problem now, or leave it. */
    data object FollowUpYes : Action()
    data object FollowUpNo : Action()
    /** An address the customer typed on its own, confirmed. */
    data class UseAddress(val address: String) : Action()
}

enum class QualityKind(val label: String) {
    Cold("It arrived cold"),
    Spilled("Spilled or damaged"),
    Undercooked("Undercooked or raw"),
    TastesOff("Tastes or smells off"),
}

enum class ChargeKind(val label: String) {
    Pending("A pending amount"),
    Twice("Charged twice"),
    Unknown("I don't recognise it"),
    WrongAmount("Wrong amount"),
}

sealed class UserInput {
    /** [allowAi] = false when the AI helper already had its turn (or is off). */
    data class Text(val text: String, val allowAi: Boolean = true) : UserInput()
    data class Choice(val action: Action) : UserInput()
    /** The AI helper understood the message (the rules then follow that intent). */
    data class AiIntent(val intent: Intent, val text: String) : UserInput()
}

/** A small card in the chat, e.g. the order summary. */
data class InfoCard(val title: String, val rows: List<Pair<String, String>>)

data class RefundSpec(val amount: Int, val reason: String)

/** Asks the app to log (or add to) a complaint. The bot itself never approves or pays a refund. */
data class Escalation(
    val category: Category,
    val subcategory: String,
    val severity: Severity,
    val orderId: String?,
    val restaurantName: String?,
    val description: String,
    val refund: RefundSpec? = null,
    val refundMethod: RefundMethod? = null,
)

/** Changes the bot is allowed to make to an order on the customer's behalf. */
sealed class OrderAction {
    data class Cancel(val orderId: String) : OrderAction()
    data class ChangeAddress(val orderId: String, val address: String) : OrderAction()
}

enum class ReplySource { Rules, Learned, Ai }

data class BotReply(
    val intent: Intent,
    val texts: List<String>,
    val card: InfoCard? = null,
    val quickReplies: List<QuickReply> = emptyList(),
    val escalation: Escalation? = null,
    val orderAction: OrderAction? = null,
    /** Raise this complaint to a supervisor (customer service guideline). */
    val chaseComplaintId: String? = null,
    /** The rules could not understand the message: try the AI helper before giving up. */
    val askAi: Boolean = false,
    /** True when the bot actually answered something, so "That helped" makes sense. */
    val answered: Boolean = false,
    /** Whether the message was understood by the built-in rules or by something the bot learned. */
    val source: ReplySource = ReplySource.Rules,
    /** Shown after the complaint line, e.g. the offer to sort out a second problem. */
    val afterTexts: List<String> = emptyList(),
)

/** What the bot is waiting for after its last question. */
/** One missing item: which order line and how many of it. */
data class MissingPick(val index: Int, val qty: Int)

sealed class Awaiting {
    data object Nothing : Awaiting()
    data class OrderChoice(val intent: Intent, val text: String) : Awaiting()
    /** [tamper] = the customer says the bag was opened or food eaten before it reached them. */
    data class MissingWhich(val tamper: Boolean = false) : Awaiting()
    data class MissingHowMany(val index: Int, val tamper: Boolean = false) : Awaiting()
    data class MissingAction(val items: List<MissingPick>, val tamper: Boolean = false) : Awaiting()
    data object WrongScope : Awaiting()
    data object WrongWhich : Awaiting()
    /** [indexes] = the wrong items; null = the whole order was wrong. */
    data class WrongAction(val indexes: List<Int>?) : Awaiting()
    data class QualityWhat(val indexes: List<Int> = emptyList()) : Awaiting()
    data class QualityWhich(val kind: QualityKind) : Awaiting()
    data class QualityAction(val kind: QualityKind, val indexes: List<Int>) : Awaiting()
    data class FollowUpOffer(val intent: Intent, val text: String) : Awaiting()
    data class AddressConfirm(val address: String) : Awaiting()
    data object DeliveryCheck : Awaiting()
    data object ChargeWhat : Awaiting()
    data object LateChoice : Awaiting()
    data object CancelConfirm : Awaiting()
    data object NewAddress : Awaiting()
    data class RefundMethodChoice(val escalation: Escalation) : Awaiting()
}

data class ConversationState(
    val awaiting: Awaiting = Awaiting.Nothing,
    val orderId: String? = null,
    val misses: Int = 0,
    val frustration: Int = 0,
    val lastProblem: Intent? = null,
    /** A second problem from the same message, offered once the first is dealt with. */
    val followUp: FollowUp? = null,
    /** The customer already asked to cancel an order that's on its way. */
    val cancelAsked: Boolean = false,
)

data class FollowUp(val intent: Intent, val text: String)

data class BotContext(
    val firstName: String,
    /** The active order first (if any), then past orders, newest first. */
    val orders: List<BotOrder>,
    val complaints: List<Complaint>,
    val now: Long,
    val aiAvailable: Boolean = false,
)

data class BotResult(val reply: BotReply, val state: ConversationState)
