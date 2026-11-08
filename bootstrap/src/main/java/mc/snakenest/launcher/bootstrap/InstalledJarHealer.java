package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Brings the *installed* bootstrap jar up to date after the fact, from a newer copy that is already running.
 *
 * <p><b>The problem.</b> On Windows a jar that is running cannot be replaced, so a bootstrap cannot update
 * itself in place. The update is therefore downloaded into the player's data directory and run from there
 * (see {@code BootstrapMain#delegateToUserCopy}) - which works, but leaves the installed jar on the old
 * version for good: every start would launch the old one just to hand over to the new one.
 *
 * <p><b>The fix.</b> The newer copy is the tool: it is not the file the old process holds open, and it is
 * already on disk. Started with {@value #PROPERTY} naming the installed jar, it waits for the old process to
 * let go of that file - which it does within a second or two, having only spawned this copy and exited - and
 * then copies itself over it. No script to extract, no second artifact, and on the next start the installed
 * jar already is the latest, so nothing is handed over any more.
 *
 * <p><b>Strictly optional.</b> If it never succeeds - the jar is under a folder this account cannot write
 * to, an antivirus keeps the file - nothing is lost: the hand-over keeps working exactly as before. That is
 * why it runs on a daemon thread, gives up after a bounded time, and never reports a failure to the player.
 *
 * <p><b>What it will and will not touch.</b> Only when this running jar is itself one of the data-directory
 * copies ({@code cache/bootstrap-<16 hex>.jar}) and the target is a plain {@code .jar} file that is not
 * owned by a package manager. A {@code .deb}'s jar must never be rewritten behind dpkg's back - that is the
 * very bug the user-copy design exists to avoid.
 */
final class InstalledJarHealer {

    /** System property naming the installed jar to bring up to date. Set by the bootstrap that hands over. */
    static final String PROPERTY = "sn3.replaceTarget";

    private static final int ATTEMPTS = 40;
    private static final long DELAY_MILLIS = 500;

    private InstalledJarHealer() {
    }

    /** Called once at startup; does nothing unless the property is set and every guard passes. */
    static void startIfRequested(AppDirs dirs, Path ownJar) {
        String requested = System.getProperty(PROPERTY);

        if (requested == null || requested.isBlank() || ownJar == null) {
            return;
        }

        Path target = Path.of(requested);

        if (!isAllowed(ownJar, target, dirs.cache())) {
            Log.info(InstalledJarHealer.class, "Not replacing " + target + ": the conditions for doing so are not met.");
            return;
        }

        Thread thread = new Thread(() -> {
            boolean done = replaceWithRetries(ownJar, target, ATTEMPTS, DELAY_MILLIS);
            Log.info(InstalledJarHealer.class, done
                    ? "The installed bootstrap at " + target + " is now up to date."
                    : "Could not update the installed bootstrap at " + target + "; continuing to hand over from the data directory.");
        }, "installed-jar-healer");
        // Daemon: if the bootstrap is done before this is, the old jar is simply healed on a later run.
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Whether {@code own} may replace {@code target}: {@code own} must be a data-directory copy (so this is
     * never asked of an installed jar, or of one somebody pointed the property at), and {@code target} a
     * plain, non-package-owned {@code .jar} file other than {@code own} itself.
     */
    static boolean isAllowed(Path own, Path target, Path cacheDir) {
        Path absoluteOwn = own.toAbsolutePath().normalize();
        Path absoluteTarget = target.toAbsolutePath().normalize();

        boolean ownIsUserCopy = cacheDir.toAbsolutePath().normalize().equals(absoluteOwn.getParent())
                && absoluteOwn.getFileName().toString().matches("bootstrap-[0-9a-f]{16}\\.jar");

        return ownIsUserCopy
                && !absoluteTarget.equals(absoluteOwn)
                && absoluteTarget.getFileName().toString().endsWith(".jar")
                && Files.isRegularFile(absoluteTarget)
                && !BootstrapMain.isPackageManaged(absoluteTarget);
    }

    /** One attempt. True when {@code target} holds {@code own}'s bytes afterwards (it may already have). */
    static boolean replaceOnce(Path own, Path target) {
        try {
            String sha;
            try (InputStream in = Files.newInputStream(own)) {
                sha = mc.snakenest.launcher.util.Sha256.hex(in);
            }

            if (BootstrapMain.hasSha256(target, sha)) {
                return true;
            }

            try (InputStream in = Files.newInputStream(own)) {
                // Written next to the target and moved into place, so a reader never sees half a jar.
                AtomicFiles.writeVerified(target, in, sha);
            }

            return BootstrapMain.hasSha256(target, sha);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Retries while the old process still holds the file; returns as soon as one attempt works. */
    static boolean replaceWithRetries(Path own, Path target, int attempts, long delayMillis) {
        for (int i = 0; i < attempts; i++) {
            if (replaceOnce(own, target)) {
                return true;
            }

            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }
}
