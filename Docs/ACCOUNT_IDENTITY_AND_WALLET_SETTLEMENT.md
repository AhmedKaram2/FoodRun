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

When a customer pays with their wallet, their share is allocated and their available balance decreases once. The cash holder becomes responsible for reimbursing the chosen person on that customer's behalf. This allocation is not counted as cash received until the holder's transfer is confirmed. The room shows holder, customer, amount, recipient and payment status, including when the holder is not a room member. The holder can send one grouped transfer; the recipient confirms or rejects the full group from the room or profile wallet. Rejection keeps the holder liable without debiting the customer again. Cash already held by the recipient settles immediately.

Web and native clients receive profile, wallet and room changes from the same server. Native Home refreshes replace changed member/session credentials in the currently open room and discard snapshots from another membership. Late callbacks from replaced subscriptions cannot overwrite the new state. Mobile clients require the updated 1.6.3 build for the new room confirmation controls and session refresh behavior.
