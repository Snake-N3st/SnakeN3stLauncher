# game

Minecraft installation and launch, split behind two small interfaces so
nothing outside `game.flowupdater`/`game.openlauncherlib` ever imports a
third-party library type:

- **`GameInstallService`** / **`InstallRequest`** / **`GameInstallListener`**
  / **`InstallStep`** — install vanilla Minecraft + an optional mod loader
  into an instance directory. Implemented by
  `game.flowupdater.FlowUpdaterGameInstallService`, the only class that
  imports `fr.flowarg.flowupdater.*` (GPL-3.0).

- **`GameLaunchService`** / **`LaunchRequest`** — spawn the actual
  Minecraft process for an already-installed instance. Implemented by
  `game.openlauncherlib.OpenLauncherLibGameLaunchService`, the only class
  that imports `fr.theshark34.openlauncherlib.*`/`fr.flowarg.openlauncherlib.*`
  (GPL-3.0). `LaunchRequest` carries the player's Ed25519 seed (hex) to
  pass as `-Dsn3.token=...` - a secret, so its `toString()` redacts it
  explicitly rather than using the default record one. It also carries
  `memoryMb`/`extraJvmArgs` (see `modpack.ModpackSettings` - the "Gerer"
  action on the modpack detail page); the launch service turns those into
  `-Xmx<memoryMb>M` plus the raw extra args, appended before `-Dsn3.token`.

- **`ModLoader`** — `VANILLA`/`FORGE`/`FABRIC`/`NEOFORGE`/`UNKNOWN`, shared
  with `modpack` (which parses it off the manifest's `loader` field) so the
  four accepted values live in exactly one place. Vanilla installs are
  supported like any other loader (a vanilla client just won't have the
  in-game passwordless-login mod - see the class Javadoc on
  `FlowUpdaterGameInstallService`) - don't reject a loader preemptively,
  only if it actually turns out to fail.

  **Forge/old-NeoForge version string gotcha**: FlowUpdater's own
  `ForgeVersion`/`NeoForgeVersion` classes expect the loader version passed
  in to already be `"<mcVersion>-<loaderBuild>"` (e.g. `"1.20.1-47.2.20"`)
  for Forge (and for NeoForge's very first release, 1.20.1 only) - they
  `split("-")` internally and index `data[1]`. Our manifest's
  `loaderVersion` only carries the bare build number (e.g. `"47.2.20"`), so
  passing it straight through crashed with
  `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1`
  inside `ForgeVersion`'s constructor. Fixed in
  `FlowUpdaterGameInstallService` (`mcVersionPrefixed`/`neoForgeVersion`)
  by prefixing `mcVersion + "-"` ourselves before calling into FlowUpdater,
  rather than requiring the site to store the combined string - Fabric and
  modern NeoForge versions are self-contained and passed through as-is.

- **`OfflineUuids`** — derives the offline-mode UUID from a username using
  vanilla Minecraft's own convention (`UUID.nameUUIDFromBytes` of
  `"OfflinePlayer:"+username`, the same algorithm the JDK already
  implements - nothing hand-rolled).

**Why this split exists**: FlowUpdater is GPL-3.0-only and OpenLauncherLib
is GPL-3.0 - see the top-level plan/NOTICE for the licensing
consequence of embedding them. The interface boundary here is about clean
code (swap either library later without touching callers), not about
avoiding that consequence - both libraries are normal `compile`
dependencies of this module.

**Verification status**: every FlowUpdater/OpenLauncherLib method signature
used in the two adapter classes was checked against the actual published
jars (`javap` + the upstream source), not guessed from documentation alone.
What hasn't been exercised in this environment is a full real install +
launch (that means downloading an actual Minecraft distribution) - worth a
manual end-to-end smoke test (pick one real modpack, install it, click
"Démarrer") before relying on this in production.
