package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.util.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Watches a freshly spawned launcher process just long enough to know whether it actually got a
 * window on screen - see {@code StartupSignal} for the handshake itself and why it is a file.
 *
 * <p>This is what lets the bootstrap keep its splash up across the several seconds a cold JVM takes
 * to paint its first frame, and what lets it report a launcher that died on the way up instead of
 * exiting silently and leaving the player with nothing.
 */
final class LauncherStartupWatcher {

    /**
     * How long to wait for the signal before giving up and closing the splash anyway.
     *
     * <p>This is a real upper bound, not a formality: a launcher jar older than the release that
     * introduced the handshake never signals at all, so on the (narrow, but real) combination of a
     * new bootstrap and a cached older launcher, every start waits this out. Thirty seconds is
     * comfortably longer than a cold JVM start on a slow disk, and timing out is not a failure -
     * it just falls back to the behaviour that shipped before: close the splash, trust the process,
     * exit.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private LauncherStartupWatcher() {
    }

    /** What the launcher process did while we watched it. */
    enum Outcome {
        /** A window appeared - the normal path. */
        WINDOW_SHOWN,

        /** Exited with status 0 before signalling. Odd, but not an error worth alarming anyone over. */
        EXITED_CLEANLY,

        /** Exited non-zero before ever showing a window - the case worth a crash report. */
        CRASHED,

        /** Still running, but never signalled within {@link #TIMEOUT}. */
        TIMED_OUT
    }

    record Result(Outcome outcome, int exitCode) {

        boolean crashed() {
            return outcome == Outcome.CRASHED;
        }
    }

    /**
     * Blocks until the launcher signals, dies, or {@link #TIMEOUT} elapses.
     *
     * @param process   the spawned launcher
     * @param readyFile the path handed to it via {@code StartupSignal.PROPERTY}
     */
    static Result await(Process process, Path readyFile) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();

        while (true) {
            if (Files.exists(readyFile)) {
                return new Result(Outcome.WINDOW_SHOWN, 0);
            }

            if (!process.isAlive()) {
                // The process can legitimately show its window and then exit almost immediately
                // (a player who closes it at once, a crash straight after the first frame). Re-check
                // the file after observing the exit, or that ordering would be misreported as a
                // startup crash: the window genuinely did appear, so there is nothing to alarm
                // anyone about.
                if (Files.exists(readyFile)) {
                    return new Result(Outcome.WINDOW_SHOWN, process.exitValue());
                }

                int exitCode = process.exitValue();
                return new Result(exitCode == 0 ? Outcome.EXITED_CLEANLY : Outcome.CRASHED, exitCode);
            }

            if (System.nanoTime() - deadline >= 0) {
                Log.warn(LauncherStartupWatcher.class,
                        "The launcher did not signal a visible window within " + TIMEOUT.toSeconds()
                                + "s; closing the splash and assuming it is starting normally.");
                return new Result(Outcome.TIMED_OUT, 0);
            }

            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Result(Outcome.TIMED_OUT, 0);
            }
        }
    }
}
