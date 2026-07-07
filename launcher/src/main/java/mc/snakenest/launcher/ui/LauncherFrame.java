package mc.snakenest.launcher.ui;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
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
    private final LogoPanel logoPanel;
    private Consumer<NavTarget> onNavigate = target -> {
    };

    /**
     * @param onOpenAccount receives the account button itself, to anchor a popover under it
     *                      (see {@code ui.account.AccountPopover})
     */
    public LauncherFrame(Consumer<JComponent> onOpenAccount) {
        super("SnakeN3st Launcher");

        this.sidebar = new Sidebar(this::navigate);
        this.topBar = new TopBar(onOpenAccount);
        this.contentArea = new ContentArea();
        this.logoPanel = new LogoPanel();

        // The logo's own square is smaller than this - it's centered within a zone exactly as
        // wide as Sidebar.WIDTH (not just within its own tight bounds), so it visually reads as
        // sitting above the nav column, the two lining up as one "left rail". GridBagLayout
        // (default constraints - fill=NONE, anchor=CENTER) centers logoPanel regardless of this
        // wrapper being wider than it, the same technique used for TopBar's rightPanel/
        // ModpackCardView's action button - BorderLayout.WEST alone would've just sized this
        // wrapper (and so the logo) to its child's own preferred width, no wider.
        JPanel logoWrapper = new JPanel(new java.awt.GridBagLayout());
        logoWrapper.setOpaque(false);
        logoWrapper.setPreferredSize(new Dimension(Sidebar.WIDTH, logoPanel.getPreferredSize().height));
        logoWrapper.add(logoPanel);

        JPanel header = new JPanel(new BorderLayout());
        header.add(logoWrapper, BorderLayout.WEST);
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

    /** Safe to call with {@code null} (reverts to the placeholder mark). */
    public void setLogo(BufferedImage image) {
        logoPanel.setImage(image);
    }

    /** Safe to call with {@code null} (reverts to the generic account icon). */
    public void setAccountAvatar(BufferedImage image) {
        topBar.setAccountAvatar(image);
    }

    /** {@code null} hides the topbar's refresh button - see {@code ui.common} README for why this lives here rather than on individual pages. */
    public void setOnRefresh(Runnable onRefresh) {
        topBar.setOnRefresh(onRefresh);
    }

    /**
     * Called every time the sidebar (or {@link #navigateTo}) switches the top-level page - lets
     * {@code LauncherApp} rebind {@link #setOnRefresh} to whatever the newly-shown page can
     * refresh (e.g. the modpack list once {@link ContentArea#show} has reset a section back to
     * its list view - see {@link Resettable}). Sub-navigation within a section (e.g. opening a
     * modpack's detail page) doesn't go through here since it never calls {@link #navigateTo};
     * those call {@link #setOnRefresh} directly instead.
     */
    public void setOnNavigate(Consumer<NavTarget> onNavigate) {
        this.onNavigate = onNavigate != null ? onNavigate : target -> {
        };
    }

    private void navigate(NavTarget target) {
        topBar.setTitle(titleFor(target));
        contentArea.show(target);
        sidebar.select(target);
        onNavigate.accept(target);
    }

    private String titleFor(NavTarget target) {
        return switch (target) {
            case NEWS -> "Actualités";
            case MODPACKS -> "Modpacks";
            case SETTINGS -> "Paramètres";
        };
    }
}
