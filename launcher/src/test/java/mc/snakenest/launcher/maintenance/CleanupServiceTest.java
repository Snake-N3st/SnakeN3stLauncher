package mc.snakenest.launcher.maintenance;

import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanupServiceTest {

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void deletesLauncherLogsCachesAndPerInstanceDiagnostics(@TempDir Path root) throws Exception {
        AppDirs dirs = new AppDirs(root);
        write(dirs.logs().resolve("launcher-0.log"), "log line");
        write(dirs.cacheLauncher().resolve("1.0.jar"), "cached jar");
        Path instance = dirs.instance("aventure-ultime");
        write(instance.resolve("logs").resolve("latest.log"), "mc log");
        write(instance.resolve("crash-reports").resolve("crash-2026.txt"), "crash");

        CleanupService.Result result = new CleanupService(dirs).cleanEverything();

        assertEquals(4, result.filesDeleted());
        assertFalse(Files.exists(dirs.logs().resolve("launcher-0.log")));
        assertFalse(Files.exists(dirs.cacheLauncher().resolve("1.0.jar")));
        assertFalse(Files.exists(instance.resolve("logs").resolve("latest.log")));
        assertFalse(Files.exists(instance.resolve("crash-reports").resolve("crash-2026.txt")));
    }

    @Test
    void neverTouchesModsConfigsOrWorlds(@TempDir Path root) throws Exception {
        AppDirs dirs = new AppDirs(root);
        Path instance = dirs.instance("aventure-ultime");
        Path mod = instance.resolve("mods").resolve("cool-mod.jar");
        Path config = instance.resolve("config").resolve("mod.cfg");
        Path world = instance.resolve("saves").resolve("my-world").resolve("level.dat");
        write(mod, "mod");
        write(config, "config");
        write(world, "world");

        new CleanupService(dirs).cleanEverything();

        assertTrue(Files.exists(mod), "a mod must never be deleted by a cleanup");
        assertTrue(Files.exists(config), "a config must never be deleted by a cleanup");
        assertTrue(Files.exists(world), "a world must never be deleted by a cleanup");
    }

    @Test
    void neverTouchesTheSessionOrPreferences(@TempDir Path root) throws Exception {
        AppDirs dirs = new AppDirs(root);
        write(dirs.secure().resolve("key.bin"), "encrypted token");
        write(dirs.secure().resolve("pin.json"), "{}");
        write(dirs.configFile(), "{}");
        write(dirs.modpackSettingsFile("aventure-ultime"), "{}");

        new CleanupService(dirs).cleanEverything();

        assertTrue(Files.exists(dirs.secure().resolve("key.bin")));
        assertTrue(Files.exists(dirs.secure().resolve("pin.json")));
        assertTrue(Files.exists(dirs.configFile()));
        assertTrue(Files.exists(dirs.modpackSettingsFile("aventure-ultime")));
    }

    @Test
    void neverTouchesTheSharedPools(@TempDir Path root) throws Exception {
        // Deleting a pooled file frees nothing while instances still hardlink it, so it would be
        // a pure loss - see the class Javadoc.
        AppDirs dirs = new AppDirs(root);
        Path pooledAsset = dirs.sharedAssetsPool().resolve("objects").resolve("ab").resolve("abc123");
        Path pooledLibrary = dirs.sharedLibrariesPool().resolve("libraries").resolve("lib.jar");
        write(pooledAsset, "asset");
        write(pooledLibrary, "lib");

        new CleanupService(dirs).cleanEverything();

        assertTrue(Files.exists(pooledAsset));
        assertTrue(Files.exists(pooledLibrary));
    }

    @Test
    void neverTouchesEncryptedSensitiveFiles(@TempDir Path root) throws Exception {
        AppDirs dirs = new AppDirs(root);
        Path sealed = dirs.instance("aventure-ultime").resolve("config").resolve("secret.json.sn3enc");
        write(sealed, "ciphertext");

        new CleanupService(dirs).cleanEverything();

        assertTrue(Files.exists(sealed), "encrypted data is data, not a cache");
    }

    @Test
    void previewCountsExactlyWhatCleanupWouldDelete(@TempDir Path root) throws Exception {
        AppDirs dirs = new AppDirs(root);
        write(dirs.logs().resolve("launcher-0.log"), "12345");
        write(dirs.instance("pack").resolve("crash-reports").resolve("crash.txt"), "1234567890");
        write(dirs.instance("pack").resolve("mods").resolve("keep.jar"), "not counted");

        CleanupService service = new CleanupService(dirs);
        CleanupService.Result preview = service.preview();
        CleanupService.Result actual = service.cleanEverything();

        assertEquals(2, preview.filesDeleted());
        assertEquals(15, preview.bytesFreed());
        assertEquals(preview.filesDeleted(), actual.filesDeleted());
        assertEquals(preview.bytesFreed(), actual.bytesFreed());
    }

    @Test
    void keepsTheLogsDirectoryItselfSoTheNextWriteStillWorks(@TempDir Path root) throws Exception {
        AppDirs dirs = new AppDirs(root);
        write(dirs.logs().resolve("launcher-0.log"), "log");

        new CleanupService(dirs).cleanEverything();

        assertTrue(Files.isDirectory(dirs.logs()), "nothing re-creates this on demand");
    }

    @Test
    void isANoOpOnAFreshInstall(@TempDir Path root) {
        CleanupService.Result result = new CleanupService(new AppDirs(root)).cleanEverything();

        assertEquals(0, result.filesDeleted());
        assertEquals(0, result.bytesFreed());
    }
}
