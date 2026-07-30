package mc.snakenest.launcher.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientIdsTest {

    @Test
    void acceptsAValidShape() {
        String candidate = "abcDEF0123456789abcDEF0123456789";
        assertEquals(candidate, ClientIds.resolve(candidate));
    }

    @Test
    void rejectsNull() {
        assertNull(ClientIds.resolve(null));
    }

    @Test
    void rejectsBlank() {
        assertNull(ClientIds.resolve("  "));
    }

    @Test
    void rejectsAnInvalidShape() {
        assertNull(ClientIds.resolve("not-a-valid-client-id!!"));
    }
}
