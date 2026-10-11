# FoodRun 1.7.8 (26)

The group feature is now named Groups / المجموعات throughout the web and mobile navigation, page headers, invitations and selection prompts, including all Arabic variants. Saved group data and membership actions keep their existing identifiers.

The iOS More / المزيد destination now sits in its own circular bubble beside the main tab dock. Arabic places the bubble on the opposite side, and all tabs retain their labels, selected states and navigation actions. Android retains its platform tab layout.

Room selection and Quick Choice now share colorful bouncing countdowns, pulsing rings and orbiting stars. Moving names have colorful floating backgrounds and a winner landing effect. Results celebrate with 54 drifting confetti pieces and an 18-star burst. The server still controls the timing and selected person. Reduced-motion preferences disable decorative movement.

The notification center adds Mark all as read and Clear notifications. These changes are saved per account, hide cleared events without recreating them on retries, and preserve the actual payment and room records.

A six-step first-use tour explains rooms, selection, wallets, groups, notifications and profile settings. It offers Next, Back and Skip and saves completion for the signed-in account.

Android checks the website release manifest on startup, downloads a newer APK within the app using DownloadManager, verifies its checksum, package, version and signing certificate, then opens Android’s installer. Downloads can continue in the background and resume after restarting the app. Android still requires its installation confirmation and, when necessary, permission to install updates from FoodRun.

Validation: mobile TypeScript and 54 tests; 124 web tests; 48 browser checks covering four languages, phone and desktop widths, both selection styles, shared results and notification actions; reduced-motion visual check; signed Android release and lint; iOS archive and export. Physical installation, updater installation and production deployment are verified separately after publication.

Downloads: Android APK and an iOS development IPA for registered devices. The IPA requires a device included in its signing profile; website installation through Safari remains available to other iPhone users.

Release preparation: after assembling the final signed Android APK, run `python3 Scripts/write-app-release-manifest.py`, then build and deploy the website. The manifest must match the public release asset checksum.
