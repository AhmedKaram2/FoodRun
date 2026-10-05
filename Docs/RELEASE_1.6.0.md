# Food Run 1.6.0

5 October 2026.

## Design

The website, Android and iOS use a consistent green and white theme inspired by the [FEAST group ordering reference](https://www.behance.net/gallery/98528901/FEAST-Food-Delivery-App-For-Groups). The indexed project previews informed the direction; full Behance image downloads were unavailable in this environment. This is an adapted Food Run design, with its existing branding and flows.

Primary green is `#167B58`, text `#172B26`, muted text `#61756C`, background `#F7FAF8`, borders `#E1ECE7` and pale green surfaces `#EDF8F2`. DM Sans and Noto Sans Arabic are bundled on all clients, with their Open Font Licenses. Web fonts are included in the offline shell.

The selected payer sees the order summary and restaurant action before their own menu and optional pricing, fee and receiving-account editors. Large summaries start with four lines; expansion and restaurant copy/share retain the complete list. Web order shortcuts remain reachable while scrolling. Native totals and next-step cards precede forms, the primary action stays at the bottom, and invitation QR codes and inactive payment details can be expanded. Received-payment forms remain visibly expanded. Home presents a short wallet overview and quick access to full details. Both languages retain responsive layouts and usable touch targets.

## Wallet

[Wallet flow and accounting](WALLET.md) describe user search, saved receiving methods, pending top-ups, holder-only receipt approval, balances by cash holder and currency, paying a settled share with confirmed funds, and grouped cash settlement across customers and rooms. A wallet allocation pays the customer's share and preserves the holder's outstanding cash obligation until the final recipient confirms receipt. Existing archived-room settlement and reminder-only email behavior remain in place.

## Validation

- 413 JVM tests: 408 passed, five optional Firestore emulator tests skipped; no failures.
- Web: 83 tests passed and production build passed.
- Arabic and English browser fixtures at 320, 390, 768 and 1280 pixels: 40 runs, 352 assertions, no horizontal overflow or runtime exceptions. Covered summary expansion, order actions, price editing, wallet flows, received-payment forms and archived payments. These fixtures capture commands without sending production orders or money.
- Android signed release build and release lint passed. Lint reports 22 nonblocking warnings; APK signature verification and 16 KB page alignment passed.
- iOS arm64 simulator build passed. Both bundled fonts are present in the app resources. This does not establish a TestFlight/App Store release.
- Eighteen English/Arabic guide screenshots were regenerated from the current UI.

Authenticated production ordering, external bank/cash transfers and real notification/inbox delivery require user acceptance testing. Local transactional tests cover authorization, replay, insufficient funds, concurrent wallet spending, durable recovery, grouped confirmation/rejection and preservation after archiving.
