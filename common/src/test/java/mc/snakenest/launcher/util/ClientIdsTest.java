package mc.snakenest.launcher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIdsTest {

    // Matches src/test/resources/.clientId, on the test classpath for every test here -
    // there is no "no resource at all" case to test as a result, since adding that fixture
    // necessarily makes it present for this whole test class.
    private static final String BUNDLED_FIXTURE = "abcdEFGH0123456789abcdEFGH012345";

    @Test
    void usesSystemPropertyWhenValid() {
        String property = "abcDEF0123456789abcDEF0123456789";
        assertEquals(property, ClientIds.resolve(property));
    }

    @Test
    void fallsBackToBundledResourceWhenPropertyIsNull() {
        assertEquals(BUNDLED_FIXTURE, ClientIds.resolve(null));
    }

    @Test
    void fallsBackToBundledResourceWhenPropertyIsBlank() {
        assertEquals(BUNDLED_FIXTURE, ClientIds.resolve("  "));
    }

    @Test
    void fallsBackToBundledResourceWhenPropertyHasAnInvalidShape() {
        assertEquals(BUNDLED_FIXTURE, ClientIds.resolve("not-a-valid-client-id!!"));
    }
}
