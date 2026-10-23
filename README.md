# Eats v0.4 (Android, Jetpack Compose)

Food ordering app: sign up / log in, browse restaurants, customise dishes, cart with promo and tip, checkout, live order tracking, order history, favourites, profile, and an advanced support chatbot with complaints tracking and a (demo) call-an-agent screen. Light mode is orange/white, dark mode is purple/black.

## Open it
1. Android Studio > File > Open > this `EatsApp` folder. Let Gradle sync (accept plugin updates if offered).
2. Run on an emulator or phone. Promo code to try: `EATSFIRST` (R30 off).
3. Screens can also be viewed without running: open `ui/Previews.kt` (Split/Design view).

## Screen flow
Splash > Login / Sign up / Forgot password > Home tabs (Home, Search, Cart, Orders, Profile) > Restaurant (dish options sheet) > Cart > Checkout > Confirmation > Tracking. Favourites and Support chat open from Home / Profile.

## Authentication
- Real on-device accounts (`auth/LocalAuthRepository.kt`): PBKDF2 salted password hashes, random session token with 30-day expiry, session restored on launch, logout clears it, login lock after 5 wrong attempts.
- Forgot password: 6 digit code (shown on screen in demo mode, since no SMS/email server), then new password.
- Signed-out users are sent to Login if they reach a protected screen (`EatsApp.kt`).
- Google / Apple buttons are placeholders (show a message).

## Where things live
- `auth/` accounts, hashing, validators, AuthViewModel
- `data/` models and sample restaurants (`SampleData.kt`, photos in `res/drawable-nodpi`)
- `order/` cart, totals, order simulation, per-user saved address/favourites/history
- `support/bot/` the chatbot itself, plain Kotlin with no Android code (guidelines, understanding, learning, decisions, complaints)
- `support/` Android side of the chatbot: ViewModel, saved data, Groq AI helper
- `docs/Resolution_Guidelines.md` the rules the chatbot follows, with sources
- `ui/theme`, `ui/components` (reusable), `ui/screens`, `ui/EatsApp.kt` (navigation)

## Prototype notes
- Restaurants/menus are sample data. Dishes without a photo use drawn plate art.
- Order progress is simulated in real time over the restaurant's delivery estimate (about 15 to 30 min: received, preparing from 20%, on the way from 50%), so the countdown matches what the chatbot says and there is time to test it. "Skip ahead (demo)" and the demo panel jump straight to a step.
- Payment is cash/card on delivery (no real payment).

## Support chatbot
Open it from Home (the (i) icon), Profile > Help and support, or Help on the tracking screen.

- **Knows the order**: status, ETA, delays, items, payment method, driver, delivery time, past orders.
- **Follows written guidelines** for 12 problem types (never arrived, late, missing, wrong, quality, food safety, refunds, charges, slow support, cancel, change address, driver conduct). Severity sets the deadline: Critical immediately, High 24 h, Medium 48 h, Low 72 h. See `docs/Resolution_Guidelines.md`.
- **Answers questions** about fees, promo codes, tips, payment, pick-up, allergens, account and ratings.
- **Takes action**: cancels the order or changes the address when the guidelines allow it.
- **Logs real complaints** under Profile > My complaints (also chat menu). Statuses: Open (logged, waiting for a person) > Pending (agent working, refund approved) > Resolved (fixed, refund paid) > Closed (customer confirmed). Unresolved = withdrawn by the customer. Customers can add notes, ask a supervisor, reopen (raises severity) or withdraw. Saved per account.
- **Several problems in one message** ("the kota was cold and the coke is missing"): the most urgent is handled first, then it offers to sort out the other one.
- **Typed answers work mid-flow**: "the coke and the chips", "2", "the whole order", "bank transfer", "yes" are understood wherever a button would be. Works out shortfalls ("I only got 1 kota but ordered 2"), flags opened or eaten-from bags, spots a new street address typed on its own, and passes notes to the driver.
- **One complaint per order and category**: a second refund on the same complaint is added to the total. Repeat problems in 30 days get a higher priority (requests such as cancellations don't count as repeats).
- **Learns from mistakes**: when it misunderstands and the customer taps what they meant, it remembers the wording. "Not what I meant" forgets the bad lesson. Chat menu > What I've learned shows and forgets lessons. (Customers don't see "AI" or "Learned" labels.)
- **Offers a person** after 2 messages in a row it can't understand (without logging a complaint about nothing), hands over after 2 upset messages, or on request. The phone icon opens a demo call (no real call).

### AI helper (Groq, on by default)
A Groq key is built into this test version (`AiSettings.BUILT_IN_KEY` in `support/bot/AiHelper.kt`), so the AI helper works straight away. Free plan: 30 requests/minute and 1,000/day on `openai/gpt-oss-20b`.
- **AI-first**: the AI reads every typed message and either answers it from the customer's orders, complaints and the policies, adds new information to an open complaint, or hands it to the bot's own rules. The rules still decide refunds, priority and order changes, and they answer on their own for answers to the bot's questions, corrections, learned phrases, food-safety reports and angry messages.
- **Case notes**: each new complaint gets a two-sentence AI summary for the agent (kept out of the customer's timeline).
- **Silent failures**: if the AI is slow (12 s timeout) or unreachable, the bot simply answers with its rules and tries the AI again a minute later. Customers never see AI errors; they go to Logcat (tag `eats-ai`) and to menu > AI helper settings > Test connection.
- Safety: the AI can never approve refunds; answers that promise money or ask for card details are thrown away.
- To use another key: Support chat > menu (three dots) > AI helper settings > paste it > Test connection > Save. Clearing the field goes back to the built-in key.
- **Important**: anyone with the app can read a key that's built in. Revoke it at https://console.groq.com/keys after the presentations, and for a public release call the AI through your own server instead of storing a key in the app.

## Demo panel (tracking screen)
Stands in for the restaurant and driver: set the status (Received, Preparing, On the way, Delivered, Cancelled), make it 15 or 30 min late, or resume the live countdown. Then tap Help and ask the chatbot.

### Suggested demo script
1. Sign up (or in the browser build use `thandi@example.com` / `eats1234`), order something, open tracking.
2. Help > "where is my order?" > "cancel my order" > "No, keep it".
3. Demo panel: 30 min late > Help > "why is it so late" (offers free cancel).
4. Demo panel: Delivered > Help > "my order never arrived" > "I've checked, it's not there" > "I was charged" (Critical complaint).
5. Type two problems at once ("the kota was cold and the coke is missing"): it sorts out the coke, then offers to deal with the cold kota.
6. With the AI helper off (menu > AI helper settings), type something it won't understand ("my koeksisters went walkabout"), then tap "Something's missing". Start a new chat and type it again: it is now understood (see menu > What I've learned).
7. Tap the phone icon (demo call), then menu > My complaints > open one > use the demo agent buttons to move it to Pending and Resolved, then close it.

## Tests
- `./gradlew test` runs `SupportBotTest` (36 tests on the chatbot logic: understanding, typos, learning, refunds, escalation, severity, loop breaking, safety, several problems in one message, shortfalls, typed addresses, refund totals, AI-first and agent summaries).
- The chatbot logic was also checked against the browser version on 530 scripted conversations and 766 phrases; both give identical answers, word for word.
- Note: the chatbot logic and tests were compiled and run off-device, and the ViewModels, saved data and Groq helper were compiled against stand-ins for the Android classes. The Compose screens were checked by hand but not built here (no Android SDK available), so run a Gradle sync and build in Android Studio first.

## Browser test build
`Eats_App_Chatbot_Test.html` (delivered with this project) runs the same app and the same chatbot in any browser, no install. Data is saved in that browser; "Reset demo data" clears it.
