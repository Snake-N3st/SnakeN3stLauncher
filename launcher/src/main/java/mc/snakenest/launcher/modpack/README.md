# modpack

Everything needed to list modpacks, fetch a version's manifest, and sync an
instance directory to it - diffing **by content hash**, never by version
string or path alone (two versions sharing a file never re-download it).

- **`ModpackApiClient`** — `/api/modpacks` endpoints. Every call is signed
  (`auth.SignedRequestSigner`) **and** carries `client_id` - both are
  mandatory per the server (`RequireLauncherClient`), not just the player
  key. `downloadBlob` returns a `net.RawResponse` for streaming rather than
  buffering a whole mod jar in memory.

- **DTOs** (`ModpackSummary`, `ModpackManifest`, `ManifestFile`,
  `ModpackVersionSummary`) — mirror the actual PHP controllers exactly
  (`ModpackListController`/`ModpackManifestController`), checked against
  their source, not just the doc. `ModpackManifest.modLoader()` maps the
  raw `loader` string to `game.ModLoader`.

- **`ManifestDiffer`** — pure function, the one place the "diff by hash"
  rule is implemented. Given the previously-applied `StoredManifest`
  (nullable/absent on a fresh install) and the target `ModpackManifest`, it
  returns a `SyncPlan`: which files to download (new path, or same path
  with a different hash) and which local paths to delete (present before,
  absent now). This is the most heavily unit-tested class in the package
  for exactly that reason - it's pure and easy to get subtly wrong.

- **`LocalManifestStore`** — persists the last-applied manifest as
  `.sn3-manifest.json` inside the instance directory. `clear(instanceDir)`
  forgets it, so the next sync treats every file as needing a fresh
  download+verify - the whole mechanism behind the "Reparer" action
  (`ui.modpack.ModpackDetailPage`).

- **`ModpackSettings`** / **`ModpackSettingsStore`** — per-modpack memory
  allocation + custom JVM args, edited from the "Gerer" action
  (`ui.modpack.ModpackSettingsDialog`). Persisted at
  `util.AppDirs#modpackSettingsFile`, deliberately **outside** the instance
  directory itself so a "Reparer" pass (which only touches files inside the
  instance dir) can never wipe it.

- **`ModpackFileDownloader`** — downloads one file, verifying its SHA-256
  **before** it's ever written at its final path
  (`util.AtomicFiles#writeVerified`) - a truncated or corrupted transfer is
  discarded, never silently accepted.

- **`ModpackSyncEngine`** — orchestrates the whole thing: diff, download
  what's needed, delete what's gone (pruning now-empty directories, bounded
  to the instance dir), then record the new manifest as applied. Downloads
  happen **in parallel** (`downloadAllInParallel`, bounded to 6 concurrent
  files via a fixed thread pool) - a modpack can be dozens/hundreds of small
  files, and downloading them one at a time (the original implementation)
  made a sync feel very slow since every file paid a full round-trip before
  the next started. `awaitAll` waits for every download and re-throws the
  first failure with its original checked type, so callers still only ever
  see `IOException`/`ModpackApiException`/`InterruptedException`, same as
  before parallelizing.

  **Cancellation** ("Annuler" in `ui.modpack.ModpackDetailPage`) is
  cooperative, not this class's own mechanism: `LauncherApp` runs
  `sync`/install/launch inside a `Future` it can `cancel(true)`, which
  interrupts the worker thread. `awaitAll` already rethrows
  `InterruptedException` as-is when that's what caused a download to fail,
  so an interrupted sync surfaces the same way a genuine I/O failure would
  - `LauncherApp` is what tells the two apart (`isCancellation`) and shows
  "Annule." instead of an error message.

- **`InstancePaths`** — every manifest `path` is server-supplied and used
  to build a filesystem path; this resolves it defensively (normalize +
  verify it's still under the instance directory) before any write/delete,
  as protection against a `../../..`-style path even though the server is
  presumed trusted - the same defense-in-depth the server itself already
  applies to blob hashes.
