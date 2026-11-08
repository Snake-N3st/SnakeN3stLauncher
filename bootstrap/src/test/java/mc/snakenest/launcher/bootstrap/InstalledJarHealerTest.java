package mc.snakenest.launcher.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The installed jar is brought up to date by a newer copy running from the data directory. What matters:
 * it does it when it should, never when it should not (a package-owned jar, a path somebody injected), and
 * never leaves a half-written or stray file behind.
 */
class InstalledJarHealerTest {

    private static final String OWN_NAME = "bootstrap-0123456789abcdef.jar";

    private static Path cache(Path dir) throws Exception {
        return Files.createDirectories(dir.resolve("cache"));
    }

    @Test
    void replacesTheInstalledJarWithTheRunningCopy(@TempDir Path dir) throws Exception {
        Path own = Files.writeString(cache(dir).resolve(OWN_NAME), "new bootstrap").toAbsolutePath();
        Path target = Files.writeString(dir.resolve("snaken3st-bootstrap.jar"), "old bootstrap");

        assertTrue(InstalledJarHealer.replaceOnce(own, target));
        assertArrayEquals(Files.readAllBytes(own), Files.readAllBytes(target));
    }

    @Test
    void leavesNoTemporaryFileBehind(@TempDir Path dir) throws Exception {
        Path own = Files.writeString(cache(dir).resolve(OWN_NAME), "new bootstrap");
        Path target = Files.writeString(dir.resolve("snaken3st-bootstrap.jar"), "old bootstrap");

        InstalledJarHealer.replaceOnce(own, target);

        try (var files = Files.list(dir)) {
            assertEquals(List.of("cache", "snaken3st-bootstrap.jar"), files.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    @Test
    void anAlreadyCurrentTargetCountsAsDoneWithoutRewriting(@TempDir Path dir) throws Exception {
        Path own = Files.writeString(cache(dir).resolve(OWN_NAME), "same");
        Path target = Files.writeString(dir.resolve("snaken3st-bootstrap.jar"), "same");
        long before = Files.getLastModifiedTime(target).toMillis();
        Thread.sleep(20);

        assertTrue(InstalledJarHealer.replaceOnce(own, target));
        assertEquals(before, Files.getLastModifiedTime(target).toMillis(), "an identical file must not be rewritten");
    }

    /**
     * A regular file where the target's directory should be: writing next to the target can never succeed, which
     * stands in for a file the OS will not let go of (a real lock cannot be reproduced portably here).
     */
    private static Path obstructed(Path dir) throws Exception {
        Path obstruction = Files.writeString(dir.resolve("blocker"), "a file, not a directory");
        return obstruction.resolve("snaken3st-bootstrap.jar");
    }

    @Test
    void failingAttemptsGiveUpAndReportIt(@TempDir Path dir) throws Exception {
        Path own = Files.writeString(cache(dir).resolve(OWN_NAME), "new");
        Path target = obstructed(dir);

        long start = System.nanoTime();
        assertFalse(InstalledJarHealer.replaceWithRetries(own, target, 3, 10));
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 20, "it must actually retry, with the delay between attempts");
    }

    @Test
    void succeedsOnALaterAttemptOnceTheOldProcessHasLetGo(@TempDir Path dir) throws Exception {
        Path own = Files.writeString(cache(dir).resolve(OWN_NAME), "new");
        Path target = obstructed(dir);
        Path obstruction = target.getParent();

        // The obstruction goes away while the retries are running - the same shape as a lock being released.
        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(80);
                Files.delete(obstruction);
            } catch (Exception ignored) {
            }
        });
        releaser.start();

        assertTrue(InstalledJarHealer.replaceWithRetries(own, target, 200, 10), "must succeed once the obstruction is gone");
        releaser.join();
        assertEquals("new", Files.readString(target));
    }

    // --- the guards -----------------------------------------------------------------------------

    @Test
    void allowsADataDirectoryCopyToReplaceAPlayerOwnedJar(@TempDir Path dir) throws Exception {
        Path cache = cache(dir);
        Path own = Files.writeString(cache.resolve(OWN_NAME), "x");
        Path target = Files.writeString(dir.resolve("snaken3st-bootstrap.jar"), "y");

        assertTrue(InstalledJarHealer.isAllowed(own, target, cache));
    }

    @Test
    void refusesWhenTheRunningJarIsNotADataDirectoryCopy(@TempDir Path dir) throws Exception {
        Path cache = cache(dir);
        Path installed = Files.writeString(dir.resolve("snaken3st-bootstrap.jar"), "x");
        Path other = Files.writeString(dir.resolve("other.jar"), "y");

        // An installed jar is never asked to overwrite anything, whatever the property says.
        assertFalse(InstalledJarHealer.isAllowed(installed, other, cache));
    }

    @Test
    void refusesACopyWhoseNameIsNotTheOneTheBootstrapMakes(@TempDir Path dir) throws Exception {
        Path cache = cache(dir);
        Path target = Files.writeString(dir.resolve("snaken3st-bootstrap.jar"), "y");

        for (String name : new String[] {"bootstrap.jar", "bootstrap-XYZ.jar", "bootstrap-0123456789abcde.jar",
                "bootstrap-0123456789ABCDEF.jar", "evil.jar"}) {
            Path own = Files.writeString(cache.resolve(name), "x");
            assertFalse(InstalledJarHealer.isAllowed(own, target, cache), name);
        }
    }

    @Test
    void refusesACopyThatLivesOutsideTheCacheDirectory(@TempDir Path dir) throws Exception {
        Path cache = cache(dir);
        Path elsewhere = Files.createDirectories(dir.resolve("elsewhere"));
        Path own = Files.writeString(elsewhere.resolve(OWN_NAME), "x");
        Path target = Files.writeString(dir.resolve("snaken3st-bootstrap.jar"), "y");

        assertFalse(InstalledJarHealer.isAllowed(own, target, cache));
    }

    @Test
    void refusesTargetsThatAreNotAPlainJarFile(@TempDir Path dir) throws Exception {
        Path cache = cache(dir);
        Path own = Files.writeString(cache.resolve(OWN_NAME), "x");

        assertFalse(InstalledJarHealer.isAllowed(own, dir.resolve("absent.jar"), cache), "does not exist");
        assertFalse(InstalledJarHealer.isAllowed(own, Files.createDirectories(dir.resolve("a.jar")), cache), "a directory");
        assertFalse(InstalledJarHealer.isAllowed(own, Files.writeString(dir.resolve("notes.txt"), "y"), cache), "not a jar");
        assertFalse(InstalledJarHealer.isAllowed(own, own, cache), "itself");
    }

    /** The guard that stops a .deb's jar from being rewritten behind dpkg's back. */
    @Test
    void refusesAPackageOwnedTarget(@TempDir Path dir) throws Exception {
        Path cache = cache(dir);
        Path own = Files.writeString(cache.resolve(OWN_NAME), "x");
        Path target = Files.writeString(dir.resolve("snaken3st-bootstrap.jar"), "y");

        assertTrue(InstalledJarHealer.isAllowed(own, target, cache));
        // Read-only is what BootstrapMain.isPackageManaged treats as someone else's to manage.
        if (target.toFile().setWritable(false) && !Files.isWritable(target)) {
            assertFalse(InstalledJarHealer.isAllowed(own, target, cache));
        }
    }

    @Test
    void doesNothingWithoutTheProperty(@TempDir Path dir) throws Exception {
        String previous = System.getProperty(InstalledJarHealer.PROPERTY);
        System.clearProperty(InstalledJarHealer.PROPERTY);
        try {
            Path cache = cache(dir);
            Path own = Files.writeString(cache.resolve(OWN_NAME), "x");
            Path target = Files.writeString(dir.resolve("snaken3st-bootstrap.jar"), "old");

            InstalledJarHealer.startIfRequested(new mc.snakenest.launcher.util.AppDirs(dir), own);
            Thread.sleep(50);

            assertEquals("old", Files.readString(target));
        } finally {
            if (previous != null) {
                System.setProperty(InstalledJarHealer.PROPERTY, previous);
            }
        }
    }
}
