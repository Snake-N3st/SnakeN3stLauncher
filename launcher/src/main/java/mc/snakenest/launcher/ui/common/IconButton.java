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
 */
public final class IconButton extends JToggleButton {

    public IconButton(Icon icon, String tooltip) {
        super(icon);
        setToolTipText(tooltip);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);

        int diameter = Math.max(icon.getIconWidth(), icon.getIconHeight()) + 22;
        setPreferredSize(new Dimension(diameter, diameter));
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
