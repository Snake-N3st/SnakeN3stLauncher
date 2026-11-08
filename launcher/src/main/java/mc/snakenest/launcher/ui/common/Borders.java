package mc.snakenest.launcher.ui.common;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** Border helpers whose behavior across a theme switch isn't what you'd first reach for. */
public final class Borders {

    private static final int CARD_ARC = 18;

    private Borders() {
    }

    /**
     * Anti-aliased rounded outline for a clickable card row (modpack list, mod manager search
     * results) - {@code LineBorder}'s built-in "rounded" flag looks too subtle to read as a card.
     */
    public static Border card() {
        return new RoundedBorder(UIManager.getColor("Component.borderColor"));
    }

    /**
     * "No border", in a way that <b>survives switching the theme</b> - which
     * {@code setBorder(null)} does not.
     *
     * <p>{@code ThemeController#switchTo} ends in {@link javax.swing.SwingUtilities
     * #updateComponentTreeUI}, which re-runs each component's {@code updateUI()} and therefore
     * {@code LookAndFeel.installBorder}. That method replaces the current border whenever it is
     * {@code null} <em>or</em> a {@link javax.swing.plaf.UIResource} - so a deliberately nulled
     * border is treated as "never set" and gets the new theme's default installed over it. The
     * visible symptom was frames suddenly appearing around scroll panes (the settings page, the
     * news and modpack lists) the moment the player changed theme, and staying until restart.
     *
     * <p>A plain {@link BorderFactory#createEmptyBorder()} is neither {@code null} nor a
     * {@code UIResource}, so {@code installBorder} leaves it alone - verified directly against
     * FlatLaf, not assumed.
     */
    public static Border none() {
        return BorderFactory.createEmptyBorder();
    }

    private static final class RoundedBorder extends AbstractBorder {

        private final Color color;

        RoundedBorder(Color color) {
            this.color = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.draw(new RoundRectangle2D.Float(x, y, width - 1, height - 1, CARD_ARC, CARD_ARC));
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(4, 4, 4, 4);
        }
    }
}
