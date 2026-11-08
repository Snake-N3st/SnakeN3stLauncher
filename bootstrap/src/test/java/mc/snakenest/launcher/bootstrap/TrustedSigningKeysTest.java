package mc.snakenest.launcher.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedSigningKeysTest {

    private static final String KEY_A = "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8";
    private static final String KEY_B = "96dc94b7be7af39355f5ace60cad95a6223d30332365b7a6934880a58d2e3f78";

    private static TrustedSigningKeys from(Map<String, String> props) {
        return TrustedSigningKeys.fromProperties(props::get);
    }

    @Test
    void readsACommaSeparatedListPerKind() {
        TrustedSigningKeys keys = from(Map.of(
                TrustedSigningKeys.LAUNCHER_PROPERTY, KEY_A,
                TrustedSigningKeys.BOOTSTRAP_PROPERTY, KEY_A + "," + KEY_B));

        assertEquals(1, keys.keysFor("launcher").size());
        assertEquals(2, keys.keysFor("bootstrap").size(), "a rotation lists the old and the new key");
    }

    @Test
    void theBootstrapKeysAlsoVerifyThePackageButTheLauncherKeysDoNot() {
        TrustedSigningKeys keys = from(Map.of(
                TrustedSigningKeys.LAUNCHER_PROPERTY, KEY_A,
                TrustedSigningKeys.BOOTSTRAP_PROPERTY, KEY_B));

        assertEquals(1, keys.keysFor("deb").size());
        assertEquals(KEY_B, mc.snakenest.launcher.util.Hex.encode(keys.keysFor("deb").get(0)));
        assertEquals(KEY_A, mc.snakenest.launcher.util.Hex.encode(keys.keysFor("launcher").get(0)));
    }

    @Test
    void nothingConfiguredMeansNothingToVerifyWith() {
        TrustedSigningKeys keys = from(Map.of());

        for (String type : new String[] {"launcher", "bootstrap", "deb"}) {
            assertFalse(keys.isConfiguredFor(type), type);
        }
    }

    @Test
    void aKindCanBeConfiguredWithoutTheOther() {
        TrustedSigningKeys keys = from(Map.of(TrustedSigningKeys.LAUNCHER_PROPERTY, KEY_A));

        assertTrue(keys.isConfiguredFor("launcher"));
        assertFalse(keys.isConfiguredFor("bootstrap"));
        assertFalse(keys.isConfiguredFor("deb"));
    }

    @Test
    void malformedEntriesAreSkippedAndDoNotCostTheGoodOnes() {
        TrustedSigningKeys keys = from(Map.of(TrustedSigningKeys.LAUNCHER_PROPERTY,
                "nothex," + KEY_A + ",," + KEY_A.substring(2) + ",  " + KEY_B + "  ,"));

        assertEquals(2, keys.keysFor("launcher").size(), "only the two well-formed 32-byte keys survive");
    }

    @Test
    void aValueWithNoUsableKeyIsUnconfiguredRatherThanUnverifiable() {
        // Warn, do not block: a typo must not turn into a fleet-wide refusal.
        TrustedSigningKeys keys = from(Map.of(TrustedSigningKeys.LAUNCHER_PROPERTY, "garbage,also-garbage"));

        assertFalse(keys.isConfiguredFor("launcher"));
    }
}
