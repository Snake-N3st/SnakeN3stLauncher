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
  `.sn3-manifest.json` inside the instance directory.

- **`ModpackFileDownloader`** — downloads one file, verifying its SHA-256
  **before** it's ever written at its final path
  (`util.AtomicFiles#writeVerified`) - a truncated or corrupted transfer is
  discarded, never silently accepted.

- **`ModpackSyncEngine`** — orchestrates the whole thing: diff, download
  what's needed, delete what's gone (pruning now-empty directories, bounded
  to the instance dir), then record the new manifest as applied.

- **`InstancePaths`** — every manifest `path` is server-supplied and used
  to build a filesystem path; this resolves it defensively (normalize +
  verify it's still under the instance directory) before any write/delete,
  as protection against a `../../..`-style path even though the server is
  presumed trusted - the same defense-in-depth the server itself already
  applies to blob hashes.
