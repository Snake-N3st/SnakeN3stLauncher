package mc.snakenest.launcher.game;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Derives an offline-mode UUID from a username, using vanilla Minecraft's
 * own convention: an MD5-based (v3) name UUID of {@code "OfflinePlayer:"+username}.
 * {@link UUID#nameUUIDFromBytes} already implements exactly this algorithm,
 * so there's nothing to hand-roll here. Matching this convention (rather
 * than a random UUID) matters for compatibility with server-side
 * mods/plugins that expect it.
 */
public final class OfflineUuids {

    private OfflineUuids() {
    }

    public static UUID forUsername(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }
}
