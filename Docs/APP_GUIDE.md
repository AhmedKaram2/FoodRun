# Food Run — app guide

Food Run helps a group choose who collects the food, agree on an order, and track who owes the payer. It runs on Android and iOS with a matching warm cream, orange and deep green theme.

[UI/UX review](UIUX_REVIEW.md) · [Earlier app and architecture demo](media/food-run-demo.mp4) · [Architecture](ARCHITECTURE.md) · [Hub setup](../room-server/README.md)

## A quick look

<table>
<tr><th>Start here · iOS</th><th>The wheel · iOS</th><th>Shared room · Android</th></tr>
<tr><td><img src="media/ios-home.jpg" width="250" alt="Refreshed home with group meal and Quick Spin choices"></td><td><img src="media/ios-wheel.jpg" width="250" alt="Quick Spin with visible crew controls and a pinned spin button"></td><td><img src="media/android-room.png" width="250" alt="Room progress, readiness and the next main action"></td></tr>
<tr><th>Connect · iOS</th><th>Restaurant library · iOS</th><th>Winner · iOS</th></tr>
<tr><td><img src="media/ios-connection.jpg" width="250" alt="Expandable manual pairing fields"></td><td><img src="media/ios-library.jpg" width="250" alt="Restaurant library empty state and Add restaurant action"></td><td><img src="media/ios-winner.jpg" width="250" alt="Fakhr selected for the food pickup"></td></tr>
</table>

These are actual captures of the refreshed app. Friday lunch club, Alex QA, and The Lunch Spot are demonstration data. See the [media index](media/README.md) for additional screenshots and the separately labeled earlier receipt/history verification captures. The earlier video shows the previous interface.

## Sign in

Use **Continue with Google** on Android or iOS to sign in or register with the same account as the website. Guest mode is unavailable. Google sign-in opens the website in the system browser and returns to the app. Rooms appear newest first.

## 1. Pick someone without a room

Open **Quick Spin**. The default crew is Karim, Karam, Hassan, Mersal, Baraa, Fayed, Ayman, Rayan, Gaber and Fakhr.

- **Who's in?** includes or excludes people for this spin.
- **Add person** adds another name; blank, duplicate and overly long names are rejected.
- Spin, see the selected person, and optionally share the result using the system share sheet.
- The crew and recent pickup history stay on this device. No hub is needed.

The spin button stays at the bottom while you review the wheel or crew. Each included person has an equal chance. Spins are independent; the same person can win twice.

## 2. Create a permanent table

Start the local hub on a Mac or PC using the [setup instructions](../room-server/README.md). Keep the computer awake and connect the phones to the same reachable local network.

Use the pairing link or QR code; expand **Enter connection details manually** when you need the hub address and fingerprint fields.

The organizer opens **Create a room**, connects to the hub using its pairing information, chooses a restaurant, sets fees and creates the room. Friends use **Join a room** with the same hub and the six-digit room code. Anyone with the invitation link or room code joins immediately, without organizer approval.

**Rooms and memberships do not expire automatically.** Return through the saved room on the home screen. Members can join or skip each meal without requesting approval. Access depends on preserving the hub's database and keys and the phone's saved data; explicit removal or storage loss can require rejoining.

There is **one restaurant per order**. The next order in the same room can use a different restaurant.

## 3. Save the restaurant once

Use **Restaurant library** to create a restaurant or import a menu JSON file. Review the import before confirming it. Save its name, currency, contact and menu, then select it for an order. Delivery/service fees and discounts are confirmed for the order.

Saved restaurants can be edited, shared as JSON and imported on another phone. **Save restaurant** copies the current room's restaurant to that device's library. Sharing opens the native share sheet; it does not publish a menu automatically.

The JSON format supports categories, variants, options, availability and pricing rules. To convert a menu PDF, photos or text with an AI agent, use the [menu import guide](RESTAURANT_MENU_IMPORT.md), [copy-paste prompt](AI_MENU_PROMPT.md), [JSON Schema](restaurant-menu.schema.json) and [example menu](restaurant-menu.example.json). The native editor provides a simpler item-entry flow; JSON is useful for richer menus.

## 4. Choose who orders

The room shows **Gather → Pick payer → Order → Settle** progress. **Room options & adjustments** contains receipts, past orders, saved-restaurant actions and management controls. The invite shortcut sits beside the room code.

Everyone joining an open order is ready automatically and can join or skip the meal. Payer eligibility is enabled by default; its separate consent switch is currently hidden. **Skip this order** excludes someone from that meal and its wheel. A spectator can watch without entering the payer selection or receiving private financial details.

The organizer chooses **Use the wheel** or **Choose person directly**. Direct selection opens ordering immediately for the chosen participant, without a wheel or acceptance screen. The wheel can start while joined members are offline. Its result is saved on the server; returning members see the same result and continue.

After a wheel selection, the selected person accepts the responsibility. A decline or recovery action follows the room rules and records a reason where required; the app does not silently assign an unwilling payer.

## 5. Submit food and send the restaurant order

The payer shares a receiving account, choosing a saved account or adding one. Members select food, options, quantities and notes, then submit their carts. Once everyone submits their food or chooses no food, the selected person can copy the combined list or open the restaurant WhatsApp chat, include the expected delivery/pickup time, and record that the order was sent.

**Example from the cross-platform test:** two AED 35 burgers plus AED 10 delivery equals AED 80. Each member's receipt is AED 40. Money uses integer minor units so fee allocation does not drift through floating-point rounding.

There is no second confirmation of the initial total. The server checks submitted food, prices, receiving details and restaurant requirements before placement. The payer sees the combined restaurant order and everyone's receipts; other ordering members see their own receipt and recipient.

## 6. Order, pay and settle

The payer uses the restaurant contact, orders manually and records the restaurant payment. Members transfer money outside Food Run and declare the amount/reference in the app. The recipient confirms or rejects each declaration.

Payment actions and receiving details are visible at the top of the room. Wallet cards show the food summary, amount paid, remaining balance and direct payment/confirmation actions; **Order details** remains available. Restaurant cards show the location and a phone-copy action. WhatsApp targets a saved WhatsApp number or UAE mobile number; where neither is available, choose the restaurant chat manually.

Food Run **records payments; it does not move money**. A declared transfer is not counted as confirmed receipt. Partial payments, corrections and refunds follow the bill and approval rules. The room cannot move to the next order while balances or unconfirmed transfers remain.

After fulfillment and settlement, the organizer archives the meal and starts **Next order**. The room code, approved members and historical receipts remain.

## 7. Take the receipt with you

Open receipts while connected to download them. Saved receipts and their original recipient remain readable offline and can be shared as a text document. The screen identifies offline data and its last synchronization time.

Live room, spin and payment updates require access to the room server. The public server works over the internet; a private local hub requires its reachable network. Cached results and receipts remain available offline.

## Common situations

| Situation | What to do |
| --- | --- |
| A friend is absent | Skip this order; the organizer must explicitly resolve absent expected invitations before spinning. |
| A selected person cannot pay | They can decline, remain in the meal, and mark themselves ready while another eligible participant is selected. If nobody remains eligible, pause the order until someone can take responsibility. |
| The restaurant or quote needs a correction | Use the permitted menu/reopen/fee controls before placement; review the new quote. |
| The app says the room changed | Read the latest state before retrying; another device may have updated it. |
| A request timed out | Use the saved retry action; the same command ID prevents duplicate application on the hub. |
| The computer changes address | Re-pair using the same hub certificate and its current address. |
| The hub is asleep or Wi-Fi is gone | Downloaded receipts remain readable; live actions resume after reconnection. |
| Next order is unavailable | Finish fulfillment, confirm transfers and resolve every balance/refund first. |

## Verification

The earlier full-flow audit passed **223 tests**, native builds, and a fresh Android ↔ iOS order/payment/adjustment/refund/recovery flow. See the [current verification report](FULL_FLOW_AUDIT.md).

The earlier UI/UX refresh passed **79 tests** and native builds, plus simulator/emulator walkthroughs. See the [UI/UX review](UIUX_REVIEW.md#verification).

The documented 1.1 run passed **146 automated tests** and a complete Android ↔ iOS order/payment/recovery workflow. See the [test report](GROUP_ORDER_TEST_REPORT.md) for exact coverage and limits. Physical-phone camera scanning, Windows execution, real restaurant calls and real bank transfers were outside that test run.

## Wallet and receiving details

Home and Profile use the same room receipts for amounts you owe, reimbursements due to you, and confirmed payment history. Profile shows the wallet before its editing fields on Android and iOS. Repeated food orders remain separate in payment history. The home preview shows three recent records; Profile shows up to thirty downloaded records. Older receipts remain available from each room's history.

Choose **Bank account** for a valid 23-character UAE IBAN beginning with AE and a bank name. Choose **Aani** for the UAE mobile number registered with Aani, such as `050 123 4567`; no bank name or IBAN is required. Switching methods keeps separate drafts. Existing historical recipients are preserved; newly saved bank details must pass IBAN validation.

## Administration and room restrictions

The administrator can add Food Run users, restrict room access by hour or day (or indefinitely), unblock them, and remove or restore their Food Run access. Room restrictions leave normal sign-in and the account screen available. The blocked room shows a red countdown and reason. A room owner can request a restriction with a reason and duration; administrator approval applies it to that room. Direct administrator restrictions can apply to one room or all rooms.

User removal revokes Food Run access while preserving the shared Firebase identity and Intrvioo data. Finish or cancel active rooms before removal. Cleanup previews cancelled or fully settled archived rooms and previous orders, then requires typed confirmation and rejects a stale selection. Deletion updates connected clients; separately exported receipts and external backups are not erased.
