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
 * @param updateAvailable whether the locally-installed version differs from
 *                  the latest one on the server (only meaningful when
 *                  {@code installed} is true) - the button reads
 *                  "Mettre a jour" instead of "Demarrer", {@code onDemarrer}
 *                  is still the same sync+install+launch action either way.
 * @param logo      the modpack's real logo, pre-fetched by {@code LauncherApp}
 *                  (best-effort - see {@code ui.common.RemoteImages}); {@code
 *                  null} falls back to the letter placeholder.
 * @param settings  current memory/JVM-args preferences, shown pre-filled in
 *                  the "Gerer" dialog.
 * @param onRepair  clears the locally-recorded manifest and re-syncs+installs
 *                  from scratch (without launching) - for a corrupted/partial
 *                  install.
 * @param onUninstall deletes the instance directory entirely.
 * @param onCancel  interrupts an in-progress sync/install - the main button
 *                  reads "Annuler" while {@link ModpackDetailPage#setBusy}
 *                  is active.
 * @param onStop    kills the running game process - the main button reads
 *                  "Arreter" while {@link ModpackDetailPage#setRunning} is
 *                  active.
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
        boolean updateAvailable,
        BufferedImage logo,
        ModpackSettings settings,
        Runnable onDemarrer,
        Runnable onRepair,
        Runnable onUninstall,
        Runnable onCancel,
        Runnable onStop,
        Consumer<ModpackSettings> onSaveSettings,
        Runnable onOpenFolder,
        Runnable onBack
) {
}
