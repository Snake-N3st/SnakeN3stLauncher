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
}
