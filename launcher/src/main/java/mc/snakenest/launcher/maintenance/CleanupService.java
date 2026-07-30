package mc.snakenest.launcher.maintenance;

import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The "Nettoyer" button: throws away everything that is purely regenerable - the launcher's own
 * logs, each modpack's Minecraft logs and crash reports, and the download caches.
 *
 * <h2>What is deliberately NOT deleted</h2>
 * <ul>
 *   <li><b>The shared assets/libraries pools</b> ({@code AppDirs#sharedAssetsPool()} and friends).
 *   Counter-intuitive but important: with hardlink sharing on, deleting a pooled file frees
 *   <em>nothing</em>, because every instance still holding a link keeps the underlying data alive -
 *   so it would cost a multi-hundred-MB re-download for zero reclaimed space. Space is reclaimed
 *   by the migration in {@code game.assets.SharedAssetsLinker} instead.
 *   <li><b>Anything under an instance other than {@code logs/} and {@code crash-reports/}</b> -
 *   mods, configs, worlds and saves live there, and a "clean up" button must never be the thing
 *   that eats a player's singleplayer world.
 *   <li><b>{@code secure/}, {@code config.json}, {@code modpack-settings/}</b> - the session, the
 *   preferences and per-modpack memory settings are not caches.
 *   <li><b>{@code .sn3enc} files</b> - encrypted, but real data (see
 *   {@code modpack.SensitiveFileVault}); wiping those is the separate, explicitly-confirmed
 *   "code oublié" path.
 * </ul>
 */
public final class CleanupService {

    /** Per-instance subdirectories that only ever hold regenerable diagnostics. */
    private static final List<String> INSTANCE_CLEANABLE_SUBDIRS = List.of("logs", "crash-reports");

    private final AppDirs dirs;

    public CleanupService(AppDirs dirs) {
        this.dirs = dirs;
    }

    /** @param filesDeleted how many files went away, {@code bytesFreed} their total size (measured before deleting) */
    public record Result(int filesDeleted, long bytesFreed) {

        static Result empty() {
            return new Result(0, 0);
        }

        Result plus(Result other) {
            return new Result(filesDeleted + other.filesDeleted, bytesFreed + other.bytesFreed);
        }
    }

    /**
     * Blocking - callers must run it off the EDT. Best-effort throughout: a file that refuses to be
     * deleted (locked on Windows, permissions) is logged and skipped, since a partial clean-up is
     * still useful and there's nothing the player could do about the rest anyway.
     */
    public Result cleanEverything() {
        Result result = deleteContents(dirs.logs())
                .plus(deleteContents(dirs.cache()));

        for (Path instanceDir : instanceDirectories()) {
            for (String subdir : INSTANCE_CLEANABLE_SUBDIRS) {
                result = result.plus(deleteContents(instanceDir.resolve(subdir)));
            }
        }

        Log.info(CleanupService.class, "Cleanup removed " + result.filesDeleted() + " file(s), "
                + (result.bytesFreed() / 1024 / 1024) + " Mo freed.");
        return result;
    }

    /**
     * Counts what {@link #cleanEverything()} would remove, so the confirmation can name a real
     * figure instead of asking the player to agree to an unknown amount of deletion.
     */
    public Result preview() {
        Result result = measureContents(dirs.logs()).plus(measureContents(dirs.cache()));
        for (Path instanceDir : instanceDirectories()) {
            for (String subdir : INSTANCE_CLEANABLE_SUBDIRS) {
                result = result.plus(measureContents(instanceDir.resolve(subdir)));
            }
        }
        return result;
    }

    /** Deletes everything <em>inside</em> {@code directory} but keeps the directory itself - nothing re-creates these on demand, and an absent {@code logs/} would just fail the next write. */
    private Result deleteContents(Path directory) {
        Result result = Result.empty();
        for (Path file : filesUnder(directory)) {
            try {
                long size = Files.size(file);
                Files.delete(file);
                result = result.plus(new Result(1, size));
            } catch (IOException e) {
                // Expected on the log file the running launcher still holds open on Windows.
                Log.warn(CleanupService.class, "Could not delete " + file.getFileName() + ": " + e.getMessage());
            }
        }
        deleteEmptySubdirectories(directory);
        return result;
    }

    private Result measureContents(Path directory) {
        Result result = Result.empty();
        for (Path file : filesUnder(directory)) {
            try {
                result = result.plus(new Result(1, Files.size(file)));
            } catch (IOException e) {
                result = result.plus(new Result(1, 0));
            }
        }
        return result;
    }

    /** Materialized rather than streamed, since the caller deletes as it goes. */
    private List<Path> filesUnder(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            Log.warn(CleanupService.class, "Could not list " + directory + ": " + e.getMessage());
            return List.of();
        }
    }

    /** Leaves behind the now-empty tree structure otherwise; deepest-first so a parent is only tried once its children are gone. */
    private void deleteEmptySubdirectories(Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<Path> subdirectories;
        try (Stream<Path> stream = Files.walk(directory)) {
            subdirectories = stream.filter(Files::isDirectory)
                    .filter(path -> !path.equals(directory))
                    .sorted(java.util.Comparator.comparingInt(Path::getNameCount).reversed())
                    .toList();
        } catch (IOException e) {
            return;
        }
        for (Path subdirectory : subdirectories) {
            try {
                Files.delete(subdirectory);
            } catch (IOException e) {
                // Not empty (something above refused to go), or gone already - either way, fine.
            }
        }
    }

    private List<Path> instanceDirectories() {
        if (!Files.isDirectory(dirs.instances())) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dirs.instances())) {
            return stream.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            Log.warn(CleanupService.class, "Could not list instances: " + e.getMessage());
            return List.of();
        }
    }
}
