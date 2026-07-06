package mc.snakenest.launcher.ui.common;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * A rounded-square placeholder showing a name's first letter, used wherever
 * a modpack/account has no real image loaded (no async image loader exists
 * yet - see {@code ui/README.md}).
 */
public final class AvatarPanel extends JPanel {

    private final String letter;

    public AvatarPanel(String name, int size) {
        this.letter = name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.min(getWidth(), getHeight());
            g2.setColor(UIManager.getColor("Component.borderColor"));
            g2.fill(new RoundRectangle2D.Float(0, 0, size, size, size / 5f, size / 5f));

            g2.setColor(getForeground());
            g2.setFont(getFont().deriveFont(Font.BOLD, size * 0.42f));
            var metrics = g2.getFontMetrics();
            int textX = (size - metrics.stringWidth(letter)) / 2;
            int textY = (size + metrics.getAscent() - metrics.getDescent()) / 2;
            g2.drawString(letter, textX, textY);
        } finally {
            g2.dispose();
        }
    }
}
