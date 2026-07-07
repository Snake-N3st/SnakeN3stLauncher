package mc.snakenest.launcher.ui.common;

import javax.swing.Icon;
import javax.swing.JButton;

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
}
