package mc.snakenest.launcher.ui;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Placeholder brand mark (a plain rounded square with "SN") until a real
 * logo asset is provided - see the mockups, which use a plain black square
 * for the same purpose ("just a rough sketch of the layout, not the final
 * look").
 */
final class LogoPanel extends JPanel {

    private static final int SIZE = 64;

    LogoPanel() {
        setPreferredSize(new Dimension(SIZE, SIZE));
        setOpaque(false);
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

            g2.setColor(UIManager.getColor("Component.accentColor") != null
                    ? UIManager.getColor("Component.accentColor")
                    : getForeground());
            g2.fill(new RoundRectangle2D.Float(x, y, size, size, size / 4f, size / 4f));

            g2.setColor(UIManager.getColor("Panel.background"));
            g2.setFont(getFont().deriveFont(java.awt.Font.BOLD, size * 0.4f));
            var metrics = g2.getFontMetrics();
            String text = "SN";
            int textX = x + (size - metrics.stringWidth(text)) / 2;
            int textY = y + (size + metrics.getAscent() - metrics.getDescent()) / 2;
            g2.drawString(text, textX, textY);
        } finally {
            g2.dispose();
        }
    }
}
