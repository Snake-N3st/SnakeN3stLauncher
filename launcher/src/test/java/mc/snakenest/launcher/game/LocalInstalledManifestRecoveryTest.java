package mc.snakenest.launcher.game;

import mc.snakenest.launcher.modpack.LocalManifestStore;
import mc.snakenest.launcher.modpack.StoredManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LocalInstalledManifestRecoveryTest {
    @TempDir Path instance;
    private void installed() throws Exception {
        Files.writeString(instance.resolve("client.jar"), "game");
        new LocalManifestStore().save(instance, new StoredManifest("pack-1", Map.of("mods/example.jar", "a".repeat(64))));
        Files.writeString(instance.resolve("1.20.1.json"), "{\"id\":\"1.20.1\",\"mainClass\":\"net.minecraft.client.main.Main\",\"libraries\":[]}");
    }
    @Test void migratesAnExistingVanillaInstallationWithoutAnyWebsiteCache() throws Exception {
        installed(); var manifest = LocalInstalledManifestRecovery.recover(instance).orElseThrow();
        assertEquals("1.20.1", manifest.mcVersion()); assertEquals("vanilla", manifest.loader());
        assertEquals("pack-1", manifest.version()); assertEquals(1, manifest.files().size());
    }
    @Test void recoversFabricLoaderAndVersionFromInstalledLoaderJson() throws Exception {
        installed();
        Path loader = instance.resolve("fabric-loader-0.16.9-1.20.1.json");
        Files.writeString(loader, "{\"id\":\"fabric-loader-0.16.9-1.20.1\",\"inheritsFrom\":\"1.20.1\",\"mainClass\":\"net.fabricmc.loader.impl.launch.knot.KnotClient\",\"libraries\":[]}");
        // Vanilla JSON is often refreshed after an already-installed loader.
        Files.setLastModifiedTime(instance.resolve("1.20.1.json"), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()+1000));
        var manifest = LocalInstalledManifestRecovery.recover(instance).orElseThrow();
        assertEquals("fabric", manifest.loader()); assertEquals("0.16.9", manifest.loaderVersion());
    }
    @Test void cachedMetadataAndPartialDirectoriesAloneAreNotAnInstallation() throws Exception {
        installed(); Files.delete(instance.resolve("client.jar"));
        assertTrue(LocalInstalledManifestRecovery.recover(instance).isEmpty());
    }
}
