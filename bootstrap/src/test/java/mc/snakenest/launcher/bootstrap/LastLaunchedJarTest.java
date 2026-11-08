package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LastLaunchedJarTest {

    @Test
    void findReturnsNullWhenNothingWasEverRecorded(@TempDir Path tempDir) {
        assertNull(LastLaunchedJar.find(new AppDirs(tempDir)));
    }

    @Test
    void recordThenFindRoundTrips(@TempDir Path tempDir) throws Exception {
        AppDirs dirs = new AppDirs(tempDir);
        Files.createDirectories(dirs.cacheLauncher());
        Path jar = dirs.cacheLauncher().resolve("1.2.0.jar");
        Files.writeString(jar, "fake jar bytes");

        LastLaunchedJar.record(dirs, "1.2.0");

        assertEquals(jar, LastLaunchedJar.find(dirs));
    }

    @Test
    void findReturnsNullWhenTheRecordedJarIsNoLongerOnDisk(@TempDir Path tempDir) throws Exception {
        AppDirs dirs = new AppDirs(tempDir);
        Files.createDirectories(dirs.cacheLauncher());

        // Recorded, but the jar itself was pruned/deleted afterward - the marker file alone
        // pointing at a version isn't enough, the jar has to actually still be there.
        LastLaunchedJar.record(dirs, "1.2.0");

        assertNull(LastLaunchedJar.find(dirs));
    }
}
