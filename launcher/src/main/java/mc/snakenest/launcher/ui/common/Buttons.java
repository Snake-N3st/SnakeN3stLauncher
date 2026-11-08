package mc.snakenest.launcher.ui.common;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

/** Small factory for consistently-styled flat buttons, reused across the sidebar and the pages. */
public final class Buttons {

    private Buttons() {
    }

    /** A borderless icon-only button (external links, folder/settings shortcuts). */
    public static JButton flatIcon(Icon icon, String tooltip, Runnable onClick) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.addActionListener(e -> onClick.run());
        return button;
    }

    /**
     * Icon-only, but with a visible background at rest (unlike
     * {@link #flatIcon}, whose "toolBarButton" style only shows a
     * background on hover) - for icon actions that should read as an
     * actual button rather than a bare glyph, e.g. the modpack detail
     * page's "settings"/"open folder" actions.
     */
    public static JButton iconButton(Icon icon, String tooltip, Runnable onClick) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.putClientProperty("JButton.buttonType", "roundRect");
        // Deliberately generous - these sat noticeably smaller than the primary action
        // button next to them (e.g. modpack detail page's "Démarrer") with the old 6px margin.
        button.setMargin(new java.awt.Insets(11, 11, 11, 11));
        button.addActionListener(e -> onClick.run());
        return button;
    }

    /**
     * A circular icon button that only paints a background on hover/press, never at rest - for a
     * per-row quick action (e.g. {@code ui.modpack.ModpackCardView}'s play/download icon, a list
     * row's remove button) where an always-visible background would be visual noise repeated
     * across every row, but the click target still needs to read clearly under the cursor.
     * Unlike {@link #flatIcon}'s FlatLaf "toolBarButton" style (a rounded square), this always
     * paints a full circle regardless of the button's own width/height. Icon/tooltip/action are
     * left unset - for a button whose icon or action changes over its lifetime (like
     * {@code ModpackCardView}'s own multi-state action icon); see the other overload for a
     * fixed one.
     */
    public static JButton roundActionButton(int size) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Color background = (getModel().isRollover() || getModel().isPressed())
                        ? UIManager.getColor("Component.borderColor")
                        : null;
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
        };
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        Dimension buttonSize = new Dimension(size, size);
        button.setPreferredSize(buttonSize);
        button.setMinimumSize(buttonSize);
        button.setMaximumSize(buttonSize);
        return button;
    }

    /** {@link #roundActionButton(int)}, for a button whose icon/tooltip/action never change. */
    public static JButton roundActionButton(Icon icon, String tooltip, int size, Runnable onClick) {
        JButton button = roundActionButton(size);
        button.setIcon(icon);
        button.setToolTipText(tooltip);
        button.addActionListener(e -> onClick.run());
        return button;
    }
}
