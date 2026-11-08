package mc.snakenest.launcher.game.openlauncherlib;

import mc.snakenest.launcher.game.LaunchRequest;
import mc.snakenest.launcher.game.ModLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LocalLaunchFilesTest {
    @TempDir Path instance;
    private LaunchRequest request() {
        return new LaunchRequest("CachedPlayer", UUID.randomUUID(), instance, "1.20.1", ModLoader.VANILLA,
                null, 512, "", null, "test-seed-not-a-credential", true);
    }
    private void vanilla(String libraries) throws Exception {
        Files.writeString(instance.resolve("client.jar"), "installed game bytes");
        Path index = instance.resolve("assets/indexes/test.json");
        Files.createDirectories(index.getParent()); Files.writeString(index, "{\"objects\":{}}");
        Files.writeString(instance.resolve("1.20.1.json"), "{\"id\":\"1.20.1\",\"mainClass\":\"example.Main\",\"minecraftArguments\":\"--username ${auth_player_name}\",\"assetIndex\":{\"id\":\"test\"},\"libraries\":" + libraries + "}");
    }

    @Test void preparesInstalledGameFromLocalMetadataWithoutStartingAProcessOrDownloadingAnything() throws Exception {
        vanilla("[]");
        List<String> command = new OpenLauncherLibGameLaunchService().prepareCommand(request());
        assertTrue(command.contains("example.Main"));
        assertTrue(command.contains("CachedPlayer"));
        assertTrue(command.contains("-Dsn3.token=test-seed-not-a-credential"));
        assertTrue(command.contains(instance.resolve("client.jar").toString()));
    }
    @Test void absentGameJarFailsRatherThanInvokingAnInstaller() throws Exception {
        vanilla("[]"); Files.delete(instance.resolve("client.jar"));
        assertThrows(IOException.class, () -> LocalLaunchFiles.validate(request()));
        assertFalse(Files.exists(instance.resolve("client.jar")));
    }
    @Test void missingLibraryFailsLocallyAndExistingLibraryAllowsLaunch() throws Exception {
        vanilla("[{\"name\":\"example:lib:1\",\"downloads\":{\"artifact\":{\"path\":\"example/lib/1/lib-1.jar\",\"url\":\"https://unreachable.invalid/library.jar\"}}}]");
        assertThrows(IOException.class, () -> LocalLaunchFiles.validate(request()));
        Path library = instance.resolve("libraries/example/lib/1/lib-1.jar");
        Files.createDirectories(library.getParent()); Files.writeString(library, "installed library");
        assertDoesNotThrow(() -> LocalLaunchFiles.validate(request()));
    }
    @Test void missingAssetFailsInsteadOfDownloadingItsRemoteObject() throws Exception {
        vanilla("[]");
        Files.writeString(instance.resolve("1.20.1.json"), "{\"id\":\"1.20.1\",\"mainClass\":\"example.Main\",\"libraries\":[],\"assetIndex\":{\"id\":\"test\",\"url\":\"https://unreachable.invalid/index\"}}");
        Path index = instance.resolve("assets/indexes/test.json"); Files.createDirectories(index.getParent());
        Files.writeString(index, "{\"objects\":{\"sound\":{\"hash\":\"" + "a".repeat(40) + "\"}}}");
        assertThrows(IOException.class, () -> LocalLaunchFiles.validate(request()));
        Path object = instance.resolve("assets/objects/aa/" + "a".repeat(40)); Files.createDirectories(object.getParent()); Files.writeString(object, "sound");
        assertDoesNotThrow(() -> LocalLaunchFiles.validate(request()));
    }
    @Test void unsafeLibraryPathsAreRejected() throws Exception {
        vanilla("[{\"name\":\"example:lib:1\",\"downloads\":{\"artifact\":{\"path\":\"../../outside.jar\"}}}]");
        assertThrows(IOException.class, () -> LocalLaunchFiles.validate(request()));
    }
}
