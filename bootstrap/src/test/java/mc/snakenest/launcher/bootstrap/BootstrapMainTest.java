package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises BootstrapMain's private helpers via reflection - it has no public API of its own, it's an entry point. */
class BootstrapMainTest {

    @Test
    void pruneOldLauncherJarsRemovesEveryOtherCachedJarButKeepsTheCurrentOneAndNonJarFiles(@TempDir Path tempDir) throws Exception {
        AppDirs dirs = new AppDirs(tempDir);
        Path cacheLauncher = dirs.cacheLauncher();
        Files.createDirectories(cacheLauncher);

        Path old1 = cacheLauncher.resolve("1.0.0.jar");
        Path old2 = cacheLauncher.resolve("1.1.0.jar");
        Path current = cacheLauncher.resolve("1.2.0.jar");
        Path notAJar = cacheLauncher.resolve("readme.txt");
        Files.writeString(old1, "old1");
        Files.writeString(old2, "old2");
        Files.writeString(current, "current");
        Files.writeString(notAJar, "not a jar");

        invokePruneOldLauncherJars(dirs, current);

        assertFalse(Files.exists(old1));
        assertFalse(Files.exists(old2));
        assertTrue(Files.exists(current));
        assertTrue(Files.exists(notAJar));
    }

    @Test
    void pruneOldLauncherJarsIsANoOpWhenTheCacheDirectoryDoesNotExistYet(@TempDir Path tempDir) throws Exception {
        AppDirs dirs = new AppDirs(tempDir);

        // Must not throw even though dirs.cacheLauncher() was never created (e.g. a fresh
        // install's very first launcher download failed before pruning ever got a chance to run).
        invokePruneOldLauncherJars(dirs, dirs.cacheLauncher().resolve("1.0.0.jar"));
    }

    private static void invokePruneOldLauncherJars(AppDirs dirs, Path keep) throws Exception {
        // Path... is a Path[] parameter at the bytecode level (varargs are erased to an array) -
        // getDeclaredMethod needs the array type, and invoke() needs the varargs wrapped in an
        // Object[] of length 2 so reflection doesn't itself try to spread a bare Path[] across
        // the (AppDirs, Path[]) parameter list.
        Method prune = BootstrapMain.class.getDeclaredMethod("pruneOldLauncherJars", AppDirs.class, Path[].class);
        prune.setAccessible(true);
        prune.invoke(null, dirs, (Object) new Path[] {keep});
    }
}
