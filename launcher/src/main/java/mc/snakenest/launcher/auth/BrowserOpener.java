package mc.snakenest.launcher.auth;

import mc.snakenest.launcher.util.Log;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

/**
 * Opens a URL in the system's default browser. Pulled out of
 * {@link DeviceAuthService} as its own collaborator specifically so tests
 * can swap in a no-op implementation - a real {@link Desktop#browse} call
 * left unmocked in a unit test silently opens a real browser tab every time
 * the test runs, which is exactly the kind of thing a test suite must never
 * do (found the hard way: dozens of stray tabs against fake challenge
 * tokens, all 404s, from running the suite repeatedly during development).
 */
public interface BrowserOpener {

    /** @return true if the browser was opened successfully */
    boolean open(URI uri);

    /** The real implementation, used everywhere except tests. */
    BrowserOpener DESKTOP = uri -> {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return true;
            }
        } catch (IOException e) {
            Log.warn(BrowserOpener.class, "Desktop.browse failed: " + e.getMessage());
        }
        return false;
    };
}
