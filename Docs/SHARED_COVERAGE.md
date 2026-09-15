# Shared behavioral coverage

## Pre-refactor gate

Resolved scope: the reachable native Food Run screen, crew sheet, nested add-person sheet, history sheet, winner, native state adapters, and shared crew/spin/persistence code. Native rendering and platform storage migration are validated by the platform suites.

The original `:shared:jvmTest` suite contained 10 passing tests. Six additional `FoodRunBehaviorBaselineTest` tests were added before production edits. The resulting 16-test baseline passed before repository/domain extraction began.

Excluded as not implemented: backend requests, loading/retry lookups, service forms, OTP, uploads, accounts and remote synchronization.

## Shared matrix

| Reachable behavior / input partition | Passing test evidence |
|---|---|
| Ten requested initial names including Fakhr | `FoodRunEngineTest.initialCrewMatchesRequestedNames`; `FoodRunControllerTest.initialContractIsCompleteAndReadOnlyForBothPlatforms` |
| Equal-eligibility selection and exact pointer landing for different counts / starts | `everyWinnerLandsUnderPointer`; `historyIsCappedAtThirtyAndOnlyEligiblePeopleWin`; `spinLocksRosterAndNavigationAndFinishesOnceWithHistoryAndWinner` |
| Forward motion, easing and clamped progress | `spinMovesForwardAndSlowsDown`; `animationClampsProgressAndRejectsInvalidPlans` |
| Valid add, whitespace normalization and persistence | `additionsAndParticipationSurviveRestart`; `successfulAddSavesOnceIncludesPersonAndReturnsToCrew` |
| Blank / duplicate / length validation | `invalidNamesDoNotChangeRoster`; `nameValidationStaysOnSheetAndEditingClearsError` |
| Unicode names, 32-codepoint boundary, Arabic normalization | `normalizedUnicodeNamesRespectCharacterBoundary` |
| Shared emoji-safe initial and long wheel label | `sharedPersonDisplayDoesNotSplitEmojiSurrogatePairs` |
| Last-participant protection and custom-only removal | `originalCrewAndLastParticipantCannotBeRemoved`; `lastParticipantFlagsMatchDomainGuardsAndRosterChangesResetWinner` |
| Unknown IDs / rejected operations cause no writes | `rejectedRosterActionsDoNotPersistOrChangeSelection` |
| Remove and re-add, preserved historical person | `removedNamesCanBeAddedAgainAndOldHistoryStaysIntact` |
| Malformed JSON / restored duplicate IDs and names / empty active selection | `invalidSavedDataRecoversSafely`; `restoreNormalizesAndDeduplicatesRosterAndHistory` |
| Haptics persists, including changes while a spin runs | `hapticsPreferencePersists`; `pendingSpinIsTransientAndHapticsMayChangeDuringAnimation` |
| Open crew / add, cancel nested sheet, reopen fresh draft, back to main | `nestedAddDismissClosesOnlyAddAndReentryStartsFresh` |
| Submit validation remains on add sheet; edit clears error | `nameValidationStaysOnSheetAndEditingClearsError` |
| Successful add returns once to crew and duplicate submit is ignored | `successfulAddSavesOnceIncludesPersonAndReturnsToCrew` |
| History navigation and irrelevant draft intents | `historyNavigationAndDraftEventsCannotChangeUnderlyingMainScreen` |
| Spin excludes duplicate taps, roster edits and competing sheet opens | `newPersonCanWinAndHistoryIsPersistedOnce`; `spinLocksRosterAndNavigationAndFinishesOnceWithHistoryAndWinner` |
| Winner is persisted once; dismiss preserves main winner status | `spinLocksRosterAndNavigationAndFinishesOnceWithHistoryAndWinner` |
| History capped at thirty, newest first | `historyIsCappedAtThirtyAndOnlyEligiblePeopleWin` |
| App restart / cancelled animation does not record unfinished spin | `pendingSpinIsTransientAndHapticsMayChangeDuringAnimation`; `cancelledAnimationDoesNotRecordPickupAndCanRestart` |
| Invalid native animation input cannot leave permanent lock | `invalidAnimationInputsCannotLeaveWheelLocked` |
| Immediate observation, no duplicate unchanged emissions, stable old snapshots, disposal | `observersReceiveInitialAndOneSnapshotPerActionAndStopAfterCancel` |
| Reentrant observer events reach all subscribers in order | `observerTriggeredEventsAreDeliveredInOrderToAllObservers` |
| Real JSON repository save failure, draft preservation and successful retry | `preferenceWriteFailurePreservesDraftAndRosterAndRetrySucceeds` |
| Failed history save unlocks wheel without unsaved winner; dismiss error | `failedHistorySaveUnlocksWheelWithoutPublishingUnpersistedWinner` |
| Common calendar/date formatting, leap day, pre-epoch, fractional-hour zone, DST transition | All three `PickupDateFormatterTest` tests |

## Commands and results

- `./gradlew :shared:jvmTest --no-daemon --console=plain`: 16-test pre-refactor gate passed.
- Same command after the extraction: 32 tests passed, 0 failures.
- Final coordinated build reran the shared suite after the Unicode-safe wheel-label regression was added: **33 tests passed, 0 failures, 0 errors** (14 controller, 10 existing engine, 6 baseline additions, 3 date formatter tests).

The controller tests use an injected in-memory domain repository for state/event behavior. Persistence and failure tests cross the controller → domain → real JSON repository → fake platform preference boundary. Baseline tests continue to exercise the compatibility facade, protecting existing callers during native migration.
