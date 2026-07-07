package mc.snakenest.launcher.ui;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Brand mark: the launcher client's real logo once fetched
 * ({@code auth.LauncherAuthApiClient#fetchClientInfo}), or a placeholder
 * scroll/parchment glyph while it hasn't loaded yet (or has no logo set) -
 * see the mockups, which use a plain black square for the same purpose
 * ("just a rough sketch of the layout, not the final look").
 */
final class LogoPanel extends JPanel {

    private static final int DEFAULT_SIZE = 64;

    private BufferedImage image;

    LogoPanel() {
        this(DEFAULT_SIZE);
    }

    LogoPanel(int size) {
        setPreferredSize(new Dimension(size, size));
        setOpaque(false);
    }

    /** Safe to call with {@code null} (reverts to the placeholder). Repaints itself. */
    void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int pad = 8;
            int size = Math.min(getWidth(), getHeight()) - pad * 2;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            if (image != null) {
                paintRealLogo(g2, x, y, size);
            } else {
                paintPlaceholder(g2, x, y, size);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintRealLogo(Graphics2D g2, int x, int y, int size) {
        RoundRectangle2D clip = new RoundRectangle2D.Float(x, y, size, size, size / 4f, size / 4f);
        var oldClip = g2.getClip();
        g2.clip(clip);
        g2.drawImage(image, x, y, size, size, null);
        g2.setClip(oldClip);
    }

    /** A little scroll/parchment glyph - reads as "news/lore" rather than a random logo stand-in. */
    private void paintPlaceholder(Graphics2D g2, int x, int y, int size) {
        g2.setColor(UIManager.getColor("Component.accentColor") != null
                ? UIManager.getColor("Component.accentColor")
                : getForeground());
        g2.fill(new RoundRectangle2D.Float(x, y, size, size, size / 4f, size / 4f));

        g2.setColor(UIManager.getColor("Panel.background"));
        g2.setStroke(new BasicStroke(Math.max(1.5f, size / 16f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        float pad = size * 0.24f;
        float bodyLeft = x + pad;
        float bodyRight = x + size - pad;
        float capHeight = size * 0.12f;
        float bodyTop = y + size * 0.3f;
        float bodyBottom = y + size * 0.78f;

        g2.draw(new RoundRectangle2D.Float(bodyLeft, bodyTop, bodyRight - bodyLeft, bodyBottom - bodyTop, capHeight, capHeight));
        g2.draw(new Ellipse2D.Float(bodyLeft, bodyTop - capHeight / 2f, bodyRight - bodyLeft, capHeight));
        g2.draw(new Ellipse2D.Float(bodyLeft, bodyBottom - capHeight / 2f, bodyRight - bodyLeft, capHeight));

        float lineY1 = bodyTop + (bodyBottom - bodyTop) * 0.42f;
        float lineY2 = bodyTop + (bodyBottom - bodyTop) * 0.66f;
        g2.draw(new Line2D.Float(bodyLeft + capHeight, lineY1, bodyRight - capHeight, lineY1));
        g2.draw(new Line2D.Float(bodyLeft + capHeight, lineY2, bodyRight - capHeight, lineY2));
    }
}
