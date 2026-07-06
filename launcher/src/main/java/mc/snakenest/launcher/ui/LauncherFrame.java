package mc.snakenest.launcher.ui;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.function.Consumer;

/**
 * The main window: logo + top bar along the top, nav sidebar on the left,
 * page content filling the rest - see {@code ui/README.md} for how the
 * pieces fit together.
 */
public final class LauncherFrame extends JFrame {

    private final Sidebar sidebar;
    private final TopBar topBar;
    private final ContentArea contentArea;

    /**
     * @param onOpenAccount receives the account button itself, to anchor a popover under it
     *                      (see {@code ui.account.AccountPopover})
     */
    public LauncherFrame(Runnable onOpenTwitch, Runnable onOpenDiscord, Consumer<JComponent> onOpenAccount) {
        super("SnakeN3st Launcher");

        this.sidebar = new Sidebar(this::navigate, onOpenTwitch, onOpenDiscord);
        this.topBar = new TopBar(onOpenAccount);
        this.contentArea = new ContentArea();

        JPanel header = new JPanel(new BorderLayout());
        header.add(new LogoPanel(), BorderLayout.WEST);
        header.add(topBar, BorderLayout.CENTER);
        header.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0, 0, 0, 40)));

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(sidebar, BorderLayout.WEST);
        add(contentArea, BorderLayout.CENTER);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(760, 500));
        setSize(1000, 660);
        setLocationRelativeTo(null);
    }

    public void addPage(NavTarget target, JComponent page) {
        contentArea.addPage(target, page);
    }

    public void navigateTo(NavTarget target) {
        navigate(target);
    }

    /** Lets a page (e.g. a modpack's detail view) show a back button and a custom title instead of the sidebar-driven one. */
    public void showBackButton(String title, Runnable onBack) {
        topBar.showBackButton(title, onBack);
    }

    private void navigate(NavTarget target) {
        topBar.setTitle(titleFor(target));
        contentArea.show(target);
        sidebar.select(target);
    }

    private String titleFor(NavTarget target) {
        return switch (target) {
            case NEWS -> "Actualités";
            case MODPACKS -> "Modpacks";
            case SETTINGS -> "Paramètres";
        };
    }
}
