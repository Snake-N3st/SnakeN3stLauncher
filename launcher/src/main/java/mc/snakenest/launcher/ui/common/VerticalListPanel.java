package mc.snakenest.launcher.ui.common;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * A {@code BoxLayout.Y_AXIS} panel meant to sit inside a {@link javax.swing.JScrollPane}, that
 * always matches the viewport's width instead of growing to fit its widest child.
 *
 * <p>A plain {@code JPanel} doesn't implement {@link Scrollable}, so a {@code JScrollPane}
 * sizes it to its own preferred width - the max of every child's preferred width. A row
 * containing an unwrapped, arbitrary-length label (e.g. a Modrinth mod's description in
 * {@code ui.modpack.ModCardView}) can end up far wider than the dialog itself, which then either
 * shows an unwanted horizontal scrollbar or visibly overflows the dialog's own frame. Reported
 * directly ("le cadre de la liste depasse de la popup") against {@code ui.modpack.ModManagerDialog}'s
 * search/version lists, which is what this class was written to fix.
 */
public final class VerticalListPanel extends JPanel implements Scrollable {

    public VerticalListPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
