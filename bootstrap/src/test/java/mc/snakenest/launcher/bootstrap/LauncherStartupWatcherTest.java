package mc.snakenest.launcher.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives LauncherStartupWatcher with real short-lived processes rather than a mocked Process: the
 * distinction the watcher has to get right - "did a window appear before this exited?" - is a race
 * between a file and a process death, and a hand-rolled fake Process would let that race be
 * asserted away rather than actually tested.
 */
class LauncherStartupWatcherTest {

    /** A process that exits with the given status after roughly the given delay. */
    private static Process sleepThenExit(long millis, int exitCode) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();

        // Spawning a JVM is heavier than a shell script, but it is the one interpreter guaranteed
        // to exist wherever these tests run, Windows included.
        return new ProcessBuilder(
                java,
                "-cp", System.getProperty("java.class.path"),
                SleepThenExit.class.getName(),
                Long.toString(millis),
                Integer.toString(exitCode)).start();
    }

    /** Helper main class for {@link #sleepThenExit}. */
    public static final class SleepThenExit {
        public static void main(String[] args) throws Exception {
            Thread.sleep(Long.parseLong(args[0]));
            System.exit(Integer.parseInt(args[1]));
        }
    }

    @Test
    void reportsTheWindowAsShownOnceTheReadyFileAppears(@TempDir Path dir) throws Exception {
        Path ready = dir.resolve("ready");
        Process process = sleepThenExit(30_000, 0);

        try {
            // Stands in for the launcher touching the file as its first window appears.
            Thread signaller = new Thread(() -> {
                try {
                    Thread.sleep(300);
                    Files.writeString(ready, "ready");
                } catch (Exception ignored) {
                    // The assertion below is what reports a failure here.
                }
            });
            signaller.start();

            LauncherStartupWatcher.Result result = LauncherStartupWatcher.await(process, ready);

            assertEquals(LauncherStartupWatcher.Outcome.WINDOW_SHOWN, result.outcome());
            assertFalse(result.crashed());
            signaller.join();
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void reportsACrashWhenTheProcessDiesNonZeroWithoutSignalling(@TempDir Path dir) throws Exception {
        Path ready = dir.resolve("ready");
        Process process = sleepThenExit(200, 3);

        LauncherStartupWatcher.Result result = LauncherStartupWatcher.await(process, ready);

        assertEquals(LauncherStartupWatcher.Outcome.CRASHED, result.outcome());
        assertEquals(3, result.exitCode());
        assertTrue(result.crashed());
    }

    @Test
    void doesNotCallAZeroExitACrash(@TempDir Path dir) throws Exception {
        Path ready = dir.resolve("ready");
        Process process = sleepThenExit(200, 0);

        LauncherStartupWatcher.Result result = LauncherStartupWatcher.await(process, ready);

        assertEquals(LauncherStartupWatcher.Outcome.EXITED_CLEANLY, result.outcome());
        assertFalse(result.crashed());
    }

    /**
     * A launcher that showed its window and then exited non-zero (a crash just after the first
     * frame, or a player closing it at once) must not be reported as a startup crash: the player
     * saw the window, so the crash dialog would be telling them something they know to be false.
     *
     * <p>This pins down the case where the signal is already on disk by the time the watcher looks.
     * The narrower interleaving - the file landing between the watcher's existence check and its
     * liveness check, within the same sub-100ms iteration - is guarded by the second {@code exists}
     * call in {@code await}, but is not deterministically reproducible from a test, so removing
     * that guard will not fail this suite. It is kept as cheap defence, not because it is covered.
     */
    @Test
    void doesNotReportACrashWhenTheWindowAppearedFirst(@TempDir Path dir) throws Exception {
        Path ready = dir.resolve("ready");
        Files.writeString(ready, "ready");

        Process process = sleepThenExit(150, 1);
        process.waitFor();

        LauncherStartupWatcher.Result result = LauncherStartupWatcher.await(process, ready);

        assertEquals(LauncherStartupWatcher.Outcome.WINDOW_SHOWN, result.outcome());
        assertFalse(result.crashed());
    }
}
