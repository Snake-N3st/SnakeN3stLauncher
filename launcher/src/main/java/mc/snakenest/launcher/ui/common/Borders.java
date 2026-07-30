package mc.snakenest.launcher.ui.common;

import javax.swing.BorderFactory;
import javax.swing.border.Border;

/** Border helpers whose behavior across a theme switch isn't what you'd first reach for. */
public final class Borders {

    private Borders() {
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
}
