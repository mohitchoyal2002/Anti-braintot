# TimeLens interface and interaction system

Applied the user-requested [apple-design skill](https://github.com/emilkowalski/skills/blob/main/skills/apple-design/SKILL.md), adapted to native Android. The skill's web examples were used as design guidance; the app remains native Java and uses Android system APIs and fonts.

The user also selected [Prompt Motion](https://prompt-motion.com/) for motion direction. See [MOTION.md](MOTION.md) for the original adapted prompt and its mapping to the implemented ring, bars, press and drag animations.

## Visual system

- Dark, low-distraction surfaces: near-black `#0B1014`, slate cards `#161E23`, clear foreground `#F1F5ED`, secondary `#94A2A8` and a restrained lime accent `#C8F78D`.
- System sans-serif typography, tabular timer digits, slightly tighter display headings and gently expanded caption tracking. Text uses Android `sp` so system font size is respected; the overlay width grows with font scale.
- Consistent rounded surfaces, an explicit total-time hierarchy, familiar bottom navigation and grouped per-app rows. Touch targets are at least 52dp for buttons and 64dp for navigation.
- Original Android vector icons for Today, History, Settings and privacy, with matching 1.8-unit rounded strokes. The application icon is also vector-based. No emoji or Apple SF Symbol assets are required.
- The small floating pill uses a near-opaque tinted surface with a subtle edge and shadow. A **Solid timer background** option removes transparency when greater separation is preferred.

## Interaction behavior

- Press feedback starts on touch-down. Buttons use a critically damped scalar spring and preserve velocity when its target changes.
- The timer follows a drag continuously, keeping the initial finger-to-window offset. Small movement is treated as a tap; edge overshoot meets progressive resistance.
- Releasing the pill projects the gesture velocity, chooses the corresponding side of the screen and settles independent X/Y springs. The spring can be interrupted immediately by another touch; no input is disabled to wait for motion.
- Native display-frame callbacks drive spring updates. The response parameter is approximately 0.4 seconds and damping ratio is 1.0. Motion is skipped when Android's system animator setting is disabled; press/selected-state feedback remains.
- A small haptic is reserved for committing an action or releasing a drag. No sounds, looping decorative animation, moving wallpaper or flashing feedback.
- Timer start/stop changes are driven by service-state notifications inside the app. Arbitrary waits were removed from those UI transitions. Resetting the timer's position updates the running service directly.
- Daily details open in a bottom sheet with a visible grab affordance and Done button; Android Back and outside-tap dismissal remain available.

## Verification boundary

Resources and Java compile against the public Android SDK. Actual motion feel, large-font layout, rotation and overlay visibility still require Android device testing. The design principles have been implemented, not represented as visually tested screenshots.
