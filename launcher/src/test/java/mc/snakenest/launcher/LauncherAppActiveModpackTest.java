package mc.snakenest.launcher;

import mc.snakenest.launcher.auth.LauncherAuthApiClient;
import mc.snakenest.launcher.bugreport.BugReportApiClient;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.crypto.KeyStorage;
import mc.snakenest.launcher.game.GameInstallService;
import mc.snakenest.launcher.game.GameLaunchService;
import mc.snakenest.launcher.modpack.InstanceStateRegistry;
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
 * nothing tracked "an install is in progress for slug X" the way the running-game case did - a
 * freshly built page/card had nothing to check and always defaulted to idle.
 *
 * <p>Also covers the follow-up bug this same area had: tracking used to live in a single
 * {@code LauncherApp} field that could only ever describe *one* modpack at a time, so a second
 * modpack's install/launch silently broke the first one's own controls and status. See
 * {@code modpack.InstanceStateRegistry}'s own Javadoc for the full story - every test below now
 * seeds that registry through its real, public API instead of reflecting into private nested
 * types, since {@link mc.snakenest.launcher.modpack.InstanceState}/
 * {@link mc.snakenest.launcher.modpack.InstanceActivity} are public. Reflection is still needed to
 * reach {@code LauncherApp}'s own private {@code restoreActiveState}/{@code restoreActiveCardState}/
 * {@code withDetailPage} and the {@code instanceStates}/{@code currentDetailPage} fields - this
 * class has no public test-observability API by design (see its own README), manual/visual QA is
 * the normal way to check it, reflection is used here specifically to lock these regressions down
 * permanently.
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
                mock(NewsApiClient.class), mock(KeyStorage.class), mock(GameInstallService.class), mock(GameLaunchService.class),
                new mc.snakenest.launcher.crypto.PinProtection(dirs), null, mock(BugReportApiClient.class));
    }

    private static InstanceStateRegistry instanceStates(LauncherApp app) throws Exception {
        Field field = LauncherApp.class.getDeclaredField("instanceStates");
        field.setAccessible(true);
        return (InstanceStateRegistry) field.get(app);
    }

    private static void seedInstalling(LauncherApp app, String slug) throws Exception {
        instanceStates(app).beginInstalling(slug);
    }

    private static void seedRunning(LauncherApp app, String slug) throws Exception {
        instanceStates(app).transitionToRunning(slug, mock(Process.class));
    }

    private static void seedStopping(LauncherApp app, String slug) throws Exception {
        InstanceStateRegistry registry = instanceStates(app);
        registry.transitionToRunning(slug, mock(Process.class));
        registry.beginStopping(slug);
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
                slug, "Aventure Ultime", "desc", "changelog", 1000, 10, true, false, null, null,
                ModpackSettings.defaults(),
                () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {},
                settings -> {}, () -> {}, () -> {}, List::of
        );
        return new ModpackDetailPage(viewModel);
    }

    private ModpackListViewModel newListViewModelWithCards(String... slugs) {
        List<ModpackSummary> modpacks = java.util.Arrays.stream(slugs)
                .map(slug -> new ModpackSummary(slug, "Aventure Ultime", "desc", null, false, "1.0.0", 1000L))
                .toList();
        ModpackListViewModel viewModel = new ModpackListViewModel(modpacks, Set.copyOf(List.of(slugs)), Set.of(), Map.of(),
                m -> {}, m -> {}, m -> {}, m -> {}, m -> {});
        new ModpackListPage(viewModel); // registers the cards as it builds
        return viewModel;
    }

    @Test
    void freshDetailPageRestoresInstallingState(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        seedInstalling(app, "aventure-ultime");

        ModpackDetailPage detail = newDetailPage("aventure-ultime");
        restoreActiveState(app, "aventure-ultime", detail);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("BUSY", detailButtonState(detail));
    }

    @Test
    void freshDetailPageForADifferentModpackStaysIdle(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        seedInstalling(app, "some-other-modpack");

        ModpackDetailPage detail = newDetailPage("aventure-ultime");
        restoreActiveState(app, "aventure-ultime", detail);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("IDLE", detailButtonState(detail));
    }

    @Test
    void freshDetailPageRestoresRunningState(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        seedRunning(app, "aventure-ultime");

        ModpackDetailPage detail = newDetailPage("aventure-ultime");
        restoreActiveState(app, "aventure-ultime", detail);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("RUNNING", detailButtonState(detail));
    }

    @Test
    void freshDetailPageRestoresStoppingState(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        seedStopping(app, "aventure-ultime");

        ModpackDetailPage detail = newDetailPage("aventure-ultime");
        restoreActiveState(app, "aventure-ultime", detail);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("STOPPING", detailButtonState(detail));
    }

    @Test
    void freshListCardRestoresInstallingState(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        seedInstalling(app, "aventure-ultime");

        ModpackListViewModel viewModel = newListViewModelWithCards("aventure-ultime");
        restoreActiveCardState(app, viewModel);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("BUSY", cardButtonState(viewModel, "aventure-ultime"));
    }

    /**
     * The central regression this whole redesign was for: two different modpacks with two
     * different activities in flight *at the same time*, each restoring correctly and completely
     * independently of the other - impossible to even represent with the old single-field design,
     * where starting B would have silently overwritten all tracking of A.
     */
    @Test
    void twoDifferentModpacksRestoreIndependentlyWhenBothActiveAtOnce(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        seedRunning(app, "aventure-ultime");
        seedInstalling(app, "survie-plus");

        ModpackDetailPage detailA = newDetailPage("aventure-ultime");
        restoreActiveState(app, "aventure-ultime", detailA);
        ModpackDetailPage detailB = newDetailPage("survie-plus");
        restoreActiveState(app, "survie-plus", detailB);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("RUNNING", detailButtonState(detailA));
        assertEquals("BUSY", detailButtonState(detailB));
    }

    /** Same regression as above, for a freshly-built list where both cards are shown together. */
    @Test
    void listCardsForTwoDifferentModpacksRestoreIndependentlyWhenBothActiveAtOnce(@TempDir Path tempDir) throws Exception {
        LauncherApp app = newLauncherApp(tempDir);
        seedRunning(app, "aventure-ultime");
        seedInstalling(app, "survie-plus");

        ModpackListViewModel viewModel = newListViewModelWithCards("aventure-ultime", "survie-plus");
        restoreActiveCardState(app, viewModel);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("RUNNING", cardButtonState(viewModel, "aventure-ultime"));
        assertEquals("BUSY", cardButtonState(viewModel, "survie-plus"));
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
