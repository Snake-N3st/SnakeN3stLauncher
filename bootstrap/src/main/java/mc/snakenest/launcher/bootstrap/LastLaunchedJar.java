package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tracks which cached launcher jar was last actually spawned as a process successfully - the
 * "Lancer le dernier jar exécuté avec succès" option in {@link ChannelFallbackDialog}, for when a
 * restricted channel can't be resolved at all (no eligible key, reconnect declined/failed). Marks
 * "successfully spawned", not "successfully ran to completion" - that's the most this module can
 * actually observe about a process it starts and then exits itself (see {@code
 * BootstrapMain#spawnLauncher}), and is enough to know the jar itself is valid and runnable.
 */
final class LastLaunchedJar {

    private static final String MARKER_FILE_NAME = "last-run.txt";

    private LastLaunchedJar() {
    }

    /** Call right after {@code spawnLauncher} succeeds. Best-effort - a failure to record this just means the fallback option below won't find anything next time. */
    static void record(AppDirs dirs, String version) {
        try {
            AtomicFiles.write(markerFile(dirs), version.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.warn(LastLaunchedJar.class, "Could not record the last-launched version: " + e.getMessage());
        }
    }

    /** The cached jar path for whatever version was last recorded, or {@code null} if nothing was ever recorded or that jar is no longer on disk (pruned, or never cached in the first place). */
    static Path find(AppDirs dirs) {
        Path marker = markerFile(dirs);
        if (!Files.isRegularFile(marker)) {
            return null;
        }
        try {
            String version = Files.readString(marker, StandardCharsets.UTF_8).strip();
            if (version.isEmpty()) {
                return null;
            }
            Path jar = dirs.cacheLauncher().resolve(version + ".jar");
            return Files.isRegularFile(jar) ? jar : null;
        } catch (IOException e) {
            Log.warn(LastLaunchedJar.class, "Could not read the last-launched marker: " + e.getMessage());
            return null;
        }
    }

    private static Path markerFile(AppDirs dirs) {
        return dirs.cacheLauncher().resolve(MARKER_FILE_NAME);
    }
}
