# bugreport

Crash/bug reporting: catches uncaught exceptions, detects a genuine game
crash, and submits a report to the site plugin's admin-visible
`bug_reports` table (`POST /api/launcher-auth/bug-reports` - see
`LAUNCHER_INTEGRATION.md` in the sibling `SnakeN3stLogin` repo). The dialogs
that ask the player's consent live in `ui.bugreport` instead of here, same
split as `crypto`/`ui.pin`.

- **`BugReportType`** — mirrors the site's `BugReport::TYPE_*` constants
  exactly (`launcher_crash`, `game_crash`, `manual`); the wire value is
  validated server-side against these three strings.

- **`BugReportApiClient`** — unauthenticated (a launcher crash can happen
  before login, so this can't depend on a signed session like
  `auth.LauncherAuthApiClient` does). `submit(...)` is best-effort and
  **never throws** - a crash reporter that can itself fail loudly would turn
  one problem into two. Truncates every field client-side to the server's
  column sizes first, so a report isn't silently dropped by a 422 the player
  never sees.

- **`CrashReportHandler`** — installed once, as early as possible in
  `Main.main`. Covers both places an uncaught exception can end up in a
  Swing app:
  - background threads, via the JVM's normal
    `Thread.setDefaultUncaughtExceptionHandler`;
  - the Event Dispatch Thread, which does **not** reach that handler -
    Swing's own event loop catches EDT exceptions internally rather than
    propagating them. The historically-documented hook for this,
    `sun.awt.exception.handler`, is **dead on modern JDKs** - confirmed
    directly against this project's JDK 21 (the string isn't even present
    in `EventDispatchThread.class` anymore, and a minimal reproduction
    silently did nothing). What still actually works, verified the same
    way: pushing a custom `java.awt.EventQueue` onto
    `Toolkit.getDefaultToolkit().getSystemEventQueue()` that wraps
    `dispatchEvent()` in a try/catch. The EDT keeps pumping events normally
    afterward, same as the JDK's own internal catch would have done.

  Either path always logs first, then shows `ui.bugreport.CrashPromptDialog`
  - nothing is ever sent without that explicit confirmation.

- **`CrashLoopWatchdog`** — a single bad exception is fine to prompt about;
  a tight throw loop is not, since nothing above stops the launcher from
  re-throwing behind an already-open prompt forever. Every call to
  `CrashReportHandler.handle` is fed through this watchdog first (pure,
  no Swing/IO, independently unit-tested); once the rate crosses the
  threshold (100 exceptions within 5s, same reasoning as a systemd
  `StartLimitBurst`/`StartLimitIntervalSec` pair - loose enough that a
  handful of unrelated crashes spread over a few seconds is never mistaken
  for a loop, tight enough that a genuine tight loop - easily hundreds per
  second - still trips within a fraction of a second) it hands off to
  `CrashReportHandler.beginShutdown` (see below).

- **`EdtHeartbeatMonitor`** / **`EdtFreezeWatchdog`** — catches the case
  neither exception-based mechanism can see at all: an EDT that has simply
  stopped processing events (a deadlock, an infinite loop inside a
  paint/listener, a lock held forever). Nothing throws, so nothing reaches
  `CrashLoopWatchdog` either. `EdtFreezeWatchdog` runs two daemon threads -
  one repeatedly posts a heartbeat to the EDT via `SwingUtilities.invokeLater`
  (never blocks even if the EDT is fully wedged, since `invokeLater` only
  ever enqueues), the other polls `EdtHeartbeatMonitor` (kept separate and
  pure, like `CrashLoopWatchdog`, so the timing decision itself is
  independently unit-tested without needing a real frozen EDT in every
  test) for whether that heartbeat is still landing within 20s. Once it
  isn't, `CrashReportHandler.beginShutdown` runs the same way the loop
  watchdog triggers it, except the report's technical detail is the frozen
  thread's own `getStackTrace()` - inspectable from another thread even
  while it's stuck, and the most useful diagnostic a launcher that can no
  longer respond to anything can still produce.

- **`CrashReportHandler.beginShutdown`** — shared by both watchdogs above,
  runs at most once per process: force-closes whatever prompt is currently
  open (`ui.bugreport.CrashPromptDialog#closeActiveDialog`, which needed
  that dialog to move off the `JOptionPane.showConfirmDialog` convenience
  method onto `createDialog` so the `JDialog` could be tracked and disposed
  from outside), logs the full diagnostic locally right away (not only if
  the player agrees to send it - too valuable to risk losing if the report
  dialog below is never answered), then offers one last report before
  `System.exit`. Termination doesn't depend on that dialog ever being
  answered: a fail-safe timer force-exits 15s later regardless, since a
  watchdog that can only actually stop the process once someone clicks a
  button isn't much of a safety net if the problem is severe enough to also
  leave the UI unresponsive.

  Both watchdogs verified end-to-end against the real `CrashReportHandler`/
  `CrashPromptDialog`, not just unit-tested in isolation - in both cases
  **with no dialog ever answered**, proving termination really doesn't
  depend on it:
  - crash loop: a background thread hammering
    `SwingUtilities.invokeLater(() -> { throw ...; })` produced a clean
    forced `System.exit` within the fail-safe window. One methodology
    pitfall worth remembering if this ever needs re-testing: a repeating
    `javax.swing.Timer` is *not* a valid way to simulate a throw loop - its
    internal "already notified" bookkeeping gets stuck the first time the
    listener throws, so it silently stops rescheduling after exactly one
    firing. A background thread posting `invokeLater` directly has no such
    state and reliably keeps generating events.
  - EDT freeze: a single `SwingUtilities.invokeLater(() -> Thread.sleep(120_000))`
    (a genuine, indefinite EDT block, no exception involved at all) was
    detected within the 20s threshold, its stack trace captured and logged
    locally, and the process force-exited via the fail-safe ~15s later -
    total ~37s from freeze to exit, matching the two thresholds added
    together.

**Game crashes** are detected in `LauncherApp.launchGame`'s process-exit
callback, not in this package: a non-zero exit only means something when
it *isn't* what `stopGame`/`killGame` already caused (see their Javadoc -
`destroy()`/`destroyForcibly()` also produce a non-zero exit that looks like
a crash from the outside). `LauncherApp` reuses the existing
`Activity.STOPPING` flag to tell the two apart, so no extra state had to be
threaded through `ActiveModpack`. When a crash-reports file appears under
the instance's `crash-reports/` after launch, its content is attached as the
report's technical detail; otherwise just the exit code is.
