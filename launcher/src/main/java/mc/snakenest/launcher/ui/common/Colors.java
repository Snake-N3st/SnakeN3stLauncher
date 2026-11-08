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

    /**
     * For secondary text - hints, status lines, anything the eye should reach after the control it
     * describes. Resolved from the look-and-feel so it stays readable in both themes; the fallback
     * is derived from the current foreground rather than hardcoded, which keeps it legible on a
     * dark background where a fixed grey would not be.
     */
    public static Color muted() {
        Color color = UIManager.getColor("Label.disabledForeground");

        if (color == null) {
            color = UIManager.getColor("Label.disabledShadow");
        }

        if (color != null) {
            return color;
        }

        Color foreground = UIManager.getColor("Label.foreground");

        return foreground != null ? blendTowardsBackground(foreground) : Color.GRAY;
    }

    /** Half-way between the text colour and the panel behind it - a theme-agnostic "dimmer". */
    private static Color blendTowardsBackground(Color foreground) {
        Color background = UIManager.getColor("Panel.background");

        if (background == null) {
            return foreground.darker();
        }

        return new Color(
                (foreground.getRed() + background.getRed()) / 2,
                (foreground.getGreen() + background.getGreen()) / 2,
                (foreground.getBlue() + background.getBlue()) / 2);
    }
}
