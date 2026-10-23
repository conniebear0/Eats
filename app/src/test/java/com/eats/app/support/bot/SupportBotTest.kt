package com.eats.app.support.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The support chatbot end to end, the way the Support screen drives it: typed messages and tapped
 * buttons (which arrive as their label text), real complaints in the store, and learning.
 * Run with: gradlew test (no phone needed).
 */
class SupportBotTest {

    private var now = 1_760_000_000_000L
    private var active: BotOrder? = null
    private val past = mutableListOf<BotOrder>()
    private var aiOn = false
    private val actions = mutableListOf<OrderAction>()
    private val events = mutableListOf<ChatEvent>()
    private val memory = LearningMemory()
    private val store = SupportStore(clock = { now }, random = Random(7))
    private val chat = SupportChat(
        store, memory, clock = { now },
        onOrderAction = { actions += it },
        onEvent = { events += it },
    ).also { c -> c.context = { BotContext("Thandi", listOfNotNull(active) + past, store.sorted(), now, aiOn) } }

    private fun order(
        state: OrderState,
        pay: PayKind = PayKind.Card,
        late: Int = 0,
        pickup: Boolean = false,
        isActive: Boolean = true,
        id: String = "E-4821",
        hoursAgo: Long = 0,
        restaurant: String = "Kota King",
    ) = BotOrder(
        id = id, restaurantName = restaurant, restaurantArea = "Quigney",
        lines = listOf(BotLine("Kota Classic", 2, 90), BotLine("Coke 440ml", 1, 20), BotLine("Slap chips", 1, 35)),
        total = 170, deliveryFee = 9, freeDeliveryOver = 150, pickup = pickup, address = "12 Oxford Street, Quigney",
        pay = pay, state = state, minutesLeft = if (state.inProgress) 12 else null, minutesLate = late,
        placedAt = now - hoursAgo * 3_600_000L - 10 * 60_000L,
        deliveredAt = if (state == OrderState.Delivered) now - hoursAgo * 3_600_000L - 5 * 60_000L else null,
        driverName = "Sipho M.", driverVehicle = "Scooter", rating = 0, active = isActive,
    )

    private fun botText() = chat.items.filter { !it.fromUser && it.card == null }.joinToString("\n") { it.text }
    private fun lastBot() = chat.items.last { !it.fromUser && it.card == null && !it.notice }.text
    private fun chips() = chat.chips.map { it.label }
    private fun say(vararg lines: String) = lines.forEach { chat.send(it) }

    // --- Understanding ------------------------------------------------------------------------

    @Test
    fun understandsEverydayWordingAndTypos() {
        val c = IntentClassifier()
        assertEquals(Intent.NeverArrived, c.classify("it says delivered but I never got it").intent)
        assertEquals(Intent.MissingItem, c.classify("they forgot my chips").intent)
        assertEquals(Intent.RefundStatus, c.classify("I want a refnud").intent)
        assertEquals(Intent.ChangeAddress, c.classify("wrong adress").intent)
        assertEquals(Intent.Account, c.classify("I forgot my password").intent)
        assertEquals(Intent.ContactDriver, c.classify("can I call the driver").intent)
        assertEquals(Intent.FoodSafety, c.classify("I was eating and found hair").intent)
        assertEquals(Intent.Allergens, c.classify("I'm allergic to nuts").intent)
        assertEquals(Intent.Goodbye, c.classify("no thanks").intent)
        assertEquals(Intent.Unknown, c.classify("stop messing around").intent)
        assertTrue(c.isCorrection("that's not what I meant"))
        assertTrue(c.isFrustrated("THIS IS UNACCEPTABLE WHERE IS MY FOOD"))
    }

    @Test
    fun buttonLabelsWorkWhenTyped() {
        val c = IntentClassifier()
        Intent.entries.filter { it != Intent.Unknown }.forEach {
            assertEquals("label of $it", it, c.classify(it.label).intent)
        }
    }

    // --- Orders in progress -------------------------------------------------------------------

    @Test
    fun answersWhereIsMyOrderFromTheLiveOrder() {
        active = order(OrderState.OnTheWay)
        chat.open()
        say("where is my order?")
        assertTrue(lastBot().contains("Sipho M. is on the way"))
        assertTrue(chat.items.any { it.card?.title == "Kota King · E-4821" })
        assertTrue(store.complaints.isEmpty())
    }

    @Test
    fun lateOrderCanBeCancelledFreeAndTheDelayIsLogged() {
        active = order(OrderState.OnTheWay, late = 20)
        chat.open()
        say("this is taking forever", "Cancel it (free)")
        assertEquals(listOf<OrderAction>(OrderAction.Cancel("E-4821")), actions)
        val c = store.complaints.single()
        assertEquals(Category.DeliveryDelay, c.category)
        assertEquals(Severity.Medium, c.severity)
    }

    @Test
    fun earlyCancelIsDoneByTheBotButPreparingOnlySendsARequest() {
        active = order(OrderState.Received)
        chat.open()
        say("cancel my order", "yes please")
        assertEquals(OrderAction.Cancel("E-4821"), actions.single())

        active = order(OrderState.Preparing)
        say("please cancel", "Yes, send the request")
        assertEquals(1, actions.size)
        assertEquals("Cancellation Request", store.complaints.single().subcategory)
        say("cancel it")
        assertTrue(lastBot().contains("already with Kota King"))
    }

    @Test
    fun addressChangeNeedsAFullAddressAndIsBlockedOnceDriverLeaves() {
        active = order(OrderState.Received)
        chat.open()
        say("I need to change my address", "45")
        assertTrue(lastBot().contains("doesn't look like a full address"))
        say("45 Fleet Street, Southernwood")
        assertEquals(OrderAction.ChangeAddress("E-4821", "45 Fleet Street, Southernwood"), actions.single())

        active = order(OrderState.OnTheWay)
        say("change delivery address")
        assertTrue(lastBot().contains("already on the way"))
        assertEquals(1, actions.size)
    }

    // --- Problems after delivery ----------------------------------------------------------------

    @Test
    fun markedDeliveredButNotReceivedAsksToCheckThenLogsHigh() {
        active = order(OrderState.Delivered)
        chat.open()
        say("my order never arrived")
        assertTrue(store.complaints.isEmpty()) // checks first, doesn't jump to a complaint
        say("I've checked, it's not there")
        val c = store.complaints.single()
        assertEquals("Marked Delivered Incorrectly", c.subcategory)
        assertEquals(Severity.High, c.severity)
        say("I was charged")
        assertEquals(Severity.Critical, store.complaints.single().severity) // added to the same case
    }

    @Test
    fun missingItemRefundsExactlyTheMissingQuantity() {
        active = order(OrderState.Delivered, pay = PayKind.Cash)
        chat.open()
        say("something is missing", "the kota", "1", "Refund R45", "As eats credit")
        val refund = store.complaints.single().refund!!
        assertEquals(45, refund.amount)
        assertEquals(RefundMethod.EatsCredit, refund.method)
        assertEquals(RefundStatus.Requested, refund.status)
        assertTrue(botText().contains("I can't approve them myself"))
    }

    @Test
    fun cardOrdersAreRefundedToTheCardWithoutAsking() {
        active = order(OrderState.Delivered)
        chat.open()
        say("they forgot my chips", "Refund R35")
        assertEquals(RefundMethod.Card, store.complaints.single().refund!!.method)
    }

    @Test
    fun wrongOrderOffersRefundOrRedelivery() {
        active = order(OrderState.Delivered)
        chat.open()
        say("I got the wrong order", "the whole thing")
        assertTrue(chips().contains("Full refund R170"))
        say("Redeliver my order")
        val c = store.complaints.single()
        assertEquals(Category.WrongOrder, c.category)
        assertEquals(Severity.High, c.severity)
        assertNull(c.refund)
    }

    @Test
    fun foodSafetyIsCriticalAndGivesMedicalAdviceFirst() {
        active = order(OrderState.Delivered)
        chat.open()
        say("I think I got food poisoning, I've been vomiting")
        assertEquals(Severity.Critical, store.complaints.single().severity)
        assertTrue(botText().contains("10177"))
    }

    @Test
    fun aPendingCardHoldIsExplainedNotLogged() {
        active = order(OrderState.Delivered)
        chat.open()
        say("there's a pending amount on my card")
        assertTrue(lastBot().contains("hasn't dropped off after 7 days"))
        assertTrue(store.complaints.isEmpty())
        say("I was charged twice")
        assertEquals("Double Billing", store.complaints.single().subcategory)
    }

    @Test
    fun overdueRefundIsChasedToASupervisor() {
        active = order(OrderState.Delivered)
        chat.open()
        say("something missing", "chips", "Refund R35")
        now += 50 * 3_600_000L
        say("where's my refund")
        val c = store.complaints.single()
        assertTrue(c.escalated)
        assertEquals(Severity.High, c.severity)
    }

    @Test
    fun repeatProblemsGetAHigherPriority() {
        active = order(OrderState.Delivered)
        chat.open()
        say("something is missing", "chips", "Refund R35")
        store.close(store.complaints.single().id)
        now += 60_000
        say("something is missing again", "chips", "Refund R35")
        assertEquals(Severity.High, store.sorted().first().severity)
    }

    @Test
    fun driverAskingForMoneyIsHighPriority() {
        active = order(OrderState.OnTheWay, pay = PayKind.Cash)
        chat.open()
        say("the driver asked for more money before handing over my food")
        assertEquals("Driver Asked For Extra Money", store.complaints.single().subcategory)
        assertEquals(Severity.High, store.complaints.single().severity)
    }

    @Test
    fun asksWhichOrderWhenThereAreSeveralPastOrders() {
        past += order(OrderState.Delivered, isActive = false, id = "E-3000", hoursAgo = 30, restaurant = "Smash and Co")
        past += order(OrderState.Delivered, pay = PayKind.Cash, isActive = false, id = "E-3001", hoursAgo = 80, restaurant = "Napoli on Moore")
        chat.open()
        say("something was missing")
        assertTrue(lastBot().contains("Which order"))
        say(chips().first { it.startsWith("Napoli") }, "the coke", "Refund R20", "By bank transfer")
        val c = store.complaints.single()
        assertEquals("E-3001", c.orderId)
        assertEquals(RefundMethod.BankTransfer, c.refund!!.method)
        assertTrue(botText().contains("more than 48 hours"))
    }

    // --- People, frustration and the loop-breaker ---------------------------------------------

    @Test
    fun twoMisunderstandingsOfferAPersonWithoutLoggingAComplaint() {
        active = order(OrderState.Delivered)
        chat.open()
        say("glorp", "fleem")
        assertTrue(lastBot().contains("I can bring in a person"))
        assertTrue(chips().containsAll(listOf("Talk to a person", "Call an agent")))
        assertTrue(store.complaints.isEmpty())
    }

    @Test
    fun anUpsetCustomerGetsAPersonAfterTwoAngryMessages() {
        active = order(OrderState.OnTheWay)
        chat.open()
        say("where is my order this is ridiculous")
        assertTrue(lastBot().startsWith("I'm sorry, I can tell"))
        say("this is a joke, useless")
        assertEquals("Customer Frustrated", store.complaints.single().subcategory)
    }

    @Test
    fun callAnAgentOpensTheCallScreen() {
        chat.open()
        say("call me", "Call an agent")
        assertEquals(ChatEvent.StartCall, events.single())
    }

    @Test
    fun aComplaintButtonOpensThatComplaint() {
        active = order(OrderState.Delivered)
        chat.open()
        say("I want to speak to a human")
        val id = store.complaints.single().id
        say("View C-$id")
        assertEquals(ChatEvent.OpenComplaint(id), events.single())
    }

    // --- Learning -----------------------------------------------------------------------------

    @Test
    fun learnsWhatAnUnclearMessageMeantFromTheNextButton() {
        active = order(OrderState.Delivered)
        chat.open()
        say("my boerewors roll is nowhere in the packet")
        assertTrue(lastBot().startsWith("Sorry, I didn't quite get that"))
        say("Something's missing")
        chat.reset()
        chat.open()
        say("my boerewors roll is nowhere in the packet")
        assertTrue(lastBot().startsWith("Sorry about that. Which item was missing?"))
        assertEquals(ReplySource.Learned, chat.items.last { !it.fromUser }.source)
    }

    @Test
    fun aCorrectionIsCountedAndTheRightMeaningLearned() {
        active = order(OrderState.Delivered)
        chat.open()
        say("the driver left", "no thats not what I meant", "Problem with my food")
        assertEquals(1, memory.mistakes)
        assertEquals(Intent.FoodQuality, memory.all.single { it.phrase == "the driver left" }.intent)
    }

    @Test
    fun aiUnderstandingIsLearnedOnceTheCustomerCarriesOn() {
        aiOn = true
        active = order(OrderState.Delivered)
        chat.open()
        say("the packet came without my cooldrink bru")
        assertNotNull(chat.pendingAi)
        chat.onAiResult(AiResult(Intent.MissingItem, null))
        // "cooldrink" names the Coke, so the bot goes straight to it
        assertEquals("Sorry, your Coke 440ml didn't arrive.", chat.items.filter { !it.fromUser }[1].text)
        aiOn = false
        say("Just tell the restaurant", "the packet came without my cooldrink bru")
        assertEquals(ReplySource.Learned, chat.items.last { !it.fromUser && !it.notice }.source)
        assertTrue(botText().endsWith("I can ask for a refund of R20, or just let Kota King know so they can fix their packing."))
    }

    // --- Port of the HTML improvements ---------------------------------------------------------

    @Test
    fun twoProblemsInOneMessageAreHandledOneAfterTheOther() {
        active = order(OrderState.Delivered)
        chat.open()
        say("the kota was cold and the coke is missing")
        assertTrue(botText().contains("Sorry, your Coke 440ml didn't arrive."))
        say("Refund R20")
        assertTrue(lastBot().contains("You also mentioned a problem with the food"))
        say("Yes, sort that out")
        assertTrue(botText().contains("Sorry your Kota Classic arrived cold."))
    }

    @Test
    fun aShortfallIsWorkedOutFromWhatWasOrdered() {
        active = order(OrderState.Delivered)
        chat.open()
        say("I only got 1 kota but I ordered 2")
        assertTrue(botText().contains("Sorry, 1 of your 2 × Kota Classic didn't arrive."))
        assertTrue(chips().contains("Refund R45"))
    }

    @Test
    fun aTypedAddressIsConfirmedBeforeItIsUsed() {
        active = order(OrderState.Received)
        chat.open()
        say("5 Beach Road, Gonubie")
        assertTrue(lastBot().startsWith("Would you like your order delivered to 5 Beach Road, Gonubie"))
        say("yes")
        assertEquals(OrderAction.ChangeAddress("E-4821", "5 Beach Road, Gonubie"), actions.single())
    }

    @Test
    fun aSecondRefundOnTheSameComplaintIsAddedToTheTotal() {
        active = order(OrderState.Delivered)
        chat.open()
        say("the coke was missing", "Refund R20", "the chips are missing too", "Refund R35")
        val c = store.complaints.single()
        assertEquals(55, c.refund!!.amount)
        assertTrue(c.events.last().text.endsWith("Refund now R55 in total."))
    }

    @Test
    fun aiFirstLetsTheRulesAnswerWhenTheyAgree() {
        chat.aiFirst = { true }
        active = order(OrderState.Delivered)
        chat.open()
        say("the coke was missing")
        assertEquals("the coke was missing", chat.pendingAi)
        chat.onAiResult(AiResult(Intent.MissingItem, null))
        assertEquals(ReplySource.Rules, chat.items.last { !it.fromUser }.source)
        assertTrue(chips().contains("Refund R20"))
    }

    @Test
    fun aiFirstStillWorksWhenTheAiDoesNotAnswer() {
        chat.aiFirst = { true }
        active = order(OrderState.Delivered)
        chat.open()
        say("my chips were cold")
        chat.onAiResult(null)
        assertTrue(botText().contains("Sorry your Slap chips arrived cold."))
    }

    @Test
    fun newInformationIsAddedToTheOpenComplaint() {
        active = order(OrderState.Delivered)
        chat.open()
        say("the coke was missing", "Refund R20")
        chat.aiFirst = { true }
        say("the driver was rude when he dropped it off")
        val c = store.complaints.single()
        chat.onAiResult(AiPrompt.parse("{\"intent\":\"none\",\"answer\":\"Thanks\",\"add_to_complaint\":\"${c.reference}\"}"))
        assertTrue(lastBot().startsWith("Thank you, I've added that to your complaint ${c.reference}."))
        assertEquals("You added: the driver was rude when he dropped it off", store.complaints.single().events.last().text)
    }

    @Test
    fun agentSummariesAreKeptOutOfTheCustomersView() {
        active = order(OrderState.Delivered)
        chat.open()
        say("the coke was missing", "Refund R20")
        val id = store.complaints.single().id
        store.addBrief(id, "Coke missing; customer wants R20 back.")
        val e = store.complaints.single().events.last()
        assertEquals(SupportStore.BY_ASSISTANT, e.by)
        assertEquals("Summary for the agent: Coke missing; customer wants R20 back.", e.text)
        assertEquals("Summarise: \"x\"", AiPrompt.parseBrief("{\"summary\":\"Summarise: \\\"x\\\"\"}"))
    }

    @Test
    fun aiCanNeverPromiseARefund() {
        assertNull(AiPrompt.parse("{\"intent\":\"none\",\"answer\":\"I have refunded you R170.\"}"))
        assertEquals(Intent.RefundStatus, AiPrompt.parse("{\"intent\":\"RefundStatus\",\"answer\":\"\"}")!!.intent)
        assertEquals("Hi \"there\"", AiPrompt.parse("{\"intent\":\"none\",\"answer\":\"Hi \\\"there\\\"\"}")!!.answer)
        assertFalse(AiPrompt.safe("Please send your card number"))
    }

    @Test
    fun whenTheAiFailsTheRulesStillAnswer() {
        aiOn = true
        active = order(OrderState.OnTheWay)
        chat.open()
        say("do your drivers wear masks")
        chat.onAiResult(null)
        assertTrue(lastBot().contains("Sipho M. is on the way"))
    }

    // --- Complaint lifecycle ------------------------------------------------------------------

    @Test
    fun complaintsMoveThroughTheirStatusesWithTheRefund() {
        active = order(OrderState.Delivered)
        chat.open()
        say("wrong order", "whole order", "Full refund R170")
        val id = store.complaints.single().id
        store.addNote(id, "The bag had someone else's name on it")
        store.advance(id)
        assertEquals(ComplaintStatus.Pending, store.complaint(id)!!.status)
        assertEquals(RefundStatus.Approved, store.complaint(id)!!.refund!!.status)
        store.advance(id)
        assertEquals(RefundStatus.Paid, store.complaint(id)!!.refund!!.status)
        store.reopen(id)
        assertEquals(ComplaintStatus.Open, store.complaint(id)!!.status)
        assertEquals(Severity.High, store.complaint(id)!!.severity)
        store.withdraw(id)
        assertEquals(ComplaintStatus.Unresolved, store.complaint(id)!!.status)
        assertTrue(store.complaint(id)!!.events.any { it.text.contains("someone else's name") })
    }

    @Test
    fun theBotNeverMovesMoneyItself() {
        active = order(OrderState.Delivered)
        chat.open()
        say("something is missing", "chips", "Refund R35", "where is my refund")
        assertEquals(RefundStatus.Requested, store.complaints.single().refund!!.status)
        assertTrue(lastBot().contains("waiting for a support agent"))
    }

    @Test
    fun postsAnUpdateWhenTheOrderMoves() {
        active = order(OrderState.Received)
        chat.open()
        active = order(OrderState.OnTheWay)
        chat.onOrdersChanged()
        assertTrue(lastBot().startsWith("Update: Sipho M. has picked up your order"))
    }
}
