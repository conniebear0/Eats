package com.eats.app.support

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eats.app.auth.User
import com.eats.app.order.OrderViewModel
import com.eats.app.support.bot.AiSettings
import com.eats.app.support.bot.BotContext
import com.eats.app.support.bot.ChatEvent
import com.eats.app.support.bot.ChatItem
import com.eats.app.support.bot.Complaint
import com.eats.app.support.bot.LearningMemory
import com.eats.app.support.bot.OrderAction
import com.eats.app.support.bot.SupportChat
import com.eats.app.support.bot.SupportStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The support chat, complaints, learning and AI settings for the signed-in user.
 * The chatbot itself is plain Kotlin in support/bot; this class connects it to the app:
 * it reads the orders from [orders], saves everything with [SupportPersistence], runs the AI helper
 * on a background thread, and carries out the order changes the bot is allowed to make.
 *
 * AI-first: while the AI helper works, it reads every typed message (SupportChat decides when the rules
 * answer instead). If it fails, the bot answers on its own and tries the AI again a minute later;
 * customers never see AI errors (they go to Logcat, tag "eats-ai", and to "Test connection").
 */
class SupportViewModel(context: Context, private val orders: OrderViewModel) : ViewModel() {

    private val saved = SupportPersistence(context)
    private var userId: String? = null
    private var firstName = "there"

    private val _ai = MutableStateFlow(saved.loadAi())
    val aiSettings: StateFlow<AiSettings> = _ai.asStateFlow()
    private val ai = GroqAiHelper { _ai.value }
    private var aiProblem: String? = null
    private var aiProblemAt = 0L

    private val memory: LearningMemory = saved.loadMemory()
    val store = SupportStore()

    private val chat = SupportChat(
        store = store,
        memory = memory,
        onOrderAction = ::applyOrderAction,
        onEvent = { _events.tryEmit(it) },
        onLearned = ::saveLearning,
    )

    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()

    private val _chips = MutableStateFlow<List<String>>(emptyList())
    val chips: StateFlow<List<String>> = _chips.asStateFlow()

    private val _typing = MutableStateFlow(false)
    val typing: StateFlow<Boolean> = _typing.asStateFlow()

    private val _complaints = MutableStateFlow<List<Complaint>>(emptyList())
    val complaints: StateFlow<List<Complaint>> = _complaints.asStateFlow()

    private val _learned = MutableStateFlow(memory.all)
    val learned: StateFlow<List<LearningMemory.Learned>> = _learned.asStateFlow()

    private val _mistakes = MutableStateFlow(memory.mistakes)
    val mistakes: StateFlow<Int> = _mistakes.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 8)
    /** Screens to open (a complaint, the call screen). */
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    init {
        chat.context = {
            BotContext(firstName, orders.botOrders(), store.sorted(), System.currentTimeMillis(), _ai.value.usable)
        }
        chat.aiFirst = { _ai.value.usable && (aiProblem == null || System.currentTimeMillis() - aiProblemAt > AI_RETRY_MS) }
        chat.addListener {
            _items.value = chat.items
            _chips.value = chat.chips.map { it.label }
            _typing.value = chat.pendingAi != null
        }
        store.addListener {
            _complaints.value = store.sorted()
            userId?.let { saved.saveComplaints(it, store.complaints) }
        }
        // Post "your driver picked it up" style updates while the customer is chatting.
        viewModelScope.launch { orders.state.collect { chat.onOrdersChanged() } }
    }

    /** Loads (or clears) the support data when the signed-in user changes. */
    fun bindUser(user: User?) {
        if (user?.id == userId) return
        userId = user?.id
        firstName = user?.firstName?.ifBlank { null } ?: "there"
        store.replaceAll(user?.let { saved.loadComplaints(it.id) } ?: emptyList())
        chat.reset()
    }

    fun open() = chat.open()

    fun send(text: String) {
        val before = store.complaints.map { it.id }.toSet()
        chat.send(text)
        val pending = chat.pendingAi
        if (pending == null) {
            briefNewComplaints(before)
            return
        }
        viewModelScope.launch {
            val result = try {
                ai.interpret(pending, chat.context(), chat.recentTranscript()).also { aiProblem = null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportAiProblem(e)
                null
            }
            chat.onAiResult(result)
            briefNewComplaints(before)
        }
    }

    /** Not shown to customers: the bot simply answers on its own. */
    private fun reportAiProblem(e: Exception) {
        val why = e.message ?: "network error"
        aiProblemAt = System.currentTimeMillis()
        if (aiProblem == why) return
        aiProblem = why
        Log.w("eats-ai", "AI helper unavailable: $why")
    }

    /** A complaint the bot just logged gets a short AI summary in its timeline, for the agent. */
    private fun briefNewComplaints(before: Set<String>) {
        store.complaints.filter { it.id !in before }.forEach { c ->
            viewModelScope.launch {
                val summary = try { ai.brief(c) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                if (summary != null) store.addBrief(c.id, summary)
            }
        }
    }

    /** Called when the (demo) call screen is closed. */
    fun callEnded(agent: String, seconds: Int) {
        if (seconds <= 0) chat.note("Call to eats support cancelled.")
        else chat.note("Call with $agent ended (%d:%02d).".format(seconds / 60, seconds % 60))
    }

    // --- Complaints ---
    fun complaint(id: String): Complaint? = store.complaint(id)
    fun addNote(id: String, text: String) = store.addNote(id, text)
    fun closeComplaint(id: String) = store.close(id)
    fun reopenComplaint(id: String) = store.reopen(id)
    fun withdrawComplaint(id: String) = store.withdraw(id)
    fun chaseComplaint(id: String) = store.chase(id)
    /** Demo: stands in for a support agent working on it. */
    fun advanceComplaint(id: String) = store.advance(id)
    fun declineRefund(id: String) = store.declineRefund(id)

    // --- Learning ---
    fun forget(phrase: String) {
        memory.forget(phrase)
        saveLearning()
    }

    fun forgetAll() {
        memory.clear()
        saveLearning()
    }

    private fun saveLearning() {
        saved.saveMemory(memory)
        _learned.value = memory.all
        _mistakes.value = memory.mistakes
    }

    // --- AI helper ---
    fun saveAiSettings(settings: AiSettings) {
        saved.saveAi(settings)
        _ai.value = saved.loadAi()
        aiProblem = null
    }

    suspend fun testAi(settings: AiSettings): String = ai.test(settings)

    private fun applyOrderAction(action: OrderAction) {
        when (action) {
            is OrderAction.Cancel -> orders.cancelActive()
            is OrderAction.ChangeAddress -> orders.changeActiveAddress(action.address)
        }
    }

    private companion object {
        /** After an AI failure, the rules answer on their own for this long before the AI is tried again. */
        const val AI_RETRY_MS = 60_000L
    }
}
