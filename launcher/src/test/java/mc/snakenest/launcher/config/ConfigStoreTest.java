package mc.snakenest.launcher.config;

import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigStoreTest {

    @Test
    void loadReturnsDefaultsWhenFileMissing(@TempDir Path tempDir) {
        ConfigStore store = new ConfigStore(new AppDirs(tempDir));

        LauncherConfig config = store.load();

        assertNotNull(config);
        assertEquals(Theme.DARK, config.theme());
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path tempDir) {
        AppDirs dirs = new AppDirs(tempDir);
        ConfigStore store = new ConfigStore(dirs);

        LauncherConfig config = new LauncherConfig();
        config.setTheme(Theme.LIGHT);
        store.save(config);

        LauncherConfig reloaded = store.load();

        assertEquals(Theme.LIGHT, reloaded.theme());
    }
}
