# TimeLens · Apple-reference design system

Sources: [VoltAgent Apple DESIGN.md](https://github.com/VoltAgent/awesome-design-md/blob/main/design-md/apple/DESIGN.md) for visual tokens; [Apple Design skill](https://github.com/dickwu/apple-design-skill/blob/main/SKILL.md) from the user's guide for the audit. This is an Android utility, so platform Back, permissions, touch targets and accessibility remain Android-native.

## Thesis

Make real time feel quiet and tangible. The signature is a small frosted clock capsule, with current-app detail one tap away. The same object appears in Settings. Time is the content; glass, motion and haptics explain the controls around it.

## Palette

The reference's Action Blue, parchment and near-black ink replace the earlier indigo palette. Secondary colors are adapted for legibility, and dark counterparts are supplied where the web reference has gaps. Ratios are calculated from the resource values.

| Role | Light | Dark | Contrast on card, light / dark |
| --- | --- | --- | --- |
| Canvas | #F5F5F7 | #000000 | Background |
| Card | #FFFFFF | #1C1C1E | Background |
| Ink | #1D1D1F | #F5F5F7 | 16.83 / 15.63 |
| Secondary ink | #626268 | #B9B9BF | 6.06 / 8.71 |
| Links / selection | #0066CC | #2997FF | 5.57 / 5.64 |
| Primary action | #0066CC | #0066CC | White label: 5.57 / 5.57 |
| Secondary surface | #EBF3FC | #14283E | Blue label: 4.97 / 4.96 |
| Hairline | #E0E0E0 | #38383C | Decorative grouping |

Cards and list content are opaque. Functional layers use regular, restrained frosting. The navigation fill uses alpha 240/255, and native floating windows use 235/255. At the worst neutral backdrop, nav text is at least 5.32 light / 7.44 dark; floating secondary text is 5.08 light / 6.94 dark. Blue on the floating dark material is used for a clock icon (4.49:1, above the 3:1 non-text threshold), not small blue text.

## Typography and geometry

Licensed Inter 4.1, with a renamed Latin subset and Android script fallback, substitutes for the reference's proprietary SF Pro. Body is regular; headings are weight 600. Display tracking is slightly negative and the optical-size axis uses 32 for display text. No Apple font is bundled. See THIRD_PARTY_NOTICES.md and the bundled OFL.

Scale: total 48sp; title 34sp; section 20–22sp; reading/action 17sp; support/data 13–16sp; tabs 12sp. Native text respects font scale; no text is canvas-painted. Large app names wrap, and app/history durations move below names above 1.2 font scale.

Utility cards use 18dp radii, 20dp padding and no shadow. Main actions are full pills with at least 52dp height. Page inset is 24dp. Wider windows center the same hierarchy within 720dp. The detail dialog is bounded to the current window and 560dp width, with a persistent Done control.

The compact capsule is 132dp wide at normal font size and at least 48dp high. Expanded detail is 224dp wide. Both grow with the person's font setting and clamp to current window bounds.

## Materials

- Navigation: a small, half-resolution sample of TimeLens's own scrolling content, blurred behind sharp controls using Android RenderEffect. No other app is sampled.
- Timer: an Android floating overlay dialog using Window.setBackgroundBlurRadius, clipped to its rounded background. It does not blur or dim the rest of the app underneath.
- Daily detail: a native floating material; modest blur behind and dim focus attention on the open detail. It is dismissed before opening another detail.
- Reduce transparency, high system contrast and battery saving use solid fills. Native blur capability changes also switch the window material to an opaque fallback. Unsupported capture/render paths fall back safely.
- Content cards, app rows and the main canvas remain opaque. Blur is never applied to labels.

## Motion and haptics

Navigation enters with a 180ms fade and 6dp travel. The daily sheet uses a short enter/exit transition. Buttons have immediate ripple and an interruptible press spring; expansion fades the extra detail. Drag follows the finger, then docks using independent interruptible springs. Timer digits never pulse or bounce.

Settings exposes Animations and Haptic feedback. Android's disabled-animator and haptic settings are honored. Short selection ticks accompany taps, toggle changes, reference steps and drag release; nothing vibrates continuously. See MOTION.md.

## Icon

An original blue clock/lens mark sits on a ceramic canvas. The foreground stays in the adaptive safe zone. Android adaptive and monochrome versions support launcher masks and themed icons; the legacy vector uses the same mark. All interface icons remain original, simple vectors with labels or accessible descriptions.

## Audit and evidence

See APP_DESIGN_AUDIT.md. The APK workflow exercises permission flows and complete design journeys on Android 15, 16 and 17, in light and dark 200% text. It captures real app screens, daily detail, Settings, the capsule and the packaged adaptive icon. Preferences and themed-icon presence are checked. A pending run is not a pass; physical Samsung blur/haptic feel and TalkBack traversal still require device review.
