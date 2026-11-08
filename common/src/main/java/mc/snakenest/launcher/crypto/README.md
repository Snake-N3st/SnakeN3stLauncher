# crypto

Ed25519 key handling and secure-at-rest storage of the player's private key -
shared by `launcher` and `bootstrap` (the latter needs it for the
restricted-distribution-channel auth flow: proving eligibility before
fetching a launcher release gated to specific roles). `launcher`'s own
`crypto` package still holds `PinProtection` (see its README) - not moved
here since `bootstrap` deliberately never prompts for a PIN, staying
minimal.

- **`Ed25519KeyPair`** — wraps a raw 32-byte seed (the format the site issues
  and the mod reads from `-Dsn3.token`). Uses `net.i2p.crypto:eddsa`, not the
  JDK's built-in `java.security` Ed25519 support: the JDK can sign with a
  raw-seed private key, but doesn't expose a portable way to derive the
  matching public key from just that seed. This library is already used
  (and cross-validated bit-for-bit against the JDK's own verifier and
  against PHP's `sodium_crypto_sign_seed_keypair`) by the in-game mod in the
  `SnakeN3stLogin` repo - reusing it here keeps one proven implementation
  instead of two different approaches to the same problem. CC0 1.0
  (public domain), so it carries no licensing complication for `bootstrap`.
  `toString()` only ever prints the public key - never the seed.

- **`SecretBox`** — AES-256-GCM of one byte array, random IV prepended so a
  single opaque blob is all that ever needs storing. The one place the
  mode/IV/tag-length choices are made; used both for the token below and
  for `.cryptlist` modpack files (`launcher`'s own `modpack.SensitiveFileVault`).
  Its authenticated-decryption failure is also what makes "was that the
  right PIN?" answerable at all, with no separate verifier file to keep in
  sync (see `launcher.crypto.PinProtection`).

- **`KeyStorage`** / **`EncryptedFileKeyStorage`** — persists the key pair
  under `secure/` (see `util.AppDirs`), in one of two modes:
  - **no PIN** (default, the only mode `bootstrap` ever uses): the
    encryption key is generated once and kept in a second file (`wrap.key`)
    next to the encrypted seed, both tightened to owner-only access via
    `SecureFilePermissions` (POSIX permission bits on Linux/macOS, an ACL
    restricted to the owner on Windows). Convenient (nothing to type at
    startup), but by construction anything able to read one file can read
    the other.
  - **PIN** (`withPin`, `launcher`-only - see `launcher.crypto.PinProtection`):
    the key is derived from the player's PIN instead, and `wrap.key` is
    **deleted** - no key material left on disk at all. If `bootstrap` finds
    a PIN-protected vault (no `wrap.key`, an encrypted seed present anyway)
    it treats that as "no usable key" rather than prompting for a PIN
    itself, staying minimal.

  **What this does and doesn't protect against**: the permission tightening
  stops casual disk browsing, another account on a shared machine, and an
  unencrypted backup tool from exposing the key in the clear. It does
  **not** stop a privileged process running as the same OS account - that's
  the same trust boundary the encryption key itself lives inside. A real OS
  keychain (DPAPI / Keychain Services / Secret Service) would close that
  gap, but needs a native/JNI dependency per OS, which conflicts with the
  no-native-code, equally-supported-on-three-OSes requirement for this
  project.

- **`SecureFilePermissions`** — the cross-platform permission-tightening
  helper described above, package-private (an implementation detail of
  `EncryptedFileKeyStorage`, not part of this package's public surface).

- **`Ed25519Verifier`** — verification only: `verify(publicKey32, message, signature64)`, `false` for anything
  malformed and never an exception. Separate from `Ed25519KeyPair` (whose contract is "I hold a seed") so that
  a verifier is never reachable through something secret. Mirrors the site plugin's `Ed25519::verify()`. The
  eddsa 0.3.0 library does not enforce RFC 8032's canonical `S`, so a signature has a second, malleated form
  that also verifies - harmless here, since the signed *statement* authenticates and nothing keys on the
  signature bytes.

- **`SignedArtifactStatement`** — the exact bytes the site signs for a release (`sn3.artifact.v1`, type,
  version, sha256, size), rebuilt client-side; byte-exact with `SigningStatement.php` through a shared test
  vector. Rejects off-format input instead of normalising it: "fixing" an uppercase digest would verify a
  statement the site never signed.

- **`SignatureVerificationException`** — an `IOException`, so it crosses every existing `throws IOException`
  on the download path and is treated as "do not use what was just fetched" by callers that have never heard
  of it.
