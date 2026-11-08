package mc.snakenest.launcher.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionCompareTest {

    @Test
    void ordersNumerically() {
        assertTrue(VersionCompare.compare("2.3.0", "2.4.0") < 0);
        assertTrue(VersionCompare.compare("2.10.0", "2.9.0") > 0, "10 is more than 9, unlike as strings");
        assertTrue(VersionCompare.compare("3.0.0", "2.99.99") > 0);
        assertEquals(0, VersionCompare.compare("2.4.0", "2.4.0"));
    }

    @Test
    void missingTrailingPartsCountAsZero() {
        assertEquals(0, VersionCompare.compare("2.4", "2.4.0"));
        assertTrue(VersionCompare.compare("2.4", "2.4.1") < 0);
        assertTrue(VersionCompare.compare("1.0", "2.0.0") < 0);
    }

    @Test
    void anythingItCannotReadComparesAsEqualSoItNeverRaisesAFalseRollback() {
        for (String odd : new String[] {"2.4.0-rc.1", "dev", "", "2..4", "v2.4", null}) {
            assertEquals(0, VersionCompare.compare(odd, "2.4.0"), String.valueOf(odd));
            assertEquals(0, VersionCompare.compare("2.4.0", odd), String.valueOf(odd));
        }
    }
}
