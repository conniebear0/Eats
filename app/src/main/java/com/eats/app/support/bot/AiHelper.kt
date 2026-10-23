package com.eats.app.support.bot

/**
 * The AI helper. With a key set it reads every typed message first ("AI-first", see SupportChat), and the
 * built-in rules handle anything it can't. It is never allowed to act on its own. It may only:
 *  1. say which of the bot's intents the customer meant (the decision table then handles it),
 *  2. answer a question from the facts it is given (orders, complaints, policies), or
 *  3. say which open complaint new information belongs to (the app checks the reference itself).
 * Answers that promise refunds or money, or ask for card details, are thrown away.
 *
 * Default provider: Groq (free plan, about 1,000 requests a day; it does not train on your data).
 * Any OpenAI-compatible endpoint works by changing [AiSettings.baseUrl] and [AiSettings.model].
 */
data class AiSettings(
    val apiKey: String = BUILT_IN_KEY,
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val enabled: Boolean = true,
) {
    val usable: Boolean get() = enabled && apiKey.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_MODEL = "openai/gpt-oss-20b"
        /**
         * The project's Groq key, so the AI helper works out of the box for testing and presentations.
         * Anyone with the app can read it: revoke it at console.groq.com/keys when you're done, and
         * never ship a real product with a key inside the app (use your own server instead).
         */
        const val BUILT_IN_KEY = "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"
    }
}

/** [complaintId]: the customer added information to this open complaint (6-character id, no "C-"). */
data class AiResult(val intent: Intent?, val answer: String?, val complaintId: String? = null)

/** Implemented by the app with a real HTTP call (see GroqAiHelper). */
fun interface AiHelper {
    suspend fun interpret(message: String, ctx: BotContext, recent: List<TranscriptLine>): AiResult?

    /** A two-sentence case summary for the agent who picks up [c]; null if unavailable. */
    suspend fun brief(c: Complaint): String? = null
}

object AiPrompt {

    private fun statusMeaning(s: ComplaintStatus) = when (s) {
        ComplaintStatus.Open -> "waiting for a person"
        ComplaintStatus.Pending -> "an agent is working on it"
        ComplaintStatus.Resolved -> "fixed"
        ComplaintStatus.Closed -> "customer confirmed it is sorted"
        ComplaintStatus.Unresolved -> "withdrawn by the customer"
    }

    private fun refundMeaning(s: RefundStatus) = when (s) {
        RefundStatus.Requested -> "waiting for approval"
        RefundStatus.Approved -> "approved, being paid"
        RefundStatus.Paid -> "paid"
        RefundStatus.Declined -> "declined after review"
    }

    private fun describe(order: BotOrder): String {
        var f = "${order.id} from ${order.restaurantName} (${if (order.active) "current order" else "past order, placed " + Fmt.dateTime(order.placedAt)}), status ${order.state.label}"
        if (order.minutesLeft != null && order.state.inProgress) f += ", about ${order.minutesLeft} min to go"
        if (order.minutesLate > 0) f += ", running ${order.minutesLate} min late"
        if (order.deliveredAt != null && order.deliveredAt != 0L) f += ", delivered ${Fmt.dateTime(order.deliveredAt)}"
        f += ". Items: " + order.lines.joinToString("; ") { "${it.qty} x ${it.name} R${it.total}" }
        f += ". Total R${order.total}, paid by ${order.pay.label} ${if (order.pickup) "at pick-up" else "on delivery"}"
        if (!order.pickup) f += ", delivery address ${order.address}, delivery fee R${order.deliveryFee}"
        if (!order.driverName.isNullOrEmpty()) f += ", driver ${order.driverName}"
        return f.replace(Regex("""\.+$"""), "") + "."
    }

    private fun describe(c: Complaint, now: Long): String {
        var f = "${c.reference}: ${c.subcategory} (${c.category.label}, handled by ${c.category.department})"
        if (!c.orderId.isNullOrEmpty()) f += ", order ${c.orderId}"
        f += ", logged ${Fmt.dateTime(c.createdAt)}, status ${c.status.label} (${statusMeaning(c.status)}), priority ${c.severity.label}"
        if (c.status.isOpen) {
            f += when {
                c.overdue(now) -> ", OVERDUE (was due ${Fmt.due(c.dueAt, now)})"
                c.severity == Severity.Critical -> ", a person is handling it now"
                else -> ", a person will respond by ${Fmt.due(c.dueAt, now)}"
            }
        }
        c.refund?.let {
            f += ". Refund R${it.amount} ${it.method.label}, refund status ${it.status.label} (${refundMeaning(it.status)}; once paid it arrives ${it.method.timing})"
        }
        if (c.escalated) f += ". Already raised to a supervisor"
        c.events.lastOrNull()?.let { f += ". Latest update: ${it.text.take(160)}" }
        return "$f."
    }

    fun system(ctx: BotContext): String {
        val intents = Intent.aiChoices.joinToString("\n") { "- ${it.name}: ${it.label}" }
        val orderFacts = if (ctx.orders.isEmpty()) "- The customer has no orders yet." else ctx.orders.take(4).joinToString("\n") { "- " + describe(it) }
        val complaints = ctx.complaints.take(6)
        val complaintFacts = if (complaints.isEmpty()) "- No complaints logged." else complaints.joinToString("\n") { "- " + describe(it, ctx.now) }
        return """
You are the support assistant inside "eats", a South African food delivery app (like Uber Eats). The customer's first name is ${ctx.firstName}. You work alongside a rule-based support bot that handles refunds, escalations and order changes.
Reply ONLY with a JSON object: {"intent": "<one intent name from the list, or none>", "answer": "<short reply or empty>", "add_to_complaint": "<the exact reference of one of the customer's open complaints listed below, or empty>"}.

How to decide:
- If the message reports a NEW problem or asks for an action that matches an intent below, return that intent name and an empty answer. The bot then follows its own resolution rules.
- If the customer gives new information about one of their OPEN complaints (what happened, evidence such as a photo or a sealed bag, the driver's behaviour, when they are available), return "none", set add_to_complaint to that complaint's reference, and in the answer thank them, say you have added it to that complaint and when a person will respond.
- If they ask about a complaint, refund, order or how things work, return "none" and answer from the facts below: say what the status means and what happens next, with the deadline. Be specific and use the references and amounts.
- If it is not about food orders or this app, return "none" and politely say you can only help with eats orders.
- If you are not sure, return "none" and an empty answer.

Style: warm, calm, plain South African English, at most 3 short sentences, no lists, no markdown. Acknowledge frustration briefly when it is there.
Never promise, approve or confirm a refund, credit, payment or compensation that the facts do not show as already approved or paid. Never change a complaint's status or priority. Never ask for card numbers, PINs or passwords. Never invent order details, times, names or policies. If the customer wants a person, return the intent TalkToHuman (or CallAgent if they want a phone call) instead of answering.

Intents:
$intents

How complaints work:
- Statuses: Open = logged, waiting for a person; Pending = an agent is working on it and any refund is approved; Resolved = fixed and any refund paid; Closed = the customer confirmed it is sorted; Unresolved = withdrawn by the customer.
- Response deadlines by priority: Critical immediately, High within 24 hours, Medium within 48 hours, Low within 72 hours. Overdue complaints can be raised to a supervisor.
- Refunds go back to the card for card orders; cash orders choose a bank transfer (3 to 5 business days) or eats credit (straight away). A pending card amount is an authorisation hold the bank releases.
- The customer can follow every complaint under Profile, My complaints, add information, ask a supervisor, reopen or withdraw it, or call an agent with the phone icon in this chat.

Facts:
- ${Guidelines.FEES}
- ${Guidelines.PROMO}
- ${Guidelines.TIP}
- ${Guidelines.PAY}
- ${Guidelines.PICKUP}
- ${Guidelines.ALLERGENS}
- ${Guidelines.ACCOUNT}
- ${Guidelines.CANT_DO}
- Support hours: ${Guidelines.SUPPORT_HOURS}. Problems with food should be reported within ${Guidelines.FOOD_ISSUE_REPORT_HOURS} hours; refund requests within ${Guidelines.REFUND_REQUEST_DAYS} days.
- Right now it is ${Fmt.dateTime(ctx.now)}.

The customer's orders (newest first):
$orderFacts

The customer's complaints (newest first):
$complaintFacts
""".trim()
    }

    /** Asks for a two-sentence case summary for the agent who picks up a complaint. */
    fun briefSystem(): String =
        "You write case notes for human support agents at eats, a South African food delivery app. Reply ONLY with JSON {\"summary\": \"<at most 2 short sentences>\"}: " +
            "what went wrong, what the customer wants, and any risk (health, money, safety, repeat problem). Use only facts from the conversation. No greetings, no promises."

    fun briefUser(c: Complaint): String {
        val lines = c.transcript.takeLast(14).joinToString("\n") { (if (it.fromCustomer) "Customer: " else "Bot: ") + it.text.take(240) }
        return "Complaint ${c.reference}: ${c.subcategory}, priority ${c.severity.label}" +
            (if (!c.orderId.isNullOrEmpty()) ", order ${c.orderId}" else "") +
            (c.refund?.let { ", refund requested R${it.amount}" } ?: "") +
            ".\nConversation:\n$lines"
    }

    fun parseBrief(content: String?): String? {
        val t = content?.let { stringField(it, "summary") }?.trim() ?: return null
        return if (t.isNotEmpty() && safe(t)) t.take(400) else null
    }

    fun user(message: String, recent: List<TranscriptLine>): String {
        val history = recent.takeLast(6).joinToString("\n") { (if (it.fromCustomer) "Customer: " else "Bot: ") + it.text.take(300) }
        return (if (history.isNotEmpty()) "Recent conversation:\n$history\n\n" else "") + "Customer's new message: ${message.take(600)}"
    }

    /** The JSON body for an OpenAI-compatible /chat/completions call. */
    fun requestBody(settings: AiSettings, system: String, user: String): String =
        "{\"model\":${json(settings.model)},\"temperature\":0.2,\"max_tokens\":700," +
            (if (settings.model.startsWith("openai/gpt-oss")) "\"reasoning_effort\":\"low\"," else "") +
            "\"response_format\":{\"type\":\"json_object\"}," +
            "\"messages\":[{\"role\":\"system\",\"content\":${json(system)}},{\"role\":\"user\",\"content\":${json(user)}}]}"

    /** Pulls the assistant's message out of a /chat/completions response. */
    fun content(responseBody: String): String? = stringField(responseBody, "content")

    /** Reads the model's {"intent", "answer", "add_to_complaint"} reply and applies the safety rules. */
    fun parse(content: String?): AiResult? {
        if (content.isNullOrBlank()) return null
        val name = stringField(content, "intent")?.trim()
        val intent = Intent.aiChoices.firstOrNull { name != null && it.name.equals(name, ignoreCase = true) }
        val answer = stringField(content, "answer")?.trim()?.take(MAX_ANSWER)?.takeIf { it.isNotEmpty() && safe(it) }
        val ref = stringField(content, "add_to_complaint")
        val m = if (!ref.isNullOrEmpty()) Regex("C-?([A-Z0-9]{6})", RegexOption.IGNORE_CASE).find(ref) else null
        val complaintId = if (intent == null && m != null) m.groupValues[1].uppercase() else null
        if (intent == null && answer == null && complaintId == null) return null
        return AiResult(intent, if (intent != null) null else answer, complaintId)
    }

    /** Answers that promise money or ask for sensitive details are not shown. */
    fun safe(answer: String): Boolean {
        val t = TextNormaliser.normalise(answer)
        return unsafe.none { t.contains(it) }
    }

    private val unsafe = listOf(
        "refund has been", "refunded you", "i have refunded", "ive refunded", "you will be refunded", "you will get a refund",
        "approved your refund", "refund is approved", "i approved", "credited", "i will refund", "ill refund",
        "card number", "your pin", "cvv",
    )

    private const val MAX_ANSWER = 600

    /** Finds "key": "value" in a JSON text and unescapes the value. Good enough for these small replies. */
    fun stringField(text: String, key: String): String? {
        val m = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(text) ?: return null
        return unescape(m.groupValues[1])
    }

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> Unit
                    'u' -> if (i + 5 < s.length) {
                        s.substring(i + 2, i + 6).toIntOrNull(16)?.let { out.append(it.toChar()) }
                        i += 4
                    }
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    fun json(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> b.append("\\\"")
                c == '\\' -> b.append("\\\\")
                c == '\n' -> b.append("\\n")
                c == '\r' -> b.append("\\r")
                c == '\t' -> b.append("\\t")
                c.code < 0x20 -> b.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> b.append(c)
            }
        }
        return b.append('"').toString()
    }
}
