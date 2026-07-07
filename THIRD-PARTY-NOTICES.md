# Third-party notices

This repository produces two distributed artifacts, each under its own
license (see each module's own `LICENSE` file):

- **`bootstrap`** — no third-party runtime dependency beyond Gson
  (Apache-2.0). Contains no GPL code. **Proprietary, All Rights Reserved**
  (Snake N3st) — never combines with GPL code, so it's under no copyleft
  obligation and stays closed at the copyright holder's discretion.
- **`launcher`** — the complete application. Embeds three GPL-3.0-licensed
  libraries (see below), so **the `launcher` module is distributed under
  the GNU General Public License v3.0** (see `launcher/LICENSE`).
- **`common`** — shared, tiny utilities used by both `bootstrap` and
  `launcher`. **GPL-3.0** (see `common/LICENSE`): not because it depends on
  any GPL code itself (it doesn't), but because a compiled copy of it ends
  up embedded in `launcher`'s combined GPL binary, whose Corresponding
  Source must be available under GPL-compatible terms. This doesn't force
  `bootstrap` to be GPL too - `bootstrap` never combines with GPL code, and
  the copyright holder of `common` (Snake N3st, sole author of this
  original code) is free to license the same source differently per
  product it's used in.

Full license texts for every dependency listed below are bundled inside the
`launcher` jar under `licenses/` (`src/main/resources/licenses/`), viewable
from the launcher's "A propos" screen.

## Dependencies of `launcher`

| Library | Version | License | Used for |
|---|---|---|---|
| [FlowUpdater](https://github.com/FlowArg/FlowUpdater) (`fr.flowarg:flowupdater`) | 1.9.4 | **GPL-3.0** | Downloading vanilla Minecraft and installing Forge/Fabric/NeoForge |
| [FlowMultitools](https://github.com/FlowArg/FlowMultitools) (`fr.flowarg:flowmultitools`, transitive via FlowUpdater) | 1.4.5 | **GPL-3.0** | Internal utilities used by FlowUpdater |
| [OpenLauncherLib](https://github.com/FlowArg/OpenLauncherLib) (`fr.flowarg:openlauncherlib`) | 3.2.11 | **GPL-3.0** | Building the game's classpath/JVM args and spawning the Minecraft process |
| [org.apache.commons:commons-compress](https://commons.apache.org/proper/commons-compress/) (transitive, via FlowMultitools) | 1.28.0 | Apache-2.0 | Archive handling used internally by FlowUpdater |
| [commons-codec](https://commons.apache.org/proper/commons-codec/) (transitive) | 1.19.0 | Apache-2.0 | Used internally by commons-compress |
| [commons-io](https://commons.apache.org/proper/commons-io/) (transitive) | 2.20.0 | Apache-2.0 | Used internally by commons-compress |
| [commons-lang3](https://commons.apache.org/proper/commons-lang3/) (transitive) | 3.18.0 | Apache-2.0 | Used internally by commons-compress |
| [JetBrains Annotations](https://github.com/JetBrains/java-annotations) (transitive, via FlowUpdater) | 26.1.0 | Apache-2.0 | Nullability annotations |
| [org.json](https://github.com/stleary/JSON-java) (transitive, via OpenLauncherLib) | 20240303 | Public Domain | JSON parsing used internally by OpenLauncherLib |
| [net.i2p.crypto:eddsa](https://github.com/str4d/ed25519-java) | 0.3.0 | CC0 1.0 Universal (public domain) | Ed25519 signing and public-key derivation |
| [Gson](https://github.com/google/gson) | 2.14.0 | Apache-2.0 | JSON (de)serialization |
| [FlatLaf](https://github.com/JFormDesigner/FlatLaf) | 3.7.1 | Apache-2.0 | Swing Look & Feel (light/dark theme) |
| [DiscordIPC](https://github.com/jagrosh/DiscordIPC) (`com.github.jagrosh:DiscordIPC`, via JitPack - see `discord/README.md`) | commit `a8d6631` | Apache-2.0 | Optional Discord Rich Presence |
| [junixsocket](https://github.com/kohlschutter/junixsocket) (transitive, via DiscordIPC) | 2.6.2 | Apache-2.0 | Unix domain socket used by DiscordIPC on Linux/macOS - the launcher's only native-code dependency |
| [SLF4J API](https://www.slf4j.org/) (transitive, via DiscordIPC) | 2.0.7 | MIT | Logging facade required by DiscordIPC (unused otherwise - this launcher uses `java.util.logging`) |

## Dependencies of `bootstrap`

| Library | Version | License | Used for |
|---|---|---|---|
| [Gson](https://github.com/google/gson) | 2.14.0 | Apache-2.0 | Parsing the launcher-release API response |

## Test-only dependencies (not shipped in either jar)

| Library | License |
|---|---|
| JUnit Jupiter | EPL-2.0 |
| Mockito | MIT |

## Icons

All UI icons (play, document, gear, back-arrow, account, folder, download,
refresh) in `ui.common.Icons` are drawn directly with Java2D - original
shapes, not derived from any external asset. No Twitch/Discord glyphs are
bundled - the sidebar shortcuts they were for were removed.
