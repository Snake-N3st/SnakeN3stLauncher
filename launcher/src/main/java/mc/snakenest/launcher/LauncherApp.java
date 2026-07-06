package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.DeviceAuthService;
import mc.snakenest.launcher.auth.LauncherAuthApiClient;
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
import mc.snakenest.launcher.modpack.ModpackSummary;
import mc.snakenest.launcher.modpack.ModpackSyncEngine;
import mc.snakenest.launcher.news.NewsApiClient;
import mc.snakenest.launcher.news.Post;
import mc.snakenest.launcher.ui.LauncherFrame;
import mc.snakenest.launcher.ui.LoginFrame;
import mc.snakenest.launcher.ui.NavTarget;
import mc.snakenest.launcher.ui.ThemeController;
import mc.snakenest.launcher.ui.account.AccountPopover;
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
import java.net.URI;
import java.util.List;
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
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "launcher-background");
        t.setDaemon(true);
        return t;
    });

    private PlayerSession session;

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
    }

    void start() {
        Ed25519KeyPair storedKey = loadStoredKeyPair();
        Long storedPlayerId = configStore.load().playerId();

        if (storedKey != null && storedPlayerId != null) {
            session = new PlayerSession(storedKey, storedPlayerId, clientId);
            showShell();
        } else {
            showLogin();
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
            showShell();
        });
        loginFrame.setVisible(true);
    }

    private void showShell() {
        LauncherFrame frame = new LauncherFrame(
                () -> openUrl("https://twitch.tv"),
                () -> openUrl("https://discord.com"),
                this::showAccountPopover
        );

        frame.addPage(NavTarget.SETTINGS, new SettingsPage(
                themeController.current(),
                themeController::switchTo,
                () -> openFolder(dirs.root()),
                this::logout
        ));

        frame.addPage(NavTarget.NEWS, buildNewsSection());
        frame.addPage(NavTarget.MODPACKS, buildModpackSection(frame));

        frame.navigateTo(NavTarget.MODPACKS);
        frame.setVisible(true);
    }

    private void showAccountPopover(java.awt.Component anchor) {
        backgroundExecutor.execute(() -> {
            String username;
            String role;
            String email;
            try {
                username = authApi.fetchUsername(session);
                role = authApi.fetchRole(session);
                email = authApi.fetchEmail(session);
            } catch (Exception e) {
                Log.warn(LauncherApp.class, "Could not fetch player info: " + e.getMessage());
                return;
            }
            String finalUsername = username;
            String finalRole = role;
            String finalEmail = email;
            SwingUtilities.invokeLater(() -> AccountPopover.show(anchor, finalUsername, finalRole, finalEmail, this::logout));
        });
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
        }));
        sectionHolder[0] = new ModpackSectionPage(emptyList);

        backgroundExecutor.execute(() -> {
            try {
                List<ModpackSummary> modpacks = modpackApi.listModpacks(session);
                Set<String> installed = installedSlugs(modpacks);
                SwingUtilities.invokeLater(() -> {
                    ModpackListPage realList = new ModpackListPage(new ModpackListViewModel(modpacks, installed,
                            modpack -> showModpackDetail(frame, sectionHolder[0], modpack)));
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

    private void showModpackDetail(LauncherFrame frame, ModpackSectionPage section, ModpackSummary modpack) {
        Runnable back = () -> {
            section.showList();
            frame.navigateTo(NavTarget.MODPACKS);
        };

        backgroundExecutor.execute(() -> {
            try {
                ModpackManifest manifest = modpackApi.getManifest(modpack.slug(), null, session);
                SwingUtilities.invokeLater(() -> {
                    ModpackDetailViewModel viewModel = new ModpackDetailViewModel(
                            modpack.name(),
                            modpack.description(),
                            manifest.changelog(),
                            manifest.totalSize(),
                            manifest.files().size(),
                            () -> installAndLaunch(modpack, manifest),
                            () -> {
                            },
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
                syncModpackFiles(modpack, manifest, detailPage);
                installGame(modpack, manifest, detailPage);
                String username = authApi.fetchUsername(session);
                launchGame(modpack, manifest, username);
            } catch (Exception e) {
                Log.error(LauncherApp.class, "Install/launch failed for " + modpack.slug(), e);
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
        LaunchRequest request = new LaunchRequest(
                username,
                OfflineUuids.forUsername(username),
                dirs.instance(modpack.slug()),
                manifest.mcVersion(),
                manifest.modLoader(),
                manifest.loaderVersion(),
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

    private void logout() {
        keyStorage.delete();
        var config = configStore.load();
        config.setPlayerId(null);
        configStore.save(config);
        System.exit(0);
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
