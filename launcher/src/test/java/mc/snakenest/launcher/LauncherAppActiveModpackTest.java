package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.LauncherAuthApiClient;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.crypto.KeyStorage;
import mc.snakenest.launcher.game.GameInstallService;
import mc.snakenest.launcher.game.GameLaunchService;
import mc.snakenest.launcher.modpack.ModpackApiClient;
import mc.snakenest.launcher.modpack.ModpackSettings;
import mc.snakenest.launcher.modpack.ModpackSummary;
import mc.snakenest.launcher.news.NewsApiClient;
import mc.snakenest.launcher.ui.ThemeController;
import mc.snakenest.launcher.ui.modpack.ModpackDetailPage;
import mc.snakenest.launcher.ui.modpack.ModpackDetailViewModel;
import mc.snakenest.launcher.ui.modpack.ModpackListPage;
import mc.snakenest.launcher.ui.modpack.ModpackListViewModel;
import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Regression test for the bug report this class exists to fix: a modpack's action button (detail
 * page or list card) kept showing "Demarrer" even while it was actively downloading, because
 * nothing tracked "an install is in progress for slug X" the way {@code active}'s {@code RUNNING}
 * predecessor tracked a running game - a freshly built page/card had nothing to check and always
 * defaulted to idle. Exercises the real, private {@code restoreActiveState}/
 * {@code restoreActiveCardState} via reflection (this class has no public test-observability API
 * by design - see its own README - manual/visual QA is the normal way to check it, reflection is
 * used here specifically to lock this one regression down permanently).
 */
class LauncherAppActiveModpackTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private LauncherApp newLauncherApp(Path dataDir) {
        AppDirs dirs = new AppDirs(dataDir);
        ConfigStore configStore = new ConfigStore(dirs);
        return new LauncherApp(dirs, URI.create("http://127.0.0.1"), "test-client-id", configStore,
                new ThemeController(configStore), mock(LauncherAuthApiClient.class), mock(ModpackApiClient.class),
                mock(NewsApiClient.class), mock(KeyStorage.class), mock(GameInstallService.class), mock(GameLaunchService.class));
    }

    private static Object activeModpack(String slug, String activityName, Object installTask, Process gameProcess) throws Exception {
        Class<?> activityClass = Class.forName("mc.snakenest.launcher.LauncherApp$Activity");
        Object activity = null;
        for (Object constant : activityClass.getEnumConstants()) {
            if (constant.toString().equals(activityName)) {
                activity = constant;
            }
        }
        Class<?> activeModpackClass = Class.forName("mc.snakenest.launcher.LauncherApp$ActiveModpack");
        Constructor<?> constructor = activeModpackClass.getDeclaredConstructor(String.class, activityClass, java.util.concurrent.Future.class, Process.class);
        constructor.setAccessible(true);
        return constructor.newInstance(slug, activity, installTask, gameProcess);
    }

    private static void setActive(LauncherApp app, Object activeModpack) throws Exception {
        Field field = LauncherApp.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(app, activeModpack);
    }

    private static void restoreActiveState(LauncherApp app, String slug, ModpackDetailPage detail) throws Exception {
        Method method = LauncherApp.class.getDeclaredMethod("restoreActiveState", String.class, ModpackDetailPage.class);
        method.setAccessible(true);
        method.invoke(app, slug, detail);
    }

    private static void restoreActiveCardState(LauncherApp app, ModpackListViewModel viewModel) throws Exception {
        Method method = LauncherApp.class.getDeclaredMethod("restoreActiveCardState", ModpackListViewModel.class);
        method.setAccessible(true);
        method.invoke(app, viewModel);
    }

    private static void setCurrentDetailPage(LauncherApp app, ModpackDetailPage page) throws Exception {
        Field field = LauncherApp.class.getDeclaredField("currentDetailPage");
        field.setAccessible(true);
        field.set(app, page);
    }

    @SuppressWarnings("unchecked")
    private static void withDetailPage(LauncherApp app, String slug, java.util.function.Consumer<ModpackDetailPage> action) throws Exception {
        Method method = LauncherApp.class.getDeclaredMethod("withDetailPage", String.class, java.util.function.Consumer.class);
        method.setAccessible(true);
        method.invoke(app, slug, action);
    }

    private static String detailButtonState(ModpackDetailPage page) throws Exception {
        Field field = ModpackDetailPage.class.getDeclaredField("buttonState");
        field.setAccessible(true);
        return field.get(page).toString();
    }

    private static String cardButtonState(ModpackListViewModel viewModel, String slug) throws Exception {
        Field cardsField = ModpackListViewModel.class.getDeclaredField("cards");
        cardsField.setAccessible(true);
        Map<?, ?> cards = (Map<?, ?>) cardsField.get(viewModel);
        Object card = cards.get(slug);
        Field buttonStateField = card.getClass().getDeclaredField("buttonState");
        buttonStateField.setAccessible(true);
        return buttonStateField.get(card).toString();
    }

    private ModpackDetailPage newDetailPage(String slug) {
        ModpackDetailViewModel viewModel = new ModpackDetailViewModel(
                slug, "Aventure Ultime", "desc", "changelog", 1000, 10, true, false, null,
                ModpackSettings.defaults(),
                () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {},
                settings -> {}, () -> {}, () -> {}
        );
        return new ModpackDetailPage(viewModel);
    }

    private ModpackListViewModel newListViewModelWithCard(String slug) {
        List<ModpackSummary> modpacks = List.of(new ModpackSummary(slug, "Aventure Ultime", "desc", null, false, "1.0.0", 1000L));
        ModpackListViewModel viewModel = new ModpackListViewModel(modpacks, Set.of(slug), Set.of(), Map.of(),
                m -> {}, m -> {}, () -> {}, () -> {}, () -> {});
        new ModpackListPage(viewModel); // registers the card as it builds
        return viewModel;
    }

    @Test
    void freshDetailPageRestoresInstallingState(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        setActive(app, activeModpack("aventure-ultime", "INSTALLING", null, null));

        ModpackDetailPage detail = newDetailPage("aventure-ultime");
        restoreActiveState(app, "aventure-ultime", detail);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("BUSY", detailButtonState(detail));
    }

    @Test
    void freshDetailPageForADifferentModpackStaysIdle(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        setActive(app, activeModpack("some-other-modpack", "INSTALLING", null, null));

        ModpackDetailPage detail = newDetailPage("aventure-ultime");
        restoreActiveState(app, "aventure-ultime", detail);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("IDLE", detailButtonState(detail));
    }

    @Test
    void freshDetailPageRestoresRunningState(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        setActive(app, activeModpack("aventure-ultime", "RUNNING", null, null));

        ModpackDetailPage detail = newDetailPage("aventure-ultime");
        restoreActiveState(app, "aventure-ultime", detail);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("RUNNING", detailButtonState(detail));
    }

    @Test
    void freshDetailPageRestoresStoppingState(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        setActive(app, activeModpack("aventure-ultime", "STOPPING", null, null));

        ModpackDetailPage detail = newDetailPage("aventure-ultime");
        restoreActiveState(app, "aventure-ultime", detail);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("STOPPING", detailButtonState(detail));
    }

    @Test
    void freshListCardRestoresInstallingState(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        setActive(app, activeModpack("aventure-ultime", "INSTALLING", null, null));

        ModpackListViewModel viewModel = newListViewModelWithCard("aventure-ultime");
        restoreActiveCardState(app, viewModel);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("BUSY", cardButtonState(viewModel, "aventure-ultime"));
    }

    /**
     * Regression test for the follow-up bug report: "I had to refresh for the button to go from
     * Annuler to Arreter" - a live push (e.g. launchGame's setRunning(true) once install finishes)
     * used to target whichever ModpackDetailPage instance was captured at the *start* of the
     * install, which is a different, no-longer-displayed instance if the page was rebuilt in the
     * meantime (refresh, or leave-and-reopen). withDetailPage looks up the current page fresh
     * every time instead, so it must reach whichever instance is genuinely on screen right now,
     * not whichever one used to be.
     */
    @Test
    void withDetailPageReachesTheCurrentlyDisplayedPageNotAnOlderOne(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        ModpackDetailPage stalePage = newDetailPage("aventure-ultime");
        ModpackDetailPage currentPage = newDetailPage("aventure-ultime");

        // Simulates the page having been rebuilt (e.g. "Actualiser") since some earlier point that
        // captured stalePage - only currentPage is "on screen" as far as LauncherApp is concerned.
        setCurrentDetailPage(app, currentPage);

        withDetailPage(app, "aventure-ultime", page -> page.setRunning(true));
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("RUNNING", detailButtonState(currentPage));
        assertEquals("IDLE", detailButtonState(stalePage));
    }

    @Test
    void withDetailPageIsANoOpForADifferentlyDisplayedModpack(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        ModpackDetailPage otherModpackPage = newDetailPage("some-other-modpack");
        setCurrentDetailPage(app, otherModpackPage);

        withDetailPage(app, "aventure-ultime", page -> page.setBusy(true));
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("IDLE", detailButtonState(otherModpackPage));
    }

    @Test
    void withDetailPageIsANoOpWhenNoPageIsOpen(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        // No exception is the assertion here - setCurrentDetailPage is never called, so
        // currentDetailPage stays null.
        withDetailPage(app, "aventure-ultime", page -> page.setBusy(true));
        SwingUtilities.invokeAndWait(() -> { });
    }
}
