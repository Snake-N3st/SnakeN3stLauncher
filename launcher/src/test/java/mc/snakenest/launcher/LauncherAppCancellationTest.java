package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.LauncherAuthApiClient;
import mc.snakenest.launcher.bugreport.BugReportApiClient;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.crypto.KeyStorage;
import mc.snakenest.launcher.crypto.PinProtection;
import mc.snakenest.launcher.game.GameInstallException;
import mc.snakenest.launcher.game.GameInstallService;
import mc.snakenest.launcher.game.GameLaunchService;
import mc.snakenest.launcher.modpack.ModpackApiClient;
import mc.snakenest.launcher.news.NewsApiClient;
import mc.snakenest.launcher.ui.ThemeController;
import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InterruptedIOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Regression test: clicking "Annuler" during the mod loader install step used to surface as a red
 * "Could not install ..." error instead of the intended quiet "Annulé.", because
 * {@code FlowUpdaterGameInstallService#install} wraps every failure - including the real
 * {@link InterruptedException} from the cancelled install {@code Future} - into one
 * {@code GameInstallException}, and {@code isCancellation} never unwrapped it. See
 * {@code LauncherApp#isCancellation}'s own Javadoc for the full story.
 */
class LauncherAppCancellationTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private LauncherApp newLauncherApp(Path dataDir) {
        AppDirs dirs = new AppDirs(dataDir);
        ConfigStore configStore = new ConfigStore(dirs);
        return new LauncherApp(dirs, URI.create("http://127.0.0.1"), "test-client-id", configStore,
                new ThemeController(configStore), mock(LauncherAuthApiClient.class), mock(ModpackApiClient.class),
                mock(NewsApiClient.class), mock(KeyStorage.class), mock(GameInstallService.class), mock(GameLaunchService.class),
                new PinProtection(dirs), null, mock(BugReportApiClient.class));
    }

    private static boolean isCancellation(LauncherApp app, Exception e) throws Exception {
        Method method = LauncherApp.class.getDeclaredMethod("isCancellation", Exception.class);
        method.setAccessible(true);
        return (boolean) method.invoke(app, e);
    }

    @Test
    void recognizesInterruptedExceptionWrappedInsideGameInstallException(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        GameInstallException wrapped = new GameInstallException("Could not install 1.20.6 (FABRIC)", new InterruptedException());

        assertTrue(isCancellation(app, wrapped));
    }

    @Test
    void recognizesInterruptedIOExceptionDeepInTheCauseChain(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        InterruptedIOException io = new InterruptedIOException();
        GameInstallException wrapped = new GameInstallException("Could not install 1.20.6 (FABRIC)", new RuntimeException(io));

        assertTrue(isCancellation(app, wrapped));
    }

    @Test
    void doesNotTreatARealFailureAsACancellation(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        GameInstallException real = new GameInstallException("Could not install 1.20.6 (FABRIC)", new java.io.IOException("HTTP 503"));

        assertFalse(isCancellation(app, real));
    }
}
