# crypto

The optional PIN code. `Ed25519KeyPair`/`SecretBox`/`KeyStorage`/
`EncryptedFileKeyStorage`/`SecureFilePermissions` moved to `common`'s own
`mc.snakenest.launcher.crypto` package (see that module's README) so
`bootstrap` can reuse them too, for the restricted-channel auth flow -
`bootstrap` deliberately never depends on `launcher` itself (see
`THIRD-PARTY-NOTICES.md`: `launcher` embeds GPL-3.0 libraries, `bootstrap`
stays proprietary and must not combine with them). Same package name, no
import changes needed here - Java resolves same-package classes across
module boundaries as long as `launcher` depends on `common`, which it
already does.

- **`PinProtection`** — the optional PIN code (the "Sécurité" setting in
  `ui.settings.SettingsPage`): PBKDF2-HMAC-SHA256, 600k iterations (OWASP's
  current figure, measured at ~0.6s here) over a per-vault random salt,
  turning a short numeric code into the AES key `common.crypto.SecretBox`
  uses. Salt and iteration count live in `secure/pin.json` in the clear -
  neither is secret, and persisting the count is what lets it be raised
  later without stranding already-encrypted data.

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

  Only ever used with `common.crypto.EncryptedFileKeyStorage.withPin` - this
  package has no storage code of its own anymore.
