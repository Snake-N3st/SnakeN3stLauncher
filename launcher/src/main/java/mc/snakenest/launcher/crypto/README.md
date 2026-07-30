# crypto

Ed25519 key handling and secure-at-rest storage of the player's private key.

- **`Ed25519KeyPair`** — wraps a raw 32-byte seed (the format the site issues
  and the mod reads from `-Dsn3.token`). Uses `net.i2p.crypto:eddsa`, not the
  JDK's built-in `java.security` Ed25519 support: the JDK can sign with a
  raw-seed private key, but doesn't expose a portable way to derive the
  matching public key from just that seed. This library is already used
  (and cross-validated bit-for-bit against the JDK's own verifier and
  against PHP's `sodium_crypto_sign_seed_keypair`) by the in-game mod in the
  `SnakeN3stLogin` repo - reusing it here keeps one proven implementation
  instead of two different approaches to the same problem.
  `toString()` only ever prints the public key - never the seed.

- **`SecretBox`** — AES-256-GCM of one byte array, random IV prepended so a
  single opaque blob is all that ever needs storing. The one place the
  mode/IV/tag-length choices are made; used both for the token below and
  for `.cryptlist` modpack files (`modpack.SensitiveFileVault`). Its
  authenticated-decryption failure is also what makes "was that the right
  PIN?" answerable at all, with no separate verifier file to keep in sync.

- **`KeyStorage`** / **`EncryptedFileKeyStorage`** — persists the key pair
  under `secure/` (see `util.AppDirs`), in one of two modes:
  - **no PIN** (default): the encryption key is generated once and kept in
    a second file (`wrap.key`) next to the encrypted seed, both tightened
    to owner-only access via `SecureFilePermissions` (POSIX permission bits
    on Linux/macOS, an ACL restricted to the owner on Windows). Convenient
    (nothing to type at startup), but by construction anything able to read
    one file can read the other.
  - **PIN** (`withPin`): the key is derived from the player's PIN instead,
    and `wrap.key` is **deleted** - no key material left on disk at all.

  **What this does and doesn't protect against** (see the Javadoc on
  `SecureFilePermissions` for the full version): the permission tightening
  stops casual disk browsing, another account on a shared machine, and an
  unencrypted backup tool from exposing the key in the clear. It does
  **not** stop a privileged process running as the same OS account - that's
  the same trust boundary the encryption key itself lives inside. A real OS
  keychain (DPAPI / Keychain Services / Secret Service) would close that
  gap, but needs a native/JNI dependency per OS, which conflicts with the
  no-native-code, equally-supported-on-three-OSes requirement for this
  project. The PIN mode below is the partial answer that needs no native
  code; a keychain is still worth revisiting if that tradeoff changes.

- **`PinProtection`** — the optional PIN code (the "Sécurité" setting in
  `ui.settings.SettingsPage`): PBKDF2-HMAC-SHA256, 600k iterations (OWASP's
  current figure, measured at ~0.6s here) over a per-vault random salt,
  turning a short numeric code into the AES key used above. Salt and
  iteration count live in `secure/pin.json` in the clear - neither is
  secret, and persisting the count is what lets it be raised later without
  stranding already-encrypted data.

  **Honest limit, spelled out because this is easy to oversell:** it raises
  the bar against the commodity "copy `secure/` and replay the token"
  grabber it's aimed at, since there is no longer a key to copy. It is
  **not** protection against an attacker willing to brute-force - a numeric
  PIN carries ~10^4-10^6 entropy, so the per-guess derivation cost is the
  only obstacle, and that's a speed bump rather than a wall. It also does
  nothing while the game is running: the seed is handed to the game as
  `-Dsn3.token=...` either way (see `game.LaunchRequest`).

  A forgotten PIN is **unrecoverable by design** - any recovery path would
  mean a key reachable without the PIN, i.e. exactly what this removes. So
  the "Code oublié ?" link wipes instead of recovering (`PinReset`, in the
  root package next to `LauncherApp` since it spans both `crypto` and
  `modpack` and belongs to neither).

- **`SecureFilePermissions`** — the cross-platform permission-tightening
  helper described above, package-private (an implementation detail of
  `EncryptedFileKeyStorage`, not part of this package's public surface).
