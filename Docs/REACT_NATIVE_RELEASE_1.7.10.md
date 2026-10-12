# FoodRun 1.7.10 (28)

Mobile Home now puts the current order first, followed by compact Create/Join actions, a wallet summary, recent rooms and Menus/Groups/Quick Choice shortcuts. Invitations remain actionable. Room names are not duplicated between the current-order card and recent rooms, and the full room list remains reachable. The view uses the shared snapshot's existing commands and enabled state; live updates and account changes replace its content.

The website top bar uses a single compact row on phones. The website name still opens Home and the profile photo still opens Profile. All four language choices remain accessible through the smaller language control. Connection status has been removed from room top bars.

Phone page headings use smaller titles and hide repeated introductory descriptions. The Profile wallet summary appears near the top, all five wallet tabs fit on screen, and the waiting heading has Arabic copy. The mobile browser Home view brings Continue order before creation controls and makes its banner compact. Desktop introductions remain available.

Validation: mobile TypeScript and 60 tests; 125 web tests; Arabic and English browser checks at 320, 390 and 1440 widths for header height, profile heading, language choices, profile-photo access, wallet tab visibility, status removal and overflow. Native Home and Arabic Profile were visually reviewed. Signed Android release/lint and iOS archive/export are checked separately from publication and device installation.

Android APK and an iOS development IPA for registered devices are published as release downloads. The IPA is not a TestFlight release. Generate `webApp/public/app-release.json` after the final APK build, then rebuild the website so its checksum and download URLs match the release assets.
