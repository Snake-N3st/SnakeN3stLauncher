package mc.snakenest.launcher.game;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Everything needed to launch one already-installed instance.
 *
 * @param username    must exactly match the site username (see the
 *                    in-game mod's AuthMe bridge, which keys the
 *                    passwordless login on the connecting player's name)
 * @param offlineUuid see {@link OfflineUuids}
 * @param sn3TokenHex the player's Ed25519 seed, hex-encoded - passed to the
 *                    game as {@code -Dsn3.token=...} for the mod to read.
 *                    A secret: never logged, and deliberately excluded from
 *                    {@link #toString()} below.
 */
public record LaunchRequest(
        String username,
        UUID offlineUuid,
        Path instanceDir,
        String mcVersion,
        ModLoader loader,
        String loaderVersion,
        String sn3TokenHex
) {
    @Override
    public String toString() {
        return "LaunchRequest[username=%s, offlineUuid=%s, instanceDir=%s, mcVersion=%s, loader=%s, loaderVersion=%s, sn3TokenHex=<redacted>]"
                .formatted(username, offlineUuid, instanceDir, mcVersion, loader, loaderVersion);
    }
}
