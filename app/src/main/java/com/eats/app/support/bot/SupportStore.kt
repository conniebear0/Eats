package com.eats.app.support.bot

import kotlin.random.Random

/**
 * The signed-in customer's complaints and refunds. Kept in memory here; the app saves and restores
 * the list on the phone (SupportPersistence) so complaints survive closing the app.
 *
 * Rules (from the guidelines):
 *  - One open complaint per order and category: a second hand-over about the same thing is added to it.
 *  - The response deadline comes from the severity matrix (Critical immediately, High 24 h,
 *    Medium 48 h, Low 72 h).
 *  - Refunds are only ever requested by the bot; an agent approves and pays them.
 */
class SupportStore(
    initial: List<Complaint> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    data class Outcome(val complaint: Complaint, val joined: Boolean)

    var complaints: List<Complaint> = initial
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun replaceAll(list: List<Complaint>) {
        complaints = list
        changed()
    }

    fun complaint(id: String): Complaint? = complaints.firstOrNull { it.id == id }

    /** Newest first. */
    fun sorted(): List<Complaint> = complaints.sortedByDescending { it.createdAt }

    fun escalate(e: Escalation, transcript: List<TranscriptLine>): Outcome {
        val now = clock()
        val open = complaints.filter {
            it.status.isOpen && e.orderId != null && it.orderId == e.orderId &&
                (it.category == e.category || e.category == Category.CustomerService || e.category == Category.PaymentIssue)
        }.maxByOrNull { it.createdAt }
        val newRefund = e.refund?.let { Refund(it.amount, e.refundMethod ?: RefundMethod.Card, RefundStatus.Requested, it.reason) }
        // A refund that's already been approved, paid or declined isn't reopened by a new request.
        val settled = open?.refund != null && e.refund != null && open.refund.status != RefundStatus.Requested
        if (open != null && !settled) {
            val severity = if (e.severity.ordinal > open.severity.ordinal) e.severity else open.severity
            // Two refund requests on one order (e.g. a missing drink, then a cold burger) become one total.
            val merged = open.refund != null && e.refund != null
            val refund = if (merged) {
                open.refund!!.copy(amount = open.refund.amount + e.refund!!.amount, reason = "${open.refund.reason}; ${e.refund.reason}")
            } else open.refund ?: newRefund
            val updated = open.copy(
                severity = severity,
                dueAt = if (severity != open.severity) dueFor(severity, now) else open.dueAt,
                refund = refund,
                events = open.events + ComplaintEvent(
                    now,
                    "Added from chat: ${e.description}" + if (merged) " Refund now ${Fmt.money(refund!!.amount)} in total." else "",
                    "bot",
                ),
                transcript = open.transcript + transcript,
            )
            complaints = complaints.map { if (it.id == open.id) updated else it }
            changed()
            return Outcome(updated, joined = true)
        }
        val complaint = Complaint(
            id = newId(),
            orderId = e.orderId,
            restaurantName = e.restaurantName,
            category = e.category,
            subcategory = e.subcategory,
            severity = e.severity,
            status = ComplaintStatus.Open,
            createdAt = now,
            dueAt = dueFor(e.severity, now),
            description = e.description,
            refund = newRefund,
            events = listOf(
                ComplaintEvent(now, "Logged by the chatbot. Sent to ${e.category.department}.", "bot", ComplaintStatus.Open),
            ) + (e.refund?.let { listOf(ComplaintEvent(now, "Refund of ${Fmt.money(it.amount)} requested ${(e.refundMethod ?: RefundMethod.Card).label}.", "bot")) } ?: emptyList()),
            transcript = transcript,
        )
        complaints = complaints + complaint
        changed()
        return Outcome(complaint, joined = false)
    }

    /** Raises a complaint to a supervisor: at least High priority, a fresh 24-hour deadline. */
    fun chase(id: String) = update(id) { c ->
        val now = clock()
        val severity = if (c.severity.ordinal < Severity.High.ordinal) Severity.High else c.severity
        val reopened = c.refund?.status == RefundStatus.Declined && !c.status.isOpen
        c.copy(
            severity = severity,
            escalated = true,
            status = if (reopened) ComplaintStatus.Open else c.status,
            refund = if (reopened) c.refund?.copy(status = RefundStatus.Requested) else c.refund,
            dueAt = dueFor(severity, now),
            events = c.events + ComplaintEvent(now, "Raised to a supervisor at the customer's request.", "bot", if (reopened) ComplaintStatus.Open else null),
        )
    }

    /** Demo stand-in for a support agent: Open → Pending (refund approved) → Resolved (refund paid). */
    fun advance(id: String) = update(id) { c ->
        val now = clock()
        when (c.status) {
            ComplaintStatus.Open -> c.copy(
                status = ComplaintStatus.Pending,
                refund = c.refund?.let { if (it.status == RefundStatus.Requested) it.copy(status = RefundStatus.Approved) else it },
                events = c.events + ComplaintEvent(now, "An agent is working on it." + (c.refund?.let { " Refund of ${Fmt.money(it.amount)} approved." } ?: ""), "agent", ComplaintStatus.Pending),
            )
            ComplaintStatus.Pending -> c.copy(
                status = ComplaintStatus.Resolved,
                refund = c.refund?.let { if (it.status == RefundStatus.Approved || it.status == RefundStatus.Requested) it.copy(status = RefundStatus.Paid) else it },
                events = c.events + ComplaintEvent(now, "Resolved by an agent." + (c.refund?.let { " Refund of ${Fmt.money(it.amount)} paid ${it.method.label}." } ?: ""), "agent", ComplaintStatus.Resolved),
            )
            else -> c
        }
    }

    /** Demo: the agent declines the refund (the complaint is resolved without one). */
    fun declineRefund(id: String) = update(id) { c ->
        val now = clock()
        if (c.refund == null || !c.status.isOpen) c else c.copy(
            status = ComplaintStatus.Resolved,
            refund = c.refund.copy(status = RefundStatus.Declined),
            events = c.events + ComplaintEvent(now, "Refund declined by an agent.", "agent", ComplaintStatus.Resolved),
        )
    }

    /** The customer confirms it's sorted. */
    fun close(id: String) = update(id) { c ->
        c.copy(status = ComplaintStatus.Closed, events = c.events + ComplaintEvent(clock(), "Closed: the customer confirmed it's sorted.", "you", ComplaintStatus.Closed))
    }

    /** The customer says it isn't fixed: back to Open with a higher priority. */
    fun reopen(id: String) = update(id) { c ->
        val now = clock()
        val severity = c.severity.raised()
        c.copy(
            status = ComplaintStatus.Open,
            severity = severity,
            dueAt = dueFor(severity, now),
            reopened = c.reopened + 1,
            events = c.events + ComplaintEvent(now, "Reopened by the customer. Priority raised to ${severity.label}.", "you", ComplaintStatus.Open),
        )
    }

    /** The customer no longer wants it followed up: closed without a fix (Unresolved). */
    fun withdraw(id: String) = update(id) { c ->
        c.copy(status = ComplaintStatus.Unresolved, events = c.events + ComplaintEvent(clock(), "Withdrawn by the customer.", "you", ComplaintStatus.Unresolved))
    }

    /** A short case summary written by the AI helper for the agent (kept out of the customer's timeline). */
    fun addBrief(id: String, text: String) {
        val clean = text.trim().take(400)
        if (clean.isEmpty()) return
        update(id) { c -> c.copy(events = c.events + ComplaintEvent(clock(), "Summary for the agent: $clean", BY_ASSISTANT)) }
    }

    fun addNote(id: String, text: String) {
        val clean = text.trim().take(500)
        if (clean.isEmpty()) return
        update(id) { c ->
            val now = clock()
            c.copy(
                events = c.events + ComplaintEvent(now, "You added: $clean", "you"),
                transcript = c.transcript + TranscriptLine(true, clean, now),
            )
        }
    }

    private fun update(id: String, change: (Complaint) -> Complaint) {
        var touched = false
        complaints = complaints.map { if (it.id == id) change(it).also { touched = true } else it }
        if (touched) changed()
    }

    private fun dueFor(severity: Severity, now: Long): Long = now + severity.responseHours * 3_600_000L

    private fun newId(): String {
        var id: String
        do {
            id = (1..ID_LENGTH).map { ID_CHARS[random.nextInt(ID_CHARS.length)] }.joinToString("")
        } while (complaints.any { it.id == id })
        return id
    }

    private fun changed() = listeners.forEach { it() }

    companion object {
        private const val ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val ID_LENGTH = 6
        /** Events written by the AI helper for agents; the app doesn't show them to the customer. */
        const val BY_ASSISTANT = "assistant"
    }
}
