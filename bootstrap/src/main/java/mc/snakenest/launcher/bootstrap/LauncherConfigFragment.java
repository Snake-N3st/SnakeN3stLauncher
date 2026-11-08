package mc.snakenest.launcher.bootstrap;

import com.google.gson.Gson;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.Log;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads just the {@code channel}/{@code playerId} fields out of the launcher's own {@code
 * config.json} (see {@code config.LauncherConfig} in the launcher module) - not a dependency on
 * that whole class (which lives in the {@code launcher} module this one must stay free of, see
 * {@code LauncherReleaseClient}'s own docblock), just a minimal Gson read of the three fields this
 * module actually needs: which channel to request when fetching the next launcher jar, whose
 * existing local key (if any) to try signing that request with when the channel turns out
 * restricted (see {@link BootstrapMain}'s fallback flow), and which theme to paint the splash in
 * so it matches the launcher that replaces it.
 *
 * <p>The running launcher never fetches its own jar - only the bootstrap does, on its *next* run -
 * so this always reflects whatever the player last saved from the Settings page/logged in as, not
 * something written this same session.
 */
final class LauncherConfigFragment {

    private static final Gson GSON = new Gson();

    /** {@code null} means "use the site's default channel" - never saved, or the config file itself doesn't exist/is unreadable. */
    final String channel;
    /** {@code null} means "not logged in (or logged out) as far as the launcher's own config knows" - see {@link #hasKnownPlayer()}. */
    final Long playerId;
    /** Raw {@code "LIGHT"}/{@code "DARK"}, or {@code null} on a first run - see {@link BootstrapTheme#use}. */
    final String theme;

    private LauncherConfigFragment(String channel, Long playerId, String theme) {
        this.channel = channel;
        this.playerId = playerId;
        this.theme = theme;
    }

    boolean hasKnownPlayer() {
        return playerId != null;
    }

    static LauncherConfigFragment read(AppDirs dirs) {
        Path configFile = dirs.configFile();
        if (!Files.isRegularFile(configFile)) {
            return new LauncherConfigFragment(null, null, null);
        }
        try {
            String json = Files.readString(configFile, StandardCharsets.UTF_8);
            RawFields fields = GSON.fromJson(json, RawFields.class);
            return fields != null
                    ? new LauncherConfigFragment(fields.channel, fields.playerId, fields.theme)
                    : new LauncherConfigFragment(null, null, null);
        } catch (Exception e) {
            Log.warn(LauncherConfigFragment.class, "Could not read config.json: " + e.getMessage());
            return new LauncherConfigFragment(null, null, null);
        }
    }

    /** Only the two fields this module cares about - Gson silently ignores every other key in the real config.json. */
    private static final class RawFields {
        String channel;
        Long playerId;
        String theme;
    }
}
