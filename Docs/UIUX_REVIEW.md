# Food Run UI/UX review and improvements

Reviewed and implemented on 15 September 2026 across SwiftUI, Jetpack Compose, and shared presentation.

## Main findings

The app's playful wheel already communicated its purpose well. Group ordering needed a clearer hierarchy: the home screen was a stack of equally styled actions, room management appeared before ordering content, and long forms pushed their main action out of view. Light secondary text and repeated orange buttons made the screens harder to scan.

The update keeps the rounded typography and food-inspired palette, introduces a deep green home feature card, and uses orange primarily for the main action. Secondary actions use neutral surfaces; destructive actions have a distinct red treatment.

## Review by experience

| Experience | Finding | Implemented improvement |
| --- | --- | --- |
| Home | Group meals, Quick Spin, and saved rooms competed without explanation. | Purpose-led header, group meal feature card, explanatory Quick Spin and library shortcuts, and a named saved-tables section. |
| Connection | Manual address and fingerprint fields dominated the first experience. | Manual fields expand on demand; pairing link and QR/discovery remain available. The connection action stays at the bottom. |
| Create/join/next order | Long forms made completing setup require more scrolling. | Persistent main action, clearer labels and toggle cards; room code uses a numeric keyboard on both platforms. |
| Restaurant library | An empty library started with raw JSON input. | Explanatory empty state, optional JSON disclosure, and a labeled restaurant collection. |
| Menu editor | Saving competed with item entry and management. | Persistent save action, labeled menu collection, neutral secondary controls, and distinct removal controls. |
| Room lobby and shared spin | The room code and next step lacked structure; utility actions occupied the top. | Room-code card with invitation shortcut; four-stage progress; room utilities and adjustment fields expand separately. The organizer's pinned action changes from ready to spin after readiness. |
| Food selection and cart | Member cards preceded menu items. | Shared grouping puts menus, personal orders, and totals before the member list. Item selection keeps its add action within reach. |
| Receiving account | Dense forms and saved account cards shared little hierarchy. | A named accounts section, clearer typography, distinct destructive controls, and a persistent share-account action. |
| Review, receipts, payments | Long details were difficult to scan. | Better line spacing, contrast, badge treatment, and named totals/recipient, confirmation, and payment-activity sections in rooms. Android details are selectable. |
| Past orders | Historical receipts must retain their original recipient context. | Improved shared card styling; historical card order is explicitly preserved and regression-tested. |
| Quick Spin | Wheel size depended only on width; the spin action and crew could be below the fold. | Wheel sizing also considers screen height, spin action remains pinned, and navigation back to rooms has a clear back affordance. |
| Crew, add person, pickup history, winner | Existing native sheets and feedback already fit the task. | Inherit stronger text contrast, quieter secondary controls, refined primary buttons, and scalable iOS text styles. Existing selection, feedback, and reduced-motion behavior remain. |
| Loading and errors | Busy feedback could scroll away; hidden optional fields could complicate recovery. | Busy/error feedback stays above content. Errors expand available optional fields. Accessibility announcements are retained. |

## Accessibility and layout

- Main iOS headings, descriptive text, metadata, and statuses use scalable system text styles.
- Native screens retain their scrollable layouts and bounded content width.
- The progress indicator exposes one spoken stage description.
- Action and field identifiers remain available, including fields inside iOS disclosures.
- Android was inspected at font scale **1.4**, then restored to **1.0**.
- Calculated color contrast: primary button **5.24:1**, secondary text on the page **5.01:1**, green status text **5.29:1**, hero descriptive text **9.26:1**. These are token-level checks, not a claim of full accessibility certification.

## Verification

- Android debug APK builds successfully.
- iOS simulator build and launch succeed.
- **55 shared JVM tests pass**, including five new layout tests and additional assertions in the three-client order flow.
- **24 iOS tests pass** on iPhone 16e.
- New regression checks cover action/field preservation, menu-before-members ordering, historical receipt/recipient ordering, the organizer's ready-to-spin action transition, and accurate saved-restaurant counts.
- iOS walkthrough: home, connection and manual fields, back navigation, empty library, Quick Spin, winner dismissal, pickup history, crew, and add-person sheet/cancel.
- Android walkthrough: pairing, room setup, restaurant/item creation, restaurant selection, room creation, saved-room resume, eligibility, readiness, progress, and the pinned spin action.
- Fixed a SwiftUI lazy-container identity issue found during the walkthrough; navigation now creates the correct page content.
- `git diff --check` passes.

The visual walkthrough used simulators/emulators and a local demonstration room. Physical-phone camera scanning and every live payment/refund screen were not manually repeated in this pass. The existing shared integration suite continues to exercise the order/payment workflow. Usability gains are design judgments; no user-study or conversion metrics are claimed.

## Screenshots

| iOS home | iOS wheel | Android room |
| --- | --- | --- |
| ![Home](media/ios-home.jpg) | ![Quick Spin](media/ios-wheel.jpg) | ![Room](media/android-room.png) |

Additional captures: [Android home](media/android-home.png), [connection](media/ios-connection.jpg), [empty library](media/ios-library.jpg), [winner](media/ios-winner.jpg), and [Android room with enlarged text](media/android-room-large-text.png).
