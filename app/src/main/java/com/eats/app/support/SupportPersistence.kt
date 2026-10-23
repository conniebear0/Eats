package com.eats.app.support

import android.content.Context
import com.eats.app.support.bot.AiSettings
import com.eats.app.support.bot.Category
import com.eats.app.support.bot.Complaint
import com.eats.app.support.bot.ComplaintEvent
import com.eats.app.support.bot.ComplaintStatus
import com.eats.app.support.bot.Intent
import com.eats.app.support.bot.LearningMemory
import com.eats.app.support.bot.Refund
import com.eats.app.support.bot.RefundMethod
import com.eats.app.support.bot.RefundStatus
import com.eats.app.support.bot.Severity
import com.eats.app.support.bot.TranscriptLine
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves the support data on the phone:
 *  - each user's complaints (with refunds, history and chat transcript),
 *  - what the chatbot has learned (shared by everyone on this phone, because it is about wording),
 *  - the AI helper settings.
 * Swap this for API calls when there is a backend; the chatbot itself doesn't change.
 */
class SupportPersistence(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("eats_support", Context.MODE_PRIVATE)

    // --- Complaints ---

    fun loadComplaints(userId: String): List<Complaint> {
        val json = prefs.getString("complaints_$userId", null) ?: return emptyList()
        return runCatching { JSONArray(json).objects().mapNotNull { runCatching { toComplaint(it) }.getOrNull() } }
            .getOrDefault(emptyList()) // a damaged record should not stop the app
    }

    fun saveComplaints(userId: String, complaints: List<Complaint>) {
        prefs.edit().putString("complaints_$userId", JSONArray(complaints.map(::fromComplaint)).toString()).apply()
    }

    private fun fromComplaint(c: Complaint) = JSONObject()
        .put("id", c.id)
        .put("orderId", c.orderId ?: "")
        .put("restaurantName", c.restaurantName ?: "")
        .put("category", c.category.name)
        .put("subcategory", c.subcategory)
        .put("severity", c.severity.name)
        .put("status", c.status.name)
        .put("createdAt", c.createdAt)
        .put("dueAt", c.dueAt)
        .put("description", c.description)
        .put("reopened", c.reopened)
        .put("escalated", c.escalated)
        .put(
            "refund",
            c.refund?.let {
                JSONObject().put("amount", it.amount).put("method", it.method.name).put("status", it.status.name).put("reason", it.reason)
            } ?: JSONObject.NULL,
        )
        .put(
            "events",
            JSONArray(c.events.map { e -> JSONObject().put("at", e.at).put("text", e.text).put("by", e.by).put("status", e.status?.name ?: "") }),
        )
        .put(
            "transcript",
            JSONArray(c.transcript.map { t -> JSONObject().put("customer", t.fromCustomer).put("text", t.text).put("at", t.at) }),
        )

    private fun toComplaint(o: JSONObject): Complaint {
        val refund = o.optJSONObject("refund")?.let {
            Refund(it.getInt("amount"), RefundMethod.valueOf(it.getString("method")), RefundStatus.valueOf(it.getString("status")), it.optString("reason"))
        }
        return Complaint(
            id = o.getString("id"),
            orderId = o.optString("orderId").ifBlank { null },
            restaurantName = o.optString("restaurantName").ifBlank { null },
            category = Category.valueOf(o.getString("category")),
            subcategory = o.getString("subcategory"),
            severity = Severity.valueOf(o.getString("severity")),
            status = ComplaintStatus.valueOf(o.getString("status")),
            createdAt = o.getLong("createdAt"),
            dueAt = o.getLong("dueAt"),
            description = o.optString("description"),
            refund = refund,
            events = o.optJSONArray("events").objects().map { e ->
                ComplaintEvent(
                    e.getLong("at"), e.getString("text"), e.optString("by"),
                    e.optString("status").takeIf { it.isNotBlank() }?.let { runCatching { ComplaintStatus.valueOf(it) }.getOrNull() },
                )
            },
            transcript = o.optJSONArray("transcript").objects().map { t ->
                TranscriptLine(t.getBoolean("customer"), t.getString("text"), t.getLong("at"))
            },
            reopened = o.optInt("reopened"),
            escalated = o.optBoolean("escalated"),
        )
    }

    // --- Learning ---

    fun loadMemory(): LearningMemory {
        val json = prefs.getString("learned", null) ?: return LearningMemory()
        return runCatching {
            val o = JSONObject(json)
            val entries = o.optJSONArray("entries").objects().mapNotNull { e ->
                runCatching {
                    LearningMemory.Learned(
                        e.getString("phrase"), Intent.valueOf(e.getString("intent")), e.getInt("hits"),
                        LearningMemory.Source.valueOf(e.getString("source")), e.getLong("at"),
                    )
                }.getOrNull()
            }
            LearningMemory(entries).also { it.mistakes = o.optInt("mistakes") }
        }.getOrDefault(LearningMemory())
    }

    fun saveMemory(memory: LearningMemory) {
        val o = JSONObject()
            .put("mistakes", memory.mistakes)
            .put(
                "entries",
                JSONArray(
                    memory.all.map {
                        JSONObject().put("phrase", it.phrase).put("intent", it.intent.name).put("hits", it.hits)
                            .put("source", it.source.name).put("at", it.learnedAt)
                    },
                ),
            )
        prefs.edit().putString("learned", o.toString()).apply()
    }

    // --- AI helper ---

    fun loadAi(): AiSettings = AiSettings(
        // An empty saved key (from an older version, or cleared in settings) falls back to the built-in key.
        apiKey = prefs.getString("ai_key", null)?.trim()?.ifBlank { null } ?: AiSettings.BUILT_IN_KEY,
        baseUrl = prefs.getString("ai_url", null) ?: AiSettings.DEFAULT_BASE_URL,
        model = prefs.getString("ai_model", null) ?: AiSettings.DEFAULT_MODEL,
        enabled = prefs.getBoolean("ai_enabled", true),
    )

    fun saveAi(s: AiSettings) {
        prefs.edit()
            .putString("ai_key", s.apiKey.trim().ifBlank { AiSettings.BUILT_IN_KEY })
            .putString("ai_url", s.baseUrl.trim().ifBlank { AiSettings.DEFAULT_BASE_URL })
            .putString("ai_model", s.model.trim().ifBlank { AiSettings.DEFAULT_MODEL })
            .putBoolean("ai_enabled", s.enabled)
            .apply()
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}
