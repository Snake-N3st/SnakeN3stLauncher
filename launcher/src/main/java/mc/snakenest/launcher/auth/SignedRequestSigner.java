package mc.snakenest.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Produces the exact signature every player-info and modpacks endpoint
 * expects: a signature over {@code "playerinfo:" + timestamp} (UTF-8, colon
 * included), using the current Unix time in seconds. The server tolerates a
 * ±60s skew and never accepts the same signature twice for long, so this
 * must be recomputed fresh for every request - never cached or reused.
 */
public final class SignedRequestSigner {

    private SignedRequestSigner() {
    }

    public static SignedParams sign(PlayerSession session) {
        long timestamp = Instant.now().getEpochSecond();
        String message = "playerinfo:" + timestamp;
        String signature = session.key().signHex(message.getBytes(StandardCharsets.UTF_8));
        return new SignedParams(session.playerId(), session.key().publicKeyHex(), timestamp, signature, session.clientId());
    }
}
