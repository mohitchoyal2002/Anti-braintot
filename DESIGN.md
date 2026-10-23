# TimeLens design system · 0.3.0

Applies the [Apple Design skill](https://github.com/dickwu/apple-design-skill/blob/main/SKILL.md) linked in the user's [setup and audit guide](https://agiwithaditya.notion.site/Apple-Design-Skill-Simple-Setup-Website-Audit-Guide-Claude-Code-Codex-400d171fd705817aa761d7d306b3a8f8). This is native Android: use the foundations and craft guidance with Android controls, fonts, permission screens, TalkBack and 48dp touch targets. See [APP_DESIGN_AUDIT.md](APP_DESIGN_AUDIT.md).

## Thesis

Make actual time easy to glance at, then let people choose the detail. The signature is a small clock capsule: one icon and the total, with app detail a tap away. Settings reuses the same component and actual available data. Neutral surfaces and a restrained blue accent leave measured time in charge. The segmented track represents the user's adjustable daily reference; it is not a 24-hour clock or an app blocker.

## Semantic tokens

Ratios use sRGB luminance calculated from resource values. Day/night colors follow the phone.

| Role | Light | Dark | Contrast, light / dark |
| --- | --- | --- | --- |
| Surface | `#F6F7FA` | `#101216` | Background |
| Card / capsule | `#FFFFFF` | `#1C2028` | Background |
| Primary content | `#171C26` | `#F1F4FA` | On card: 17.07 / 14.81 |
| Secondary content | `#5D6676` | `#ADB6C5` | On card: 5.79 / 7.99 |
| Action / selection | `#3659D9` | `#ABBDFF` | On card: 5.85 / 8.90 |
| Secondary action surface | `#EBEFFE` | `#272F48` | Primary content: 14.88 / 12.01 |
| Divider / empty track | `#E1E5ED` | `#343C4B` | Decorative grouping |

Primary button labels use surface on accent: 5.46 / 10.23. Secondary copy on tint: 5.05 / 6.48. All listed text pairs exceed 4.5:1. The capsule is opaque so its contrast stays stable over photos and video, with a 1dp boundary and 4dp elevation.

## Type and layout

Android system sans, regular and medium weights, with tabular numbers. Scale: total 46sp; title 32sp; section 20–22sp; body/actions 15–16sp; support 13–14sp; tabs 12sp. No canvas-rendered text. Native text scales with the system.

| Surface | Compact layout | Wider / larger text |
| --- | --- | --- |
| App shell | Scrollable column, 24dp page and 20dp card insets | Centered within 720dp; same functions |
| Today | Total, reference, current app, timer action, ranked apps | App durations move below names above 1.2 font scale |
| History | Seven day cards with explicit detail affordance | Date and duration stack above 1.2 font scale |
| Day detail | Scrollable app list, title and Done outside scroll | Maximum 560dp width, bounded to current window |
| Capsule | Clock + total; 132dp width, minimum 48dp height | Grows with font scale and clamps to window bounds |
| Expanded capsule | Today total + app name/time; 224dp width | Wrapping app text and scalable height |
| Settings | Interactive capsule, reference, access, privacy | Android shortcuts progressively disclosed |

Buttons have 52dp minimum height. Switches and navigation meet Android's 48dp target. Simple original vector icons share rounded strokes. The clock denotes time and the four-app grid introduces app detail. No Apple fonts or SF Symbols are bundled.

## Flow and motion

Read actual permission grants on opening and returning. Usage and overlay access are required for the timer; notifications are optional. Settings open on a tap. Recovery remains conditional. Not now, Back and optional notification continuation stay available; missing access returns on a later visit as requested.

History uses disclosure text and ripple. Unavailable days remain distinct from zero. The daily detail's Done button stays outside its scrolling content. Capsule and preview expose meaningful descriptions and Show/Hide app detail accessibility actions. Reset timer position is a button alternative to dragging.

Press and reference changes use interruptible springs and honor disabled Android animators. The timer follows the finger and docks at an edge. Frequent time updates do not pulse, bounce or move the window. No animated blur or promotional video is needed. See [MOTION.md](MOTION.md).

## Validation boundary

The APK workflow runs native checks on Android 15, 16 and 17 and captures setup, recovery, Today, History, daily detail, Settings, and the actual compact/expanded/docked capsule. Covered appearances are normal light and dark 200% text. Check the result for the matching commit; a pending run is not a pass. Physical Samsung settings, TalkBack traversal, outdoor viewing and gesture feel still require device review.
