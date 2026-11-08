package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.LauncherAuthApiClient;
import mc.snakenest.launcher.bugreport.BugReportApiClient;
import mc.snakenest.launcher.bugreport.CrashReportHandler;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.crypto.EncryptedFileKeyStorage;
import mc.snakenest.launcher.crypto.KeyStorage;
import mc.snakenest.launcher.crypto.KeyStorageException;
import mc.snakenest.launcher.crypto.PinProtection;
import mc.snakenest.launcher.game.GameInstallService;
import mc.snakenest.launcher.game.GameLaunchService;
import mc.snakenest.launcher.game.flowupdater.FlowUpdaterGameInstallService;
import mc.snakenest.launcher.game.openlauncherlib.OpenLauncherLibGameLaunchService;
import mc.snakenest.launcher.modpack.ModpackApiClient;
import mc.snakenest.launcher.modpack.signing.ModpackManifestVerifier;
import mc.snakenest.launcher.modpack.signing.ModpackSigningKeys;
import mc.snakenest.launcher.modpack.signing.ModpackTrustState;
import mc.snakenest.launcher.news.NewsApiClient;
import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.LogoCache;
import mc.snakenest.launcher.ui.ThemeController;
import mc.snakenest.launcher.ui.pin.PinUnlockDialog;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.Log;

import javax.crypto.SecretKey;
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

        HttpJsonClient http = new HttpJsonClient();
        // Installed before anything else is built, so a crash during the rest of this method (or
        // any point afterward) is still caught - see CrashReportHandler's own Javadoc for why the
        // EDT needs a second, separate hook alongside the JVM's normal default handler.
        BugReportApiClient bugReportApi = new BugReportApiClient(http, baseUrl, clientId);
        CrashReportHandler.install(bugReportApi);

        ConfigStore configStore = new ConfigStore(dirs);
        ThemeController themeController = new ThemeController(configStore);
        themeController.applyStartupTheme();

        LauncherAuthApiClient authApi = new LauncherAuthApiClient(http, baseUrl);
        // Verifies every manifest against the root key the site baked into the bootstrap (none pinned = nothing checked).
        ModpackManifestVerifier manifestVerifier = new ModpackManifestVerifier(ModpackSigningKeys.fromSystemProperties(),
                ModpackTrustState.load(dirs.root().resolve(ModpackTrustState.FILE_NAME)));
        ModpackApiClient modpackApi = new ModpackApiClient(http, baseUrl, manifestVerifier);
        NewsApiClient newsApi = new NewsApiClient(http, baseUrl);
        // Blocks on the PIN gate first when one is set, so nothing else is even constructed
        // until the token is actually decryptable (or the player has reset it) - see
        // unlockWithPinIfEnabled.
        PinProtection pinProtection = new PinProtection(dirs);
        SecretKey pinKey = unlockWithPinIfEnabled(dirs, pinProtection);

        KeyStorage keyStorage = pinKey != null
                ? EncryptedFileKeyStorage.withPin(dirs, pinKey)
                : new EncryptedFileKeyStorage(dirs);
        GameInstallService gameInstallService = new FlowUpdaterGameInstallService();
        GameLaunchService gameLaunchService = new OpenLauncherLibGameLaunchService();

        LauncherApp app = new LauncherApp(dirs, baseUrl, clientId, configStore, themeController,
                authApi, modpackApi, newsApi, keyStorage, gameInstallService, gameLaunchService,
                pinProtection, pinKey, bugReportApi);

        // Not wrapped in invokeLater: start() fetches client/player branding over the
        // network before constructing any window, and must do so off the EDT to avoid
        // freezing it - see LauncherApp#start's Javadoc.
        app.start();
    }

    /**
     * Shows the startup PIN gate when one is set, and returns the key it derived.
     *
     * <p>Blocks this thread until the player answers: {@code main} is deliberately not on the EDT
     * (see {@link LauncherApp#start()}), and everything after this point depends on the answer -
     * there's nothing sensible to do concurrently while the player types their code.
     *
     * @return the derived key, or {@code null} when there's no PIN set (or the player reset it),
     *         meaning the caller should fall back to the on-disk wrapping key
     */
    private static SecretKey unlockWithPinIfEnabled(AppDirs dirs, PinProtection pinProtection) {
        if (!pinProtection.isEnabled()) {
            return null;
        }

        // Holder rather than a return value: the verifier runs on the EDT inside the window, and
        // the key it derives has to survive back out here.
        SecretKey[] unlocked = new SecretKey[1];

        // Called straight from this (non-EDT) thread, not wrapped in invokeAndWait: the PIN window
        // is a JFrame now, so it no longer blocks by being modal - showAndAwait does the waiting
        // itself, and doing that on the EDT would deadlock it.
        PinUnlockDialog.Outcome outcome = PinUnlockDialog.showAndAwait(pin -> {
            SecretKey candidate = deriveAndVerify(dirs, pinProtection, pin);
            if (candidate == null) {
                return false;
            }
            unlocked[0] = candidate;
            return true;
        }, new LogoCache(dirs).cached());

        return switch (outcome) {
            case UNLOCKED -> unlocked[0];
            case FORGOTTEN -> {
                PinReset.wipe(dirs, pinProtection);
                // No PIN anymore and no token left - the launcher carries on to the login screen.
                yield null;
            }
            case CANCELLED -> {
                // Closing the gate means "I don't want to unlock", and there is nothing to show
                // without a decrypted token, so quitting is the only honest outcome.
                Log.info(Main.class, "PIN unlock cancelled, exiting.");
                System.exit(0);
                yield null;
            }
        };
    }

    /**
     * Derives the key for {@code pin} and proves it by actually decrypting the stored token - a
     * wrong PIN derives a perfectly valid-looking key that only fails at that point (see
     * {@code crypto.PinProtection}).
     *
     * @return the key when it really did decrypt the token, {@code null} when the PIN was wrong
     */
    private static SecretKey deriveAndVerify(AppDirs dirs, PinProtection pinProtection, char[] pin) {
        SecretKey candidate;
        try {
            candidate = pinProtection.deriveExisting(pin);
        } catch (KeyStorageException e) {
            Log.warn(Main.class, "Could not derive a key from the entered PIN: " + e.getMessage());
            return null;
        }

        try {
            // Not throwing is the proof. An empty result (no token stored at all - e.g. the PIN was
            // set, then the player logged out) says nothing against the PIN either, so it's
            // accepted too and the login screen takes over.
            EncryptedFileKeyStorage.withPin(dirs, candidate).load();
            return candidate;
        } catch (KeyStorageException e) {
            // The expected path for a wrong PIN: the GCM tag didn't authenticate.
            return null;
        }
    }
}
