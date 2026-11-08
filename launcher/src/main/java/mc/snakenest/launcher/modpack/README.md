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
  discarded, never silently accepted. `ManifestFile.url()`, when set, redirects
  the fetch to a third-party origin server (`net.HttpJsonClient#getRaw`, no
  signing/`client_id`) instead of this site's own blob storage - the "curator's
  own zip can't legally bundle this mod" case, e.g. its license forbids
  redistribution. The site computes `hash`/`size` itself once at import time by
  fetching the URL server-side (see `modpacks-plugin`'s `.autodownload`
  handling in `ModpackImporter`), so the file is otherwise completely normal -
  same hash-diffing, same verified write, `ManifestDiffer` doesn't even need to
  know the difference.

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

- **`SensitiveFileVault`** — encrypts a pack's own sensitive files at rest
  under the player's PIN-derived key (`crypto.PinProtection`), driven by an
  **optional `.cryptlist`** at the instance root: one glob per line
  (relative to the instance), `#` comments and blank lines ignored. Globs
  rather than plain paths, so a pack can write `config/*.secret`; matching
  runs against files actually found under the instance, which is also why a
  pattern can't reach outside it however it's written (covered by a test).

  An encrypted file becomes `<original>.sn3enc`, plaintext removed. Since
  the game can't read that, `LauncherApp` decrypts just before launch and
  re-encrypts when the process exits, making the plaintext window exactly
  the play session; it also re-seals at startup, which covers a crash or a
  kill that skipped the exit hook. **Decryption works off the `.sn3enc`
  files present on disk, not off `.cryptlist`** - otherwise editing the list
  could strand a file as permanently unreadable.

  Skipped entirely when no PIN is set: there would be no key worth
  encrypting under, since it would just sit in `secure/wrap.key` beside the
  data it protects.

- **`InstanceActivity`** / **`InstanceState`** / **`InstanceStateRegistry`** —
  what every modpack instance is currently doing (installing/running/being
  stopped), one entry per slug in the registry's `ConcurrentHashMap`, fully
  independent of every other slug. Replaced an earlier `LauncherApp`-private
  single field that could only ever describe *one* modpack at a time:
  starting an install on a second modpack while a first one was still
  running silently overwrote the only record of the first one, so its own
  "Arreter"/"Annuler" buttons read the now-wrong global state and became
  no-ops, and a freshly (re)built page/card for it rendered idle even
  though it was genuinely still active - see `ui.modpack/README.md` for the
  full bug history.

  Every operation (`beginInstalling`, `attachInstallTask`,
  `transitionToRunning`, `beginStopping`, `processFor`, `cancelInstalling`,
  `clearIfInstalling`, `clearIfProcessMatches`) is scoped to one slug and
  built on `ConcurrentHashMap`'s own atomic `compute`/`computeIfPresent`/
  `putIfAbsent` rather than a read-then-write pair, so two different
  modpacks (or two threads touching the same one) can never race each
  other into an inconsistent snapshot. `beginInstalling` returning `false`
  is the one guard that never existed before: a slug that already has
  activity in flight refuses a second one instead of silently overwriting
  it. `isAnyActive()` is the one deliberately *non*-per-slug method - the
  coarse "is anything happening anywhere" guard behind `game.assets`
  migration and `maintenance.CleanupService`, which touch files across
  every instance at once.

  Pure Java, no Swing, no dependency on `LauncherApp` - unit-tested
  directly (`InstanceStateRegistryTest`), unlike the private-field design
  it replaced, which needed reflection into `LauncherApp` internals to test
  at all.


## `signing/` - signed manifests

Every manifest the launcher fetches is checked in one place, `ModpackApiClient#getManifest`, before the sync
engine sees it. Nothing is downloaded or installed from a refused one (`ModpackSignatureException`, a
`ModpackApiException`, so it is shown like any other manifest failure).

**The chain.** One pinned *root* key (`sn3.signing.modpackRootKeys`, baked into the bootstrap's `jvm.prop` by the
site and forwarded by `BootstrapMain#spawnLauncher`; a list, for rotation) certifies each modpack's own key
(`certificate {modpack, public_key, serial, signature}`: the root signs "this key is the key of this slug"), and
that key signs each version (`signature`). Adding a modpack never touches the pin. `ModpackManifestVerifier` checks
the certificate against the pin, then the version signature against the certified key.

**What is signed.** `ModpackManifestStatements` rebuilds, byte-for-byte like the site's `ManifestCanonical.php`
and `ModpackStatements.php` (a shared vector pins both - `ModpackSigningVectorTest`), the canonical manifest text
(slug, version, Minecraft version, loader, loader version, **default JVM args**, every file's path / SHA-256 /
size / external URL, sorted) and the two statements. Never sent by the server, always rebuilt from the slug that
was asked for and the fields in hand. Not covered, on purpose: changelog, channel, `total_size`,
`default_memory_mb` - none of them can run code.

**Policy** (`ModpackTrustState`, `modpack-trust-state.json` in the data root): no pin = nothing checked;
unsigned and never seen signed = accepted with a log warning (the transition); unsigned after a valid one was
seen = refused (downgrade); an invalid signature, a certificate for another slug or from an unpinned root, a
signed manifest of another version than the one pinned by name = refused; a certificate **serial** lower than
the highest seen for that modpack = refused, which is what makes a compromised modpack key repairable (issue a
higher serial and the old key's certificate can no longer be replayed). A missing or corrupt state file reads as
"nothing seen yet" and never throws. The warning is a log line only - there is no dialog here, unlike the
bootstrap's.

## Téléchargements groupés

La synchronisation utilise `GET /api/modpacks/{slug}/blobs?hashes=...`, jusqu’à 64 SHA-256 internes distincts par requête, uniquement pour les fichiers manquants ou modifiés. Chaque lot est transmis en flux `SN3B1`, sans archive ni recompression, puis vérifié (ordre, taille, SHA-256, fin du flux) dans un répertoire temporaire supprimé après synchronisation ou échec. Les doublons de contenu ne sont téléchargés qu’une fois. La fusion `.filesmetas` et les URL externes conservent leur comportement ; le fichier de règles peut nécessiter sa requête préalable.

Le site doit être mis à jour avant de distribuer ces clients : pas de repli automatique vers les téléchargements unitaires si l’endpoint manque ou refuse le lot, pour éviter une rafale après un 429. L’ancien endpoint blob reste disponible pour les anciens clients. Les appels successifs au nouvel endpoint sont espacés pour éviter les signatures identiques dans la même seconde. L’espace temporaire peut atteindre la taille des blobs internes à mettre à jour ; le transport reste borné en mémoire.
