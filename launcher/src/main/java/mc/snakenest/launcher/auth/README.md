# auth

Passwordless "device flow" login, mirroring an OAuth device authorization
grant: the launcher gets a short-lived challenge, the user approves it in
their browser, the launcher polls until it receives a one-time Ed25519
private key.

- **`LauncherAuthApiClient`** — the endpoints of the `launcher-auth` plugin:
  `requestChallenge`, `pollChallenge`, `loginUrl` (builds the
  `/launcher-login?challenge=...` URL to open), `fetchClientInfo` (the
  client's name/logo, unsigned - used to brand the login window before any
  session exists), `fetchUsername` (used on its own right before launching
  a game, to detect a username change - see `LauncherApp#warnIfUsernameChanged`),
  and `fetchPlayerInfo` (username+role+email+avatar in one signed call).

  `fetchPlayerInfo` used to be three separate calls
  (`fetchRole`/`fetchEmail` alongside `fetchUsername`), each re-signed and
  re-sent every time the account popover was opened. Consolidated into one
  endpoint (`/api/launcher-auth/player/info`, see `LAUNCHER_INTEGRATION.md`
  section 6) specifically so `LauncherApp` can fetch it once - at login and
  at startup - and cache the result, rather than hitting the network every
  time the user clicks the account icon.

- **`ClientInfo`** — the `fetchClientInfo` result (`name`, nullable `image`
  URL). Decoding that URL into an actual image is `ui.common.RemoteImages`'
  job, not this package's - `ClientInfo` only carries the raw API response.

- **`PlayerInfo`** — the `fetchPlayerInfo` result (`username`, `role`,
  `email`, nullable `avatar` URL). Same split as `ClientInfo`: this record
  only carries the raw response, `ui.common.RemoteImages` decodes the
  avatar URL into an image.

- **`SignedRequestSigner`** / **`SignedParams`** — the one signing recipe
  every player-info and modpacks call shares: sign
  `"playerinfo:" + unixTimestamp` with the player's key, send
  `playerId`/`publicKey`/`timestamp`/`signature` (+ `client_id` for
  modpacks). Centralized here so it's never subtly reimplemented twice.
  **Must be recomputed per request** - the server tolerates ±60s skew and
  won't accept a stale timestamp, so never cache a `SignedParams`.

- **`PollResult`** — sealed result of one challenge poll: `Pending` (403,
  keep polling), `NotFound` (404, stop - expired or already consumed),
  `Success` (200, delivered exactly once).

- **`DeviceAuthState`** / **`DeviceAuthListener`** / **`DeviceAuthService`**
  — the state machine that drives the whole flow on one background thread:
  request a challenge, open the browser, poll every ~2.5s, store the key
  via `crypto.KeyStorage` the moment it arrives, and report
  `SUCCEEDED`/`FAILED`/`CANCELLED`. Callbacks fire on the service's own
  thread, never the Swing EDT - UI code must hop back itself.

- **`BrowserOpener`** — the actual `java.awt.Desktop.browse(...)` call,
  pulled out from `DeviceAuthService` into its own one-method interface
  purely so it can be swapped for a no-op in tests. `DeviceAuthService`'s
  3-arg constructor defaults to the real `BrowserOpener.DESKTOP`; the 4-arg
  one lets a test inject a fake instead. **Any test that constructs a
  `DeviceAuthService` and calls `start()` without doing this will open a
  real browser tab every time it runs** - this was missed once during
  development and produced dozens of stray tabs against fake challenge
  tokens (all 404s) from repeated test runs.

Manual milestone: this package can be exercised end-to-end from a bare
`main()` against the real local Azuriom instance (`http://127.0.0.1`) before
any UI exists - see the plan's build order.
