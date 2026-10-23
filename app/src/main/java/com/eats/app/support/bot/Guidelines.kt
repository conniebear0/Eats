package com.eats.app.support.bot

/**
 * The resolution guidelines the chatbot follows. They come from:
 *  - the Uber Eats Complaint Data Warehouse Repository (122 complaints, 8 categories, severity matrix,
 *    root causes and responsible departments),
 *  - Uber's South African refund policy (30-day window; refunds to the card, as credit, or by bank
 *    transfer for cash orders; the bot cannot promise an outcome),
 *  - recurring Hellopeter complaints (support that closes cases without solving them, slow or partial
 *    refunds, drivers marking orders delivered that never arrived, no way to reach a person),
 *  - the Consumer Protection Act 68 of 2008 (sections 55 and 56: safe, good-quality goods; section 61:
 *    liability for harm from unsafe goods).
 *
 * Each guideline says how serious the problem is, who fixes it, what the bot does itself and what it
 * hands to a person. The full write-up is in docs/Resolution_Guidelines.md.
 */
object Guidelines {

    data class Guideline(
        val category: Category,
        val subcategory: String,
        val severity: Severity,
        val rootCause: String,
        /** What the bot does before (or instead of) handing over. */
        val botSteps: List<String>,
        /** What the agent must do once it is logged. */
        val agentSteps: List<String>,
    )

    val notDelivered = Guideline(
        Category.DeliveryFailure, "Order Never Arrived", Severity.High, "Driver failed delivery",
        botSteps = listOf(
            "Check the live status first: if it is still on its way, give the ETA instead of logging a complaint.",
            "If it is marked delivered, ask the customer to check the door, reception, security gate and neighbours.",
            "Tell the customer whether they were charged (cash or card on delivery means no payment was taken).",
            "Log a High complaint (24 h) for Logistics with the delivery time and address.",
        ),
        agentSteps = listOf(
            "Check the driver's GPS trail and drop-off time against the address.",
            "Contact the driver; if not delivered, cancel the charge or refund in full and flag the driver.",
        ),
    )

    val markedDelivered = notDelivered.copy(subcategory = "Marked Delivered Incorrectly", rootCause = "GPS verification failure")

    val late = Guideline(
        Category.DeliveryDelay, "Excessive Waiting Time", Severity.Medium, "Driver availability",
        botSteps = listOf(
            "Give the live status and a revised ETA, and say sorry once.",
            "Offer: keep waiting, cancel free of charge (because it is late), or speak to a person.",
            "Log a Medium complaint (48 h) only if the customer wants it followed up or cancels.",
        ),
        agentSteps = listOf("Check driver allocation and kitchen delay; offer a delivery-fee refund or voucher if late by 20+ minutes."),
    )

    val missing = Guideline(
        Category.MissingItems, "Food Missing", Severity.Medium, "Incomplete packing",
        botSteps = listOf(
            "Only after delivery. Ask which item and how many were missing.",
            "Offer a refund for exactly those items (unit price x quantity) or to tell the restaurant.",
            "Ask how the refund should be paid (card orders go back to the card).",
            "Log a Medium complaint (48 h) for the restaurant partner with a refund request.",
        ),
        agentSteps = listOf("Approve the item refund unless the account shows refund abuse; tell the restaurant about the packing error."),
    )

    val wrong = Guideline(
        Category.WrongOrder, "Wrong Food Delivered", Severity.Medium, "Order picking error",
        botSteps = listOf(
            "Ask whether the whole order or only some items were wrong.",
            "Whole order: offer a full refund or a redelivery. Some items: refund those items.",
            "Warn the customer not to eat food that was not theirs if they have allergies.",
            "Log a Medium complaint (48 h) for the restaurant partner.",
        ),
        agentSteps = listOf("Confirm with the restaurant which order was swapped; refund or redeliver; check the other customer's order too."),
    )

    val quality = Guideline(
        Category.FoodQuality, "Cold Food Delivered", Severity.Medium, "Delayed collection / extended delivery time",
        botSteps = listOf(
            "Ask what was wrong (cold, spilled, undercooked, tastes off) and which item.",
            "Undercooked or off food: tell the customer not to eat it.",
            "Offer a refund for the affected item or to pass feedback to the restaurant.",
            "Log a Medium complaint (48 h).",
        ),
        agentSteps = listOf("Check how long the food waited for collection; refund the item; warn the restaurant if it repeats."),
    )

    val safety = Guideline(
        Category.FoodQuality, "Food Safety", Severity.Critical, "Food handling or allergen error",
        botSteps = listOf(
            "Put health first: advise medical help if the customer feels unwell or has an allergic reaction.",
            "Ask them to keep the food and packaging and take a photo.",
            "Log a Critical complaint (immediate). Never argue or ask for proof first.",
        ),
        agentSteps = listOf("Call the customer immediately; suspend the dish if needed; follow the Consumer Protection Act (s55, s61)."),
    )

    val refundLate = Guideline(
        Category.RefundIssue, "Refund Not Received", Severity.High, "Refund processing delay",
        botSteps = listOf(
            "Look up the refund and give its status and the expected timing.",
            "If it is overdue, raise it to Finance as High (24 h) and tell the customer when they will hear back.",
            "If no refund exists, check whether the order qualifies (missing, wrong, quality, not delivered).",
        ),
        agentSteps = listOf("Check the approval workflow; pay or explain with the bank reference."),
    )

    val payment = Guideline(
        Category.PaymentIssue, "Double Billing", Severity.Critical, "System billing error",
        botSteps = listOf(
            "Ask what the customer sees: a pending amount, two charges, an unknown charge or a wrong amount.",
            "Explain card authorisation holds: a pending amount is released by the bank, usually within a few days.",
            "Completed duplicate or unknown charges: log a Critical complaint (immediate) for Finance.",
            "Never ask for full card numbers or PINs in the chat.",
        ),
        agentSteps = listOf("Compare the payment gateway records; reverse duplicates; treat unknown charges as possible fraud."),
    )

    val unauthorised = payment.copy(subcategory = "Unauthorised Deduction", rootCause = "Payment gateway error")

    val poorSupport = Guideline(
        Category.CustomerService, "No Response From Support", Severity.High, "Poor escalation process",
        botSteps = listOf(
            "Find the customer's open complaints. If one is overdue, raise it to a supervisor at once.",
            "If it is not overdue, say exactly when a person will respond.",
            "Never close a complaint for the customer; offer a person or a call.",
        ),
        agentSteps = listOf("Supervisor contacts the customer within 24 hours and owns the case until closed."),
    )

    val cancel = Guideline(
        Category.OrderChange, "Cancellation Request", Severity.High, "Customer request",
        botSteps = listOf(
            "Before the kitchen starts: cancel at once, no charge.",
            "While it is being prepared: send a request to the restaurant (food already made may be charged).",
            "On the way: it cannot be cancelled; offer to contact the driver or a person.",
            "Late orders can always be cancelled free of charge.",
        ),
        agentSteps = listOf("Confirm with the restaurant within minutes; tell the customer whether a charge applies."),
    )

    val address = Guideline(
        Category.OrderChange, "Address Change", Severity.High, "Customer request",
        botSteps = listOf(
            "Before the driver leaves: update the address.",
            "On the way: ask the customer to contact the driver; a person can redirect it.",
        ),
        agentSteps = listOf("Tell the driver about the new address; a longer trip may change the delivery fee."),
    )

    val driver = Guideline(
        Category.DriverConduct, "Driver Conduct", Severity.Medium, "Driver behaviour",
        botSteps = listOf(
            "Apologise and log it for Logistics; never ask the customer to deal with the driver again.",
            "A driver asking for extra money is High priority: the amount in the app is all the customer owes.",
            "Threats, harassment or feeling unsafe is High priority; give the SAPS number (10111).",
        ),
        agentSteps = listOf("Check the trip, contact the driver, and suspend them if it is serious or repeated."),
    )

    /** Time limits used by the bot. */
    const val FOOD_ISSUE_REPORT_HOURS = 48
    const val REFUND_REQUEST_DAYS = 30
    const val LATE_FREE_CANCEL_MINUTES = 10
    const val SUPPORT_HOURS = "every day, 07:00 to 23:00"

    // --- Questions about the service (answered without a person) ---

    const val FEES = "Each restaurant sets its own delivery fee (shown on its page), and many waive it once your " +
        "basket reaches a set amount. There is also a R6 service fee on delivery orders. Pick-up orders have no " +
        "delivery or service fee."
    const val PROMO = "You can use one promo code per order. Enter it in the cart before checkout. Right now " +
        "EATSFIRST gives R30 off. A code can't be added after the order is placed."
    const val TIP = "Tips go 100% to your driver. You choose the amount at checkout (R10 is the default) and you can " +
        "change it there before you place the order. Pick-up orders have no tip."
    const val PAY = "You can pay cash or card when your food arrives (or when you collect it). There's no online " +
        "payment yet, so you are only charged once you have your order."
    const val PICKUP = "Choose Pick-up at the top of Home. You'll see when it's ready on the tracking screen; then " +
        "collect it from the restaurant and pay there. No delivery or service fee applies."
    const val ALLERGENS = "Dish descriptions list the main ingredients, but kitchens handle common allergens " +
        "(nuts, gluten, dairy, egg, fish). If you have a serious allergy, add a note to the dish and check with the " +
        "restaurant before you eat. If you're having a reaction now, get medical help first."
    const val ACCOUNT = "You can change your delivery address and appearance under Profile. Forgot your password? " +
        "Sign out and tap \"Forgot password?\" on the login screen to get a reset code."
    const val RATE = "Once your order is delivered, the tracking screen asks you to rate it with 1 to 5 stars. " +
        "Ratings go to the restaurant."
    const val INSTRUCTIONS = "You can add delivery instructions (gate code, complex name, \"call on arrival\") at " +
        "checkout. If your order is already on its way, tell me and I'll pass a note to the driver."
    const val CANT_DO = "I can't approve refunds, take payments or see card details. A person handles anything " +
        "involving money, and I'll always tell you when they will respond."
}
