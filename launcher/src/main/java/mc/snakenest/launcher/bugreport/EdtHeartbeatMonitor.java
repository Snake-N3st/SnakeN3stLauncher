package mc.snakenest.launcher.bugreport;

import java.time.Duration;

/**
 * Pure "how long since the last heartbeat" clock - deliberately separated from
 * {@link EdtFreezeWatchdog}'s actual Swing/thread plumbing (posting to the EDT, running a checker
 * thread) so the timing decision itself is independently and quickly unit-testable, the same way
 * {@link CrashLoopWatchdog} is. Thread-safe: {@link #heartbeat()} runs on the EDT, {@link #isFrozen()}
 * on a separate checker thread.
 */
final class EdtHeartbeatMonitor {

    private final Duration freezeThreshold;
    private volatile long lastHeartbeatNanos = System.nanoTime();

    EdtHeartbeatMonitor(Duration freezeThreshold) {
        this.freezeThreshold = freezeThreshold;
    }

    void heartbeat() {
        lastHeartbeatNanos = System.nanoTime();
    }

    boolean isFrozen() {
        return System.nanoTime() - lastHeartbeatNanos >= freezeThreshold.toNanos();
    }
}
