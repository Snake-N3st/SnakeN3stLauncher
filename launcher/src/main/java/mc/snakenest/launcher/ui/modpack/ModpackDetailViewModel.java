package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modpack.ModpackSettings;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Data + callbacks for {@link ModpackDetailPage}. Holds no logic of its own.
 *
 * @param installed whether this modpack's instance folder already exists -
 *                  drives whether the action button reads "Telecharger" or
 *                  "Demarrer" (see {@link ModpackDetailPage}); {@code
 *                  onDemarrer} is the same action either way (sync+install,
 *                  then launch).
 * @param logo      the modpack's real logo, pre-fetched by {@code LauncherApp}
 *                  (best-effort - see {@code ui.common.RemoteImages}); {@code
 *                  null} falls back to the letter placeholder.
 * @param settings  current memory/JVM-args preferences, shown pre-filled in
 *                  the "Gerer" dialog.
 * @param onRepair  clears the locally-recorded manifest and re-syncs+installs
 *                  from scratch (without launching) - for a corrupted/partial
 *                  install.
 * @param onUninstall deletes the instance directory entirely.
 * @param onSaveSettings called with the new {@link ModpackSettings} when the
 *                       "Gerer" dialog is confirmed.
 */
public record ModpackDetailViewModel(
        String name,
        String description,
        String changelog,
        long totalSizeBytes,
        int fileCount,
        boolean installed,
        BufferedImage logo,
        ModpackSettings settings,
        Runnable onDemarrer,
        Runnable onRepair,
        Runnable onUninstall,
        Consumer<ModpackSettings> onSaveSettings,
        Runnable onOpenFolder,
        Runnable onBack
) {
}
