package com.eats.app.support.bot

/** One bubble in the support chat. */
data class ChatItem(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val at: Long,
    val card: InfoCard? = null,
    val source: ReplySource = ReplySource.Rules,
    /** Small grey line, e.g. "Learned: ...", rather than a speech bubble. */
    val notice: Boolean = false,
)

/** Things the chat asks the app to do (open a screen). */
sealed class ChatEvent {
    data class OpenComplaint(val id: String) : ChatEvent()
    data object StartCall : ChatEvent()
}

/**
 * Connects the engine to the Support screen: keeps the messages and buttons, logs hand-overs as
 * complaints, hands typed messages to the AI helper (AI-first) and does the learning.
 *
 * How it learns from mistakes:
 *  - The bot didn't understand a message, then the customer tapped what they meant: that wording is saved.
 *  - The customer says "that's not what I meant" (or taps "Not what I meant"): the mistake is counted,
 *    anything learned that caused it is forgotten, and the customer's next choice is saved instead.
 *  - The AI helper worked out what a message meant and the customer carried on (no correction):
 *    that wording is saved, so next time no AI call is needed.
 *
 * Plain Kotlin: the Android ViewModel only forwards these calls and runs the AI request.
 */
class SupportChat(
    val store: SupportStore,
    val memory: LearningMemory,
    private val clock: () -> Long = System::currentTimeMillis,
    private val engine: SupportEngine = SupportEngine(IntentClassifier(memory)),
    private val classifier: IntentClassifier = IntentClassifier(memory),
    private val onOrderAction: (OrderAction) -> Unit = {},
    private val onEvent: (ChatEvent) -> Unit = {},
    private val onLearned: () -> Unit = {},
) {
    /** Builds the bot's view of the customer's orders and complaints. Set by the app. */
    var context: () -> BotContext = { BotContext("there", emptyList(), store.sorted(), clock()) }

    /**
     * True when the AI helper should read every typed message first (it's set up and working).
     * Set by the app. The rules still answer on their own when the AI is off or not responding.
     */
    var aiFirst: () -> Boolean = { false }

    var items: List<ChatItem> = emptyList()
        private set
    var chips: List<QuickReply> = emptyList()
        private set
    /** The message waiting for the AI helper, or null. While set, the chat shows "typing". */
    var pendingAi: String? = null
        private set

    private val listeners = mutableListOf<() -> Unit>()
    private var state = ConversationState()
    private var nextId = 0L
    private val transcript = mutableListOf<TranscriptLine>()

    private var lastUnclear: String? = null
    /** The last typed message the bot acted on: its text, how it was understood, and as what. */
    private var lastInterpretation: Triple<String, ReplySource, Intent>? = null
    private var pendingLearn: Pair<String, Intent>? = null
    /** What the rules made of the message the AI is reading (AI-first mode only). */
    private var aiFirstGuess: Classification? = null
    private var watchedOrderId: String? = null
    private var watchedState: OrderState? = null
    private var applyingAction = false

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    /** Greets the customer the first time Support opens. */
    fun open() {
        if (items.isNotEmpty()) return
        val ctx = context()
        watch(ctx)
        show(BotResult(engine.greet(ctx), state))
        changed()
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || pendingAi != null) return
        val chip = chips.firstOrNull { it.label.equals(clean, ignoreCase = true) }

        // Buttons that open a screen don't go to the bot.
        when (val a = chip?.action) {
            is Action.OpenComplaint -> return onEvent(ChatEvent.OpenComplaint(a.complaintId))
            Action.Call -> {
                add(clean, fromUser = true)
                addNotice("Calling an agent…")
                changed()
                return onEvent(ChatEvent.StartCall)
            }
            else -> Unit
        }

        val previousText = lastInterpretation?.first
        add(clean, fromUser = true)
        val input: UserInput
        var corrected = false
        if (chip != null) {
            val action = chip.action
            when {
                action == Action.NotHelped -> penalise()
                action is Action.Pick && lastUnclear != null && action.intent !in notTaught -> {
                    val unclear = lastUnclear!!
                    memory.learn(unclear, action.intent, LearningMemory.Source.Customer, clock())
                    addNotice("Learned: \"${unclear.take(60)}\" means ${action.intent.label.lowercase()}.")
                    lastUnclear = null
                    pendingLearn = null
                    onLearned()
                }
                else -> commitPending()
            }
            input = UserInput.Choice(action)
        } else {
            if (classifier.isCorrection(clean)) {
                // "No, my food is cold" restates the same thing: that's not a mistake by the bot.
                val restated = classifier.ranked(clean).firstOrNull { it.intent !in notTaught }?.intent
                if (restated == null || restated != lastInterpretation?.third) {
                    penalise()
                    corrected = true
                }
            } else {
                commitPending()
            }
            input = UserInput.Text(clean)
            // AI-first: the AI reads every typed message. The rules still take answers to a question the bot
            // is in the middle of asking, corrections, learned phrases, food-safety reports and angry messages.
            val guess = classifier.classify(clean)
            if (aiFirst() && !corrected && state.awaiting == Awaiting.Nothing && guess.source != ReplySource.Learned &&
                guess.intent != Intent.FoodSafety && !classifier.isFrustrated(clean)
            ) {
                aiFirstGuess = guess
                pendingAi = clean
                changed()
                return
            }
        }

        val ctx = context()
        val result = engine.respond(input, state, ctx)
        if (result.reply.askAi) {
            pendingAi = clean
            changed()
            return
        }
        show(result)
        if (input is UserInput.Text) {
            val intent = result.reply.intent
            if (corrected && previousText != null && intent !in notTaught && intent != Intent.Unknown && intent != Intent.Correction) {
                // "No, my food is cold": the earlier message meant this.
                memory.learn(previousText, intent, LearningMemory.Source.Customer, clock())
                addNotice("Learned: \"${previousText.take(60)}\" means ${intent.label.lowercase()}.")
                lastUnclear = null
                onLearned()
            } else if (intent == Intent.Unknown) {
                lastUnclear = clean
            } else if (!corrected && intent != Intent.Correction) {
                lastUnclear = null
                lastInterpretation = Triple(clean, result.reply.source, intent)
            }
        }
        changed()
    }

    /** Called by the app with the AI helper's answer (null when it failed, timed out or is switched off). */
    fun onAiResult(result: AiResult?) {
        val text = pendingAi ?: return
        pendingAi = null
        val ctx = context()
        val guess = aiFirstGuess
        aiFirstGuess = null
        if (guess != null) {
            val strong = guess.intent != Intent.Unknown && guess.confidence >= SupportEngine.STRONG_ENOUGH
            // Same reading as the rules, or the AI only chatted about something the rules have a proper flow
            // for (an order card, a refund, an escalation): let the rules reply, exactly as without AI.
            val agrees = result?.intent == guess.intent
            val chatOnly = result == null || (result.intent == null && result.complaintId == null)
            if (strong && (agrees || (chatOnly && guess.intent !in faqIntents)) || result == null) {
                runRules(text, ctx)
                changed()
                return
            }
        }
        val target = result?.complaintId?.let(::noteTarget)
        when {
            result?.intent != null -> {
                val r = engine.respond(UserInput.AiIntent(result.intent, text), state, ctx)
                show(r)
                // Learn only what the rules could not read on their own.
                if (guess == null || guess.intent == Intent.Unknown || guess.confidence < SupportEngine.STRONG_ENOUGH) pendingLearn = text to result.intent
                lastInterpretation = Triple(text, ReplySource.Ai, result.intent)
                lastUnclear = null
            }
            target != null -> {
                // The app checks the reference itself: a wrong or closed one goes to the newest open complaint.
                store.addNote(target.id, text)
                val due = if (target.severity == Severity.Critical) "A person is handling it now." else "A person will respond by ${Fmt.due(target.dueAt, ctx.now)}."
                val own = "Thank you, I've added that to your complaint ${target.reference}. $due"
                add(if (result.answer != null && result.answer.contains(target.reference)) result.answer else own, fromUser = false, source = ReplySource.Ai)
                state = state.copy(misses = 0, awaiting = Awaiting.Nothing)
                chips = listOf(
                    QuickReply(Action.OpenComplaint(target.id), "View ${target.reference}"),
                    QuickReply(Action.Helped, "That helped"),
                    QuickReply(Action.Human, "Talk to a person"),
                )
                lastUnclear = null
                lastInterpretation = Triple(text, ReplySource.Ai, Intent.Unknown)
            }
            result?.answer != null -> {
                add(result.answer, fromUser = false, source = ReplySource.Ai)
                state = state.copy(misses = 0, awaiting = Awaiting.Nothing)
                chips = listOf(
                    QuickReply(Action.Helped, "That helped"),
                    QuickReply(Action.NotHelped, "Not what I meant"),
                    QuickReply(Action.Human, "Talk to a person"),
                )
                lastUnclear = text
                lastInterpretation = Triple(text, ReplySource.Ai, Intent.Unknown)
            }
            else -> runRules(text, ctx)
        }
        changed()
    }

    /** No help from the AI: the rules answer (a weak match, or "I didn't quite get that"). */
    private fun runRules(text: String, ctx: BotContext) {
        val r = engine.respond(UserInput.Text(text, allowAi = false), state, ctx)
        show(r)
        if (r.reply.intent == Intent.Unknown) {
            lastUnclear = text
        } else {
            lastUnclear = null
            lastInterpretation = Triple(text, r.reply.source, r.reply.intent)
        }
    }

    /** The open complaint new information goes to: the one the AI named, else the newest open one. */
    private fun noteTarget(id: String): Complaint? {
        val exact = store.complaint(id)
        if (exact != null && exact.status.isOpen) return exact
        return store.sorted().firstOrNull { it.status.isOpen }
    }

    /** Call when the orders change, so the bot can post "your driver picked it up" updates. */
    fun onOrdersChanged() {
        if (applyingAction) return
        val ctx = context()
        val active = ctx.orders.firstOrNull { it.active }
        val moved = active != null && active.id == watchedOrderId && active.state != watchedState
        watch(ctx)
        if (moved && items.isNotEmpty()) {
            engine.statusUpdate(active!!)?.let {
                show(BotResult(it, state))
                changed()
            }
        }
    }

    /** A small grey line in the chat, e.g. after a call with an agent. */
    fun note(text: String) {
        addNotice(text)
        transcript += TranscriptLine(false, text, clock())
        changed()
    }

    fun reset() {
        items = emptyList()
        chips = emptyList()
        pendingAi = null
        state = ConversationState()
        transcript.clear()
        lastUnclear = null
        lastInterpretation = null
        pendingLearn = null
        aiFirstGuess = null
        watchedOrderId = null
        watchedState = null
        changed()
    }

    // -----------------------------------------------------------------------------------------

    private fun show(result: BotResult) {
        state = result.state
        val reply = result.reply
        // The card (order summary etc.) goes after the first sentence.
        reply.texts.forEachIndexed { i, t ->
            add(t, fromUser = false, source = reply.source)
            if (i == 0) reply.card?.let { add("", fromUser = false, card = it, source = reply.source) }
        }
        if (reply.texts.isEmpty()) reply.card?.let { add("", fromUser = false, card = it, source = reply.source) }
        reply.orderAction?.let {
            // The order change is the bot's own doing: don't also post an "Update:" about it.
            applyingAction = true
            try {
                onOrderAction(it)
            } finally {
                applyingAction = false
            }
            watch(context())
        }
        reply.chaseComplaintId?.let(store::chase)

        var list = reply.quickReplies
        val escalation = reply.escalation
        if (escalation != null) {
            val outcome = store.escalate(escalation, transcript.toList())
            add(escalationText(outcome), fromUser = false)
            list = list + QuickReply(Action.OpenComplaint(outcome.complaint.id), "View ${outcome.complaint.reference}") +
                QuickReply(Action.Menu, "Something else")
        }
        // e.g. "You also mentioned something missing. Shall we sort that out now?"
        reply.afterTexts.forEach { add(it, fromUser = false, source = reply.source) }
        if (escalation != null) {
            // the complaint's buttons are already there
        } else if (reply.answered && list.none { it.action == Action.Helped }) {
            list = list + QuickReply(Action.Helped, "That helped")
        }
        val hasPerson = list.any { it.action == Action.Human || it.action == Action.Call }
        if (!hasPerson && escalation == null && reply.intent != Intent.Goodbye) list = list + QuickReply(Action.Human, "Talk to a person")
        val unique = list.distinctBy { it.label.lowercase() }
        // Keep the way to a person visible even when a long menu is cut short.
        val person = unique.lastOrNull { it.action == Action.Human || it.action == Action.Call }
        chips = if (unique.size <= MAX_CHIPS || person == null) unique.take(MAX_CHIPS)
        else unique.filter { it !== person }.take(MAX_CHIPS - 1) + person
    }

    private fun escalationText(o: SupportStore.Outcome): String {
        val c = o.complaint
        val now = clock()
        val due = if (c.severity == Severity.Critical) "A person is being assigned right now." else "A person will respond by ${Fmt.due(c.dueAt, now)}."
        val refund = c.refund?.let { " Refund requested: ${Fmt.money(it.amount)} ${it.method.label}." } ?: ""
        return if (o.joined) {
            "I've added this to your open complaint ${c.reference} (${c.severity.label} priority). $due$refund"
        } else {
            "Complaint ${c.reference} is logged with ${c.severity.label} priority. $due$refund You can follow it under My complaints."
        }
    }

    private fun penalise() {
        memory.mistakes++
        val li = lastInterpretation
        if (li != null && li.second == ReplySource.Learned) memory.forget(li.first)
        pendingLearn = null
        lastUnclear = li?.first ?: lastUnclear
        lastInterpretation = null
        onLearned()
    }

    private fun commitPending() {
        val p = pendingLearn ?: return
        memory.learn(p.first, p.second, LearningMemory.Source.Ai, clock())
        pendingLearn = null
        onLearned()
    }

    private fun watch(ctx: BotContext) {
        val active = ctx.orders.firstOrNull { it.active }
        watchedOrderId = active?.id
        watchedState = active?.state
    }

    private fun add(text: String, fromUser: Boolean, card: InfoCard? = null, source: ReplySource = ReplySource.Rules) {
        val now = clock()
        items = items + ChatItem(nextId++, text, fromUser, now, card, source)
        val line = card?.let { c -> c.title + ": " + c.rows.joinToString(", ") { "${it.first} ${it.second}" } } ?: text
        transcript += TranscriptLine(fromUser, line, now)
    }

    private fun addNotice(text: String) {
        items = items + ChatItem(nextId++, text, fromUser = false, at = clock(), notice = true)
    }

    private fun changed() = listeners.forEach { it() }

    /** Recent lines, for the AI helper. */
    fun recentTranscript(): List<TranscriptLine> = transcript.takeLast(8)

    companion object {
        const val MAX_CHIPS = 7
        /** Plain questions the AI may answer in its own words even when the rules recognise them. */
        private val faqIntents = setOf(
            Intent.SmallTalk, Intent.ServiceInfo, Intent.Fees, Intent.Promo, Intent.Tip, Intent.PaymentMethods, Intent.Pickup,
            Intent.Allergens, Intent.Account, Intent.Rate, Intent.DeliveryInstructions,
        )
        private val notTaught = setOf(Intent.Greeting, Intent.Thanks, Intent.Goodbye, Intent.Unknown, Intent.Correction)
    }
}
