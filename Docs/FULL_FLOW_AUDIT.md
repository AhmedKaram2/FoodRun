# Full order flow audit

16 September 2026 · shared Kotlin, native Android/iOS, and local hub.

## Status and evidence

This pass fixes the confusing receiving-account screen and checks the surrounding order lifecycle. The final matrix passed **223 automated tests with zero failures or skips**: 191 Kotlin/JVM, 8 Android instrumentation, and 24 iOS tests. A fresh Android emulator ↔ iPhone 16e simulator walkthrough also completed the same room from joining through archive, offline receipts, hub restart, and reuse for order 2.

The previous [state consistency report](CONSISTENCY_TEST_REPORT.md) records an earlier 190-test run and an earlier Android ↔ iOS meal flow. Its screenshots and live observations remain historical evidence; they do not establish that the changes in this report passed a fresh native walkthrough.

## Corrections in this pass

| Problem | Resulting behavior |
| --- | --- |
| **Select** appeared active on an already selected receiving account; the account step kept competing with food submission. | The selected card explains its state. Selecting a saved account fills the form locally. Sharing explicitly sends the chosen account, waits for the hub, then advances the next action to food submission or organizer review. An unchanged shared account offers **Continue to order**. |
| Failed account/cart requests could appear to have completed because the form closed. | The form and entered details remain available until acknowledgement. An uncertain saved request exposes **Retry** as the primary action. Validation failures leave editable details visible. |
| Re-saving unchanged account details, fees, or bill adjustments restarted confirmation/payment work. | Harmless saves preserve the existing quote, approval, and payment state. Actual changes still trigger the required confirmations. |
| Fixing a missing restaurant contact required a library detour and invalidated everyone's order. | **Edit restaurant details** edits the current order directly. Contact-only changes preserve readiness, submitted carts, confirmed quotes, and review stage. Actual menu/pricing changes retain the existing invalidation rules. |
| Imported tax settings or a minimum order could block placement without a usable editor. | Restaurant details expose tax treatment, tax rate, and minimum order. Invalid values keep the editor open with feedback. Placement explains missing contact, unresolved tax, or insufficient food total before the action. |
| Review/archive availability was inferred from a member's private receipt. | The hub supplies safe `OrderProgress` availability and blocker messages. A nonpayer or skipped organizer can manage the order without broader access to private receipts, carts, accounts, or transfers. |
| Completed/pending financial steps still exposed repeat actions. | Confirmation, restaurant payment, reimbursement, refund, and completion show the appropriate next action or waiting state. Settled and no-food members do not see a new payment form. |
| Payment amounts and reference text could carry over from a different step or bill. | Bill adjustment has its own signed input. Payment drafts follow the current total or remaining balance; restaurant references do not become transfer references. Old item/account forms close when the order changes. |
| View-only guest approval changed the quote. | Guest admission preserves the bill revision and carts. Guests remain outside ordering and payment. |
| Phone fields accepted strings containing only formatting characters. | Restaurant phone/WhatsApp values require actual digits, with an actionable validation message. |
| Native form controls could leave the keyboard covering the next step; iOS options expansion was hard to activate. | Action/page changes dismiss input focus. The iOS options header uses a full-width button with explicit expanded/collapsed accessibility state. |
| Kotlin-backed SwiftUI rows sometimes kept an old title or action after the shared state advanced. | Visible card/button state now participates in SwiftUI identity, so **Skip this order** immediately becomes **Join this order**, selected accounts update, and stage actions cannot retain stale callbacks. |
| A raw Kotlin serialization exception reached Android after invalid menu JSON. | Menu import now reports a short schema/field message without exposing implementation details. ASCII JSON input preserves punctuation exactly. |

The consent switch remains hidden and new ordering participants remain eligible by default, as established in the previous pass. A declined payer remains in the meal without being silently put back into the next spin. Skipped members remain saved room members; joining today's order is distinct from joining the permanent room.

## Flow coverage map

`New` identifies tests added in this pass. `Existing` identifies regression coverage already present and included in the relevant suites. Shared flow tests use real controllers with deterministic platform fakes and a real `RoomService`/temporary database; they are not native UI automation. Presentation tests verify available actions and messages from privacy-filtered state.

| User flow | Named automated evidence | Coverage |
| --- | --- | --- |
| Create a room, including lost acknowledgement | `GroupFlowIntegrationTest.lostCreateAcknowledgementRetriesSameRequestWithoutDuplicateRoom`; `RoomServiceTest.oversizedProjectedMenusAreRejectedBeforeCreatingAnUnusableRoom` | Existing: durable retry creates one room; oversized state is rejected before persistence. |
| Import/export restaurant JSON | `MenuImportTest.exportAndImportPreserveMenuData`, `duplicateKeysIncludingEscapedKeysCannotOverridePrices`, `largeDeepAndIncompleteInputsAreRejectedBeforeDecode`; `GroupFlowIntegrationTest.observerDetachesAndMenuImportRequiresExplicitConfirmation` | Existing: format, size, duplicate fields, explicit import confirmation; phone validation strengthened this pass. |
| Edit/delete saved restaurants | `GroupStateConsistencyTest.savingTheSelectedRestaurantUpdatesTheMenuUsedForRoomCreation`, `deletingTheSelectedSavedRestaurantRequiresAReplacementBeforeCreating`, `aFailedImportDoesNotAppearSavedOrReplaceTheExistingMenu` | Existing: selected menu consistency and storage rollback. |
| Repair the current restaurant/contact/tax | `GroupFlowIntegrationTest.aContactRepairFromTheRoomKeepsSubmittedFoodAndConfirmedQuotes`, `restaurantTaxCanBeResolvedInTheRoomAndInvalidRateKeepsTheEditor`; `OrderProgressTest.addingOnlyTheRestaurantContactDoesNotRestartReview` | New: direct repair without restarting confirmed food; invalid tax stays editable. |
| Join, approval, spectators, removal, late arrivals | `RoomServiceTest.lateJoinNeedsActualPayerConsentAndOrganizerApproval`, `pendingGuestsAndInactiveMembersCannotSeePrivateDataOrAct`, `invalidCredentialsCrossRoomTokensAndRemovedSessionsFail`; `OrderProgressTest.admissionOfAViewOnlyGuestDoesNotChangeTheQuote` | Existing authorization/privacy plus new guest revision regression. |
| Ready, skip, consent default, decline duty | `GroupFlowIntegrationTest.payerConsentIsHiddenAndAllReadyParticipantsEnterTheWheelByDefault`, `skippedMemberCannotConsentOrBecomeReadyWithoutJoiningThisOrder`, `declinedPayerCanStayInTheMealWithoutReenteringTheNextSpin` | Existing: default participation, explicit readiness, skip and decline semantics. |
| Shared spin, reconnect, restart | `RoomServiceTest.allOrderingMembersMustAcknowledgeBeforeRandomSelection`, `concurrentRetriesChooseOneWinnerAndPersistOnlyOneSpin`, `hubRestartFinalizesTheSameSpinWithoutAnyConnectedClients`; `GroupFlowIntegrationTest.rejectedSpinAcknowledgementRetriesAfterOtherMemberReconnects` | Existing: shared winner, durable spin, connection/preparation recovery. |
| Save/select/share receiving accounts | `GroupFlowIntegrationTest.savedAccountSelectionLeadsToFoodSubmissionAndOrganizerReview`, `lostAccountAcknowledgementKeepsTheFormAndMakesRetryThePrimaryAction`, `rejectedAccountShareRetainsEditableDetailsAndDoesNotPretendToAdvance`; `OrderProgressTest.sharingTheSameAccountPreservesConfirmedQuotes` | New: selection versus explicit sharing, next action, rejection, retry, restart, no-op save. |
| Add/edit food, extras, no food, deadline | `GroupFlowIntegrationTest.collectingPrimaryActionAdvancesAndFoodEditsRequireResubmission`, `failedCartSaveKeepsTheItemFormUntilRetryConfirmsIt`, `noFoodOrdersShowAnExplanationInsteadOfAnEnabledReviewAction`; `OrderDomainTest.variantsReplaceBaseAndOptionsApplyPerUnit`; `RoomServiceTest.deadlinesRejectEditsAndSubmitUntilOrganizerReopens` | New form/progress regressions plus existing item validation and deadline guards. |
| Fees, totals, review, private receipts | `OrderProgressTest.savingUnchangedFeesKeepsTheOrderReadyToPlace`; `GroupFlowIntegrationTest.anOrganizerOrderingNoFoodCanReviewAnotherMembersPrivateCart`; `OrderDomainTest.documentedReceiptAddsUpExactly`, `allocationConservesAmountsForDifferentWeightsAndInputOrders`, `taxIsRoundedOnceThenAllocated` | New review/no-op regressions plus existing exact allocation and tax arithmetic. |
| Confirm quote and place restaurant order | `GroupSettlementPresentationTest.ownConfirmationIsEnabledOnceAndThenBecomesWaitingFeedback`, `payerCannotPlaceUntilEveryOrderingMemberIncludingNoFoodConfirms`, `placementExplainsMissingContactAndMinimumBeforeTheAction`; `RoomServiceTest.quoteChangesRequireEveryMembersConfirmationAgain` | New waiting/blocker UI state; existing server enforcement of changed quotes. |
| Record restaurant payment and reimbursement | `GroupSettlementPresentationTest.confirmedRestaurantPaymentAdvancesToFoodArrivalInsteadOfRepeatingPayment`, `pendingTransferWaitsForPayerAndCannotBeDeclaredAgain`, `confirmedPartialPaymentOffersOnlyTheRemainingAmountAndRejectedClaimsCanRetry`, `fullySettledAndNoFoodMembersDoNotGetPaymentForms`; `RoomServiceTest.unconfirmedTransferDoesNotSettleAndCannotBeDuplicatedOrOverpaid` | New next-action/wait-state coverage plus existing duplicate, balance, and confirmation enforcement. |
| Bill adjustment and refunds | `GroupFlowIntegrationTest.paymentDraftsFollowTheCurrentBillWithoutReusingTheRestaurantReference`; `GroupSettlementPresentationTest.billAdjustmentUsesSeparateInputAndWaitsForAllApprovalsBeforePayment`, `refundWaitStatesBlockDuplicateDeclarationsUntilClaimResolved`; `OrderProgressTest.savingTheSameBillAdjustmentDoesNotRequirePaymentAgain`; `RoomServiceTest.partialTransfersAndRefundsRequireTheCorrectRecipientToConfirm` | New draft and repeat-action regressions; existing refund authorization and accounting. |
| Fulfill, settle, archive, organizer/payer split | `OrderProgressTest.skippedOrganizerCanSeeSafeProgressAndCompleteAnotherPayersOrder`; `GroupSettlementPresentationTest.nonpayerOrganizerDoesNotInferOtherMembersSettlementFromItsPrivateReceipt`, `payerOrganizerCanCompleteOnlyAfterArrivalPaymentAndSettlement`; `RoomServiceTest.unfinishedMoneyCannotBeSilentlyArchivedAndPlacedOrdersCannotBeCancelled` | New three-person workflow through payment, archive, restart, with private data preserved; existing financial completion guards. |
| Same room tomorrow, stale drafts and requests | `GroupFlowIntegrationTest.aNewOrderClosesAnOldItemDraftBeforeItCanBeSentToThatOrder`, `threeClientsCompleteMealPersistReceiptAndReuseRoomDaysLater`; `OrderIsolationTest.anUndeliveredCartCannotOverwriteTheNextOrdersEmptyCart`; `GroupStateConsistencyTest.nextOrderAfterRestartUsesTheSavedDeliveryDetailsAndExpectedPeople` | New stale-form regression plus existing permanent membership, order binding, and restored defaults. |
| Offline receipts, history, retry, storage failure | `GroupFlowIntegrationTest.downloadedHistorySurvivesLiveRefreshAndAppRestart`, `uncertainServerFailureKeepsCommandForIdempotentRetry`, `pendingCreateKeepsItsOriginalHubAcrossHubSwitchAndAppRestart`, `failedSnapshotSaveIsRetriedOnNextHeartbeatWithoutShowingUnsavedConsent`; `RoomServiceTest.databaseFailureRollsBackStateAndMissingKeyNeverCreatesANewKey` | Existing recovery coverage rerun with the changed controllers and hub. |

### Test sources

- Shared: [GroupFlowIntegrationTest](../shared/src/jvmTest/kotlin/com/karim/foodrun/shared/orders/GroupFlowIntegrationTest.kt), [GroupStateConsistencyTest](../shared/src/jvmTest/kotlin/com/karim/foodrun/shared/orders/GroupStateConsistencyTest.kt), [GroupSettlementPresentationTest](../shared/src/jvmTest/kotlin/com/karim/foodrun/shared/orders/GroupSettlementPresentationTest.kt).
- Hub: [OrderProgressTest](../room-server/src/test/kotlin/com/karim/foodrun/server/OrderProgressTest.kt), [RoomServiceTest](../room-server/src/test/kotlin/com/karim/foodrun/server/RoomServiceTest.kt), [OrderIsolationTest](../room-server/src/test/kotlin/com/karim/foodrun/server/OrderIsolationTest.kt).
- Domain/protocol: [MenuImportTest](../order-domain/src/commonTest/kotlin/com/karim/foodrun/orders/MenuImportTest.kt), [OrderDomainTest](../order-domain/src/commonTest/kotlin/com/karim/foodrun/orders/OrderDomainTest.kt), [ProtocolTest](../order-contract/src/commonTest/kotlin/com/karim/foodrun/orders/ProtocolTest.kt).

## Recorded commands and results

The first six new server progress tests all failed against the earlier behavior. After the fixes, the complete automated matrix passed **223 tests with zero failures or skips**.

| Suite | Passed |
| --- | ---: |
| Shared Kotlin/JVM | 101 |
| Room hub | 57 |
| Order domain | 27 |
| Wire contract | 6 |
| Android instrumentation | 8 |
| iOS XCTest | 24 |
| **Total** | **223** |

```sh
./gradlew :order-domain:jvmTest :order-contract:jvmTest :room-server:test
./gradlew :shared:jvmTest :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:assembleDebugAndroidTest :androidApp:lintRelease :room-server:installDist --console=plain
./gradlew :androidApp:connectedDebugAndroidTest
adb shell am instrument -w -e hubUrl https://10.0.2.2:8443 -e fingerprint <hub-fingerprint> com.karim.foodrun.test/androidx.test.runner.AndroidJUnitRunner
xcodebuild test -project FoodRun.xcodeproj -scheme FoodRun -destination 'platform=iOS Simulator,id=<simulator-id>'
```

The broad Gradle build, release lint, install distribution, signed simulator test, and live-hub native instrumentation all passed. The Android command without hub arguments passed its six local tests and skipped the two configuration-dependent transport tests; the explicit live-hub invocation then passed all eight with no skips. Generated logs and test databases are excluded from Git.

## Fresh native and cross-device verification

The fresh walkthrough used the updated Android emulator, iPhone 16e simulator, and the same TLS-pinned local Mac hub.

| Stage | Observed result |
| --- | --- |
| Room and membership | Android created **Full Flow QA**; iOS joined as Hassan and Android approved the request. Both clients showed two connected members. |
| Readiness and spin | Both joined the meal and became ready. The synchronized spin selected Karim on both clients; the selected payer accepted. |
| Receiving account | A fictitious saved account could be selected once, showed **Selected**, and shared successfully. The next action advanced to ordering instead of presenting a misleading repeat selection. |
| Menu and carts | Android imported the test restaurant JSON. Both clients selected a burger with modifiers; a missing required option produced validation feedback. Both submitted successfully. |
| Review and contact repair | Review remained blocked until both submissions and confirmations were complete. The organizer added a fictitious contact directly to the current restaurant; the carts, quote, confirmations, and review stage stayed intact. |
| Placement and restaurant payment | The payer placed the recorded order with a test reference. An incorrect payment amount was rejected; the exact amount was accepted. No real restaurant was contacted. |
| Reimbursement | Hassan sent a partial payment, the payer confirmed it, then Hassan sent the remaining balance and the payer confirmed it. The UI offered only the outstanding amount. |
| Adjustment and refund | After fulfillment, the payer recorded a negative bill adjustment. Hassan approved it, the revised restaurant total was recorded, receipts changed, and the payer recorded the resulting refund for Hassan to confirm. |
| Completion and history | The organizer completed and archived the settled order. Both devices retained their own receipt while the hub was stopped. Both reconnected after restart. |
| Permanent room reuse | The organizer started order 2 in the same room without either member joining the room again. iOS skip/join updated immediately after the SwiftUI identity fix. |
| Regression probes | Android current-restaurant **Back** discarded edits and returned to the room. Invalid JSON preserved ASCII punctuation and displayed the safe schema error. |

The native suites also exercised real HTTPS/WSS resume against the hub and rejected an incorrect certificate pin: Android `GroupNativeTest.realHubHttpsAndWebSocketResumeTheSameJoinedMembership`, iOS `HubIntegrationTests.testNativeHTTPSJoinAndWebSocketSnapshotReconnect`, and their wrong-pin counterparts all passed.

## Updating the installed apps and hub

1. Install the updated Android and iOS builds while retaining app data.
2. Stop and restart the updated hub using the **same `FOODRUN_DATA` directory** and existing storage/TLS keys. Follow the [hub run guide](../room-server/README.md).
3. Resume the saved room; no new room, account, or membership is required by this update.

The new `RoomReply.progress` is optional when reading older saved replies or an older hub. Older app builds use strict JSON decoding and cannot read the new hub's nonnull `progress` field, so update both clients before restarting the updated hub. Do not clear rooms/history, replace the hub directory, delete encryption keys, or reset application storage during this upgrade.

## Practical limits

Food Run records manual restaurant orders and payments; these checks did not place a real restaurant order or transfer money. Physical devices, real bank transfers, real restaurant calls, camera recognition, Windows hosting, and router-specific discovery/firewall conditions require separate verification. The tested hub is local-network only, and downloaded receipts remain available offline.
