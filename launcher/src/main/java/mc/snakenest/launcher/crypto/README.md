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

- **`KeyStorage`** / **`EncryptedFileKeyStorage`** — persists the key pair
  under `secure/` (see `util.AppDirs`): AES-256-GCM, with the encryption key
  itself stored in a second file (`wrap.key`), both tightened to
  owner-only access via `SecureFilePermissions` (POSIX permission bits on
  Linux/macOS, an ACL restricted to the owner on Windows).

  **What this does and doesn't protect against** (see the Javadoc on
  `SecureFilePermissions` for the full version): it stops casual disk
  browsing, another account on a shared machine, and an unencrypted backup
  tool from exposing the key in the clear. It does **not** stop a
  privileged process running as the same OS account - that's the same
  trust boundary the encryption key itself lives inside. A real OS keychain
  (DPAPI / Keychain Services / Secret Service) would close that gap, but
  needs a native/JNI dependency per OS, which conflicts with the
  no-native-code, equally-supported-on-three-OSes requirement for this
  project. Worth revisiting later if that tradeoff changes.

- **`SecureFilePermissions`** — the cross-platform permission-tightening
  helper described above, package-private (an implementation detail of
  `EncryptedFileKeyStorage`, not part of this package's public surface).
