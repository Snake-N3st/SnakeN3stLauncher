package mc.snakenest.launcher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppVersionTest {

    @Test
    void reportsTheVersionMavenFilteredIn() {
        String version = AppVersion.current();

        assertNotNull(version);
        assertFalse(version.isBlank());
        // Tests run against target/classes, where filtering has already happened - so an
        // unsubstituted placeholder here would mean the pom's resource filtering broke.
        assertFalse(version.startsWith("${"), "the version placeholder was not substituted: " + version);
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+(-.+)?"),
                "expected MAJEUR.MOYEN.MINEUR (see VERSIONING.md), got: " + version);
    }

    @Test
    void isStableAcrossCalls() {
        assertTrue(AppVersion.current().equals(AppVersion.current()));
    }
}
