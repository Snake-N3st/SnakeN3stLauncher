package mc.snakenest.launcher.ui.common;

import javax.swing.Icon;
import javax.swing.JToggleButton;

/**
 * A borderless, flat icon-only toggle button used for sidebar navigation:
 * exactly one is selected at a time within a {@link javax.swing.ButtonGroup}.
 * Plain {@link JToggleButton} styling (FlatLaf already renders this cleanly
 * as a flat button) rather than a custom-painted component - simplest thing
 * that looks right.
 */
public final class IconButton extends JToggleButton {

    public IconButton(Icon icon, String tooltip) {
        super(icon);
        setToolTipText(tooltip);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(true);
        setOpaque(false);
        putClientProperty("JButton.buttonType", "toolBarButton");
    }
}
