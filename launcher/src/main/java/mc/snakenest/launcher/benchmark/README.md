# Short hardware benchmark

Settings → Performances starts an isolated helper JVM (18 second deadline), with CPU and GPU phases kept sequential. Closing the dialog forcibly stops the helper. No network requests, account credentials or modpack files are involved. Native libraries load only inside the helper, so driver errors cannot terminate the launcher.

CPU workload v1: 32-bit xorshift13/17/5, multiply1664525+1013904223 and XOR sink, blocks32768, 500ms warmup then3s measurement on one thread. GPU: fixed512×512 framebuffer, one full-screen triangle, fragment shader48 sin/fract iterations, varying seed; glFinish after each draw and no VSync, 500ms warmup then4s measurement. Renderer metadata is displayed; software or missing renderers never produce graphical usage advice.

Scores are raw synthetic iterations/s and pixels/s. The report also gives the average time for one 512×512 GPU test image, explicitly not a Minecraft frame time. CPU and GPU measurement validity are handled independently; missing/software renderers cannot validate a GPU result.

There is no calibrated model relating this workload to Minecraft performance, so no score thresholds classify a modpack or shaders as suitable. The report says light Minecraft needs an in-game check, heavy modpacks remain unvalidated, and shaders are not recommended without an in-game check. A 120 Hz target (8.33 ms/frame) is explicitly not evaluated. RAM capacity, modpack content, shader complexity, drivers, thermal throttling and the in-game renderer are not measured.
