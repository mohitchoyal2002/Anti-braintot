# TimeLens interface and interaction system

The interface applies the [Apple Design skill linked in the user's guide](https://github.com/dickwu/apple-design-skill/blob/main/SKILL.md), plus the earlier [emilkowalski apple-design skill](https://github.com/emilkowalski/skills/blob/main/skills/apple-design/SKILL.md). This is a native Android utility: accessibility, hierarchy, privacy and typography principles apply; Android system permission screens, fonts, Back behavior and 48dp minimum targets remain Android conventions.

[Prompt Motion](https://prompt-motion.com/) remains the requested motion reference. [MOTION.md](MOTION.md) maps the adapted direction to the existing interruptible press, progress and drag springs. Permission status never depends on animation.

## Design thesis

Make actual app time visible, with a short, understandable route through Android's permission controls. The signature is the reference ring and small live timer; permission setup uses a quiet checklist with written status and a single next action. Recovery is part of setup, not a help page people must discover elsewhere.

## Semantic color tokens

Colors come from Android day/night resources and follow the phone's appearance automatically. Ratios below are calculated from the sRGB values in resources, not sampled from screenshots.

| Role | Light | Dark | Contrast, light / dark |
| --- | --- | --- | --- |
| Surface | `#F5F7F4` | `#0B1014` | Background |
| Card | `#FFFFFF` | `#161E23` | Background |
| Content | `#14221A` | `#F1F5ED` | On card: 16.49 / 15.29 |
| Secondary content | `#55655B` | `#A2AFB3` | On card: 6.18 / 7.49 |
| Action/accent | `#2D6634` | `#C8F78D` | On card: 6.86 / 13.78 |
| Tinted surface | `#E9F1E7` | `#20332B` | Secondary content: 5.36 / 5.94 |

Primary buttons use the surface color as text on the accent: 6.37:1 light / 15.61:1 dark. Secondary buttons use content on `#DCE4DE` / `#2B373D`: 12.72:1 / 11.08:1. All these text pairs exceed 4.5:1.

## Type and layout

- Android system sans, normal and medium weights; tabular numeric timer digits. Setup display 32sp, detail title 22sp, body/actions 16sp, supporting labels 13–15sp. Data total 48sp, ring percentage 24sp.
- Native TextViews scale with the system setting, including the ring's percentage. Canvas draws only the ring, not text.
- Compact phones: one scrollable column, 24dp page inset, 20dp card inset, wrapping actions with minimum 52dp height. Checklist status sits below its name, so large text does not collide with a right-hand badge. Landscape can scroll to every action.
- Wider windows keep the same reading order and actions. A dedicated tablet navigation redesign is outside this fix; no functionality depends on width.
- The earlier decorative privacy chip in the header is removed. A short privacy explanation appears beside the permission decision where it is useful.
- Vector icons share the existing stroke style. Action views expose the Android Button accessibility role; statuses use words as well as color. Native ripple/focus feedback and system Back remain available.

## Permission interaction

1. Read actual grants on opening, returning from Settings and while visible. Usage Access and Display over apps are required for the floating timer. Notifications are recommended and optional; app-level and timer-channel blocking are both checked.
2. Explain the current missing permission, then let the person open its Android screen. Do not launch settings or a runtime dialog without a tap.
3. If Usage Access is still off after returning, show conditional restricted-settings recovery with an App info shortcut. A settings visit never counts as a grant.
4. Request notifications on a tap. Denial leaves a choice to continue. A later retry opens Android notification settings if another runtime prompt is unavailable.
5. Start the timer only after an explicit Start tap. Not now and Android Back open the dashboard with a setup action; a later foreground visit offers missing access again, as requested.

## Motion

Press feedback begins on touch down and uses a critically damped scalar spring. The timer follows a drag, projects release velocity and settles to an edge with independent interruptible X/Y springs. Native frame callbacks drive updates; the response is approximately 0.4 seconds with damping ratio 1.0. Android's disabled animator setting skips motion. A small haptic marks actions and drag release. No new decorative setup animation is needed.

## Verification

See [PERMISSION_UX_REVIEW.md](PERMISSION_UX_REVIEW.md) for the audit and platform boundary. The workflow records Android 15 permission-flow checks and screenshots, including light appearance and dark setup at 200% font scale. Its result is the authority on whether those scenarios passed. The Samsung restricted-settings confirmation, TalkBack traversal, battery behavior and motion feel require checking on the actual phone.
