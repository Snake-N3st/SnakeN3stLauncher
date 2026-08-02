package mc.snakenest.launcher.bugreport;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdtHeartbeatMonitorTest {

    @Test
    void isNotFrozenRightAfterConstruction() {
        EdtHeartbeatMonitor monitor = new EdtHeartbeatMonitor(Duration.ofMillis(200));

        assertFalse(monitor.isFrozen());
    }

    @Test
    void isNotFrozenWhileHeartbeatsKeepLanding() throws InterruptedException {
        EdtHeartbeatMonitor monitor = new EdtHeartbeatMonitor(Duration.ofMillis(200));

        for (int i = 0; i < 5; i++) {
            Thread.sleep(80);
            monitor.heartbeat();
            assertFalse(monitor.isFrozen());
        }
    }

    @Test
    void becomesFrozenOnceTheThresholdElapsesWithNoHeartbeat() throws InterruptedException {
        EdtHeartbeatMonitor monitor = new EdtHeartbeatMonitor(Duration.ofMillis(150));

        Thread.sleep(300);

        assertTrue(monitor.isFrozen());
    }

    @Test
    void aFreshHeartbeatUnfreezesIt() throws InterruptedException {
        EdtHeartbeatMonitor monitor = new EdtHeartbeatMonitor(Duration.ofMillis(150));
        Thread.sleep(300);
        assertTrue(monitor.isFrozen());

        monitor.heartbeat();

        assertFalse(monitor.isFrozen());
    }
}
