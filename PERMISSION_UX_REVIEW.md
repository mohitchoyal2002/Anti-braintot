# Design review: TimeLens permission recovery

## Summary

Before this change: **Needs work**. The supplied Samsung screenshot shows a disabled Usage Access switch controlled by a restricted setting. The app's old permission buttons did not explain that blocker or guide a first-time user through recovery. The single job of the revised screen is to help the user understand and grant the access that makes their screen-time timer work.

Evidence: the supplied screenshot, source review, measured resource colors and compiled Android code. The screenshot proves the observed OS restriction, not that any programmatic grant is possible. Device/emulator results are recorded separately in GitHub Actions; Samsung-specific confirmation cannot be verified by code inspection or an AOSP emulator.

## Improvements

- **High — Permission dead end.** `MainActivity.java: usageSettings`, `PermissionSetup.java: USAGE`: explain the disabled-switch case, open App info directly, show **⋮ → Allow restricted settings → confirmation**, then return to Usage Access. Keep the wording conditional because a denied grant does not prove the cause. `onboarding.md › Additional requests`: “consider integrating the permission request into your onboarding flow.” [Android's special-permission guidance](https://developer.android.com/training/permissions/requesting-special) requires a system Settings flow and a real grant recheck.
- **High — No first-open sequence or recovery on return.** `MainActivity.java: onResume, onStop, renderSetup`, `PermissionState.java`: show a sequential setup when access is missing, recheck on return, preserve recovery across recreation, and allow a per-visit exit. Required access and optional notifications have explicit labels. `privacy.md › Requesting permission` informs the contextual explanation and user choice; native Android dialogs remain user-triggered.
- **Medium — Appearance and text scaling gaps.** `Ui.java`, day/night resources, `MainActivity.java: Ring`: add system light/dark appearance and replace fixed-size canvas text with a scalable TextView. Setup uses wrapped vertical statuses, 16sp body/actions and at least 52dp action targets. `accessibility.md › Vision`: “Support larger text sizes.” `cross-platform.md › Conventions to check`: Android targets use 48dp. Primary/secondary text contrast is documented with computed values in DESIGN.md.

## Craft notes

The live timer and reference ring are specific to a time-awareness utility. Permission setup stays quiet and functional. The three numbered items represent the actual sequence of Android permissions. Removing the header privacy chip reduces decoration; the relevant privacy explanation now accompanies each permission choice. No additional animation is needed for setup; Android's reduced-motion setting still applies to existing springs.

## What works

Keep the on-device data model, absence of Internet/Accessibility permissions, explicit start/stop action, consistent vector icons, visible optional-notification exit and bottom navigation for the dashboard. Never imply that opening Settings granted access or automatically start the foreground service after a permission change.

## Platform notes

The user's linked guide resolves to [dickwu/apple-design-skill](https://github.com/dickwu/apple-design-skill). Read references: accessibility, layout, typography, color, designing-for-ios, cross-platform, onboarding, privacy, buttons and code-audit. The skill explicitly limits an Android-only app to principles and foundations. This implementation uses Android special access screens, notification runtime permission, native Back and system fonts; it does not import iOS permission or navigation conventions.

Android controls restricted settings. Only the phone owner can approve the OS confirmation, and a managed phone may prohibit it. See [Google Android Help](https://support.google.com/android/answer/12623953?hl=en). No installer spoofing, target-SDK downgrade, security-disable instruction or programmatic bypass is included.
