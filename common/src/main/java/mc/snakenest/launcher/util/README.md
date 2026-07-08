# util

Low-level, dependency-free helpers used everywhere else in the launcher.
Nothing here talks to the network, to Swing, or to any third-party library.

- **`AppDirs`** — the single source of truth for where the launcher's data
  lives on disk. Root is `snake-n3st/` under the OS-appropriate user data
  directory:
  - Linux: `$HOME/.local/share/snake-n3st`
  - macOS: `~/Library/Application Support/snake-n3st`
  - Windows: `%APPDATA%\snake-n3st`

  `-Dsn3.dataDir=...` overrides all of the above with an arbitrary
  directory, read by both `bootstrap` and `launcher` (both go through this
  same class) - mainly to point a test/dev run at its own throwaway
  directory, completely separate from a real install's config/keys/cached
  jars/modpacks on the same machine. Verified end-to-end against the real
  packaged `bootstrap` jar, not just unit-tested in isolation.

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

Note: `ClientIds` (resolves `sn3.clientId`, falling back to a bundled
`.clientId` resource) lives in `bootstrap.ClientIds`, not here - it only
needs to run before `bootstrap` spawns `launcher` (which always receives
`-Dsn3.clientId=...` explicitly forwarded by then), so it doesn't need to be
shared via `common`. See `bootstrap/README.md`.
