# TimeLens motion and feedback

References: [Prompt Motion](https://prompt-motion.com/), the user's [Apple visual reference](https://github.com/VoltAgent/awesome-design-md/blob/main/design-md/apple/DESIGN.md), and the Apple Design skill's motion and haptics guidance. No promotional video or external runtime is bundled.

## Original implementation prompt

Use parchment and near-black surfaces with Action Blue, clear Inter typography and restrained functional frosting. Let TimeLens's real duration be the focal point. Reveal a new page with a brief fade and a small upward settle. Keep digits steady. A button compresses immediately under the finger, then settles on release. App detail appears only on demand. The floating clock tracks the finger and docks at an edge with a short selection tick. Glass stays bounded to navigation, floating timer and the open detail; do not blur content or animate blur. Provide reduced transparency, disabled motion and optional haptics.

## Native implementation

| Surface | Behavior |
| --- | --- |
| Navigation | 180ms opacity/6dp enter; interruptible and skipped when motion is off |
| Daily detail | 180ms enter / 140ms exit; native floating window animation |
| Buttons | Ripple and critically damped press/release spring |
| Reference | Display-frame progress spring; native per-app progress |
| Capsule expansion | Additional detail fades in; dimensions and controls remain usable immediately |
| Drag | Direct tracking, edge resistance, velocity projection and docking springs |
| Haptics | Optional short ticks on discrete actions, selections and release; respects system feedback settings |
| Accessibility | Motion preference and system animator setting skip animation; reduced transparency/system contrast/battery saving use solid materials |

No loading sequence blocks app access. Timer updates do not animate, shake, pulse or vibrate. The emulator verifies state changes and preference handling; the physical sensation of haptics needs phone testing.
