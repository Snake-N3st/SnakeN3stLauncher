package mc.snakenest.launcher.ui.news;

import mc.snakenest.launcher.ui.Resettable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;

/**
 * The whole "Actualités" section registered under {@code NavTarget.NEWS}:
 * a small internal {@link CardLayout} between the list and one post's full
 * content, mirroring {@code ui.modpack.ModpackSectionPage} (including
 * implementing {@link Resettable} for the same reason - see its Javadoc).
 */
public final class NewsSectionPage extends JPanel implements Resettable {

    private static final String LIST = "list";
    private static final String DETAIL = "detail";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel listContainer = new JPanel(new BorderLayout());
    private final JPanel detailContainer = new JPanel(new BorderLayout());

    public NewsSectionPage(NewsListPage listPage) {
        setLayout(cardLayout);
        listContainer.add(listPage, BorderLayout.CENTER);
        add(listContainer, LIST);
        add(detailContainer, DETAIL);
    }

    /**
     * Swaps the list content in place without disturbing the detail card - usually a
     * {@code NewsListPage} once real data has loaded, but also used with a
     * {@code ui.common.LoadingPanel} placeholder shown immediately while the list is (re)loading,
     * mirroring {@code ui.modpack.ModpackSectionPage#replaceList}.
     */
    public void replaceList(JComponent listContent) {
        listContainer.removeAll();
        listContainer.add(listContent, BorderLayout.CENTER);
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

    public void showDetail(NewsDetailPage detailPage) {
        detailContainer.removeAll();
        detailContainer.add(detailPage, BorderLayout.CENTER);
        detailContainer.revalidate();
        cardLayout.show(this, DETAIL);
    }
}
