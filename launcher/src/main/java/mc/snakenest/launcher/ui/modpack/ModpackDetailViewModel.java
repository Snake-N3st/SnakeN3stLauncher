package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.auth.ChannelInfo;
import mc.snakenest.launcher.modpack.ModpackSettings;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Data + callbacks for {@link ModpackDetailPage}. Holds no logic of its own.
 *
 * @param slug      this modpack's stable identifier - lets {@code LauncherApp} check whether a
 *                  given {@code ModpackDetailPage} (via {@link ModpackDetailPage#slug()}) is
 *                  still the one currently on screen for a given modpack before pushing a live
 *                  update to it, instead of threading a possibly-stale page reference through a
 *                  long-running install/launch call chain (a page rebuilt in the meantime, e.g.
 *                  by "Actualiser", would otherwise never receive updates meant for it).
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
 * @param installedSizeBytes actual on-disk size of the instance directory, {@code null} if
 *                  never installed at all - deliberately reflects reality even on a
 *                  partial/interrupted install rather than the manifest's declared total, shown
 *                  alongside it in {@link ModpackDetailPage}'s header.
 * @param logo      the modpack's real logo, pre-fetched by {@code LauncherApp}
 *                  (best-effort - see {@code ui.common.RemoteImages}); {@code
 *                  null} falls back to the letter placeholder.
 * @param settings  current memory/JVM-args preferences, shown pre-filled in
 *                  the "Gerer" dialog.
 * @param onRepair  clears the locally-recorded manifest and re-syncs+installs
 *                  from scratch (without launching) - for a corrupted/partial
 *                  install.
 * @param onManageMods opens {@code ModManagerDialog} - only enabled when {@code installed}, same
 *                  as {@code onRepair}/{@code onUninstall}, since there's no {@code mods/} folder
 *                  to manage before the modpack has ever been installed.
 * @param onUninstall deletes the instance directory entirely.
 * @param onCancel  interrupts an in-progress sync/install - the main button
 *                  reads "Annuler" while {@link ModpackDetailPage#setBusy}
 *                  is active.
 * @param onStop    asks the running game process to exit (not forcibly) -
 *                  the main button reads "Arreter" while
 *                  {@link ModpackDetailPage#setRunning} is active.
 * @param onKill    force-kills the game process outright - the main button
 *                  reads "Tuer" while {@link ModpackDetailPage#setStopping}
 *                  is active, entered by clicking "Arreter" once already
 *                  (a graceful {@code onStop} isn't guaranteed to actually
 *                  terminate a hung process).
 * @param onSaveSettings called with the new {@link ModpackSettings} when the
 *                       "Gerer" dialog is confirmed.
 * @param availableChannels the distribution channels the "Gerer" dialog's channel picker offers -
 *                  a {@link Supplier} (not a plain list) since {@code LauncherApp} fetches it
 *                  once, asynchronously, at startup (shared with {@code ui.settings.SettingsPage}'s
 *                  own picker) and this page is often built before that fetch resolves; reading
 *                  through the supplier at dialog-open time (rather than baking in whatever list
 *                  existed when this record was built) picks up the real list once it's in.
 */
public record ModpackDetailViewModel(
        String slug,
        String name,
        String description,
        String changelog,
        long totalSizeBytes,
        int fileCount,
        boolean installed,
        boolean updateAvailable,
        Long installedSizeBytes,
        BufferedImage logo,
        ModpackSettings settings,
        Runnable onDemarrer,
        Runnable onRepair,
        Runnable onManageMods,
        Runnable onUninstall,
        Runnable onCancel,
        Runnable onStop,
        Runnable onKill,
        Consumer<ModpackSettings> onSaveSettings,
        Runnable onOpenFolder,
        Runnable onBack,
        Supplier<List<ChannelInfo>> availableChannels
) {
}
