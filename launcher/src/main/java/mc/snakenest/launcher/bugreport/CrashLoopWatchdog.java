package mc.snakenest.launcher.bugreport;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Detects "exceptions recurring faster than a player could ever dismiss them one at a time" -
 * {@link CrashReportHandler}'s normal per-exception prompt already stops a second dialog from
 * piling on top of a first one, but a genuine throw loop (a bad repaint, a bad {@code Timer}
 * listener, ...) would otherwise just mean the very first exception's prompt sits there forever
 * while the launcher silently keeps re-throwing behind it - nothing else in the design catches
 * that on its own.
 *
 * <p>Pure and independently testable on purpose: no Swing, no I/O, just "does the rate of calls to
 * {@link #recordAndCheckTripped()} exceed {@link #THRESHOLD} within {@link #WINDOW}". Thread-safe,
 * since exceptions on different threads (background handler vs. the EDT's own) can race here.
 */
final class CrashLoopWatchdog {

    // Chosen the same way a systemd StartLimitBurst/StartLimitIntervalSec pair would be: loose
    // enough that a handful of unrelated crashes spread over a few seconds is never mistaken for
    // a loop, tight enough that a genuine tight throw loop (easily hundreds per second) still
    // trips within a fraction of a second of starting.
    private static final int THRESHOLD = 100;
    private static final Duration WINDOW = Duration.ofSeconds(5);

    private final Deque<Instant> recentExceptions = new ArrayDeque<>();

    /**
     * Records one more exception and reports whether the rate over the trailing {@link #WINDOW}
     * has now reached {@link #THRESHOLD}. Once tripped, callers are expected to stop calling this
     * (see {@code CrashReportHandler}'s own tripped flag) - this method doesn't remember having
     * already tripped, it just answers the same question again each time.
     */
    synchronized boolean recordAndCheckTripped() {
        Instant now = Instant.now();
        recentExceptions.addLast(now);
        while (!recentExceptions.isEmpty() && Duration.between(recentExceptions.peekFirst(), now).compareTo(WINDOW) > 0) {
            recentExceptions.removeFirst();
        }
        return recentExceptions.size() >= THRESHOLD;
    }
}
