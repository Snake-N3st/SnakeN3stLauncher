# discord

Optional Discord Rich Presence, entirely best-effort:

- **`DiscordPresenceService`** — the only class allowed to import
  `com.jagrosh.discordipc.*` (Apache-2.0, pulled in via JitPack - see
  `launcher/pom.xml` for why, and `THIRD-PARTY-NOTICES.md`). `forAppId(String)`
  returns `null` if the client has no `discord_app_id` configured
  (`auth.ClientInfo#discordAppId`, set per-`LauncherClient` in
  `/admin/launcher-auth`) - `LauncherApp` simply skips Discord entirely in
  that case, never constructing an instance.

  Every method swallows `Throwable`, not just `Exception`: the underlying
  library depends on junixsocket for the Unix domain socket it uses to
  reach Discord on Linux/macOS, which bundles native code that could fail
  to load on some platform/architecture with an `Error`, not an
  `Exception`. Discord Rich Presence is a nice-to-have - it must never be
  able to crash or block the rest of the launcher, on any platform, for
  any reason. `connect()` does real (blocking, potentially slow) I/O and
  must be called off the EDT.

  `connect()` doesn't just try once: a single one-shot attempt at launcher
  startup used to mean a Discord client that wasn't fully up yet at that
  exact moment (or one that starts/restarts later in the session) never
  got Rich Presence back at all - the actual root cause behind an earlier
  "Discord status doesn't work" report, since the data plumbing
  (`discord_app_id` end to end) was already verified correct via a direct
  API call against the test client. It now retries every 15s (own
  single-thread `ScheduledExecutorService`, checking `IPCClient#getStatus()
  == PipeStatus.CONNECTED`) until it succeeds or `close()` is called, and
  replays whatever `setBrowsing()`/`setPlaying()` was last asked for the
  moment a (re)connect succeeds, via an internal `pendingActivity` field -
  otherwise an activity requested while disconnected would just be
  dropped instead of appearing once reconnected.

  Each connection attempt runs on its own `connectWorker` (a cached thread
  pool), bounded by `CONNECT_TIMEOUT_SECONDS` (5s), rather than directly on
  the reconnect scheduler's thread - `IPCClient#connect()`'s handshake read
  has no timeout of its own, and if Discord ever accepts the raw socket but
  is slow/unresponsive answering it, that call can hang indefinitely; since
  `scheduleWithFixedDelay` never starts run N+1 until run N returns, a
  single hung attempt used to silently kill every future retry for the
  rest of the session. A stuck attempt is now just abandoned (its thread
  may linger - it's a daemon thread, and this is an edge case) instead of
  wedging the whole retry loop.

  A still-unreproduced report (Linux, native `.deb` Discord install): the
  Unix socket path resolution was independently verified correct by
  decompiling the actual `com.jagrosh:DiscordIPC` jar -
  `Pipe.getPipeLocation()` checks `XDG_RUNTIME_DIR`/`TMPDIR`/`TMP`/`TEMP` in
  order (falling back to `/tmp`), then tries `<dir>/discord-ipc-0` through
  `-9`, which is exactly the standard Discord IPC socket convention for a
  native Linux install - not a bug in that logic. If it's still not
  working after the timeout fix above, the next thing to check is the
  actual `logs/launcher-*.log` line for the connection attempt (`Log.warn`
  in `attemptConnect()`), since guessing further without that is unlikely
  to be productive.

**Wiring** (in `LauncherApp`, not this package): connects once at startup
if a `discord_app_id` was returned by `fetchClientInfo` *and*
`LauncherConfig#discordEnabled()` is true (default); `setBrowsing()` while
sitting in the shell, `setPlaying(modpackName)` while a game process is
running (same `setRunning`/`onExit` hook that drives the modpack detail
page's "Arreter" button), `close()` on logout/app exit. The Settings
page's "Afficher mon statut sur Discord" checkbox
(`LauncherApp#setDiscordEnabled`) persists the flag and, live, either
`close()`s the current connection or calls `connectDiscordPresence()`
again - both that method and the startup path share the same
`discordEnabled()` gate rather than duplicating the check.

**Why JitPack, not Maven Central**: `com.jagrosh:DiscordIPC` is genuinely
100% Java for the *IPC protocol* itself, but its `pom.xml` only ever
published to Bintray/JCenter, which has been shut down since 2022 - there
is no working Maven Central coordinate for it. JitPack builds directly
from the GitHub repository; pinned to an exact commit SHA
(`a8d6631cc90b25f1ede2178b99ad19d016f002a0`) since the upstream repo has no
tagged releases to pin to instead.
