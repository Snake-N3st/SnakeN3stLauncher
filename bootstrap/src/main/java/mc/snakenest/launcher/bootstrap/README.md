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
  never two JVMs running at once. `clientId` itself goes through
  `ClientIds#resolve` first (JVM property, falling back to a bundled
  `.clientId` classpath resource) - exits with an error only if neither
  source resolves to something valid.

  **`loadPropertiesFileNextToJar()`** runs first, before anything else
  (even `AppDirs`, since `sn3.dataDir` might come from here too): if a
  `bootstrap.properties` file sits in the same directory as this jar
  (`jarDirectory()`, from `getProtectionDomain().getCodeSource()` -
  `null`/no-op if that can't be determined), every key in it becomes a JVM
  system property, *unless* already set via `-D` (an explicit `-D` always
  wins over the file - same precedence as `ClientIds`). Exists for a
  double-clickable jar or a desktop shortcut/file association, neither of
  which has a launch command line to add `-D` arguments to at all -
  verified end-to-end (real jar, zero `-D` arguments, `sn3.baseUrl`/
  `sn3.clientId`/`sn3.dataDir` all read from the file, and correctly
  forwarded to the spawned `launcher` process too) before relying on this.
  A real `bootstrap.properties` (with a real `client_id`) should never be
  committed, same reasoning as `.clientId` below - not bundled as a
  resource here on purpose, since it has to sit *next to* the jar at
  runtime, not inside it.

- **`ClientIds`** — resolves `sn3.clientId`: the JVM system property if set
  (valid, non-blank), otherwise a `.clientId` file bundled as a classpath
  resource. Lets an operator ship a turnkey single-client build - drop a
  `.clientId` file under this module's own `src/main/resources/` before
  running `mvn package` and `bootstrap` needs no `-Dsn3.clientId=...`
  argument at all anymore (it always forwards the resolved value explicitly
  to the `launcher` process it spawns, so `launcher.Main` never needs this
  fallback itself - see its Javadoc). **Never commit a real one** - this
  repo is public/GPL-3.0, and a real client id identifies a specific
  deployment; `.gitignore` already excludes `**/src/main/resources/.clientId`
  for exactly this reason. Validates the resolved value against the shape
  the site actually issues (`LauncherClient::boot()` server-side, Laravel's
  `Str::random(32)`: 32 alphanumeric characters) with some slack on length,
  as a sanity check against an empty/corrupted file rather than strict
  format enforcement.

- **`LauncherReleaseClient`** — talks to `GET /api/launcher-auth/releases/latest`
  and `GET /api/launcher-auth/releases/{version}/download` directly via
  `java.net.http` (see `LAUNCHER_INTEGRATION.md` section 8 in the sibling
  `SnakeN3stLogin` repo). Deliberately independent of `launcher.net.HttpJsonClient` -
  this module must never depend on `launcher`. Downloads go through
  `common.util.AtomicFiles#writeVerified`, so a corrupted/tampered transfer
  is discarded before ever landing at the real cache path - the same rigor
  applied to modpack blobs, since this is code we're about to *execute*.

- **`LauncherReleaseInfo`** — mirrors the JSON shape of the `latest`
  endpoint (`version`, `sha256`, `size`, `changelog`, `download_url`).

- **`BootstrapSplash`** — a small undecorated "loading..." popup
  (title + status label + indeterminate progress bar) shown for however
  long `BootstrapMain` spends checking for/downloading a release, the same
  idea as the splash screen GIMP or IntelliJ IDEA show at startup. Package-
  private, self-contained Swing (no dependency on the launcher module's
  `ui.common.LoadingPanel`, to keep this module's GPL-free boundary from
  ever depending on anything in `launcher`, even something GPL-free itself
  today). `showIfPossible()`/`showFatalError()` both degrade to a no-op in
  a headless environment (`GraphicsEnvironment.isHeadless()`) rather than
  ever failing the actual update/handoff logic over a UI nicety - every
  caller in `BootstrapMain` null-checks accordingly.

Depends only on `common` (paths/hashing/logging) plus Gson for the tiny
JSON response - no crypto library beyond what's needed to verify a hash,
nothing GPL. Swing/AWT (`BootstrapSplash`) is part of the JDK itself, not
an extra dependency, so this doesn't change that.
