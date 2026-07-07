package mc.snakenest.launcher.ui.common;

import javax.swing.UIManager;
import java.awt.Color;

/** Shared theme-aware colors used across pages, so "what red means here" isn't reinvented per screen. */
public final class Colors {

    private Colors() {
    }

    /** For error text (login failures, install/repair/uninstall failures, ...). Falls back to a plain red if the L&F doesn't define one. */
    public static Color danger() {
        Color color = UIManager.getColor("Component.error.focusedBorderColor");
        return color != null ? color : Color.RED.darker();
    }
}
