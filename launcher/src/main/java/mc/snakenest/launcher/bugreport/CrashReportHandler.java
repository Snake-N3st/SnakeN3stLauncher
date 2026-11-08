package mc.snakenest.launcher.bugreport;

import mc.snakenest.launcher.ui.bugreport.CrashPromptDialog;
import mc.snakenest.launcher.util.Log;
import mc.snakenest.launcher.util.Throwables;

import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wires everything that can go wrong with the launcher's own process to the same consent prompt
 * and, if it's bad enough, the same forced shutdown: uncaught exceptions on background threads,
 * uncaught exceptions on the EDT, a tight loop of either, and an EDT that has stopped responding
 * altogether.
 *
 * <h2>Uncaught exceptions</h2>
 * Background threads reach the JVM's normal {@link Thread#setDefaultUncaughtExceptionHandler}.
 * The EDT does not: Swing's own event-dispatch loop
 * ({@code EventDispatchThread.pumpOneEventForFilters}) catches exceptions thrown while handling
 * an event itself, so they never propagate to that handler at all. The historically-documented
 * hook for this, the {@code sun.awt.exception.handler} system property, is <b>dead on modern
 * JDKs</b> - verified directly against this project's JDK 21 build: the string isn't even present
 * in {@code EventDispatchThread.class} anymore, and a minimal reproduction confirmed it silently
 * does nothing. The mechanism that still actually works is pushing a custom {@link EventQueue}
 * that wraps {@code dispatchEvent} in a try/catch - also verified directly: the EDT keeps pumping
 * events normally afterward, exactly as if nothing happened, which is what makes it possible to
 * report a single bad exception without taking the whole window down.
 *
 * <p>Never silent about the exception itself: it always goes to the log first, before the player
 * is ever asked anything - the dialog on top of that is purely about consent to also send it to
 * the server, which the player can decline.
 *
 * <h2>Crash loops</h2>
 * A single bad exception is fine to prompt about. A tight throw loop is not: nothing above stops
 * the launcher from re-throwing behind an already-open prompt forever, one crash-report dialog
 * jammed on screen while the real problem keeps happening invisibly underneath it. Every call to
 * {@link #handle} is fed through {@link CrashLoopWatchdog}; once the rate trips,
 * {@link #beginShutdown} takes over.
 *
 * <h2>A frozen EDT</h2>
 * Neither mechanism above helps if the EDT simply stops processing events - a deadlock, an
 * infinite loop inside a paint method, a lock held forever - since nothing throws, so nothing
 * reaches either watchdog. {@link EdtFreezeWatchdog} catches that case independently: if a
 * heartbeat posted to the EDT hasn't landed in 20s, the same {@link #beginShutdown} runs, using
 * the frozen thread's own stack trace as the report detail - the most useful diagnostic a launcher
 * that can no longer respond to anything can still produce.
 */
public final class CrashReportHandler {

    private static final Duration SUBMIT_TIMEOUT_BEFORE_EXIT = Duration.ofSeconds(5);
    // Comfortably longer than SUBMIT_TIMEOUT_BEFORE_EXIT, so the normal (dialog-answered) path
    // always wins the race - this only ever fires when that path didn't happen at all.
    private static final Duration FAIL_SAFE_EXIT_DELAY = Duration.ofSeconds(15);

    // Only one crash prompt at a time - guards against a second exception (e.g. thrown while the
    // first prompt is still on screen) piling up another dialog on top of it.
    private static final AtomicBoolean PROMPTING = new AtomicBoolean();
    private static final CrashLoopWatchdog LOOP_WATCHDOG = new CrashLoopWatchdog();
    // Once a forced shutdown has started (crash loop or EDT freeze - whichever gets there first),
    // every further exception is a no-op - there is exactly one shutdown dialog and exactly one
    // exit, never a pile-up of either.
    private static final AtomicBoolean SHUTDOWN_STARTED = new AtomicBoolean();

    private CrashReportHandler() {
    }

    /** Installs every handler. Call once, as early in {@code main} as possible. */
    public static void install(BugReportApiClient api) {
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> handle(api, thread, ex));

        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
            @Override
            protected void dispatchEvent(AWTEvent event) {
                try {
                    super.dispatchEvent(event);
                } catch (Throwable ex) {
                    handle(api, Thread.currentThread(), ex);
                }
            }
        });

        new EdtFreezeWatchdog(frozenEdtThread -> handleFreeze(api, frozenEdtThread)).start();
    }

    static void handle(BugReportApiClient api, Thread thread, Throwable ex) {
        if (SHUTDOWN_STARTED.get()) {
            return;
        }
        Log.error(CrashReportHandler.class, "Uncaught exception on " + thread.getName(), ex);

        if (LOOP_WATCHDOG.recordAndCheckTripped()) {
            handleLoop(api, ex);
            return;
        }

        if (!PROMPTING.compareAndSet(false, true)) {
            return;
        }

        String stackTrace = Throwables.stackTraceOf(ex);
        String summary = ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : "");

        // main() runs off the EDT, and an early startup crash could fire before any window
        // exists - invokeLater still works fine with no window behind it (JOptionPane centers on
        // the screen when parent is null).
        SwingUtilities.invokeLater(() -> {
            String description = CrashPromptDialog.ask(null, summary);
            PROMPTING.set(false);
            if (description != null) {
                submitInBackground(api, summary, description, stackTrace);
            }
        });
    }

    private static void handleLoop(BugReportApiClient api, Throwable lastException) {
        String summary = "Le launcher enchaîne trop d'erreurs et doit se fermer (dernière : "
                + lastException.getClass().getSimpleName() + ").";
        beginShutdown(api, summary, Throwables.stackTraceOf(lastException));
    }

    private static void handleFreeze(BugReportApiClient api, Thread frozenEdtThread) {
        String summary = "Le launcher ne répond plus depuis plus de 20 secondes et doit se fermer.";
        String details = frozenEdtThread != null
                ? "Pile de la file d'événements (EDT) au moment du gel :\n" + stackTraceOf(frozenEdtThread)
                : "Aucun battement de la file d'événements n'a jamais été observé.";
        beginShutdown(api, summary, details);
    }

    /**
     * Runs at most once per launcher process, from whichever of {@link #handleLoop}/
     * {@link #handleFreeze} gets there first. Closes any prompt already on screen (there is
     * nothing left to gain by letting the player answer it - the launcher is already past the
     * point of recovering), force-arms an unconditional exit, then offers one last report before
     * closing for good.
     *
     * <p>Termination doesn't depend on that last report ever being answered: see
     * {@link #armFailSafeExit}. A watchdog that can only actually stop the process once someone
     * clicks a button isn't much of a safety net if the problem is severe enough to also leave the
     * UI unresponsive.
     */
    private static void beginShutdown(BugReportApiClient api, String summary, String details) {
        if (!SHUTDOWN_STARTED.compareAndSet(false, true)) {
            return;
        }
        // Logged locally regardless of what the player answers below - the freeze/loop diagnostic
        // (the frozen EDT's own stack, or the last exception) is too valuable to only exist in
        // memory until an unanswered dialog gets force-closed by the fail-safe exit.
        Log.error(CrashReportHandler.class, summary + "\n" + details, null);
        CrashPromptDialog.closeActiveDialog();
        armFailSafeExit();

        SwingUtilities.invokeLater(() -> {
            String description = CrashPromptDialog.ask(null, summary);
            if (description != null) {
                submitAndWaitBriefly(api, summary, description, details);
            }
            System.exit(1);
        });
    }

    /** Unconditional {@link System#exit} after {@link #FAIL_SAFE_EXIT_DELAY} - see {@link #beginShutdown}. */
    private static void armFailSafeExit() {
        Thread failSafe = new Thread(() -> {
            try {
                Thread.sleep(FAIL_SAFE_EXIT_DELAY.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Log.error(CrashReportHandler.class,
                    "Fail-safe exit: the shutdown dialog was never answered, forcing exit anyway", null);
            System.exit(1);
        }, "crash-shutdown-fail-safe");
        failSafe.setDaemon(true);
        failSafe.start();
    }

    /**
     * Blocking, on purpose and bounded: this is the last thing the process ever does, so there is
     * no "later" to defer the network call to - but a launcher that's already in trouble
     * shouldn't also hang indefinitely on a dead connection before it can finally exit.
     */
    private static void submitAndWaitBriefly(BugReportApiClient api, String summary, String description, String details) {
        Thread reporter = new Thread(
                () -> api.submit(BugReportType.LAUNCHER_CRASH, summary, description, details, null),
                "bug-report-submit-before-exit");
        reporter.setDaemon(true);
        reporter.start();
        try {
            reporter.join(SUBMIT_TIMEOUT_BEFORE_EXIT.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void submitInBackground(BugReportApiClient api, String summary, String description, String stackTrace) {
        Thread reporter = new Thread(
                () -> api.submit(BugReportType.LAUNCHER_CRASH, summary, description, stackTrace, null),
                "bug-report-submit");
        reporter.setDaemon(true);
        reporter.start();
    }

    private static String stackTraceOf(Thread thread) {
        StringBuilder builder = new StringBuilder();
        for (StackTraceElement element : thread.getStackTrace()) {
            builder.append("\tat ").append(element).append('\n');
        }
        return builder.toString();
    }
}
