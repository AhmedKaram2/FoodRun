# Group order implementation

Scope: finalized GROUP_ORDER_PLAN.md, native Android/iOS, shared Kotlin, local computer hub. Quick Spin must remain functional.

Confirmed: local Mac/PC server; LAN-only live updates; downloaded receipts available offline.

Baseline: existing `:shared:jvmTest` green before production edits. Prior iOS test suite will be rerun with the expanded app.

## Verification gates

- [x] Domain: 26 tests for menu validation, billing rounding, consent, state transitions, revisions and ledger rules.
- [x] Server/contract: 40 tests for permissions, durable commands, restart, shared spin and protocol behavior; native clients verified pinned HTTPS/WSS.
- [x] Native Android: complete visible ordering/payment/recovery flow; 6 final adapter tests passed; signed release installed with saved data preserved.
- [x] Native iOS: visible join/spin/cart/receipt/history/offline/share flow and real Keychain storage verified; final signed suite passed all 24 tests with zero failures/skips.
- [x] Cross-client communication: same winner, two AED 40 receipts, confirmed reimbursement, restart recovery and order 2 in the same room 762390.
- [x] Shared regression suite: 50 tests passed, including 17 group-flow tests and the original 33 Quick Spin tests.
- [x] Artifacts: signed Android 1.1 APK, Mac/PC hub ZIP, signed iOS simulator ZIP; archive integrity and secret exclusions checked.

See [measured results and limits](GROUP_ORDER_TEST_REPORT.md), [Android audit](android-audit.md), [iOS audit](ios-audit.md), and [server audit](../room-server/AUDIT.md). The user requested emulator/simulator testing; physical devices were not used.
