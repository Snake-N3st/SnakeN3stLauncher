# config

Persisted user preferences (`config.json`, plain JSON, never encrypted).

- **`Theme`** — `LIGHT` or `DARK`.
- **`LauncherConfig`** — the POJO that gets (de)serialized: the theme, and
  the site `playerId` paired with the locally-stored key (not a secret -
  see the field's Javadoc - but required on every signed request, so it
  has to survive a restart alongside the key itself). **Never add a secret
  field here** - this file has no encryption and no restricted permissions;
  the private key lives in `mc.snakenest.launcher.crypto` instead.
- **`ConfigStore`** — load/save, atomic writes via `util.AtomicFiles`, falls
  back to defaults on a missing or corrupt file rather than failing to
  start.

`baseUrl` and `clientId` are deliberately **not** stored here: they come
from the `sn3.baseUrl`/`sn3.clientId` JVM system properties, set by the
bootstrap when it launches the complete jar (or passed by hand with `-D`
during development). Reading them fresh from system properties each run
keeps a single source of truth and matches how the bootstrap itself is
configured.
