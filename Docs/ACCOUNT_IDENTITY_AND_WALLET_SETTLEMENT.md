# Stable accounts and wallet settlement

Changing a profile name keeps the same account, room member, carts, roles, balances and payments. Joining again resolves the signed-in account before creating a member. Missing membership indexes recover from the member's account link; new members also have a stable account-based ID within their room. Another account cannot claim membership by matching a display name.

For an explicitly identified duplicate login, an operator can supply `FOODRUN_ACCOUNT_MERGES` as a JSON array:

```json
[
  {
    "sourceUserId": "duplicate-login-uid",
    "targetUserId": "retained-login-uid",
    "expectedSourceName": "Duplicate name",
    "expectedTargetName": "Previous name",
    "name": "Current name"
  }
]
```

The repair runs after restoring durable storage and before accepting requests. It checks the exact identities and profiles, preserves financial transaction IDs, amounts, statuses and original receiving details, transfers notifications and invitations, and stores a durable alias and repair record in one transaction. Both Firebase logins subsequently access the retained FoodRun account. Firebase login accounts and unrelated `users/{uid}` fields are preserved. Names and phone matches never trigger automatic merges.

The repair refuses overlapping orders, payment duties, conflicting saved method IDs, restrictions or any change that alters an existing bill. Empty duplicate members become inactive while the original member IDs, carts, bills, roles and past receipts remain intact. A repeated deployment does not apply the repair again. Remove the operator configuration after verifying the repair.

Rehearse an exported durable snapshot without connecting to Firestore:

```sh
./gradlew :room-server:installDist
java -cp 'room-server/build/install/room-server/lib/*' \
  com.karim.foodrun.server.AccountMergePreview snapshot.json plan.json
```

When a customer pays with their wallet, their share is allocated and their available balance decreases once. The cash holder becomes responsible for reimbursing the chosen person on that customer's behalf. This allocation is not counted as cash received until the holder's transfer is confirmed. The room shows holder, customer, amount, recipient and payment status, including when the holder is not a room member. The holder can send one grouped transfer; the recipient confirms or rejects the full group from the room or profile wallet. Rejection keeps the holder liable without debiting the customer again. New wallet allocations appear in the chosen person's normal approval list. Approval settles a share using cash already held by that person; money held elsewhere stays assigned to its holder until the cash transfer is confirmed. Declining an untransferred allocation returns its funds once and retains the cancelled allocation in the audit journal. Existing confirmed allocations remain unchanged.

After the chosen person records restaurant payment, customers' shares automatically reserve confirmed wallet money already held by that chosen person if it covers each full remaining share in the same currency. New already-paid payment rooms use the same rule. Later bill/price changes or confirmed direct payments can settle the remaining share when enough of that customer's wallet balance remains with the chosen person. Pending top-ups, an insufficient balance, another cash holder, pending direct payment claims, archived orders and the chosen person's own share do not trigger an automatic deduction. Every allocation belongs to the current room/order and commits with the triggering command; retries and restarts cannot debit it twice. The customer and chosen person receive an in-app notification and the chosen person approves the generated wallet claim in the same place as direct payments. Sender payment tasks exclude amounts already marked sent; pending confirmation remains visible in history and to the recipient.

Web and native clients receive profile, wallet and room changes from the same server. Native Home refreshes replace changed member/session credentials in the currently open room and discard snapshots from another membership. Late callbacks from replaced subscriptions cannot overwrite the new state. Mobile clients require the updated 1.6.5 build for the new room confirmation controls and session refresh behavior.

Favourite friend groups are visible only to their creator and members. Creators can rename the group and add or remove members; members can view its details and leave. Leaving removes every address belonging to that account, stops future group invitations and pending invitation emails, and preserves existing rooms, orders and wallets. Group revisions prevent a stale owner edit from re-adding a member who just left. The joined-group and revision fields are omitted for older strict clients. Exact email lookup resolves existing members; new addresses receive one signup invitation when added. Selecting a group at room creation queues room details and a join link for its members. Delivery retries use the durable email outbox; deleted groups, removed addresses, closed join windows, and stale orders invalidate queued jobs.

Any current friend group member can select their joined group when creating a room and announce it by email to the group. Commands carry both group owner ID and group ID so identical group IDs owned by different users remain separate. The server validates current membership at creation and before each email attempt. Email jobs retain the announcing creator separately from the group owner; older owner-created jobs remain readable. Leaving or removal revokes announcements, while only the owner can rename or edit membership. Web and shared Android/iOS selectors list all joined groups alongside owned favourites.

An optional 1–1440 minute join timer uses server time and survives restart. Expiry closes new joins, resolves any restaurant poll, expires unpaid wheel reservations, and starts the shared wheel using everyone who joined and consented to selection. Existing members may resume. Pending declared protection payments must be reviewed before selection proceeds; a manually started selection is never restarted by the timer.

The verified owner account can search the admin user list by email and open a 30-minute support session. Its room credentials are separate from the real user's credentials, expire together, and revoke on return. Both the owner and target retain restriction checks. Support sessions cannot access administration or Firebase credentials, and support mutations record the real owner as actor in the admin audit.
