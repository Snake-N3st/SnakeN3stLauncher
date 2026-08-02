package mc.snakenest.launcher.bugreport;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Detects an EDT that has stopped processing events at all - a deadlock, an infinite loop inside
 * a paint/listener, a lock held forever - which {@link CrashReportHandler}'s exception-based
 * mechanisms can't see: nothing throws, so nothing reaches {@link CrashLoopWatchdog} either.
 *
 * <p>Two threads, both daemon: one repeatedly posts a heartbeat to the EDT via
 * {@link SwingUtilities#invokeLater}, the other polls whether that heartbeat is still landing on
 * schedule ({@link EdtHeartbeatMonitor}, kept separate and pure for its own unit tests). Posting
 * never blocks even if the EDT is fully wedged - {@code invokeLater} only ever enqueues - so the
 * poster keeps running regardless, and the checker's {@code onFrozen} callback fires exactly once,
 * with whichever {@link Thread} last identified itself as the EDT (its {@link Thread#getStackTrace()}
 * can still be inspected from another thread even while it's stuck, which is the whole point -
 * that stack is the most useful diagnostic a frozen launcher can still produce).
 */
final class EdtFreezeWatchdog {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(1);
    private static final Duration FREEZE_THRESHOLD = Duration.ofSeconds(20);

    private final Duration heartbeatInterval;
    private final EdtHeartbeatMonitor monitor;
    private final Consumer<Thread> onFrozen;
    private final AtomicBoolean triggered = new AtomicBoolean();
    private volatile Thread lastKnownEdtThread;

    EdtFreezeWatchdog(Consumer<Thread> onFrozen) {
        this(HEARTBEAT_INTERVAL, FREEZE_THRESHOLD, onFrozen);
    }

    EdtFreezeWatchdog(Duration heartbeatInterval, Duration freezeThreshold, Consumer<Thread> onFrozen) {
        this.heartbeatInterval = heartbeatInterval;
        this.monitor = new EdtHeartbeatMonitor(freezeThreshold);
        this.onFrozen = onFrozen;
    }

    void start() {
        Thread poster = new Thread(this::pumpHeartbeats, "edt-freeze-watchdog-heartbeat");
        poster.setDaemon(true);
        poster.start();

        Thread checker = new Thread(this::checkLoop, "edt-freeze-watchdog-checker");
        checker.setDaemon(true);
        checker.start();
    }

    private void pumpHeartbeats() {
        while (!triggered.get()) {
            SwingUtilities.invokeLater(() -> {
                lastKnownEdtThread = Thread.currentThread();
                monitor.heartbeat();
            });
            sleep(heartbeatInterval);
        }
    }

    private void checkLoop() {
        while (!triggered.get()) {
            sleep(heartbeatInterval);
            if (monitor.isFrozen() && triggered.compareAndSet(false, true)) {
                onFrozen.accept(lastKnownEdtThread);
                return;
            }
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
