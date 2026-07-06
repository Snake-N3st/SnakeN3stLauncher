package mc.snakenest.launcher.devpreview;

import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.config.Theme;
import mc.snakenest.launcher.modpack.ModpackSummary;
import mc.snakenest.launcher.news.Post;
import mc.snakenest.launcher.ui.LauncherFrame;
import mc.snakenest.launcher.ui.NavTarget;
import mc.snakenest.launcher.ui.ThemeController;
import mc.snakenest.launcher.ui.account.AccountPopover;
import mc.snakenest.launcher.ui.modpack.ModpackDetailPage;
import mc.snakenest.launcher.ui.modpack.ModpackDetailViewModel;
import mc.snakenest.launcher.ui.modpack.ModpackListPage;
import mc.snakenest.launcher.ui.modpack.ModpackListViewModel;
import mc.snakenest.launcher.ui.modpack.ModpackSectionPage;
import mc.snakenest.launcher.ui.news.NewsListPage;
import mc.snakenest.launcher.ui.news.NewsListViewModel;
import mc.snakenest.launcher.ui.settings.SettingsPage;
import mc.snakenest.launcher.util.AppDirs;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Manual QA aid: the whole shell, wired to every real page but backed by
 * fake in-memory data - no network, no auth, no game install/launch code
 * runs. Run directly from IntelliJ; pass "light" as the first argument for
 * the light theme.
 */
public final class FullShellPreview {

    public static void main(String[] args) {
        Theme theme = args.length > 0 && "light".equalsIgnoreCase(args[0]) ? Theme.LIGHT : Theme.DARK;
        ThemeController themeController = new ThemeController(new ConfigStore(new AppDirs(Path.of(System.getProperty("java.io.tmpdir"), "sn3-devpreview"))));
        themeController.applyStartupTheme(theme);

        SwingUtilities.invokeLater(() -> buildAndShow(themeController));
    }

    private static void buildAndShow(ThemeController themeController) {
        LauncherFrame frame = new LauncherFrame(
                () -> System.out.println("[preview] would open Twitch"),
                () -> System.out.println("[preview] would open Discord"),
                anchor -> AccountPopover.show(anchor, "JoueurDeTest", "Admin", "joueur@example.com",
                        () -> System.out.println("[preview] would log out"))
        );

        frame.addPage(NavTarget.SETTINGS, new SettingsPage(
                themeController.current(),
                themeController::switchTo,
                () -> System.out.println("[preview] would open data folder"),
                () -> System.out.println("[preview] would log out")
        ));

        List<Post> posts = List.of(
                new Post(1, "Ouverture du serveur", "On est en ligne !", "ouverture", "http://127.0.0.1/news/1", "<p>Bienvenue sur le serveur, l'aventure commence maintenant.</p>", new Post.Author(1, "Admin"), "2026-07-01T12:00:00+00:00", null),
                new Post(2, "Mise a jour 1.2.0", "Nouveau contenu disponible", "maj-1-2-0", "http://127.0.0.1/news/2", "<p>De nouveaux modpacks sont disponibles.</p>", new Post.Author(1, "Admin"), "2026-07-05T09:30:00+00:00", null)
        );
        frame.addPage(NavTarget.NEWS, new NewsListPage(new NewsListViewModel(posts, post ->
                JOptionPane.showMessageDialog(frame, post.title()))));

        List<ModpackSummary> modpacks = List.of(
                new ModpackSummary("aventure-ultime", "Aventure Ultime", "Modpack de demonstration cree pour tester le plugin.", null, false, "1.0.1", 51_380_224L),
                new ModpackSummary("survie-plus", "Survie Plus", "Un modpack de survie ameliore, avec de nouvelles dimensions.", null, true, "2.3.0", 10_600_000L)
        );
        // A one-element holder breaks the construction cycle: the list page's callback needs a
        // reference to the section it's about to be placed inside of.
        ModpackSectionPage[] sectionHolder = new ModpackSectionPage[1];
        ModpackListPage listPage = new ModpackListPage(new ModpackListViewModel(modpacks, Set.of("aventure-ultime"),
                modpack -> showDetail(frame, sectionHolder[0], modpack)));
        sectionHolder[0] = new ModpackSectionPage(listPage);
        frame.addPage(NavTarget.MODPACKS, sectionHolder[0]);

        frame.navigateTo(NavTarget.MODPACKS);
        frame.setVisible(true);
    }

    private static void showDetail(LauncherFrame frame, ModpackSectionPage section, ModpackSummary modpack) {
        Runnable back = () -> {
            section.showList();
            frame.navigateTo(NavTarget.MODPACKS);
        };
        ModpackDetailViewModel viewModel = new ModpackDetailViewModel(
                modpack.name(),
                modpack.description(),
                "Premiere version stable.",
                modpack.totalSize(),
                126,
                () -> System.out.println("[preview] would install/launch " + modpack.slug()),
                () -> System.out.println("[preview] would open modpack settings"),
                () -> System.out.println("[preview] would open the instance folder"),
                back
        );
        section.showDetail(new ModpackDetailPage(viewModel));
        frame.showBackButton(modpack.name(), back);
    }
}
