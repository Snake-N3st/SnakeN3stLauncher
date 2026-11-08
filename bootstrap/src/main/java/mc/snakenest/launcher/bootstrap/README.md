# bootstrap

The tiny, rarely-updated stub actually distributed to players. Contains no
GPL code and no dependency on the `launcher` module - see the top-level
plan's "Deux artefacts, un seul JVM actif à la fois" for why this split
exists.

- **`BootstrapMain`** — the only entry point. Reads `sn3.baseUrl`/
  `sn3.clientId` (same JVM properties `launcher.Main` reads), fetches the
  latest release info, downloads it if not already cached under
  `AppDirs#cacheLauncher()` (skips the download entirely if a jar matching
  the announced SHA-256 is already there), spawns `java -jar <cached-jar>`
  with the same two properties forwarded, then **exits immediately** -
  never two JVMs running at once. `clientId` goes through `ClientIds#resolve`
  for format validation only - shows a fatal error dialog (`BootstrapSplash
  #showFatalError`, not just a console message - a double-clicked jar has no
  visible console) if the resolved `System` property (from `-D`,
  `bootstrap.properties`, or a bundled `jvm.prop`, see below) isn't a
  valid-looking one; this one case isn't retryable (nothing about the
  config changes between attempts), unlike the loop below.
  `pruneOldLauncherJars` deletes every other cached launcher jar once the
  current one is confirmed present - each version previously got its own
  filename with nothing ever cleaning up an older one, so this cache grew
  forever.

  **`attemptStartup`/the retry loop** — everything from the self-update
  check through spawning the launcher runs inside `attemptStartup`, called
  in a loop from `main()`. Any failure there (network error, download
  corruption, spawn failure) shows a blocking error dialog with a
  "Réessayer"/"Fermer" choice (`BootstrapSplash#showFatalErrorWithRetry`)
  instead of just exiting - a player watching a console flash shut (or,
  again, nothing at all for a double-clicked jar) would otherwise have no
  way to tell a real failure from "still doing something", nor any way to
  retry short of re-launching the jar by hand. Safe from ever spinning
  forever unattended: the loop only continues if a human actually clicked
  "Réessayer" on that dialog, so a persistent failure just sits at the
  dialog until the player gives up and clicks "Fermer" (or closes it).

  **`selfUpdateIfNeeded`** runs first, before the launcher-jar handoff:
  checks the site for a newer *bootstrap* release (`ReleaseType.BOOTSTRAP`,
  independently versioned/published from the launcher jar - see
  `LauncherRelease::$type` in the sibling `SnakeN3stLogin` repo), and if the
  running jar's own SHA-256 doesn't match it, downloads the new one and
  atomically replaces this jar's own file on disk
  (`LauncherReleaseClient#download`, same verified-write mechanism as the
  launcher jar). Compares by hash rather than a tracked version string -
  this jar has no reliable notion of "its own version number" otherwise
  (unlike a release, which the site tracks independently of the Maven build
  version). On success, `main()` shows a restart prompt
  (`BootstrapSplash#showRestartPrompt`) and returns *without* spawning the
  launcher this run - the running process's own code is now stale, and
  re-executing itself in place is fragile cross-platform, so the player
  just relaunches. Best-effort in every other respect (no release published
  yet, a network error, running unpackaged from an IDE): self-updating must
  never block the actual job of getting the launcher running.

  **`loadPropertiesFileNextToJar()`** runs first, before anything else
  (even `AppDirs`, since `sn3.dataDir` might come from here too): if a
  `bootstrap.properties` file sits in the same directory as this jar
  (`jarDirectory()`, from `getProtectionDomain().getCodeSource()` -
  `null`/no-op if that can't be determined), every key in it becomes a JVM
  system property, *unless* already set via `-D` (an explicit `-D` always
  wins over the file). Exists for a double-clickable jar or a desktop
  shortcut/file association, neither of which has a launch command line to
  add `-D` arguments to at all - verified end-to-end (real jar, zero `-D`
  arguments, `sn3.baseUrl`/`sn3.clientId`/`sn3.dataDir` all read from the
  file, and correctly forwarded to the spawned `launcher` process too)
  before relying on this. A real `bootstrap.properties` (with a real
  `client_id`) should never be committed, same reasoning as `jvm.prop`
  below - not bundled as a resource here on purpose, since it has to sit
  *next to* the jar at runtime, not inside it.

  **`BundledJvmProperties#loadIfPresent()`** runs right after, same
  best-effort/"only fill in what's not already set" reasoning applied to a
  `jvm.prop` file *inside* the jar (a classpath resource, not a file next to
  it) instead - typically `sn3.clientId`/`sn3.baseUrl`, baked in by the site
  when it publishes a bootstrap release or builds the portable/installer
  distributions (`SnakeN3stLogin`'s `BootstrapJvmPropInjector`), so a
  player's downloaded jar needs zero configuration of any kind. Lowest
  precedence of the two file-based sources - it has to run *after*
  `loadPropertiesFileNextToJar`, not before, or a value baked in here would
  already be set by the time the external file's own check runs and could
  never be overridden by it.

- **`ClientIds`** — validates `sn3.clientId` against the shape the site
  actually issues (`LauncherClient::boot()` server-side, Laravel's
  `Str::random(32)`: 32 alphanumeric characters) with some slack on length,
  a sanity check against an empty/corrupted value rather than strict format
  enforcement. Resolving the value itself is `BootstrapMain`'s job (the two
  sources above, applied as plain `System` properties before this class is
  ever consulted) - this class used to also read a dedicated `.clientId`
  classpath resource itself; that's folded into the more general
  `BundledJvmProperties`/`jvm.prop` mechanism instead, which can also carry
  `sn3.baseUrl`. **Never commit a real `jvm.prop`** - this repo is
  public/GPL-3.0, and a real client id identifies a specific deployment;
  `.gitignore` already excludes `**/src/main/resources/jvm.prop` for exactly
  this reason.

- **`LauncherReleaseClient`** — talks to `GET /api/launcher-auth/releases/{type}/latest`
  and `GET /api/launcher-auth/releases/{type}/{version}/download` directly via
  `java.net.http` (see `LAUNCHER_INTEGRATION.md` section 8 in the sibling
  `SnakeN3stLogin` repo). `{type}` (`ReleaseType.LAUNCHER`/`BOOTSTRAP`) is
  the same value both `ensureLatestLauncherJar` and `selfUpdateIfNeeded`
  pass in - one client, two independently-versioned release channels.
  Deliberately independent of `launcher.net.HttpJsonClient` - this module
  must never depend on `launcher`. Downloads go through
  `common.util.AtomicFiles#writeVerified`, so a corrupted/tampered transfer
  is discarded before ever landing at the real target path - the same rigor
  applied to modpack blobs, since this is code we're about to *execute*
  (doubly so for a self-update, which overwrites this jar's own file).

- **`ReleaseType`** — `LAUNCHER`/`BOOTSTRAP`, mirrors the site's
  `LauncherRelease::TYPE_LAUNCHER`/`TYPE_BOOTSTRAP` constants exactly
  (`apiValue()` is the literal string the API's `{type}` route segment
  expects).

- **`LauncherReleaseInfo`** — mirrors the JSON shape of the `latest`
  endpoint (`version`, `sha256`, `size`, `changelog`, `download_url`) -
  shared by both release types, since the API response shape doesn't
  otherwise differ.

- **`BootstrapSplash`** — a small undecorated "loading..." popup
  (title + status label + indeterminate progress bar) shown for however
  long `BootstrapMain` spends checking for/downloading a release, the same
  idea as the splash screen GIMP or IntelliJ IDEA show at startup. Package-
  private, self-contained Swing (no dependency on the launcher module's
  `ui.common.LoadingPanel`, to keep this module's GPL-free boundary from
  ever depending on anything in `launcher`, even something GPL-free itself
  today). `showIfPossible()`/`showFatalError()`/`showRestartPrompt()` all
  degrade to a no-op in a headless environment
  (`GraphicsEnvironment.isHeadless()`) rather than ever failing the actual
  update/handoff logic over a UI nicety - every caller in `BootstrapMain`
  null-checks or logs the same message independently accordingly.

Depends only on `common` (paths/hashing/logging) plus Gson for the tiny
JSON response - no crypto library beyond what's needed to verify a hash
and an Ed25519 signature (`common`'s `eddsa`), nothing GPL. Swing/AWT (`BootstrapSplash`) is part of the JDK itself, not
an extra dependency, so this doesn't change that.

## Signature verification

Every release the site publishes carries an Ed25519 signature, and this module checks it **before a single
byte is downloaded**, in one place: `LauncherReleaseClient#fetchLatestChecked` (plain `fetchLatest` delegates
to it, so no caller can skip the hard rules). It covers the launcher jar, the bootstrap's own update and the
`.deb`, with no extra pass over the file: the client rebuilds the signed statement
(`sn3.artifact.v1` / type / version / sha256 / size - `common`'s `SignedArtifactStatement`, byte-exact with the
site's `SigningStatement.php` through a shared test vector) from values it already has, and
`AtomicFiles.writeVerified` then binds the downloaded bytes to that now-authenticated digest. **The server never
sends the statement**; verifying a message the server chose would prove nothing.

| Situation | What happens |
|---|---|
| valid signature | carry on silently; remembered in `trust-state.json` |
| signature **present but invalid** (wrong key, wrong type, altered version/digest/size, unknown algorithm) | refused - always |
| signature **absent**, but a valid one was verified before on this installation | refused: a downgrade of the protection itself (the HSTS rule) |
| signature **absent**, none ever verified | strong warning (`UnverifiedReleaseDialog`), the player may continue; no display = continue, warning logged |
| build pins no key | nothing is checked (a dev build) |

What "refused" means differs by artifact, on purpose: a **launcher jar** is not started (an error dialog, never
the "launch the last jar" shortcut, which is reserved for a restricted channel); a **bootstrap update** is not
applied and the current bootstrap keeps running (an unverified file is never written, and refusing to start
would trade a real availability risk for nothing); a **`.deb`** whose signature fails is not installed through
the package manager (it runs as root), while the jar it carries is still delivered, verified on its own.

**Older versions are warned about, not refused.** `trust-state.json` also keeps the highest version verified
per type, but only to log a rollback: withdrawing a bad release legitimately makes an older one "latest" again,
and refusing it would turn that into a fleet-wide outage.

### Where the keys come from

Not from Java constants: each site has a vault, hence its own keys, and a constant would make a build for any
other site (a test server) refuse everything it serves. The site writes its public keys into the `jvm.prop` of
each bootstrap it publishes (`sn3.signing.launcherKeys`, `sn3.signing.bootstrapKeys`, comma-separated 64-hex),
and `BundledJvmProperties` turns them into system properties like the rest of that file. That is as strong as a
constant where it matters: the jar carrying the keys is itself signed, and a bootstrap only ever replaces itself
with a release verified against the keys it already holds. **Rotation** is therefore just publishing a bootstrap,
signed with the old key, whose list holds the old and the new one. The same `jvm.prop` also carries
`sn3.signing.modpackRootKeys`, which the bootstrap does not use itself: it only forwards it to the launcher it
spawns (not to a replacement bootstrap, whose own `jvm.prop` must win for a rotation to work). `launcher` artifacts are checked against the
launcher keys, `bootstrap` and `deb` against the bootstrap keys. A malformed entry is skipped with an error in
the log, and if none is left the type is treated as unconfigured, not unverifiable: a typo must not become a
fleet-wide refusal.

### What this does not cover

- **The first installation** is trust-on-first-use: the jar a player downloads (or the `.exe`, the portable zip,
  the `/sh` and `/pwsh` one-liners) carries a digest the same site vouches for. Everything *after* the first is
  authenticated.
- **A bootstrap from before this feature** verifies nothing, and cannot be made to. Publishing the first
  verifying bootstrap is the point of no return on the site: from then on the site must never stop signing,
  because a client that has seen a signature refuses its absence.
- `trust-state.json` lives in the player's own data directory; someone who can edit it can replace the jar.
