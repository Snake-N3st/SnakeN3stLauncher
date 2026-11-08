# auth

The signing recipe shared by `launcher` (player-info/modpacks calls) and
`bootstrap` (the restricted-distribution-channel auth flow - proving
eligibility for a gated launcher release). `launcher`'s own `auth` package
holds everything else (the device-flow orchestration, player-info DTOs,
browser-opening) - see its README for why that stays `launcher`-only.

- **`SignedRequestSigner`** / **`SignedParams`** — the one signing recipe
  every signed call shares: sign `"{METHOD}:{path}:" + unixTimestamp`
  (method uppercase, path without its leading slash - the exact path/method
  about to be requested) with the player's key, send
  `playerId`/`publicKey`/`timestamp`/`signature` (+ `client_id` where the
  endpoint needs one). Centralized here so it's never subtly reimplemented
  twice - including by `bootstrap`, which needs the exact same recipe (see
  `VerifyLauncherSignature`/`LauncherSignatureVerifier` in the sibling
  `SnakeN3stLogin` repo) but must stay independent of `launcher`'s own
  `LauncherAuthApiClient`. Binding the signature to the exact method+path
  means a signature captured for one endpoint (e.g. from server logs) can
  never be replayed against a different one; the server also rejects a
  signature it's already seen once, even for the same endpoint. **Must be
  recomputed per request** - the server tolerates ±60s skew and won't
  accept a stale or already-used timestamp/signature, so never cache a
  `SignedParams`.

- **`PlayerSession`** — the authenticated player bundled with the
  launcher/bootstrap's own client id: exactly the triple every signed
  request needs (`Ed25519KeyPair`, `playerId`, `clientId`). Avoids passing
  the same 3 parameters through every API client method.
