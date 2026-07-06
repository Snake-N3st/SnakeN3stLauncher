package mc.snakenest.launcher.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Expected values independently computed in Python (MD5 of
 * {@code "OfflinePlayer:"+username}, with version/variant bits set per
 * RFC 4122 v3) to confirm {@link UUID#nameUUIDFromBytes} really implements
 * vanilla Minecraft's offline-UUID convention, not just "some" UUID.
 */
class OfflineUuidsTest {

    @Test
    void matchesTheKnownVanillaConventionForNotch() {
        assertEquals(UUID.fromString("b50ad385-829d-3141-a216-7e7d7539ba7f"), OfflineUuids.forUsername("Notch"));
    }

    @Test
    void matchesTheKnownVanillaConventionForAnArbitraryName() {
        assertEquals(UUID.fromString("b7f390f4-b94a-303b-9c9b-e76769e2856c"), OfflineUuids.forUsername("SnakeTest"));
    }

    @Test
    void isDeterministic() {
        assertEquals(OfflineUuids.forUsername("SamePlayer"), OfflineUuids.forUsername("SamePlayer"));
    }

    @Test
    void differentUsernamesProduceDifferentUuids() {
        assertNotEquals(OfflineUuids.forUsername("PlayerA"), OfflineUuids.forUsername("PlayerB"));
    }
}
