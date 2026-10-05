# Food Run 1.6.1

5 October 2026. Android version code 11; iOS build 11.

The website and native apps now offer editable room names based on the device's local meal time: Breakfast before 1 PM, Lunch from 1 PM, followed by the date in Arabic or English. Untouched suggestions refresh at creation time; custom names remain intact. Food rooms remember the current account's last restaurant and delivery address, using the restaurant's current fees. Home offers a shortcut to continue the most recent ongoing order. Payment screens can open the wallet with the exact missing amount and currency ready to top up.

Wallet search supports names and account emails, shows no people until a query is entered, and ignores outdated responses after clearing the query. Email matching uses private server records; results reveal only the existing person projection. Reminder delivery continues to use verified contacts and explicit payment reminder requests. Native search failures do not leave a payment retry pending or reopen a form after navigating back.

## Screen review

| Screen | Result |
| --- | --- |
| Sign in | Mobile web presents the form first; native buttons share compact padding and centered text. |
| Home | Continue-order shortcut, compact mobile actions, consistent reading alignment. |
| Profile | Wallet appears before completed profile forms. Editing stays available, receiving details fold away, and native history sections expand on request. |
| Wallet | Padded balances and holder rows, explicit zero balance, compact top-up form, name/email search, exact shortfall shortcut. |
| Create/join/payment room | Editable meal/date suggestions, remembered restaurant and address, consistent form labels and input sizes. |
| Restaurants and ordering | Logical text alignment, consistent card spacing and controls, preserved compact summary and full-order expansion. |
| Payer and settlement | Compact invitation with expandable QR; received-payment forms remain visible and expanded. Currency and exact minor-unit amounts remain intact. |

The existing green theme and bundled Arabic/English fonts continue across clients. The web font declaration is corrected to load DM Sans. Eighteen bilingual guide screenshots were refreshed.

## Validation

- 421 JVM tests: 416 passed, five optional Firestore emulator tests skipped, no failures.
- 86 web tests and production build passed.
- Arabic/English responsive fixtures at 320, 390, 768 and 1280 pixels: 48 runs and 456 assertions, no horizontal overflow or runtime exceptions. Separate payment regression checks cover partial receipt confirmation, overpayment prevention, archived settlement and quantity/price behavior.
- Android signed release build, release lint, signature and 16 KB alignment verification passed. The APK was installed and launched on the test emulator.
- iOS arm64 simulator build passed; the app was installed and launched in an isolated iPhone simulator. This is source and simulator validation, not a TestFlight/App Store publication.

Authenticated production wallet transfers, ordering and real inbox delivery still need account-based acceptance testing. Browser fixtures capture commands and do not move real money.
