package mc.snakenest.launcher.game;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Everything needed to launch one already-installed instance.
 *
 * @param username      must exactly match the site username (see the
 *                      in-game mod's AuthMe bridge, which keys the
 *                      passwordless login on the connecting player's name)
 * @param offlineUuid   see {@link OfflineUuids}
 * @param memoryMb      heap size, passed as {@code -Xmx<memoryMb>M} - see
 *                      {@code modpack.ModpackSettings}
 * @param extraJvmArgs  raw, space-separated extra JVM args from {@code
 *                      modpack.ModpackSettings}; empty string (not null) when unset
 * @param javaPath      the {@code java} binary to run the game with, already resolved from the
 *                      per-modpack and launcher-wide settings by {@link JavaRuntimes#resolve};
 *                      {@code null} means "let OpenLauncherLib use the launcher's own JVM", the
 *                      behaviour that predates the setting
 * @param sn3TokenHex   the player's Ed25519 seed, hex-encoded - passed to the
 *                      game as {@code -Dsn3.token=...} for the mod to read.
 *                      A secret: never logged, and deliberately excluded from
 *                      {@link #toString()} below.
 */
public record LaunchRequest(
        String username,
        UUID offlineUuid,
        Path instanceDir,
        String mcVersion,
        ModLoader loader,
        String loaderVersion,
        int memoryMb,
        String extraJvmArgs,
        String javaPath,
        String sn3TokenHex,
        boolean localOnly
) {
    public LaunchRequest(String username, UUID offlineUuid, Path instanceDir, String mcVersion, ModLoader loader,
                         String loaderVersion, int memoryMb, String extraJvmArgs, String javaPath, String sn3TokenHex) {
        this(username, offlineUuid, instanceDir, mcVersion, loader, loaderVersion, memoryMb, extraJvmArgs, javaPath, sn3TokenHex, false);
    }

    @Override
    public String toString() {
        return "LaunchRequest[username=%s, offlineUuid=%s, instanceDir=%s, mcVersion=%s, loader=%s, loaderVersion=%s, memoryMb=%d, extraJvmArgs=%s, javaPath=%s, sn3TokenHex=<redacted>]"
                .formatted(username, offlineUuid, instanceDir, mcVersion, loader, loaderVersion, memoryMb, extraJvmArgs, javaPath);
    }
}
