package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.LauncherAuthApiClient;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.crypto.EncryptedFileKeyStorage;
import mc.snakenest.launcher.crypto.KeyStorage;
import mc.snakenest.launcher.game.GameInstallService;
import mc.snakenest.launcher.game.GameLaunchService;
import mc.snakenest.launcher.game.flowupdater.FlowUpdaterGameInstallService;
import mc.snakenest.launcher.game.openlauncherlib.OpenLauncherLibGameLaunchService;
import mc.snakenest.launcher.modpack.ModpackApiClient;
import mc.snakenest.launcher.news.NewsApiClient;
import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.ui.ThemeController;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.Log;

import java.net.URI;

/**
 * Composition root: builds every dependency and hands them to
 * {@link LauncherApp}. Deliberately holds no logic of its own beyond
 * reading the JVM properties the bootstrap (or a developer, by hand)
 * sets - {@code sn3.baseUrl}/{@code sn3.clientId} directly below, plus
 * {@code sn3.dataDir} (optional, overrides where all launcher data lives on
 * disk - see {@link mc.snakenest.launcher.util.AppDirs}) read transitively
 * via {@link AppDirs#AppDirs()}.
 * No {@code .clientId}-resource fallback here (unlike {@code bootstrap.ClientIds}) - this
 * module must never depend on {@code bootstrap}'s package, and in the real deployment flow
 * {@code launcher} is always spawned BY {@code bootstrap} with {@code -Dsn3.clientId=...}
 * already resolved and forwarded, so this only ever matters when running this jar directly
 * (dev/testing), where passing the property by hand is fine.
 */
public final class Main {

    private static final String DEFAULT_BASE_URL = "https://snake-n3st.fr";

    public static void main(String[] args) {
        AppDirs dirs = new AppDirs();
        Log.initialize(dirs);

        String clientId = System.getProperty("sn3.clientId");
        if (clientId == null || clientId.isBlank()) {
            System.err.println("Missing required -Dsn3.clientId system property.");
            Log.error(Main.class, "Missing required -Dsn3.clientId system property", null);
            System.exit(1);
            return;
        }
        URI baseUrl = URI.create(System.getProperty("sn3.baseUrl", DEFAULT_BASE_URL));

        ConfigStore configStore = new ConfigStore(dirs);
        ThemeController themeController = new ThemeController(configStore);
        themeController.applyStartupTheme();

        HttpJsonClient http = new HttpJsonClient();
        LauncherAuthApiClient authApi = new LauncherAuthApiClient(http, baseUrl);
        ModpackApiClient modpackApi = new ModpackApiClient(http, baseUrl);
        NewsApiClient newsApi = new NewsApiClient(http, baseUrl);
        KeyStorage keyStorage = new EncryptedFileKeyStorage(dirs);
        GameInstallService gameInstallService = new FlowUpdaterGameInstallService();
        GameLaunchService gameLaunchService = new OpenLauncherLibGameLaunchService();

        LauncherApp app = new LauncherApp(dirs, baseUrl, clientId, configStore, themeController,
                authApi, modpackApi, newsApi, keyStorage, gameInstallService, gameLaunchService);

        // Not wrapped in invokeLater: start() fetches client/player branding over the
        // network before constructing any window, and must do so off the EDT to avoid
        // freezing it - see LauncherApp#start's Javadoc.
        app.start();
    }
}
