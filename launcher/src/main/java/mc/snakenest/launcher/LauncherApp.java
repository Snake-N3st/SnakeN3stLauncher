package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.ClientInfo;
import mc.snakenest.launcher.auth.DeviceAuthService;
import mc.snakenest.launcher.auth.LauncherAuthApiClient;
import mc.snakenest.launcher.auth.PlayerInfo;
import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.crypto.Ed25519KeyPair;
import mc.snakenest.launcher.crypto.KeyStorage;
import mc.snakenest.launcher.game.GameInstallListener;
import mc.snakenest.launcher.game.GameInstallService;
import mc.snakenest.launcher.game.GameLaunchService;
import mc.snakenest.launcher.game.InstallRequest;
import mc.snakenest.launcher.game.InstallStep;
import mc.snakenest.launcher.game.LaunchRequest;
import mc.snakenest.launcher.game.OfflineUuids;
import mc.snakenest.launcher.modpack.ModpackApiClient;
import mc.snakenest.launcher.modpack.ModpackManifest;
import mc.snakenest.launcher.modpack.ModpackSettings;
import mc.snakenest.launcher.modpack.ModpackSettingsStore;
import mc.snakenest.launcher.modpack.ModpackSummary;
import mc.snakenest.launcher.modpack.ModpackSyncEngine;
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
import mc.snakenest.launcher.ui.settings.SettingsPage;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.Log;

import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.net.URI;
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
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "launcher-background");
        t.setDaemon(true);
        return t;
    });

    private PlayerSession session;
    private ClientInfo clientInfo;
    private BufferedImage clientLogo;
    // Fetched once at login/startup (never on-demand when the account popover is opened - see ui/README.md).
    private PlayerInfo playerInfo;
    private BufferedImage playerAvatar;
    private LauncherFrame shellFrame;

    LauncherApp(AppDirs dirs, URI baseUrl, String clientId, ConfigStore configStore, ThemeController themeController,
                LauncherAuthApiClient authApi, ModpackApiClient modpackApi, NewsApiClient newsApi, KeyStorage keyStorage,
                GameInstallService gameInstallService, GameLaunchService gameLaunchService) {
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
        fetchClientBrandingBlocking();

        Ed25519KeyPair storedKey = loadStoredKeyPair();
        Long storedPlayerId = configStore.load().playerId();

        if (storedKey != null && storedPlayerId != null) {
            session = new PlayerSession(storedKey, storedPlayerId, clientId);
            fetchPlayerInfoBlocking();
            SwingUtilities.invokeLater(this::showShell);
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

    /** Best-effort; leaves {@link #clientInfo}/{@link #clientLogo} {@code null} on any failure. */
    private void fetchClientBrandingBlocking() {
        try {
            clientInfo = authApi.fetchClientInfo(clientId);
            clientLogo = RemoteImages.tryLoad(clientInfo.image());
        } catch (Exception e) {
            Log.warn(LauncherApp.class, "Could not fetch client branding: " + e.getMessage());
        }
    }

    /** Best-effort; leaves {@link #playerInfo}/{@link #playerAvatar} {@code null} on any failure. */
    private void fetchPlayerInfoBlocking() {
        try {
            playerInfo = authApi.fetchPlayerInfo(session);
            playerAvatar = RemoteImages.tryLoad(playerInfo.avatar());
        } catch (Exception e) {
            Log.warn(LauncherApp.class, "Could not fetch player info: " + e.getMessage());
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
                return;
            }
            session = new PlayerSession(key, playerId, clientId);
            backgroundExecutor.execute(() -> {
                fetchPlayerInfoBlocking();
                SwingUtilities.invokeLater(this::showShell);
            });
        });
        if (clientInfo != null) {
            loginFrame.setClientInfo(clientInfo.name(), clientLogo);
        }
        if (clientLogo != null) {
            loginFrame.setIconImage(clientLogo);
        }
        loginFrame.setVisible(true);
    }

    private void showShell() {
        LauncherFrame frame = new LauncherFrame(this::showAccountPopover);
        this.shellFrame = frame;

        frame.addPage(NavTarget.SETTINGS, new SettingsPage(
                themeController.current(),
                themeController::switchTo,
                () -> openFolder(dirs.root()),
                this::logout
        ));

        frame.addPage(NavTarget.NEWS, buildNewsSection());
        frame.addPage(NavTarget.MODPACKS, buildModpackSection(frame));

        frame.navigateTo(NavTarget.MODPACKS);
        frame.setLogo(clientLogo);
        frame.setAccountAvatar(playerAvatar);
        if (clientLogo != null) {
            frame.setIconImage(clientLogo);
        }
        frame.setVisible(true);
    }

    /** Shows the cached {@link #playerInfo}/{@link #playerAvatar} - never makes a network call itself. */
    private void showAccountPopover(java.awt.Component anchor) {
        String username = playerInfo != null ? playerInfo.username() : "?";
        String role = playerInfo != null ? playerInfo.role() : null;
        String email = playerInfo != null ? playerInfo.email() : null;
        AccountPopover.show(anchor, username, role, email, playerAvatar, this::logout);
    }

    private NewsSectionPage buildNewsSection() {
        NewsSectionPage[] sectionHolder = new NewsSectionPage[1];
        NewsListPage listPage = new NewsListPage(new NewsListViewModel(List.of(), post -> {
        }));
        sectionHolder[0] = new NewsSectionPage(listPage);

        backgroundExecutor.execute(() -> {
            try {
                List<Post> posts = newsApi.listPosts();
                SwingUtilities.invokeLater(() -> {
                    NewsListPage realList = new NewsListPage(new NewsListViewModel(posts, post ->
                            sectionHolder[0].showDetail(new NewsDetailPage(post, () -> openUrl(post.url())))));
                    sectionHolder[0].replaceList(realList);
                });
            } catch (Exception e) {
                Log.warn(LauncherApp.class, "Could not load news: " + e.getMessage());
            }
        });
        return sectionHolder[0];
    }

    private ModpackSectionPage buildModpackSection(LauncherFrame frame) {
        ModpackSectionPage[] sectionHolder = new ModpackSectionPage[1];
        ModpackListPage emptyList = new ModpackListPage(new ModpackListViewModel(List.of(), Set.of(), m -> {
        }, m -> {
        }));
        sectionHolder[0] = new ModpackSectionPage(emptyList);

        backgroundExecutor.execute(() -> {
            try {
                List<ModpackSummary> modpacks = modpackApi.listModpacks(session);
                Set<String> installed = installedSlugs(modpacks);
                Map<String, BufferedImage> logos = fetchModpackLogos(modpacks);
                SwingUtilities.invokeLater(() -> {
                    ModpackListPage realList = new ModpackListPage(new ModpackListViewModel(modpacks, installed, logos,
                            modpack -> showModpackDetail(frame, sectionHolder[0], modpack),
                            this::quickInstallAndLaunch));
                    sectionHolder[0].replaceList(realList);
                });
            } catch (Exception e) {
                Log.warn(LauncherApp.class, "Could not load modpacks: " + e.getMessage());
            }
        });
        return sectionHolder[0];
    }

    private Set<String> installedSlugs(List<ModpackSummary> modpacks) {
        return modpacks.stream()
                .map(ModpackSummary::slug)
                .filter(slug -> java.nio.file.Files.isDirectory(dirs.instance(slug)))
                .collect(Collectors.toSet());
    }

    /** Fetches every modpack's logo in parallel (best-effort - {@code null} entries just fall back to the letter placeholder). */
    private Map<String, BufferedImage> fetchModpackLogos(List<ModpackSummary> modpacks) {
        Map<String, java.util.concurrent.CompletableFuture<BufferedImage>> futures = new java.util.HashMap<>();
        for (ModpackSummary modpack : modpacks) {
            futures.put(modpack.slug(), java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> RemoteImages.tryLoad(modpack.image()), backgroundExecutor));
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

        boolean installed = java.nio.file.Files.isDirectory(dirs.instance(modpack.slug()));

        backgroundExecutor.execute(() -> {
            try {
                ModpackManifest manifest = modpackApi.getManifest(modpack.slug(), null, session);
                ModpackSettings settings = modpackSettingsStore.load(modpack.slug());
                BufferedImage logo = RemoteImages.tryLoad(modpack.image());
                SwingUtilities.invokeLater(() -> {
                    ModpackDetailViewModel viewModel = new ModpackDetailViewModel(
                            modpack.name(),
                            modpack.description(),
                            manifest.changelog(),
                            manifest.totalSize(),
                            manifest.files().size(),
                            installed,
                            logo,
                            settings,
                            () -> installAndLaunch(modpack, manifest),
                            () -> repairModpack(modpack, manifest),
                            () -> uninstallModpack(modpack),
                            newSettings -> modpackSettingsStore.save(modpack.slug(), newSettings),
                            () -> openFolder(dirs.instance(modpack.slug())),
                            back
                    );
                    ModpackDetailPage detail = new ModpackDetailPage(viewModel);
                    currentDetailPage = detail;
                    section.showDetail(detail);
                    frame.showBackButton(modpack.name(), back);
                });
            } catch (Exception e) {
                Log.warn(LauncherApp.class, "Could not fetch manifest for " + modpack.slug() + ": " + e.getMessage());
            }
        });
    }

    // Set right before install/launch starts, so its async progress callbacks have somewhere to report to.
    private volatile ModpackDetailPage currentDetailPage;

    private void installAndLaunch(ModpackSummary modpack, ModpackManifest manifest) {
        ModpackDetailPage detailPage = currentDetailPage;
        if (detailPage != null) {
            detailPage.setDemarrerEnabled(false);
        }

        backgroundExecutor.execute(() -> {
            try {
                doInstallAndLaunch(modpack, manifest, detailPage);
            } finally {
                if (detailPage != null) {
                    detailPage.setDemarrerEnabled(true);
                }
            }
        });
    }

    /** Same action as the card's play/download icon in the list, without opening the detail page first. */
    private void quickInstallAndLaunch(ModpackSummary modpack) {
        backgroundExecutor.execute(() -> {
            try {
                ModpackManifest manifest = modpackApi.getManifest(modpack.slug(), null, session);
                doInstallAndLaunch(modpack, manifest, null);
            } catch (Exception e) {
                Log.error(LauncherApp.class, "Quick install/launch failed for " + modpack.slug(), e);
            }
        });
    }

    /** The shared body of {@link #installAndLaunch}/{@link #quickInstallAndLaunch} - {@code detailPage} may be {@code null}. */
    private void doInstallAndLaunch(ModpackSummary modpack, ModpackManifest manifest, ModpackDetailPage detailPage) {
        try {
            syncModpackFiles(modpack, manifest, detailPage);
            installGame(modpack, manifest, detailPage);
            if (detailPage != null) {
                detailPage.setInstalled(true);
            }
            String username = authApi.fetchUsername(session);
            warnIfUsernameChanged(username, detailPage);
            launchGame(modpack, manifest, username);
        } catch (Exception e) {
            Log.error(LauncherApp.class, "Install/launch failed for " + modpack.slug(), e);
            if (detailPage != null) {
                detailPage.setStatus("Erreur : " + e.getMessage());
                detailPage.setProgress(-1);
            }
        }
    }

    /** Clears the locally-recorded manifest so the next sync re-verifies/re-downloads everything, then re-installs (no launch). */
    private void repairModpack(ModpackSummary modpack, ModpackManifest manifest) {
        ModpackDetailPage detailPage = currentDetailPage;
        if (detailPage != null) {
            detailPage.setDemarrerEnabled(false);
        }
        backgroundExecutor.execute(() -> {
            try {
                new mc.snakenest.launcher.modpack.LocalManifestStore().clear(dirs.instance(modpack.slug()));
                syncModpackFiles(modpack, manifest, detailPage);
                installGame(modpack, manifest, detailPage);
                if (detailPage != null) {
                    detailPage.setInstalled(true);
                    detailPage.setStatus("Reparation terminee.");
                    detailPage.setProgress(-1);
                }
            } catch (Exception e) {
                Log.error(LauncherApp.class, "Repair failed for " + modpack.slug(), e);
                if (detailPage != null) {
                    detailPage.setStatus("Erreur : " + e.getMessage());
                    detailPage.setProgress(-1);
                }
            } finally {
                if (detailPage != null) {
                    detailPage.setDemarrerEnabled(true);
                }
            }
        });
    }

    /** Deletes the instance directory entirely - the confirmation dialog already happened in the UI. */
    private void uninstallModpack(ModpackSummary modpack) {
        ModpackDetailPage detailPage = currentDetailPage;
        backgroundExecutor.execute(() -> {
            try {
                deleteRecursively(dirs.instance(modpack.slug()));
                if (detailPage != null) {
                    detailPage.setInstalled(false);
                    detailPage.setStatus("Desinstalle.");
                }
            } catch (Exception e) {
                Log.error(LauncherApp.class, "Uninstall failed for " + modpack.slug(), e);
                if (detailPage != null) {
                    detailPage.setStatus("Erreur : " + e.getMessage());
                }
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
     * The offline UUID launched with is derived from the username - if it
     * changed since the cached {@link #playerInfo} was fetched (login or
     * startup), this session's UUID won't match earlier ones, which matters
     * for per-world player data. Just a warning, not a blocker.
     */
    private void warnIfUsernameChanged(String currentUsername, ModpackDetailPage detailPage) {
        if (playerInfo == null || playerInfo.username() == null || playerInfo.username().equals(currentUsername)) {
            return;
        }
        String message = "Pseudo change depuis la connexion (" + playerInfo.username() + " -> " + currentUsername + ") : l'UUID hors-ligne sera different.";
        Log.warn(LauncherApp.class, message);
        if (detailPage != null) {
            detailPage.setStatus(message);
        }
    }

    private void syncModpackFiles(ModpackSummary modpack, ModpackManifest manifest, ModpackDetailPage detailPage) throws Exception {
        if (detailPage != null) {
            detailPage.setStatus("Synchronisation des fichiers...");
            detailPage.setProgress(0);
        }
        var syncEngine = new ModpackSyncEngine(new mc.snakenest.launcher.modpack.ModpackFileDownloader(modpackApi), new mc.snakenest.launcher.modpack.LocalManifestStore());
        syncEngine.sync(modpack.slug(), dirs.instance(modpack.slug()), manifest, session, new mc.snakenest.launcher.modpack.SyncProgressListener() {
            @Override
            public void onFileStarted(String path, long size) {
                if (detailPage != null) {
                    detailPage.setStatus("Telechargement : " + path);
                }
            }

            @Override
            public void onFileDone(String path) {
            }

            @Override
            public void onDeleted(String path) {
            }
        });
    }

    private void installGame(ModpackSummary modpack, ModpackManifest manifest, ModpackDetailPage detailPage) throws Exception {
        if (detailPage != null) {
            detailPage.setStatus("Installation du jeu...");
        }
        InstallRequest request = new InstallRequest(manifest.mcVersion(), manifest.modLoader(), manifest.loaderVersion(), dirs.instance(modpack.slug()));
        gameInstallService.install(request, new GameInstallListener() {
            @Override
            public void onStepStarted(InstallStep step) {
                if (detailPage != null) {
                    detailPage.setStatus(describeInstallStep(step));
                }
            }

            @Override
            public void onProgress(long bytesDownloaded, long bytesTotal) {
                if (detailPage != null && bytesTotal > 0) {
                    detailPage.setProgress((double) bytesDownloaded / bytesTotal);
                }
            }
        });
        if (detailPage != null) {
            detailPage.setProgress(-1);
        }
    }

    private void launchGame(ModpackSummary modpack, ModpackManifest manifest, String username) throws Exception {
        ModpackSettings settings = modpackSettingsStore.load(modpack.slug());
        LaunchRequest request = new LaunchRequest(
                username,
                OfflineUuids.forUsername(username),
                dirs.instance(modpack.slug()),
                manifest.mcVersion(),
                manifest.modLoader(),
                manifest.loaderVersion(),
                settings.memoryMb(),
                settings.extraJvmArgs(),
                session.key().seedHex()
        );
        gameLaunchService.launch(request);
    }

    private String describeInstallStep(InstallStep step) {
        return switch (step) {
            case READING_VERSION_INFO -> "Lecture des informations de version...";
            case DOWNLOADING_LIBRARIES -> "Telechargement des bibliotheques...";
            case DOWNLOADING_ASSETS -> "Telechargement des assets...";
            case EXTRACTING_NATIVES -> "Extraction des fichiers natifs...";
            case INSTALLING_MOD_LOADER -> "Installation du mod loader...";
            case FINALIZING -> "Finalisation...";
            case DONE -> "Termine.";
        };
    }

    /** Clears the session and shows the login window again - never quits the whole app. */
    private void logout() {
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
