# eats Support: Resolution Guidelines

21 October 2026

## Purpose

The eats chatbot settles routine order problems itself and passes anything involving money, safety or a failed delivery to a person, with a deadline the customer can see. These guidelines are the rulebook it follows. They are built into the app (`support/bot/Guidelines.kt` and `SupportEngine.kt`) and into the browser test build.

They draw on four kinds of evidence:

- **The Uber Eats Complaint Data Warehouse Repository** (the project's own data set): 122 complaints in 8 categories, with a severity matrix, root causes and the department that owns each problem.
- **Uber's published South African refund policy**: the 30-day window, how card and cash orders are refunded, and the fact that each case is decided individually.
- **What South African customers actually complain about**: recent Hellopeter reviews of Uber, which mostly concern partial refunds, undelivered orders and support that is hard to reach.
- **The Consumer Protection Act 68 of 2008**: sections 55 and 56 (safe, good-quality goods, with a repair, replace or refund remedy) and section 61 (liability for harm caused by unsafe goods).

## What the complaint data shows

Half of all complaints (49%) are about food that never arrived or money that wasn't paid back, so those two flows get the most care in the bot.

| Category | Share of 122 complaints |
| --- | --- |
| Delivery failure | 28% |
| Refund issues | 21% |
| Customer support | 15% |
| Payment | 12% |
| Missing items | 9% |
| Delays | 7% |
| Wrong order | 5% |
| Food quality | 3% |

The data set rates every complaint on four severity levels, and the bot uses the same levels and deadlines: Critical gets an immediate response, High within 24 hours, Medium within 48 hours and Low within 72 hours. Its root causes (driver failed delivery, GPS verification failure, refund processing delay, incomplete packing, system billing error, poor escalation) decide which department each case goes to. Two categories were added for problems the bot must also handle: order changes (cancelling, changing the address) and driver conduct.

## Principles the chatbot follows

Every reply follows these rules, whatever the problem is.

1. **Check the facts before blaming anyone.** The bot reads the live order (status, ETA, delay, payment method, delivery time) before it answers. If the order is still on its way, it gives the ETA instead of logging a complaint.
2. **Say sorry once, then act.** It skips long apologies. It shows the order card, offers the next step as buttons, and says exactly what happens next.
3. **Never promise money.** The bot can *request* a refund and say how it will be paid. Only a person approves one. The AI helper is blocked from promising refunds.
4. **Always say when a person will respond.** Each complaint gets a due time from the severity matrix ("A person will respond by tomorrow at 12:44"). This answers the most common Hellopeter grievance: being left without an answer.
5. **Health and safety come first.** Signs of food poisoning, an allergic reaction or foreign objects are Critical. The bot gives the emergency numbers (10177 ambulance, 112 from a cellphone) before anything else, and never asks for proof first.
6. **Never ask for card numbers, PINs or CVVs.** It says so whenever money comes up.
7. **A person is always one tap away.** Every set of buttons includes "Talk to a person", and the phone icon starts a call to an agent.
8. **Don't loop.** After two messages in a row it can't understand, the bot stops guessing and offers a person (chat or call) without logging a complaint about nothing. Two upset messages (angry words, many "!" or mostly capitals) hand the chat to a person straight away.
9. **Repeat problems escalate.** A second complaint of the same kind within 30 days is raised one severity level. A reopened complaint is raised one level too.
10. **Join, don't duplicate.** A new report about the same order and problem is added to the open complaint, not filed as a new case. A second refund on it is added to the total.
11. **One problem at a time, none forgotten.** When a message holds several problems, the most urgent is handled first (safety, driver, never arrived, wrong, missing, quality, charges, then changes), and the bot offers to sort out the next one when it's done.
12. **Act in one step when the customer already said enough.** "The coke and the chips are missing", "I only got 1 kota but ordered 2" or "change my address to 5 Beach Road" go straight to the answer, without asking questions the customer has already answered.

## Guideline for each problem

Each problem has a set severity, an owning department and a fixed split between what the bot does and what a person does. Severity sets the response deadline: Critical is immediate, High is 24 hours, Medium is 48 hours, Low is 72 hours.

| Problem | Severity | Owner | What the bot does | What the agent does |
| --- | --- | --- | --- | --- |
| Order never arrived | High | Logistics | Checks the live status first and gives the ETA if the order is still moving. If it shows as delivered, asks the customer to check the door, reception, gate and neighbours. Says whether they were charged, then logs the case with the delivery time and address. | Checks the driver's GPS trail against the address. Contacts the driver. If the order wasn't delivered, cancels the charge or refunds in full, and flags the driver. |
| Late order | Medium | Logistics | Gives a revised ETA and says sorry once. Offers to keep waiting, cancel free of charge or talk to a person. Logs a case only if the customer wants follow-up or cancels. | Checks driver allocation and kitchen delay. Offers a delivery-fee refund or voucher if the order was 20+ min late. |
| Missing item | Medium | Restaurant Operations | Only after delivery. Asks which item and how many were missing. Requests a refund of unit price × quantity and asks how to pay it. | Approves the refund unless the account shows refund abuse. Tells the restaurant about the packing error. |
| Wrong order or item | Medium | Restaurant Operations | Asks whether the whole order or some items were wrong. Offers a full refund or a redelivery for a wrong order, and an item refund for wrong items. Warns customers with allergies not to eat food that isn't theirs. | Confirms the swap with the restaurant, then refunds or redelivers. Checks the other customer's order. |
| Cold, spilled or undercooked food | Medium | Restaurant Operations | Asks what was wrong and with which item. Tells the customer not to eat undercooked or off food. Offers an item refund or to pass feedback to the restaurant. | Checks how long the food waited for collection. Refunds the item and warns the restaurant if it happens again. |
| Food safety (illness, allergen, foreign object) | Critical | Restaurant Operations | Gives health advice first (10177 or 112). Asks the customer to keep the food and packaging and take a photo. Logs the case at once without asking for proof. | Calls the customer immediately. Suspends the dish if needed. Follows the CPA (s55, s61). |
| Refund not received | High | Finance | Gives the refund's status and expected timing. Escalates it if overdue. If there is no refund, checks whether the order qualifies. | Checks the approval workflow, then pays or explains with the bank reference. |
| Double charge or unknown charge | Critical | Finance | Asks what the customer sees. Explains that a pending amount is a card authorisation the bank releases. Logs completed duplicate or unknown charges as Critical. | Compares the payment gateway records. Reverses duplicates and treats unknown charges as possible fraud. |
| No response from support | High | Customer Service | Finds the open complaints. Raises an overdue one to a supervisor; otherwise says exactly when a person will respond. | A supervisor contacts the customer within 24 hours and owns the case until it is closed. |
| Cancel an order | High | Customer Service | Before the kitchen starts: cancels at once, free. While it's being prepared: sends a request to the restaurant. On the way: can't cancel, so offers the driver or a person. Late orders can always be cancelled free. | Confirms with the restaurant within minutes and says whether a charge applies. |
| Change the delivery address | High | Customer Service | Before the driver leaves: updates the address. On the way: asks the customer to contact the driver and offers a person. | Tells the driver the new address. A longer trip may change the fee. |
| Driver conduct | Medium (High for extra money or safety) | Logistics | Apologises and logs it. A driver asking for extra money is High: the amount in the app is all the customer owes. Threats or feeling unsafe are High, and the bot gives the SAPS number (10111). | Checks the trip and contacts the driver. Suspends them if the problem is serious or repeated. |

Questions that aren't complaints (fees, promo codes, tips, payment options, pick-up, allergens, account settings, ratings, delivery instructions) are answered straight away from a fixed set of approved answers.

## Refunds, payments and time limits

Refunds follow Uber's South African policy: they are requested within 30 days and decided case by case. The method depends on how the customer paid.

| How they paid | Refund goes to | When the bot says it arrives |
| --- | --- | --- |
| Card | The same card | Within 3 to 5 business days, depending on the bank |
| Cash | The customer chooses a bank transfer to a South African account | Within 3 to 5 business days |
| Cash | Or eats credit (like Uber Cash) | Straight away |

Other rules the bot applies:

- **Food problems** (missing, wrong, quality) must be reported within 48 hours of delivery. After that the bot still logs the case but says a person will decide.
- **Refund requests** older than 30 days go to a person rather than being refused outright, because Uber's policy lets it review late requests at its discretion.
- **Pending card amounts** are authorisation holds, not charges. The bot tells the customer the bank releases them, usually within a few business days. A completed second charge is a real double charge and is logged as Critical.
- **Pay on delivery** (the eats demo app takes cash or card at the door) means a customer whose order never arrived usually wasn't charged. The bot says so, and offers an "I was charged" button for the cases where money did leave their account.
- **Late orders** of 10 minutes or more can be cancelled free of charge.

The Consumer Protection Act backs this up. Section 55 entitles a customer to safe goods of good quality, and section 56 lets them choose a repair, a replacement or a refund when goods fall short. For food, that means a replacement order or a refund. Section 61 makes everyone in the supply chain liable for harm caused by unsafe goods. That is why food-safety cases are Critical and go straight to a person.

## Learning from mistakes and the AI helper

The bot understands customers with an AI model working alongside its own rules, and learns from its corrections.

1. **Rules.** About 600 phrases for 32 kinds of question, with typo tolerance ("refnud" still means refund). Words that look alike but mean different things, such as "allergy" and "eating", are never auto-corrected.
2. **What it has learned.** If the bot doesn't understand a message and the customer then taps the button they meant, it remembers that wording. Next time the same phrase is understood straight away. (Customers don't see "AI" or "Learned" labels; the team sees lessons under What I've learned.)
3. **Corrections.** "Not what I meant" counts as a mistake. Anything learned that caused the mistake is forgotten, and the customer's next message is learned under the right meaning.
4. **The AI helper (on by default).** Every typed message goes to Groq's free API (model `openai/gpt-oss-20b`) with the customer's orders, complaints and these policies. The AI may only pick one of the bot's own actions, answer a question from those facts, or add new information to an open complaint. The rules still decide refunds, priority and order changes, and they answer on their own for answers to the bot's questions, corrections, learned phrases, food-safety reports and angry messages. A safety filter drops any answer that promises a refund or asks for card details. If the customer doesn't correct an AI interpretation the rules couldn't read, the bot learns it too.
5. **Case notes.** Each new complaint gets a two-sentence AI summary for the agent who picks it up.

A Groq key is built into this test version, so the AI helper works straight away (another key can be pasted under chat menu ⋮ → AI helper settings). The free plan allows 30 requests a minute and 1,000 a day on this model, which is plenty for a presentation. If the AI is slow (12 seconds) or unreachable, the bot answers with its rules and tries the AI again a minute later; the customer never sees an error. Revoke the built-in key at console.groq.com/keys after the presentations.

Everything learned can be reviewed and forgotten under chat menu ⋮ → What I've learned.

## Complaint lifecycle and calling an agent

Every escalation creates a real complaint the customer can follow under Profile → My complaints. It moves through the statuses from the complaint data set.

| Status | Meaning | Who moves it there |
| --- | --- | --- |
| Open | Logged by the chatbot; waiting for a person | The bot, when it escalates |
| Pending | An agent is working on it; any refund is approved | The agent |
| Resolved | The agent has fixed it and any refund is paid | The agent |
| Closed | The customer confirmed it is sorted | The customer |
| Unresolved | Withdrawn by the customer, closed without a fix | The customer |

The customer can add information, ask a supervisor to look at it, reopen a resolved or closed case (which raises its severity), or withdraw it. A case past its due time shows as Overdue, and the bot raises it to a supervisor when the customer asks about it. Each complaint keeps a timeline and the chat transcript, so the agent never asks the customer to start over.

The phone icon in the chat (and the "Call an agent" button on a complaint) opens a call screen with mute, speaker, hold and end controls. In this version it is a demonstration: no real call is made, and the call's length is noted in the chat. For the presentation, the complaint screen also has demo buttons that act as the agent (pick it up, resolve it, decline the refund).

## Sources

- Uber Eats Complaint Data Warehouse Repository (project data set, supplied by the team): 122 complaints, categories, severity matrix, statuses, root causes and departments.
- [Uber South Africa refund policy](https://www.uber.com/za/en/legal/refund-policy/): 30-day window, card and cash refund methods, case-by-case decisions.
- [Uber South Africa reviews on Hellopeter](https://www.hellopeter.com/uber-south-africa): recurring complaints about partial refunds, undelivered orders and slow support.
- [Cliffe Dekker Hofmeyr: The rights of consumers to safe, good quality goods (2026)](https://www.cliffedekkerhofmeyr.com/news/publications/2026/South-Africa/Corporate-Commercial/corporate-commercial-15-april-the-rights-of-consumers-to-safe-good-quality-goods-and-the-case-of-avura-motors): CPA sections 55 and 56.
- [Michalsons: Product liability for damage caused by goods](https://www.michalsons.com/blog/product-liability-for-damage-caused-by-goods/4584): CPA section 61.
- [Paystack: card pre-authorisation holds](https://support.paystack.com/en/articles/10450882): how authorisation holds work and that banks set their own release times.
- [Groq rate limits](https://console.groq.com/docs/rate-limits.md): free-plan limits for `openai/gpt-oss-20b`.
