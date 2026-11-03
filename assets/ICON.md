# Launcher artwork — 0.10.4

Source: user-supplied `7344.jpg`, preserved byte for byte as `assets/mascot-thumbs-up.jpg`.
The README uses that original file. No generative edits, recolouring or background removal were applied.

Android assets are deterministic Lanczos resizes of the supplied image:

- Legacy launcher PNGs: 48, 72, 96, 144 and 192 px, with 6% white padding per side.
- Adaptive foreground: 432 px transparent canvas with a centred 216 px image. The existing adaptive background remains white. This framing keeps the face and thumbs-up readable under round and rounded-square masks.
- Both adaptive icon XML files reference the same foreground.

The previous mascot assets are retained as historical artwork.
