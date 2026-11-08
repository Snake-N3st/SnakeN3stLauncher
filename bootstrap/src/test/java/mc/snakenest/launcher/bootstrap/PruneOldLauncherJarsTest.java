package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression cover for a crash that reached production in bootstrap 2.1.0.
 *
 * <p>{@code LastLaunchedJar.find()} returns null whenever no launcher has been recorded yet - a
 * brand new data directory, which is what a first run under a fresh account has. That null was
 * passed straight into {@code List.of(...)}, which rejects null elements, so the whole startup died
 * with a NullPointerException during what is only ever a best-effort cleanup of old jars.
 */
class PruneOldLauncherJarsTest {

    private static AppDirs dirsWithJars(Path root, String... versions) throws Exception {
        AppDirs dirs = new AppDirs(root);
        Files.createDirectories(dirs.cacheLauncher());

        for (String version : versions) {
            Files.writeString(dirs.cacheLauncher().resolve(version + ".jar"), "jar " + version);
        }

        return dirs;
    }

    /** The exact shape of the production crash: a null among the paths to keep. */
    @Test
    void survivesANullAmongThePathsToKeep(@TempDir Path root) throws Exception {
        AppDirs dirs = dirsWithJars(root, "2.1.1", "2.2.0");
        Path keep = dirs.cacheLauncher().resolve("2.2.0.jar");

        BootstrapMain.pruneOldLauncherJars(dirs, keep, null);

        assertTrue(Files.exists(keep), "the jar to keep must survive");
        assertFalse(Files.exists(dirs.cacheLauncher().resolve("2.1.1.jar")), "the old jar must be pruned");
    }

    @Test
    void keepsEveryNonNullPathItIsGiven(@TempDir Path root) throws Exception {
        AppDirs dirs = dirsWithJars(root, "2.1.0", "2.1.1", "2.2.0");
        Path current = dirs.cacheLauncher().resolve("2.2.0.jar");
        Path lastLaunched = dirs.cacheLauncher().resolve("2.1.1.jar");

        BootstrapMain.pruneOldLauncherJars(dirs, current, lastLaunched);

        assertTrue(Files.exists(current));
        assertTrue(Files.exists(lastLaunched), "the fallback jar must not be pruned away");
        assertFalse(Files.exists(dirs.cacheLauncher().resolve("2.1.0.jar")));
    }

    @Test
    void leavesNonJarFilesAlone(@TempDir Path root) throws Exception {
        AppDirs dirs = dirsWithJars(root, "2.2.0");
        Path marker = dirs.cacheLauncher().resolve("last-launched.txt");
        Files.writeString(marker, "2.2.0");

        BootstrapMain.pruneOldLauncherJars(dirs, dirs.cacheLauncher().resolve("2.2.0.jar"), null);

        assertTrue(Files.exists(marker), "the last-launched marker is not a jar and must be kept");
    }

    @Test
    void doesNothingWhenTheCacheDirectoryIsAbsent(@TempDir Path root) {
        BootstrapMain.pruneOldLauncherJars(new AppDirs(root), (Path) null);
    }
}
