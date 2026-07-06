# Third-party notices

This repository produces two artifacts with different licensing:

- **`bootstrap`** — no third-party runtime dependency beyond Gson
  (Apache-2.0). Contains no GPL code.
- **`launcher`** — the complete application. Embeds two GPL-3.0-licensed
  libraries (see below), so **the `launcher` module is distributed under
  the GNU General Public License v3.0** (see `launcher/LICENSE`). The
  `common` module (shared, tiny utilities) and `bootstrap` are not GPL
  themselves, but `launcher` as a combined work is.

Full license texts for every dependency listed below are bundled inside the
`launcher` jar under `licenses/` (`src/main/resources/licenses/`), viewable
from the launcher's "A propos" screen.

## Dependencies of `launcher`

| Library | Version | License | Used for |
|---|---|---|---|
| [FlowUpdater](https://github.com/FlowArg/FlowUpdater) (`fr.flowarg:flowupdater`) | 1.9.4 | **GPL-3.0** | Downloading vanilla Minecraft and installing Forge/Fabric/NeoForge |
| [OpenLauncherLib](https://github.com/FlowArg/OpenLauncherLib) (`fr.flowarg:openlauncherlib`) | 3.2.11 | **GPL-3.0** | Building the game's classpath/JVM args and spawning the Minecraft process |
| [net.i2p.crypto:eddsa](https://github.com/str4d/ed25519-java) | 0.3.0 | CC0 1.0 Universal (public domain) | Ed25519 signing and public-key derivation |
| [Gson](https://github.com/google/gson) | 2.14.0 | Apache-2.0 | JSON (de)serialization |
| [FlatLaf](https://github.com/JFormDesigner/FlatLaf) | 3.7.1 | Apache-2.0 | Swing Look & Feel (light/dark theme) |

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

Generic UI icons (play, document, gear, back-arrow, account, folder,
download) in `ui.common.Icons` are drawn directly with Java2D - original
shapes, not derived from any external asset. The Twitch/Discord sidebar
icons are deliberately generic placeholders (a "screen" and a "chat
bubble"), not the real trademarked logos - see that class's Javadoc.
