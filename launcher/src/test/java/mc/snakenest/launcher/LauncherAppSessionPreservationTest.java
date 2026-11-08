package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.*;
import mc.snakenest.launcher.bugreport.BugReportApiClient;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.crypto.*;
import mc.snakenest.launcher.game.*;
import mc.snakenest.launcher.modpack.ModpackApiClient;
import mc.snakenest.launcher.news.NewsApiClient;
import mc.snakenest.launcher.ui.ThemeController;
import mc.snakenest.launcher.util.AppDirs;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LauncherAppSessionPreservationTest {
    @ParameterizedTest @ValueSource(ints = {401,403})
    void rejectingOneRequestDoesNotDestroyThisDevicesCredential(int status,@TempDir Path root) throws Exception {
        AppDirs dirs=new AppDirs(root);
        ConfigStore config=new ConfigStore(dirs);
        var saved=config.load(); saved.setPlayerId(7L); config.save(saved);
        KeyStorage keys=new EncryptedFileKeyStorage(dirs);
        Ed25519KeyPair key=Ed25519KeyPair.fromSeed(new byte[32]); keys.save(key);
        LauncherAuthApiClient auth=mock(LauncherAuthApiClient.class);
        PlayerSession session=new PlayerSession(key,7L,"client");
        when(auth.fetchPlayerInfo(session)).thenThrow(new LauncherApiException("Invalid or expired signature",status));
        LauncherApp app=new LauncherApp(dirs,URI.create("https://example.test"),"client",config,
                mock(ThemeController.class),auth,mock(ModpackApiClient.class),mock(NewsApiClient.class),keys,
                mock(GameInstallService.class),mock(GameLaunchService.class),mock(PinProtection.class),null,mock(BugReportApiClient.class));
        Field sessionField=LauncherApp.class.getDeclaredField("session"); sessionField.setAccessible(true); sessionField.set(app,session);
        Field executor=LauncherApp.class.getDeclaredField("backgroundExecutor"); executor.setAccessible(true);
        try {
            Method fetch=LauncherApp.class.getDeclaredMethod("fetchPlayerInfoBlocking"); fetch.setAccessible(true);
            assertEquals(false,fetch.invoke(app));
            Method suspend=LauncherApp.class.getDeclaredMethod("suspendRejectedSession"); suspend.setAccessible(true); suspend.invoke(app);
            assertNull(sessionField.get(app));
            assertEquals(7L,config.load().playerId());
            assertEquals(key.publicKeyHex(),keys.load().orElseThrow().publicKeyHex());
            verify(auth,never()).revokeKey(any());
        } finally { ((ExecutorService)executor.get(app)).shutdownNow(); }
    }
}
