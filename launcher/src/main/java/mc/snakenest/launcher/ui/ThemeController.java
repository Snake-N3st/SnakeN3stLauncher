package mc.snakenest.launcher.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import mc.snakenest.launcher.config.ConfigStore;
import mc.snakenest.launcher.config.Theme;
import mc.snakenest.launcher.util.Log;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Applies the light/dark Look & Feel (FlatLaf) and persists the choice.
 * {@link #applyStartupTheme} must be called before any {@link javax.swing.JFrame}
 * is constructed; {@link #switchTo} can be called any time afterwards and
 * updates every already-open window live.
 */
public final class ThemeController {

    private final ConfigStore configStore;
    private Theme current;

    public ThemeController(ConfigStore configStore) {
        this.configStore = configStore;
        this.current = configStore.load().theme();
    }

    public Theme current() {
        return current;
    }

    /** Call once, first thing in main(), before creating any Swing component. */
    public void applyStartupTheme() {
        setLookAndFeel(current);
    }

    /** Like {@link #applyStartupTheme}, but overrides whatever was persisted - mainly for previews/testing. */
    public void applyStartupTheme(Theme override) {
        current = override;
        setLookAndFeel(current);
    }

    /** Switches theme at runtime and repaints every open window. Safe to call from the EDT. */
    public void switchTo(Theme theme) {
        if (theme == current) {
            return;
        }
        current = theme;
        setLookAndFeel(theme);
        FlatLaf.updateUI();

        var config = configStore.load();
        config.setTheme(theme);
        configStore.save(config);
    }

    private void setLookAndFeel(Theme theme) {
        try {
            if (theme == Theme.DARK) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
            // FlatLaf's default (4px) is barely perceptible - the account popover
            // (ui.account.AccountPopover) needs to actually read as rounded.
            UIManager.put("Popup.borderCornerRadius", 12);
        } catch (UnsupportedLookAndFeelException e) {
            Log.warn(ThemeController.class, "Could not apply FlatLaf theme: " + e.getMessage());
        }
    }
}
