package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.ui.Resettable;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;

/**
 * The whole "Modpacks" section registered under {@code NavTarget.MODPACKS}:
 * a small internal {@link CardLayout} between the list and one modpack's
 * detail view (the third mockup), since that's a sub-navigation within this
 * section rather than a top-level sidebar page.
 *
 * <p>Implements {@link Resettable} so re-navigating here (e.g. Actualites
 * then back to Modpacks) always lands on the list, not a stale detail view.
 */
public final class ModpackSectionPage extends JPanel implements Resettable {

    private static final String LIST = "list";
    private static final String DETAIL = "detail";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel listContainer = new JPanel(new BorderLayout());
    private final JPanel detailContainer = new JPanel(new BorderLayout());

    public ModpackSectionPage(ModpackListPage listPage) {
        setLayout(cardLayout);
        listContainer.add(listPage, BorderLayout.CENTER);
        add(listContainer, LIST);
        add(detailContainer, DETAIL);
    }

    /** Swaps the list content in place (e.g. once real data has loaded) without disturbing the detail card. */
    public void replaceList(ModpackListPage listPage) {
        listContainer.removeAll();
        listContainer.add(listPage, BorderLayout.CENTER);
        listContainer.revalidate();
        listContainer.repaint();
    }

    public void showList() {
        cardLayout.show(this, LIST);
    }

    @Override
    public void resetToDefault() {
        showList();
    }

    public void showDetail(ModpackDetailPage detailPage) {
        detailContainer.removeAll();
        detailContainer.add(detailPage, BorderLayout.CENTER);
        detailContainer.revalidate();
        cardLayout.show(this, DETAIL);
    }
}
