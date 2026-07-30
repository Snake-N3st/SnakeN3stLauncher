package mc.snakenest.launcher;

import mc.snakenest.launcher.crypto.EncryptedFileKeyStorage;
import mc.snakenest.launcher.crypto.PinProtection;
import mc.snakenest.launcher.modpack.SensitiveFileVault;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The "Code oublié ?" path (see {@code ui.pin.PinUnlockDialog}): throws away everything the
 * forgotten PIN was protecting, because by design there is nothing else that can be done with it.
 *
 * <p>Not a recovery - a recovery mechanism would have to keep the key (or something equivalent to
 * it) somewhere reachable without the PIN, which is precisely what {@code crypto.PinProtection}
 * exists to avoid. So this deletes the encrypted token (the player logs in again, getting a fresh
 * one) and every {@code .sn3enc} modpack file (the pack re-downloads them like any other missing
 * file), then turns the PIN off so the launcher stops asking.
 *
 * <p>Lives in the root package next to {@link Main}/{@link LauncherApp} rather than in {@code
 * crypto} or {@code modpack}, because it deliberately spans both and belongs to neither.
 */
final class PinReset {

    private PinReset() {
    }

    static void wipe(AppDirs dirs, PinProtection pinProtection) {
        // Uses the no-PIN constructor purely to reach delete(), which doesn't need a key at all.
        new EncryptedFileKeyStorage(dirs).delete();

        int wiped = 0;
        for (Path instanceDir : instanceDirectories(dirs)) {
            wiped += SensitiveFileVault.wipeEncrypted(instanceDir);
        }

        pinProtection.disable();

        // The wrapping key is regenerated on demand by EncryptedFileKeyStorage, but an old one left
        // from before the PIN was enabled would silently become the key protecting the *next*
        // token - removing it keeps "PIN off" meaning "a fresh wrapping key", not "whatever was
        // lying around".
        deleteQuietly(dirs.secure().resolve("wrap.key"));

        Log.info(PinReset.class, "PIN reset: token deleted, " + wiped + " encrypted modpack file(s) removed, PIN disabled.");
    }

    private static List<Path> instanceDirectories(AppDirs dirs) {
        if (!Files.isDirectory(dirs.instances())) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dirs.instances())) {
            return stream.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            Log.warn(PinReset.class, "Could not list instances to wipe encrypted files: " + e.getMessage());
            return List.of();
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            Log.warn(PinReset.class, "Could not delete " + path.getFileName() + ": " + e.getMessage());
        }
    }
}
