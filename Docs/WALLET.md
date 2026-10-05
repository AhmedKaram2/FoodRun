# Custodial wallet

Profile → Wallet balance → Charge wallet. Enter the amount and currency, search registered discoverable users, and select a person and one of their saved receiving methods. Send money outside Food Run, then mark the top-up sent. Only the chosen cash holder can confirm or reject receipt. Pending and rejected top-ups give no spendable credit.

Every balance is separated by customer, holder and currency. The profile shows where funds are held, incoming receipt requests, available funds held for other customers, outgoing grouped payments and incoming grouped confirmations.

After the restaurant payment is recorded, customers can pay their remaining share directly or with their wallet. Wallet payment covers the full remaining share, automatically using confirmed funds in the same currency. Funds already held by the order recipient are used first and settle immediately. Other funds are allocated across holders, who become responsible for sending that cash. The customer's receipt records the wallet allocation; the order recipient still sees each unpaid holder obligation.

A holder can send one full transfer per recipient and currency, covering several customers and rooms. The receiving person confirms or rejects the entire group. Confirmation settles all included obligations atomically. Rejection returns them to the holder's unpaid list without debiting customer wallets again. New obligations remain outside an already pending group.

Example: Alice charges AED 100 held by Hassan. Alice pays AED 15 from her wallet, leaving AED 85 available with Hassan. Hassan now owes the selected person AED 15. If Bob also allocates AED 25 held by Hassan to the same selected person, Hassan can send AED 40 together. Only that selected person's receipt confirmation settles both cash obligations.

## Accounting and access

- The server derives available balances from confirmed top-ups minus wallet allocations. Clients cannot submit balances or choose another customer's wallet.
- Financial changes, receipt transfers, obligations, notifications and replay records commit in one database transaction. Repeated command IDs do not repeat a credit or debit; identity renewal does not change that guarantee.
- Wallet payment requires matching authenticated account and room membership, current order/revision, a positive current receipt balance and no pending direct payment. Insufficient funds leave the ledger unchanged.
- Cash holders remain liable while a grouped transfer awaits confirmation. Both manual completion and the next order stay blocked by unsettled wallet obligations. Automatic 24-hour archiving preserves those obligations and keeps settlement updates live.
- Existing bill corrections and recipient-confirmed cash refunds still apply. A bill reduction does not silently undo a committed wallet allocation or erase a holder obligation.
- Wallet records survive room/history cleanup. Accounts with available wallet funds or unsettled wallet activity cannot be removed through administration.
- Wallet data is projected only to the customer, holder or final recipient as appropriate. Older clients omit the new schema through the `walletDetails` capability.
- Wallet confirmations use the notification inbox and configured push delivery. Email stays restricted to explicit payment reminders.

The wallet records custody and settlement. Bank and cash transfers are performed by users; the app does not initiate bank transfers.
