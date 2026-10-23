# Design review: TimeLens permission recovery

## Summary

Before this change: **Needs work**. The supplied Samsung screenshot shows a disabled Usage Access switch controlled by a restricted setting. The app's old permission buttons did not explain that blocker or guide a first-time user through recovery. The single job of the revised screen is to help the user understand and grant the access that makes their screen-time timer work.

Evidence: the supplied screenshot, source review, measured resource colors and compiled Android code. The screenshot proves the observed OS restriction, not that any programmatic grant is possible. Device/emulator results are recorded separately in GitHub Actions; Samsung-specific confirmation cannot be verified by code inspection or an AOSP emulator.

## Improvements

- **High — Permission dead end.** `MainActivity.java: usageSettings`, `PermissionSetup.java: USAGE`: explain the disabled-switch case, open App info directly, show **⋮ → Allow restricted settings → confirmation**, then return to Usage Access. Keep the wording conditional because a denied grant does not prove the cause. `onboarding.md › Additional requests`: “consider integrating the permission request into your onboarding flow.” [Android's special-permission guidance](https://developer.android.com/training/permissions/requesting-special) requires a system Settings flow and a real grant recheck.
- **High — No first-open sequence or recovery on return.** `MainActivity.java: onResume, onPause, renderSetup`, `PermissionState.java`: show a sequential setup when access is missing, recheck on return, preserve recovery across recreation, and allow a per-visit exit. Required access and optional notifications have explicit labels. `privacy.md › Requesting permission` informs the contextual explanation and user choice; native Android dialogs remain user-triggered.
- **Medium — Appearance and text scaling gaps.** `Ui.java`, day/night resources, `MainActivity.java: Ring`: add system light/dark appearance and replace fixed-size canvas text with a scalable TextView. Setup uses wrapped vertical statuses, 16sp body/actions and at least 52dp action targets. `accessibility.md › Vision`: “Support larger text sizes.” `cross-platform.md › Conventions to check`: Android targets use 48dp. Primary/secondary text contrast is documented with computed values in DESIGN.md.

## Craft notes

The live timer and reference ring are specific to a time-awareness utility. Permission setup stays quiet and functional. The three numbered items represent the actual sequence of Android permissions. Removing the header privacy chip reduces decoration; the relevant privacy explanation now accompanies each permission choice. No additional animation is needed for setup; Android's reduced-motion setting still applies to existing springs.

## What works

Keep the on-device data model, absence of Internet/Accessibility permissions, explicit start/stop action, consistent vector icons, visible optional-notification exit and bottom navigation for the dashboard. Never imply that opening Settings granted access or automatically start the foreground service after a permission change.

## Platform notes

The user's linked guide resolves to [dickwu/apple-design-skill](https://github.com/dickwu/apple-design-skill). Read references: accessibility, layout, typography, color, designing-for-ios, cross-platform, onboarding, privacy, buttons and code-audit. The skill explicitly limits an Android-only app to principles and foundations. This implementation uses Android special access screens, notification runtime permission, native Back and system fonts; it does not import iOS permission or navigation conventions.

Android controls restricted settings. Only the phone owner can approve the OS confirmation, and a managed phone may prohibit it. See [Google Android Help](https://support.google.com/android/answer/12623953?hl=en). No installer spoofing, target-SDK downgrade, security-disable instruction or programmatic bypass is included.

## Permission-flow follow-up — 24 October 2026

Inspection of `main` at `34e0b2b` found that the manifest already declared `PACKAGE_USAGE_STATS` and the Usage access action already used `package:work.timelens.app`. The original disabled toggle recorded above is Android's restricted-settings gate, rather than a runtime permission dialog or a missing manifest declaration. The app cannot remove that OS gate. Its recovery must guide the owner through App info approval **and then a separate Usage access grant**. A denied return alone does not establish the reason for denial.

Additional gaps addressed:

- Preserve the Settings destination and feedback across activity recreation, including recreation while Settings is on top. Drop restored feedback if the actual grants changed.
- Recheck grants in `onResume`, on focus return, at 250/750/1500ms after return, during normal foreground refresh, and on **Check permissions again**. A Settings result code or empty event query is never treated as a grant.
- Use the public three-argument `AppOpsManager.checkOpNoThrow` (available since API 19; the `unsafe` alias is deprecated in API 36). Respect `MODE_DEFAULT` only when the underlying permission is actually granted.
- Fall back from a package-specific special-access screen to its permission list, App info and Android Settings without relying on package-visibility-sensitive resolution checks. Handle both missing Activities and security-denied launches. Leave manual directions and a retry action if every launch fails.
- Explain unsuccessful overlay and notification returns, count the two required grants separately from optional notifications, and open the timer channel directly when it is disabled. Starting the service still requires a separate user action and a fresh required-permission check.
- Extend native instrumentation for exact package/channel intent contracts, every fallback position, both launch exceptions, all-handlers-unavailable UI, preserved denial feedback, default AppOps state and Settings returns. Collect all scenario failures and still attempt Android 15/16 if Android 17 fails.

### Validation and device checks

Local validation uses JDK 17 and the Android 35 SDK: compile/link/dex/sign the app, compile the native instrumentation APK, run the 23 counting assertions, and check script syntax and whitespace. Android 15–17 runtime validation belongs to the three CI emulator runs; a compiled test APK is not a device-test pass. The previous `main` run passed its five initial Android 17 permission scenarios but failed later in the design scenario, so it did not establish an Android 15/16 pass.

On the Samsung S24 and a representative Android 15, 16 and 17 device/emulator:

1. Fresh launch with both required grants off: verify both are listed as missing; opening or cancelling Settings must not show them as allowed or start the timer.
2. If the Usage access switch is restricted, follow App info → ⋮ → Allow restricted settings and the system confirmation. Return, reopen Usage access and enable TimeLens. Verify the next step asks only for Display over apps. If Android's menu is absent or policy-controlled, confirm that the conditional guidance remains accurate.
3. Rotate or recreate TimeLens while in Settings, return with and without granting access, and verify correct feedback and the current checklist. Repeat after OS process death; only saved state can be restored, and grants must always be read again.
4. Cancel Display over apps, then enable it. Deny app notifications or disable only the timer category; verify that notifications remain optional and the category action opens the correct page.
5. Start the timer explicitly. Revoke Usage access or overlay access externally, return, and verify missing-access UI and service shutdown. Test both gesture Back and switching back through Recents.

References: [special permission lifecycle](https://developer.android.com/training/permissions/requesting-special), [Settings contracts](https://developer.android.com/reference/android/provider/Settings#ACTION_USAGE_ACCESS_SETTINGS), [AppOps API](https://developer.android.com/reference/android/app/AppOpsManager#checkOpNoThrow(java.lang.String,int,java.lang.String)), [Android 17 all-app changes](https://developer.android.com/about/versions/17/behavior-changes-all). Compile/target remain 35 and minSdk remains 29; no version-specific or hidden permission APIs are needed for this flow.
