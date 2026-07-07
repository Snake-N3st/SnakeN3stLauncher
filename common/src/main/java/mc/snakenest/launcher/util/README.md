# util

Low-level, dependency-free helpers used everywhere else in the launcher.
Nothing here talks to the network, to Swing, or to any third-party library.

- **`AppDirs`** — the single source of truth for where the launcher's data
  lives on disk. Root is `snake-n3st/` under the OS-appropriate user data
  directory:
  - Linux: `$HOME/.local/share/snake-n3st`
  - macOS: `~/Library/Application Support/snake-n3st`
  - Windows: `%APPDATA%\snake-n3st`

  Layout under that root:
  ```
  snake-n3st/
  ├── cache/launcher/   jars downloaded by the bootstrap
  ├── instances/<slug>/ one full, independent game directory per modpack
  ├── secure/           encrypted private key + its local wrapping key
  ├── logs/             rotating log files
  └── config.json       theme, no secrets
  ```
  No other class should build a path under this root by hand — always go
  through a method here, so the layout only changes in one place.

- **`Hex`** — lower-case hex encode/decode, used for keys/signatures/hashes.

- **`Sha256`** — SHA-256 of a byte array or stream, hex-encoded.

- **`AtomicFiles`** — write-then-rename (temp file in the same directory,
  then `Files.move` with `ATOMIC_MOVE`) so a crash mid-write never leaves a
  half-written file at the real path. `writeVerifying` additionally computes
  the SHA-256 of what was written while streaming it, for callers that need
  to check it against a hash from the server.

- **`Log`** — thin wrapper over `java.util.logging`: one rotating file under
  `logs/`, plus console output. **Never log a private key seed, a signature,
  or a URL with a query string** (the API's query strings carry
  `signature`/`publicKey`) — log the request path only.

- **`ClientIds`** — resolves `sn3.clientId`: the JVM system property if set
  (valid, non-blank), otherwise a `.clientId` file bundled as a classpath
  resource (`resolve(null)`/`resolve("")` both fall through to it). Lets an
  operator ship a turnkey single-client build of `bootstrap`/`launcher` -
  drop a `.clientId` file under this module's own `src/main/resources/`
  before running `mvn package` (it gets shaded into *both* final jars
  automatically, since both already bundle `common`'s classes/resources)
  and neither jar needs a `-Dsn3.clientId=...` argument at all anymore.
  **Never commit a real one** - this repo is public/GPL-3.0, and a real
  client id identifies a specific deployment; `.gitignore` already excludes
  `**/src/main/resources/.clientId` for exactly this reason. Validates the
  resolved value against the shape the site actually issues
  (`LauncherClient::boot()` server-side, Laravel's `Str::random(32)`: 32
  alphanumeric characters) with some slack on length, as a sanity check
  against an empty/corrupted file rather than strict format enforcement.
