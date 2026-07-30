# maintenance

Housekeeping actions the player triggers from the Settings page, kept out of
`LauncherApp` because both are real filesystem work with their own rules
about what must survive.

- **`CleanupService`** — the "Nettoyer" button: deletes the launcher's own
  logs, every modpack's Minecraft `logs/` and `crash-reports/`, and the
  download caches. `preview()` measures the same set first, so the
  confirmation dialog can name an actual size instead of asking the player
  to approve an unknown amount of deletion.

  The interesting part is the **exclusion list** (see the class Javadoc for
  the full version). Most notably, the shared assets/libraries pools are
  deliberately *not* cleaned: with hardlink sharing on, deleting a pooled
  file frees nothing at all, because every instance still linking to it
  keeps the data alive - it would force a large re-download and reclaim
  zero bytes. Also excluded: everything in an instance except those two
  diagnostic folders (mods, configs and **worlds** live there), `secure/`,
  `config.json`, `modpack-settings/`, and `.sn3enc` files (encrypted, but
  real data - wiping those is the separate, explicitly-confirmed "code
  oublié" path in `PinReset`).

Space actually taken by duplicated vanilla assets is reclaimed by
`game.assets.SharedAssetsLinker#migrate` instead - see the "Migrer" button
next to the storage-mode setting.
