package mc.snakenest.launcher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HumanSizeTest {

    @Test
    void bytesStayWhole() {
        assertEquals("512 o", HumanSize.format(512));
    }

    @Test
    void megabytesGetOneDecimal() {
        assertEquals("104,1 Mo", HumanSize.format((long) (104.1 * 1024 * 1024)));
    }

    @Test
    void gigabytes() {
        assertEquals("1,5 Go", HumanSize.format((long) (1.5 * 1024 * 1024 * 1024)));
    }
}
