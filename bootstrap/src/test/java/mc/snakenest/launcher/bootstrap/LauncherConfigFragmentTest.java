package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherConfigFragmentTest {

    @Test
    void bothFieldsAreNullWhenTheConfigFileDoesNotExist(@TempDir Path tempDir) {
        LauncherConfigFragment config = LauncherConfigFragment.read(new AppDirs(tempDir));

        assertNull(config.channel);
        assertNull(config.playerId);
        assertFalse(config.hasKnownPlayer());
    }

    @Test
    void readsChannelAndPlayerIdFromTheRealLauncherConfigShape(@TempDir Path tempDir) throws Exception {
        AppDirs dirs = new AppDirs(tempDir);
        // The real config.json (config.LauncherConfig, launcher module) has many more fields -
        // this confirms the minimal fragment ignores everything it doesn't need rather than
        // failing on the extra keys.
        Files.writeString(dirs.configFile(), """
                {"theme":"DARK","playerId":42,"discordEnabled":true,"channel":"beta","assetStorageMode":"LAUNCHER_SHARED"}
                """);

        LauncherConfigFragment config = LauncherConfigFragment.read(dirs);

        assertEquals("beta", config.channel);
        assertEquals(42L, config.playerId);
        assertTrue(config.hasKnownPlayer());
    }

    @Test
    void bothFieldsAreNullWhenNeitherWasEverSaved(@TempDir Path tempDir) throws Exception {
        AppDirs dirs = new AppDirs(tempDir);
        Files.writeString(dirs.configFile(), "{\"theme\":\"DARK\"}");

        LauncherConfigFragment config = LauncherConfigFragment.read(dirs);

        assertNull(config.channel);
        assertNull(config.playerId);
    }

    @Test
    void malformedJsonIsTreatedAsNothingSaved(@TempDir Path tempDir) throws Exception {
        AppDirs dirs = new AppDirs(tempDir);
        Files.writeString(dirs.configFile(), "{ not json at all");

        LauncherConfigFragment config = LauncherConfigFragment.read(dirs);

        assertNull(config.channel);
        assertNull(config.playerId);
    }
}
