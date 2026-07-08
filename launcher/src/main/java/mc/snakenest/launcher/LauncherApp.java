package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.ClientInfo;
import mc.snakenest.launcher.auth.DeviceAuthService;
import mc.snakenest.launcher.auth.LauncherAuthApiClient;
import mc.snakenest.launcher.auth.PlayerInfo;
import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.config.LauncherConfig;
import mc.snakenest.launcher.crypto.Ed25519KeyPair;
import mc.snakenest.launcher.crypto.KeyStorage;
import mc.snakenest.launcher.discord.DiscordPresenceService;
import mc.snakenest.launcher.game.GameInstallListener;
import mc.snakenest.launcher.game.GameInstallService;
import mc.snakenest.launcher.game.GameLaunchService;
import mc.snakenest.launcher.game.InstallRequest;
import mc.snakenest.launcher.game.InstallStep;
import mc.snakenest.launcher.game.LaunchRequest;
import mc.snakenest.launcher.game.OfflineUuids;
import mc.snakenest.launcher.modpack.LocalManifestStore;
import mc.snakenest.launcher.modpack.ModpackApiClient;
import mc.snakenest.launcher.modpack.ModpackManifest;
import mc.snakenest.launcher.modpack.ModpackSettings;
import mc.snakenest.launcher.modpack.ModpackSettingsStore;
import mc.snakenest.launcher.modpack.ModpackSummary;
import mc.snakenest.launcher.modpack.ModpackSyncEngine;
import mc.snakenest.launcher.modpack.StoredManifest;
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

    /** The one thing this launcher can be doing to a modpack at a time - never more than one in flight. */
    private enum Activity { INSTALLING, RUNNING, STOPPING }

    /**
     * One immutable snapshot of "what the launcher is currently doing, and to which modpack" -
     * {@code null} when nothing is. Replaces what used to be four separate fields
     * ({@code currentInstallTask}, {@code runningGameProcess}, {@code runningModpackSlug},
     * {@code stopRequestedForRunningGame}) that were each set/cleared independently by whichever
     * method happened to touch them.
     *
     * <p>That split was the actual bug behind "the button still says Demarrer while a modpack is
     * downloading": {@code RUNNING} had a slug tracked (so a freshly (re)built page/card could at
     * least restore that one state), but installing/downloading never got the same treatment -
     * there was no field at all recording "an install is in progress for slug X", so
     * {@code showModpackDetail}/{@code loadModpackList} had nothing to check and always defaulted
     * a new page/card to idle. Every method that used to touch one of the four old fields now
     * replaces this single reference wholesale instead - one atomic fact, read back by the one
     * {@link #restoreActiveState}/{@link #restoreActiveCardState} pair for every activity the
     * same way, instead of a bespoke "if slug matches, call setX" check that's easy to forget
     * writing for the next new state.
     */
    private record ActiveModpack(String slug, Activity activity, java.util.concurrent.Future<?> installTask, Process gameProcess) {
    }

    private volatile ActiveModpack active;

    // The list currently shown, if any - lets card-state pushes (setCardBusy/Running/Installed)
    // reach the right ModpackCardView regardless of whether that modpack's detail page is also
    // open. Replaced wholesale on every (re)load, same "stale reference is a harmless no-op"
    // reasoning as currentDetailPage below.
    private volatile ModpackListViewModel currentListViewModel;
    // null whenever this client has no discord_app_id configured - Discord is then never touched at all.
    private volatile DiscordPresenceService discordPresence;

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

    // A slow/unresponsive server must not leave the launcher showing no window at all for as
    // long as HttpJsonClient's own (much longer, 30s) request timeout - bounding these two
    // startup calls keeps the worst case short, at the cost of a placeholder logo/no cached
    // player info if the server really is that slow (both already degrade gracefully).
    private static final java.time.Duration STARTUP_FETCH_TIMEOUT = java.time.Duration.ofSeconds(4);

    /** Best-effort; leaves {@link #clientInfo}/{@link #clientLogo} {@code null} on any failure or timeout. */
    private void fetchClientBrandingBlocking() {
        try {
            clientInfo = runWithTimeout(() -> authApi.fetchClientInfo(clientId));
            clientLogo = RemoteImages.tryLoad(clientInfo.image());
            connectDiscordPresence();
        } catch (Exception e) {
            Log.warn(LauncherApp.class, "Could not fetch client branding: " + e.getMessage());
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

    /** Best-effort; leaves {@link #playerInfo}/{@link #playerAvatar} {@code null} on any failure or timeout. */
    private void fetchPlayerInfoBlocking() {
        try {
            playerInfo = runWithTimeout(() -> authApi.fetchPlayerInfo(session));
            playerAvatar = RemoteImages.tryLoad(playerInfo.avatar());
        } catch (Exception e) {
            Log.warn(LauncherApp.class, "Could not fetch player info: " + e.getMessage());
        }
    }

    private <T> T runWithTimeout(java.util.concurrent.Callable<T> task) throws Exception {
        java.util.concurrent.Future<T> future = backgroundExecutor.submit(task);
        try {
            return future.get(STARTUP_FETCH_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new java.io.IOException("Timed out after " + STARTUP_FETCH_TIMEOUT, e);
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

        NewsSectionPage newsSection = buildNewsSection(frame);
        ModpackSectionPage modpackSection = buildModpackSection(frame);

        frame.addPage(NavTarget.SETTINGS, new SettingsPage(
                themeController.current(),
                themeController::switchTo,
                configStore.load().discordEnabled(),
                this::setDiscordEnabled,
                () -> openFolder(dirs.root()),
                this::logout
        ));
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
        backgroundExecutor.execute(() -> {
            try {
                List<Post> posts = newsApi.listPosts();
                SwingUtilities.invokeLater(() -> {
                    NewsListPage realList = new NewsListPage(new NewsListViewModel(posts,
                            post -> showNewsDetail(frame, section, post)));
                    section.replaceList(realList);
                });
            } catch (Exception e) {
                Log.warn(LauncherApp.class, "Could not load news: " + e.getMessage());
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
        backgroundExecutor.execute(() -> {
            try {
                List<ModpackSummary> modpacks = modpackApi.listModpacks(session);
                Set<String> installed = installedSlugs(modpacks);
                Set<String> updateAvailable = updateAvailableSlugs(modpacks);
                Map<String, BufferedImage> logos = fetchModpackLogos(modpacks);
                SwingUtilities.invokeLater(() -> {
                    ModpackListViewModel viewModel = new ModpackListViewModel(modpacks, installed, updateAvailable, logos,
                            modpack -> showModpackDetail(frame, section, modpack),
                            this::quickInstallAndLaunch,
                            this::cancelInstall,
                            this::stopGame,
                            this::killGame);
                    currentListViewModel = viewModel;
                    section.replaceList(new ModpackListPage(viewModel));
                    restoreActiveCardState(viewModel);
                });
            } catch (Exception e) {
                Log.warn(LauncherApp.class, "Could not load modpacks: " + e.getMessage());
            }
        });
    }

    private Set<String> installedSlugs(List<ModpackSummary> modpacks) {
        return modpacks.stream()
                .map(ModpackSummary::slug)
                .filter(slug -> java.nio.file.Files.isDirectory(dirs.instance(slug)))
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

        // Immediate visual transition - a click that visibly does nothing until the manifest
        // fetch completes reads as the app hanging, especially on a slow connection.
        section.showDetail(new mc.snakenest.launcher.ui.common.LoadingPanel("Chargement de " + modpack.name() + "..."));
        frame.showBackButton(modpack.name(), back);
        // Re-running this whole method is an idempotent "reload" - safe to bind immediately,
        // even before the manifest fetch below has completed.
        frame.setOnRefresh(() -> showModpackDetail(frame, section, modpack));

        boolean installed = java.nio.file.Files.isDirectory(dirs.instance(modpack.slug()));

        backgroundExecutor.execute(() -> {
            try {
                ModpackManifest manifest = modpackApi.getManifest(modpack.slug(), null, session);
                ModpackSettings settings = modpackSettingsStore.load(modpack.slug());
                BufferedImage logo = RemoteImages.tryLoad(modpack.image());
                boolean updateAvailable = isUpdateAvailable(modpack.slug(), manifest.version());
                SwingUtilities.invokeLater(() -> {
                    ModpackDetailViewModel viewModel = new ModpackDetailViewModel(
                            modpack.slug(),
                            modpack.name(),
                            modpack.description(),
                            manifest.changelog(),
                            manifest.totalSize(),
                            manifest.files().size(),
                            installed,
                            updateAvailable,
                            logo,
                            settings,
                            () -> installAndLaunch(modpack, manifest),
                            () -> repairModpack(modpack, manifest),
                            () -> uninstallModpack(modpack),
                            this::cancelInstall,
                            this::stopGame,
                            this::killGame,
                            newSettings -> modpackSettingsStore.save(modpack.slug(), newSettings),
                            () -> openFolder(dirs.instance(modpack.slug())),
                            back
                    );
                    ModpackDetailPage detail = new ModpackDetailPage(viewModel);
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
                        () -> showModpackDetail(frame, section, modpack),
                        back)));
            }
        });
    }

    // Set right before install/launch starts, so its async progress callbacks have somewhere to report to.
    private volatile ModpackDetailPage currentDetailPage;

    /**
     * Applies whichever activity {@link #active} says is genuinely happening to {@code slug}
     * right now to a freshly built {@code ModpackDetailPage} - called every time one is built
     * ({@code showModpackDetail}, on open/refresh/reopen), covering installing/running/stopping
     * the same way instead of a separate check per activity.
     */
    private void restoreActiveState(String slug, ModpackDetailPage detail) {
        ActiveModpack current = active;
        if (current == null || !current.slug().equals(slug)) {
            return;
        }
        switch (current.activity()) {
            case INSTALLING -> detail.setBusy(true);
            case RUNNING -> detail.setRunning(true);
            case STOPPING -> {
                detail.setRunning(true);
                detail.setStopping(true);
            }
        }
    }

    /** Same as {@link #restoreActiveState}, for a freshly built {@code ModpackListPage}'s cards. */
    private void restoreActiveCardState(ModpackListViewModel viewModel) {
        ActiveModpack current = active;
        if (current == null) {
            return;
        }
        switch (current.activity()) {
            case INSTALLING -> viewModel.setCardBusy(current.slug(), true);
            case RUNNING -> viewModel.setCardRunning(current.slug(), true);
            case STOPPING -> {
                viewModel.setCardRunning(current.slug(), true);
                viewModel.setCardStopping(current.slug(), true);
            }
        }
    }

    /**
     * Clears {@link #active} back to {@code null}, but only if it's still the {@code INSTALLING}
     * episode for {@code slug} - a no-op if it already moved on to {@code RUNNING} (a successful
     * install immediately followed by launching the game) or was never this slug's to begin with.
     * Called wherever an install/repair ends without ever reaching {@link #launchGame} (cancelled
     * or failed) - the success-into-launch path clears nothing here since {@link #launchGame}
     * already replaced {@link #active} with the {@code RUNNING} snapshot by the time this would run.
     */
    private void clearActiveIfInstalling(String slug) {
        ActiveModpack current = active;
        if (current != null && current.activity() == Activity.INSTALLING && current.slug().equals(slug)) {
            active = null;
        }
    }

    /**
     * Fills in {@code task} on the just-created {@code INSTALLING} snapshot for {@code slug} -
     * called right after {@code backgroundExecutor.submit(...)} returns, not before, since the
     * {@link java.util.concurrent.Future} doesn't exist until then. {@link #active} is still set
     * to {@code INSTALLING} (with a {@code null} task) *before* submitting, not after - if it were
     * set only after, the background task itself could in theory already have run far enough to
     * move {@link #active} on to {@code RUNNING} (or clear it, on a very fast failure) by the time
     * the submitting thread got back around to writing it, and that stale write would silently
     * clobber the newer, correct state. Guarded here the same way, so attaching the task can never
     * do that either: a no-op unless {@link #active} is still exactly the bare snapshot this method
     * is trying to complete.
     */
    private void attachInstallTask(String slug, java.util.concurrent.Future<?> task) {
        ActiveModpack current = active;
        if (current != null && current.activity() == Activity.INSTALLING && current.slug().equals(slug) && current.installTask() == null) {
            active = new ActiveModpack(slug, Activity.INSTALLING, task, null);
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
        withDetailPage(modpack.slug(), page -> page.setBusy(true));
        setCardBusy(modpack.slug(), true);
        active = new ActiveModpack(modpack.slug(), Activity.INSTALLING, null, null);

        java.util.concurrent.Future<?> task = backgroundExecutor.submit(() -> {
            try {
                doInstallAndLaunch(modpack, manifest);
            } finally {
                withDetailPage(modpack.slug(), page -> page.setBusy(false));
                setCardBusy(modpack.slug(), false);
            }
        });
        attachInstallTask(modpack.slug(), task);
    }

    /**
     * Same action as the card's play/download icon in the list, without opening the detail page
     * first - submitted (not fire-and-forget) so the card's icon can switch to "Annuler" and
     * {@link #cancelInstall()} actually has something to cancel, the same as a detail-page-started
     * install.
     */
    private void quickInstallAndLaunch(ModpackSummary modpack) {
        setCardBusy(modpack.slug(), true);
        active = new ActiveModpack(modpack.slug(), Activity.INSTALLING, null, null);
        java.util.concurrent.Future<?> task = backgroundExecutor.submit(() -> {
            try {
                ModpackManifest manifest = modpackApi.getManifest(modpack.slug(), null, session);
                doInstallAndLaunch(modpack, manifest);
            } catch (Exception e) {
                // Covers a failure in the getManifest() call above too, not just doInstallAndLaunch
                // - that one already clears active itself when it's the one that fails/cancels,
                // this is a harmless no-op in that case (already cleared, or moved on to RUNNING).
                clearActiveIfInstalling(modpack.slug());
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
        attachInstallTask(modpack.slug(), task);
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

    /** Interrupts whichever sync/install/repair task is currently running for the open detail page - the "Annuler" button. */
    private void cancelInstall() {
        ActiveModpack current = active;
        if (current != null && current.activity() == Activity.INSTALLING && current.installTask() != null) {
            current.installTask().cancel(true);
        }
    }

    private static final int FORCE_KILL_TIMEOUT_SECONDS = 10;

    /**
     * Asks the running game process to exit - the "Arreter" button. {@code destroy()} (SIGTERM on
     * Linux/macOS) asks the JVM to shut down but doesn't guarantee it will - a native
     * graphics/mod cleanup hang on shutdown leaves the process alive with no visible feedback.
     * Immediately flips the button to "Tuer" ({@link #killGame}, a manual force-kill the user can
     * click right away rather than waiting) and, as a backstop in case they don't notice/click
     * it, escalates to {@code destroyForcibly()} on its own after a grace period if the process
     * still hasn't exited by then - off the EDT (this runs from the button's action listener)
     * since {@code waitFor} blocks.
     */
    private void stopGame() {
        ActiveModpack current = active;
        if (current == null || current.activity() != Activity.RUNNING) {
            return;
        }
        Process process = current.gameProcess();
        active = new ActiveModpack(current.slug(), Activity.STOPPING, null, process);
        withDetailPage(current.slug(), page -> page.setStopping(true));
        setCardStopping(current.slug(), true);
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
    }

    /**
     * Force-kills the running game process outright - the "Tuer" button, shown after "Arreter"
     * has already been clicked once ({@link #stopGame}). {@code destroyForcibly()} is SIGKILL on
     * Linux/macOS: unconditional, no cooperation required from the target process, so there's
     * nothing to wait for or escalate here unlike {@link #stopGame} - verified against a real
     * process that traps and ignores {@code SIGTERM} (what plain {@code destroy()} sends).
     */
    private void killGame() {
        ActiveModpack current = active;
        if (current != null && current.gameProcess() != null) {
            current.gameProcess().destroyForcibly();
        }
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
            syncModpackFiles(modpack, manifest);
            installGame(modpack, manifest);
            withDetailPage(modpack.slug(), page -> {
                page.setInstalled(true);
                page.setUpdateAvailable(false);
            });
            setCardInstalled(modpack.slug(), true);
            setCardUpdateAvailable(modpack.slug(), false);
            String username = authApi.fetchUsername(session);
            warnIfUsernameChanged(modpack.slug(), username);
            launchGame(modpack, manifest, username);
        } catch (Exception e) {
            // Only actually clears anything if we didn't reach launchGame above - that already
            // moved active on to RUNNING, so this is a no-op in that case (see its Javadoc).
            clearActiveIfInstalling(modpack.slug());
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

    /** True if {@code e} (or the fact the thread is now interrupted) means "cancelled", not "actually failed". */
    private boolean isCancellation(Exception e) {
        return Thread.currentThread().isInterrupted() || e instanceof InterruptedException;
    }

    private void showErrorDialog(ModpackSummary modpack, Exception e) {
        SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(shellFrame,
                "Impossible d'installer/lancer \"" + modpack.name() + "\" : " + e.getMessage(),
                "Erreur", javax.swing.JOptionPane.ERROR_MESSAGE));
    }

    /** Clears the locally-recorded manifest so the next sync re-verifies/re-downloads everything, then re-installs (no launch). */
    private void repairModpack(ModpackSummary modpack, ModpackManifest manifest) {
        withDetailPage(modpack.slug(), page -> page.setBusy(true));
        setCardBusy(modpack.slug(), true);
        active = new ActiveModpack(modpack.slug(), Activity.INSTALLING, null, null);
        java.util.concurrent.Future<?> task = backgroundExecutor.submit(() -> {
            try {
                new LocalManifestStore().clear(dirs.instance(modpack.slug()));
                syncModpackFiles(modpack, manifest);
                installGame(modpack, manifest);
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
                // is unconditional (still guarded by clearActiveIfInstalling itself) - there's no
                // RUNNING transition here to avoid clobbering.
                clearActiveIfInstalling(modpack.slug());
                withDetailPage(modpack.slug(), page -> page.setBusy(false));
                setCardBusy(modpack.slug(), false);
            }
        });
        attachInstallTask(modpack.slug(), task);
    }

    /** Deletes the instance directory entirely - the confirmation dialog already happened in the UI. */
    private void uninstallModpack(ModpackSummary modpack) {
        backgroundExecutor.execute(() -> {
            try {
                deleteRecursively(dirs.instance(modpack.slug()));
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

    private void syncModpackFiles(ModpackSummary modpack, ModpackManifest manifest) throws Exception {
        withDetailPage(modpack.slug(), page -> {
            page.setStatus("Synchronisation des fichiers...");
            page.setProgress(0);
        });
        var syncEngine = new ModpackSyncEngine(new mc.snakenest.launcher.modpack.ModpackFileDownloader(modpackApi), new LocalManifestStore());
        syncEngine.sync(modpack.slug(), dirs.instance(modpack.slug()), manifest, session, new mc.snakenest.launcher.modpack.SyncProgressListener() {
            @Override
            public void onFileStarted(String path, long size) {
                withDetailPage(modpack.slug(), page -> page.setStatus("Téléchargement : " + path));
            }

            @Override
            public void onFileDone(String path) {
            }

            @Override
            public void onDeleted(String path) {
            }
        });
    }

    private void installGame(ModpackSummary modpack, ModpackManifest manifest) throws Exception {
        withDetailPage(modpack.slug(), page -> page.setStatus("Installation du jeu..."));
        InstallRequest request = new InstallRequest(manifest.mcVersion(), manifest.modLoader(), manifest.loaderVersion(), dirs.instance(modpack.slug()));
        gameInstallService.install(request, new GameInstallListener() {
            @Override
            public void onStepStarted(InstallStep step) {
                withDetailPage(modpack.slug(), page -> page.setStatus(describeInstallStep(step)));
            }

            @Override
            public void onProgress(long bytesDownloaded, long bytesTotal) {
                if (bytesTotal > 0) {
                    withDetailPage(modpack.slug(), page -> page.setProgress((double) bytesDownloaded / bytesTotal));
                }
            }
        });
        withDetailPage(modpack.slug(), page -> page.setProgress(-1));
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
        Process process = gameLaunchService.launch(request);
        active = new ActiveModpack(modpack.slug(), Activity.RUNNING, null, process);
        withDetailPage(modpack.slug(), page -> page.setRunning(true));
        setCardRunning(modpack.slug(), true);
        if (discordPresence != null) {
            discordPresence.setPlaying(modpack.name());
        }
        process.onExit().thenRun(() -> {
            ActiveModpack current = active;
            if (current != null && current.gameProcess() == process) {
                active = null;
            }
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
        });
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
        if (sessionToRevoke != null) {
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
