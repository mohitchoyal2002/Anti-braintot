# Anti-braintot · TimeLens

[![Build Android APK](https://github.com/mohitchoyal2002/Anti-braintot/actions/workflows/android-apk.yml/badge.svg)](https://github.com/mohitchoyal2002/Anti-braintot/actions/workflows/android-apk.yml)

A native Android screen-time dashboard and a draggable timer that stays above other apps. Built for Mohit's requested BrainPal-style awareness feature: time spent in **all ordinary apps**, rather than reel counts.

## Download the APK from GitHub

Open [Build Android APK](https://github.com/mohitchoyal2002/Anti-braintot/actions/workflows/android-apk.yml), select the latest successful run and download **TimeLens-0.1.0-debug.apk** from **Artifacts** or the run summary's download link. Sign in to GitHub to download artifacts. The artifact is the APK itself, so no ZIP extraction is needed.

The workflow runs on pushes to `main`, pull requests, or **Run workflow** manually. It installs JDK 17 and the Android 35 SDK, runs the counting checks, compiles/signs the APK with the verified SDK-only script, verifies its signature, and retains the download for 30 days. No repository secrets are required for this personal debug build. Each clean run generates its own development signing key; you may need to uninstall a previous debug build before installing a later run, which also removes local history. A fixed private signing secret can be configured later for persistent installs; no production signing key is checked in.

## Install the personal test build

1. Install `TimeLens-0.1.0-debug.apk` on your Android phone (Android 10 or newer).
2. Open TimeLens and tap **Allow Usage Access**. Enable TimeLens in Android settings, then return.
3. Tap **Allow display over apps**, enable TimeLens, then return.
4. Tap **Start floating timer**. Allow notifications if you want the notification's Stop button and reboot reminder.
5. Open another app. The pill shows `TODAY  02:14:36` and, for example, `YouTube · 00:18:20`. These are example values, not measured usage.
6. Drag the pill to move it. Tap it to toggle the current-app line. Stop from Today or the persistent notification.

If Samsung pauses the service, set TimeLens → App info → Battery → **Unrestricted** and remove it from Sleeping apps. After a phone restart, open TimeLens and start the timer again; the app sends a reminder when notifications are allowed.

## What is included

- Today's total active-app time and a ranked per-app breakdown.
- A live floating timer: today's total + current app's accumulated time today.
- Seven-day view of locally saved history, with daily app breakdowns.
- A configurable daily reference ring, total-only overlay mode, remembered drag position, and Stop control.
- An Apple-design-informed native interface: consistent vector icons, immediate press feedback, interruptible spring docking, a solid-overlay option and Android reduced-motion support. See [DESIGN.md](DESIGN.md).
- Screen-off/lock handling, local-midnight boundaries, overlapping activity transitions and event deduplication.
- Native Java UI, Android UsageStatsManager, a user-started foreground service, WindowManager and SQLite. No third-party runtime libraries.
- No account, backend, Internet permission, AccessibilityService, screen capture, microphone or chat access.

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

The signed personal APK is written to `build/sdk/TimeLens-0.1.0-debug.apk`. This is the build path verified in this workspace. It uses the official SDK compiler/dex/resource/signing tools and avoids downloading Gradle dependencies. The generated `debug-signing/timelens-debug.keystore` is for personal development, uses the conventional `android` password, and must not be used for a store release. Keep your own copy if you want subsequent SDK-only builds to update an installed APK without uninstalling it.

The project can also be opened in Android Studio. A standard Gradle wrapper is included; on a normal development machine use `./gradlew :app:assembleDebug`. Android Gradle Plugin is pinned to 8.7.3 and Gradle to 8.9. The Gradle dependency-resolution path was blocked by this host's Java networking; it has not been reported as a passing build. Do not put this workspace's proxy settings into your machine's project.

## Verification performed

- 23 executable domain assertions: app switches, old-activity pauses, screen/lock intervals, carried sessions across midnight, duplicate events, stop/shutdown/startup boundaries, daylight-saving days, exclusive query ends and bounded total duration.
- All app sources compiled against the public Android 35 API; resources linked, Java bytecode converted to DEX and APK signature verified.
- Binary manifest inspected for package/version, minimum/target SDK, launcher activity and permissions. No Internet or accessibility permission is declared.
- This is a first personal test build. No attached Android device or emulator was available, so launch, Samsung behavior, battery consumption, overlay interaction and end-to-end timing have not yet been measured on a phone.

## Main files

| File | Responsibility |
| --- | --- |
| `UsageTimeline.java` | Pure event-to-duration reducer; one owner per instant |
| `UsageRepository.java` | Usage Access, bounded overlap queries, deduplication, snapshots and day boundaries |
| `UsageStore.java` | Atomic SQLite day/app archives and rolling event cache |
| `OverlayService.java` | Foreground notification, two-second polling, one-second visual updates, drag and lock visibility |
| `MainActivity.java` | Today, History, Settings and permission setup |
| `BootReceiver.java` | User-tap reboot reminder; no background service launch |
| `tests/UsageTimelineTest.java` | Executable counting regressions |

## BrainPal and REA research

See `BrainPal_Architecture_and_TimeLens.md`. BrainPal's public listing confirms an accessibility-based reels counter; no BrainPal APK/source was supplied, so its internal classes and storage implementation have **not** been decompiled or verified.

REA CLI and MCP launch were verified separately in this workspace. Linux process-ownership readiness failed in this host. `scripts/setup-rea.sh` is a reproducible setup for your own developer machine; `scripts/export-brainpal.sh` exports your installed BrainPal APKs using an authorized USB-debugging connection.
