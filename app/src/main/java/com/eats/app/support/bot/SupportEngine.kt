package com.eats.app.support.bot

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Dates and money the way the app shows them. */
object Fmt {
    fun money(rands: Int): String = "R$rands"
    fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(ms))
    fun date(ms: Long): String = SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date(ms))
    fun dateTime(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH).format(Date(ms))
    fun minutes(n: Int): String = if (n == 1) "1 min" else "$n min"

    /** "today at 14:05", "tomorrow at 09:30" or "23 Oct at 14:05". */
    fun due(due: Long, now: Long): String {
        val days = dayNumber(due) - dayNumber(now)
        return when (days) {
            0L -> "today at ${time(due)}"
            1L -> "tomorrow at ${time(due)}"
            else -> "${date(due)} at ${time(due)}"
        }
    }

    /** A number that goes up by one each calendar day (local time). */
    fun dayNumber(ms: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return c.get(Calendar.YEAR) * 400L + c.get(Calendar.DAY_OF_YEAR)
    }
}

/**
 * The decision table. Each intent is answered from the customer's real order and complaints,
 * following [Guidelines]. The engine never approves refunds or takes payments: anything involving
 * money becomes an [Escalation] that a person reviews.
 *
 * web/bot.js is a line-by-line port of this file; harness/ checks both give identical answers.
 */
class SupportEngine(private val classifier: IntentClassifier = IntentClassifier()) {

    /** One item the customer says is missing; [qty] = null when they didn't say how many. */
    private data class Want(val index: Int, val qty: Int?)

    /** Several problems in one message: the one to handle now, and the one to offer next. */
    private data class Plan(val primary: Intent, val focus: String, val secondary: FollowUp?)

    // =========================================================================================
    // Entry points
    // =========================================================================================

    fun greet(ctx: BotContext): BotReply {
        val order = ctx.orders.firstOrNull()
        val hello = "Hi ${ctx.firstName}, I'm the eats support assistant."
        val open = ctx.complaints.filter { it.status.isOpen }
        val complaintNote = when (open.size) {
            0 -> null
            1 -> "You have an open complaint, ${open[0].reference}. A person will respond by ${Fmt.due(open[0].dueAt, ctx.now)}."
            else -> "You have ${open.size} open complaints. I can give you an update on any of them."
        }
        val texts = mutableListOf<String>()
        val chips: List<QuickReply>
        if (order != null && order.active) {
            texts += "$hello ${stageSummary(order)}"
            chips = stageChips(order)
        } else {
            texts += if (order != null) {
                "$hello I can see your last order from ${order.restaurantName} on ${Fmt.date(order.placedAt)}. What can I help with?"
            } else {
                "$hello What can I help with?"
            }
            chips = menuChips()
        }
        complaintNote?.let { texts += it }
        val withComplaints = if (open.isNotEmpty() && chips.none { (it.action as? Action.Pick)?.intent == Intent.ComplaintStatus }) {
            chips + pick(Intent.ComplaintStatus)
        } else chips
        return BotReply(Intent.Greeting, texts, quickReplies = withComplaints)
    }

    /** Posted in the chat when the order being tracked moves on, e.g. the driver picks it up. */
    fun statusUpdate(order: BotOrder): BotReply? {
        val text = when (order.state) {
            OrderState.Received -> return null
            OrderState.Preparing -> "Update: ${order.restaurantName} has started preparing your food."
            OrderState.OnTheWay -> if (order.pickup) {
                "Update: your order is ready for pick-up at ${order.restaurantName}, ${order.restaurantArea}."
            } else {
                "Update: ${order.driverName ?: "your driver"} has picked up your order and is about ${Fmt.minutes(order.minutesLeft ?: 1)} away."
            }
            OrderState.Delivered -> if (order.pickup) "Update: you've collected your order. Enjoy! If anything isn't right, tell me."
            else "Update: your order has been delivered. Enjoy! If anything isn't right, tell me."
            OrderState.Cancelled -> "Update: this order was cancelled. You won't be charged."
        }
        return BotReply(Intent.WhereIsOrder, listOf(text), quickReplies = stageChips(order))
    }

    fun respond(input: UserInput, state: ConversationState, ctx: BotContext): BotResult {
        val res = when (input) {
            is UserInput.Choice -> onChoice(input.action, state.copy(misses = 0), ctx)
            is UserInput.Text -> onText(input.text, input.allowAi, state, ctx)
            is UserInput.AiIntent -> {
                val r = onIntent(input.intent, input.text, state.copy(misses = 0), ctx)
                r.copy(reply = r.reply.copy(source = ReplySource.Ai))
            }
        }
        return offerFollowUp(res)
    }

    /** After the first problem is dealt with, offer to sort out the second one the customer mentioned. */
    private fun offerFollowUp(res: BotResult): BotResult {
        val f = res.state.followUp ?: return res
        if (res.reply.askAi || res.state.awaiting != Awaiting.Nothing) return res
        if (res.reply.intent == f.intent) return BotResult(res.reply, res.state.copy(followUp = null))
        return BotResult(
            res.reply.copy(
                afterTexts = res.reply.afterTexts + "You also mentioned ${followUps[f.intent]}. Shall we sort that out now?",
                quickReplies = listOf(qr(Action.FollowUpYes, "Yes, sort that out"), qr(Action.FollowUpNo, "No, that's all")) +
                    res.reply.quickReplies.filter { it.action != Action.Menu },
            ),
            res.state.copy(followUp = null, awaiting = Awaiting.FollowUpOffer(f.intent, f.text)),
        )
    }

    /** Used when neither the rules nor the AI helper understood the message. */
    fun notUnderstood(state: ConversationState, ctx: BotContext): BotResult {
        val misses = state.misses + 1
        if (misses >= LOOP_BREAKER) {
            // Don't keep guessing, and don't log a complaint about nothing: offer a person instead.
            return BotResult(
                BotReply(
                    Intent.Unknown,
                    listOf("Sorry, I'm still not sure what you mean. I can bring in a person to help, or you can pick one of these."),
                    quickReplies = listOf(human(), qr(Action.Call, "Call an agent")) + menuChips(),
                ),
                state.copy(misses = 0, awaiting = Awaiting.Nothing),
            )
        }
        return BotResult(
            BotReply(
                Intent.Unknown,
                listOf("Sorry, I didn't quite get that. Could you tell me in a few words what's wrong, or pick one of these?"),
                quickReplies = menuChips(),
            ),
            state.copy(misses = misses, awaiting = Awaiting.Nothing),
        )
    }

    private fun frustratedUnknown(text: String, state: ConversationState, ctx: BotContext): BotResult {
        val count = state.frustration + 1
        if (count < FRUSTRATION_LIMIT) {
            return BotResult(
                BotReply(
                    Intent.Unknown,
                    listOf("I'm sorry, I can tell this has been frustrating. Tell me what went wrong in a few words and I'll sort it out, or I can get a person to help."),
                    quickReplies = listOf(human()) + menuChips(),
                ),
                state.copy(frustration = count, awaiting = Awaiting.Nothing),
            )
        }
        val o = orderFor(state, ctx)
        return escalate(
            Intent.Unknown,
            listOf("I'm really sorry, I can hear how frustrating this is. Let me get a person to take over right away."),
            Escalation(Category.CustomerService, "Customer Frustrated", Severity.High, o?.id, o?.restaurantName,
                withWords("The customer is upset and asked for help more than once.", text)),
            state.copy(frustration = 0), ctx,
        )
    }

    // =========================================================================================
    // Typed messages
    // =========================================================================================

    private fun onText(text: String, allowAi: Boolean, state: ConversationState, ctx: BotContext): BotResult {
        val order = orderFor(state, ctx)
        val m0 = state.copy(misses = 0)
        // Only punctuation or emoji: show the menu (or acknowledge an angry emoji), never count it as a miss.
        if (TextNormaliser.tokens(text).isEmpty()) {
            return if (classifier.isFrustrated(text)) frustratedUnknown(text, state, ctx) else greeting(m0, ctx)
        }
        val correcting = classifier.isCorrection(text)
        if (!correcting) answerAwaiting(text, order, m0, ctx)?.let { return it }

        val found = classifier.classify(text)
        if (state.awaiting == Awaiting.NewAddress && order != null &&
            (found.intent == Intent.Unknown || found.intent == Intent.Ack || (found.intent == Intent.ChangeAddress && looksLikeAddress(text)))
        ) {
            if (found.intent != Intent.Ack || looksLikeAddress(text)) return newAddress(order, text, m0, ctx)
        }
        var intent = found.intent
        if (correcting) {
            // "No, that's wrong" alone asks what they meant; "not that, my coke is missing" goes straight to the coke.
            val rest = withoutCorrection(text)
            val again = if (rest == "") null else classifier.classify(rest)
            intent = if (again != null && again.intent !in notACorrection) again.intent else Intent.Correction
        }
        val frustrated = classifier.isFrustrated(text)
        var focus = text
        var followUp = state.followUp
        if (!correcting && found.source != ReplySource.Learned) {
            val plan = planProblems(text, intent, order)
            if (plan != null) {
                intent = plan.primary
                focus = plan.focus
                if (plan.secondary != null) followUp = plan.secondary
            }
        }
        intent = inContext(text, intent, order)
        if (isComplaintFollowUp(text, intent, ctx)) return complaintFollowUp(text, m0, ctx)
        val weakOnly = found.source == ReplySource.Rules && found.confidence < STRONG_ENOUGH && intent !in socialIntents
        if (allowAi && ctx.aiAvailable && !frustrated && (intent == Intent.Unknown || weakOnly)) {
            return BotResult(BotReply(Intent.Unknown, emptyList(), askAi = true), state)
        }
        if (intent == Intent.Unknown) return if (frustrated) frustratedUnknown(text, state, ctx) else notUnderstood(state, ctx)

        val fresh = state.copy(misses = 0, frustration = if (frustrated) state.frustration + 1 else 0, followUp = followUp)
        if (frustrated && fresh.frustration >= FRUSTRATION_LIMIT && intent !in calmIntents) {
            val o = orderFor(fresh, ctx)
            return escalate(
                intent,
                listOf("I'm really sorry, I can hear how frustrating this is. Let me get a person to take over right away."),
                Escalation(Category.CustomerService, "Customer Frustrated", Severity.High, o?.id, o?.restaurantName,
                    withWords("The customer is upset and asked for help more than once.", text)),
                fresh.copy(frustration = 0), ctx,
            )
        }

        val result = onIntent(intent, focus, fresh, ctx)
        val reply = result.reply.copy(source = found.source)
        if (!frustrated || intent in calmIntents) return result.copy(reply = reply)
        val empathy = "I'm sorry, I can tell this has been frustrating."
        val chips = if (reply.quickReplies.any { it.action == Action.Human }) reply.quickReplies else reply.quickReplies + human()
        val texts = if (reply.texts.isEmpty()) listOf(empathy) else listOf("$empathy ${reply.texts[0]}") + reply.texts.drop(1)
        return result.copy(reply = reply.copy(texts = texts, quickReplies = chips))
    }

    /** Typed answers to the question the bot just asked (buttons work too, but people type). */
    private fun answerAwaiting(text: String, order: BotOrder?, m0: ConversationState, ctx: BotContext): BotResult? {
        val a = m0.awaiting
        val t = TextNormaliser.tokens(text)
        val all = rx("""\b(everything|all of it|all of them|the whole order|whole order|entire order|the lot|nothing came|all)\b""")
        when (a) {
            is Awaiting.MissingWhich -> if (order != null) {
                val items = missingItems(text, order)
                if (items.isNotEmpty()) return missingFor(order, items, m0, a.tamper)
                if (has(text, all)) return neverArrived(order, m0, ctx, text)
            }
            Awaiting.WrongWhich -> if (order != null) {
                val idx = findItems(text, order)
                if (idx.isNotEmpty()) return wrongItems(order, idx, m0)
                if (has(text, all)) return onChoice(Action.WholeOrderWrong, m0, ctx)
            }
            is Awaiting.QualityWhich -> if (order != null) {
                if (has(text, rx("""\b(just tell|tell them|tell the restaurant|let them know|feedback)\b"""))) return onChoice(Action.TellRestaurant, m0, ctx)
                val idx = findItems(text, order)
                if (idx.isNotEmpty()) return qualityAction(order, a.kind, idx, m0)
                if (has(text, all) || has(text, rx("""\b(the food|my food|the meal|both)\b"""))) return qualityAction(order, a.kind, order.lines.indices.toList(), m0)
            }
            is Awaiting.MissingHowMany -> {
                val qty = Regex("""\d+""").find(text)?.value?.toIntOrNull() ?: wordNumber(text)
                if (qty != null && qty > 0) return onChoice(Action.ChooseQty(qty), m0, ctx)
            }
            Awaiting.ChargeWhat -> chargeKind(text)?.let { return chargeAnswer(it, m0, ctx) }
            is Awaiting.QualityWhat -> if (order != null) {
                val k = qualityKind(text)
                if (k != null) {
                    val idx = findItems(text, order)
                    return chooseQuality(order, k, m0, idx.ifEmpty { a.indexes })
                }
            }
            Awaiting.WrongScope -> if (order != null) {
                if (has(text, rx("""\b(whole|all|everything|entire|full|someone else|not mine|not my)\b"""))) return onChoice(Action.WholeOrderWrong, m0, ctx)
                val idx = findItems(text, order)
                if (idx.isNotEmpty()) return wrongItems(order, idx, m0)
                if (t.any { it in setOf("some", "one", "only", "part", "item", "items", "few", "just") }) return onChoice(Action.SomeItemsWrong, m0, ctx)
            }
            Awaiting.DeliveryCheck -> {
                if (order != null && findItems(text, order).isNotEmpty() && findItems(text, order).size < order.lines.size) {
                    return missingFor(order, missingItems(text, order), m0, false)
                }
                if (has(text, rx("""\b(found it|got it|its here|it was there|found)\b""")) && !has(text, rx("""\b(not|no|didnt|havent|cant)\b"""))) return onChoice(Action.FoundIt, m0, ctx)
                if (has(text, rx("""\b(no|not|nothing|nowhere|checked|looked|still|nope|nah|already)\b"""))) return onChoice(Action.CheckedEverywhere, m0, ctx)
            }
            Awaiting.CancelConfirm -> when (cancelAnswer(text)) {
                true -> return onChoice(Action.ConfirmCancel, m0, ctx)
                false -> return onChoice(Action.KeepOrder, m0, ctx)
                null -> Unit
            }
            Awaiting.LateChoice -> {
                if (has(text, rx("""\b(keep waiting|ill wait|i will wait|i can wait|happy to wait|ill keep waiting|keep it|its fine|no cancel|dont cancel|no thanks)\b""")) &&
                    !has(text, rx("""\b(still|cancel it|cancel please)\b"""))
                ) return onChoice(Action.KeepWaiting, m0, ctx)
                if (has(text, rx("""\b(cancel|yes|yeah|please|dont want|do not want)\b"""))) return onChoice(Action.ConfirmCancel, m0, ctx)
            }
            is Awaiting.RefundMethodChoice -> {
                if (has(text, rx("""\b(credit|voucher|wallet|app|account credit)\b""")) && !has(text, rx("""\bbank\b"""))) {
                    return onChoice(Action.ChooseMethod(RefundMethod.EatsCredit), m0, ctx)
                }
                if (has(text, rx("""\b(bank|transfer|eft|account|cash)\b"""))) return onChoice(Action.ChooseMethod(RefundMethod.BankTransfer), m0, ctx)
            }
            is Awaiting.OrderChoice -> {
                val match = orderFromText(text, ctx)
                if (match != null) return onChoice(Action.ChooseOrder(match.id), m0, ctx)
                // still need to know which order: ask again rather than wander off
                val again = classifier.classify(text).intent
                if (again in setOf(Intent.RefundStatus, Intent.Ack, Intent.Unknown, Intent.Greeting, a.intent) && ctx.orders.size > 1) {
                    return BotResult(
                        BotReply(a.intent, listOf("Sure. First, which order is this about? Tap it below."),
                            quickReplies = ctx.orders.take(3).map { qr(Action.ChooseOrder(it.id), orderLabel(it)) }),
                        m0,
                    )
                }
            }
            is Awaiting.MissingAction, is Awaiting.QualityAction, is Awaiting.WrongAction -> {
                val whole = a is Awaiting.WrongAction && a.indexes == null
                if (whole && has(text, rx("""\b(redeliver|resend|send (it|the|my|me|a)|replace|replacement|correct order|new order|right order|deliver the right)\b"""))) {
                    return onChoice(Action.Redeliver, m0, ctx)
                }
                val noRefund = has(text, rx("""\b(no refund|dont want a refund|dont need a refund|not a refund|no money)\b"""))
                if (!noRefund && has(text, rx("""\b(refund|money back|money|reimburse|credit|yes|yeah|yep|please|ok|okay|sure|ja|yebo)\b"""))) {
                    return onChoice(if (whole) Action.FullRefund else Action.ItemRefund, m0, ctx)
                }
                if (noRefund || has(text, rx("""\b(tell|let them know|feedback|inform|report|no thanks|no|nah|nope|just)\b"""))) return onChoice(Action.TellRestaurant, m0, ctx)
            }
            is Awaiting.FollowUpOffer -> {
                if (has(text, rx("""\b(yes|yeah|yep|ja|please|sure|ok|okay|go ahead|sort it|yebo)\b"""))) return onChoice(Action.FollowUpYes, m0, ctx)
                if (has(text, rx("""\b(no|nope|nah|thats all|that is all|its fine|dont worry|leave it)\b"""))) return onChoice(Action.FollowUpNo, m0, ctx)
            }
            else -> Unit
        }
        val live = order != null && order.active && order.state.inProgress
        if (live && has(text, rx("""^(ok |okay |fine |no )?(ill|i will|i can|happy to|ill just) (keep )?wait"""))) return onChoice(Action.KeepWaiting, m0, ctx)
        if (live && !order.pickup && order.state != OrderState.OnTheWay && a == Awaiting.Nothing && isStreetAddress(text) &&
            classifier.classify(text).intent == Intent.Unknown
        ) {
            val address = text.trim().replace(Regex("""[.!]+$"""), "").take(MAX_ADDRESS)
            return BotResult(
                BotReply(
                    Intent.ChangeAddress,
                    listOf("Would you like your order delivered to $address instead of ${order.address}?"),
                    quickReplies = listOf(qr(Action.UseAddress(address), "Yes, use this address"), qr(Action.KeepOrder, "No, keep it")),
                ),
                m0.copy(orderId = order.id, awaiting = Awaiting.AddressConfirm(address)),
            )
        }
        if (a is Awaiting.AddressConfirm && order != null) {
            if (has(text, rx("""\b(yes|yeah|yep|ja|please|sure|ok|okay|correct|use it)\b"""))) return newAddress(order, a.address, m0, ctx)
            if (has(text, rx("""\b(no|nope|nah|keep|wrong)\b"""))) return onChoice(Action.KeepOrder, m0, ctx)
        }
        return null
    }

    private fun isStreetAddress(text: String): Boolean =
        Regex("""\d""").containsMatchIn(text) &&
            Regex("""\b(street|st|road|rd|avenue|ave|drive|dr|lane|ln|close|crescent|cres|way|place|pl|terrace|boulevard|blvd|court|ct|complex|estate|flat|unit)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(text) &&
            looksLikeAddress(text)

    /** Readings that depend on the order: "where are my chips" after delivery means they're missing. */
    private fun inContext(text: String, intent: Intent, order: BotOrder?): Intent {
        if (order == null || !order.active) return intent
        val delivered = order.state == OrderState.Delivered
        if (delivered && intent in setOf(Intent.WhereIsOrder, Intent.Unknown, Intent.Greeting, Intent.Ack, Intent.NeverArrived)) {
            val items = findItems(text, order)
            if (items.isNotEmpty() && items.size < order.lines.size &&
                has(text, rx("""\b(where|no|not|didnt|never|missing|wasnt|werent|without|forgot|left|arent|isnt|nowhere)\b"""))
            ) return Intent.MissingItem
        }
        if (intent == Intent.WrongOrder && delivered &&
            has(text, rx("""\b(only (got|received|came)|got only|received only|only (\d+|one|two|three)\b|(\d+|one|two|three) \w+ (came|arrived) instead of (\d+|two|three|four)|(got|received) (\d+|one|two|three)\b.*\b(ordered|asked for|paid for) (\d+|two|three|four))"""))
        ) return Intent.MissingItem
        if (intent == Intent.Allergens && delivered &&
            has(text, rx("""\b(had|contained|contains|was in|there was|ate|eaten|reaction|swollen|swelling|itchy|rash|cant breathe)\b"""))
        ) return Intent.FoodSafety
        if (intent == Intent.Unknown && order.state.inProgress && has(text, rx("""\b(where|when|how long|status|eta|update)\b""")) &&
            has(text, rx("""\b(food|order|it|driver|delivery)\b"""))
        ) return Intent.WhereIsOrder
        return intent
    }

    private fun cancelAnswer(text: String): Boolean? {
        val n = TextNormaliser.normalise(text)
        val wantsOut = rx("""\b(dont want|do not want|changed my mind|cancel it|cancel the order|cancel please|please cancel|go ahead|do it)\b""").containsMatchIn(n)
        if (!wantsOut && rx("""\b(dont cancel|do not cancel|keep|never ?mind|leave it|no|nope|nah|not now|wait)\b""").containsMatchIn(n)) return false
        if (wantsOut || rx("""\b(yes|yeah|yep|ja|yebo|sure|ok|okay|please|cancel)\b""").containsMatchIn(n)) return true
        return null
    }

    private fun withoutCorrection(text: String): String = TextNormaliser.normalise(text)
        .replace(rx("""\b(no|nope|nah|thats|that is|its|it is|not|what|i|you|meant|mean|asked|said|wrong|right|it|misunderstood|dont understand|you got|got|that|one|didnt|doesnt|help|helpful|answer)\b"""), " ")
        .replace(rx("""\s+"""), " ").trim()

    private fun clauses(text: String): List<String> =
        text.split(Regex("""\b(?:and|but|also|plus|then)\b|[,.;!?]""", RegexOption.IGNORE_CASE))

    /** Several problems in one message: handle the most urgent first and remember the next one. */
    private fun planProblems(text: String, top: Intent, order: BotOrder?): Plan? {
        val keepTop = top !in priority && top !in setOf(Intent.Greeting, Intent.Ack, Intent.Thanks, Intent.SmallTalk, Intent.Unknown, Intent.Goodbye)
        if (keepTop) return null
        val all = classifier.ranked(text)
        val best = all.firstOrNull()?.confidence ?: 0.0
        // A weaker reading counts only if it comes from its own part of the message ("..., and the kota was cold").
        val parts = clauses(text).filter { TextNormaliser.tokens(it).isNotEmpty() }
        fun ownClause(c: Classification): Boolean = parts.size > 1 && parts.any { p ->
            val r = classifier.ranked(p)
            r.any { it.intent == c.intent } && r.none { it.intent != c.intent && it.confidence > c.confidence && it.intent in priority }
        }
        fun toks(c: Classification) = TextNormaliser.tokens(c.matched ?: "")
        val top0 = all.firstOrNull()
        val ranked = all.filter { c ->
            c.confidence >= 0.8 && (c.confidence >= best - 0.15 || ownClause(c)) &&
                // in the same clause, a shorter match than the winner's is just part of the same sentence ("the address on the order is wrong")
                !(top0 != null && c !== top0 && !ownClause(c) && toks(c).size < toks(top0).size)
        }
        fun inside(c: Classification) = ranked.any { d -> d !== c && toks(d).size > toks(c).size && toks(c).all { it in toks(d) } }
        val negated = mutableSetOf<Intent>()
        val neg = rx("""\b(not|nothing|isnt|wasnt|arent|werent|never|no) (missing|wrong|cold|late)\b""")
        for (m in neg.findAll(TextNormaliser.normalise(text))) {
            negated += when (m.groupValues[2]) {
                "missing" -> Intent.MissingItem
                "wrong" -> Intent.WrongOrder
                "cold" -> Intent.FoodQuality
                else -> Intent.Late
            }
        }
        var set = ranked.filter { it.intent in priority && !inside(it) && it.intent !in negated }.map { it.intent }.distinct()
        if (set.isEmpty()) return null
        if (Intent.FoodSafety in set) set = set.filter { it != Intent.FoodQuality }
        if (Intent.NeverArrived in set && Intent.MissingItem in set) {
            val partial = has(text, rx("""\b(some|part|half|few|one of|only)\b""")) || (order != null && findItems(text, order).isNotEmpty())
            set = set.filter { it != (if (partial) Intent.NeverArrived else Intent.MissingItem) }
        }
        if (Intent.CancelOrder in set) set = set.filter { it != Intent.Late }
        if (set.any { it != Intent.RefundStatus && it != Intent.WhereIsOrder }) set = set.filter { it != Intent.RefundStatus && it != Intent.WhereIsOrder }
        set = set.sortedBy { priority.indexOf(it) }
        val primary = set[0]
        val secondary = set.drop(1).firstOrNull { it in followUps }
            ?: return Plan(primary, text, null)
        return Plan(primary, clauseFor(text, primary), FollowUp(secondary, clauseFor(text, secondary)))
    }

    private fun clauseFor(text: String, intent: Intent): String =
        clauses(text).map { it.trim() }.filter { TextNormaliser.tokens(it).isNotEmpty() }
            .firstOrNull { p -> classifier.ranked(p).any { it.intent == intent } } ?: text

    /** The customer's newest open complaint, if any. */
    fun newestOpen(ctx: BotContext): Complaint? = ctx.complaints.filter { it.status.isOpen }.maxByOrNull { it.createdAt }

    private fun isComplaintFollowUp(text: String, intent: Intent, ctx: BotContext): Boolean {
        if (intent !in setOf(Intent.WhereIsOrder, Intent.Late, Intent.Unknown, Intent.Ack)) return false
        if (!timing.containsMatchIn(TextNormaliser.normalise(text))) return false
        if (ctx.orders.any { it.active && it.state.inProgress }) return false
        val c = newestOpen(ctx) ?: return false
        return ctx.now - c.createdAt <= 2 * HOUR
    }

    private fun complaintFollowUp(text: String, state: ConversationState, ctx: BotContext): BotResult {
        val c = newestOpen(ctx)!!
        val speed = has(text, rx("""\b(speed|hurry|faster|quicker|asap|urgent)\b"""))
        val whenText = if (c.severity == Severity.Critical) "is Critical, so a person is being assigned to it right now"
        else "will be picked up by a person by ${Fmt.due(c.dueAt, ctx.now)}"
        val texts = mutableListOf(
            "Your complaint ${c.reference} (${c.subcategory}) $whenText." +
                (c.refund?.let { " Your refund of ${Fmt.money(it.amount)} is ${it.status.label.lowercase()}; once approved it arrives ${it.method.timing}." } ?: ""),
        )
        val chips: List<QuickReply>
        if (speed && c.severity != Severity.Critical && !c.escalated) {
            texts += "I can ask a supervisor to look at it sooner, or you can call an agent now."
            chips = listOf(qr(Action.ChaseComplaint, "Raise it to a supervisor"), qr(Action.Call, "Call an agent"))
        } else {
            texts += "If you'd rather not wait, you can call an agent now."
            chips = listOf(viewChip(c), qr(Action.Call, "Call an agent"))
        }
        return BotResult(
            BotReply(Intent.ComplaintStatus, texts, card = complaintCard(c, ctx.now), quickReplies = chips, answered = true),
            state.copy(awaiting = Awaiting.Nothing),
        )
    }

    // =========================================================================================
    // Intents
    // =========================================================================================

    private fun onIntent(intent: Intent, text: String, stateIn: ConversationState, ctx: BotContext): BotResult {
        var state = stateIn
        if (intent in needsOrder && ctx.orders.isEmpty()) return noOrder(intent, state)
        if (intent in pastOrderProblems && state.orderId == null) {
            val named = orderFromText(text, ctx)
            if (named != null) state = state.copy(orderId = named.id)
            else if (ctx.orders.none { it.active } && ctx.orders.size > 1) return askWhichOrder(intent, text, state, ctx)
        }
        val order = orderFor(state, ctx)
        val s = if (intent in problems) state.copy(lastProblem = intent) else state
        return when (intent) {
            Intent.WhereIsOrder -> whereIsOrder(order!!, s, ctx, emptyList(), text)
            Intent.NeverArrived -> neverArrived(order!!, s, ctx, text)
            Intent.Late -> late(order!!, s, ctx, text)
            Intent.MissingItem -> missingItem(order!!, text, s, ctx)
            Intent.WrongOrder -> wrongOrder(order!!, text, s, ctx)
            Intent.FoodQuality -> foodQuality(order!!, text, s, ctx)
            Intent.FoodSafety -> foodSafety(order, text, s, ctx)
            Intent.RefundStatus -> refundStatus(order, s, ctx, text)
            Intent.PaymentProblem -> paymentProblem(text, s, ctx)
            Intent.PoorSupport -> poorSupport(text, s, ctx)
            Intent.CancelOrder -> cancelOrder(order!!, s, ctx)
            Intent.ChangeAddress -> changeAddress(order!!, s, text, ctx)
            Intent.ModifyOrder -> modifyOrder(order!!, s)
            Intent.ContactDriver -> contactDriver(order!!, s, text)
            Intent.DriverIssue -> driverIssue(order, text, s, ctx)
            Intent.OrderDetails -> orderDetails(order!!, s)
            Intent.Fees -> fees(order, s)
            Intent.Promo -> promo(text, s)
            Intent.Tip -> tip(order, s)
            Intent.PaymentMethods -> payMethods(text, s)
            Intent.Pickup -> pickup(order, s, text, ctx)
            Intent.Allergens -> if (has(text, rx("""\b(halal|vegan|vegetarian|kosher|plant based)\b"""))) serviceInfo(text, s)
            else info(Intent.Allergens, Guidelines.ALLERGENS, s)
            Intent.Account -> account(text, s, ctx)
            Intent.DeliveryInstructions -> deliveryInstructions(order, s, text)
            Intent.Rate -> rate(order!!, s)
            Intent.ComplaintStatus -> complaintStatus(s, ctx)
            Intent.TalkToHuman -> handOff(text, s, ctx)
            Intent.CallAgent -> callAgent(s)
            Intent.SmallTalk -> smallTalk(text, s, ctx)
            Intent.Ack -> ack(s, ctx)
            Intent.ServiceInfo -> serviceInfo(text, s)
            Intent.WillBeCharged -> willBeCharged(order, s, ctx)
            Intent.Correction -> correction(s)
            Intent.Greeting -> greeting(s, ctx)
            Intent.Thanks -> done(Intent.Thanks, "You're welcome, ${ctx.firstName}. Anything else I can help with?", s)
            Intent.Goodbye -> BotResult(
                BotReply(Intent.Goodbye, listOf("Thanks for chatting, ${ctx.firstName}. Enjoy your meal!"), quickReplies = listOf(qr(Action.Menu, "I need something else"))),
                s.copy(awaiting = Awaiting.Nothing),
            )
            Intent.Unknown -> notUnderstood(s, ctx)
        }
    }

    // --- Where is my order / never arrived / late -----------------------------------------------

    private fun whereIsOrder(order: BotOrder, state: ConversationState, ctx: BotContext, prefix: List<String>, text: String = ""): BotResult {
        val s = state.copy(orderId = order.id)
        if (order.state == OrderState.Cancelled) return cancelledOrder(order, s, prefix, has(text, rx("""\b(why|who|how come|reason)\b""")))
        val extra = if (order.state == OrderState.OnTheWay && !order.pickup && order.driverName != null &&
            has(text, rx("""\b(car|vehicle|plate|registration|bike|scooter|who is|name|which driver|what driver)\b"""))
        ) {
            val v = order.driverVehicle
            listOf("Your driver is ${order.driverName}" + (if (v != null) ", on a ${v.split(",")[0].lowercase()} (registration ${v.split(",").drop(1).joinToString(",").trim()})." else "."))
        } else emptyList()
        return when (order.state) {
            OrderState.Received, OrderState.Preparing, OrderState.OnTheWay -> {
                val texts = prefix + progressText(order) + extra
                if (order.minutesLate > 0) {
                    lateOptions(order, texts + "It's running about ${Fmt.minutes(order.minutesLate)} late. I'm sorry about that.", s)
                } else {
                    BotResult(
                        BotReply(Intent.WhereIsOrder, texts, card = orderCard(order), quickReplies = stageChips(order), answered = true),
                        s.copy(awaiting = Awaiting.Nothing),
                    )
                }
            }
            OrderState.Delivered -> BotResult(
                BotReply(
                    Intent.WhereIsOrder,
                    prefix + (
                        "Our records show it was ${if (order.pickup) "collected" else "delivered"} at ${Fmt.time(order.deliveredAt ?: order.placedAt)}" +
                            (if (order.pickup) "." else " to ${order.address}.") + " Did you get it?"
                        ),
                    card = orderCard(order),
                    quickReplies = listOf(qr(Action.Helped, "Yes, I got it"), pick(Intent.NeverArrived), pick(Intent.MissingItem), pick(Intent.FoodQuality)),
                    answered = true,
                ),
                s.copy(awaiting = Awaiting.Nothing),
            )
            OrderState.Cancelled -> cancelledOrder(order, s, prefix)
        }
    }

    private fun progressText(order: BotOrder): String {
        val left = Fmt.minutes(order.minutesLeft ?: 1)
        return when (order.state) {
            OrderState.Received -> if (order.pickup) {
                "${order.restaurantName} has your order and will start on it shortly. It should be ready for pick-up in about $left."
            } else {
                "${order.restaurantName} has your order and will start on it shortly. It should reach you in about $left."
            }
            OrderState.Preparing -> "${order.restaurantName} is preparing your food now. About $left to go."
            OrderState.OnTheWay -> if (order.pickup) {
                "Your order is ready for pick-up at ${order.restaurantName}, ${order.restaurantArea}."
            } else {
                "${order.driverName ?: "Your driver"} is on the way with your order, about $left out."
            }
            else -> ""
        }
    }

    private fun lateOptions(order: BotOrder, texts: List<String>, state: ConversationState): BotResult = BotResult(
        BotReply(
            Intent.Late,
            texts + "Because it's late, I can cancel it free of charge, or you can keep waiting.",
            card = orderCard(order),
            quickReplies = listOf(qr(Action.KeepWaiting, "I'll keep waiting"), qr(Action.ConfirmCancel, "Cancel it (free)"), human()),
            answered = true,
        ),
        state.copy(awaiting = Awaiting.LateChoice, lastProblem = Intent.Late),
    )

    private fun late(order: BotOrder, state: ConversationState, ctx: BotContext, text: String): BotResult {
        if (order.state.inProgress && order.minutesLate > 0) return whereIsOrder(order, state, ctx, emptyList())
        if (order.state.inProgress) {
            return BotResult(
                BotReply(
                    Intent.Late,
                    listOf(progressText(order), "It's still within the promised time. If it goes past that, I can cancel it free of charge."),
                    card = orderCard(order), quickReplies = stageChips(order), answered = true,
                ),
                state.copy(orderId = order.id, awaiting = Awaiting.Nothing),
            )
        }
        if (order.state == OrderState.Delivered && order.deliveryFee > 0 && has(text, rx("""\b(refund|money back|compensat\w*|credit|voucher)\b"""))) {
            val e = esc(Guidelines.late, order, "The customer reports that order ${order.id} arrived late and asks for the ${Fmt.money(order.deliveryFee)} delivery fee back.",
                RefundSpec(order.deliveryFee, "Late delivery: delivery fee"))
            return refundFlow(e, order, state.copy(orderId = order.id), ctx,
                listOf("Sorry it took so long. A late delivery can get its delivery fee back, so I'll ask for that."))
        }
        if (order.state == OrderState.Delivered) {
            return escalate(
                Intent.Late,
                listOf("Sorry it took so long. I've logged the late delivery so our logistics team can look at what held it up."),
                esc(Guidelines.late, order, "The customer reports that order ${order.id} arrived late."),
                state.copy(orderId = order.id), ctx, listOf(pick(Intent.FoodQuality)),
            )
        }
        return cancelledOrder(order, state, emptyList())
    }

    private fun neverArrived(order: BotOrder, state: ConversationState, ctx: BotContext, text: String): BotResult {
        val s = state.copy(orderId = order.id)
        if (order.state == OrderState.Delivered) {
            // The customer already knows it went to someone else: don't ask them to go and look.
            if (has(text, rx("""\b(someone else|wrong house|wrong address|wrong person|wrong place|wrong door|neighbour|neighbor|gave (it|my food|my order|the food))\b"""))) {
                return notReceived(order, s.copy(awaiting = Awaiting.Nothing), ctx, true)
            }
            return BotResult(
                BotReply(
                    Intent.NeverArrived,
                    listOf(
                        "I'm sorry. It's marked as ${if (order.pickup) "collected" else "delivered"} at ${Fmt.time(order.deliveredAt ?: order.placedAt)}" +
                            (if (order.pickup) "." else " to ${order.address}."),
                        "Before I log it, please check your door, reception or security gate, and ask your neighbours. Drivers sometimes leave orders there.",
                    ),
                    quickReplies = listOf(qr(Action.CheckedEverywhere, "I've checked, it's not there"), qr(Action.FoundIt, "I found it")),
                ),
                s.copy(awaiting = Awaiting.DeliveryCheck),
            )
        }
        if (order.state == OrderState.Cancelled) return cancelledOrder(order, s, emptyList())
        return whereIsOrder(order, s, ctx, listOf("It hasn't been delivered yet. It's still on its way:"))
    }

    private fun notReceived(order: BotOrder, state: ConversationState, ctx: BotContext, wrongPerson: Boolean = false): BotResult = escalate(
        Intent.NeverArrived,
        listOf(
            if (wrongPerson) "I'm sorry, that shouldn't happen. I've opened a \"delivered to the wrong place\" case for our logistics team. They'll check the driver's GPS and drop-off."
            else "Thank you for checking. I've opened a \"delivered but not received\" case for our logistics team. They'll check the driver's GPS and drop-off.",
            "Because you pay by ${order.pay.label} on delivery, you shouldn't have been charged for food you never got. If money did leave your account, tap \"I was charged\".",
        ),
        esc(
            Guidelines.markedDelivered, order,
            "Order ${order.id} from ${order.restaurantName} is marked delivered at ${Fmt.time(order.deliveredAt ?: order.placedAt)} " +
                "to ${order.address}, but the customer ${if (wrongPerson) "says it went to the wrong person or place" else "checked and never received it"}.",
        ),
        state, ctx,
        listOf(qr(Action.WasCharged, "I was charged")),
    )

    private fun cancelledOrder(order: BotOrder, state: ConversationState, prefix: List<String>, why: Boolean = false): BotResult {
        val texts = prefix.toMutableList()
        texts += "Your order from ${order.restaurantName} was cancelled. You won't be charged, because you pay on delivery."
        if (why) texts += "Orders are cancelled when you ask us to, when the restaurant can't make your food, or when no driver is available. If you didn't ask for this, a person can look into it for you."
        return BotResult(
            BotReply(Intent.WhereIsOrder, texts, card = orderCard(order), quickReplies = listOf(pick(Intent.PaymentProblem), pick(Intent.ComplaintStatus)), answered = true),
            state.copy(orderId = order.id, awaiting = Awaiting.Nothing),
        )
    }

    // --- Missing items ------------------------------------------------------------------------

    private fun missingItem(order: BotOrder, text: String, state: ConversationState, ctx: BotContext): BotResult {
        if (order.state != OrderState.Delivered) return notArrivedYet(Intent.MissingItem, order, state, ctx)
        val s = state.copy(orderId = order.id)
        val tamper = has(text, rx("""\b(ate|eaten|eating|tampered|seal was broken|seal broken|broken seal|already opened|was opened|had been opened|bag was open|opened before)\b"""))
        val items = missingItems(text, order)
        if (items.isNotEmpty()) return missingFor(order, items, s, tamper)
        // "my order is missing" means the whole order, which is a delivery problem
        if (has(text, rx("""\b(my|the|whole|entire|all my) (order|food|meal|delivery)( is| was| has gone)? missing\b""")) &&
            !has(text, rx("""\b(half|part|some|most|bits|parts|pieces)\b"""))
        ) return neverArrived(order, s, ctx, text)
        return BotResult(
            BotReply(
                Intent.MissingItem,
                listOf((if (tamper) "I'm sorry, an opened bag is serious and I'll flag it too. " else "Sorry about that. ") +
                    "Which item was missing? If more than one, you can type them, e.g. \"the coke and the chips\"."),
                quickReplies = itemChips(order) + qr(Action.ChooseItem(-1), "Everything"),
            ),
            s.copy(awaiting = Awaiting.MissingWhich(tamper)),
        )
    }

    private fun missingItems(text: String, order: BotOrder): List<Want> {
        val idx = findItems(text, order)
        if (idx.isEmpty()) return emptyList()
        if (idx.size == 1) return listOf(Want(idx[0], shortfall(text, order.lines[idx[0]])))
        return idx.map { Want(it, order.lines[it].qty) }
    }

    /** "I only got 1 kota but ordered 2" -> 1 missing. */
    private fun shortfall(text: String, line: BotLine): Int? {
        val n = TextNormaliser.normalise(text)
        val num = "(\\d+|one|two|three|four|five|six)"
        fun value(w: String): Double = if (w.any { it.isDigit() }) w.toDouble() else numWords.getValue(w).toDouble()
        var m = Regex("(?:got|received|came with|there was|there were|only) (?:only )?$num\\b").find(n)
        val got = m?.let { value(it.groupValues[1]) }
        m = Regex("(?:ordered|asked for|paid for|instead of|out of) $num\\b").find(n)
        val ordered = m?.let { value(it.groupValues[1]) } ?: line.qty.toDouble()
        if (got != null && ordered > got) return min(ordered - got, line.qty.toDouble()).toInt()
        m = Regex("$num (?:\\w+ )?(?:is |are |was |were )?missing").find(n) ?: Regex("missing $num\\b").find(n)
        if (m != null) return min(max(value(m.groupValues[1]), 1.0), line.qty.toDouble()).toInt()
        return null
    }

    private fun missingFor(order: BotOrder, items: List<Want>, state: ConversationState, tamper: Boolean): BotResult {
        if (items.size == 1) {
            val it = items[0]
            val line = order.lines[it.index]
            if (it.qty == null && line.qty > 1) return chooseMissing(order, it.index, state, tamper)
            return missingAction(order, listOf(MissingPick(it.index, it.qty?.takeIf { q -> q != 0 } ?: line.qty)), state, tamper)
        }
        return missingAction(order, items.map { MissingPick(it.index, it.qty ?: 0) }, state, tamper)
    }

    private fun chooseMissing(order: BotOrder, index: Int, state: ConversationState, tamper: Boolean = false): BotResult {
        val line = order.lines[index]
        if (line.qty > 1) {
            val chips = (1..min(line.qty, 5)).map { qr(Action.ChooseQty(it), "$it") }
            return BotResult(
                BotReply(Intent.MissingItem, listOf("You ordered ${line.qty} × ${line.name}. How many were missing?"), quickReplies = chips),
                state.copy(awaiting = Awaiting.MissingHowMany(index, tamper)),
            )
        }
        return missingAction(order, listOf(MissingPick(index, 1)), state, tamper)
    }

    private fun missingAction(order: BotOrder, items: List<MissingPick>, state: ConversationState, tamper: Boolean): BotResult {
        val list = items.map { val line = order.lines[it.index]; MissingPick(it.index, min(max(it.qty, 1), line.qty)) }
        val amount = list.sumOf { order.lines[it.index].unit * it.qty }
        val names = list.map {
            val line = order.lines[it.index]
            if (it.qty == line.qty) (if (line.qty == 1) line.name else "${line.qty} × ${line.name}") else "${it.qty} of your ${line.qty} × ${line.name}"
        }
        val texts = mutableListOf("Sorry, ${if (list.size == 1 && names[0].contains(" of your ")) names[0] else "your " + listJoin(names)} didn't arrive.")
        if (tamper) texts += "Food that's been opened or eaten from is serious, so I'll also flag it to our logistics team."
        texts += "I can ask for a refund of ${Fmt.money(amount)}, or just let ${order.restaurantName} know so they can fix their packing."
        return BotResult(
            BotReply(
                Intent.MissingItem, texts,
                quickReplies = listOf(qr(Action.ItemRefund, "Refund ${Fmt.money(amount)}"), qr(Action.TellRestaurant, if (tamper) "Just report it" else "Just tell the restaurant")),
            ),
            state.copy(awaiting = Awaiting.MissingAction(list, tamper)),
        )
    }

    // --- Wrong order ----------------------------------------------------------------------------

    private fun wrongOrder(order: BotOrder, text: String, state: ConversationState, ctx: BotContext): BotResult {
        if (order.state != OrderState.Delivered) return notArrivedYet(Intent.WrongOrder, order, state, ctx)
        val s = state.copy(orderId = order.id)
        if (has(text, rx("""\b(someone elses?|somebody elses?|not my (food|order|meal)|not mine|whole order|entire order|everything was wrong|all wrong)\b"""))) {
            return onChoice(Action.WholeOrderWrong, s, ctx)
        }
        val idx = wrongItemsFromText(text, order)
        if (idx.isNotEmpty()) return wrongItems(order, idx, s)
        return BotResult(
            BotReply(
                Intent.WrongOrder, listOf("Sorry about that. Was the whole order wrong, or only some items?"),
                quickReplies = listOf(qr(Action.WholeOrderWrong, "The whole order"), qr(Action.SomeItemsWrong, "Only some items")),
            ),
            s.copy(awaiting = Awaiting.WrongScope),
        )
    }

    private fun wrongItemsFromText(text: String, order: BotOrder): List<Int> {
        val n = TextNormaliser.normalise(text)
        rx("""instead of (.+)$""").find(n)?.let { m -> findItems(m.groupValues[1], order).let { if (it.isNotEmpty()) return it } }
        rx("""(?:ordered|asked for|wanted) (.+?) (?:but|and) (?:i )?(?:got|received|they sent|it was)""").find(n)?.let { m ->
            findItems(m.groupValues[1], order).let { if (it.isNotEmpty()) return it }
        }
        return findItems(text, order)
    }

    private fun wrongItems(order: BotOrder, idx: List<Int>, state: ConversationState): BotResult {
        val names = idx.map { order.lines[it].name }
        val amount = idx.sumOf { order.lines[it].total }
        return BotResult(
            BotReply(
                Intent.WrongOrder,
                listOf(
                    "Sorry, you were sent the wrong ${listJoin(names)}. If you have allergies, please don't eat food that isn't yours.",
                    "I can ask for a refund of ${Fmt.money(amount)}, or let ${order.restaurantName} know.",
                ),
                quickReplies = listOf(qr(Action.ItemRefund, "Refund ${Fmt.money(amount)}"), qr(Action.TellRestaurant, "Just tell the restaurant")),
            ),
            state.copy(awaiting = Awaiting.WrongAction(idx)),
        )
    }

    // --- Food quality and safety ----------------------------------------------------------------

    private fun foodQuality(order: BotOrder, text: String, state: ConversationState, ctx: BotContext): BotResult {
        if (mentionsSafety(text)) return foodSafety(order, text, state, ctx)
        if (order.state != OrderState.Delivered) return notArrivedYet(Intent.FoodQuality, order, state, ctx)
        val s = state.copy(orderId = order.id)
        val kind = qualityKind(text)
        val idx = findItems(text, order)
        val everything = has(text, rx("""\b(everything|all of it|whole order|the order|my order)\b"""))
        val food = has(text, rx("""\b(all the food|whole meal|the food|my food|the meal|my meal|my kos|die kos)\b"""))
        val scope = when {
            idx.isNotEmpty() -> idx
            everything -> order.lines.indices.toList()
            food -> foodLines(order)
            else -> emptyList()
        }
        if (kind != null) return chooseQuality(order, kind, s, scope)
        return BotResult(
            BotReply(
                Intent.FoodQuality, listOf("I'm sorry, that's not the standard we expect. What was wrong with it?"),
                quickReplies = QualityKind.entries.map { qr(Action.ChooseQuality(it), it.label) },
            ),
            s.copy(awaiting = Awaiting.QualityWhat(scope)),
        )
    }

    /** Every line that isn't a drink (or every line, if they're all drinks). */
    private fun foodLines(order: BotOrder): List<Int> {
        val food = order.lines.indices.filter { !drink.containsMatchIn(TextNormaliser.normalise(order.lines[it].name)) }
        return food.ifEmpty { order.lines.indices.toList() }
    }

    private fun chooseQuality(order: BotOrder, kind: QualityKind, state: ConversationState, indexes: List<Int>): BotResult {
        if (indexes.isNotEmpty() || order.lines.size == 1) return qualityAction(order, kind, indexes.ifEmpty { listOf(0) }, state)
        val warn = when (kind) {
            QualityKind.Undercooked -> "Please don't eat it: undercooked food can make you ill. "
            QualityKind.TastesOff -> "If it smells or tastes off, please don't eat it. "
            else -> ""
        }
        return BotResult(
            BotReply(Intent.FoodQuality, listOf("${warn}Which item was affected?"), quickReplies = itemChips(order) + qr(Action.ChooseItem(-1), "Everything")),
            state.copy(awaiting = Awaiting.QualityWhich(kind)),
        )
    }

    private fun qualityAction(order: BotOrder, kind: QualityKind, idx: List<Int>, state: ConversationState): BotResult {
        val all = idx.size > 1 && (idx.size == order.lines.size || idx.joinToString(",") == foodLines(order).joinToString(","))
        val names = if (all) (if (idx.size == order.lines.size) "order" else "food") else listJoin(idx.map { order.lines[it].name })
        val many = (!all && idx.size > 1) || all
        val first = when (kind) {
            QualityKind.Cold -> "Sorry your $names arrived cold."
            QualityKind.Spilled -> "Sorry your $names arrived spilled or damaged."
            QualityKind.Undercooked -> "Please don't eat your $names: undercooked food can make you ill."
            QualityKind.TastesOff -> "Sorry about your $names. If ${if (many) "they smell" else "it smells"} or taste${if (many) "" else "s"} off, please don't eat ${if (many) "them" else "it"}."
        }
        val amount = idx.sumOf { order.lines[it].total }
        return BotResult(
            BotReply(
                Intent.FoodQuality,
                listOf(first, "I can ask for a refund of ${Fmt.money(amount)}, or pass your feedback to ${order.restaurantName}."),
                quickReplies = listOf(qr(Action.ItemRefund, "Refund ${Fmt.money(amount)}"), qr(Action.TellRestaurant, "Just tell the restaurant")),
            ),
            state.copy(awaiting = Awaiting.QualityAction(kind, idx)),
        )
    }

    private fun foodSafety(order: BotOrder?, text: String, state: ConversationState, ctx: BotContext): BotResult {
        val o = order?.takeIf { it.state == OrderState.Delivered }
        return escalate(
            Intent.FoodSafety,
            listOf(
                "I'm really sorry. Your health comes first: if you feel unwell or have signs of an allergic reaction, please get medical help now (10177 for an ambulance, or 112 from a cellphone).",
                "Please keep the food and packaging and take a photo. I've flagged this as Critical, so a person will contact you immediately.",
            ),
            Escalation(Guidelines.safety.category, Guidelines.safety.subcategory, Severity.Critical, o?.id, o?.restaurantName,
                withWords("Possible food safety problem" + (o?.let { " with order ${it.id} from ${it.restaurantName}" } ?: "") + ".", text)),
            state.copy(orderId = o?.id ?: state.orderId, lastProblem = Intent.FoodSafety), ctx,
        )
    }

    private fun notArrivedYet(intent: Intent, order: BotOrder, state: ConversationState, ctx: BotContext): BotResult {
        if (order.state == OrderState.Cancelled) return cancelledOrder(order, state, emptyList())
        val r = whereIsOrder(order, state, ctx, listOf("Your current order hasn't arrived yet, so let's check where it is first:"))
        val earlier = ctx.orders.filter { !it.active }
        if (earlier.isEmpty()) return r
        return BotResult(
            r.reply.copy(quickReplies = r.reply.quickReplies + qr(Action.Pick(Intent.OrderDetails), "It's about an earlier order")),
            r.state.copy(awaiting = Awaiting.OrderChoice(intent, ""), lastProblem = intent),
        )
    }

    // --- Refunds and payments -------------------------------------------------------------------

    private fun refundStatus(order: BotOrder?, state: ConversationState, ctx: BotContext, text: String): BotResult {
        val withRefund = ctx.complaints.filter { it.refund != null }
        val c = withRefund.firstOrNull { state.orderId != null && it.orderId == state.orderId } ?: withRefund.maxByOrNull { it.createdAt }
        if (c == null) {
            val texts = mutableListOf("I can't see a refund request on your account.")
            if (has(text, rx("""\b(how long|how many days|when|take|timing|how quickly)\b"""))) {
                texts[0] = "Once a refund is approved, it goes back to your card within 3 to 5 business days (depending on your bank), by bank transfer within 3 to 5 business days, or as eats credit straight away. " +
                    "I can't see a refund request on your account right now."
            }
            val chips: List<QuickReply>
            if (order != null && order.state == OrderState.Delivered) {
                texts += "Did something go wrong with your order from ${order.restaurantName}? Refunds are for missing, wrong, damaged or undelivered food."
                chips = listOf(pick(Intent.MissingItem), pick(Intent.WrongOrder), pick(Intent.FoodQuality), pick(Intent.NeverArrived))
            } else {
                texts += "Because you pay on delivery, cancelled or undelivered orders aren't charged, so they don't need a refund."
                chips = listOf(pick(Intent.PaymentProblem), menu())
            }
            return BotResult(BotReply(Intent.RefundStatus, texts, quickReplies = chips, answered = true), state.copy(awaiting = Awaiting.Nothing))
        }
        val refund = c.refund!!
        val card = InfoCard(
            "Refund ${c.reference}",
            listOf("Amount" to Fmt.money(refund.amount), "Paid" to capFirst(refund.method.label), "Status" to refund.status.label, "Complaint" to c.status.label),
        )
        val s = state.copy(orderId = c.orderId ?: state.orderId, awaiting = Awaiting.Nothing)
        return when (refund.status) {
            RefundStatus.Requested -> if (c.overdue(ctx.now)) {
                BotResult(
                    BotReply(
                        Intent.RefundStatus,
                        listOf(
                            "Your refund of ${Fmt.money(refund.amount)} (${c.reference}) should have been reviewed by ${Fmt.due(c.dueAt, ctx.now)}, and it hasn't been. I'm sorry.",
                            "I've raised it to a supervisor in Finance as High priority. They'll get back to you within 24 hours.",
                        ),
                        card = card, chaseComplaintId = c.id, quickReplies = listOf(pick(Intent.ComplaintStatus)), answered = true,
                    ),
                    s,
                )
            } else {
                BotResult(
                    BotReply(
                        Intent.RefundStatus,
                        listOf("Your refund of ${Fmt.money(refund.amount)} (${c.reference}) is waiting for a support agent to review it. You'll hear back by ${Fmt.due(c.dueAt, ctx.now)}. Once approved, it's paid ${refund.method.label} and arrives ${refund.method.timing}."),
                        card = card, quickReplies = listOf(pick(Intent.ComplaintStatus)), answered = true,
                    ),
                    s,
                )
            }
            RefundStatus.Approved -> BotResult(
                BotReply(Intent.RefundStatus, listOf("Good news: your refund of ${Fmt.money(refund.amount)} has been approved. It's paid ${refund.method.label} and arrives ${refund.method.timing}."), card = card, answered = true),
                s,
            )
            RefundStatus.Paid -> BotResult(
                BotReply(
                    Intent.RefundStatus,
                    listOf("Your refund of ${Fmt.money(refund.amount)} has been paid ${refund.method.label}." +
                        if (refund.method == RefundMethod.EatsCredit) " It's ready to use on your next order." else " Depending on your bank it can take up to 5 business days to show."),
                    card = card, answered = true,
                ),
                s,
            )
            RefundStatus.Declined -> BotResult(
                BotReply(
                    Intent.RefundStatus,
                    listOf("Your refund request of ${Fmt.money(refund.amount)} was declined. If you think that's wrong, I can ask a supervisor to look at it again."),
                    card = card, quickReplies = listOf(qr(Action.ChaseComplaint, "Ask a supervisor")), answered = true,
                ),
                s.copy(awaiting = Awaiting.Nothing),
            )
        }
    }

    private fun paymentProblem(text: String, state: ConversationState, ctx: BotContext): BotResult {
        val kind = chargeKind(text)
        if (kind != null) return chargeAnswer(kind, state, ctx)
        return BotResult(
            BotReply(Intent.PaymentProblem, listOf("Let's sort that out. What do you see on your statement?"),
                quickReplies = ChargeKind.entries.map { qr(Action.ChooseCharge(it), it.label) }),
            state.copy(awaiting = Awaiting.ChargeWhat, lastProblem = Intent.PaymentProblem),
        )
    }

    private fun chargeAnswer(kind: ChargeKind, state: ConversationState, ctx: BotContext): BotResult {
        val order = orderFor(state, ctx)
        val s = state.copy(lastProblem = Intent.PaymentProblem)
        if (kind == ChargeKind.Pending) {
            return BotResult(
                BotReply(
                    Intent.PaymentProblem,
                    listOf(
                        "That's most likely a card authorisation hold. When a card is tapped, the bank can reserve the amount for a few days before it settles. It isn't a second charge, and the bank releases it, usually within 3 to 7 business days.",
                        "If it hasn't dropped off after 7 days, tell me and I'll log it for our payments team.",
                    ),
                    quickReplies = listOf(qr(Action.ChooseCharge(ChargeKind.Twice), "It's a completed charge")), answered = true,
                ),
                s.copy(awaiting = Awaiting.Nothing),
            )
        }
        val g = if (kind == ChargeKind.Unknown) Guidelines.unauthorised else Guidelines.payment
        val (sub, sev) = when (kind) {
            ChargeKind.Twice -> "Double Billing" to Severity.Critical
            ChargeKind.Unknown -> "Unauthorised Deduction" to Severity.Critical
            else -> "Incorrect Amount Charged" to Severity.High
        }
        val texts = listOf(
            "I've flagged this for our payments team" + if (sev == Severity.Critical) " as Critical, so it's looked at immediately." else ".",
            "Please don't share card numbers or PINs here; we will never ask for your PIN." +
                if (kind == ChargeKind.Unknown) " If you think someone else used your card, call your bank to block it." else "",
        )
        return escalate(
            Intent.PaymentProblem, texts,
            Escalation(g.category, sub, sev, order?.id, order?.restaurantName,
                "Payment problem (${chargeWords(kind)})" + (order?.let { " on order ${it.id}, total ${Fmt.money(it.total)}, paid by ${it.pay.label}" } ?: "") + "."),
            s, ctx,
        )
    }

    // --- Support that did not help --------------------------------------------------------------

    private fun poorSupport(text: String, state: ConversationState, ctx: BotContext): BotResult {
        val open = ctx.complaints.filter { it.status.isOpen }.sortedByDescending { it.createdAt }
        if (has(text, rx("""\b(manager|supervisor|escalate|someone senior|higher up)\b"""))) {
            open.firstOrNull()?.let { return chase(it, state, ctx) }
            val o = orderFor(state, ctx)
            return escalate(
                Intent.PoorSupport,
                listOf("Of course. I've asked a supervisor to contact you. They'll get back to you within 24 hours, or you can call an agent now."),
                Escalation(Category.CustomerService, "Supervisor Requested", Severity.High, o?.id, o?.restaurantName, withWords("The customer asked for a supervisor.", text)),
                state.copy(lastProblem = Intent.PoorSupport), ctx, listOf(qr(Action.Call, "Call an agent")),
            )
        }
        open.firstOrNull { it.overdue(ctx.now) }?.let { return chase(it, state, ctx) }
        val latest = open.firstOrNull()
        if (latest != null) {
            return BotResult(
                BotReply(
                    Intent.PoorSupport,
                    listOf(
                        "I'm sorry for the wait. Your complaint ${latest.reference} (${latest.subcategory}) is ${latest.status.label.lowercase()}, and a person will respond by ${Fmt.due(latest.dueAt, ctx.now)}.",
                        "If you can't wait, I can raise it to a supervisor now, or you can call an agent.",
                    ),
                    quickReplies = listOf(qr(Action.ChaseComplaint, "Raise it to a supervisor"), qr(Action.Call, "Call an agent")), answered = true,
                ),
                state.copy(awaiting = Awaiting.Nothing, lastProblem = Intent.PoorSupport),
            )
        }
        val order = orderFor(state, ctx)
        return escalate(
            Intent.PoorSupport,
            listOf("I'm sorry you've had trouble getting help. I've logged this for a supervisor as High priority."),
            Escalation(Guidelines.poorSupport.category, Guidelines.poorSupport.subcategory, Guidelines.poorSupport.severity, order?.id, order?.restaurantName,
                withWords("The customer says support has not helped them.", text)),
            state.copy(lastProblem = Intent.PoorSupport), ctx, listOf(qr(Action.Call, "Call an agent")),
        )
    }

    private fun chase(c: Complaint, state: ConversationState, ctx: BotContext): BotResult {
        val text = when {
            c.refund != null && c.refund.status == RefundStatus.Declined && !c.status.isOpen ->
                "I've asked a supervisor to look at your refund for ${c.reference} again. They'll contact you within 24 hours."
            c.escalated && !c.overdue(ctx.now) ->
                "${c.reference} is already with a supervisor, and they'll contact you by ${Fmt.due(c.dueAt, ctx.now)}. I'm sorry for the wait. You can also call an agent now."
            else -> "I'm sorry nobody has got back to you. I've raised ${c.reference} to a supervisor as High priority, and they'll contact you within 24 hours."
        }
        return BotResult(
            BotReply(
                Intent.PoorSupport, listOf(text),
                chaseComplaintId = if (c.escalated && !c.overdue(ctx.now) && c.status.isOpen) null else c.id,
                quickReplies = listOf(pick(Intent.ComplaintStatus), qr(Action.Call, "Call an agent")), answered = true,
            ),
            state.copy(awaiting = Awaiting.Nothing),
        )
    }

    // --- Changes to an order --------------------------------------------------------------------

    private fun freeCancel(order: BotOrder) =
        order.state == OrderState.Received || (order.state.inProgress && order.minutesLate >= Guidelines.LATE_FREE_CANCEL_MINUTES)

    private fun cancelOrder(order: BotOrder, state: ConversationState, ctx: BotContext): BotResult {
        val s = state.copy(orderId = order.id, lastProblem = Intent.CancelOrder)
        if (!order.active) {
            return simple(Intent.CancelOrder, "You don't have an order in progress to cancel. Your last order from ${order.restaurantName} has already been ${order.state.label.lowercase()}.", s, stageChips(order))
        }
        if (freeCancel(order)) {
            return BotResult(
                BotReply(
                    Intent.CancelOrder,
                    listOf(
                        if (order.state == OrderState.Received) "${order.restaurantName} hasn't started on it yet, so I can cancel it now at no charge."
                        else "Your order is running late, so I can cancel it free of charge.",
                        "Shall I cancel it?",
                    ),
                    card = orderCard(order),
                    quickReplies = listOf(qr(Action.ConfirmCancel, "Yes, cancel it"), qr(Action.KeepOrder, "No, keep it")),
                ),
                s.copy(awaiting = Awaiting.CancelConfirm),
            )
        }
        if (order.state == OrderState.Preparing &&
            ctx.complaints.any { it.orderId == order.id && it.status.isOpen && it.subcategory == Guidelines.cancel.subcategory }
        ) {
            return simple(Intent.CancelOrder, "Your cancellation request is already with ${order.restaurantName} and our support team. They'll confirm shortly; I'll post here if your order changes.",
                s, listOf(pick(Intent.ComplaintStatus), human()))
        }
        if (order.state == OrderState.Preparing) {
            return BotResult(
                BotReply(
                    Intent.CancelOrder,
                    listOf("${order.restaurantName} is already preparing your food, so I can only send them a cancellation request. They may charge for food that's already made. Send the request?"),
                    quickReplies = listOf(qr(Action.ConfirmCancel, "Yes, send the request"), qr(Action.KeepOrder, "No, keep it")),
                ),
                s.copy(awaiting = Awaiting.CancelConfirm),
            )
        }
        if (order.state == OrderState.OnTheWay && state.cancelAsked && !order.pickup) {
            return simple(Intent.CancelOrder, "I understand you don't want it. Because it's already on its way I can't cancel it myself, but a person can look at it with you. " +
                "You pay on delivery, so nothing has been charged yet.", s, listOf(human(), qr(Action.Call, "Call an agent")))
        }
        if (order.state == OrderState.OnTheWay) {
            return simple(
                Intent.CancelOrder,
                if (order.pickup) "Your order is ready and waiting for you, so it can't be cancelled now."
                else "${order.driverName ?: "Your driver"} is already on the way with your order, so it can't be cancelled now. It'll be with you in about ${Fmt.minutes(order.minutesLeft ?: 1)}.",
                s.copy(cancelAsked = true), listOf(pick(Intent.ContactDriver), human()),
            )
        }
        if (order.state == OrderState.Delivered) {
            return simple(Intent.CancelOrder, "This order has already been delivered, so it can't be cancelled. If something was wrong with it, tell me and I'll help put it right.", s, stageChips(order))
        }
        return cancelledOrder(order, s, emptyList())
    }

    private fun confirmCancel(order: BotOrder, state: ConversationState, ctx: BotContext): BotResult {
        if (freeCancel(order)) {
            val texts = listOf("Done. Your order from ${order.restaurantName} is cancelled, and you won't be charged.")
            val action = OrderAction.Cancel(order.id)
            if (order.minutesLate > 0) {
                val r = escalate(
                    Intent.CancelOrder, texts + "I've also logged the delay so our logistics team can follow up.",
                    esc(Guidelines.late, order, "Order ${order.id} was cancelled by the customer after running ${order.minutesLate} min late."), state, ctx,
                )
                return r.copy(reply = r.reply.copy(orderAction = action))
            }
            return BotResult(BotReply(Intent.CancelOrder, texts, orderAction = action, quickReplies = listOf(menu()), answered = true), state.copy(awaiting = Awaiting.Nothing))
        }
        if (order.state == OrderState.Preparing) {
            return escalate(
                Intent.CancelOrder,
                listOf("Request sent. ${order.restaurantName} and our support team will confirm shortly whether it can still be cancelled. I can't cancel orders that are being made myself."),
                esc(Guidelines.cancel, order, "The customer asks to cancel order ${order.id} while ${order.restaurantName} is preparing it."), state, ctx,
            )
        }
        return cancelOrder(order, state, ctx)
    }

    private fun changeAddress(order: BotOrder, state: ConversationState, text: String, ctx: BotContext): BotResult {
        val s = state.copy(orderId = order.id, lastProblem = Intent.ChangeAddress)
        if (!order.active || !order.state.inProgress) {
            return simple(Intent.ChangeAddress, "This order is already finished, so its address can't be changed. For your next order, update your address under Profile or at checkout.", s, emptyList())
        }
        if (order.pickup) return simple(Intent.ChangeAddress, "This is a pick-up order, so there's no delivery address to change.", s, stageChips(order))
        val inline = addressIn(text)
        if (order.state == OrderState.OnTheWay) {
            return simple(
                Intent.ChangeAddress,
                "${order.driverName ?: "Your driver"} is already on the way to ${order.address}, so I can't change it from here. " +
                    if (inline != null) "I can ask them to call you so you can direct them to $inline, or a person can help redirect it."
                    else "I can ask them to call you, or a person can help redirect it.",
                s, listOf(pick(Intent.ContactDriver), human()),
            )
        }
        if (inline != null) return newAddress(order, inline, s, ctx)
        return BotResult(
            BotReply(
                Intent.ChangeAddress,
                listOf("Your order is going to ${order.address}. Type the full new address and I'll update it before your driver sets off."),
                quickReplies = listOf(qr(Action.KeepOrder, "Keep this address")),
            ),
            s.copy(awaiting = Awaiting.NewAddress),
        )
    }

    /** "change my address to 5 Beach Road, Gonubie" -> "5 Beach Road, Gonubie" (needs a street number). */
    private fun addressIn(text: String): String? {
        val parts = text.trim().split(Regex("""\b(?:to|is|address|at)\b:?""", RegexOption.IGNORE_CASE))
        for (i in parts.size - 1 downTo 1) {
            val p = parts[i]
                .replace(Regex("""\b(instead|please|thanks|thank you|now)\b\.?""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""[.!?]+$"""), "")
                .replace(Regex("""^[\s:,-]+|[\s,]+$"""), "")
            if (Regex("""\d""").containsMatchIn(p) && looksLikeAddress(p)) return p.take(MAX_ADDRESS)
        }
        return null
    }

    private fun newAddress(order: BotOrder, text: String, state: ConversationState, ctx: BotContext): BotResult {
        if (!order.active || order.state == OrderState.OnTheWay || !order.state.inProgress) return changeAddress(order, state, "", ctx)
        val address = text.trim()
            .replaceFirst(Regex("""^(its|it's|it is|the address is|address is|my address is|deliver to|send it to)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[.!]+$"""), "")
            .take(MAX_ADDRESS)
        if (!looksLikeAddress(address)) {
            return BotResult(
                BotReply(
                    Intent.ChangeAddress,
                    listOf("That doesn't look like a full address. Please include the street number, street name and suburb, e.g. 12 Oxford Street, Quigney."),
                    quickReplies = listOf(qr(Action.KeepOrder, "Keep the old address")),
                ),
                state.copy(awaiting = Awaiting.NewAddress),
            )
        }
        return BotResult(
            BotReply(
                Intent.ChangeAddress,
                listOf("Updated. Your order will now go to $address, and your driver will see the new address."),
                orderAction = OrderAction.ChangeAddress(order.id, address),
                quickReplies = listOf(pick(Intent.WhereIsOrder)), answered = true,
            ),
            state.copy(awaiting = Awaiting.Nothing),
        )
    }

    private fun modifyOrder(order: BotOrder, state: ConversationState): BotResult {
        val s = state.copy(orderId = order.id)
        if (order.active && order.state == OrderState.Received) {
            return simple(
                Intent.ModifyOrder,
                "Orders can't be changed once they're placed, but ${order.restaurantName} hasn't started yet. I can cancel it at no charge so you can order again with the right items.",
                s, listOf(pick(Intent.CancelOrder), qr(Action.KeepOrder, "No, keep it")),
            )
        }
        return simple(
            Intent.ModifyOrder,
            if (order.active && order.state.inProgress) "${order.restaurantName} is already working on your order, so it can't be changed. You're welcome to place a separate order for anything extra."
            else "That order is finished, so it can't be changed. You can order again from the Orders tab.",
            s, stageChips(order),
        )
    }

    private fun contactDriver(order: BotOrder, state: ConversationState, text: String): BotResult {
        val s = state.copy(orderId = order.id)
        val name = order.driverName ?: "Your driver"
        val riding = !order.pickup && order.active && order.state == OrderState.OnTheWay
        if (riding && has(text, rx("""\b(not answering|isnt answering|wont answer|doesnt answer|no answer|not picking up|cant reach|cant get hold|unreachable|phone off)\b"""))) {
            return simple(
                Intent.ContactDriver,
                "I'm sorry $name isn't answering. Drivers can't take calls while they're riding, so they'll usually call back when they stop. They're about ${Fmt.minutes(order.minutesLeft ?: 1)} away. " +
                    "If you still can't reach them, a person can contact them for you.",
                s, listOf(human(), pick(Intent.WhereIsOrder)), true,
            )
        }
        if (riding && has(text, rx("""\b(lost|cant find|cannot find|driving around|wrong way|circles)\b"""))) {
            return simple(
                Intent.ContactDriver,
                "I've asked $name to call you so you can guide them in. Please keep your phone nearby. They're about ${Fmt.minutes(order.minutesLeft ?: 1)} away.",
                s, listOf(pick(Intent.DeliveryInstructions), human()), true,
            )
        }
        val reply = when {
            order.pickup -> "This is a pick-up order, so there's no driver. Your food will be waiting at ${order.restaurantName}, ${order.restaurantArea}."
            order.active && order.state == OrderState.OnTheWay ->
                "Your driver is ${order.driverName ?: "on the way"}" + (order.driverVehicle?.let { " ($it)" } ?: "") +
                    ". I've sent them a message to call you on the number on your profile. They're about ${Fmt.minutes(order.minutesLeft ?: 1)} away."
            order.active && order.state.inProgress ->
                "A driver is assigned when your food is nearly ready. You'll see their name on the tracking screen, and I'll post an update here when they pick it up."
            order.state == OrderState.Delivered -> "${order.driverName ?: "Your driver"} delivered this order. If something went wrong with the delivery, tell me and I'll help."
            else -> "This order was cancelled, so no driver is assigned."
        }
        return simple(Intent.ContactDriver, reply, s, stageChips(order), true)
    }

    private fun driverIssue(order: BotOrder?, text: String, state: ConversationState, ctx: BotContext): BotResult {
        val t = TextNormaliser.normalise(text)
        val words = TextNormaliser.tokens(text)
        val money = words.any { it in setOf("money", "cash", "extra", "more", "paid", "pay", "demanded", "charged", "tip", "tips") }
        val unsafe = words.any { it in unsafeWords } || t.contains("cant breathe")
        val sub: String
        val sev: Severity
        val texts: List<String>
        when {
            unsafe -> {
                sub = "Driver Conduct (Safety)"; sev = Severity.High
                texts = listOf(
                    "I'm sorry, that's not okay. If you feel unsafe right now, please call the police on 10111.",
                    "I've reported the driver to our logistics team as High priority, and they'll follow it up with you.",
                )
            }
            money -> {
                sub = "Driver Asked For Extra Money"; sev = Severity.High
                texts = listOf(
                    "Drivers must never ask for extra money: the amount in the app is all you owe (tips are optional and set at checkout).",
                    "I've reported this to our logistics team as High priority. If you paid extra, tell the agent how much and they'll look at getting it back.",
                )
            }
            else -> {
                sub = Guidelines.driver.subcategory; sev = Guidelines.driver.severity
                texts = listOf("I'm sorry about your driver's behaviour. That's not the service we expect. I've reported it to our logistics team so they can follow it up with the driver.")
            }
        }
        return escalate(
            Intent.DriverIssue, texts,
            Escalation(Category.DriverConduct, sub, sev, order?.id, order?.restaurantName,
                withWords("Driver complaint" + (order?.driverName?.let { " about $it" } ?: "") + ".", text)),
            state.copy(lastProblem = Intent.DriverIssue), ctx,
        )
    }

    // --- Questions ------------------------------------------------------------------------------

    private fun orderDetails(order: BotOrder, state: ConversationState): BotResult = BotResult(
        BotReply(
            Intent.OrderDetails,
            listOf("Here's your ${if (order.active) "current" else "last"} order from ${order.restaurantName}, placed ${Fmt.dateTime(order.placedAt)}."),
            card = InfoCard(
                "Order ${order.id}",
                order.lines.map { "${it.qty}× ${it.name}" to Fmt.money(it.total) } + listOf(
                    "Total" to Fmt.money(order.total),
                    "Payment" to capFirst(order.pay.label) + if (order.pickup) " at pick-up" else " on delivery",
                    "Status" to order.state.label,
                ),
            ),
            quickReplies = stageChips(order), answered = true,
        ),
        state.copy(orderId = order.id, awaiting = Awaiting.Nothing),
    )

    private fun fees(order: BotOrder?, state: ConversationState): BotResult {
        val texts = mutableListOf(Guidelines.FEES)
        if (order != null && !order.pickup) {
            texts += "${order.restaurantName}'s delivery fee is ${Fmt.money(order.deliveryFee)}" +
                if (order.freeDeliveryOver > 0) ", and it's free on orders over ${Fmt.money(order.freeDeliveryOver)}." else "."
        }
        return BotResult(BotReply(Intent.Fees, texts, quickReplies = listOf(pick(Intent.Promo), menu()), answered = true), state.copy(awaiting = Awaiting.Nothing))
    }

    private fun deliveryInstructions(order: BotOrder?, state: ConversationState, text: String): BotResult {
        if (order != null && order.active && order.state.inProgress && !order.pickup) {
            val note = text.trim()
                .replaceFirst(Regex("""^(please |can you |could you )?(tell|ask) (the |my )?(driver|him|her|them) (to |that )?""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""[.!]+$"""), "")
            val isNote = !has(text, rx("""\b(how|where do|can i add|what are|what is)\b""")) && note.length >= 4 &&
                has(text, rx("""\b(code|gate|reception|door|flat|unit|block|complex|estate|buzz|intercom|guard|security|call|knock|ring|hoot|leave|park|dog|stairs|floor|house|blue|green|red|white|next to|opposite|behind)\b"""))
            if (isNote) {
                val who = if (order.state == OrderState.OnTheWay && order.driverName != null) order.driverName else "your driver"
                return simple(
                    Intent.DeliveryInstructions,
                    "Done. I've passed this note to $who: \"${note.take(160)}\"." + if (order.state == OrderState.OnTheWay) "" else " They'll see it when they collect your order.",
                    state.copy(orderId = order.id), listOf(pick(Intent.WhereIsOrder)), true,
                )
            }
            return simple(
                Intent.DeliveryInstructions,
                "Instructions are added at checkout, but your order is already placed. Tell me what the driver needs to know (like a gate code or where to leave it) and I'll pass it on.",
                state.copy(orderId = order.id), listOf(pick(Intent.ContactDriver)), true,
            )
        }
        return info(Intent.DeliveryInstructions, Guidelines.INSTRUCTIONS, state)
    }

    private fun promo(text: String, state: ConversationState): BotResult {
        if (has(text, rx("""\b(for the trouble|for my trouble|compensat\w*|make up for|goodwill|for the inconvenience|something for|credit for|voucher for|discount for)\b"""))) {
            return simple(
                Intent.Promo,
                "I'm sorry for the trouble. Credits for a bad experience are decided by a support agent, not by me. If something went wrong with your order, tell me what happened and I'll log it with your request, so the agent can look at both together.",
                state, listOf(pick(Intent.MissingItem), pick(Intent.FoodQuality), pick(Intent.Late), human()), true,
            )
        }
        if (has(text, rx("""\b(not working|doesnt work|isnt working|wont work|invalid|error|failed|rejected|not valid|expired|not accepted|didnt work)\b"""))) {
            return simple(
                Intent.Promo,
                "Sorry about that. Please check that the code is typed exactly (for example EATSFIRST), that you add it in the cart before checkout, and that it applies to you: EATSFIRST is for first orders only, and you can use one code per order. " +
                    "If it still won't work, a person can check it for you.",
                state, listOf(human(), menu()), true,
            )
        }
        return info(Intent.Promo, Guidelines.PROMO, state)
    }

    private fun tip(order: BotOrder?, state: ConversationState): BotResult {
        if (order != null && order.active && order.state.inProgress && !order.pickup) {
            return info(Intent.Tip, "Your tip was set at checkout and can't be changed once the order is placed. You're welcome to give your driver a cash tip at the door, and 100% of tips go to them.", state)
        }
        return info(Intent.Tip, Guidelines.TIP, state)
    }

    private fun payMethods(text: String, state: ConversationState): BotResult {
        if (has(text, rx("""\b(snapscan|zapper|eft|instant eft|ozow|apple pay|google pay|samsung pay|paypal|online|mobicred|payflex|crypto|bitcoin|voucher)\b"""))) {
            return info(Intent.PaymentMethods, "Not yet. eats takes cash or card when your food arrives (or when you collect it). Online payments like SnapScan, Zapper and EFT aren't available yet.", state)
        }
        return info(Intent.PaymentMethods, Guidelines.PAY, state)
    }

    private fun pickup(order: BotOrder?, state: ConversationState, text: String, ctx: BotContext): BotResult {
        val collecting = order != null && order.active && order.pickup && order.state.inProgress
        if (collecting && has(text, rx("""\b(cant find|cannot find|dont have|doesnt have|not ready|isnt ready|no order|never got|lost|wrong|closed|waiting|says|told me)\b"""))) {
            return escalate(
                Intent.Pickup,
                listOf(
                    "Sorry about that. Please show them your order number, ${order!!.id}, under the name ${ctx.firstName}.",
                    "I've also alerted ${order.restaurantName} and our support team, so a person will help you right away. You only pay once you have your food.",
                ),
                Escalation(Category.OrderChange, "Pick-up Problem", Severity.High, order.id, order.restaurantName,
                    withWords("The customer is at ${order.restaurantName} to collect order ${order.id} and there is a problem.", text)),
                state.copy(orderId = order.id), ctx, listOf(qr(Action.Call, "Call an agent")),
            )
        }
        if (collecting) {
            val o = order!!
            val pay = "Pay by ${o.pay.label} when you collect it."
            val reply = if (o.state == OrderState.OnTheWay) "It's ready now at ${o.restaurantName}, ${o.restaurantArea}. $pay"
            else "Collect it from ${o.restaurantName}, ${o.restaurantArea}. It'll be ready in about ${Fmt.minutes(o.minutesLeft ?: 1)}, and I'll post here when it is. $pay"
            return BotResult(
                BotReply(Intent.Pickup, listOf(reply), card = orderCard(o), quickReplies = stageChips(o), answered = true),
                state.copy(orderId = o.id, awaiting = Awaiting.Nothing),
            )
        }
        return info(Intent.Pickup, Guidelines.PICKUP, state)
    }

    private fun account(text: String, state: ConversationState, ctx: BotContext): BotResult {
        if (has(text, rx("""\b(delete|close|remove|deactivate|cancel) (my )?account\b"""))) {
            return escalate(
                Intent.Account,
                listOf("I can't delete accounts myself, so I've passed your request to our team. They'll confirm with you before anything is removed."),
                Escalation(Category.CustomerService, "Account Deletion Request", Severity.Low, null, null, withWords("The customer asked for their account to be deleted.", text)),
                state, ctx,
            )
        }
        if (has(text, rx("""\b(email|e mail|phone number|mobile number|cell number|my number|name)\b""")) && has(text, rx("""\b(change|update|wrong|new)\b"""))) {
            return escalate(
                Intent.Account,
                listOf("For your security, a person changes email addresses and phone numbers. I've logged the request, and they'll contact you to confirm it's you."),
                Escalation(Category.CustomerService, "Account Details Change", Severity.Low, null, null, withWords("The customer wants to change their account details.", text)),
                state, ctx,
            )
        }
        if (has(text, rx("""\b(password|log ?in|sign ?in|locked)\b"""))) {
            return info(Intent.Account, "To reset your password, sign out, tap \"Forgot password?\" on the login screen and enter your email or mobile number. We'll give you a 6-digit code to set a new one. After 5 wrong tries, sign-in pauses for 30 seconds.", state)
        }
        return info(Intent.Account, Guidelines.ACCOUNT, state)
    }

    private fun smallTalk(text: String, state: ConversationState, ctx: BotContext): BotResult {
        val order = orderFor(state, ctx)
        val chips = if (order != null && order.active) stageChips(order) else menuChips()
        if (has(text, rx("""\b(who are you|what are you|bot|robot|human|real person|are you real|your name|talking to|ai)\b"""))) {
            val t = "I'm the eats virtual assistant: an automated helper, not a person. I can track your order and sort out most problems straight away, like missing or wrong food, refunds and charges. If you'd rather talk to a person, just say so."
            return BotResult(BotReply(Intent.SmallTalk, listOf(t), quickReplies = listOf(human()) + chips), state.copy(awaiting = Awaiting.Nothing))
        }
        val t = when {
            has(text, rx("""\b(what can you do|what can you help|what do you do|how can you help)\b""")) ->
                "I can track your order, cancel it or change the address while that is still possible, sort out missing, wrong or poor-quality food, chase refunds, check charges, update you on complaints, and answer questions about fees, promos and payments."
            has(text, rx("""\b(how are you|hows it going|how is it going|whats up)\b""")) -> "I'm good, thanks for asking! How can I help with your order today?"
            has(text, rx("""\b(good bot|love you|you are great|youre great|amazing|well done)\b""")) -> "Thank you, that's kind! Anything else I can help with?"
            has(text, rx("""\bjoke\b""")) -> "I'll leave the jokes to the comedians, but I'm very good at finding lost food. Anything I can help with on an order?"
            else -> "I can only help with eats orders and your account, so I'll have to leave that one. Is there anything I can help with on an order?"
        }
        return BotResult(BotReply(Intent.SmallTalk, listOf(t), quickReplies = chips), state.copy(awaiting = Awaiting.Nothing))
    }

    private fun ack(state: ConversationState, ctx: BotContext): BotResult {
        val c = newestOpen(ctx)
        val text = if (c != null && ctx.now - c.createdAt <= HOUR) {
            "Okay. ${c.reference} is with our team, and you can follow it under My complaints. Anything else I can help with?"
        } else "Okay! Anything else I can help with?"
        return done(Intent.Ack, text, state)
    }

    private fun serviceInfo(text: String, state: ConversationState): BotResult {
        val t = when {
            has(text, rx("""\b(close|open|hours|closing|opening|trading)\b""")) ->
                "Restaurants set their own opening hours, shown on each restaurant's page. Our support team is available ${Guidelines.SUPPORT_HOURS}, and I'm here any time."
            has(text, rx("""\b(minimum|min order|smallest)\b""")) ->
                "There's no minimum order. Small orders just pay the restaurant's delivery fee and the R6 service fee."
            has(text, rx("""\b(halal|vegan|vegetarian|kosher|plant based|meat free)\b""")) ->
                "Each restaurant sets its own menu, and dish descriptions list the main ingredients. For halal, vegan or vegetarian needs, check the dish description, add a note for the kitchen, or ask the restaurant before you order."
            has(text, rx("""\b(how does|how do i order|how it works)\b""")) ->
                "Pick a restaurant, add dishes to your cart and check out. You pay cash or card when the food arrives, and you can follow it live on the tracking screen."
            else -> {
                val m = Regex("""\b(?:to|in)\s+([A-Za-z][A-Za-z\s-]{2,30})\??$""").find(text.trim())
                val place = m?.groupValues?.get(1)?.trim()
                "We deliver around East London, including areas like Quigney, Vincent, Southernwood, Berea, Nahoon and Beacon Bay. " +
                    if (place != null) "To check $place, set it as your delivery address on Home: you'll see every restaurant that delivers there."
                    else "Set your delivery address on Home and you'll see every restaurant that delivers there."
            }
        }
        return info(Intent.ServiceInfo, t, state)
    }

    private fun willBeCharged(order: BotOrder?, state: ConversationState, ctx: BotContext): BotResult {
        if (order == null) return info(Intent.WillBeCharged, Guidelines.PAY, state)
        val s = state.copy(orderId = order.id)
        if (order.state == OrderState.Cancelled) {
            return info(Intent.WillBeCharged, "No. Your order from ${order.restaurantName} was cancelled, and because you pay on delivery you won't be charged anything.", s)
        }
        val requested = ctx.complaints.any { it.orderId == order.id && it.status.isOpen && it.subcategory == Guidelines.cancel.subcategory }
        if (requested && order.state.inProgress) {
            return info(Intent.WillBeCharged, "Your cancellation request is with ${order.restaurantName}. If they've already made the food they may charge for it, and the agent will tell you before anything is charged. You pay on delivery, so nothing has been taken yet.", s)
        }
        val how = "by ${order.pay.label}" + if (order.pickup) " at pick-up" else " on delivery"
        if (order.state.inProgress) return info(Intent.WillBeCharged, "You're only charged when your order arrives: ${Fmt.money(order.total)} $how. Nothing has been taken yet.", s)
        return BotResult(
            BotReply(
                Intent.WillBeCharged,
                listOf("You paid ${Fmt.money(order.total)} $how for your order from ${order.restaurantName}. If you see a different amount or a second charge, tell me and I'll sort it out."),
                quickReplies = listOf(pick(Intent.PaymentProblem)), answered = true,
            ),
            s.copy(awaiting = Awaiting.Nothing),
        )
    }

    /** "the bakery order", "yesterday's order", "E-4802" -> that order, if only one fits. */
    private fun orderFromText(text: String, ctx: BotContext): BotOrder? {
        val n = TextNormaliser.normalise(text)
        if (n == "") return null
        ctx.orders.firstOrNull { n.contains(it.id.lowercase().replaceFirst("-", " ")) || n.contains(it.id.lowercase().replaceFirst("-", "")) }?.let { return it }
        var list = ctx.orders
        val words = TextNormaliser.tokens(text)
        val named = list.filter { o ->
            TextNormaliser.tokens(o.restaurantName).filter { it.length >= 4 && it !in setOf("and", "the", "kitchen", "house") }
                .any { w -> words.any { TokenMatcher.matches(it, w) } }
        }
        if (named.isNotEmpty()) list = named
        val today = Fmt.dayNumber(ctx.now)
        val dated: List<BotOrder>? = when {
            rx("""\b(today|tonight|this morning|this afternoon|this evening|earlier)\b""").containsMatchIn(n) -> list.filter { Fmt.dayNumber(it.placedAt) == today }
            rx("""\b(yesterday|last night)\b""").containsMatchIn(n) -> list.filter { Fmt.dayNumber(it.placedAt) == today - 1 }
            rx("""\blast week\b""").containsMatchIn(n) -> list.filter { today - Fmt.dayNumber(it.placedAt) >= 2 }
            else -> null
        }
        if (!dated.isNullOrEmpty()) list = dated
        if ((named.isNotEmpty() || !dated.isNullOrEmpty()) && list.size == 1) return list[0]
        // the items they mention ("the burger", "the shake") show which order it was
        val byItems = list.filter { findItems(text, it).isNotEmpty() }
        return if (byItems.size == 1) byItems[0] else null
    }

    private fun rate(order: BotOrder, state: ConversationState): BotResult {
        val text = when {
            order.rating > 0 -> "You rated your order from ${order.restaurantName} ${order.rating} star${if (order.rating > 1) "s" else ""}. Thanks, it helps other customers."
            order.active && order.state == OrderState.Delivered -> "Scroll down on the tracking screen and tap the stars to rate your order from ${order.restaurantName}."
            else -> Guidelines.RATE
        }
        return simple(Intent.Rate, text, state, emptyList(), true)
    }

    private fun info(intent: Intent, text: String, state: ConversationState) =
        BotResult(BotReply(intent, listOf(text), quickReplies = listOf(menu()), answered = true), state.copy(awaiting = Awaiting.Nothing))

    // --- Complaints and people ------------------------------------------------------------------

    private fun complaintStatus(state: ConversationState, ctx: BotContext): BotResult {
        val open = ctx.complaints.filter { it.status.isOpen }.sortedByDescending { it.createdAt }
        if (open.isEmpty()) {
            val last = ctx.complaints.maxByOrNull { it.createdAt }
            val text = if (last == null) "You don't have any complaints with us. Is something wrong with an order?"
            else "You have no open complaints. Your latest, ${last.reference} (${last.subcategory}), is ${last.status.label.lowercase()}."
            return BotResult(
                BotReply(Intent.ComplaintStatus, listOf(text), quickReplies = listOfNotNull(last?.let(::viewChip)) + menuChips().take(4), answered = true),
                state.copy(awaiting = Awaiting.Nothing),
            )
        }
        val c = open[0]
        val lines = mutableListOf(if (open.size == 1) "You have one open complaint." else "You have ${open.size} open complaints. The latest:")
        lines += "${c.reference}: ${c.subcategory}, ${c.severity.label} priority. It's ${c.status.label.lowercase()}" + when {
            c.severity == Severity.Critical -> ", and a person is handling it now."
            c.overdue(ctx.now) -> ", and it's overdue. I can raise it to a supervisor."
            else -> ", and a person will respond by ${Fmt.due(c.dueAt, ctx.now)}."
        }
        val chips = open.take(3).map(::viewChip) +
            if (c.overdue(ctx.now) && c.severity != Severity.Critical) listOf(qr(Action.ChaseComplaint, "Raise it to a supervisor")) else emptyList()
        return BotResult(
            BotReply(Intent.ComplaintStatus, lines, card = complaintCard(c, ctx.now), quickReplies = chips, answered = true),
            state.copy(awaiting = Awaiting.Nothing),
        )
    }

    private fun handOff(text: String, state: ConversationState, ctx: BotContext): BotResult {
        val order = orderFor(state, ctx)
        val g = state.lastProblem?.let(::guidelineFor)
        return escalate(
            Intent.TalkToHuman,
            listOf("No problem. I'm handing this conversation to a person, so you won't have to repeat yourself."),
            Escalation(
                g?.category ?: Category.CustomerService, g?.subcategory ?: "Customer Asked For A Person", g?.severity ?: Severity.Medium,
                order?.id, order?.restaurantName, withWords("The customer asked to speak to a person.", text),
            ),
            state, ctx, listOf(qr(Action.Call, "Call an agent")),
        )
    }

    private fun callAgent(state: ConversationState) = BotResult(
        BotReply(
            Intent.CallAgent, listOf("You can call one of our agents now. Support is open ${Guidelines.SUPPORT_HOURS}."),
            quickReplies = listOf(qr(Action.Call, "Call an agent"), human()), answered = true,
        ),
        state.copy(awaiting = Awaiting.Nothing),
    )

    private fun correction(state: ConversationState) = BotResult(
        BotReply(
            Intent.Correction, listOf("Sorry, I got that wrong. What did you mean? Pick one of these and I'll remember it for next time."),
            quickReplies = menuChips() + human(),
        ),
        state.copy(awaiting = Awaiting.Nothing),
    )

    private fun greeting(state: ConversationState, ctx: BotContext): BotResult {
        val order = orderFor(state, ctx)
        val chips = if (order != null && order.active) stageChips(order) else menuChips()
        return BotResult(
            BotReply(Intent.Greeting, listOf("Hi ${ctx.firstName}, what can I help with? Tap one of these or tell me in your own words."), quickReplies = chips),
            state.copy(awaiting = Awaiting.Nothing),
        )
    }

    private fun done(intent: Intent, text: String, state: ConversationState) = BotResult(
        BotReply(intent, listOf(text), quickReplies = listOf(qr(Action.Pick(Intent.Goodbye), "No, that's all"), qr(Action.Menu, "Something else"))),
        state.copy(awaiting = Awaiting.Nothing),
    )

    private fun noOrder(intent: Intent, state: ConversationState): BotResult {
        val text = if (intent in setOf(Intent.WhereIsOrder, Intent.NeverArrived, Intent.Late, Intent.CancelOrder, Intent.ChangeAddress, Intent.ContactDriver)) {
            "I can't see an order on this account yet. If you've just ordered, check you're signed in with the account you used; orders show here as soon as they're placed. You haven't been charged, because you only pay on delivery."
        } else "I can't see any orders on your account yet. Once you place one, I can track it and help with any problem."
        return BotResult(
            BotReply(intent, listOf(text), quickReplies = listOf(pick(Intent.Fees), pick(Intent.PaymentMethods), human())),
            state.copy(awaiting = Awaiting.Nothing),
        )
    }

    private fun askWhichOrder(intent: Intent, text: String, state: ConversationState, ctx: BotContext) = BotResult(
        BotReply(intent, listOf("Which order is this about?"), quickReplies = ctx.orders.take(3).map { qr(Action.ChooseOrder(it.id), orderLabel(it)) }),
        state.copy(awaiting = Awaiting.OrderChoice(intent, text)),
    )

    // =========================================================================================
    // Buttons
    // =========================================================================================

    private fun onChoice(action: Action, state: ConversationState, ctx: BotContext): BotResult {
        val order = orderFor(state, ctx)
        val a = state.awaiting
        return when (action) {
            is Action.Pick -> {
                if (action.intent == Intent.OrderDetails && a is Awaiting.OrderChoice) {
                    val earlier = ctx.orders.filter { !it.active }
                    if (earlier.size == 1) return onChoice(Action.ChooseOrder(earlier[0].id), state, ctx)
                    return BotResult(
                        BotReply(a.intent, listOf("Which order is this about?"), quickReplies = earlier.take(3).map { qr(Action.ChooseOrder(it.id), orderLabel(it)) }),
                        state,
                    )
                }
                onIntent(action.intent, "", state.copy(awaiting = Awaiting.Nothing), ctx)
            }
            is Action.ChooseOrder -> {
                val intent = if (a is Awaiting.OrderChoice) a.intent else Intent.OrderDetails
                val text = if (a is Awaiting.OrderChoice) a.text else ""
                onIntent(intent, text, state.copy(orderId = action.orderId, awaiting = Awaiting.Nothing), ctx)
            }
            is Action.ChooseItem -> {
                val everything = action.index == -1
                if (order == null || (!everything && (action.index < 0 || action.index >= order.lines.size))) return greeting(state, ctx)
                when (a) {
                    is Awaiting.MissingWhich -> if (everything) neverArrived(order, state, ctx, "") else chooseMissing(order, action.index, state, a.tamper)
                    Awaiting.WrongWhich -> if (everything) onChoice(Action.WholeOrderWrong, state, ctx) else wrongItems(order, listOf(action.index), state)
                    is Awaiting.QualityWhich -> qualityAction(order, a.kind, if (everything) order.lines.indices.toList() else listOf(action.index), state)
                    else -> if (everything) neverArrived(order, state, ctx, "") else chooseMissing(order, action.index, state.copy(awaiting = Awaiting.MissingWhich()))
                }
            }
            is Action.ChooseQty -> if (order != null && a is Awaiting.MissingHowMany) {
                missingAction(order, listOf(MissingPick(a.index, action.qty)), state, a.tamper)
            } else greeting(state, ctx)
            is Action.ChooseQuality -> if (order != null) {
                chooseQuality(order, action.kind, state, if (a is Awaiting.QualityWhat) a.indexes else emptyList())
            } else greeting(state, ctx)
            Action.FollowUpYes -> if (a is Awaiting.FollowUpOffer) onIntent(a.intent, a.text, state.copy(awaiting = Awaiting.Nothing), ctx) else greeting(state, ctx)
            Action.FollowUpNo -> done(Intent.Thanks, "No problem. Anything else I can help with?", state)
            is Action.ChooseCharge -> chargeAnswer(action.kind, state, ctx)
            is Action.ChooseMethod -> {
                if (a !is Awaiting.RefundMethodChoice) return greeting(state, ctx)
                val e = a.escalation.copy(refundMethod = action.method)
                escalate(
                    intentFor(e.category),
                    listOf("I've asked for ${Fmt.money(e.refund?.amount ?: 0)} ${action.method.label}. A support agent reviews every refund (I can't approve them myself). Once approved, it arrives ${action.method.timing}."),
                    e, state, ctx,
                )
            }
            is Action.OpenComplaint -> complaintStatus(state, ctx)
            Action.WholeOrderWrong -> {
                if (order == null) return greeting(state, ctx)
                BotResult(
                    BotReply(
                        Intent.WrongOrder,
                        listOf("If you have allergies, please don't eat food that isn't yours.", "Would you like a full refund of ${Fmt.money(order.total)}, or the correct order delivered?"),
                        quickReplies = listOf(qr(Action.FullRefund, "Full refund ${Fmt.money(order.total)}"), qr(Action.Redeliver, "Redeliver my order")),
                    ),
                    state.copy(orderId = order.id, awaiting = Awaiting.WrongAction(null)),
                )
            }
            Action.SomeItemsWrong -> {
                if (order == null) return greeting(state, ctx)
                BotResult(BotReply(Intent.WrongOrder, listOf("Which item was wrong?"), quickReplies = itemChips(order)), state.copy(awaiting = Awaiting.WrongWhich))
            }
            Action.FullRefund -> {
                if (order == null) return greeting(state, ctx)
                refundFlow(
                    esc(Guidelines.wrong, order, "The whole order ${order.id} from ${order.restaurantName} was wrong. The customer asks for a full refund.", RefundSpec(order.total, "Whole order wrong")),
                    order, state, ctx,
                )
            }
            Action.Redeliver -> {
                if (order == null) return greeting(state, ctx)
                escalate(
                    Intent.WrongOrder,
                    listOf("I've asked ${order.restaurantName} and our team to send the correct order. Because you need your food now, it's High priority."),
                    esc(Guidelines.wrong, order, "The whole order ${order.id} from ${order.restaurantName} was wrong. The customer asks for the correct order to be delivered.")
                        .copy(severity = Severity.High),
                    state, ctx,
                )
            }
            Action.ItemRefund -> itemRefund(order, state, ctx)
            Action.TellRestaurant -> {
                if (order == null) return greeting(state, ctx)
                if (a is Awaiting.MissingAction && a.tamper) {
                    return escalate(
                        Intent.DriverIssue,
                        listOf("Done. I've told ${order.restaurantName}, and reported the opened bag to our logistics team so they can follow it up with the driver."),
                        Escalation(Category.DriverConduct, "Possible Tampering", Severity.High, order.id, order.restaurantName,
                            "Order ${order.id}: the customer says the bag was opened or food eaten before delivery. No refund requested."),
                        state, ctx,
                    )
                }
                BotResult(
                    BotReply(Intent.FoodQuality, listOf("Done. I've passed your feedback to ${order.restaurantName} so they can put it right. Anything else I can help with?"),
                        quickReplies = listOf(menu()), answered = true),
                    state.copy(awaiting = Awaiting.Nothing),
                )
            }
            Action.CheckedEverywhere -> if (order != null) notReceived(order, state.copy(awaiting = Awaiting.Nothing), ctx) else greeting(state, ctx)
            Action.FoundIt -> done(Intent.NeverArrived, "Great, I'm glad you found it. Enjoy your meal!", state)
            Action.WasCharged -> escalate(
                Intent.PaymentProblem,
                listOf(
                    "Thanks for telling me. Being charged for food you never got is serious, so I've added it to your case as Critical. Our payments team will look at it immediately.",
                    "Please don't share card numbers or PINs here; we will never ask for your PIN.",
                ),
                Escalation(Category.PaymentIssue, "Charged For Undelivered Order", Severity.Critical, order?.id, order?.restaurantName,
                    "The customer was charged for order ${order?.id ?: ""} but never received it."),
                state.copy(lastProblem = Intent.PaymentProblem), ctx,
            )
            is Action.UseAddress -> if (order != null) newAddress(order, action.address, state.copy(awaiting = Awaiting.Nothing), ctx) else greeting(state, ctx)
            Action.KeepWaiting -> BotResult(
                BotReply(Intent.Late, listOf("Thanks for your patience. I'll post an update here as soon as it moves."), answered = true),
                state.copy(awaiting = Awaiting.Nothing),
            )
            Action.ConfirmCancel -> if (order != null) confirmCancel(order, state.copy(awaiting = Awaiting.Nothing), ctx) else greeting(state, ctx)
            Action.KeepOrder -> BotResult(
                BotReply(Intent.CancelOrder, listOf("No problem, your order stays as it is. Anything else I can help with?"),
                    quickReplies = if (order != null) stageChips(order) else menuChips()),
                state.copy(awaiting = Awaiting.Nothing),
            )
            Action.ChaseComplaint -> {
                val c = ctx.complaints.filter { it.status.isOpen || it.refund?.status == RefundStatus.Declined }.maxByOrNull { it.createdAt }
                if (c != null) chase(c, state, ctx) else complaintStatus(state, ctx)
            }
            Action.Helped -> done(Intent.Thanks, "Glad I could help. Anything else?", state)
            Action.NotHelped -> BotResult(
                BotReply(Intent.Correction, listOf("Sorry that didn't help. Would you like a person to take over, or try one of these?"), quickReplies = listOf(human()) + menuChips()),
                state.copy(awaiting = Awaiting.Nothing),
            )
            Action.Human -> handOff("", state, ctx)
            Action.Call -> callAgent(state)
            Action.Menu -> greeting(state, ctx)
        }
    }

    private fun itemRefund(order: BotOrder?, state: ConversationState, ctx: BotContext): BotResult {
        if (order == null) return greeting(state, ctx)
        return when (val a = state.awaiting) {
            is Awaiting.MissingAction -> {
                val amount = a.items.sumOf { order.lines[it.index].unit * it.qty }
                val what = listJoin(a.items.map { "${it.qty} × ${order.lines[it.index].name}" })
                var e = esc(
                    Guidelines.missing, order,
                    "$what missing from order ${order.id} (${Fmt.money(amount)}). The customer asks for a refund." +
                        if (a.tamper) " The customer says the bag was opened or food eaten before delivery (possible tampering)." else "",
                    RefundSpec(amount, "Missing: $what"),
                )
                if (a.tamper) e = e.copy(subcategory = "Missing Items (Possible Tampering)", severity = Severity.High)
                refundFlow(e, order, state, ctx)
            }
            is Awaiting.WrongAction -> {
                val idx = a.indexes ?: listOf(0)
                val amount = idx.sumOf { order.lines[it].total }
                val what = listJoin(idx.map { order.lines[it].name })
                refundFlow(
                    esc(Guidelines.wrong, order, "Wrong $what on order ${order.id} (${Fmt.money(amount)}). The customer asks for a refund.", RefundSpec(amount, "Wrong item: $what")),
                    order, state, ctx,
                )
            }
            is Awaiting.QualityAction -> {
                val amount = a.indexes.sumOf { order.lines[it].total }
                val what = listJoin(a.indexes.map { order.lines[it].name })
                val sub = when (a.kind) {
                    QualityKind.Cold -> "Cold Food Delivered"
                    QualityKind.Spilled -> "Damaged Or Spilled Food"
                    QualityKind.Undercooked -> "Undercooked Food"
                    QualityKind.TastesOff -> "Poor Taste Or Freshness"
                }
                refundFlow(
                    esc(Guidelines.quality, order, "${a.kind.label}: $what on order ${order.id} (${Fmt.money(amount)}). The customer asks for a refund.", RefundSpec(amount, "${a.kind.label}: $what"))
                        .copy(subcategory = sub),
                    order, state, ctx,
                )
            }
            else -> greeting(state, ctx)
        }
    }

    /** Card orders are refunded to the card; cash orders choose credit or a bank transfer (Uber SA refund policy). */
    private fun refundFlow(e: Escalation, order: BotOrder, state: ConversationState, ctx: BotContext, lead: List<String> = emptyList()): BotResult {
        val amount = e.refund?.amount ?: 0
        val notes = timeNotes(order, ctx)
        if (order.pay == PayKind.Card) {
            return escalate(
                intentFor(e.category),
                lead + "I've asked for a refund of ${Fmt.money(amount)} back to your card. A support agent reviews every refund (I can't approve them myself). Once approved, it arrives ${RefundMethod.Card.timing}." + notes,
                e.copy(refundMethod = RefundMethod.Card), state, ctx,
            )
        }
        return BotResult(
            BotReply(
                intentFor(e.category),
                lead + "You paid cash, so how would you like the ${Fmt.money(amount)} back?" + notes,
                quickReplies = listOf(qr(Action.ChooseMethod(RefundMethod.EatsCredit), "As eats credit"), qr(Action.ChooseMethod(RefundMethod.BankTransfer), "By bank transfer")),
            ),
            state.copy(awaiting = Awaiting.RefundMethodChoice(e)),
        )
    }

    private fun timeNotes(order: BotOrder, ctx: BotContext): List<String> {
        val since = ctx.now - (order.deliveredAt ?: order.placedAt)
        return when {
            since > Guidelines.REFUND_REQUEST_DAYS * DAY -> listOf("This order is more than ${Guidelines.REFUND_REQUEST_DAYS} days old, so a refund is at the agent's discretion.")
            since > Guidelines.FOOD_ISSUE_REPORT_HOURS * HOUR -> listOf("It's been more than ${Guidelines.FOOD_ISSUE_REPORT_HOURS} hours since delivery, so the agent will check this one carefully.")
            else -> emptyList()
        }
    }

    // =========================================================================================
    // Escalation
    // =========================================================================================

    /**
     * Logs the complaint. Repeat problems (the same category in the last 30 days) get a higher
     * severity, because the data shows repeat complaints are the ones that end up on Hellopeter.
     * Requests (Customer Service, Order Change) aren't failures, so they never count as repeats.
     */
    private fun escalate(
        intent: Intent,
        texts: List<String>,
        escalation: Escalation,
        state: ConversationState,
        ctx: BotContext,
        extraChips: List<QuickReply> = emptyList(),
    ): BotResult {
        val earlier = ctx.complaints.count {
            it.category == escalation.category && ctx.now - it.createdAt <= 30 * DAY &&
                !(it.status.isOpen && it.orderId != null && it.orderId == escalation.orderId)
        }
        val counts = escalation.category != Category.CustomerService && escalation.category != Category.OrderChange
        val e = if (counts && earlier > 0 && escalation.severity != Severity.Critical) {
            escalation.copy(
                severity = escalation.severity.raised(),
                description = escalation.description + " Repeat issue: $earlier earlier ${escalation.category.label} complaint${if (earlier > 1) "s" else ""} in 30 days.",
            )
        } else escalation
        val extra = if (e.severity != escalation.severity) listOf("Because this has happened to you before, I've given it a higher priority.") else emptyList()
        return BotResult(
            BotReply(intent, texts + extra, escalation = e, quickReplies = extraChips),
            state.copy(awaiting = Awaiting.Nothing, misses = 0),
        )
    }

    private fun esc(g: Guidelines.Guideline, order: BotOrder, description: String, refund: RefundSpec? = null) =
        Escalation(g.category, g.subcategory, g.severity, order.id, order.restaurantName, description, refund)

    // =========================================================================================
    // Helpers
    // =========================================================================================

    private fun simple(intent: Intent, text: String, state: ConversationState, chips: List<QuickReply>, answered: Boolean = false) =
        BotResult(BotReply(intent, listOf(text), quickReplies = chips, answered = answered), state.copy(awaiting = Awaiting.Nothing))

    private fun orderFor(state: ConversationState, ctx: BotContext): BotOrder? =
        state.orderId?.let { id -> ctx.orders.firstOrNull { it.id == id } } ?: ctx.orders.firstOrNull()

    private fun stageSummary(order: BotOrder): String = when (order.state) {
        OrderState.Delivered -> "Your order from ${order.restaurantName} was ${if (order.pickup) "collected" else "delivered"} at ${Fmt.time(order.deliveredAt ?: order.placedAt)}. Was everything okay?"
        OrderState.Cancelled -> "Your order from ${order.restaurantName} was cancelled. How can I help?"
        else -> progressText(order) + " How can I help?"
    }

    private fun stageChips(order: BotOrder): List<QuickReply> = if (order.pickup) when (order.state) {
        OrderState.Received -> listOf(pick(Intent.WhereIsOrder), pick(Intent.CancelOrder), pick(Intent.ModifyOrder), pick(Intent.Pickup))
        OrderState.Preparing -> listOf(pick(Intent.WhereIsOrder), pick(Intent.CancelOrder), pick(Intent.Late), pick(Intent.Pickup))
        OrderState.OnTheWay -> listOf(pick(Intent.WhereIsOrder), pick(Intent.Pickup), pick(Intent.OrderDetails))
        OrderState.Delivered -> listOf(pick(Intent.MissingItem), pick(Intent.WrongOrder), pick(Intent.FoodQuality), pick(Intent.RefundStatus))
        OrderState.Cancelled -> listOf(pick(Intent.PaymentProblem), pick(Intent.ComplaintStatus))
    } else when (order.state) {
        OrderState.Received -> listOf(pick(Intent.WhereIsOrder), pick(Intent.CancelOrder), pick(Intent.ChangeAddress), pick(Intent.ModifyOrder))
        OrderState.Preparing -> listOf(pick(Intent.WhereIsOrder), pick(Intent.CancelOrder), pick(Intent.ChangeAddress), pick(Intent.Late))
        OrderState.OnTheWay -> listOf(pick(Intent.WhereIsOrder), pick(Intent.ContactDriver), pick(Intent.Late), pick(Intent.ChangeAddress))
        OrderState.Delivered -> listOf(pick(Intent.MissingItem), pick(Intent.WrongOrder), pick(Intent.FoodQuality), pick(Intent.NeverArrived), pick(Intent.RefundStatus))
        OrderState.Cancelled -> listOf(pick(Intent.PaymentProblem), pick(Intent.ComplaintStatus))
    }

    private fun menuChips() = Intent.menu.map(::pick)
    private fun itemChips(order: BotOrder): List<QuickReply> {
        val seen = mutableSetOf<String>()
        return order.lines.mapIndexed { i, l ->
            val label = if (seen.add(l.name)) l.name else "${l.name} (${i + 1})"
            qr(Action.ChooseItem(i), label)
        }
    }

    private fun orderLabel(o: BotOrder) = "${o.restaurantName}, ${Fmt.date(o.placedAt)}" + if (o.active) " (current)" else ""
    private fun viewChip(c: Complaint) = qr(Action.OpenComplaint(c.id), "View ${c.reference}")
    private fun pick(intent: Intent) = qr(Action.Pick(intent), intent.label)
    private fun human() = qr(Action.Human, "Talk to a person")
    private fun menu() = qr(Action.Menu, "Something else")
    private fun qr(action: Action, label: String) = QuickReply(action, label)

    fun orderCard(order: BotOrder): InfoCard {
        val rows = mutableListOf("Status" to if (order.pickup && order.state == OrderState.OnTheWay) "Ready for pick-up" else order.state.label)
        if (order.state.inProgress && order.minutesLeft != null && !(order.pickup && order.state == OrderState.OnTheWay)) {
            rows += (if (order.pickup) "Ready in" else "Arriving in") to ("about " + Fmt.minutes(order.minutesLeft) + if (order.minutesLate > 0) " (late)" else "")
        }
        if (order.state == OrderState.Delivered && order.deliveredAt != null) rows += (if (order.pickup) "Collected" else "Delivered") to Fmt.time(order.deliveredAt)
        if (!order.pickup && order.driverName != null && order.state != OrderState.Received && order.state != OrderState.Cancelled) {
            rows += "Driver" to (order.driverName + (order.driverVehicle?.let { ", $it" } ?: ""))
        }
        rows += "Items" to "${order.itemCount}"
        rows += "Total" to "${Fmt.money(order.total)} (${order.pay.label})"
        return InfoCard("${order.restaurantName} · ${order.id}", rows)
    }

    fun complaintCard(c: Complaint, now: Long): InfoCard = InfoCard(
        "Complaint ${c.reference}",
        listOfNotNull(
            "Problem" to c.subcategory,
            "Priority" to c.severity.label,
            "Status" to c.status.label,
            (if (c.status.isOpen) "Response by" to (if (c.severity == Severity.Critical) "Immediately" else Fmt.due(c.dueAt, now)) else null),
            c.refund?.let { "Refund" to "${Fmt.money(it.amount)}, ${it.status.label.lowercase()}" },
        ),
    )

    private fun guidelineFor(intent: Intent): Guidelines.Guideline? = when (intent) {
        Intent.WhereIsOrder, Intent.NeverArrived -> Guidelines.notDelivered
        Intent.Late -> Guidelines.late
        Intent.MissingItem -> Guidelines.missing
        Intent.WrongOrder -> Guidelines.wrong
        Intent.FoodQuality -> Guidelines.quality
        Intent.FoodSafety -> Guidelines.safety
        Intent.RefundStatus -> Guidelines.refundLate
        Intent.PaymentProblem -> Guidelines.payment
        Intent.PoorSupport -> Guidelines.poorSupport
        Intent.CancelOrder -> Guidelines.cancel
        Intent.ChangeAddress -> Guidelines.address
        Intent.DriverIssue -> Guidelines.driver
        else -> null
    }

    private fun intentFor(category: Category): Intent = when (category) {
        Category.MissingItems -> Intent.MissingItem
        Category.WrongOrder -> Intent.WrongOrder
        Category.FoodQuality -> Intent.FoodQuality
        Category.DeliveryFailure -> Intent.NeverArrived
        Category.DeliveryDelay -> Intent.Late
        Category.RefundIssue -> Intent.RefundStatus
        Category.PaymentIssue -> Intent.PaymentProblem
        Category.CustomerService -> Intent.PoorSupport
        Category.OrderChange -> Intent.CancelOrder
        Category.DriverConduct -> Intent.DriverIssue
    }

    /** Every order line the text names. A word that fits several items ("kota" with two kotas) is ambiguous, so it's skipped. */
    private fun findItems(text: String, order: BotOrder?): List<Int> {
        val lines = order?.lines ?: return emptyList()
        if (lines.isEmpty()) return emptyList()
        // The restaurant's own name ("Kota King") is not an item.
        val shop = TextNormaliser.normalise(order.restaurantName)
        val normal = TextNormaliser.normalise(text)
        val input = (if (shop.isNotEmpty()) normal.replace(shop, " ") else normal).replace(rx("""\s+"""), " ").trim()
        if (input.isEmpty()) return emptyList()
        val found = sortedSetOf<Int>()
        lines.forEachIndexed { i, l ->
            val name = TextNormaliser.normalise(l.name)
            if (name.isNotEmpty() && input.contains(name)) found += i
        }
        val words = TextNormaliser.tokens(input).filter { it.length >= 3 && it !in itemStopWords }
        val nameWords = lines.map { l -> TextNormaliser.tokens(l.name).filter { it.length >= 3 && !it[0].isDigit() && it != "with" } }
        for (w in words) {
            val hits = nameWords.indices.filter { i -> nameWords[i].any { TokenMatcher.matches(w, it) } }
            if (hits.size == 1) found += hits[0]
        }
        if (rx("""\b(drink|drinks|cooldrink|cooldrinks|cold drink|soda|beverage|cola)\b""").containsMatchIn(input)) {
            val drinks = lines.indices.filter { drink.containsMatchIn(TextNormaliser.normalise(lines[it].name)) }
            if (drinks.size == 1) found += drinks[0]
        }
        return found.toList()
    }

    private fun wordNumber(text: String): Int? {
        val words = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "all" to 99, "both" to 2)
        return TextNormaliser.tokens(text).firstNotNullOfOrNull { words[it] }
    }

    private fun qualityKind(text: String): QualityKind? {
        val t = TextNormaliser.tokens(text)
        val n = TextNormaliser.normalise(text)
        return when {
            t.any { it in setOf("cold", "lukewarm", "warm", "koud", "yskoud", "freezing") } -> QualityKind.Cold
            t.any { it in setOf("spilled", "spilt", "leaked", "leaking", "damaged", "squashed", "crushed", "melted", "soggy", "broken") } -> QualityKind.Spilled
            t.any { it in setOf("undercooked", "raw") } || n.contains("not cooked") || n.contains("half cooked") -> QualityKind.Undercooked
            t.any {
                it in setOf("stale", "tastes", "tasted", "smells", "smelled", "off", "salty", "bland", "burnt", "burned", "overcooked",
                    "flat", "hard", "dry", "old", "oily", "greasy")
            } -> QualityKind.TastesOff
            else -> null
        }
    }

    private fun chargeKind(text: String): ChargeKind? {
        val t = TextNormaliser.tokens(text)
        val n = TextNormaliser.normalise(text)
        val charged = rx("""\bcharg""").containsMatchIn(n)
        return when {
            t.any { it in setOf("pending", "hold", "reserved") } -> ChargeKind.Pending
            t.any { it in setOf("twice", "double", "duplicate", "doubled") } || n.contains("two times") ||
                (rx("""\b(also|as well|both)\b""").containsMatchIn(n) && charged) ||
                (rx("""\bpaid cash\b""").containsMatchIn(n) && rx("""\bcard\b""").containsMatchIn(n)) -> ChargeKind.Twice
            t.any { it in setOf("unauthorised", "unauthorized", "fraud", "fraudulent", "recognise", "recognize", "strange", "unknown", "stolen") } -> ChargeKind.Unknown
            t.any { it in setOf("overcharged", "more", "amount", "extra", "wrong", "different", "higher", "instead") } ||
                (rx("""\br ?\d+""").containsMatchIn(n) && charged) -> ChargeKind.WrongAmount
            else -> null
        }
    }

    private fun chargeWords(kind: ChargeKind) = when (kind) {
        ChargeKind.Pending -> "pending amount"
        ChargeKind.Twice -> "charged twice"
        ChargeKind.Unknown -> "unrecognised charge"
        ChargeKind.WrongAmount -> "wrong amount"
    }

    private fun mentionsSafety(text: String) = classifier.ranked(text).any { it.intent == Intent.FoodSafety }

    private fun looksLikeAddress(text: String): Boolean {
        val t = text.trim()
        return t.length >= 8 && TextNormaliser.tokens(t).size >= 2 && t.any { it.isLetter() }
    }

    private fun withWords(description: String, text: String): String =
        if (text.trim() == "") description else "$description Customer said: \"${text.trim().take(MAX_QUOTE)}\""

    private fun has(text: String, re: Regex) = re.containsMatchIn(TextNormaliser.normalise(text))
    private fun rx(pattern: String) = regexCache.getOrPut(pattern) { Regex(pattern) }
    private fun listJoin(xs: List<String>) = if (xs.size <= 1) xs.joinToString("") else xs.dropLast(1).joinToString(", ") + " and " + xs.last()
    private fun capFirst(s: String) = s.replaceFirstChar { it.uppercase() }

    companion object {
        const val LOOP_BREAKER = 2
        /** Below this, a match rests on one weak word ("driver"), so the AI helper gets a look first. */
        const val STRONG_ENOUGH = 0.7
        const val FRUSTRATION_LIMIT = 2
        const val MAX_ADDRESS = 200
        const val MAX_QUOTE = 280
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR

        private val regexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
        private val calmIntents = setOf(Intent.TalkToHuman, Intent.CallAgent, Intent.FoodSafety, Intent.PoorSupport, Intent.DriverIssue)
        private val socialIntents = setOf(Intent.Greeting, Intent.Thanks, Intent.Goodbye, Intent.Ack)
        private val notACorrection = setOf(Intent.Unknown, Intent.Correction, Intent.Ack, Intent.Greeting, Intent.Thanks, Intent.Goodbye)
        private val unsafeWords = setOf(
            "threatened", "threat", "threatening", "harassed", "harassment", "aggressive", "drunk", "unsafe", "scared",
            "touched", "followed", "assaulted", "hit", "weapon", "gun", "knife",
        )
        private val needsOrder = setOf(
            Intent.WhereIsOrder, Intent.NeverArrived, Intent.Late, Intent.MissingItem, Intent.WrongOrder,
            Intent.FoodQuality, Intent.CancelOrder, Intent.ChangeAddress, Intent.ModifyOrder, Intent.ContactDriver,
            Intent.OrderDetails, Intent.Rate,
        )
        private val pastOrderProblems = setOf(Intent.MissingItem, Intent.WrongOrder, Intent.FoodQuality, Intent.NeverArrived, Intent.OrderDetails)
        private val problems = setOf(
            Intent.WhereIsOrder, Intent.NeverArrived, Intent.Late, Intent.MissingItem, Intent.WrongOrder, Intent.FoodQuality,
            Intent.FoodSafety, Intent.RefundStatus, Intent.PaymentProblem, Intent.PoorSupport, Intent.CancelOrder, Intent.ChangeAddress, Intent.DriverIssue,
        )
        private val itemStopWords = setOf(
            "the", "my", "and", "was", "were", "missing", "item", "items", "didnt", "get", "got", "forgot", "they",
            "order", "one", "with", "from", "not", "for", "its", "that", "this", "there", "wrong", "cold", "all",
            "food", "two", "three", "both",
        )

        /** Which problem to handle first when a message mentions several (most urgent first). */
        private val priority = listOf(
            Intent.FoodSafety, Intent.DriverIssue, Intent.NeverArrived, Intent.WrongOrder, Intent.MissingItem, Intent.FoodQuality,
            Intent.PaymentProblem, Intent.CancelOrder, Intent.ChangeAddress, Intent.Late, Intent.RefundStatus, Intent.WhereIsOrder,
        )

        /** Problems worth offering to sort out next, if the customer mentioned them too. */
        private val followUps = mapOf(
            Intent.MissingItem to "something missing",
            Intent.WrongOrder to "a wrong item",
            Intent.FoodQuality to "a problem with the food",
            Intent.DriverIssue to "a problem with your driver",
            Intent.PaymentProblem to "a problem with a charge",
            Intent.NeverArrived to "an order that never arrived",
        )
        private val timing = Regex("""\b(how long|how much longer|when will|when can|when do|speed|hurry|faster|quicker|any update|any news|anything yet|still waiting|what now|what happens next|whats next|next step|asap|urgent)\b""")
        private val drink = Regex("""(coke|fanta|sprite|juice|water|shake|soda|tea|coffee|drink|cola|lemonade|ice tea|iced tea|milkshake|smoothie|\d+ ?ml)""")
        private val numWords = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "a" to 1, "an" to 1)
    }
}
