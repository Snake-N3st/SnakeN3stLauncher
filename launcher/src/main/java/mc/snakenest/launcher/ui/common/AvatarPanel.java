package mc.snakenest.launcher.ui.common;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * A rounded-square avatar for a modpack/account: the real image once fetched
 * ({@code ui.common.RemoteImages}), or a letter placeholder before it loads
 * (or if there's no image at all) - same "real image with a placeholder
 * fallback" idea as {@code ui.LogoPanel}, just for a per-item avatar instead
 * of the one app/client brand mark.
 */
public final class AvatarPanel extends JPanel {

    private final String letter;
    private BufferedImage image;

    public AvatarPanel(String name, int size) {
        this.letter = name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
        setOpaque(false);
    }

    /** Safe to call with {@code null} (reverts to the letter placeholder). Repaints itself. */
    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Centered rather than drawn corner-to-corner: a container layout (e.g.
            // BorderLayout.WEST) can stretch this panel taller/wider than its preferred
            // size, and without centering the square would end up pinned to the
            // top-left instead of centered with a margin from the panel's own bounds.
            int pad = Math.max(2, Math.min(getWidth(), getHeight()) / 12);
            int size = Math.min(getWidth(), getHeight()) - pad * 2;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            if (image != null) {
                paintRealImage(g2, x, y, size);
            } else {
                paintPlaceholder(g2, x, y, size);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintRealImage(Graphics2D g2, int x, int y, int size) {
        RoundRectangle2D clip = new RoundRectangle2D.Float(x, y, size, size, size / 5f, size / 5f);
        var oldClip = g2.getClip();
        g2.clip(clip);
        g2.drawImage(image, x, y, size, size, null);
        g2.setClip(oldClip);
    }

    private void paintPlaceholder(Graphics2D g2, int x, int y, int size) {
        g2.setColor(UIManager.getColor("Component.borderColor"));
        g2.fill(new RoundRectangle2D.Float(x, y, size, size, size / 5f, size / 5f));

        g2.setColor(getForeground());
        g2.setFont(getFont().deriveFont(Font.BOLD, size * 0.42f));
        var metrics = g2.getFontMetrics();
        int textX = x + (size - metrics.stringWidth(letter)) / 2;
        int textY = y + (size + metrics.getAscent() - metrics.getDescent()) / 2;
        g2.drawString(letter, textX, textY);
    }
}
