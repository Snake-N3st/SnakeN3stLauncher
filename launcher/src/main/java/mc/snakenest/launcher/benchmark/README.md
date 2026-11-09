# Short hardware benchmark

Settings → Performances starts an isolated helper JVM (18 second deadline), with CPU and GPU phases kept sequential. Closing the dialog forcibly stops the helper. No network requests, account credentials or modpack files are involved. Native libraries load only inside the helper, so driver errors cannot terminate the launcher.

CPU workload v1: 32-bit xorshift13/17/5, multiply1664525+1013904223 and XOR sink, blocks32768, 500ms warmup then3s measurement on one thread. GPU: fixed512×512 framebuffer, one full-screen triangle, fragment shader48 sin/fract iterations, varying seed; glFinish after each draw and no VSync, 500ms warmup then4s measurement. Renderer metadata is displayed; software or missing renderers never produce graphical usage advice.

Scores are synthetic iterations/s and pixels/s, not game FPS. Usage thresholds (CPU40/120/240Miter/s, GPU4/15/50Mpixel/s) provide rough guidance only. RAM capacity, modpack content, shader complexity, drivers, thermal throttling and in-game renderers are not measured. These thresholds are not calibrated Minecraft performance guarantees.
