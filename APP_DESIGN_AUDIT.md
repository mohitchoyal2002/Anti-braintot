# Design review: TimeLens 0.3.0

## Summary

Source review: **Good**, with the identified Critical and High implementation findings addressed below. The app's job is to show real app time with minimal interruption; its distinctive element is the clock capsule, which reveals app detail on demand. Runtime evidence comes from the matching APK workflow; this report does not turn a pending run into a pass.

Visual basis: the user-selected VoltAgent Apple DESIGN.md supplies Action Blue #0066CC, parchment #F5F5F7, ink #1D1D1F, Inter as an off-platform substitute, 600-weight headlines and pill actions. The final implementation adds bounded functional blur and optional motion/haptics before this review.

Scope: all TimeLens-owned surfaces—permission setup/recovery, optional notifications, ready state, Today, app rows, History, daily detail, Settings and preview, navigation, compact/expanded floating windows, foreground notification and restart reminder. Reviewed native Java layouts/resources with the [Apple Design skill](https://github.com/dickwu/apple-design-skill/blob/main/SKILL.md) from the user's guide. Its Android-only boundary applies: foundations and craft translate; OS settings and installer dialogs remain Android/Samsung-controlled.

## Critical

**Resolved — floating touch target.** The former compact branch removed its second line without enforcing a minimum target height. `accessibility.md › Mobility`: “Offer sufficiently sized controls.” The cross-platform companion specifies 48dp for Material. `TimerCapsule.java` now enforces that minimum, supports focus and Button semantics, and labels expansion/collapse. Reset position supplies a button alternative to dragging.

## Improvements

| Severity / status | Finding and reason | Fix |
| --- | --- | --- |
| High / resolved | The default 205dp two-line overlay occupied more of another app than necessary. `layout.md › Visual hierarchy`: “Use progressive disclosure to make layouts cleaner and easier to interact with.” | `TimerCapsule.java`, `OverlayService.java`: 132dp icon-and-total default, about 36% narrower at normal text size. Tap expands app details. |
| High / resolved | Names, dates and durations competed horizontally at large text sizes. `typography.md › Conveying hierarchy`: “Prioritize important content when responding to text-size changes.” | `MainActivity.java`, `AppRow` and `buildHistory`: wrap app names and stack durations at font scale above 1.2. Text stays in sp. |
| High / resolved | Closing a long daily breakdown required reaching the end of its list. Reviewer judgment, supported by layout and reachable-control principles. | `showDay`: separate list scroll region; title and Done remain outside it. Bound width to current window/560dp and retain Android Back. |
| Medium / resolved | Large slogans and a separate ring postponed the useful app list. Reviewer judgment. | `buildToday`: direct title/date, actual total, compact reference track, written reference and percentage, current app and ranked activity. |
| Medium / resolved | Accent color competed across controls and passive data. `color.md › Best practices`: “Avoid using the same color to mean different things.” | Neutral duration text, restrained blue actions/selection/progress, semantic light/dark tokens. Contrast figures below. |
| Medium / resolved | Settings did not show the actual timer being configured. `onboarding.md › Best practices`: “Teach through interactivity.” | `buildSettings`: interactive preview reuses the live component and stored detail preference, with actual available usage. |
| Medium / resolved | Repeated Android shortcuts expanded Settings before they were needed. Same progressive-disclosure guidance. | Show/Hide Android settings groups the three special settings shortcuts; review and battery actions stay immediate. |
| Medium / resolved | Translucency made overlay contrast depend on another app's content. `dark-mode.md › Dark Mode colors`: “Aim for sufficient color contrast in all appearances.” | Native bounded blur with a high-opacity tint; Reduce transparency, system contrast, battery saving and native blur-disabled states restore an opaque material. Content cards remain opaque. |
| Medium / resolved | History drill-down lacked a visible navigation hint and press feedback. `lists-and-tables.md › Best practices`: “Provide appropriate feedback when people select a list item.” | Written View app activity action, ripple, focus and meaningful accessible description. Missing dates stay noninteractive. |
| Low / resolved | Pre-settings labels could imply the app grants access. Privacy/consent principle; Android wording is reviewer judgment. | Open display settings and Set up notifications describe the next step; actual OS grants are checked on return. |

## Craft notes

The capsule is the one distinctive object: a 20dp clock, steady tabular duration, restrained frosted surface and one blue accent. The expanded state adds an 18dp app-grid icon and wrapping app details. It does not become a dashboard over someone else's app.

`icons.md › Best practices`: “Maintain visual consistency across all interface icons in your app.” Original vectors share simple rounded strokes, with labels/accessibility descriptions for meaning. `motion.md › Best practices`: “Aim for brevity and precision in feedback animations.” Drag follows input and docking is interruptible; frequently updating digits remain still. Reduce transparency, Animations and Haptic feedback now provide explicit control. Glass stays on functional layers; no content row or text is blurred.

## What works

- Permissions use actual Android grants and written required/optional status. A Settings visit never means approval. Blocked-access help explains recovery without promising to override the phone.
- Start is explicit; notifications remain optional. Not now and Back keep the dashboard reachable. Missing access returns on later visits because the user requested that behavior.
- Missing records remain distinct from zero. No synthetic usage, account, ads or Internet access.
- Android Back, scalable licensed Inter typography, system appearance, switches, ripple and notification controls remain familiar.

## Contrast and dimensions

Computed from resource colors using sRGB relative luminance:

| Text/background | Light | Dark |
| --- | --- | --- |
| Primary/card | 16.83:1 | 15.63:1 |
| Secondary/card | 6.06:1 | 8.71:1 |
| Accent/card | 5.57:1 | 5.64:1 |
| Primary button label/action | 5.57:1 | 5.57:1 |
| Blue label/tint | 4.97:1 | 4.96:1 |

All listed text pairs exceed 4.5:1. Worst neutral backdrops under the tinted glass retain secondary text contrast of 5.08:1 light / 6.94:1 dark; the floating clock icon exceeds the 3:1 non-text threshold. No small blue text is placed on the dark floating glass. Buttons have 52dp minimum height; the capsule has a 48dp minimum and grows with system text. Exact colors, type sizes and compact/wide mappings are in [DESIGN.md](DESIGN.md).

## Platform notes and verification

The phone layout scrolls; wider windows center the same functions within 720dp. The daily dialog has its own scrolling list and persistent exit. These use native Android controls and public blur APIs; they translate the Apple reference without importing proprietary fonts, symbols or Apple framework code.

Native instrumentation covers permission denial/recovery, Settings return, optional notifications, foreground start/stop, revocation, notification-channel blocking, 200% dark setup and complete design journeys in light and 200% dark appearance. Design journeys capture Today, History, daily detail and Settings, then leave the app to test the real floating window: accessible expand/collapse actions, target size, display bounds and saved drag position. The workflow runs them on Android 15, 16 and 17 before APK publication. Counting has 23 independent domain assertions. See the matching run's result for pass/fail and evidence.

Manual checks remain: physical Samsung restricted-settings confirmation, TalkBack traversal, finger-drag/haptic feel, outdoor viewing and unusual display scaling. Those are not claimed as emulator validation. Existing per-run debug signing remains a distribution limitation: installing a later run may require uninstalling the old test build, which deletes history. Configure persistent private signing before routine distribution.

## New material and feedback checks

- Glass review: only the functional navigation/capsule/detail uses blur. Opaque cards preserve the content layer. Android native blur capability is observed and the fallback is opaque. The timer never dims or blurs the entire underlying app.
- Typography: Inter-derived Latin subset, OFL bundled in the APK, weight 400/600, 17sp reading text and 34sp titles. Native font scaling and script fallback remain available.
- Motion: short page/sheet/detail transitions and existing interruptible springs. Animations can be disabled in Settings and via the Android animator setting. There is no looping digit animation.
- Haptics: short selection feedback on discrete actions, a saved opt-out and system preference respect. No continuous feedback and no emulator claim about physical feel.
- Icon: original adaptive foreground plus monochrome themed-icon resource. Instrumentation checks both and exports the actual packaged icon.

## Implementation references

Source entry points: MainActivity.java (renderSetup, buildToday, AppRow, buildHistory, showDay, buildSettings); TimerCapsule.java (setExpanded, useWindowMaterial); OverlayService.java (createPill, render, hide); FrostedBar.java; WindowMaterial.java; Ui.java (text, button, settingToggle, enter, haptic). Exact validated source anchors are added with the final workflow result.
