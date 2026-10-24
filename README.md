# Anti-braintot · TimeLens

[![Build Android APK](https://github.com/mohitchoyal2002/Anti-braintot/actions/workflows/android-apk.yml/badge.svg)](https://github.com/mohitchoyal2002/Anti-braintot/actions/workflows/android-apk.yml)

A native Android screen-time dashboard and a draggable timer that stays above other apps. Built for Mohit's requested BrainPal-style awareness feature: time spent in **all ordinary apps**, rather than reel counts.

## Download the APK from GitHub

Open [Build Android APK](https://github.com/mohitchoyal2002/Anti-braintot/actions/workflows/android-apk.yml), select the latest successful run and download **TimeLens-0.3.1-debug.apk** from **Artifacts** or the run summary's download link. Sign in to GitHub to download artifacts. The artifact is the APK itself, so no ZIP extraction is needed.

The workflow runs on pushes to `main`, pull requests, or **Run workflow** manually. It installs JDK 17 and the Android 35 SDK, runs the counting checks, compiles/signs the APK with the SDK-only script, verifies its signature, and builds the instrumentation APK once and runs that exact signed app/test pair on Android 15, 16 and 17 emulators in parallel before publishing the APK. Downloads are retained for 30 days; test screenshots and logs are retained for 14 days. No repository secrets are required for this personal debug build. Each clean run generates its own development signing key; you may need to uninstall a previous debug build before installing a later run, which also removes local history. A fixed private signing secret can be configured later for persistent installs; no production signing key is checked in.

## Install the personal test build

1. Install `TimeLens-0.3.1-debug.apk` on your Android phone (Android 10 or newer).
2. Open TimeLens. A guided setup appears whenever a permission is missing. Tap **Open usage access**, select TimeLens if needed, enable access, then return.
3. If Android blocks the switch, follow **Usage switch disabled?** in setup: **Open App info → ⋮ → Allow restricted settings**, confirm in Android, return and open Usage access again. This is an Android security confirmation; TimeLens cannot grant or bypass it.
4. Follow **Open display settings** to enable Display over apps. Return to TimeLens after each Settings change; it checks the actual grants. **Check permissions again** also refreshes the checklist without leaving the app. Choose whether to allow timer notifications (optional), then tap **Start floating timer**. **Not now** opens the dashboard; missing permissions remain visible and setup returns on the next visit.
5. Open another app. The capsule shows a clock icon and today’s total, for example `2h 14m`. Tap it to see the current app and its accumulated time today. These are example values, not measured usage.
6. Drag the pill to move it. Tap it to toggle the current-app line. Stop from Today or the persistent notification.

If Samsung pauses the service, set TimeLens → App info → Battery → **Unrestricted** and remove it from Sleeping apps. After a phone restart, open TimeLens and start the timer again; the app sends a reminder when notifications are allowed.

## Usage access blocked on a Samsung phone

The message **“Controlled by restricted setting” / “App was denied access”** can appear after installing an APK outside an app store. Open **Settings → Apps → TimeLens → ⋮ → Allow restricted settings**, follow the phone’s confirmation, and then enable **Usage data access** for TimeLens. Only do this for an app you trust. The exact menu varies by Android version and manufacturer; a managed phone may have administrator restrictions.

The setup includes an App info shortcut and these instructions. Returning from a denied Usage access attempt reveals recovery help. **Allow restricted settings** only removes the restriction: you must then open **Usage access** and enable TimeLens separately. Every return from Settings rechecks the actual grants; visiting a settings page never marks access as allowed. The app cannot reliably detect why Android refused a grant, so the restriction instructions are conditional. See [Google’s restricted-settings help](https://support.google.com/android/answer/12623953?hl=en) and [Android special-permission guidance](https://developer.android.com/training/permissions/requesting-special).

The Usage access and Display over apps routes try the package-specific screen, the general permission list, App info, and finally Android Settings if a handler is missing or blocked. Manufacturers can also open the list while ignoring the package URI; select **TimeLens** there. Manual paths remain visible, and a failed launch leaves a recoverable message. The Settings destination and feedback survive activity recreation; `onResume`, window focus and bounded delayed checks refresh access after returning. The checklist counts **two required permissions** separately from optional notifications. If only the **Live screen-time timer** notification category is blocked, its own channel Settings page opens.

## What is included

- Today's total active-app time and a ranked per-app breakdown.
- A live floating timer: today's total + current app's accumulated time today.
- Seven-day view of locally saved history, with daily app breakdowns.
- A configurable daily reference track, compact icon-and-time capsule by default, tap-to-expand app detail, remembered drag position and docking side, and Stop control.
- Permission onboarding with live grant status, restricted-settings recovery, optional notifications, notification-channel checks and per-visit dismissal.
- System light/dark appearance and scalable licensed Inter typography.
- A new adaptive clock/lens icon with a monochrome version for Android themed icons.
- An Apple-design-informed native interface: consistent vector icons, immediate press feedback, interruptible spring docking, frosted functional surfaces, optional haptics, an opaque fallback and Android reduced-motion support. See [DESIGN.md](DESIGN.md).
- Screen-off/lock handling, local-midnight boundaries, overlapping activity transitions and event deduplication.
- Native Java UI, Android UsageStatsManager, a user-started foreground service, WindowManager and SQLite. No third-party runtime libraries.
- No account, backend, Internet permission, AccessibilityService, screen capture, microphone or chat access.

## Design audit and minimal timer

The whole-app audit is in [APP_DESIGN_AUDIT.md](APP_DESIGN_AUDIT.md), following the Apple Design skill from the requested guide and translating its foundations to native Android. Version 0.3.0 uses Apple-reference Action Blue (#0066CC), parchment (#F5F5F7), near-black ink, licensed Inter typography, simpler section headings, a segmented daily reference, responsive History rows, a pinned daily-detail Done action, and a shared live/preview timer component. The floating window defaults to a 132dp-wide icon-and-time capsule at normal font size, with a minimum 48dp touch height. Tap to expand app details; larger system fonts increase the window size rather than shrinking the text. The timer and daily sheet use native bounded background blur when supported, with an opaque fallback. Navigation uses a small blurred copy of TimeLens content. Reduce transparency, Animations and Haptic feedback are configurable in Settings; battery saving and increased system contrast also use solid materials.

## Android 15, 16 and 17 compatibility

The same APK is exercised on Android 15 (API 35), Android 16 (API 36) and Android 17 (API 37; Google’s stable image package `android-37.0`) before download publication. The workflow tests permissions, Settings recovery, optional notifications, actual foreground timer start/stop, permission revocation, system Back, and dark setup with large text on each OS. Evidence is grouped by API level.

Minimum SDK remains 29, with no maximum SDK restriction. Compile/target SDK remains 35; the APK uses public APIs available on all three requested OS versions. Running on a newer Android version does not require its newest target SDK. Native `OnBackInvokedDispatcher` handles Back on Android 13+; edge-to-edge insets include navigation, status bars and display cutouts; overlay width uses current window bounds. The timer starts from an explicit visible activity action and uses the declared `specialUse` foreground-service type. No boot-time foreground-service start is attempted.

An emulator pass verifies the covered flows, not every manufacturer’s battery or restricted-setting behavior. Samsung’s approval is still performed by the phone owner in Android settings.

## Counting definition and limits

Screen time here means the union of time intervals owned by the latest resumed app while the screen is interactive and unlocked. TimeLens itself and Settings count as apps; the launcher/home screen, Android System UI and lock screen do not. Background music with the screen off does not count.

Android usage events usually reach the pill within about two seconds. Split screen and picture-in-picture use the latest resumed app as an approximation, not simultaneous interaction tracking. The public UsageEvents API identifies activity classes but does not expose a usable public activity-instance ID; same-class overlapping instances cannot be distinguished perfectly. Device-specific event gaps can affect totals. This build does not claim numerical parity with Samsung Digital Wellbeing, which can use different definitions and privileged data.

Apps and sensitive system screens can hide application overlays. TimeLens respects that Android control. It cannot guarantee visibility literally everywhere.

Android retains raw usage events for a limited number of days. Today imports the events Android still provides; the app cannot reconstruct older missing activity. Daily snapshots and a rolling raw-event cache are saved locally while TimeLens is open or its service runs. Dates with no archive are shown as **No saved data**, not zero. Removing app data/uninstalling deletes this history. Device shutdown markers discard an unclosed interval rather than inventing usage over a reboot.

## Build

Requirements: JDK 17+, Android SDK platform 35, Build Tools 35.0.0, and Python 3 for the SDK-only packaging script.

```sh
export ANDROID_SDK_ROOT=/absolute/path/to/Android/Sdk
bash scripts/test-counting.sh
bash scripts/build-apk.sh
```

The signed personal APK is written to `build/sdk/TimeLens-0.3.1-debug.apk`. This is the build path verified in this workspace. It uses the official SDK compiler/dex/resource/signing tools and avoids downloading Gradle dependencies. The generated `debug-signing/timelens-debug.keystore` is for personal development, uses the conventional `android` password, and must not be used for a store release. Keep your own copy if you want subsequent SDK-only builds to update an installed APK without uninstalling it.

The project can also be opened in Android Studio. A standard Gradle wrapper is included; on a normal development machine use `./gradlew :app:assembleDebug`. Android Gradle Plugin is pinned to 8.7.3 and Gradle to 8.9. The Gradle dependency-resolution path was blocked by this host's Java networking; it has not been reported as a passing build. Do not put this workspace's proxy settings into your machine's project.

## Verification performed

- 23 executable domain assertions: app switches, old-activity pauses, screen/lock intervals, carried sessions across midnight, duplicate events, stop/shutdown/startup boundaries, daylight-saving days, exclusive query ends and bounded total duration.
- All app sources compiled against the public Android 35 API; resources linked, Java bytecode converted to DEX and APK signature verified.
- Binary manifest inspected for package/version, minimum/target SDK, launcher activity and permissions. No Internet or accessibility permission is declared.
- The workflow runs nine native instrumentation scenarios on each of Android 15 (API 35), 16 (API 36) and 17 (API 37): denied Usage access and Settings return/recovery (including recreation while Settings is open); usage-granted/overlay-missing and denied return; optional notifications with foreground timer start/stop; all grants followed by revocation; disabled notification channel; Settings fallback contracts and complete launch failure with restored feedback; dark setup at 200% font scale; Today, History, daily-detail dismissal and Settings; real compact/expanded overlay accessibility actions and saved drag position at normal and 200% font scale; feedback preferences and adaptive/monochrome icon packaging. Failed scenarios are collected so later scenarios still run, and a failed Android 17 check does not skip Android 15/16. Logs and screenshots are available in **permission-test-evidence-android-15/16/17**. Check the run’s result rather than assuming a pending run passed.
- Samsung’s restricted-settings confirmation requires testing and approval on the actual phone; the emulator does not reproduce that manufacturer/security state. Battery consumption and numerical parity with Digital Wellbeing have not been measured.

To run permission tests locally, start a disposable Android 15, 16 or 17 emulator, build the app, then run `bash scripts/test-permissions-device.sh`. This deliberately clears TimeLens test data and modifies grants on the emulator; the script refuses a physical phone. Version name/code are defined once in `version.properties` for both Gradle and SDK builds.

## Main files

| File | Responsibility |
| --- | --- |
| `UsageTimeline.java` | Pure event-to-duration reducer; one owner per instant |
| `UsageRepository.java` | Usage Access, bounded overlap queries, deduplication, snapshots and day boundaries |
| `UsageStore.java` | Atomic SQLite day/app archives and rolling event cache |
| `TimerCapsule.java` | Shared minimal timer and settings preview, scalable text, expand/collapse accessibility action |
| `OverlayService.java` | Foreground notification, two-second polling, one-second visual updates, drag and lock visibility |
| `MainActivity.java` | Today, History, Settings, setup lifecycle and Android permission requests |
| `PermissionState.java` / `PermissionSetup.java` | Actual grant reading, sequential setup, recovery and optional permissions |
| `PermissionSettings.java` | Public Settings intents, package/list fallbacks and safe destination restoration |
| `PermissionFlowInstrumentation.java` | Native device-level permission and lifecycle regressions |
| `BootReceiver.java` | User-tap reboot reminder; no background service launch |
| `tests/UsageTimelineTest.java` | Executable counting regressions |

## BrainPal and REA research

See `BrainPal_Architecture_and_TimeLens.md`. BrainPal's public listing confirms an accessibility-based reels counter; no BrainPal APK/source was supplied, so its internal classes and storage implementation have **not** been decompiled or verified.

REA CLI and MCP launch were verified separately in this workspace. Linux process-ownership readiness failed in this host. `scripts/setup-rea.sh` is a reproducible setup for your own developer machine; `scripts/export-brainpal.sh` exports your installed BrainPal APKs using an authorized USB-debugging connection.
