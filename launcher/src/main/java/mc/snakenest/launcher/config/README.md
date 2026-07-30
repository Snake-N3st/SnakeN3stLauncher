# config

Persisted user preferences (`config.json`, plain JSON, never encrypted).

- **`Theme`** — `LIGHT` or `DARK`.
- **`AssetStorageMode`** — where vanilla Minecraft assets/libraries are
  stored, i.e. the "Stockage des ressources vanilla" setting:
  `LAUNCHER_SHARED` (default - one shared pool in the launcher's own data
  dir), `OFFICIAL_MINECRAFT` (share the real `.minecraft` folder, saving the
  most space), or `PER_MODPACK` (no sharing, a full copy per instance - the
  original behavior). Implemented by `game.assets.SharedAssetsLinker`; see
  that package's README for how each mode actually works on disk.
- **`LauncherConfig`** — the POJO that gets (de)serialized: the theme, and
  the site `playerId` paired with the locally-stored key (not a secret -
  see the field's Javadoc - but required on every signed request, so it
  has to survive a restart alongside the key itself). **Never add a secret
  field here** - this file has no encryption and no restricted permissions;
  the private key lives in `mc.snakenest.launcher.crypto` instead. A field
  added here keeps its initializer as the default when reading a
  `config.json` written before that field existed (GSON leaves absent keys
  alone - verified against a real pre-change file, not assumed), so adding
  an option needs no migration.
- **`ConfigStore`** — load/save, atomic writes via `util.AtomicFiles`, falls
  back to defaults on a missing or corrupt file rather than failing to
  start.

`baseUrl` and `clientId` are deliberately **not** stored here: they come
from the `sn3.baseUrl`/`sn3.clientId` JVM system properties, set by the
bootstrap when it launches the complete jar (or passed by hand with `-D`
during development). Reading them fresh from system properties each run
keeps a single source of truth and matches how the bootstrap itself is
configured.
