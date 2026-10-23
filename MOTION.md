# Motion direction and reusable prompt

User-selected reference: [Prompt Motion](https://prompt-motion.com/). I read the gallery and its [TypingMind reveal example](https://prompt-motion.com/tdinh-me-815acb) and [engraving ad example](https://prompt-motion.com/lexnlin-038035). The useful adaptation is staged vector reveals and precise transitions; these are original TimeLens instructions, not copied prompts or imported video assets.

## Original motion prompt for TimeLens

Design a calm motion identity for TimeLens, a native Android screen-time companion. Use quiet neutral surfaces, clear system typography and one blue accent. Reveal its original clock mark through a clean circular stroke, then resolve into the real segmented daily reference track. Keep app labels and measured timer values steady and legible. Progress bars extend from their own leading edge when data changes. Buttons compress slightly at touch-down and settle when released. The floating timer follows the finger exactly; a release carries its velocity into a soft edge docking spring. Use consistent spatial origins and preserve current position and velocity when interrupted. Prioritize clear app usage and total time. Honor Android font scaling, disabled animations and opaque timer materials. Keep decorative movement brief and purposeful; avoid full-screen video backgrounds, simulated usage counts, flashing effects, continuous loops and delays before controls become usable.

## Implemented in the app

| Element | Native behavior |
| --- | --- |
| Daily reference track | Display-frame-driven progress reveal; subsequent changes retarget the same spring |
| Per-app usage bars | Native animated progress on real data updates |
| Buttons | Immediate ripple and interruptible press/release spring |
| Floating timer | Direct drag, progressive edge resistance, projected release and independent X/Y docking springs |
| Motion preference | Uses Android's system animator-enabled setting; no wait before controls respond |
| Timer material | Opaque surface in each appearance; no animated blur or varying contrast |

This is an in-app motion specification and implementation. No promotional video was generated or bundled into the APK, and there is no runtime dependency on Prompt Motion or a generative model. Actual visual and gesture quality still needs phone testing.
