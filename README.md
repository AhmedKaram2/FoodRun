# Food Run 🍟

A Kotlin Multiplatform app that chooses who picks up the food, with native SwiftUI on iOS and Jetpack Compose on Android.

**Crew:** Karim, Karam, Hassan, Mersal, Baraa, Fayed, Ayman, Rayan, Gaber and Fakhr. Add more people in **Who’s in? → Add person**.

## Install on Android

Send [FoodRun-1.0.apk](Distributions/FoodRun-1.0.apk) to your friends. Open it on an Android phone, allow installation from that browser or file app when Android asks, and tap **Install**. Requires Android 8.0 or later.

The APK is signed for installation and future updates. No account or internet connection is needed. Names and results are saved on each device independently.

## Features

- Equal random chances for everyone included on the wheel.
- Smooth 5.4-second slowdown, moving pointer, optional haptic ticks, spring winner reveal and finite confetti.
- Include or sit out friends; at least one person stays in.
- Add names, validate blank/duplicate/long entries, and remove custom names.
- Save the crew, selection, haptic setting and last 30 pickups.
- Share the selected name through the native share sheet.
- Screen-reader labels and a brief reveal when reduced motion is enabled.
- Preserve preferences from the original iOS app when upgrading.

Each spin is independent, so a previous winner can win again. Kotlin chooses the winner first and calculates the exact landing angle; both platforms animate that same plan.

## Architecture

```text
SwiftUI screens                         Compose screens
      ↓                                     ↓
WheelStore (iOS adapter)          FoodRunViewModel (Android adapter)
      └──────────────────┬──────────────────┘
              FoodRunController
         immutable state + typed events
                       ↓
              FoodRunSession
         roster rules + random spin plan
                       ↓
              FoodRunRepository
                       ↓
       UserDefaults / SharedPreferences
```

KMP owns form drafts, validation, navigation destinations, selection, persistence schema, history dates and display copy. Native adapters handle lifecycle, animation frames, haptics, timezone offsets and storage access. Screens render observed state and send events. Crew rows render lazily as the list grows.

Reusable buttons, controlled inputs, avatars, cards and dividers preserve Food Run’s orange/cream theme. The focused local [IosComponents package](Packages/IosComponents) is adapted from the supplied MOHRE library; Android has equivalent Compose components. Source provenance and fixes are in [component reuse](Docs/component-reuse.md).

The current release uses English copy centralized in KMP. It has no server or cross-device synchronization; the repository and copy boundaries allow these features to be added later without putting application rules in the views.

## Build Android

Prerequisites: JDK 17+, Android SDK platform 36, and SDK build tools. Set `ANDROID_HOME` or create an untracked `local.properties` with `sdk.dir=/path/to/Android/sdk`.

```sh
./gradlew :shared:jvmTest :androidApp:assembleDebug
```

Open this folder in Android Studio to run on a device or emulator.

For a signed release, this workspace already contains its release identity in the ignored `.signing/` directory. On a new project checkout, restore that directory to keep the same app signing identity. Only create a new identity for a new distribution:

```sh
python3 Scripts/create-android-signing.py
./gradlew :androidApp:assembleRelease :androidApp:lintRelease
```

The creation script preserves existing signing files. Back up `.signing/` privately; future updates need the same key. The generated APK is `androidApp/build/outputs/apk/release/androidApp-release.apk`.

## Build iOS

Prerequisites: macOS, Xcode, JDK 17+, and the Android SDK for Gradle project configuration. The first build downloads Kotlin dependencies.

1. Open `FoodRun.xcodeproj` in Xcode.
2. Select **FoodRun** and an iPhone simulator.
3. Run with **⌘R**; test with **⌘U**.

The Xcode pre-build phase builds and embeds the shared Kotlin framework automatically. For a physical iPhone, select your Apple development team under **Signing & Capabilities** and run on the connected device. Requires iOS 17+.

After changing project structure, regenerate the project with `xcodegen generate`. To build both shared Apple framework variants explicitly:

```sh
./gradlew :shared:assembleFoodRunSharedDebugXCFramework
```

## Validation

- **33 shared tests passed:** wheel landing, random eligibility, persistence, drafts, navigation, cancellation, save failures and timezone formatting.
- **12 iOS tests passed**, including the original preference/history migration.
- Android signed release build and lint passed; APK signature and 16 KB alignment verified.
- Installed and exercised the release on an Android emulator; built and launched the updated iOS app on iPhone 16e.
- [Audit and verification](Docs/AUDIT.md), [shared coverage](Docs/SHARED_COVERAGE.md), [Android audit](Docs/android-audit.md).
- Previews: [Android](Preview/android-main.png), [winner](Preview/android-winner.png), [crew](Preview/android-crew.png).

Pinned build versions are in the Gradle files. Nunito is bundled under its [Open Font License](Licenses/Nunito-OFL.txt).
