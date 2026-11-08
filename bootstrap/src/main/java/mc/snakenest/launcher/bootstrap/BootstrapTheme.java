package mc.snakenest.launcher.bootstrap;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * The colours the bootstrap's own windows paint with, in both themes, plus the {@link UIManager}
 * tweaks that make Swing's stock dialogs ({@link javax.swing.JOptionPane}) match them.
 *
 * <p>The values are sampled from the launcher's own FlatLaf themes rather than invented, so the
 * splash the player stares at for a few seconds and the launcher window that replaces it look like
 * one application instead of two. They are duplicated here as literals on purpose: this module must
 * not depend on the launcher module (see this package's Javadoc for the GPL boundary), and the
 * launcher's {@code ui.common.Colors} resolves them from a look-and-feel this module doesn't ship.
 *
 * <p><b>Which theme.</b> Read from the launcher's own {@code config.json} (see {@link
 * LauncherConfigFragment}) rather than guessed: a player who set the launcher to the light theme
 * and then gets a dark splash every startup is being shown two different applications. Dark stays
 * the default for a first run, where no config exists yet.
 *
 * <p>Restyling {@code UIManager} globally is safe here in a way it would not be in the launcher:
 * this JVM's entire UI is the three or four windows in this package, and it exits as soon as the
 * launcher is up.
 */
final class BootstrapTheme {

    /** FlatLaf Darcula. */
    static final BootstrapTheme DARK = new BootstrapTheme(
            new Color(0x3C3F41),  // background - the launcher's panel colour
            new Color(0x2B2D2E),  // recessed - progress trough, text areas
            new Color(0x4B6EAF),  // accent - the launcher's active sidebar button
            new Color(0xDDDDDD),  // text
            new Color(0x9A9A9A),  // muted
            new Color(0x55595C),  // border
            new Color(0xE05252)); // danger

    /** FlatLaf Light. */
    static final BootstrapTheme LIGHT = new BootstrapTheme(
            new Color(0xF2F2F2),
            new Color(0xE1E1E1),
            new Color(0x2675BF),
            new Color(0x1E1E1E),
            new Color(0x6E6E6E),
            new Color(0xC0C0C0),
            new Color(0xC0392B));

    /**
     * Written once during startup, read from the Swing thread afterwards - volatile so the
     * assignment is actually visible to the EDT rather than left in the calling thread's cache.
     */
    private static volatile BootstrapTheme current = DARK;

    final Color background;
    final Color recessed;
    final Color accent;
    final Color text;
    final Color muted;
    final Color border;
    final Color danger;

    private BootstrapTheme(Color background, Color recessed, Color accent, Color text,
                           Color muted, Color border, Color danger) {
        this.background = background;
        this.recessed = recessed;
        this.accent = accent;
        this.text = text;
        this.muted = muted;
        this.border = border;
        this.danger = danger;
    }

    static BootstrapTheme current() {
        return current;
    }

    /**
     * Selects the palette from whatever the launcher last saved.
     *
     * @param themeName the raw {@code theme} value out of config.json - {@code "LIGHT"} switches to
     *                  the light palette, anything else (including null, an unknown value, or no
     *                  config at all on a first run) keeps the dark one
     */
    static void use(String themeName) {
        current = "LIGHT".equalsIgnoreCase(themeName) ? LIGHT : DARK;
        applyToDialogs();
    }

    /**
     * Makes Swing's built-in dialogs follow the selected palette. Best-effort and never fatal: a
     * look-and-feel that ignores these keys simply yields the stock dialogs, which is what shipped
     * before.
     */
    static void applyToDialogs() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            BootstrapTheme theme = current();
            UIManager.put("OptionPane.background", theme.background);
            UIManager.put("OptionPane.messageForeground", theme.text);
            UIManager.put("Panel.background", theme.background);
            UIManager.put("Button.background", theme.recessed);
            UIManager.put("Button.foreground", theme.text);
            UIManager.put("TextArea.background", theme.recessed);
            UIManager.put("TextArea.foreground", theme.text);
            UIManager.put("TextArea.caretForeground", theme.text);
            UIManager.put("ScrollPane.background", theme.background);
            UIManager.put("Label.foreground", theme.text);
        } catch (Exception ignored) {
            // Purely cosmetic - a failure here must never stop the launcher from starting.
        }
    }

    /** The UI font at a given size/style, falling back to whatever Swing already chose. */
    static Font font(Font base, int style, float size) {
        return base.deriveFont(style, size);
    }
}
