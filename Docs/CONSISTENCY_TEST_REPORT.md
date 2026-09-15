# State consistency and default payer participation

15 September 2026 · macOS local hub, Android emulator and iPhone 16e simulator.

## Final behavior

The payer-consent switch is hidden. New ordering participants are eligible for the wheel by default; they still press **I'm ready** before the organizer spins. Watch-only guests and people skipping the meal stay out of the wheel. A selected person can decline responsibility, remain in the meal, and mark themselves ready without being silently added back to the next spin. Skipping and explicitly joining again restores default eligibility.

Existing rooms, memberships, receipts and explicit saved values are preserved. A legacy room member with no previous decline can establish the new default through **I'm ready**. The app and hub should be updated together: the updated hub rejects new mutations from clients that do not identify the current order number.

## Defects corrected

| Area | Correction |
| --- | --- |
| Consent versus readiness | The former switch could change a local draft after readiness without updating the hub. Consent changes now use the durable command path and display confirmed state; the current interface hides the switch and defaults participation on. |
| Live iOS cards | Scroll content captures the current immutable state. Roster changes now render without leaving and reopening the room. |
| Delayed requests | Mutations identify their order number, so an offline request from yesterday cannot enroll someone or add food to today's order. Already recorded retries, including pre-upgrade requests, retain their original result. |
| Restaurant/fee drafts | Editing a saved restaurant cannot overwrite the order's fee form. Background room updates do not overwrite a restaurant or new-order form. |
| Selected menus | Editing/importing a selected restaurant updates the subsequent create payload. Deleting it clears the selection. |
| Failed storage/transport | Failed writes roll back visible local changes; failed snapshot saves retry; request startup failures clear loading; durable requests remain retryable until confirmation is saved. |
| Receiving accounts | Reopening an account retains its identity. **Add another account** explicitly starts a blank form. |
| Next orders | Delivery details and expected participants restore from the saved room after restart. |
| Native inputs/files | Both amount inputs allow a minus sign. Android rejects malformed UTF-8 menu files and uses a separate file path for each receipt export. |

## Automated verification

Final results: **190 tests passed, zero failures, zero skips**.

| Suite | Passed |
| --- | ---: |
| Shared Kotlin, including complete meal/recovery and state-consistency regressions | 77 |
| Menu, billing, room domain and published menu example | 27 |
| Protocol | 5 |
| Hub persistence, order isolation, default eligibility, HTTP/WebSocket | 49 |
| Android native storage, input/export and real hub transport | 8 |
| iOS native storage, transport and app regression tests | 24 |

JVM counts come from Gradle XML reports. Native tests run with the real local hub configuration, including HTTPS certificate-pin rejection and WebSocket reconnect tests. Android Debug/Release builds and release lint pass (0 errors, 16 warnings); iOS builds and tests use simulator signing for real Keychain access.

Local logs: `.build/consistency-kotlin-tests.log`, `.build/consistency-android-final.log`, `.build/ios-consistency-tests.log`, and `.build/android-consistency-tests.log`. These generated files are excluded from Git.

## Fresh Android ↔ iOS UI verification

An isolated **Consistency QA** room (`949249`) was created from Android; iOS joined and Android approved it. The original **Breakfast** room (`769744`) was preserved in its gathering phase.

| Check | Observed result |
| --- | --- |
| Default participation | Consent switch absent on both devices; both ordering members entered with eligibility enabled. |
| Live roster | iOS displayed approval and Android's readiness changes without reopening the room. |
| Shared spin | Both phones showed the same selected participant, karim. |
| Account and carts | Android accepted responsibility and shared a fictitious Test Bank account; each phone submitted one AED 5 item. |
| Exact receipts | AED 10 food + AED 10 delivery = AED 20 total, AED 10 per person. Both confirmed their current total and recipient. |
| Restaurant contact validation | Placement was rejected because the saved menu lacked a contact. A fictitious contact was added to this QA room only via the authenticated API. Both users then resubmitted and reconfirmed the updated quote. No saved restaurant or Breakfast menu was changed. |
| Payment validation | AED 19 was rejected; AED 20 was accepted. |
| Reimbursement | iOS declared AED 10; Android confirmed it. iOS's receipt showed Remaining AED 0.00. |
| Signed input | The iOS amount field accepted `-1`; it was replaced with the intended reimbursement amount before submission. |
| Completion | Android marked food received and completed the settled order; iOS reflected completion live. |
| Offline recovery | With the hub stopped, both phones showed cached receipts and offline status. The same hub data/keys restarted successfully. |
| Daily reuse | Android created order 2 with the same room code; iOS retained approval, selected **Join this order**, and became eligible by default. |

[Settled iOS QA receipt](media/consistency-check/ios-settled-receipt.jpg).

## Scope

These results establish the checks above, not every possible device or network condition. Physical phones, camera recognition, router-specific discovery/firewalls, Windows execution, real restaurant calls and real bank transfers were outside this run. Food Run records manual payments; this test made no external order or transfer. Earlier build artifacts and screenshots remain historical; rebuild from the current source for these fixes.
