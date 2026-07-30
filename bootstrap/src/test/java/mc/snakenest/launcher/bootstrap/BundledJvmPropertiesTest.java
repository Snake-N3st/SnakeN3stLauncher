package mc.snakenest.launcher.bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises against src/test/resources/jvm.prop, on the test classpath for every test here. */
class BundledJvmPropertiesTest {

    @AfterEach
    void cleanUp() {
        System.clearProperty("sn3.clientId");
        System.clearProperty("sn3.baseUrl");
    }

    @Test
    void appliesEveryKeyFromTheBundledResource() {
        BundledJvmProperties.loadIfPresent();

        assertEquals("abcDEF0123456789abcDEF0123456789", System.getProperty("sn3.clientId"));
        assertEquals("https://example.test", System.getProperty("sn3.baseUrl"));
    }

    @Test
    void neverOverridesAPropertyAlreadySetByAHigherPrecedenceSource() {
        System.setProperty("sn3.clientId", "already-set-elsewhere");

        BundledJvmProperties.loadIfPresent();

        assertEquals("already-set-elsewhere", System.getProperty("sn3.clientId"));
        // sn3.baseUrl wasn't already set, so the bundled resource still fills it in.
        assertEquals("https://example.test", System.getProperty("sn3.baseUrl"));
    }
}
