package mc.snakenest.launcher.ui.common;

import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

/**
 * A borderless icon-only toggle button used for sidebar navigation: exactly
 * one is selected at a time within a {@link javax.swing.ButtonGroup}. Paints
 * its own circular background (selected, or on hover/press) rather than
 * using FlatLaf's default "toolBarButton" shape, which is a rounded square -
 * a nav rail reads better with round buttons than with a subtly-squared one.
 *
 * <p>{@code diameter} is a separate parameter from the icon's own size
 * (rather than derived from it) specifically so buttons with different-sized
 * icons - e.g. the sidebar's smaller Settings gear next to the bigger
 * Modpacks/Actualités icons - can still share the exact same selection
 * background size instead of each getting its own icon-derived circle.
 *
 * <p>{@code setMaximumSize}/{@code setMinimumSize} matter here just as much
 * as {@code setPreferredSize}: {@code Sidebar} lays these out in a
 * {@code BoxLayout.Y_AXIS}, and without an explicit maximum, {@code
 * getMaximumSize()} falls through to the look-and-feel's own icon-derived
 * size (roughly icon size + a few px of margin) instead of the requested
 * {@code diameter} - which silently shrank every button's actual on-screen
 * width below what {@code diameter} asked for (confirmed: e.g. a 64px
 * {@code diameter} rendered as ~40px for the 34px nav icons and ~32px for
 * the smaller 26px Settings gear), so two buttons with different icon
 * sizes ended up with different rollover-circle sizes despite sharing the
 * same {@code diameter} argument.
 */
public final class IconButton extends JToggleButton {

    public IconButton(Icon icon, String tooltip, int diameter) {
        super(icon);
        setToolTipText(tooltip);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        Dimension size = new Dimension(diameter, diameter);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color background = backgroundFor(getModel());
        if (background != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int d = Math.min(getWidth(), getHeight());
                int x = (getWidth() - d) / 2;
                int y = (getHeight() - d) / 2;
                g2.setColor(background);
                g2.fill(new Ellipse2D.Float(x, y, d, d));
            } finally {
                g2.dispose();
            }
        }
        super.paintComponent(g);
    }

    private Color backgroundFor(javax.swing.ButtonModel model) {
        if (model.isSelected()) {
            return UIManager.getColor("Component.accentColor");
        }
        if (model.isRollover() || model.isPressed()) {
            return UIManager.getColor("Component.borderColor");
        }
        return null;
    }
}
