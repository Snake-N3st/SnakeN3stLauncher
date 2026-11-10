package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.ClientInfo;
import mc.snakenest.launcher.auth.DeviceAuthService;
import mc.snakenest.launcher.auth.LauncherApiException;
import mc.snakenest.launcher.auth.LauncherAuthApiClient;
import mc.snakenest.launcher.auth.PlayerInfo;
import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.bugreport.BugReportApiClient;
import mc.snakenest.launcher.bugreport.BugReportType;
import mc.snakenest.launcher.config.AssetStorageMode;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.config.LaunchWindowAction;
import mc.snakenest.launcher.config.LauncherConfig;
import mc.snakenest.launcher.crypto.Ed25519KeyPair;
import mc.snakenest.launcher.crypto.EncryptedFileKeyStorage;
import mc.snakenest.launcher.crypto.KeyStorage;
import mc.snakenest.launcher.crypto.KeyStorageException;
import mc.snakenest.launcher.crypto.PinProtection;
import mc.snakenest.launcher.discord.DiscordPresenceService;
import mc.snakenest.launcher.game.GameInstallListener;
import mc.snakenest.launcher.game.GameInstallService;
import mc.snakenest.launcher.game.GameLaunchService;
import mc.snakenest.launcher.game.InstallRequest;
import mc.snakenest.launcher.game.InstallStep;
import mc.snakenest.launcher.game.JavaRuntimes;
import mc.snakenest.launcher.game.LaunchRequest;
import mc.snakenest.launcher.game.OfflineUuids;
import mc.snakenest.launcher.game.assets.SharedAssetsLinker;
import mc.snakenest.launcher.maintenance.CleanupService;
import mc.snakenest.launcher.modpack.FileMetas;
import mc.snakenest.launcher.modpack.InstanceActivity;
import mc.snakenest.launcher.modpack.InstanceState;
import mc.snakenest.launcher.modpack.InstanceStateRegistry;
import mc.snakenest.launcher.modpack.LocalManifestStore;
import mc.snakenest.launcher.modpack.ManifestDiffer;
import mc.snakenest.launcher.modpack.ModBlacklistCacheStore;
import mc.snakenest.launcher.modpack.ModBlacklistEntry;
import mc.snakenest.launcher.modpack.ModBlacklistedException;
import mc.snakenest.launcher.modpack.ModpackApiClient;
import mc.snakenest.launcher.modpack.ModpackManifest;
import mc.snakenest.launcher.modpack.ModpackSettings;
import mc.snakenest.launcher.modpack.ModpackSettingsStore;
import mc.snakenest.launcher.modpack.ModpackSummary;
import mc.snakenest.launcher.modpack.ModpackSyncEngine;
import mc.snakenest.launcher.modpack.SensitiveFileVault;
import mc.snakenest.launcher.modpack.StoredManifest;
import mc.snakenest.launcher.net.LogoCache;
import mc.snakenest.launcher.news.NewsApiClient;
import mc.snakenest.launcher.news.Post;
import mc.snakenest.launcher.ui.LauncherFrame;
import mc.snakenest.launcher.ui.LoginFrame;
import mc.snakenest.launcher.ui.NavTarget;
import mc.snakenest.launcher.ui.ThemeController;
import mc.snakenest.launcher.ui.account.AccountPopover;
import mc.snakenest.launcher.ui.common.RemoteImages;
import mc.snakenest.launcher.ui.modpack.ModpackDetailPage;
import mc.snakenest.launcher.ui.modpack.ModpackDetailViewModel;
import mc.snakenest.launcher.ui.modpack.ModpackListPage;
import mc.snakenest.launcher.ui.modpack.ModpackListViewModel;
import mc.snakenest.launcher.ui.modpack.ModpackSectionPage;
import mc.snakenest.launcher.ui.news.NewsDetailPage;
import mc.snakenest.launcher.ui.news.NewsListPage;
import mc.snakenest.launcher.ui.news.NewsListViewModel;
import mc.snakenest.launcher.ui.news.NewsSectionPage;
import mc.snakenest.launcher.ui.bugreport.CrashPromptDialog;
import mc.snakenest.launcher.ui.bugreport.ManualBugReportDialog;
import mc.snakenest.launcher.ui.pin.PinSetupDialog;
import mc.snakenest.launcher.ui.settings.SettingsPage;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.HumanSize;
import mc.snakenest.launcher.util.Log;
import mc.snakenest.launcher.util.Sha256;
import mc.snakenest.launcher.util.StartupSignal;

import javax.crypto.SecretKey;
import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * The application's orchestration: everything {@link Main} needs to do
 * beyond constructing dependencies. Not itself a page or a service - it
 * exists to keep {@code Main.main()} a one-screen composition root while
 * still having one clear place for "what happens when the user does X".
 */
final class LauncherApp {

    /**
     * Shown both after a failed migration and once per session when an install falls back - the
     * wording has to work in both, hence living here rather than being inlined twice.
     */
    private static final String HARDLINK_FALLBACK_MESSAGE =
            "Le partage des ressources vanilla n'est pas possible sur ce système\n"
                    + "(liens physiques indisponibles, souvent parce que les modpacks et le stockage\n"
                    + "partagé sont sur des disques différents).\n\n"
                    + "Chaque modpack garde donc sa propre copie, comme avec la troisième option.\n"
                    + "Tout fonctionne normalement, mais sans économie d'espace disque.";

    private final AppDirs dirs;
    private final URI baseUrl;
    private final String clientId;
    private final ConfigStore configStore;
    private final ThemeController themeController;
    private final LauncherAuthApiClient authApi;
    private final ModpackApiClient modpackApi;
    private final NewsApiClient newsApi;
    private final KeyStorage keyStorage;
    private final GameInstallService gameInstallService;
    private final GameLaunchService gameLaunchService;
    private final ModpackSettingsStore modpackSettingsStore;
    private final ModBlacklistCacheStore modBlacklistCache = new ModBlacklistCacheStore();
    private final SharedAssetsLinker assetsLinker;
    private final CleanupService cleanupService;
    private final BugReportApiClient bugReportApi;
    /** Guards the once-per-session hardlink-fallback warning - see {@link #notifyHardlinkFallback}. */
    private final java.util.concurrent.atomic.AtomicBoolean hardlinkFallbackReported = new java.util.concurrent.atomic.AtomicBoolean();
    private final PinProtection pinProtection;
    /**
     * The key derived from the PIN at startup, or {@code null} when no PIN is set. Held for the
     * whole session because it's needed again on every launch/exit to un/re-seal a modpack's
     * {@code .cryptlist} files - see {@link #launchGame}. Swapped (not just re-read) when the
     * player turns the PIN on or off mid-session, hence {@code volatile} rather than final.
     */
    private volatile SecretKey pinKey;
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "launcher-background");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean offlineMode;
    private volatile String lastRequestFailure;
    private volatile Set<String> removedPackSlugs = Set.of();
    private final mc.snakenest.launcher.modpack.LocalPackCatalogStore localCatalog;
    private PlayerSession session;
    private ClientInfo clientInfo;
    private final LogoCache logoCache;
    private final mc.snakenest.launcher.net.PackIconCache iconCache;

    private BufferedImage clientLogo;
    // Fetched once at login/startup (never on-demand when the account popover is opened - see ui/README.md).
    private PlayerInfo playerInfo;
    private BufferedImage playerAvatar;
    // Fetched once at startup (loadChannelsForSettings) and shared by the Settings page's own
    // channel picker and every modpack's "Gerer" dialog - volatile since it's written on
    // backgroundExecutor and read from the EDT whenever either picker opens, with no other
    // synchronization between the two.
    private volatile java.util.List<mc.snakenest.launcher.auth.ChannelInfo> availableChannels = java.util.List.of();
    private LauncherFrame shellFrame;
    /**
     * The login window, while it's up. Closed by {@link #showShell()} rather than by itself, so
     * there is never a moment with no displayable window at all - AWT shuts the EDT down in that
     * state, which used to kill the JVM (exit 0) mid-login while the player-info fetch was still
     * running on a daemon thread.
     */
    private LoginFrame loginFrame;

    /**
     * What every modpack instance is currently doing - installing, running, or being stopped -
     * tracked independently per slug. See {@link InstanceStateRegistry}'s own Javadoc for the bug
     * this replaced: a single shared field could only ever describe one modpack at a time, so
     * starting an install on a second modpack while a first one was still running silently broke
     * the first one's own Arreter/Annuler buttons and its status on a freshly (re)built page/card.
     */
    private final InstanceStateRegistry instanceStates = new InstanceStateRegistry();

    // The list currently shown, if any - lets card-state pushes (setCardBusy/Running/Installed)
    // reach the right ModpackCardView regardless of whether that modpack's detail page is also
    // open. Replaced wholesale on every (re)load, same "stale reference is a harmless no-op"
    // reasoning as currentDetailPage below.
    private volatile ModpackListViewModel currentListViewModel;
    // null whenever this client has no discord_app_id configured - Discord is then never touched at all.
    private volatile DiscordPresenceService discordPresence;

    LauncherApp(AppDirs dirs, URI baseUrl, String clientId, ConfigStore configStore, ThemeController themeController,
                LauncherAuthApiClient authApi, ModpackApiClient modpackApi, NewsApiClient newsApi, KeyStorage keyStorage,
                GameInstallService gameInstallService, GameLaunchService gameLaunchService,
                PinProtection pinProtection, SecretKey pinKey, BugReportApiClient bugReportApi) {
        this.pinProtection = pinProtection;
        this.pinKey = pinKey;
        this.dirs = dirs;
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.configStore = configStore;
        this.themeController = themeController;
        this.authApi = authApi;
        this.modpackApi = modpackApi;
        this.newsApi = newsApi;
        this.keyStorage = keyStorage;
        this.gameInstallService = gameInstallService;
        this.gameLaunchService = gameLaunchService;
        this.modpackSettingsStore = new ModpackSettingsStore(dirs);
        this.logoCache = new LogoCache(dirs);
        this.iconCache = new mc.snakenest.launcher.net.PackIconCache(dirs.cache().resolve("public-icons"), baseUrl.toString());
        this.localCatalog = new mc.snakenest.launcher.modpack.LocalPackCatalogStore(
                dirs.root().resolve("local-pack-catalog.json"), baseUrl.toString());
        this.assetsLinker = new SharedAssetsLinker(dirs);
        this.cleanupService = new CleanupService(dirs);
        this.bugReportApi = bugReportApi;

        // Covers every exit path (window close, System.exit, Ctrl+C), not just an explicit
        // logout - Discord Rich Presence should disappear whenever the launcher actually quits.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            DiscordPresenceService presence = discordPresence;
            if (presence != null) {
                presence.close();
            }
        }, "discord-presence-shutdown"));
    }

    /**
     * Called on the main thread (not the EDT) by {@link Main}, precisely so
     * the branding/player-info network calls below can block without
     * freezing a Swing window that doesn't exist yet - the login/shell
     * window is only constructed (on the EDT, via {@code invokeLater}) once
     * that data is already in hand, so it never shows a placeholder logo
     * before flashing to the real one.
     */
    void start() {
        resealSensitiveFiles();
        if (Boolean.getBoolean("sn3.offline")) {
            try { restoreOfflineIdentity(); SwingUtilities.invokeLater(this::showShell); }
            catch (Exception unavailable) { lastRequestFailure = unavailable.getMessage(); SwingUtilities.invokeLater(this::showLogin); }
            return;
        }
        fetchClientBrandingBlocking();

        Ed25519KeyPair storedKey = loadStoredKeyPair();
        Long storedPlayerId = configStore.load().playerId();

        if (storedKey != null && storedPlayerId != null) {
            session = new PlayerSession(storedKey, storedPlayerId, clientId);
            if (fetchPlayerInfoBlocking()) {
                SwingUtilities.invokeLater(this::showShell);
            } else {
                // A 403 also covers clock skew and replay: never destroy a device's saved
                // credential merely because its request was rejected.
                suspendRejectedSession();
                SwingUtilities.invokeLater(this::showLogin);
            }
        } else {
            SwingUtilities.invokeLater(this::showLogin);
        }
    }

    private Ed25519KeyPair loadStoredKeyPair() {
        try {
            return keyStorage.load().orElse(null);
        } catch (Exception e) {
            Log.warn(LauncherApp.class, "Could not load the stored key, falling back to login: " + e.getMessage());
            return null;
        }
    }

    // A slow/unresponsive server must not leave the launcher showing no window at all for as
    // long as HttpJsonClient's own (much longer, 30s) request timeout - bounding these two
    // startup calls keeps the worst case short, at the cost of a placeholder logo/no cached
    // player info if the server really is that slow (both already degrade gracefully).
    private static final java.time.Duration STARTUP_FETCH_TIMEOUT = java.time.Duration.ofSeconds(4);

    /** Best-effort; leaves {@link #clientInfo}/{@link #clientLogo} {@code null} on any failure or timeout. */
    private void fetchClientBrandingBlocking() {
        // Straight off disk, no network: the logo changes about once a year, so making every
        // start wait on a download for it was paying a round-trip on every launch for nothing.
        // The real check happens in refreshLogoInBackground() once a window is already up.
        clientLogo = logoCache.cached();

        try {
            clientInfo = runWithTimeout(() -> authApi.fetchClientInfo(clientId));
            connectDiscordPresence();
        } catch (Exception e) {
            Log.warn(LauncherApp.class, "Could not fetch client branding: " + e.getMessage());
        }
    }

    /**
     * Re-checks the logo against the site and swaps it in if it actually changed, long after the
     * startup path cares - deliberately started only once a window is on screen, so a slow or
     * unreachable server delays nothing a player can see.
     */
    private void refreshLogoInBackground() {
        if (offlineMode) return;
        if (clientInfo == null) {
            return;
        }

        Thread refresh = new Thread(() -> {
            BufferedImage updated = logoCache.refreshIfChanged(clientInfo.image());

            if (updated != null) {
                SwingUtilities.invokeLater(() -> applyClientLogo(updated));
            }
        }, "logo-refresh");

        // Daemon: this must never be the thread that keeps the JVM alive after the player quits.
        refresh.setDaemon(true);
        refresh.start();
    }

    /** Applies a freshly downloaded logo to every window currently showing the old one. */
    private void applyClientLogo(BufferedImage logo) {
        clientLogo = logo;

        if (shellFrame != null) {
            shellFrame.setLogo(logo);
            shellFrame.setIconImage(logo);
        }

        if (loginFrame != null) {
            loginFrame.setIconImage(logo);
        }
    }

    /**
     * Fire-and-forget: whether/how fast Discord connects must never affect the startup
     * timeline (see {@link #STARTUP_FETCH_TIMEOUT}'s Javadoc for the same reasoning applied to
     * the branding/player-info fetches) - this isn't even inside that bound.
     *
     * <p>Also the re-entry point when the player flips the Settings toggle back on (see
     * {@link #setDiscordEnabled}) - re-checks {@link LauncherConfig#discordEnabled()} itself so
     * both callers share one gate instead of duplicating the check.
     */
    private void connectDiscordPresence() {
        if (!configStore.load().discordEnabled()) {
            return;
        }
        DiscordPresenceService presence = clientInfo != null ? DiscordPresenceService.forAppId(clientInfo.discordAppId()) : null;
        if (presence == null) {
            return;
        }
        discordPresence = presence;
        backgroundExecutor.execute(() -> {
            presence.connect();
            presence.setBrowsing();
        });
    }

    /**
     * Re-encrypts any {@code .cryptlist} file left in plaintext by a previous run that didn't get
     * to finish (the launcher was killed, or the machine lost power, while a game was running -
     * see {@link #launchGame}). Cheap when there's nothing to do, and skipped entirely without a
     * PIN, so it can just run unconditionally at startup rather than needing a "did we crash?"
     * flag to go stale.
     */
    private void resealSensitiveFiles() {
        SecretKey key = pinKey;
        if (key == null || !Files.isDirectory(dirs.instances())) {
            return;
        }
        try (var instances = Files.list(dirs.instances())) {
            for (Path instanceDir : instances.filter(Files::isDirectory).toList()) {
                SensitiveFileVault.encryptAll(instanceDir, key);
            }
        } catch (java.io.IOException e) {
            Log.warn(LauncherApp.class, "Could not re-seal sensitive modpack files at startup: " + e.getMessage());
        }
    }

    /**
     * The Settings page's "Code PIN" checkbox. Turning it on re-encrypts the token under the new
     * PIN-derived key (and deletes the on-disk wrapping key, which is what actually makes the PIN
     * worth anything - see {@code crypto.PinProtection}); turning it off needs the current PIN,
     * both to prove the player knows it and because the token has to be readable to be re-encrypted
     * the other way.
     *
     * @return the state the checkbox should end up in, which is <em>not</em> always what was asked
     *         for - a cancelled dialog or a wrong PIN leaves it as it was
     */
    private boolean setPinEnabled(boolean enable, java.awt.Component parent) {
        if (enable == pinProtection.isEnabled()) {
            return enable;
        }
        return enable ? enablePin(parent) : disablePin(parent);
    }

    private boolean enablePin(java.awt.Component parent) {
        char[] pin = PinSetupDialog.askNewPin(parent);
        if (pin == null) {
            return false;
        }
        try {
            SecretKey newKey = pinProtection.enable(pin);
            // Re-encrypt whatever is currently stored under the new key. Without a session there's
            // nothing to re-encrypt yet, and the next login will simply save under the PIN key.
            Ed25519KeyPair current = loadStoredKeyPair();
            pinKey = newKey;
            if (current != null) {
                EncryptedFileKeyStorage.withPin(dirs, newKey).save(current);
            }
            // Existing packs' sensitive files were plaintext until now - seal them immediately
            // rather than waiting for the next game exit.
            resealSensitiveFiles();
            Log.info(LauncherApp.class, "PIN enabled.");
            return true;
        } catch (KeyStorageException e) {
            Log.error(LauncherApp.class, "Could not enable the PIN", e);
            pinProtection.disable();
            pinKey = null;
            javax.swing.JOptionPane.showMessageDialog(parent,
                    "Impossible d'activer le code PIN : " + e.getMessage(),
                    "Code PIN", javax.swing.JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            java.util.Arrays.fill(pin, '\0');
        }
    }

    private boolean disablePin(java.awt.Component parent) {
        char[] pin = PinSetupDialog.askCurrentPin(parent, "Désactiver le code PIN",
                "Entre ton code PIN actuel pour le désactiver.");
        if (pin == null) {
            return true;
        }
        try {
            SecretKey currentKey = pinProtection.deriveExisting(pin);
            KeyStorage pinStorage = EncryptedFileKeyStorage.withPin(dirs, currentKey);
            Ed25519KeyPair stored = pinStorage.load().orElse(null);

            // Decrypt everything still sealed *before* dropping the key, or those files become
            // unreadable forever.
            decryptAllInstances(currentKey);

            pinProtection.disable();
            pinKey = null;
            if (stored != null) {
                // The no-PIN storage regenerates a wrapping key on demand and saves under it.
                new EncryptedFileKeyStorage(dirs).save(stored);
            }
            Log.info(LauncherApp.class, "PIN disabled.");
            return false;
        } catch (KeyStorageException e) {
            javax.swing.JOptionPane.showMessageDialog(parent, "Code incorrect.",
                    "Code PIN", javax.swing.JOptionPane.WARNING_MESSAGE);
            return true;
        } finally {
            java.util.Arrays.fill(pin, '\0');
        }
    }

    private void decryptAllInstances(SecretKey key) {
        if (!Files.isDirectory(dirs.instances())) {
            return;
        }
        try (var instances = Files.list(dirs.instances())) {
            for (Path instanceDir : instances.filter(Files::isDirectory).toList()) {
                SensitiveFileVault.decryptAll(instanceDir, key);
            }
        } catch (java.io.IOException e) {
            Log.warn(LauncherApp.class, "Could not decrypt sensitive modpack files: " + e.getMessage());
        }
    }

    /** The Settings page's "Stockage des ressources vanilla" radio buttons. */
    private void setAssetStorageMode(AssetStorageMode mode) {
        var config = configStore.load();
        config.setAssetStorageMode(mode);
        configStore.save(config);
    }

    private void setLaunchWindowAction(LaunchWindowAction action) {
        var config = configStore.load();
        config.setLaunchWindowAction(action);
        configStore.save(config);
    }

    /**
     * Only takes effect on the bootstrap's *next* run (see {@code config.LauncherConfig#channel}'s
     * own Javadoc) - this running launcher never re-fetches its own jar.
     */
    private void setChannel(String channel) {
        var config = configStore.load();
        config.setChannel(channel);
        configStore.save(config);
    }

    /**
     * The Java binary modpacks run on unless one overrides it - applies to the next launch, since
     * an already-running game keeps the JVM it was started with. {@code null} restores the
     * launcher's own JVM (see {@code game.JavaRuntimes}).
     */
    private void setDefaultJavaPath(String javaPath) {
        var config = configStore.load();
        config.setDefaultJavaPath(JavaRuntimes.normalize(javaPath));
        configStore.save(config);
    }

    /**
     * Best-effort - a failed fetch just leaves the Settings page's channel picker showing
     * "Chargement..." forever and every modpack's "Gerer" dialog offering only "canal par
     * défaut", no error dialog for something this minor. Also populates {@link #availableChannels},
     * shared with every modpack detail page's own channel picker (see
     * {@code ModpackDetailViewModel#availableChannels}) so it isn't fetched twice.
     */
    private void loadChannelsForSettings(SettingsPage settingsPage) {
        if (offlineMode) return;
        backgroundExecutor.execute(() -> {
            try {
                var channels = authApi.fetchChannels();
                availableChannels = channels;
                settingsPage.setAvailableChannels(channels, configStore.load().channel());
            } catch (Exception e) {
                Log.warn(LauncherApp.class, "Could not fetch distribution channels: " + e.getMessage());
            }
        });
    }

    /**
     * The "Migrer les modpacks déjà installés" button: applies the chosen storage mode to instances
     * that are already on disk, which changing the setting alone deliberately doesn't do.
     *
     * <p>Refused outright while anything is installing or running - it moves and re-links the very
     * files an install is writing or a game has open.
     */
    private void migrateAssetStorage(java.awt.Component parent) {
        if (instanceStates.isAnyActive()) {
            javax.swing.JOptionPane.showMessageDialog(parent,
                    "Impossible de migrer pendant qu'un modpack est en cours d'installation ou de jeu.",
                    "Migration", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        AssetStorageMode mode = configStore.load().assetStorageMode();
        if (javax.swing.JOptionPane.showConfirmDialog(parent,
                describeMigration(mode) + "\n\nCela peut prendre un moment. Continuer ?",
                "Migrer les modpacks installés",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE) != javax.swing.JOptionPane.YES_OPTION) {
            return;
        }

        backgroundExecutor.execute(() -> {
            SharedAssetsLinker.MigrationResult total = new SharedAssetsLinker.MigrationResult(0, 0, 0, 0, false, 0);
            for (Path instanceDir : instanceDirectories()) {
                SharedAssetsLinker.MigrationResult one = assetsLinker.migrate(instanceDir, mode);
                total = new SharedAssetsLinker.MigrationResult(
                        total.pooled() + one.pooled(),
                        total.deduplicated() + one.deduplicated(),
                        total.privatised() + one.privatised(),
                        total.bytesReclaimed() + one.bytesReclaimed(),
                        total.hardlinksFailed() || one.hardlinksFailed(),
                        total.unlinked() + one.unlinked());
            }
            SharedAssetsLinker.MigrationResult result = total;
            SwingUtilities.invokeLater(() -> showMigrationResult(parent, mode, result));
        });
    }

    private String describeMigration(AssetStorageMode mode) {
        return switch (mode) {
            case LAUNCHER_SHARED -> "Les ressources vanilla déjà présentes dans chaque modpack vont être regroupées "
                    + "dans le stockage partagé du launcher, et les copies en double remplacées par des liens.";
            case OFFICIAL_MINECRAFT -> "Les ressources vanilla déjà présentes dans chaque modpack vont être regroupées "
                    + "dans le dossier du lanceur officiel (.minecraft), et les copies en double remplacées par des liens.";
            case PER_MODPACK -> "Chaque modpack va récupérer sa propre copie indépendante des ressources vanilla. "
                    + "Cela va AUGMENTER l'espace disque utilisé.";
        };
    }

    private void showMigrationResult(java.awt.Component parent, AssetStorageMode mode, SharedAssetsLinker.MigrationResult result) {
        StringBuilder message = new StringBuilder();
        if (mode == AssetStorageMode.PER_MODPACK) {
            message.append(result.privatised()).append(" fichier(s) rendus indépendants.");
        } else {
            message.append(result.pooled()).append(" fichier(s) déplacés vers le stockage partagé,\n")
                    .append(result.deduplicated()).append(" doublon(s) remplacés par des liens,\n")
                    .append(HumanSize.format(result.bytesReclaimed())).append(" récupérés.");
        }

        // Worth stating separately rather than folding into the counts above: these files weren't
        // this modpack's at all (other Minecraft profiles pulled in by an older build - see
        // SharedAssetsLinker.LEGACY_SHARED_SUBDIR), and a player seeing their instance shrink by
        // thousands of files deserves to know nothing of theirs was thrown away.
        if (result.unlinked() > 0) {
            message.append("\n\n").append(result.unlinked())
                    .append(" fichier(s) appartenant à d'autres installations Minecraft ont été retirés du modpack.\n")
                    .append("Tes autres profils (.minecraft) ne sont pas touchés : seul le lien depuis le modpack a été supprimé.");
        }

        if (result.hardlinksFailed()) {
            // The one case the player genuinely needs told: everything "worked", but the sharing
            // silently didn't happen - see notifyHardlinkFallback.
            message.append("\n\n").append(HARDLINK_FALLBACK_MESSAGE);
            javax.swing.JOptionPane.showMessageDialog(parent, message.toString(), "Migration", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        javax.swing.JOptionPane.showMessageDialog(parent, message.toString(), "Migration", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * The "Nettoyer" button. Previews first so the confirmation can state a real size, then deletes
     * off the EDT (walking every instance can take a moment on a big install).
     */
    private void runCleanup(java.awt.Component parent) {
        if (instanceStates.isAnyActive()) {
            javax.swing.JOptionPane.showMessageDialog(parent,
                    "Impossible de nettoyer pendant qu'un modpack est en cours d'installation ou de jeu.",
                    "Nettoyage", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        backgroundExecutor.execute(() -> {
            CleanupService.Result preview = cleanupService.preview();
            SwingUtilities.invokeLater(() -> {
                if (preview.filesDeleted() == 0) {
                    javax.swing.JOptionPane.showMessageDialog(parent, "Rien à nettoyer.",
                            "Nettoyage", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                int choice = javax.swing.JOptionPane.showConfirmDialog(parent,
                        "Supprimer " + preview.filesDeleted() + " fichier(s) (" + HumanSize.format(preview.bytesFreed()) + ") ?\n\n"
                                + "Logs du launcher, logs et rapports de crash des modpacks, caches de téléchargement.\n"
                                + "Tes mods, configs, mondes et ta session ne sont pas touchés.",
                        "Nettoyage", javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE);
                if (choice != javax.swing.JOptionPane.YES_OPTION) {
                    return;
                }
                backgroundExecutor.execute(() -> {
                    CleanupService.Result result = cleanupService.cleanEverything();
                    SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(parent,
                            result.filesDeleted() + " fichier(s) supprimés, " + HumanSize.format(result.bytesFreed()) + " libérés.",
                            "Nettoyage", javax.swing.JOptionPane.INFORMATION_MESSAGE));
                });
            });
        });
    }

    /** The "Signaler un bug" button - a voluntary report, no crash involved. */
    private void reportBug(java.awt.Component parent) {
        ManualBugReportDialog.Input input = ManualBugReportDialog.ask(parent);
        if (input == null) {
            return;
        }
        String modpackSlug = instanceStates.anyRunningSlug().orElse(null);
        backgroundExecutor.execute(() -> {
            boolean sent = bugReportApi.submit(BugReportType.MANUAL, input.summary(), input.description(), null, modpackSlug);
            SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(parent,
                    sent ? "Rapport envoyé, merci !" : "Impossible d'envoyer le rapport (pas de connexion au serveur ?).",
                    "Signaler un bug",
                    sent ? javax.swing.JOptionPane.INFORMATION_MESSAGE : javax.swing.JOptionPane.WARNING_MESSAGE));
        });
    }

    /**
     * Tells the player, once per session, that asset sharing silently isn't happening.
     *
     * <p>Worth surfacing rather than only logging: everything still installs and runs correctly, so
     * the only visible symptom is disk usage quietly behaving like the per-modpack option even
     * though a sharing option is selected - which looks like the setting simply not working.
     *
     * <p>Once per session because it's a property of the filesystem layout, not of this particular
     * install: it will keep failing identically for every modpack, and a dialog on each one would
     * just train the player to dismiss it.
     */
    private void notifyHardlinkFallback() {
        if (!hardlinkFallbackReported.compareAndSet(false, true)) {
            return;
        }
        Log.warn(LauncherApp.class, "Hardlink sharing unavailable - behaving as PER_MODPACK for this session.");
        SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(shellFrame,
                HARDLINK_FALLBACK_MESSAGE, "Stockage des ressources vanilla", javax.swing.JOptionPane.WARNING_MESSAGE));
    }

    private List<Path> instanceDirectories() {
        if (!Files.isDirectory(dirs.instances())) {
            return List.of();
        }
        try (var stream = Files.list(dirs.instances())) {
            return stream.filter(Files::isDirectory).toList();
        } catch (java.io.IOException e) {
            Log.warn(LauncherApp.class, "Could not list instances: " + e.getMessage());
            return List.of();
        }
    }

    /** The Settings page's "Afficher mon statut sur Discord" checkbox. */
    private void setDiscordEnabled(boolean enabled) {
        var config = configStore.load();
        config.setDiscordEnabled(enabled);
        configStore.save(config);

        if (enabled) {
            connectDiscordPresence();
            return;
        }
        DiscordPresenceService presence = discordPresence;
        discordPresence = null;
        if (presence != null) {
            backgroundExecutor.execute(presence::close);
        }
    }

    /**
     * Best-effort; leaves {@link #playerInfo}/{@link #playerAvatar} {@code null} on any failure
     * or timeout. A 401/403 is reported to the caller so it can request revalidation without
     * deleting the stored key: the server also uses 403 for clock skew and signature replay.
     *
     * @return false if the stored key was rejected (401/403); true otherwise, including on any
     * other failure (offline, timeout, server error) where the cached data is simply left stale
     */
    private boolean fetchPlayerInfoBlocking() {
        try {
            playerInfo = runWithTimeout(() -> authApi.fetchPlayerInfo(session));
            lastRequestFailure = null;
            try { localCatalog.cacheUsername(session.playerId(), playerInfo.username()); }
            catch (Exception cacheFailure) { Log.warn(LauncherApp.class, "Could not cache player identity"); }
            playerAvatar = iconCache.load("avatar:" + session.playerId(), playerInfo.avatar(), null, true);
            return true;
        } catch (LauncherApiException e) {
            lastRequestFailure = e.getMessage();
            if (e.statusCode() == 401 || e.statusCode() == 403) {
                Log.warn(LauncherApp.class, "Stored session was rejected (status " + e.statusCode() + ")");
                return false;
            }
            Log.warn(LauncherApp.class, "Could not fetch player info: " + e.getMessage());
            return true;
        } catch (Exception e) {
            lastRequestFailure = e.getMessage();
            Log.warn(LauncherApp.class, "Could not fetch player info: " + e.getMessage());
            return true;
        }
    }

    /**
     * Unwraps {@link java.util.concurrent.ExecutionException} so callers can catch the typed
     * exception a {@code task} actually threw (e.g. {@link LauncherApiException}) instead of
     * string-matching a wrapper's message.
     */
    private <T> T runWithTimeout(java.util.concurrent.Callable<T> task) throws Exception {
        java.util.concurrent.Future<T> future = backgroundExecutor.submit(task);
        try {
            return future.get(STARTUP_FETCH_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new java.io.IOException("Timed out after " + STARTUP_FETCH_TIMEOUT, e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    private void showLogin() {
        DeviceAuthService deviceAuth = new DeviceAuthService(authApi, keyStorage, clientId);
        LoginFrame loginFrame = new LoginFrame(deviceAuth, playerId -> {
            var config = configStore.load();
            config.setPlayerId(playerId);
            configStore.save(config);

            Ed25519KeyPair key = loadStoredKeyPair();
            if (key == null) {
                Log.error(LauncherApp.class, "Login reported success but no key is stored - cannot continue", null);
                LoginFrame stuckFrame = this.loginFrame;
                if (stuckFrame != null) {
                    stuckFrame.showPostLoginFailure("Connexion reussie mais la cle n'a pas pu etre enregistree. Reessaie.");
                }
                return;
            }
            session = new PlayerSession(key, playerId, clientId);
            offlineMode = false;
            backgroundExecutor.execute(() -> {
                if (!fetchPlayerInfoBlocking()) {
                    suspendRejectedSession();
                    SwingUtilities.invokeLater(() -> {
                        if (this.loginFrame != null) this.loginFrame.showPostLoginFailure(
                                "Session non validée. Vérifie l’horloge de l’appareil puis réessaie. La clé enregistrée est conservée.");
                    });
                    return;
                }
                SwingUtilities.invokeLater(this::showShell);
            });
        });
        if (clientInfo != null) {
            loginFrame.setClientInfo(clientInfo.name(), clientLogo);
        }
        if (clientLogo != null) {
            loginFrame.setIconImage(clientLogo);
        }
        // Kept so showShell can close it only once the shell window exists - see LoginFrame's
        // onSucceeded, which deliberately no longer disposes itself.
        this.loginFrame = loginFrame;
        loginFrame.setOfflineAction(offlineAction());
        if (lastRequestFailure != null) loginFrame.showPostLoginFailure(
                "Connexion au site indisponible. Réessaie ou utilise les installations locales.");
        loginFrame.setVisible(true);

        // The login window is the first thing a signed-out player sees, so this is where the
        // bootstrap's splash should hand over. Waiting for the shell instead would leave the
        // splash stacked on top of the login window for as long as the player takes to sign in.
        StartupSignal.signalReady();

        refreshLogoInBackground();
    }

    /**
     * Raises and focuses a window reliably, including on Windows where plain
     * {@code toFront()}/{@code requestFocus()} is routinely a no-op: Windows' foreground-lock
     * mechanism only lets the process that currently owns the foreground (or one it explicitly
     * hands focus to) call {@code SetForegroundWindow} - a background process asking Swing to
     * raise its own window doesn't qualify, so the window silently stays behind whatever already
     * had focus. Toggling always-on-top briefly forces a topmost restack Windows honors
     * regardless of that restriction, then reverting it stops the window from staying pinned
     * above everything else afterward.
     */
    private static void forceToFront(Window window) {
        window.setAlwaysOnTop(true);
        window.toFront();
        window.requestFocus();
        window.setAlwaysOnTop(false);
    }

    private void showShell() {
        LauncherFrame previousFrame = shellFrame;
        LauncherFrame frame = new LauncherFrame(this::showAccountPopover);
        this.shellFrame = frame;
        if (offlineMode) frame.setTitle(frame.getTitle() + " — Hors connexion");

        NewsSectionPage newsSection = buildNewsSection(frame);
        ModpackSectionPage modpackSection = buildModpackSection(frame);

        SettingsPage settingsPage = new SettingsPage(
                themeController.current(),
                themeController::switchTo,
                configStore.load().discordEnabled(),
                this::setDiscordEnabled,
                configStore.load().assetStorageMode(),
                this::setAssetStorageMode,
                configStore.load().launchWindowAction(),
                this::setLaunchWindowAction,
                configStore.load().channel(),
                this::setChannel,
                configStore.load().defaultJavaPath(),
                this::setDefaultJavaPath,
                pinProtection.isEnabled(),
                enable -> setPinEnabled(enable, frame),
                () -> migrateAssetStorage(frame),
                () -> openFolder(dirs.root()),
                () -> runCleanup(frame),
                // Unreachable client info (offline start) means the button stays available - a
                // failed submit is a better outcome than silently hiding a feature the site
                // actually allows. See ClientInfo#bugReportsAllowed.
                clientInfo == null || clientInfo.bugReportsAllowed(),
                () -> reportBug(frame),
                this::logout
        );
        frame.addPage(NavTarget.SETTINGS, settingsPage);
        loadChannelsForSettings(settingsPage);
        frame.addPage(NavTarget.NEWS, newsSection);
        frame.addPage(NavTarget.MODPACKS, modpackSection);

        // Rebinds the topbar's refresh button to whatever the newly-shown top-level page can
        // refresh - sub-navigation within a section (modpack/news detail) sets it directly
        // instead, since it never goes through here (see LauncherFrame#setOnNavigate's Javadoc).
        frame.setOnNavigate(target -> {
            switch (target) {
                case MODPACKS -> frame.setOnRefresh(() -> loadModpackList(frame, modpackSection));
                case NEWS -> frame.setOnRefresh(() -> loadNewsList(frame, newsSection));
                case SETTINGS -> frame.setOnRefresh(null);
            }
        });

        frame.navigateTo(NavTarget.MODPACKS);
        frame.setLogo(clientLogo);
        frame.setAccountAvatar(playerAvatar);
        if (clientLogo != null) {
            frame.setIconImage(clientLogo);
        }
        frame.setVisible(true);
        if (previousFrame != null && previousFrame != frame) previousFrame.dispose();
        // setVisible(true)/toFront() alone doesn't reliably raise/focus the window on Windows -
        // Windows actively blocks a background process from stealing foreground focus
        // (SetForegroundWindow restrictions), so plain toFront() is routinely a no-op there,
        // confirmed on a real Windows machine (the shell window opened behind whatever else had
        // focus and needed a manual click). See forceToFront's own doc for the actual fix.
        forceToFront(frame);

        // Lets the bootstrap retire its splash now that there is a real window to replace it.
        // Also reached without any login window at all, when a stored session skips straight here.
        StartupSignal.signalReady();

        // Only now, with the window up, is it worth asking the site whether the logo changed.
        refreshLogoInBackground();

        // Only now that the shell is on screen is it safe to drop the login window - see the
        // loginFrame field. No-op when there was no login this run (a stored session).
        if (loginFrame != null) {
            loginFrame.dispose();
            loginFrame = null;
        }
    }

    /** Shows the cached {@link #playerInfo}/{@link #playerAvatar} - never makes a network call itself. */
    private void showAccountPopover(java.awt.Component anchor) {
        String username = playerInfo != null ? playerInfo.username() : "?";
        String role = playerInfo != null ? playerInfo.role() : null;
        String email = playerInfo != null ? playerInfo.email() : null;
        AccountPopover.show(anchor, username, role, email, playerAvatar,
                () -> openUrl(baseUrl.resolve("/profile").toString()), this::logout);
    }

    private NewsSectionPage buildNewsSection(LauncherFrame frame) {
        NewsSectionPage[] sectionHolder = new NewsSectionPage[1];
        NewsListPage emptyList = new NewsListPage(new NewsListViewModel(List.of(), post -> {
        }));
        sectionHolder[0] = new NewsSectionPage(emptyList);

        loadNewsList(frame, sectionHolder[0]);
        return sectionHolder[0];
    }

    /** Fetches the news list and replaces the section's list page - the initial load, and what the topbar's refresh button re-runs while the list is shown. */
    private void loadNewsList(LauncherFrame frame, NewsSectionPage section) {
        if (offlineMode) {
            section.replaceList(new NewsListPage(new NewsListViewModel(List.of(), post -> { })));
            return;
        }
        // Immediate visual transition, same reasoning as showModpackDetail's LoadingPanel - a
        // click on "Actualiser" that visibly does nothing until the fetch completes reads as the
        // app hanging.
        section.replaceList(new mc.snakenest.launcher.ui.common.LoadingPanel("Chargement des actualités..."));

        backgroundExecutor.execute(() -> {
            try {
                List<Post> posts = newsApi.listPosts();
                SwingUtilities.invokeLater(() -> {
                    NewsListPage realList = new NewsListPage(new NewsListViewModel(posts,
                            post -> openUrl(post.url())));
                    section.replaceList(realList);
                });
            } catch (Exception e) {
                Log.warn(LauncherApp.class, "Could not load news: " + e.getMessage());
                SwingUtilities.invokeLater(() -> section.replaceList(new mc.snakenest.launcher.ui.common.ErrorPanel(
                        "Impossible de charger les actualités : " + e.getMessage(),
                        mc.snakenest.launcher.util.Throwables.stackTraceOf(e),
                        () -> loadNewsList(frame, section),
                        () -> frame.navigateTo(NavTarget.MODPACKS), offlineAction())));
            }
        });
    }

    private void showNewsDetail(LauncherFrame frame, NewsSectionPage section, Post post) {
        section.showDetail(new NewsDetailPage(post, () -> openUrl(post.url())));
        frame.showBackButton(post.title(), () -> {
            section.showList();
            frame.navigateTo(NavTarget.NEWS);
        });
        // Nothing to refresh about a single already-loaded post - the list is what "Actualiser" applies to.
        frame.setOnRefresh(null);
    }

    private ModpackSectionPage buildModpackSection(LauncherFrame frame) {
        ModpackSectionPage[] sectionHolder = new ModpackSectionPage[1];
        ModpackListPage emptyList = new ModpackListPage(new ModpackListViewModel(List.of(), Set.of(), m -> {
        }, m -> {
        }));
        sectionHolder[0] = new ModpackSectionPage(emptyList);

        loadModpackList(frame, sectionHolder[0]);
        return sectionHolder[0];
    }

    /** Fetches the modpack list + logos and replaces the section's list page - the initial load, and what "Actualiser" re-runs. */
    private void loadModpackList(LauncherFrame frame, ModpackSectionPage section) {
        // Immediate visual transition, same reasoning as showModpackDetail's LoadingPanel - a
        // click on "Actualiser" that visibly does nothing until the fetch completes reads as the
        // app hanging.
        section.replaceList(new mc.snakenest.launcher.ui.common.LoadingPanel("Chargement des modpacks..."));

        backgroundExecutor.execute(() -> {
            try {
                Set<String> local = localInstalledSlugs();
                List<ModpackSummary> modpacks = offlineMode ? localCatalog.localSummaries(local)
                        : localCatalog.mergeInstalled(modpackApi.listModpacks(session), local);
                removedPackSlugs = localCatalog.removedSlugs(local);
                Set<String> installed = installedSlugs(modpacks);
                Set<String> updateAvailable = offlineMode ? Set.of() : updateAvailableSlugs(modpacks).stream()
                        .filter(slug -> !removedPackSlugs.contains(slug)).collect(Collectors.toSet());
                Map<String, BufferedImage> logos = fetchModpackLogos(modpacks);
                SwingUtilities.invokeLater(() -> {
                    ModpackListViewModel viewModel = new ModpackListViewModel(modpacks, installed, updateAvailable, logos,
                            modpack -> showModpackDetail(frame, section, modpack),
                            this::quickInstallAndLaunch,
                            modpack -> cancelInstall(modpack.slug()),
                            modpack -> stopGame(modpack.slug()),
                            modpack -> killGame(modpack.slug()));
                    viewModel.setLocalAvailability(offlineMode, removedPackSlugs);
                    currentListViewModel = viewModel;
                    section.replaceList(new ModpackListPage(viewModel));
                    restoreActiveCardState(viewModel);
                });
            } catch (Exception e) {
                Log.warn(LauncherApp.class, "Could not load modpacks: " + e.getMessage());
                // A retryable error state, not the empty-list message - falling back to "Aucun
                // modpack disponible" here used to silently pass off a real failure (e.g. an
                // Access Denied from the server) as "you just don't have any modpacks", which is
                // actively misleading instead of merely unhelpful.
                SwingUtilities.invokeLater(() -> section.replaceList(new mc.snakenest.launcher.ui.common.ErrorPanel(
                        "Impossible de charger les modpacks : " + e.getMessage(),
                        mc.snakenest.launcher.util.Throwables.stackTraceOf(e),
                        () -> loadModpackList(frame, section),
                        () -> frame.navigateTo(NavTarget.NEWS), offlineAction())));
            }
        });
    }

    private boolean localOnly(String slug) { return offlineMode || removedPackSlugs.contains(slug); }

    private boolean isLocallyInstalled(String slug) {
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) return false;
        try {
            Path instance = dirs.instance(slug);
            return Files.isDirectory(instance, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(instance.resolve("client.jar")) && Files.size(instance.resolve("client.jar")) > 0
                    && new LocalManifestStore().load(instance).isPresent();
        } catch (Exception ignored) { return false; }
    }

    private Set<String> localInstalledSlugs() throws java.io.IOException {
        if (!Files.isDirectory(dirs.instances())) return Set.of();
        try (var directories = Files.list(dirs.instances())) {
            return directories.map(path -> path.getFileName().toString()).filter(this::isLocallyInstalled).collect(Collectors.toSet());
        }
    }

    private ModpackManifest localManifest(String slug) throws java.io.IOException {
        if (!isLocallyInstalled(slug)) throw new java.io.IOException("Ce modpack n’est pas entièrement installé sur cet appareil.");
        var cached = localCatalog.loadManifest(slug);
        if (cached.isPresent()) return cached.get();
        var recovered = mc.snakenest.launcher.game.LocalInstalledManifestRecovery.recover(dirs.instance(slug));
        if (recovered.isEmpty()) throw new java.io.IOException("Métadonnées de lancement local absentes. Connecte-toi une fois pour terminer cette installation.");
        localCatalog.saveManifest(slug, recovered.get());
        return recovered.get();
    }

    private String localUsername() throws java.io.IOException {
        if (session == null) throw new java.io.IOException("Aucune session enregistrée sur cet appareil.");
        String username = localCatalog.loadUsername(session.playerId()).orElseThrow(() -> new java.io.IOException(
                "Aucun pseudo mémorisé pour ce compte. Connecte-toi une fois pour préparer le mode hors connexion."));
        if (!username.matches("[A-Za-z0-9_]{1,16}")) throw new java.io.IOException("Pseudo local invalide.");
        return username;
    }

    private Runnable offlineAction() {
        return configStore.load().playerId() == null ? null : this::enterOfflineMode;
    }

    private void restoreOfflineIdentity() throws java.io.IOException {
        Long playerId = configStore.load().playerId();
        Ed25519KeyPair key = loadStoredKeyPair();
        if (key == null || playerId == null) throw new java.io.IOException("Aucune session sauvegardée sur cet appareil.");
        session = new PlayerSession(key, playerId, clientId);
        String username = localUsername();
        offlineMode = true;
        playerInfo = new PlayerInfo(username, "Hors connexion", null, null);
        playerAvatar = iconCache.cached("avatar:" + playerId);
        clientLogo = logoCache.cached();
    }

    private void enterOfflineMode() {
        backgroundExecutor.execute(() -> {
            try {
                restoreOfflineIdentity();
                SwingUtilities.invokeLater(this::showShell);
            } catch (Exception failure) {
                SwingUtilities.invokeLater(() -> {
                    if (loginFrame != null) loginFrame.showPostLoginFailure(failure.getMessage());
                    else javax.swing.JOptionPane.showMessageDialog(shellFrame, failure.getMessage(), "Mode hors connexion", javax.swing.JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private Set<String> installedSlugs(List<ModpackSummary> modpacks) {
        return modpacks.stream()
                .map(ModpackSummary::slug)
                .filter(this::isLocallyInstalled)
                .collect(Collectors.toSet());
    }

    private Set<String> updateAvailableSlugs(List<ModpackSummary> modpacks) {
        return modpacks.stream()
                .filter(modpack -> isUpdateAvailable(modpack.slug(), modpack.latestVersion()))
                .map(ModpackSummary::slug)
                .collect(Collectors.toSet());
    }

    /**
     * Compares the version recorded in the local install's manifest (if any) against
     * {@code latestVersion} - {@code false} whenever the modpack isn't installed at all
     * ({@link LocalManifestStore#load} then returns empty), the server didn't report a latest
     * version, or the local manifest can't be read (treated as "nothing to flag", not an error -
     * this is a pure UI hint, not something {@link #syncModpackFiles} relies on).
     */
    private boolean isUpdateAvailable(String slug, String latestVersion) {
        if (latestVersion == null) {
            return false;
        }
        try {
            return new LocalManifestStore().load(dirs.instance(slug))
                    .map(StoredManifest::version)
                    .map(installedVersion -> !latestVersion.equals(installedVersion))
                    .orElse(false);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /** Fetches every modpack's logo in parallel (best-effort - {@code null} entries just fall back to the letter placeholder). */
    private Map<String, BufferedImage> fetchModpackLogos(List<ModpackSummary> modpacks) {
        Map<String, java.util.concurrent.CompletableFuture<BufferedImage>> futures = new java.util.HashMap<>();
        for (ModpackSummary modpack : modpacks) {
            futures.put(modpack.slug(), java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> iconCache.load("pack:" + modpack.slug(), modpack.image(), modpack.latestVersion(), !localOnly(modpack.slug())), backgroundExecutor));
        }
        Map<String, BufferedImage> logos = new java.util.HashMap<>();
        futures.forEach((slug, future) -> logos.put(slug, future.join()));
        return logos;
    }

    private void showModpackDetail(LauncherFrame frame, ModpackSectionPage section, ModpackSummary modpack) {
        Runnable back = () -> {
            section.showList();
            frame.navigateTo(NavTarget.MODPACKS);
        };

        // Immediate visual transition - a click that visibly does nothing until the manifest
        // fetch completes reads as the app hanging, especially on a slow connection.
        section.showDetail(new mc.snakenest.launcher.ui.common.LoadingPanel("Chargement de " + modpack.name() + "..."));
        frame.showBackButton(modpack.name(), back);
        // Re-running this whole method is an idempotent "reload" - safe to bind immediately,
        // even before the manifest fetch below has completed.
        frame.setOnRefresh(() -> showModpackDetail(frame, section, modpack));

        boolean installed = isLocallyInstalled(modpack.slug());

        backgroundExecutor.execute(() -> {
            try {
                // Read once just for the saved channel, before the manifest fetch that needs it -
                // re-read below with the manifest-aware fallback once the real settings (if any
                // were ever saved) are known either way, channel included.
                String channel = modpackSettingsStore.load(modpack.slug(), ModpackSettings.defaults()).channel();
                boolean localOnly = localOnly(modpack.slug());
                ModpackManifest manifest = localOnly ? localManifest(modpack.slug())
                        : modpackApi.getManifest(modpack.slug(), null, channel, session);
                ModpackSettings settings = modpackSettingsStore.load(modpack.slug(),
                        ModpackSettings.defaults(manifest.defaultMemoryMb(), manifest.defaultJvmArgs()));
                BufferedImage logo = iconCache.load("pack:" + modpack.slug(), modpack.image(), modpack.latestVersion(), !localOnly);
                boolean updateAvailable = !localOnly && isUpdateAvailable(modpack.slug(), manifest.version());
                Long installedSizeBytes = installedSizeBytes(dirs.instance(modpack.slug()));
                SwingUtilities.invokeLater(() -> {
                    // Forward reference: onManageMods needs to navigate back to this exact
                    // detail page instance once InstalledModsPage's own "Retour" is clicked, but
                    // that page isn't built until after the ViewModel that captures this lambda.
                    ModpackDetailPage[] detailHolder = new ModpackDetailPage[1];
                    ModpackDetailViewModel viewModel = new ModpackDetailViewModel(
                            modpack.slug(),
                            modpack.name(),
                            modpack.description(),
                            manifest.changelog(),
                            manifest.totalSize(),
                            manifest.files().size(),
                            installed,
                            updateAvailable,
                            installedSizeBytes,
                            logo,
                            settings,
                            () -> installAndLaunch(modpack, manifest),
                            () -> repairModpack(modpack, manifest),
                            () -> section.showInstalledMods(new mc.snakenest.launcher.ui.modpack.InstalledModsPage(
                                    manifest, dirs.instance(modpack.slug()), backgroundExecutor,
                                    () -> section.showDetail(detailHolder[0]))),
                            () -> uninstallModpack(modpack),
                            () -> cancelInstall(modpack.slug()),
                            () -> stopGame(modpack.slug()),
                            () -> killGame(modpack.slug()),
                            newSettings -> modpackSettingsStore.save(modpack.slug(), newSettings),
                            () -> openFolder(dirs.instance(modpack.slug())),
                            back,
                            () -> availableChannels
                    );
                    ModpackDetailPage detail = new ModpackDetailPage(viewModel);
                    detail.setLocalAvailability(offlineMode, removedPackSlugs.contains(modpack.slug()));
                    detail.setOfflineAction(offlineAction());
                    detailHolder[0] = detail;
                    // A brand new page always starts idle (ModpackDetailPage has no way to know
                    // otherwise) - restore whatever's genuinely happening to this modpack right
                    // now, since this runs on every open AND every "Actualiser"/back-then-reopen,
                    // not just the very first time this modpack's page is shown.
                    restoreActiveState(modpack.slug(), detail);
                    currentDetailPage = detail;
                    section.showDetail(detail);
                });
            } catch (Exception e) {
                Log.warn(LauncherApp.class, "Could not fetch manifest for " + modpack.slug() + ": " + e.getMessage());
                // Replaces the page's content with a retryable error state instead of bouncing
                // back to the list plus a modal dialog - spamming "Actualiser" into a 429 used to
                // leave the page looking stuck/broken with no obvious way to try again short of
                // re-opening the modpack from the list.
                SwingUtilities.invokeLater(() -> section.showDetail(new mc.snakenest.launcher.ui.common.ErrorPanel(
                        "Impossible de charger \"" + modpack.name() + "\" : " + e.getMessage(),
                        mc.snakenest.launcher.util.Throwables.stackTraceOf(e),
                        () -> showModpackDetail(frame, section, modpack),
                        back, offlineAction())));
            }
        });
    }

    // Set right before install/launch starts, so its async progress callbacks have somewhere to report to.
    private volatile ModpackDetailPage currentDetailPage;

    /**
     * Applies whichever activity {@code instanceStates} says is genuinely happening to
     * {@code slug} right now to a freshly built {@code ModpackDetailPage} - called every time one
     * is built ({@code showModpackDetail}, on open/refresh/reopen), covering
     * installing/running/stopping the same way instead of a separate check per activity.
     */
    private void restoreActiveState(String slug, ModpackDetailPage detail) {
        instanceStates.get(slug).ifPresent(state -> {
            switch (state.activity()) {
                case INSTALLING -> detail.setBusy(true);
                case RUNNING -> detail.setRunning(true);
                case STOPPING -> {
                    detail.setRunning(true);
                    detail.setStopping(true);
                }
            }
        });
    }

    /**
     * Same as {@link #restoreActiveState}, for a freshly built {@code ModpackListPage}'s cards -
     * every modpack in the list is checked, not just one, since several can genuinely be active at
     * once (installing one while another's game is running).
     */
    private void restoreActiveCardState(ModpackListViewModel viewModel) {
        for (ModpackSummary modpack : viewModel.modpacks()) {
            instanceStates.get(modpack.slug()).ifPresent(state -> {
                switch (state.activity()) {
                    case INSTALLING -> viewModel.setCardBusy(state.slug(), true);
                    case RUNNING -> viewModel.setCardRunning(state.slug(), true);
                    case STOPPING -> {
                        viewModel.setCardRunning(state.slug(), true);
                        viewModel.setCardStopping(state.slug(), true);
                    }
                }
            });
        }
    }

    /**
     * Applies {@code action} to the currently open detail page, but only if it's still showing
     * {@code slug} - a page rebuilt since {@code slug}'s install/launch started (e.g. the user hit
     * "Actualiser", or left and reopened it) is a different {@code ModpackDetailPage} instance, and
     * a reference to the old one captured once at the start of a long-running install/launch would
     * silently never reach whatever's actually on screen now. Every live push during that chain
     * goes through here instead of threading a page reference through it, so this fix applies
     * everywhere at once (progress/status text, the busy/running/stopping button state, ...)
     * rather than one call site at a time.
     */
    private void withDetailPage(String slug, java.util.function.Consumer<ModpackDetailPage> action) {
        ModpackDetailPage page = currentDetailPage;
        if (page != null && slug.equals(page.slug())) {
            action.accept(page);
        }
    }

    private void installAndLaunch(ModpackSummary modpack, ModpackManifest manifest) {
        // False means this slug already has an activity in flight - the button that triggered
        // this shouldn't have been clickable in that state, so this is defense in depth rather
        // than an expected path, and a silent no-op is the right response to it either way.
        if (!instanceStates.beginInstalling(modpack.slug())) {
            return;
        }
        withDetailPage(modpack.slug(), page -> page.setBusy(true));
        setCardBusy(modpack.slug(), true);

        java.util.concurrent.Future<?> task = backgroundExecutor.submit(() -> {
            try {
                doInstallAndLaunch(modpack, manifest);
            } finally {
                withDetailPage(modpack.slug(), page -> page.setBusy(false));
                setCardBusy(modpack.slug(), false);
            }
        });
        instanceStates.attachInstallTask(modpack.slug(), task);
    }

    /**
     * Same action as the card's play/download icon in the list, without opening the detail page
     * first - submitted (not fire-and-forget) so the card's icon can switch to "Annuler" and
     * {@link #cancelInstall} actually has something to cancel, the same as a detail-page-started
     * install.
     */
    private void quickInstallAndLaunch(ModpackSummary modpack) {
        if (!instanceStates.beginInstalling(modpack.slug())) {
            return;
        }
        setCardBusy(modpack.slug(), true);
        java.util.concurrent.Future<?> task = backgroundExecutor.submit(() -> {
            try {
                String channel = modpackSettingsStore.load(modpack.slug(), ModpackSettings.defaults()).channel();
                ModpackManifest manifest = localOnly(modpack.slug()) ? localManifest(modpack.slug())
                        : modpackApi.getManifest(modpack.slug(), null, channel, session);
                doInstallAndLaunch(modpack, manifest);
            } catch (Exception e) {
                // Covers a failure in the getManifest() call above too, not just doInstallAndLaunch
                // - that one already clears this slug's state itself when it's the one that
                // fails/cancels, this is a harmless no-op in that case (already cleared, or moved
                // on to RUNNING).
                instanceStates.clearIfInstalling(modpack.slug());
                if (isCancellation(e)) {
                    Log.warn(LauncherApp.class, "Quick install/launch cancelled for " + modpack.slug());
                } else {
                    Log.error(LauncherApp.class, "Quick install/launch failed for " + modpack.slug(), e);
                    showErrorDialog(modpack, e);
                }
            } finally {
                setCardBusy(modpack.slug(), false);
            }
        });
        instanceStates.attachInstallTask(modpack.slug(), task);
    }

    /** No-op if the given slug's card isn't in the currently-shown list (e.g. it was refreshed away, or its detail page is the only thing open). */
    private void setCardBusy(String slug, boolean busy) {
        ModpackListViewModel list = currentListViewModel;
        if (list != null) {
            list.setCardBusy(slug, busy);
        }
    }

    /** Same as {@link #setCardBusy}, for the "Tuer" (force-kill) state. */
    private void setCardStopping(String slug, boolean stopping) {
        ModpackListViewModel list = currentListViewModel;
        if (list != null) {
            list.setCardStopping(slug, stopping);
        }
    }

    /** Same as {@link #setCardBusy}, for the running-game state. */
    private void setCardRunning(String slug, boolean running) {
        ModpackListViewModel list = currentListViewModel;
        if (list != null) {
            list.setCardRunning(slug, running);
        }
    }

    /** Same as {@link #setCardBusy}, for the installed (download vs play icon) state. */
    private void setCardInstalled(String slug, boolean installed) {
        ModpackListViewModel list = currentListViewModel;
        if (list != null) {
            list.setCardInstalled(slug, installed);
        }
    }

    /** Same as {@link #setCardBusy}, for the "Mettre à jour" state. */
    private void setCardUpdateAvailable(String slug, boolean updateAvailable) {
        ModpackListViewModel list = currentListViewModel;
        if (list != null) {
            list.setCardUpdateAvailable(slug, updateAvailable);
        }
    }

    /** Interrupts {@code slug}'s sync/install/repair task, if one is running - the "Annuler" button. */
    private void cancelInstall(String slug) {
        instanceStates.cancelInstalling(slug);
    }

    private static final int FORCE_KILL_TIMEOUT_SECONDS = 10;

    /**
     * Asks {@code slug}'s running game process to exit - the "Arreter" button. {@code destroy()}
     * (SIGTERM on Linux/macOS) asks the JVM to shut down but doesn't guarantee it will - a native
     * graphics/mod cleanup hang on shutdown leaves the process alive with no visible feedback.
     * Immediately flips the button to "Tuer" ({@link #killGame}, a manual force-kill the user can
     * click right away rather than waiting) and, as a backstop in case they don't notice/click
     * it, escalates to {@code destroyForcibly()} on its own after a grace period if the process
     * still hasn't exited by then - off the EDT (this runs from the button's action listener)
     * since {@code waitFor} blocks.
     */
    private void stopGame(String slug) {
        instanceStates.beginStopping(slug).ifPresent(process -> {
            withDetailPage(slug, page -> page.setStopping(true));
            setCardStopping(slug, true);
            process.destroy();
            backgroundExecutor.execute(() -> {
                try {
                    if (!process.waitFor(FORCE_KILL_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS) && process.isAlive()) {
                        Log.warn(LauncherApp.class, "Game process ignored destroy() for " + FORCE_KILL_TIMEOUT_SECONDS + "s, forcing kill");
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });
    }

    /**
     * Force-kills {@code slug}'s running game process outright - the "Tuer" button, shown after
     * "Arreter" has already been clicked once ({@link #stopGame}). {@code destroyForcibly()} is
     * SIGKILL on Linux/macOS: unconditional, no cooperation required from the target process, so
     * there's nothing to wait for or escalate here unlike {@link #stopGame} - verified against a
     * real process that traps and ignores {@code SIGTERM} (what plain {@code destroy()} sends).
     */
    private void killGame(String slug) {
        instanceStates.processFor(slug).ifPresent(Process::destroyForcibly);
    }

    /** Whether the currently open detail page (if any) is showing {@code slug} right now - see {@link #withDetailPage}. */
    private boolean isDetailPageShowing(String slug) {
        ModpackDetailPage page = currentDetailPage;
        return page != null && slug.equals(page.slug());
    }

    /**
     * The shared body of {@link #installAndLaunch}/{@link #quickInstallAndLaunch}. A failure here
     * must never be silent just because this modpack's detail page isn't the one currently on
     * screen (started from the list, or the user navigated away since) - falls back to a dialog
     * in that case instead, via {@link #isDetailPageShowing}.
     */
    private void doInstallAndLaunch(ModpackSummary modpack, ModpackManifest manifest) {
        try {
            boolean localOnly = localOnly(modpack.slug());
            if (!localOnly) {
                prepareInstallation(modpack, manifest, false);
                localCatalog.rememberSummary(modpack);
                localCatalog.saveManifest(modpack.slug(), manifest);
            }
            withDetailPage(modpack.slug(), page -> {
                page.setInstalled(true);
                page.setUpdateAvailable(false);
            });
            setCardInstalled(modpack.slug(), true);
            setCardUpdateAvailable(modpack.slug(), false);
            String username = localOnly ? localUsername() : authApi.fetchUsername(session);
            if (!localOnly) localCatalog.cacheUsername(session.playerId(), username);
            warnIfUsernameChanged(modpack.slug(), username);
            launchGame(modpack, manifest, username, localOnly);
        } catch (Exception e) {
            // Only actually clears anything if we didn't reach launchGame above - that already
            // moved this slug's state on to RUNNING, so this is a no-op in that case (see
            // InstanceStateRegistry#clearIfInstalling's Javadoc).
            instanceStates.clearIfInstalling(modpack.slug());
            if (isCancellation(e)) {
                Log.warn(LauncherApp.class, "Install/launch cancelled for " + modpack.slug());
                withDetailPage(modpack.slug(), page -> {
                    page.setStatus("Annulé.");
                    page.setProgress(-1);
                });
                return;
            }
            Log.error(LauncherApp.class, "Install/launch failed for " + modpack.slug(), e);
            if (isDetailPageShowing(modpack.slug())) {
                withDetailPage(modpack.slug(), page -> {
                    page.setError("Erreur : " + e.getMessage());
                    page.setProgress(-1);
                });
            } else {
                showErrorDialog(modpack, e);
            }
        }
    }

    /**
     * True if {@code e} (or the fact the thread is now interrupted) means "cancelled", not
     * "actually failed" - e.g. clicking "Annuler" during the mod loader install step used to
     * surface as a scary red "Could not install ..." error instead of the intended quiet
     * "Annulé.": {@code FlowUpdaterGameInstallService#install} wraps every failure (including a
     * real {@link InterruptedException} from the cancelled {@code Future}) into one
     * {@code GameInstallException}, and FlowUpdater's own internal I/O layer can turn an
     * interrupt into an {@link java.io.InterruptedIOException} without restoring the thread's
     * interrupted flag - so neither the top-level exception type nor
     * {@code Thread.currentThread().isInterrupted()} alone catches it; this walks the whole
     * cause chain for any of the usual "this was actually a deliberate interruption" markers.
     */
    private boolean isCancellation(Exception e) {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException
                    || cause instanceof java.io.InterruptedIOException
                    || cause instanceof java.nio.channels.ClosedByInterruptException) {
                return true;
            }
        }
        return false;
    }

    private void showErrorDialog(ModpackSummary modpack, Exception e) {
        SwingUtilities.invokeLater(() -> {
            String message = "Impossible d’installer/lancer « " + modpack.name() + " » : " + e.getMessage();
            if (offlineAction() != null && !offlineMode) {
                int choice = javax.swing.JOptionPane.showOptionDialog(shellFrame, message, "Erreur",
                        javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.ERROR_MESSAGE, null,
                        new String[]{"Mode hors connexion", "Fermer"}, "Mode hors connexion");
                if (choice == 0) enterOfflineMode();
            } else javax.swing.JOptionPane.showMessageDialog(shellFrame, message, "Erreur", javax.swing.JOptionPane.ERROR_MESSAGE);
        });
    }

    /** Clears the locally-recorded manifest so the next sync re-verifies/re-downloads everything, then re-installs (no launch). */
    private void repairModpack(ModpackSummary modpack, ModpackManifest manifest) {
        if (localOnly(modpack.slug())) return;
        // Asked before anything starts, and on the EDT: a repair re-downloads the pack, and the
        // only genuinely destructive part of that is the player's own files. Answered "no" by
        // default - see askWhetherToResetPlayerFiles.
        boolean resetPlayerFiles = askWhetherToResetPlayerFiles(modpack);

        if (!instanceStates.beginInstalling(modpack.slug())) {
            return;
        }
        withDetailPage(modpack.slug(), page -> page.setBusy(true));
        setCardBusy(modpack.slug(), true);
        java.util.concurrent.Future<?> task = backgroundExecutor.submit(() -> {
            try {
                new LocalManifestStore().clear(dirs.instance(modpack.slug()));
                prepareInstallation(modpack, manifest, resetPlayerFiles);
                localCatalog.rememberSummary(modpack);
                localCatalog.saveManifest(modpack.slug(), manifest);
                withDetailPage(modpack.slug(), page -> {
                    page.setInstalled(true);
                    page.setUpdateAvailable(false);
                    page.setStatus("Réparation terminée.");
                    page.setProgress(-1);
                });
                setCardInstalled(modpack.slug(), true);
                setCardUpdateAvailable(modpack.slug(), false);
            } catch (Exception e) {
                if (isCancellation(e)) {
                    Log.warn(LauncherApp.class, "Repair cancelled for " + modpack.slug());
                    withDetailPage(modpack.slug(), page -> {
                        page.setStatus("Annulé.");
                        page.setProgress(-1);
                    });
                } else {
                    Log.error(LauncherApp.class, "Repair failed for " + modpack.slug(), e);
                    withDetailPage(modpack.slug(), page -> {
                        page.setError("Erreur : " + e.getMessage());
                        page.setProgress(-1);
                    });
                }
            } finally {
                // Repair never launches the game, so unlike doInstallAndLaunch's clear this one
                // is unconditional (still guarded by clearIfInstalling itself) - there's no
                // RUNNING transition here to avoid clobbering.
                instanceStates.clearIfInstalling(modpack.slug());
                withDetailPage(modpack.slug(), page -> page.setBusy(false));
                setCardBusy(modpack.slug(), false);
            }
        });
        instanceStates.attachInstallTask(modpack.slug(), task);
    }

    /** Deletes the instance directory entirely - the confirmation dialog already happened in the UI. */
    private void uninstallModpack(ModpackSummary modpack) {
        backgroundExecutor.execute(() -> {
            try {
                deleteRecursively(dirs.instance(modpack.slug()));
                localCatalog.removeInstalled(modpack.slug());
                withDetailPage(modpack.slug(), page -> {
                    page.setInstalled(false);
                    page.setStatus("Désinstallé.");
                });
                setCardInstalled(modpack.slug(), false);
            } catch (Exception e) {
                Log.error(LauncherApp.class, "Uninstall failed for " + modpack.slug(), e);
                withDetailPage(modpack.slug(), page -> page.setError("Erreur : " + e.getMessage()));
            }
        });
    }

    private void deleteRecursively(java.nio.file.Path dir) throws java.io.IOException {
        if (!java.nio.file.Files.isDirectory(dir)) {
            return;
        }
        try (var paths = java.nio.file.Files.walk(dir)) {
            for (java.nio.file.Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                java.nio.file.Files.delete(path);
            }
        }
    }

    /**
     * Actual on-disk size of the instance directory, not the manifest's declared total -
     * deliberately reflects reality even mid-install (a cancelled/interrupted sync leaves some
     * files present and others not) rather than assuming a successful sync ever completed. {@code
     * null} if there's no instance directory at all yet (never installed).
     */
    private Long installedSizeBytes(java.nio.file.Path instanceDir) {
        if (!java.nio.file.Files.isDirectory(instanceDir)) {
            return null;
        }
        try (var paths = java.nio.file.Files.walk(instanceDir)) {
            return paths.filter(java.nio.file.Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return java.nio.file.Files.size(path);
                        } catch (java.io.IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (java.io.IOException e) {
            Log.warn(LauncherApp.class, "Could not compute installed size for " + instanceDir + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * The offline UUID launched with is derived from the username - if it
     * changed since the cached {@link #playerInfo} was fetched (login or
     * startup), this session's UUID won't match earlier ones, which matters
     * for per-world player data. Just a warning, not a blocker.
     */
    private void warnIfUsernameChanged(String slug, String currentUsername) {
        if (playerInfo == null || playerInfo.username() == null || playerInfo.username().equals(currentUsername)) {
            return;
        }
        String message = "Pseudo changé depuis la connexion (" + playerInfo.username() + " -> " + currentUsername + ") : l'UUID hors-ligne sera différent.";
        Log.warn(LauncherApp.class, message);
        withDetailPage(slug, page -> page.setStatus(message));
    }

    /**
     * Asks whether a repair should also restore the files the pack marked as the player's -
     * {@code DEFAULT}/{@code MERGE} in {@code .filesmetas}, plus the two paths protected by
     * default. Returns false without asking anything when the instance holds none of them.
     *
     * <p>The rules are read from the copy already in the instance rather than fetched: the
     * question is about files sitting on this disk right now, and it must not wait on the network.
     * The affected paths are listed in the prompt, because "réinitialiser mes réglages" means
     * nothing until a player sees that it includes their server list.
     */
    private boolean askWhetherToResetPlayerFiles(ModpackSummary modpack) {
        Path instanceDir = dirs.instance(modpack.slug());
        java.util.List<String> present = new java.util.ArrayList<>();

        try {
            Path metasFile = instanceDir.resolve(FileMetas.FILE_NAME);
            FileMetas metas = Files.isRegularFile(metasFile)
                    ? FileMetas.parse(Files.readString(metasFile, java.nio.charset.StandardCharsets.UTF_8))
                    : FileMetas.empty();

            for (String path : ManifestDiffer.playerOwnedPaths(metas)) {
                if (Files.exists(instanceDir.resolve(path))) {
                    present.add(path);
                }
            }
        } catch (Exception e) {
            // Unreadable rules must not block a repair; the safe answer is simply "don't reset".
            Log.warn(LauncherApp.class, "Could not list player-owned files for " + modpack.slug() + ": " + e.getMessage());
            return false;
        }

        if (present.isEmpty()) {
            return false;
        }

        Object[] options = {"Garder mes fichiers", "Tout réinitialiser"};
        int choice = javax.swing.JOptionPane.showOptionDialog(shellFrame,
                "La réparation va retélécharger les fichiers du modpack.\n\n"
                        + "Ces fichiers t'appartiennent et seront conservés :\n"
                        + "  • " + String.join("\n  • ", present) + "\n\n"
                        + "Veux-tu aussi les remettre à leur version d'origine ?\n"
                        + "Tes réglages et ta liste de serveurs seraient alors perdus.",
                "Réparer " + modpack.name(),
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        return choice == 1;
    }

    private void prepareInstallation(ModpackSummary modpack,ModpackManifest manifest,boolean resetPlayerFiles) throws Exception {
        java.util.List<String> paths=new java.util.ArrayList<>();
        for(var file:manifest.files())paths.add(file.path());
        new LocalManifestStore().load(dirs.instance(modpack.slug())).ifPresent(previous->paths.addAll(previous.pathToHash().keySet()));
        withDetailPage(modpack.slug(),page->page.setStatus("Préparation du modpack et de Minecraft…"));
        try {
            if(mc.snakenest.launcher.game.ParallelPreparation.canOverlap(paths)) {
                mc.snakenest.launcher.game.ParallelPreparation.run(
                        barrier->syncModpackFiles(modpack,manifest,resetPlayerFiles,barrier),
                        ()->installGame(modpack,manifest));
            } else {
                syncModpackFiles(modpack,manifest,resetPlayerFiles);
                installGame(modpack,manifest);
            }
        } finally {withDetailPage(modpack.slug(),ModpackDetailPage::clearTransferProgress);}
    }

    private void syncModpackFiles(ModpackSummary modpack, ModpackManifest manifest) throws Exception {
        syncModpackFiles(modpack, manifest, false);
    }

    /**
     * @param resetPlayerFiles also restore the files the pack marked DEFAULT/MERGE - only ever true
     *                         when the player answered yes to the repair prompt
     */
    private void syncModpackFiles(ModpackSummary modpack, ModpackManifest manifest, boolean resetPlayerFiles) throws Exception {
        syncModpackFiles(modpack,manifest,resetPlayerFiles,()->{});
    }
    private void syncModpackFiles(ModpackSummary modpack,ModpackManifest manifest,boolean resetPlayerFiles,
            mc.snakenest.launcher.game.ParallelPreparation.Barrier beforeApplication) throws Exception {
        withDetailPage(modpack.slug(), page -> {
            page.setTransferProgress("modpack","Modpack · vérification des fichiers…",0);
        });
        var syncEngine = new ModpackSyncEngine(
                new mc.snakenest.launcher.modpack.ModpackFileDownloader(modpackApi, new mc.snakenest.launcher.net.HttpJsonClient()),
                new LocalManifestStore());
        syncEngine.sync(modpack.slug(), dirs.instance(modpack.slug()), manifest, session, new mc.snakenest.launcher.modpack.SyncProgressListener() {
            @Override
            public void beforeFileApplication() throws InterruptedException {
                withDetailPage(modpack.slug(),page->page.setTransferProgress("modpack","Modpack · téléchargé, attente de Minecraft…",1));
                beforeApplication.await();
            }
            @Override public void onFileStarted(String path, long size) {
                withDetailPage(modpack.slug(),page->page.setTransferProgress("files","Fichiers du modpack · "+path,Double.NaN));
            }

            @Override
            public void onDownloadProgress(long receivedBytes, long totalBytes) {
                withDetailPage(modpack.slug(), page -> {
                    page.setTransferProgress("modpack","Modpack · "
                            + mc.snakenest.launcher.util.HumanSize.format(receivedBytes) + " / "
                            + mc.snakenest.launcher.util.HumanSize.format(totalBytes),
                            totalBytes>0?Math.min(1.0,(double)receivedBytes/totalBytes):0);
                });
            }

            @Override
            public void onFileDone(String path) {
            }

            @Override
            public void onDeleted(String path) {
            }
        }, resetPlayerFiles);
        withDetailPage(modpack.slug(),page->{page.setTransferProgress("modpack","Modpack · terminé",1);page.clearTransferProgress("files");});
    }

    private void installGame(ModpackSummary modpack, ModpackManifest manifest) throws Exception {
        withDetailPage(modpack.slug(),page->page.setTransferProgress("game","Minecraft · préparation…",Double.NaN));
        Path instanceDir = dirs.instance(modpack.slug());
        AssetStorageMode assetStorageMode = configStore.load().assetStorageMode();

        // Hardlinks whatever's already in the shared pool into the instance first, so
        // FlowUpdater's own "already present, skip" check does the actual sharing - see
        // SharedAssetsLinker's own Javadoc for why this needs no fork of FlowUpdater/
        // OpenLauncherLib. No-op for AssetStorageMode.PER_MODPACK.
        boolean sharingWorked = assetsLinker.preSeed(instanceDir, assetStorageMode);

        InstallRequest request = new InstallRequest(manifest.mcVersion(), manifest.modLoader(), manifest.loaderVersion(), instanceDir);
        gameInstallService.install(request, new GameInstallListener() {
            @Override
            public void onStepStarted(InstallStep step) {
                withDetailPage(modpack.slug(), page->page.setTransferProgress("game","Minecraft · "+describeInstallStep(step),Double.NaN));
            }

            @Override
            public void onProgress(long bytesDownloaded, long bytesTotal) {
                if (bytesTotal > 0) {
                    withDetailPage(modpack.slug(), page->page.setTransferProgress("game",null,(double)bytesDownloaded/bytesTotal));
                }
            }
        });

        // Moves whatever FlowUpdater just downloaded fresh into the pool and hardlinks it back,
        // so the next modpack/reinstall benefits too.
        sharingWorked &= assetsLinker.absorb(instanceDir, assetStorageMode);

        if (!sharingWorked) {
            notifyHardlinkFallback();
        }

        withDetailPage(modpack.slug(),page->page.setTransferProgress("game","Minecraft · terminé",1));
    }

    private void launchGame(ModpackSummary modpack, ModpackManifest manifest, String username) throws Exception {
        launchGame(modpack, manifest, username, false);
    }

    private void launchGame(ModpackSummary modpack, ModpackManifest manifest, String username, boolean localOnly) throws Exception {
        ModpackSettings settings = modpackSettingsStore.load(modpack.slug(),
                ModpackSettings.defaults(manifest.defaultMemoryMb(), manifest.defaultJvmArgs()));
        Path instanceDir = dirs.instance(modpack.slug());

        checkModBlacklist(modpack, instanceDir, localOnly);

        // The game can't read its own .cryptlist files while they're encrypted, so the plaintext
        // window is opened here and closed again in onExit below - see SensitiveFileVault. No-op
        // without a PIN (nothing is ever encrypted in that case).
        SecretKey key = pinKey;
        if (key != null) {
            SensitiveFileVault.decryptAll(instanceDir, key);
        }

        LaunchRequest request = new LaunchRequest(
                username,
                OfflineUuids.forUsername(username),
                instanceDir,
                manifest.mcVersion(),
                manifest.modLoader(),
                manifest.loaderVersion(),
                settings.memoryMb(),
                settings.extraJvmArgs(),
                // This modpack's own choice if it has one, otherwise the launcher-wide default,
                // otherwise null for the launcher's own JVM - see JavaRuntimes.
                JavaRuntimes.resolve(settings.javaPath(), configStore.load().defaultJavaPath()),
                session.key().seedHex(),
                localOnly
        );
        Process process;
        try { process = gameLaunchService.launch(request); }
        catch (Exception failure) {
            if (key != null) try { SensitiveFileVault.encryptAll(instanceDir, key); }
            catch (Exception sealFailure) { failure.addSuppressed(sealFailure); }
            throw failure;
        }
        java.time.Instant launchedAt = java.time.Instant.now();
        instanceStates.transitionToRunning(modpack.slug(), process);
        applyLaunchWindowActionOnLaunch();
        withDetailPage(modpack.slug(), page -> page.setRunning(true));
        setCardRunning(modpack.slug(), true);
        if (discordPresence != null) {
            discordPresence.setPlaying(modpack.name());
        }
        process.onExit().thenRun(() -> {
            // Re-seals the .cryptlist files the launch just decrypted. Runs before the UI updates
            // so "the game is stopped" and "the secrets are sealed again" become true together.
            SecretKey exitKey = pinKey;
            if (exitKey != null) {
                SensitiveFileVault.encryptAll(instanceDir, exitKey);
            }

            // STOPPING means the player clicked "Arreter"/"Tuer" (see stopGame/killGame) -
            // destroy()/destroyForcibly() also produce a non-zero exit that looks like a crash
            // from the outside, so this is what tells the two apart without a dedicated flag.
            boolean playerRequestedStop = instanceStates.get(modpack.slug())
                    .map(state -> state.activity() == InstanceActivity.STOPPING)
                    .orElse(false);
            instanceStates.clearIfProcessMatches(modpack.slug(), process);
            settleWindowAfterInstanceExit();
            // withDetailPage, not a closed-over detailPage parameter: the game can easily still
            // be running minutes after launch, long enough for the user to refresh/leave-and-
            // reopen this modpack's page in the meantime (a brand new ModpackDetailPage instance
            // every time) - looking up the current page by slug here means this always reaches
            // whichever instance is actually on screen, not whichever one existed at launch time.
            withDetailPage(modpack.slug(), page -> page.setRunning(false));
            setCardRunning(modpack.slug(), false);
            if (discordPresence != null) {
                discordPresence.setBrowsing();
            }
            if (!localOnly && !playerRequestedStop) {
                promptGameCrashReport(modpack, instanceDir, process, launchedAt);
            }
        });
    }

    /**
     * Refuses to let the game start if a file under {@code mods/} matches a blacklisted hash (see
     * {@link ModBlacklistEntry}, {@code Api\ModpackBlacklistController} on the site) - global
     * entries and this modpack's own are already merged server-side. Identification is always by
     * hash, never filename (see the entry's own docblock - a rename doesn't help).
     *
     * <p>Tries a fresh fetch first; if the site can't be reached right now, falls back to the last
     * successfully-fetched list for this instance ({@link ModBlacklistCacheStore}) rather than
     * either blocking every launch on a network hiccup or skipping the check entirely. If there's
     * no cache yet either (e.g. the very first launch, offline), the check has nothing to compare
     * against and lets the launch through - an honest, disclosed gap, not a silent one.
     */
    private void checkModBlacklist(ModpackSummary modpack, Path instanceDir) throws Exception {
        checkModBlacklist(modpack, instanceDir, false);
    }

    private void checkModBlacklist(ModpackSummary modpack, Path instanceDir, boolean localOnly) throws Exception {
        List<ModBlacklistEntry> entries;
        try {
            if (localOnly) entries = modBlacklistCache.load(instanceDir).orElse(List.of());
            else {
            entries = modpackApi.getBlacklist(modpack.slug(), session);
            modBlacklistCache.save(instanceDir, entries);
            }
        } catch (Exception e) {
            Log.warn(LauncherApp.class, "Could not fetch the mod blacklist for " + modpack.slug()
                    + ", falling back to the local cache: " + e.getMessage());
            entries = modBlacklistCache.load(instanceDir).orElse(List.of());
        }

        if (entries.isEmpty()) {
            return;
        }

        Map<String, ModBlacklistEntry> byHash = new java.util.HashMap<>();
        for (ModBlacklistEntry entry : entries) {
            byHash.put(entry.hash(), entry);
        }

        Path modsDir = instanceDir.resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return;
        }

        List<String> blocked = new ArrayList<>();
        try (var files = Files.list(modsDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String hash;
                try (var in = Files.newInputStream(file)) {
                    hash = Sha256.hex(in);
                }
                ModBlacklistEntry match = byHash.get(hash);
                if (match != null) {
                    blocked.add(file.getFileName() + " (" + (match.reason() != null ? match.reason() : "interdit") + ")");
                }
            }
        }

        if (!blocked.isEmpty()) {
            throw new ModBlacklistedException("Mod(s) interdit(s) détecté(s) : " + String.join(", ", blocked)
                    + ". Retire-le(s) via \"Gérer les mods\" avant de relancer.");
        }
    }

    /**
     * Applies the "Action au lancement" setting (see {@link LaunchWindowAction}) right after a game
     * starts: {@code STAY_OPEN} does nothing, the other two both hide the window immediately - the
     * difference between them (re-show vs. actually quit) only plays out later, in
     * {@link #settleWindowAfterInstanceExit}, once every running modpack has exited. Hiding here
     * rather than deciding close-vs-not up front is what keeps a second modpack launched while this
     * one is still running from prematurely closing the launcher.
     */
    private void applyLaunchWindowActionOnLaunch() {
        LaunchWindowAction action = configStore.load().launchWindowAction();
        if (action == LaunchWindowAction.STAY_OPEN) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (shellFrame != null) {
                shellFrame.setVisible(false);
            }
        });
    }

    /**
     * Called after every game process exit, once {@code instanceStates} has already been updated
     * for it. Only actually re-shows or closes the window once {@link InstanceStateRegistry#isAnyActive()}
     * is false - i.e. every modpack this launcher knows about (installing or running) has finished -
     * never on the first of several to exit while another is still going. This is the only
     * interpretation that can't kill/hide-forever a modpack that's still running just because a
     * different one launched alongside it just finished.
     */
    private void settleWindowAfterInstanceExit() {
        LaunchWindowAction action = configStore.load().launchWindowAction();
        if (action == LaunchWindowAction.STAY_OPEN || instanceStates.isAnyActive()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (action == LaunchWindowAction.HIDE_AND_RESHOW) {
                if (shellFrame != null) {
                    shellFrame.setVisible(true);
                    forceToFront(shellFrame);
                }
            } else if (action == LaunchWindowAction.CLOSE_ON_LAUNCH) {
                if (shellFrame != null) {
                    shellFrame.dispose();
                    shellFrame = null;
                }
                // dispose() alone doesn't reliably terminate the JVM (EXIT_ON_CLOSE only fires for a
                // real WINDOW_CLOSING event, and other subsystems - HTTP clients, Discord RPC - may
                // keep non-daemon threads alive) - an explicit exit is unambiguous. The existing
                // shutdown hook (see the constructor) still runs normally, Discord RPC included.
                System.exit(0);
            }
        });
    }

    /**
     * Offers to report a genuine game crash (non-zero exit the player didn't ask for). A crash
     * report file written to {@code crash-reports/} after launch is attached as technical detail
     * when Forge/Fabric/vanilla produced one; the exit code alone is still reported without it.
     */
    private void promptGameCrashReport(ModpackSummary modpack, Path instanceDir, Process process, java.time.Instant launchedAt) {
        int exitCode = process.exitValue();
        if (exitCode == 0) {
            return;
        }
        Path crashReportFile = findCrashReportSince(instanceDir, launchedAt);
        String details = crashReportFile != null ? readCrashReport(crashReportFile) : ("Code de sortie : " + exitCode);
        String summary = "Le jeu (" + modpack.name() + ") s'est arrêté de façon inattendue (code " + exitCode + ").";
        SwingUtilities.invokeLater(() -> {
            String description = CrashPromptDialog.ask(shellFrame, summary);
            if (description != null) {
                backgroundExecutor.execute(() -> bugReportApi.submit(
                        BugReportType.GAME_CRASH, summary, description, details, modpack.slug()));
            }
        });
    }

    /** Most recently modified file under {@code instanceDir/crash-reports} written after {@code since}, or {@code null}. */
    private Path findCrashReportSince(Path instanceDir, java.time.Instant since) {
        Path crashReports = instanceDir.resolve("crash-reports");
        if (!Files.isDirectory(crashReports)) {
            return null;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(crashReports)) {
            return stream.filter(Files::isRegularFile)
                    .filter(file -> isNewerThan(file, since))
                    .max(java.util.Comparator.comparing(this::lastModifiedOrEpoch))
                    .orElse(null);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private boolean isNewerThan(Path file, java.time.Instant since) {
        return lastModifiedOrEpoch(file).isAfter(since);
    }

    private java.time.Instant lastModifiedOrEpoch(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (java.io.IOException e) {
            return java.time.Instant.EPOCH;
        }
    }

    /** Capped read - a crash report is normally a few KB of text, never worth risking a huge allocation over. */
    private String readCrashReport(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int cap = Math.min(bytes.length, 200_000);
            return new String(bytes, 0, cap, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            Log.warn(LauncherApp.class, "Could not read crash report " + file.getFileName() + ": " + e.getMessage());
            return "Impossible de lire le rapport de crash.";
        }
    }

    private String describeInstallStep(InstallStep step) {
        return switch (step) {
            case READING_VERSION_INFO -> "Lecture des informations de version...";
            case DOWNLOADING_LIBRARIES -> "Téléchargement des bibliothèques...";
            case DOWNLOADING_ASSETS -> "Téléchargement des assets...";
            case EXTRACTING_NATIVES -> "Extraction des fichiers natifs...";
            case INSTALLING_MOD_LOADER -> "Installation du mod loader...";
            case FINALIZING -> "Finalisation...";
            case DONE -> "Terminé.";
        };
    }

    /** Clears the session and shows the login window again - never quits the whole app. */
    private void logout() {
        // Best-effort and fired off in the background - a failure here (offline, server down)
        // must never block logging out locally, but this is always attempted: skipping it would
        // leave a key that leaked before this point (e.g. copied off disk) valid forever
        // server-side, even after the legitimate user logs out.
        PlayerSession sessionToRevoke = session;
        if (sessionToRevoke != null && !offlineMode) {
            backgroundExecutor.execute(() -> {
                try {
                    authApi.revokeKey(sessionToRevoke);
                } catch (Exception e) {
                    Log.warn(LauncherApp.class, "Could not revoke the launcher key server-side: " + e.getMessage());
                }
            });
        }

        keyStorage.delete();
        var config = configStore.load();
        config.setPlayerId(null);
        configStore.save(config);

        session = null;
        playerInfo = null;
        playerAvatar = null;

        if (shellFrame != null) {
            shellFrame.dispose();
            shellFrame = null;
        }
        showLogin();
    }

    /** Suspend only the in-memory session; preserve the device key and player id for retry. */
    private void suspendRejectedSession() {
        session = null;
        playerInfo = null;
        playerAvatar = null;
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            Log.warn(LauncherApp.class, "Could not open browser: " + e.getMessage());
        }
    }

    private void openFolder(java.nio.file.Path path) {
        try {
            java.nio.file.Files.createDirectories(path);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
            }
        } catch (Exception e) {
            Log.warn(LauncherApp.class, "Could not open folder " + path + ": " + e.getMessage());
        }
    }
}
